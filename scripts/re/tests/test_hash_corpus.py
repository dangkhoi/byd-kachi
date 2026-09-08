import runpy
import tempfile
import unittest
from pathlib import Path

MODULE = runpy.run_path(str(Path(__file__).resolve().parents[1] / "hash-corpus.py"))


class HashCorpusTest(unittest.TestCase):
    def test_tree_hash_is_stable_and_contains_no_source_path(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "corpus"
            root.mkdir()
            (root / "b.txt").write_text("beta\n", encoding="utf-8")
            (root / "a.txt").write_text("alpha\n", encoding="utf-8")
            spec = [f"fixture={root}"]
            first = MODULE["build_manifest"](spec, include_files=True)
            second = MODULE["build_manifest"](spec, include_files=True)
            self.assertEqual(first, second)
            self.assertNotIn(temp, str(first))
            self.assertEqual(["fixture/a.txt", "fixture/b.txt"], [row["path"] for row in first["artifacts"][0]["files"]])

    def test_unsafe_or_duplicate_alias_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            with self.assertRaises(ValueError):
                MODULE["parse_artifacts"]([f"Bad_Alias={root}"])
            with self.assertRaises(ValueError):
                MODULE["parse_artifacts"]([f"same={root}", f"same={root}"])


if __name__ == "__main__":
    unittest.main()
