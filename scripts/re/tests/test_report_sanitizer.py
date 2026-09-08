import json
import re
import runpy
import tempfile
import unittest
from pathlib import Path

RE_DIR = Path(__file__).resolve().parents[1]
INDEX = runpy.run_path(str(RE_DIR / "index-features.py"))
VERIFY = runpy.run_path(str(RE_DIR / "verify-reproducibility.py"))


class ReportSanitizerTest(unittest.TestCase):
    def test_sanitizer_redacts_unix_windows_homes_and_private_ips(self):
        raw = "/Users/example/project C:\\Users\\example\\project 192.168.1.9 10.1.2.3 172.20.2.4"
        clean = INDEX["sanitize"](raw)
        self.assertNotIn("example", clean)
        self.assertNotIn("192.168", clean)
        self.assertNotIn("10.1", clean)
        self.assertNotIn("172.20", clean)
        self.assertIn("<user-home>", clean)
        self.assertIn("<vehicle-ip>", clean)

        nested = VERIFY["sanitize_value"]({"paths": [raw], "safe": 7})
        self.assertEqual(7, nested["safe"])
        self.assertNotIn("example", nested["paths"][0])
        self.assertIn("<vehicle-ip>", nested["paths"][0])

    def test_reproducibility_verifier_accepts_identical_sanitized_json(self):
        with tempfile.TemporaryDirectory() as temp:
            first = Path(temp) / "first.json"
            second = Path(temp) / "second.json"
            payload = json.dumps({"path": "<project-root>/file", "value": 1}, sort_keys=True) + "\n"
            first.write_text(payload, encoding="utf-8")
            second.write_text(payload, encoding="utf-8")
            result = VERIFY["verify"]([first, second], expect_equal=True)
            self.assertTrue(result["equal"])

    def test_reproducibility_verifier_rejects_machine_path(self):
        with tempfile.TemporaryDirectory() as temp:
            report = Path(temp) / "report.txt"
            report.write_text("/Users/example/private\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                VERIFY["verify"]([report], expect_equal=False)

    def test_production_scripts_have_no_network_or_vehicle_transport_invocation(self):
        names = [
            "hash-corpus.py", "decode-java.sh", "index-features.py",
            "build-evidence-graph.py", "run-native-analysis.sh",
            "verify-reproducibility.py", "ghidra/ExportRelevantFunctions.java",
            "../verify-seal-hud-sign-offcar.sh",
        ]
        forbidden_imports = ["urllib.request", "java.net.", "socket."]
        forbidden_command = re.compile(
            r"(?im)^\s*(?:exec\s+)?(?:sudo\s+)?(?:\S*/)?(?:ad" + r"b|dad" + r"b|curl|wget|nc|ssh)\b"
        )
        for name in names:
            source = (RE_DIR / name).read_text(encoding="utf-8")
            lowered = source.lower()
            for token in forbidden_imports:
                self.assertNotIn(token, lowered, f"{name} imports forbidden API {token}")
            self.assertIsNone(forbidden_command.search(source), f"{name} invokes a network/vehicle tool")

    def test_forbidden_raw_corpus_fields_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "snippet"):
            VERIFY["_reject_fields"]({"citation": {"snippet": "copied source"}}, {"snippet"}, "evidence")
        with self.assertRaisesRegex(ValueError, "'c'"):
            VERIFY["_reject_fields"]({"decompile": {"c": "int vendor() {}"}}, {"c"}, "native")

    def test_complete_report_set_is_sanitized_schema_valid_and_truthful(self):
        root = RE_DIR.parents[1]
        result = VERIFY["validate_report_set"](root)
        self.assertEqual("PASS", result["status"])
        self.assertEqual("NOT_EXHAUSTIVE", result["corpusVerdict"])
        self.assertFalse(result["visualPass"])
        self.assertEqual(32, sum(result["requirementStatuses"].values()))

    def test_native_report_schema_is_local_non_executable_and_name_paired(self):
        report_path = RE_DIR.parents[1] / "docs/diagnostics/hud-sign-re/native/libbydcluster-diff.json"
        report = json.loads(report_path.read_text(encoding="utf-8"))
        self.assertEqual("clusternav.libbydcluster-native-diff/v1", report["schema"])
        self.assertEqual("NOT_EXHAUSTIVE", report["summary"]["overall_verdict"])
        self.assertEqual(0, report["summary"]["unresolved_function_count"])
        self.assertTrue(report["functions"])
        self.assertTrue(all(not row["comparison"]["address_used_for_pairing"] for row in report["functions"]))
        self.assertTrue(all(row["comparison"]["match_basis"] == "FULL_DEMANGLED_SYMBOL_NAME" for row in report["functions"]))
        sides = [side for row in report["functions"] for side in (row.get("old"), row.get("new")) if side]
        self.assertTrue(sides)
        self.assertTrue(all("c" not in side["decompile"] for side in sides))
        self.assertTrue(all(set(side["decompile"]) == {"address_normalized_sha256", "character_count", "message", "sha256", "status", "truncated"} for side in sides))
        adjacent = [row for row in report["functions"] if "trafficSignalStatus" in row["demangled_name"]]
        self.assertTrue(adjacent)
        self.assertTrue(all(row["scope"] == "ADJACENT_OUT_OF_SCOPE" for row in adjacent))


if __name__ == "__main__":
    unittest.main()
