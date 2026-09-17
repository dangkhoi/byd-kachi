#!/usr/bin/env python3
"""
gen-icons.py — ĐƯỜNG ỐNG ICON (spec docs/specs/kachi-visual-refresh.html §4.6 · R2 · T4/T7/T9).

Nguồn:   design/glyph/<id>.svg  (một tệp SVG 24×24 mỗi icon, path có `data-layer` main|sub|bg)
Ngữ pháp: design/icon-grammar.json (họ màu theo lĩnh vực, cờ `large`, ghi chú khái niệm)
Đích:    <out>/ic_<id>.xml (24dp) + ic_<id>_l.xml (32dp) + ic_<id>_xl.xml (48dp) cho icon `large`.

Luật đóng cứng trong script (mỗi luật = một AC của spec; vi phạm ⇒ dừng, không sinh tệp):
  AC2.1  thang alpha đúng 3 bậc: main 1.0 · sub 0.38 · bg 0.16 (theo `data-layer`, không gõ tay alpha)
  AC2.2  hai độ dày nét: 1.8 (lớp main) · 1.2 (lớp sub/bg)
  AC2.3  strokeLineCap/Join = round trên MỌI đường có nét; đường chỉ tô KHÔNG mang thuộc tính nét
  AC2.4  khung 24×24 · hộp mực (kể cả nửa nét) trong ô quang học 2..22 · cạnh lớn ≥ 14
  AC2.5  màu chỉ đến từ họ màu của lĩnh vực khai trong grammar (token `ink|main|light|deep|grad|glow`)
  AC5.1  ≤ 6 path ở 24dp · ≤ 10 path ở 32/48dp
  §4.2   ≤ 3 lớp · lớp main luôn tồn tại

Đầu ra tất định (thứ tự thuộc tính cố định, số làm tròn 2 chữ số) ⇒ `--check` sinh lại rồi so byte với thư mục
đích; lệch = ai đó vá tay tệp sinh (vi phạm luật cứng §4.6).

Cách dùng:
  python3 scripts/design/gen-icons.py                                  # → app/src/main/res/drawable (đích thật)
  python3 scripts/design/gen-icons.py --check app/src/main/res/drawable   # exit 1 nếu lệch (T10 sinh-lại-so-byte)
"""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GLYPH_DIR = ROOT / "design" / "glyph"
GRAMMAR = ROOT / "design" / "icon-grammar.json"
# Đích THẬT (spec §4.6 luật cứng: chỉ script này được ghi icon glyph vào res/drawable — vá tay = `--check` đỏ).
DEFAULT_OUT = ROOT / "app" / "src" / "main" / "res" / "drawable"

SVG_NS = "{http://www.w3.org/2000/svg}"
LAYER_ALPHA = {"main": 1.0, "sub": 0.38, "bg": 0.16}
STROKE_MAIN, STROKE_SUB = 1.8, 1.2
PATH_CAP = {24: 6, 32: 10, 48: 10}
OPTICAL_LO, OPTICAL_HI, OPTICAL_TOL, MIN_SIDE = 2.0, 22.0, 0.05, 16.0   # = IconGeometryContractTest (2..22 dung sai 0.05 · cạnh lớn ≥ 16)
VARIANT_SUFFIX = {24: "", 32: "_l", 48: "_xl"}


class GlyphError(Exception):
    pass


# ── path parsing (đủ cho bộ nguồn: M L H V C S Q T A Z, tuyệt đối + tương đối) ──────────────────────────

_TOK = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def tokenize(d: str) -> list[str]:
    return _TOK.findall(d)


def fmt(v: float) -> str:
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s


def normalize_path(d: str) -> str:
    """Định dạng lại pathData tất định: lệnh + số cách nhau một khoảng trắng, số làm tròn 2 chữ số."""
    out: list[str] = []
    for t in tokenize(d):
        if t[0].isalpha():
            out.append(t)
        else:
            out.append(fmt(float(t)))
    return " ".join(out)


