#!/usr/bin/env bash
# can-0x43f-listen-2606.sh — nghe khung CAN điều hướng 0x43F trên xe qua navopen `canmon` · fw 2606 · CHỈ ĐỌC.
#
# Chuẩn bị + bằng chứng: docs/diagnostics/oncar-prep-2026-10-09-isa-can-odo.md §B2. Verdict: SAFE (có đường trả).
#
# sendRegTable LÀM GÌ, GHI ĐÂU, CÓ BỀN KHÔNG (đã truy nguồn, không đoán):
#   - navopen `canmon`/`canreg` → `BYDAutoVehicleDataDevice.sendRegisterTable(3, table)`
#     [ĐO framework.jar 2606 — android/hardware/bydauto/vehicledata/BYDAutoVehicleDataDevice.java:66-83]:
#     type 3 (DATA_TYPE_BIG_DATA) ⇒ super.set(1048, 0xAA000022=-1442840542, table). KHÔNG ghi file, KHÔNG
#     SetConfig/saveConfigXML — chỉ nạp "bảng id cần theo dõi" vào RAM của MCU (thanh ghi theo dõi), hệt như
#     CanDataCollect làm mỗi khi MCU thức.
#   - Bảng KHÔNG bền qua tắt máy/power-cycle [SUY mạnh, có cơ sở]:
#     [ĐO CanDataCollect.apk 2606 — entity/CanDataHandle.java:303-317,320-332] mỗi lần MCU thức
#     (`onMcuStatusChanged MCU_WAKE`) hoặc MCU đòi (`onNeedRendRegisterTable`), CanDataCollect (persistent,
#     uid=android.uid.system) tự NẠP LẠI bảng của nó. Tức bảng sống trong MCU đang thức, mất khi MCU ngủ/tắt,
#     và chủ thật của nó nạp lại ngay ⇒ reboot/tắt máy đưa về cấu hình gốc.
#   - ĐƯỜNG TRẢ TƯỜNG MINH: khi `canmon` chạy HẾT GIỜ, chính navopen gửi bảng "xoá" {00 00 03 D5 00 03 00 03} — ĐÚNG byte
#     mà CanDataHandle.notify_hal_remove_collect_id_conf gửi [ĐO NavOpen.java:616]. Sau đó CanDataCollect tự nạp lại bảng
#     THẬT ở lần MCU thức kế.
#     ⚠ Senior review Pass 10 [P1]: navopen-v4 KHÔNG có lệnh xoá bảng riêng (`canreg` không id ⇒ in "no ids given" và thoát,
#     `canreg 0x3D5` lại là ĐĂNG KÝ 0x3D5 với flag=1, không phải bảng xoá — NavOpen.java:620-645) ⇒ bảng chỉ được xoá nếu
#     `canmon` chạy tới cuối. Vì thế script CHẶN Ctrl-C/TERM trong lúc canmon chạy (tối đa SECS giây + vài giây) và kiểm
#     dòng "register table cleared" trong đầu ra; không thấy ⇒ in [CHƯA TRẢ] + cách dọn (CanDataCollect nạp lại khi MCU thức
#     = tắt máy/khởi động lại đầu xe). Giới hạn: tắt terminal / rớt mạng giữa chừng thì vẫn phải chờ MCU thức lại.
#
# ⚠ 10/10 [ĐO xe]: (1) navopen tự giết canmon ở ~120 s (`watchdogMsFor` trần 120 s + 10 s, NavOpen.java:63-69) ⇒ lượt
#   SECS=600 bị cắt TRƯỚC khi xoá bảng (phải dọn bằng một lượt 5 s). Script nay: mỗi lượt ≤ 110 s; SECS lớn hơn ⇒ chia
#   nhiều lượt, MỖI lượt phải in "register table cleared", thiếu ⇒ dừng vòng + chạy lượt dọn 5 s.
#   (2) Lượt 120 s ids 0x235,0x237,0x43F ⇒ frames=0 kể cả 0x237 đang online. Nguyên nhân nghi số 1 [SUY mạnh, ĐO nguồn]:
#   navopen-v4 `buildRegTable` gửi [id][00][00][00][01] (mode 0, kênh 0) trong khi CanDataCollect gửi
#   [id][subid][kênh MCU][mode 2|1][op 1] (CanDataHandle.java:224-260, JsonDataHandel.java:42-53,133,263) ⇒ với
#   navopen-v4, 0 khung là kết quả DỰ KIẾN tới khi navopen được sửa. Chi tiết + bảng sửa:
#   docs/diagnostics/oncar-seal-2026-10-10-r18-sla-can.md §3.
#
# VÌ SAO SAFE (khác với ghi 0x237 ở inject): đây KHÔNG phải inject — không gửi dữ liệu xe, chỉ ĐĂNG KÝ các id
#   để MCU chép khung lên cho mình đọc. Rủi ro còn lại = bảng theo dõi bị bỏ lại ⇒ đã có đường xoá + chủ thật
#   nạp lại. Không chạm bus, không đổi giá trị nào. An toàn chạy cả khi đỗ hoặc đang có người lái (chỉ đọc).
#
# Điều kiện: Kachi chạy; navopen-v4.jar (mặc định ../apks/navopen-v4.jar). KHÔNG cần test-mode (navopen chạy
#   app_process uid shell qua dadb, không qua cầu Kachi).
# Cách dùng:
#   VEH=<vehicle-ip>:5555 ./scripts/vehicle/can-0x43f-listen-2606.sh                 # nghe 0x43F 30 s
#   VEH=<vehicle-ip>:5555 SECS=60 IDS=0x43F,0x237 ./scripts/vehicle/can-0x43f-listen-2606.sh
#   VEH=<vehicle-ip>:5555 SECS=330 ./scripts/vehicle/can-0x43f-listen-2606.sh    # 3 lượt × 110 s, mỗi lượt tự xoá bảng
#   VEH=<vehicle-ip>:5555 CLEAN=1 ./scripts/vehicle/can-0x43f-listen-2606.sh      # chỉ dọn: canmon 5 s ⇒ bảng xoá
# Kết quả: logs/oncar-can43f-<ngày-giờ>/ (logs/ không vào git — khung CAN có thể lộ dữ liệu xe).
set -uo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
VEH="${VEH:?đặt VEH=<vehicle-ip>:5555}"
SECS="${SECS:-30}"
CHUNK=110          # trần mỗi lượt: dưới watchdog 120 s của navopen-v4 (NavOpen.java:63-69)
CLEAN="${CLEAN:-0}"
IDS="${IDS:-0x43F}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NAVOPEN_JAR="${NAVOPEN_JAR:-$ROOT/../apks/navopen-v4.jar}"
OUT="$ROOT/logs/oncar-can43f-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
LOG="$OUT/session.log"
NAVCP="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"

