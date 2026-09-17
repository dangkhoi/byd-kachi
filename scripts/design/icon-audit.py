#!/usr/bin/env python3
"""
icon-audit.py — KIỂM bộ icon đã sinh, KHÔNG cần gradle (spec kachi-visual-refresh §R2 · §R6 · AC6.2 · AC2.5).

1. Luật tệp sinh (đọc lại XML thật, không tin script sinh): khung 24 · trần path · thang alpha · hai nét · cap/join
   tròn · màu ∈ họ màu của lĩnh vực (+ #FFFFFF, trong suốt).
2. Phủ registry: mọi tên `ic-…` mà `:core` truyền vào `KachiTheme.iconRes` phải tra ra một tệp trong bộ sinh
   HOẶC nằm trong danh sách LOẠI có lý do (hình xe của B2, icon điều hướng, icon nút nổi Cast…).
3. Tương phản họ màu trên nền TỐI (bg · thẻ) và SÁNG — bảng WCAG sinh bằng máy, ghi `lightSafe` từng họ.
4. Bảng ảnh (contact sheet) 24/36/48 px trên nền tối và sáng bằng bộ vẽ PIL nội bộ (không cần rsvg/cairo).

Cách dùng:  python3 scripts/design/icon-audit.py [--drawable app/src/main/res/drawable] [--sheets] [--evidence docs/diagnostics/.../p2]
Exit 1 nếu luật 1 hoặc 2 đỏ.
"""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
from pathlib import Path

import importlib.util as _ilu

_spec = _ilu.spec_from_file_location("gen_icons", Path(__file__).resolve().parent / "gen-icons.py")
_gen = _ilu.module_from_spec(_spec)
_spec.loader.exec_module(_gen)
flatten = _gen.flatten

ROOT = Path(__file__).resolve().parents[2]
GRAMMAR = ROOT / "design" / "icon-grammar.json"
CORE = ROOT / "core" / "src" / "main" / "kotlin" / "com" / "byd" / "clusternav" / "launcher"
THEME = ROOT / "app" / "src" / "main" / "java" / "com" / "byd" / "clusternav" / "launcher" / "KachiTheme.kt"
RES = ROOT / "app" / "src" / "main" / "res" / "drawable"

# Tệp ic_* KHÔNG thuộc bộ glyph (mỗi dòng phải có lý do — cùng lệ orphanPending của IconStyleContractTest)
EXCLUDED: dict[str, str] = {
    "ic_car_": "hình xe theo vị trí — nguồn design/car/*.svg của agent B2 (spec §4.3), không phải glyph trừu tượng",
    "ic_launcher": "icon app hoa anh đào (R7, xong 1.68) — smallIcon thông báo, hợp đồng riêng",
    "ic_turn_left": "mũi tên rẽ của màn dẫn đường (ClusterNavActivity) — bảng NEW_ICON/CAN RE 2026-08-14, không thuộc launcher",
    "ic_turn_right": "mũi tên rẽ của màn dẫn đường — như trên",
    "ic_turn_straight": "mũi tên đi thẳng của màn dẫn đường — như trên",
    "ic_turn_right_g": "mũi tên rẽ cockpit cũ (turn_tile_bg) — cùng họ dẫn đường",
    "ic_bubble_nav": "nút nổi Cast vẽ trực tiếp (BubbleRenderer), xanh thương hiệu = danh tính Cast; bề mặt đã chạy trên xe (CLAUDE.md §6)",
    "ic_menu_config": "bảng con nút nổi Cast — không có bước tint, giữ nguyên",
    "ic_menu_left": "bảng con nút nổi Cast — như trên",
    "ic_menu_right": "bảng con nút nổi Cast — như trên",
    "ic_check_selected": "dấu tích xanh lá = trạng thái đang chọn (màu là nghĩa)",
    "ic_chevron_down": "nằm trong layer-list spinner_bg, không có View để tint",
    "ic_corner_cut": "mặt nạ che góc, không phải icon",
}


def is_excluded(name: str) -> str | None:
    for k, why in EXCLUDED.items():
        if name == k or (k.endswith("_") and name.startswith(k)):
            return why
    return None


# ── 1 · luật tệp sinh ──────────────────────────────────────────────────────────────────────────────

_PATH = re.compile(r"<path\b(.*?)(?:/>|>(.*?)</path>)", re.S)


def attr(el: str, name: str) -> str | None:
    m = re.search(rf'android:{name}="([^"]*)"', el)
    return m.group(1) if m else None


