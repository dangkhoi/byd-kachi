#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sinh CORPUS CÂU MIX `scripts/voice/data/mix.tsv` — nhiều lệnh trong MỘT câu, có/không liên từ.

## Vì sao (owner 2026-09-22)
*"thêm vài ngàn case mix nữa: hạ kính lấy gió ngoài tắt máy lạnh"* — người ta nói nhiều việc liền một hơi,
thường KHÔNG có "và/rồi". Golden dataset đơn-lệnh không đo được đường này; đây là tệp bù.

## Cách sinh
Ghép 2–4 CLAUSE (mỗi clause = một câu lệnh đơn + id của nó) bằng:
 • liên từ "và"/"rồi"/", " HOẶC nối trần (dấu cách) — người Việt nói cả hai;
 • giữ thứ tự, khử trùng, deterministic (chạy lại ra y hệt).
Cột ra: `ids(phẩy) | text`. `ids` = danh sách id mong đợi theo đúng thứ tự.

Clause lấy từ một bảng NGƯỜI soạn (dưới đây) — cùng tinh thần `templates.tsv`: máy không biết cách người ta
nói tự nhiên, nên clause do người khai, máy chỉ tổ hợp.

Dùng: python3 scripts/voice/gen-mix.py [--out …] [--max N]
"""
from __future__ import annotations

import argparse
import itertools
import os
import random

# ── CLAUSE đơn: (id, câu nói) — mỗi id vài cách nói tự nhiên (đúng + phương ngữ) ──────────────────
CLAUSES: list[tuple[str, str]] = [
    ("win_lf", "hạ kính lái"), ("win_lf", "mở kính trước trái"), ("win_lf", "hạ kính trước trái"),
    ("win_lf", "hạ kiếng trái"), ("win_lf", "mở kính bên lái"),
    ("win_rf", "mở kính trước phải"), ("win_rf", "hạ kính phụ"), ("win_rf", "hạ kiếng phải"),
    ("win_lr", "mở kính sau trái"), ("win_rr", "mở kính sau phải"),
    ("windows_all", "đóng hết kính"), ("windows_all", "mở tất cả kính"), ("windows_all", "hạ hết kính"),
    ("ac_auto", "tắt máy lạnh"), ("ac_auto", "tắt điều hoà"), ("ac_auto", "bật điều hoà"),
    ("ac_auto", "bật máy lạnh"), ("ac_auto", "tắt máy nạnh"),
    ("recirc", "lấy gió ngoài"), ("recirc", "lấy gió trong"), ("recirc", "chuyển gió ngoài"),
    ("readl", "bật đèn đọc"), ("readl", "tắt đèn đọc"), ("readl", "bật đèn trần"),
    ("pm25", "bật lọc bụi"), ("pm25", "tắt lọc bụi"), ("pm25", "bật lọc không khí"),
    ("defrost", "bật sấy kính"), ("defrost", "tắt sấy kính"), ("defrost", "bật sấy kính trước"),
    ("defrost_rear", "bật sấy kính sau"),
    ("trunk", "mở cốp"), ("trunk", "đóng cốp"), ("trunk", "mở cốp sau"),
    ("sunroof", "mở cửa sổ trời"), ("sunroof", "đóng cửa sổ trời"), ("sunroof", "mở nóc xe"),
    ("cam", "bật camera 360"), ("cam", "mở camera"),
    ("temp", "đặt nhiệt độ 24 độ"), ("temp", "chỉnh nhiệt độ 22 độ"),
    ("fan", "tăng gió"), ("fan", "giảm gió"),
    ("headl", "bật đèn pha"), ("headl", "tắt đèn pha"),
    ("drl", "bật đèn ban ngày"),
    ("win_half_lf", "mở nửa kính lái"),
]

# ── Cách NỐI hai clause: (chuỗi-chèn, tên) — nối trần (dấu cách) là ca owner nhắm ─────────────────
JOINERS = [" ", " ", " và ", " rồi ", ", ", " xong ", " sau đó "]


def build(max_n: int, seed: int = 7) -> list[tuple[str, str]]:
    rng = random.Random(seed)
    out: list[tuple[str, str]] = []
    seen: set[str] = set()
    # 2-lệnh: mọi cặp id KHÁC nhau (giới hạn); 3-4 lệnh: tổ hợp ngẫu nhiên có kiểm.
    by_id: dict[str, list[str]] = {}
    for i, txt in CLAUSES:
        by_id.setdefault(i, []).append(txt)
    ids = list(by_id.keys())

    def emit(chosen: list[tuple[str, str]]):
        # chosen = list[(id, text)]; nối bằng joiner luân phiên/ngẫu nhiên.
        idlist = ",".join(c[0] for c in chosen)
        parts = [c[1] for c in chosen]
        text = parts[0]
        for p in parts[1:]:
            text += rng.choice(JOINERS) + p
        text = " ".join(text.split())  # chuẩn hoá khoảng trắng
        key = text.lower()
        if key in seen:
            return
        seen.add(key)
        out.append((idlist, text))

    # 2-lệnh
    for a, b in itertools.combinations(ids, 2):
        if len(out) >= max_n * 0.55:
            break
        emit([(a, rng.choice(by_id[a])), (b, rng.choice(by_id[b]))])
    # 3-lệnh
    while len(out) < max_n * 0.85:
        tri = rng.sample(ids, 3)
        emit([(i, rng.choice(by_id[i])) for i in tri])
        if len(out) > 100000:
            break
    # 4-lệnh
    while len(out) < max_n:
        quad = rng.sample(ids, 4)
        emit([(i, rng.choice(by_id[i])) for i in quad])
        if len(out) > 200000:
            break
    return out[:max_n]


def main() -> None:
    ap = argparse.ArgumentParser()
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    ap.add_argument("--out", default=os.path.join(root, "scripts/voice/data/mix.tsv"))
    ap.add_argument("--max", type=int, default=3000)
    a = ap.parse_args()
    rows = build(a.max)
    with open(a.out, "w", encoding="utf-8") as f:
        f.write("# CORPUS CÂU MIX — sinh bằng scripts/voice/gen-mix.py (đừng sửa tay: sửa CLAUSES trong script).\n")
        f.write("# Cột: ids(phẩy, đúng thứ tự) | text\n")
        for idlist, text in rows:
            f.write(f"{idlist}\t{text}\n")
    print(f"mix.tsv: {len(rows)} câu")


if __name__ == "__main__":
    main()
