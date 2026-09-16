#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bảng cặp NGHE NHẦM — **sinh bằng máy** từ `VoicePhoneticConfusions.kt`, KHÔNG sửa tay.

Nguồn sự thật là bản Kotlin (`:core`), vì đó là thứ CHẠY TRÊN XE. Bản Python này chỉ tồn tại để bộ đo
off-car (`scripts/voice/ft/eval-lm.py`) chấm **cùng một chuẩn** với bộ phân tích thật — nếu hai bên
lệch nhau thì số của host không ghép được vào số của xe, và cả hai đều vô nghĩa.

Sinh lại:  /tmp/sherpa-venv/bin/python scripts/voice/ft/gen-phonetic-pairs.py
"""
from __future__ import annotations

# 30 cặp, trích từ core/src/main/kotlin/com/byd/clusternav/launcher/voice/VoicePhoneticConfusions.kt.
# (chuẩn, nghe_thành, mức_bằng_chứng: REC = bản thu giọng thật · HOST = đo trên host)
OBSERVED: list[tuple[str, str, str]] = [
    ('cốp', 'cấp', 'REC'),
    ('cốp', 'góc', 'REC'),
    ('đọc', 'độc', 'REC'),
    ('pin', 'bên', 'REC'),
    ('pin', 'biên', 'REC'),
    ('pin', 'bin', 'REC'),
    ('pin', 'phim', 'REC'),
    ('pin', 'tin', 'HOST'),
    ('dừng', 'dần', 'REC'),
    ('dừng', 'rừng', 'HOST'),
    ('quây', 'quay', 'REC'),
    ('kính', 'kiếng', 'HOST'),
    ('kính', 'kiêng', 'REC'),
    ('kính', 'kim', 'REC'),
    ('kính', 'kín', 'HOST'),
    ('sưởi', 'sửi', 'HOST'),
    ('sưởi', 'sữa', 'HOST'),
    ('nhiệt', 'mật', 'REC'),
    ('mở', 'ở', 'REC'),
    ('mở', 'mã', 'REC'),
    ('mở', 'mỡ', 'REC'),
    ('hết', 'máy', 'REC'),
    ('đèn', 'đang', 'HOST'),
    ('bật', 'bằng', 'REC'),
    ('xem', 'xe', 'REC'),
    ('xem', 'sẽ', 'REC'),
    ('hạ', 'hạt', 'REC'),
    ('lái', 'gái', 'REC'),
    ('lái', 'đá', 'REC'),
    ('giảm', 'mầm', 'REC'),
]

#: tra ngược "nghe thành" → "chuẩn". Dùng để chuẩn hoá bản NGHE ĐƯỢC trước khi phân tích
#: ý định, tức mô phỏng việc `VoicePhoneticMatch` tha cho đúng những cặp đã đo.
HEARD_TO_REF: dict[str, str] = {h: r for r, h, _ in OBSERVED}
