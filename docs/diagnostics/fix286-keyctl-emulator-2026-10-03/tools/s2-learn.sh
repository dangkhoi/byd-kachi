#!/usr/bin/env bash
# Học phím qua đường THẬT "Học phím mới…" của Cài đặt › Nút vật lý. $1 = mã linux, $2 = tên tệp.
source <scratchpad>/lane2-286/kc/lib.sh
LK=${1:-115}; NAME=${2:-learn}
a shell am start -n $HOME_ACT --es open_settings_group keys >/dev/null; sleep 3
ui ${NAME}-0
tap_exact ${NAME}-0 "Học phím mới…" || exit 1
sleep 2; lc_clear; key $LK; sleep 2
a logcat -d -s 'NavAccess:*' | grep -E "onKeyEvent|learned"
ui ${NAME}-1; shot ${NAME}-1
texts ${NAME}-1 | grep -v "^'' |" | tail -8