def _arc_points(x1, y1, rx, ry, phi, fa, fs, x2, y2, n=16):
    """Chuyển cung SVG (endpoint) sang điểm — chỉ để đo hộp bao/xem trước."""
    if rx == 0 or ry == 0:
        return [(x2, y2)]
    phi = math.radians(phi)
    cp, sp = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p = cp * dx + sp * dy
    y1p = -sp * dx + cp * dy
    rx, ry = abs(rx), abs(ry)
    lam = (x1p**2) / (rx**2) + (y1p**2) / (ry**2)
    if lam > 1:
        rx *= math.sqrt(lam)
        ry *= math.sqrt(lam)
    num = rx**2 * ry**2 - rx**2 * y1p**2 - ry**2 * x1p**2
    den = rx**2 * y1p**2 + ry**2 * x1p**2
    coef = math.sqrt(max(0.0, num / den)) if den else 0.0
    if fa == fs:
        coef = -coef
    cxp = coef * rx * y1p / ry
    cyp = -coef * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x1 + x2) / 2
    cy = sp * cxp + cp * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        d = ux * vx + uy * vy
        l = math.hypot(ux, uy) * math.hypot(vx, vy)
        a = math.acos(max(-1, min(1, d / l)))
        return -a if ux * vy - uy * vx < 0 else a

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dt > 0:
        dt -= 2 * math.pi
    elif fs and dt < 0:
        dt += 2 * math.pi
    pts = []
    for i in range(1, n + 1):
        t = t1 + dt * i / n
        px = cp * rx * math.cos(t) - sp * ry * math.sin(t) + cx
        py = sp * rx * math.cos(t) + cp * ry * math.sin(t) + cy
        pts.append((px, py))
    return pts


def flatten(d: str, n: int = 12) -> list[list[tuple[float, float]]]:
    """pathData → danh sách subpath (đa giác điểm). Dùng cho hộp bao (gen) và xem trước (audit)."""
    toks = tokenize(d)
    i = 0
    cmd = None
    cx = cy = 0.0
    sx = sy = 0.0
    lc2 = None  # điểm điều khiển cuối (cho S/T)
    subs: list[list[tuple[float, float]]] = []
    cur: list[tuple[float, float]] = []

    def num():
        nonlocal i
        v = float(toks[i])
        i += 1
        return v

    while i < len(toks):
        t = toks[i]
        if t[0].isalpha():
            cmd = t
            i += 1
            if cmd in "Zz":
                if cur:
                    subs.append(cur)
                cur = []
                cx, cy = sx, sy
                continue
        if cmd is None:
            raise GlyphError("pathData không bắt đầu bằng lệnh")
        rel = cmd.islower()
        c = cmd.upper()
        if c == "M":
            x, y = num(), num()
            if rel:
                x += cx
                y += cy
            if cur:
                subs.append(cur)
            cur = [(x, y)]
            cx, cy, sx, sy = x, y, x, y
            cmd = "l" if rel else "L"
            lc2 = None
        elif c == "L":
            x, y = num(), num()
            if rel:
                x += cx
                y += cy
            cur.append((x, y))
            cx, cy = x, y
            lc2 = None
        elif c == "H":
            x = num()
            if rel:
                x += cx
            cur.append((x, cy))
            cx = x
            lc2 = None
        elif c == "V":
            y = num()
            if rel:
                y += cy
            cur.append((cx, y))
            cy = y
            lc2 = None
        elif c in ("C", "S"):
            if c == "C":
                x1, y1 = num(), num()
                if rel:
                    x1 += cx
                    y1 += cy
            else:
                x1, y1 = (2 * cx - lc2[0], 2 * cy - lc2[1]) if lc2 else (cx, cy)
            x2, y2, x, y = num(), num(), num(), num()
            if rel:
                x2 += cx
                y2 += cy
                x += cx
                y += cy
            for k in range(1, n + 1):
                s = k / n
                px = (1 - s) ** 3 * cx + 3 * (1 - s) ** 2 * s * x1 + 3 * (1 - s) * s**2 * x2 + s**3 * x
                py = (1 - s) ** 3 * cy + 3 * (1 - s) ** 2 * s * y1 + 3 * (1 - s) * s**2 * y2 + s**3 * y
                cur.append((px, py))
            lc2 = (x2, y2)
            cx, cy = x, y
        elif c in ("Q", "T"):
            if c == "Q":
                x1, y1 = num(), num()
                if rel:
                    x1 += cx
                    y1 += cy
            else:
                x1, y1 = (2 * cx - lc2[0], 2 * cy - lc2[1]) if lc2 else (cx, cy)
            x, y = num(), num()
            if rel:
                x += cx
                y += cy
            for k in range(1, n + 1):
                s = k / n
                px = (1 - s) ** 2 * cx + 2 * (1 - s) * s * x1 + s**2 * x
                py = (1 - s) ** 2 * cy + 2 * (1 - s) * s * y1 + s**2 * y
                cur.append((px, py))
            lc2 = (x1, y1)
            cx, cy = x, y
        elif c == "A":
            rx, ry, phi, fa, fs, x, y = num(), num(), num(), int(num()), int(num()), num(), num()
            if rel:
                x += cx
                y += cy
            cur.extend(_arc_points(cx, cy, rx, ry, phi, fa, fs, x, y, n * 2))
            cx, cy = x, y
            lc2 = None
        else:
            raise GlyphError(f"lệnh path không hỗ trợ: {cmd}")
    if cur:
        subs.append(cur)
    return subs


