#!/usr/bin/env bash
source <scratchpad>/lane2-286/kc/lib.sh
a shell media volume --stream 3 --set 15 >/dev/null 2>&1   # âm lượng nhạc về như lúc đầu (15/15, đo ở s5)
restore
home; sleep 3
"$ADB" -s $SERIAL install -r $REPO/apk/Kachi-2.85-release.apk 2>&1 | tail -1
a11y_rebind && echo "a11y bound lại (chuỗi khoá = bản sao lưu)"
a shell settings get secure enabled_accessibility_services | tr -d '\r'
a shell dumpsys package $PKG | grep -E "versionName|versionCode|flags=\[" | head -3
a shell "cd $DD && sha256sum shared_prefs/*" | tr -d '\r' | sort > $K/backup/prefs-final.sha
if diff $K/backup/prefs.sha $K/backup/prefs-final.sha >/dev/null; then echo "sha prefs cuối khớp $(wc -l < $K/backup/prefs.sha)/$(wc -l < $K/backup/prefs-final.sha)"; else echo "sha prefs cuối LỆCH:"; diff $K/backup/prefs.sha $K/backup/prefs-final.sha; fi
a shell "ls $DD/shared_prefs/kachi_test_bridge.xml" 2>&1 | tr -d '\r'
a shell "pm path $PKG" | tr -d '\r' | sed 's/package://' | while read p; do a shell sha256sum "$p" | tr -d '\r' | cut -c1-16; done
shasum -a 256 $REPO/apk/Kachi-2.85-release.apk | cut -c1-16
