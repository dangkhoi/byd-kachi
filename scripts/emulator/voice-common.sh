#!/usr/bin/env bash
# ═══ HÀM DÙNG CHUNG cho harness giọng nói trên máy ảo — `source` từ script khác, KHÔNG chạy trực tiếp ═══════════
#
# 2.91 VOICE-APP-NAMES (spec docs/specs/kachi-290-voice-app-names.html §4.11) khai bản ĐẦU; 2.93 DEBT-VOICE-COMMON-SH
# (spec docs/specs/kachi-293-voice.html) GỘP bằng pure move: `voice-e2e.sh` nay source tệp này thay cho bản hàm riêng,
# và `voice-audio-e2e.sh` dùng chung phép kiểm ngôn ngữ (`require_voice_vi`, VOICE-AUDIO-E2E-LANG). Hàm nào hai bản
# lệch nhau thì giữ bản ĐẦY ĐỦ hơn (đã ghi ở từng hàm) — không đổi hành vi đã đo của harness nền.
#
# Cần trước khi source: SERIAL · PKG · ADB (đường dẫn adb) · HERE · OUT. Định nghĩa: adbs, adbs_stdin, die, note, shq,
# require_emulator, bridge, state_json, json_get, start_home, detect_data_mode, put_app_file, enable_test_mode,
# disable_test_mode, require_voice_vi.

adbs() { "$ADB" -s "$SERIAL" "$@" </dev/null; }
# Biến thể DUY NHẤT được phép đọc stdin (ghi tệp qua `run-as sh -c cat`).
adbs_stdin() { "$ADB" -s "$SERIAL" "$@"; }
die() { echo "✗ $*" >&2; exit 1; }
note() { echo "── $*"; }

# Bọc một chuỗi cho shell của THIẾT BỊ.
#
# [SOÁT 2026-09-15 · P2] Cột `text` của bộ ca và tên hồ sơ (do người dùng đặt trên máy, đọc về qua `pick_profile`) đi
# thẳng vào `adbs shell "am broadcast … $*"`. Bọc tay bằng một cặp nháy đơn là đủ cho khoảng trắng, nhưng MỘT dấu `'`
# trong tên hồ sơ thì thoát ra khỏi cặp ấy ⇒ chạy lệnh tuỳ ý trên thiết bị.
shq() {
  local s=$1 q="'"
  s=${s//$q/$q\\$q$q}
  printf '%s' "$q$s$q"
}

# ═══ CHỐT: KHÔNG chạy trên XE THẬT ═════════════════════════════════════════════════════════════════════
#
# [SOÁT 2026-09-15 · P0] Bộ ca giọng nói có những ca **thi hành thật, không hỏi lại** (`voice-cases.tsv` cột auto: t109
# *"mở cốp"*, t111 gói *"mở cốp + đèn đọc"*, mở app / dẫn đường / đổi hồ sơ), harness dạy tên thì gỡ/cài lại app. Trên máy
# ảo chúng vô hại (không HAL); trên xe là mở khoá cửa một chiếc xe đang đỗ không qua xác nhận — đúng thứ CLAUDE.md §4 cấm.
# Cùng khuôn `require_emulator` của `scripts/emulator/e2e-smoke.sh`.
# $1 (tuỳ chọn) — lý do riêng của script gọi, in kèm lời từ chối (vd bộ ca nào nguy hiểm).
require_emulator() {
  case "$SERIAL" in
    emulator-*) ;;
    *)
      [ "${ALLOW_NON_EMULATOR:-}" = "YES" ] || die \
        "«${SERIAL}» không phải máy ảo — từ chối chạy${1:+: $1}.
   Đặt ALLOW_NON_EMULATOR=YES nếu thật sự có chủ ý."
      echo "⚠ ALLOW_NON_EMULATOR=YES — đang chạy trên thiết bị THẬT «${SERIAL}»"
      ;;
  esac
}

