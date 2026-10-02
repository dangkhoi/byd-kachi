#!/usr/bin/env python3
"""Đo máy ảo (T-M2 / T-M6 / E-ca còn mở) — công cụ chung. Chỉ đọc/ghi: `am stack list`, logcat, pidof, `am start`
(uid 2000 qua `su 2000`), cầu kiểm thử Kachi (`am broadcast … TEST`), prefs Kachi bằng root khi Kachi dừng.

Dùng: m.py bridge_on | snap <dir> <name> | t0 | ev <dir> <t0> | log <dir> <t0> | pids <pkg...> | top
"""
import os, re, subprocess, sys, time

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
SER = "emulator-5554"
PKG = "com.byd.launcher"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
TAGS = ("KachiTrip:V KachiBehind:V KachiReady:V KachiShortcut:V KachiTest:V VdAppHost:V KachiVd:V SlotLiveProbe:V "
        "KachiDetach:V A11yLifecycle:V AndroidRuntime:E *:S")


def adb(*args, inp=None, timeout=120):
    r = subprocess.run([ADB, "-s", SER, *args], input=inp, capture_output=True, timeout=timeout)
    return r.stdout.decode(errors="replace") + (r.stderr.decode(errors="replace") if r.returncode else "")


def sh(cmd, timeout=120):
    return adb("shell", cmd, timeout=timeout)


def d(sub):
    p = os.path.join(OUT, sub)
    os.makedirs(p, exist_ok=True)
    return p


def save(sub, name, text):
    with open(os.path.join(d(sub), name), "w") as f:
        f.write(text)


def t0():
    return sh("date '+%m-%d %H:%M:%S.000'").strip()


def stack():
    return sh("am stack list")


def snap(sub, name):
    s = stack()
    save(sub, name + ".txt", s)
    return s


def pids(*pkgs):
    return {p: sh("pidof %s" % p).strip() for p in pkgs}


def brief(s):
    out = []
    cur = None
    for l in s.splitlines():
        m = re.match(r"Stack id=(\d+) bounds=(\S+) displayId=(\d+)", l)
        if m:
            cur = "S%s d%s %s" % (m.group(1), m.group(3), m.group(2))
            continue
        m = re.search(r"taskId=(\d+): (\S+) bounds=(\S+).*visible=(\w+)", l)
        if m and cur:
            out.append("%s | t%s %s %s vis=%s" % (cur, m.group(1), m.group(2), m.group(3), m.group(4)))
    return "\n".join(out)


def ev(sub, t, name="events.txt"):
    e = adb("logcat", "-b", "events", "-d", "-v", "threadtime", "-T", t)
    keep = [l for l in e.splitlines() if re.search(r" (am_|wm_|screen_toggled|power_screen)", l)
            and not re.search(r"am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound|am_on_top_resumed", l)]
    save(sub, name, "\n".join(keep) + "\n")
    return keep


def log(sub, t, name="kachi.txt"):
    s = adb("logcat", "-d", "-v", "threadtime", "-T", t, *TAGS.split())
    save(sub, name, s)
    return s


def bridge(cmd, **extras):
    a = "am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd %s" % cmd
    for k, v in extras.items():
        a += " --es %s '%s'" % (k, v)
    return sh(a)


def bridge_on():
    boot = sh("cat /proc/sys/kernel/random/boot_id").strip()
    up = float(sh("cat /proc/uptime").split()[0])
    until = int(up * 1000) + 3_500_000
    xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
           "    <string name=\"test_bridge_until\">%s:%d</string>\n</map>\n" % (boot, until))
    p = "/data/data/%s/shared_prefs/kachi_test_bridge.xml" % PKG
    adb("shell", "am force-stop %s; cat > %s; chown 10163:10163 %s; chmod 660 %s; restorecon %s; am force-stop %s"
        % (PKG, p, p, p, p, PKG), inp=xml.encode())
    sh("input keyevent KEYCODE_HOME")
    return until


def vd_of(s, pkg):
    """displayId ≥ 1 của task [pkg] trong bản đọc (None nếu không có)."""
    disp = None
    for l in s.splitlines():
        m = re.match(r"Stack id=\d+ bounds=\S+ displayId=(\d+)", l)
        if m:
            disp = int(m.group(1))
        elif ("taskId=" in l) and (" %s/" % pkg in l or ": %s/" % pkg in l) and disp:
            return disp
    return None


def task_of(s, pkg):
    for l in s.splitlines():
        m = re.search(r"taskId=(\d+): %s/" % re.escape(pkg), l)
        if m:
            return int(m.group(1))
    return None


def slot_vds(s):
    return sorted({int(m) for m in re.findall(r"displayId=([1-9]\d*)", s)})


def sample(sub, secs, step=0.4, name="timeline.txt"):
    """Mỗi `step` giây: dòng visible=true đầu tiên của display 0 + tóm tắt mọi task (taskId/display/visible)."""
    lines = []
    end = time.time() + secs
    while time.time() < end:
        s = stack()
        top = None
        disp = None
        for l in s.splitlines():
            m = re.match(r"Stack id=(\d+) bounds=\S+ displayId=(\d+)", l)
            if m:
                disp = int(m.group(2))
            elif disp == 0 and "visible=true" in l and top is None:
                mm = re.search(r"taskId=(\d+): (\S+)", l)
                if mm:
                    top = "%s:%s" % (mm.group(1), mm.group(2))
        tasks = ";".join("t%s@d%s%s" % (m[1], m[0], "V" if m[2] == "true" else "") for m in
                         re.findall(r"displayId=(\d+)[^\n]*\n[^\n]*\n\s+taskId=(\d+):[^\n]*visible=(\w+)", s))
        lines.append("%s top0=%s" % (time.strftime("%H:%M:%S"), top))
        time.sleep(step)
    save(sub, name, "\n".join(lines) + "\n")
    return lines


