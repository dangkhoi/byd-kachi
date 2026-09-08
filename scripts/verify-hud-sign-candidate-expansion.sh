#!/bin/bash -p
set -euo pipefail

# Kernel-dispatched privileged startup ignores BASH_ENV/ENV, imported shell functions,
# and environment-provided SHELLOPTS/BASHOPTS/CDPATH/GLOBIGNORE before line 1.
# The body then runs under a second privileged Bash with an explicit empty environment,
# so ignored BASH_FUNC_* and option entries cannot propagate to any verifier child.

if (( $# != 0 )); then
  printf 'usage: %s\n' "${0##*/}" >&2
  exit 64
fi

readonly BOOTSTRAP_CALLER_DIRECTORY="$(pwd -P)"
readonly BOOTSTRAP_SCRIPT_SOURCE="${BASH_SOURCE[0]}"
BOOTSTRAP_ENV=(
  /usr/bin/env -i
  "CLUSTERNAV_BOOTSTRAP_CALLER_DIRECTORY=$BOOTSTRAP_CALLER_DIRECTORY"
  "CLUSTERNAV_BOOTSTRAP_SCRIPT_SOURCE=$BOOTSTRAP_SCRIPT_SOURCE"
)
if [[ "${CLUSTERNAV_EXPANSION_GATE+set}" == set ]]; then
  BOOTSTRAP_ENV+=("CLUSTERNAV_EXPANSION_GATE=$CLUSTERNAV_EXPANSION_GATE")
fi
readonly -a BOOTSTRAP_ENV
exec "${BOOTSTRAP_ENV[@]}" /bin/bash -p -s <<'CLUSTERNAV_VERIFIER_BODY'
set -euo pipefail

fail() {
  printf 'verification failed: %s\n' "$1" >&2
  exit 1
}

# Discard caller-controlled lookup, temporary, proxy, classpath and tool-option state.
export PATH="/usr/bin:/bin"
unset TMPDIR TEMP TMP CDPATH GLOBIGNORE
unset HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY FTP_PROXY SOCKS_PROXY CGI_HTTP_PROXY RSYNC_PROXY
unset http_proxy https_proxy all_proxy no_proxy ftp_proxy socks_proxy rsync_proxy GIT_PROXY_COMMAND PIP_PROXY
unset CLASSPATH JAVA_HOME JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS JAVA_OPTS GRADLE_OPTS KOTLIN_OPTS MAVEN_OPTS
unset GRADLE_USER_HOME PYTHONHOME PYTHONPATH PYTHONSTARTUP BASH_ENV ENV LD_PRELOAD DYLD_INSERT_LIBRARIES
export LC_ALL=C LANG=C

SELECTED_GATE=""
if [[ "${CLUSTERNAV_EXPANSION_GATE+set}" == set ]]; then
  SELECTED_GATE="$CLUSTERNAV_EXPANSION_GATE"
  unset CLUSTERNAV_EXPANSION_GATE
  case "$SELECTED_GATE" in
    GATE-X-O1|GATE-X-O2|GATE-X-O3|GATE-X-O4|GATE-X-O5|GATE-X-O6|GATE-X-O7|GATE-X-O8|GATE-X-O9|GATE-X-O10|GATE-X-O12) ;;
    "") fail "CLUSTERNAV_EXPANSION_GATE must not be empty" ;;
    GATE-X-O11) fail "GATE-X-O11 is the plain no-selector full verifier" ;;
    *) fail "unknown CLUSTERNAV_EXPANSION_GATE: $SELECTED_GATE" ;;
  esac
fi
readonly SELECTED_GATE

require_no_symlink_components() {
  local path="$1" remainder component cursor="/"
  [[ "$path" == /* ]] || fail "path must be absolute: $path"
  remainder="${path#/}"
  while [[ -n "$remainder" ]]; do
    component="${remainder%%/*}"
    if [[ "$remainder" == */* ]]; then remainder="${remainder#*/}"; else remainder=""; fi
    [[ -z "$component" || "$component" == "." ]] && continue
    [[ "$component" != ".." ]] || fail "parent traversal is forbidden: $path"
    cursor="${cursor%/}/$component"
    [[ ! -L "$cursor" ]] || fail "symbolic link component is forbidden: $cursor"
  done
}