def check_rules(drawable: Path, grammar: dict) -> list[str]:
    bad: list[str] = []
    fams = grammar["families"]
    doms = grammar["domains"]
    for gid, entry in grammar["icons"].items():
        fam = fams[entry.get("family") or doms[entry["domain"]]]
        allowed = {fam["main"].upper(), fam["light"].upper(), fam["deep"].upper(), "#FFFFFF", "#00000000"}
        for suffix, size in (("", 24), ("_l", 32), ("_xl", 48)):
            if suffix and not entry.get("large"):
                continue
            p = drawable / f"ic_{gid}{suffix}.xml"
            if not p.exists():
                bad.append(f"{p.name}: thiếu")
                continue
            xml = p.read_text(encoding="utf-8")
            if attr(xml, "viewportWidth") != "24" or attr(xml, "viewportHeight") != "24":
                bad.append(f"{p.name}: khung không phải 24")
            if attr(xml, "width") != f"{size}dp":
                bad.append(f"{p.name}: width phải {size}dp")
            paths = _PATH.findall(xml)
            cap = grammar["pathCap"][str(size)]
            if len(paths) > cap:
                bad.append(f"{p.name}: {len(paths)} path > {cap}")
            alphas = set()
            for head, inner in paths:
                el = head
                fa, sa = attr(el, "fillAlpha"), attr(el, "strokeAlpha")
                for a in (fa, sa):
                    if a is not None:
                        alphas.add(a)
                        if a not in ("0.38", "0.16"):
                            bad.append(f"{p.name}: alpha {a} ngoài thang 0.38/0.16")
                sc = attr(el, "strokeColor")
                if sc and sc != "#00000000":
                    if attr(el, "strokeWidth") not in ("1.8", "1.2"):
                        bad.append(f"{p.name}: nét {attr(el, 'strokeWidth')} ngoài {{1.8, 1.2}}")
                    if attr(el, "strokeLineCap") != "round" or attr(el, "strokeLineJoin") != "round":
                        bad.append(f"{p.name}: cap/join không tròn")
                    if sc.upper() not in allowed:
                        bad.append(f"{p.name}: strokeColor {sc} ngoài họ {fam['name'] if 'name' in fam else ''}")
                else:
                    if any(attr(el, a) for a in ("strokeWidth", "strokeLineCap", "strokeLineJoin")):
                        bad.append(f"{p.name}: đường chỉ tô mà mang thuộc tính nét")
                fc = attr(el, "fillColor")
                if fc and fc.upper() not in allowed:
                    bad.append(f"{p.name}: fillColor {fc} ngoài họ")
                for c in re.findall(r'android:color="(#[0-9A-Fa-f]{6,8})"', inner or ""):
                    rgb = "#" + c[-6:].upper()
                    if rgb not in allowed:
                        bad.append(f"{p.name}: màu gradient {c} ngoài họ")
    return bad


# ── 2 · phủ registry ──────────────────────────────────────────────────────────────────────────────


def check_registry(drawable: Path) -> tuple[list[str], dict]:
    theme = THEME.read_text(encoding="utf-8")
    mapped = dict(re.findall(r'"(ic-[a-z0-9-]+)"\s*->\s*R\.drawable\.(\w+)', theme))
    used: dict[str, set[str]] = {}
    for f in CORE.glob("*.kt"):
        code = re.sub(r"/\*.*?\*/", " ", f.read_text(encoding="utf-8"), flags=re.S)
        code = "\n".join(l.split("//")[0] for l in code.splitlines())
        for m in re.findall(r'"(ic-[a-z0-9-]+)"', code):
            used.setdefault(m, set()).add(f.name)
    generated = {p.stem for p in drawable.glob("ic_*.xml")}
    existing = {p.stem for p in RES.glob("ic_*.xml")}
    bad: list[str] = []
    cover = {"generated": 0, "excluded": 0, "unmapped": [], "missing": []}
    for name in sorted(used):
        d = mapped.get(name)
        if not d:
            bad.append(f"{name}: không có dòng KachiTheme.iconRes (dùng ở {sorted(used[name])})")
            cover["unmapped"].append(name)
            continue
        if d in generated:
            cover["generated"] += 1
        elif is_excluded(d):
            cover["excluded"] += 1
        elif d in existing:
            bad.append(f"{name} → {d}: có tệp cũ nhưng CHƯA vẽ lại và không nằm trong danh sách loại")
            cover["missing"].append(d)
        else:
            bad.append(f"{name} → {d}: không có tệp")
            cover["missing"].append(d)
    # tệp cũ trong res chưa được phủ (không sinh, không loại)
    stale = sorted(n for n in existing if n not in generated and not is_excluded(n))
    if stale:
        bad.append("tệp ic_* trong res chưa vẽ lại và không nằm trong EXCLUDED: " + ", ".join(stale))
    cover["stale"] = stale
    cover["mapped_total"] = len(mapped)
    cover["used_total"] = len(used)
    return bad, cover


