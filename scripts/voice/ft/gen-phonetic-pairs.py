#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Sinh `phonetic_pairs.py` từ `VoicePhoneticConfusions.kt` — một nguồn sự thật, hai ngôn ngữ.

## Vì sao sinh bằng máy chứ không chép tay

Bảng cặp nghe-nhầm tồn tại ở **hai** nơi: `:core` (Kotlin, chạy trên xe) và host (Python, chấm off-car).
Chép tay là chắc chắn lệch — và lúc lệch thì số của host không còn ghép được vào số của xe, hai bộ số
cùng sai mà không ai biết bên nào. Đúng họ lỗi mà CLAUDE.md §4.1 (DRY) cấm.

**Kotlin là nguồn**, Python là bản sinh ra. Sửa bảng ⇒ sửa bên Kotlin rồi chạy lại tệp này.
"""
from __future__ import annotations

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
SRC = os.path.join(REPO, "core/src/main/kotlin/com/byd/clusternav/launcher/voice",
                   "VoicePhoneticConfusions.kt")
DST = os.path.join(HERE, "phonetic_pairs.py")

HEAD = '''#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bảng cặp NGHE NHẦM — **sinh bằng máy** từ `VoicePhoneticConfusions.kt`, KHÔNG sửa tay.

Nguồn sự thật là bản Kotlin (`:core`), vì đó là thứ CHẠY TRÊN XE. Bản Python này chỉ tồn tại để bộ đo
off-car (`scripts/voice/ft/eval-lm.py`) chấm **cùng một chuẩn** với bộ phân tích thật — nếu hai bên
lệch nhau thì số của host không ghép được vào số của xe, và cả hai đều vô nghĩa.

Sinh lại:  /tmp/sherpa-venv/bin/python scripts/voice/ft/gen-phonetic-pairs.py
"""
from __future__ import annotations

'''


def main() -> int:
    if not os.path.isfile(SRC):
        sys.exit(f"không thấy {SRC} — bảng cặp do phiên voice-hotfix sở hữu; chờ tệp đó có mặt")
    src = open(SRC, encoding="utf-8").read()
    pairs = re.findall(r'Observed\("([^"]+)",\s*"([^"]+)",\s*Seen\.(\w+)', src)
    if not pairs:
        sys.exit("không trích được cặp nào — cấu trúc Kotlin đã đổi, sửa regex trước khi chạy tiếp")
    with open(DST, "w", encoding="utf-8") as f:
        f.write(HEAD)
        f.write(f"# {len(pairs)} cặp, trích từ {os.path.relpath(SRC, REPO)}.\n")
        f.write("# (chuẩn, nghe_thành, mức_bằng_chứng: REC = bản thu giọng thật · HOST = đo trên host)\n")
        f.write("OBSERVED: list[tuple[str, str, str]] = [\n")
        for r, h, s in pairs:
            f.write(f"    ({r!r}, {h!r}, {s!r}),\n")
        f.write("]\n\n")
        f.write('#: tra ngược "nghe thành" → "chuẩn". Dùng để chuẩn hoá bản NGHE ĐƯỢC trước khi phân tích\n')
        f.write("#: ý định, tức mô phỏng việc `VoicePhoneticMatch` tha cho đúng những cặp đã đo.\n")
        f.write("HEARD_TO_REF: dict[str, str] = {h: r for r, h, _ in OBSERVED}\n")
    print(f"== {len(pairs)} cặp → {os.path.relpath(DST, REPO)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
