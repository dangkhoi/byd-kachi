#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════════════════════════════
# aa-nav-probe.sh — KỊCH BẢN XE, CHỈ ĐỌC: app Android Auto của BYD (com.byd.androidauto) có đẩy dẫn đường
# của Google Maps (kênh nav của giao thức AA) ra cụm/HAL không? Nếu có, Kachi đọc lại được các ô dẫn đường
# của Instrument và đưa lên HUD/cụm theo đường đang thử (HUD-CLUSTERDEBUG-PATH) — không phải làm bộ nhận AA.
#
# Bối cảnh: DiAuto lấy dẫn đường vì CHÍNH NÓ là bộ nhận AA (Channel.ID_NAV: NEXTTURNDETAILS,
# NEXTTURNDISTANCEANDTIME…). App AA của BYD chạy thư viện bộ nhận của Google (AAPService/AAWService, dump xe
# 23/07) ⇒ có nhận kênh nav; có chuyển đi đâu không = [CHƯA BIẾT]. Đây là phép đo tầng 1 (CLAUDE.md §14).
#
# DÙNG:   scripts/oncar/aa-nav-probe.sh [<adb-serial>] [<thư-mục-ra>]
#         serial mặc định 127.0.0.1:15555 · thư mục ra mặc định docs/diagnostics/oncar-aa-nav-<ts>/ (gitignore oncar-*/)
# Cần:    Kachi › Cài đặt › Hệ thống & quyền › Nâng cao › "Chế độ kiểm thử qua adb" BẬT (chỉ để đọc HAL getid).
# LUẬT:   KHÔNG một lệnh ghi nào — chỉ pm path / pull / dumpsys / logcat -d / hal getid. APK kéo về là tài sản
#         BYD: nằm trong thư mục oncar-*/ (gitignore), KHÔNG commit, chỉ để dịch ngược cục bộ.
# macOS bash 3.2: không mảng kết hợp, không `timeout`.
# ═════════════════════════════════════════════════════════════════════════════════════════════════
set -uo pipefail

SERIAL="${1:-127.0.0.1:15555}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="${2:-$ROOT/docs/diagnostics/oncar-aa-nav-$(date +%Y%m%d-%H%M%S)}"
ADB="${ADB:-adb}"

AA_PKG="com.byd.androidauto"
COMP="com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge"
ACTION="com.byd.launcher.TEST"
DEV="BYDAutoInstrumentDevice"
# Ô dẫn đường của Instrument (cùng id Kachi/DiAuto/AmapService OEM ghi):
#   0x43E0003A trạng thái dẫn · 0x43F01018 cự ly · 0x43F01010 / 0x43F01030 mũi tên · 0x43FA1008 tên đường
NAV_IDS="1138753594 1139806232 1139806224 1139806256 1140461576"

mkdir -p "$OUT" || { echo "ERROR: không tạo được $OUT" >&2; exit 2; }
LOG="$OUT/session.log"; SUMMARY="$OUT/summary.tsv"; HALTSV="$OUT/hal-reads.tsv"
printf 'step\tquestion\tanswer\n' > "$SUMMARY"
printf 'time\tstep\tfeature_id\tvalue\tsentinel\treply\n' > "$HALTSV"
ANS=""; BR_JSON=""; BRIDGE=0

say()  { printf '%s\n' "$*"; printf '%s  %s\n' "$(date +%FT%T%z)" "$*" >> "$LOG"; }
a()    { "$ADB" -s "$SERIAL" "$@"; }
ash()  { a shell "$@" 2>&1 | tr -d '\r'; }
note() { printf '%s\t%s\t%s\n' "$1" "$2" "$3" >> "$SUMMARY"; }
todo() {
  printf '\n>>> [VIỆC CỦA ANH] %s\n    (xong bấm Enter) ' "$*"
  local _x; read -r _x </dev/tty || { say "không đọc được /dev/tty — dừng"; exit 3; }
}
ask_yn() {
  local r=""
  while :; do
    printf '\n??? %s [c/k] ' "$*"; read -r r </dev/tty || { say "không đọc được /dev/tty — dừng"; exit 3; }
    case "$r" in c|C|y|Y|co|có) ANS=y; break ;; k|K|n|N|khong|không) ANS=n; break ;; esac
  done
  printf '%s  ASK %s -> %s\n' "$(date +%FT%T%z)" "$*" "$ANS" >> "$LOG"
}
cap() { local f="$OUT/$1.txt"; shift; { printf '# $ adb shell %s\n\n' "$*"; ash "$@" || true; } > "$f"; say "   → $(basename "$f") ($(wc -l < "$f" | tr -d ' ') dòng)"; }

