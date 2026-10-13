#!/bin/bash
# owner.sh <outpng>: restore owner layout in Vietnamese + dummy on, screenshot
S=${WFIT2_DIR:?set WFIT2_DIR (thư mục có ws_vi.xml + lang_vi.xml)}
A="$HOME/Library/Android/sdk/platform-tools/adb -s emulator-5556"; P=com.byd.launcher
$A push $S/ws_vi.xml /data/local/tmp/ws.xml >/dev/null; $A push $S/lang_vi.xml /data/local/tmp/lang.xml >/dev/null
$A shell am force-stop $P
$A shell run-as $P sh -c "'cat /data/local/tmp/ws.xml > shared_prefs/kachi_workspace.xml; cat /data/local/tmp/lang.xml > shared_prefs/clusternav_lang.xml'"; $A shell am force-stop $P
$A shell am start -n $P/com.byd.clusternav.launcher.KachiHomeActivity >/dev/null; sleep 5
$A shell am broadcast -a com.byd.clusternav.vehicletest.DUMMY_CAR -p $P --ez on true >/dev/null; sleep 3
$A exec-out screencap -p > $1; $A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $A exec-out cat /sdcard/ui.xml > ${1%.png}.xml
