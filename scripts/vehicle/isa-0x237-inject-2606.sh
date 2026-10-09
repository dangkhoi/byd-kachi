#!/usr/bin/env bash
# isa-0x237-inject-2606.sh — thử bơm giá trị đèn "dự báo giới hạn tốc độ" (khung CAN 0x237, tín hiệu 0x2370002E)
# vào cụm bằng TestDevice TEST_SIMULATE_DOWN (0xAA00020F) · fw 2606 · CHỈ KHI ĐỖ.
#
# Chuẩn bị + bằng chứng: docs/diagnostics/oncar-prep-2026-10-09-isa-can-odo.md §B1. Tóm tắt:
#   - [ĐO disasm fw 2606] `BusinessUi1::updateSpeedLimitValue` @0x13254c (lib64_libBydDataSource.so):
#       cổng: 0x12D0002A (mức nguồn) == 3 · cờ SLA trang bị (+0x2a3 = CAN 0x4320000E) == 1 · cờ +0x10cf == 0
#       v = CAN 0x2370002E (u8), online = CAN 0x23700000
#       v ∈ 2..25 ⇒ hiện, km/h = 5·v − 5 (v=11 ⇒ 50) · v ∈ 26..29 ⇒ 10·v − 130 · v = 30 ⇒ 82 (mã đặc biệt)
#       còn lại (0, 1, ≥ 31) hoặc online ≠ 1 ⇒ ẩn (item 587 = 2, item 588 = 0)
#     Chỉ ghi data-item 587/588 trong RAM của tiến trình cụm (không SetConfig/saveConfigXML) ⇒ không bền.
#   - [CHƯA BIẾT] TEST_SIMULATE_DOWN đi tới đâu sau HAL (MCU: chỉ luồng SPI của cụm, hay phát RA BUS CAN?) và
#     khuôn byte của nó. Script dùng khuôn cộng đồng [ĐOÁN] `[featureId BE 4][len 1][data]` = 23 70 00 2E 01 <v>.
#   - Khung 0x237 THẬT đang online [ĐO xe 08/10] ⇒ khung thật kế tiếp ghi đè giá trị bơm [SUY] ⇒ phải lặp HOLD lần.
#
# HAI PHA (CLAUDE.md §4 phạm vi tường minh + đường trả; §14 lệnh thô trên xe trước):
#   PHASE=check  (mặc định) CHỈ ĐỌC: test-mode Kachi · số P · tốc độ 0 · mức nguồn · 0x237 online · (DOORA=1:
#                đọc bố cục bit 0x237 trong /collect2/byd_datasource_config.xml qua navopen readcfg — chỉ đọc).
#   PHASE=inject GHI: lặp lại toàn bộ check (dừng nếu một mục không đọc được / không đạt) → owner gõ xác nhận →
#                bơm HOLD lần → hỏi người nhìn cụm → đọc lại → TRẢ (trap: dừng bơm, hỏi; còn hiện ⇒ bơm mã ẩn 0
#                một lần; vẫn còn ⇒ tắt máy/khởi động lại đầu xe).
#
# Phạm vi (CLAUDE.md §4): đúng MỘT feature-id 0x2370002E, đúng MỘT đường TestDevice 0xAA00020F; không chạm
# setting/ADAS/sendRegisterTable; không quét; không chạy khi xe không ở P hoặc tốc độ ≠ 0.
#
# Điều kiện: Kachi đang chạy + BẬT «Chế độ kiểm thử qua adb»; navopen-v4.jar (mặc định ../apks/navopen-v4.jar).
# Cách dùng:
#   VEH=<vehicle-ip>:5555 ./scripts/vehicle/isa-0x237-inject-2606.sh                      # chỉ đọc
#   VEH=<vehicle-ip>:5555 DOORA=1 ./scripts/vehicle/isa-0x237-inject-2606.sh              # + đọc bố cục bit
#   VEH=<vehicle-ip>:5555 PHASE=inject CODE=11 ./scripts/vehicle/isa-0x237-inject-2606.sh # bơm 50 km/h
# Kết quả: logs/oncar-isa237-<ngày-giờ>/ (logs/ không vào git).
set -uo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
VEH="${VEH:?đặt VEH=<vehicle-ip>:5555}"
PHASE="${PHASE:-check}"
CODE="${CODE:-11}"          # mã 0x2370002E; 11 ⇒ 50 km/h
HOLD="${HOLD:-5}"           # số lần bơm (cách 0,4 s) để thắng khung thật
DOORA="${DOORA:-0}"
# Senior review Pass 10 [P3]: CODE/HOLD phải là số nguyên trong dải u8 / hợp lý — chuỗi lạ làm `printf %02X` hỏng và frame rỗng.
case "$CODE" in ''|*[!0-9]*) echo "CODE phải là số nguyên 0..255 (đang: '$CODE')"; exit 2 ;; esac
[ "$CODE" -le 255 ] || { echo "CODE=$CODE vượt u8 (0x2370002E là u8)"; exit 2; }
case "$HOLD" in ''|*[!0-9]*) echo "HOLD phải là số nguyên 1..20 (đang: '$HOLD')"; exit 2 ;; esac
[ "$HOLD" -ge 1 ] && [ "$HOLD" -le 20 ] || { echo "HOLD=$HOLD ngoài 1..20"; exit 2; }
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NAVOPEN_JAR="${NAVOPEN_JAR:-$ROOT/../apks/navopen-v4.jar}"
OUT="$ROOT/logs/oncar-isa237-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
LOG="$OUT/session.log"
BRIDGE="com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge"
NAVCP="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"

