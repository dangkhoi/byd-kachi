#!/usr/bin/env bash
# R4 (owner 11/10) đo: YouTube phát → đặt STREAM_MUSIC = $START → cầu `listen` → chỉ số STREAM_MUSIC + trạng thái phiên nhạc
# trước / trong / sau. CASE=base (thường) | knob (vặn núm giữa lượt nghe ⇒ phải GIỮ mức người dùng) | kill (force-stop giữa
# lượt nghe ⇒ lần khởi động sau phải trả mức cũ) | say (bắn lệnh CMD qua cầu trong lúc nghe, vd "dừng nhạc"). Chỉ máy ảo (voice-common.sh từ chối thiết bị thật).
set -u
SERIAL=${SERIAL:-emulator-5556}; PKG=com.byd.launcher; ADB=$HOME/Library/Android/sdk/platform-tools/adb
HERE="$(cd "$(dirname "$0")/../../../scripts/emulator" && pwd)"; OUT=${OUT:-/tmp/r4v}; mkdir -p "$OUT"
source "$HERE/voice-common.sh"; MODE=runas
CASE=${CASE:-base}; START=${START:-10}
vol() { adbs shell dumpsys audio | grep -A5 '^- STREAM_MUSIC' | grep -o 'streamVolume:[0-9]*' | cut -d: -f2 | tr -d '\r'; }
yt() { adbs shell dumpsys media_session | grep -A14 'package=com.google.android.youtube' | grep -m1 -o 'state=PlaybackState {state=[0-9]' | grep -o '[0-9]$'; }
focus() { adbs shell dumpsys audio | sed -n '/Audio Focus stack entries/,/^ *$/p' | grep -o 'pack: [a-z.]*' | tr '\n' ' '; }
snap() { echo "$(date +%T.%N | cut -c1-12) $1: STREAM_MUSIC=$(vol) · YouTube state=$(yt) · focus: $(focus)"; }
[ "${SKIP_ENABLE:-}" = 1 ] || enable_test_mode
adbs shell am start -f 0x10008000 -a android.intent.action.VIEW -d "https://www.youtube.com/watch?v=${VID:-kJQP7kiw5Fk}" -p com.google.android.youtube >/dev/null
sleep ${WARM:-20}
[ "$(yt)" = 3 ] || { adbs shell input keyevent 126; sleep 3; }   # YouTube 21.35 hay dừng ở 0:2x sau khi mở bằng link ⇒ PLAY
adbs shell media volume --stream 3 --set "$START" >/dev/null 2>&1
[ "${STAY_YT:-1}" = 1 ] || { adbs shell am start -n "$HOME_ACT" >/dev/null; sleep 2; }   # mặc định ở lại YouTube: bản 21.35 không Premium dừng khi về HOME
adbs logcat -c 2>/dev/null
snap before
bridge "--es cmd listen" >/dev/null
sleep 1.5; snap during
case "$CASE" in
  knob) adbs shell media volume --stream 3 --set 6 >/dev/null 2>&1; sleep 0.5; snap "knob→6" ;;
  say) bridge "--es cmd say --es text $(shq "${CMD:-dừng nhạc}")" >/dev/null; sleep 0.5; snap "say «${CMD:-dừng nhạc}»" ;;
  kill) adbs shell am force-stop "$PKG"; sleep 2; snap "sau force-stop (chưa khởi động lại)"
        adbs shell am start -n "$HOME_ACT" >/dev/null; sleep 4; snap "sau khởi động lại" ;;
esac
sleep 6; snap "+6 s (giữa lượt nối)"; sleep ${AFTER:-16}; snap after
adbs logcat -d -s KachiVoiceDuck:* KachiVoiceTiming:* | grep -E "KachiVoiceDuck|nhạc" | head -20
