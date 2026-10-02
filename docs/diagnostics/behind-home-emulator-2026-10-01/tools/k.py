#!/usr/bin/env python3
"""E2E máy ảo (bản RELEASE 2.84/185 chưa commit) — công cụ đo theo ĐƯỜNG UI NGƯỜI DÙNG.

Chỉ đọc/ghi: chụp màn (screencap), uiautomator dump, `input tap/keyevent`, `am stack list`, logcat, prefs Kachi đọc
bằng root (adbd đã root trên máy ảo; bản release không debuggable ⇒ không run-as). Mọi lượt ghi thư mục <tag>/.

Dùng:
  k.py shot <tag> <name>            chụp màn → <tag>/<name>.png
  k.py ui <tag> <name> [filter]     uiautomator dump → <tag>/<name>.xml + in các node có chữ/desc (lọc tuỳ chọn)
  k.py tap <x> <y>                  input tap
  k.py pref [suffix...]             in khoá kachi_workspace của hồ sơ đang dùng (Mặc định)
  k.py state                        in clusternav_state.xml
  k.py top                          đỉnh display 0 + stack list rút gọn
  k.py mark                         mốc giờ máy (để -T)
  k.py cap <tag> <t0> [secs]        chụp nhật ký từ mốc t0: events + Kachi + stack + resumed
"""
import html, os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
SER = "emulator-5554"
PKG = "com.byd.launcher"
HERE = os.path.dirname(os.path.abspath(__file__))
PROFILE = "Mặc định"
TAGS = "KachiTrip:V KachiBehind:V KachiReady:V KachiShortcut:V A11yLifecycle:V KachiFloat:V VdAppHost:V KachiVd:V AndroidRuntime:E *:S"


def adb(*args, inp=None, timeout=120):
    r = subprocess.run([ADB, "-s", SER, *args], input=inp, capture_output=True, timeout=timeout)
    return r.stdout.decode(errors="replace") + (r.stderr.decode(errors="replace") if r.returncode else "")


def sh(cmd, timeout=120):
    return adb("shell", cmd, timeout=timeout)


def outdir(tag):
    d = os.path.join(HERE, tag)
    os.makedirs(d, exist_ok=True)
    return d


def save(tag, name, text):
    with open(os.path.join(outdir(tag), name), "w") as f:
        f.write(text)


def shot(tag, name):
    data = subprocess.run([ADB, "-s", SER, "exec-out", "screencap", "-p"], capture_output=True, timeout=120).stdout
    p = os.path.join(outdir(tag), name + ".png")
    with open(p, "wb") as f:
        f.write(data)
    return p


def ui(tag, name, flt=None):
    for _ in range(3):
        out = sh("uiautomator dump /sdcard/kui.xml 2>&1")
        raw = adb("exec-out", "cat", "/sdcard/kui.xml")
        if raw.strip().startswith("<?xml") and "dumped" in out:
            break
        time.sleep(2)
    save(tag, name + ".xml", raw)
    rows = []
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as e:
        print("parse error", e, out[:300])
        return rows
    for n in root.iter("node"):
        t, d = n.get("text", ""), n.get("content-desc", "")
        if not (t or d):
            continue
        b = tuple(map(int, re.findall(r"\d+", n.get("bounds", ""))))
        rows.append((t, d, b, n.get("clickable"), n.get("resource-id", ""), n.get("checked")))
    for r in rows:
        line = "%r desc=%r b=%s click=%s id=%s chk=%s" % r
        if flt is None or re.search(flt, line, re.I):
            print(line)
    return rows


def pref_all():
    x = sh("cat /data/data/%s/shared_prefs/kachi_workspace.xml" % PKG)
    out = {}
    for m in re.finditer(r'<(\w+) name="([^"]+)"(?: value="([^"]*)")?(?:>(.*?)</\1>|\s*/>)', x, re.S):
        out[m.group(2)] = m.group(3) if m.group(3) is not None else html.unescape(m.group(4) or "")
    return out


def top():
    s = sh("am stack list")
    lines = [l for l in s.splitlines() if re.search(r"Stack id|taskId", l)]
    return "\n".join(lines)


def mark():
    return sh("date '+%m-%d %H:%M:%S.000'").strip()


