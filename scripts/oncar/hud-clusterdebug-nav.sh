#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════════════════════════════
# hud-clusterdebug-nav.sh — KỊCH BẢN XE cho HUD-CLUSTERDEBUG-PATH (dẫn đường lên HUD kính / cụm qua
# receiver gốc của ClusterDebug thay vì HAL Instrument của Kachi).
#
# Cơ chế, mức bằng chứng, bảng quyết định: docs/diagnostics/hud-clusterdebug-path-2026-10-16.md
#   • Đường gửi: adb shell (uid shell) → broadcast TƯỜNG MINH tới
#       com.byd.clusterdebug/com.byd.clusterdebug.BroadcastReceiverCAN
#     -a byd.hud.NAVIGATION, -f 0x10000000 (= FLAG_RECEIVER_FOREGROUND, giống DiAuto), --es normal <hex,hex,…>
#   • Receiver (RE ClusterDebug 10601004 của chính fw 2606): tách chuỗi theo "," → byte[] →
#     BYDAutoTestDevice.set({0xAA00020F TEST_SIMULATE_DOWN}, buffer). Action KHÔNG được đọc.
#   • Gói = bản ghi [id BE 4][len 1][giá trị], CHỈ các id dẫn đường (allow-list cứng ở check_packet):
#       0x43E0003A trạng thái dẫn (2 bật · 1 xoá theo DiAuto · 4 tắt theo AmapService OEM)
#       0x43F01018 cự ly · 0x43F01010 + 0x43F01030 mũi tên · 0x43FA1008..0x43FA4008 tên đường (khúc 7 byte)
#
# DÙNG:   scripts/oncar/hud-clusterdebug-nav.sh [<adb-serial>] [<thư-mục-ra>]
#         serial mặc định 127.0.0.1:15555 (cầu nc — docs/diagnostics/adb-car-tunnel-macos.md)
#         thư mục ra mặc định docs/diagnostics/oncar-hud-cd-<YYYYmmdd-HHMMSS>/ (đã gitignore: oncar-*/)
# Biến:   ADB=<đường adb>
#
# LUẬT (CLAUDE.md §4, memory "thấy sập lần đầu là dừng"):
#   • Display: không chạm (không am start / wm / stack). App: CHỈ receiver trên. Loại lệnh: CHỈ bản ghi dẫn
#     đường ở trên — không wholeFrame, không opcode clusterDebug (cấm 1/18/41/2/91/92), không id khác.
#   • Hoàn tác: trap EXIT luôn gửi xoá (status=1 rồi status=4) nếu đã từng gửi bật.
#   • Bất thường đầu tiên (pid SurfaceFlinger/system_server/Kachi đổi, logcat -b crash có tiến trình hệ thống
#     hoặc ClusterDebug, owner báo cụm/HUD lạ) ⇒ xoá + thoát.
# macOS bash 3.2: không mảng kết hợp, không `timeout`.
# ═════════════════════════════════════════════════════════════════════════════════════════════════
set -uo pipefail

SERIAL="${1:-127.0.0.1:15555}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="${2:-$ROOT/docs/diagnostics/oncar-hud-cd-$(date +%Y%m%d-%H%M%S)}"
ADB="${ADB:-adb}"

KACHI_PKG="com.byd.launcher"
CD_PKG="com.byd.clusterdebug"
CD_COMP="$CD_PKG/com.byd.clusterdebug.BroadcastReceiverCAN"
CD_ACTION="byd.hud.NAVIGATION"
CD_FLAGS="0x10000000"          # Intent.FLAG_RECEIVER_FOREGROUND
CD_VERSION="10601004"          # bản đã RE (fw 2606 meta/packages.txt) — khác ⇒ dừng
HOLD_SECS=10                   # mỗi bước giữ 10 s, nhịp 1 s (DiAuto: gửi lại khi đổi hoặc ≥ 1 s)

