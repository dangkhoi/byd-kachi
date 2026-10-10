SERIAL=emulator-5556; PKG=com.byd.launcher; ADB=$HOME/Library/Android/sdk/platform-tools/adb
HERE="$(cd "$(dirname "$0")/../../../scripts/emulator" && pwd)"; OUT=/tmp/r4k; mkdir -p $OUT; source "$HERE/voice-common.sh"; MODE=runas
adbs shell am start -n "$HOME_ACT" >/dev/null; sleep 2
bridge "--es cmd listen"; echo; sleep 1.5
echo "trước kill: $(adbs shell dumpsys media_session | grep -m1 -o 'state=PlaybackState {state=[0-9]')"
pid=$(adbs shell pidof com.byd.launcher | tr -d '\r'); echo "kill -9 $pid"; adbs shell am force-stop com.byd.launcher
sleep 2; echo "sau kill 2s: $(adbs shell dumpsys media_session | grep -m1 -o 'state=PlaybackState {state=[0-9]')"
adbs shell dumpsys audio | sed -n '/Audio Focus stack entries/,/^ *$/p' | grep -o "pack: [a-z.]* .*gain: [A-Z_]*" 
