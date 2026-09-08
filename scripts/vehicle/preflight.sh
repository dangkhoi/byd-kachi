#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"

require_command shasum
require_candidate
select_device
new_evidence_dir

{
  echo "timestamp=$(date -u +%FT%TZ)"
  echo "apk=$APK"
  echo "sha256=$EXPECTED_SHA256"
  echo "exact_source_id=${CANDIDATE_EXACT_SOURCE_ID:-unknown}"
  echo "candidate_version=${CANDIDATE_VERSION_NAME:-?} (${CANDIDATE_VERSION_CODE:-?})"
  inspect_installed_package
  "${ADB[@]}" shell getprop ro.product.model
  "${ADB[@]}" shell getprop ro.build.fingerprint
  "${ADB[@]}" shell getprop ro.build.version.release
  "${ADB[@]}" shell getprop ro.build.version.sdk
  "${ADB[@]}" shell dumpsys display | sed -n '1,180p'
} | tee "$EVIDENCE_DIR/preflight.txt"

echo "PASS: read-only preflight complete. Review model/ROM/profile before install."
echo "Do not use adb reboot: physical power-button reboot evidence is required by the matrix."
