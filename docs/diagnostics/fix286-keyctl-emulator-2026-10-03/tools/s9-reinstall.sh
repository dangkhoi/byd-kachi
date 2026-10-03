#!/usr/bin/env bash
source <scratchpad>/lane2-286/kc/lib.sh
"$ADB" -s $SERIAL install -r $REPO/app/build/outputs/apk/vehicleTest/app-vehicleTest.apk 2>&1 | tail -1
shasum -a 256 $REPO/app/build/outputs/apk/vehicleTest/app-vehicleTest.apk | cut -c1-16
bridge_on
lc_clear; home; sleep 3
a11y_rebind && echo "a11y bound"
cat_prefs clusternav_prefs | grep -E "voicekey_bindings"
# Đảo kính (VOLUME DOWN đang gán ctl:trunk:open) ⇒ gán lại đảo kính qua UI rồi bấm: câu ngắn mới
a shell am start -n $HOME_ACT --es open_settings_group keys >/dev/null; sleep 3
bash $K/s4-bind.sh "VOLUME DOWN (mã 25)" "Kính" "Bật/tắt Kính lái (đảo)" flip2 | grep -E "tap|voicekey"
a shell input keyevent 4; sleep 1; home; sleep 2
lc_clear; key 114; sleep 0.3; shot flip2-toast; sleep 2
lc_dump $K/flip2.log; grep -E "KeyCtl|voice-key" $K/flip2.log
# Một lần bấm gió +1 ở bản mới
lc_clear; key 115; sleep 0.3; shot press2-toast; sleep 2
lc_dump $K/press2.log; grep -E "KeyCtl|voice-key" $K/press2.log
