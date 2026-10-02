VD=$1; TA=$2; S=$3; BCOMP=$4; AC=com.google.android.deskclock/com.android.deskclock.DeskClock
snap() { echo "--- [$1] $(date +%T.%N | cut -c1-12) $(dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+')"; am stack list | grep -E 'Stack id|taskId' | sed -E 's/ bounds=\[[^ ]*//; s/topActivity=.*//'; dumpsys activity activities | grep -E 'mResumedActivity'; echo "pidA=$(pidof com.google.android.deskclock) pidB=$(pidof vn.vietmap.live)"; }
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T"; snap start
echo "\$ [3] am start --display $VD -n $BCOMP  (B-first: B to front inside VD)"; su 2000 am start --display $VD -n $BCOMP 2>&1 | grep -v '^Starting'; sleep 2; snap after-3
echo "\$ [4] am stack move-task $TA $S false  (A out; B on top of A in VD)"; su 2000 am stack move-task $TA $S false; sleep 2; snap after-4
echo "\$ [5] am start --display $VD -n $AC  (A back into VD, on top)"; su 2000 am start --display $VD -n $AC 2>&1 | grep -v '^Starting'; sleep 2; snap after-5
echo "\$ [6] am stack move-task $TA $S false  (T1: A is TOP of VD)"; su 2000 am stack move-task $TA $S false; sleep 2; snap after-6
echo "\$ [7] am start HOME"; su 2000 am start -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1 | grep -v '^Starting'; sleep 2; snap after-7
echo '--- events'
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_|screen_toggled|power_sleep)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound'
