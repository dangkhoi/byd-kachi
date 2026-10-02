PKG=$1
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T"
su 2000 am start -a android.intent.action.MAIN -c android.intent.category.HOME
sleep 3
echo "pid_after=$(pidof $PKG)"
am stack list | grep -E 'Stack id|taskId'
dumpsys activity activities | grep -E 'mResumedActivity'
echo '--- events'
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_|screen_toggled)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound'
