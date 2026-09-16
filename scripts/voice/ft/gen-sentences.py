#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Nở câu cho fine-tune: lệnh + biến thể phát âm vùng miền + câu KHÔNG phải lệnh (spec P2.1).

Ba thứ tệp này thêm vào so với `gen-variants.py`, mỗi thứ vì một lý do ĐÃ ĐO đêm 2026-09-16:

1. **Biến thể chính tả theo cách phát âm** — bộ giữ lại giọng thật cho thấy mô hình xuất ra
   *"kiếng"* khi người nói *"kính"*, *"cấp"* khi người nói *"cốp"*, *"sửi"* khi người nói *"sưởi"*.
   Nếu dữ liệu huấn luyện chỉ có một cách viết, mô hình bị phạt vì đã nghe **đúng âm**. Nên ta sinh
   **cả hai** cách viết cho cùng một câu nói: cái gì người Việt thật sự nói ra thì đưa vào.
   ⚠ Đây là biến thể ở **mức chữ**, không phải giả giọng ở mức âm — và đó là có chủ ý: [ĐO] giọng Nam
   THẬT đạt 96.7 % (nam.A) với mô hình đang ship, trong khi giọng Nam **giả bằng TTS** chỉ 26.7 %.
   ⇒ chỗ thủng nằm ở **chính tả/từ vựng**, không nằm ở âm sắc vùng miền.

2. **Câu KHÔNG phải lệnh** — 100 % dữ liệu là lệnh thì mô hình học cách *ép* mọi âm thanh thành lệnh.
   Đúng bệnh *"ừ bật đèn đọc"* / *"đang đọc sách"* trong log xe (`voice-stream-eval-2026-09-16.md` §6).

