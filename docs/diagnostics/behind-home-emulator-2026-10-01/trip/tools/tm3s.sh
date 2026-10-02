# T-M3' (phiên nhạc thay K9): app nhạc NGUỘI dàn vào màn ảo ô bằng K4 (MAIN/LAUNCHER, đúng stageCmd) rồi "phát gì" đi qua
# MediaController (play / playFromUri / playFromSearch — API công khai, đúng đường MediaBridge của Kachi), KHÔNG intent VIEW.
PKG=${PKG:-com.google.android.apps.youtube.music}
URL=${URL:-https://music.youtube.com/watch?v=kJQP7kiw5Fk}
Q=${Q:-son tung mtp}
BC=vn.vietmap.live/vn.vietmap.live.MainActivity
MS() { su 2000 sh -c "CLASSPATH=/data/local/tmp/kprobe_ms.dex app_process /system/bin MS $*"; }
snap() {
  echo "--- [$1] $(date +%T.%N | cut -c1-12) $(dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+')"
  am stack list | grep -E 'Stack id|taskId' | sed -E 's/ bounds=\[[^ ]*//; s/topActivity=.*//'
  dumpsys activity activities | grep -E 'mResumedActivity' | head -3
  echo "pidX=$(pidof $PKG)"; MS list
}
VD=$(am stack list | awk '/Stack id/ && /displayId=[1-9]/ {for(i=1;i<=NF;i++) if($i ~ /^displayId=/){split($i,a,"="); print a[2]; exit}}')
COMP=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $PKG | tail -1)
echo "VD=$VD PKG=$PKG COMP=$COMP"
[ -z "$VD" ] && { echo "GATE FAILED"; exit 1; }
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T"
su 2000 am force-stop $PKG; sleep 1; snap start
K4="am start --display $VD --windowingMode 1 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n '$COMP'"
echo "\$ $K4"; su 2000 sh -c "$K4" | grep -v '^Starting'; sleep 10; snap after-k4-cold
echo "\$ MS play $PKG"; MS play $PKG; sleep 8; snap after-play
echo "\$ MS uri $PKG $URL"; MS uri $PKG "$URL"; sleep 10; snap after-uri
echo "\$ MS search $PKG '$Q'"; MS search $PKG "'$Q'"; sleep 10; snap after-search
echo "\$ am start --display $VD -n '$BC'   (K3)"; su 2000 am start --display $VD -n "$BC" | grep -v '^Starting'; sleep 6; snap after-k3
echo '--- events'
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_|screen_toggled)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound|am_on_top_resumed|am_activity_launch_time'
