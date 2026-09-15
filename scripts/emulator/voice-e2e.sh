#!/usr/bin/env bash
# ═══ E2E GIỌNG NÓI TRÊN MÁY ẢO — hai tầng, một lượt chạy ═════════════════════════════════════════════════════
#
# T1 (chữ → ý định → thi hành): `am broadcast … --es cmd say --es text "<câu>"` đi ĐÚNG đường mà ô *Gõ lệnh chữ*
#    đi (KachiTestBridge.runSay → VoiceDispatcher.preview + execute). Đọc `intents` / `replies` / `needs_confirm`.
# T2 (tiếng → nhận dạng → ý định): `--es cmd wav --es path <wav>` đi ĐÚNG đường mà phiên mic đi
#    (TestBridgeWav → VoiceWavProbe → VoiceRecognizer/sherpa). Đọc `heard` + `intents`. KHÔNG thi hành (theo thiết kế).
#
# Ràng buộc mức bằng chứng (CLAUDE.md §2): mọi con số script này in ra là [ĐO] **trên máy ảo API 29**, giọng TTS
# tổng hợp, KHÔNG có HAL xe ⇒ nhánh `Control` sẽ báo `✗ … xe không nhận lệnh` (đúng, vì không có xe). Ý định
# (`intents`) mới là thứ tầng này đo được; tác dụng phụ đo được là: app lên màn, ô đổi nội dung, hồ sơ đổi.
#
# Dùng:
#   scripts/emulator/voice-e2e.sh [--serial emulator-5554] [--only say|wav|all] [--apk <path>]
#                                 [--wavdir /tmp/kachi-voice-wav] [--modeldir <dir 4 tệp onnx>] [--out <dir>]
# Idempotent: chạy lại không cài lại APK/mô hình nếu đã sẵn sàng.
set -uo pipefail

SERIAL="emulator-5554"
ONLY="all"
APK=""
WAVDIR="/tmp/kachi-voice-wav"
MODELDIR=""
OUT="/tmp/kachi-voice-e2e"
PKG="com.byd.launcher"
HOME_ACT="$PKG/com.byd.clusternav.launcher.KachiHomeActivity"
MODEL_ID="zipformer-vi-2025-04-20"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CASES="$HERE/voice-cases.tsv"

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2;;
    --only) ONLY="$2"; shift 2;;
    --apk) APK="$2"; shift 2;;
    --wavdir) WAVDIR="$2"; shift 2;;
    --modeldir) MODELDIR="$2"; shift 2;;
    --out) OUT="$2"; shift 2;;
    --cases) CASES="$2"; shift 2;;
    *) echo "tham số lạ: $1" >&2; exit 2;;
  esac
done

mkdir -p "$OUT"
# Bằng chứng chứa `boot_id` của máy + tên hồ sơ do người dùng đặt ⇒ không để ngỏ trong /tmp dùng chung
# (cùng lệ `scripts/vehicle/common.sh`).
chmod 700 "$OUT" 2>/dev/null || true
T1_TSV="$OUT/t1-results.tsv"
T2_TSV="$OUT/t2-results.tsv"
adbs() { "$ADB" -s "$SERIAL" "$@" </dev/null; }
# Biến thể DUY NHẤT được phép đọc stdin (ghi tệp qua `run-as sh -c cat`).
adbs_stdin() { "$ADB" -s "$SERIAL" "$@"; }

# ── Tiện ích ────────────────────────────────────────────────────────────────────────────────────
die() { echo "✗ $*" >&2; exit 1; }
note() { echo "── $*"; }

