# -*- coding: utf-8 -*-
# Tách THUẦN từ scripts/design/gen-car.py (882 dòng → trần 500, L6-debt 2026-09-27). Thân hàm giữ nguyên byte;
# `gen-car.py` chỉ còn là điểm vào CLI + re-export cho render-car.py. `--check` so byte đầu ra: khớp 41/41 sau khi tách.
"""Đường dẫn + ngữ pháp icon (hằng) — hệ toạ độ 24×24, ba đích thật."""
from __future__ import annotations

import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))  # gencar/ nằm sâu thêm một bậc so với gen-car.py
SRC_DIR = os.path.join(ROOT, "design", "car")
# Đích THẬT (spec §4.6 luật cứng: chỉ script này ghi hình xe vào ba nơi dưới; vá tay = `--check` đỏ)
RES_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable")                       # ic_car_*.xml · car_face_*.xml
KT_PATH = os.path.join(ROOT, "app", "src", "main", "java", "com", "byd", "clusternav", "launcher", "CarFramesGenerated.kt")
MANIFEST_PATH = os.path.join(SRC_DIR, "manifest.json")                                        # bảng mảnh/hộp bao (đọc được, không build)
FACES = ("top", "front", "side")
SVG_NS = "{http://www.w3.org/2000/svg}"

# ── Ngữ pháp icon (spec §4.2 · AC2.1 · AC2.2) ─────────────────────────────────────────────────────────────────
ALPHA_CTX = "0.16"   # lớp NỀN — thân xe tô mờ
ALPHA_SUB = "0.38"   # lớp PHỤ — kính, thấu kính chưa sáng, gờ ca-pô
STROKE_MAIN = "1.8"  # nét CHÍNH
STROKE_SUB = "1.2"   # nét PHỤ (viền thân, gờ)
MAX_ICON_PATHS = 6   # AC5.1 icon 24dp
MAX_FACE_PATHS = 28  # AC5.1 khung + mọi bộ phận của một mặt (không tính glyph, highlight)