3. **Tên app đọc theo âm Việt** — *"quây"*/*"quay"* cho Waze, *"gu gồ máp"*, *"du túp"*. Mô hình nên
   được phép xuất ra đúng cái người ta nói; việc quy về mã là của `VoiceSynonyms`/`VoiceIntentParser`.

Chia train/dev **theo CÂU** (băm ổn định), giữ nguyên 20 % holdout của `lm-text.py` để hai pha dùng chung
một đường phân chia — nếu không thì câu LM đã thấy lại lọt vào dev của fine-tune và mọi số đều lệch.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(REPO, "scripts", "voice"))

from vi_text import norm as vi_norm  # noqa: E402

# Cặp (chuẩn → cách nói/ghi khác) QUAN SÁT ĐƯỢC trên bộ giữ lại 3 người, 2026-09-16.
# Chỉ đưa vào cặp mà người Việt THẬT SỰ nói/viết như vậy — không bịa lỗi chính tả ngẫu nhiên.
SPOKEN_VARIANTS: list[tuple[str, list[str]]] = [
    ("kính", ["kiếng"]),                 # miền Nam: cửa kiếng
    ("cốp", ["cốp xe", "cóp"]),
    ("sưởi", ["sấy"]),
    ("điều hoà", ["máy lạnh", "điều hòa"]),
    ("cốp sau", ["cửa hậu", "cốp phía sau"]),
    ("giùm", ["dùm", "giúp"]),
    ("bật", ["mở"]),                     # miền Nam dùng "mở" cho cả bật
    ("tắt", ["đóng"]),
    ("âm lượng", ["volume", "tiếng"]),
    ("pin", ["bình", "bình điện"]),
    ("đèn đọc", ["đèn đọc sách"]),
    ("chế độ lái", ["chế độ"]),
    ("áp suất lốp", ["áp suất bánh xe", "hơi lốp"]),
]

# Tên app đọc theo âm Việt — người lái nói thế nào thì mô hình nên được phép xuất ra thế ấy.
APP_SPOKEN: dict[str, list[str]] = {
    "youtube": ["du túp", "diu túp", "iu túp"],
    "google map": ["gu gồ máp", "gu gô map", "google maps"],
    "waze": ["quây", "quay", "guê"],
    "zalo": ["gia lô", "da lô"],
    "carplay": ["ca plây", "ca play"],
    "spotify": ["pót ti phai", "spo ti phai"],
    "vietmap": ["việt máp", "việt map"],
    "netflix": ["nét phờ lích"],
}

# Câu KHÔNG phải lệnh — nói chuyện trong xe. Mô hình phải được phép nghe ra CHÍNH NÓ, không ép thành lệnh.
CHATTER = [
    "hôm nay trời đẹp quá", "chiều nay mình ăn gì", "đường này kẹt xe quá",
    "mai con đi học lúc mấy giờ", "nhớ gọi cho anh A nhé", "cái này bao nhiêu tiền vậy",
    "đợi tí nữa rồi đi", "sao hôm nay mệt thế", "để anh nghe điện thoại đã",
    "mình tới nơi chưa em", "nay kẹt xe kinh khủng", "bên kia có chỗ đậu xe kìa",
    "trưa nay ăn cơm ở đâu", "con ngủ đi cho khoẻ", "bài hát này hay quá",
    "anh mới đi công tác về", "chắc khoảng mười lăm phút nữa", "để mai tính tiếp",
    "trời sắp mưa rồi đó", "xe phía trước chạy chậm quá", "đi lối này gần hơn",
    "thôi khỏi cần đâu", "em thấy sao cũng được", "lát nữa ghé siêu thị nhé",
    "hôm qua xem phim gì", "ừ đúng rồi đó", "à quên mất", "không phải cái đó",
    "để anh suy nghĩ đã", "nghe nói mai trời lạnh",
]


def stable_bucket(text: str, seed: str = "kachi-voice-ft-2026-09-16") -> float:
    """Cùng hàm băm với `lm-text.py` ⇒ hai pha chia câu y hệt nhau, không rò rỉ chéo."""
    return int(hashlib.sha256((seed + "|" + text).encode("utf-8")).hexdigest()[:8], 16) / 0xFFFFFFFF


def read_variants(path: str) -> list[tuple[str, str, str, str, str]]:
    out = []
    for line in open(path, encoding="utf-8"):
        if line.startswith("#"):
            continue
        p = line.rstrip("\n").split("\t")
        if len(p) >= 5 and p[4].strip():
            out.append((p[0], p[1], p[2], p[3], p[4].strip()))
    return out


def variants_of(text: str, cap: int) -> list[str]:
    """Sinh biến thể chính tả. Áp lần lượt từng cặp, dừng ở `cap` để không nở bùng nổ."""
    out = [text]
    seen = {text}
    for base, alts in SPOKEN_VARIANTS:
        if base not in text:
            continue
        for alt in alts:
            t = text.replace(base, alt)
            if t not in seen:
                seen.add(t)
                out.append(t)
            if len(out) >= cap:
                return out
    for app, alts in APP_SPOKEN.items():
        if app not in text.lower():
            continue
        for alt in alts:
            t = re.sub(re.escape(app), alt, text, flags=re.IGNORECASE)
            if t not in seen:
                seen.add(t)
                out.append(t)
            if len(out) >= cap:
                return out
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--variants", required=True)
    ap.add_argument("--out", required=True, help="TSV: id | kind | region | style | text | split")
    ap.add_argument("--cap", type=int, default=4, help="tối đa bao nhiêu biến thể cho 1 câu")
    ap.add_argument("--chatter-repeat", type=int, default=12,
                    help="mỗi câu tán gẫu lặp mấy lần (để chiếm ~5 %% dữ liệu)")
    ap.add_argument("--holdout-frac", type=float, default=0.20)
    args = ap.parse_args()

    rows: list[tuple[str, str, str, str, str]] = []
    for rid, kind, region, style, text in read_variants(args.variants):
        for k, t in enumerate(variants_of(text, args.cap)):
            rows.append((rid, kind, region, "goc" if k == 0 else f"{style}_bienthe", vi_norm(t)))
    for i, c in enumerate(CHATTER):
        for r in range(args.chatter_repeat):
            # id riêng cho từng lần lặp: khử trùng theo (id, text) nên trùng id sẽ nuốt mất bản lặp,
            # mà câu tán gẫu cần CHIẾM TỈ TRỌNG chứ không chỉ cần có mặt.
            rows.append((f"chatter{i:02d}_{r}", "non_command", "chung", "tan_gau", vi_norm(c)))

    # loại trùng theo (id, text) — biến thể có thể trùng nhau khi hai câu gốc khác nhau quy về một
    seen = set()
    uniq = []
    for r in rows:
        k = (r[0], r[4])
        if k in seen:
            continue
        seen.add(k)
        uniq.append(r)

    n_tr = n_dv = n_ho = 0
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("# id\tkind\tregion\tstyle\ttext\tsplit   (sinh bằng scripts/voice/ft/gen-sentences.py)\n")
        for rid, kind, region, style, text in uniq:
            b = stable_bucket(text)
            if b < args.holdout_frac:
                split = "holdout"       # chưa bao giờ vào huấn luyện — dùng đo khái quát hoá
                n_ho += 1
            elif b < args.holdout_frac + 0.05:
                split = "dev"
                n_dv += 1
            else:
                split = "train"
                n_tr += 1
            f.write(f"{rid}\t{kind}\t{region}\t{style}\t{text}\t{split}\n")

    print(f"== {len(uniq)} câu duy nhất  (train {n_tr} · dev {n_dv} · holdout {n_ho})")
    print(f"== trong đó tán gẫu (không phải lệnh): "
          f"{sum(1 for r in uniq if r[1]=='non_command')} "
          f"({100*sum(1 for r in uniq if r[1]=='non_command')/len(uniq):.1f} %)")
    print(f"== ghi {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