# Bọc một chuỗi cho shell của **THIẾT BỊ**.
#
# [SOÁT 2026-09-15 · P2] Cột `text` của bộ ca và `$PROFILE` (tên hồ sơ **do người dùng đặt trên máy**, đọc về
# qua `pick_profile`) đi thẳng vào `adbs shell "am broadcast … $*"`. Bọc tay bằng một cặp nháy đơn là đủ cho
# khoảng trắng, nhưng MỘT dấu `'` trong tên hồ sơ thì thoát ra khỏi cặp ấy ⇒ chạy lệnh tuỳ ý trên thiết bị.
shq() {
  local s=$1 q="'"
  s=${s//$q/$q\\$q$q}
  printf '%s' "$q$s$q"
}

# ═══ CHỐT: KHÔNG chạy bộ ca này trên XE THẬT ═════════════════════════════════════════════════════
#
# [SOÁT 2026-09-15 · P0] Bộ ca `voice-cases.tsv` có những ca **thi hành thật, không hỏi lại**
# (`auto_confirm=1`): t27 *"mở khoá cửa"*, t29 gói *"mở cửa + đèn đọc"*, các ca mở app / dẫn đường / đổi hồ sơ.
# Trên máy ảo chúng vô hại (không có HAL nên `Control` chỉ trả *"xe không nhận lệnh"* — xem đầu tệp). Trên xe
# thì đúng những ca ấy **mở khoá cửa một chiếc xe đang đỗ**, không một cú xác nhận nào — đúng thứ CLAUDE.md §4
# cấm: một lệnh đổi state hệ thống mà không nêu tường minh nó nhắm cái gì.
#
# Cùng khuôn `require_emulator` của `scripts/emulator/e2e-smoke.sh` (*"refusing to touch a real head unit"*).
require_emulator() {
  case "$SERIAL" in
    emulator-*) ;;
    *)
      [ "${ALLOW_NON_EMULATOR:-}" = "YES" ] || die \
        "«$SERIAL» không phải máy ảo — từ chối chạy bộ ca có lệnh thi hành thật (t27 mở khoá cửa…) trên đầu xe.
   Đặt ALLOW_NON_EMULATOR=YES nếu thật sự có chủ ý, và đọc lại voice-cases.tsv cột auto trước đã."
      echo "⚠ ALLOW_NON_EMULATOR=YES — đang chạy trên thiết bị THẬT «$SERIAL»"
      ;;
  esac
}

# ═══ DỌN: đóng lại cửa cầu kiểm thử ══════════════════════════════════════════════════════════════
#
# [SOÁT 2026-09-15 · P1] `enable_test_mode` mở cửa ~58 phút. `KachiTestBridge` là receiver `exported=true`
# (mọi app trên máy bắn vào được — xem KDoc của nó), và công tắc này là **cổng duy nhất** chặn nó. Chạy xong mà
# để ngỏ là để lại một mặt điều khiển toàn thiết bị gần một giờ. Chạy qua `trap` nên nó vẫn dọn khi script chết
# giữa chừng (Ctrl-C, `die`) — đúng luật CLAUDE.md §5: đổi state ngoài tiến trình thì phải có đường trả lại.
TEST_MODE_ON=0
cleanup() {
  local rc=$?
  if [ "$TEST_MODE_ON" = "1" ]; then
    "$ADB" -s "$SERIAL" shell am force-stop "$PKG" </dev/null >/dev/null 2>&1 || true
    "$ADB" -s "$SERIAL" shell "run-as $PKG rm -f shared_prefs/kachi_test_bridge.xml" </dev/null >/dev/null 2>&1 \
      || "$ADB" -s "$SERIAL" shell "rm -f /data/data/$PKG/shared_prefs/kachi_test_bridge.xml" </dev/null >/dev/null 2>&1 \
      || true
    echo "── đã tắt chế độ kiểm thử"
  fi
  return $rc
}
trap cleanup EXIT

# Bắn một lệnh cầu kiểm thử; in JSON thuần ra stdout (rỗng nếu không lấy được).
bridge() {
  local raw
  raw="$(adbs shell "am broadcast -a $PKG.TEST -p $PKG $*" 2>&1)"
  printf '%s' "$raw" | python3 "$HERE/voice_e2e_json.py" extract
}

state_json() { bridge "--es cmd state"; }

# ── 0. Máy ảo + gói ─────────────────────────────────────────────────────────────────────────────
require_emulator
adbs get-state >/dev/null 2>&1 || die "không thấy thiết bị $SERIAL"
if [ -n "$APK" ]; then
  note "cài $APK"
  adbs install -r "$APK" || die "cài APK hỏng"
fi
adbs shell pm path "$PKG" >/dev/null 2>&1 || die "$PKG chưa cài (truyền --apk)"

# ── Cách ghi vào vùng dữ liệu của app: `run-as` (bản debuggable) HOẶC root (máy ảo eng/userdebug) ──
# Hai đường vì hai ca thật: bản `vehicleTest` debuggable ⇒ `run-as` chạy trên CẢ xe lẫn máy ảo; bản `release`
# đang cài sẵn trên máy ảo thì KHÔNG debuggable, nhưng máy ảo cho `adb root` ⇒ vẫn đo được mà không phải cài lại.
# Trên XE THẬT chỉ có đường `run-as` (không root) — đó là lý do không bỏ nhánh nào.
DATA="/data/data/$PKG"
MODE=""
if adbs shell run-as "$PKG" true 2>/dev/null; then
  MODE="runas"
elif adbs root >/dev/null 2>&1 && sleep 2 && [ "$(adbs shell id -u | tr -d '\r')" = "0" ]; then
  MODE="root"
  APPUID="$(adbs shell dumpsys package "$PKG" | grep -m1 userId= | tr -d '\r' | sed 's/.*userId=\([0-9]*\).*/\1/')"
  [ -n "$APPUID" ] || die "không đọc được uid của $PKG"