readonly CALLER_DIRECTORY="$CLUSTERNAV_BOOTSTRAP_CALLER_DIRECTORY"
readonly SCRIPT_SOURCE="$CLUSTERNAV_BOOTSTRAP_SCRIPT_SOURCE"
unset CLUSTERNAV_BOOTSTRAP_CALLER_DIRECTORY CLUSTERNAV_BOOTSTRAP_SCRIPT_SOURCE
if [[ "$SCRIPT_SOURCE" == /* ]]; then INVOKED_SOURCE="$SCRIPT_SOURCE"; else INVOKED_SOURCE="$CALLER_DIRECTORY/$SCRIPT_SOURCE"; fi
readonly INVOKED_SOURCE
require_no_symlink_components "$INVOKED_SOURCE"
if [[ "$SCRIPT_SOURCE" == */* ]]; then SCRIPT_PARENT="${SCRIPT_SOURCE%/*}"; else SCRIPT_PARENT="."; fi
SCRIPT_DIR="$(cd -- "$SCRIPT_PARENT" && pwd -P)"
ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
readonly SCRIPT_DIR ROOT
readonly EXPECTED_SCRIPT="$ROOT/scripts/verify-hud-sign-candidate-expansion.sh"
require_no_symlink_components "$ROOT"
require_no_symlink_components "$EXPECTED_SCRIPT"
[[ "$SCRIPT_DIR/${SCRIPT_SOURCE##*/}" == "$EXPECTED_SCRIPT" ]] || fail "unsafe script location"
[[ -f "$EXPECTED_SCRIPT" && -x "$EXPECTED_SCRIPT" && ! -L "$EXPECTED_SCRIPT" ]] || fail "canonical verifier must be a regular executable"
[[ -f "$ROOT/settings.gradle.kts" && ! -L "$ROOT/settings.gradle.kts" ]] || fail "project root marker is missing"

resolve_regular_executable() {
  local candidate="$1" parent base physical resolved
  [[ "$candidate" == /* && -f "$candidate" && -x "$candidate" ]] || return 1
  parent="${candidate%/*}"; base="${candidate##*/}"
  physical="$(cd -- "$parent" 2>/dev/null && pwd -P)" || return 1
  resolved="$physical/$base"
  [[ -f "$resolved" && -x "$resolved" && ! -L "$resolved" ]] || return 1
  require_no_symlink_components "$resolved"
  printf '%s\n' "$resolved"
}

select_java17() {
  local discovered="" candidate resolved settings
  if [[ -x /usr/libexec/java_home && -f /usr/libexec/java_home && ! -L /usr/libexec/java_home ]]; then
    discovered="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  fi
  for candidate in \
    "${discovered:+$discovered/bin/java}" \
    /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java \
    /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java \
    /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java \
    /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java \
    /usr/lib/jvm/java-17-openjdk-amd64/bin/java \
    /usr/lib/jvm/java-17-openjdk/bin/java \
    /usr/lib/jvm/temurin-17-jdk-amd64/bin/java; do
    [[ -n "$candidate" ]] || continue
    resolved="$(resolve_regular_executable "$candidate" 2>/dev/null || true)"
    [[ -n "$resolved" ]] || continue
    settings="$($resolved -XshowSettings:properties -version 2>&1 || true)"
    if [[ "$settings" =~ java\.specification\.version[[:space:]]*=[[:space:]]*17([^0-9]|$) ]]; then
      printf '%s\n' "$resolved"
      return 0
    fi
  done
  return 1
}

select_python3() {
  local candidate resolved
  for candidate in /usr/bin/python3 /opt/homebrew/opt/python@3.14/bin/python3.14 /opt/homebrew/bin/python3 /usr/local/bin/python3; do
    resolved="$(resolve_regular_executable "$candidate" 2>/dev/null || true)"
    [[ -n "$resolved" ]] || continue
    if "$resolved" -I -S -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 9) and sys.version_info < (4, 0) else 1)' 2>/dev/null; then
      printf '%s\n' "$resolved"
      return 0
    fi
  done
  return 1
}

