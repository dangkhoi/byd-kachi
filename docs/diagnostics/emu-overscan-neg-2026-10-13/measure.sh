#!/bin/bash
# usage: measure.sh <label> <pkg> <overscanTop(positive number = negative applied)>
ADB="$HOME/Library/Android/sdk/platform-tools/adb -s emulator-5554"
OUT="$(cd "$(dirname "$0")" && pwd)"
S="$(mktemp -d)"; trap 'rm -rf "$S"' EXIT
L=$1; PKG=$2; T=$3
case $T in 0) CMD="wm overscan 0,0,0,0 -d 1";; p*) CMD="wm overscan 0,${T#p},0,0 -d 1";; *) CMD="wm overscan 0,-$T,0,0 -d 1";; esac
F=$OUT/$L-os$T.txt
{ echo "### $(date '+%F %T') case=$L pkg=$PKG cmd: $CMD"
  $ADB shell $CMD 2>&1; echo "rc=$?"; sleep 2
  echo "--- wm size/overscan readback"; $ADB shell wm size -d 1; $ADB shell wm density -d 1
  echo "--- DisplayFrames d1"; $ADB shell dumpsys window displays | awk '/Display: mDisplayId=1/{f=1} f&&/DisplayFrames/{g=1} g{print} g&&/mRotatedDisplayInfoOverscan/{exit}'
  echo "--- am stack list (display 1)"; $ADB shell am stack list | grep -A2 "displayId=1" | grep -vE "configuration="
  echo "--- windows of $PKG on display 1"
  $ADB shell dumpsys window windows | awk -v p="$PKG" '/^  Window #/{f=0} /^  Window #/ && index($0,p){f=1} f' | grep -E "Window #|mDisplayId|fl=|vsysui|sysui|Frames:|content=|decor=|mFrame=|Cur insets|mAttrs|ty=|mWindowFrames|frame=" 
} > $F 2>&1
$ADB shell screencap -d 1 -p /sdcard/c.png && $ADB pull /sdcard/c.png $S/c.png >/dev/null && sips -Z 960 $S/c.png --out $OUT/$L-os$T.png >/dev/null
grep -E "mFrame=|content=|Frames:|mUnrestricted|mContent=" $F | head -8
