#!/usr/bin/env python3
"""Index source-backed HUD/sign evidence across named local corpora."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

TEXT_EXTENSIONS = {".aidl", ".java", ".json", ".kt", ".qml", ".txt", ".xml"}
ALIAS = re.compile(r"^[a-z][a-z0-9-]{0,63}$")
UNIX_HOME = re.compile(r"/(?:Users|home)/[^/\s\"'<>]+")
WINDOWS_HOME = re.compile(r"[A-Za-z]:\\Users\\[^\\\r\n\"'<>]+", re.IGNORECASE)
PRIVATE_IP = re.compile(r"\b(?:10(?:\.\d{1,3}){3}|192\.168(?:\.\d{1,3}){2}|172\.(?:1[6-9]|2\d|3[01])(?:\.\d{1,3}){2})\b")

TAXONOMY = [
    ("H0", "Physical HUD switch/config", "CONTROL_OR_GATE", r"setHudSwitchEnabled|HUD_SWITCH_STATUS|HUD_CONFIG_STATUS"),
    ("H1", "HUD navigation-map gate", "CONCRETE_SET_CALL_SITE", r"INSTRUMENT_HUD_NAVIGATION_MAP_(?:SET|CONFIG|STATUS)|Hud00600401300000"),
    ("H2", "Modern HUD service", "SERVICE_INTERFACE", r"ICarHud(?:Service|Manager)|DynamicNavigation|NavigationFusion|SafeDriving"),
    ("H3", "Fusion/request/map-format controls", "CONTROL_OR_GATE", r"HUD_MAP_FORMAT|IMAGE_TEXT_FUSION|DRIVING_FUSION|NAVIGATION_FUSION|INTEGRATE_CONFIG|INTELLIGENT_PROJECTION"),
    ("H4", "Canonical Amap state/profile", "CONCRETE_SET_CALL_SITE", r"AUTONAVI_STANDARD_BROADCAST_SEND|KEY_TYPE_GUIDE_INFO|NaviInfo|sendNaviInfo|startObject\(18\)"),
    ("H5", "Direct instrument icon/distance", "CONCRETE_SET_CALL_SITE", r"INSTRUMENT_(?:GUIDE_INFO_SIMPLE_SET|FRONT_CROSSING_DISTANCE_SET|SEND_NAVI_STATUS_SET)"),
    ("H6", "Road side-channel", "SOURCE_CONSTANT", r"nextRouteName|NEXT_ROAD_NAME|PATHNAME_INFO|ROAD_NAME"),
    ("H7", "HUD topology/status", "SOURCE_CONSTANT", r"ATOM_HUD|HUD_DISPLAY_SCREEN_IDENTIFICATION|TRAFFIC_SIGNS_VISIBLE|ARHUD|HUD_.*STATUS"),
    ("S0", "Waze/VietMap acquisition", "RECORDED_FIELD", r"Waze|VietMap|speedLimit"),
    ("S1", "Statistics ISA value/type/unit/sign", "WRITE_INTENT_CONSTANT", r"STATISTICS_ISA_(?:CURRENT_ROAD_SPEED_LIMIT|ROAD_SPEED_LIMIT_UNIT|TRAFFIC_SIGN_TYPE)"),
    ("S2", "Instrument traffic-sign family", "STATUS_OR_OUTPUT_ONLY", r"INSTRUMENT_(?:FUZZ_)?TRAFFIC_SIGN"),
    ("S3", "ADAS SLA/SLR/RSI outputs", "STATUS_OR_OUTPUT_ONLY", r"ADAS_(?:SLA_OUTPUT_SPEED_LIMIT|SLR_OUTPUT_SEPPD_LIMIT|RSI_SIGNMISC)"),
    ("S4", "SLA/ISA/TSR controls", "CONTROL_OR_GATE", r"ADAS_(?:SMART_SPEED_LIMIT|SPEED_LIMIT_ASSIST|TSR_SPEED_LIMIT_MAP|SLA_STATE_SET|SLR_STATUS_SET)"),
    ("S5", "Setting ISA-map speed-limit family", "WRITE_INTENT_CONSTANT", r"SETTING_ISA_MAP_.*SPEED_LIMIT.*_SET"),
    ("S6", "Modern string property transport", "CONCRETE_SET_CALL_SITE", r"getCarProperty\(|setCarProperty\(|getPropertyConfigs\("),
    ("S7", "trafficmonitor/provider arbitration", "SERVICE_INTERFACE", r"com\.(?:telenav|neusoft)\.app\.isa|com\.byd\.trafficmonitor|IAppTrafficInterface"),
    ("S8", "Native cluster sign consumers", "NATIVE_CONSUMER", r"trafficSignValue|trafficSignType|slaEquip|limitTrafficSignRecognition"),
    ("S9", "Speed reminder thresholds", "REMINDER_THRESHOLD", r"SPEED_REMINDER|SETTING_SPEED_LIMIT_SET|SET_SPEED_REMINDER_SET"),
    ("S10", "Legacy ADAS prompt", "SOURCE_CONSTANT", r"ADAS_TRAFFIC_LIMIT_SPEED_STATUS_PROMPT"),
]

FACT_MATCHERS = [
    ("F-ADAS-SLA-OUTPUT", r"\bADAS_SLA_OUTPUT_SPEED_LIMIT\b"),
    ("F-AMAP-TRANSPORT", r"\bAUTONAVI_STANDARD_BROADCAST_SEND\b"),
    ("F-H1-CALL-SITE", r"bool\.booleanValue\(\)\s*\?\s*2\s*:\s*1"),
    ("F-H1-CONFIG", r"\bINSTRUMENT_HUD_NAVIGATION_MAP_CONFIG\b"),
    ("F-H1-KEY", r"\bINSTRUMENT_HUD_NAVIGATION_MAP_SET\b"),
    ("F-H1-STATUS", r"\bINSTRUMENT_HUD_NAVIGATION_MAP_STATUS\b"),
    ("F-INSTRUMENT-TRAFFIC-SIGN-VALUE", r"\bINSTRUMENT_TRAFFIC_SIGN_IDENTIFY_VALUE\b"),
    ("F-MODERN-PROPERTY-READ", r"\bgetCarProperty\s*\("),
    ("F-MODERN-PROPERTY-WRITE", r"\bsetCarProperty\s*\("),
    ("F-PROVIDER-NEUSOFT", r"\bcom\.neusoft\.app\.isa\b"),
    ("F-PROVIDER-TELENAV", r"\bcom\.telenav\.app\.isa\b"),
    ("F-STATISTICS-VALUE", r"\bSTATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET\b"),
]

INVENTORY_QUERIES = [
    ("C-BYDAUTO-PROVIDER-LIB", r"libbydauto(?:service)?[^/]*\.so$"),
    ("C-ODM-PARTITION", r"(?:^|/)odm(?:/|$)"),
    ("C-PROPERTY-REGISTRY", r"(?:car|vehicle)[^/]*property[^/]*\.(?:db|json|sqlite|xml)$"),
    ("C-PROVIDER-APK", r"(?:telenav|neusoft)[^/]*\.apk$"),
    ("C-QML-RCC", r"\.(?:qml|rcc)$"),
    ("C-SERVICE-CONTEXT", r"(?:^|/)(?:hwservice|service)_contexts$"),
    ("C-SYSTEM-EXT-PARTITION", r"(?:^|/)system_ext(?:/|$)"),
    ("C-VENDOR-BOOT", r"(?:^|/)vendor_boot(?:\.|/|$)"),
    ("C-VENDOR-PARTITION", r"(?:^|/)vendor(?:/|$)"),
]


def sanitize(text: str) -> str:
    return PRIVATE_IP.sub("<vehicle-ip>", UNIX_HOME.sub("<user-home>", WINDOWS_HOME.sub("<user-home>", text)))


def parse_roots(specs: list[str]) -> list[tuple[str, Path]]:
    roots, seen = [], set()
    for spec in specs:
        if "=" not in spec:
            raise ValueError("root must use ALIAS=PATH")
        alias, raw = spec.split("=", 1)
        if not ALIAS.fullmatch(alias) or alias in seen or not raw or ".." in Path(raw).parts:
            raise ValueError(f"invalid or duplicate root: {alias!r}")
        path = Path(raw).expanduser().resolve()
        if not path.is_dir() or path.name.startswith("."):
            raise ValueError(f"root unavailable: {alias}")
        roots.append((alias, path))
        seen.add(alias)
    return sorted(roots, key=lambda row: row[0])


def files_for(roots: list[tuple[str, Path]], text_only: bool) -> list[tuple[str, Path, str]]:
    files = []
    for alias, root in roots:
        for path in root.rglob("*"):
            if path.is_file() and not path.is_symlink() and (not text_only or path.suffix.lower() in TEXT_EXTENSIONS):
                files.append((alias, path, path.relative_to(root).as_posix()))
    return sorted(files, key=lambda row: (row[0], row[2]))


def file_sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _schema_fact(alias: str, relative: str, path: Path, lines: list[str]) -> dict | None:
    if path.name != "NaviInfo.java":
        return None
    accessor = re.compile(r"^\s*public\s+(?:final\s+)?(?:int|String)\s+([A-Za-z][A-Za-z0-9]*)\(\)\s*\{")
    fields = [match.group(1) for line in lines if (match := accessor.match(line))]
    declared = next((index + 1 for index, line in enumerate(lines) if "startObject(18)" in line), None)
    return {
        "declared_field_count": 18 if declared else None,
        "fields": fields,
        "path": f"{alias}/{relative}",
        "speed_fields": [field for field in fields if "speed" in field.lower() or "limit" in field.lower()],
        "start_object_line": declared,
    }


def build_index(content_specs: list[str], inventory_specs: list[str], max_hits: int = 40) -> dict:
    content_roots = parse_roots(content_specs)
    inventory_roots = parse_roots(inventory_specs)
    compiled = [(item, re.compile(item[3], re.IGNORECASE)) for item in TAXONOMY]
    fact_patterns = [(token, re.compile(expression, re.IGNORECASE)) for token, expression in FACT_MATCHERS]
    rows = {item[0]: {"citations": [], "claim": item[1], "classification": item[2], "hit_count": 0, "id": item[0]} for item in TAXONOMY}
    schema_facts, read_errors = [], []
    hashes: dict[Path, str] = {}

    for alias, path, relative in files_for(content_roots, text_only=True):
        try:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError as error:
            read_errors.append({"path": f"{alias}/{relative}", "error": sanitize(str(error))})
            continue
        fact = _schema_fact(alias, relative, path, lines)
        if fact:
            schema_facts.append(fact)
        for number, raw_line in enumerate(lines, 1):
            line = sanitize(raw_line.strip())
            fact_tokens = [token for token, fact_pattern in fact_patterns if fact_pattern.search(line)]
            for (item, pattern) in compiled:
                if pattern.search(line):
                    row = rows[item[0]]
                    row["hit_count"] += 1
                    if len(row["citations"]) < max_hits:
                        if path not in hashes:
                            hashes[path] = file_sha(path)
                        row["citations"].append({
                            "artifact_sha256": hashes[path],
                            "line": number,
                            "line_sha256": hashlib.sha256(line.encode("utf-8")).hexdigest(),
                            "matched_tokens": sorted({item[0], *fact_tokens}),
                            "path": f"{alias}/{relative}",
                        })

    inventory = []
    all_inventory = files_for(inventory_roots, text_only=False)
    for query_id, expression in INVENTORY_QUERIES:
        pattern = re.compile(expression, re.IGNORECASE)
        hits = [f"{alias}/{relative}" for alias, _, relative in all_inventory if pattern.search(relative)]
        inventory.append({"hit_count": len(hits), "hits": hits[:max_hits], "id": query_id, "zero_hit": not hits})

    taxonomy = []
    for item in TAXONOMY:
        row = rows[item[0]]
        row["citations_truncated"] = row["hit_count"] > len(row["citations"])
        row["zero_hit"] = row["hit_count"] == 0
        row["executable"] = False
        taxonomy.append(row)
    return {
        "content_roots": [alias for alias, _ in content_roots],
        "inventory": inventory,
        "inventory_roots": [alias for alias, _ in inventory_roots],
        "read_errors": sorted(read_errors, key=lambda row: row["path"]),
        "schema": "clusternav.re-feature-index/v1",
        "schema_facts": sorted(schema_facts, key=lambda row: row["path"]),
        "taxonomy": taxonomy,
    }


def zero_report(index: dict) -> str:
    zero = [(row["id"], row.get("claim", "required corpus branch")) for section in (index["taxonomy"], index["inventory"]) for row in section if row["zero_hit"]]
    lines = ["ClusterNav HUD/sign RE zero-hit report", "scope=T0-T2 local available corpus", ""]
    lines.extend(f"ZERO {query_id}: {claim}" for query_id, claim in sorted(zero))
    if not zero:
        lines.append("ZERO none")
    lines.extend(["", "A zero hit is evidence of absence only within the named available roots, not proof of platform absence."])
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", action="append", required=True, help="content ALIAS=PATH")
    parser.add_argument("--inventory-root", action="append", required=True, help="filename inventory ALIAS=PATH")
    parser.add_argument("--output", required=True)
    parser.add_argument("--zero-report", required=True)
    parser.add_argument("--max-hits", type=int, default=40)
    args = parser.parse_args()
    try:
        result = build_index(args.root, args.inventory_root, args.max_hits)
        Path(args.output).write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        Path(args.zero_report).write_text(zero_report(result), encoding="utf-8")
    except (OSError, ValueError) as error:
        parser.error(sanitize(str(error)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
