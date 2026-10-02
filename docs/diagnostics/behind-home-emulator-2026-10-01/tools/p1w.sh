# P1-warm: A (deskclock) alive with a task behind HOME. am start A ; am start HOME back-to-back.
AC=com.google.android.deskclock/com.android.deskclock.DeskClock
am force-stop com.android.settings; sleep 1
echo "pre: $(dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+') pidA=$(pidof com.google.android.deskclock)"; am stack list | grep -E 'Stack id|taskId' | sed -E 's/ bounds=\[[^ ]*//; s/topActivity=.*//'
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T"
su 2000 am start -n $AC | grep -v '^Starting'; su 2000 am start -a android.intent.action.MAIN -c android.intent.category.HOME | grep -v '^Starting'
sleep 2; echo "post: pidA=$(pidof com.google.android.deskclock)"; am stack list | grep -E 'Stack id|taskId' | sed -E 's/ bounds=\[[^ ]*//; s/topActivity=.*//'; dumpsys activity activities | grep mResumedActivity
echo '--- events'; logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_|screen_toggled)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound'
