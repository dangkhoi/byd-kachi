#!/usr/bin/env bash
source <scratchpad>/lane2-286/kc/lib.sh
lc_clear; home; sleep 2
a11y_rebind && echo "a11y bound"
a shell dumpsys accessibility | grep -E "Bound services" | head -1
echo "--- input keyevent 88 (tiêm)"
lc_clear; a shell input keyevent 88; sleep 2
a logcat -d -s 'NavAccess:*' | grep "onKeyEvent" || echo "0 dòng onKeyEvent (tiêm KHÔNG qua bộ lọc Hỗ trợ)"
echo "--- sendevent 165 trên qwerty2"
lc_clear; key 165; sleep 2
a logcat -d -s 'NavAccess:*' 'VoiceKeyLauncher:*' | grep -E "onKeyEvent|voice-key"
