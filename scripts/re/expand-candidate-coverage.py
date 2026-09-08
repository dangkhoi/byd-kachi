#!/usr/bin/env python3
"""Generate the closed, metadata-only C01-C12 candidate coverage report."""
from __future__ import annotations

import argparse
import copy
import hashlib
import ipaddress
import json
import os
import re
import stat
import unicodedata
from pathlib import Path
from typing import Any, Iterable

SCRIPT_PATH = Path(os.path.abspath(__file__))
PROJECT_ROOT = SCRIPT_PATH.parents[2]
OUTPUT_RELATIVE = Path("docs/diagnostics/hud-sign-re/expansion/corpus-coverage.json")
EVIDENCE_RELATIVE = Path("docs/diagnostics/hud-sign-re/evidence-index.json")
COMPLETENESS_RELATIVE = Path("docs/diagnostics/hud-sign-re/corpus-completeness.json")
ZERO_HIT_RELATIVE = Path("docs/diagnostics/hud-sign-re/zero-hit-report.txt")
NATIVE_RELATIVE = Path("docs/diagnostics/hud-sign-re/native/libbydcluster-diff.json")
SCHEMA_ID = "clusternav.expansion-corpus-coverage/v1"
TOOL_ID = "TOOL-EXPANSION-COVERAGE-SCANNER"
VERSION_ID = "VERSION-X1-2"
EMPTY_SHA256 = hashlib.sha256(b"").hexdigest()
CORPUS_IDS = tuple(f"C{number:02d}" for number in range(1, 13))
QUERY_IDS = {corpus: query for corpus, query in zip(CORPUS_IDS, (
    "QRY-C01-AMAP-NAVI-METADATA", "QRY-C02-SETTINGS-DICAR-METADATA",
    "QRY-C03-FRAMEWORK-HUD-SIGN-METADATA", "QRY-C04-NATIVE-CLUSTER-METADATA",
    "QRY-C05-REFERENCE-APPS-METADATA", "QRY-C06-VENDOR-HAL-INVENTORY",
    "QRY-C07-PARTITION-INVENTORY", "QRY-C08-PROVIDER-LIB-INVENTORY",
    "QRY-C09-PROPERTY-REGISTRY-INVENTORY", "QRY-C10-SERVICE-SELINUX-INVENTORY",
    "QRY-C11-ISA-PROVIDER-INVENTORY", "QRY-C12-QML-RCC-INVENTORY",
))}
QUERY_TOKENS = {corpus: [f"TOKEN-{corpus}-QUERY"] for corpus in CORPUS_IDS}
ARTIFACT_ALIASES = {
    **{corpus: "ALIAS-PARENT-EVIDENCE-INDEX" for corpus in ("C01", "C02", "C03", "C05")},
    "C04": "ALIAS-PARENT-NATIVE-REPORT", "C06": "ALIAS-C06-VENDOR-HAL-CORPUS",
    "C07": "ALIAS-C07-PARTITION-CORPUS", "C08": "ALIAS-C08-PROVIDER-LIB-CORPUS",
    "C09": "ALIAS-C09-PROPERTY-REGISTRY-CORPUS", "C10": "ALIAS-C10-SERVICE-SELINUX-CORPUS",
    "C11": "ALIAS-C11-ISA-PROVIDER-CORPUS", "C12": "ALIAS-C12-QML-RCC-CORPUS",
}
SOURCE_ROOTS = {"C01": frozenset({"amap"}), "C02": frozenset({"carsettings"}), "C03": frozenset({"fw-new", "fw-old"}), "C05": frozenset({"dashcast", "navopen", "openbyd", "tmap"})}
REQUIRED_ARTIFACT_ALIASES = {
    "C01": frozenset({"amap-apk", "amap-java"}), "C02": frozenset({"carsettings-dex", "carsettings-java"}),
    "C03": frozenset({"fw-new-java", "fw-old-java", "services-new-framework", "services-old-framework"}),
    "C04": frozenset({"cluster-new-native", "cluster-old-native"}),
    "C05": frozenset({"dashcast-java", "navopen-java", "openbyd-java", "tmap-java"}),
}
UNAVAILABLE_INVENTORY_IDS = {
    "C06": frozenset({"C-VENDOR-PARTITION"}), "C07": frozenset({"C-ODM-PARTITION", "C-SYSTEM-EXT-PARTITION", "C-VENDOR-BOOT"}),
    "C08": frozenset({"C-BYDAUTO-PROVIDER-LIB"}), "C09": frozenset({"C-PROPERTY-REGISTRY"}),
    "C10": frozenset({"C-SERVICE-CONTEXT"}), "C11": frozenset({"C-PROVIDER-APK"}), "C12": frozenset({"C-QML-RCC"}),
}
AVAILABILITY = frozenset({"AVAILABLE", "UNAVAILABLE", "UNSEARCHED", "BUDGET_STOPPED", "ACQUIRED_UNREVIEWED"})
CANDIDATE_STATES = frozenset({"DISCOVERED", "SOURCE_BACKED", "READ_ONLY_READY", "MUTATION_REVIEW", "READY_FOR_FIELD", "REJECTED"})
OPEN_CANDIDATE_STATES = frozenset({"DISCOVERED", "SOURCE_BACKED", "MUTATION_REVIEW"})
ABSOLUTE_REJECTS = ("RAW_SELECTOR", "FREE_FORM_SELECTOR", "MASS_MUTATION", "GUESSED_ENUM", "RETAINED_STATE_DEPENDENCY", "WEAK_EVIDENCE_ONLY")
CLAIM_FIELDS = frozenset({"absoluteRejects", "access", "boundedDomainValueIds", "clearOperationId", "clearPolicy", "configId", "consumerId", "inverseOperationIds", "javaType", "mutationOperationId", "ownership", "permissionId", "priorReadOperationId", "providerId", "readBackOperationId", "readProbeId", "risk", "selectorId", "transportId"})

