#!/system/bin/sh
# B (Waze) thoát khỏi ô sau khi A đã ra sau màn nhà — chụp `am stack list` NGUYÊN BẢN mỗi ~0,2 s quanh lượt đặt tạm.
OUT=/data/local/tmp/kprobe-esc; rm -rf $OUT; mkdir -p $OUT
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T" > $OUT/meta.txt
am stack list > $OUT/s000.txt
am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd say --es text "mở waze vào ô 1" > $OUT/say.txt 2>&1
i=1
while [ $i -le 60 ]; do am stack list > $OUT/s$(printf %03d $i).txt; i=$((i+1)); done
sleep 3; am stack list > $OUT/s999-after.txt
echo "marks: $(grep behind_marks /data/data/com.byd.launcher/shared_prefs/clusternav_state.xml)" >> $OUT/meta.txt
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound|am_on_' > $OUT/events.txt
logcat -d -v threadtime -T "$T" -s KachiBehind:V > $OUT/kachi.txt
