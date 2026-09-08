#!/usr/bin/env bash
set -euo pipefail

vehicle_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "ERROR: missing command: $1" >&2; exit 2; }
}

APPROVED_CANDIDATE_MANIFEST_REL="docs/_handoff/vehicle-candidate.json"

# Every artifact that must never reach a head unit again. The first entry is the 0.67
# candidate whose 2026-07-25 field run failed on every case; the rest are superseded
# pre-0.72 candidates that still exist on disk under .authorized-build/.
INVALIDATED_CANDIDATE_SHA256S=(
  "1b9c016273296454c9fd0ac88bb51dd8c7447b8b7d60b113d689eb7eb9d6b184"
  "afcd7a07651d5def520c62a4eb3016876d3b0610a438f156a0517564a4688005"
  "a7c4701bc881298d05500fe59517ac55ff8fe68d2c328c208506c0529ed616aa"
  "b1f1356251950601da5b7e87c5e0f09c3a94f1510766c841d17add2dfceb7c0c"
  # 1.04 pre-hardening release (2026-08-06): exported TEST_ADAS_*/TEST_SPEED_LIMIT via RebindReceiver
  # (WARN-1 / P0). Current source is hardened; this stale binary must never reach a head unit. Rebuild
  # the candidate from hardened source via collectAuthorizedApk, then regenerate vehicle-candidate.json.
  "b9a0259e7174a694b7bd4fd8984199ac91091e5e52e8de9c73ae6c67542bb598"
)

candidate_field() {
  local root="$1" field="$2"
  python3 - "$root/$APPROVED_CANDIDATE_MANIFEST_REL" "$field" <<'PY'
import json,sys
try:
    with open(sys.argv[1], encoding="utf-8") as handle:
        data = json.load(handle)
except FileNotFoundError:
    sys.exit(11)
except (OSError, ValueError):
    sys.exit(12)
value = data.get(sys.argv[2])
if isinstance(value, bool) or not isinstance(value, (str, int)) or value == "":
    sys.exit(13)
print(value)
PY
}

# Resolves the one installable candidate from the manifest written by the authorized
# build. No approved hash is hardcoded here, so a new candidate never needs a script
# edit, and an operator can never silently install a stale artifact that happens to
# match an old constant.
require_candidate() {
  require_command shasum
  require_command python3
  local root status
  root="$(vehicle_root)"

  local manifest="$root/$APPROVED_CANDIDATE_MANIFEST_REL"
  [[ -f "$manifest" ]] || {
    echo "ERROR: no vehicle candidate manifest at $APPROVED_CANDIDATE_MANIFEST_REL" >&2
    echo "       Run the authorized collectAuthorizedApk build first." >&2
    exit 2
  }

  local declared_apk declared_sha
  declared_apk="$(candidate_field "$root" apk)" || status=$?
  declared_sha="$(candidate_field "$root" sha256)" || status=$?
  [[ -n "${declared_apk:-}" && -n "${declared_sha:-}" ]] || {
    echo "ERROR: candidate manifest is unreadable or incomplete (status=${status:-?})" >&2
    exit 2
  }
  [[ "$declared_sha" =~ ^[0-9a-f]{64}$ ]] || {
    echo "ERROR: candidate manifest sha256 is not 64 lowercase hex: $declared_sha" >&2
    exit 3
  }

  APK="${APK:-$root/$declared_apk}"
  EXPECTED_SHA256="${EXPECTED_SHA256:-$declared_sha}"
  [[ -f "$APK" ]] || { echo "ERROR: APK not found: $APK" >&2; exit 2; }

  [[ "$EXPECTED_SHA256" == "$declared_sha" ]] || {
    echo "ERROR: EXPECTED_SHA256 does not match the recorded candidate" >&2
    echo "       recorded=$declared_sha requested=$EXPECTED_SHA256" >&2
    exit 3
  }

  local actual
  actual="$(shasum -a 256 "$APK" | awk '{print $1}')"
  local blocked
  for blocked in "${INVALIDATED_CANDIDATE_SHA256S[@]}"; do
    [[ "$actual" != "$blocked" ]] || {
      echo "ERROR: invalidated or superseded vehicle candidate is prohibited: $actual" >&2
      exit 9
    }
  done
  [[ "$actual" == "$declared_sha" ]] || {
    echo "ERROR: APK SHA mismatch recorded=$declared_sha actual=$actual" >&2
    exit 3
  }

  CANDIDATE_EXACT_SOURCE_ID="$(candidate_field "$root" exactSourceId || true)"
  CANDIDATE_VERSION_NAME="$(candidate_field "$root" versionName || true)"
  CANDIDATE_VERSION_CODE="$(candidate_field "$root" versionCode || true)"
  export APK EXPECTED_SHA256 CANDIDATE_EXACT_SOURCE_ID CANDIDATE_VERSION_NAME CANDIDATE_VERSION_CODE
  echo "APK=$APK"
  echo "APK_SHA256=$actual"
  echo "CANDIDATE_VERSION=${CANDIDATE_VERSION_NAME:-?} (${CANDIDATE_VERSION_CODE:-?})"
  echo "CANDIDATE_EXACT_SOURCE_ID=${CANDIDATE_EXACT_SOURCE_ID:-?}"
}