def _claim(*, rejects: Iterable[str] = (), selector: str, probe: str | None, mutation: str | None,
           config: str, access: str, java_type: str, provider: str, permission: str,
           transport: str, values: Iterable[str], consumer: str, risk: int) -> dict[str, Any]:
    return {"absoluteRejects": list(rejects), "access": access, "boundedDomainValueIds": list(values),
            "clearOperationId": None, "clearPolicy": "NOT_APPLICABLE", "configId": config,
            "consumerId": consumer, "inverseOperationIds": [], "javaType": java_type,
            "mutationOperationId": mutation, "ownership": "DIAGNOSTIC_TEMP", "permissionId": permission,
            "priorReadOperationId": None, "providerId": provider, "readBackOperationId": None,
            "readProbeId": probe, "risk": risk, "selectorId": selector, "transportId": transport}


_H8_CLAIM = _claim(selector="SEL-H8-PROPERTY-CONFIG-METADATA", probe="PROBE-READ-PROPERTY-CONFIG", mutation=None,
    config="CONFIG-H8-PROPERTY-METADATA", access="READ_ONLY", java_type="STRING", provider="PROVIDER-SOURCE-METADATA",
    permission="PERMISSION-NONE", transport="TRANSPORT-READ-ONLY-METADATA", values=("VALUE-METADATA-AVAILABLE",),
    consumer="CONSUMER-EXPANSION-REVIEW", risk=0)
_S11_CLAIM = _claim(selector="SEL-S11-SOURCE-DOMAIN", probe=None, mutation="OP-MUTATE-S11-SOURCE-DOMAIN",
    config="CONFIG-S11-SOURCE-DOMAIN", access="READ_WRITE", java_type="STRING", provider="PROVIDER-SOURCE-METADATA",
    permission="PERMISSION-VENDOR-CAR", transport="TRANSPORT-SOURCE-PROVEN", values=(),
    consumer="CONSUMER-CLUSTER-NATIVE", risk=25)
_S12_CLAIM = _claim(rejects=("RAW_SELECTOR",), selector="SEL-S11-SOURCE-DOMAIN", probe=None,
    mutation="OP-MUTATE-S11-SOURCE-DOMAIN", config="CONFIG-S11-SOURCE-DOMAIN", access="READ_WRITE",
    java_type="STRING", provider="PROVIDER-SOURCE-METADATA", permission="PERMISSION-VENDOR-CAR",
    transport="TRANSPORT-SOURCE-PROVEN", values=(), consumer="CONSUMER-CLUSTER-NATIVE", risk=25)
CANDIDATE_HIT_METADATA = {
    ("C02", "H7"): {"disposition": {"candidateRevisionId": "CAND-H-008-PROPERTY-CONFIG-METADATA@3", "candidateState": "READ_ONLY_READY", "kind": "CANDIDATE_DERIVATION"}, "promotionProofClaim": _H8_CLAIM},
    ("C03", "S6"): {"disposition": {"candidateRevisionId": "CAND-S-011-SOURCE-DOMAIN@1", "candidateState": "MUTATION_REVIEW", "kind": "CANDIDATE_DERIVATION"}, "promotionProofClaim": _S11_CLAIM},
    ("C02", "S2"): {"disposition": {"candidateRevisionId": "CAND-S-012-REJECTED-SHAPE@1", "candidateState": "REJECTED", "kind": "CANDIDATE_DERIVATION"}, "promotionProofClaim": _S12_CLAIM},
}
CANDIDATE_DISPOSITIONS = {key: value["disposition"] for key, value in CANDIDATE_HIT_METADATA.items()}
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
CORPUS_RE = re.compile(r"^C(?:0[1-9]|1[0-2])$")
QUERY_RE = re.compile(r"^QRY-C(?:0[1-9]|1[0-2])-[A-Z0-9][A-Z0-9-]{0,63}$")
ALIAS_RE = re.compile(r"^ALIAS-[A-Z0-9][A-Z0-9-]{0,63}$")
TOKEN_RE = re.compile(r"^TOKEN-[A-Z0-9][A-Z0-9-]{0,63}$")
FACT_RE = re.compile(r"^FACT-[A-Z0-9][A-Z0-9-]{0,63}$")
HIT_RE = re.compile(r"^HIT-(C(?:0[1-9]|1[0-2]))-(QRY-C(?:0[1-9]|1[0-2])-[A-Z0-9][A-Z0-9-]{0,63})-A([0-9a-f]{12})-S([0-9a-f]{12})-Q([0-9a-f]{12})-L([0-9a-f]{12})-T([0-9a-f]{12})$")
PRIVATE_PATH_RE = re.compile(r"/(?:Users|home|private)/[^\s\"'<>]+|[A-Za-z]:\\Users\\[^\r\n\"'<>]+", re.IGNORECASE)
PRIVATE_IP_RE = re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b")
REVISION_PATTERN = r"(?:[1-9][0-9]{0,8}|1[0-9]{9}|20[0-9]{8}|21[0-3][0-9]{7}|214[0-6][0-9]{6}|2147[0-3][0-9]{5}|21474[0-7][0-9]{4}|214748[0-2][0-9]{3}|2147483[0-5][0-9]{2}|21474836[0-3][0-9]|214748364[0-7])"
CANDIDATE_RE = re.compile(r"^CAND-(?:H|S|NATIVE|PROVIDER)-[0-9]{3}-([A-Z0-9][A-Z0-9-]{0,63})@(" + REVISION_PATTERN + r")$")
SIMPLE_ID_RE = re.compile(r"^(?:ALIAS|TOKEN|FACT|SEL|CONSUMER|RULE|BLOCKER|TOOL|CONFIG|PROVIDER|PERMISSION|TRANSPORT|VALUE|PARAM)-[A-Z0-9][A-Z0-9-]{0,63}$")
OP_ID_RE = re.compile(r"^OP-(?:READ|MUTATE|CLEAR|INVERSE|RESTORE)-[A-Z0-9][A-Z0-9-]{0,63}$")
PROBE_ID_RE = re.compile(r"^PROBE-(?:READ|LIST)-[A-Z0-9][A-Z0-9-]{0,63}$")
VERSION_RE = re.compile(r"^VERSION-[A-Z0-9][A-Z0-9.-]{0,63}$")
VIN_RE = re.compile(r"(?<![A-Z0-9])[A-HJ-NPR-Z0-9]{8}[0-9X][A-HJ-NPR-Z0-9]{8}(?![A-Z0-9])", re.IGNORECASE)
SEMANTIC_MARKER_RE = re.compile(r"(?i)(?:\b(?:vin|serial(?:[-_ ]?(?:number|no))?|gps|latitude|longitude|coordinates?|decompiled|decompilation|raw(?:[-_ ]?(?:dump|payload|token|bytes|content))?|source(?:[-_ ]?(?:line|path|code|body|file|dump)))\b|\bs/?n\s*[:=])")
URI_PATH_RE = re.compile(r"(?i)(?:[a-z][a-z0-9+.-]*://|file:/|(?:^|\s)/(?:[^/\s]+/)*[^/\s]+|[A-Z]:\\|\\\\[^\\\s]+\\)")
COORDINATE_RE = re.compile(r"(?<!\d)[+-]?\d{1,2}\.\d{3,}\s*[,/]\s*[+-]?\d{1,3}\.\d{3,}(?!\d)")
CREDENTIAL_RE = re.compile(r"(?i)(?:\b(?:password|passwd|secret|credential|api[-_ ]?key|access[-_ ]?token|token|bearer|authorization)\b\s*[:=]?|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{36}|sk-[A-Za-z0-9_-]{20,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)")
PII_RE = re.compile(r"(?i)(?:[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|\b\d{3}-\d{2}-\d{4}\b|\b(?:name|full[-_ ]?name|email|passport|tax[-_ ]?id|date[-_ ]?of[-_ ]?birth|phone|address)\b\s*[:=])")
APPROVED_SCALARS = AVAILABILITY | CANDIDATE_STATES | frozenset(ABSOLUTE_REJECTS) | frozenset({SCHEMA_ID, "NO_MATCH", "READ_ONLY", "READ_WRITE", "WRITE", "INT", "BOOLEAN", "DOUBLE", "STRING", "BYTES", "REQUIRED", "NOT_APPLICABLE", "APP_OWNED", "PHYSICAL_DURABLE", "DIAGNOSTIC_TEMP", "CANDIDATE_DERIVATION", "READ_ONLY_PROBE", "BLOCKER", "DUPLICATE_OF", "OUT_OF_SCOPE"})

