#!/usr/bin/env python3
"""Group B (Shortcuts UI) emulator driver — đo đường THẬT (chạm icon trên thanh nút / widget) trên clusternav10.

Chỉ đọc/ghi: prefs của Kachi qua run-as (bản vehicleTest debuggable), `input tap`, `am stack list`, logcat, uiautomator.
Mọi lượt đo ghi thư mục ui-probe/<tag>/ (nguyên văn).
"""
import os, re, subprocess, sys, time, hashlib, json, html
import xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
SER = "emulator-5554"
PKG = "com.byd.launcher"
HERE = os.path.dirname(os.path.abspath(__file__))
PROFILE = "Mặc định"


def adb(*args, inp=None, check=False):
    r = subprocess.run([ADB, "-s", SER, *args], input=inp, capture_output=True)
    if check and r.returncode != 0:
        raise RuntimeError(r.stderr.decode(errors="replace"))
    return r.stdout.decode(errors="replace")


def sh(cmd):
    return adb("shell", cmd)


def outdir(tag):
    d = os.path.join(HERE, tag)
    os.makedirs(d, exist_ok=True)
    return d


def save(tag, name, text):
    with open(os.path.join(outdir(tag), name), "w") as f:
        f.write(text)


def prefs_xml():
    return adb("exec-out", "run-as", PKG, "cat", "shared_prefs/kachi_workspace.xml")


def prefs_sha():
    return hashlib.sha1(prefs_xml().encode()).hexdigest()


def pref(name):
    m = re.search(r'<string name="%s">(.*?)</string>' % re.escape(PROFILE + "__" + name), prefs_xml(), re.S)
    return html.unescape(m.group(1)) if m else None


def set_prefs(values):
    """Ghi các hậu tố theo hồ sơ đang dùng (force-stop Kachi trước — nó ghi đè tệp khi đang sống)."""
    x = prefs_xml()
    for suffix, v in values.items():
        key = PROFILE + "__" + suffix
        x = re.sub(r'\s*<string name="%s">.*?</string>' % re.escape(key), "", x, flags=re.S)
        if v is not None:
            x = x.replace("</map>", '    <string name="%s">%s</string>\n</map>' % (key, html.escape(v, quote=False)))
    # HOME bị force-stop thì hệ dựng lại NGAY (đọc tệp cũ) ⇒ dừng · ghi · dừng lần nữa trong cùng một lượt shell;
    # tiến trình mới (lần dựng lại thứ hai) đọc tệp vừa ghi. Tiến trình bị force-stop không ghi gì ra đĩa.
    adb("shell", "am force-stop %s; run-as %s sh -c 'cat > shared_prefs/kachi_workspace.xml'; am force-stop %s" % (PKG, PKG, PKG),
        inp=x.encode())


def home(wait=12):
    sh("input keyevent 3")
    time.sleep(wait)


def dump():
    sh("uiautomator dump /sdcard/kui.xml >/dev/null 2>&1")
    raw = adb("exec-out", "cat", "/sdcard/kui.xml")
    return raw


def nodes(raw):
    out = []
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return out
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) == 4:
            out.append({
                "desc": n.get("content-desc", ""), "text": n.get("text", ""), "cls": n.get("class", ""),
                "b": tuple(map(int, b)), "click": n.get("clickable") == "true",
            })
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


def capture(tag, action, secs=8.0, step=0.5, extra_pids=()):
    """Chạy [action] rồi chụp `am stack list` nguyên bản theo nhịp + events + log Kachi + pid."""
    t0 = sh("date '+%m-%d %H:%M:%S.000'").strip()
    pids = {p: sh("pidof %s" % p).strip() for p in extra_pids}
    save(tag, "meta-before.json", json.dumps({"t0": t0, "pids": pids, "prefs_sha": prefs_sha()}, ensure_ascii=False, indent=1))
    save(tag, "s00-before.txt", stacks())
    action()
    i, t = 1, 0.0
    while t < secs:
        time.sleep(step); t += step
        save(tag, "s%02d.txt" % i, stacks()); i += 1
    save(tag, "s99-after.txt", stacks())
    pids2 = {p: sh("pidof %s" % p).strip() for p in extra_pids}
    save(tag, "meta-after.json", json.dumps({"pids": pids2, "prefs_sha": prefs_sha()}, ensure_ascii=False, indent=1))
    ev = sh("logcat -b events -d -v threadtime -T '%s'" % t0)
    ev = "\n".join(l for l in ev.splitlines() if re.search(r" (am_|wm_)", l) and not re.search(r"am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound", l))
    save(tag, "events.txt", ev)
    save(tag, "kachi.txt", sh("logcat -d -v threadtime -T '%s' -s KachiShortcut:V KachiBehind:V VdAppHost:V KachiVd:V" % t0))
    save(tag, "resumed.txt", sh("dumpsys activity activities | grep -E 'mResumedActivity|mFocusedApp|ResumedActivity'"))


if __name__ == "__main__":
    print("module")
