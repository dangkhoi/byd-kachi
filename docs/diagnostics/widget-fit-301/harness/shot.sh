#!/bin/bash
# shot.sh <name> <grid> <lang> <slot0..5>
S=${WFIT_DIR:?set WFIT_DIR (thư mục làm việc: base/ prefs + mkprefs.py)}
ADB="$HOME/Library/Android/sdk/platform-tools/adb -s emulator-5556"
P=com.byd.launcher
name=$1; grid=$2; lang=$3; shift 3
OUT=${OUTDIR:-$S/before}; mkdir -p $OUT
python3 $S/mkprefs.py $S/base/shared_prefs/kachi_workspace.xml $S/ws.xml "$grid" "$lang" "$@"
printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"lang\">%s</string>\n</map>\n" "$lang" > $S/lang.xml
$ADB push $S/ws.xml /data/local/tmp/ws.xml >/dev/null 2>&1; $ADB push $S/lang.xml /data/local/tmp/lang.xml >/dev/null 2>&1
$ADB push $S/apply.sh /data/local/tmp/apply.sh >/dev/null; $ADB shell sh /data/local/tmp/apply.sh
$ADB shell am start -n $P/com.byd.clusternav.launcher.KachiHomeActivity >/dev/null
sleep 4
[ -n "$DUMMY" ] && $ADB shell am broadcast -a com.byd.clusternav.vehicletest.DUMMY_CAR -p $P --ez on true >/dev/null && sleep 3
got=$($ADB shell run-as $P cat shared_prefs/kachi_workspace.xml | grep -c "QA2__grid_layout\">$grid<")
$ADB exec-out screencap -p > $OUT/$name.png
$ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $ADB exec-out cat /sdcard/ui.xml > $OUT/$name.xml
echo "$name grid_ok=$got"
