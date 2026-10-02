#!/usr/bin/env python3
"""Phá thử review lượt 6: mỗi đột biến sửa mã -> chạy bài liên quan -> phải ĐỎ -> khôi phục.

Chạy: REPO=<gốc repo> python3 mut_review6.py  (ghi ../break-tests-review6.log). Cùng khuôn mut_review5.py, thêm cột
"bài chạy" vì đột biến R6-3 ở :app (bài canh tĩnh).
"""
import glob, os, re, subprocess

REPO = os.environ["REPO"]
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "break-tests-review6.log")
CORE = [":core:test", "--tests", "*SlotReturnTest"]
APP = [":app:testDebugUnitTest", "--tests", "*BehindHomeWiringContractTest"]
# Chỉ đọc XML của ĐÚNG module vừa chạy (XML module kia có thể còn ĐỎ từ đột biến trước).
XML = {id(CORE): "core/build/test-results/test/*.xml", id(APP): "app/build/test-results/testDebugUnitTest/*.xml"}
SR = "core/src/main/kotlin/com/byd/clusternav/launcher/behind/SlotReturn.kt"
RUN = "app/src/main/java/com/byd/clusternav/launcher/SlotReturnRun.kt"
M = [
    ("R6-1 bringBack bỏ cổng màn-nhà-ở-đỉnh: K8 dưới camera khi về ô từ onStart / chạm thẻ", SR, CORE,
     "if (BehindHomePlan.homeOnTop(entries, homeComps)) k8(tag, vd, entries.first { it.taskId == taskId })",
     "if (true) k8(tag, vd, entries.first { it.taskId == taskId })"),
    ("R6-2 bringBackMarked (R1.8) bỏ cổng màn-nhà-ở-đỉnh: K8 dưới camera khi ô dựng lại", SR, CORE,
     "if (!BehindHomePlan.homeOnTop(entries, homeComps)) {",
     "if (false) {"),
    ("R6-3 bên thi hành trao danh sách màn nhà RỖNG cho đường về ô (cổng không bao giờ mở)", RUN, APP,
     "seq(sh).bringBack(vd, taskId, homes)",
     "seq(sh).bringBack(vd, taskId, emptyList())"),
]
GRADLE = ["./gradlew", "--continue", "-q"]

with open(OUT, "w") as log:
    log.write("# Phá thử review lượt 6 (02/10) — công cụ finish/tools/mut_review6.py, REPO=<gốc repo>. "
              "Mỗi đột biến: sửa mã → chạy bài liên quan → phải ĐỎ → khôi phục.\n")
    for name, path, tests, old, new in M:
        p = os.path.join(REPO, path)
        src = open(p).read()
        if src.count(old) != 1:
            log.write(f"{name}: KHÔNG ÁP ĐƯỢC (mốc xuất hiện {src.count(old)} lần)\n"); log.flush(); continue
        open(p, "w").write(src.replace(old, new))
        try:
            r = subprocess.run(GRADLE + tests, cwd=REPO, capture_output=True, text=True)
            log.write(f"{name}: {'ĐỎ' if r.returncode != 0 else 'XANH (LỌT!)'} exit={r.returncode}\n")
            for x in glob.glob(os.path.join(REPO, XML[id(tests)])):
                for m in re.finditer(r'<testcase name="([^"]*)" classname="([^"]*)"[^>]*>\s*<failure', open(x).read()):
                    log.write(f"    test ĐỎ: {m.group(2).split('.')[-1]} > {m.group(1)}\n")
        finally:
            open(p, "w").write(src)
        log.flush()
    r = subprocess.run(GRADLE + CORE + APP, cwd=REPO, capture_output=True, text=True)
    log.write(f"KHÔI PHỤC: {'XANH' if r.returncode == 0 else 'ĐỎ'} exit={r.returncode}\n")
