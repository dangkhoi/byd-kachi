#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Đề xuất **thêm** dòng hotword (HOA có dấu, ≥ 2 từ, không đụng tiền tố) từ kết quả đo của `mishear-table.py`.

## Vì sao không đổ hết cách nói mới vào tệp hotword
Ba luật đã có phép đo đứng sau (KDoc `SherpaPhraseHotwords` · `SherpaHotwords.dropPrefixes`):
 1. **chỉ cụm ≥ 2 từ** — thêm 39 động từ rời vào tệp toàn cụm kéo 21/25 xuống 17/25;
 2. **không dòng nào là tiền tố theo từ của dòng khác** — `CHẾ ĐỘ LÁI` nuốt mất `CHẾ ĐỘ LÁI THỂ THAO`;
 3. cụm nào **bộ phân tích không hiểu** thì đừng bias — bias đúng một chuỗi rác là kéo câu về chỗ rác.
Tệp này tự canh (1) và (2) **bằng máy**, và chỉ lấy cách nói đã khai trong `data/nouns.tsv` / `data/apps.tsv`
(tức cách nói mà pha sửa từ vựng sẽ đưa vào `VoiceSynonyms`) ⇒ (3) không bị vi phạm sau khi hotfix.

## Lọc theo SỐ ĐO, không theo cảm giác
Chỉ đề xuất cho **mã đã đo thấy sai** trong `--dump` của `mishear-table.py`. Mã nào corpus nghe đúng 100 % thì
thêm dòng vào tệp hotword là **rủi ro thuần**: to thêm tệp, chậm thêm `createStream`, không đổi gì tốt lên.

Dùng:
  python3 scripts/voice/propose-hotwords.py --dump <tsv> \
      --existing core/build/hotwords/hotwords-phrases.txt --out scripts/voice/data/hotwords-proposed.txt
