#!/usr/bin/env python3
"""FIX286 R-SC — driver máy ảo (clusternav10, emulator-5554). Dựa trên tools/ui-groupB.py của shortcuts-autostart.

Chỉ: đọc/ghi prefs Kachi (root hoặc run-as), `input tap` vào icon lối tắt tìm bằng uiautomator, `am stack list`,
`am force-stop` app khách, logcat, screencap, dumpsys appwidget. Không lệnh nào chạm display 1 (cụm).
"""
import os, re, subprocess, sys, time, hashlib, json, html
import xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
SER = "emulator-5554"
PKG = "com.byd.launcher"
HERE = os.path.dirname(os.path.abspath(__file__))
PROFILE = "Mặc định"
WS = "/data/data/%s/shared_prefs/kachi_workspace.xml" % PKG
MODE = os.environ.get("SC_MODE", "root")   # root | runas


def adb(*args, inp=None, timeout=110):
    r = subprocess.run([ADB, "-s", SER, *args], input=inp, capture_output=True, timeout=timeout)
    return r.stdout.decode(errors="replace")


def sh(cmd, timeout=110):
    return adb("shell", cmd, timeout=timeout)


def outdir(tag):
    d = os.path.join(HERE, "ev", tag)
    os.makedirs(d, exist_ok=True)
    return d


def save(tag, name, text):
    with open(os.path.join(outdir(tag), name), "w") as f:
        f.write(text)


def prefs_xml():
    if MODE == "root":
        return adb("exec-out", "cat", WS)
    return adb("exec-out", "run-as", PKG, "cat", "shared_prefs/kachi_workspace.xml")


def prefs_sha():
    return hashlib.sha1(prefs_xml().encode()).hexdigest()


def pref(name):
    m = re.search(r'<string name="%s">(.*?)</string>' % re.escape(PROFILE + "__" + name), prefs_xml(), re.S)
    return html.unescape(m.group(1)) if m else None


def set_prefs(values):
    """Ghi các hậu tố theo hồ sơ đang dùng giữa HAI lượt force-stop (HOME bị dừng thì hệ dựng lại ngay, đọc tệp cũ)."""
    x = prefs_xml()
    assert "<map>" in x, x[:200]
    for suffix, v in values.items():
        key = PROFILE + "__" + suffix
        x = re.sub(r'\s*<string name="%s">.*?</string>' % re.escape(key), "", x, flags=re.S)
        if v is not None:
            x = x.replace("</map>", '    <string name="%s">%s</string>\n</map>' % (key, html.escape(v, quote=False)))
    if MODE == "root":
        cmd = ("am force-stop %s; cat > %s; chown 10163:10163 %s; chmod 660 %s; restorecon %s; am force-stop %s"
               % (PKG, WS, WS, WS, WS, PKG))
    else:
        cmd = "am force-stop %s; run-as %s sh -c 'cat > shared_prefs/kachi_workspace.xml'; am force-stop %s" % (PKG, PKG, PKG)
    adb("shell", cmd, inp=x.encode())


def home(wait=14):
    sh("input keyevent 3")
    time.sleep(wait)


def dump():
    sh("uiautomator dump /sdcard/kui.xml >/dev/null 2>&1")
    return adb("exec-out", "cat", "/sdcard/kui.xml")


def nodes(raw):
    out = []
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return out
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) == 4:
            out.append({"desc": n.get("content-desc", ""), "text": n.get("text", ""), "cls": n.get("class", ""),
                        "b": tuple(map(int, b)), "click": n.get("clickable") == "true"})
    return out


def find(raw, desc=None, text=None):
    for n in nodes(raw):
        if desc is not None and n["desc"] == desc:
            return n
        if text is not None and n["text"] == text:
            return n
    return None


def center(b):
    return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2


def tap(x, y):
    sh("input tap %d %d" % (x, y))


def stacks():
    return sh("am stack list")


def shot(tag, name):
    with open(os.path.join(outdir(tag), name), "wb") as f:
        f.write(subprocess.run([ADB, "-s", SER, "exec-out", "screencap", "-p"], capture_output=True).stdout)


def capture(tag, action, secs=8.0, step=1.0, extra_pids=()):
    t0 = sh("date '+%m-%d %H:%M:%S.000'").strip()
    pids = {p: sh("pidof %s" % p).strip() for p in extra_pids}
    save(tag, "meta-before.json", json.dumps({"t0": t0, "pids": pids, "prefs_sha": prefs_sha(),
                                              "slot_0": pref("slot_0"), "slot_1": pref("slot_1")}, ensure_ascii=False, indent=1))
    save(tag, "s00-before.txt", stacks())
    action()
    i, t = 1, 0.0
    while t < secs:
        time.sleep(step); t += step
        save(tag, "s%02d.txt" % i, stacks()); i += 1
    save(tag, "s99-after.txt", stacks())
    pids2 = {p: sh("pidof %s" % p).strip() for p in extra_pids}
    save(tag, "meta-after.json", json.dumps({"pids": pids2, "prefs_sha": prefs_sha(),
                                             "slot_0": pref("slot_0"), "slot_1": pref("slot_1")}, ensure_ascii=False, indent=1))
    ev = sh("logcat -b events -d -v threadtime -T '%s'" % t0)
    ev = "\n".join(l for l in ev.splitlines() if re.search(r" (am_|wm_)", l)
                   and not re.search(r"am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound", l))
    save(tag, "events.txt", ev)
    save(tag, "kachi.txt", sh("logcat -d -v threadtime -T '%s' -s KachiShortcut:V KachiBehind:V VdAppHost:V KachiVd:V KachiAppWidget:V" % t0))
    return t0


def tap_desc(desc, raw=None):
    raw = raw or dump()
    n = find(raw, desc=desc)
    if n is None:
        raise RuntimeError("không thấy icon '%s'" % desc)
    x, y = center(n["b"])
    tap(x, y)
    return n


if __name__ == "__main__":
    print("module")


def enable_tm():
    """Bật chế độ kiểm thử qua run-as (bản vehicleTest) — cùng cách lib.sh của luồng R-ES."""
    boot = sh("cat /proc/sys/kernel/random/boot_id").strip()
    up = float(sh("cat /proc/uptime").split()[0])
    until = int(up * 1000) + 3600000 - 120000
    x = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
         "    <string name=\"test_bridge_until\">%s:%d</string>\n</map>\n" % (boot, until))
    adb("shell", "am force-stop %s; run-as %s sh -c 'cat > shared_prefs/kachi_test_bridge.xml'; am force-stop %s" % (PKG, PKG, PKG),
        inp=x.encode())


def br(args):
    return sh("am broadcast -a %s.TEST -p %s %s" % (PKG, PKG, args))
