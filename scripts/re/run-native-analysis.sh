#!/usr/bin/env bash
set -euo pipefail

export LC_ALL=C
export TZ=UTC

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)"
CACHE_ROOT="${HOME}/Library/Caches/clusternav-re"
GHIDRA_ARCHIVE="${CACHE_ROOT}/downloads/ghidra_12.1.2_PUBLIC_20260605.zip"
GHIDRA_ARCHIVE_SHA="b62e81a0390618466c019c60d8c2f796ced2509c4c1aea4a37644a77272cf99d"
GHIDRA_ROOT="${HOME}/.local/share/clusternav-re/ghidra_12.1.2_PUBLIC"
JAVA_HOME="${CACHE_ROOT}/tools/jdk-21.0.12+8/Contents/Home"
HEADLESS="${GHIDRA_ROOT}/support/analyzeHeadless"
JAVA_BIN="${JAVA_HOME}/bin/java"
EXPORTER="${ROOT}/scripts/re/ghidra/ExportRelevantFunctions.java"
RUNNER="${ROOT}/scripts/re/run-native-analysis.sh"
OLD_INPUT="${ROOT}/../firmware/fw-2602-diff/cmp/libBydCluster_OLD.so"
NEW_INPUT="${ROOT}/../firmware/fw-2602-diff/cmp/libBydCluster_NEW.so"
OLD_SHA="9f8a0b269fbee37bad510e8dfbc239b857e00a87663fc9fb7ae39913f86017ca"
NEW_SHA="3197abee462e1de4ae476b8643a5570a4e90b3d0623534e9451ea810d8ee8ae8"
HEADLESS_SHA="302880328a0024ee24cfe0326d4d9a61c2237116d95f2e0e0df090f747f95e30"
JAVA_SHA="34b9c157bedcebafc6033b8beaa72c2ff14e2b697e33f45aa959a8373d6581a0"
ANALYSIS_TIMEOUT=1200
OUTER_TIMEOUT=1500
MAX_CPU=1
PROJECT_ROOT="${CACHE_ROOT}/ghidra-projects/native-t3"
RUN_ROOT="${CACHE_ROOT}/native-t3"
LOG_ROOT="${CACHE_ROOT}/logs/native-t3"
REPORT="${ROOT}/docs/diagnostics/hud-sign-re/native/libbydcluster-diff.json"
MODE="${1:-run}"

if [[ "${MODE}" != "run" && "${MODE}" != "--verify-determinism" ]]; then
  printf 'usage: %s [--verify-determinism]\n' "$0" >&2
  exit 64
fi

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

require_hash() {
  local path="$1" expected="$2" label="$3" actual
  [[ -f "$path" ]] || { printf 'missing %s: %s\n' "$label" "$path" >&2; exit 2; }
  actual="$(sha256_file "$path")"
  [[ "$actual" == "$expected" ]] || {
    printf '%s hash mismatch: expected=%s actual=%s\n' "$label" "$expected" "$actual" >&2
    exit 2
  }
}

preflight() {
  require_hash "$OLD_INPUT" "$OLD_SHA" "old input"
  require_hash "$NEW_INPUT" "$NEW_SHA" "new input"
  if [[ ! -e "$GHIDRA_ROOT" ]]; then
    require_hash "$GHIDRA_ARCHIVE" "$GHIDRA_ARCHIVE_SHA" "Ghidra archive"
    local stage="${HOME}/.local/share/clusternav-re/.ghidra-extract-t3"
    rm -rf "$stage"
    mkdir -p "$stage" "$(dirname "$GHIDRA_ROOT")"
    /usr/bin/unzip -q "$GHIDRA_ARCHIVE" -d "$stage"
    mv "$stage/ghidra_12.1.2_PUBLIC" "$GHIDRA_ROOT"
    rmdir "$stage"
  fi
  require_hash "$HEADLESS" "$HEADLESS_SHA" "Ghidra analyzeHeadless"
  require_hash "$JAVA_BIN" "$JAVA_SHA" "Ghidra Java 21"
  [[ -f "$EXPORTER" && -f "$RUNNER" ]] || { echo "analysis implementation missing" >&2; exit 2; }
  for tool in /usr/bin/file /usr/bin/nm /usr/bin/objdump /usr/bin/c++filt /usr/bin/strings python3; do
    command -v "$tool" >/dev/null 2>&1 || { echo "required tool missing: $tool" >&2; exit 2; }
  done
  mkdir -p "$PROJECT_ROOT" "$RUN_ROOT" "$LOG_ROOT" "$(dirname "$REPORT")"
  export JAVA_HOME
  export PATH="${JAVA_HOME}/bin:${PATH}"
}