# ── gói (tính sẵn từ dữ kiện giao thức; dẫn xuất trong doc §3) ─────────────────────────────────────
PKT_START="43,E0,00,3A,01,02"                                  # trạng thái dẫn = 2
PKT_CLEAR="43,E0,00,3A,01,01"                                  # DiAuto clear = 1
PKT_END="43,E0,00,3A,01,04"                                    # AmapService OEM kết thúc = 4
REC_DIST="43,F0,10,18,04,00,00,01,F4"                          # cự ly 500 m
REC_TURN1="43,F0,10,10,04,00,00,00,02"                         # mũi tên rẽ phải (NEW_ICON 3 → mã 2)
REC_TURN2="43,F0,10,30,04,00,00,00,02"                         # cùng mã, id thứ hai
PKT_GUIDE="$REC_DIST,$REC_TURN1,$REC_TURN2"
# "Kachi test": 20 byte UTF-16LE, tổng kiểm 0xC0, 4 khúc
PKT_ST_EN="43,FA,10,08,08,A1,14,4B,00,61,00,63,00,43,FA,20,08,08,A2,68,00,69,00,20,00,74,43,FA,30,08,08,A3,00,65,00,73,00,74,00,43,FA,40,08,08,A4,C0,FF,FF,FF,FF,FF,FF"
# "Đường Lê Lợi" (NFC, 12 đơn vị UTF-16): 24 byte, tổng kiểm 0xBE, 4 khúc
PKT_ST_VI="43,FA,10,08,08,A1,18,10,01,B0,01,DD,1E,43,FA,20,08,08,A2,6E,00,67,00,20,00,4C,43,FA,30,08,08,A3,00,EA,00,20,00,4C,00,43,FA,40,08,08,A4,E3,1E,69,00,BE,FF,FF"

mkdir -p "$OUT" || { echo "ERROR: không tạo được $OUT" >&2; exit 2; }
LOG="$OUT/session.log"
SUMMARY="$OUT/summary.tsv"
printf 'step\tquestion\tanswer\tevidence\n' > "$SUMMARY"

# ── trạng thái phiên ────────────────────────────────────────────────────────────────────────────
SEQ=0
STARTED=0        # 1 = đã (có thể đã) gửi PKT_START ⇒ trap phải xoá
CLEARED=0
SF_PID=""; SS_PID=""; K_PID=""; T0=""; ANS=""; CAP_FILE=""; STEP_T=""; NF=""

now()   { date +%Y%m%d-%H%M%S; }
say()   { printf '%s\n' "$*"; printf '%s  %s\n' "$(date +%FT%T%z)" "$*" >> "$LOG"; }
hr()    { say "──────────────────────────────────────────────────────────────"; }
note()  { printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "${4:-}" >> "$SUMMARY"; }
a()     { "$ADB" -s "$SERIAL" "$@"; }
ash()   { a shell "$@" 2>&1 | tr -d '\r'; }

todo() {
  printf '\n>>> [VIỆC CỦA ANH] %s\n    (xong bấm Enter) ' "$*"
  printf '%s  TODO %s\n' "$(date +%FT%T%z)" "$*" >> "$LOG"
  local _x; read -r _x </dev/tty || abort "không đọc được /dev/tty (chạy ở terminal thật, không nohup/pipe)"
}

ask_yn() {
  local r=""
  while :; do
    printf '\n??? %s [c/k] ' "$*"; read -r r </dev/tty || abort "không đọc được /dev/tty (hết đầu vào) — không đoán câu trả lời"
    case "$r" in c|C|y|Y|co|có) ANS=y; break ;; k|K|n|N|khong|không) ANS=n; break ;; esac
  done
  printf '%s  ASK %s -> %s\n' "$(date +%FT%T%z)" "$*" "$ANS" >> "$LOG"
}

# next_file <bước> <tên> → đặt $NF (KHÔNG gọi trong $(…): SEQ phải tăng ở shell chính)
next_file() { SEQ=$((SEQ + 1)); NF="$(printf '%s/%02d-%s-%s-%s.txt' "$OUT" "$SEQ" "$1" "$2" "$(now)")"; }

cap() {
  local step="$1" name="$2"; shift 2
  next_file "$step" "$name"; CAP_FILE="$NF"
  { printf '# $ adb -s %s shell %s\n# %s\n\n' "$SERIAL" "$*" "$(date +%FT%T%z)"; ash "$@" || true; } > "$CAP_FILE"
  say "   → $(basename "$CAP_FILE") ($(wc -l < "$CAP_FILE" | tr -d ' ') dòng)"
}

devtime() { ash "date '+%m-%d %H:%M:%S.000'" | head -1; }
pidof_dev() { ash "pidof $1" | head -1; }

