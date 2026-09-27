# -*- coding: utf-8 -*-
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

# Bề mặt công khai của gói = đúng các tên mà `gen-car.py` (điểm vào) và `render-car.py` (`gc.<tên>`) dùng.
from .paths import (ROOT, SRC_DIR, RES_DIR, KT_PATH, MANIFEST_PATH, FACES, SVG_NS,  # noqa: F401
                    ALPHA_CTX, ALPHA_SUB, STROKE_MAIN, STROKE_SUB, MAX_ICON_PATHS, MAX_FACE_PATHS)
from .pathdata import fmt, parse_d, serialize, rect_cmds, ellipse_cmds, flatten, bounds_of  # noqa: F401
from .model import Gradient, Piece, Face, load_face  # noqa: F401
from .icons import TOP_SUB, TOP_WS, FRONT_SUB, REAR_SUB, ICONS, FACE_VD, gen_icon  # noqa: F401
from .faces import gen_face_vd, gen_kotlin  # noqa: F401
from .paint import contrast, gen_paint  # noqa: F401
from .cli import dests, generate, main  # noqa: F401
