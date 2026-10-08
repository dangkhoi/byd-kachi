#!/usr/bin/env bash
# isa-speedlimit-2606.sh — có cửa nào đẩy GIỚI HẠN TỐC ĐỘ vào ô biển tốc độ GỐC của cụm (thay vì Kachi tự vẽ)? · fw 2606
#
# Runbook: docs/diagnostics/oncar-runbook-2.96.md §B. Bối cảnh + bằng chứng cũ:
#   - [ĐO xe 2026-08-15, fw 2602] biển trên cụm = data-item 564 nuôi bằng CAN ADAS (`ADAS_SLA_OUTPUT_SPEED_LIMIT`) qua OS
#     riêng của cụm; MỌI id HAL ghi tốc độ (kể cả `STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET`) rc=0 mà cụm KHÔNG đổi
#     (docs/archive/diagnostics/oncar-inject-sweep-2026-08-15.md §S3).
#   - [ĐO nguồn fw 2606] MỚI: 8 setter `BYDAutoSettingDevice.setIsaMap*` (→ `SETTING_ISA_MAP_*`, không kiểm dải giá trị) +
#     đèn mới "dự báo giới hạn tốc độ" trên cụm đọc CAN `0x2370002E` khi khung `0x237` online
#     (../firmware/fw-2606/RE-2606-findings.md §1.3). [ĐOÁN] IVI → ECU ADAS → 0x237 → cụm.
#
# HAI PHA (CLAUDE.md §14: lệnh thô trên xe trước, mã sau; §4: mọi lệnh ghi có phạm vi + đường trả):
#   PHASE=read  (mặc định)  CHỈ ĐỌC — an toàn, làm lúc nào cũng được (đỗ hoặc đang chạy; người ngồi ghế phụ chạy).
#   PHASE=watch             CHỈ ĐỌC lặp 1 s trong SECS giây — chạy lúc XE ĐI QUA BIỂN TỐC ĐỘ để thấy giá trị ADAS đổi.
#   PHASE=write             GHI THỬ — CHỈ khi: xe ĐỖ, số P, phanh tay, owner nói "ok thử ghi". Mỗi bước ghi 1 giá trị,
#                           chờ người quan sát cụm/HUD trả lời, rồi TRẢ VỀ 0 ngay. Ctrl-C bất kỳ lúc nào ⇒ vẫn trả về 0.
#
# Điều kiện: Kachi đang chạy + BẬT "Chế độ kiểm thử qua adb" (Cài đặt › Hệ thống & quyền › Nâng cao) (người ngồi trên xe bật; tự tắt sau 60 phút).
# Cách dùng:
#   VEH=<ip-xe>:5555 ./scripts/vehicle/isa-speedlimit-2606.sh                 # đọc
#   VEH=<ip-xe>:5555 PHASE=watch SECS=120 ./scripts/vehicle/isa-speedlimit-2606.sh
#   VEH=<ip-xe>:5555 PHASE=write ./scripts/vehicle/isa-speedlimit-2606.sh     # hỏi xác nhận từng bước
# Kết quả: logs/oncar-isa-<ngày-giờ>/ (logs/ không vào git — không đưa ảnh/tọa độ chuyến đi vào repo).
set -uo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
VEH="${VEH:?đặt VEH=<ip-xe>:5555}"
PHASE="${PHASE:-read}"
SECS="${SECS:-120}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/logs/oncar-isa-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
LOG="$OUT/session.log"
BRIDGE="com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge"

say() { echo "$*" | tee -a "$LOG"; }

# Một lượt cầu kiểm thử `hal` → in phần data="…" của lời đáp (một dòng).
hal() {
  local raw
  raw="$("$ADB" -s "$VEH" shell am broadcast -n "$BRIDGE" -a com.byd.launcher.TEST --es cmd hal "$@" 2>&1)"
  python3 -c 'import re,sys; t=sys.stdin.read(); m=re.search(r"data=\"(.*)\"", t, re.S); print((m.group(1) if m else t).replace("\n"," ")[:400])' <<<"$raw"
}

read_id()  { local dev="$1" name="$2"; say "  READ  $dev $name → $(hal --es op getid --es dev "$dev" --es m "$name")"; }
write_id() { local dev="$1" name="$2" v="$3"; say "  WRITE $dev $name=$v → $(hal --es op setev --es dev "$dev" --es m "$name" --es args "$v" --ez auto_confirm true)"; }
write_m()  { local dev="$1" m="$2" v="$3"; say "  WRITE $dev $m($v) → $(hal --es op set --es dev "$dev" --es m "$m" --es args "$v" --ez auto_confirm true)"; }

preflight() {
  "$ADB" connect "$VEH" >/dev/null 2>&1
  local v; v="$("$ADB" -s "$VEH" shell dumpsys package com.byd.launcher | grep -m1 versionName | tr -d '\r ')"
  say "== $(date '+%F %T') · xe $VEH · Kachi $v · fw $("$ADB" -s "$VEH" shell getprop ro.build.version.incremental | tr -d '\r')"
  local probe; probe="$(hal --es op getid --es dev BYDAutoBodyworkDevice --es m 594542592)"
  if grep -q "test_mode_off" <<<"$probe"; then
    say "DỪNG: Chế độ kiểm thử của Kachi đang TẮT — người trên xe bật «Chế độ kiểm thử qua adb» (Kachi › Cài đặt › Hệ thống & quyền › Nâng cao) rồi chạy lại."
    exit 3
  fi
}