bridge() {
  local raw; raw="$(ash "am broadcast -n $COMP -a $ACTION $*")"
  printf '### %s am broadcast … %s\n%s\n' "$(date +%FT%T%z)" "$*" "$raw" >> "$OUT/bridge.log"
  BR_JSON="$(printf '%s' "$raw" | python3 -I -c 'import re,sys
t=sys.stdin.read(); m=re.search(r"data=\"(.*)\"", t, re.S); print(m.group(1).replace("\n"," ") if m else "")')"
}
jfield() {
  printf '%s' "$BR_JSON" | python3 -I -c 'import json,sys
try: d=json.loads(sys.stdin.read())
except Exception: print(""); sys.exit(0)
v=d.get(sys.argv[1],""); print(str(v).lower() if isinstance(v,bool) else v)' "$1"
}
read_nav() {
  local step="$1" id v s r
  if [ "$BRIDGE" != 1 ]; then say "   (cầu kiểm thử tắt — bỏ đọc HAL ở $step)"; return 0; fi
  for id in $NAV_IDS; do
    bridge "--es cmd hal --es op getid --es dev $DEV --es m $id"
    v="$(jfield value)"; s="$(jfield sentinel)"; r="$(jfield reply)"; [ -z "$r" ] && r="$(jfield error)"
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$(date +%T)" "$step" "$id" "$v" "$s" "$r" >> "$HALTSV"
    say "   hal getid $id → value=[$v] sentinel=$s"
  done
}

say "aa-nav-probe — CHỈ ĐỌC · serial $SERIAL · ra $OUT"
a get-state >/dev/null 2>&1 || { say "ERROR: adb không thấy $SERIAL"; exit 2; }

# A0 — app AA của BYD: phiên bản, đường APK, kéo APK về để dịch ngược cục bộ.
cap A0-aa-package "dumpsys package $AA_PKG | grep -E 'versionName|versionCode|codePath|Receiver|Service' | head -80"
APK_PATH="$(ash "pm path $AA_PKG" | sed -n 's/^package://p' | head -1)"
if [ -n "$APK_PATH" ]; then
  a pull "$APK_PATH" "$OUT/AndroidAutoNew.apk" >> "$LOG" 2>&1 && say "   → AndroidAutoNew.apk (kéo về, KHÔNG commit)"
else
  say "   pm path $AA_PKG rỗng — app AA BYD không có trên xe này?"
fi

bridge "--es cmd state"; [ "$(jfield ok)" = "true" ] && BRIDGE=1
[ "$BRIDGE" = 1 ] || say "   cầu kiểm thử chưa mở ($(jfield error)) — phần đọc HAL sẽ bỏ qua."

# A1 — mốc: chưa dẫn đường.
todo "Cắm điện thoại vào Android Auto (app AA của BYD), CHƯA bấm dẫn đường. Tắt 'Dẫn đường lên cụm đồng hồ' của Kachi."
ash "logcat -c" >/dev/null
read_nav A1-idle

# A2 — Google Maps trong Android Auto đang dẫn đường.
todo "Trên Android Auto mở Google Maps, chọn một điểm đến gần, bấm BẮT ĐẦU dẫn đường. Chờ ~20 s."
cap A2-logcat-aa "logcat -d -v time | grep -iE 'androidauto|projection|navigation|nav_|turn|instrument|cluster|43f0|43fa|43e0' | tail -400"
cap A2-logcat-instrument "logcat -d -v time | grep -E 'AbsBYDAutoDevice|BYDAutoInstrumentDevice' | tail -200"
cap A2-svc-aa "dumpsys activity services $AA_PKG | head -120"
read_nav A2-nav
ask_yn "Cụm đồng hồ có tự hiện mũi tên/khoảng cách của Android Auto không?"; note A2 "cụm hiện dẫn đường AA" "$ANS"
ask_yn "HUD kính lái có hiện gì không?"; note A2 "HUD hiện dẫn đường AA" "$ANS"

# A3 — sau một chỗ rẽ / đổi khoảng cách: số đọc có đổi theo không.
todo "Chờ khoảng cách tới chỗ rẽ đổi (hoặc qua một chỗ rẽ) rồi bấm Enter."
read_nav A3-nav-later
cap A3-logcat-instrument "logcat -d -v time | grep -E 'AbsBYDAutoDevice|BYDAutoInstrumentDevice' | tail -200"

# A4 — dừng dẫn đường.
todo "Dừng dẫn đường trên Android Auto."
read_nav A4-stopped

say ""
say "XONG — tóm tắt: $SUMMARY · HAL: $HALTSV"
say "Đọc: A2/A3 số đọc khác A1 (và đổi theo khoảng cách) ⇒ app AA BYD có ghi ô dẫn đường ⇒ Kachi đọc lại được."
say "     Không đổi + logcat không có setter 43f0/43fa ⇒ app AA BYD không chuyển ra ⇒ dịch ngược AndroidAutoNew.apk."
