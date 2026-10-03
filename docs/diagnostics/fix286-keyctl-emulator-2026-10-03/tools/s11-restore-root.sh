#!/usr/bin/env bash
source <scratchpad>/lane2-286/kc/lib.sh
"$ADB" -s $SERIAL root 2>&1 | head -1; sleep 3; "$ADB" -s $SERIAL wait-for-device
a shell id | tr -d '\r' | cut -c1-30
a shell "cd $DD && sha256sum shared_prefs/*" | tr -d '\r' | sort > $K/backup/prefs-mid.sha
echo "-- lệch TRƯỚC khi trả (do harness + E2E):"; diff $K/backup/prefs.sha $K/backup/prefs-mid.sha | grep '^[<>]' | sed 's/^\(.\) [0-9a-f]* /\1 /'
restore
home; sleep 3
a shell "ls $DD/shared_prefs/" | tr -d '\r' | tr '\n' ' '; echo
cat_prefs clusternav_prefs | grep -E "voicekey_bindings|voicekey_custom|voicekey_enabled|voice_wake_enabled"
a shell "cd $DD && sha256sum shared_prefs/*" | tr -d '\r' | sort > $K/backup/prefs-final.sha
if diff $K/backup/prefs.sha $K/backup/prefs-final.sha >/dev/null; then echo "sha prefs cuối (sau HOME) khớp $(wc -l < $K/backup/prefs.sha)/$(wc -l < $K/backup/prefs-final.sha)"; else echo "sha prefs cuối (sau HOME) lệch:"; diff $K/backup/prefs.sha $K/backup/prefs-final.sha | grep '^[<>]' | sed 's/^\(.\) [0-9a-f]* /\1 /'; fi
