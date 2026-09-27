# -*- coding: utf-8 -*-
# Tách THUẦN từ scripts/design/gen-car.py (882 dòng → trần 500, L6-debt 2026-09-27). Thân hàm giữ nguyên byte;
# `gen-car.py` chỉ còn là điểm vào CLI + re-export cho render-car.py. `--check` so byte đầu ra: khớp 41/41 sau khi tách.
"""Màu sơn & tương phản (§4.8 bảng 5 màu · AC8.5) — điền `contrast`/`forceOutline` vào paint.json."""
from __future__ import annotations

import json

# ── Màu sơn & tương phản (§4.8 bảng 5 màu · AC8.5) ───────────────────────────────────────────────────────────

def _lum(hex_color: str) -> float:
    c = hex_color.lstrip("#")
    r, g, b = (int(c[i:i + 2], 16) / 255 for i in (0, 2, 4))

    def lin(v):
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4

    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def contrast(a: str, b: str) -> float:
    la, lb = _lum(a), _lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return round((hi + 0.05) / (lo + 0.05), 2)


def gen_paint(src_path: str) -> str:
    with open(src_path, encoding="utf-8") as f:
        data = json.load(f)
    floor = float(data.get("outlineFloor", 3.0))
    bgs = data["backgrounds"]
    for p in data["paints"]:
        top, bottom = p["from"], p["to"]
        c = {}
        for k, bg in bgs.items():
            t, b = contrast(top, bg), contrast(bottom, bg)
            c[k] = {"top": t, "bottom": b, "worst": min(t, b)}
        p["contrast"] = c
        # AC3.3: đo CẢ HAI ĐẦU gradient — đầu nào chạm nền là chiếc xe mất mép ở đầu đó (đáy trên nền tối, đỉnh trên nền sáng)
        p["forceOutline"] = {k: v["worst"] < floor for k, v in c.items()}
    data["_generated"] = ("contrast · forceOutline do scripts/design/gen-car.py điền (WCAG 2.x, cả hai đầu gradient vs nền; "
                          "worst < outlineFloor ⇒ app tự bật viền partLine); sửa màu rồi sinh lại")
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"