# ── allow-list gói: chỉ bản ghi dẫn đường, đúng khuôn [id4][len][val] ───────────────────────────
# Trả 0 nếu hợp lệ. Lỗi parse ở receiver (Integer.parseInt) = NumberFormatException trong tiến trình hệ thống
# ⇒ chặn TRƯỚC khi gửi.
check_packet() {
  python3 -I -c '
import re, sys
s = sys.argv[1]
if not re.fullmatch(r"[0-9A-F]{2}(,[0-9A-F]{2})*", s): sys.exit("khuôn hex sai")
b = [int(x, 16) for x in s.split(",")]
i = 0
while i < len(b):
    if i + 5 > len(b): sys.exit("bản ghi cụt ở byte %d" % i)
    fid = (b[i] << 24) | (b[i+1] << 16) | (b[i+2] << 8) | b[i+3]; n = b[i+4]; v = b[i+5:i+5+n]
    if len(v) != n: sys.exit("độ dài sai ở 0x%08X" % fid)
    if fid == 0x43E0003A:
        if n != 1 or v[0] not in (1, 2, 4): sys.exit("status ngoài {1,2,4}")
    elif fid in (0x43F01018, 0x43F01010, 0x43F01030):
        if n != 4: sys.exit("bản ghi INT phải 4 byte")
    elif fid in (0x43FA1008, 0x43FA2008, 0x43FA3008, 0x43FA4008):
        if n != 8 or v[0] != ((fid >> 12) & 0xFF): sys.exit("khúc tên đường sai số thứ tự")
    else:
        sys.exit("id 0x%08X KHÔNG trong allow-list" % fid)
    i += 5 + n
' "$1"
}

# send <bước> <nhãn> <gói>  → 0 nếu am báo "Broadcast completed"
send() {
  local step="$1" label="$2" pkt="$3" out
  if ! check_packet "$pkt" 2>>"$LOG"; then abort "gói $label bị allow-list chặn (xem session.log)"; fi
  case "$label" in start*) STARTED=1 ;; esac
  out="$(ash "am broadcast -n $CD_COMP -a $CD_ACTION -f $CD_FLAGS --es normal $pkt")"
  printf '### %s [%s] %s normal=%s\n%s\n' "$(date +%FT%T%z)" "$step" "$label" "$pkt" "$out" >> "$OUT/broadcast.log"
  case "$out" in *"Broadcast completed"*) return 0 ;; esac
  say "   [!] am broadcast $label không báo completed: $(printf '%s' "$out" | tail -1)"
  return 1
}

# quick_health — chỉ so pid (rẻ, chạy mỗi nhịp)
quick_health() {
  local step="$1" why="" sf ss k
  sf="$(pidof_dev surfaceflinger)"; ss="$(pidof_dev system_server)"; k="$(pidof_dev "$KACHI_PKG")"
  [ "$sf" != "$SF_PID" ] && why="$why surfaceflinger pid ${SF_PID}→${sf:-mất};"
  [ "$ss" != "$SS_PID" ] && why="$why system_server pid ${SS_PID}→${ss:-mất};"
  if [ -n "$K_PID" ] && [ "$k" != "$K_PID" ]; then why="$why Kachi pid ${K_PID}→${k:-chết};"; fi
  if [ -n "$why" ]; then
    say "!!! BẤT THƯỜNG ở bước $step:$why"; note "$step" health ANOMALY "$why"
    cap "$step" anomaly-logcat "logcat -d -v time -t '$T0'"
    abort "bất thường ở $step"
  fi
}

# health <bước> [ask] — pid + logcat -b crash từ t0 (+ hỏi owner)
health() {
  local step="$1" why=""
  quick_health "$step"
  cap "$step" crash "logcat -b crash -d -v time -t '$T0'"
  if grep -v '^#' "$CAP_FILE" | grep -Eq "$KACHI_PKG|$CD_PKG|surfaceflinger|system_server|FATAL"; then
    why="$why logcat -b crash có dòng mới (Kachi/ClusterDebug/SurfaceFlinger/system_server/FATAL);"
  fi
  if [ "${2:-}" = ask ] && [ -z "$why" ]; then
    ask_yn "Cụm + HUD có gì BẤT THƯỜNG không (đen, nháy, treo, cảnh báo lạ, đồng hồ mất)?"
    [ "$ANS" = y ] && why="$why owner: cụm/HUD bất thường;"
  fi
  if [ -n "$why" ]; then
    say "!!! BẤT THƯỜNG ở bước $step:$why"; note "$step" health ANOMALY "$why"
    cap "$step" anomaly-logcat "logcat -d -v time -t '$T0'"
    abort "bất thường ở $step"
  fi
}