else
  die "bản đang cài KHÔNG debuggable và máy không cho adb root ⇒ không bật được chế độ kiểm thử. Cài bản vehicleTest."
fi
note "đường ghi dữ liệu app: $MODE"

# Chép một tệp local vào <data>/<đích tương đối> với đúng chủ sở hữu + nhãn SELinux.
put_app_file() {
  local src="$1" rel="$2" dir
  dir="$(dirname "$rel")"
  if [ "$MODE" = "runas" ]; then
    adbs shell "run-as $PKG mkdir -p $dir"
    adbs_stdin shell "run-as $PKG sh -c 'cat > $rel'" < "$src"
  else
    adbs shell "mkdir -p $DATA/$dir"
    adbs push "$src" "/data/local/tmp/.kachi-put" >/dev/null || return 1
    adbs shell "cp /data/local/tmp/.kachi-put $DATA/$rel && rm -f /data/local/tmp/.kachi-put"
    adbs shell "chown $APPUID:$APPUID $DATA/$rel; chmod 660 $DATA/$rel; restorecon -R $DATA/$dir" >/dev/null 2>&1
  fi
}

# ── 1. Bật chế độ kiểm thử (TestBridgeStore: prefs `kachi_test_bridge`, khoá `test_bridge_until`) ──
# Giá trị = "<boot_id>:<elapsedRealtime lúc bật + 60 phút>" (TestBridgeWindow.encode). Đọc boot_id + uptime
# THẬT trên máy rồi dựng lại đúng khuôn đó; trừ bớt 2 phút để `left > WINDOW_MS` không bao giờ đúng (bị coi là
# giá trị sửa tay ⇒ cửa đóng). KHÔNG có đường bật bằng broadcast — có chủ ý, xem KDoc TestBridgeStore.
enable_test_mode() {
  local boot up until_ms tmp
  boot="$(adbs shell cat /proc/sys/kernel/random/boot_id | tr -d '\r\n')"
  up="$(adbs shell cat /proc/uptime | tr -d '\r' | cut -d' ' -f1)"
  until_ms="$(python3 -c "import sys;print(int(float(sys.argv[1])*1000)+3600000-120000)" "$up")"
  tmp="$OUT/kachi_test_bridge.xml"
  cat > "$tmp" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="test_bridge_until">$boot:$until_ms</string>
</map>
XML
  # force-stop TRƯỚC khi ghi: SharedPreferences giữ bản trong RAM, ghi đè tệp dưới chân một tiến trình đang chạy
  # thì bản RAM thắng (và có thể ghi đè ngược lại lúc app thoát) — tức công tắc "đã bật" mà cầu vẫn báo tắt.
  adbs shell am force-stop "$PKG"
  put_app_file "$tmp" "shared_prefs/kachi_test_bridge.xml" || die "ghi prefs hỏng"
  adbs shell am start -n "$HOME_ACT" >/dev/null
  sleep 4
}

start_home() { adbs shell am start -n "$HOME_ACT" >/dev/null; sleep 3; }

note "bật chế độ kiểm thử"
TEST_MODE_ON=1
enable_test_mode
LEFT="$(state_json | python3 "$HERE/voice_e2e_json.py" get test_mode_minutes_left)"
[ "${LEFT:-0}" -gt 0 ] 2>/dev/null || die "chế độ kiểm thử vẫn TẮT (còn $LEFT phút) — xem reply: $(state_json | head -c 400)"
note "chế độ kiểm thử: còn $LEFT phút"

# ── 2. Mô hình sherpa (T2) ──────────────────────────────────────────────────────────────────────
ensure_model() {
  local ready
  ready="$(state_json | python3 "$HERE/voice_e2e_json.py" get voice_model.ready)"
  if [ "$ready" = "True" ] || [ "$ready" = "true" ]; then note "mô hình đã sẵn sàng"; return 0; fi
  [ -n "$MODELDIR" ] || { echo "⚠ mô hình CHƯA có và không truyền --modeldir ⇒ bỏ tầng T2"; return 1; }
  note "nạp mô hình từ $MODELDIR (đường (b): chép thẳng vào filesDir ⇒ VoiceModelStore.isReady() true, không cần UI)"
  local f
  for f in encoder.onnx decoder.onnx joiner.onnx tokens.txt; do
    [ -f "$MODELDIR/$f" ] || die "thiếu $MODELDIR/$f"
    put_app_file "$MODELDIR/$f" "files/sherpa/$MODEL_ID/$f" || die "chép $f hỏng"
  done
  # Đổi mô hình dưới chân tiến trình đang chạy: `VoiceEngine` giữ recognizer cho cả tiến trình ⇒ khởi động lại
  # cho chắc (và cũng là cách duy nhất để `isReady` được đọc lại từ đĩa).
  adbs shell am force-stop "$PKG"; start_home
  ready="$(state_json | python3 "$HERE/voice_e2e_json.py" get voice_model.ready)"
  note "voice_model.ready = $ready"
  [ "$ready" = "True" ] || [ "$ready" = "true" ]
}

