#!/usr/bin/env bash
# "Thêm gán…" THẬT: $1 = nhãn nút (VD 'VOLUME UP (mã 24)'), $2 = nhóm, $3 = việc, $4 = tiền tố tệp
source <scratchpad>/lane2-286/kc/lib.sh
B="$1"; G="$2"; ACT="$3"; P="$4"
ui ${P}-0
tap_exact ${P}-0 "Thêm gán…" || exit 1; sleep 2
ui ${P}-1; shot ${P}-1
tap_exact ${P}-1 "$B" || { texts ${P}-1 | tail -15; exit 1; }; sleep 2
ui ${P}-2; shot ${P}-2
echo "-- bước 2:"; texts ${P}-2 | grep -v "^'' |" | tail -14
tap_exact ${P}-2 "$G" || exit 1; sleep 2
ui ${P}-3; shot ${P}-3
echo "-- bước 3:"; texts ${P}-3 | grep -v "^'' |" | tail -40
tap_exact ${P}-3 "$ACT" || exit 1; sleep 2
ui ${P}-4; shot ${P}-4
cat_prefs clusternav_prefs | grep -E "voicekey_bindings"
