"""Review lượt 2 — chốt ca 'tiến trình YT Music có mà KHÔNG task' bằng ĐO: sau khi màn bật, bắn broadcast cập nhật widget
(tường minh, kèm FLAG_INCLUDE_STOPPED_PACKAGES=0x20 như hệ gửi cho provider) ⇒ hệ bật TIẾN TRÌNH YT Music bằng broadcast,
không task — đúng trạng thái đo được ở r2-alias-trip (am_proc_start … broadcast … MusicWidgetProvider)."""
import threading, time, k
k.sh("am force-stop com.waze; am force-stop com.google.android.apps.youtube.music")
t = threading.Thread(target=k.ignite, args=("r2-widget-proc", 80.0))
t.start()
time.sleep(20)
out = k.sh("am broadcast -f 0x20 -a android.appwidget.action.APPWIDGET_UPDATE -n com.google.android.apps.youtube.music/.player.widget.MusicWidgetProvider")
pid = k.sh("pidof com.google.android.apps.youtube.music").strip()
tasks = [l for l in k.sh("am stack list").splitlines() if "youtube.music" in l]
k.save("r2-widget-proc", "widget-proc.txt", "%s\nbroadcast=%s\npid=%s\ntasks=%s\n" % (time.strftime("%H:%M:%S"), out.strip(), pid, tasks))
print("WIDGET-PROC", time.strftime("%H:%M:%S"), "pid=", pid, "tasks=", tasks)
t.join()
