#!/usr/bin/env bash
# isa-map-sendarr-2606.sh — GỬI đúng khuôn 12 ô map-ISA (`sendIsaDataToAdas(int[12])`) rồi đọc lại CỔNG coding ADAS · fw 2606
#
# Mục đích (RE docs/diagnostics/re-map-speedlimit-2606-2026-10-10.md §6.2 + §10): các lượt test trước ghi km/h THÔ,
# từng ô RỜI (§5) ⇒ chưa hợp lệ. Lượt này gửi MỘT lời gọi `int[12]` đúng mã `km/h÷5` qua op `setisa` MỚI của cầu
# kiểm thử, để PHÂN BIỆT "sai khuôn" với "ADAS bị coding-lock" (`ADAS_TSR_SPEED_LIMIT_MAP_CONFIG 0x4320002A`=1, chỉ
# `2` = dùng bản đồ). Quan sát biển ĐỎ/XÁM trên cụm + HUD BẰNG MẮT (IVI không đọc được `0x2B400020`/`0x2370002E`, §3).
#
# ⚠⚠ GHI VÀO ĐẦU VÀO ECU ADAS — CHỈ chạy khi: xe ĐỖ (số P), tốc độ 0, phanh tay, ACC/ISLC TẮT, OWNER CÓ MẶT và đồng ý.
# Script tự KIỂM speed=0 (bắt buộc đọc được) + gear=P(3) (null ⇒ owner gõ PARKED-NO-GEAR) trước khi cho gửi, và đòi gõ tay
# chuỗi `ISAMAP`. Luôn có lượt TRẢ (mảng invalid) SAU KHI đã gửi; Ctrl-C trước khi gửi ⇒ thoát, KHÔNG ghi gì (kể cả mảng trả).
# KHÔNG auto-run, KHÔNG lặp — mỗi lần chạy = một lượt gửi + một lượt trả.
#
# Điều kiện: Kachi chạy + BẬT «Chế độ kiểm thử qua adb» (Cài đặt › Hệ thống & quyền › Nâng cao; người trên xe bật, tự tắt 60').
# Cách dùng:
#   VEH=<ip-xe>:5555 ./scripts/vehicle/isa-map-sendarr-2606.sh                         # tải thử T1 (50 km/h, bản đồ active)
#   VEH=<ip-xe>:5555 DATA="10,2,2,0,8191,2,31,31,2,255,1,1" ./scripts/vehicle/isa-map-sendarr-2606.sh
# Mã 12 ô (RE §1.2): [0]speed(km/h÷5:50→10) [1]time_cond [2]cur_time_cond [3]fork_ramp [4]ramp_dist(8191=invalid)
#   [5]limit_type [6]rain(km/h÷5) [7]snow(km/h÷5) [8]road_type [9]country(255=invalid) [10]unit(1=km/h) [11]map_status(1=active)
# Kết quả: logs/oncar-isamap-<ngày-giờ>/ (logs/ không vào git).
set -uo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
VEH="${VEH:?đặt VEH=<ip-xe>:5555}"
# Tải thử T1 (RE §6.2): 50 km/h rõ ràng, bản đồ ACTIVE, đơn vị km/h, quốc gia invalid. Ghi đè bằng DATA=.
DATA="${DATA:-10,2,2,0,8191,2,31,31,2,255,1,1}"
# Mảng TRẢ / XOÁ (RE §6.2): map status INVALID (0) + mọi giới hạn 0.
RESTORE="${RESTORE:-31,2,2,0,8191,0,0,0,0,255,1,0}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/logs/oncar-isamap-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
LOG="$OUT/session.log"
BRIDGE="com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge"

say() { echo "$*" | tee -a "$LOG"; }

# Một lượt cầu kiểm thử `hal` → in phần data="…" của lời đáp (một dòng).
hal() {
  local raw
  raw="$("$ADB" -s "$VEH" shell am broadcast -n "$BRIDGE" -a com.byd.launcher.TEST --es cmd hal "$@" 2>&1)"
  python3 -c 'import re,sys; t=sys.stdin.read(); m=re.search(r"data=\"(.*)\"", t, re.S); print((m.group(1) if m else t).replace("\n"," ")[:500])' <<<"$raw"
}

# Đọc một getter (value=…) → in giá trị thô.
get_val() {
  local dev="$1" m="$2"
  hal --es op get --es dev "$dev" --es m "$m" | python3 -c 'import re,sys;t=sys.stdin.read();m=re.search(r"\"value\":\"?([^\",}]*)",t);print(m.group(1) if m else t)'
}

preflight() {
  "$ADB" connect "$VEH" >/dev/null 2>&1
  local v fw; v="$("$ADB" -s "$VEH" shell dumpsys package com.byd.launcher | grep -m1 versionName | tr -d '\r ')"
  fw="$("$ADB" -s "$VEH" shell getprop ro.build.version.incremental | tr -d '\r')"
  say "== $(date '+%F %T') · xe $VEH · Kachi $v · fw $fw"
  local probe; probe="$(hal --es op getid --es dev BYDAutoBodyworkDevice --es m 594542592)"
  if grep -q "test_mode_off" <<<"$probe"; then
    say "DỪNG: «Chế độ kiểm thử qua adb» đang TẮT — người trên xe bật (Kachi › Cài đặt › Hệ thống & quyền › Nâng cao) rồi chạy lại."
    exit 3
  fi
}

