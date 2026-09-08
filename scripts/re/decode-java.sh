#!/usr/bin/env bash
set -euo pipefail

# Exact T1 tools. Large archives and decoded output stay outside the repository.
JDK17="/opt/homebrew/opt/openjdk@17"
JADX="/opt/homebrew/Cellar/jadx/1.5.6/bin/jadx"
JADX_SCRIPT_SHA="64a6ee6bcf7490ea682508db2a73d6cda8b671a5211af5ee3ff098441af038a7"
JADX_JAR="/opt/homebrew/Cellar/jadx/1.5.6/libexec/lib/jadx-1.5.6-all.jar"
JADX_JAR_SHA="966d314282d95ff7c72f597bb9c744999856666a3b856575e3c035a9c12a0b65"
CACHE="$HOME/Library/Caches/clusternav-re"
APKTOOL="$CACHE/downloads/apktool_3.0.3.jar"
APKTOOL_SHA="dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423"
DECODE_ROOT="${CLUSTERNAV_RE_DECODE_ROOT:-$CACHE/decoded}"
[[ "$DECODE_ROOT" == /* && "$DECODE_ROOT" != "/" ]] || {
  printf 'decode root must be an absolute non-root path\n' >&2
  exit 2
}
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd -P)"
STALE_JAR="$REPO_ROOT/../tools/jadx/lib/jadx-1.5.0-all.jar"
STALE_SHA="c1290292e17ff6dcaa030d38b9173794c3eda4b844eaa90d17e82f9a8ab4429f"

hash_file() { shasum -a 256 "$1" | awk '{print $1}'; }
require_hash() {
  local path="$1" expected="$2" label="$3"
  [[ -f "$path" ]] || { printf 'missing %s\n' "$label" >&2; exit 2; }
  local actual
  actual="$(hash_file "$path")"
  [[ "$actual" == "$expected" ]] || {
    printf '%s hash mismatch: expected=%s actual=%s\n' "$label" "$expected" "$actual" >&2
    exit 2
  }
}

preflight() {
  [[ -x "$JDK17/bin/java" ]] || { printf 'JDK17 unavailable\n' >&2; exit 2; }
  [[ -x "$JADX" ]] || { printf 'pinned JADX unavailable\n' >&2; exit 2; }
  require_hash "$JADX" "$JADX_SCRIPT_SHA" "JADX launcher"
  require_hash "$JADX_JAR" "$JADX_JAR_SHA" "JADX jar"
  require_hash "$APKTOOL" "$APKTOOL_SHA" "Apktool jar"
  [[ "$(JAVA_HOME="$JDK17" "$JADX" --version)" == "1.5.6" ]] || exit 2
  [[ "$(JAVA_HOME="$JDK17" "$JDK17/bin/java" -jar "$APKTOOL" --version)" == "3.0.3" ]] || exit 2
  if [[ -f "$STALE_JAR" ]]; then
    local stale_actual
    stale_actual="$(hash_file "$STALE_JAR")"
    printf 'REJECTED stale sibling JADX 1.5.0: <project-root>/../tools/jadx (%s)\n' "$stale_actual"
    [[ "$stale_actual" == "$STALE_SHA" ]] || printf 'note: stale candidate bytes changed; still rejected\n'
  fi
  printf 'PASS JADX=1.5.6 Apktool=3.0.3 JAVA_HOME=/opt/homebrew/opt/openjdk@17\n'
}

usage() {
  printf 'usage: %s --preflight | <local-apk-or-dex>\n' "$(basename "$0")" >&2
  exit 2
}

preflight
[[ $# -eq 1 ]] || usage
[[ "$1" == "--preflight" ]] && exit 0
INPUT="$1"
[[ -f "$INPUT" && ! -L "$INPUT" ]] || { printf 'input is not a regular local file\n' >&2; exit 2; }
case "$(basename "$INPUT")" in ""|.*|*..*) printf 'unsafe input basename\n' >&2; exit 2;; esac

INPUT="$(cd "$(dirname "$INPUT")" && pwd -P)/$(basename "$INPUT")"
INPUT_SHA="$(hash_file "$INPUT")"
TARGET="$DECODE_ROOT/$INPUT_SHA"
if [[ -f "$TARGET/decode-manifest.json" ]]; then
  printf 'EXISTS sha256=%s output=<user-cache>/clusternav-re/decoded/%s\n' "$INPUT_SHA" "$INPUT_SHA"
  exit 0
fi

mkdir -p "$DECODE_ROOT" "$CACHE/framework"
WORK="$DECODE_ROOT/.${INPUT_SHA}.tmp.$$"
rm -rf "$WORK"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/jadx-auto" "$WORK/jadx-fallback"
INPUT_KIND="apk"
case "${INPUT##*.}" in dex|DEX) INPUT_KIND="dex";; esac
set +e
JAVA_HOME="$JDK17" "$JADX" --no-res --show-bad-code --threads-count 1 \
  --decompilation-mode auto --output-dir "$WORK/jadx-auto" "$INPUT" >"$WORK/jadx-auto.log" 2>&1
AUTO_RC=$?
set -e
[[ "$AUTO_RC" == "0" || "$AUTO_RC" == "3" ]] || { cat "$WORK/jadx-auto.log" >&2; exit "$AUTO_RC"; }
[[ -n "$(find "$WORK/jadx-auto" -type f -print -quit)" ]] || { printf 'JADX auto produced no files\n' >&2; exit 3; }
AUTO_ERRORS="$(sed -n 's/.*finished with errors, count: \([0-9][0-9]*\).*/\1/p' "$WORK/jadx-auto.log" | tail -1)"
AUTO_ERRORS="${AUTO_ERRORS:-0}"
set +e
JAVA_HOME="$JDK17" "$JADX" --no-res --show-bad-code --threads-count 1 \
  --decompilation-mode fallback --output-dir "$WORK/jadx-fallback" "$INPUT" >"$WORK/jadx-fallback.log" 2>&1
FALLBACK_RC=$?
set -e
if [[ "$FALLBACK_RC" != "0" ]]; then cat "$WORK/jadx-fallback.log" >&2; exit "$FALLBACK_RC"; fi
[[ -n "$(find "$WORK/jadx-fallback" -type f -print -quit)" ]] || { printf 'JADX fallback produced no files\n' >&2; exit 3; }
if [[ "$INPUT_KIND" == "apk" ]]; then
  mkdir -p "$WORK/apktool"
  set +e
  JAVA_HOME="$JDK17" "$JDK17/bin/java" -jar "$APKTOOL" decode --all-src --no-res --force --jobs 1 \
    --frame-path "$CACHE/framework" --output "$WORK/apktool" "$INPUT" >"$WORK/apktool.log" 2>&1
  APKTOOL_RC=$?
  set -e
  if [[ "$APKTOOL_RC" != "0" ]]; then cat "$WORK/apktool.log" >&2; exit "$APKTOOL_RC"; fi
  APKTOOL_STATE="decoded-smali-no-res"
else
  printf 'Apktool is not applicable to a standalone DEX input.\n' >"$WORK/apktool.log"
  APKTOOL_STATE="not-applicable-standalone-dex"
fi
python3 - "$WORK/decode-manifest.json" "$INPUT_SHA" "$(basename "$INPUT")" "$AUTO_RC" "$AUTO_ERRORS" "$INPUT_KIND" "$APKTOOL_STATE" <<'PY'
import json, sys
from pathlib import Path
out, digest, name, auto_exit, auto_errors, input_kind, apktool_state = sys.argv[1:]
modes = ["jadx-auto", "jadx-fallback"]
if input_kind == "apk":
    modes.append("apktool-smali-no-res")
value = {
    "apktool": {"state": apktool_state},
    "input_kind": input_kind,
    "input_name": name,
    "input_sha256": digest,
    "modes": modes,
    "schema": "clusternav.re-decode/v1",
    "tools": {"apktool": "3.0.3", "jadx": "1.5.6", "java": "17.0.19"},
    "jadx_auto": {"error_count": int(auto_errors), "exit_code": int(auto_exit), "partial_output_retained": int(auto_exit) == 3},
    "jadx_fallback": {"exit_code": 0},
}
Path(out).write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
rm -rf "$TARGET"
mv "$WORK" "$TARGET"
trap - EXIT
printf 'DECODED sha256=%s output=<user-cache>/clusternav-re/decoded/%s\n' "$INPUT_SHA" "$INPUT_SHA"
