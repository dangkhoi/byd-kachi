# T-M3 (spec shortcuts-autostart §5.0): K9 = am start --display <VD> --windowingMode 1 -a VIEW -d '<url>' -p <pkg>
# vào màn ảo của MỘT ô app (Kachi sở hữu). Ca (b) app NGUỘI lên màn ảo · ca (a) app ĐANG ở đỉnh màn ảo nhận URL mới ·
# rồi K3 đưa app của ô về lại đỉnh. Đọc: task ở màn ảo hay rơi về display 0, display 0 có sự kiện focus không, phiên nhạc.
PKG=${PKG:-com.google.android.apps.youtube.music}
HOST=${HOST:-music.youtube.com}
BC=vn.vietmap.live/vn.vietmap.live.MainActivity
snap() {
  echo "--- [$1] $(date +%T.%N | cut -c1-12) $(dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+')"
  am stack list
  dumpsys activity activities | grep -E 'mResumedActivity' | head -3
  echo "pidX=$(pidof $PKG) pidVM=$(pidof vn.vietmap.live)"
  dumpsys media_session | grep -A16 "package=$PKG" | grep -E 'package=|active=|state=PlaybackState' | head -6
}
VD=$(am stack list | awk '/Stack id/ && /displayId=[1-9]/ {for(i=1;i<=NF;i++) if($i ~ /^displayId=/){split($i,a,"="); print a[2]; exit}}')
echo "VD=$VD PKG=$PKG"
[ -z "$VD" ] && { echo "GATE FAILED: không có màn ảo ô"; exit 1; }
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T"
snap start
su 2000 am force-stop $PKG; sleep 1; snap after-force-stop
K9A="am start --display $VD --windowingMode 1 -a android.intent.action.VIEW -d 'https://$HOST/watch?v=dQw4w9WgXcQ' -p $PKG -f 0x10000000"
echo "\$ $K9A"; su 2000 sh -c "$K9A"; sleep 10; snap after-k9-cold
K9B="am start --display $VD --windowingMode 1 -a android.intent.action.VIEW -d 'https://$HOST/watch?v=kJQP7kiw5Fk' -p $PKG -f 0x10000000"
echo "\$ $K9B"; su 2000 sh -c "$K9B"; sleep 10; snap after-k9-warm
echo "\$ am start --display $VD -n '$BC'   (K3: app của ô lên lại)"; su 2000 am start --display $VD -n "$BC"; sleep 5; snap after-k3
echo '--- events'
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_|screen_toggled)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound|am_on_top_resumed|am_activity_launch_time'
