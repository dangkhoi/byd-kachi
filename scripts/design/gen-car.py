#!/usr/bin/env python3
"""
gen-car.py — MỘT CHIẾC XE, BA MẶT, HAI ĐÍCH (spec docs/specs/kachi-visual-refresh.html §4.3 · §4.6 · R1 · T5/T6).

Nguồn:  design/car/top.svg · front.svg · side.svg  (mỗi bộ phận = một phần tử có id, hệ 24×24)
        design/car/paint.json                        (5 màu sơn — script điền phần `contrast`)
Đích:   app/src/main/res/drawable/ic_car_*.xml                  (43 icon 24dp, một tông, tint được — theo bảng ICONS dưới)
        app/src/main/res/drawable/car_face_*.xml                (4 mặt 48dp có chuyển sắc <aapt:attr> — mức tả thực (1))
        app/src/main/java/.../launcher/CarFramesGenerated.kt    (hằng path + hộp bao, SINH chứ không gõ tay)
        design/car/manifest.json                                (bộ phận · số path · hộp bao · thành phần icon)

Chỉ dùng thư viện chuẩn. Kết quả TẤT ĐỊNH (làm tròn 2 chữ số, bỏ số 0 thừa, thứ tự theo tài liệu).
    python3 scripts/design/gen-car.py            # sinh
    python3 scripts/design/gen-car.py --check    # sinh vào thư mục tạm rồi so BYTE với res/drawable + CarFramesGenerated.kt + manifest — lệch ⇒ exit 1

Luật đường ống (spec §4.6): KHÔNG công cụ nào khác được ghi vào đích; vá tay một tệp đã sinh = vi phạm, `--check` bắt.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC_DIR = os.path.join(ROOT, "design", "car")
# Đích THẬT (spec §4.6 luật cứng: chỉ script này ghi hình xe vào ba nơi dưới; vá tay = `--check` đỏ)
RES_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable")                       # ic_car_*.xml · car_face_*.xml
KT_PATH = os.path.join(ROOT, "app", "src", "main", "java", "com", "byd", "clusternav", "launcher", "CarFramesGenerated.kt")
MANIFEST_PATH = os.path.join(SRC_DIR, "manifest.json")                                        # bảng mảnh/hộp bao (đọc được, không build)
FACES = ("top", "front", "side")
SVG_NS = "{http://www.w3.org/2000/svg}"

# ── Ngữ pháp icon (spec §4.2 · AC2.1 · AC2.2) ─────────────────────────────────────────────────────────────────
ALPHA_CTX = "0.16"   # lớp NỀN — thân xe tô mờ
ALPHA_SUB = "0.38"   # lớp PHỤ — kính, thấu kính chưa sáng, gờ ca-pô
STROKE_MAIN = "1.8"  # nét CHÍNH
STROKE_SUB = "1.2"   # nét PHỤ (viền thân, gờ)
MAX_ICON_PATHS = 6   # AC5.1 icon 24dp
MAX_FACE_PATHS = 28  # AC5.1 khung + mọi bộ phận của một mặt (không tính glyph, highlight)

# ── Số & path ─────────────────────────────────────────────────────────────────────────────────────────────────

def fmt(v: float) -> str:
    s = f"{round(v + 0.0, 2):.2f}".rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s


_TOK = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def parse_d(d: str) -> list[tuple]:
    """Chuẩn hoá `d` về lệnh TUYỆT ĐỐI M · L · C · A · Z (H/V→L, S/Q/T→C, tương đối→tuyệt đối)."""
    toks = _TOK.findall(d)
    out: list[tuple] = []
    i = 0
    cx = cy = sx = sy = 0.0
    last_c2 = None
    cmd = None
    while i < len(toks):
        t = toks[i]
        if t.isalpha():
            cmd = t
            i += 1
            if cmd in "Zz":
                out.append(("Z",))
                cx, cy = sx, sy
                last_c2 = None
                continue
        if cmd is None:
            raise ValueError(f"path không bắt đầu bằng lệnh: {d!r}")

        def num(k: int) -> float:
            return float(toks[i + k])

        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            x, y = num(0), num(1)
            if rel:
                x, y = cx + x, cy + y
            out.append(("M", x, y))
            cx, cy = sx, sy = x, y
            i += 2
            cmd = "l" if rel else "L"  # M kéo dài thành L
            last_c2 = None
        elif c == "L":
            x, y = num(0), num(1)
            if rel:
                x, y = cx + x, cy + y
            out.append(("L", x, y))
            cx, cy = x, y
            i += 2
            last_c2 = None
        elif c == "H":
            x = num(0)
            x = cx + x if rel else x
            out.append(("L", x, cy))
            cx = x
            i += 1
            last_c2 = None
        elif c == "V":
            y = num(0)
            y = cy + y if rel else y
            out.append(("L", cx, y))
            cy = y
            i += 1
            last_c2 = None
        elif c == "C":
            x1, y1, x2, y2, x, y = (num(k) for k in range(6))
            if rel:
                x1, y1, x2, y2, x, y = cx + x1, cy + y1, cx + x2, cy + y2, cx + x, cy + y
            out.append(("C", x1, y1, x2, y2, x, y))
            cx, cy = x, y
            last_c2 = (x2, y2)
            i += 6
        elif c == "S":
            x2, y2, x, y = (num(k) for k in range(4))
            if rel:
                x2, y2, x, y = cx + x2, cy + y2, cx + x, cy + y
            x1, y1 = (2 * cx - last_c2[0], 2 * cy - last_c2[1]) if last_c2 else (cx, cy)
            out.append(("C", x1, y1, x2, y2, x, y))
            cx, cy = x, y
            last_c2 = (x2, y2)
            i += 4
        elif c in ("Q", "T"):
            if c == "Q":
                qx, qy, x, y = (num(k) for k in range(4))
                if rel:
                    qx, qy, x, y = cx + qx, cy + qy, cx + x, cy + y
                i += 4
            else:
                x, y = num(0), num(1)
                if rel:
                    x, y = cx + x, cy + y
                qx, qy = (2 * cx - last_c2[0], 2 * cy - last_c2[1]) if last_c2 else (cx, cy)
                i += 2
            x1, y1 = cx + 2 / 3 * (qx - cx), cy + 2 / 3 * (qy - cy)
            x2, y2 = x + 2 / 3 * (qx - x), y + 2 / 3 * (qy - y)
            out.append(("C", x1, y1, x2, y2, x, y))
            cx, cy = x, y
            last_c2 = (qx, qy)
        elif c == "A":
            rx, ry, rot, laf, sf, x, y = (num(k) for k in range(7))
            if rel:
                x, y = cx + x, cy + y
            out.append(("A", rx, ry, rot, int(laf), int(sf), x, y))
            cx, cy = x, y
            i += 7
            last_c2 = None
        else:
            raise ValueError(f"lệnh không hỗ trợ {cmd!r} trong {d!r}")
    return out


def serialize(cmds: list[tuple]) -> str:
    parts = []
    for c in cmds:
        k = c[0]
        if k == "Z":
            parts.append("Z")
        elif k in ("M", "L"):
            parts.append(f"{k}{fmt(c[1])},{fmt(c[2])}")
        elif k == "C":
            parts.append(f"C{fmt(c[1])},{fmt(c[2])} {fmt(c[3])},{fmt(c[4])} {fmt(c[5])},{fmt(c[6])}")
        elif k == "A":
            parts.append(f"A{fmt(c[1])},{fmt(c[2])} {fmt(c[3])} {c[4]} {c[5]} {fmt(c[6])},{fmt(c[7])}")
    return " ".join(parts)


def rect_cmds(x: float, y: float, w: float, h: float, r: float) -> list[tuple]:
    if r <= 0:
        return [("M", x, y), ("L", x + w, y), ("L", x + w, y + h), ("L", x, y + h), ("Z",)]
    r = min(r, w / 2, h / 2)
    return [
        ("M", x + r, y), ("L", x + w - r, y), ("A", r, r, 0, 0, 1, x + w, y + r),
        ("L", x + w, y + h - r), ("A", r, r, 0, 0, 1, x + w - r, y + h),
        ("L", x + r, y + h), ("A", r, r, 0, 0, 1, x, y + h - r),
        ("L", x, y + r), ("A", r, r, 0, 0, 1, x + r, y), ("Z",),
    ]


def ellipse_cmds(cx: float, cy: float, rx: float, ry: float) -> list[tuple]:
    if abs(rx - ry) < 1e-9:  # tròn: cùng khuôn với bộ U7 (M đỉnh · hai cung 1 1)
        return [("M", cx, cy - rx), ("A", rx, rx, 0, 1, 1, cx, cy + rx), ("A", rx, rx, 0, 1, 1, cx, cy - rx), ("Z",)]
    return [("M", cx - rx, cy), ("A", rx, ry, 0, 1, 1, cx + rx, cy), ("A", rx, ry, 0, 1, 1, cx - rx, cy), ("Z",)]


# ── Làm phẳng (cho hộp bao + trình vẽ xem trước) ─────────────────────────────────────────────────────────────

def _arc_points(x0, y0, rx, ry, rot, laf, sf, x, y, n=32):
    """SVG F.6.5 — cung → tham số tâm → n điểm."""
    if rx == 0 or ry == 0 or (x0 == x and y0 == y):
        return [(x, y)]
    phi = math.radians(rot)
    cp, sp = math.cos(phi), math.sin(phi)
    dx, dy = (x0 - x) / 2, (y0 - y) / 2
    x1p = cp * dx + sp * dy
    y1p = -sp * dx + cp * dy
    rx, ry = abs(rx), abs(ry)
    lam = (x1p / rx) ** 2 + (y1p / ry) ** 2
    if lam > 1:
        rx *= math.sqrt(lam)
        ry *= math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    coef = math.sqrt(max(0.0, num / den)) if den else 0.0
    if laf == sf:
        coef = -coef
    cxp, cyp = coef * rx * y1p / ry, -coef * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x0 + x) / 2
    cy = sp * cxp + cp * cyp + (y0 + y) / 2

    def ang(ux, uy, vx, vy):
        d = math.hypot(ux, uy) * math.hypot(vx, vy)
        a = math.acos(max(-1.0, min(1.0, (ux * vx + uy * vy) / d)))
        return -a if ux * vy - uy * vx < 0 else a

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sf and dt > 0:
        dt -= 2 * math.pi
    elif sf and dt < 0:
        dt += 2 * math.pi
    pts = []
    for k in range(1, n + 1):
        t = t1 + dt * k / n
        ex, ey = rx * math.cos(t), ry * math.sin(t)
        pts.append((cp * ex - sp * ey + cx, sp * ex + cp * ey + cy))
    return pts


def flatten(cmds: list[tuple], n_curve: int = 24) -> list[list[tuple[float, float]]]:
    """Path → danh sách đa giác (mỗi nhánh M một đa giác, kín hay không đều trả về các điểm)."""
    polys: list[list[tuple[float, float]]] = []
    cur: list[tuple[float, float]] = []
    cx = cy = 0.0
    for c in cmds:
        k = c[0]
        if k == "M":
            if cur:
                polys.append(cur)
            cur = [(c[1], c[2])]
            cx, cy = c[1], c[2]
        elif k == "L":
            cur.append((c[1], c[2]))
            cx, cy = c[1], c[2]
        elif k == "C":
            x1, y1, x2, y2, x, y = c[1:]
            for s in range(1, n_curve + 1):
                t = s / n_curve
                u = 1 - t
                cur.append((u * u * u * cx + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x,
                            u * u * u * cy + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y))
            cx, cy = x, y
        elif k == "A":
            rx, ry, rot, laf, sf, x, y = c[1:]
            cur.extend(_arc_points(cx, cy, rx, ry, rot, laf, sf, x, y))
            cx, cy = x, y
        elif k == "Z":
            if cur:
                polys.append(cur)
                cx, cy = cur[0]
            cur = []
    if cur:
        polys.append(cur)
    return polys


def bounds_of(cmds: list[tuple]) -> tuple[float, float, float, float]:
    xs, ys = [], []
    for poly in flatten(cmds, 48):
        for x, y in poly:
            xs.append(x)
            ys.append(y)
    return (round(min(xs), 2), round(min(ys), 2), round(max(xs), 2), round(max(ys), 2))


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


# ── Bảng ICON: id → (mặt, PHỤ, CHÍNH, vì-sao) ─────────────────────────────────────────────────────────────────
# CHÍNH: chuỗi = một mảnh; tuple = gộp nhiều mảnh thành MỘT path; hậu tố ":stroke" = vẽ bằng nét dù mảnh là vùng tô.
TOP_SUB = ["windscreen", "glass_rear"]
TOP_WS = ["windscreen"]
FRONT_SUB = ["windscreen", "headlamp_l", "headlamp_r", "bonnet"]
REAR_SUB = ["windscreen", "tail_bar", "plate"]

ICONS: dict[str, tuple[str, list[str], list, str]] = {
    # — mặt trên · lốp —
    "ic_car_top_tyre_fl": ("top", TOP_SUB, ["wheel_fl"], "ÁP SUẤT LỐP FL — khung xe + CHỈ bánh này, tô đặc. Ba bánh kia BỎ HẲN ([ĐO Pass 1 U7] vẽ kèm ba bánh kia thì bốn ô chỉ khác nhau 0.8dp ⇒ 4/8 đúng)."),
    "ic_car_top_tyre_fr": ("top", TOP_SUB, ["wheel_fr"], "ÁP SUẤT LỐP FR — khung xe + CHỈ bánh này, tô đặc. Ba bánh kia BỎ HẲN."),
    "ic_car_top_tyre_rl": ("top", TOP_SUB, ["wheel_rl"], "ÁP SUẤT LỐP RL — khung xe + CHỈ bánh này, tô đặc. Ba bánh kia BỎ HẲN."),
    "ic_car_top_tyre_rr": ("top", TOP_SUB, ["wheel_rr"], "ÁP SUẤT LỐP RR — khung xe + CHỈ bánh này, tô đặc. Ba bánh kia BỎ HẲN."),
    "ic_car_top_tyre_temp_fl": ("top", TOP_SUB, ["wheel_fl", "therm_stem", "therm_bulb"], "NHIỆT LỐP FL — bánh tô đặc + NHIỆT KẾ giữa xe (bầu tròn + cán) ⇒ hai họ lốp không lẫn."),
    "ic_car_top_tyre_temp_fr": ("top", TOP_SUB, ["wheel_fr", "therm_stem", "therm_bulb"], "NHIỆT LỐP FR — bánh tô đặc + NHIỆT KẾ giữa xe (bầu tròn + cán)."),
    "ic_car_top_tyre_temp_rl": ("top", TOP_SUB, ["wheel_rl", "therm_stem", "therm_bulb"], "NHIỆT LỐP RL — bánh tô đặc + NHIỆT KẾ giữa xe (bầu tròn + cán)."),
    "ic_car_top_tyre_temp_rr": ("top", TOP_SUB, ["wheel_rr", "therm_stem", "therm_bulb"], "NHIỆT LỐP RR — bánh tô đặc + NHIỆT KẾ giữa xe (bầu tròn + cán)."),
    # — mặt trên · cửa & kính —
    "ic_car_top_door_lf": ("top", TOP_SUB, ["door_lf"], "CỬA LF — vạt cửa mở ra ngoài đúng góc xe."),
    "ic_car_top_door_rf": ("top", TOP_SUB, ["door_rf"], "CỬA RF — vạt cửa mở ra ngoài đúng góc xe."),
    "ic_car_top_door_lr": ("top", TOP_SUB, ["door_lr"], "CỬA LR — vạt cửa mở ra ngoài đúng góc xe."),
    "ic_car_top_door_rr": ("top", TOP_SUB, ["door_rr"], "CỬA RR — vạt cửa mở ra ngoài đúng góc xe."),
    "ic_car_top_door_all": ("top", TOP_SUB, ["door_lf", "door_lr", "door_rf", "door_rr"], "TẤT CẢ CỬA — cả bốn vạt cửa mở."),
    "ic_car_top_window_lf": ("top", TOP_SUB, ["glass_lf"], "KÍNH LF — ô kính cửa trong thân, đúng góc xe, tô đặc trên nền hai ô kính cố định mờ."),
    "ic_car_top_window_rf": ("top", TOP_SUB, ["glass_rf"], "KÍNH RF — ô kính cửa trong thân, đúng góc xe."),
    "ic_car_top_window_lr": ("top", TOP_SUB, ["glass_lr"], "KÍNH LR — ô kính cửa trong thân, đúng góc xe."),
    "ic_car_top_window_rr": ("top", TOP_SUB, ["glass_rr"], "KÍNH RR — ô kính cửa trong thân, đúng góc xe."),
    "ic_car_top_window_all": ("top", TOP_SUB, ["glass_lf", "glass_rf", "glass_lr", "glass_rr"], "TẤT CẢ KÍNH — cả bốn ô kính cửa."),
    "ic_car_top_trunk": ("top", TOP_SUB, ["boot"], "CỐP SAU — mảng đuôi xe tô đặc (nắp ĐANG ĐÓNG, liền khối)."),
    "ic_car_top_sunroof": ("top", TOP_SUB, ["sunroof"], "CỬA SỔ TRỜI — ô nóc tô đặc (tấm kính ĐANG ĐÓNG, kín ô)."),
    "ic_car_top_sunshade": ("top", TOP_SUB, ["sunshade"], "RÈM CHE NẮNG — THANH CUỘN ở mép trước nóc + TẤM PHỦ có HAI NẾP GẤP khoét rỗng (even-odd)."),
    "ic_car_top_sunroof_pos": ("top", TOP_WS, ["sunroof:stroke", "sunroof_gap", "arrow_v_stem", "arrow_v_heads"], "VỊ TRÍ CỬA SỔ TRỜI — ô nóc NÉT + khe hở TÔ ở mép trước (kính đã trượt) + MŨI TÊN ĐÔI dọc xe (kính hậu bỏ để chừa chỗ mũi tên)."),
    "ic_car_top_mirror": ("top", TOP_SUB, ["mirror"], "GƯƠNG CHIẾU HẬU — hai tai gương hai bên."),
    "ic_car_top_lock": ("top", TOP_SUB, ["lock_shackle", "lock_body"], "KHOÁ XE — thân xe + ổ khoá giữa khoang."),
    "ic_car_top_seat_fl": ("top", TOP_SUB, ["seat_fl"], "GHẾ FL — đệm + tựa, đúng chỗ trong khoang."),
    # — mặt trên · đèn viền —
    "ic_car_top_ambient": ("top", TOP_WS, ["chev_front", "chev_rear"], "ĐÈN VIỀN CABIN (tất cả) — hào quang hắt vào khoang ở CẢ hai nửa, không ký hiệu đại lượng."),
    "ic_car_top_ambient_bright_front": ("top", TOP_WS, ["chev_front", "dots"], "ĐỘ SÁNG VIỀN TRƯỚC — hào quang nửa TRƯỚC + THANG BA CHẤM to dần giữa khoang."),
    "ic_car_top_ambient_bright_rear": ("top", TOP_WS, ["chev_rear", "dots"], "ĐỘ SÁNG VIỀN SAU — hào quang nửa SAU + THANG BA CHẤM to dần giữa khoang."),
    "ic_car_top_ambient_color_front": ("top", TOP_WS, ["chev_front", "drop"], "MÀU VIỀN TRƯỚC — hào quang nửa TRƯỚC + GIỌT SƠN giữa khoang."),
    "ic_car_top_ambient_color_rear": ("top", TOP_WS, ["chev_rear", "drop"], "MÀU VIỀN SAU — hào quang nửa SAU + GIỌT SƠN giữa khoang."),
    "ic_car_top_ambient_music": ("top", TOP_WS, ["chev_front", "chev_rear", "note_head", "note_stem"], "ĐÈN VIỀN THEO NHẠC — hào quang cả hai nửa + NỐT NHẠC giữa khoang."),
    # — mặt trước · đèn —
    "ic_car_front_highbeam": ("front", ["windscreen", "headlamp_r", "bonnet"], ["headlamp_l", "rays_high"], "ĐÈN PHA — MỘT đèn sáng + hai tia NGANG DÀI xuyên ra trước, nằm HẲN ngoài thân xe; đèn kia mờ."),
    "ic_car_front_lowbeam": ("front", ["windscreen", "headlamp_r", "bonnet"], ["headlamp_l", "rays_low"], "ĐÈN CỐT — MỘT đèn sáng + ba tia NGẮN CHÚC XUỐNG mặt đường, nằm HẲN ngoài thân xe."),
    "ic_car_front_headlight_mode": ("front", ["windscreen", "bonnet"], [("headlamp_l", "headlamp_r"), "mode_rays"], "CHẾ ĐỘ ĐÈN PHA — hai đèn cùng sáng + CẢ HAI KIỂU CHÙM bày cạnh nhau dưới cản: cốt (chúc) trái, pha (ngang) phải."),
    "ic_car_front_drl": ("front", FRONT_SUB, ["drl"], "ĐÈN BAN NGÀY — DẢI SÁNG dài tô đặc (LED strip) vắt qua hai hốc đèn mờ."),
    "ic_car_front_fog": ("front", FRONT_SUB, ["fog", "fog_beam"], "ĐÈN SƯƠNG MÙ TRƯỚC — đèn DƯỚI CẢN (thấp, sát mép vỏ) + tia thấp bị VỆT SƯƠNG cắt ngang."),
    "ic_car_front_sidelight": ("front", FRONT_SUB, ["sidelight"], "ĐÈN HÔNG — hốc đèn chính mờ (tắt) + hai chấm sáng nằm trên VÁCH HÔNG xe."),
    "ic_car_front_turn_l": ("front", FRONT_SUB, ["turn_l"], "XI-NHAN TRÁI — mũi tên TÔ ở cụm đèn trái, chỉ RA NGOÀI; bên phải không tô gì (chưa sáng)."),
    "ic_car_front_turn_r": ("front", FRONT_SUB, ["turn_r"], "XI-NHAN PHẢI — mũi tên TÔ ở cụm đèn phải, chỉ RA NGOÀI; bên trái không tô gì."),
    # — mặt sau (cùng bóng thân với mặt trước) —
    "ic_car_rear_fog": ("front", REAR_SUB, ["fog_rear", "fog_beam"], "ĐÈN SƯƠNG MÙ SAU — khung NHÌN TỪ SAU (đèn hậu DÀI NGANG + BIỂN SỐ mờ) + đèn sương mù THẤP bên trái, tô đặc + vệt sương."),
    "ic_car_rear_defrost": ("front", REAR_SUB, ["defrost_waves"], "SẤY KÍNH SAU — khung NHÌN TỪ SAU (đèn hậu + biển số mờ) + SÓNG NHIỆT trên kính hậu."),
    "ic_car_top_trunk_pos": ("front", ["plate"], ["hinge", "arrow_h_stem", "arrow_h_heads"], "VỊ TRÍ CỐP — nhìn TỪ SAU: mép khoang (vạch ngang) + NẮP CỐP HÉ (đường nghiêng, hở khe) + MŨI TÊN ĐÔI dọc bên trái."),
}

# Thành phần bốn mặt 48dp có chuyển sắc — theo THỨ TỰ vẽ (bánh dưới thân ở mặt trước/sau; trên thân ở mặt ngang).
FACE_VD: dict[str, tuple[str, list[str]]] = {
    # Xi-nhan · sương mù · đèn hông là LỚP TRẠNG THÁI (chỉ vẽ khi bật) ⇒ không nằm trong mặt "nghỉ".
    "top": ("top", ["glow_front", "glow_rear", "body", "windscreen", "glass_rear", "glass_lf", "glass_rf", "glass_lr", "glass_rr",
                    "sunroof", "headlamp", "drl", "tail", "wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr",
                    "bonnet", "boot", "mirror"]),
    "front": ("front", ["shadow", "glow_front", "wheel_l", "wheel_r", "body", "bonnet", "windscreen", "headlamp_l", "headlamp_r",
                        "drl", "mirror"]),
    "rear": ("front", ["shadow", "glow_rear", "wheel_l", "wheel_r", "body", "windscreen", "tail_bar", "plate", "fog_rear", "mirror"]),
    "side": ("side", ["shadow", "glow_front", "glow_rear", "body", "bonnet", "windscreen", "glass_front", "glass_rear", "glass_quarter",
                      "headlamp", "tail", "wheel_front", "wheel_rear", "rim_front", "rim_rear", "door_front", "door_rear", "mirror"]),
}


# ── Sinh icon 24dp ────────────────────────────────────────────────────────────────────────────────────────────

def _resolve(face: Face, spec) -> tuple[str, bool, bool, int]:
    """spec → (path, stroke?, evenOdd?, số mảnh). Tuple = gộp; ':stroke' = ép nét."""
    ids = list(spec) if isinstance(spec, tuple) else [spec]
    force_stroke = False
    paths, ev, n = [], False, 0
    by = face.by_id()
    for s in ids:
        if s.endswith(":stroke"):
            s = s[: -len(":stroke")]
            force_stroke = True
        p = by[s]
        if p.ref:
            p = by[p.ref]
        paths.append(p.path)
        ev = ev or p.even_odd
        n += 1
    stroke = force_stroke or all((by[s.split(":")[0]]).stroke_only for s in ids)
    return " ".join(paths), stroke, ev, n


def _path_xml(path: str, *, fill_alpha: str | None, stroke: bool, stroke_w: str | None, stroke_alpha: str | None,
              even_odd: bool, fill_and_stroke: bool = False) -> str:
    a = [f'android:pathData="{path}"']
    if stroke and not fill_and_stroke:
        a.append('android:fillColor="#00000000"')
    else:
        a.append('android:fillColor="#FFFFFF"')
        if fill_alpha:
            a.append(f'android:fillAlpha="{fill_alpha}"')
        if even_odd:
            a.append('android:fillType="evenOdd"')
    if stroke or fill_and_stroke:
        a.append('android:strokeColor="#FFFFFF"')
        a.append(f'android:strokeWidth="{stroke_w}"')
        if stroke_alpha:
            a.append(f'android:strokeAlpha="{stroke_alpha}"')
        a.append('android:strokeLineCap="round"')
        a.append('android:strokeLineJoin="round"')
    return "    <path " + "\n        ".join(a) + " />"


def gen_icon(faces: dict[str, Face], name: str) -> tuple[str, dict]:
    face_name, sub, main, why = ICONS[name]
    face = faces[face_name]
    by = face.by_id()
    body = by["body"]
    paths: list[str] = []
    # NỀN: thân tô 0.16 + viền nét phụ 0.38 — MỘT path (fillAlpha + strokeAlpha cùng phần tử)
    paths.append(_path_xml(body.path, fill_alpha=ALPHA_CTX, stroke=True, stroke_w=STROKE_SUB, stroke_alpha=ALPHA_SUB,
                           even_odd=False, fill_and_stroke=True))
    # PHỤ: vùng tô gộp một path · nét gộp một path
    sub_fill = [by[s].path for s in sub if not by[s].stroke_only]
    sub_stroke = [by[s].path for s in sub if by[s].stroke_only]
    if sub_fill:
        paths.append(_path_xml(" ".join(sub_fill), fill_alpha=ALPHA_SUB, stroke=False, stroke_w=None, stroke_alpha=None,
                               even_odd=False))
    if sub_stroke:
        paths.append(_path_xml(" ".join(sub_stroke), fill_alpha=None, stroke=True, stroke_w=STROKE_SUB, stroke_alpha=ALPHA_SUB,
                               even_odd=False))
    # CHÍNH: mỗi mục một path, alpha 1.0
    pieces_used = []
    for spec in main:
        path, stroke, ev, n = _resolve(face, spec)
        paths.append(_path_xml(path, fill_alpha=None, stroke=stroke, stroke_w=STROKE_MAIN if stroke else None,
                               stroke_alpha=None, even_odd=ev))
        pieces_used.append(spec if isinstance(spec, str) else "+".join(spec))
    if len(paths) > MAX_ICON_PATHS:
        raise ValueError(f"{name}: {len(paths)} path > trần {MAX_ICON_PATHS} (AC5.1)")
    xml = (
        "<!--\n"
        f"  SINH BỞI scripts/design/gen-car.py từ design/car/{face_name}.svg — KHÔNG SỬA TAY (sửa SVG rồi sinh lại; cờ check của script so byte).\n"
        "  VISUAL-REFRESH P3 · spec docs/specs/kachi-visual-refresh.html §4.2: NỀN thân 0.16 + viền 1.2/0.38 · PHỤ 0.38 · CHÍNH 1.0/nét 1.8.\n"
        "  Khung 24×24 · một tông #FFFFFF (chỗ dùng tint) · cap/join tròn · ≤ 6 path.\n"
        f"  {why}\n"
        "-->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp" android:height="24dp"\n'
        '    android:viewportWidth="24" android:viewportHeight="24">\n'
        + "\n".join(paths)
        + "\n</vector>\n"
    )
    meta = {"face": face_name, "sub": list(sub), "main": pieces_used, "paths": len(paths)}
    return xml, meta


# ── Sinh mặt 48dp có chuyển sắc (<aapt:attr>) ─────────────────────────────────────────────────────────────────

def _hex_a(color: str, opacity: float) -> str:
    color = color.lstrip("#")
    if len(color) == 3:
        color = "".join(c * 2 for c in color)
    a = max(0, min(255, round(opacity * 255)))
    return f"#{a:02X}{color.upper()}"


def _gradient_xml(face: Face, gid: str, b, paint: tuple[str, str] | None, attr: str) -> str:
    g = face.gradients[gid]
    w, h = b[2] - b[0], b[3] - b[1]
    stops = g.stops
    if gid == "paint" and paint:
        stops = [(0.0, paint[0], 1.0), (1.0, paint[1], 1.0)]
    items = "".join(
        f'\n                <item android:offset="{fmt(o)}" android:color="{_hex_a(c, op)}" />' for o, c, op in stops
    )
    if g.kind == "linear":
        x1, y1, x2, y2 = g.coords
        geo = (f'android:startX="{fmt(b[0] + x1 * w)}" android:startY="{fmt(b[1] + y1 * h)}" '
               f'android:endX="{fmt(b[0] + x2 * w)}" android:endY="{fmt(b[1] + y2 * h)}"')
    else:
        cx, cy, r = g.coords
        geo = (f'android:centerX="{fmt(b[0] + cx * w)}" android:centerY="{fmt(b[1] + cy * h)}" '
               f'android:gradientRadius="{fmt(r * max(w, h))}"')
    return (f'        <aapt:attr name="android:{attr}">\n'
            f'            <gradient android:type="{g.kind}" {geo}>{items}\n'
            f'            </gradient>\n'
            f'        </aapt:attr>\n')


def gen_face_vd(faces: dict[str, Face], view: str, paint: tuple[str, str] | None = None) -> str:
    face_name, order = FACE_VD[view]
    face = faces[face_name]
    by = face.by_id()
    out = []
    for pid in order:
        p = by[pid]
        attrs = [f'android:pathData="{p.path}"']
        inner = ""
        if p.fill and p.fill.startswith("url:"):
            inner += _gradient_xml(face, p.fill[4:], p.bounds, paint, "fillColor")
            if p.fill_opacity < 1:
                attrs.append(f'android:fillAlpha="{fmt(p.fill_opacity)}"')
        elif p.fill:
            attrs.append(f'android:fillColor="{_hex_a(p.fill, p.fill_opacity)}"')
        else:
            attrs.append('android:fillColor="#00000000"')
        if p.even_odd:
            attrs.append('android:fillType="evenOdd"')
        if p.stroke and p.stroke_width > 0:
            if p.stroke.startswith("url:"):
                inner += _gradient_xml(face, p.stroke[4:], p.bounds, paint, "strokeColor")
            else:
                attrs.append(f'android:strokeColor="{_hex_a(p.stroke, p.stroke_opacity)}"')
            attrs.append(f'android:strokeWidth="{fmt(p.stroke_width)}"')
            attrs.append('android:strokeLineCap="round" android:strokeLineJoin="round"')
        if inner:
            out.append("    <path " + "\n        ".join(attrs) + ">\n" + inner + "    </path>")
        else:
            out.append("    <path " + "\n        ".join(attrs) + " />")
    return (
        "<!--\n"
        f"  SINH BỞI scripts/design/gen-car.py từ design/car/{face_name}.svg — KHÔNG SỬA TAY.\n"
        f"  VISUAL-REFRESH P3 · mặt {view.upper()} 48dp, mức tả thực (1) §4.8: thân = chuyển sắc MÀU SƠN (mặc định Trắng ngọc trai),\n"
        "  kính có phản chiếu, đèn có quầng (gradient toả, KHÔNG blur), bóng đổ = ellipse toả. Gradient <aapt:attr> render đúng trên API 29 ([ĐO] T4).\n"
        "  ⚠ setTint đè cả hình ⇒ KHÔNG tint mặt này; màu sơn đổi bằng cách sinh lại (hoặc VectorCarArt vẽ Canvas từ CarFramesGenerated).\n"
        "-->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="48dp" android:height="48dp"\n'
        '    android:viewportWidth="24" android:viewportHeight="24">\n'
        + "\n".join(out)
        + "\n</vector>\n"
    )


# ── Kotlin sinh ───────────────────────────────────────────────────────────────────────────────────────────────

def gen_kotlin(faces: dict[str, Face]) -> str:
    lines = [
        "package com.byd.clusternav.launcher",
        "",
        "/**",
        " * ═══ SINH BỞI `scripts/design/gen-car.py` từ `design/car/{top,front,side}.svg` — KHÔNG SỬA TAY ═══════════════════",
        " *",
        " * VISUAL-REFRESH P3 (spec `docs/specs/kachi-visual-refresh.html` §4.3 · R1 · AC1.2): đây là nửa \"sinh, không gõ tay\" của",
        " * [CarFrames]. Mỗi mảnh = một bộ phận có tên trong SVG nguồn; chuỗi path CHÍNH LÀ chuỗi `android:pathData` của các",
        " * `ic_car_*.xml` cùng lượt sinh (icon gộp nhiều mảnh thì pathData = các chuỗi nối bằng MỘT dấu cách, theo thứ tự).",
        " * Hộp bao tính bằng script trên hình học THẬT (làm phẳng cung/bezier) — không phải hộp bao điểm điều khiển như",
        " * `Path.computeBounds`, nên có thể hụt ≤ 0.3 so với số Android trả về cho thân xe (điểm điều khiển 7.2 vs mép 7.35).",
        " * Không có mã màu ở đây (luật 0-hex của `ThemePaletteContractTest`): `role` là TÊN vai, `CarPartStyle` đổi ra token.",
        " * Sửa: sửa SVG rồi `python3 scripts/design/gen-car.py`; `--check` so byte (đường ống §4.6).",
        " */",
        "internal object CarFramesGenerated {",
        "",
        "    /**",
        "     * Một mảnh hình. [face] top|front|side · [layer] lớp §4.3 · [role] vai màu (paint · glass · lamp · drl · turn · tail ·",
        "     * glow · glowtail · tyre · rim · flap · panel · mirror · shadow · plate · shade · glyph) · [path] hệ 24×24 ·",
        "     * [bounds] `[trái, trên, phải, dưới]` · [evenOdd] tô even-odd (rèm) · [strokeOnly] vẽ bằng nét · [glyph] ký hiệu",
        "     * chỉ dành cho icon (không thuộc khung xe) · [subpaths] số nhánh M đã gộp.",
        "     */",
        "    internal class Piece(",
        "        val face: String,",
        "        val id: String,",
        "        val layer: String,",
        "        val role: String,",
        "        val path: String,",
        "        val bounds: FloatArray,",
        "        val evenOdd: Boolean,",
        "        val strokeOnly: Boolean,",
        "        val glyph: Boolean,",
        "        val subpaths: Int,",
        "    )",
        "",
        "    /** Mọi mảnh của ba mặt, đúng THỨ TỰ VẼ trong SVG (lớp dưới trước). Lớp highlight dùng lại thân — xem [HIGHLIGHT_REF]. */",
        "    val PIECES: List<Piece> = listOf(",
    ]
    for fname in FACES:
        face = faces[fname]
        lines.append(f"        // ── {fname} ──")
        for p in face.pieces:
            if p.ref:
                continue
            b = ", ".join(f"{fmt(v)}f" for v in p.bounds)
            lines.append(
                f'        Piece("{p.face}", "{p.id}", "{p.layer}", "{p.role}", "{p.path}", floatArrayOf({b}), '
                f"{str(p.even_odd).lower()}, {str(p.stroke_only).lower()}, {str(p.glyph).lower()}, {p.n_sub}),"
            )
    lines.append("    )")
    lines.append("")
    lines.append("    /** Lớp highlight của mỗi mặt dùng LẠI hình của mảnh nào (viền theo tone, không phải path mới). */")
    refs = ", ".join(f'"{f}" to "{[p for p in faces[f].pieces if p.ref][0].ref}"' for f in FACES)
    lines.append(f"    val HIGHLIGHT_REF: Map<String, String> = mapOf({refs})")
    lines.append("}")
    lines.append("")
    return "\n".join(lines)


# ── Màu sơn & tương phản (§4.8 bảng 5 màu · AC8.5) ───────────────────────────────────────────────────────────

def _lum(hex_color: str) -> float:
    c = hex_color.lstrip("#")
    r, g, b = (int(c[i:i + 2], 16) / 255 for i in (0, 2, 4))

    def lin(v):
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4

    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def contrast(a: str, b: str) -> float:
    la, lb = _lum(a), _lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return round((hi + 0.05) / (lo + 0.05), 2)


def gen_paint(src_path: str) -> str:
    with open(src_path, encoding="utf-8") as f:
        data = json.load(f)
    floor = float(data.get("outlineFloor", 3.0))
    bgs = data["backgrounds"]
    for p in data["paints"]:
        top, bottom = p["from"], p["to"]
        c = {}
        for k, bg in bgs.items():
            t, b = contrast(top, bg), contrast(bottom, bg)
            c[k] = {"top": t, "bottom": b, "worst": min(t, b)}
        p["contrast"] = c
        # AC3.3: đo CẢ HAI ĐẦU gradient — đầu nào chạm nền là chiếc xe mất mép ở đầu đó (đáy trên nền tối, đỉnh trên nền sáng)
        p["forceOutline"] = {k: v["worst"] < floor for k, v in c.items()}
    data["_generated"] = ("contrast · forceOutline do scripts/design/gen-car.py điền (WCAG 2.x, cả hai đầu gradient vs nền; "
                          "worst < outlineFloor ⇒ app tự bật viền partLine); sửa màu rồi sinh lại")
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


# ── Chạy ──────────────────────────────────────────────────────────────────────────────────────────────────────

def dests(root: str | None = None) -> dict[str, str]:
    """Ba đích của script. `root=None` = đích thật; `root=<tmp>` = bản sao để `--check` so byte."""
    if root is None:
        return {"drawable": RES_DIR, "kotlin": KT_PATH, "manifest": MANIFEST_PATH, "paint": os.path.join(SRC_DIR, "paint.json")}
    return {"drawable": os.path.join(root, "drawable"), "kotlin": os.path.join(root, "CarFramesGenerated.kt"),
            "manifest": os.path.join(root, "manifest.json"), "paint": os.path.join(root, "paint.json")}


def generate(d: dict[str, str]) -> dict:
    faces = {f: load_face(f) for f in FACES}
    dr = d["drawable"]
    os.makedirs(dr, exist_ok=True)
    manifest: dict = {"faces": {}, "icons": {}, "faceVectors": {}}
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
    for view in FACE_VD:
        xml = gen_face_vd(faces, view)
        with open(os.path.join(dr, f"car_face_{view}.xml"), "w", encoding="utf-8") as f:
            f.write(xml)
        manifest["faceVectors"][f"car_face_{view}"] = {"face": FACE_VD[view][0], "paths": len(FACE_VD[view][1])}
    os.makedirs(os.path.dirname(d["kotlin"]), exist_ok=True)
    with open(d["kotlin"], "w", encoding="utf-8") as f:
        f.write(gen_kotlin(faces))
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
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
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
            for key, label in (("kotlin", "CarFramesGenerated.kt"), ("manifest", "design/car/manifest.json"),
                               ("paint", "design/car/paint.json (chưa điền contrast — chạy lại không --check)")):
                if _read(want_d[key]) != _read(have_d[key]):
                    bad.append(label)
            if bad:
                print("LỆCH so với nguồn (vá tay? quên sinh lại?):")
                for k in bad:
                    print("  " + k)
                return 1
            print(f"OK — {len(want) + 3} tệp khớp byte")
            return 0
    m = generate(dests(a.out))
    for fname, fm in m["faces"].items():
        print(f"{fname}: {fm['framePaths']}/{fm['maxFramePaths']} path khung · {len(fm['pieces'])} mảnh")
    print(f"{len(m['icons'])} icon · {len(m['faceVectors'])} mặt 48dp → {dests(a.out)['drawable']}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