# ── 3 · tương phản ────────────────────────────────────────────────────────────────────────────────


def lum(hexc: str) -> float:
    r, g, b = (int(hexc[i : i + 2], 16) / 255 for i in (1, 3, 5))

    def ch(c):
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def contrast(a: str, b: str) -> float:
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


BACKS = {"bg tối #0a0d13": "#0a0d13", "thẻ tối #1d232e": "#1d232e", "bg sáng #eef1f6": "#eef1f6"}


def contrast_table(grammar: dict) -> tuple[str, dict]:
    rows = ["| họ | vai | " + " | ".join(BACKS) + " | lightSafe (≥3:1 cả 3 vai trên nền sáng) |", "|---|---|" + "---|" * len(BACKS) + "---|"]
    safe: dict[str, bool] = {}
    for name, fam in grammar["families"].items():
        ok_light = True
        for role in ("main", "light", "deep"):
            cs = [contrast(fam[role], b) for b in BACKS.values()]
            if cs[2] < 3.0:
                ok_light = False
            rows.append(f"| {name} | {role} `{fam[role]}` | " + " | ".join(f"{c:.2f}" for c in cs) + f" | {'' if role != 'deep' else ('✅' if ok_light else '❌ → chỗ dùng phải tint INK ở chủ đề SÁNG')} |")
        safe[name] = ok_light
    return "\n".join(rows), safe


# ── 4 · bảng ảnh (PIL) ────────────────────────────────────────────────────────────────────────────


def render_sheet(drawable: Path, out: Path, px: int, bg: str, names: list[str]) -> None:
    from PIL import Image, ImageDraw, ImageFont

    SS = 8
    cols = 12
    cell = px + 14
    rows = math.ceil(len(names) / cols)
    sheet = Image.new("RGB", (cols * cell + 8, rows * (cell + 10) + 8), bg)
    font = ImageFont.load_default()
    dr = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        xml = (drawable / f"{name}.xml").read_text(encoding="utf-8")
        icon = render_icon(xml, px, SS)
        x = 8 + (i % cols) * cell
        y = 8 + (i // cols) * (cell + 10)
        sheet.paste(icon, (x + 7, y + 2), icon)
        label = name.removeprefix("ic_")[:11]
        dr.text((x, y + px + 4), label, fill="#888888" if bg != "#eef1f6" else "#666666", font=font)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)


def _parse_color(c: str) -> tuple[int, int, int, int]:
    if not c.startswith("#"):
        # tham chiếu tài nguyên (`@color/…`, chỉ ở tệp LOẠI như ic_chevron_down) — vẽ xám để contact sheet không sập
        return 136, 136, 136, 255
    c = c.lstrip("#")
    if len(c) == 6:
        return int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255
    return int(c[2:4], 16), int(c[4:6], 16), int(c[6:8], 16), int(c[0:2], 16)


