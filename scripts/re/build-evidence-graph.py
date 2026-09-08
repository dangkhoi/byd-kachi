#!/usr/bin/env python3
"""Build a deterministic, non-executable evidence graph from the Java feature index."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

EXPECTED_IDS = [*(f"H{i}" for i in range(8)), *(f"S{i}" for i in range(11))]
MACHINE_PATH = re.compile(r"(?:/Users/|/home/[^<]|[A-Za-z]:\\Users\\)", re.IGNORECASE)
PRIVATE_IP = re.compile(r"\b(?:10(?:\.\d{1,3}){3}|192\.168(?:\.\d{1,3}){2}|172\.(?:1[6-9]|2\d|3[01])(?:\.\d{1,3}){2})\b")


def _pick(rows: dict, family: str, token: str) -> dict | None:
    return next((citation for citation in rows[family]["citations"] if token in citation.get("matched_tokens", [])), None)


def _pick_path(rows: dict, family: str, token: str, suffix: str) -> dict | None:
    return next((citation for citation in rows[family]["citations"] if citation["path"].endswith(suffix) and token in citation.get("matched_tokens", [])), None)


def build_graph(index: dict) -> dict:
    rows = {row["id"]: row for row in index.get("taxonomy", [])}
    if sorted(rows) != sorted(EXPECTED_IDS):
        raise ValueError(f"taxonomy mismatch: {sorted(rows)}")
    serialized = json.dumps(index, ensure_ascii=False)
    if MACHINE_PATH.search(serialized) or PRIVATE_IP.search(serialized):
        raise ValueError("unsanitized machine path or private IP in feature index")

    navi = next((fact for fact in index.get("schema_facts", []) if fact.get("declared_field_count") == 18), None)
    key_facts = {
        "adas_sla_output": _pick(rows, "S3", "F-ADAS-SLA-OUTPUT"),
        "amap_transport": _pick(rows, "H4", "F-AMAP-TRANSPORT"),
        "h1_call_site": _pick(rows, "H1", "F-H1-CALL-SITE"),
        "h1_config": _pick(rows, "H1", "F-H1-CONFIG"),
        "h1_key": _pick(rows, "H1", "F-H1-KEY"),
        "h1_status": _pick(rows, "H1", "F-H1-STATUS"),
        "instrument_traffic_sign_value": _pick(rows, "S2", "F-INSTRUMENT-TRAFFIC-SIGN-VALUE"),
        "modern_property_implementation_read": _pick_path(rows, "S6", "F-MODERN-PROPERTY-READ", "car/s2.java"),
        "modern_property_implementation_write": _pick_path(rows, "S6", "F-MODERN-PROPERTY-WRITE", "car/s2.java"),
        "modern_property_interface_read": _pick_path(rows, "S6", "F-MODERN-PROPERTY-READ", "ICarPropertyManager.java"),
        "modern_property_interface_write": _pick_path(rows, "S6", "F-MODERN-PROPERTY-WRITE", "ICarPropertyManager.java"),
        "provider_neusoft": _pick(rows, "S7", "F-PROVIDER-NEUSOFT"),
        "provider_telenav": _pick(rows, "S7", "F-PROVIDER-TELENAV"),
        "statistics_value": _pick(rows, "S1", "F-STATISTICS-VALUE"),
    }
    complete_available = navi is not None and all(value is not None for value in key_facts.values())

    evidence = []
    for family in EXPECTED_IDS:
        row = rows[family]
        evidence.append({
            "citations": row["citations"],
            "claim": row["claim"],
            "classification": row["classification"],
            "executable": False,
            "hit_count": row["hit_count"],
            "id": family,
            "state": "ZERO_HIT_AVAILABLE_CORPUS" if row["zero_hit"] else "CITED_CANDIDATE",
        })

    edges = [
        {"from": "navigation-source", "id": "E-NAV-AMAP", "state": "SOURCE_BACKED", "to": "H4:AmapService->cluster", "surface": "cluster"},
        {"from": "H4:AmapService", "id": "E-NAV-HUD-GATE", "state": "CANDIDATE_UNEXECUTED", "to": "H1:HUD-navigation-map", "surface": "HUD"},
        {"from": "S0:typed-speed-source", "id": "E-SIGN-PROPERTY", "state": "TRANSPORT_SEMANTICS_UNPROVEN", "to": "S1/S2/S3 via S6", "surface": "none-off-car"},
        {"from": "S1/S2/S3", "id": "E-SIGN-CONSUMER", "state": "T3_NATIVE_ANALYSIS_DEFERRED", "to": "S8:native-consumer", "surface": "cluster/HUD-unknown"},
        {"from": "S7:provider-package", "id": "E-PROVIDER-ARBITRATION", "state": "PACKAGE_CANDIDATE_TARGET_INSTALL_UNKNOWN", "to": "trafficmonitor", "surface": "none-off-car"},
    ]
    return {
        "available_scope_verdict": "COMPLETE_FOR_AVAILABLE_JAVA_CORPUS" if complete_available else "INCOMPLETE_FOR_AVAILABLE_JAVA_CORPUS",
        "corpus_verdict": "NOT_EXHAUSTIVE",
        "edges": edges,
        "evidence": evidence,
        "facts": {
            "key_citations": key_facts,
            "navi_info": navi,
            "navi_info_has_speed_limit": bool(navi and navi["speed_fields"]),
            "off_car_visual_pass": False,
            "property_write_promoted": False,
        },
        "inventory": index.get("inventory", []),
        "schema": "clusternav.re-evidence-graph/v1",
        "truth_state": "SOURCE_MINED_NOT_FIELD_PROVEN",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        index = json.loads(Path(args.input).read_text(encoding="utf-8"))
        graph = build_graph(index)
        Path(args.output).write_text(json.dumps(graph, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except (OSError, ValueError, json.JSONDecodeError) as error:
        parser.error(str(error))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