# Cổng AN TOÀN (cùng luật `isa-0x237-inject-2606.sh`, CLAUDE.md §4): tốc độ PHẢI đọc được và = 0 — không đọc được ⇒ DỪNG,
# không có đường thay thế. Số: đọc được ⇒ phải = P (getCurrentGear=3, TelemetryRegistry GEAR_P). KHÔNG đọc được (10/10 [ĐO]
# Seal fw 2606 trả null) ⇒ đóng an toàn, trừ khi owner nhìn cần số và gõ `PARKED-NO-GEAR` (ghi vào log).
# Soát 2.99 Pass 15 [P1]: bản trước cho qua khi tốc độ KHÔNG đọc được, và không có đường nào cho xe trả gear null.
safety_gate() {
  local gear speed a
  speed="$(get_val BYDAutoSpeedDevice getCurrentSpeed)"
  gear="$(get_val BYDAutoGearboxDevice getCurrentGear)"
  say "-- an toàn: gear=$gear (P=3) · speed=$speed km/h --"
  case "$speed" in
    ''|null|None) say "DỪNG: không đọc được tốc độ (getCurrentSpeed rỗng) — KHÔNG ghi (không có đường thay thế)."; exit 4 ;;
    0|0.0|0.00) ;;
    *) say "DỪNG: tốc độ KHÔNG bằng 0 (đọc speed=$speed). Dừng hẳn rồi chạy lại."; exit 4 ;;
  esac
  case "$gear" in
    ''|null|None)
      say "  ⚠ không đọc được số (getCurrentGear=$gear). Tốc độ = 0 đọc được."
      printf '  Owner nhìn cần số: xe ĐANG Ở P? Gõ PARKED-NO-GEAR để tiếp, khác ⇒ dừng: ' | tee -a "$LOG"
      read -r a; echo "$a" >>"$LOG"
      [ "$a" = "PARKED-NO-GEAR" ] || { say "DỪNG: owner không xác nhận P khi số không đọc được — KHÔNG ghi."; exit 4; }
      say "  [OK] đỗ: tốc độ=0 + owner xác nhận P (số không đọc được) — $(date '+%T')" ;;
    3) say "  [OK] đỗ: gear=P tốc độ=0" ;;
    *) say "DỪNG: số KHÔNG ở P (đọc gear=$gear). Đỗ xe, về P, kéo phanh tay rồi chạy lại. (ghi ADAS chỉ khi đỗ)"; exit 4 ;;
  esac
}

RESTORED=0
restore() {
  # Idempotent: trap INT/TERM/EXIT có thể gọi hai lần ⇒ chỉ MỘT lượt ghi mảng trả.
  [ "$RESTORED" = 1 ] && return 0; RESTORED=1
  say "-- TRẢ VỀ (mảng invalid/clear): $RESTORE --"
  say "  SETISA restore → $(hal --es op setisa --es data "$RESTORE" --ez auto_confirm true)"
  say "  GATE sau trả  → $(hal --es op getid --es dev BYDAutoADASDevice --es m ADAS_TSR_SPEED_LIMIT_MAP_CONFIG)"
}
# ⚠ Trap TRẢ chỉ được cài SAU khi đã gửi (xem dưới). Soát 2.99 Pass 15 [P1]: bản trước cài trap từ đầu ⇒ Ctrl-C ngay ở câu hỏi
# ISAMAP (chưa gửi gì, chưa xác nhận) vẫn GHI mảng trả vào ECU ADAS — một lượt ghi không qua cổng xác nhận.

preflight
safety_gate

# Đọc CỔNG coding TRƯỚC khi gửi (0x4320002A: 2=dùng bản đồ; 1/0/3=không).
say "-- CỔNG coding ADAS TRƯỚC khi gửi --"
say "  GATE 0x4320002A → $(hal --es op getid --es dev BYDAutoADASDevice --es m ADAS_TSR_SPEED_LIMIT_MAP_CONFIG)"

# Xác nhận tay TƯỜNG MINH (CLAUDE.md §4): không gửi nếu không gõ đúng ISAMAP.
say ""
say "SẮP GỬI 12 ô map-ISA vào ECU ADAS: [$DATA]"
printf 'Owner có mặt, xe đỗ P, đồng ý GỬI? Gõ ISAMAP để tiếp, Enter để huỷ: ' | tee -a "$LOG"
read -r CONFIRM
echo "$CONFIRM" >>"$LOG"
if [ "$CONFIRM" != "ISAMAP" ]; then
  say "HUỶ (không gõ ISAMAP) — không gửi gì. Thoát."
  exit 0
fi

say "-- GỬI (sendIsaDataToAdas int[12]) --"
# Từ đây đã có một lượt ghi ⇒ mọi lối ra (Ctrl-C · lỗi · hết script) đều phải đi qua lượt TRẢ.
trap 'say "(ngắt) chạy lượt trả…"; restore; exit 1' INT TERM
trap restore EXIT
say "  SETISA → $(hal --es op setisa --es data "$DATA" --ez auto_confirm true)"
say ""
say "⇒ NHÌN cụm + HUD: biển ĐỎ / biển XÁM có hiện không? (IVI không đọc được giá trị biển — xác nhận bằng MẮT)"
say "  GATE sau gửi  → $(hal --es op getid --es dev BYDAutoADASDevice --es m ADAS_TSR_SPEED_LIMIT_MAP_CONFIG)"
printf 'Quan sát xong, Enter để TRẢ VỀ mảng invalid: ' | tee -a "$LOG"; read -r _

restore
say "== xong · log: $OUT"