def _normalize(value: Any) -> Any:
    if value is None or isinstance(value, bool): return value
    if isinstance(value, int):
        if not -(1 << 63) <= value <= (1 << 63) - 1: raise ValueError("integer is outside signed-64 range")
        return value
    if isinstance(value, float): raise ValueError("floating-point values are forbidden")
    if isinstance(value, str):
        normalized = unicodedata.normalize("NFC", value); normalized.encode("utf-8", "strict"); return normalized
    if isinstance(value, list): return [_normalize(item) for item in value]
    if isinstance(value, dict):
        if not all(isinstance(key, str) and key.isascii() for key in value): raise ValueError("object keys must be ASCII strings")
        normalized = [(_normalize(key), _normalize(item)) for key, item in value.items()]
        if len({key for key, _ in normalized}) != len(normalized): raise ValueError("duplicate normalized JSON object key")
        return dict(normalized)
    raise ValueError(f"unsupported canonical JSON type: {type(value).__name__}")

def canonical_bytes(value: Any) -> bytes:
    return json.dumps(_normalize(value), ensure_ascii=False, allow_nan=False, sort_keys=True, separators=(",", ":")).encode("utf-8")

def canonical_sha256(value: Any) -> str: return hashlib.sha256(canonical_bytes(value)).hexdigest()

def _checked_repository_root(project_root: Path) -> Path:
    root, current = Path(os.path.abspath(project_root)), Path(Path(os.path.abspath(project_root)).anchor)
    for part in ("", *root.parts[1:]):
        if part: current /= part
        info = os.lstat(current)
        if stat.S_ISLNK(info.st_mode): raise ValueError("fixed repository root has a symbolic-link ancestor")
        if not stat.S_ISDIR(info.st_mode): raise ValueError("fixed repository root is not a no-follow directory")
    return root