# Tập ĐỌC: nguồn biển thật (camera ADAS) · trạng thái ISA/TSR · khung 0x237 (đèn dự báo) · giá trị các ô ISA đang có.
read_set() {
  read_id BYDAutoAdasDevice      ADAS_SLA_OUTPUT_SPEED_LIMIT                 # số trên biển cụm (nguồn camera) — chỉ đọc
  read_id BYDAutoAdasDevice      ADAS_SLA_STATE                              # chế độ SLA (enum, không phải km/h)
  read_id BYDAutoAdasDevice      ADAS_TSR_SPEED_LIMIT_MAP_CONFIG             # TSR có dùng bản đồ không (2606)
  read_id BYDAutoAdasDevice      ADAS_INTELLIGENT_SPEED_LIMIT_INFORMATION_GRAY
  read_id BYDAutoAdasDevice      ADAS_INTELLIGENT_SPEED_LIMIT_CONTROL_GRAY
  read_id BYDAutoSettingDevice   SETTING_SPEED_LIMIT_CHANGE_SWITCH
  read_id BYDAutoSettingDevice   SETTING_SPEED_LIMIT_CHANGE_CONFIG
  read_id BYDAutoBodyworkDevice  594542592                                   # 0x23700000 khung 0x237 online?
  read_id BYDAutoBodyworkDevice  594542638                                   # 0x2370002E giá trị đèn dự báo
  read_id BYDAutoStatisticDevice STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET # đọc lại ô ISA (có thể không đọc được)
  read_id BYDAutoStatisticDevice STATISTICS_ISA_MAP_STATUS_SET
}

restore() {
  say "-- TRẢ VỀ 0 (mọi ô đã ghi trong phiên) --"
  write_id BYDAutoStatisticDevice STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET 0
  write_id BYDAutoStatisticDevice STATISTICS_ISA_MAP_STATUS_SET 0
  write_m  BYDAutoSettingDevice   setIsaMapSpeedLimit 0
  write_m  BYDAutoSettingDevice   setIsaMapSpeedLimitCarToPointDistance 0
  write_m  BYDAutoSettingDevice   setIsaMapSpeedLimitType 0
  read_set
}

ask() {  # ask "<câu hỏi>" → ghi câu trả lời người quan sát vào log
  local a; read -r -p "  ▶ $1 (ghi: y/n/mô tả): " a; say "  OBS: $1 → $a"
}

step() {  # step "<tên>" <lệnh ghi…>
  local name="$1"; shift
  say "== BƯỚC $name"
  "$@"
  sleep 5
  ask "Cụm: biển tốc độ gốc / đèn 'dự báo giới hạn' có hiện số mới không? HUD có đổi không?"
  read_set
}

w2() {
  write_id BYDAutoStatisticDevice STATISTICS_ISA_MAP_STATUS_SET 1
  write_id BYDAutoStatisticDevice STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET 60
}
w3() {
  write_m BYDAutoSettingDevice setIsaMapSpeedLimit 60
  write_m BYDAutoSettingDevice setIsaMapSpeedLimitCarToPointDistance 200
  write_m BYDAutoSettingDevice setIsaMapSpeedLimitType 1
}

preflight
case "$PHASE" in
  read)
    say "== PHA ĐỌC"; read_set
    "$ADB" -s "$VEH" logcat -d -v time | grep -E "BYDAutoSettingDevice|BYDAutoAdasDevice|BYDAutoStatisticDevice|HalSpeedSign" | tail -40 >> "$LOG"
    ;;
  watch)
    say "== PHA THEO DÕI ${SECS}s (đi qua biển tốc độ)"
    end=$(( $(date +%s) + SECS ))
    while [ "$(date +%s)" -lt "$end" ]; do
      say "$(date +%T) SLA=$(hal --es op getid --es dev BYDAutoAdasDevice --es m ADAS_SLA_OUTPUT_SPEED_LIMIT | grep -oE 'value=[^,; ]*' | head -1) 0x237=$(hal --es op getid --es dev BYDAutoBodyworkDevice --es m 594542592 | grep -oE 'value=[^,; ]*' | head -1) dựbáo=$(hal --es op getid --es dev BYDAutoBodyworkDevice --es m 594542638 | grep -oE 'value=[^,; ]*' | head -1)"
      sleep 1
    done
    ;;
  write)
    say "== PHA GHI THỬ"
    read -r -p "Xe ĐỖ, số P, phanh tay, owner đã nói 'ok thử ghi'? Gõ ISA để tiếp: " ok
    [ "$ok" = "ISA" ] || { say "Huỷ (không ghi gì)."; exit 0; }
    trap restore EXIT INT TERM
    say "-- trạng thái TRƯỚC"; read_set
    step "W1 · ô ISA hiện tại = 60 (thử lại trên 2606; 2602 đã inert)" \
      write_id BYDAutoStatisticDevice STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET 60
    step "W2 · bản đồ ISA 'hợp lệ' + ô hiện tại = 60" w2
    step "W3 · MỚI 2606: setIsaMap* — giới hạn 60 sắp đổi sau 200 (đèn 'dự báo')" w3
    say "Xong — trap sẽ trả mọi ô về 0."
    ;;
  *) say "PHASE phải là read | watch | write"; exit 2;;
esac
say "Log: $LOG"