# Reports what the head unit already holds. A signer change makes `adb install -r`
# fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE, and the only remedy (uninstall) also
# destroys Cast durable state, which changes the preconditions of F1 and of the
# durable-Adjustment rows. The operator must see this before installing.
inspect_installed_package() {
  local pkg="com.byd.clusternav" dump
  dump="$("${ADB[@]}" shell dumpsys package "$pkg" 2>/dev/null || true)"
  if [[ -z "$dump" || "$dump" != *"$pkg"* ]]; then
    echo "INSTALLED_STATE=absent"
    INSTALLED_VERSION_CODE=""
    INSTALLED_SIGNER=""
    export INSTALLED_VERSION_CODE INSTALLED_SIGNER
    return 0
  fi
  INSTALLED_VERSION_CODE="$(printf '%s\n' "$dump" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)"
  INSTALLED_SIGNER="$(printf '%s\n' "$dump" | sed -n 's/.*\(PackageSignatures\|signatures\)=\(.*\)/\2/p' | head -1)"
  export INSTALLED_VERSION_CODE INSTALLED_SIGNER
  echo "INSTALLED_STATE=present"
  echo "INSTALLED_VERSION_CODE=${INSTALLED_VERSION_CODE:-unknown}"
  echo "INSTALLED_SIGNER=${INSTALLED_SIGNER:-unknown}"
  if [[ -n "${CANDIDATE_VERSION_CODE:-}" && -n "$INSTALLED_VERSION_CODE" ]] \
    && (( INSTALLED_VERSION_CODE > CANDIDATE_VERSION_CODE )); then
    echo "WARNING: installed versionCode $INSTALLED_VERSION_CODE is newer than candidate $CANDIDATE_VERSION_CODE;" >&2
    echo "         adb install -r will refuse the downgrade." >&2
  fi
  echo "NOTE: if install fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE the signer differs."
  echo "      Uninstalling clears Cast durable state, so F1 (adopt existing cluster VD) and the"
  echo "      durable-Adjustment rows must then be recorded as fresh-install variants."
}

select_device() {
  require_command adb
  if [[ -n "${ADB_SERIAL:-}" ]]; then
    DEVICE_SERIAL="$ADB_SERIAL"
  else
    local devices count
    devices="$(adb devices | awk 'NR>1 && $2=="device" {print $1}')"
    count="$(printf '%s\n' "$devices" | sed '/^$/d' | wc -l | tr -d ' ')"
    [[ "$count" -eq 1 ]] || {
      echo "ERROR: expected exactly one adb device; set ADB_SERIAL explicitly" >&2
      exit 4
    }
    DEVICE_SERIAL="$devices"
  fi
  ADB=(adb -s "$DEVICE_SERIAL")
  "${ADB[@]}" get-state >/dev/null
  echo "ADB_SERIAL=$DEVICE_SERIAL"
}

new_evidence_dir() {
  local root stamp
  root="$(vehicle_root)"
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  EVIDENCE_DIR="${EVIDENCE_DIR:-$root/oncar-v2-$stamp}"
  mkdir -p "$EVIDENCE_DIR"
  chmod 700 "$EVIDENCE_DIR"
  echo "EVIDENCE_DIR=$EVIDENCE_DIR"
}
