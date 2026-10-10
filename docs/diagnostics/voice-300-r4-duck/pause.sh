SERIAL=emulator-5556; PKG=com.byd.launcher; ADB=$HOME/Library/Android/sdk/platform-tools/adb
HERE="$(cd "$(dirname "$0")/../../../scripts/emulator" && pwd)"; OUT=/tmp/r4p; mkdir -p $OUT; source "$HERE/voice-common.sh"; MODE=runas
st() { adbs shell dumpsys media_session | grep -A20 "package=com.google.android.youtube" | grep -m1 -o 'state=PlaybackState {state=[0-9]'; }
adbs shell am start -a android.intent.action.VIEW -d "https://www.youtube.com/watch?v=kJQP7kiw5Fk" -p com.google.android.youtube >/dev/null; sleep 8; adbs shell input keyevent 126; sleep 4
echo "phát: $(st)"
adbs shell am start -n "$HOME_ACT" >/dev/null; sleep 2
bridge "--es cmd listen" >/dev/null; sleep 1.5; echo "đang nghe: $(st)"
bridge "--es cmd say --es text $(shq "${CMD:-dừng nhạc}")" | python3 -c 'import sys,json;d=json.load(sys.stdin);print("say:",d.get("replies"))'
sleep 16; echo "sau phiên: $(st)"