def bbox(subs) -> tuple[float, float, float, float]:
    xs = [p[0] for s in subs for p in s]
    ys = [p[1] for s in subs for p in s]
    return min(xs), min(ys), max(xs), max(ys)


# ── nguồn SVG → mô hình lớp ──────────────────────────────────────────────────────────────────────────


class Layer:
    __slots__ = ("d", "layer", "fill", "stroke", "width", "min_size", "fx")

    def __init__(self, d, layer, fill, stroke, width, min_size, fx):
        self.d, self.layer, self.fill, self.stroke = d, layer, fill, stroke
        self.width, self.min_size, self.fx = width, min_size, fx


def _token(v: str | None) -> str:
    """`var(--main)` → `main`; `none`/None → `none`."""
    if v is None:
        return "none"
    v = v.strip()
    if v == "none":
        return "none"
    m = re.fullmatch(r"var\(--([a-z]+)\)", v)
    if not m:
        raise GlyphError(f"màu phải là token var(--…) hoặc none, gặp: {v!r}")
    return m.group(1)


def read_glyph(svg_path: Path) -> tuple[str, list[Layer]]:
    text = svg_path.read_text(encoding="utf-8")
    why = re.search(r"<!--(.*?)-->", text, re.S)
    if not why or len(" ".join(why.group(1).split())) < 24:
        raise GlyphError(f"{svg_path.name}: thiếu chú thích VÌ SAO hình này (≥ 24 ký tự) ở đầu tệp")
    root = ET.fromstring(text)
    vb = (root.get("viewBox") or "").split()
    if vb != ["0", "0", "24", "24"]:
        raise GlyphError(f"{svg_path.name}: viewBox phải là 0 0 24 24")
    layers: list[Layer] = []
    for el in root.iter(SVG_NS + "path"):
        d = el.get("d")
        if not d:
            raise GlyphError(f"{svg_path.name}: <path> thiếu d")
        layer = el.get("data-layer", "main")
        if layer not in LAYER_ALPHA:
            raise GlyphError(f"{svg_path.name}: data-layer phải là main|sub|bg, gặp {layer}")
        fill = _token(el.get("fill"))
        stroke = _token(el.get("stroke"))
        width = float(el.get("stroke-width", "0") or 0)
        min_size = int(el.get("data-min", "24"))
        fx = el.get("data-fx")  # grad | glow | None
        if fill == "none" and stroke == "none":
            raise GlyphError(f"{svg_path.name}: path không tô cũng không nét")
        if stroke != "none":
            want = STROKE_MAIN if layer == "main" else STROKE_SUB
            if abs(width - want) > 1e-6:
                raise GlyphError(f"{svg_path.name}: lớp {layer} phải dùng nét {want}, gặp {width}")
        elif width:
            raise GlyphError(f"{svg_path.name}: path chỉ tô mà có stroke-width")
        if fx and fill == "none":
            raise GlyphError(f"{svg_path.name}: data-fx chỉ dùng cho path có tô")
        if fx not in (None, "grad", "glow"):
            raise GlyphError(f"{svg_path.name}: data-fx phải là grad|glow")
        layers.append(Layer(d, layer, fill, stroke, width, min_size, fx))
    if not layers:
        raise GlyphError(f"{svg_path.name}: không có path nào")
    return " ".join(why.group(1).split()), layers


# ── sinh VectorDrawable ──────────────────────────────────────────────────────────────────────────────


def resolve_color(token: str, family: dict) -> str:
    if token == "ink":
        return "#FFFFFF"
    if token in family:
        return family[token].upper()
    raise GlyphError(f"token màu {token!r} không có trong họ màu {family.get('name')}")


def alpha_hex(a: float) -> str:
    return f"{round(a * 255):02X}"


