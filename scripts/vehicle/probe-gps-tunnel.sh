#!/usr/bin/env bash
# ClusterNav tunnel GPS probe — READ-ONLY measurement for M1..M4 of
# docs/research/gps-dead-reckon-tunnel.html.
#
# Answers, with evidence instead of guesswork:
#   M1 does this head unit expose an IMU (gyroscope / accelerometer / rotation vector)?
#   M2 how long after entering a tunnel is the fix lost, and how long until re-fix on exit?
#   M3 does the BYD speed HAL keep reading inside the tunnel?
#   M4 does the navigation notification keep arriving, and does its distance freeze?
#
# This script never installs, never starts an activity, never writes a setting and never
# mutates location state. It only reads dumpsys/logcat. Run it for a whole tunnel pass.
set -euo pipefail
source "$(dirname "$0")/common.sh"

PACKAGE="com.byd.clusternav"
INTERVAL="${INTERVAL:-2}"
DURATION="${DURATION:-600}"

require_command adb
require_command shasum
select_device

EVIDENCE_DIR="${EVIDENCE_DIR:-$(vehicle_root)/oncar-tunnel-probe-$(date -u +%Y%m%dT%H%M%SZ)}"
export EVIDENCE_DIR
new_evidence_dir

echo "Sampling every ${INTERVAL}s for up to ${DURATION}s. Ctrl-C when the tunnel pass is done."

# ── M1: sensor inventory (once) ───────────────────────────────────────────────
{
  echo "timestamp=$(date -u +%FT%TZ)"
  echo "serial=$DEVICE_SERIAL"
  echo "model=$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
  echo "android=$("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r')"
  echo "--- sensorservice ---"
  "${ADB[@]}" shell dumpsys sensorservice 2>/dev/null | sed -n '1,120p'
} > "$EVIDENCE_DIR/m1-sensors.txt" 2>&1 || true

IMU_HITS="$(grep -icE "gyroscope|accelerometer|rotation vector|game rotation" "$EVIDENCE_DIR/m1-sensors.txt" || true)"
echo "M1 imu_sensor_mentions=${IMU_HITS:-0} (0 means no IMU exposed → position DR is impossible here)"
echo "m1_imu_sensor_mentions=${IMU_HITS:-0}" > "$EVIDENCE_DIR/summary.txt"

# ── M2/M3/M4: time series ─────────────────────────────────────────────────────
SERIES="$EVIDENCE_DIR/timeseries.tsv"
printf 'iso_utc\telapsed_s\tlast_fix_age\tgnss_sv_used\tspeed_kmh\tnav_distance\n' > "$SERIES"

started=$(date +%s)
trap 'echo; echo "stopped by operator"' INT

while :; do
  now=$(date +%s)
  elapsed=$(( now - started ))
  [[ "$elapsed" -lt "$DURATION" ]] || break

  loc="$("${ADB[@]}" shell dumpsys location 2>/dev/null || true)"
  # Age of the last known fix as the platform reports it; wording varies per ROM, so keep raw.
  fix_age="$(printf '%s\n' "$loc" | sed -n 's/.*last location=.*time=\([0-9]*\).*/\1/p' | head -1)"
  [[ -n "$fix_age" ]] || fix_age="$(printf '%s\n' "$loc" | grep -m1 -oE 'age=[0-9]+' | cut -d= -f2 || true)"
  sv_used="$(printf '%s\n' "$loc" | grep -m1 -oE 'satellites?[^0-9]{0,12}[0-9]+' | grep -oE '[0-9]+$' || true)"

  # M3: speed comes from ClusterNav's own log line (the app owns the HAL read).
  speed="$("${ADB[@]}" logcat -d -t 60 2>/dev/null | grep -oE 'speed(_kmh)?=[0-9.]+' | tail -1 | cut -d= -f2 || true)"

  # M4: navigation notification distance, if any listener-visible text carries it.
  nav="$("${ADB[@]}" shell dumpsys notification --noredact 2>/dev/null \
    | grep -iE 'com\.google\.android\.apps\.maps|amap|clusternav' -A4 \
    | grep -m1 -oE '[0-9]+([.,][0-9]+)? ?(m|km)\b' || true)"

  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(date -u +%FT%TZ)" "$elapsed" "${fix_age:-?}" "${sv_used:-?}" "${speed:-?}" "${nav:-?}" >> "$SERIES"
  printf '\r  t=%ss  sv=%s  speed=%s  nav=%s        ' "$elapsed" "${sv_used:-?}" "${speed:-?}" "${nav:-?}"
  sleep "$INTERVAL"
done
echo

# ── Summary ───────────────────────────────────────────────────────────────────
{
  echo "samples=$(( $(wc -l < "$SERIES") - 1 ))"
  echo "samples_without_satellites=$(awk -F'\t' 'NR>1 && ($4=="?"||$4=="0"){n++} END{print n+0}' "$SERIES")"
  echo "samples_with_speed=$(awk -F'\t' 'NR>1 && $5!="?"{n++} END{print n+0}' "$SERIES")"
  echo "distinct_nav_distances=$(awk -F'\t' 'NR>1 && $6!="?"{print $6}' "$SERIES" | sort -u | wc -l | tr -d ' ')"
} >> "$EVIDENCE_DIR/summary.txt"

find "$EVIDENCE_DIR" -maxdepth 1 -type f ! -name SHA256SUMS.txt -print0 \
  | xargs -0 shasum -a 256 > "$EVIDENCE_DIR/SHA256SUMS.txt"

echo "--- summary ---"; cat "$EVIDENCE_DIR/summary.txt"
cat <<'EOF'

How to read it:
  m1_imu_sensor_mentions = 0        -> no IMU: position dead reckoning is impossible on this unit,
                                       and a frozen map inside a tunnel is expected behaviour.
  samples_with_speed = 0            -> the speed HAL is unusable here: Tunnel Hold has no input, stop.
  distinct_nav_distances = 1        -> the navigation distance froze: a countdown can still be
                                       extrapolated from that last value.
  distinct_nav_distances = 0        -> the notification disappeared entirely: nothing to extrapolate,
                                       so Tunnel Hold would not help either.
EOF
echo "Evidence at $EVIDENCE_DIR (gitignored; review before sharing — notification text can contain addresses)."
