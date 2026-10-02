#!/system/bin/sh
# E6-kill: giết Kachi kiểu BYD (kill -9, không force-stop) khi một app đang sau màn nhà; đo bao lâu màn nhà lên lại.
OUT=/data/local/tmp/kprobe-kill; rm -rf $OUT; mkdir -p $OUT
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T vm=$(pidof vn.vietmap.live) kachi=$(pidof com.byd.launcher)" > $OUT/meta.txt
am stack list > $OUT/before.txt
kill -9 $(pidof com.byd.launcher)
i=0
while [ $i -lt 40 ]; do
  am stack list > $OUT/k$(printf %02d $i).txt
  top=$(am stack list | grep -A2 "displayId=0 " | grep "visible=true" | head -1 | sed -E 's/.*taskId=[0-9]+: ([^ ]+).*/\1/')
  echo "$(date +%T.%N | cut -c1-12) top=$top" >> $OUT/timeline.txt
  sleep 0.5
  i=$((i+1))
done
echo "after: vm=$(pidof vn.vietmap.live) kachi=$(pidof com.byd.launcher)" >> $OUT/meta.txt
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound' > $OUT/events.txt
logcat -d -v threadtime -T "$T" -s KachiBehind:V KachiReady:V > $OUT/kachi.txt