# feature-id (thập phân) — tính từ hex ở KDoc trên
FID_POWER=315621418      # 0x12D0002A mức nguồn (3 = ON)
FID_237_ON=594542592     # 0x23700000 khung 0x237 online
FID_237_V=594542638      # 0x2370002E giá trị đèn dự báo
FID_SLA_EQ=1126170638    # 0x4320000E SLA trang bị (chỉ ghi log, có thể không đọc được từ Android)
GEAR_P=3                 # BYDAutoGearboxDevice.getCurrentGear: P=3 (TelemetryRegistry.kt:187, [ĐO xe =3 lúc đỗ])

say() { echo "$*" | tee -a "$LOG"; }
die() { say "DỪNG: $*"; exit 3; }

# Một lượt cầu kiểm thử `hal` → in nguyên JSON trong data="…".
hal() {
  local raw
  raw="$("$ADB" -s "$VEH" shell am broadcast -n "$BRIDGE" -a com.byd.launcher.TEST --es cmd hal "$@" 2>&1)"
  python3 -c 'import re,sys; t=sys.stdin.read(); m=re.search(r"data=\"(.*)\"", t, re.S); print((m.group(1) if m else t).replace("\n"," ")[:600])' <<<"$raw"
}
# Lấy một trường của JSON lời đáp (rỗng nếu không có / không phải JSON).
field() {
  python3 -c 'import json,sys
try: print(json.loads(sys.stdin.read()).get(sys.argv[1], ""))
except Exception: print("")' "$1"
}
getid() { hal --es op getid --es dev "$1" --es m "$2"; }
nav()   { "$ADB" -s "$VEH" shell "$NAVCP $*" 2>&1 | tee -a "$LOG"; }

# Mã 0x2370002E → câu người đọc (khớp disasm @0x13254c).
code_meaning() {
  local v="$1"
  if   [ "$v" -ge 2 ] && [ "$v" -le 25 ]; then echo "hiện $((5 * v - 5)) km/h"
  elif [ "$v" -ge 26 ] && [ "$v" -le 29 ]; then echo "hiện $((10 * v - 130)) km/h"
  elif [ "$v" -eq 30 ]; then echo "hiện mã đặc biệt 82"
  else echo "ẩn đèn"; fi
}

