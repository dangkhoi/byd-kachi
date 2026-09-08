import runpy
import tempfile
import unittest
from pathlib import Path

MODULE = runpy.run_path(str(Path(__file__).resolve().parents[1] / "index-features.py"))


class FeatureIndexTest(unittest.TestCase):
    def test_all_taxonomy_rows_and_concrete_facts_are_retained(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "sources"
            root.mkdir()
            (root / "Evidence.java").write_text(
                '\n'.join([
                    'String x = "AUTONAVI_STANDARD_BROADCAST_SEND";',
                    'String raw = "AUTONAVI_STANDARD_BROADCAST_SEND"; // pkill dumpsys logcat ' + ('com.vendor.internal.package,' * 200),
                    'String h = Instrument.INSTRUMENT_HUD_NAVIGATION_MAP_SET;',
                    'setCarProperty(h, 2); getCarProperty(h);',
                    'String s = Statistics.STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET;',
                    'String p = "com.telenav.app.isa";',
                    'int v = trafficSignValue;',
                ]) + '\n',
                encoding="utf-8",
            )
            (root / "NaviInfo.java").write_text(
                "public class NaviInfo {\n"
                + "\n".join(f" public int field{i}() {{ return 0; }}" for i in range(18))
                + "\n void make() { startObject(18); }\n}\n",
                encoding="utf-8",
            )
            index = MODULE["build_index"]([f"fixture={root}"], [f"inventory={root}"], 20)
            self.assertEqual([*(f"H{i}" for i in range(8)), *(f"S{i}" for i in range(11))], [row["id"] for row in index["taxonomy"]])
            rows = {row["id"]: row for row in index["taxonomy"]}
            self.assertGreater(rows["H1"]["hit_count"], 0)
            self.assertGreater(rows["S6"]["hit_count"], 0)
            self.assertGreater(rows["S8"]["hit_count"], 0)
            citations = [citation for row in rows.values() for citation in row["citations"]]
            self.assertTrue(citations)
            self.assertTrue(all("snippet" not in citation for citation in citations))
            self.assertTrue(all(citation["matched_tokens"] and len(citation["line_sha256"]) == 64 for citation in citations))
            serialized = __import__("json").dumps(index)
            self.assertNotIn("pkill dumpsys logcat", serialized)
            self.assertNotIn("com.vendor.internal.package", serialized)
            self.assertEqual(18, index["schema_facts"][0]["declared_field_count"])
            self.assertFalse(any(temp in str(row) for row in rows.values()))

    def test_zero_report_explains_available_corpus_boundary(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "sources"
            root.mkdir()
            (root / "Empty.java").write_text("class Empty {}\n", encoding="utf-8")
            report = MODULE["zero_report"](MODULE["build_index"]([f"fixture={root}"], [f"inventory={root}"], 5))
            self.assertIn("ZERO H1", report)
            self.assertIn("not proof of platform absence", report)


if __name__ == "__main__":
    unittest.main()
