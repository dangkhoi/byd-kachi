#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

PACKAGE="com.byd.clusternav"
MAIN_ACTIVITY="$PACKAGE/.MainActivity"
INSTALL=1
WAIT_SECONDS=8
SELF_TEST=0
PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

usage() {
  cat <<'EOF'
ClusterNav one-command vehicle smoke test

Usage:
  scripts/vehicle/auto-smoke-test.sh [options]

Default: verify the current APK, install with adb -r, launch MainActivity,
check package/version/process/crash/ANR, and capture screenshot/UI/AM/WM/display/log evidence.

Options:
  --serial SERIAL   Select an explicit adb device (recommended on the car)
  --no-install      Test the already-installed package without installing
  --wait SECONDS    Wait after launch before health checks (default: 8)
  --output DIR      Evidence directory (default: ignored oncar-v2-<UTC>)
  --self-test       Validate script and APK locally; do not call adb
  -h, --help        Show this help

Examples:
  scripts/vehicle/auto-smoke-test.sh --serial 192.0.2.10:5555
  ADB_SERIAL=<serial> scripts/vehicle/auto-smoke-test.sh
  scripts/vehicle/auto-smoke-test.sh --no-install
EOF
}

ok() { PASS_COUNT=$((PASS_COUNT + 1)); printf 'PASS  %s\n' "$*"; }
bad() { FAIL_COUNT=$((FAIL_COUNT + 1)); printf 'FAIL  %s\n' "$*" >&2; }
warn() { WARN_COUNT=$((WARN_COUNT + 1)); printf 'WARN  %s\n' "$*" >&2; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "ERROR: --serial needs a value" >&2; exit 2; }
      ADB_SERIAL="$2"; export ADB_SERIAL; shift 2 ;;
    --no-install) INSTALL=0; shift ;;
    --wait)
      [[ $# -ge 2 && "$2" =~ ^[0-9]+$ ]] || { echo "ERROR: --wait needs integer seconds" >&2; exit 2; }
      WAIT_SECONDS="$2"; shift 2 ;;
    --output)
      [[ $# -ge 2 && -n "$2" ]] || { echo "ERROR: --output needs a directory" >&2; exit 2; }
      EVIDENCE_DIR="$2"; export EVIDENCE_DIR; shift 2 ;;
    --self-test) SELF_TEST=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

require_command shasum
require_candidate

if [[ "$SELF_TEST" -eq 1 ]]; then
  # Assert the candidate came from the build-written manifest, not a baked path.
  [[ "$APK" == "$(vehicle_root)/apk/"*"-release.apk" ]] || {
    echo "ERROR: candidate is not a collected artifact under apk/: $APK" >&2; exit 3
  }
  [[ "$EXPECTED_SHA256" =~ ^[0-9a-f]{64}$ ]] || { echo "ERROR: candidate sha missing" >&2; exit 3; }
  [[ -n "${CANDIDATE_EXACT_SOURCE_ID:-}" ]] || { echo "ERROR: candidate exactSourceId missing" >&2; exit 3; }
  echo "SELF_TEST=PASS"
  echo "No adb/device command executed."
  exit 0
fi

require_command adb
select_device
new_evidence_dir

MARKER="CLUSTERNAV_SMOKE_$(date -u +%Y%m%dT%H%M%SZ)_$$"
STARTED_AT="$(date -u +%FT%TZ)"

{
  echo "started_at=$STARTED_AT"
  echo "marker=$MARKER"
  echo "apk=$APK"
  echo "apk_sha256=$EXPECTED_SHA256"
  echo "package=$PACKAGE"
  echo "install=$INSTALL"
  echo "wait_seconds=$WAIT_SECONDS"
  echo "device_serial=$DEVICE_SERIAL"
  echo "model=$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
  echo "fingerprint=$("${ADB[@]}" shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "android=$("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r')"
  echo "sdk=$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
} > "$EVIDENCE_DIR/metadata.txt"
ok "adb device reachable: $DEVICE_SERIAL"

"${ADB[@]}" shell log -t ClusterNavSmoke "$MARKER" >/dev/null 2>&1 || true

if [[ "$INSTALL" -eq 1 ]]; then
  if "${ADB[@]}" install -r "$APK" > "$EVIDENCE_DIR/install.txt" 2>&1; then
    if grep -q 'Success' "$EVIDENCE_DIR/install.txt"; then
      ok "APK installed"
    else
      bad "adb install returned without Success"
    fi
  else
    bad "APK install failed; see $EVIDENCE_DIR/install.txt"
  fi
else
  echo "install skipped by --no-install" > "$EVIDENCE_DIR/install.txt"
  ok "install skipped"
fi

PACKAGE_DUMP="$EVIDENCE_DIR/package.txt"
"${ADB[@]}" shell dumpsys package "$PACKAGE" > "$PACKAGE_DUMP" 2>&1 || true
if grep -q "versionCode=${CANDIDATE_VERSION_CODE:-?}" "$PACKAGE_DUMP" \
  && grep -q "versionName=${CANDIDATE_VERSION_NAME:-?}" "$PACKAGE_DUMP"; then
  ok "installed package version is ${CANDIDATE_VERSION_NAME:-?} (${CANDIDATE_VERSION_CODE:-?})"
else
  bad "package missing or not the recorded candidate version ${CANDIDATE_VERSION_NAME:-?} (${CANDIDATE_VERSION_CODE:-?})"
fi

if "${ADB[@]}" shell pm path "$PACKAGE" > "$EVIDENCE_DIR/package-path.txt" 2>&1 \
  && grep -q '^package:' "$EVIDENCE_DIR/package-path.txt"; then
  ok "package path resolved"
  REMOTE_APK="$(sed -n 's/^package://p' "$EVIDENCE_DIR/package-path.txt" | head -n 1 | tr -d '\r')"
  if INSTALLED_SHA256="$("${ADB[@]}" exec-out cat "$REMOTE_APK" 2>/dev/null | shasum -a 256 | awk '{print $1}')"; then
    echo "$INSTALLED_SHA256  $REMOTE_APK" > "$EVIDENCE_DIR/installed-apk-sha256.txt"
    if [[ "$INSTALLED_SHA256" == "$EXPECTED_SHA256" ]]; then
      ok "installed base.apk matches the approved SHA"
    else
      bad "installed base.apk SHA mismatch"
    fi
  else
    warn "ROM did not allow hashing installed base.apk"
  fi
else
  bad "package path not found"
fi

if "${ADB[@]}" shell am start -W -n "$MAIN_ACTIVITY" > "$EVIDENCE_DIR/launch.txt" 2>&1; then
  if grep -q 'Status: ok' "$EVIDENCE_DIR/launch.txt"; then
    ok "MainActivity launched"
  else
    warn "launch command completed without Status: ok"
  fi
else
  bad "MainActivity launch failed"
fi

sleep "$WAIT_SECONDS"

PID="$("${ADB[@]}" shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | xargs || true)"
if [[ -n "$PID" ]]; then
  ok "app process alive (pid $PID)"
else
  bad "app process not running"
fi

"${ADB[@]}" shell dumpsys activity activities > "$EVIDENCE_DIR/activity.txt" 2>&1 || true
"${ADB[@]}" shell am stack list > "$EVIDENCE_DIR/am-stack-list.txt" 2>&1 || true
"${ADB[@]}" shell dumpsys window displays > "$EVIDENCE_DIR/window-displays.txt" 2>&1 || true
"${ADB[@]}" shell dumpsys display > "$EVIDENCE_DIR/display.txt" 2>&1 || true
"${ADB[@]}" shell dumpsys activity services "$PACKAGE" > "$EVIDENCE_DIR/services.txt" 2>&1 || true
"${ADB[@]}" shell dumpsys appops "$PACKAGE" > "$EVIDENCE_DIR/appops.txt" 2>&1 || true

if "${ADB[@]}" exec-out screencap -p > "$EVIDENCE_DIR/screenshot.png" 2>/dev/null \
  && [[ -s "$EVIDENCE_DIR/screenshot.png" ]]; then
  ok "screenshot captured"
else
  bad "screenshot capture failed"
fi

REMOTE_UI="/sdcard/clusternav-smoke-ui-$$.xml"
if "${ADB[@]}" shell uiautomator dump "$REMOTE_UI" > "$EVIDENCE_DIR/uiautomator.txt" 2>&1 \
  && "${ADB[@]}" pull "$REMOTE_UI" "$EVIDENCE_DIR/ui.xml" >> "$EVIDENCE_DIR/uiautomator.txt" 2>&1; then
  "${ADB[@]}" shell rm -f "$REMOTE_UI" >/dev/null 2>&1 || true
  ok "UI hierarchy captured"
  if ! grep -q "package=\"$PACKAGE\"" "$EVIDENCE_DIR/ui.xml"; then
    warn "ClusterNav is not present in the captured foreground UI"
  fi
else
  warn "UI hierarchy unavailable on this ROM"
fi

"${ADB[@]}" logcat -d -v threadtime > "$EVIDENCE_DIR/logcat-all.txt" 2>&1 || true
if grep -q "$MARKER" "$EVIDENCE_DIR/logcat-all.txt"; then
  awk -v marker="$MARKER" 'index($0, marker) { seen=1 } seen { print }' \
    "$EVIDENCE_DIR/logcat-all.txt" > "$EVIDENCE_DIR/logcat-smoke.txt"
else
  tail -n 2000 "$EVIDENCE_DIR/logcat-all.txt" > "$EVIDENCE_DIR/logcat-smoke.txt"
  warn "ROM dropped the smoke log marker; scanned the latest 2000 lines"
fi
if grep -E "Process: $PACKAGE|ANR in $PACKAGE|am_crash.*$PACKAGE|am_anr.*$PACKAGE" \
  "$EVIDENCE_DIR/logcat-smoke.txt" > "$EVIDENCE_DIR/crash-anr.txt"; then
  bad "ClusterNav crash/ANR detected"
else
  : > "$EVIDENCE_DIR/crash-anr.txt"
  ok "no ClusterNav crash/ANR after launch"
fi

PID_AFTER="$("${ADB[@]}" shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | xargs || true)"
if [[ -n "$PID_AFTER" ]]; then
  ok "app process still alive after evidence capture"
else
  bad "app process died during smoke test"
fi

{
  echo "finished_at=$(date -u +%FT%TZ)"
  echo "pass=$PASS_COUNT"
  echo "fail=$FAIL_COUNT"
  echo "warn=$WARN_COUNT"
  echo "apk_sha256=$EXPECTED_SHA256"
  echo "evidence_dir=$EVIDENCE_DIR"
} > "$EVIDENCE_DIR/summary.txt"

find "$EVIDENCE_DIR" -maxdepth 1 -type f ! -name SHA256SUMS.txt -print0 \
  | xargs -0 shasum -a 256 > "$EVIDENCE_DIR/SHA256SUMS.txt"
chmod 600 "$EVIDENCE_DIR"/*

echo
echo "RESULT: PASS=$PASS_COUNT FAIL=$FAIL_COUNT WARN=$WARN_COUNT"
echo "Evidence: $EVIDENCE_DIR"
echo "Full Cast/CarPlay/Android Auto behavior still needs the guided vehicle matrix."
[[ "$FAIL_COUNT" -eq 0 ]]