# step_logs <bước> — logcat từ đầu bước, lọc ClusterDebug/TestDevice/cụm; báo receiver có nhận không
step_logs() {
  local step="$1" flt n m
  cap "$step" logcat "logcat -d -v time -t '$STEP_T'"
  next_file "$step" logcat-filtered; flt="$NF"
  grep -E 'ClusterDebug|clusterdebug|TestDevice|BYDAutoTest|CLUSTER_LOG|AbsBYDAutoDevice|AndroidRuntime|NumberFormat' "$CAP_FILE" > "$flt" || true
  n="$(grep -c 'receive normal:' "$flt" || true)"
  m="$(grep -c 'set featureID is aa00020f' "$flt" || true)"
  say "   receiver 'receive normal:' $n lần · AbsBYDAutoDevice 'set featureID is aa00020f' $m lần (lọc: $(basename "$flt"))"
  note "$step" "ClusterDebug nhận / TestDevice set" "$n / $m" "$(basename "$flt")"
  grep -E 'AndroidRuntime|Exception|denied|permission' "$flt" | tail -6 | sed 's/^/     /'
}

# hold <bước> <gói> — gửi gói mỗi 1 s trong HOLD_SECS, kiểm pid mỗi nhịp
hold() {
  local step="$1" pkt="$2" i=0
  while [ "$i" -lt "$HOLD_SECS" ]; do
    send "$step" "guide#$i" "$pkt" || true
    quick_health "$step"
    sleep 1
    i=$((i + 1))
  done
}

clear_all() {
  [ "$STARTED" = 1 ] || return 0
  [ "$CLEARED" = 1 ] && return 0
  say "XOÁ: status=1 (DiAuto) rồi status=4 (AmapService OEM)"
  send clear clear "$PKT_CLEAR" || true
  sleep 1
  send clear end "$PKT_END" || true
  CLEARED=1
}

# shellcheck disable=SC2329  # gọi qua trap EXIT
on_exit() {
  clear_all
  if [ "$STARTED" = 1 ]; then
    say "Nếu cụm/HUD VẪN còn mũi tên: chạy lại tay"
    say "    $ADB -s $SERIAL shell am broadcast -n $CD_COMP -a $CD_ACTION -f $CD_FLAGS --es normal $PKT_END"
    say "    còn nữa ⇒ tắt máy xe hẳn rồi nổ lại (SIMULATE_DOWN không ghi tệp — [SUY], doc §5)."
  fi
  say "Bằng chứng: $OUT"
}
abort() { say "DỪNG: $*"; exit 3; }
trap on_exit EXIT
trap 'say "Ngắt (Ctrl-C)"; exit 130' INT TERM

# ═════════════════════════════════════════════════════════════════════════════════════════════════
say "hud-clusterdebug-nav.sh · xe=$SERIAL · ra=$OUT"
hr
if ! a get-state >/dev/null 2>&1; then
  say "ERROR: adb không thấy $SERIAL. Dựng cầu nc theo docs/diagnostics/adb-car-tunnel-macos.md rồi chạy lại."
  exit 4
fi
for p in "$PKT_START" "$PKT_CLEAR" "$PKT_END" "$PKT_GUIDE" "$REC_DIST" "$REC_TURN1" "$REC_TURN2" \
         "$PKT_GUIDE,$PKT_ST_EN" "$PKT_GUIDE,$PKT_ST_VI"; do
  check_packet "$p" || { say "ERROR: gói dựng sẵn sai allow-list — sửa script trước khi lên xe."; exit 5; }
done

