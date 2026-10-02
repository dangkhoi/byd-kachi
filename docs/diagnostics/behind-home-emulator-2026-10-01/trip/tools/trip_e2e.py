#!/usr/bin/env python3
"""Nhóm C (chuyến lên xe F2/F3) — driver E2E máy ảo clusternav10 (bản vehicleTest debuggable, chữ ký release).

Chỉ đọc/ghi: prefs của Kachi qua run-as, `input keyevent`, `kill -9` tiến trình Kachi (kiểu BYD: không force-stop, không
PACKAGE_RESTARTED), `am stack list`, logcat, `dumpsys media_session`. Mọi lượt ghi thư mục <tag>/ nguyên văn.

Dùng: trip_e2e.py backup | config <apps> <music> | clear-ledger | ignite <tag> [secs] | screen <tag> [secs] |
      killmid <tag> <after_s> [secs] | upgrade <tag> <apk> [secs] | snap <tag> | restore <apk-goc>
"""
import hashlib, html, os, re, subprocess, sys, time

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
SER = "emulator-5554"
PKG = "com.byd.launcher"
HERE = os.path.dirname(os.path.abspath(__file__))
PROFILE = "Mặc định"
TAGS = "KachiTrip:V KachiBehind:V KachiReady:V A11yLifecycle:V KachiShortcut:V *:S"


def adb(*args, inp=None):
    r = subprocess.run([ADB, "-s", SER, *args], input=inp, capture_output=True)
    return r.stdout.decode(errors="replace") + (r.stderr.decode(errors="replace") if r.returncode else "")


def sh(cmd):
    return adb("shell", cmd)


def outdir(tag):
    d = os.path.join(HERE, tag)
    os.makedirs(d, exist_ok=True)
    return d


def save(tag, name, text):
    with open(os.path.join(outdir(tag), name), "w") as f:
        f.write(text)


def cat(name):
    return adb("exec-out", "run-as", PKG, "cat", "shared_prefs/%s.xml" % name)


def write_prefs(name, xml):
    # HOME bị force-stop thì hệ dựng lại NGAY (đọc tệp cũ) ⇒ dừng · ghi · dừng lần nữa trong cùng một lượt shell.
    adb("shell", "am force-stop %s; run-as %s sh -c 'cat > shared_prefs/%s.xml'; am force-stop %s" % (PKG, PKG, name, PKG),
        inp=xml.encode())


def set_profile(values):
    x = cat("kachi_workspace")
    for suffix, v in values.items():
        key = PROFILE + "__" + suffix
        x = re.sub(r'\s*<string name="%s">.*?</string>' % re.escape(key), "", x, flags=re.S)
        if v is not None:
            x = x.replace("</map>", '    <string name="%s">%s</string>\n</map>' % (key, html.escape(v, quote=False)))
    write_prefs("kachi_workspace", x)


def clear_ledger():
    x = cat("clusternav_state")
    for k in ("kachi_trip_ledger", "kachi_trip_last"):
        x = re.sub(r'\s*<string name="%s">.*?</string>' % k, "", x, flags=re.S)
    write_prefs("clusternav_state", x)


def kachi_pids():
    out = sh("ps -A -o PID,NAME | grep com.byd.launcher")
    return [l.split()[0] for l in out.splitlines() if l.strip()]


def now_tag():
    return sh("date '+%m-%d %H:%M:%S.000'").strip()


def collect(tag, t0, secs, step=1.0):
    """Chụp mỗi `step` giây: đỉnh display 0 (dòng visible=true đầu tiên) + nhịp media; cuối lượt: log + events + stack."""
    lines = []
    end = time.time() + secs
    while time.time() < end:
        top = sh("am stack list | grep -A2 'displayId=0 ' | grep 'visible=true' | head -1").strip()
        m = re.search(r"taskId=\d+: (\S+)", top)
        lines.append("%s top0=%s" % (time.strftime("%H:%M:%S"), m.group(1) if m else "-"))
        time.sleep(step)
    save(tag, "timeline.txt", "\n".join(lines) + "\n")
    save(tag, "kachi.txt", adb("logcat", "-d", "-v", "threadtime", "-T", t0, *TAGS.split()))
    ev = adb("logcat", "-b", "events", "-d", "-v", "threadtime", "-T", t0)
    keep = [l for l in ev.splitlines() if re.search(r" (am_|wm_|screen_toggled|power_screen)", l)
            and not re.search(r"am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound|am_on_top_resumed", l)]
    save(tag, "events.txt", "\n".join(keep) + "\n")
    save(tag, "stack-after.txt", sh("am stack list"))
    save(tag, "media-after.txt", sh("dumpsys media_session | grep -E 'package=|state=PlaybackState' "))
    save(tag, "state-after.txt", cat("clusternav_state"))
    print(open(os.path.join(outdir(tag), "kachi.txt")).read()[-3000:])