say() { echo "$*" | tee -a "$LOG"; }
die() { say "DỪNG: $*"; exit 3; }
nav() { "$ADB" -s "$VEH" shell "$NAVCP $*" 2>&1 | tee -a "$LOG"; }

[ -f "$NAVOPEN_JAR" ] || die "không thấy navopen-v4.jar (đặt NAVOPEN_JAR=...)"
"$ADB" connect "$VEH" >/dev/null 2>&1
say "== $(date '+%F %T') · xe $VEH · nghe CAN ids=$IDS trong ${SECS}s"
say "   fw: $("$ADB" -s "$VEH" shell getprop ro.build.version.incremental | tr -d '\r')"
"$ADB" -s "$VEH" push "$NAVOPEN_JAR" /data/local/tmp/navopen.jar >/dev/null 2>&1 && say "   navopen pushed"

# ĐƯỜNG TRẢ (CLAUDE.md §4) — SỰ THẬT: chỉ canmon chạy hết giờ mới gửi bảng xoá (xem ⚠ đầu tệp). Ta không cắt nó.
case "$SECS" in ''|*[!0-9]*) die "SECS phải là số giây nguyên (đang: '$SECS')" ;; esac
[ "$SECS" -ge 1 ] || die "SECS phải ≥ 1"
[ "$SECS" -le 1800 ] || die "SECS=$SECS quá dài (trần 1800 s tổng — Ctrl-C bị chặn trong từng lượt)"

# Một lượt canmon ≤ CHUNK giây. canmon tự: đăng ký listener → sendRegisterTable(3, <ids>) → nghe (lặp bảng mỗi 2 s) →
# in DONE frames/uniqueIds → GỬI BẢNG XOÁ {00 00 03 D5 00 03 00 03} (navopen-v4 NavOpen.java:592-617).
# Trả 0 nếu thấy "register table cleared" (hoặc không gửi bảng nào), 1 nếu chưa trả.
run_once() {
  local secs="$1" ids="$2" tag="$3" out="$OUT/canmon-$3.out"
  say "== canmon lượt $tag (${secs}s, ids=$ids) — Ctrl-C bị CHẶN trong lượt"
  trap '' INT TERM
  nav canmon "$secs" "$ids" > "$out"
  trap - INT TERM
  grep -E "DONE frames|\[can\] id=|watchdog|FAILED|null" "$out" | head -40 | tee -a "$LOG"
  if grep -q "register table cleared" "$out"; then
    say "-- lượt $tag TRẢ ✓: bảng xoá đã gửi --"; return 0
  elif grep -q "passive listen (no table sent)\|BigDataDevice null" "$out"; then
    say "-- lượt $tag TRẢ ✓ (không gửi bảng nào) --"; return 0
  fi
  say "-- lượt $tag [CHƯA TRẢ]: không thấy 'register table cleared' --"; return 1
}

cleanup_table() {
  say "== DỌN: canmon 5 s chỉ để navopen gửi bảng xoá"
  if run_once 5 "$IDS" clean; then return 0; fi
  say "   [CHƯA TRẢ] dọn thất bại — CanDataCollect (persistent, uid system) nạp lại bảng thật khi MCU thức"
  say "   (CanDataHandle.java:303-332) ⇒ tắt máy/khởi động lại đầu xe. navopen-v4 không có lệnh xoá riêng."
  return 1
}

if [ "$CLEAN" = "1" ]; then cleanup_table; rc=$?; say "Log: $LOG"; exit "$rc"; fi

say "   ⚠ Mỗi lượt ≤ ${CHUNK}s (watchdog navopen 120 s). Tổng ${SECS}s."
left="$SECS"; n=0; status=0
while [ "$left" -gt 0 ]; do
  n=$((n + 1)); chunk="$left"; [ "$chunk" -gt "$CHUNK" ] && chunk="$CHUNK"
  if ! run_once "$chunk" "$IDS" "$n"; then status=1; cleanup_table || status=2; break; fi
  left=$((left - chunk))
done
frames="$(cat "$OUT"/canmon-*.out 2>/dev/null | grep -o 'DONE frames=[0-9]*' | awk -F= '{s+=$2} END {print s+0}')"
say "== Tổng: $n lượt · frames=$frames · trạng thái trả=$status (0 sạch · 1 đã dọn bằng lượt 5 s · 2 CHƯA TRẢ)"
[ "$frames" = "0" ] && say "   frames=0: dự kiến với navopen-v4 (bảng mode 0/kênh 0 — xem ⚠ đầu tệp), KHÔNG kết luận bus im."
say "Log: $LOG"