# ── H0 — CHỈ ĐỌC ───────────────────────────────────────────────────────────────────────────────
hr; say "[H0] chỉ đọc: ClusterDebug · build · Kachi · logcat mốc"
todo "Xe ĐỖ, số P, nổ máy (cụm sáng). KHÔNG dẫn đường trong app nào (GMaps/VietMap/Waze/bản đồ BYD)."
cap H0 build "getprop ro.build.fingerprint; getprop ro.build.version.release; getprop ro.build.display.id; getprop persist.sys.car.type"
cap H0 clusterdebug "dumpsys package $CD_PKG"
CDF="$CAP_FILE"
CD_VC="$(sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' "$CDF" | head -1)"
CD_PATH="$(sed -n 's/.*codePath=\([^ ]*\).*/\1/p' "$CDF" | head -1)"
CD_SYS=n; grep -q 'pkgFlags=\[.*SYSTEM' "$CDF" && CD_SYS=y
CD_TEST=n; grep -q 'BYDAUTO_TEST_SET: granted=true' "$CDF" && CD_TEST=y
CD_STOP="$(sed -n 's/.*stopped=\([a-z]*\).*/\1/p' "$CDF" | head -1)"
CD_RECV=n; grep -q 'BroadcastReceiverCAN' "$CDF" && CD_RECV=y
say "   ClusterDebug versionCode=$CD_VC codePath=$CD_PATH SYSTEM=$CD_SYS TEST_SET=$CD_TEST stopped=$CD_STOP receiver=$CD_RECV"
note H0 "ClusterDebug versionCode/SYSTEM/TEST_SET/stopped/receiver" "$CD_VC/$CD_SYS/$CD_TEST/$CD_STOP/$CD_RECV" "$(basename "$CDF")"
cap H0 query-receivers "cmd package query-receivers --components -n $CD_COMP"
cap H0 kachi-pkg "dumpsys package $KACHI_PKG | grep -E 'versionName|versionCode'"
cap H0 kachi-services "dumpsys activity services $KACHI_PKG"
cap H0 kachi-notif "dumpsys notification | grep -A3 -E 'NotificationListeners|$KACHI_PKG' | head -40"
cap H0 logcat-baseline "logcat -d -v time -t 300"
grep -E 'ClusterDebug|TestDevice|CLUSTER_LOG' "$CAP_FILE" | tail -5 | sed 's/^/     /'
T0="$(devtime)"
SF_PID="$(pidof_dev surfaceflinger)"; SS_PID="$(pidof_dev system_server)"; K_PID="$(pidof_dev "$KACHI_PKG")"
say "mốc sức khoẻ: t0=[$T0] surfaceflinger=$SF_PID system_server=$SS_PID kachi=$K_PID clusterdebug=$(pidof_dev "$CD_PKG")"
note H0 "pid mốc" "sf=$SF_PID ss=$SS_PID kachi=$K_PID" "$T0"
health H0
if [ "$CD_VC" != "$CD_VERSION" ] || [ "$CD_SYS" != y ] || [ "$CD_RECV" != y ]; then
  abort "ClusterDebug khác bản đã RE ($CD_VC, SYSTEM=$CD_SYS, receiver=$CD_RECV) — chưa có source ⇒ không gửi"
fi
[ "$CD_TEST" != y ] && say "   [!] Không thấy BYDAUTO_TEST_SET granted=true — receiver có thể nhận mà TestDevice từ chối; đo tiếp, xem log."

# ── cổng an toàn trước khi GHI ─────────────────────────────────────────────────────────────────
hr; say "[CỔNG] trước H1"
todo "Kachi › Cài đặt › Dẫn đường + HUD › TẮT 'Dẫn đường lên cụm đồng hồ' (để Kachi không ghi đè). Nhìn cụm + HUD lúc này, nhớ trạng thái gốc."
cap gate kachi-services-after-off "dumpsys activity services $KACHI_PKG"
ask_yn "Xe đang đỗ số P, đứng yên, không app nào dẫn đường, công tắc Kachi đã TẮT?"
[ "$ANS" = y ] || abort "điều kiện đỗ/tắt chưa đủ"
printf '\n!!! Sắp gửi gói dẫn đường qua ClusterDebug (TestDevice SIMULATE_DOWN). Gõ GUI để tiếp, khác = thoát: '
read -r CONFIRM </dev/tty || CONFIRM=""
printf '%s  CONFIRM [%s]\n' "$(date +%FT%T%z)" "$CONFIRM" >> "$LOG"
[ "$CONFIRM" = "GUI" ] || abort "owner không gõ GUI"

