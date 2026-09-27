# -*- coding: utf-8 -*-
# Tách THUẦN từ scripts/design/gen-car.py (882 dòng → trần 500, L6-debt 2026-09-27). Thân hàm giữ nguyên byte;
# `gen-car.py` chỉ còn là điểm vào CLI + re-export cho render-car.py. `--check` so byte đầu ra: khớp 41/41 sau khi tách.
"""Mô hình mảnh hình (Gradient · Piece · Face) + đọc SVG nguồn `design/car/<mặt>.svg`."""
from __future__ import annotations

import os
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

from .paths import MAX_FACE_PATHS, SRC_DIR, SVG_NS
from .pathdata import bounds_of, ellipse_cmds, parse_d, rect_cmds, serialize

# ── Mô hình mảnh hình ─────────────────────────────────────────────────────────────────────────────────────────

@dataclass
class Gradient:
    kind: str                      # linear | radial
    coords: tuple                  # linear: (x1,y1,x2,y2) · radial: (cx,cy,r) — hệ hộp bao 0..1
    stops: list[tuple[float, str, float]]  # (offset, #rrggbb, opacity)


@dataclass
class Piece:
    face: str
    id: str
    layer: str
    role: str
    cmds: list[tuple]
    path: str
    bounds: tuple[float, float, float, float]
    even_odd: bool = False
    glyph: bool = False
    stroke_only: bool = False      # data-stroke="1" — icon vẽ bằng NÉT
    fill: str | None = None        # "#rrggbb" | "url:<id>" | None
    fill_opacity: float = 1.0
    stroke: str | None = None
    stroke_opacity: float = 1.0
    stroke_width: float = 0.0
    ref: str | None = None         # data-ref → mảnh dùng lại hình của mảnh khác (highlight)
    n_sub: int = 1                 # số nhánh M gộp


@dataclass
class Face:
    name: str
    pieces: list[Piece] = field(default_factory=list)
    gradients: dict[str, Gradient] = field(default_factory=dict)
    paint_default: tuple[str, str] = ("#e9eef5", "#8d99ab")

    def by_id(self) -> dict[str, Piece]:
        return {p.id: p for p in self.pieces}

    def frame_pieces(self) -> list[Piece]:
        return [p for p in self.pieces if not p.glyph and p.ref is None]


def _attr(el, name, default=None):
    return el.get(name, default)


def _elem_cmds(el) -> list[tuple]:
    tag = el.tag.replace(SVG_NS, "")
    if tag == "path":
        return parse_d(el.get("d"))
    if tag == "rect":
        return rect_cmds(float(el.get("x", 0)), float(el.get("y", 0)), float(el.get("width")), float(el.get("height")),
                         float(el.get("rx", el.get("ry", 0)) or 0))
    if tag == "circle":
        r = float(el.get("r"))
        return ellipse_cmds(float(el.get("cx", 0)), float(el.get("cy", 0)), r, r)
    if tag == "ellipse":
        return ellipse_cmds(float(el.get("cx", 0)), float(el.get("cy", 0)), float(el.get("rx")), float(el.get("ry")))
    raise ValueError(f"phần tử không hỗ trợ <{tag}> (id={el.get('id')})")


def _paint(val: str | None) -> str | None:
    if val is None or val == "none":
        return None
    m = re.fullmatch(r"url\(#([\w-]+)\)", val.strip())
    return f"url:{m.group(1)}" if m else val.lower()


def _inherit(el, parents, name, default=None):
    for p in reversed(parents):
        if p.get(name) is not None:
            return p.get(name)
    return el.get(name, default) if el.get(name) is not None else default


