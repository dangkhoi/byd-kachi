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
# (`auto_confirm=1`): t109 *"mở cốp"*, t111 gói *"mở cốp + đèn đọc"*, các ca mở app / dẫn đường / đổi hồ sơ.
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
        "«$SERIAL» không phải máy ảo — từ chối chạy bộ ca có lệnh thi hành thật (t109 mở cốp…) trên đầu xe.
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
    # [SOÁT Pass 1 · 2026-09-16] Trả 5 khoá `prefs_set` về mặc định TRƯỚC khi đóng cửa cầu: mỗi ca đã tự dọn
    # phần của nó, nhưng một lượt chết giữa chừng (Ctrl-C, `die`) thì không — và để `voice_confirm_ids` còn
    # một mã đang bật nghĩa là lượt chạy SAU đo nhầm một cổng đang mở sẵn. Cùng luật CLAUDE.md §5: đổi state
    # ngoài tiến trình thì phải có đường trả lại, và đường đó phải chạy cả khi script chết.
    reset_all_prefs || true
  fi
  if [ "$TEST_MODE_ON" = "1" ]; then
    "$ADB" -s "$SERIAL" shell am force-stop "$PKG" </dev/null >/dev/null 2>&1 || true
    "$ADB" -s "$SERIAL" shell "run-as $PKG rm -f shared_prefs/kachi_test_bridge.xml" </dev/null >/dev/null 2>&1 \
      || "$ADB" -s "$SERIAL" shell "rm -f /data/data/$PKG/shared_prefs/kachi_test_bridge.xml" </dev/null >/dev/null 2>&1 \
      || true
    echo "── đã tắt chế độ kiểm thử"
  fi
  # DEBT-E2E-SH(c) 2026-09-23: dọn WAV đã đẩy vào máy + adb unroot (adb root bật ở §196 không bao giờ nhả).
  "$ADB" -s "$SERIAL" shell "rm -f /sdcard/Android/data/$PKG/files/wavin/*" </dev/null >/dev/null 2>&1 || true
  "$ADB" -s "$SERIAL" unroot </dev/null >/dev/null 2>&1 || true
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

# ═══ prefs_set — đặt/dọn một khoá trong DANH SÁCH TRẮNG của cầu kiểm thử ═════════════════════════
#
# [SOÁT Pass 1 · 2026-09-16] Sinh ra để trả lại lớp canh E2E của cổng xác nhận: từ 1.66 mặc định là
# *"không hỏi gì cả"*, nên một ca `confirm=1` chỉ có nghĩa khi nó **tự bật** mã của nó trước
# (`TestBridgeCommands.WRITABLE_PREFS_KEYS` giữ danh sách trắng; không có đường ghi khoá tuỳ ý).
prefs_set() {
  local key=$1 value=${2:-} json
  json="$(bridge "--es cmd prefs_set --es key $(shq "$key") --es text $(shq "$value")")"
  case "$json" in
    *'"ok":true'*) ;;
    *) echo "  ⚠ prefs_set $key=$value KHÔNG ăn: $(printf '%s' "$json" | head -c 160)"; return 1;;
  esac
}

# Mặc định của từng khoá — khai MỘT chỗ. Khác "chuỗi rỗng cho tất cả": `top_strip_labels` là công tắc nên
# rỗng là một **giá trị sai** (cầu từ chối), còn `voice_follow_up_ms` mặc định là 5000 chứ không phải 0.
prefs_default() {
  case "$1" in
    voice_confirm_ids) printf '';;
    voice_ask_aloud) printf '0';;
    voice_follow_up_ms) printf '5000';;
    voice_mic_source) printf '0';;
    top_strip_labels) printf '1';;
    *) printf '';;
  esac
}

PREFS_ALL="voice_confirm_ids voice_ask_aloud voice_follow_up_ms voice_mic_source top_strip_labels"
reset_all_prefs() {
  local k
  for k in $PREFS_ALL; do prefs_set "$k" "$(prefs_default "$k")" >/dev/null 2>&1 || true; done
}

