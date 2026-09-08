import contextlib
import copy
import hashlib
import io
import json
import os
import runpy
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "expand-candidate-coverage.py"
ROOT = SCRIPT.parents[2]
MODULE = runpy.run_path(str(SCRIPT))
CLAIM_FIELDS = {
    "absoluteRejects", "access", "boundedDomainValueIds", "clearOperationId", "clearPolicy",
    "configId", "consumerId", "inverseOperationIds", "javaType", "mutationOperationId",
    "ownership", "permissionId", "priorReadOperationId", "providerId", "readBackOperationId",
    "readProbeId", "risk", "selectorId", "transportId",
}
H8_CLAIM = {
    "absoluteRejects": [], "access": "READ_ONLY", "boundedDomainValueIds": ["VALUE-METADATA-AVAILABLE"],
    "clearOperationId": None, "clearPolicy": "NOT_APPLICABLE", "configId": "CONFIG-H8-PROPERTY-METADATA",
    "consumerId": "CONSUMER-EXPANSION-REVIEW", "inverseOperationIds": [], "javaType": "STRING",
    "mutationOperationId": None, "ownership": "DIAGNOSTIC_TEMP", "permissionId": "PERMISSION-NONE",
    "priorReadOperationId": None, "providerId": "PROVIDER-SOURCE-METADATA", "readBackOperationId": None,
    "readProbeId": "PROBE-READ-PROPERTY-CONFIG", "risk": 0,
    "selectorId": "SEL-H8-PROPERTY-CONFIG-METADATA", "transportId": "TRANSPORT-READ-ONLY-METADATA",
}
S11_CLAIM = {
    "absoluteRejects": [], "access": "READ_WRITE", "boundedDomainValueIds": [],
    "clearOperationId": None, "clearPolicy": "NOT_APPLICABLE", "configId": "CONFIG-S11-SOURCE-DOMAIN",
    "consumerId": "CONSUMER-CLUSTER-NATIVE", "inverseOperationIds": [], "javaType": "STRING",
    "mutationOperationId": "OP-MUTATE-S11-SOURCE-DOMAIN", "ownership": "DIAGNOSTIC_TEMP",
    "permissionId": "PERMISSION-VENDOR-CAR", "priorReadOperationId": None,
    "providerId": "PROVIDER-SOURCE-METADATA", "readBackOperationId": None, "readProbeId": None,
    "risk": 25, "selectorId": "SEL-S11-SOURCE-DOMAIN", "transportId": "TRANSPORT-SOURCE-PROVEN",
}
S12_CLAIM = {**S11_CLAIM, "absoluteRejects": ["RAW_SELECTOR"]}
EXPECTED_CLAIMS = {
    "CAND-H-008-PROPERTY-CONFIG-METADATA@3": H8_CLAIM,
    "CAND-S-011-SOURCE-DOMAIN@1": S11_CLAIM,
    "CAND-S-012-REJECTED-SHAPE@1": S12_CLAIM,
}


class ExpandCandidateCoverageTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.document = MODULE["build_document"]()
        cls.payload = MODULE["render_document"](cls.document)
        cls.hits = [hit for row in cls.document["entries"] for hit in row["hits"]]
        cls.candidates = {
            hit["disposition"]["candidateRevisionId"]: hit
            for hit in cls.hits if hit["disposition"]["kind"] == "CANDIDATE_DERIVATION"
        }

    def test_all_twelve_corpora_have_exact_current_availability(self):
        entries = self.document["entries"]
        self.assertEqual([f"C{number:02d}" for number in range(1, 13)], [row["corpusId"] for row in entries])
        self.assertEqual(MODULE["QUERY_IDS"], {row["corpusId"]: row["query"]["queryId"] for row in entries})
        self.assertEqual({MODULE["TOOL_ID"]}, {row["scanner"]["toolId"] for row in entries})
        self.assertEqual({MODULE["VERSION_ID"]}, {row["scanner"]["versionId"] for row in entries})
        self.assertEqual(["AVAILABLE"] * 5 + ["UNAVAILABLE"] * 7, [row["status"] for row in entries])
        self.assertEqual("NOT_EXHAUSTIVE", self.document["expansionVerdict"])
        short = copy.deepcopy(self.document)
        short["entries"].pop()
        with self.assertRaisesRegex(ValueError, "C01-C12"):
            MODULE["validate_document"](short)

    def test_scanner_query_hit_hashes_and_normative_duplicate_preimage_are_bound(self):
        sha_re, hit_re = MODULE["SHA256_RE"], MODULE["HIT_RE"]
        for row in self.document["entries"]:
            scanner = row["scanner"]
            self.assertEqual(hashlib.sha256(SCRIPT.read_bytes()).hexdigest(), scanner["binaryFileSha256"])
            scanner_preimage = {key: scanner[key] for key in ("binaryFileSha256", "configFileSha256", "toolId", "versionId")}
            self.assertEqual(MODULE["canonical_sha256"](scanner_preimage), scanner["scannerIdentitySha256"])
            query = row["query"]
            query_preimage = {key: query[key] for key in ("parameters", "queryId", "scannerIdentitySha256", "tokenIds")}
            self.assertEqual(MODULE["canonical_sha256"](query_preimage), query["queryDefinitionSha256"])
            if row["status"] == "AVAILABLE":
                self.assertRegex(row["artifactSha256"], sha_re)
            else:
                self.assertIsNone(row["artifactSha256"])
            for hit in row["hits"]:
                match = hit_re.fullmatch(hit["hitId"])
                self.assertIsNotNone(match)
                self.assertEqual((row["corpusId"], query["queryId"]), match.groups()[:2])
                self.assertEqual((row["artifactSha256"][:12], scanner["scannerIdentitySha256"][:12],
                                  query["queryDefinitionSha256"][:12], hit["locationSha256"][:12],
                                  hit["tokenSetSha256"][:12]), match.groups()[2:])
                preimage = {"consumerIds": hit["consumerIds"], "corpusId": row["corpusId"],
                            "normalizedFactIds": hit["normalizedFactIds"],
                            "queryDefinitionSha256": query["queryDefinitionSha256"],
                            "selectorIds": hit["selectorIds"]}
                self.assertNotIn("promotionProofClaim", preimage)
                self.assertEqual(MODULE["canonical_sha256"](preimage), hit["duplicateEquivalenceSha256"])
                self.assertEqual(MODULE["canonical_sha256"](hit["tokenIds"]), hit["tokenSetSha256"])

    def test_query_and_parent_evidence_bindings_reject_mismatch(self):
        broken = copy.deepcopy(self.document)
        row = broken["entries"][0]
        row["hits"][0]["hitId"] = row["hits"][0]["hitId"].replace(row["query"]["queryId"], MODULE["QUERY_IDS"]["C02"])
        row["hits"].sort(key=lambda item: item["hitId"])
        with self.assertRaisesRegex(ValueError, "binding"):
            MODULE["validate_document"](broken)
        broken = copy.deepcopy(self.document)
        candidate = next(hit for hit in broken["entries"][1]["hits"] if hit["disposition"].get("candidateRevisionId") == "CAND-H-008-PROPERTY-CONFIG-METADATA@3")
        candidate["tokenIds"] = ["TOKEN-H6"]
        with self.assertRaisesRegex(ValueError, "parent evidence"):
            MODULE["validate_document"](broken)

    def test_three_source_backed_claims_are_exact_and_all_other_claims_are_null(self):
        self.assertEqual(EXPECTED_CLAIMS, {candidate_id: hit["promotionProofClaim"] for candidate_id, hit in self.candidates.items()})
        expected_parent = {
            "CAND-H-008-PROPERTY-CONFIG-METADATA@3": ("C02", "H7"),
            "CAND-S-011-SOURCE-DOMAIN@1": ("C03", "S6"),
            "CAND-S-012-REJECTED-SHAPE@1": ("C02", "S2"),
        }
        for candidate_id, hit in self.candidates.items():
            claim = hit["promotionProofClaim"]
            self.assertEqual(CLAIM_FIELDS, set(claim))
            self.assertEqual([claim["selectorId"]], hit["selectorIds"])
            self.assertEqual([claim["consumerId"]], hit["consumerIds"])
            corpus, evidence = expected_parent[candidate_id]
            row = next(item for item in self.document["entries"] if hit in item["hits"])
            self.assertEqual(corpus, row["corpusId"])
            self.assertEqual([f"TOKEN-{evidence}"], hit["tokenIds"])
            self.assertEqual([f"FACT-PARENT-{evidence}"], hit["normalizedFactIds"])
        for hit in self.hits:
            if hit["disposition"]["kind"] != "CANDIDATE_DERIVATION":
                self.assertIsNone(hit["promotionProofClaim"])
                self.assertEqual([], hit["selectorIds"])
                self.assertEqual([], hit["consumerIds"])

    def test_claim_shape_ids_enums_ranges_and_selector_consumer_equality_are_rejected(self):
        validate = MODULE["_validate_claim"]
        missing = copy.deepcopy(H8_CLAIM); missing.pop("risk")
        extra = {**H8_CLAIM, "evidenceIds": ["H7"]}
        for claim in (missing, extra):
            with self.assertRaisesRegex(ValueError, "closed"):
                validate(claim)
        for field, bad in (("access", "EXECUTE"), ("javaType", "OBJECT"), ("ownership", "VEHICLE"), ("clearPolicy", "OPTIONAL")):
            claim = copy.deepcopy(H8_CLAIM); claim[field] = bad
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "enum"):
                validate(claim)
        for bad in (True, -1, 101, "25"):
            claim = copy.deepcopy(H8_CLAIM); claim["risk"] = bad
            with self.subTest(risk=bad), self.assertRaisesRegex(ValueError, "risk"):
                validate(claim)
        id_fields = ("selectorId", "readProbeId", "mutationOperationId", "configId", "providerId", "permissionId",
                     "transportId", "priorReadOperationId", "readBackOperationId", "clearOperationId", "consumerId")
        for field in id_fields:
            claim = copy.deepcopy(S11_CLAIM); claim[field] = "untyped-private-value"
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, field):
                validate(claim)
        for field, bad in (("boundedDomainValueIds", ["VALUE-X", "VALUE-X"]),
                           ("inverseOperationIds", ["OP-INVERSE-X", "OP-INVERSE-X"]),
                           ("absoluteRejects", ["UNKNOWN_REJECT"])):
            claim = copy.deepcopy(S11_CLAIM); claim[field] = bad
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate(claim)
        broken = copy.deepcopy(self.document)
        target = next(hit for row in broken["entries"] for hit in row["hits"] if hit["disposition"]["kind"] == "CANDIDATE_DERIVATION")
        target["selectorIds"] = []
        with self.assertRaisesRegex(ValueError, "cardinality"):
            MODULE["validate_document"](broken)
        broken = copy.deepcopy(self.document)
        target = next(hit for row in broken["entries"] for hit in row["hits"] if hit["disposition"]["kind"] == "CANDIDATE_DERIVATION")
        target["promotionProofClaim"]["configId"] = "CONFIG-DIFFERENT-BUT-TYPED"
        with self.assertRaisesRegex(ValueError, "source metadata"):
            MODULE["validate_document"](broken)

    def test_duplicate_classes_close_to_minimum_and_require_equal_claims(self):
        hits = self.document["entries"][3]["hits"]
        classes = {}
        for hit in hits:
            classes.setdefault(hit["duplicateEquivalenceSha256"], []).append(hit)
        duplicate_class = max(classes.values(), key=len)
        self.assertGreater(len(duplicate_class), 1)
        ordered = sorted(duplicate_class, key=lambda item: item["hitId"])
        self.assertNotEqual("DUPLICATE_OF", ordered[0]["disposition"]["kind"])
        for duplicate in ordered[1:]:
            self.assertEqual({"canonicalHitId": ordered[0]["hitId"], "kind": "DUPLICATE_OF"}, duplicate["disposition"])
        unequal = [
            {"hitId": "A", "duplicateEquivalenceSha256": "a" * 64, "disposition": {"kind": "OUT_OF_SCOPE"}, "promotionProofClaim": None},
            {"hitId": "B", "duplicateEquivalenceSha256": "a" * 64, "disposition": {"kind": "OUT_OF_SCOPE"}, "promotionProofClaim": H8_CLAIM},
        ]
        with self.assertRaisesRegex(ValueError, "claims differ"):
            MODULE["canonicalize_duplicates"](unequal)
        chained = copy.deepcopy(self.document)
        native_duplicates = [hit for hit in chained["entries"][3]["hits"] if hit["disposition"]["kind"] == "DUPLICATE_OF"]
        native_duplicates[-1]["disposition"]["canonicalHitId"] = native_duplicates[0]["hitId"]
        with self.assertRaisesRegex(ValueError, "minimal representative"):
            MODULE["validate_document"](chained)

    def test_availability_matches_all_six_schema_branches(self):
        validate, digest = MODULE["validate_availability"], "a" * 64
        valid = [
            ("AVAILABLE", digest, True, [], True, "NO_MATCH"),
            ("AVAILABLE", digest, True, [{}], False, None),
            ("UNAVAILABLE", None, False, [], None, None),
            ("UNSEARCHED", None, False, [], None, None),
            ("BUDGET_STOPPED", digest, False, [], None, None),
            ("BUDGET_STOPPED", digest, False, [{}], None, None),
            ("ACQUIRED_UNREVIEWED", digest, False, [], None, None),
        ]
        for values in valid:
            with self.subTest(values=values): validate(*values)
        invalid = [
            ("AVAILABLE", None, True, [], True, "NO_MATCH"),
            ("AVAILABLE", digest, True, [{}], True, "NO_MATCH"),
            ("UNAVAILABLE", digest, False, [], None, None),
            ("UNSEARCHED", None, True, [], None, None),
            ("BUDGET_STOPPED", None, False, [], None, None),
            ("ACQUIRED_UNREVIEWED", digest, False, [{}], None, None),
            ("ACQUIRED_UNREVIEWED", digest, False, [], False, None),
        ]
        for values in invalid:
            with self.subTest(values=values), self.assertRaises(ValueError): validate(*values)

    def test_all_available_open_is_not_exhaustive(self):
        entries = self._all_available_entries()
        for state in ("DISCOVERED", "SOURCE_BACKED", "MUTATION_REVIEW"):
            candidate = next(hit for row in entries for hit in row["hits"] if hit["disposition"]["kind"] == "CANDIDATE_DERIVATION")
            original = candidate["disposition"]["candidateState"]
            candidate["disposition"]["candidateState"] = state
            with self.subTest(state=state): self.assertEqual("NOT_EXHAUSTIVE", MODULE["derive_verdict"](entries))
            candidate["disposition"]["candidateState"] = original

    def test_all_available_closed_is_ready_data(self):
        entries = self._all_available_entries()
        for row in entries:
            for hit in row["hits"]:
                if hit["disposition"].get("candidateState") in MODULE["OPEN_CANDIDATE_STATES"]:
                    hit["disposition"]["candidateState"] = "READY_FOR_FIELD"
        self.assertEqual("READY_DATA", MODULE["derive_verdict"](entries))
        self.assertEqual("NOT_EXHAUSTIVE", MODULE["derive_verdict"](entries[:-1]))

    def _all_available_entries(self):
        entries = copy.deepcopy(self.document["entries"])
        for row in entries:
            row["status"] = "AVAILABLE"
            row["artifactSha256"] = row["artifactSha256"] or "a" * 64
            row["zeroHit"] = not row["hits"]
            row["zeroHitOutcome"] = "NO_MATCH" if not row["hits"] else None
        return entries

    def test_canonical_integer_range_and_output_ancestor_symlinks_are_rejected(self):
        for value in (1 << 63, -(1 << 63) - 1):
            with self.assertRaisesRegex(ValueError, "signed-64"):
                MODULE["canonical_bytes"]({"value": value})
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp).resolve()
            output = root / MODULE["OUTPUT_RELATIVE"]
            output.parent.mkdir(parents=True)
            target = root / "outside.json"; target.write_text("unchanged", encoding="utf-8")
            try: output.symlink_to(target)
            except OSError as error: self.skipTest(f"symlink creation unavailable: {error}")
            with self.assertRaisesRegex(ValueError, "symbolic link"):
                MODULE["write_default_output"](root)
            self.assertEqual("unchanged", target.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp).resolve(); root, outside = base / "root", base / "outside"
            root.mkdir(); outside.mkdir()
            try: (root / "docs").symlink_to(outside, target_is_directory=True)
            except OSError as error: self.skipTest(f"symlink creation unavailable: {error}")
            with self.assertRaisesRegex(ValueError, "symbolic link"):
                MODULE["write_default_output"](root)
            self.assertFalse((outside / "diagnostics").exists())
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp).resolve(); physical = base / "physical-parent"; project = physical / "project"
            physical.mkdir(); project.mkdir(); linked = base / "linked-parent"
            try: linked.symlink_to(physical, target_is_directory=True)
            except OSError as error: self.skipTest(f"symlink creation unavailable: {error}")
            linked_root = linked / "project"
            self.assertFalse(linked_root.is_symlink())
            with self.assertRaisesRegex(ValueError, "fixed repository root"):
                MODULE["write_default_output"](linked_root)
            self.assertFalse((project / "docs").exists())
            source = project / "input.json"; source.write_text("{}", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "fixed repository root"):
                MODULE["load_json"](linked_root / "input.json", linked_root)

    def test_repository_input_reader_rejects_leaf_and_ancestor_symlinks(self):
        self.assertEqual(SCRIPT.read_bytes(), MODULE["read_repository_bytes"](SCRIPT))
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp).resolve(); root = base / "root"; root.mkdir()
            outside_file = base / "outside.json"; outside_file.write_text("{}", encoding="utf-8")
            leaf = root / "leaf.json"
            try: leaf.symlink_to(outside_file)
            except OSError as error: self.skipTest(f"symlink creation unavailable: {error}")
            with self.assertRaisesRegex(ValueError, "leaf"):
                MODULE["load_json"](leaf, root)

            outside_directory = base / "outside"; outside_directory.mkdir()
            (outside_directory / "input.json").write_text("{}", encoding="utf-8")
            linked = root / "linked"; linked.symlink_to(outside_directory, target_is_directory=True)
            with self.assertRaisesRegex(ValueError, "ancestor"):
                MODULE["load_json"](linked / "input.json", root)

    def test_bytes_are_deterministic_from_any_working_directory_without_writing_output(self):
        original = Path.cwd()
        try:
            with tempfile.TemporaryDirectory() as temp:
                os.chdir(temp)
                second = MODULE["render_document"](MODULE["build_document"]())
        finally:
            os.chdir(original)
        self.assertEqual(self.payload, second)
        self.assertFalse(self.payload.endswith(b"\n"))
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            MODULE["main"](["--output", "elsewhere.json"])

    def test_recursive_semantic_privacy_rejects_malicious_generic_values(self):
        malicious = [
            "VIN 1HGCM82633A004352", "serialNumber=SN-123456", "raw payload bytes",
            "decompiled source body", "latitude=10.762622 longitude=106.660172",
            "10.0.0.106", "/Users/example/private/report.txt", "/opt/internal/report.bin",
            "https://internal.example.test/data", "token=opaque-private-credential",
            "password=correct-horse-battery-staple", "name=Jane Citizen", "alice@example.test", "+84 912 345 678",
            "FACT-8.8.8.8", "VERSION-8.8.8.8", "FACT-1HGCM82633A004352",
            "FACT-RAW-DUMP-PAYLOAD", "FACT-SOURCE-LINE-42", "FACT-SERIAL-NUMBER-42", "FACT-GPS-21.0285",
        ]
        for value in malicious:
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "semantically private"):
                MODULE["validate_semantic_privacy"]({"generic": [value]})
        safe = [
            "a" * 64, MODULE["SCHEMA_ID"], "CAND-S-011-SOURCE-DOMAIN@1", "PROVIDER-SOURCE-METADATA",
            "RAW_SELECTOR", "SOURCE_BACKED", "TOKEN-C02-QUERY", "VERSION-X1-2",
            self.hits[0]["hitId"],
        ]
        MODULE["validate_semantic_privacy"]({"approved": safe})
        MODULE["validate_semantic_privacy"](self.document)

    def test_output_boundary_matches_authoritative_closed_schema(self):
        schema_path = ROOT / "offcar-planner/src/main/resources/expansion-contracts.schema.json"
        definitions = json.loads(schema_path.read_text(encoding="utf-8"))["$defs"]
        root_schema = definitions["CorpusCoverageRoot"]
        self.assertEqual(12, root_schema["properties"]["entries"]["minItems"])
        self.assertEqual(12, root_schema["properties"]["entries"]["maxItems"])
        self.assertEqual(set(root_schema["properties"]), set(self.document))
        self.assertEqual(6, len(definitions["CorpusCoverage"]["oneOf"]))
        self.assertEqual(set(definitions["CorpusCoverage"]["properties"]), set(self.document["entries"][0]))
        self.assertEqual(set(definitions["CorpusHit"]["properties"]), set(self.hits[0]))
        self.assertEqual(set(definitions["PromotionProofClaim"]["properties"]), CLAIM_FIELDS)
        allowed_kinds = {branch["properties"]["kind"]["const"] for branch in definitions["HitDisposition"]["oneOf"]}
        self.assertLessEqual({hit["disposition"]["kind"] for hit in self.hits}, allowed_kinds)

    def test_self_hash_canonical_round_trip_is_exact_in_memory(self):
        draft = {key: value for key, value in self.document.items() if key != "selfSha256"}
        self.assertEqual(MODULE["canonical_sha256"](draft), self.document["selfSha256"])
        self.assertEqual(self.document, json.loads(self.payload))
        self.assertEqual(self.payload, MODULE["canonical_bytes"](self.document))


if __name__ == "__main__":
    unittest.main()
