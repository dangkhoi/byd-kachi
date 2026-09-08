#!/usr/bin/env bash
set -uo pipefail

usage() {
  cat <<'EOF'
Listen for navigation-signal candidates from one app at a time.

Usage:
  scripts/vehicle/listen-nav-signals.sh APP [options]

APP:
  carplay | cp       com.byd.carplay.ui
  android-auto | aa  com.byd.androidauto
  vietmap            vn.vietmap.live
  waze               com.waze

Options:
  --serial SERIAL    Explicit adb serial or already-connected IP:port
  --seconds N        Listening duration; 0 = until Ctrl-C (default: 90)
  --interval N       Snapshot interval in seconds (default: 3)
  --package NAME     Override package when the ROM uses another package name
  --output DIR       Local evidence directory
  --self-test        Validate arguments/package mapping; never call adb
  -h, --help         Show help

Start this listener first, then open the selected app and leave it visible for a while.
Fixed vehicle setup: CP/AA phone uses USB; Wi-Fi is reserved for head-unit ADB.
Connect once with `adb connect <HEAD_UNIT_IP>:5555`, then pass that IP:port with --serial.
All evidence stays in ignored oncar-signals-* directories and may contain private route/location data.
EOF
}

[[ $# -ge 1 ]] || { usage >&2; exit 2; }
case "$1" in -h|--help) usage; exit 0 ;; esac
APP_KEY="$1"; shift
DURATION=90
INTERVAL=3
PACKAGE_OVERRIDE=""
OUTPUT_OVERRIDE=""
SELF_TEST=0
ADB_BIN="${ADB:-adb}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "ERROR: --serial needs a value" >&2; exit 2; }
      ADB_SERIAL="$2"; shift 2 ;;
    --seconds)
      [[ $# -ge 2 && "$2" =~ ^[0-9]+$ ]] || { echo "ERROR: --seconds needs a non-negative integer" >&2; exit 2; }
      DURATION="$2"; shift 2 ;;
    --interval)
      [[ $# -ge 2 && "$2" =~ ^[1-9][0-9]*$ ]] || { echo "ERROR: --interval needs a positive integer" >&2; exit 2; }
      INTERVAL="$2"; shift 2 ;;
    --package)
      [[ $# -ge 2 && "$2" =~ ^[A-Za-z0-9_]+([.][A-Za-z0-9_]+)+$ ]] || { echo "ERROR: invalid package name" >&2; exit 2; }
      PACKAGE_OVERRIDE="$2"; shift 2 ;;
    --output)
      [[ $# -ge 2 && -n "$2" ]] || { echo "ERROR: --output needs a directory" >&2; exit 2; }
      OUTPUT_OVERRIDE="$2"; shift 2 ;;
    --self-test) SELF_TEST=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

APP_KEY_NORMALIZED="$(printf '%s' "$APP_KEY" | tr '[:upper:]' '[:lower:]')"
case "$APP_KEY_NORMALIZED" in
  carplay|cp)
    APP_KEY="carplay"; DEFAULT_PACKAGE="com.byd.carplay.ui" ;;
  android-auto|androidauto|aa)
    APP_KEY="android-auto"; DEFAULT_PACKAGE="com.byd.androidauto" ;;
  vietmap)
    APP_KEY="vietmap"; DEFAULT_PACKAGE="vn.vietmap.live" ;;
  waze)
    APP_KEY="waze"; DEFAULT_PACKAGE="com.waze" ;;
  *) echo "ERROR: unknown app: $APP_KEY" >&2; usage >&2; exit 2 ;;
esac
PACKAGE="${PACKAGE_OVERRIDE:-$DEFAULT_PACKAGE}"
SIGNAL_REGEX='navigation|navigate|maneuver|guidance|route|turn|next[_ .-]?turn|distance|remaining|eta|arrival|destination|street|road|lane|junction|roundabout|navinfo|nav[_ .-]?info|autonavi|tbt|rẽ|đường|khoảng cách|làn|vòng xuyến'

if [[ "$SELF_TEST" -eq 1 ]]; then
  echo "SELF_TEST=PASS app=$APP_KEY package=$PACKAGE duration=$DURATION interval=$INTERVAL"
  echo "No adb/device command executed."
  exit 0
fi

command -v "$ADB_BIN" >/dev/null 2>&1 || { echo "ERROR: adb not found: $ADB_BIN" >&2; exit 2; }
command -v shasum >/dev/null 2>&1 || { echo "ERROR: shasum not found" >&2; exit 2; }

if [[ -n "${ADB_SERIAL:-}" ]]; then
  DEVICE_SERIAL="$ADB_SERIAL"
