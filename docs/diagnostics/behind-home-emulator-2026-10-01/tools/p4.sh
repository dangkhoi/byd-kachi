# P4: S = placeholder stack (avoidMoveToFront) behind HOME; B-first; move-task A S **true**; then user re-opens A fullscreen.
AC=com.google.android.deskclock/com.android.deskclock.DeskClock; BC=vn.vietmap.live/.MainActivity
snap() { echo "--- [$1] $(date +%T.%N | cut -c1-12) $(dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+')"; am stack list | grep -E 'Stack id|taskId' | sed -E 's/ bounds=\[[^ ]*//; s/topActivity=.*//'; dumpsys activity activities | grep -E 'mResumedActivity'; echo "pidA=$(pidof com.google.android.deskclock) pidS=$(pidof com.android.settings)"; }
VD=$(am stack list | awk '/Stack id/ && /displayId=[1-9]/ {for(i=1;i<=NF;i++) if($i ~ /^displayId=/){split($i,a,"="); print a[2]; exit}}'); echo "VD=$VD"
[ -z "$VD" ] && { echo GATE FAILED; exit 1; }
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T"
su 2000 am start --display $VD -n $AC | grep -v '^Starting'; sleep 2
su 2000 sh -c "CLASSPATH=/data/local/tmp/kprobe_lb.dex app_process /system/bin LB avoid com.android.settings/.Settings"; sleep 1
su 2000 am start --display $VD -n $BC | grep -v '^Starting'; sleep 2
TA=$(am stack list | awk '/taskId=[0-9]+: com.google.android.deskclock/ {sub("taskId=","",$1); sub(":","",$1); print $1; exit}')
S=$(am stack list | awk '/^Stack id=/ {sid=$2} /com.android.settings\/com.android.settings.Settings/ {sub("id=","",sid); print sid; exit}')
echo "TA=$TA S=$S"; snap before-move
echo "\$ am stack move-task $TA $S true"; su 2000 am stack move-task $TA $S true; sleep 2; snap after-move-true
echo "\$ am start -n $AC   (user re-opens A fullscreen)"; su 2000 am start -n $AC | grep -v '^Starting'; sleep 2; snap after-reopen
su 2000 am start -a android.intent.action.MAIN -c android.intent.category.HOME | grep -v '^Starting'; sleep 2; snap after-home
echo '--- events'; logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_|screen_toggled)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound|am_on_top_resumed'
