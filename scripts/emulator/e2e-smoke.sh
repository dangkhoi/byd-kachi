#!/usr/bin/env bash
# ClusterNav emulator end-to-end smoke.
#
# Purpose: de-risk the vehicle session off-car by exercising the SAME candidate
# resolution, install and launch path the operator will run, on an Android 34
# emulator. The emulator has no BYD AutoContainer service, so a cast attempt MUST
# refuse cleanly instead of crashing — that refusal is a required observation here.
#
# This script refuses to run against anything that is not an emulator serial.
set -euo pipefail
source "$(dirname "$0")/../vehicle/common.sh"

PACKAGE="com.byd.clusternav"
OVERLAY_SPEC="${OVERLAY_SPEC:-1920x720/180}"
PASS=0
FAIL=0
SKIP=0

ok()   { echo "PASS  $*"; PASS=$((PASS + 1)); }
bad()  { echo "FAIL  $*" >&2; FAIL=$((FAIL + 1)); }
skip() { echo "SKIP  $*"; SKIP=$((SKIP + 1)); }

require_emulator() {
  select_device
  if [[ "$DEVICE_SERIAL" != emulator-* && "${ALLOW_NON_EMULATOR:-}" != "YES" ]]; then
    echo "ERROR: $DEVICE_SERIAL is not an emulator; refusing to touch a real head unit." >&2
    echo "       Set ALLOW_NON_EMULATOR=YES only if you truly intend otherwise." >&2
    exit 4
  fi
  local sdk
  sdk="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "EMULATOR_SDK=$sdk"
}

dump_ui() {
  local dest="$1"
  "${ADB[@]}" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  "${ADB[@]}" shell cat /sdcard/ui.xml > "$dest" 2>/dev/null || return 1
  [[ -s "$dest" ]] || return 1
}

# Taps the nearest clickable ancestor of a node whose text, content-desc or resource-id
# contains $1. Matching a bare label is not enough: the label is usually a TextView
# inside a clickable card. Controls can also appear a moment after the screen renders
# (async state load), so the dump is retried for a bounded time.
tap_text() {
  local needle="$1" attempts="${2:-6}" dump coords attempt=1
  while (( attempt <= attempts )); do
    dump="$EVIDENCE_DIR/ui-$(date -u +%H%M%S)-$RANDOM.xml"
    if dump_ui "$dump"; then
      coords="$(python3 - "$dump" "$needle" <<'PY'
import sys
import xml.etree.ElementTree as ET

try:
    tree = ET.parse(sys.argv[1])
except (OSError, ET.ParseError):
    sys.exit(1)
needle = sys.argv[2].lower()
root = tree.getroot()
parents = {child: parent for parent in root.iter() for child in parent}


def bounds(node):
    raw = node.get("bounds", "")
    try:
        left, top, right, bottom = (
            int(v) for v in raw.replace("][", ",").strip("[]").split(",")
        )
    except ValueError:
        return None
    return left, top, right, bottom


def usable(node):
    return node.get("clickable") == "true" and node.get("enabled", "true") == "true"


matches = [
    node
    for node in root.iter("node")
    if needle
    in f"{node.get('text', '')} {node.get('content-desc', '')} {node.get('resource-id', '')}".lower()
]
if not matches:
    sys.exit(2)

# A label can match a non-clickable title before the real control, so every match is
# considered: directly clickable nodes first, then the nearest clickable ancestor.
for candidate in [node for node in matches if usable(node)] + matches:
    node = candidate
    while node is not None:
        if usable(node):
            box = bounds(node)
            if box:
                print((box[0] + box[2]) // 2, (box[1] + box[3]) // 2)
                sys.exit(0)
        node = parents.get(node)
sys.exit(3)
PY
)" || coords=""
      if [[ -n "$coords" ]]; then
        # shellcheck disable=SC2086
        "${ADB[@]}" shell input tap $coords
        sleep 3
        return 0
      fi
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  return 1
}

