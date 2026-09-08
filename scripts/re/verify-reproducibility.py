#!/usr/bin/env python3
"""Verify deterministic report bytes, sanitize values, and validate the full inert report set."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter
from pathlib import Path
from typing import Any

UNIX_HOME = re.compile(r"/(?:Users|home)/[^/<\s\"']+")
WINDOWS_HOME = re.compile(r"[A-Za-z]:\\Users\\[^\\\r\n<\"']+", re.IGNORECASE)
PRIVATE_IP = re.compile(
    r"\b(?:10(?:\.\d{1,3}){3}|192\.168(?:\.\d{1,3}){2}|"
    r"172\.(?:1[6-9]|2\d|3[01])(?:\.\d{1,3}){2})\b"
)
LEAKS = [
    re.compile(UNIX_HOME.pattern.encode()),
    re.compile(WINDOWS_HOME.pattern.encode(), re.IGNORECASE),
    re.compile(PRIVATE_IP.pattern.encode()),
]

REPORT_FILES = (
    "docs/diagnostics/hud-sign-re/README.md",
    "docs/diagnostics/hud-sign-re/evidence-index.json",
    "docs/diagnostics/hud-sign-re/candidate-report.html",
    "docs/diagnostics/hud-sign-re/zero-hit-report.txt",
    "docs/diagnostics/hud-sign-re/native/libbydcluster-diff.json",
    "docs/diagnostics/hud-sign-re/first-launch-emulator-result.json",
    "docs/diagnostics/hud-sign-re/traceability.json",
    "docs/diagnostics/hud-sign-re/corpus-completeness.json",
    "docs/diagnostics/hud-sign-re/m1-nav-hud-plan.json",
    "docs/diagnostics/hud-sign-re/m2-hud-road-plan.json",
    "docs/diagnostics/hud-sign-re/m3-cluster-sign-plan.json",
    "docs/diagnostics/hud-sign-re/m4-hud-sign-plan.json",
    "tools/re/manifest.json",
    "docs/specs/seal-nav-hud-speed-sign-offcar.html",
)

MISSING_CORPUS_IDS = {
    "C-BYDAUTO-PROVIDER-LIB",
    "C-ODM-PARTITION",
    "C-PROPERTY-REGISTRY",
    "C-PROVIDER-APK",
    "C-QML-RCC",
    "C-SERVICE-CONTEXT",
    "C-SYSTEM-EXT-PARTITION",
    "C-VENDOR-BOOT",
    "C-VENDOR-PARTITION",
}
FUTURE_IDS = {
    "D-H0-HUD-PHYSICAL-TEMP",
    "D-M1-NAV-HUD",
    "D-M2-HUD-ROAD",
    "D-M3-CLUSTER-SIGN",
    "D-M4-HUD-SIGN",
    "P-M1-NAV-HUD",
    "P-M2-HUD-ROAD",
    "P-M3-CLUSTER-SIGN",
    "P-M4-HUD-SIGN",
}
EXPECTED_REQUIREMENT_STATUS = {
    **{f"R{number}": "VERIFIED_OFF_CAR" for number in range(1, 33)},
    "R10": "DEFERRED_T10_T11",
    "R11": "DEFERRED_T10_T11",
    "R24": "BLOCKED_BY_EXPLICIT_NO_ADB_INSTALL",
    "R28": "DEFERRED_T10_T11",
    "R32": "NOT_EXHAUSTIVE",
}


def sanitize_text(text: str) -> str:
    return PRIVATE_IP.sub(
        "<vehicle-ip>",
        UNIX_HOME.sub("<user-home>", WINDOWS_HOME.sub("<user-home>", text)),
    )


def sanitize_value(value: Any) -> Any:
    if isinstance(value, str):
        return sanitize_text(value)
    if isinstance(value, list):
        return [sanitize_value(item) for item in value]
    if isinstance(value, dict):
        return {key: sanitize_value(item) for key, item in value.items()}
    return value


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def _load_json(root: Path, relative: str) -> dict:
    path = root / relative
    _require(path.is_file() and not path.is_symlink(), f"missing regular report: {relative}")
    value = json.loads(path.read_text(encoding="utf-8"))
    _require(isinstance(value, dict), f"report root must be an object: {relative}")
    return value


def _walk(value: Any):
    yield value
    if isinstance(value, dict):
        for item in value.values():
            yield from _walk(item)
    elif isinstance(value, list):
        for item in value:
            yield from _walk(item)


def _reject_fields(value: Any, fields: set[str], label: str) -> None:
    for node in _walk(value):
        if isinstance(node, dict):
            found = fields.intersection(node)
            _require(not found, f"{label} contains prohibited raw field(s): {sorted(found)}")


def _reject_visual_success(value: Any, label: str) -> None:
    for node in _walk(value):
        if isinstance(node, dict):
            for key in ("visualPass", "offCarVisualPass"):
                if key in node:
                    _require(node[key] is False, f"{label} has non-false {key}")
        if isinstance(node, str):
            _require(node not in {"FIELD_PROVEN", "UNSUPPORTED"}, f"{label} overpromotes {node}")


def inspect(path: Path) -> dict:
    data = path.read_bytes()
    leaks = [pattern.pattern.decode("ascii", errors="replace") for pattern in LEAKS if pattern.search(data)]
    canonical = None
    if path.suffix.lower() == ".json":
        value = json.loads(data.decode("utf-8"))
        canonical_bytes = (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()
        canonical = hashlib.sha256(canonical_bytes).hexdigest()
    return {
        "byte_length": len(data),
        "canonical_json_sha256": canonical,
        "leaks": leaks,
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def verify(paths: list[Path], expect_equal: bool) -> dict:
    reports = [{"path": path.name, **inspect(path)} for path in paths]
    if any(row["leaks"] for row in reports):
        raise ValueError("sanitization failure")
    if expect_equal and len({row["sha256"] for row in reports}) != 1:
        raise ValueError("report bytes are not reproducible")
    return {
        "equal": len({row["sha256"] for row in reports}) == 1,
        "reports": reports,
        "schema": "clusternav.re-reproducibility/v1",
    }


def _validate_corpus(root: Path) -> dict:
    relative = "docs/diagnostics/hud-sign-re/corpus-completeness.json"
    report = _load_json(root, relative)
    _require(report.get("schema") == "clusternav.hud-sign-corpus-completeness/v1", "bad corpus schema")
    corpus = report.get("corpus", {})
    _require(corpus.get("verdict") == "NOT_EXHAUSTIVE", "missing corpus must force NOT_EXHAUSTIVE")
    _require(
        corpus.get("available_scope_verdict") == "COMPLETE_FOR_AVAILABLE_JAVA_AND_REQUESTED_NATIVE_SYMBOL_SCOPE",
        "available corpus scope verdict drifted",
    )
    artifacts = corpus.get("selected_artifacts", [])
    _require(len(artifacts) == 17, "selected corpus must contain 17 exact artifacts")
    _require(all(re.fullmatch(r"[0-9a-f]{64}", row.get("sha256", "")) for row in artifacts), "bad corpus hash")
    availability = " ".join(row.get("state", "") for row in corpus.get("availability", []))
    for token in ("KNOWN_MISSING", "UNAVAILABLE_ZERO_HIT"):
        _require(token in availability, f"corpus availability omits {token}")
    scope = report.get("scope", {})
    _require(set(scope.get("authorized", [])) >= {f"T{i}" for i in range(10)}, "T0-T9 scope not consolidated")
    _require({"T10", "T11"} <= set(scope.get("forbidden", [])), "future phases are not forbidden")
    evidence = report.get("evidence", {})
    bindings = {
        "evidence_index_sha256": "docs/diagnostics/hud-sign-re/evidence-index.json",
        "native_report_sha256": "docs/diagnostics/hud-sign-re/native/libbydcluster-diff.json",
        "zero_hit_report_sha256": "docs/diagnostics/hud-sign-re/zero-hit-report.txt",
    }
    for key, path in bindings.items():
        _require(evidence.get(key) == _sha256(root / path), f"stale corpus binding: {key}")
    _require(report.get("tool_manifest_sha256") == _sha256(root / "tools/re/manifest.json"), "stale tool binding")
    return report


def _validate_evidence(root: Path) -> dict:
    report = _load_json(root, "docs/diagnostics/hud-sign-re/evidence-index.json")
    _require(report.get("schema") == "clusternav.re-evidence-graph/v1", "bad evidence schema")
    _require(report.get("corpus_verdict") == "NOT_EXHAUSTIVE", "evidence corpus verdict is not honest")
    expected = {*(f"H{i}" for i in range(8)), *(f"S{i}" for i in range(11))}
    rows = report.get("evidence", [])
    _require({row.get("id") for row in rows} == expected, "H/S taxonomy is incomplete")
    _require(all(row.get("citations") and row.get("executable") is False for row in rows), "uncited or executable evidence")
    _reject_fields(report, {"snippet"}, "evidence-index")
    allowed = {"address", "artifact_sha256", "body_sha256", "line", "line_sha256", "matched_tokens", "path", "size_bytes", "symbol"}
    for row in rows:
        for citation in row.get("citations", []):
            _require(set(citation) <= allowed, f"non-metadata citation field in {row.get('id')}")
            _require(re.fullmatch(r"[0-9a-f]{64}", citation.get("artifact_sha256", "")) is not None, "bad citation artifact hash")
            _require(isinstance(citation.get("matched_tokens"), list) and citation["matched_tokens"], "citation lacks matched tokens")
            if "line" in citation:
                _require(isinstance(citation["line"], int) and citation["line"] > 0, "bad citation line")
                _require(re.fullmatch(r"[0-9a-f]{64}", citation.get("line_sha256", "")) is not None, "bad citation line hash")
            else:
                _require({"address", "body_sha256", "size_bytes", "symbol"} <= set(citation), "bad native citation metadata")
    _require(len(report.get("edges", [])) == 5, "evidence edge count drifted")
    inventory = report.get("inventory", [])
    zero = {row.get("id") for row in inventory if row.get("zero_hit") is True}
    _require(zero == MISSING_CORPUS_IDS, "missing corpus inventory drifted")
    facts = report.get("facts", {})
    _require(facts.get("off_car_visual_pass") is False, "off-car evidence claims visual success")
    _require(facts.get("property_write_promoted") is False, "property write was prematurely promoted")
    _require(facts.get("navi_info_has_speed_limit") is False, "NaviInfo incorrectly contains speed")
    _reject_visual_success(report, "evidence-index")
    return report


def _validate_native(root: Path) -> dict:
    report = _load_json(root, "docs/diagnostics/hud-sign-re/native/libbydcluster-diff.json")
    _require(report.get("schema") == "clusternav.libbydcluster-native-diff/v1", "bad native schema")
    summary = report.get("summary", {})
    _require(summary.get("overall_verdict") == "NOT_EXHAUSTIVE", "native verdict overclaims corpus")
    _require(summary.get("primary_paired_function_count") == 25, "native primary pair count drifted")
    _require(summary.get("unresolved_function_count") == 0, "native functions unresolved")
    _require(summary.get("possible_semantic_change_count") == 0, "unreviewed native semantic change")
    _require(report.get("validation", {}).get("status") == "PASS", "native report validation failed")
    _require(report.get("qml_rcc_linkage", {}).get("state") == "UNAVAILABLE", "QML gap must stay unavailable")
    _reject_fields(report.get("functions", []), {"c"}, "canonical native report")
    decompile_fields = {"address_normalized_sha256", "character_count", "message", "sha256", "status", "truncated"}
    for row in report.get("functions", []):
        comparison = row.get("comparison", {})
        _require(comparison.get("match_basis") == "FULL_DEMANGLED_SYMBOL_NAME", "native address pairing used")
        _require(comparison.get("address_used_for_pairing") is False, "native address pairing used")
        for side in (row.get("old"), row.get("new")):
            if side is not None:
                decompile = side.get("decompile", {})
                _require(set(decompile) == decompile_fields, "canonical native decompile is not metadata-only")
                _require(decompile.get("status") == "COMPLETE", "canonical native decompile incomplete")
                _require(all(re.fullmatch(r"[0-9a-f]{64}", decompile.get(key, "")) for key in ("sha256", "address_normalized_sha256")), "bad native decompile hash")
    return report


def _validate_traceability(root: Path) -> dict:
    report = _load_json(root, "docs/diagnostics/hud-sign-re/traceability.json")
    _require(report.get("schema") == "clusternav.hud-sign-traceability/v1", "bad traceability schema")
    _require(report.get("corpusVerdict") == "NOT_EXHAUSTIVE" and report.get("visualPass") is False, "bad trace truth")
    links = report.get("requirements", [])
    by_id = {row.get("id"): row for row in links}
    _require(set(by_id) == set(EXPECTED_REQUIREMENT_STATUS), "R1-R32 are incomplete")
    _require({key: row.get("status") for key, row in by_id.items()} == EXPECTED_REQUIREMENT_STATUS, "requirement statuses drifted")
    expected_sets = {
        "tasksToRequirements": {f"T{i}" for i in range(12)},
        "gatesToRequirements": {f"O{i}" for i in range(1, 28)},
        "futureToRequirements": FUTURE_IDS,
    }
    selectors = {"tasksToRequirements": "tasks", "gatesToRequirements": "gates", "futureToRequirements": "futureIds"}
    aliases = re.compile(r"^(?:all|N/A|T\d+-T\d+|O\d+-O\d+)$", re.IGNORECASE)
    for reverse_key, expected_ids in expected_sets.items():
        reverse = report.get(reverse_key, {})
        _require(set(reverse) == expected_ids, f"{reverse_key} IDs are incomplete")
        selector = selectors[reverse_key]
        for value_id, requirements in reverse.items():
            derived = sorted(req_id for req_id, row in by_id.items() if value_id in row.get(selector, []))
            _require(sorted(requirements) == derived, f"stale reverse trace for {value_id}")
    for row in links:
        for value in row.get("tasks", []) + row.get("gates", []) + row.get("futureIds", []):
            _require(not aliases.fullmatch(value), f"trace alias forbidden: {value}")
        for artifact in row.get("artifact", "").split(" + "):
            _require("/" in artifact and artifact != "N/A", f"non-exact artifact for {row.get('id')}")
            if not (root / artifact).is_file():
                _require(row.get("status") == "DEFERRED_T10_T11", f"missing current evidence: {artifact}")
    _reject_visual_success(report, "traceability")
    return report


def _validate_pack(root: Path, name: str, milestone: str, diagnostic: str, production: str) -> dict:
    report = _load_json(root, f"docs/diagnostics/hud-sign-re/{name}")
    _require(report.get("schema") == "clusternav.offcar-milestone-pack/v1", f"bad pack schema: {name}")
    _require(report.get("milestone") == milestone, f"bad milestone: {name}")
    _require(report.get("corpusVerdict") == "NOT_EXHAUSTIVE" and report.get("visualPass") is False, f"bad pack truth: {name}")
    d_section, p_section = report.get(diagnostic, {}), report.get(production, {})
    _require(d_section.get("state") == "INERT_DATA_ONLY", f"diagnostic pack not inert: {name}")
    _require(p_section.get("state") == "BLOCKED_PENDING_FIELD_PROOF", f"production pack not blocked: {name}")
    _require(p_section.get("capability") == "UNKNOWN", f"production capability overpromoted: {name}")
    plans = d_section.get("plans", [])
    _require(report.get("candidateCount") == len(plans) and plans, f"candidate count drifted: {name}")
    _require(report.get("blockedCount", 0) + report.get("unknownCount", 0) == len(plans), f"disposition count drifted: {name}")
    for plan in plans:
        phases = [step.get("phase") for step in plan.get("steps", [])]
        _require(phases.count("PROPOSE") == 1, f"plan does not have one mutation: {plan.get('id')}")
        _require({"READ", "PROPOSE", "OBSERVE", "INVERSE"} <= set(phases), f"plan lifecycle incomplete: {plan.get('id')}")
        prohibited = {"RAW_ID", "FREE_FORM_NAME", "MASS_MODE", "GUESSED_ENUM", "MULTI_DIMENSION_MUTATION"}
        _require(not prohibited.intersection(plan.get("validationIssues", [])), f"unsafe plan: {plan.get('id')}")
    _reject_visual_success(report, name)
    return report


def _validate_first_launch(root: Path) -> dict:
    report = _load_json(root, "docs/diagnostics/hud-sign-re/first-launch-emulator-result.json")
    _require(report.get("schema") == "clusternav.first-launch-emulator-result/v1", "bad emulator report schema")
    _require(report.get("status") == "BLOCKED_BY_EXPLICIT_NO_ADB_INSTALL", "emulator blocker is not exact")
    _require(report.get("executed") is False and report.get("visualPass") is False, "emulator report claims execution")
    auth = report.get("authorization", {})
    _require(auth.get("adbAllowed") is False and auth.get("apkInstallAllowed") is False, "forbidden emulator operations allowed")
    environment = report.get("environment", {})
    _require(environment.get("emulatorBinaryAvailable") is True, "emulator availability not recorded")
    _require(environment.get("configuredAvdCount", 0) >= 1, "AVD availability not recorded")
    _require(report.get("offCarEvidence", {}).get("unitAndStaticStatus") == "PASS", "unit/static fallback missing")
    _require(all(value is False for value in report.get("claims", {}).values()), "unobserved emulator claim promoted")
    return report


def _validate_tools(root: Path) -> dict:
    report = _load_json(root, "tools/re/manifest.json")
    _require(report.get("schema") == "clusternav.re-tools/v1", "bad tool schema")
    versions = {row.get("name"): row.get("version") for row in report.get("tools", [])}
    expected = {"JADX": "1.5.6", "Apktool": "3.0.3", "Ghidra": "12.1.2", "Project Gradle JDK": "17.0.19"}
    _require(all(versions.get(name) == version for name, version in expected.items()), "tool versions drifted")
    jadx = next(row for row in report["tools"] if row.get("name") == "JADX")
    _require(jadx.get("rejected", {}).get("reason") == "STALE_VERSION_1.5.0", "stale JADX is not rejected")
    return report


def validate_report_set(root: Path) -> dict:
    root = root.resolve()
    _require((root / "settings.gradle.kts").is_file(), "project root is invalid")
    for relative in REPORT_FILES:
        path = root / relative
        _require(path.is_file() and not path.is_symlink(), f"required report missing: {relative}")
        _require(not inspect(path)["leaks"], f"privacy leak in {relative}")
    report_dir = root / "docs/diagnostics/hud-sign-re"
    executable = [path for path in report_dir.rglob("*") if path.is_file() and path.suffix.lower() in {".sh", ".command", ".bat", ".ps1"}]
    _require(not executable, "report pack contains executable command files")

    _validate_tools(root)
    corpus = _validate_corpus(root)
    _validate_evidence(root)
    _validate_native(root)
    trace = _validate_traceability(root)
    packs = (
        ("m1-nav-hud-plan.json", "M1_NAV_HUD", "D-M1-NAV-HUD", "P-M1-NAV-HUD"),
        ("m2-hud-road-plan.json", "M2_HUD_ROAD", "D-M2-HUD-ROAD", "P-M2-HUD-ROAD"),
        ("m3-cluster-sign-plan.json", "M3_CLUSTER_SIGN", "D-M3-CLUSTER-SIGN", "P-M3-CLUSTER-SIGN"),
        ("m4-hud-sign-plan.json", "M4_HUD_SIGN", "D-M4-HUD-SIGN", "P-M4-HUD-SIGN"),
    )
    for definition in packs:
        _validate_pack(root, *definition)
    _validate_first_launch(root)

    html = (root / "docs/diagnostics/hud-sign-re/candidate-report.html").read_text(encoding="utf-8")
    _require("12</b><span>generated candidates" in html, "candidate report count drifted")
    _require("Corpus: NOT_EXHAUSTIVE" in html, "candidate report hides corpus verdict")
    _require("visualPass\": true" not in html and "FEATURE_DONE" not in html, "candidate report overclaims completion")
    zero = (root / "docs/diagnostics/hud-sign-re/zero-hit-report.txt").read_text(encoding="utf-8")
    _require(all(f"ZERO {gap}" in zero for gap in MISSING_CORPUS_IDS), "zero-hit report is incomplete")

    statuses = Counter(row["status"] for row in trace["requirements"])
    return {
        "corpusVerdict": corpus["corpus"]["verdict"],
        "reportCount": len(REPORT_FILES),
        "requirementStatuses": dict(sorted(statuses.items())),
        "schema": "clusternav.hud-sign-report-set-validation/v1",
        "status": "PASS",
        "visualPass": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("files", nargs="*")
    parser.add_argument("--expect-equal", action="store_true")
    parser.add_argument("--validate-report-set", metavar="PROJECT_ROOT")
    args = parser.parse_args()
    try:
        if args.validate_report_set:
            if args.files or args.expect_equal:
                parser.error("--validate-report-set cannot be combined with file comparison")
            result = validate_report_set(Path(args.validate_report_set))
        else:
            if not args.files:
                parser.error("provide reports or --validate-report-set")
            result = verify([Path(item) for item in args.files], args.expect_equal)
        print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as error:
        parser.error(sanitize_text(str(error)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
