#!/usr/bin/env python3
"""Phá thử nhóm C (chuyến lên xe): mỗi đột biến sửa ĐÚNG một mắt xích, chạy bài test hẹp, đếm đỏ, rồi trả nguyên tệp.

Kết quả ghi `break-tests-trip.log`. Tệp nguồn luôn được khôi phục (finally), kể cả khi gradle hỏng.

Dùng: `GR=<đường tới gr.sh> python3 mut_trip.py` — gr.sh chạy gradle với máy ảo tạm dừng và ghi `g.log` cạnh nó
(`./gradlew "$@" --continue > g.log 2>&1; echo EXIT=$? >> g.log`).
"""
import glob, os, re, subprocess, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", "..", "..", ".."))   # trip/tools → … → gốc repo
GR = os.environ.get("GR") or sys.exit("thiếu biến GR=<đường tới gr.sh>")
LOG = os.path.join(HERE, "break-tests-trip.log")

CORE = "core/src/main/kotlin/com/byd/clusternav/launcher/trip/"
APP = "app/src/main/java/com/byd/clusternav/"

MUTS = [
    ("M1 onReady chạy THẲNG trên luồng kachi-ready (bỏ executor)", APP + "launcher/trip/TripStart.kt",
     "            EXEC.execute {\n                try {\n                    TripRun(app.applicationContext).run()",
     "            run {\n                try {\n                    TripRun(app.applicationContext).run()",
     ["app", "*TripWiringContractTest"]),
    ("M2 ghi sổ CLAIMED SAU khi làm việc", APP + "launcher/trip/TripStart.kt",
     "                if (!store.write(d.claim)) { Log.e(TripStart.TAG, \"claim write failed trip=$trip -> no trip\"); return }\n                val t0 = now\n                val code = body(boot, firstWake)\n",
     "                val t0 = now\n                val code = body(boot, firstWake)\n                if (!store.write(d.claim)) { Log.e(TripStart.TAG, \"claim write failed trip=$trip -> no trip\"); return }\n",
     ["app", "*TripWiringContractTest"]),
    ("M3 nhạc giao bằng ý-định VIEW thay phiên nhạc", APP + "launcher/trip/TripMusicRun.kt",
     "is TripMusicPlan.Play.FromUri -> \"uri=${bridge.playFromUri(pkg, p.url)}${verify(pkg)}\"",
     "is TripMusicPlan.Play.FromUri -> \"uri=${VoiceAppIntents.send(app, VoiceAppIntents.Handoff(pkg, target.watch!!, p.url))}${verify(pkg)}\"",
     ["app", "*TripWiringContractTest"]),
    ("M4 không đọc được phiên nhạc (null) mà vẫn đi tiếp", CORE + "TripMusicPlan.kt",
     "        sessions == null -> Gate.UNKNOWN_MEDIA\n        musicActive || sessions.any { it.playing } -> Gate.OTHER_PLAYING",
     "        musicActive || sessions.orEmpty().any { it.playing } -> Gate.OTHER_PLAYING",
     ["core", "*TripMusicPlanTest"]),
    ("M5 sổ đã FIRED mà vẫn chạy lại (tắt/bật màn, nâng cấp APK)", CORE + "TripGate.kt",
     "        if (same && ledger?.phase == Phase.FIRED) return Decision.Done(Skip.ALREADY_FIRED)\n",
     "",
     ["core", "*TripGateTest"]),
    ("M6 id chuyến bỏ khoá lần khởi động máy", CORE + "TripGate.kt",
     "if (AccessibilityHealGates.escalatedThisBoot(tatMayAt, elapsedNow)) \"$bootKey.t$tatMayAt\" else \"$bootKey.b\"",
     "if (AccessibilityHealGates.escalatedThisBoot(tatMayAt, elapsedNow)) \"t$tatMayAt\" else \"b\"",
     ["core", "*TripGateTest"]),
    ("M7 HOME yên đọc stack ĐẦU display 0 (không xét đang hiện)", CORE + "TripPlan.kt",
     "it.displayId == BehindHomePlan.MAIN_DISPLAY && it.visible }?.stackId ?: return false",
     "it.displayId == BehindHomePlan.MAIN_DISPLAY }?.stackId ?: return false",
     ["core", "*TripPlanTest"]),
    ("M8 lệnh mở bình thường không thoát dấu $", CORE + "TripPlan.kt",
     "-n ${comp.replace(\"$\", \"\\\\$\")}\"",
     "-n $comp\"",
     ["core", "*TripPlanTest"]),
    ("M9 cho HAI app Mở bình thường", CORE + "TripPlan.kt",
     "if (!it.background && normalSeen) it.copy(background = true) else",
     "if (false) it.copy(background = true) else",
     ["core", "*TripPlanTest"]),
    ("M10 chuyến móc TRƯỚC kiểm phím trong chuỗi SẴN", APP + "EarlyShellChannel.kt",
     "        BehindHomeRecovery.onReady(app)   //",
     "        TripStart.onReady(app)\n        BehindHomeRecovery.onReady(app)   //",
     ["app", "*TripWiringContractTest"]),
    ("M11 app Mở bình thường chạy TRƯỚC nhạc", CORE + "TripPlan.kt",
     "        return bg + music + normal",
     "        return bg + normal + music",
     ["core", "*TripPlanTest"]),
]


def results(module):
    d = {"core": "core/build/test-results/test", "app": "app/build/test-results/testDebugUnitTest"}[module]
    fails, total = [], 0
    for p in glob.glob(os.path.join(REPO, d, "TEST-*.xml")):
        if os.path.getmtime(p) < START:
            continue
        s = open(p).read()
        m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', s)
        total += int(m.group(1))
        for f in re.finditer(r'<testcase name="([^"]*)"[^>]*>\s*<failure', s):
            fails.append(f.group(1))
    return total, fails


out = open(LOG, "a")
out.write("\n=== %s ===\n" % time.strftime("%Y-%m-%d %H:%M:%S"))
for name, rel, old, new, (module, test) in [m for m in MUTS if not os.environ.get("ONLY") or m[0].startswith(os.environ["ONLY"])]:
    path = os.path.join(REPO, rel)
    src = open(path).read()
    if old not in src:
        out.write("%s: MẪU KHÔNG KHỚP — bỏ\n" % name); out.flush(); continue
    try:
        open(path, "w").write(src.replace(old, new, 1))
        START = time.time() - 1
        task = ":core:test" if module == "core" else ":app:testDebugUnitTest"
        subprocess.run(["bash", GR, task, "--tests", test], check=False)
        g = open(os.path.join(os.path.dirname(GR), "g.log")).read()
        compile_fail = "Compilation error" in g or re.search(r"^e: ", g, re.M)
        total, fails = results(module)
        verdict = "ĐỎ" if (fails or compile_fail) else "XANH (đột biến LỌT!)"
        out.write("%s [%s %s] ⇒ %s · %d test chạy · đỏ: %s%s\n" % (
            name, module, test, verdict, total, fails, " · lỗi biên dịch" if compile_fail else ""))
        out.flush()
    finally:
        open(path, "w").write(src)
out.write("— khôi phục mọi tệp; chạy lại bộ hẹp để xác nhận xanh —\n")
out.close()
print(open(LOG).read())