run_with_timeout() {
  local seconds="$1" output_log="$2"
  shift 2
  python3 - "$seconds" "$output_log" "$@" <<'PY'
import os, signal, subprocess, sys

seconds = int(sys.argv[1])
log_path = sys.argv[2]
command = sys.argv[3:]
with open(log_path, "wb") as log:
    process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    try:
        code = process.wait(timeout=seconds)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
        code = 124
sys.exit(code)
PY
}

try_native_rebuild() {
  local log="${LOG_ROOT}/build-natives-offline.log"
  local gradle_dir="${GHIDRA_ROOT}/support/gradle"
  local -a command
  if command -v gradle >/dev/null 2>&1; then
    command=(gradle --offline buildNatives)
  elif [[ -x "${gradle_dir}/gradlew" ]]; then
    command=("${gradle_dir}/gradlew" --offline buildNatives)
  else
    echo "native fallback unavailable: no Gradle executable" >&2
    return 1
  fi
  echo "Ghidra native-component failure detected; trying documented offline buildNatives fallback." >&2
  (cd "$gradle_dir" && run_with_timeout "$OUTER_TIMEOUT" "$log" "${command[@]}")
}

run_one() {
  local label="$1" input="$2" expected_sha="$3" raw_output="$4"
  local prefix="${label}-${expected_sha:0:12}"
  local project_name="libbydcluster-${prefix}"
  local stdout_log="${LOG_ROOT}/${prefix}-stdout.log"
  local headless_log="${LOG_ROOT}/${prefix}-headless.log"
  local script_log="${LOG_ROOT}/${prefix}-script.log"
  local project_file="${PROJECT_ROOT}/${project_name}.gpr"
  local project_dir="${PROJECT_ROOT}/${project_name}.rep"
  local status=0

  rm -rf "$project_file" "$project_dir"
  rm -f "$stdout_log" "$headless_log" "$script_log" "$raw_output"
  local -a command=(
    "$HEADLESS" "$PROJECT_ROOT" "$project_name"
    -import "$input"
    -readOnly
    -deleteProject
    -analysisTimeoutPerFile "$ANALYSIS_TIMEOUT"
    -max-cpu "$MAX_CPU"
    -scriptPath "${ROOT}/scripts/re/ghidra"
    -postScript ExportRelevantFunctions.java "$raw_output" "$expected_sha" "$label"
    -log "$headless_log"
    -scriptlog "$script_log"
  )
  run_with_timeout "$OUTER_TIMEOUT" "$stdout_log" "${command[@]}" || status=$?
  if [[ "$status" -ne 0 ]] && grep -Eiq 'UnsatisfiedLinkError|native (binary|library).*(missing|failed|not found)|osx_arm_64' "$stdout_log" "$headless_log" 2>/dev/null; then
    try_native_rebuild || true
    rm -rf "$project_file" "$project_dir"
    rm -f "$stdout_log" "$headless_log" "$script_log" "$raw_output"
    status=0
    run_with_timeout "$OUTER_TIMEOUT" "$stdout_log" "${command[@]}" || status=$?
  fi
  if [[ "$status" -ne 0 || ! -s "$raw_output" ]]; then
    printf 'Ghidra %s analysis failed after safe alternatives: exit=%s stdout=%s headless=%s\n' \
      "$label" "$status" "$stdout_log" "$headless_log" >&2
    return 1
  fi
  grep -Eq "EXPORT_RELEVANT_FUNCTIONS result=PASS label=${label}" "$stdout_log" || {
    printf 'Ghidra %s exporter did not emit PASS; see %s\n' "$label" "$stdout_log" >&2
    return 1
  }
}

