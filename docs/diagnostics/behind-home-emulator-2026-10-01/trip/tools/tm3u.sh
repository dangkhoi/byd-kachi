# T-M3'' — playFromUri / playFromSearch với ID KHÁC bài đang phát (lượt tm3s dùng nhầm ID của chính bài đang phát).
PKG=com.google.android.apps.youtube.music
MS() { su 2000 sh -c "CLASSPATH=/data/local/tmp/kprobe_ms.dex app_process /system/bin MS $*"; }
st() { echo "--- [$1] $(date +%T.%N | cut -c1-12)"; am stack list | grep -E 'Stack id|taskId' | sed -E 's/ bounds=\[[^ ]*//; s/topActivity=.*//'; MS list; }
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T"
st start
echo "\$ MS uri $PKG https://music.youtube.com/watch?v=9bZkp7q19f0"; MS uri $PKG https://music.youtube.com/watch?v=9bZkp7q19f0; sleep 15; st after-uri
echo "\$ MS search $PKG 'son tung mtp'"; MS search $PKG "'son tung mtp'"; sleep 15; st after-search
echo "\$ MS uri $PKG https://music.youtube.com/playlist?list=RDCLAK5uy_kmPRjHDECIcuVwnKsx2Ng7fyNgFKWNJFs"; MS uri $PKG "'https://music.youtube.com/playlist?list=RDCLAK5uy_kmPRjHDECIcuVwnKsx2Ng7fyNgFKWNJFs'"; sleep 15; st after-uri-playlist
echo '--- events'
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound|am_on_top_resumed|am_activity_launch_time'
