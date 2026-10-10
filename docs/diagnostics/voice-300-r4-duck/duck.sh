#!/usr/bin/env bash
# R4 đo: YouTube đang phát → mở phiên nghe (cầu `listen`) → chụp focus/ducking/trạng thái phiên nhạc trong và sau.
set -u
SERIAL=emulator-5556; PKG=com.byd.launcher; ADB=$HOME/Library/Android/sdk/platform-tools/adb
HERE="$(cd "$(dirname "$0")/../../../scripts/emulator" && pwd)"; OUT=${OUT:-/tmp/r4}; MODE=runas
mkdir -p "$OUT"; source "$HERE/voice-common.sh"; MODE=runas
snap() { # $1 nhãn
  local t=$1
  echo "=== $t $(date +%T.%N | cut -c1-12)"
  adbs shell dumpsys media_session | grep -E "package=com.google.android.youtube|state=PlaybackState" | head -4
  adbs shell dumpsys audio | sed -n '/Audio Focus stack entries/,/^ *$/p' | head -12
  adbs shell dumpsys audio | grep -iE "ducked|muted players" | head -4
  adbs shell dumpsys audio | grep -E "AudioPlaybackConfiguration.*(u/pid:[0-9]+/[0-9]+).*" | grep -E "state:started" | head -6
}
[ "${SKIP_ENABLE:-}" = 1 ] || enable_test_mode
adbs shell am start -a android.intent.action.VIEW -d "https://www.youtube.com/watch?v=${VID:-kJQP7kiw5Fk}" -p com.google.android.youtube >/dev/null
sleep ${WARM:-20}
snap before
adbs shell am start -n "$HOME_ACT" >/dev/null; sleep 2
snap home
bridge "--es cmd listen"; echo
sleep 1.5; snap during1
sleep 2; snap during2
sleep ${AFTER:-14}; snap after