MODEL_OK=0
if [ "$ONLY" = "all" ] || [ "$ONLY" = "wav" ]; then ensure_model && MODEL_OK=1; fi

# Hồ sơ có thật để thay @PROFILE@ (ưu tiên hồ sơ KHÔNG phải hồ sơ đang dùng — đổi sang chính nó thì không đo được gì).
PROFILE="$(state_json | python3 "$HERE/voice_e2e_json.py" pick_profile)"
note "hồ sơ dùng cho ca đổi hồ sơ: ${PROFILE:-<không có>}"

# ── 3. T1 ───────────────────────────────────────────────────────────────────────────────────────
run_t1() {
  : > "$T1_TSV"
  local id lop text kinds want conf auto side
  while IFS=$'\t' read -r id lop text kinds want conf auto side <&3; do
    case "${id:-}" in ''|'#'*) continue;; esac
    text="${text//@PROFILE@/$PROFILE}"; want="${want//@PROFILE@/$PROFILE}"; side="${side//@PROFILE@/$PROFILE}"
    local args="--es cmd say --es text $(shq "$text")"
    [ "$auto" = "1" ] && args="$args --ez auto_confirm true"
    local json; json="$(bridge "$args")"
    if [ -z "$json" ]; then
      start_home; json="$(bridge "$args")"
    fi
    sleep 1
    local sidereal="-"
    case "$side" in
      resumed:*) sleep 2; sidereal="$(adbs shell dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity' | tr -d '\r')";;
      slot:*) sidereal="$(state_json | python3 "$HERE/voice_e2e_json.py" slot "${side#slot:}")";;
      profile:*) sidereal="$(state_json | python3 "$HERE/voice_e2e_json.py" get profile.active)";;
      media) sidereal="$(adbs shell dumpsys media_session | grep -m1 -i 'package=' | tr -d '\r')";;
    esac
    # Sau mỗi ca mở app: đưa Kachi lên lại để ca sau còn móc (hooks sống theo Activity, không theo tiêu điểm).
    case "$side" in resumed:*) start_home;; esac
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$id" "$lop" "$text" "$kinds" "$want" "$conf" "$side" "$sidereal" "$json" >> "$T1_TSV"
    echo "  [$id] $text"
  done 3< "$CASES"
}

# ── 4. T2 ───────────────────────────────────────────────────────────────────────────────────────
run_t2() {
  : > "$T2_TSV"
  [ -d "$WAVDIR" ] || die "không có $WAVDIR — chạy scripts/emulator/voice-wavgen.sh trước"
  local dst="/sdcard/Android/data/$PKG/files/wavin"
  adbs shell "mkdir -p $dst"
  local id text w
  while IFS=$'\t' read -r id text <&3; do
    case "${id:-}" in ''|'#'*) continue;; esac
    w="$WAVDIR/$id.wav"; [ -f "$w" ] || { echo "  ⚠ thiếu $w"; continue; }
    adbs push "$w" "$dst/$id.wav" >/dev/null
    local json; json="$(bridge "--es cmd wav --es path $(shq "$dst/$id.wav")")"
    if [ -z "$json" ]; then start_home; json="$(bridge "--es cmd wav --es path $(shq "$dst/$id.wav")")"; fi
    printf '%s\t%s\t%s\n' "$id" "$text" "$json" >> "$T2_TSV"
    echo "  [$id] $text"
  done 3< "$WAVDIR/cases.tsv"
}

case "$ONLY" in
  say) note "T1 (say)"; run_t1;;
  wav) note "T2 (wav)"; [ "$MODEL_OK" = "1" ] && run_t2 || die "mô hình chưa sẵn sàng";;
  all) note "T1 (say)"; run_t1; if [ "$MODEL_OK" = "1" ]; then note "T2 (wav)"; run_t2; else echo "⚠ bỏ T2: mô hình chưa sẵn sàng"; fi;;
  *) die "--only phải là say|wav|all";;
esac

# ── 5. Bảng kết quả ─────────────────────────────────────────────────────────────────────────────
python3 "$HERE/voice_e2e_json.py" report "$T1_TSV" "$T2_TSV" | tee "$OUT/report.md"
echo
echo "== TSV thô: $T1_TSV · $T2_TSV — bảng: $OUT/report.md"