def gradient_xml(kind: str, family: dict, bb, indent: str) -> str:
    """Chuyển sắc AAPT: linear trên-trái → dưới-phải (ánh sáng trên-trái) hoặc radial (quầng)."""
    x0, y0, x1, y1 = bb
    if kind == "grad":
        return (
            f'{indent}<aapt:attr name="android:fillColor">\n'
            f'{indent}    <gradient android:type="linear" android:startX="{fmt(x0)}" android:startY="{fmt(y0)}" '
            f'android:endX="{fmt(x1)}" android:endY="{fmt(y1)}">\n'
            f'{indent}        <item android:offset="0" android:color="{family["light"].upper()}" />\n'
            f'{indent}        <item android:offset="1" android:color="{family["deep"].upper()}" />\n'
            f"{indent}    </gradient>\n"
            f"{indent}</aapt:attr>\n"
        )
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    r = max(x1 - x0, y1 - y0) / 2
    return (
        f'{indent}<aapt:attr name="android:fillColor">\n'
        f'{indent}    <gradient android:type="radial" android:centerX="{fmt(cx)}" android:centerY="{fmt(cy)}" '
        f'android:gradientRadius="{fmt(r)}">\n'
        f'{indent}        <item android:offset="0" android:color="#{alpha_hex(0.9)}{family["light"].upper()[1:]}" />\n'
        f'{indent}        <item android:offset="1" android:color="#00{family["main"].upper()[1:]}" />\n'
        f"{indent}    </gradient>\n"
        f"{indent}</aapt:attr>\n"
    )


def measure(layers: list[Layer], size: int) -> tuple[float, float, float, float]:
    """Hộp mực của hình ở cỡ `size` (kể cả nửa nét)."""
    x0 = y0 = 1e9
    x1 = y1 = -1e9
    for L in layers:
        if L.min_size > size:
            continue
        bx0, by0, bx1, by1 = bbox(flatten(L.d))
        h = L.width / 2 if L.stroke != "none" else 0.0
        x0, y0 = min(x0, bx0 - h), min(y0, by0 - h)
        x1, y1 = max(x1, bx1 + h), max(y1, by1 + h)
    return x0, y0, x1, y1


def render(gid: str, why: str, layers: list[Layer], size: int, family: dict, entry: dict, src_name: str) -> str:
    used = [L for L in layers if L.min_size <= size]
    # Lớp NỀN (bg 0.16) luôn vẽ TRƯỚC — nó là ngữ cảnh nằm DƯỚI hình; để sau là phủ một màng 16 % lên chính lớp
    # main (đĩa nền của các icon large từng bị vậy). Thứ tự sub/main giữ như nguồn (chi tiết sub có thể nằm trên main).
    used = [L for L in used if L.layer == "bg"] + [L for L in used if L.layer != "bg"]
    if not any(L.layer == "main" for L in used):
        raise GlyphError(f"{gid}: không có lớp main ở cỡ {size}")
    if len({L.layer for L in used}) > 3:
        raise GlyphError(f"{gid}: quá 3 lớp")
    if len(used) > PATH_CAP[size]:
        raise GlyphError(f"{gid}: {len(used)} path > trần {PATH_CAP[size]} ở {size}dp")
    x0, y0, x1, y1 = measure(used, size)
    if x0 < OPTICAL_LO - OPTICAL_TOL or y0 < OPTICAL_LO - OPTICAL_TOL or x1 > OPTICAL_HI + OPTICAL_TOL or y1 > OPTICAL_HI + OPTICAL_TOL:
        raise GlyphError(f"{gid}: hộp mực {fmt(x0)}..{fmt(x1)} × {fmt(y0)}..{fmt(y1)} tràn ô quang học 2..22")
    if max(x1 - x0, y1 - y0) < MIN_SIDE:
        raise GlyphError(f"{gid}: cạnh lớn {fmt(max(x1-x0, y1-y0))} < {MIN_SIDE} — hình quá nhỏ trong ô")

    uses_aapt = any(L.fx for L in used)
    ns = 'xmlns:android="http://schemas.android.com/apk/res/android"'
    if uses_aapt:
        ns += '\n    xmlns:aapt="http://schemas.android.com/aapt"'
    head = [
        "<!--",
        f"  {why}",
        f"  ── SINH BỞI scripts/design/gen-icons.py từ design/glyph/{src_name} — ĐỪNG VÁ TAY (sửa SVG rồi sinh lại;",
        "  chế độ check so byte và đỏ nếu lệch). Ngữ pháp §4.2 spec kachi-visual-refresh: ≤ 3 lớp (main 1.0 · sub 0.38 ·",
        "  bg 0.16) · nét 1.8/1.2 tròn · khung 24 · ô quang học 20×20 · họ màu theo lĩnh vực trong design/icon-grammar.json.",
        f"  Lĩnh vực {entry['domain']} · họ màu {family['name']} · cỡ {size}dp.",
        "-->",
    ]
    body = [
        f"<vector {ns}",
        f'    android:width="{size}dp" android:height="{size}dp"',
        '    android:viewportWidth="24" android:viewportHeight="24">',
    ]
    for L in used:
        a = LAYER_ALPHA[L.layer]
        attrs = [f'android:pathData="{normalize_path(L.d)}"']
        if L.fx:
            pass  # màu tô = <aapt:attr> bên trong; KHÔNG kèm android:fillColor phẳng — [ĐO] AGP ResourceCompiler
            #       ("Cannot find attribute fillColor") từ chối path có CẢ hai; aapt2 thuần thì nuốt, Gradle thì đỏ.
        elif L.fill == "none":
            attrs.append('android:fillColor="#00000000"')
        else:
            attrs.append(f'android:fillColor="{resolve_color(L.fill, family)}"')
            if a < 1:
                attrs.append(f'android:fillAlpha="{fmt(a)}"')
        if L.stroke != "none":
            attrs.append(f'android:strokeColor="{resolve_color(L.stroke, family)}"')
            attrs.append(f'android:strokeWidth="{fmt(L.width)}"')
            if a < 1:
                attrs.append(f'android:strokeAlpha="{fmt(a)}"')
            attrs.append('android:strokeLineCap="round" android:strokeLineJoin="round"')
        if L.fx:
            body.append("    <path " + attrs[0])
            body.append("        " + " ".join(attrs[1:]) + ">")
            body.append(gradient_xml(L.fx, family, bbox(flatten(L.d)), "        ").rstrip("\n"))
            body.append("    </path>")
        else:
            body.append("    <path " + attrs[0])
            body.append("        " + " ".join(attrs[1:]) + " />")
    body.append("</vector>")
    return "\n".join(head + body) + "\n"


