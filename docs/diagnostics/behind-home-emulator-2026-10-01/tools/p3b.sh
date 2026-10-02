# P3 (gated). A = deskclock, B = app already in Kachi slot VD, S = Settings placeholder stack (avoidMoveToFront) on display 0 behind HOME.
AC=com.google.android.deskclock/com.android.deskclock.DeskClock
snap() { echo "--- [$1] $(date +%T.%N | cut -c1-12) $(dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+')"; am stack list | grep -E 'Stack id|taskId'; dumpsys activity activities | grep -E 'mResumedActivity'; echo "pidA=$(pidof com.google.android.deskclock) pidB=$(pidof vn.vietmap.live)"; }
i=0; while [ $i -lt 120 ]; do
  W=$(dumpsys power | grep -c 'mWakefulness=Awake'); VD=$(am stack list | grep -oE 'displayId=[1-9][0-9]*' | head -1 | cut -d= -f2)
  H=$(dumpsys activity activities | grep mResumedActivity | grep -c KachiHome)
  [ "$W" = 1 ] && [ -n "$VD" ] && [ "$H" = 1 ] && break; sleep 0.5; i=$((i+1)); done
echo "gate after $i polls: VD=$VD"
[ -z "$VD" ] && { echo "GATE FAILED"; exit 1; }
BC=$(am stack list | grep -A1 "displayId=$VD " | grep -oE 'taskId=[0-9]+: [^ ]+' | head -1); echo "B on VD: $BC"; BCOMP=${BC#*: }
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T"; snap start
echo "\$ [1] am start --display $VD -n $AC   (A into slot VD, A on top)"; su 2000 am start --display $VD -n $AC 2>&1 | head -3; sleep 2; snap after-1
TA=$(am stack list | grep -oE 'taskId=[0-9]+: com.google.android.deskclock' | grep -oE '[0-9]+' | head -1)
echo "\$ [2] LB avoid com.android.settings/.Settings  (S = standard stack on display 0, bottom)"; su 2000 sh -c "CLASSPATH=/data/local/tmp/kprobe_lb.dex app_process /system/bin LB avoid com.android.settings/.Settings"; sleep 1
S=$(am stack list | grep -B1 'taskId=[0-9]*: com.android.settings' | grep -oE 'Stack id=[0-9]+' | cut -d= -f2); echo "TA=$TA S=$S"
echo "\$ [3] am start --display $VD -n $BCOMP  (B-first: B to front inside VD)"; su 2000 am start --display $VD -n $BCOMP 2>&1 | head -3; sleep 2; snap after-3
echo "\$ [4] am stack move-task $TA $S false  (A out; B on top of A in VD)"; su 2000 am stack move-task $TA $S false; sleep 2; snap after-4
echo "\$ [5] am start --display $VD -n $AC  (T1 setup: A back into VD on top)"; su 2000 am start --display $VD -n $AC 2>&1 | head -3; sleep 2; snap after-5
echo "\$ [6] am stack move-task $TA $S false  (T1: A is TOP of VD)"; su 2000 am stack move-task $TA $S false; sleep 2; snap after-6
echo "\$ [7] am start HOME"; su 2000 am start -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1 | head -1; sleep 2; snap after-7
echo '--- events'
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_|screen_toggled|power_sleep)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound'