# Bắn một lệnh cầu kiểm thử; in JSON thuần ra stdout (rỗng nếu không lấy được).
bridge() {
  local raw
  raw="$(adbs shell "am broadcast -a $PKG.TEST -p $PKG $*" 2>&1)"
  printf '%s' "$raw" | python3 "$HERE/voice_e2e_json.py" extract
}

state_json() { bridge "--es cmd state"; }
json_get() { python3 "$HERE/voice_e2e_json.py" get "$1"; }

HOME_ACT="${HOME_ACT:-$PKG/com.byd.clusternav.launcher.KachiHomeActivity}"
start_home() { adbs shell am start -n "$HOME_ACT" >/dev/null; sleep 3; }

# ── Cách ghi vào vùng dữ liệu của app: `run-as` (bản debuggable) HOẶC root (máy ảo eng/userdebug) ──
# Hai đường vì hai ca thật: bản `vehicleTest` debuggable ⇒ `run-as` chạy trên CẢ xe lẫn máy ảo; bản `release` đang cài
# sẵn trên máy ảo thì KHÔNG debuggable, nhưng máy ảo cho `adb root` ⇒ vẫn đo được mà không phải cài lại. Trên XE THẬT chỉ
# có đường `run-as` (không root) — đó là lý do không bỏ nhánh nào.
DATA="/data/data/$PKG"
MODE=""
detect_data_mode() {
  if adbs shell run-as "$PKG" true 2>/dev/null; then
    MODE="runas"
  elif adbs root >/dev/null 2>&1 && sleep 2 && [ "$(adbs shell id -u | tr -d '\r')" = "0" ]; then
    MODE="root"
    APPUID="$(adbs shell dumpsys package "$PKG" | grep -m1 userId= | tr -d '\r' | sed 's/.*userId=\([0-9]*\).*/\1/')"
    [ -n "$APPUID" ] || die "không đọc được uid của $PKG"
  else
    die "bản đang cài KHÔNG debuggable và máy không cho adb root ⇒ không bật được chế độ kiểm thử. Cài bản vehicleTest."
  fi
}

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

# ── Bật chế độ kiểm thử (TestBridgeStore: prefs `kachi_test_bridge`, khoá `test_bridge_until`) ──
# Giá trị = "<boot_id>:<elapsedRealtime lúc bật + 60 phút>" (TestBridgeWindow.encode). Đọc boot_id + uptime THẬT trên máy
# rồi dựng lại đúng khuôn đó; trừ bớt 2 phút để `left > WINDOW_MS` không bao giờ đúng (bị coi là giá trị sửa tay ⇒ cửa
# đóng). KHÔNG có đường bật bằng broadcast — có chủ ý, xem KDoc TestBridgeStore. Cửa mở ~58 phút.
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
  # force-stop TRƯỚC khi ghi: SharedPreferences giữ bản trong RAM, ghi đè tệp dưới chân một tiến trình đang chạy thì bản
  # RAM thắng (và có thể ghi đè ngược lại lúc app thoát) — tức công tắc "đã bật" mà cầu vẫn báo tắt.
  adbs shell am force-stop "$PKG"
  put_app_file "$tmp" "shared_prefs/kachi_test_bridge.xml" || die "ghi prefs hỏng"
  start_home
  sleep 1
}

disable_test_mode() {
  adbs shell am force-stop "$PKG" >/dev/null 2>&1 || true
  adbs shell "run-as $PKG rm -f shared_prefs/kachi_test_bridge.xml" >/dev/null 2>&1 \
    || adbs shell "rm -f $DATA/shared_prefs/kachi_test_bridge.xml" >/dev/null 2>&1 || true
}