# ── PRE-CHECK (chỉ đọc) ──────────────────────────────────────────────────────────────────────
preflight() {
  "$ADB" connect "$VEH" >/dev/null 2>&1
  local ver fw
  ver="$("$ADB" -s "$VEH" shell dumpsys package com.byd.launcher | grep -m1 versionName | tr -d '\r ')"
  fw="$("$ADB" -s "$VEH" shell getprop ro.build.version.incremental | tr -d '\r')"
  say "== $(date '+%F %T') · xe $VEH · Kachi $ver · fw $fw"
  local probe; probe="$(getid BYDAutoBodyworkDevice "$FID_237_ON")"
  echo "$probe" | grep -q "test_mode_off" && die "Chế độ kiểm thử Kachi đang TẮT — bật «Chế độ kiểm thử qua adb» rồi chạy lại."
}

# Cổng đỗ: số P + tốc độ 0. Không đọc được bất kỳ mục nào ⇒ DỪNG (CLAUDE.md §4: không ghi khi không chắc đang đỗ).
park_guard() {
  local gj gear spj speed
  gj="$(hal --es op get --es dev BYDAutoGearboxDevice --es m getCurrentGear)"; say "  gear raw: $gj"
  gear="$(echo "$gj" | field value)"
  [ -n "$gear" ] || die "không đọc được số (getCurrentGear rỗng) — KHÔNG ghi."
  [ "$gear" = "$GEAR_P" ] || die "xe KHÔNG ở số P (gear=$gear, cần $GEAR_P) — KHÔNG ghi."
  spj="$(hal --es op get --es dev BYDAutoSpeedDevice --es m getCurrentSpeed)"; say "  speed raw: $spj"
  speed="$(echo "$spj" | field value)"
  [ -n "$speed" ] || die "không đọc được tốc độ (getCurrentSpeed rỗng) — KHÔNG ghi."
  case "$speed" in 0|0.0|0.00) ;; *) die "tốc độ ≠ 0 (speed=$speed) — KHÔNG ghi." ;; esac
  say "  [OK] đỗ: gear=P tốc độ=0"
}

read_set() {
  say "  power(0x12D0002A) = $(getid BYDAutoBodyworkDevice "$FID_POWER" | field value) (cần 3 = ON)"
  say "  0x237 online      = $(getid BYDAutoBodyworkDevice "$FID_237_ON" | field value) (cần 1)"
  say "  0x2370002E value  = $(getid BYDAutoBodyworkDevice "$FID_237_V" | field value)"
  say "  SLA trang bị(0x4320000E) = $(getid BYDAutoBodyworkDevice "$FID_SLA_EQ" | field value) (có thể bad_feature)"
}

door_a() {
  [ "$DOORA" = "1" ] || return 0
  [ -f "$NAVOPEN_JAR" ] || { say "  [DoorA] bỏ qua — không thấy $NAVOPEN_JAR"; return 0; }
  say "== DoorA (chỉ đọc): bố cục bit khung 0x237 trong byd_datasource_config.xml"
  "$ADB" -s "$VEH" push "$NAVOPEN_JAR" /data/local/tmp/navopen.jar >/dev/null 2>&1
  nav readcfg /collect2/byd_datasource_config.xml content://com.byd.car.server.provider.CarServiceProvider \
    | grep -iE "0237|2370|speed|limit|sla" | head -40
}

