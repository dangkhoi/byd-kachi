#!/usr/bin/env python3
"""Phá thử review lượt 5: mỗi đột biến sửa mã -> chạy bài liên quan -> phải ĐỎ -> khôi phục.

Chạy: REPO=<gốc repo> python3 mut_review5.py  (ghi ../break-tests-review5.log). Cùng khuôn mut_review4.py.
"""
import glob, os, re, subprocess

REPO = os.environ["REPO"]
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "break-tests-review5.log")
CORE = [":core:test", "--tests", "*SlotReturnTest"]
SR = "core/src/main/kotlin/com/byd/clusternav/launcher/behind/SlotReturn.kt"
M = [
    ("R5-1 bỏ cổng màn-nhà-ở-đỉnh: K8 cả khi camera ở trên", SR,
     "if (where == SlotReturn.Where.HIDDEN_MAIN && BehindHomePlan.homeOnTop(last, homeComps)) {",
     "if (where == SlotReturn.Where.HIDDEN_MAIN) {"),
    ("R5-2 rời ô sang display 0 mà không ở trước: đo lại ô (ô đen) thay vì coi như toàn màn", SR,
     "        if (last.any { it.taskId == t.taskId && it.displayId == BehindHomePlan.MAIN_DISPLAY }) {\n",
     "        if (false) {\n"),
    ("R5-3 coi như toàn màn cả khi app sang display KHÁC (không phải 0)", SR,
     "        if (last.any { it.taskId == t.taskId && it.displayId == BehindHomePlan.MAIN_DISPLAY }) {\n",
     "        if (last.any { it.taskId == t.taskId && it.displayId != vd }) {\n"),
]
GRADLE = ["./gradlew", "--continue", "-q"]
XML = "core/build/test-results/test/*.xml"

with open(OUT, "w") as log:
    log.write("# Phá thử review lượt 5 (02/10) — công cụ finish/tools/mut_review5.py, REPO=<gốc repo>. "
              "Mỗi đột biến: sửa mã → chạy SlotReturnTest → phải ĐỎ → khôi phục.\n")
    for name, path, old, new in M:
        p = os.path.join(REPO, path)
        src = open(p).read()
        if src.count(old) != 1:
            log.write(f"{name}: KHÔNG ÁP ĐƯỢC (mốc xuất hiện {src.count(old)} lần)\n"); log.flush(); continue
        open(p, "w").write(src.replace(old, new))
        try:
            r = subprocess.run(GRADLE + CORE, cwd=REPO, capture_output=True, text=True)
            log.write(f"{name}: {'ĐỎ' if r.returncode != 0 else 'XANH (LỌT!)'} exit={r.returncode}\n")
            for x in glob.glob(os.path.join(REPO, XML)):
                for m in re.finditer(r'<testcase name="([^"]*)" classname="([^"]*)"[^>]*>\s*<failure', open(x).read()):
                    log.write(f"    test ĐỎ: {m.group(2).split('.')[-1]} > {m.group(1)}\n")
        finally:
            open(p, "w").write(src)
        log.flush()
    r = subprocess.run(GRADLE + CORE, cwd=REPO, capture_output=True, text=True)
    log.write(f"KHÔI PHỤC: {'XANH' if r.returncode == 0 else 'ĐỎ'} exit={r.returncode}\n")
