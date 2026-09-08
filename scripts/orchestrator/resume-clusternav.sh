#!/bin/zsh
set -u

ROOT="${CLUSTERNAV_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
RUNTIME="$ROOT/.kiro/runtime"
STATE_FILE="$RUNTIME/orchestrator.state"
SESSION_FILE="$RUNTIME/orchestrator-session-id"
KIRO_FILE="$RUNTIME/kiro-bin"
RUN_LOCK="$RUNTIME/orchestrator-run.lock"
LOG_FILE="$RUNTIME/orchestrator.log"
mkdir -p "$RUNTIME"

state="$(cat "$STATE_FILE" 2>/dev/null || printf CONTINUE)"
case "$state" in
  CONTINUE) ;;
  WAITING_FOR_VEHICLE_TEST|DONE|PAUSED) exit 0 ;;
  *) printf '%s invalid state=%s\n' "$(date -u +%FT%TZ)" "$state" >> "$LOG_FILE"; exit 2 ;;
esac

session_id="$(cat "$SESSION_FILE" 2>/dev/null || true)"
[ -n "$session_id" ] || exit 0
kiro_bin="$(cat "$KIRO_FILE" 2>/dev/null || command -v kiro-cli || true)"
[ -x "$kiro_bin" ] || exit 0

kiro_home="${KIRO_HOME:-$HOME/.kiro}"
session_lock="$kiro_home/sessions/cli/$session_id.lock"
if [ -f "$session_lock" ]; then
  pid="$(sed -nE 's/.*"pid"[[:space:]]*:[[:space:]]*([0-9]+).*/\1/p' "$session_lock" | head -1)"
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    exit 0
  fi
  rm -f "$session_lock"
fi

mkdir "$RUN_LOCK" 2>/dev/null || exit 0
trap 'rmdir "$RUN_LOCK" 2>/dev/null || true' EXIT INT TERM

prompt='Continue the approved autonomous ClusterNav off-car execution from docs/_handoff/AUTONOMOUS-RESUME.md and docs/_handoff/two-track-autonomous-progress.md. First inspect any partial side effects and current tests. Work in visible 3–5-file batches. Never reset, clean, switch branches, discard files, install, execute vehicle ADB/dadb tests, commit, push, merge, or touch historical APK bytes. Exactly one final authorized release-variant vehicle-test APK may be built only after full off-car validation and final exact-source closure. Update .kiro/runtime/orchestrator.state to WAITING_FOR_VEHICLE_TEST when the vehicle-ready handoff is complete.'

printf '%s resume session=%s\n' "$(date -u +%FT%TZ)" "$session_id" >> "$LOG_FILE"
cd "$ROOT" || exit 3
"$kiro_bin" chat --resume-id "$session_id" --no-interactive --trust-all-tools "$prompt" >> "$LOG_FILE" 2>&1
rc=$?
printf '%s exit=%s\n' "$(date -u +%FT%TZ)" "$rc" >> "$LOG_FILE"
exit "$rc"