def read_repository_bytes(path: Path, project_root: Path = PROJECT_ROOT) -> bytes:
    root, target = _checked_repository_root(project_root), Path(os.path.abspath(path))
    try: relative = target.relative_to(root)
    except ValueError as error: raise ValueError("repository input escapes the fixed root") from error
    if not relative.parts: raise ValueError("repository input must be a file below the fixed root")
    def checked_leaf() -> tuple[Path, os.stat_result]:
        _checked_repository_root(root); current = root
        for part in relative.parts[:-1]:
            current /= part; info = os.lstat(current)
            if stat.S_ISLNK(info.st_mode) or not stat.S_ISDIR(info.st_mode): raise ValueError("repository input ancestor is not a no-follow directory")
        leaf = current / relative.parts[-1]; info = os.lstat(leaf)
        if stat.S_ISLNK(info.st_mode) or not stat.S_ISREG(info.st_mode): raise ValueError("repository input leaf is not a no-follow regular file")
        return leaf, info
    identity = lambda item: (item.st_dev, item.st_ino, item.st_size, item.st_mtime_ns, item.st_ctime_ns)
    leaf, path_before = checked_leaf(); descriptor = os.open(leaf, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    try:
        before = os.fstat(descriptor)
        if not stat.S_ISREG(before.st_mode): raise ValueError("repository input descriptor is not regular")
        payload = b"".join(iter(lambda: os.read(descriptor, 1048576), b"")); after = os.fstat(descriptor)
        if identity(before) != identity(after): raise ValueError("repository input changed while reading")
    finally: os.close(descriptor)
    _, path_after = checked_leaf()
    if identity(path_before) != identity(path_after) or (before.st_dev, before.st_ino) != (path_after.st_dev, path_after.st_ino): raise ValueError("repository input path changed while reading")
    return payload

def file_sha256(path: Path, project_root: Path = PROJECT_ROOT) -> str:
    return hashlib.sha256(read_repository_bytes(path, project_root)).hexdigest()

def _object_no_duplicates(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result: raise ValueError("duplicate JSON object key")
        result[key] = value
    return result

def load_json(path: Path, project_root: Path = PROJECT_ROOT) -> dict[str, Any]:
    value = json.loads(read_repository_bytes(path, project_root), object_pairs_hook=_object_no_duplicates, parse_float=lambda _: (_ for _ in ()).throw(ValueError("floats forbidden")))
    if not isinstance(value, dict): raise ValueError("parent JSON root must be an object")
    return value

def _typed_id_payload(value: str) -> str | None:
    candidate = CANDIDATE_RE.fullmatch(value)
    if candidate: return candidate.group(1)
    hit = HIT_RE.fullmatch(value)
    if hit: return hit.group(2).split("-", 2)[2]
    if QUERY_RE.fullmatch(value): return value.split("-", 2)[2]
    if any(pattern.fullmatch(value) for pattern in (SIMPLE_ID_RE, OP_ID_RE, PROBE_ID_RE, VERSION_RE)):
        return value.split("-", 1)[1]
    return None

def _contains_ip(value: str) -> bool:
    for token in re.findall(r"[0-9A-Fa-f:.]{3,}", value):
        try: ipaddress.ip_address(token.strip("[].,;()")); return True
        except ValueError: pass
    return False

def _looks_like_phone_or_account(value: str) -> bool:
    for token in re.findall(r"(?<![A-Za-z0-9])\+?[0-9][0-9 .()-]{8,}[0-9](?![A-Za-z0-9])", value):
        digits = re.sub(r"\D", "", token)
        if 10 <= len(digits) <= 19: return True
    return False

def validate_semantic_privacy(value: Any) -> None:
    if isinstance(value, str):
        if value in APPROVED_SCALARS or SHA256_RE.fullmatch(value) or CORPUS_RE.fullmatch(value): return
        inspected = _typed_id_payload(value) or value
        if (VIN_RE.search(inspected) or SEMANTIC_MARKER_RE.search(inspected) or URI_PATH_RE.search(inspected) or
                PRIVATE_PATH_RE.search(inspected) or COORDINATE_RE.search(inspected) or _contains_ip(inspected) or
                CREDENTIAL_RE.search(inspected) or PII_RE.search(inspected) or _looks_like_phone_or_account(inspected)):
            raise ValueError("coverage publication contains semantically private or raw content")
    elif isinstance(value, dict):
        for item in value.values(): validate_semantic_privacy(item)
    elif isinstance(value, list):
        for item in value: validate_semantic_privacy(item)

def build_scanner_identity(binary_bytes: bytes | None = None) -> dict[str, Any]:
    binary_hash = hashlib.sha256(read_repository_bytes(SCRIPT_PATH) if binary_bytes is None else binary_bytes).hexdigest()
    preimage = {"binaryFileSha256": binary_hash, "configFileSha256": EMPTY_SHA256, "toolId": TOOL_ID, "versionId": VERSION_ID}
    return {**preimage, "scannerIdentitySha256": canonical_sha256(preimage)}

def build_query_definition(corpus_id: str, scanner_hash: str) -> dict[str, Any]:
    preimage = {"parameters": [], "queryId": QUERY_IDS[corpus_id], "scannerIdentitySha256": scanner_hash, "tokenIds": QUERY_TOKENS[corpus_id]}
    return {**preimage, "queryDefinitionSha256": canonical_sha256(preimage)}

def _sorted_unique(values: Iterable[str]) -> list[str]:
    source = list(values)
    if any(not isinstance(value, str) for value in source): raise ValueError("ID arrays must contain strings")
    return sorted(set(source))

def _location_alias(corpus_id: str, private_metadata: Any) -> str:
    return f"ALIAS-{corpus_id}-LOCATION-{canonical_sha256(private_metadata)[:16].upper()}"

def out_of_scope() -> dict[str, str]: return {"kind": "OUT_OF_SCOPE", "ruleId": "RULE-PARENT-EVIDENCE-SEALED"}

def make_hit(corpus_id: str, artifact: dict[str, Any], scanner: dict[str, Any], query: dict[str, Any], location_alias: str,
             token_ids: Iterable[str], fact_ids: Iterable[str], disposition: dict[str, Any] | None = None,
             promotion_proof_claim: dict[str, Any] | None = None) -> dict[str, Any]:
    artifact_hash = artifact["artifactSha256"]
    if not isinstance(artifact_hash, str) or not SHA256_RE.fullmatch(artifact_hash): raise ValueError("hits require an acquired artifact hash")
    tokens, facts = _sorted_unique(token_ids), _sorted_unique(fact_ids)
    claim = copy.deepcopy(promotion_proof_claim)
    selectors = [] if claim is None or claim["selectorId"] is None else [claim["selectorId"]]
    consumers = [] if claim is None or claim["consumerId"] is None else [claim["consumerId"]]
    location_hash = canonical_sha256({"artifactAlias": artifact["artifactAlias"], "locationAlias": location_alias})
    token_hash = canonical_sha256(tokens)
    equivalence_hash = canonical_sha256({"consumerIds": consumers, "corpusId": corpus_id, "normalizedFactIds": facts, "queryDefinitionSha256": query["queryDefinitionSha256"], "selectorIds": selectors})
    hit_id = f"HIT-{corpus_id}-{query['queryId']}-A{artifact_hash[:12]}-S{scanner['scannerIdentitySha256'][:12]}-Q{query['queryDefinitionSha256'][:12]}-L{location_hash[:12]}-T{token_hash[:12]}"
    return {"consumerIds": consumers, "disposition": copy.deepcopy(disposition) if disposition else out_of_scope(),
            "duplicateEquivalenceSha256": equivalence_hash, "hitId": hit_id, "locationAlias": location_alias,
            "locationSha256": location_hash, "normalizedFactIds": facts, "promotionProofClaim": claim,
            "selectorIds": selectors, "tokenIds": tokens, "tokenSetSha256": token_hash}

def canonicalize_duplicates(hits: list[dict[str, Any]]) -> list[dict[str, Any]]:
    result, by_id, classes = copy.deepcopy(hits), {}, {}
    for hit in result:
        if hit["hitId"] in by_id:
            if canonical_bytes(hit) != canonical_bytes(by_id[hit["hitId"]]): raise ValueError("hit ID collision with unequal content")
            raise ValueError("duplicate hit ID")
        by_id[hit["hitId"]] = hit; classes.setdefault(hit["duplicateEquivalenceSha256"], []).append(hit)
    for members in classes.values():
        members.sort(key=lambda item: item["hitId"])
        if len({canonical_bytes(item["promotionProofClaim"]) for item in members}) != 1: raise ValueError("duplicate class promotion proof claims differ")
        if len(members) > 1 and members[0]["promotionProofClaim"] is not None: raise ValueError("candidate promotion proof cannot be discarded as a duplicate")
        if members[0]["disposition"].get("kind") == "DUPLICATE_OF": raise ValueError("canonical hit must start with a substantive disposition")
        for duplicate in members[1:]: duplicate["disposition"] = {"canonicalHitId": members[0]["hitId"], "kind": "DUPLICATE_OF"}
    return sorted(result, key=lambda item: item["hitId"])

def _evidence_hits(evidence: dict[str, Any], corpus_id: str, artifact: dict[str, Any], scanner: dict[str, Any], query: dict[str, Any]) -> list[dict[str, Any]]:
    hits = []
    for row in evidence.get("evidence", []):
        evidence_id = row.get("id")
        if not isinstance(evidence_id, str) or not re.fullmatch(r"(?:H[0-7]|S(?:[0-9]|10))", evidence_id): raise ValueError("unexpected parent evidence ID")
        locations = []
        for citation in row.get("citations", []):
            relative = citation.get("path")
            if not isinstance(relative, str): raise ValueError("invalid parent citation metadata")
            root, separator, _ = relative.partition("/")
            if root not in SOURCE_ROOTS[corpus_id]: continue
            if not separator: raise ValueError("invalid target citation metadata")
            locations.append({"artifactSha256": citation.get("artifact_sha256"), "line": citation.get("line"), "lineSha256": citation.get("line_sha256"), "privateRelativeLocation": relative})
        if locations:
            alias = _location_alias(corpus_id, {"evidenceId": evidence_id, "locations": sorted(locations, key=canonical_bytes)})
            metadata = CANDIDATE_HIT_METADATA.get((corpus_id, evidence_id))
            hits.append(make_hit(corpus_id, artifact, scanner, query, alias, [f"TOKEN-{evidence_id}"], [f"FACT-PARENT-{evidence_id}"],
                None if metadata is None else metadata["disposition"], None if metadata is None else metadata["promotionProofClaim"]))
    return canonicalize_duplicates(hits)

def _native_hits(native: dict[str, Any], artifact: dict[str, Any], scanner: dict[str, Any], query: dict[str, Any]) -> list[dict[str, Any]]:
    hits = []
    for function in native.get("functions", []):
        if function.get("scope") != "PRIMARY_T3" or function.get("function_role") != "IMPLEMENTATION": continue
        private = {"demangledName": function.get("demangled_name"), "functionRole": function.get("function_role"), "matchedTerms": function.get("matched_terms"), "presence": function.get("comparison", {}).get("presence"), "scope": function.get("scope")}
        hits.append(make_hit("C04", artifact, scanner, query, _location_alias("C04", private), ["TOKEN-S8"], ["FACT-PARENT-S8"]))
    return canonicalize_duplicates(hits)

def validate_availability(status: str, artifact_sha256: str | None, search_completed: bool, hits: list[Any], zero_hit: bool | None, outcome: str | None) -> None:
    if status not in AVAILABILITY or type(search_completed) is not bool or not isinstance(hits, list) or len(hits) > 65535: raise ValueError("invalid availability")
    if zero_hit is not None and type(zero_hit) is not bool: raise ValueError("invalid zero-hit value")
    acquired = isinstance(artifact_sha256, str) and SHA256_RE.fullmatch(artifact_sha256) is not None
    if status == "AVAILABLE":
        expected_zero = not hits
        if not acquired or search_completed is not True or zero_hit is not expected_zero or outcome != ("NO_MATCH" if expected_zero else None): raise ValueError("AVAILABLE branch mismatch")
    elif status in {"UNAVAILABLE", "UNSEARCHED"}:
        if artifact_sha256 is not None or search_completed is not False or hits or zero_hit is not None or outcome is not None: raise ValueError(f"{status} branch mismatch")
    elif status == "BUDGET_STOPPED":
        if not acquired or search_completed is not False or zero_hit is not None or outcome is not None: raise ValueError("BUDGET_STOPPED branch mismatch")
    elif not acquired or search_completed is not False or hits or zero_hit is not None or outcome is not None:
        raise ValueError("ACQUIRED_UNREVIEWED branch mismatch")

def make_coverage_entry(corpus_id: str, status: str, alias: str, artifact_hash: str | None, scanner: dict[str, Any], query: dict[str, Any], hits: list[dict[str, Any]]) -> dict[str, Any]:
    completed, zero = status == "AVAILABLE", not hits if status == "AVAILABLE" else None
    outcome = "NO_MATCH" if completed and not hits else None
    validate_availability(status, artifact_hash, completed, hits, zero, outcome)
    return {"artifactAlias": alias, "artifactSha256": artifact_hash, "corpusId": corpus_id, "hits": hits, "query": query, "scanner": scanner, "status": status, "zeroHit": zero, "zeroHitOutcome": outcome}

def _parent_state(project_root: Path) -> dict[str, Any]:
    evidence_path, completeness_path = project_root / EVIDENCE_RELATIVE, project_root / COMPLETENESS_RELATIVE
    zero_path, native_path = project_root / ZERO_HIT_RELATIVE, project_root / NATIVE_RELATIVE
    evidence, completeness, native = load_json(evidence_path, project_root), load_json(completeness_path, project_root), load_json(native_path, project_root)
    if evidence.get("corpus_verdict") != "NOT_EXHAUSTIVE" or completeness.get("corpus", {}).get("verdict") != "NOT_EXHAUSTIVE": raise ValueError("parent corpus honesty verdict changed")
    zero_text = read_repository_bytes(zero_path, project_root).decode("utf-8", "strict")
    return {"evidence": evidence, "evidenceSha256": file_sha256(evidence_path, project_root), "native": native, "nativeSha256": file_sha256(native_path, project_root),
            "selectedAliases": {row.get("alias") for row in completeness.get("corpus", {}).get("selected_artifacts", [])},
            "inventory": {row.get("id"): row for row in evidence.get("inventory", [])},
            "zeroIds": set(re.findall(r"^ZERO ([A-Z0-9-]+):", zero_text, re.MULTILINE))}

def derive_verdict(entries: list[dict[str, Any]]) -> str:
    complete = isinstance(entries, list) and len(entries) == len(CORPUS_IDS) and {row.get("corpusId") for row in entries if isinstance(row, dict)} == set(CORPUS_IDS) and all(row.get("status") == "AVAILABLE" for row in entries)
    open_derivation = any(hit.get("disposition", {}).get("candidateState") in OPEN_CANDIDATE_STATES for row in entries if isinstance(row, dict) for hit in row.get("hits", []) if isinstance(hit, dict) and hit.get("disposition", {}).get("kind") == "CANDIDATE_DERIVATION")
    return "READY_DATA" if complete and not open_derivation else "NOT_EXHAUSTIVE"

def build_document(project_root: Path = PROJECT_ROOT, scanner_binary_bytes: bytes | None = None) -> dict[str, Any]:
    state, scanner, coverage = _parent_state(Path(os.path.abspath(project_root))), build_scanner_identity(scanner_binary_bytes), []
    for corpus_id in CORPUS_IDS:
        query = build_query_definition(corpus_id, scanner["scannerIdentitySha256"])
        available = corpus_id in REQUIRED_ARTIFACT_ALIASES and REQUIRED_ARTIFACT_ALIASES[corpus_id] <= state["selectedAliases"]
        if available:
            artifact_hash = state["nativeSha256"] if corpus_id == "C04" else state["evidenceSha256"]
            artifact = {"artifactAlias": ARTIFACT_ALIASES[corpus_id], "artifactSha256": artifact_hash}
            hits = _native_hits(state["native"], artifact, scanner, query) if corpus_id == "C04" else _evidence_hits(state["evidence"], corpus_id, artifact, scanner, query)
            status = "AVAILABLE"
        else:
            expected = UNAVAILABLE_INVENTORY_IDS.get(corpus_id, frozenset())
            status = "UNAVAILABLE" if expected and all(state["inventory"].get(item, {}).get("zero_hit") is True and item in state["zeroIds"] for item in expected) else "UNSEARCHED"
            artifact_hash, hits = None, []
        coverage.append(make_coverage_entry(corpus_id, status, ARTIFACT_ALIASES[corpus_id], artifact_hash, scanner, query, hits))
    draft = {"entries": coverage, "expansionVerdict": derive_verdict(coverage), "schemaId": SCHEMA_ID}
    document = {**draft, "selfSha256": canonical_sha256(draft)}; validate_document(document); return document

def _validate_disposition(disposition: dict[str, Any]) -> None:
    shapes = {"CANDIDATE_DERIVATION": {"kind", "candidateRevisionId", "candidateState"}, "READ_ONLY_PROBE": {"kind", "probeId"}, "BLOCKER": {"kind", "blockerId"}, "DUPLICATE_OF": {"kind", "canonicalHitId"}, "OUT_OF_SCOPE": {"kind", "ruleId"}}
    if not isinstance(disposition, dict) or disposition.get("kind") not in shapes or set(disposition) != shapes[disposition["kind"]]: raise ValueError("hit must have exactly one closed typed disposition")
    field_patterns = {"CANDIDATE_DERIVATION": ("candidateRevisionId", CANDIDATE_RE), "READ_ONLY_PROBE": ("probeId", re.compile(r"^PROBE-(?:READ|LIST)-[A-Z0-9][A-Z0-9-]{0,63}$")), "BLOCKER": ("blockerId", re.compile(r"^BLOCKER-[A-Z0-9][A-Z0-9-]{0,63}$")), "DUPLICATE_OF": ("canonicalHitId", HIT_RE), "OUT_OF_SCOPE": ("ruleId", re.compile(r"^RULE-[A-Z0-9][A-Z0-9-]{0,63}$"))}
    field, pattern = field_patterns[disposition["kind"]]
    if not isinstance(disposition[field], str) or not pattern.fullmatch(disposition[field]): raise ValueError("invalid typed disposition reference")
    if disposition["kind"] == "CANDIDATE_DERIVATION" and disposition["candidateState"] not in CANDIDATE_STATES: raise ValueError("invalid candidate derivation state")

def _optional_id(claim: dict[str, Any], field: str, pattern: str) -> None:
    value = claim[field]
    if value is not None and (not isinstance(value, str) or re.fullmatch(pattern, value) is None): raise ValueError(f"invalid promotion proof {field}")

def _validate_claim(claim: Any) -> None:
    if not isinstance(claim, dict) or set(claim) != CLAIM_FIELDS: raise ValueError("invalid closed promotion proof claim")
    rejects = claim["absoluteRejects"]
    if not isinstance(rejects, list) or len(rejects) > 6 or any(not isinstance(item, str) or item not in ABSOLUTE_REJECTS for item in rejects) or len(set(rejects)) != len(rejects): raise ValueError("invalid promotion proof absolute rejects")
    if rejects != sorted(rejects, key=ABSOLUTE_REJECTS.index): raise ValueError("promotion proof absolute rejects are not ordered")
    enum_rules = {"access": {None, "READ_ONLY", "READ_WRITE", "WRITE"}, "javaType": {None, "INT", "BOOLEAN", "DOUBLE", "STRING", "BYTES"}, "ownership": {None, "APP_OWNED", "PHYSICAL_DURABLE", "DIAGNOSTIC_TEMP"}, "clearPolicy": {"REQUIRED", "NOT_APPLICABLE"}}
    if any((claim[field] is None and None not in allowed) or (claim[field] is not None and (not isinstance(claim[field], str) or claim[field] not in allowed)) for field, allowed in enum_rules.items()): raise ValueError("invalid promotion proof enum")
    patterns = {"selectorId": r"^SEL-[A-Z0-9][A-Z0-9-]{0,63}$", "readProbeId": r"^PROBE-(?:READ|LIST)-[A-Z0-9][A-Z0-9-]{0,63}$", "mutationOperationId": r"^OP-MUTATE-[A-Z0-9][A-Z0-9-]{0,63}$", "configId": r"^CONFIG-[A-Z0-9][A-Z0-9-]{0,63}$", "providerId": r"^PROVIDER-[A-Z0-9][A-Z0-9-]{0,63}$", "permissionId": r"^PERMISSION-[A-Z0-9][A-Z0-9-]{0,63}$", "transportId": r"^TRANSPORT-[A-Z0-9][A-Z0-9-]{0,63}$", "priorReadOperationId": r"^OP-READ-[A-Z0-9][A-Z0-9-]{0,63}$", "readBackOperationId": r"^OP-READ-[A-Z0-9][A-Z0-9-]{0,63}$", "clearOperationId": r"^OP-CLEAR-[A-Z0-9][A-Z0-9-]{0,63}$", "consumerId": r"^CONSUMER-[A-Z0-9][A-Z0-9-]{0,63}$"}
    for field, pattern in patterns.items(): _optional_id(claim, field, pattern)
    for field, pattern, maximum in (("boundedDomainValueIds", r"^VALUE-[A-Z0-9][A-Z0-9-]{0,63}$", 256), ("inverseOperationIds", r"^OP-(?:INVERSE|RESTORE)-[A-Z0-9][A-Z0-9-]{0,63}$", 64)):
        values = claim[field]
        if not isinstance(values, list) or len(values) > maximum or any(not isinstance(item, str) or re.fullmatch(pattern, item) is None for item in values) or len(set(values)) != len(values): raise ValueError(f"invalid promotion proof {field}")
    if claim["boundedDomainValueIds"] != sorted(claim["boundedDomainValueIds"]): raise ValueError("promotion proof values are not ordered")
    if claim["risk"] is not None and (type(claim["risk"]) is not int or not 0 <= claim["risk"] <= 100): raise ValueError("invalid promotion proof risk")

def _candidate_metadata(candidate_id: str) -> tuple[tuple[str, str], dict[str, Any]] | None:
    return next(((key, metadata) for key, metadata in CANDIDATE_HIT_METADATA.items() if metadata["disposition"]["candidateRevisionId"] == candidate_id), None)

def _validate_hit(hit: dict[str, Any], entry: dict[str, Any]) -> None:
    required = {"consumerIds", "disposition", "duplicateEquivalenceSha256", "hitId", "locationAlias", "locationSha256", "normalizedFactIds", "promotionProofClaim", "selectorIds", "tokenIds", "tokenSetSha256"}
    if not isinstance(hit, dict) or set(hit) != required: raise ValueError("invalid closed hit shape")
    _validate_disposition(hit["disposition"]); kind, claim = hit["disposition"]["kind"], hit["promotionProofClaim"]
    if kind == "CANDIDATE_DERIVATION":
        _validate_claim(claim)
        source = _candidate_metadata(hit["disposition"]["candidateRevisionId"])
        if source is None or hit["disposition"] != source[1]["disposition"] or claim != source[1]["promotionProofClaim"]: raise ValueError("candidate promotion proof claim does not match source metadata")
        if entry["corpusId"] != source[0][0] or hit["tokenIds"] != [f"TOKEN-{source[0][1]}"] or hit["normalizedFactIds"] != [f"FACT-PARENT-{source[0][1]}"]: raise ValueError("candidate promotion proof is not bound to its parent evidence hit")
    elif claim is not None: raise ValueError("non-candidate promotion proof claim must be null")
    expected_selectors = [] if claim is None or claim["selectorId"] is None else [claim["selectorId"]]
    expected_consumers = [] if claim is None or claim["consumerId"] is None else [claim["consumerId"]]
    if hit["selectorIds"] != expected_selectors or hit["consumerIds"] != expected_consumers: raise ValueError("promotion proof selector/consumer cardinality or equality mismatch")
    match = HIT_RE.fullmatch(hit["hitId"])
    if not match or match.group(1) != entry["corpusId"] or match.group(2) != entry["query"]["queryId"]: raise ValueError("hit corpus/query binding mismatch")
    expected_location = canonical_sha256({"artifactAlias": entry["artifactAlias"], "locationAlias": hit["locationAlias"]})
    expected_equivalence = canonical_sha256({"consumerIds": hit["consumerIds"], "corpusId": entry["corpusId"], "normalizedFactIds": hit["normalizedFactIds"], "queryDefinitionSha256": entry["query"]["queryDefinitionSha256"], "selectorIds": hit["selectorIds"]})
    expected_token_set = canonical_sha256(hit["tokenIds"])
    fragments = (entry["artifactSha256"][:12], entry["scanner"]["scannerIdentitySha256"][:12], entry["query"]["queryDefinitionSha256"][:12], expected_location[:12], expected_token_set[:12])
    if match.groups()[2:] != fragments: raise ValueError("hit ID hash fragment mismatch")
    if hit["locationSha256"] != expected_location or hit["tokenSetSha256"] != expected_token_set or hit["duplicateEquivalenceSha256"] != expected_equivalence: raise ValueError("hit derived hash mismatch")
    arrays = (("normalizedFactIds", FACT_RE), ("selectorIds", re.compile(r"^SEL-[A-Z0-9][A-Z0-9-]{0,63}$")), ("consumerIds", re.compile(r"^CONSUMER-[A-Z0-9][A-Z0-9-]{0,63}$")), ("tokenIds", TOKEN_RE))
    if not ALIAS_RE.fullmatch(hit["locationAlias"]) or not all(isinstance(hit[key], str) and SHA256_RE.fullmatch(hit[key]) for key in ("locationSha256", "tokenSetSha256", "duplicateEquivalenceSha256")): raise ValueError("invalid hit identity")
    for key, pattern in arrays:
        if not isinstance(hit[key], list) or hit[key] != _sorted_unique(hit[key]) or not all(pattern.fullmatch(item) for item in hit[key]): raise ValueError(f"invalid {key}")

def validate_document(document: dict[str, Any]) -> None:
    validate_semantic_privacy(document)
    if not isinstance(document, dict) or document.get("schemaId") != SCHEMA_ID or set(document) != {"entries", "expansionVerdict", "schemaId", "selfSha256"}: raise ValueError("invalid coverage root")
    entries = document["entries"]
    if not isinstance(entries, list) or len(entries) != 12 or [row.get("corpusId") for row in entries if isinstance(row, dict)] != list(CORPUS_IDS): raise ValueError("coverage root must contain C01-C12 exactly once in order")
    uniqueness, all_hits, classes = set(), {}, {}
    entry_fields = {"artifactAlias", "artifactSha256", "corpusId", "hits", "query", "scanner", "status", "zeroHit", "zeroHitOutcome"}
    scanner_fields = {"binaryFileSha256", "configFileSha256", "scannerIdentitySha256", "toolId", "versionId"}
    query_fields = {"parameters", "queryDefinitionSha256", "queryId", "scannerIdentitySha256", "tokenIds"}
    for row in entries:
        if set(row) != entry_fields: raise ValueError("invalid closed coverage shape")
        corpus_id, scanner, query = row["corpusId"], row["scanner"], row["query"]
        if not CORPUS_RE.fullmatch(corpus_id) or row["artifactAlias"] != ARTIFACT_ALIASES[corpus_id] or not ALIAS_RE.fullmatch(row["artifactAlias"]): raise ValueError("fixed corpus/artifact alias mismatch")
        if not isinstance(scanner, dict) or set(scanner) != scanner_fields or scanner["toolId"] != TOOL_ID or scanner["versionId"] != VERSION_ID or scanner["configFileSha256"] != EMPTY_SHA256: raise ValueError("fixed scanner identity mismatch")
        scanner_preimage = {key: scanner[key] for key in ("binaryFileSha256", "configFileSha256", "toolId", "versionId")}
        if not all(isinstance(scanner[key], str) and SHA256_RE.fullmatch(scanner[key]) for key in ("binaryFileSha256", "configFileSha256", "scannerIdentitySha256")) or scanner["scannerIdentitySha256"] != canonical_sha256(scanner_preimage): raise ValueError("scanner identity hash mismatch")
        if not isinstance(query, dict) or set(query) != query_fields or query["queryId"] != QUERY_IDS[corpus_id] or query["tokenIds"] != QUERY_TOKENS[corpus_id] or query["parameters"] != [] or not QUERY_RE.fullmatch(query["queryId"]): raise ValueError("fixed query definition mismatch")
        query_preimage = {key: query[key] for key in ("parameters", "queryId", "scannerIdentitySha256", "tokenIds")}
        if query["scannerIdentitySha256"] != scanner["scannerIdentitySha256"] or query["queryDefinitionSha256"] != canonical_sha256(query_preimage): raise ValueError("query definition hash mismatch")
        validate_availability(row["status"], row["artifactSha256"], row["status"] == "AVAILABLE", row["hits"], row["zeroHit"], row["zeroHitOutcome"])
        key = (corpus_id, row["artifactAlias"], row["artifactSha256"] or "", query["queryId"], query["queryDefinitionSha256"], scanner["scannerIdentitySha256"])
        if key in uniqueness: raise ValueError("duplicate coverage uniqueness tuple")
        uniqueness.add(key)
        if row["hits"] != sorted(row["hits"], key=lambda item: item["hitId"]): raise ValueError("hits are not canonically sorted")
        for hit in row["hits"]:
            _validate_hit(hit, row)
            if hit["hitId"] in all_hits: raise ValueError("duplicate global hit ID")
            all_hits[hit["hitId"]] = hit; classes.setdefault(hit["duplicateEquivalenceSha256"], []).append(hit)
    for members in classes.values():
        members.sort(key=lambda item: item["hitId"])
        if len({canonical_bytes(item["promotionProofClaim"]) for item in members}) != 1: raise ValueError("duplicate class promotion proof claims differ")
        if members[0]["disposition"]["kind"] == "DUPLICATE_OF": raise ValueError("minimal duplicate representative is not canonical")
        for duplicate in members[1:]:
            if duplicate["disposition"] != {"canonicalHitId": members[0]["hitId"], "kind": "DUPLICATE_OF"}: raise ValueError("duplicate must point directly to the minimal representative")
    actual = [(hit["disposition"]["candidateRevisionId"], hit["disposition"]["candidateState"]) for hit in all_hits.values() if hit["disposition"]["kind"] == "CANDIDATE_DERIVATION"]
    expected = [(metadata["disposition"]["candidateRevisionId"], metadata["disposition"]["candidateState"]) for metadata in CANDIDATE_HIT_METADATA.values()]
    if len(actual) != len(expected) or set(actual) != set(expected): raise ValueError("candidate derivation coverage is not closed")
    if document["expansionVerdict"] != derive_verdict(entries): raise ValueError("dishonest expansion verdict")
    draft = {key: value for key, value in document.items() if key != "selfSha256"}
    if not isinstance(document["selfSha256"], str) or document["selfSha256"] != canonical_sha256(draft): raise ValueError("coverage self hash mismatch")

def render_document(document: dict[str, Any]) -> bytes: validate_document(document); return canonical_bytes(document)

def _checked_output_path(project_root: Path) -> tuple[Path, Path]:
    root = _checked_repository_root(project_root)
    relative = OUTPUT_RELATIVE
    if relative.is_absolute() or any(part in {"", ".", ".."} for part in relative.parts): raise ValueError("canonical coverage output path is invalid")
    components, current = [root], root
    for part in relative.parts: current = current / part; components.append(current)
    for index, component in enumerate(components):
        try: mode = os.lstat(component).st_mode
        except FileNotFoundError:
            if index == 0: raise ValueError("trusted project root does not exist")
            break
        if stat.S_ISLNK(mode): raise ValueError("canonical coverage output cannot contain a symbolic link")
        if index < len(components) - 1 and not stat.S_ISDIR(mode): raise ValueError("canonical coverage output ancestor is not a directory")
        if index == len(components) - 1 and not stat.S_ISREG(mode): raise ValueError("canonical coverage output is not a regular file")
    return root, root / relative

def write_default_output(project_root: Path = PROJECT_ROOT) -> Path:
    root, output = _checked_output_path(project_root)
    output.parent.mkdir(parents=True, exist_ok=True)
    root, output = _checked_output_path(root)
    payload = render_document(build_document(root))
    _checked_output_path(root)
    flags = os.O_WRONLY | os.O_CREAT | os.O_TRUNC | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(output, flags, 0o644)
    try:
        if not stat.S_ISREG(os.fstat(descriptor).st_mode): raise ValueError("canonical coverage output is not a regular file")
        with os.fdopen(descriptor, "wb", closefd=False) as stream: stream.write(payload)
    finally: os.close(descriptor)
    _checked_output_path(root)
    return output

def _safe_error(error: BaseException) -> str:
    return PRIVATE_IP_RE.sub("<ip>", PRIVATE_PATH_RE.sub("<private-path>", str(error)))

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    try: parser.parse_args(argv); write_default_output()
    except (KeyError, OSError, TypeError, ValueError) as error: parser.error(_safe_error(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