else
  DEVICES="$($ADB_BIN devices | awk 'NR>1 && $2=="device" {print $1}')"
  DEVICE_COUNT="$(printf '%s\n' "$DEVICES" | sed '/^$/d' | wc -l | tr -d ' ')"
  [[ "$DEVICE_COUNT" -eq 1 ]] || {
    echo "ERROR: expected one adb device; pass --serial when multiple/none are visible" >&2
    exit 4
  }
  DEVICE_SERIAL="$DEVICES"
fi
ADB_CMD=("$ADB_BIN" -s "$DEVICE_SERIAL")
"${ADB_CMD[@]}" get-state >/dev/null || { echo "ERROR: adb device is not ready: $DEVICE_SERIAL" >&2; exit 4; }

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="${OUTPUT_OVERRIDE:-$ROOT/oncar-signals-$APP_KEY-$STAMP}"
mkdir -p "$OUT"
chmod 700 "$OUT"
: > "$OUT/events.txt"
: > "$OUT/candidates.txt"
: > "$OUT/logcat-all.txt"
LOGCAT_PID=""
FINISHED=0
ITERATION=0
LAST_PID=""

say() {
  printf '%s\n' "$*"
  printf '[%s] %s\n' "$(date -u +%FT%TZ)" "$*" >> "$OUT/events.txt"
}

record_changed() {
  local channel="$1" content="$2"
  local hash_file="$OUT/.${channel}.sha" history="$OUT/${channel}-history.txt"
  [[ -n "$content" ]] || return 0
  local hash previous=""
  hash="$(printf '%s' "$content" | shasum -a 256 | awk '{print $1}')"
  [[ -f "$hash_file" ]] && previous="$(cat "$hash_file")"
  [[ "$hash" != "$previous" ]] || return 0
  printf '%s' "$hash" > "$hash_file"
  {
    printf '\n===== %s · %s =====\n' "$(date -u +%FT%TZ)" "$channel"
    printf '%s\n' "$content"
  } >> "$history"
  say "[$channel] changed"
  local candidate
  candidate="$(printf '%s\n' "$content" | grep -Ei "$SIGNAL_REGEX" | head -n 12 || true)"
  if [[ -n "$candidate" ]]; then
    printf '\n[%s][%s]\n%s\n' "$(date -u +%FT%TZ)" "$channel" "$candidate" >> "$OUT/candidates.txt"
    printf '  NAV CANDIDATE: %s\n' "$(printf '%s' "$candidate" | head -n 1 | cut -c1-180)"
  fi
}

