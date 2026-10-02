"""Review lượt 2 — ĐO vá P2 lượt 1 (BehindHomeRecovery một lượt mỗi TIẾN TRÌNH): Waze có dấu (đã chạy ngầm qua chuyến) được
người lái mở toàn màn (Intent MAIN/LAUNCHER như ngăn kéo), rồi tắt/bật màn trong CÙNG tiến trình Kachi ⇒ Waze phải ở lại
trước, không có dòng `KachiBehind: recovery` mới, pid Kachi không đổi."""
import time, k
tag = "r2-latch"
pid0 = k.kachi_pids()
t0 = k.mark()
k.save(tag, "a0-stack.txt", k.sh("am stack list"))
print("start waze:", k.sh("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.waze/.FreeMapAppActivity").strip())
time.sleep(4)
top_before = k.top0()
k.save(tag, "a1-stack-waze-front.txt", k.sh("am stack list"))
k.sh("input keyevent KEYCODE_SLEEP"); time.sleep(4)
k.sh("input keyevent KEYCODE_WAKEUP")
tl = k.timeline(tag, 15, 1.0)
pid1 = k.kachi_pids()
log = k.adb("logcat", "-d", "-v", "threadtime", "-T", t0, "KachiBehind:V", "KachiReady:V", "KachiTrip:V", "*:S")
k.save(tag, "kachi.txt", log)
k.save(tag, "a2-stack-after.txt", k.sh("am stack list"))
k.save(tag, "state-after.txt", k.sh("cat /data/data/%s/shared_prefs/clusternav_state.xml" % k.PKG))
ev = k.adb("logcat", "-b", "events", "-d", "-v", "threadtime", "-T", t0)
k.save(tag, "events.txt", "\n".join(l for l in ev.splitlines() if " am_focused_stack" in l or " screen_toggled" in l or "am_set_resumed" in l) + "\n")
meta = "t0=%s pid_kachi_before=%s after=%s top_before_off=%s\n" % (t0, pid0, pid1, top_before)
k.save(tag, "meta.txt", meta)
print(meta)
print("timeline:", " | ".join(t.split(" top0=")[0][-8:] + "=" + t.split("top0=")[1].split("/")[0] for t in tl))
print("recovery lines since t0:", [l for l in log.splitlines() if "recovery" in l])
print("screen_on lines:", [l[-80:] for l in log.splitlines() if "screen_on" in l])