# ── H1 — bật + mũi tên phải + 500 m, gói gộp như DiAuto ─────────────────────────────────────────
hr; say "[H1] start + (cự ly 500 m, rẽ phải) gộp 1 broadcast, nhịp 1 s × ${HOLD_SECS} s"
todo "Nhìn HUD kính lái và cụm. Bấm Enter để bắt đầu gửi (${HOLD_SECS} giây)."
STEP_T="$(devtime)"
send H1 start "$PKT_START" || true
hold H1 "$PKT_GUIDE"
step_logs H1
ask_yn "H1: HUD có hiện mũi tên/khoảng cách không?"; H1_HUD="$ANS"; note H1 "HUD mũi tên/cự ly" "$H1_HUD" ""
ask_yn "H1: Cụm thì sao — có hiện mũi tên rẽ phải / 500 m không?"; H1_CL="$ANS"; note H1 "cụm mũi tên/cự ly" "$H1_CL" ""
health H1 ask

# ── H1b — cùng giá trị, MỖI bản ghi một broadcast (chỉ khi H1 không hiện gì) ─────────────────────
if [ "$H1_HUD" = n ] && [ "$H1_CL" = n ]; then
  hr; say "[H1b] mỗi bản ghi 1 broadcast (khuôn 1 bản ghi/lần đã chạy trên xe 10/10 với 0x237)"
  STEP_T="$(devtime)"
  send H1b start "$PKT_START" || true
  i=0
  while [ "$i" -lt "$HOLD_SECS" ]; do
    send H1b "dist#$i" "$REC_DIST" || true
    send H1b "turn1#$i" "$REC_TURN1" || true
    send H1b "turn2#$i" "$REC_TURN2" || true
    quick_health H1b
    sleep 1; i=$((i + 1))
  done
  step_logs H1b
  ask_yn "H1b: HUD có hiện mũi tên/khoảng cách không?"; note H1b "HUD mũi tên/cự ly" "$ANS" ""
  ask_yn "H1b: Cụm có hiện mũi tên/khoảng cách không?"; note H1b "cụm mũi tên/cự ly" "$ANS" ""
  health H1b ask
fi

# ── H2 — thêm tên đường (Latin rồi tiếng Việt có dấu) ───────────────────────────────────────────
hr; say "[H2a] dẫn + tên đường 'Kachi test' (khúc 0x43FA1008…, gộp cùng broadcast như DiAuto)"
STEP_T="$(devtime)"
hold H2a "$PKT_GUIDE,$PKT_ST_EN"
step_logs H2a
ask_yn "H2a: HUD có hiện chữ 'Kachi test' không?"; note H2a "HUD tên đường Latin" "$ANS" ""
ask_yn "H2a: Cụm có hiện chữ 'Kachi test' không?"; note H2a "cụm tên đường Latin" "$ANS" ""
health H2a ask

hr; say "[H2b] dẫn + tên đường 'Đường Lê Lợi' (có dấu)"
STEP_T="$(devtime)"
hold H2b "$PKT_GUIDE,$PKT_ST_VI"
step_logs H2b
ask_yn "H2b: HUD có hiện 'Đường Lê Lợi' (đúng dấu) không?"; note H2b "HUD tên đường có dấu" "$ANS" ""
ask_yn "H2b: Cụm có hiện 'Đường Lê Lợi' (đúng dấu) không?"; note H2b "cụm tên đường có dấu" "$ANS" ""
health H2b ask

# ── H3 — xoá ───────────────────────────────────────────────────────────────────────────────────
hr; say "[H3] xoá"
STEP_T="$(devtime)"
clear_all
sleep 2
step_logs H3
ask_yn "H3: Mọi thứ (mũi tên, cự ly, tên đường) đã biến mất trên CẢ HUD và cụm chưa?"; H3_CLEAN="$ANS"
note H3 "xoá sạch" "$H3_CLEAN" ""
health H3 ask
[ "$H3_CLEAN" = n ] && say "   [!] chưa sạch — trap sẽ in lệnh gửi lại status=4; còn nữa thì tắt/nổ máy."

todo "Bật lại Kachi › Dẫn đường + HUD › 'Dẫn đường lên cụm đồng hồ' như cũ."
hr; say "XONG. Tóm tắt: $SUMMARY"
column -t -s "$(printf '\t')" "$SUMMARY" 2>/dev/null | sed 's/^/   /' || cat "$SUMMARY"
