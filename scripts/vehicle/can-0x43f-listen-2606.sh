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
# VÌ SAO SAFE (khác với ghi 0x237 ở inject): đây KHÔNG phải inject — không gửi dữ liệu xe, chỉ ĐĂNG KÝ các id
#   để MCU chép khung lên cho mình đọc. Rủi ro còn lại = bảng theo dõi bị bỏ lại ⇒ đã có đường xoá + chủ thật
#   nạp lại. Không chạm bus, không đổi giá trị nào. An toàn chạy cả khi đỗ hoặc đang có người lái (chỉ đọc).
#
# Điều kiện: Kachi chạy; navopen-v4.jar (mặc định ../apks/navopen-v4.jar). KHÔNG cần test-mode (navopen chạy
#   app_process uid shell qua dadb, không qua cầu Kachi).
# Cách dùng:
#   VEH=<vehicle-ip>:5555 ./scripts/vehicle/can-0x43f-listen-2606.sh                 # nghe 0x43F 30 s
#   VEH=<vehicle-ip>:5555 SECS=60 IDS=0x43F,0x237 ./scripts/vehicle/can-0x43f-listen-2606.sh
# Kết quả: logs/oncar-can43f-<ngày-giờ>/ (logs/ không vào git — khung CAN có thể lộ dữ liệu xe).
set -uo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
VEH="${VEH:?đặt VEH=<vehicle-ip>:5555}"
SECS="${SECS:-30}"
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
[ "$SECS" -le 600 ] || die "SECS=$SECS quá dài (trần 600 s — Ctrl-C bị chặn trong lúc nghe)"

# canmon tự: đăng ký listener → sendRegisterTable(3, <ids>) → nghe SECS giây (lặp lại bảng mỗi 2 s) →
#           in DONE frames/uniqueIds → GỬI BẢNG XOÁ {00 00 03 D5 00 03 00 03}. (navopen-v4 NavOpen.java:592-617)
say "== canmon (đăng ký bảng theo dõi tạm thời, nghe ${SECS}s, rồi tự xoá bảng)"
say "   ⚠ Ctrl-C bị CHẶN trong lúc nghe: cắt giữa chừng là bỏ lại bảng theo dõi trong MCU tới lần thức kế."
trap '' INT TERM
nav canmon "$SECS" "$IDS" | tee "$OUT/canmon.out"     # hiện sống + giữ bản riêng để kiểm dòng xoá bảng
trap - INT TERM

if grep -q "register table cleared" "$OUT/canmon.out"; then
  say "-- TRẢ ✓: navopen đã gửi bảng xoá {00 00 03 D5 00 03 00 03}; CanDataCollect nạp lại bảng thật ở lần MCU thức kế --"
elif grep -q "passive listen (no table sent)\|BigDataDevice null" "$OUT/canmon.out"; then
  say "-- TRẢ ✓ (không có gì để trả): canmon KHÔNG gửi bảng nào (VehicleDataDevice/BigDataDevice null — giới hạn quyền) --"
else
  say "-- [CHƯA TRẢ] không thấy 'register table cleared' trong đầu ra canmon ⇒ bảng theo dõi có thể còn trong MCU."
  say "   Dọn: CanDataCollect (persistent, uid system) tự nạp lại bảng thật khi MCU thức (CanDataHandle.java:303-332) ⇒ tắt máy/"
  say "   khởi động lại đầu xe. Không có lệnh xoá riêng trong navopen-v4 (xem ⚠ đầu tệp)."
fi
say "Log: $LOG"
