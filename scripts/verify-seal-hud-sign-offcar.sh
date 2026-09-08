#!/usr/bin/env bash
set -Eeuo pipefail

export LC_ALL=C
export TZ=UTC
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PARENT="$(cd "$ROOT/.." && pwd -P)"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/clusternav-seal-verify.XXXXXX")"
trap 'status=$?; rm -rf "$TMP_ROOT"; if [[ $status -ne 0 ]]; then printf "NEEDS_CHANGES verifier_exit=%s\n" "$status" >&2; fi; exit "$status"' EXIT
cd "$ROOT"

[[ $# -eq 0 ]] || { printf 'usage: %s\n' "$(basename "$0")" >&2; exit 64; }
JAVA_HOME="/opt/homebrew/opt/openjdk@17"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT
[[ -x "$JAVA_HOME/bin/java" ]] || { echo 'missing pinned JDK 17' >&2; exit 2; }
[[ -d "$ANDROID_HOME" ]] || { echo 'missing Android SDK' >&2; exit 2; }

sha256_file() { shasum -a 256 "$1" | awk '{print $1}'; }
require_hash() {
  local path="$1" expected="$2" label="$3" actual
  [[ -f "$path" && ! -L "$path" ]] || { printf 'missing %s\n' "$label" >&2; exit 2; }
  actual="$(sha256_file "$path")"
  [[ "$actual" == "$expected" ]] || {
    printf '%s hash mismatch: expected=%s actual=%s\n' "$label" "$expected" "$actual" >&2
    exit 2
  }
}

# O1 — rehash every selected local corpus input; no artifact leaves this machine.
CORPUS_REPEAT="$TMP_ROOT/corpus.json"
python3 scripts/re/hash-corpus.py \
  --artifact "amap-apk=$PARENT/apks/AmapService.apk" \
  --artifact "amap-java=$PARENT/jadx-amap2/sources" \
  --artifact "carsettings-apk=$PARENT/carsettings-apk/CarSetting.apk" \
  --artifact "carsettings-dex=$PARENT/carsettings-apk/dex" \
  --artifact "carsettings-java=$PARENT/carsettings-apk/jadx-carsettings/sources" \
  --artifact "cluster-new-native=$PARENT/firmware/fw-2602-diff/cmp/libBydCluster_NEW.so" \
  --artifact "cluster-old-native=$PARENT/firmware/fw-2602-diff/cmp/libBydCluster_OLD.so" \
  --artifact "dashcast-java=$PARENT/jadx-dashcast/sources" \
  --artifact "fw-new-java=$PARENT/firmware/fw-2602-diff/jadx-l3-new/sources" \
  --artifact "fw-old-java=$PARENT/firmware/fw-2602-diff/jadx-l3-old/sources" \
  --artifact "l3-new-apk=$PARENT/firmware/fw-2602-diff/L3_new.apk" \
  --artifact "l3-old-apk=$PARENT/firmware/fw-2602-diff/L3_old.apk" \
  --artifact "navopen-java=$PARENT/NavOpen/src" \
  --artifact "openbyd-java=$PARENT/jadx-openbyd24/sources" \
  --artifact "services-new-framework=$PARENT/firmware/fw-2602-diff/cmp/services_NEW.jar" \
  --artifact "services-old-framework=$PARENT/firmware/fw-2602-diff/cmp/services_OLD.jar" \
  --artifact "tmap-java=$PARENT/jadx-tmap/sources" \
  --output "$CORPUS_REPEAT"
python3 - "$CORPUS_REPEAT" <<'PY'
import hashlib, json, sys
from pathlib import Path
actual = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
report = json.loads(Path("docs/diagnostics/hud-sign-re/corpus-completeness.json").read_text(encoding="utf-8"))
expected = report["corpus"]["selected_artifacts"]
def rows(items):
    return {row["alias"]: (row["sha256"], row["byte_count"], row["file_count"]) for row in items}
if rows(actual["artifacts"]) != rows(expected):
    raise SystemExit("O1 selected corpus bytes drifted")
digest = hashlib.sha256(Path(sys.argv[1]).read_bytes()).hexdigest()
if digest != report["corpus"]["selected_artifact_manifest_sha256"]:
    raise SystemExit("O1 selected corpus manifest identity drifted")
if report["baseline"]["head"] != __import__("subprocess").check_output(["git", "rev-parse", "HEAD"], text=True).strip():
    raise SystemExit("O1 baseline HEAD drifted")
PY
echo 'PASS O1 repo/corpus manifest'

# O2 — exact local tool bytes. Ghidra is hash-checked, never launched here.
scripts/re/decode-java.sh --preflight >/dev/null
require_hash "$HOME/Library/Caches/clusternav-re/downloads/ghidra_12.1.2_PUBLIC_20260605.zip" \
  b62e81a0390618466c019c60d8c2f796ced2509c4c1aea4a37644a77272cf99d 'Ghidra archive'
require_hash "$HOME/.local/share/clusternav-re/ghidra_12.1.2_PUBLIC/support/analyzeHeadless" \
  302880328a0024ee24cfe0326d4d9a61c2237116d95f2e0e0df090f747f95e30 'Ghidra headless launcher'
require_hash "$HOME/Library/Caches/clusternav-re/tools/jdk-21.0.12+8/Contents/Home/bin/java" \
  34b9c157bedcebafc6033b8beaa72c2ff14e2b697e33f45aa959a8373d6581a0 'Ghidra Java 21'
echo 'PASS O2 tool manifest; TOOL_VERDICT=PASS_PINNED'

# O3 — isolated second decode pass; compare normalized output trees and error counts.
DECODE_REPEAT="$TMP_ROOT/decoded"
inputs=(
  "$PARENT/apks/AmapService.apk"
  "$PARENT/carsettings-apk/dex/classes.dex"
  "$PARENT/carsettings-apk/dex/classes2.dex"
  "$PARENT/carsettings-apk/dex/classes3.dex"
  "$PARENT/carsettings-apk/dex/classes4.dex"
  "$PARENT/firmware/fw-2602-diff/L3_old.apk"
  "$PARENT/firmware/fw-2602-diff/L3_new.apk"
)
for input in "${inputs[@]}"; do
  CLUSTERNAV_RE_DECODE_ROOT="$DECODE_REPEAT" scripts/re/decode-java.sh "$input" >/dev/null
done
python3 - "$HOME/Library/Caches/clusternav-re/decoded" "$DECODE_REPEAT" <<'PY'
import hashlib, json, sys
from pathlib import Path
canonical, repeated = map(Path, sys.argv[1:])
corpus = json.loads(Path("docs/diagnostics/hud-sign-re/corpus-completeness.json").read_text(encoding="utf-8"))
expected = {row["sha256"]: row for row in corpus["decode"]["completed"]}
def tree_hash(path, content=True):
    digest = hashlib.sha256()
    for item in sorted((p for p in path.rglob("*") if p.is_file()), key=lambda p: p.relative_to(path).as_posix()):
        relative = item.relative_to(path).as_posix().encode()
        digest.update(relative + b"\n")
        if content:
            digest.update(hashlib.sha256(item.read_bytes()).hexdigest().encode() + b"\n")
    return digest.hexdigest()
for digest, row in expected.items():
    old, new = canonical / digest, repeated / digest
    if not old.is_dir() or not new.is_dir():
        raise SystemExit(f"O3 missing decode for {row['input']}")
    old_manifest = json.loads((old / "decode-manifest.json").read_text(encoding="utf-8"))
    new_manifest = json.loads((new / "decode-manifest.json").read_text(encoding="utf-8"))
    if old_manifest != new_manifest or new_manifest["jadx_auto"]["error_count"] != row["jadx_auto_errors"]:
        raise SystemExit(f"O3 decode manifest drift for {row['input']}")
    # JADX auto can choose different equivalent try/catch reconstructions across runs. Its complete
    # file inventory and error count must match; deterministic fallback and Apktool bytes must match.
    if tree_hash(old / "jadx-auto", content=False) != tree_hash(new / "jadx-auto", content=False):
        raise SystemExit(f"O3 JADX auto inventory drift for {row['input']}")
    directories = ["jadx-fallback"] + (["apktool"] if new_manifest["input_kind"] == "apk" else [])
    for directory in directories:
        if tree_hash(old / directory) != tree_hash(new / directory):
            raise SystemExit(f"O3 normalized {directory} drift for {row['input']}")
PY
echo 'PASS O3 decode determinism'

# O4–O21/O24–O27 — schemas, truth states, exact paths, source health and dependency fences.
python3 scripts/re/verify-reproducibility.py --validate-report-set "$ROOT" >/dev/null
python3 - "$ROOT" <<'PY'
import hashlib, json, re, subprocess, sys
from pathlib import Path
root = Path(sys.argv[1])

def reject_duplicates(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ValueError(f"duplicate JSON key: {key}")
        value[key] = item
    return value

def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")

def sha256(value):
    return hashlib.sha256(canonical(value)).hexdigest()

def exact_keys(value, expected, label):
    if not isinstance(value, dict) or set(value) != set(expected):
        raise SystemExit(f"O24 {label} fields drifted")

def checked_class(classes, name):
    value = classes[name]
    exact_keys(value, {"paths", "policyTokens"}, f"{name} class")
    for field in ("paths", "policyTokens"):
        items = value[field]
        if not isinstance(items, list) or not all(isinstance(item, str) for item in items):
            raise SystemExit(f"O24 {name}.{field} must be a string array")
        if items != sorted(set(items)):
            raise SystemExit(f"O24 {name}.{field} must be sorted and unique")
    for item in value["paths"]:
        parts = item.split("/")
        if not item or item.startswith("/") or "\\" in item or "" in parts or "." in parts or ".." in parts or any(c in item for c in "*?[]"):
            raise SystemExit(f"O24 non-exact path in {name}: {item}")
    return set(value["paths"]), set(value["policyTokens"])

authority_path = root / "docs/diagnostics/hud-sign-re/offcar-boundary-revisions.json"
raw_authority = authority_path.read_bytes()
try:
    authority = json.loads(raw_authority.decode("utf-8", errors="strict"), object_pairs_hook=reject_duplicates)
except (UnicodeDecodeError, ValueError, json.JSONDecodeError) as error:
    raise SystemExit(f"O24 invalid boundary JSON: {error}")
if raw_authority != canonical(authority):
    raise SystemExit("O24 boundary JSON is not canonical compact UTF-8")
exact_keys(authority, {"activeRevision", "revisions", "schemaId", "selfSha256"}, "boundary root")
if authority["schemaId"] != "clusternav.offcar-boundary-revisions/v1" or authority["activeRevision"] != 2:
    raise SystemExit("O24 boundary schema/active revision drifted")
if not re.fullmatch(r"[0-9a-f]{64}", authority["selfSha256"]):
    raise SystemExit("O24 invalid boundary self SHA-256")
root_projection = dict(authority); root_projection.pop("selfSha256")
if sha256(root_projection) != authority["selfSha256"]:
    raise SystemExit("O24 boundary self SHA-256 mismatch")
revisions = authority["revisions"]
if not isinstance(revisions, list) or [item.get("revision") for item in revisions if isinstance(item, dict)] != [1, 2]:
    raise SystemExit("O24 boundary revisions must be exactly [1,2]")
expected_classes = [
    {"CURRENT", "FUTURE_T10", "FUTURE_T11"},
    {"FUTURE_FORBIDDEN", "LOCAL_IGNORED", "POST_BUILD_ATTESTATION", "SOURCE_SEAL_INPUT"},
]
previous = None
for index, revision in enumerate(revisions):
    exact_keys(revision, {"legacyParentBaselineSha256", "pathClasses", "predecessorRevisionSha256", "revision", "revisionSha256"}, f"revision {index + 1}")
    if not re.fullmatch(r"[0-9a-f]{64}", revision["legacyParentBaselineSha256"]):
        raise SystemExit("O24 invalid legacy parent baseline hash")
    classes = revision["pathClasses"]
    if not isinstance(classes, dict) or set(classes) != expected_classes[index]:
        raise SystemExit(f"O24 revision {index + 1} path classes drifted")
    for name in classes:
        checked_class(classes, name)
    projection = dict(revision); declared = projection.pop("revisionSha256")
    if not re.fullmatch(r"[0-9a-f]{64}", declared) or sha256(projection) != declared:
        raise SystemExit(f"O24 revision {index + 1} SHA-256 mismatch")
    if revision["predecessorRevisionSha256"] != previous:
        raise SystemExit(f"O24 revision {index + 1} predecessor mismatch")
    previous = declared
historical, active = revisions
if historical["revisionSha256"] != "a05be7e4d6a521a81a285321754e8370dec4402e4406f2969727cd3863c46301":
    raise SystemExit("O24 immutable revision 1 drifted")
if historical["legacyParentBaselineSha256"] != "8f636f508aaf89592ca676d85a8d13dbb8c7e9225112957d281dc1368901e1d4":
    raise SystemExit("O24 historical parent baseline drifted")
current, current_tokens = checked_class(historical["pathClasses"], "CURRENT")
old_t10, old_t10_tokens = checked_class(historical["pathClasses"], "FUTURE_T10")
old_t11, old_t11_tokens = checked_class(historical["pathClasses"], "FUTURE_T11")
source, source_tokens = checked_class(active["pathClasses"], "SOURCE_SEAL_INPUT")
post_build, post_tokens = checked_class(active["pathClasses"], "POST_BUILD_ATTESTATION")
local_ignored, local_tokens = checked_class(active["pathClasses"], "LOCAL_IGNORED")
future, future_tokens = checked_class(active["pathClasses"], "FUTURE_FORBIDDEN")
if (len(current), len(old_t10), len(old_t11), len(source), len(post_build), len(future)) != (81, 15, 13, 155, 7, 13):
    raise SystemExit("O24 boundary path cardinality drifted")
if any((current_tokens, old_t10_tokens, old_t11_tokens, source_tokens, post_tokens, future_tokens)):
    raise SystemExit("O24 policy tokens are allowed only for LOCAL_IGNORED")
if local_ignored != {".authorized-build", ".t10-local", "keystore.properties", "release.keystore"} or local_tokens != {"POLICY-T10-APK-ARTIFACT-NAME"}:
    raise SystemExit("O24 LOCAL_IGNORED policy drifted")
if future != old_t11 or not old_t10 <= source | post_build:
    raise SystemExit("O24 historical FUTURE transition drifted")
if source & post_build or source & future or post_build & future or local_ignored & (source | post_build | future):
    raise SystemExit("O24 active path classes overlap")
if "docs/diagnostics/hud-sign-re/offcar-boundary-revisions.json" not in source:
    raise SystemExit("O24 authority does not authorize its own exact path")
legacy = json.loads((root / "docs/diagnostics/hud-sign-re/expansion/legacy-baseline.json").read_text(encoding="utf-8"))
baseline_digest = hashlib.sha256()
for artifact in sorted(legacy["artifacts"], key=lambda item: item["path"]):
    relative = artifact["path"]; path_bytes = relative.encode("utf-8")
    file_digest = hashlib.sha256((root / relative).read_bytes()).digest()
    baseline_digest.update(len(path_bytes).to_bytes(4, "big") + path_bytes + file_digest)
if len(legacy["artifacts"]) != 13 or baseline_digest.hexdigest() != active["legacyParentBaselineSha256"]:
    raise SystemExit("O24 active parent baseline identity drifted")

preexisting = {
    "docs/_handoff/session-2026-08-06-pm2-firstlaunch-fixes-and-hud-enable.md": "7b5de3354ec83166762c8cefdbe39557fde4470cadcf84a1c0c89a9d0d6cb9d3",
    "docs/_handoff/v1.04-exact-source.json": "c2fc96335cfcb7a59a231006e0dd4dc9e7c20d7f24b11cf8f2ce21a4d5424c7a",
    "docs/specs/windshield-hud-enable.html": "96f57dcea19650d6096a99844121fbeaad45b91e92a322bf9f0b3f5cffa91899",
}
def output(*args):
    return subprocess.check_output(args).decode().split("\0")
changed = {item for item in output("git", "diff", "--name-only", "-z", "HEAD") if item}
changed |= {item for item in output("git", "ls-files", "--others", "--exclude-standard", "-z") if item}
forbidden_changed = changed & future
if forbidden_changed:
    raise SystemExit("O24 FUTURE_FORBIDDEN T11 paths changed: " + ", ".join(sorted(forbidden_changed)))
local_changed = changed & local_ignored
if local_changed:
    raise SystemExit("O24 LOCAL_IGNORED cannot authorize tracked changes: " + ", ".join(sorted(local_changed)))
authorized = source | post_build
unknown = changed - authorized - set(preexisting)
if unknown:
    raise SystemExit("O24 unauthorized changed paths: " + ", ".join(sorted(unknown)))
for path, expected in preexisting.items():
    actual = hashlib.sha256((root / path).read_bytes()).hexdigest()
    if actual != expected:
        raise SystemExit(f"O24 pre-existing out-of-scope file drifted: {path}")
source_extensions = {".kt", ".kts", ".java", ".py", ".sh"}
for path in sorted(changed & authorized):
    candidate = root / path
    if candidate.suffix in source_extensions:
        lines = len(candidate.read_text(encoding="utf-8").splitlines())
        if lines > 500:
            raise SystemExit(f"O24 source over 500 LOC: {path} ({lines})")
main_roots = [root / "app/src/main", root / "app/src/release"]
unsafe = re.compile(r"com\.byd\.clusternav\.TEST_|TEST_ADAS_MASS|TEST_HAL_WRITE|TEST_HUD_NAV|getIntExtra\(\"id\"|getStringExtra\(\"name\"")
for base in main_roots:
    if base.exists():
        for path in base.rglob("*"):
            if path.is_file() and path.suffix in {".kt", ".java", ".xml"} and unsafe.search(path.read_text(encoding="utf-8", errors="replace")):
                raise SystemExit(f"O15 unsafe main/release hook: {path.relative_to(root)}")
planner_build = (root / "offcar-planner/build.gradle.kts").read_text(encoding="utf-8")
projects = set(re.findall(r'project\("([^\"]+)"\)', planner_build))
if projects != {":vehicle-contracts"}:
    raise SystemExit(f"O13 planner dependencies drifted: {sorted(projects)}")
for path in ("app/build.gradle.kts", "core/build.gradle.kts", "car-integration/build.gradle.kts"):
    if ':offcar-planner' in (root / path).read_text(encoding="utf-8"):
        raise SystemExit(f"O13 runtime depends on planner: {path}")
for base in (root / "offcar-planner/src/main", root / "vehicle-contracts/src/main"):
    for path in base.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for pattern in (r"\bProcessBuilder\b", r"Runtime\s*\.\s*getRuntime", r"java\.net\.", r"\bSocket\b", r"(?i)\bdadb\b|\badb\b", r"\bCarExec\b", r"android\.", r"--execute"):
            if re.search(pattern, text):
                raise SystemExit(f"O13 transport token in {path.relative_to(root)}: {pattern}")
corpus = json.loads((root / "docs/diagnostics/hud-sign-re/corpus-completeness.json").read_text(encoding="utf-8"))
consolidation = corpus.get("consolidation", {})
diff = subprocess.check_output(["git", "diff", "--binary", "--no-ext-diff", "HEAD"])
if hashlib.sha256(diff).hexdigest() != consolidation.get("tracked_diff_sha256"):
    raise SystemExit("O1 tracked diff identity drifted")
source_rows = consolidation.get("source_files", [])
identity_paths = sorted(
    path for path in changed & current
    if path == ".gitignore" or (root / path).suffix in source_extensions
)
if {row.get("path") for row in source_rows} != set(identity_paths):
    raise SystemExit("O1 source identity coverage drifted")
for row in source_rows:
    path = root / row["path"]
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != row["sha256"]:
        raise SystemExit(f"O1 source identity drifted: {row['path']}")
for path, expected in consolidation.get("report_identities", {}).items():
    target = root / path
    if not target.is_file() or hashlib.sha256(target.read_bytes()).hexdigest() != expected:
        raise SystemExit(f"O1 report identity drifted: {path}")
PY

echo 'PASS O4 corpus completeness; CORPUS_VERDICT=NOT_EXHAUSTIVE'
echo 'PASS O5 Java/access index'
echo 'PASS O6 NaviInfo schema'
echo 'PASS O7 native body index'
echo 'PASS O8 native/QML map (QML=UNAVAILABLE)'
echo 'PASS O9 evidence graph'
echo 'PASS O10 candidate taxonomy'
echo 'PASS O11 safety schema'
echo 'PASS O12 scenario completeness'
echo 'PASS O13 pure planner fence'
echo 'PASS O14 inert deterministic snapshots'
echo 'PASS O15 main/release quarantine'
echo 'PASS O16 modern property boundary'
echo 'PASS O24 canonical revision-2 boundary, exact classes and LOC'
echo 'PASS O25 privacy/no exfiltration static gate'
echo 'PASS O26 bidirectional traceability'
echo 'PASS O27 four inert milestone packs'

python3 -m unittest discover -s scripts/re/tests -p 'test_*.py' -v

git diff --check
./gradlew --offline --console=plain \
  :vehicle-contracts:test \
  :offcar-planner:test \
  :core:test \
  :app:testDebugUnitTest \
  :app:testVehicleTestUnitTest \
  :car-integration:test \
  :app:assembleDebug \
  :app:assembleVehicleTest \
  :app:assembleRelease \
  :app:lintDebug \
  :app:lintVehicleTest \
  :app:lintRelease

echo 'PASS O17 typed frame'
echo 'PASS O18 parameterized source lifecycle'
echo 'PASS O19 output isolation'
echo 'PASS O20 physical ownership'
echo 'PASS_WITH_AUTHORIZATION_BLOCKER O21 unit/static green; emulator requires explicitly forbidden ADB/install'
echo 'PASS O22 full tests/build'
echo 'PASS O23 debug/vehicleTest/release lint'
echo 'APPROVED T0-T9 achievable gates; CORPUS_VERDICT=NOT_EXHAUSTIVE; FEATURE_DONE=false'
