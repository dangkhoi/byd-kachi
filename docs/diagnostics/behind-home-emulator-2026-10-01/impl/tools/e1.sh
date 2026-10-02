#!/system/bin/sh
# E1/T-M1 trên máy (chạy bằng `adb shell sh /data/local/tmp/e1.sh <tag> <text>`): chụp `am stack list` NGUYÊN BẢN
# trước/trong/sau một lượt "mở X vào ô n" (đường giọng nói thật qua cầu kiểm thử), + logcat events + KachiBehind.
TAG=$1; TEXT=$2; OUT=/data/local/tmp/kprobe-$TAG; rm -rf $OUT; mkdir -p $OUT
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T" > $OUT/meta.txt
echo "pid_vietmap_before=$(pidof vn.vietmap.live) pid_clock_before=$(pidof com.google.android.deskclock) pid_kachi=$(pidof com.byd.launcher)" >> $OUT/meta.txt
am stack list > $OUT/s00-before.txt
am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd say --es text "$TEXT" > $OUT/say.txt 2>&1
i=1
while [ $i -le 40 ]; do
  am stack list > $OUT/s$(printf %02d $i).txt
  sleep 0.25
  i=$((i+1))
done
sleep 2
am stack list > $OUT/s99-after.txt
echo "pid_vietmap_after=$(pidof vn.vietmap.live) pid_clock_after=$(pidof com.google.android.deskclock) pid_kachi=$(pidof com.byd.launcher)" >> $OUT/meta.txt
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound' > $OUT/events.txt
logcat -d -v threadtime -T "$T" -s KachiBehind:V VdAppHost:V KachiTest:V > $OUT/kachi.txt
dumpsys activity activities | grep -E 'mResumedActivity|mFocusedApp' > $OUT/resumed.txt