# Bơm một mã qua TestDevice 0xAA00020F (khuôn [featureId BE4][len1][data]) — xem cảnh báo [ĐOÁN] ở đầu tệp.
# Trả 0 khi MỌI lượt setbytes in `rc=` (HAL nhận); ≠ 0 khi navopen báo `device null` / `FAILED` / không in gì — Senior review
# Pass 10 [P2]: bản trước nuốt đầu ra (>/dev/null) ⇒ "bơm 5×" in ra kể cả khi TestDevice null, người nhìn cụm chờ một thứ chưa gửi.
inject_code() {
  local v="$1" hexv frame out ok=0 bad=0
  hexv="$(printf '%02X' "$v")"
  frame="23,70,00,2E,01,$hexv"
  say "  bơm 0x2370002E = $v ($(code_meaning "$v")) · wholeFrame=$frame · ${HOLD}×"
  [ -f "$NAVOPEN_JAR" ] || { say "  cần navopen để bơm (đặt NAVOPEN_JAR) — không bơm."; return 1; }
  "$ADB" -s "$VEH" push "$NAVOPEN_JAR" /data/local/tmp/navopen.jar >/dev/null 2>&1
  local i; for i in $(seq 1 "$HOLD"); do
    out="$("$ADB" -s "$VEH" shell "$NAVCP setbytes test AA00020F $frame" 2>&1)"; echo "$out" >> "$LOG"
    if echo "$out" | grep -q "rc="; then ok=$((ok + 1)); else bad=$((bad + 1)); say "  ⚠ lượt $i: $(echo "$out" | grep -m1 'setbytes\|device null\|FAILED\|ERROR' | tr -d '\r')"; fi
    sleep 0.4
  done
  say "  setbytes: $ok nhận (rc=) · $bad hỏng"
  [ "$bad" -eq 0 ] && [ "$ok" -gt 0 ]
}

ask() { local a; read -r -p "  ▶ $1 (ghi: y/n/mô tả): " a; say "  OBS: $1 → $a"; }

RESTORED=0
restore() {
  # Idempotent (Pass 10 [P3]): trap INT chạy restore rồi EXIT lại chạy lần hai ⇒ chỉ một lượt hỏi + một lượt bơm mã ẩn.
  [ "$RESTORED" = 1 ] && return 0; RESTORED=1
  say "-- TRẢ: dừng bơm --"
  ask "Cụm còn hiện đèn/giá trị dự báo giới hạn tốc độ không?"
  # Mã 0 (hoặc 1) ⇒ nhánh ẩn ở disasm; bơm một lần để dọn nhanh nếu khung thật chưa kịp ghi đè. Hỏng ⇒ chỉ báo (không die trong trap).
  inject_code 0 || say "  ⚠ bơm mã ẩn không được HAL nhận — chờ khung thật 0x237 ghi đè hoặc khởi động lại đầu xe."
  say "   nếu vẫn còn: TẮT MÁY / khởi động lại đầu xe (frame thật 0x237 hoặc reboot dọn sạch — giá trị bơm chỉ ở RAM)."
}

case "$PHASE" in
  check)
    preflight
    say "== PHA ĐỌC"
    park_guard
    read_set
    door_a
    ;;
  inject)
    preflight
    say "== PHA GHI THỬ (đỗ + owner đồng ý)"
    park_guard
    say "-- trạng thái TRƯỚC --"; read_set
    on="$(getid BYDAutoBodyworkDevice "$FID_237_ON" | field value)"
    [ "$on" = "1" ] || say "  ⚠ 0x237 online=$on ≠ 1 — cụm có thể bỏ qua giá trị bơm (vẫn thử theo yêu cầu)."
    read -r -p "Xe ĐỖ (P, tốc độ 0), owner đã nói 'ok thử ghi 0x237'? Gõ ISA237 để bơm: " ok
    [ "$ok" = "ISA237" ] || { say "Huỷ (không bơm gì)."; exit 0; }
    trap restore EXIT INT TERM
    inject_code "$CODE" || die "TestDevice không nhận setbytes (xem dòng ⚠ trên + $LOG) — không có gì được bơm, dừng để không hỏi người nhìn cụm vô ích."
    sleep 1
    ask "Cụm: biển/đèn 'dự báo giới hạn tốc độ' có hiện $(code_meaning "$CODE") không?"
    say "-- trạng thái SAU --"; read_set
    say "Xong — trap sẽ dừng bơm + dọn."
    ;;
  *) die "PHASE phải là check | inject" ;;
esac
say "Log: $LOG"