def cap(tag, t0, secs=0):
    if secs:
        time.sleep(float(secs))
    ev = adb("logcat", "-b", "events", "-d", "-v", "threadtime", "-T", t0)
    keep = [l for l in ev.splitlines() if re.search(r" (am_|wm_|screen_toggled|power_screen)", l)
            and not re.search(r"am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound|am_on_top_resumed", l)]
    save(tag, "events.txt", "\n".join(keep) + "\n")
    save(tag, "kachi.txt", adb("logcat", "-d", "-v", "threadtime", "-T", t0, *TAGS.split()))
    save(tag, "stack-after.txt", sh("am stack list"))
    save(tag, "resumed.txt", sh("dumpsys activity activities | grep -E 'mResumedActivity|ResumedActivity|mFocusedApp'"))
    print("== events (focus/pause/resume/moved) ==")
    for l in keep:
        if re.search(r"am_focused_stack|am_pause_activity|am_set_resumed|wm_task_moved|am_proc_start|am_on_resume_called|am_create_activity|am_finish_activity|am_kill|am_proc_died|am_new_intent|am_restart", l):
            print(l[:220])
    print("== kachi ==")
    print(open(os.path.join(outdir(tag), "kachi.txt")).read()[-4000:])


if __name__ == "__main__":
    c, a = sys.argv[1], sys.argv[2:]
    if c == "shot":
        print(shot(a[0], a[1]))
    elif c == "ui":
        ui(a[0], a[1], a[2] if len(a) > 2 else None)
    elif c == "tap":
        sh("input tap %s %s" % (a[0], a[1]))
    elif c == "pref":
        p = pref_all()
        for k in sorted(p):
            if k.startswith(PROFILE + "__") and (not a or any(k.endswith("__" + s) for s in a)):
                print(k, "=", p[k][:300])
    elif c == "state":
        print(sh("cat /data/data/%s/shared_prefs/clusternav_state.xml" % PKG))
    elif c == "top":
        print(top())
    elif c == "mark":
        print(mark())
    elif c == "cap":
        cap(a[0], a[1], a[2] if len(a) > 2 else 0)
    elif c in ("tapcap", "ignite", "ignite_fs"):
        pass
    else:
        raise SystemExit(__doc__)


def prefs_sha():
    return sh("sha1sum /data/data/%s/shared_prefs/kachi_workspace.xml" % PKG).split()[0]


def top0():
    s = sh("am stack list")
    cur = None
    for l in s.splitlines():
        m = re.match(r"Stack id=(\d+) .*displayId=(\d+)", l)
        if m:
            cur = m.group(2)
            continue
        if cur == "0" and "visible=true" in l:
            mm = re.search(r"taskId=\d+: (\S+)", l)
            return mm.group(1) if mm else "?"
    return "-"


def tapcap(tag, x, y, secs, pkgs):
    import json
    t0 = mark()
    shot(tag, "before")
    pids = {p: sh("pidof %s" % p).strip() for p in pkgs}
    save(tag, "meta-before.json", json.dumps({"t0": t0, "pids": pids, "prefs_sha": prefs_sha(), "tap": [x, y]}, ensure_ascii=False, indent=1))
    save(tag, "s00-before.txt", sh("am stack list"))
    sh("input tap %s %s" % (x, y))
    tl, i, end = [], 1, time.time() + float(secs)
    while time.time() < end:
        st = sh("am stack list")
        save(tag, "s%02d.txt" % i, st)
        tl.append("%s top0=%s" % (time.strftime("%H:%M:%S"), top0()))
        i += 1
        time.sleep(0.3)
    save(tag, "timeline.txt", "\n".join(tl) + "\n")
    shot(tag, "after")
    pids2 = {p: sh("pidof %s" % p).strip() for p in pkgs}
    save(tag, "meta-after.json", json.dumps({"pids": pids2, "prefs_sha": prefs_sha()}, ensure_ascii=False, indent=1))
    print("pids before", pids, "after", pids2)
    print("timeline:", " | ".join(tl))
    cap(tag, t0)


if __name__ == "__main__" and sys.argv[1] == "tapcap":
    tapcap(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6:])


def kachi_pids():
    out = sh("ps -A -o PID,NAME | grep com.byd.launcher")
    return [l.split()[0] for l in out.splitlines() if l.strip()]


