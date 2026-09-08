import runpy
import unittest
from pathlib import Path

MODULE = runpy.run_path(str(Path(__file__).resolve().parents[1] / "build-evidence-graph.py"))


def citation(*tokens, path="fixture/Evidence.java"):
    return {
        "artifact_sha256": "a" * 64,
        "line": 1,
        "line_sha256": "b" * 64,
        "matched_tokens": list(tokens),
        "path": path,
    }


class EvidenceGraphTest(unittest.TestCase):
    def fixture(self):
        ids = [*(f"H{i}" for i in range(8)), *(f"S{i}" for i in range(11))]
        citations_by_family = {
            "H1": [citation("H1", "F-H1-CONFIG", "F-H1-STATUS", "F-H1-KEY", "F-H1-CALL-SITE")],
            "H4": [citation("H4", "F-AMAP-TRANSPORT")],
            "S1": [citation("S1", "F-STATISTICS-VALUE")],
            "S2": [citation("S2", "F-INSTRUMENT-TRAFFIC-SIGN-VALUE")],
            "S3": [citation("S3", "F-ADAS-SLA-OUTPUT")],
            "S6": [
                citation("S6", "F-MODERN-PROPERTY-READ", "F-MODERN-PROPERTY-WRITE", path="fixture/ICarPropertyManager.java"),
                citation("S6", "F-MODERN-PROPERTY-READ", "F-MODERN-PROPERTY-WRITE", path="fixture/car/s2.java"),
            ],
            "S7": [citation("S7", "F-PROVIDER-TELENAV", "F-PROVIDER-NEUSOFT")],
        }
        rows = []
        for family in ids:
            cites = citations_by_family.get(family, [])
            rows.append({"citations": cites, "claim": family, "classification": "SOURCE_CONSTANT", "hit_count": len(cites), "id": family, "zero_hit": not cites})
        return {
            "inventory": [],
            "schema_facts": [{"declared_field_count": 18, "fields": [f"field{i}" for i in range(18)], "path": "fixture/NaviInfo.java", "speed_fields": [], "start_object_line": 20}],
            "taxonomy": rows,
        }

    def test_graph_is_complete_non_executable_and_honest(self):
        graph = MODULE["build_graph"](self.fixture())
        self.assertEqual("COMPLETE_FOR_AVAILABLE_JAVA_CORPUS", graph["available_scope_verdict"])
        self.assertEqual("NOT_EXHAUSTIVE", graph["corpus_verdict"])
        self.assertFalse(graph["facts"]["off_car_visual_pass"])
        self.assertFalse(graph["facts"]["navi_info_has_speed_limit"])
        self.assertTrue(all(not row["executable"] for row in graph["evidence"]))

    def test_missing_taxonomy_is_rejected(self):
        index = self.fixture()
        index["taxonomy"].pop()
        with self.assertRaises(ValueError):
            MODULE["build_graph"](index)


if __name__ == "__main__":
    unittest.main()
