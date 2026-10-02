# (iii) YouTube fullscreen playing -> HOME on top: does playback continue when YouTube is stopped behind HOME?
st() { dumpsys media_session | grep -A12 'package=com.google.android.youtube' | grep -oE 'state=PlaybackState \{state=[0-9]+, position=[0-9]+' | head -1; }
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T $(dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+')"
su 2000 am start -a android.intent.action.VIEW -d 'https://www.youtube.com/watch?v=jNQXAC9IVRw' com.google.android.youtube | grep -v '^Starting'
i=0; while [ $i -lt 25 ]; do s=$(st); case "$s" in *state=3,*) break;; esac; sleep 2; i=$((i+1)); done
echo "[front] $(date +%T) poll=$i $s"; sleep 3; echo "[front+3s] $(st)"
dumpsys activity activities | grep mResumedActivity
su 2000 am start -a android.intent.action.MAIN -c android.intent.category.HOME | grep -v '^Starting'
sleep 3; echo "[behind HOME +3s] $(date +%T) $(st)"; sleep 4; echo "[behind HOME +7s] $(st)  pid=$(pidof com.google.android.youtube)"
am stack list | grep -E 'Stack id|taskId' | sed -E 's/ bounds=\[[^ ]*//; s/topActivity=.*//'
dumpsys activity activities | grep mResumedActivity
echo '--- events'; logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_on_|am_focused|screen_toggled|am_relaunch|am_kill)' | grep -E 'youtube|screen|focused|KachiHome'
