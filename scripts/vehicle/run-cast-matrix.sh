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

# Sau lần tách module 2026-07-27, code Cast nằm ở ba nơi: quyết định trong :core, transport trong
# :car-integration, ghép nối Android trong :app. Assertion tĩnh phải soi cả ba, nếu không nó thành vô
# nghĩa mà vẫn xanh — đúng kiểu bẫy đã gặp: kiểm thứ dễ kiểm thay vì thứ cần kiểm.
V2_SRC=""
for candidate in \
  "$(vehicle_root)/core/src/main/kotlin/com/byd/clusternav/modules/clustercast/v2" \
  "$(vehicle_root)/car-integration/src/main/kotlin/com/byd/clusternav/modules/clustercast/v2" \
  "$(vehicle_root)/app/src/main/java/com/byd/clusternav/modules/clustercast/v2"; do
  [[ -d "$candidate" ]] && V2_SRC="$V2_SRC $candidate"
done
if [[ -z "${V2_SRC// /}" ]]; then
  echo "ERROR: không tìm thấy source Cast ở module nào — assertion tĩnh sẽ vô nghĩa" >&2; exit 7
fi

# D10 restored `am display move-stack`, so its ABSENCE is the failure, not its presence.
# What is genuinely checkable without a device:
#   1. exactly one encoding site, so no second path can smuggle a different destination;
#   2. the destination is the resolved cluster-display variable, never a literal;
#   3. no move-stack line mentions display 0;
#   4. the centre screen is still reached with `am start --display 0`, not by reparenting.
MOVE_SITES="$(grep -RhoE "am display move-stack [^\"]*" $V2_SRC || true)"
MOVE_COUNT="$(printf '%s\n' "$MOVE_SITES" | grep -cE "move-stack" || true)"
if [[ "${MOVE_COUNT:-0}" -ne 1 ]]; then
  echo "ERROR: expected exactly one move-stack encoding site, found ${MOVE_COUNT:-0}" >&2; exit 7
fi
if ! printf '%s\n' "$MOVE_SITES" | grep -qE 'move-stack \$[A-Za-z_][A-Za-z0-9_]* \$display$'; then
  echo "ERROR: move-stack destination is not the resolved cluster display variable: $MOVE_SITES" >&2
  echo "       (rename or literal destination requires re-review of the D10 ladder)" >&2
  exit 7
fi
if grep -RnE "move-stack[^\"]*(--display 0|[[:space:]]0([[:space:]]|\"|$))" $V2_SRC >/dev/null; then
  echo "ERROR: move-stack toward display 0 is forbidden" >&2; exit 7
fi
if ! grep -Rq -- "am start --display 0" $V2_SRC; then
  echo "ERROR: the return-to-centre path no longer uses am start --display 0" >&2; exit 7
fi
capture() {
  local name="$1"
  {
    echo "timestamp=$(date -u +%FT%TZ)"
    "${ADB[@]}" shell am stack list || true
    "${ADB[@]}" shell dumpsys window displays || true
    "${ADB[@]}" shell dumpsys display || true
    "${ADB[@]}" shell dumpsys activity top || true
    "${ADB[@]}" logcat -d -t 800 | grep -E 'ClusterCast|Cast V2|CastAndroid' || true
  } > "$EVIDENCE_DIR/cast-$name.txt" < /dev/null
}

MATRIX_STEPS=(C1 C2 C3 C4 C5 C6 C7 C8 C9 C10 F1 F2 F3 F4 F5 F6 F7 F8 F9 C11)
matrix_init "cast"

matrix_step C1 "Open Cluster Cast, choose a normal app, Cast; verify one target and exact gauges return after Stop" \
  && capture "c1-normal-cold"
matrix_step C2 "Recast same normal app, then normal→normal switch; verify no blank/freeze and one occupant" \
  && capture "c2-normal-warm"
matrix_step C3 "Connect CarPlay, Cast via resume-only, Switch/Stop; verify phone session continuity" \
  && capture "c3-carplay"
matrix_step C4 "Connect Android Auto, Cast via resume-only, Switch/Stop; verify attach/session and no hidden primitive" \
  && capture "c4-android-auto"