JAVA_BIN="$(select_java17 || true)"
PYTHON_BIN="$(select_python3 || true)"
[[ -n "$JAVA_BIN" ]] || fail "trusted local JDK 17 is unavailable"
[[ -n "$PYTHON_BIN" ]] || fail "trusted local Python 3 is unavailable"
readonly JAVA_BIN PYTHON_BIN
JAVA_HOME="${JAVA_BIN%/bin/java}"
TRUSTED_HOME="$($PYTHON_BIN -I -S -c 'import os,pwd; print(pwd.getpwuid(os.getuid()).pw_dir)')"
[[ "$TRUSTED_HOME" == /* && -d "$TRUSTED_HOME" ]] || fail "trusted account home is unavailable"
TRUSTED_HOME="$(cd -- "$TRUSTED_HOME" && pwd -P)"
require_no_symlink_components "$TRUSTED_HOME"
readonly TRUSTED_HOME
export JAVA_HOME HOME="$TRUSTED_HOME" GRADLE_USER_HOME="$TRUSTED_HOME/.gradle"
export PATH="${JAVA_BIN%/*}:${PYTHON_BIN%/*}:/usr/bin:/bin"
export CLUSTERNAV_OFFCAR_ONLY=1 CLUSTERNAV_ALLOW_VEHICLE=0 CLUSTERNAV_ALLOW_NETWORK=0 GRADLE_OFFLINE=true

readonly GRADLEW="$ROOT/gradlew"
readonly WRAPPER_JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
readonly WRAPPER_PROPERTIES="$ROOT/gradle/wrapper/gradle-wrapper.properties"
sha256_regular() {
  "$PYTHON_BIN" -I -S -c 'import hashlib,os,stat,sys
p=sys.argv[1]; flags=os.O_RDONLY|getattr(os,"O_NOFOLLOW",0); fd=os.open(p,flags)
try:
 s=os.fstat(fd); assert stat.S_ISREG(s.st_mode); h=hashlib.sha256()
 while True:
  b=os.read(fd,1048576)
  if not b: break
  h.update(b)
 print(h.hexdigest())
finally: os.close(fd)' "$1"
}
verify_pinned_file() {
  local path="$1" expected="$2"
  require_no_symlink_components "$path"
  [[ -f "$path" && ! -L "$path" ]] || fail "pinned repository file is not regular: $path"
  [[ "$(sha256_regular "$path")" == "$expected" ]] || fail "pinned repository identity mismatch: $path"
}
verify_pinned_file "$GRADLEW" "a5a5c199ba02189ae8c46a334223371a20599d9c298ef65e7540ede4a3f72d59"
verify_pinned_file "$WRAPPER_JAR" "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
verify_pinned_file "$WRAPPER_PROPERTIES" "556f4aa5f360e35fca77b010b306307765d4f4915a82a612035cd5cfb7a587cb"
[[ -x "$GRADLEW" ]] || fail "pinned Gradle wrapper is not executable"

readonly TEMP_BASE_LOGICAL="/tmp"
TEMP_BASE="$(cd -- "$TEMP_BASE_LOGICAL" && pwd -P)"
readonly TEMP_BASE
[[ -d "$TEMP_BASE" && ! -L "$TEMP_BASE" ]] || fail "fixed /tmp base is not a regular directory"
require_no_symlink_components "$TEMP_BASE"

cd -- "$ROOT"
umask 077
readonly OUTPUT_DIRECTORY="$ROOT/docs/diagnostics/hud-sign-re/expansion"
require_no_symlink_components "$OUTPUT_DIRECTORY"
[[ -d "$OUTPUT_DIRECTORY" && ! -L "$OUTPUT_DIRECTORY" ]] || fail "canonical output directory is unsafe"
TEMP_DIRECTORY=""
BEFORE_DIRECTORY=""
cleanup() { if [[ -n "$TEMP_DIRECTORY" ]]; then /bin/rm -rf -- "$TEMP_DIRECTORY"; fi; }
trap cleanup EXIT

# PRIVACY_SCANNER_BEGIN
run_privacy_scan() {
  "$PYTHON_BIN" -I - "$1" <<'PY_PRIVACY'
# PRIVACY_SCANNER_PROGRAM_BEGIN
import ipaddress, json, os, re, stat, sys
root = os.path.abspath(sys.argv[1])
fixed_urls = {
    "https://clusternav.invalid/schema/result-ledger.schema.json",
    "https://json-schema.org/draft/2020-12/schema",
}
authorized = {"Đăng Khôi · dangkhoi"}
sha = re.compile(r"^[0-9a-f]{64}$")
candidate_id = re.compile(
    r"^CAND-(?:H|S|NATIVE|PROVIDER)-[0-9]{3}-([A-Z0-9][A-Z0-9-]{0,63})@(?:[1-9][0-9]{0,8}|1[0-9]{9}|20[0-9]{8}|21[0-3][0-9]{7}|214[0-6][0-9]{6}|2147[0-3][0-9]{5}|21474[0-7][0-9]{4}|214748[0-2][0-9]{3}|2147483[0-5][0-9]{2}|21474836[0-3][0-9]|214748364[0-7])$"
)
typed_id = re.compile(
    r"^(?:(?:ALIAS|ARTIFACT|BLOCKER|COMPONENT|CONFIG|CONSUMER|DIMENSION|FACT|HYP|PARAM|PERMISSION|PROFILE|PROVIDER|PRUNE|REASON|RENDERER|RULE|SEL|SENDER|TOKEN|TOOL|TRANSPORT|VALUE)-[A-Z0-9][A-Z0-9-]{0,63}"
    r"|EVENT-[0-9]{6}|GATE-X-O(?:[1-9]|1[0-2])|OBS-(?:D-H0|M[1-4])-[A-Z0-9][A-Z0-9-]{0,63}|OP-(?:READ|MUTATE|CLEAR|INVERSE|RESTORE)-[A-Z0-9][A-Z0-9-]{0,63}"
    r"|PROBE-(?:READ|LIST)-[A-Z0-9][A-Z0-9-]{0,63}|QRY-C(?:0[1-9]|1[0-2])-[A-Z0-9][A-Z0-9-]{0,63}|REQ-X(?:[1-9]|1[0-8])|RESULT-(?:D-H0|D-M[1-4]|P-M[1-4])-[0-9]{4}"
    r"|ROW-[0-9]{4}-[A-Z0-9][A-Z0-9-]{0,63}|SESSION-[0-9A-F]{16}|TASK-X[0-5]|VERSION-[A-Z0-9][A-Z0-9.-]{0,63}"
    r"|CAND-(?:H|S|NATIVE|PROVIDER)-[0-9]{3}-[A-Z0-9][A-Z0-9-]{0,63}@(?:[1-9][0-9]{0,8}|1[0-9]{9}|20[0-9]{8}|21[0-3][0-9]{7}|214[0-6][0-9]{6}|2147[0-3][0-9]{5}|21474[0-7][0-9]{4}|214748[0-2][0-9]{3}|2147483[0-5][0-9]{2}|21474836[0-3][0-9]|214748364[0-7])"
    r"|HIT-C(?:0[1-9]|1[0-2])-QRY-C(?:0[1-9]|1[0-2])-[A-Z0-9][A-Z0-9-]{0,63}-A[0-9a-f]{12}-S[0-9a-f]{12}-Q[0-9a-f]{12}-L[0-9a-f]{12}-T[0-9a-f]{12}|H[0-9]{1,3}|S[0-9]{1,3})$"
)
sensitive_id_markers = tuple(tuple(value.split("-")) for value in (
    "PASSWORD", "PASSWD", "SECRET", "CREDENTIAL", "AUTHORIZATION", "BEARER", "TOKEN",
    "API-KEY", "PRIVATE-KEY", "ACCESS-TOKEN", "REFRESH-TOKEN",
))
sensitive_id_prefixes = ("PASSWORD", "PASSWD", "SECRET", "CREDENTIAL", "AUTHORIZATION", "BEARER", "APIKEY", "PRIVATEKEY", "ACCESSTOKEN", "REFRESHTOKEN")
def typed_payload(value):
    candidate = candidate_id.fullmatch(value)
    if candidate: return candidate.group(1)
    return value.split("-", 1)[1] if typed_id.fullmatch(value) and "-" in value else value

def sensitive_typed_id(value):
    if not typed_id.fullmatch(value): return False
    parts = [part.upper() for part in re.split(r"[-.@]", typed_payload(value)) if part]
    for index, part in enumerate(parts):
        if any(parts[index:index + len(marker)] == list(marker) and index + len(marker) < len(parts) for marker in sensitive_id_markers): return True
        if any(part.startswith(marker) and len(part) > len(marker) for marker in sensitive_id_prefixes): return True
    return False
blocked_keys = {
    "vin", "vehicleidentificationnumber", "serial", "serialnumber", "gps", "latitude", "longitude",
    "coordinates", "ip", "ipaddress", "raw", "rawdump", "rawdata", "rawpayload", "rawbytes",
    "sourceline", "sourcepath", "sourcecode", "sourcetext", "sourcebody", "sourcedump", "decompiled",
    "decompiledbody", "decompiledsource", "decompiledcode", "decompiledtext", "blob",
}
sensitive_keys = {"apikey", "secret", "token", "password", "passwd", "credential", "authorization", "accesstoken", "refreshtoken", "privatekey"}
patterns = [
    ("credential", re.compile(r"(?i)(?:AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|gh[pors]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,}|glpat-[A-Za-z0-9_-]{20,}|sk-(?:proj-)?[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+)")),
    ("credential marker", re.compile(r"(?i)\b(?:api[_-]?key|secret|token|password|passwd|credential|authorization|bearer)(?:\s*[:=]\s*|[-_])[\"']?[A-Za-z0-9_./+=-]{8,}")),
    ("serial/raw/source/decompiled marker", re.compile(r"(?i)\b(?:serial(?:number|no)?|raw(?:dump|data|payload|bytes)?|source(?:[-_ ]?(?:line|path|code|text|body|dump))|decompiled(?:body|source|code|text)?|gps|latitude|longitude|ipaddress|blob)\b")),
    ("private path", re.compile(r"(?i)(?:/(?:Users|home|private|tmp|var)/[^\s\"'<>]+|[A-Za-z]:\\Users\\[^\r\n\"'<>]+|\\\\[A-Za-z0-9_.-]+\\)")),
    ("URI", re.compile(r"(?i)\b[a-z][a-z0-9+.-]{1,20}://[^\s\"'<>]+")),
    ("email/PII", re.compile(r"(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b")),
    ("name/PII", re.compile(r"\b(?:full[_ -]?name|name)\s*[:=]\s*[\"']?[A-Z][a-z]+(?:[ -][A-Z][a-z]+)+")),
    ("phone/PII", re.compile(r"(?<![A-Za-z0-9])(?:\+?[0-9][ ()-]*){8,15}(?![A-Za-z0-9])")),
    ("government ID/PII", re.compile(r"(?<![A-Za-z0-9])[0-9]{9,12}(?![A-Za-z0-9])")),
    ("internal host", re.compile(r"(?i)\b(?:[A-Za-z0-9-]+\.)*(?:internal|intranet|corp|private|cluster\.local|svc\.local)(?:\.[A-Za-z0-9-]+)+\b")),
    ("GPS coordinates", re.compile(r"(?<![0-9])[-+]?(?:[0-8]?[0-9](?:\.[0-9]+)|90(?:\.0+)?)\s*[,;]\s*[-+]?(?:1[0-7][0-9](?:\.[0-9]+)|[0-9]?[0-9](?:\.[0-9]+)|180(?:\.0+)?)(?![0-9])")),
    ("GPS coordinates", re.compile(r"(?i)\b(?:lat(?:itude)?|lon(?:gitude)?)\s*[:=]\s*[-+]?[0-9]{1,3}(?:\.[0-9]+)?")),
    ("VIN/check-digit candidate", re.compile(r"(?<![A-Z0-9])[A-HJ-NPR-Z0-9]{17}(?![A-Z0-9])")),
]

def text_tokens(text):
    return re.findall(r"(?<![A-Za-z0-9@.-])[A-Za-z0-9][A-Za-z0-9@.-]*(?![A-Za-z0-9@.-])", text)

def scrub(text):
    for value in fixed_urls | authorized:
        text = text.replace(value, "")
    text = re.sub(r"\b[0-9a-f]{64}\b", "", text)
    for token in set(text_tokens(text)):
        if typed_id.fullmatch(token): text = text.replace(token, "")
    return text

def inspect_semantics(text, label, schema_pattern=False):
    for name, pattern in patterns:
        if schema_pattern and name in {"phone/PII", "government ID/PII"}: continue
        if pattern.search(text): raise ValueError(f"{label}: {name}")
    for token in re.findall(r"(?<![0-9A-Fa-f:])[0-9A-Fa-f:]{2,}(?![0-9A-Fa-f:])", text):
        if token.count(":") >= 2:
            try: ipaddress.ip_address(token)
            except ValueError: pass
            else: raise ValueError(f"{label}: IP address")
    for token in re.findall(r"(?<![0-9.])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![0-9.])", text):
        try: ipaddress.ip_address(token)
        except ValueError: continue
        raise ValueError(f"{label}: IP address")
    for token in re.findall(r"(?<![0-9])[0-9][0-9 -]{11,21}[0-9](?![0-9])", text):
        digits = re.sub(r"\D", "", token)
        if 13 <= len(digits) <= 19 and sum((int(d) * (2 if (len(digits)-i) % 2 == 0 else 1) // 10 + (int(d) * (2 if (len(digits)-i) % 2 == 0 else 1)) % 10) for i, d in enumerate(digits)) % 10 == 0:
            raise ValueError(f"{label}: payment-card/PII")

def semantic_check(text, label, schema_pattern=False):
    for token in set(text_tokens(text)):
        if typed_id.fullmatch(token):
            if sensitive_typed_id(token): raise ValueError(f"{label}: sensitive typed ID")
            inspect_semantics(typed_payload(token), label)
    inspect_semantics(scrub(text), label, schema_pattern)

def nullable_hash_path(path):
    return ((len(path) == 4 and path[0] == "corpus-coverage.json" and path[1] == "entries" and isinstance(path[2], int) and path[3] == "artifactSha256") or
        (len(path) == 4 and path[0] == "candidate-registry.json" and path[1] == "candidates" and isinstance(path[2], int) and path[3] == "predecessorCandidateSha256") or
        (len(path) == 4 and path[0] == "candidate-registry.json" and path[1] == "history" and isinstance(path[2], int) and path[3] == "predecessorRegistryRevisionSha256"))

def walk(value, label, key="", schema_document=False, path=()):
    if isinstance(value, dict):
        declaration_map = schema_document and key in {"$defs", "properties"}
        for child_key, child in value.items():
            child_path = path + (child_key,)
            normalized = re.sub(r"[^a-z0-9]", "", child_key.lower())
            if not declaration_map:
                if normalized in blocked_keys:
                    raise ValueError(f"{label}: privacy-forbidden key {child_key}")
                if child_key.lower().endswith("sha256"):
                    if child is None and nullable_hash_path(child_path): continue
                    if not isinstance(child, str) or not sha.fullmatch(child):
                        raise ValueError(f"{label}: invalid hash-typed key {child_key}")
                    continue
                if normalized in sensitive_keys and not (isinstance(child, str) and sha.fullmatch(child)):
                    raise ValueError(f"{label}: credential-bearing key {child_key}")
            walk(child, f"{label}.{child_key}", child_key, schema_document, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value): walk(child, f"{label}[{index}]", key, schema_document, path + (index,))
    elif isinstance(value, str):
        if sha.fullmatch(value) or value in fixed_urls or value in authorized:
            return
        semantic_check(value, label, schema_document and key == "pattern")

def read_regular(path):
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(path, flags)
    try:
        before = os.fstat(fd)
        if not stat.S_ISREG(before.st_mode): raise ValueError(f"{path}: non-regular output")
        chunks = []
        while True:
            chunk = os.read(fd, 1048576)
            if not chunk: break
            chunks.append(chunk)
        after = os.fstat(fd)
        if (before.st_dev, before.st_ino) != (after.st_dev, after.st_ino): raise ValueError(f"{path}: replaced while reading")
        return b"".join(chunks)
    finally: os.close(fd)

if stat.S_ISLNK(os.lstat(root).st_mode) or not stat.S_ISDIR(os.lstat(root).st_mode):
    raise ValueError("privacy root must be a no-follow directory")
for current, dirs, names in os.walk(root, topdown=True, followlinks=False):
    entries = {entry.name: entry for entry in os.scandir(current)}
    for name in dirs + names:
        if entries[name].is_symlink(): raise ValueError(f"{entries[name].path}: symbolic link output")
    for name in names:
        path = os.path.join(current, name); raw = read_regular(path)
        text = raw.decode("utf-8", "strict")
        if name.endswith(".json"):
            document = json.loads(text)
            schema_document = isinstance(document, dict) and "$schema" in document and "$defs" in document
            walk(document, os.path.relpath(path, root), schema_document=schema_document, path=(name,))
        else: semantic_check(text, os.path.relpath(path, root))
# PRIVACY_SCANNER_PROGRAM_END
PY_PRIVACY
}
# PRIVACY_SCANNER_END

validate_outputs() {
  local expected="$1/expected-files.txt" actual="$1/actual-files.txt" path
  printf '%s\n' \
    candidate-diff.json candidate-expansion-report.html candidate-registry.json corpus-coverage.json \
    evidence-map.json legacy-baseline.json pack-manifest.json result-ledger.schema.json traceability.json \
    vehicle-session-checklist.html vehicle-session-plan.json vehicle-session-plan.txt >"$expected"
  : >"$actual"
  shopt -s nullglob dotglob
  for path in "$OUTPUT_DIRECTORY"/*; do
    [[ -f "$path" && ! -L "$path" ]] || fail "output allowlist/no-follow mismatch"
    printf '%s\n' "${path##*/}" >>"$actual"
  done
  shopt -u nullglob dotglob
  /usr/bin/sort -o "$actual" "$actual"
  /usr/bin/diff -u "$expected" "$actual" >/dev/null || fail "output allowlist mismatch"
  run_privacy_scan "$OUTPUT_DIRECTORY" || fail "semantic privacy fence failed"
}