finish() {
  [[ "$FINISHED" -eq 0 ]] || return 0
  FINISHED=1
  if [[ -n "$LOGCAT_PID" ]]; then
    kill "$LOGCAT_PID" >/dev/null 2>&1 || true
    wait "$LOGCAT_PID" >/dev/null 2>&1 || true
  fi

  grep -i "$PACKAGE" "$OUT/logcat-all.txt" > "$OUT/logcat-package.txt" 2>/dev/null || true
  grep -Ei "$SIGNAL_REGEX" "$OUT/logcat-all.txt" > "$OUT/logcat-nav-keywords.txt" 2>/dev/null || true
  if [[ -s "$OUT/logcat-nav-keywords.txt" ]]; then
    printf '\n[%s][logcat-final]\n' "$(date -u +%FT%TZ)" >> "$OUT/candidates.txt"
    tail -n 100 "$OUT/logcat-nav-keywords.txt" >> "$OUT/candidates.txt"
  fi

  local candidate_lines channel_files
  candidate_lines="$(wc -l < "$OUT/candidates.txt" | tr -d ' ')"
  channel_files="$(find "$OUT" -maxdepth 1 -name '*-history.txt' | wc -l | tr -d ' ')"
  {
    echo "app=$APP_KEY"
    echo "package=$PACKAGE"
    echo "device_serial=$DEVICE_SERIAL"
    echo "duration_seconds=$DURATION"
    echo "interval_seconds=$INTERVAL"
    echo "snapshot_rounds=$ITERATION"
    echo "changed_channels=$channel_files"
    echo "candidate_lines=$candidate_lines"
    if [[ "$candidate_lines" -gt 0 ]]; then echo "result=CANDIDATES_FOUND"; else echo "result=NO_NAV_CANDIDATE"; fi
  } > "$OUT/summary.txt"

  find "$OUT" -maxdepth 1 -type f ! -name SHA256SUMS.txt ! -name '.*.sha' -print0 \
    | xargs -0 shasum -a 256 > "$OUT/SHA256SUMS.txt"
  rm -f "$OUT"/.*.sha 2>/dev/null || true
  chmod 600 "$OUT"/* 2>/dev/null || true
  echo
  echo "Done: $OUT"
  cat "$OUT/summary.txt"
}
trap 'exit 130' INT TERM
trap finish EXIT

say "Listening app=$APP_KEY package=$PACKAGE device=$DEVICE_SERIAL"
say "Open the app now; listener runs for ${DURATION}s (0 means until Ctrl-C)."
say "Evidence may contain route/location data; keep $OUT local."

if ! "${ADB_CMD[@]}" shell pm path "$PACKAGE" > "$OUT/package-path.txt" 2>&1; then
  say "WARN package is not installed/resolvable: $PACKAGE"
  "${ADB_CMD[@]}" shell pm list packages | grep -Ei "carplay|androidauto|vietmap|waze|projection" \
    > "$OUT/similar-packages.txt" 2>/dev/null || true
else
  "${ADB_CMD[@]}" shell dumpsys package "$PACKAGE" > "$OUT/package.txt" 2>&1 || true
fi

"${ADB_CMD[@]}" logcat -v threadtime -T 1 > "$OUT/logcat-all.txt" 2>&1 &
LOGCAT_PID=$!
START_EPOCH="$(date +%s)"
END_EPOCH=0
[[ "$DURATION" -eq 0 ]] || END_EPOCH=$((START_EPOCH + DURATION))
REMOTE_UI="/sdcard/nav-signal-${APP_KEY}-$$.xml"

while [[ "$END_EPOCH" -eq 0 || "$(date +%s)" -lt "$END_EPOCH" ]]; do
  ITERATION=$((ITERATION + 1))
  NOW="$(date -u +%FT%TZ)"
  PID="$("${ADB_CMD[@]}" shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | xargs || true)"
  if [[ "$PID" != "$LAST_PID" ]]; then
    if [[ -n "$PID" ]]; then say "[process] OPEN/RUNNING pid=$PID"; else say "[process] not running"; fi
    printf '%s pid=%s\n' "$NOW" "${PID:-none}" >> "$OUT/process-history.txt"
    LAST_PID="$PID"
  fi

  ACTIVITY="$("${ADB_CMD[@]}" shell dumpsys activity top 2>/dev/null | grep -i -B 4 -A 14 -E "$PACKAGE|$SIGNAL_REGEX" | head -n 160 || true)"
  record_changed activity "$ACTIVITY"

  SERVICES="$("${ADB_CMD[@]}" shell dumpsys activity services "$PACKAGE" 2>/dev/null | head -n 220 || true)"
  record_changed services "$SERVICES"

  NOTIFICATIONS="$("${ADB_CMD[@]}" shell dumpsys notification --noredact 2>/dev/null | grep -i -B 8 -A 35 "$PACKAGE" | head -n 260 || true)"
  record_changed notification "$NOTIFICATIONS"

  MEDIA="$("${ADB_CMD[@]}" shell dumpsys media_session 2>/dev/null | grep -i -B 8 -A 35 -E "$PACKAGE|$SIGNAL_REGEX" | head -n 260 || true)"
  record_changed media-session "$MEDIA"

  BROADCASTS="$("${ADB_CMD[@]}" shell dumpsys activity broadcasts 2>/dev/null | grep -i -B 8 -A 28 -E "$PACKAGE|$SIGNAL_REGEX" | head -n 260 || true)"
  record_changed broadcasts "$BROADCASTS"

  WINDOWS="$("${ADB_CMD[@]}" shell dumpsys window windows 2>/dev/null | grep -i -B 4 -A 18 "$PACKAGE" | head -n 180 || true)"
  record_changed windows "$WINDOWS"

  if (( ITERATION % 2 == 1 )); then
    UI_TMP="$OUT/.ui-new.xml"
    if "${ADB_CMD[@]}" shell uiautomator dump "$REMOTE_UI" >/dev/null 2>&1 \
      && "${ADB_CMD[@]}" pull "$REMOTE_UI" "$UI_TMP" >/dev/null 2>&1; then
      "${ADB_CMD[@]}" shell rm -f "$REMOTE_UI" >/dev/null 2>&1 || true
      UI_CONTENT="$(grep -i -E "$PACKAGE|$SIGNAL_REGEX" "$UI_TMP" | head -n 100 || true)"
      record_changed ui "$UI_CONTENT"
    fi
    rm -f "$UI_TMP"
  fi

  LIVE_LOG="$(tail -n 500 "$OUT/logcat-all.txt" | grep -Ei "$SIGNAL_REGEX" | tail -n 1 || true)"
  if [[ -n "$LIVE_LOG" ]]; then
    LIVE_HASH="$(printf '%s' "$LIVE_LOG" | shasum -a 256 | awk '{print $1}')"
    if [[ "$LIVE_HASH" != "${LAST_LIVE_LOG_HASH:-}" ]]; then
      LAST_LIVE_LOG_HASH="$LIVE_HASH"
      printf '\n[%s][logcat-live]\n%s\n' "$NOW" "$LIVE_LOG" >> "$OUT/candidates.txt"
      printf '  NAV CANDIDATE [logcat]: %s\n' "$(printf '%s' "$LIVE_LOG" | cut -c1-180)"
    fi
  fi

  sleep "$INTERVAL"
done
