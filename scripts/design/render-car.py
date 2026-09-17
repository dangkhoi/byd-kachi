#!/usr/bin/env python3
"""
render-car.py — xem trước hình xe & icon bằng Pillow (KHÔNG có rsvg/cairosvg/inkscape trên máy build ⇒ tự làm phẳng path).

Chỉ để NGẮM (docs/diagnostics/visual-refresh-2026-09-16/p3/preview-*.png), không nằm trên đường build. Dùng lại bộ đọc SVG + bảng
ICONS của gen-car.py để thứ được vẽ ở đây đúng là thứ được sinh.
    python3 scripts/design/render-car.py            # → docs/diagnostics/visual-refresh-2026-09-16/p3/preview-*.png
Gradient: tuyến tính/toả tính theo hộp bao mảnh (như VectorDrawable); nét: đường dày, khớp tròn; siêu lấy mẫu 4×.
"""
from __future__ import annotations

import importlib.util
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("gen_car", os.path.join(HERE, "gen-car.py"))
gc = importlib.util.module_from_spec(spec)
assert spec.loader
sys.modules["gen_car"] = gc  # dataclasses cần module có trong sys.modules khi định nghĩa lớp
spec.loader.exec_module(gc)

SS = 4  # siêu lấy mẫu
ACCENT = "#4c7dff"


def _rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    if len(h) == 3:
        h = "".join(c * 2 for c in h)
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))  # type: ignore


def _lerp_stop(stops, t):
    t = max(0.0, min(1.0, t))
    prev = stops[0]
    for s in stops[1:]:
        if t <= s[0]:
            span = s[0] - prev[0]
            u = 0.0 if span <= 0 else (t - prev[0]) / span
            c0, c1 = _rgb(prev[1]), _rgb(s[1])
            return tuple(round(c0[i] + (c1[i] - c0[i]) * u) for i in range(3)) + (round((prev[2] + (s[2] - prev[2]) * u) * 255),)
        prev = s
    return _rgb(prev[1]) + (round(prev[2] * 255),)


def _gradient_image(g: gc.Gradient, box_px, size, stops=None) -> Image.Image:
    """Ảnh RGBA cỡ canvas, tô gradient theo hộp bao (đơn vị px) — chỉ tính trong hộp bao."""
    stops = stops or g.stops
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    x0, y0, x1, y1 = box_px
    w, h = max(1.0, x1 - x0), max(1.0, y1 - y0)
    ix0, iy0, ix1, iy1 = int(max(0, x0)) - 1, int(max(0, y0)) - 1, int(min(size[0], x1)) + 2, int(min(size[1], y1)) + 2
    px = img.load()
    if g.kind == "linear":
        gx1, gy1, gx2, gy2 = g.coords
        ax, ay = x0 + gx1 * w, y0 + gy1 * h
        bx, by = x0 + gx2 * w, y0 + gy2 * h
        dx, dy = bx - ax, by - ay
        dd = dx * dx + dy * dy or 1.0
        for y in range(max(0, iy0), min(size[1], iy1)):
            for x in range(max(0, ix0), min(size[0], ix1)):
                t = ((x - ax) * dx + (y - ay) * dy) / dd
                px[x, y] = _lerp_stop(stops, t)
    else:
        cx, cy, r = g.coords
        ccx, ccy = x0 + cx * w, y0 + cy * h
        rx, ry = r * w, r * h
        for y in range(max(0, iy0), min(size[1], iy1)):
            for x in range(max(0, ix0), min(size[0], ix1)):
                t = math.hypot((x - ccx) / rx, (y - ccy) / ry) if rx and ry else 1.0
                px[x, y] = _lerp_stop(stops, t)
    return img


def _mask(polys, size, even_odd) -> Image.Image:
    m = Image.new("1", size, 0)
    for poly in polys:
        if len(poly) < 3:
            continue
        layer = Image.new("1", size, 0)
        ImageDraw.Draw(layer).polygon(poly, fill=1)
        m = ImageChops.logical_xor(m, layer) if even_odd else ImageChops.logical_or(m, layer)
    return m.convert("L")