def load_face(name: str) -> Face:
    tree = ET.parse(os.path.join(SRC_DIR, f"{name}.svg"))
    root = tree.getroot()
    if root.get("viewBox") != "0 0 24 24":
        raise ValueError(f"{name}.svg phải có viewBox 0 0 24 24 (hệ toạ độ CarFrames)")
    face = Face(name)
    defs = root.find(f"{SVG_NS}defs")
    if defs is not None:
        for g in defs:
            tag = g.tag.replace(SVG_NS, "")
            if tag not in ("linearGradient", "radialGradient"):
                continue
            stops = []
            for s in g:
                stops.append((float(s.get("offset", 0)), s.get("stop-color", "#000000").lower(),
                              float(s.get("stop-opacity", 1))))
                if s.get("id") == "paintFrom":
                    face.paint_default = (s.get("stop-color").lower(), face.paint_default[1])
                if s.get("id") == "paintTo":
                    face.paint_default = (face.paint_default[0], s.get("stop-color").lower())
            if tag == "linearGradient":
                coords = tuple(float(g.get(k, d)) for k, d in (("x1", 0), ("y1", 0), ("x2", 1), ("y2", 0)))
                face.gradients[g.get("id")] = Gradient("linear", coords, stops)
            else:
                coords = tuple(float(g.get(k, 0.5)) for k in ("cx", "cy", "r"))
                face.gradients[g.get("id")] = Gradient("radial", coords, stops)

    def walk(el, parents, layer, glyph):
        for ch in el:
            tag = ch.tag.replace(SVG_NS, "")
            if tag in ("defs", "title"):
                continue
            if ch.get("transform") is not None:
                raise ValueError(f"{name}.svg: cấm transform (id={ch.get('id')}) — viết toạ độ tuyệt đối")
            if tag == "g":
                gid = ch.get("id", "")
                if gid.startswith("layer-"):
                    walk(ch, parents + [ch], gid[len("layer-"):], glyph or ch.get("data-glyph") == "1")
                    continue
                if ch.get("data-merge") == "1":
                    cmds: list[tuple] = []
                    n = 0
                    for sub in ch:
                        cmds.extend(_elem_cmds(sub))
                        n += 1
                    face.pieces.append(_mk(ch, parents, layer, glyph, cmds, n))
                    continue
                walk(ch, parents + [ch], layer, glyph)
                continue
            if ch.get("data-ref"):
                p = _mk(ch, parents, layer, glyph, [], 0)
                p.ref = ch.get("data-ref")
                face.pieces.append(p)
                continue
            face.pieces.append(_mk(ch, parents, layer, glyph, _elem_cmds(ch), 1))

    def _mk(el, parents, layer, glyph, cmds, n) -> Piece:
        pid = el.get("id")
        if not pid:
            raise ValueError(f"{name}.svg: phần tử không có id ở lớp {layer}")
        path = serialize(cmds) if cmds else ""
        b = bounds_of(cmds) if cmds else (0.0, 0.0, 0.0, 0.0)
        sw = _inherit(el, parents, "stroke-width", "0")
        return Piece(
            face=name, id=pid, layer=layer, role=el.get("data-role", "glyph" if glyph else "misc"),
            cmds=cmds, path=path, bounds=b, even_odd=el.get("data-evenodd") == "1", glyph=glyph,
            stroke_only=el.get("data-stroke") == "1",
            fill=_paint(_inherit(el, parents, "fill", "#000000")), fill_opacity=float(_inherit(el, parents, "fill-opacity", 1)),
            stroke=_paint(_inherit(el, parents, "stroke", None)), stroke_opacity=float(_inherit(el, parents, "stroke-opacity", 1)),
            stroke_width=float(sw), n_sub=n,
        )

    walk(root, [], "root", False)
    ids = [p.id for p in face.pieces]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        raise ValueError(f"{name}.svg: id trùng {sorted(dup)}")
    for p in face.pieces:
        if p.ref and p.ref not in ids:
            raise ValueError(f"{name}.svg: {p.id} data-ref tới id không có: {p.ref}")
    n_frame = len(face.frame_pieces())
    if n_frame > MAX_FACE_PATHS:
        raise ValueError(f"{name}.svg: khung + bộ phận = {n_frame} path > trần {MAX_FACE_PATHS} (AC5.1)")
    return face
