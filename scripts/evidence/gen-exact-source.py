#!/usr/bin/env python3
"""Generate a ClusterNav exact-source attestation.

Identity rule (must stay byte-identical to the Gradle gate in app/build.gradle.kts):

    sourceId = SHA-256( canonical_json(manifest_without_sourceId) )

where canonical JSON is UTF-8, recursively lexicographic object keys, original array
order, compact ``,``/``:`` separators, ``ensure_ascii=False`` and no trailing newline.
The whole-file byte hash is deliberately NOT the identity: the emitted file embeds
``sourceId``, so a byte hash could never bind a manifest to its own identity.

Usage:
    scripts/evidence/gen-exact-source.py \
        --out docs/_handoff/<name>-exact-source.json \
        --label "<version label>" \
        [--base docs/_handoff/<previous>-exact-source.json]

The optional base manifest supplies authoritative input classifications so a revision
never silently reclassifies an existing path.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

CANONICALIZATION = (
    "UTF-8 JSON; recursively lexicographic object keys; preserve array order; "
    "ensure_ascii=false; compact comma/colon separators; no BOM."
)
DIFF_PRODUCER = "GIT_OPTIONAL_LOCKS=0 git diff --binary --no-ext-diff HEAD"
SCHEMA = "clusternav.exact-source/v1"

# Untracked paths that are deliberately not build inputs.
EXCLUSION_RULES: tuple[tuple[str, str], ...] = (
    (".kiro/", "LOCAL_TOOLING_OR_RUNTIME_PRESERVE"),
    ("apk/", "HISTORICAL_APK_IMMUTABLE"),
    ("docs/_handoff/vehicle-candidate.json", "GENERATED_VEHICLE_CANDIDATE_RECORD"),
)

# Fallback classification for paths absent from the base manifest.
CLASS_RULES: tuple[tuple[str, str], ...] = (
    ("app/src/test/", "TEST_SOURCE"),
    ("app/", "PRODUCT_SOURCE_OR_RESOURCE"),
    ("docs/specs/", "CANONICAL_SPEC"),
    ("docs/design/", "EVIDENCE_DESIGN"),
    ("docs/_handoff/", "EXECUTION_HANDOFF"),
    ("scripts/vehicle/", "VEHICLE_KIT"),
    ("scripts/", "EXECUTION_SCRIPT"),
    ("docs/", "SUPPORTING_DOCUMENT"),
)


def canonical(value: object) -> str:
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def git(root: Path, *args: str) -> bytes:
    env = dict(os.environ, GIT_OPTIONAL_LOCKS="0")
    result = subprocess.run(
        ["git", *args], cwd=root, capture_output=True, env=env, check=False
    )
    if result.returncode != 0:
        raise SystemExit(f"git {' '.join(args)} failed: {result.stderr.decode(errors='replace')}")
    return result.stdout


def untracked_paths(root: Path) -> list[str]:
    status = git(root, "status", "--porcelain", "-uall").decode("utf-8")
    return sorted(line[3:] for line in status.splitlines() if line.startswith("?? "))


def classify(path: str, inherited: dict[str, str]) -> str:
    if path in inherited:
        return inherited[path]
    for prefix, name in CLASS_RULES:
        if path.startswith(prefix):
            return name
    return "SUPPORTING_DOCUMENT"


def exclusion_reason(path: str, out_rel: str, version_name: str) -> str | None:
    if path == out_rel:
        return "GENERATED_ATTESTATION_SELF_REFERENCE"
    # A candidate for the version being built is a build OUTPUT, not immutable evidence: each
    # regenerated identity produces another one, and stale candidates get pruned. Hash-inventorying
    # them would leave dangling entries after a prune. Older APKs stay hash-inventoried evidence.
    if path.startswith(f"apk/ClusterNav-{version_name}-") and path.endswith(".apk"):
        return "CANDIDATE_BUILD_OUTPUT"
    for prefix, reason in EXCLUSION_RULES:
        if path == prefix or path.startswith(prefix):
            return reason
    if path.startswith("docs/_handoff/") and path.endswith("exact-source.json"):
        return "PRIOR_OR_INVALIDATED_ATTESTATION"
    return None


def hashed_entry(root: Path, path: str, extra: dict[str, object]) -> dict[str, object]:
    data = (root / path).read_bytes()
    return {"byteLength": len(data), "sha256": sha256_bytes(data), "path": path, **extra}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True, help="repo-relative output manifest path")
    parser.add_argument("--label", required=True, help="version label recorded in the manifest")
    parser.add_argument("--base", help="repo-relative previous manifest for class inheritance")
    args = parser.parse_args()

    root = Path(git(Path.cwd(), "rev-parse", "--show-toplevel").decode().strip())
    out_rel = args.out.replace(os.sep, "/")

    inherited: dict[str, str] = {}
    if args.base:
        base = json.loads((root / args.base).read_text(encoding="utf-8"))
        inherited = {e["path"]: e["class"] for e in base["intendedUntracked"]}

    diff = git(root, "diff", "--binary", "--no-ext-diff", "HEAD")
    version_code, version_name = read_version(root)

    inputs: list[dict[str, object]] = []
    exclusions: list[dict[str, object]] = []
    for path in untracked_paths(root):
        reason = exclusion_reason(path, out_rel, version_name)
        if reason is None:
            inputs.append(hashed_entry(root, path, {"class": classify(path, inherited)}))
            continue
        entry: dict[str, object] = {"path": path, "reason": reason}
        # Build outputs and the emitted manifest are path-only: hashing them would record
        # a value the next authorized build immediately invalidates.
        path_only = reason in {
            "GENERATED_VEHICLE_CANDIDATE_RECORD",
            "GENERATED_ATTESTATION_SELF_REFERENCE",
            "CANDIDATE_BUILD_OUTPUT",
        }
        if not path_only and (root / path).is_file():
            data = (root / path).read_bytes()
            entry = {"byteLength": len(data), "sha256": sha256_bytes(data), **entry}
        exclusions.append(entry)

    # Path-only exclusions for the emitted manifest and for build-generated records
    # prevent identity recursion and keep an output from becoming an unattested input.
    # They are declared even when the file does not exist yet.
    declared = {str(e["path"]) for e in exclusions}
    for path, reason in EXCLUSION_RULES:
        if path.endswith("/") or path in declared:
            continue
        exclusions.append({"path": path, "reason": reason})
        declared.add(path)
    if out_rel not in declared:
        exclusions.append({"path": out_rel, "reason": "GENERATED_ATTESTATION_SELF_REFERENCE"})

    manifest = {
        "branch": git(root, "rev-parse", "--abbrev-ref", "HEAD").decode().strip(),
        "canonicalization": CANONICALIZATION,
        "head": git(root, "rev-parse", "HEAD").decode().strip(),
        "identityExclusions": {
            "entries": sorted(exclusions, key=lambda e: e["path"]),
            "rule": (
                "Path-only exclusions prevent identity recursion and keep local tooling state, "
                "historical APK bytes, every prior attestation and build-generated candidate "
                "records out of the build inputs. Predecessor identities stay independently "
                "verifiable and unmodified."
            ),
        },
        "intendedUntracked": sorted(inputs, key=lambda e: e["path"]),
        "schema": SCHEMA,
        "trackedDiff": {
            "byteLength": len(diff),
            "producer": DIFF_PRODUCER,
            "sha256": sha256_bytes(diff),
        },
        "version": {
            "label": args.label,
            "versionCode": version_code,
            "versionName": version_name,
        },
    }

    source_id = sha256_bytes(canonical(manifest).encode("utf-8"))
    manifest["sourceId"] = source_id

    destination = root / out_rel
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        json.dumps(manifest, sort_keys=True, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"sourceId={source_id}")
    print(f"inputs={len(inputs)} exclusions={len(exclusions)} trackedDiffBytes={len(diff)}")
    print(f"written={out_rel}")
    return 0


def read_version(root: Path) -> tuple[int, str]:
    text = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    code = int(text.split("versionCode = ")[1].split("\n")[0].strip())
    name = text.split("versionName = ")[1].split("\n")[0].strip().strip('"')
    return code, name


if __name__ == "__main__":
    sys.exit(main())
