#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sinh VĂN BẢN huấn luyện RNN-LM cho hợp nhất LM nông (spec `kachi-voice-finetune` §4.2 / P1.2).

Ba nguồn, ba vai trò khác nhau — trộn sai tỉ lệ là hỏng cả phép đo:

 1. **Câu domain** (`variants.tsv` nở to + bọc thêm ngữ đệm) — thứ LM phải thuộc. Đây là nguồn tạo ra
    toàn bộ cái lợi: khi bộ giải mã phân vân giữa *"bật"* và *"tắt"*, LM domain biết câu nào có thật.
 2. **Văn phổ thông tiếng Việt** (Wikipedia vi qua HF datasets-server) — thứ giữ LM **không sập**. LM chỉ
    học lệnh sẽ đẩy MỌI âm thanh về phía lệnh, kể cả khi người ta nói chuyện riêng trong xe. Đó đúng là
    họ lỗi *"ừ bật đèn đọc"* / *"đang đọc sách"* đã thấy trong log xe
    (`voice-stream-eval-2026-09-16.md` §6), nên thêm dầu vào lửa là điều cuối cùng nên làm.
 3. **Câu KHÔNG phải lệnh** (`extra.tsv` mục non-command) — âm neo cho vùng giữa hai nguồn trên.

## Kỷ luật CHIA CÂU (spec R-nf1) — đọc trước khi trích số

Corpus WAV được sinh từ chính `variants.tsv`. Nếu LM học **mọi** câu rồi ta đo lại trên corpus, con số
tăng lên chỉ nói *"LM đã thuộc bài"*, không nói *"LM nghe tốt hơn"*. Nên script này chia câu domain làm hai:

  • `--split train` → bỏ ra 20 % câu (băm ổn định theo nội dung câu, seed cố định) ⇒ dùng để **đo thật**:
    WAV có câu tham chiếu nằm trong 20 % đó là **CHƯA THẤY** với LM, số trên đó mới là số khái quát hoá.
  • `--split all`   → giữ mọi câu ⇒ đây là bản **để ship** (trên xe ta muốn LM biết hết lệnh).

Báo cáo bắt buộc tách hai cột "đã thấy" / "chưa thấy". Chỉ báo cột "đã thấy" là tự lừa.

Giấy phép nguồn 2: Wikipedia tiếng Việt — **CC-BY-SA-4.0**. Chỉ dùng làm văn liệu huấn luyện LM, không
phân phối lại nguyên văn; ghi nhận nguồn trong §4.5 của spec.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(REPO, "scripts", "voice"))

from vi_text import norm as vi_norm  # noqa: E402

HOLDOUT_FRAC = 0.20
HOLDOUT_SEED = "kachi-voice-ft-2026-09-16"

# Ngữ đệm người lái hay gắn vào lệnh. Lấy từ `templates.tsv` (cùng nguồn tay đã soạn) + log xe.
PREFIX = ["", "", "", "", "kachi ơi ", "ê kachi ", "này kachi ", "làm ơn ", "cho tôi ", "cho mình ",
          "giúp tôi ", "bạn ơi ", "ok "]
SUFFIX = ["", "", "", "", " đi", " nhé", " nha", " với", " giùm mình", " dùm tôi", " cái",
          " được không", " giúp mình cái"]


def stable_bucket(text: str) -> float:
    """Băm ổn định 0..1 — cùng câu luôn ra cùng số, không phụ thuộc thứ tự đọc tệp hay phiên bản python."""
    h = hashlib.sha256((HOLDOUT_SEED + "|" + text).encode("utf-8")).hexdigest()
    return int(h[:8], 16) / 0xFFFFFFFF


def read_variants(path: str) -> list[str]:
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            p = line.rstrip("\n").split("\t")
            if len(p) >= 5 and p[4].strip():
                out.append(p[4].strip())
    return out


def wrap(sents: list[str], factor: int) -> list[str]:
    """Bọc ngữ đệm quanh câu lệnh. Băm theo nội dung ⇒ tái lập được, không dùng random toàn cục."""
    out = []
    for s in sents:
        seen = set()
        for k in range(factor):
            h = hashlib.sha256(f"{s}|{k}".encode("utf-8")).digest()
            p = PREFIX[h[0] % len(PREFIX)]
            q = SUFFIX[h[1] % len(SUFFIX)]
            t = (p + s + q).strip()
            if t not in seen:
                seen.add(t)
                out.append(t)
    return out


