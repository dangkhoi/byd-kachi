#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
source "$(dirname "$0")/matrix.sh"
matrix_parse_args "$@"
if ! matrix_listing; then
  require_candidate
  select_device
  new_evidence_dir
fi

capture() {
  local name="$1"
  {
    echo "timestamp=$(date -u +%FT%TZ)"
    "${ADB[@]}" shell dumpsys notification --noredact | grep -E 'com.byd.clusternav|NotificationListener' || true
    "${ADB[@]}" shell dumpsys activity services com.byd.clusternav || true
    "${ADB[@]}" logcat -d -t 500 | grep -E 'ClusterBroadcaster|NavRepository|NavNotification' || true
  } > "$EVIDENCE_DIR/navigation-$name.txt" < /dev/null
}

MATRIX_STEPS=(N1 N2 N3 N4 N5)
matrix_init "navigation"

matrix_step N1 "Start supported phone navigation; verify Cluster lane and HUD show the same maneuver/freshness" \
  && capture "n1-both-on"
matrix_step N2 "Disable HUD only; verify Cluster lane continues and HUD clears" \
  && capture "n2-hud-off"
matrix_step N3 "Enable HUD and disable Cluster lane only; verify HUD continues" \
  && capture "n3-lane-off"
matrix_step N4 "Re-enable both, change road/maneuver, then end navigation; verify whole-session Stop clears both" \
  && capture "n4-stop"

if matrix_step N5 "Physical power-button reboot, then verify both outputs rehydrate without stale data" \
  "adb reboot is NOT valid evidence; use the head-unit power button"; then
  read -r -p "    Type PHYSICAL to attest the power-button reboot: " proof
  [[ "$proof" == "PHYSICAL" ]] || { echo "ERROR: physical reboot not attested" >&2; exit 6; }
  capture "n5-physical-reboot"
fi

matrix_summary
matrix_listing || echo "Navigation matrix captured at $EVIDENCE_DIR"