build_report() {
  local old_raw="$1" new_raw="$2" destination="$3" update_dependents="$4"
  OLD_RAW="$old_raw" NEW_RAW="$new_raw" DESTINATION="$destination" \
  UPDATE_DEPENDENTS="$update_dependents" ROOT="$ROOT" CACHE_ROOT="$CACHE_ROOT" \
  OLD_INPUT="$OLD_INPUT" NEW_INPUT="$NEW_INPUT" OLD_SHA="$OLD_SHA" NEW_SHA="$NEW_SHA" \
  HEADLESS="$HEADLESS" JAVA_BIN="$JAVA_BIN" HEADLESS_SHA="$HEADLESS_SHA" JAVA_SHA="$JAVA_SHA" \
  GHIDRA_ARCHIVE_SHA="$GHIDRA_ARCHIVE_SHA" EXPORTER="$EXPORTER" RUNNER="$RUNNER" LOG_ROOT="$LOG_ROOT" \
  ANALYSIS_TIMEOUT="$ANALYSIS_TIMEOUT" OUTER_TIMEOUT="$OUTER_TIMEOUT" MAX_CPU="$MAX_CPU" \
  python3 - <<'PY'
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path

root = Path(os.environ["ROOT"])
terms = ["trafficSignValue", "trafficSignType", "limitTrafficSignRecognition", "trafficSign", "slaEquip", "NaviInfo"]
adjacent = "trafficSignalStatus"
private_ip = re.compile(r"\b(?:10(?:\.\d{1,3}){3}|192\.168(?:\.\d{1,3}){2}|172\.(?:1[6-9]|2\d|3[01])(?:\.\d{1,3}){2})\b")
home_path = re.compile(r"(?:/(?:Users|home)/[^/\s\"'<>]+|[A-Za-z]:\\Users\\[^\\\r\n\"'<>]+)")

def sha(data):
    if isinstance(data, Path):
        data = data.read_bytes()
    if isinstance(data, str):
        data = data.encode("utf-8")
    return hashlib.sha256(data).hexdigest()

def command(*args, input_text=None):
    return subprocess.run(args, input=input_text, text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, check=True).stdout

def clean(value):
    if isinstance(value, str):
        return private_ip.sub("<vehicle-ip>", home_path.sub("<build-root>", value))
    if isinstance(value, list):
        return [clean(item) for item in value]
    if isinstance(value, dict):
        return {key: clean(item) for key, item in value.items()}
    return value

def matched(name):
    if adjacent in name:
        return [adjacent]
    return [term for term in terms if term in name]

def metadata(label, path, expected):
    file_text = command("/usr/bin/file", "-b", str(path)).strip()
    objdump = command("/usr/bin/objdump", "-f", "-p", str(path))
    build = re.search(r"BuildID\[sha1\]=([0-9a-f]{40})", file_text)
    fmt = re.search(r"file format\s+(\S+)", objdump)
    arch = re.search(r"architecture:\s*([^\n]+)", objdump)
    entry = re.search(r"start address:\s*(0x[0-9a-f]+)", objdump)
    needed = sorted(set(re.findall(r"^\s*NEEDED\s+(\S+)", objdump, re.MULTILINE)))
    soname = re.search(r"^\s*SONAME\s+(\S+)", objdump, re.MULTILINE)
    nm_text = command("/usr/bin/nm", "-S", str(path))
    nm_lines = [line.strip() for line in nm_text.splitlines() if line.strip()]
    parsed = []
    all_names = []
    pattern = re.compile(r"^([0-9a-fA-F]+)\s+([0-9a-fA-F]+)\s+(\S)\s+(\S+)$")
    candidates = []
    for line in nm_lines:
        match = pattern.match(line)
        if not match:
            continue
        address, size, kind, mangled = match.groups()
        all_names.append(mangled)
        if any(term in mangled for term in terms + [adjacent]):
            candidates.append((address, size, kind, mangled))
    demangled = command("/usr/bin/c++filt", input_text="\n".join(row[3] for row in candidates) + "\n").splitlines() if candidates else []
    for (address, size, kind, mangled), name in zip(candidates, demangled):
        parsed.append({"address": f"0x{int(address, 16):x}", "size_bytes": int(size, 16),
                       "symbol_type": kind, "mangled_name": mangled, "demangled_name": name,
                       "matched_terms": matched(name),
                       "scope": "ADJACENT_OUT_OF_SCOPE" if adjacent in name else "PRIMARY_T3"})
    parsed.sort(key=lambda row: (row["demangled_name"], row["address"], row["symbol_type"]))
    normalized_nm = "\n".join(nm_lines) + "\n"
    name_set = "\n".join(sorted(set(all_names))) + "\n"
    return {"alias": f"cluster-{label}-native", "sha256": expected,
            "byte_count": path.stat().st_size, "build_id_sha1": build.group(1) if build else None,
            "elf": {"file_description": file_text, "file_format": fmt.group(1) if fmt else None,
                    "architecture": arch.group(1).strip() if arch else None,
                    "entry_point": entry.group(1) if entry else None,
                    "class": "ELF64", "endianness": "little", "type": "shared_object",
                    "dynamically_linked": "dynamically linked" in file_text,
                    "debug_info": "with debug_info" in file_text,
                    "stripped": "not stripped" not in file_text,
                    "soname": soname.group(1) if soname else None, "needed_libraries": needed},
            "nm_preindex": {"tool": "Apple LLVM nm -S plus c++filt", "total_symbol_count": len(all_names),
                            "normalized_output_sha256": sha(normalized_nm),
                            "symbol_name_set_sha256": sha(name_set),
                            "relevant_symbol_count": len(parsed), "relevant_symbols": parsed}}

def status(label, expected):
    prefix = f"{label}-{expected[:12]}"
    log_root = Path(os.environ["LOG_ROOT"])
    stdout = (log_root / f"{prefix}-stdout.log").read_text(encoding="utf-8", errors="replace")
    lines = [line.strip() for line in stdout.splitlines() if "EXPORT_RELEVANT_FUNCTIONS result=" in line]
    timeout_seen = bool(re.search(r"analysis.*tim(?:ed out|eout)", stdout, re.IGNORECASE))
    diagnostics = {"error_line_count": sum(line.startswith("ERROR ") for line in stdout.splitlines()), "warning_line_count": sum(line.startswith("WARN ") for line in stdout.splitlines()), "gcc_exception_disassembly_failure_count": stdout.count("Failed to disassemble at"), "varnode_context_error_count": stdout.count("VarnodeContext: out of address spaces")}
    return {"label": label, "headless_exit_code": 0, "outer_timeout_seconds": int(os.environ["OUTER_TIMEOUT"]),
            "analysis_timeout_seconds": int(os.environ["ANALYSIS_TIMEOUT"]), "analysis_diagnostics": diagnostics,
            "analysis_timeout_detected": timeout_seen,
            "exporter_status_line": lines[-1] if lines else None,
            "stdout_log": f"<user-cache>/clusternav-re/logs/native-t3/{prefix}-stdout.log",
            "headless_log": f"<user-cache>/clusternav-re/logs/native-t3/{prefix}-headless.log",
            "script_log": f"<user-cache>/clusternav-re/logs/native-t3/{prefix}-script.log"}

def qml_evidence(inputs):
    roots = [("fw-old-product", root / "../firmware/fw-2602-diff/old_product"),
             ("fw-new-product", root / "../firmware/fw-2602-diff/new_product")]
    files = []
    for alias, base in roots:
        if base.is_dir():
            files.extend(f"{alias}/{path.relative_to(base).as_posix()}" for path in base.rglob("*")
                         if path.is_file() and path.suffix.lower() in {".qml", ".rcc"})
    indicators = {}
    pattern = re.compile(r"(?:\.qml\b|\.rcc\b|qrc:/|qInitResources)", re.IGNORECASE)
    for label, path in inputs.items():
        output = command("/usr/bin/strings", "-a", str(path))
        hits = sorted(set(clean(line.strip()) for line in output.splitlines() if pattern.search(line)))
        indicators[label] = {"hit_count": len(hits), "retained": hits[:40], "truncated": len(hits) > 40}
    return {"state": "UNAVAILABLE", "standalone_qml_rcc_hits": sorted(files),
            "native_resource_indicators": indicators, "direct_binding_proven": False,
            "reason": "NO_EXTRACTABLE_QML_RCC_ASSET_OR_DIRECT_DATA_REFERENCE_BINDING_IN_AVAILABLE_CORPUS"}

def function_groups(raw):
    groups = {}
    for function in raw["functions"]:
        role = "THUNK" if function["is_thunk"] else "IMPLEMENTATION"
        groups.setdefault(function["demangled_name"] + "@@" + role, []).append(function)
    return groups

def shape(function):
    if function is None:
        return None
    return {"body_sha256": function["body_sha256"], "body_range_count": function["body_range_count"],
            "callers": function["callers"], "data_references": function["data_references"],
            "decompile": {key: value for key, value in function["decompile"].items() if key != "c"}, "demangled_name": function["demangled_name"],
            "address": function["elf_virtual_address"], "ghidra_entry": function["entry"],
            "is_thunk": function["is_thunk"],
            "matched_terms": function["matched_terms"], "scope": function["scope"],
            "short_name": function["short_name"], "signature": function["signature"],
            "size_bytes": function["size_bytes"], "symbol_source": function["symbol_source"]}

def caller_names(function):
    return sorted(set(row.get("caller_name") for row in function["callers"] if row.get("caller_name")))

def data_targets(function):
    return sorted(set(row.get("target_symbol") for row in function["data_references"]["items"] if row.get("target_symbol")))

old_raw = json.loads(Path(os.environ["OLD_RAW"]).read_text(encoding="utf-8"))
new_raw = json.loads(Path(os.environ["NEW_RAW"]).read_text(encoding="utf-8"))
old_meta = metadata("old", Path(os.environ["OLD_INPUT"]), os.environ["OLD_SHA"])
new_meta = metadata("new", Path(os.environ["NEW_INPUT"]), os.environ["NEW_SHA"])
old_groups, new_groups = function_groups(old_raw), function_groups(new_raw)
unresolved = []
comparisons = []
for identity in sorted(set(old_groups) | set(new_groups)):
    name, role = identity.rsplit("@@", 1)
    olds, news = old_groups.get(identity, []), new_groups.get(identity, [])
    if len(olds) != 1 or len(news) != 1:
        unresolved.append({"kind": "AMBIGUOUS_OR_MISSING_DEMANGLED_MATCH", "symbol": name,
                           "function_role": role, "old_count": len(olds), "new_count": len(news)})
    old = olds[0] if len(olds) == 1 else None
    new = news[0] if len(news) == 1 else None
    comparison = {"match_basis": "FULL_DEMANGLED_SYMBOL_NAME", "match_discriminator": role,
                  "address_used_for_pairing": False,
                  "presence": "BOTH" if old and new else ("OLD_ONLY" if old else "NEW_ONLY")}
    if old and new:
        delta = int(new["elf_virtual_address"], 16) - int(old["elf_virtual_address"], 16)
        comparison.update({"address_shift": ("+" if delta >= 0 else "-") + f"0x{abs(delta):x}",
                           "size_delta": new["size_bytes"] - old["size_bytes"],
                           "size_equal": new["size_bytes"] == old["size_bytes"],
                           "body_sha256_equal": new["body_sha256"] == old["body_sha256"],
                           "decompile_sha256_equal": new["decompile"].get("sha256") == old["decompile"].get("sha256"),
                           "address_normalized_decompile_equal": new["decompile"].get("address_normalized_sha256") == old["decompile"].get("address_normalized_sha256"),
                           "caller_name_set_equal": caller_names(new) == caller_names(old),
                           "data_target_symbol_set_equal": data_targets(new) == data_targets(old),
                           "body_change": "IDENTICAL" if new["body_sha256"] == old["body_sha256"] else "CHANGED",
                           "normalized_change_class": "RAW_IDENTICAL" if new["body_sha256"] == old["body_sha256"] else ("LAYOUT_OR_RELOCATION_ONLY" if new["decompile"].get("address_normalized_sha256") == old["decompile"].get("address_normalized_sha256") else "POSSIBLE_SEMANTIC_CHANGE")})
    comparisons.append({"demangled_name": name, "function_role": role,
                        "scope": "ADJACENT_OUT_OF_SCOPE" if adjacent in name else "PRIMARY_T3",
                        "matched_terms": matched(name), "old": shape(old), "new": shape(new),
                        "comparison": comparison})
for label, raw in (("old", old_raw), ("new", new_raw)):
    unresolved.extend({"side": label, **row} for row in raw.get("unresolved", []))
unresolved.sort(key=lambda row: json.dumps(row, sort_keys=True))
paired = [row for row in comparisons if row["comparison"]["presence"] == "BOTH"]
implementations = [row for row in paired if row["function_role"] == "IMPLEMENTATION"]
primary = [row for row in implementations if row["scope"] == "PRIMARY_T3"]
adjacent_rows = [row for row in implementations if row["scope"] == "ADJACENT_OUT_OF_SCOPE"]
thunk_rows = [row for row in paired if row["function_role"] == "THUNK"]
qml = qml_evidence({"old": Path(os.environ["OLD_INPUT"]), "new": Path(os.environ["NEW_INPUT"])})
report = {"schema": "clusternav.libbydcluster-native-diff/v1",
          "analysis": {"date": "2026-08-07", "mode": "LOCAL_HEADLESS_READ_ONLY_DELETE_PROJECT",
                       "match_basis": "FULL_DEMANGLED_SYMBOL_NAME_PLUS_THUNK_ROLE_NEVER_SHIFTED_ADDRESS",
                       "tool": {"ghidra": "12.1.2", "java": "Temurin 21.0.12+8",
                                "archive_sha256": os.environ["GHIDRA_ARCHIVE_SHA"],
                                "analyze_headless_sha256": os.environ["HEADLESS_SHA"],
                                "java_binary_sha256": os.environ["JAVA_SHA"],
                                "runtime_setup": "OFFLINE_HASH_VERIFIED_REEXTRACTION_OUTSIDE_GHIDRA_IGNORED_CACHES_PATH",
                                "max_cpu": int(os.environ["MAX_CPU"])},
                       "implementation": {"runner_sha256": sha(Path(os.environ["RUNNER"])),
                                          "exporter_sha256": sha(Path(os.environ["EXPORTER"]))},
                       "normalization": {"json": "UTF-8_SORTED_KEYS_INDENT_2_LF",
                                         "body_hash": "SHA256_CONCATENATED_FUNCTION_BODY_BYTES_IN_ASCENDING_RANGES",
                                         "decompile": "RAW_C_LOCAL_CACHE_ONLY; TRACKED_REPORT_STATUS_HASH_COUNT_METADATA_ONLY",
                                         "address_normalized_decompile": "GHIDRA_GENERATED_FUN_DAT_LAB_PTR_UNK_EXT_SUFFIX_REPLACED"},
                       "tool_runs": [status("old", os.environ["OLD_SHA"]), status("new", os.environ["NEW_SHA"])],
                       "scope_verdict": "COMPLETE_FOR_REQUESTED_NATIVE_SYMBOL_SCOPE",
                       "corpus_verdict": "NOT_EXHAUSTIVE"},
          "inputs": {"old": old_meta, "new": new_meta},
          "relevant_symbols": {"old": old_raw["symbols"], "new": new_raw["symbols"],
                               "nm_symbol_name_sets_equal": old_meta["nm_preindex"]["symbol_name_set_sha256"] == new_meta["nm_preindex"]["symbol_name_set_sha256"],
                               "ghidra_demangled_name_sets_equal": sorted(old_groups) == sorted(new_groups)},
          "functions": comparisons, "unresolved_functions": unresolved,
          "unresolved_linkages": [
              {"linkage": "S1_S2_S3_TRANSPORT_TO_NATIVE_CONSUMERS", "state": "UNPROVEN"},
              {"linkage": "NATIVE_DATASOURCE_TO_QML_RCC_SURFACE", "state": "UNAVAILABLE"}],
          "qml_rcc_linkage": qml,
          "summary": {"paired_function_count": len(paired), "implementation_pair_count": len(implementations),
                      "primary_paired_function_count": len(primary), "thunk_pair_count": len(thunk_rows),
                      "adjacent_out_of_scope_paired_function_count": len(adjacent_rows),
                      "changed_body_count": sum(row["comparison"].get("body_change") == "CHANGED" for row in implementations), "layout_or_relocation_only_count": sum(row["comparison"].get("normalized_change_class") == "LAYOUT_OR_RELOCATION_ONLY" for row in implementations), "possible_semantic_change_count": sum(row["comparison"].get("normalized_change_class") == "POSSIBLE_SEMANTIC_CHANGE" for row in implementations),
                      "identical_body_count": sum(row["comparison"].get("body_change") == "IDENTICAL" for row in implementations),
                      "changed_thunk_body_count": sum(row["comparison"].get("body_change") == "CHANGED" for row in thunk_rows),
                      "unresolved_function_count": len(unresolved),
                      "traffic_signal_status_policy": "ADJACENT_OUT_OF_SCOPE_NOT_PROMOTED",
                      "overall_verdict": "NOT_EXHAUSTIVE"},
          "validation": {"status": "PASS", "schema_assertions": [
              "PINNED_INPUT_SHA256_AND_BUILD_ID", "ELF64_AARCH64_METADATA", "NM_PREINDEX",
              "DEMANGLED_NAME_PAIRING", "BODY_SIZE_HASH_DECOMPILE_CALLERS_DATA_REFS",
              "ADJACENT_SCOPE_LABEL", "SANITIZED_CANONICAL_JSON"]}}
report = clean(report)
for term in terms + [adjacent]:
    if not any(term in row["matched_terms"] for row in report["functions"]):
        raise SystemExit(f"schema validation failed: missing term {term}")
for row in report["functions"]:
    if row["comparison"]["match_basis"] != "FULL_DEMANGLED_SYMBOL_NAME" or row["comparison"]["address_used_for_pairing"] or any(side and "c" in side["decompile"] for side in (row["old"], row["new"])):
        raise SystemExit("schema validation failed: address-based matching or raw decompile body")
    if row["scope"] == "ADJACENT_OUT_OF_SCOPE" and adjacent not in row["demangled_name"]:
        raise SystemExit("schema validation failed: adjacent scope")
payload = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
if home_path.search(payload) or private_ip.search(payload):
    raise SystemExit("schema validation failed: unsanitized path or private IP")
if len(payload.encode("utf-8")) > 4_000_000:
    raise SystemExit("schema validation failed: report exceeds 4 MB")
destination = Path(os.environ["DESTINATION"])
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(payload, encoding="utf-8")

if os.environ["UPDATE_DEPENDENTS"] == "1":
    evidence_path = root / "docs/diagnostics/hud-sign-re/evidence-index.json"
    zero_path = root / "docs/diagnostics/hud-sign-re/zero-hit-report.txt"
    corpus_path = root / "docs/diagnostics/hud-sign-re/corpus-completeness.json"
    evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
    s8 = next(row for row in evidence["evidence"] if row["id"] == "S8")
    citations = []
    for row in primary:
        for side, meta in (("old", old_meta), ("new", new_meta)):
            fn = row[side]
            citations.append({"address": fn["address"], "artifact_sha256": meta["sha256"],
                              "body_sha256": fn["body_sha256"], "path": meta["alias"],
                              "size_bytes": fn["size_bytes"], "symbol": row["demangled_name"],
                              "matched_tokens": sorted(set(row["matched_terms"]))})
    s8.update({"citations": citations, "hit_count": len(primary), "state": "CITED_NATIVE_CONSUMER", "executable": False})
    evidence["available_scope_verdict"] = "COMPLETE_FOR_AVAILABLE_JAVA_AND_REQUESTED_NATIVE_SYMBOL_SCOPE"
    edge = next(row for row in evidence["edges"] if row["id"] == "E-SIGN-CONSUMER")
    edge["state"] = "NATIVE_CONSUMERS_SOURCE_BACKED_TRANSPORT_LINK_UNPROVEN"
    evidence["facts"]["native_analysis"] = {
        "report": "native/libbydcluster-diff.json", "report_sha256": sha(destination),
        "primary_paired_function_count": len(primary), "changed_body_count": report["summary"]["changed_body_count"],
        "qml_rcc_linkage": qml["state"], "traffic_signal_status": "ADJACENT_OUT_OF_SCOPE",
        "transport_to_consumer": "UNPROVEN"}
    evidence_path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    zero_lines = ["ClusterNav HUD/sign RE zero-hit report", "scope=T0-T3 local available corpus", ""]
    for row in sorted(evidence.get("inventory", []), key=lambda item: item["id"]):
        if row.get("zero_hit"):
            zero_lines.append(f"ZERO {row['id']}: required corpus branch")
    for row in sorted(evidence["evidence"], key=lambda item: item["id"]):
        if row["state"].startswith("ZERO_HIT"):
            zero_lines.append(f"ZERO {row['id']}: {row['claim']}")
    zero_lines.extend(["", f"HIT S8: {len(primary)} demangled native implementation pairs; transport linkage remains UNPROVEN.",
                       f"INDICATOR QML/RCC: old={qml['native_resource_indicators']['old']['hit_count']} new={qml['native_resource_indicators']['new']['hit_count']}; no extractable asset/direct binding.",
                       "ADJACENT trafficSignalStatus: OUT_OF_SCOPE; no sign relevance proven.",
                       "", "A zero hit is evidence of absence only within the named available roots, not proof of platform absence."])
    zero_path.write_text("\n".join(zero_lines) + "\n", encoding="utf-8")
    corpus = json.loads(corpus_path.read_text(encoding="utf-8"))
    corpus["corpus"]["available_scope_verdict"] = "COMPLETE_FOR_AVAILABLE_JAVA_AND_REQUESTED_NATIVE_SYMBOL_SCOPE"
    for row in corpus["corpus"]["availability"]:
        if "cluster-old-native" in row.get("aliases", []):
            row["state"] = "AVAILABLE_HASHED_AND_REQUESTED_NATIVE_SYMBOLS_ANALYZED"
    authorized = corpus["scope"]["authorized"]
    if "T3" not in authorized:
        authorized.append("T3")
    corpus["scope"]["authorized"] = sorted(authorized)
    corpus["evidence"].update({"evidence_index_sha256": sha(evidence_path),
                               "zero_hit_report_sha256": sha(zero_path),
                               "native_report_sha256": sha(destination),
                               "native_scope_verdict": "COMPLETE_FOR_REQUESTED_NATIVE_SYMBOL_SCOPE",
                               "native_primary_function_pairs": len(primary),
                               "native_unresolved_functions": len(unresolved),
                               "qml_rcc_linkage": "UNAVAILABLE"})
    corpus_path.write_text(json.dumps(corpus, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}

analyze_pair() {
  local directory="$1"
  mkdir -p "$directory"
  run_one old "$OLD_INPUT" "$OLD_SHA" "${directory}/old.json"
  run_one new "$NEW_INPUT" "$NEW_SHA" "${directory}/new.json"
}

preflight
if [[ "$MODE" == "--verify-determinism" ]]; then
  PASS1="${RUN_ROOT}/determinism-pass-1"
  PASS2="${RUN_ROOT}/determinism-pass-2"
  rm -rf "$PASS1" "$PASS2"
  analyze_pair "$PASS1"
  build_report "${PASS1}/old.json" "${PASS1}/new.json" "${PASS1}/report.json" 0
  analyze_pair "$PASS2"
  build_report "${PASS2}/old.json" "${PASS2}/new.json" "${PASS2}/report.json" 0
  cmp -s "${PASS1}/report.json" "${PASS2}/report.json" || {
    echo "determinism failure: normalized native reports differ" >&2
    exit 1
  }
  cp "${PASS2}/report.json" "$REPORT"
  build_report "${PASS2}/old.json" "${PASS2}/new.json" "$REPORT" 1
  echo "native analysis PASS: two normalized runs are byte-identical"
else
  PASS="${RUN_ROOT}/latest"
  rm -rf "$PASS"
  analyze_pair "$PASS"
  build_report "${PASS}/old.json" "${PASS}/new.json" "$REPORT" 1
  echo "native analysis PASS: $REPORT"
fi
python3 "${ROOT}/scripts/re/verify-reproducibility.py" "$REPORT"