def fetch_wiki(n_rows: int, cache: str) -> list[str]:
    """Kéo văn phổ thông qua HF datasets-server (không cần token cho tập công khai).

    Trả về CÂU đã tách, đã chuẩn hoá bằng `vi_text.norm` — cùng một hàm mà bộ đo dùng, nếu không thì
    LM học một kiểu chữ còn bộ giải mã chấm một kiểu chữ khác.
    """
    if os.path.exists(cache):
        with open(cache, encoding="utf-8") as f:
            return [l.rstrip("\n") for l in f if l.strip()]
    base = ("https://datasets-server.huggingface.co/rows?dataset=wikimedia%2Fwikipedia"
            "&config=20231101.vi&split=train")
    sents: list[str] = []
    offset = 0
    step = 100
    while len(sents) < n_rows and offset < 400_000:
        url = f"{base}&offset={offset}&length={step}"
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                data = json.load(r)
        except Exception as e:  # mạng hỏng giữa chừng: dùng phần đã kéo được, ghi rõ ra stderr
            print(f"!! wiki fetch dừng ở offset={offset}: {e}", file=sys.stderr)
            break
        rows = data.get("rows", [])
        if not rows:
            break
        for row in rows:
            txt = row["row"].get("text", "")
            for para in txt.split("\n"):
                for s in re.split(r"(?<=[.!?;:])\s+", para):
                    s = s.strip()
                    if not (12 <= len(s) <= 180):
                        continue
                    if re.search(r"[0-9]{5,}|https?://|[|{}<>\[\]=]", s):
                        continue
                    s = vi_norm(s)
                    if not s or len(s.split()) < 4:
                        continue
                    # bỏ câu có chữ ngoài bảng chữ tiếng Việt (tên riêng nước ngoài, ký tự lạ)
                    if re.search(r"[^a-zà-ỹ\s]", s):
                        continue
                    sents.append(s)
        offset += step * 7  # nhảy quãng để lấy chủ đề đa dạng, không đọc tuần tự một mảng bài
        time.sleep(0.15)
    sents = sents[:n_rows]
    os.makedirs(os.path.dirname(cache), exist_ok=True)
    with open(cache, "w", encoding="utf-8") as f:
        f.write("\n".join(sents) + "\n")
    return sents


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--variants", required=True, help="tệp variants.tsv (bản đã nở)")
    ap.add_argument("--extra", default=os.path.join(REPO, "scripts/voice/data/extra.tsv"))
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--split", choices=["train", "all"], default="train",
                    help="train = bỏ ra 20 %% câu để đo; all = giữ hết (bản để ship)")
    ap.add_argument("--wrap-factor", type=int, default=4)
    ap.add_argument("--wiki-rows", type=int, default=60000)
    ap.add_argument("--wiki-cache", default="/tmp/kachi-ft-wiki-vi.txt")
    args = ap.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)

    dom_all = sorted(set(vi_norm(s) for s in read_variants(args.variants)))
    dom_all = [s for s in dom_all if s]
    holdout = sorted(s for s in dom_all if stable_bucket(s) < HOLDOUT_FRAC)
    dom_use = dom_all if args.split == "all" else [s for s in dom_all if stable_bucket(s) >= HOLDOUT_FRAC]

    wrapped = sorted(set(wrap(dom_use, args.wrap_factor)))
    wiki = fetch_wiki(args.wiki_rows, args.wiki_cache)

    # Tỉ lệ 1 : 1 theo SỐ CÂU giữa domain và phổ thông. Lý do: domain là thứ ta muốn LM thuộc, phổ thông là
    # thứ giữ nó không sập. Lệch hẳn về domain ⇒ LM ép mọi thứ thành lệnh (rủi ro §4.6 "LM lấn át âm thanh").
    n_gen = min(len(wiki), max(len(wrapped), 1))
    body = wrapped + wiki[:n_gen]

    dev_n = max(200, len(body) // 50)
    dev = [s for s in body if stable_bucket("DEV|" + s) < dev_n / max(len(body), 1)]
    dev_set = set(dev)
    train = [s for s in body if s not in dev_set]

    def dump(name: str, rows: list[str]):
        p = os.path.join(args.out_dir, name)
        with open(p, "w", encoding="utf-8") as f:
            f.write("\n".join(rows) + "\n")
        return p

    dump("lm-train.txt", train)
    dump("lm-dev.txt", dev)
    dump("domain-holdout.txt", holdout)
    dump("domain-used.txt", dom_use)

    print(f"== câu domain gốc  : {len(dom_all)}")
    print(f"== giữ lại (holdout): {len(holdout)}  ({100*len(holdout)/max(len(dom_all),1):.1f} %)")
    print(f"== domain dùng cho LM: {len(dom_use)} → bọc ngữ đệm thành {len(wrapped)}")
    print(f"== wiki vi          : {len(wiki)} câu (dùng {n_gen})")
    print(f"== lm-train.txt     : {len(train)}   lm-dev.txt: {len(dev)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
