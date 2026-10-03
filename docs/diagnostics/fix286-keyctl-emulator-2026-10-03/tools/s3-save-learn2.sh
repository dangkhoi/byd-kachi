#!/usr/bin/env bash
source <scratchpad>/lane2-286/kc/lib.sh
tap_exact learnvu-1 "Lưu"; sleep 2
ui learnvu-2
# học thêm VOLUME_DOWN (linux 114 → 25) — núm vặn chiều ngược
tap_exact learnvu-2 "Học phím mới…" || exit 1
sleep 2; lc_clear; key 114; sleep 2
a logcat -d -s 'NavAccess:*' | grep -E "onKeyEvent|learned"
ui learnvd-1
texts learnvd-1 | grep -v "^'' |" | tail -4
tap_exact learnvd-1 "Lưu"; sleep 2
ui learn-done; shot learn-done
cat_prefs clusternav_prefs | grep -E "voicekey_custom_buttons|voicekey_bindings"