# Áp cột `prefs` của một ca ("-" = không làm gì). Dạng `k=v;k=v`.
apply_case_prefs() {
  local spec=$1 pair key value
  case "$spec" in ''|'-') return 0;; esac
  local IFS=';'
  for pair in $spec; do
    [ -n "$pair" ] || continue
    key=${pair%%=*}; value=${pair#*=}
    [ "$key" = "$pair" ] && value=""
    prefs_set "$key" "$value" || true
  done
}

# Trả các khoá của một ca về mặc định (chỉ những khoá ca đó đụng tới — rẻ hơn dọn cả 5 sau mỗi ca).
reset_case_prefs() {
  local spec=$1 pair key
  case "$spec" in ''|'-') return 0;; esac
  local IFS=';'
  for pair in $spec; do
    [ -n "$pair" ] || continue
    key=${pair%%=*}
    prefs_set "$key" "$(prefs_default "$key")" >/dev/null 2>&1 || true
  done
}

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

# ═══ Tác dụng phụ `resumed:<pkg>` — đọc `mResumedActivity` bằng POLL, không đọc một lần ═══════════
#
# [CLOSE-2 · 2026-09-26] Bản cũ đọc `dumpsys activity activities` ĐÚNG MỘT LẦN sau 3 s cố định (`sleep 1` +
# `sleep 2`). t52 *"mở bài Diễm Xưa trên YouTube Music"* lệch giữa các lượt (lượt 2 FAIL / lượt 3 PASS, backlog
# CLOSE-2) dù app trả `✓ … đang phát` đúng. Nghi ban đầu [ĐOÁN]: 3 s chưa đủ cho YT Music lên — gốc THẬT đo được
# nằm ở khối ⚠ dưới (display). Poll vẫn đúng về nguyên tắc: ca đo *"app CÓ lên màn không"*, không đo *"lên trong
# bao lâu"* ⇒ poll mỗi 0,5 s, trần 8 s (`RESUMED_WAIT_S`), khớp là dừng ngay. Hết trần thì in dòng đọc được CUỐI
# (để báo cáo nói rõ app nào đang chiếm màn) kèm ⏱ để phân biệt "chưa kịp" với "không bao giờ lên".
# `SECONDS` (bash 3.2 của macOS không có EPOCHREALTIME) ⇒ độ phân giải 1 s cho phần đo giờ; nhịp poll vẫn 0,5 s.
#
# ⚠ Đọc MỌI display, không chỉ display 0. [ĐO 2026-09-26, lượt stable-run1] poll 8 s vẫn FAIL t52: `am stack list`
# cho thấy task YT Music `visible=true` trên `displayId=65` = màn ảo ô 0 của Kachi (`kachi-slot-0-…`, Kachi gieo lại
# app trong ô mỗi lần mở màn chính), và `dumpsys activity activities` in `mResumedActivity` RIÊNG cho từng
# `Display #N`. `grep -m1` cũ chỉ lấy dòng đầu = display 0 (Kachi) ⇒ app đã lên màn trong ô mà harness bảo "không
# lên". Với người lái, app hiện trong ô CŨNG là "lên màn" ⇒ khớp gói ở BẤT KỲ display nào; in kèm `#N` (display) để báo
# cáo nói rõ nó lên ở đâu. Không khớp ⇒ in dòng của display 0 (app nào đang chiếm màn chính).
RESUMED_WAIT_S=8
read_resumed_all() {
  adbs shell dumpsys activity activities | tr -d '\r' | awk '
    /^ *Display #[0-9]+/ { d=$2 }
    /mResumedActivity|topResumedActivity/ { sub(/^ +/, ""); print d " " $0 }'
}
# ⚠ Khớp `"$pkg/"` (dấu gạch của tên component trong `ActivityRecord{… pkg/.Activity}`), KHÔNG khớp `"$pkg"` trần:
# `com.google.android.youtube` là TIỀN TỐ của nhiều gói thật (`…youtube.music`, `…youtube.tv`), nên một lượt t38/t40
# *"mở YouTube"* sẽ PASS oan nếu YT Music đang chiếm màn. `com.google.android.apps.youtube.music` của t52 tình cờ
# không bị (có `apps.` ở giữa) — tức bài kiểm đang đúng vì may, không vì luật. Thêm một ký tự là hết ca may rủi.
poll_resumed() {
  local pkg=$1 all hit t0=$SECONDS
  while :; do
    all="$(read_resumed_all)"
    hit="$(printf '%s\n' "$all" | grep -m1 -F "$pkg/")"
    if [ -n "$hit" ]; then printf '%s ⏱%ss' "$hit" "$((SECONDS - t0))"; return 0; fi
    [ $((SECONDS - t0)) -lt "$RESUMED_WAIT_S" ] || break
    sleep 0.5
  done
  printf '%s ⏱>%ss' "$(printf '%s\n' "$all" | head -1)" "$RESUMED_WAIT_S"
  return 1
}

