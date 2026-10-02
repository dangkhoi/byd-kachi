#!/usr/bin/env python3
"""T-M2: B đang ở màn ảo ô ⇒ (a) Intent từ HOME (cầu `open` = KachiHomeSlots.openAppFullscreen = AppOpener.openByIntent)
hoặc (b) K7 shell thô (FreeformLaunch.fullscreenCmd, uid 2000) ⇒ task có sang display 0 toàn màn không, pid giữ không;
rồi HOME ⇒ K8 (`am start --display <VD> -n <B>`, uid 2000) đưa về ô."""
import sys, time
from m import *

case, pkg, mech = sys.argv[1], sys.argv[2], sys.argv[3]
comp = sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER %s" % pkg).strip().splitlines()[-1]
sub = "tm2/" + case
t = t0()
s0 = snap(sub, "s0-before")
vd = vd_of(s0, pkg)
task = task_of(s0, pkg)
p0 = pids(pkg, PKG)
save(sub, "meta.txt", "T0=%s pkg=%s comp=%s mech=%s vd=%s task=%s pids_before=%s\n" % (t, pkg, comp, mech, vd, task, p0))
print("before:", brief(s0), sep="\n")
if vd is None:
    raise SystemExit("B không ở màn ảo ô nào")
if mech == "intent":
    print(bridge("open", pkg=pkg)[-120:])
else:
    k7 = "am start --display 0 --windowingMode 1 -f 0x20000000 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n '%s'" % comp
    save(sub, "k7.txt", k7 + "\n" + sh("su 2000 " + k7.replace("'", "") ))
tl = sample(sub, 5.0, name="timeline-detach.txt")
s1 = snap(sub, "s1-after-detach")
p1 = pids(pkg, PKG)
print("after detach:", brief(s1), "pids", p1, sep="\n")
sh("input keyevent KEYCODE_HOME")
time.sleep(2.5)
s2 = snap(sub, "s2-after-home")
print("after HOME:", brief(s2), sep="\n")
vd_now = slot_vds(s2)
target_vd = vd if vd in vd_now else (vd_now[0] if vd_now else vd)
t8 = t0()
k8 = "am start --display %d -n %s" % (target_vd, comp)
save(sub, "k8.txt", k8 + "\n" + sh("su 2000 " + k8))
sample(sub, 4.0, name="timeline-k8.txt")
s3 = snap(sub, "s3-after-k8")
p3 = pids(pkg, PKG)
print("after K8:", brief(s3), "pids", p3, sep="\n")
with open(os.path.join(d(sub), "meta.txt"), "a") as f:
    f.write("pids_after_detach=%s\nT8=%s k8_vd=%s pids_after_k8=%s\n" % (p1, t8, target_vd, p3))
evs = ev(sub, t)
log(sub, t)
for l in evs:
    if re.search(r"am_focused_stack|am_pause_activity|am_set_resumed|wm_task_moved|am_proc_start|am_kill|am_proc_died|am_new_intent|am_create_activity|am_restart|am_relaunch|am_task_moved|wm_task_removed|am_finish", l):
        print(l[:200])
