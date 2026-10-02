#!/usr/bin/env python3
"""T-M6: B có task trong một stack display 0 ẩn (S do Kachi tạo / sau HOME) ⇒ K8 `am start --display <VD> -n <B>` (uid 2000).
Đạt: pid giữ · 0 am_focused_stack display 0 · không mẫu nào B visible=true trên display 0 · task B ở màn ảo sau lệnh."""
import sys, threading, time
from m import *

case, pkg = sys.argv[1], sys.argv[2]
comp = sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER %s" % pkg).strip().splitlines()[-1]
sub = "tm6/" + case
s0 = snap(sub, "s0-before")
vds = slot_vds(s0)
if not vds:
    raise SystemExit("không có màn ảo ô")
vd = vds[0]
task = task_of(s0, pkg)
p0 = pids(pkg, PKG)
marks = sh("cat /data/data/%s/shared_prefs/clusternav_state.xml | grep behind_marks" % PKG).strip()
t = t0()
samples = []
stop = [False]
def poll():
    while not stop[0]:
        s = stack(); disp = None; vis0 = False; where = None
        for l in s.splitlines():
            m = re.match(r"Stack id=\d+ bounds=\S+ displayId=(\d+)", l)
            if m: disp = int(m.group(1))
            elif "taskId=%s:" % task in l:
                where = disp
                if disp == 0 and "visible=true" in l: vis0 = True
        samples.append("%s task%s@d%s vis0=%s" % (time.strftime("%H:%M:%S"), task, where, vis0))
        time.sleep(0.25)
th = threading.Thread(target=poll); th.start()
time.sleep(1.0)
k8 = "am start --display %d -n %s" % (vd, comp)
out = sh("su 2000 " + k8)
time.sleep(4.0)
stop[0] = True; th.join()
s1 = snap(sub, "s1-after-k8")
p1 = pids(pkg, PKG)
save(sub, "timeline.txt", "\n".join(samples) + "\n")
save(sub, "meta.txt", "T0=%s pkg=%s comp=%s task=%s vd=%s marks=%s\n$ %s\n%s\npids_before=%s pids_after=%s\n" % (t, pkg, comp, task, vd, marks, k8, out, p0, p1))
evs = ev(sub, t)
log(sub, t)
foc0 = [l for l in evs if re.search(r"am_focused_stack: \[0,0,", l)]
vis0 = sum(1 for x in samples if "vis0=True" in x)
print("before:", brief(s0), "after:", brief(s1), sep="\n")
print("pids", p0, "->", p1, "| am_focused_stack d0:", len(foc0), "| samples vis0=True:", vis0, "/", len(samples), "| task now on VD:", vd_of(s1, pkg))
for l in evs:
    if re.search(r"am_focused_stack|wm_task_moved|am_set_resumed|am_relaunch|am_create_activity|am_proc_start|am_kill|wm_task_removed|am_new_intent", l):
        print(l[:190])
