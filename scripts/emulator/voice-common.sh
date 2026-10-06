#!/usr/bin/env bash
# ═══ HÀM DÙNG CHUNG cho harness giọng nói trên máy ảo — `source` từ script khác, KHÔNG chạy trực tiếp ═══════════
#
# 2.91 VOICE-APP-NAMES (spec docs/specs/kachi-290-voice-app-names.html §4.11). Bản khai ĐẦU của các hàm mà
# `voice-e2e.sh` hiện vẫn mang bản riêng (adbs · shq · require_emulator · bridge · state_json · start_home · đường ghi
# dữ liệu app · bật test-mode). Chưa gộp `voice-e2e.sh` vào đây ở lượt này có CHỦ Ý: nó là harness NỀN đo trước/sau
# của chính thay đổi này — đổi nó giữa hai lần đo là thêm một biến. Nợ ghi ở backlog `DEBT-VOICE-COMMON-SH`.
#
# Cần trước khi source: SERIAL · PKG · ADB · HERE · OUT. Định nghĩa: adbs, adbs_stdin, die, note, shq, require_emulator,
# bridge, state_json, json_get, start_home, detect_data_mode, put_app_file, enable_test_mode, disable_test_mode.

adbs() { "$ADB" -s "$SERIAL" "$@" </dev/null; }
adbs_stdin() { "$ADB" -s "$SERIAL" "$@"; }
die() { echo "✗ $*" >&2; exit 1; }
note() { echo "── $*"; }

# Bọc một chuỗi cho shell của THIẾT BỊ (một dấu ' trong chuỗi không được thoát khỏi cặp nháy).
shq() {
  local s=$1 q="'"
  s=${s//$q/$q\\$q$q}
  printf '%s' "$q$s$q"
}

# KHÔNG chạy trên đầu xe thật (bộ ca có lệnh đổi state + gỡ/cài lại app).
require_emulator() {
  case "$SERIAL" in
    emulator-*) ;;
    *) [ "${ALLOW_NON_EMULATOR:-}" = "YES" ] || die "«${SERIAL}» không phải máy ảo — từ chối chạy (đặt ALLOW_NON_EMULATOR=YES nếu thật sự có chủ ý)";;
  esac
}

bridge() {
  local raw
  raw="$(adbs shell "am broadcast -a $PKG.TEST -p $PKG $*" 2>&1)"
  printf '%s' "$raw" | python3 "$HERE/voice_e2e_json.py" extract
}

state_json() { bridge "--es cmd state"; }
json_get() { python3 "$HERE/voice_e2e_json.py" get "$1"; }

HOME_ACT="${HOME_ACT:-$PKG/com.byd.clusternav.launcher.KachiHomeActivity}"
start_home() { adbs shell am start -n "$HOME_ACT" >/dev/null; sleep 3; }

# `run-as` (bản debuggable) hoặc root (máy ảo userdebug) — xem KDoc cùng đoạn ở voice-e2e.sh.
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
    die "bản đang cài KHÔNG debuggable và máy không cho adb root — cài bản vehicleTest"
  fi
}

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

# Cửa cầu kiểm thử ~58 phút (TestBridgeStore). Không có đường bật bằng broadcast — có chủ ý.
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
