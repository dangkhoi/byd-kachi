#!/usr/bin/env python3
"""Phá thử (đột biến) cho phần đóng scope T-M2/T-M6/dấu K12 — mỗi đột biến: sửa mã → chạy bài liên quan → phải ĐỎ → khôi phục.
REPO suy từ biến môi trường REPO (bắt buộc); JAVA_HOME từ môi trường. Không ghi đường tuyệt đối có tên máy vào tệp."""
import os, re, subprocess, sys, glob
REPO = os.environ.get("REPO") or sys.exit("đặt REPO=<gốc repo>")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mut.log")
CORE = ["--tests", "*SlotReturnTest", "--tests", "*BehindHomeSequenceTest"]
APP = ["--tests", "*BehindHomeWiringContractTest", "--tests", "*ShortcutsWiringContractTest"]
M = [
    ("M1 X lên trước màn nhà: bỏ dấu trước K12", "core/src/main/kotlin/com/byd/clusternav/launcher/behind/BehindHomeSequence.kt",
     "val marked = markMain(now, setOf(x))", "val marked = 0", "core"),
    ("M2 B thoát ô (FRONT_CHANGED): bỏ dấu A/B trước K12", "core/src/main/kotlin/com/byd/clusternav/launcher/behind/BehindHomeSequence.kt",
     "if (homeWasTop) { markMain(r3, setOf(a, b)); runCatching { sh(goHomeCmd) } }", "if (homeWasTop) { runCatching { sh(goHomeCmd) } }", "core"),
    ("M3 R1.8 nhận task KHÔNG mang dấu", "core/src/main/kotlin/com/byd/clusternav/launcher/behind/SlotReturn.kt",
     "entries.firstOrNull { marks[it.taskId] == pkg && it.pkg == pkg &&", "entries.firstOrNull { it.pkg == pkg &&", "core"),
    ("M4 về ô: bỏ đọc lại khi màn nhà vừa hiện", "core/src/main/kotlin/com/byd/clusternav/launcher/behind/SlotReturn.kt",
     "where == SlotReturn.Where.FRONT_MAIN && tries < FRONT_TRIES", "where == SlotReturn.Where.FRONT_MAIN && tries < 1", "core"),
    ("M5 rào K7 không dấu camera: thêm nhánh mọi-thứ-khác", "core/src/main/kotlin/com/byd/clusternav/launcher/camera/CameraGuard.kt",
     '") $cmd ;; esac"', '") $cmd ;; *) $cmd ;; esac"', "core"),
    ("M6 phân loại: app đang hiện coi như ẩn", "core/src/main/kotlin/com/byd/clusternav/launcher/behind/SlotReturn.kt",
     "            t.visible -> Where.FRONT_MAIN\n", "", "core"),
    ("M7 R1.8 móc SAU force-stop (giết rồi mới K8)", "app/src/main/java/com/byd/clusternav/launcher/VdAppHost.kt",
     '        if (SlotReturnRun.bringBackMarked(context, displayId, p, sh)) {\n',
     '        sh("am force-stop $p")\n        if (SlotReturnRun.bringBackMarked(context, displayId, p, sh)) {\n', "app"),
    ("M8 R1.8 bỏ cổng 0-lệnh khi không dấu", "app/src/main/java/com/byd/clusternav/launcher/SlotReturnRun.kt",
     "        if (pkg !in marks.values) return false\n", "", "app"),
    ("M10 chạm đúp Toàn màn: lượt 2 đo lại ô (thẻ 'đã đóng' trên app toàn màn)", "app/src/main/java/com/byd/clusternav/launcher/SlotReturnRun.kt",
     "                if (task != null) { done(true); return@detach }\n", "", "app"),
    ("M11 nhả ô giết app đang toàn màn", "app/src/main/java/com/byd/clusternav/launcher/VdAppHost.kt",
     "if (wasLaunched && p != null && sh != null && !full.isDetached)", "if (wasLaunched && p != null && sh != null)", "app"),
    ("M9 Toàn màn từ ô đi đường Intent", "app/src/main/java/com/byd/clusternav/launcher/KachiHomeShortcuts.kt",
     "val started = slots().detachToFull(action.slot)", "slots().openAppFullscreen(sc.pkg); val started = slots().detachToFull(action.slot)", "app"),
]

def run(mod):
    task = [":core:test", *CORE] if mod == "core" else [":app:testDebugUnitTest", *APP]
    r = subprocess.run(["./gradlew", *task, "--continue", "-q"], cwd=REPO, capture_output=True, text=True)
    res = os.path.join(REPO, "core/build/test-results/test" if mod == "core" else "app/build/test-results/testDebugUnitTest")
    red = []
    for f in glob.glob(os.path.join(res, "*.xml")):
        t = open(f).read()
        for m in re.finditer(r'<testcase name="([^"]+)" classname="([^"]+)"[^>]*>\s*<failure', t):
            red.append("%s > %s" % (m.group(2).split(".")[-1], m.group(1)))
    return r.returncode, red

with open(OUT, "w") as log:
    for name, rel, old, new, mod in M:
        p = os.path.join(REPO, rel)
        src = open(p).read()
        if old not in src:
            log.write("%s: KHÔNG ÁP ĐƯỢC (mốc không có)\n" % name); log.flush(); continue
        open(p, "w").write(src.replace(old, new, 1))
        try:
            code, red = run(mod)
        finally:
            open(p, "w").write(src)
        log.write("%s: exit=%s đỏ=%d %s\n" % (name, code, len(red), "; ".join(red)))
        log.flush()
    code, red = run("core"); log.write("KHÔI PHỤC core: exit=%s đỏ=%d\n" % (code, len(red)))
    code, red = run("app"); log.write("KHÔI PHỤC app: exit=%s đỏ=%d\n" % (code, len(red)))
print(open(OUT).read())
