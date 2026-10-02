#!/system/bin/sh
# T-M1 (mồ côi): giết Kachi (kill -9) ĐÚNG lúc stack giữ chỗ đã dựng mà chưa gỡ ⇒ giữ chỗ mồ côi qua lần chết.
OUT=/data/local/tmp/kprobe-orphan; rm -rf $OUT; mkdir -p $OUT
T=$(date '+%m-%d %H:%M:%S.000'); echo "T0=$T vm=$(pidof vn.vietmap.live) kachi=$(pidof com.byd.launcher)" > $OUT/meta.txt
am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd say --es text "$1" > $OUT/say.txt 2>&1
i=0; hit=0
while [ $i -lt 600 ]; do
  am stack list > $OUT/poll.txt
  if grep -q BehindAnchorActivity $OUT/poll.txt; then kill -9 $(pidof com.byd.launcher); cp $OUT/poll.txt $OUT/at-kill.txt; hit=1; break; fi
  i=$((i+1))
done
echo "hit=$hit polls=$i killed_at=$(date +%T.%N | cut -c1-12)" >> $OUT/meta.txt
sleep 10
am stack list > $OUT/after10s.txt
echo "after: vm=$(pidof vn.vietmap.live) kachi=$(pidof com.byd.launcher)" >> $OUT/meta.txt
logcat -b events -d -v threadtime -T "$T" | grep -E ' (am_|wm_)' | grep -vE 'am_pss|am_mem|am_low_memory|am_meminfo|am_uid_|am_proc_bound' > $OUT/events.txt
logcat -d -v threadtime -T "$T" -s KachiBehind:V > $OUT/kachi.txt