if __name__ == "__main__":
    c, a = sys.argv[1], sys.argv[2:]
    if c == "bridge_on":
        print(bridge_on())
    elif c == "snap":
        print(brief(snap(a[0], a[1])))
    elif c == "t0":
        print(t0())
    elif c == "top":
        print(brief(stack()))
    elif c == "pids":
        print(pids(*a))
    elif c == "bridge":
        print(bridge(a[0], **dict(x.split("=", 1) for x in a[1:])))
    else:
        raise SystemExit(__doc__)


PROFILE = "Mặc định"


def cat_prefs(name):
    return sh("cat /data/data/%s/shared_prefs/%s.xml" % (PKG, name))


def write_prefs(name, xml):
    p = "/data/data/%s/shared_prefs/%s.xml" % (PKG, name)
    adb("shell", "am force-stop %s; cat > %s; chown 10163:10163 %s; chmod 660 %s; restorecon %s; am force-stop %s"
        % (PKG, p, p, p, p, PKG), inp=xml.encode())


def set_ws(values):
    import html as H
    x = cat_prefs("kachi_workspace")
    for suffix, v in values.items():
        key = PROFILE + "__" + suffix
        x = re.sub(r'\s*<string name="%s">.*?</string>' % re.escape(key), "", x, flags=re.S)
        x = re.sub(r'\s*<string name="%s"\s*/>' % re.escape(key), "", x)
        if v is not None:
            x = x.replace("</map>", '    <string name="%s">%s</string>\n</map>' % (key, H.escape(v, quote=False)))
    write_prefs("kachi_workspace", x)
    sh("input keyevent KEYCODE_HOME")


def ui_find(pattern):
    """uiautomator dump → [(text, desc, (x1,y1,x2,y2))] khớp regex trên text/desc."""
    import xml.etree.ElementTree as ET
    for _ in range(3):
        o = sh("uiautomator dump /sdcard/kui.xml 2>&1")
        raw = adb("exec-out", "cat", "/sdcard/kui.xml")
        if raw.strip().startswith("<?xml"):
            break
        time.sleep(2)
    rows = []
    for n in ET.fromstring(raw).iter("node"):
        t, dsc = n.get("text", ""), n.get("content-desc", "")
        if re.search(pattern, t + " " + dsc, re.I):
            b = tuple(map(int, re.findall(r"\d+", n.get("bounds", ""))))
            rows.append((t, dsc, b))
    return rows


def tap_center(b):
    sh("input tap %d %d" % ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2))


def shot(sub, name):
    data = subprocess.run([ADB, "-s", SER, "exec-out", "screencap", "-p"], capture_output=True, timeout=120).stdout
    with open(os.path.join(d(sub), name + ".png"), "wb") as f:
        f.write(data)


def kachi_pids():
    out = sh("ps -A -o PID,NAME | grep com.byd.launcher")
    return [l.split()[0] for l in out.splitlines() if l.strip()]


def media():
    return sh("dumpsys media_session | grep -E 'package=|state=PlaybackState' ")


def collect(sub, t, secs, step=1.0, during=None):
    lines = []
    end = time.time() + secs
    start = time.time()
    while time.time() < end:
        if during:
            during(time.time() - start)
        top = sh("am stack list | grep -A2 'displayId=0 ' | grep 'visible=true' | head -1").strip()
        mm = re.search(r"taskId=\d+: (\S+)", top)
        lines.append("%s top0=%s" % (time.strftime("%H:%M:%S"), mm.group(1) if mm else "-"))
        time.sleep(step)
    save(sub, "timeline.txt", "\n".join(lines) + "\n")
    log(sub, t)
    ev(sub, t)
    save(sub, "stack-after.txt", stack())
    save(sub, "media-after.txt", media())
    st = re.sub(r"fp=[^\"<;]*", "fp=<redacted>", cat_prefs("clusternav_state"))
    save(sub, "state-after.txt", st)
    return lines


def ignite(sub, secs=70.0, during=None):
    """Tắt máy kiểu BYD: màn tắt → kill -9 mọi tiến trình Kachi → chờ 10 s → màn bật; rồi ghi đỉnh display 0 mỗi giây."""
    t = t0()
    meta = ["T0=%s pids_before=%s" % (t, kachi_pids())]
    sh("input keyevent KEYCODE_SLEEP")
    time.sleep(2)
    for p in kachi_pids():
        sh("kill -9 %s" % p)
    time.sleep(10)
    meta.append("pids_while_off=%s %s" % (kachi_pids(), sh("dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+'").strip()))
    sh("input keyevent KEYCODE_WAKEUP")
    meta.append("wake=%s" % time.strftime("%H:%M:%S"))
    save(sub, "meta.txt", "\n".join(meta) + "\n")
    return collect(sub, t, secs, during=during)