# Only OUR crashes count. The unfiltered check attributed any process crash to ClusterNav; on the
# vehicle that meant a BYD SystemUI NPE looked like the cast target dying.
crashed() {
  "${ADB[@]}" logcat -d -b crash 2>/dev/null \
    | awk -v pkg="$PACKAGE" '/FATAL EXCEPTION/{f=1} f&&index($0,pkg){print;exit}' \
    | grep -q .
}

resumed_activity() {
  "${ADB[@]}" shell dumpsys activity activities 2>/dev/null \
    | grep -oE "(ResumedActivity|topResumedActivity)[^\"]*" \
    | grep -oE "com\.byd\.clusternav/[A-Za-z0-9_.\$]+" \
    | head -1
}

# Reads the text of a node by resource-id suffix from a fresh dump.
node_text() {
  local suffix="$1" dump
  dump="$EVIDENCE_DIR/ui-read-$(date -u +%H%M%S)-$RANDOM.xml"
  dump_ui "$dump" || return 1
  python3 - "$dump" "$suffix" <<'PY'
import sys
import xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    sys.exit(1)
for node in root.iter("node"):
    if node.get("resource-id", "").split("/")[-1] == sys.argv[2]:
        print(node.get("text", ""))
        sys.exit(0)
sys.exit(2)
PY
}

# Reads the enabled attribute of a node by resource-id suffix.
node_enabled() {
  local suffix="$1" dump
  dump="$EVIDENCE_DIR/ui-read-$(date -u +%H%M%S)-$RANDOM.xml"
  dump_ui "$dump" || return 1
  python3 - "$dump" "$suffix" <<'PY'
import sys
import xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    sys.exit(1)
for node in root.iter("node"):
    if node.get("resource-id", "").split("/")[-1] == sys.argv[2]:
        print(node.get("enabled", "unknown"))
        sys.exit(0)
sys.exit(2)
PY
}

# Absence of a crash is not evidence that a screen opened: a non-exported activity
# started from shell is denied and the previous screen simply stays on top.
assert_resumed() {
  local expected="$1" label="$2" actual
  actual="$(resumed_activity || true)"
  if [[ "$actual" == *"$expected"* ]]; then
    ok "$label resumed ($actual)"
    return 0
  fi
  bad "$label did not become the resumed activity (resumed=${actual:-none})"
  return 1
}

require_candidate
require_emulator
# Distinct from a real vehicle run, still covered by the gitignored oncar-*/ pattern.
EVIDENCE_DIR="${EVIDENCE_DIR:-$(vehicle_root)/oncar-emulator-$(date -u +%Y%m%dT%H%M%SZ)}"
export EVIDENCE_DIR
new_evidence_dir

echo "=== O1 candidate resolution ==="
[[ -n "${CANDIDATE_EXACT_SOURCE_ID:-}" ]] \
  && ok "candidate resolved from build-written manifest (${CANDIDATE_VERSION_NAME:-?} / ${CANDIDATE_EXACT_SOURCE_ID:0:12}…)" \
  || bad "candidate manifest lacks exactSourceId"

{
  echo "timestamp=$(date -u +%FT%TZ)"
  echo "serial=$DEVICE_SERIAL"
  echo "apk=$APK"
  echo "apk_sha256=$EXPECTED_SHA256"
  echo "exact_source_id=${CANDIDATE_EXACT_SOURCE_ID:-unknown}"
  echo "overlay_spec=$OVERLAY_SPEC"
} > "$EVIDENCE_DIR/metadata.txt"

echo "=== O2 install and cold launch ==="
"${ADB[@]}" logcat -c -b all >/dev/null 2>&1 || true
inspect_installed_package > "$EVIDENCE_DIR/pre-install-state.txt" 2>&1 || true
# Xoá dữ liệu app trước khi cài. 2026-07-27: thiếu bước này nên O5 chạy trên journal sót lại từ
# lần trước; một lần bị kẹt ở "cần xử lý thủ công" là mọi lần sau đều fail, và kết quả của bộ kiểm
# phụ thuộc thứ tự lịch sử chứ không phụ thuộc bản dựng đang kiểm.
"${ADB[@]}" shell pm clear "$PACKAGE" > "$EVIDENCE_DIR/state-reset.txt" 2>&1 || true
if "${ADB[@]}" install -r "$APK" > "$EVIDENCE_DIR/install.txt" 2>&1 \
  && grep -q "^Success" "$EVIDENCE_DIR/install.txt"; then
  ok "install -r accepted the exact candidate"
