C=com.google.android.deskclock/com.android.deskclock.DeskClock
echo "pid_before=$(pidof com.google.android.deskclock)"
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T"
su 2000 am start -n $C
su 2000 am start -a android.intent.action.MAIN -c android.intent.category.HOME
sleep 4
echo "pid_after=$(pidof com.google.android.deskclock)"
am stack list | grep -E 'Stack id|taskId'
dumpsys activity activities | grep -E 'mResumedActivity|mFocusedApp'
echo '--- events'
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo'
