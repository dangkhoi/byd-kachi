#!/usr/bin/env bash
# QA 3.1 R18 — lượt nghe bấm nút "Nói với xe" (UI, không cần cầu kiểm thử) với YouTube đang phát.
# CASE=base|knob|kill · N lượt · START âm lượng. In mốc STREAM_MUSIC trước / +1,5 s / sau; log thô ở $OUT.
set -u
SERIAL=${SERIAL:-emulator-5556}; A_BIN=$HOME/Library/Android/sdk/platform-tools/adb; A() { "$A_BIN" -s "$SERIAL" "$@"; }; A=A; CASE=${CASE:-base}; N=${N:-1}; START=${START:-10}; OUT=${OUT:-/tmp/qa}; TAG=${TAG:-x}
mkdir -p "$OUT"
vol() { "$A" shell dumpsys audio | grep -A5 '^- STREAM_MUSIC' | grep -o 'streamVolume:[0-9]*' | cut -d: -f2 | tr -d '\r'; }
# u/pid 10140 = uid YouTube trên AVD kachi_play (đọc từ dumpsys audio) — máy khác phải đổi.
yts() { "$A" shell dumpsys audio | grep -o 'u/pid:10140/[0-9]* -- state:[a-z]*' | grep -m1 -o 'state:[a-z]*'; }
"$A" shell media volume --stream 3 --set "$START" >/dev/null 2>&1
T0="$("$A" shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
for i in $(seq 1 "$N"); do
  [ "$(vol)" = "$START" ] || "$A" shell media volume --stream 3 --set "$START" >/dev/null 2>&1
  echo "[$TAG #$i $CASE] trước: vol=$(vol) yt=$(yts)"
  "$A" shell input tap 1662 44
  sleep 1.5; echo "[$TAG #$i] +1,5 s đang nghe: vol=$(vol) yt=$(yts)"
  case "$CASE" in
    knob) "$A" shell media volume --stream 3 --set ${KNOB:-6} >/dev/null 2>&1; sleep 0.3; echo "[$TAG #$i] vặn núm → ${KNOB:-6}: vol=$(vol)";;
    kill) "$A" shell am force-stop com.byd.launcher; sleep 2; echo "[$TAG #$i] force-stop +2 s: vol=$(vol) yt=$(yts)"
          "$A" shell am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity >/dev/null; sleep 4
          echo "[$TAG #$i] khởi động lại +4 s: vol=$(vol) yt=$(yts)";;
  esac
  sleep "${WAIT:-10}"; echo "[$TAG #$i] sau lượt: vol=$(vol) yt=$(yts)"
  sleep 2
done
"$A" shell logcat -d -v time -T "'$T0'" -s 'KachiVoiceTiming:*' 'KachiVoiceDuck:*' > "$OUT/$TAG-logcat.txt"
grep -E "sẵn sàng|mic mở|hạ nhạc|trả nhạc|đóng mic|không trả|orphan" "$OUT/$TAG-logcat.txt"
