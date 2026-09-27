# -*- coding: utf-8 -*-
# Tách THUẦN từ scripts/design/gen-car.py (882 dòng → trần 500, L6-debt 2026-09-27). Thân hàm giữ nguyên byte;
# `gen-car.py` chỉ còn là điểm vào CLI + re-export cho render-car.py. `--check` so byte đầu ra: khớp 41/41 sau khi tách.
"""Mặt 48dp có chuyển sắc (<aapt:attr>) + Kotlin sinh — giữ nguyên dù WP3-v5 không còn ghi ra đích (render-car.py vẫn đọc FACE_VD)."""
from __future__ import annotations

from .icons import FACE_VD
from .model import Face
from .pathdata import fmt
from .paths import FACES

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
