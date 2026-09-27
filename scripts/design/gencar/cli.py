# -*- coding: utf-8 -*-
# Tách THUẦN từ scripts/design/gen-car.py (882 dòng → trần 500, L6-debt 2026-09-27). Thân hàm giữ nguyên byte;
# `gen-car.py` chỉ còn là điểm vào CLI + re-export cho render-car.py. `--check` so byte đầu ra: khớp 41/41 sau khi tách.
"""Chạy: ba đích, sinh, so byte (`--check`)."""
from __future__ import annotations

import argparse
import json
import os
import tempfile

from . import __doc__ as PACKAGE_DOC
from .icons import ICONS, gen_icon
from .model import load_face
from .paint import gen_paint
from .paths import FACES, MANIFEST_PATH, MAX_FACE_PATHS, RES_DIR, SRC_DIR

# ── Chạy ──────────────────────────────────────────────────────────────────────────────────────────────────────

def dests(root: str | None = None) -> dict[str, str]:
    """Ba đích của script. `root=None` = đích thật; `root=<tmp>` = bản sao để `--check` so byte."""
    if root is None:
        return {"drawable": RES_DIR, "manifest": MANIFEST_PATH, "paint": os.path.join(SRC_DIR, "paint.json")}
    return {"drawable": os.path.join(root, "drawable"),
            "manifest": os.path.join(root, "manifest.json"), "paint": os.path.join(root, "paint.json")}


def generate(d: dict[str, str]) -> dict:
    faces = {f: load_face(f) for f in FACES}
    dr = d["drawable"]
    os.makedirs(dr, exist_ok=True)
    manifest: dict = {"faces": {}, "icons": {}}
    for fname in FACES:
        face = faces[fname]
        frame = face.frame_pieces()
        manifest["faces"][fname] = {
            "framePaths": len(frame),
            "maxFramePaths": MAX_FACE_PATHS,
            "pieces": [
                {"id": p.id, "layer": p.layer, "role": p.role, "glyph": p.glyph, "strokeOnly": p.stroke_only,
                 "evenOdd": p.even_odd, "subpaths": p.n_sub, "bounds": list(p.bounds), "ref": p.ref}
                for p in face.pieces
            ],
        }
    for name in sorted(ICONS):
        xml, meta = gen_icon(faces, name)
        with open(os.path.join(dr, f"{name}.xml"), "w", encoding="utf-8") as f:
            f.write(xml)
        manifest["icons"][name] = meta
    # ⚠ WP3-v5 (2026-09-20) — GỠ hình xe VECTOR (owner: bỏ vector, dùng ảnh bitmap): không còn sinh `car_face_*.xml`
    # (mặt 48dp) và `CarFramesGenerated.kt` (hằng path). Script này nay CHỈ sinh icon `ic_car_*.xml` + manifest + paint.
    with open(d["manifest"], "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")
    # paint.json: đọc nguồn, điền contrast/forceOutline, ghi lại (idempotent — chạy hai lần ra cùng byte)
    filled = gen_paint(os.path.join(SRC_DIR, "paint.json"))
    with open(d["paint"], "w", encoding="utf-8") as f:
        f.write(filled)
    return manifest


def _files(d: str) -> dict[str, bytes]:
    out = {}
    for base, _, names in os.walk(d):
        for n in names:
            p = os.path.join(base, n)
            with open(p, "rb") as f:
                out[os.path.relpath(p, d)] = f.read()
    return out


def _read(p: str) -> bytes | None:
    if not os.path.exists(p):
        return None
    with open(p, "rb") as f:
        return f.read()


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=PACKAGE_DOC, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true",
                    help="sinh vào thư mục tạm rồi so byte với res/drawable · CarFramesGenerated.kt · design/car/manifest.json · paint.json")
    ap.add_argument("--out", default=None, help="gốc thư mục thay thế (thử nghiệm); mặc định = ba đích thật")
    a = ap.parse_args(argv)
    if a.check:
        with tempfile.TemporaryDirectory() as tmp:
            want_d, have_d = dests(tmp), dests(a.out)
            generate(want_d)
            want, have = _files(want_d["drawable"]), _files(have_d["drawable"])
            bad = sorted(f"drawable/{k}" for k in want if want[k] != have.get(k))
            for key, label in (("manifest", "design/car/manifest.json"),
                               ("paint", "design/car/paint.json (chưa điền contrast — chạy lại không --check)")):
                if _read(want_d[key]) != _read(have_d[key]):
                    bad.append(label)
            if bad:
                print("LỆCH so với nguồn (vá tay? quên sinh lại?):")
                for k in bad:
                    print("  " + k)
                return 1
            print(f"OK — {len(want) + 2} tệp khớp byte")
            return 0
    m = generate(dests(a.out))
    for fname, fm in m["faces"].items():
        print(f"{fname}: {fm['framePaths']}/{fm['maxFramePaths']} path khung · {len(fm['pieces'])} mảnh")
    print(f"{len(m['icons'])} icon → {dests(a.out)['drawable']}")
    return 0
