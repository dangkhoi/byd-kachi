#!/usr/bin/env python3
"""Phá thử review lượt 4: mỗi đột biến sửa mã -> chạy bài liên quan -> phải ĐỎ -> khôi phục."""
import os, subprocess, sys
REPO = os.environ["REPO"]
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mut_review4.log")
CORE = [":core:test", "--tests", "*SlotReturnTest"]
APP = [":app:testDebugUnitTest", "--tests", "*BehindHomeWiringContractTest", "--tests", "*ShortcutsWiringContractTest"]
SR = "core/src/main/kotlin/com/byd/clusternav/launcher/behind/SlotReturn.kt"
RUN = "app/src/main/java/com/byd/clusternav/launcher/SlotReturnRun.kt"
HOST = "app/src/main/java/com/byd/clusternav/launcher/VdAppHost.kt"
M = [
    ("R4-1 K7 rồi app ẩn: bỏ K8 tại chỗ (ô đen)", SR,
     "        if (where == SlotReturn.Where.HIDDEN_MAIN) {\n", "        if (false && where == SlotReturn.Where.HIDDEN_MAIN) {\n", CORE),
    ("R4-2 K7 rồi app đóng: bỏ GONE", SR,
     "        if (where == SlotReturn.Where.GONE && last.isNotEmpty()) {\n", "        if (false) {\n", CORE),
    ("R4-3 GONE cả khi đọc hỏng", SR,
     "SlotReturn.Where.GONE && last.isNotEmpty()", "SlotReturn.Where.GONE", CORE),
    ("R4-4 host luôn đo lại ô (bỏ xử lý back)", RUN,
     "                when (out.back) {\n", "                when (null as SlotReturn.Back?) {\n", APP),
    ("R4-5 đổi app tại chỗ không bỏ trạng thái toàn màn", HOST,
     "        full.reset()                                    // A đang toàn màn (dòng 9) không còn là app của ô này\n", "", APP),
]
GRADLE = ["./gradlew", "--continue", "-q"]
log = open(OUT, "w")
for name, path, old, new, tasks in M:
    p = os.path.join(REPO, path)
    src = open(p).read()
    if src.count(old) != 1:
        log.write(f"{name}: KHÔNG ÁP ĐƯỢC (mốc xuất hiện {src.count(old)} lần)\n"); log.flush(); continue
    open(p, "w").write(src.replace(old, new))
    try:
        r = subprocess.run(GRADLE + tasks, cwd=REPO, capture_output=True, text=True)
        log.write(f"{name}: {'ĐỎ' if r.returncode != 0 else 'XANH (LỌT!)'} exit={r.returncode}\n")
        txt = (r.stdout + r.stderr).splitlines()
        for l in [l for l in txt if "Execution failed for task" in l or "Compilation error" in l or l.startswith("e: ")][:4]:
            log.write(f"    {l.strip()}\n")
        import glob, re
        for x in glob.glob(os.path.join(REPO, "core/build/test-results/test/*.xml")) + glob.glob(os.path.join(REPO, "app/build/test-results/testDebugUnitTest/*.xml")):
            t = open(x).read()
            for m in re.finditer(r'<testcase name="([^"]*)" classname="([^"]*)"[^>]*>\s*<failure', t):
                log.write(f"    test ĐỎ: {m.group(2).split('.')[-1]} > {m.group(1)}\n")
    finally:
        open(p, "w").write(src)
    log.flush()
r = subprocess.run(GRADLE + CORE + APP[0:1] + APP[1:], cwd=REPO, capture_output=True, text=True)
log.write(f"KHÔI PHỤC: {'XANH' if r.returncode == 0 else 'ĐỎ'} exit={r.returncode}\n")
log.close()