# ═══ Trạng thái ĐẦU VÀO xác định cho ca có kiểm `resumed:*` / `slot:*` ═══════════════════════════
#
# [CLOSE-2 · 2026-09-26] Ca kiểm "app lên màn" chỉ có nghĩa khi TRƯỚC câu nói app đó CHƯA ở trên đỉnh và Kachi
# đang là activity resumed (móc của cầu sống theo Activity — xem ghi chú sau mỗi ca `resumed:`). Nền cũ phụ thuộc
# ca đứng trước: t45 *"mở bản đồ"* (side "-") để Maps trên đỉnh suốt t46–t52. Ở đây đưa về MỘT trạng thái:
# `start_home` (Kachi resumed). KHÔNG `force-stop` app đích: khởi động lạnh trên máy ảo 2 lõi thêm phương sai
# vài giây — đúng thứ làm ca lệch giữa các lượt — trong khi ca đo "app lên màn", không đo "khởi động lạnh".
# `LAST_ENDED_HOME` tránh gọi `start_home` hai lần liên tiếp (ca `resumed:` trước đã kết thúc bằng `start_home`).
LAST_ENDED_HOME=0
settle_before_side() {
  case "$1" in
    resumed:*|slot:*) [ "$LAST_ENDED_HOME" = "1" ] || start_home;;
  esac
}