def ignite(tag, secs=70.0):
    """Tắt máy kiểu BYD: màn tắt → giết MỌI tiến trình Kachi (kill -9) → chờ hệ dựng lại lúc màn tắt → mở xe (màn bật)."""
    t0 = now_tag()
    meta = ["T0=%s pids_before=%s" % (t0, kachi_pids())]
    sh("input keyevent KEYCODE_SLEEP")
    time.sleep(2)
    for p in kachi_pids():
        sh("su 0 kill -9 %s" % p)
    time.sleep(10)
    meta.append("pids_while_off=%s interactive=%s" % (kachi_pids(), sh("dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+'").strip()))
    sh("input keyevent KEYCODE_WAKEUP")
    meta.append("wake=%s" % time.strftime("%H:%M:%S"))
    save(tag, "meta.txt", "\n".join(meta) + "\n")
    collect(tag, t0, secs)


def screen(tag, secs=40.0):
    """Tắt/bật màn KHÔNG giết Kachi (vd dừng xe chốc lát) — không phải một chuyến."""
    t0 = now_tag()
    sh("input keyevent KEYCODE_SLEEP")
    time.sleep(4)
    sh("input keyevent KEYCODE_WAKEUP")
    save(tag, "meta.txt", "T0=%s pids=%s\n" % (t0, kachi_pids()))
    collect(tag, t0, secs)


def killmid(tag, after_s, secs=70.0):
    """E9 — như ignite, nhưng giết Kachi LẦN NỮA (màn đang bật) sau `after_s` giây kể từ lúc mở xe (giữa chuyến)."""
    t0 = now_tag()
    sh("input keyevent KEYCODE_SLEEP")
    time.sleep(2)
    for p in kachi_pids():
        sh("su 0 kill -9 %s" % p)
    time.sleep(10)
    sh("input keyevent KEYCODE_WAKEUP")
    time.sleep(float(after_s))
    killed = kachi_pids()
    for p in killed:
        sh("su 0 kill -9 %s" % p)
    save(tag, "meta.txt", "T0=%s killed_mid=%s at+%ss\n" % (t0, killed, after_s))
    collect(tag, t0, secs)


def upgrade(tag, apk, secs=40.0):
    """Nâng cấp APK giữa chuyến (MY_PACKAGE_REPLACED, màn bật) — không phải chuyến mới."""
    t0 = now_tag()
    save(tag, "install.txt", adb("install", "-r", apk))
    save(tag, "meta.txt", "T0=%s pids=%s\n" % (t0, kachi_pids()))
    collect(tag, t0, secs)


def backup():
    d = outdir("backup")
    with open(os.path.join(d, "prefs-before.tar"), "wb") as f:
        f.write(subprocess.run([ADB, "-s", SER, "exec-out", "run-as", PKG, "tar", "cf", "-", "shared_prefs"], capture_output=True).stdout)
    names = sh("run-as %s ls shared_prefs" % PKG).split()
    shas = ["%s %s" % (hashlib.sha1(cat(n[:-4]).encode()).hexdigest(), n) for n in sorted(names) if n.endswith(".xml")]
    save("backup", "prefs-sha-before.txt", "\n".join(shas) + "\n")
    print("\n".join(shas))


def restore(apk):
    tar = open(os.path.join(HERE, "backup", "prefs-before.tar"), "rb").read()
    adb("shell", "am force-stop %s; run-as %s sh -c 'rm -rf shared_prefs; tar xf -'; am force-stop %s" % (PKG, PKG, PKG), inp=tar)
    names = sh("run-as %s ls shared_prefs" % PKG).split()
    shas = ["%s %s" % (hashlib.sha1(cat(n[:-4]).encode()).hexdigest(), n) for n in sorted(names) if n.endswith(".xml")]
    save("backup", "prefs-sha-after-restore.txt", "\n".join(shas) + "\n")
    print(adb("install", "-r", apk))
    print("\n".join(shas))


if __name__ == "__main__":
    cmd, args = sys.argv[1], sys.argv[2:]
    if cmd == "backup": backup()
    elif cmd == "config": set_profile({"ignition_apps": args[0] or None, "ignition_music": args[1] or None})
    elif cmd == "clear-ledger": clear_ledger()
    elif cmd == "ignite": ignite(args[0], float(args[1]) if len(args) > 1 else 70.0)
    elif cmd == "screen": screen(args[0], float(args[1]) if len(args) > 1 else 40.0)
    elif cmd == "killmid": killmid(args[0], args[1], float(args[2]) if len(args) > 2 else 70.0)
    elif cmd == "upgrade": upgrade(args[0], args[1], float(args[2]) if len(args) > 2 else 40.0)
    elif cmd == "snap":
        save(args[0], "stack.txt", sh("am stack list")); save(args[0], "state.txt", cat("clusternav_state"))
        save(args[0], "workspace.txt", cat("kachi_workspace"))
    elif cmd == "restore": restore(args[0])
    else: raise SystemExit(__doc__)