class Canvas:
    def __init__(self, size_px: int, bg: str, scale_units: float = 24.0):
        self.size = size_px * SS
        self.k = self.size / scale_units
        self.img = Image.new("RGBA", (self.size, self.size), _rgb(bg) + (255,))

    def px(self, pt):
        return (pt[0] * self.k, pt[1] * self.k)

    def fill(self, piece: gc.Piece, face: gc.Face, color=None, alpha=1.0, paint=None):
        polys = [[self.px(p) for p in poly] for poly in gc.flatten(piece.cmds)]
        mask = _mask(polys, (self.size, self.size), piece.even_odd)
        if alpha < 1:
            mask = mask.point(lambda v: int(v * alpha))
        if color is not None:
            layer = Image.new("RGBA", (self.size, self.size), _rgb(color) + (255,))
        elif piece.fill and piece.fill.startswith("url:"):
            g = face.gradients[piece.fill[4:]]
            stops = None
            if piece.fill[4:] == "paint" and paint:
                stops = [(0.0, paint[0], 1.0), (1.0, paint[1], 1.0)]
            b = piece.bounds
            layer = _gradient_image(g, (b[0] * self.k, b[1] * self.k, b[2] * self.k, b[3] * self.k), (self.size, self.size), stops)
        elif piece.fill:
            layer = Image.new("RGBA", (self.size, self.size), _rgb(piece.fill) + (round(piece.fill_opacity * 255),))
        else:
            return
        r, g_, b_, a = layer.split()
        a = ImageChops.multiply(a, mask)
        self.img.alpha_composite(Image.merge("RGBA", (r, g_, b_, a)))

    def stroke(self, piece: gc.Piece, color: str, width_units: float, alpha=1.0):
        layer = Image.new("RGBA", (self.size, self.size), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        w = max(1, round(width_units * self.k))
        col = _rgb(color) + (round(alpha * 255),)
        closed = any(c[0] == "Z" for c in piece.cmds)
        for poly in gc.flatten(piece.cmds):
            pts = [self.px(p) for p in poly]
            if closed and len(pts) > 2:
                pts.append(pts[0])
            if len(pts) >= 2:
                d.line(pts, fill=col, width=w, joint="curve")
                r = w / 2
                for p in (pts[0], pts[-1]):
                    d.ellipse((p[0] - r, p[1] - r, p[0] + r, p[1] + r), fill=col)
        self.img.alpha_composite(layer)

    def out(self) -> Image.Image:
        return self.img.resize((self.size // SS, self.size // SS), Image.LANCZOS)


def render_face(faces, view: str, size: int, bg: str, paint=None, highlight_id: str | None = None) -> Image.Image:
    face_name, order = gc.FACE_VD[view]
    face = faces[face_name]
    by = face.by_id()
    cv = Canvas(size, bg)
    for pid in order:
        p = by[pid]
        if highlight_id and pid == highlight_id:
            cv.fill(p, face, color=ACCENT)
            cv.stroke(p, ACCENT, 0.3)
            continue
        cv.fill(p, face, paint=paint)
        if p.stroke and p.stroke_width > 0 and not p.stroke.startswith("url:"):
            cv.stroke(p, p.stroke, p.stroke_width, p.stroke_opacity)
    return cv.out()


def render_icon(faces, name: str, size: int, bg: str, ink: str = "#ffffff") -> Image.Image:
    face_name, sub, main, _ = gc.ICONS[name]
    face = faces[face_name]
    by = face.by_id()
    cv = Canvas(size, bg)
    body = by["body"]
    cv.fill(body, face, color=ink, alpha=float(gc.ALPHA_CTX))
    cv.stroke(body, ink, float(gc.STROKE_SUB), float(gc.ALPHA_SUB))
    for s in sub:
        p = by[s]
        if p.stroke_only:
            cv.stroke(p, ink, float(gc.STROKE_SUB), float(gc.ALPHA_SUB))
        else:
            cv.fill(p, face, color=ink, alpha=float(gc.ALPHA_SUB))
    for spec_ in main:
        ids = list(spec_) if isinstance(spec_, tuple) else [spec_]
        for s in ids:
            force = s.endswith(":stroke")
            p = by[s.replace(":stroke", "")]
            if force or p.stroke_only:
                cv.stroke(p, ink, float(gc.STROKE_MAIN))
            else:
                cv.fill(p, face, color=ink)
    return cv.out()


def sheet(images: list[tuple[str, Image.Image]], cols: int, bg: str, label_h=14) -> Image.Image:
    cw = max(i.width for _, i in images)
    ch = max(i.height for _, i in images) + label_h
    rows = (len(images) + cols - 1) // cols
    out = Image.new("RGB", (cols * (cw + 8) + 8, rows * (ch + 8) + 8), _rgb(bg))
    d = ImageDraw.Draw(out)
    font = ImageFont.load_default()
    for k, (name, im) in enumerate(images):
        x = 8 + (k % cols) * (cw + 8)
        y = 8 + (k // cols) * (ch + 8)
        out.paste(im.convert("RGB"), (x, y))
        d.text((x, y + im.height + 1), name[:22], fill=(150, 160, 175), font=font)
    return out


def main() -> int:
    faces = {f: gc.load_face(f) for f in gc.FACES}
    out = os.path.join(gc.ROOT, "docs", "diagnostics", "visual-refresh-2026-09-16", "p3")
    os.makedirs(out, exist_ok=True)
    bgs = {"dark": "#0a0d13", "light": "#eef1f6"}
    for view in gc.FACE_VD:
        for size in (240, 48):
            for tone, bg in bgs.items():
                render_face(faces, view, size, bg).save(os.path.join(out, f"preview-{view}-{size}-{tone}.png"))
    # bảng màu sơn: 5 màu × 2 nền, mặt trên 160px
    import json
    with open(os.path.join(gc.SRC_DIR, "paint.json"), encoding="utf-8") as f:
        paints = json.load(f)["paints"]
    cells = []
    for tone, bg in bgs.items():
        for p in paints:
            cells.append((f"{p['id']} {tone}", render_face(faces, "top", 160, bg, paint=(p["from"], p["to"]))))
    sheet(cells, 5, "#20242c").save(os.path.join(out, "preview-paints.png"))
    # bảng nổi từng bộ phận mặt TRÊN
    top = faces["top"]
    cells = [(pid, render_face(faces, "top", 120, "#0a0d13", highlight_id=pid)) for pid in gc.FACE_VD["top"][1]]
    # vạt cửa/rèm/xi-nhan không nằm trong FACE_VD (chỉ vẽ khi có trạng thái) — vẽ đè thêm
    by = top.by_id()
    for pid in ("door_lf", "door_rf", "door_lr", "door_rr", "sunshade", "turn_l", "turn_r"):
        im = render_face(faces, "top", 120, "#0a0d13")
        cv = Canvas(120, "#0a0d13")
        cv.img = im.resize((120 * SS, 120 * SS)).convert("RGBA")
        cv.fill(by[pid], top, color=ACCENT)
        cells.append((pid, cv.out()))
    sheet(cells, 7, "#20242c").save(os.path.join(out, "preview-top-parts.png"))
    # 43 icon 24dp ×3 (72px) trên nền tối và sáng (mực trắng / mực tối)
    for tone, bg, ink in (("dark", "#161b24", "#ffffff"), ("light", "#f4f6fa", "#1a2130")):
        cells = [(n, render_icon(faces, n, 72, bg, ink)) for n in sorted(gc.ICONS)]
        sheet(cells, 9, bg).save(os.path.join(out, f"preview-icons-{tone}.png"))
    # bảng tổng: 4 mặt × {tối, sáng} 240px + hàng 48px phóng NEAREST ×3 (soi điểm ảnh cỡ ô nhỏ)
    cells = [(f"{v} {t}", Image.open(os.path.join(out, f"preview-{v}-240-{t}.png"))) for t in bgs for v in gc.FACE_VD]
    sheet(cells, 4, "#20242c").save(os.path.join(out, "preview-sheet-faces.png"))
    cells = [(f"{v} 48px ×3", Image.open(os.path.join(out, f"preview-{v}-48-{t}.png")).resize((144, 144), Image.NEAREST))
             for t in bgs for v in gc.FACE_VD]
    sheet(cells, 4, "#20242c").save(os.path.join(out, "preview-sheet-48.png"))
    print("→", out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
