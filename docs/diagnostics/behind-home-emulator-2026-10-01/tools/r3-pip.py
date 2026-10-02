"""Review lượt 3 — chốt [CHƯA BIẾT] của vá PIP (review lượt 2) bằng ĐO máy ảo.

GMaps mở toàn màn rồi vào PIP (`am stack move-top-activity-to-pinned-stack <stack> l t r b` — chỉ đổi thứ tự cửa sổ của
GMaps, không chạm Kachi) ⇒ stack `pinned` đứng ĐẦU display 0 trong `am stack list`, màn nhà Kachi đang hiện ngay dưới.
Tắt máy kiểu BYD như `k.ignite` (màn tắt → `kill -9` mọi tiến trình Kachi → màn bật). Trước vá lượt 2, phép "đỉnh
display 0" lấy PIP làm đỉnh ⇒ streak = 0 ⇒ chuyến chờ `HOME_STEADY` tới `EXPIRED`.

Hai kiểu:
  before — PIP dựng TRƯỚC khi tắt máy (lượt `r3-pip-trip`: [ĐO] PIP KHÔNG sống qua tắt máy kiểu BYD trên máy ảo);
  wait   — PIP dựng SAU khi màn bật, trong lúc chuyến đang chờ (đúng ca GMaps dẫn đường thu về PIP lúc lên xe).

Mẫu mỗi giây ghi CẢ stack đầu display 0 (thường là PIP) lẫn stack ĐANG HIỆN đầu tiên KHÔNG ghim (thứ người lái thấy dưới
PIP). Ghi vào `../e2e/<tag>/`. Dùng: `python3 r3-pip.py <before|wait> [tag] [giây]` (cấu hình chuyến + sao lưu prefs
làm ngoài script). Mọi tiến trình Kachi cũ phải xong chuyến trước khi chạy: chuyến của tiến trình cũ còn đang chờ sẽ
chạy tiếp khi màn nhà lộ ra (lượt `r3-pip-trip` bị lẫn đúng như vậy — xem README Lượt 7).
"""
import os, re, sys, time
import k

MODE = sys.argv[1]
TAG = sys.argv[2] if len(sys.argv) > 2 else "r3-pip-" + MODE
SECS = float(sys.argv[3]) if len(sys.argv) > 3 else 75.0
k.HERE = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(k.__file__)), "..", "e2e"))
MAPS = "com.google.android.apps.maps/com.google.android.maps.MapsActivity"


def tops(s):
    """(đầu display 0, đang hiện đầu tiên không ghim) — `pkg/cls`, PIP gắn đuôi `[pip]`."""
    first = vis = None
    disp, pinned = None, False
    for l in s.splitlines():
        m = re.match(r"\s*(?:Stack|RootTask) id=(\d+) .*displayId=(\d+)", l)
        if m:
            disp, pinned = m.group(2), False
            continue
        if disp != "0":
            continue
        if "mWindowingMode=pinned" in l:
            pinned = True
        t = re.search(r"taskId=\d+: (\S+) .*visible=(true|false)", l)
        if t:
            if first is None:
                first = t.group(1) + ("[pip]" if pinned else "")
            if vis is None and not pinned and t.group(2) == "true":
                vis = t.group(1)
    return first, vis


def stack_of(s, comp, disp="0"):
    cur = None
    for l in s.splitlines():
        m = re.match(r"\s*(?:Stack|RootTask) id=(\d+) .*displayId=(\d+)", l)
        if m:
            cur = m.group(1) if m.group(2) == disp else None
        elif cur and comp in l:
            return cur
    return None


def pip_on(name):
    """GMaps toàn màn (che màn nhà) → PIP. Trả (stack, kết quả lệnh, tops)."""
    k.sh("am start -n %s" % MAPS)
    time.sleep(8)
    sid = stack_of(k.sh("am stack list"), MAPS.split("/")[0] + "/")
    out = k.sh("am stack move-top-activity-to-pinned-stack %s 1400 700 1900 1060" % sid) if sid else "no maps stack"
    time.sleep(3)
    s = k.sh("am stack list")
    k.save(TAG, name, s)
    return sid, out.strip(), tops(s)


k.sh("am force-stop com.waze; am force-stop com.google.android.apps.maps")
k.sh("input keyevent KEYCODE_WAKEUP")
time.sleep(1)
k.sh("input keyevent KEYCODE_HOME")
time.sleep(3)
meta = []
if MODE == "before":
    sid, out, tp = pip_on("pip-before.txt")
    meta.append("pip_before stack=%s move=%r tops=%s" % (sid, out, tp))
    if not (tp[0] or "").endswith("[pip]"):
        sys.exit("PIP không vào được — dừng, không tắt máy (lượt đo không hợp lệ)")

t0 = k.mark()
meta.insert(0, "T0=%s pids_before=%s" % (t0, k.kachi_pids()))
k.sh("input keyevent KEYCODE_SLEEP")
time.sleep(2)
for p in k.kachi_pids():
    k.sh("kill -9 %s" % p)
meta.append("killed_at=%s" % time.strftime("%H:%M:%S"))
time.sleep(10)
meta.append("pids_while_off=%s" % k.kachi_pids())
k.sh("input keyevent KEYCODE_WAKEUP")
meta.append("wake=%s" % time.strftime("%H:%M:%S"))
tl, end = [], time.time() + SECS
if MODE == "wait":
    time.sleep(3)
    sid, out, tp = pip_on("pip-after-wake.txt")
    meta.append("pip_after_wake at=%s stack=%s move=%r tops=%s" % (time.strftime("%H:%M:%S"), sid, out, tp))
while time.time() < end:
    f, v = tops(k.sh("am stack list"))
    tl.append("%s first0=%s visible0=%s" % (time.strftime("%H:%M:%S"), f, v))
    time.sleep(1.0)
k.save(TAG, "timeline.txt", "\n".join(tl) + "\n")
k.save(TAG, "meta.txt", "\n".join(meta) + "\n")
k.save(TAG, "state-after.txt", k.sh("cat /data/data/%s/shared_prefs/clusternav_state.xml" % k.PKG))
print("\n".join(meta))
print("first0:", sorted(set(t.split("first0=")[1].split(" ")[0] for t in tl)))
print("visible0:", sorted(set(t.split("visible0=")[1] for t in tl)))
k.cap(TAG, t0)
