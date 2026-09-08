#!/usr/bin/env python3
"""Create deterministic SHA-256 manifests for explicitly selected local artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Iterable

_ALIAS = re.compile(r"^[a-z][a-z0-9-]{0,63}$")
_UNIX_HOME = re.compile(r"/(?:Users|home)/[^/\s\"'<>]+")
_WINDOWS_HOME = re.compile(r"[A-Za-z]:\\Users\\[^\\\r\n\"'<>]+", re.IGNORECASE)
_PRIVATE_IP = re.compile(r"\b(?:10(?:\.\d{1,3}){3}|192\.168(?:\.\d{1,3}){2}|172\.(?:1[6-9]|2\d|3[01])(?:\.\d{1,3}){2})\b")


def sanitize(text: str) -> str:
    text = _WINDOWS_HOME.sub("<user-home>", text)
    text = _UNIX_HOME.sub("<user-home>", text)
    return _PRIVATE_IP.sub("<vehicle-ip>", text)


def sha256_file(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
            size += len(block)
    return digest.hexdigest(), size


def _directory_entries(root: Path) -> Iterable[tuple[str, Path]]:
    for path in sorted(root.rglob("*"), key=lambda item: item.relative_to(root).as_posix()):
        if path.is_file() and not path.is_symlink():
            yield path.relative_to(root).as_posix(), path


def hash_artifact(alias: str, source: Path, include_files: bool = False) -> dict:
    if source.is_file() and not source.is_symlink():
        digest, size = sha256_file(source)
        return {"alias": alias, "byte_count": size, "file_count": 1, "sha256": digest, "type": "file"}
    if not source.is_dir():
        raise FileNotFoundError(sanitize(str(source)))

    tree = hashlib.sha256()
    entries = []
    byte_count = 0
    for relative, path in _directory_entries(source):
        digest, size = sha256_file(path)
        byte_count += size
        tree.update(relative.encode("utf-8"))
        tree.update(b"\0")
        tree.update(digest.encode("ascii"))
        tree.update(b"\0")
        tree.update(str(size).encode("ascii"))
        tree.update(b"\n")
        if include_files:
            entries.append({"path": f"{alias}/{relative}", "sha256": digest, "size": size})
    result = {
        "alias": alias,
        "byte_count": byte_count,
        "file_count": len(entries) if include_files else sum(1 for _ in _directory_entries(source)),
        "sha256": tree.hexdigest(),
        "type": "directory-tree",
    }
    if include_files:
        result["files"] = entries
    return result


def parse_artifacts(specs: list[str]) -> list[tuple[str, Path]]:
    parsed = []
    seen = set()
    for spec in specs:
        if "=" not in spec:
            raise ValueError("artifact must use ALIAS=PATH")
        alias, raw_path = spec.split("=", 1)
        if not _ALIAS.fullmatch(alias) or alias in seen or not raw_path:
            raise ValueError(f"invalid or duplicate artifact alias: {alias!r}")
        path = Path(raw_path).expanduser().resolve()
        if path.name.startswith(".") or ".." in Path(raw_path).parts:
            raise ValueError(f"unsafe artifact path for {alias}")
        parsed.append((alias, path))
        seen.add(alias)
    return sorted(parsed, key=lambda item: item[0])


def build_manifest(specs: list[str], include_files: bool = False) -> dict:
    artifacts = [hash_artifact(alias, path, include_files) for alias, path in parse_artifacts(specs)]
    return {"algorithm": "sha256", "artifacts": artifacts, "schema": "clusternav.re-corpus/v1"}


def write_json(value: dict, output: str) -> None:
    payload = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if output == "-":
        sys.stdout.write(payload)
    else:
        Path(output).write_text(payload, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact", action="append", required=True, help="stable ALIAS=PATH")
    parser.add_argument("--include-files", action="store_true")
    parser.add_argument("--output", default="-")
    args = parser.parse_args()
    try:
        write_json(build_manifest(args.artifact, args.include_files), args.output)
    except (OSError, ValueError) as error:
        parser.error(sanitize(str(error)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
