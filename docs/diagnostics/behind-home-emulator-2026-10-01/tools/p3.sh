# P3: slot VD = $VD (kachi-slot, VietMap = B already in slot). A = deskclock (task $TA). S = standard fullscreen stack on display 0 behind HOME ($S).
VD=$1; TA=$2; S=$3; B=vn.vietmap.live/.MainActivity; AC=com.google.android.deskclock/com.android.deskclock.DeskClock
snap() { echo "--- [$1] $(date +%T.%N | cut -c1-12)"; am stack list | grep -E 'Stack id|taskId'; dumpsys activity activities | grep -E 'mResumedActivity'; echo "pidA=$(pidof com.google.android.deskclock) pidB=$(pidof vn.vietmap.live)"; }
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T screen=$(dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+')"
snap start
echo '$ [1 setup: A into slot VD] am start --display '$VD' -n '$AC; su 2000 am start --display $VD -n $AC; sleep 3; snap after-1
echo '$ [2 B-first: bring B to front inside VD] am start --display '$VD' -n '$B; su 2000 am start --display $VD -n $B; sleep 3; snap after-2
echo '$ [3 A out, B on top of A in VD] am stack move-task '$TA' '$S' false'; su 2000 am stack move-task $TA $S false; sleep 3; snap after-3
echo '--- events'
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_|screen_toggled)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound'