# ═══ Tiếng GIỌNG NÓI phải là tiếng Việt — kiểm TRƯỚC khi so preview / đo câu đọc (soát 2.87 · voice P3) ══════════════
#
# Preview mà cầu kiểm thử dựng (`previewOf`, KachiTestBridge) và câu ĐỌC (Piper tiếng Việt) theo tiếng GIỌNG NÓI
# (`Strings.current.voice`: English ⇒ English, mọi tiếng khác — kể cả 简体中文/ไทย/Melayu — ⇒ tiếng Việt; spec
# kachi-i18n-zh-th-ms R6). Kachi để English, hoặc "Theo xe" (AUTO) trên máy ảo locale en-US mặc định ⇒ `voice-e2e.sh`
# FAIL "preview thiếu …" hàng loạt, `voice-audio-e2e.sh` đo giờ dựng câu ĐỌC trên câu tiếng Anh — không một dòng nào nói
# gốc là ngôn ngữ. Cầu KHÔNG có lệnh đổi ngôn ngữ (thêm là quyết định của owner) và harness KHÔNG tự ghi prefs ngôn ngữ ⇒
# KIỂM rồi DỪNG với lời nhắc đúng địa chỉ. Ngôn ngữ theo HỒ SƠ ⇒ script gọi kiểm lại sau mỗi ca đổi hồ sơ.
# Đọc `ORIG_PROFILE` (hồ sơ lúc bắt đầu, script gọi đặt — rỗng ⇒ bỏ câu so sánh).
require_voice_vi() {
  local why=$1 json vl mode loc prof where orig="${ORIG_PROFILE:-}"
  json="$(state_json)"
  vl="$(printf '%s' "$json" | python3 "$HERE/voice_e2e_json.py" get look.voice_lang)"
  mode="$(printf '%s' "$json" | python3 "$HERE/voice_e2e_json.py" get look.lang)"
  # [soát 2.87 vòng 2 · P3] Nêu ĐÚNG TÊN hồ sơ đang mang ngôn ngữ sai (đọc cùng bản state). Sau ca đổi hồ sơ, `cleanup`
  # (trap EXIT của `die`) trả máy về hồ sơ lúc bắt đầu ⇒ câu "hồ sơ đang dùng" chỉ QA sửa nhầm hồ sơ (đang đúng tiếng Việt).
  prof="$(printf '%s' "$json" | python3 "$HERE/voice_e2e_json.py" get profile.active)"
  where="cho hồ sơ «${prof:-?}»"
  if [ -n "$prof" ] && [ -n "$orig" ] && [ "$prof" != "$orig" ]; then
    where="$where (KHÔNG phải hồ sơ lúc bắt đầu «${orig}» — lúc dọn, harness đã trả máy về «${orig}»; mở hồ sơ «${prof}» để sửa)"
  fi
  if [ -z "$vl" ]; then
    # APK cũ chưa phơi `look.voice_lang` ⇒ suy như LangMode.resolve: AUTO = locale máy vi* ⇒ VI, còn lại ⇒ EN.
    case "$mode" in
      EN) vl="en";;
      AUTO)
        loc="$(adbs shell getprop persist.sys.locale | tr -d '\r')"
        [ -n "$loc" ] || loc="$(adbs shell getprop ro.product.locale | tr -d '\r')"
        case "$loc" in vi*) vl="vi";; *) vl="en";; esac;;
      VI|ZH|TH|MS) vl="vi";;
      *) die "$why: không đọc được ngôn ngữ của Kachi (look.lang='${mode}') ${where} — xem reply: $(printf '%s' "$json" | head -c 300)";;
    esac
  fi
  [ "$vl" = "vi" ] || die "$why: Kachi đang nói tiếng '$vl' (look.lang=${mode:-?}) nhưng harness mong câu TIẾNG VIỆT. Đặt Cài đặt › Hiển thị › Ngôn ngữ = Tiếng Việt (hoặc 简体中文/ไทย/Melayu — giọng nói vẫn là tiếng Việt) ${where} rồi chạy lại."
  note "tiếng giọng nói: $vl (look.lang=${mode:-?}) — $why"
}