matrix_step C5 "Run CP→normal, normal→AA, AA→CP pairwise cases; verify at most one protected residue and truthful degraded label" \
  && capture "c5-protected-pairs"
matrix_step C6 "Open Adjustment; apply preset/move/DPI, Undo, Restore-entry, Reset and Done; verify accepted geometry changes only on Done" \
  && capture "c6-adjustment"
matrix_step C7 "Edit favorite/protected policy, recreate Activity, verify order/policy plus scroll/focus restoration; use Bubble direct Stop and verify acknowledgement within 500ms" \
  && capture "c7-app-policy-bubble"
matrix_step C8 "Disconnect a projection phone session; verify connected guidance first, then one explicitly confirmed owner-bound recovery after two stable samples" \
  && capture "c8-disconnect-recovery"
matrix_step C9 "Interrupt transport during a user operation; verify unknown-effect recovery, no blind replay, one compensation maximum and no worker growth" \
  && capture "c9-transport"
matrix_step C10 "Sleep/wake head unit; verify Cast-owned lifecycle re-observation, journal rehydration and no auto-cast/replay" \
  && capture "c10-sleep-wake"

matrix_step F1 "Cold start with the cluster VD already present; bootstrap adopts it, zero seal commands, ACTIVE_VERIFIED" "Copy the in-app operation log afterwards: Chiếu cụm → Chẩn đoán → sao chép" \
  && capture "f1-adopt-existing-vd"
matrix_step F2 "Cast a navigation app with a route already set; route survives, no force-stop in log, no white frame" "Copy the in-app operation log afterwards: Chiếu cụm → Chẩn đoán → sao chép" \
  && capture "f2-route-preserved"
matrix_step F3 "App whose task refuses to reparent; prompt offers 'Tắt app và chiếu lại', declining leaves prior state intact" "Copy the in-app operation log afterwards: Chiếu cụm → Chẩn đoán → sao chép" \
  && capture "f3-reparent-refusal-prompt"
matrix_step F4 "CarPlay / Android Auto; resume-only, no force-stop offered, phone session intact" "Copy the in-app operation log afterwards: Chiếu cụm → Chẩn đoán → sao chép" \
  && capture "f4-protected-resume-only"
matrix_step F5 "Kiểu cụm thẳng (31) for one app and cong (30) for another; each app keeps its shape, km/h gauge returns" "Copy the in-app operation log afterwards: Chiếu cụm → Chẩn đoán → sao chép" \
  && capture "f5-cluster-kind-per-app"
matrix_step F6 "Cỡ chữ cụm (DPI) per app; wm density appears once for that app and rendered size changes" "Copy the in-app operation log afterwards: Chiếu cụm → Chẩn đoán → sao chép" \
  && capture "f6-dpi-per-app"
matrix_step F7 "Force-stop the cast app externally; watchdog runs exactly one canonical Stop within ~2 minutes, gauges return" "Copy the in-app operation log afterwards: Chiếu cụm → Chẩn đoán → sao chép" \
  && capture "f7-watchdog-single-stop"
matrix_step F8 "Stop after any cast; PIP app-op and animation scales read back to pre-cast values" "Copy the in-app operation log afterwards: Chiếu cụm → Chẩn đoán → sao chép" \
  && capture "f8-stop-restores-baseline"
matrix_step F9 "Firmware dump wording changed / parse miss; log shows DisplayManager fallback identity, not a dead read-only state" "Copy the in-app operation log afterwards: Chiếu cụm → Chẩn đoán → sao chép" \
  && capture "f9-parse-miss-fallback"

if matrix_step C11 "Physical power-button reboot, then reconnect and verify rehydration" \
  "adb reboot is NOT valid evidence; use the head-unit power button"; then
  read -r -p "    Type PHYSICAL to attest the power-button reboot: " proof
  [[ "$proof" == "PHYSICAL" ]] || { echo "ERROR: physical reboot not attested" >&2; exit 8; }
  capture "c11-physical-reboot"
fi

matrix_summary
matrix_listing || echo "Cast matrix captured at $EVIDENCE_DIR"
