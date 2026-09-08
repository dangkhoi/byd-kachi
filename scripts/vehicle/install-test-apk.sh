#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"

require_candidate
select_device
: "${CONFIRM_VEHICLE_INSTALL:?Set CONFIRM_VEHICLE_INSTALL=YES after reviewing preflight evidence}"
[[ "$CONFIRM_VEHICLE_INSTALL" == "YES" ]] || { echo "ERROR: install not confirmed" >&2; exit 5; }

new_evidence_dir
# Copy the build-written candidate record, never a hand-written handoff: the old
# two-track-vehicle-ready.md still carries 0.67 export instructions and a blocklisted SHA.
cp "$(vehicle_root)/docs/_handoff/vehicle-candidate.json" "$EVIDENCE_DIR/" 2>/dev/null || true
inspect_installed_package | tee "$EVIDENCE_DIR/pre-install-state.txt"
printf 'install_started=%s\napk=%s\nsha256=%s\nexact_source_id=%s\n' \
  "$(date -u +%FT%TZ)" "$APK" "$EXPECTED_SHA256" "${CANDIDATE_EXACT_SOURCE_ID:-unknown}" \
  > "$EVIDENCE_DIR/install.txt"
if ! "${ADB[@]}" install -r "$APK" 2>&1 | tee -a "$EVIDENCE_DIR/install.txt" | grep -q "^Success"; then
  echo "ERROR: install failed; see $EVIDENCE_DIR/install.txt" >&2
  if grep -qE "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match" "$EVIDENCE_DIR/install.txt"; then
    cat >&2 <<'EOF'
Signer mismatch with the build already on the unit. The only remedy is:
  adb uninstall com.byd.clusternav
That also clears Cast durable state, so after reinstalling you MUST record F1 and the
durable-Adjustment rows as fresh-install variants and note it in the sign-off table.
Do not substitute another APK.
EOF
    exit 6
  fi
  exit 5
fi
"${ADB[@]}" shell dumpsys package com.byd.clusternav | grep -E 'versionCode|versionName|firstInstallTime|lastUpdateTime' | tee -a "$EVIDENCE_DIR/install.txt"
printf 'install_finished=%s\n' "$(date -u +%FT%TZ)" >> "$EVIDENCE_DIR/install.txt"
echo "Installed exact hash. Continue with navigation and Cast matrices; do not substitute another APK."