"""
from __future__ import annotations

import argparse
import os
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
REPO = os.path.dirname(os.path.dirname(HERE))

sys.path.insert(0, HERE)
from vi_text import canon_tone, norm  # noqa: E402  — cùng luật chuẩn hoá với `mishear-table.py`

# Động từ ghép trước cách gọi, theo loại nút — cùng bảng `SherpaPhraseHotwords.CONTROL_VERBS`.
KIND_VERBS = {
    "control_toggle": ["BẬT", "TẮT", "MỞ", "ĐÓNG"],
    "control_cover": ["MỞ", "ĐÓNG", "BẬT", "TẮT"],
    "control_step": ["TĂNG", "GIẢM", "ĐẶT"],
    "control_button": ["BẬT", "MỞ"],
    "control_select": ["ĐỔI", "ĐẶT"],
    "telemetry_read": ["XEM", "ĐỌC", "KIỂM TRA"],
    "macro": [],
    "launcher": ["MỞ"],
    "app": ["MỞ", "BẬT"],
}
VERB_HEADS = {"BẬT", "TẮT", "MỞ", "ĐÓNG", "TĂNG", "GIẢM", "ĐẶT", "CHỈNH", "XEM", "ĐỌC", "HẠ", "KÉO"}


def normalize(raw: str) -> str | None:
    """Cùng luật `SherpaHotwords.normalize`: giữ chữ cái, bỏ **token** có chữ số, HOA, tối thiểu 2 ký tự."""
    raw = canon_tone(raw)   # tệp hotword phải viết ĐÚNG kiểu đặt dấu của `tokens.txt` (xem `vi_text`)
    words, word, has_digit = [], [], False
    def flush():
        nonlocal word, has_digit
        if not has_digit and word:
            words.append("".join(word))
        word, has_digit = [], False
    for c in unicodedata.normalize("NFC", raw):
        if c.isalpha():
            word.append(c)
        elif c.isdigit():
            has_digit = True
        else:
            flush()
    flush()
    s = " ".join(words)
    return s.upper() if len(s) >= 2 else None


def is_phrase(h: str) -> bool:
    return " " in h


def prefix_clash(cand: str, pool: set[str]) -> str | None:
    """Trả về dòng đụng tiền tố (theo TỪ) nếu có — cả hai chiều, vì `dropPrefixes` bỏ dòng NGẮN."""
    for line in pool:
        if line != cand and (line.startswith(cand + " ") or cand.startswith(line + " ")):
            return line
    return None


def rows(path: str, ncol: int):
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if line.strip() and not line.lstrip().startswith("#"):
            p = line.split("\t")
            if len(p) == ncol:
                yield [x.strip() for x in p]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dump", required=True, help="TSV do `mishear-table.py --dump` ghi ra")
    ap.add_argument("--existing", default=os.path.join(REPO, "core/build/hotwords/hotwords-phrases.txt"))
    ap.add_argument("--variants", default=os.path.join(DATA, "variants.tsv"))
    ap.add_argument("--out", default=os.path.join(DATA, "hotwords-proposed.txt"))
    a = ap.parse_args()

    # So với tệp ship ở dạng ĐÃ quy chuẩn dấu: hai bản *"KHOÁ XE"* / *"KHÓA XE"* là **một** dòng, không phải
    # hai. Đề xuất ở đây đi kèm bản vá đặt-dấu cho chính tệp ship (xem §8 của doc `voice-mishear-…`), nên nếu
    # coi chúng là hai thì mọi dòng có `hoà`/`khoá`/`khoẻ` sẽ bị đề xuất lại một lần nữa — rác thuần.
    existing = {canon_tone(l.rstrip("\n")) for l in open(a.existing, encoding="utf-8") if l.strip()}

    # Mã nào ĐÃ ĐO thấy sai (ít nhất một WAV nghe khác câu gốc).
    kind_of = {r[0]: r[1] for r in rows(a.variants, 5)}
    bad: set[str] = set()
    for uid, _voice, _rate, ref, hyp in rows(a.dump, 5):
        rid = uid.rsplit("-", 1)[0]
        if norm(ref) != norm(hyp):
            bad.add(rid)

    # Ứng viên = cách gọi do người khai (nouns.tsv · apps.tsv), cho riêng những mã đã đo thấy sai.
    cand: list[tuple[str, str]] = []          # (hotword, nguồn)
    def offer(rid: str, phrase: str):
        if rid not in bad:
            return
        base = normalize(phrase)
        if not base:
            return
        pool = [base] + [f"{v} {base}" for v in KIND_VERBS.get(kind_of.get(rid, ""), [])
                         if base.split(" ", 1)[0] not in VERB_HEADS]
        for h in pool:
            if is_phrase(h):
                cand.append((h, rid))

    for rid, _region, noun in rows(os.path.join(DATA, "nouns.tsv"), 3):
        if noun != "@only":
            offer(rid, noun)
    for _key, _region, _style, name in rows(os.path.join(DATA, "apps.tsv"), 4):
        offer("open_app", name)

    # ── Luật thứ ba, sinh ra từ MỘT phép đo ([ĐO] 2026-09-16, 25 WAV) ───────────────────────────
    # Thêm đúng một dòng `YOUTUBE MUSIC` làm w10 (*"đưa YouTube vào ô số hai"*) tụt xuống *"ĐƯA YOUTUBE"* —
    # mất sạch phần đuôi. Gỡ riêng dòng đó là w10 xanh lại (2206 dòng ⇒ 21/25, đúng bằng mốc).
    # Cơ chế: sau khi giải mã hết `YOUTUBE`, đồ thị ngữ cảnh đang đứng GIỮA cụm `YOUTUBE MUSIC`, nên nó cộng
    # điểm cho đường đi tiếp bằng `MUSIC` và **trừ** (backoff) mọi đường khác — kể cả `VÀO Ô SỐ HAI`. Đây là
    # mặt sau của luật tiền tố ở `dropPrefixes`: ở đó cụm NGẮN nuốt cụm DÀI, ở đây cụm DÀI nuốt phần đuôi của
    # một câu khác. Luật mechanically: **cụm bắt đầu bằng một tên app đứng-một-mình thì bỏ** — tên app hay là
    # chỗ KẾT của câu (*"mở YouTube"* · *"đưa YouTube vào ô 2"*), nên đặt nó làm đầu một cụm là mời lỗi này.
    app_solo = {normalize(n) for _k, _r, _s, n in rows(os.path.join(DATA, "apps.tsv"), 4)
                if normalize(n) and " " not in normalize(n)}

    kept: list[str] = []
    seen: set[str] = set()
    skipped: list[tuple[str, str, str]] = []
    pool = set(existing)
    for h, rid in cand:
        if h in seen:
            continue
        seen.add(h)
        if h in existing:
            skipped.append((h, rid, "đã có trong tệp ship"))
            continue
        if h.split(" ", 1)[0] in app_solo:
            skipped.append((h, rid, "bắt đầu bằng tên app đứng-một-mình (bẫy nuốt đuôi câu)"))
            continue
        clash = prefix_clash(h, pool | set(kept))
        if clash:
            skipped.append((h, rid, f"đụng tiền tố với {clash!r}"))
            continue
        kept.append(h)
        pool.add(h)

    with open(a.out, "w", encoding="utf-8") as f:
        f.write("\n".join(kept) + ("\n" if kept else ""))
    print(f"== {len(kept)} dòng đề xuất ⇒ {a.out}")
    print(f"== bỏ {len(skipped)} ứng viên ({sum(1 for s in skipped if 'tiền tố' in s[2])} vì tiền tố, "
          f"{sum(1 for s in skipped if 'đã có' in s[2])} vì trùng tệp ship)")
    print(f"== {len(bad)} mã có ít nhất một WAV nghe sai (nguồn lọc)")
    for h, rid, why in skipped[:10]:
        print(f"   - {h!r} ({rid}): {why}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