def timeline(tag, secs, step=1.0):
    tl, end = [], time.time() + float(secs)
    while time.time() < end:
        tl.append("%s top0=%s" % (time.strftime("%H:%M:%S"), top0()))
        time.sleep(step)
    save(tag, "timeline.txt", "\n".join(tl) + "\n")
    return tl


def ignite(tag, secs=70.0, wake=True, off_wait=10.0):
    """Tắt máy kiểu BYD: màn tắt → kill -9 MỌI tiến trình Kachi → chờ hệ dựng lại lúc màn tắt → (tuỳ) màn bật."""
    t0 = mark()
    meta = ["T0=%s pids_before=%s" % (t0, kachi_pids())]
    sh("input keyevent KEYCODE_SLEEP")
    time.sleep(2)
    for p in kachi_pids():
        sh("kill -9 %s" % p)
    meta.append("killed_at=%s" % time.strftime("%H:%M:%S"))
    time.sleep(float(off_wait))
    meta.append("pids_while_off=%s %s" % (kachi_pids(), sh("dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+'").strip()))
    off_log = adb("logcat", "-d", "-v", "threadtime", "-T", t0, "KachiTrip:V", "KachiReady:V", "KachiBehind:V", "*:S")
    save(tag, "while-off-kachi.txt", off_log)
    meta.append("trip_lines_while_off=%d" % len([l for l in off_log.splitlines() if " KachiTrip:" in l]))
    if wake:
        sh("input keyevent KEYCODE_WAKEUP")
        meta.append("wake=%s" % time.strftime("%H:%M:%S"))
    save(tag, "meta.txt", "\n".join(meta) + "\n")
    print("\n".join(meta))
    tl = timeline(tag, secs)
    print("timeline:", " | ".join(t.split(" top0=")[0][-8:] + "=" + t.split("top0=")[1].split("/")[0] for t in tl))
    shot(tag, "after")
    save(tag, "state-after.txt", sh("cat /data/data/%s/shared_prefs/clusternav_state.xml" % PKG))
    cap(tag, t0)


if __name__ == "__main__" and sys.argv[1] == "ignite":
    ignite(sys.argv[2], float(sys.argv[3]) if len(sys.argv) > 3 else 70.0, (sys.argv[4] != "nowake") if len(sys.argv) > 4 else True,
           float(sys.argv[5]) if len(sys.argv) > 5 else 10.0)


def ignite_fs(tag, fs_after, secs=90.0):
    """Như ignite, nhưng `am force-stop` Kachi sau `fs_after` s kể từ lúc màn bật (giả lập lượt chữa phím force-stop
    giữa chuyến — ân hạn khởi động không tự kích được trên máy ảo vì dịch vụ Hỗ trợ không kẹt bền)."""
    t0 = mark()
    meta = ["T0=%s pids_before=%s" % (t0, kachi_pids())]
    sh("input keyevent KEYCODE_SLEEP")
    time.sleep(2)
    for p in kachi_pids():
        sh("kill -9 %s" % p)
    time.sleep(12)
    meta.append("pids_while_off=%s" % kachi_pids())
    sh("input keyevent KEYCODE_WAKEUP")
    meta.append("wake=%s" % time.strftime("%H:%M:%S"))
    time.sleep(float(fs_after))
    meta.append("force-stop at %s (state before: %s)" % (time.strftime("%H:%M:%S"),
                re.findall(r"kachi_trip_ledger\">([^<]*)", sh("cat /data/data/%s/shared_prefs/clusternav_state.xml" % PKG))))
    sh("am force-stop %s" % PKG)
    save(tag, "meta.txt", "\n".join(meta) + "\n")
    print("\n".join(meta))
    tl = timeline(tag, secs)
    print("timeline:", " | ".join(t.split(" top0=")[0][-8:] + "=" + t.split("top0=")[1].split("/")[-1][-24:] for t in tl[::3]))
    shot(tag, "after")
    save(tag, "state-after.txt", sh("cat /data/data/%s/shared_prefs/clusternav_state.xml" % PKG))
    cap(tag, t0)


if __name__ == "__main__" and sys.argv[1] == "ignite_fs":
    ignite_fs(sys.argv[2], sys.argv[3], float(sys.argv[4]) if len(sys.argv) > 4 else 90.0)