begin_output_checks() {
  TEMP_DIRECTORY="$(/usr/bin/mktemp -d "$TEMP_BASE/clusternav-expansion-verify.XXXXXX")"
  BEFORE_DIRECTORY="$TEMP_DIRECTORY/before"
  readonly TEMP_DIRECTORY BEFORE_DIRECTORY
  require_no_symlink_components "$TEMP_DIRECTORY"
  [[ -d "$TEMP_DIRECTORY" && ! -L "$TEMP_DIRECTORY" ]] || fail "unsafe verifier temporary directory"
  /bin/mkdir -p -- "$BEFORE_DIRECTORY"
  validate_outputs "$TEMP_DIRECTORY"
  /bin/cp -Rp -- "$OUTPUT_DIRECTORY/." "$BEFORE_DIRECTORY/"
}

finish_output_checks() {
  validate_outputs "$TEMP_DIRECTORY"
  /usr/bin/diff -ru "$BEFORE_DIRECTORY" "$OUTPUT_DIRECTORY" >/dev/null || fail "checked generation diff is not empty"
}

run_python_coverage_test() {
  "$PYTHON_BIN" -I -m unittest discover -s scripts/re/tests -p 'test_expand_candidate_coverage.py'
}

verify_offline_gradle_distribution() {
  local cache marker launcher found=0
  local -a caches
  shopt -s nullglob
  caches=("$GRADLE_USER_HOME/wrapper/dists/gradle-9.6.1-bin"/*)
  shopt -u nullglob
  for cache in "${caches[@]}"; do
    marker="$cache/gradle-9.6.1-bin.zip.ok"
    launcher="$cache/gradle-9.6.1/bin/gradle"
    if [[ -f "$marker" && ! -L "$marker" && -f "$launcher" && -x "$launcher" && ! -L "$launcher" ]]; then
      require_no_symlink_components "$marker"; require_no_symlink_components "$launcher"; found=1
    fi
  done
  (( found == 1 )) || fail "pinned Gradle 9.6.1 offline distribution is unavailable"
}

run_gradle_test() {
  verify_offline_gradle_distribution
  local gradle_log test_name
  local -a gradle_args=(--offline --no-daemon --rerun-tasks --no-build-cache --console=plain :offcar-planner:test)
  for test_name in "$@"; do gradle_args+=(--tests "$test_name"); done
  gradle_log="$(/usr/bin/mktemp "$TEMP_BASE/clusternav-gradle.XXXXXX")"
  if ! "$GRADLEW" "${gradle_args[@]}" 2>&1 | /usr/bin/tee "$gradle_log"; then
    /bin/rm -f -- "$gradle_log"; fail "isolated Gradle test execution failed"
  fi
  if ! /usr/bin/grep -Fxq '> Task :offcar-planner:test' "$gradle_log" ||
      /usr/bin/grep -Eq '^> Task :offcar-planner:test (FROM-CACHE|UP-TO-DATE|SKIPPED)$' "$gradle_log"; then
    /bin/rm -f -- "$gradle_log"; fail "Gradle test task was not freshly executed"
  fi
  /bin/rm -f -- "$gradle_log"
}

verify_o8_test_count() {
  "$PYTHON_BIN" -I -S - "$ROOT/offcar-planner/build/test-results/test" <<'PY_O8'
import os, stat, sys, xml.etree.ElementTree as ET
base = sys.argv[1]
expected = {
    "com.byd.clusternav.offcar.SameSessionQuarantineTest": 3,
    "com.byd.clusternav.offcar.LedgerSemanticValidationTest": 6,
}
total = 0
for class_name, count in expected.items():
    path = os.path.join(base, "TEST-" + class_name + ".xml")
    fd = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    try:
        before = os.fstat(fd)
        if not stat.S_ISREG(before.st_mode): raise ValueError("O8 result is not regular")
        chunks = []
        while True:
            chunk = os.read(fd, 1048576)
            if not chunk: break
            chunks.append(chunk)
        after = os.fstat(fd)
        if (before.st_dev, before.st_ino) != (after.st_dev, after.st_ino): raise ValueError("O8 result changed while reading")
    finally: os.close(fd)
    suite = ET.fromstring(b"".join(chunks))
    if int(suite.attrib.get("tests", "-1")) != count: raise ValueError(f"{class_name}: expected {count} tests")
    total += count
if total != 9: raise ValueError("GATE-X-O8 must select exactly 9 tests")
PY_O8
}

run_selected_gate() {
  case "$SELECTED_GATE" in
    GATE-X-O1) run_gradle_test "com.byd.clusternav.offcar.LegacyBaselineIdentityTest" ;;
    GATE-X-O2) run_gradle_test "com.byd.clusternav.offcar.ExpansionDeterminismTest" ;;
    GATE-X-O3) run_python_coverage_test ;;
    GATE-X-O4) run_gradle_test "com.byd.clusternav.offcar.ExpansionPromotionTest" ;;
    GATE-X-O5) run_gradle_test "com.byd.clusternav.offcar.DerivationClosureTest" ;;
    GATE-X-O6) run_gradle_test "com.byd.clusternav.offcar.ExpansionPromotionTest" ;;
    GATE-X-O7) run_gradle_test "com.byd.clusternav.offcar.AdaptivePruningTest" ;;
    GATE-X-O8) run_gradle_test "com.byd.clusternav.offcar.SameSessionQuarantineTest" "com.byd.clusternav.offcar.LedgerSemanticValidationTest"; verify_o8_test_count ;;
    GATE-X-O9) run_gradle_test "com.byd.clusternav.offcar.ExpansionDeterminismTest" ;;
    GATE-X-O10) run_gradle_test "com.byd.clusternav.offcar.ExpansionTransportFenceTest" ;;
    GATE-X-O12) run_gradle_test "com.byd.clusternav.offcar.ExpansionTraceabilityTest" ;;
    *) fail "internal selector dispatch failure" ;;
  esac
}

if [[ -n "$SELECTED_GATE" ]]; then
  if [[ "$SELECTED_GATE" == "GATE-X-O12" ]]; then begin_output_checks; fi
  run_selected_gate
  if [[ "$SELECTED_GATE" == "GATE-X-O12" ]]; then finish_output_checks; fi
  printf 'HUD/sign candidate expansion %s passed.\n' "$SELECTED_GATE"
else
  begin_output_checks
  run_python_coverage_test
  run_gradle_test "com.byd.clusternav.offcar.ExpansionTransportFenceTest"
  run_gradle_test "com.byd.clusternav.offcar.ExpansionTraceabilityTest"
  run_gradle_test
  finish_output_checks
  printf 'HUD/sign candidate expansion verification passed: 12 outputs, offline tests, semantic privacy and byte diff clean.\n'
fi
CLUSTERNAV_VERIFIER_BODY