else
  bad "install failed (see install.txt)"
fi
"${ADB[@]}" shell appops set "$PACKAGE" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 || true
"${ADB[@]}" shell dumpsys package "$PACKAGE" \
  | grep -E 'versionCode|versionName' > "$EVIDENCE_DIR/installed-version.txt" 2>&1 || true

# Baseline that Stop must restore, read before anything is cast.
{
  for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    echo "$key=$("${ADB[@]}" shell settings get global "$key" | tr -d '\r')"
  done
  echo "pip_appop=$("${ADB[@]}" shell appops get "$PACKAGE" PICTURE_IN_PICTURE 2>/dev/null | tr -d '\r')"
} > "$EVIDENCE_DIR/baseline-before-cast.txt"

"${ADB[@]}" shell am start -W -n "$PACKAGE/.MainActivity" > "$EVIDENCE_DIR/launch-main.txt" 2>&1 || true
sleep 4
if crashed; then
  bad "MainActivity crashed on cold launch"
else
  ok "MainActivity launched without FATAL EXCEPTION"
fi
assert_resumed "MainActivity" "MainActivity" || true

echo "=== O3 in-app navigation reaches the Cast screen ==="
# Stock Android denies shell starts of non-exported activities (the head unit allows it),
# so the Cast screen must be reached the way a user does: through MainActivity.
if tap_text "cluster cast"; then
  if assert_resumed "ClusterCastActivity" "ClusterCastActivity"; then
    CAST_SCREEN=1
  else
    CAST_SCREEN=0
  fi
else
  CAST_SCREEN=0
  bad "no clickable Cluster Cast entry point found on MainActivity"
fi
dump_ui "$EVIDENCE_DIR/ui-cast-screen.xml" || true
if crashed; then
  bad "reaching the Cast screen produced a FATAL EXCEPTION"
else
  ok "no crash while navigating to the Cast screen"
fi

echo "=== O4 secondary display inventory (F9-adjacent parse/fallback path) ==="
"${ADB[@]}" shell settings put global overlay_display_devices "$OVERLAY_SPEC" >/dev/null
sleep 5
"${ADB[@]}" shell dumpsys display > "$EVIDENCE_DIR/display-with-overlay.txt" 2>&1 || true
if grep -qiE "overlay #1|overlay_display" "$EVIDENCE_DIR/display-with-overlay.txt"; then
  ok "overlay secondary display present in dumpsys display"
else
  bad "overlay secondary display did not appear"
fi
"${ADB[@]}" shell dumpsys window displays > "$EVIDENCE_DIR/window-displays-with-overlay.txt" 2>&1 || true
DISPLAY_COUNT="$(grep -cE "Display: mDisplayId=" "$EVIDENCE_DIR/window-displays-with-overlay.txt" || true)"
if [[ "${DISPLAY_COUNT:-0}" -ge 2 ]]; then
  ok "window manager reports $DISPLAY_COUNT displays"
else
  bad "window manager still reports ${DISPLAY_COUNT:-0} display(s)"
fi

