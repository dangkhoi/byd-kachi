# usage: sh lb.sh <plain|behind|avoid> <component> [displayId]
MODE=$1; C=$2; D=$3; PKG=${C%%/*}
echo "screen: $(dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+')  pid_before=$(pidof $PKG)"
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T"
su 2000 sh -c "CLASSPATH=/data/local/tmp/kprobe_lb.dex app_process /system/bin LB $MODE $C $D"
sleep 4
echo "pid_after=$(pidof $PKG)"
am stack list | grep -E 'Stack id|taskId'
dumpsys activity activities | grep -E 'mResumedActivity|mFocusedApp'
echo '--- events'
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_|screen_toggled)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound'