def render_icon(xml: str, px: int, SS: int):
    from PIL import Image, ImageDraw

    W = px * SS
    k = W / 24
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    for head, inner in _PATH.findall(xml):
        d = attr(head, "pathData")
        subs = flatten(d, n=10)
        fill = attr(head, "fillColor") or "#00000000"
        fa = float(attr(head, "fillAlpha") or 1)
        sc = attr(head, "strokeColor")
        sa = float(attr(head, "strokeAlpha") or 1)
        sw = float(attr(head, "strokeWidth") or 0)
        grad = re.findall(r'android:color="(#[0-9A-Fa-f]{6,8})"', inner or "")
        # fill
        if grad or fill != "#00000000":
            mask = Image.new("L", (W, W), 0)
            md = ImageDraw.Draw(mask)
            for s in subs:
                if len(s) >= 3:
                    md.polygon([(x * k, y * k) for x, y in s], fill=255)
            mask = mask.resize((px, px), Image.LANCZOS)
            if grad:
                c0, c1 = _parse_color(grad[0]), _parse_color(grad[-1])
                if 'type="radial"' in (inner or ""):
                    g = Image.radial_gradient("L").resize((px, px))
                    a0, a1 = c0[3], c1[3]
                    col = Image.new("RGBA", (px, px), c0[:3] + (255,))
                    alpha = Image.eval(g, lambda v: int(a0 + (a1 - a0) * v / 255))
                    mask = Image.composite(alpha, Image.new("L", (px, px), 0), mask)
                    col.putalpha(mask)
                    layer = col
                else:
                    g = Image.linear_gradient("L").resize((px * 2, px * 2)).rotate(45, resample=Image.BILINEAR)
                    g = g.crop((px // 2, px // 2, px // 2 + px, px // 2 + px))
                    a_img = Image.new("RGBA", (px, px), c0[:3] + (255,))
                    b_img = Image.new("RGBA", (px, px), c1[:3] + (255,))
                    layer = Image.composite(b_img, a_img, g)
                    layer.putalpha(mask)
            else:
                r, g_, b, a = _parse_color(fill)
                layer = Image.new("RGBA", (px, px), (r, g_, b, 255))
                layer.putalpha(Image.eval(mask, lambda v: int(v * a / 255 * fa)))
            if fa < 1 and grad:
                layer.putalpha(Image.eval(layer.getchannel("A"), lambda v: int(v * fa)))
            canvas = Image.alpha_composite(canvas, layer)
        # stroke
        if sc and sc != "#00000000" and sw > 0:
            mask = Image.new("L", (W, W), 0)
            md = ImageDraw.Draw(mask)
            w = max(1, int(round(sw * k)))
            r = w / 2
            for s in subs:
                pts = [(x * k, y * k) for x, y in s]
                closed = d.strip().upper().endswith("Z") and len(pts) > 2 and False
                if len(pts) >= 2:
                    md.line(pts, fill=255, width=w, joint="curve")
                for (x, y) in (pts[0], pts[-1]):
                    md.ellipse((x - r, y - r, x + r, y + r), fill=255)
            mask = mask.resize((px, px), Image.LANCZOS)
            rr, gg, bb, aa = _parse_color(sc)
            layer = Image.new("RGBA", (px, px), (rr, gg, bb, 255))
            layer.putalpha(Image.eval(mask, lambda v: int(v * aa / 255 * sa)))
            canvas = Image.alpha_composite(canvas, layer)
    return canvas


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--drawable", type=Path, default=ROOT / "app" / "src" / "main" / "res" / "drawable")
    ap.add_argument("--sheets", action="store_true", help="vẽ contact sheet ra docs/diagnostics/visual-refresh-2026-09-16/p2/")
    ap.add_argument("--evidence", type=Path, default=ROOT / "docs" / "diagnostics" / "visual-refresh-2026-09-16" / "p2",
                    help="thư mục ghi bảng tương phản + contact sheet (bằng chứng T11)")
    args = ap.parse_args(argv)
    grammar = json.loads(GRAMMAR.read_text(encoding="utf-8"))
    for k, v in grammar["families"].items():
        v["name"] = k

    rules = check_rules(args.drawable, grammar)
    reg, cover = check_registry(args.drawable)
    table, safe = contrast_table(grammar)

    print("== 1 · luật tệp sinh:", "OK" if not rules else f"{len(rules)} lỗi")
    for r in rules[:40]:
        print("   ", r)
    print(f"== 2 · phủ registry: {cover['used_total']} tên dùng ở :core · {cover['mapped_total']} dòng bảng tra · "
          f"{cover['generated']} tra ra tệp SINH · {cover['excluded']} tra ra tệp LOẠI có lý do · stale {len(cover['stale'])}")
    for r in reg:
        print("   ", r)
    print("== 3 · tương phản họ màu\n" + table)
    args.evidence.mkdir(parents=True, exist_ok=True)
    (args.evidence / "contrast-families.md").write_text(
        "# Tương phản họ màu icon (sinh bởi scripts/design/icon-audit.py)\n\n" + table + "\n", encoding="utf-8")
    if args.sheets:
        names = sorted(p.stem for p in args.drawable.glob("ic_*.xml") if not p.stem.endswith(("_l", "_xl")))
        for px in (24, 36, 48):
            for tag, bg in (("toi", "#0a0d13"), ("the", "#1d232e"), ("sang", "#eef1f6")):
                render_sheet(args.drawable, args.evidence / f"contact-sheet-{px}px-{tag}.png", px, bg, names)
        print(f"== 4 · contact sheet: {args.evidence}/contact-sheet-{{24,36,48}}px-{{toi,the,sang}}.png")
    return 1 if (rules or reg) else 0


if __name__ == "__main__":
    sys.exit(main())