echo "=== O5 cast attempt must refuse truthfully without BYD AutoContainer ==="
if [[ "$CAST_SCREEN" -eq 1 ]]; then
  # The cast control is deliberately disabled until a target app is chosen, so the
  # harness must select one first — tapping the disabled button proves nothing.
  SELECTED=0
  for app in chrome calendar clock camera; do
    if tap_text "$app" 3; then
      selection="$(node_text cast_selected || true)"
      if [[ -n "$selection" && "$selection" != *"Chưa chọn"* ]]; then
        ok "target app selected ($selection)"
        SELECTED=1
        break
      fi
    fi
  done
  [[ "$SELECTED" -eq 1 ]] || bad "could not select a target app on the Cast screen"

  "${ADB[@]}" logcat -c -b all >/dev/null 2>&1 || true
  if [[ "$SELECTED" -eq 1 ]] && tap_text "chiếu / chuyển"; then
    sleep 8
    dump_ui "$EVIDENCE_DIR/ui-after-cast.xml" || true
    "${ADB[@]}" logcat -d -b all > "$EVIDENCE_DIR/logcat-cast-attempt.txt" 2>&1 || true
    if crashed; then
      bad "cast attempt crashed instead of refusing"
    else
      ok "cast attempt did not crash on hardware without AutoContainer"
    fi
    CAST_STATUS="$(node_text cast_status || true)"
    echo "      cast_status=$CAST_STATUS"
    # A truthful refusal must be surfaced, not swallowed.
    if grep -qiE "unavailable|không|thất bại|lỗi|refus|cluster display|hụt" \
        "$EVIDENCE_DIR/ui-after-cast.xml" "$EVIDENCE_DIR/logcat-cast-attempt.txt" 2>/dev/null; then
      ok "refusal surfaced with a reason rather than failing silently"
    else
      bad "cast attempt produced no visible refusal reason"
    fi
    # F3 shape: an app that will not reparent must produce the two-option prompt, and
    # declining must leave the previous state intact.
    PROMPT="$(python3 - "$EVIDENCE_DIR/ui-after-cast.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

try:
    root = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    sys.exit(0)
labels = [
    (node.get("text") or "").strip().lower()
    for node in root.iter("node")
    if node.get("clickable") == "true"
]
force = any("chiếu lại" in label for label in labels)
decline = any("để nguyên" in label for label in labels)
print("both" if force and decline else "partial" if force or decline else "none")
PY
)"
    case "$PROMPT" in
      both) ok "F3 prompt offers both force-restart and leave-intact options" ;;
      partial) bad "F3 prompt is missing one of its two options" ;;
      *) skip "no reparent-refusal prompt in this run" ;;
    esac
    if [[ "$PROMPT" == "both" ]] && tap_text "để nguyên" 3; then
      sleep 3
      if crashed; then
        bad "declining the F3 prompt crashed the app"
      else
        ok "declining the F3 prompt left the app running"
      fi
    fi
    assert_resumed "clusternav" "ClusterNav" || true
    CAST_ATTEMPTED=1
  else
    CAST_ATTEMPTED=0
    bad "cast control never became enabled/tappable"
  fi
else
  CAST_ATTEMPTED=0
  skip "Cast screen unreachable; cast attempt not exercised"
fi

echo "=== O5b nút nổi phải khởi tạo được (không crash) ==="
# 2026-07-27: nút nổi crash ngay khi bật vì `catalog` bị đọc trước khi gán, và bộ kiểm này KHÔNG bắt được vì
# nó chưa bao giờ bật nút nổi. Một service không được start thì "no FATAL EXCEPTION" chỉ chứng minh service
# đó không chạy.
#
# Phải bật qua ĐÚNG công tắc trên UI: service không export nên `am start-foreground-service` bị hệ thống
# chặn, và nếu dùng cách đó thì bước kiểm này xanh mà rỗng.
if [[ "$CAST_SCREEN" -eq 1 ]]; then
  "${ADB[@]}" shell appops set "$PACKAGE" SYSTEM_ALERT_WINDOW allow > /dev/null 2>&1 || true
  "${ADB[@]}" logcat -c > /dev/null 2>&1 || true
  if tap_text "Nút nổi" 4; then
    sleep 3
    BUBBLE_RUNNING="$("${ADB[@]}" shell dumpsys activity services "$PACKAGE" 2>/dev/null \
      | grep -c "FloatingBubbleService" || true)"
    BUBBLE_CRASH="$("${ADB[@]}" logcat -d 2>/dev/null | grep -F "$PACKAGE" \
      | grep -cE "UninitializedPropertyAccess|FATAL EXCEPTION" || true)"
    "${ADB[@]}" shell dumpsys activity services "$PACKAGE" > "$EVIDENCE_DIR/bubble-service.txt" 2>&1 || true
    if [[ "${BUBBLE_RUNNING:-0}" -eq 0 ]]; then
      bad "bật công tắc rồi mà service nút nổi không xuất hiện (xem bubble-service.txt)"
    elif [[ "${BUBBLE_CRASH:-0}" -ne 0 ]]; then
      bad "service nút nổi crash khi khởi tạo (xem logcat trong evidence)"
    else
      ok "nút nổi bật được và khởi tạo không crash"
    fi
  else
    skip "không tìm thấy công tắc nút nổi trên màn Cast"
  fi