def build_all(grammar: dict) -> dict[str, str]:
    families = {k: dict(v, name=k) for k, v in grammar["families"].items()}
    out: dict[str, str] = {}
    errors: list[str] = []
    for gid, entry in sorted(grammar["icons"].items()):
        src = GLYPH_DIR / f"{gid}.svg"
        if not src.exists():
            errors.append(f"{gid}: thiếu {src.relative_to(ROOT)}")
            continue
        fam = families.get(entry.get("family") or grammar["domains"][entry["domain"]])
        if fam is None:
            errors.append(f"{gid}: họ màu không khai")
            continue
        try:
            why, layers = read_glyph(src)
            sizes = [24] + ([32, 48] if entry.get("large") else [])
            for s in sizes:
                out[f"ic_{gid}{VARIANT_SUFFIX[s]}.xml"] = render(gid, why, layers, s, fam, entry, src.name)
        except GlyphError as e:
            errors.append(str(e))
    if errors:
        raise GlyphError("\n".join(errors))
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT, help="thư mục ghi tệp sinh")
    ap.add_argument("--check", type=Path, metavar="DIR", help="không ghi; sinh lại rồi so byte với DIR")
    ap.add_argument("--only", nargs="*", help="chỉ sinh các id này (không có tiền tố ic_)")
    args = ap.parse_args(argv)

    grammar = json.loads(GRAMMAR.read_text(encoding="utf-8"))
    if args.only:
        grammar["icons"] = {k: v for k, v in grammar["icons"].items() if k in set(args.only)}
    try:
        files = build_all(grammar)
    except GlyphError as e:
        print("LỖI NGUỒN ICON:\n" + str(e), file=sys.stderr)
        return 2

    if args.check:
        drift = []
        for name, content in files.items():
            p = args.check / name
            if not p.exists():
                drift.append(f"{name}: thiếu trong {args.check}")
            elif p.read_text(encoding="utf-8") != content:
                drift.append(f"{name}: LỆCH byte so với bản sinh")
        if drift:
            print("\n".join(drift), file=sys.stderr)
            print(f"--check: {len(drift)} tệp lệch / {len(files)}", file=sys.stderr)
            return 1
        print(f"--check: {len(files)} tệp khớp byte với {args.check}")
        return 0

    args.out.mkdir(parents=True, exist_ok=True)
    for name, content in files.items():
        (args.out / name).write_text(content, encoding="utf-8")
    n24 = sum(1 for n in files if not n.endswith(("_l.xml", "_xl.xml")))
    print(f"sinh {len(files)} tệp ({n24} × 24dp, {len(files) - n24} biến thể 32/48) → {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
