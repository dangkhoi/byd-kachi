# T-M3 (a'): app nhạc NGUỘI dàn vào màn ảo ô bằng K4 (MAIN/LAUNCHER) — task của nó Ở màn ảo — RỒI mới bắn ý-định VIEW
# (-p gói) vào CÙNG màn ảo. Câu hỏi: activity trung chuyển có tái dùng task đang ở màn ảo (ở lại màn ảo) hay vẫn mở
# MusicActivity lên display 0 (che màn nhà) như ca nguội tm3-ytmusic.txt?
PKG=${PKG:-com.google.android.apps.youtube.music}
HOST=${HOST:-music.youtube.com}
BC=vn.vietmap.live/vn.vietmap.live.MainActivity
MS() { su 2000 sh -c "CLASSPATH=/data/local/tmp/kprobe_ms.dex app_process /system/bin MS $*"; }
snap() {
  echo "--- [$1] $(date +%T.%N | cut -c1-12)"
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
echo "\$ $K4"; su 2000 sh -c "$K4" | grep -v '^Starting'; sleep 8; snap after-k4
K9="am start --display $VD --windowingMode 1 -a android.intent.action.VIEW -d 'https://$HOST/watch?v=9bZkp7q19f0' -p $PKG -f 0x10000000"
echo "\$ $K9"; su 2000 sh -c "$K9" | grep -v '^Starting'; sleep 10; snap after-k9-on-vd-task
echo "\$ am start --display $VD -n '$BC'   (K3)"; su 2000 am start --display $VD -n "$BC" | grep -v '^Starting'; sleep 5; snap after-k3
echo '--- events'
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound|am_on_top_resumed|am_activity_launch_time'