else
  skip "Cast screen unreachable; nút nổi không kiểm được"
fi

echo "=== O6 Stop is fail-closed and the animation/PIP baseline is intact ==="
STOP_ENABLED="$(node_enabled cast_stop || echo unknown)"
if [[ "${CAST_ATTEMPTED:-0}" -eq 1 && "$STOP_ENABLED" == "true" ]]; then
  if tap_text "dừng — trả" 3; then
    sleep 5
    ok "stop was offered for a landed session and was tapped"
  else
    bad "stop was enabled but could not be tapped"
  fi
elif [[ "$STOP_ENABLED" == "false" ]]; then
  # Nothing landed on this hardware, so Stop must stay unavailable. A tappable Stop
  # with no session is the class of defect that relaunches a vanished app.
  ok "stop stays disabled when no session landed (fail-closed)"
else
  skip "stop state could not be read ($STOP_ENABLED)"
fi
{
  for key in window_animation_scale transition_animation_scale animator_duration_scale; do
    echo "$key=$("${ADB[@]}" shell settings get global "$key" | tr -d '\r')"
  done
  echo "pip_appop=$("${ADB[@]}" shell appops get "$PACKAGE" PICTURE_IN_PICTURE 2>/dev/null | tr -d '\r')"
} > "$EVIDENCE_DIR/baseline-after-stop.txt"
if diff -q "$EVIDENCE_DIR/baseline-before-cast.txt" "$EVIDENCE_DIR/baseline-after-stop.txt" >/dev/null; then
  ok "animation scales and PIP app-op match the pre-cast baseline"
else
  bad "baseline drifted across the cast/stop cycle"
  diff "$EVIDENCE_DIR/baseline-before-cast.txt" "$EVIDENCE_DIR/baseline-after-stop.txt" \
    > "$EVIDENCE_DIR/baseline-diff.txt" || true
fi

echo "=== O7 no fatal exception across the run ==="
"${ADB[@]}" logcat -d -b all > "$EVIDENCE_DIR/logcat-full.txt" 2>&1 || true
grep -E "ClusterCast|ClusterNav|Cast V2" "$EVIDENCE_DIR/logcat-full.txt" \
  > "$EVIDENCE_DIR/logcat-clusternav.txt" 2>/dev/null || true
if grep -q "FATAL EXCEPTION" "$EVIDENCE_DIR/logcat-full.txt"; then
  bad "FATAL EXCEPTION present in logcat"
  grep -A 20 "FATAL EXCEPTION" "$EVIDENCE_DIR/logcat-full.txt" > "$EVIDENCE_DIR/fatal.txt" || true
else
  ok "no FATAL EXCEPTION in the full log buffer"
fi

echo "=== teardown ==="
# `settings put ... ""` fails with "Bad arguments" and would silently leave the
# emulator with a stray secondary display.
"${ADB[@]}" shell settings delete global overlay_display_devices >/dev/null 2>&1 || true
if [[ -z "$("${ADB[@]}" shell settings get global overlay_display_devices | tr -d '\r' | sed 's/null//')" ]]; then
  ok "overlay display removed during teardown"
else
  bad "overlay display still present after teardown"
fi
"${ADB[@]}" shell rm -f /sdcard/ui.xml >/dev/null 2>&1 || true
find "$EVIDENCE_DIR" -maxdepth 1 -type f ! -name SHA256SUMS.txt -print0 \
  | xargs -0 shasum -a 256 > "$EVIDENCE_DIR/SHA256SUMS.txt"

echo
echo "EMULATOR_E2E pass=$PASS fail=$FAIL skip=$SKIP"
echo "EVIDENCE=$EVIDENCE_DIR"
[[ "$FAIL" -eq 0 ]] || exit 1