note "bật chế độ kiểm thử"
TEST_MODE_ON=1
enable_test_mode
LEFT="$(state_json | python3 "$HERE/voice_e2e_json.py" get test_mode_minutes_left)"
[ "${LEFT:-0}" -gt 0 ] 2>/dev/null || die "chế độ kiểm thử vẫn TẮT (còn $LEFT phút) — xem reply: $(state_json | head -c 400)"
note "chế độ kiểm thử: còn $LEFT phút"
# Nền sạch cho cả lượt chạy: một lượt trước chết giữa chừng có thể để lại một mã đang bật trong
# `voice_confirm_ids` — và ca nào cũng đo *"mặc định không hỏi gì"*, nên nền bẩn làm hỏng cả bảng.
reset_all_prefs
note "prefs giọng nói + nhãn chip đã về mặc định"

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
    # DEBT-E2E-SH(b) 2026-09-23: verify sha256 sau chép — hỏng ngầm (chép cụt) biểu hiện thành "model init lỗi"
    # khó lần. So sha local vs trên máy; lệch ⇒ die NGAY với đúng nguyên nhân.
    local want got
    want="$(shasum -a 256 "$MODELDIR/$f" 2>/dev/null | cut -d' ' -f1)"
    got="$(adbs shell "run-as $PKG sha256sum files/sherpa/$MODEL_ID/$f 2>/dev/null || sha256sum /data/data/$PKG/files/sherpa/$MODEL_ID/$f 2>/dev/null" | tr -d '\r' | cut -d' ' -f1)"
    [ -n "$want" ] && [ -n "$got" ] && [ "$want" != "$got" ] && die "chép $f LỆCH sha256 (local=$want máy=$got) — chép cụt/hỏng"
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
  local id lop text kinds want conf auto side prefs
  while IFS=$'\t' read -r id lop text kinds want conf auto side prefs <&3; do
    case "${id:-}" in ''|'#'*) continue;; esac
    text="${text//@PROFILE@/$PROFILE}"; want="${want//@PROFILE@/$PROFILE}"; side="${side//@PROFILE@/$PROFILE}"
    # Nền xác định TRƯỚC ca có kiểm `resumed:`/`slot:` (CLOSE-2, xem `settle_before_side`).
    settle_before_side "$side"
    # Cột `prefs` đặt TRƯỚC lượt `say`: từ 1.66 một ca `confirm=1` chỉ hỏi lại khi mã của nó đang được bật.
    apply_case_prefs "${prefs:--}"
    local args="--es cmd say --es text $(shq "$text")"
    [ "$auto" = "1" ] && args="$args --ez auto_confirm true"
    local json; json="$(bridge "$args")"
    if [ -z "$json" ]; then
      start_home; json="$(bridge "$args")"
    fi
    sleep 1
    local sidereal="-"
    case "$side" in
      # CLOSE-2: poll ≤ RESUMED_WAIT_S thay cho `sleep 2` + đọc một lần (xem `poll_resumed`).
      resumed:*) sidereal="$(poll_resumed "${side#resumed:}")";;
      slot:*) sidereal="$(state_json | python3 "$HERE/voice_e2e_json.py" slot "${side#slot:}")";;
      profile:*) sidereal="$(state_json | python3 "$HERE/voice_e2e_json.py" get profile.active)";;
      # L7 — bố cục bằng giọng nói: đọc PRESET ĐANG DÙNG từ chính bridge `state` (cùng nguồn mà màn hình vẽ),
      # không đoán qua ảnh chụp màn hình.
      preset:*) sidereal="$(state_json | python3 "$HERE/voice_e2e_json.py" get layout.preset)";;
      # R14 — nhãn chip đọc từ CHÍNH nguồn màn hình vẽ (`HomeUiState.topStrip`), không phải từ prefs và
      # không phải từ một ảnh chụp: `prefs_set top_strip_labels` đi qua đúng lambda mà ô tích trong Cài đặt đi.
      chip_labels:*) sidereal="$(state_json | python3 "$HERE/voice_e2e_json.py" get bars.chip_labels)";;
      media) sidereal="$(adbs shell dumpsys media_session | grep -m1 -i 'package=' | tr -d '\r')";;
    esac
    # Sau mỗi ca mở app: đưa Kachi lên lại để ca sau còn móc (hooks sống theo Activity, không theo tiêu điểm).
    case "$side" in resumed:*) start_home; LAST_ENDED_HOME=1;; *) LAST_ENDED_HOME=0;; esac
    # Dọn NGAY sau ca: một mã còn bật sẽ làm ca kế tiếp (không khai `prefs`) bị hỏi lại ⇒ FAIL sai địa chỉ.
    reset_case_prefs "${prefs:--}"
    # ⚠ Cột `prefs` đi SAU `json` (cột thứ 10): `voice_e2e_json.py report` zip đúng 9 tên đầu, nên thêm ở
    # cuối là không đụng tới bộ đọc — thêm ở giữa thì mọi cột lệch một chỗ, im lặng.
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$id" "$lop" "$text" "$kinds" "$want" "$conf" "$side" "$sidereal" "$json" "${prefs:--}" >> "$T1_TSV"
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
  # KHÔNG dùng `a && b || c`: run_t2 hụt (thiếu WAVDIR, push lỗi…) cũng rơi vào nhánh `||` và
  # `die` báo nhầm nguyên nhân là "mô hình chưa sẵn sàng" — đúng loại chẩn đoán sai địa chỉ mà
  # CLAUDE.md §2 cấm.
  wav) note "T2 (wav)"; [ "$MODEL_OK" = "1" ] || die "mô hình chưa sẵn sàng"; run_t2;;
  all) note "T1 (say)"; run_t1; if [ "$MODEL_OK" = "1" ]; then note "T2 (wav)"; run_t2; else echo "⚠ bỏ T2: mô hình chưa sẵn sàng"; fi;;
  *) die "--only phải là say|wav|all";;
esac

# ── 5. Bảng kết quả ─────────────────────────────────────────────────────────────────────────────
python3 "$HERE/voice_e2e_json.py" report "$T1_TSV" "$T2_TSV" | tee "$OUT/report.md"
echo
echo "== TSV thô: $T1_TSV · $T2_TSV — bảng: $OUT/report.md"
