#!/usr/bin/env python3
"""
gen-car.py — MỘT CHIẾC XE, BA MẶT, HAI ĐÍCH (spec docs/specs/kachi-visual-refresh.html §4.3 · §4.6 · R1 · T5/T6).

Nguồn:  design/car/top.svg · front.svg · side.svg  (mỗi bộ phận = một phần tử có id, hệ 24×24)
        design/car/paint.json                        (5 màu sơn — script điền phần `contrast`)
Đích:   app/src/main/res/drawable/ic_car_*.xml                  (43 icon 24dp, một tông, tint được — theo bảng ICONS dưới)
        app/src/main/res/drawable/car_face_*.xml                (4 mặt 48dp có chuyển sắc <aapt:attr> — mức tả thực (1))
        app/src/main/java/.../launcher/CarFramesGenerated.kt    (hằng path + hộp bao, SINH chứ không gõ tay)
        design/car/manifest.json                                (bộ phận · số path · hộp bao · thành phần icon)

Chỉ dùng thư viện chuẩn. Kết quả TẤT ĐỊNH (làm tròn 2 chữ số, bỏ số 0 thừa, thứ tự theo tài liệu).
    python3 scripts/design/gen-car.py            # sinh
    python3 scripts/design/gen-car.py --check    # sinh vào thư mục tạm rồi so BYTE với res/drawable + CarFramesGenerated.kt + manifest — lệch ⇒ exit 1

Luật đường ống (spec §4.6): KHÔNG công cụ nào khác được ghi vào đích; vá tay một tệp đã sinh = vi phạm, `--check` bắt.
"""
from __future__ import annotations

import os
import sys

# Thân script sống trong gói `gencar/` cạnh tệp này (tách THUẦN theo trần 500 dòng, L6-debt 2026-09-27; `--check` khớp
# 41/41 byte sau khi tách). Điểm vào CLI ở lại đây để lệnh `python3 scripts/design/gen-car.py [--check]` không đổi, và
# `render-car.py` (nạp tệp này bằng importlib rồi dùng `gc.ICONS` · `gc.FACE_VD` · `gc.load_face` · `gc.flatten` …)
# vẫn thấy đủ tên nhờ re-export bên dưới.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gencar import *  # noqa: E402,F401,F403 — bề mặt công khai khai ở gencar/__init__.py
from gencar.cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
