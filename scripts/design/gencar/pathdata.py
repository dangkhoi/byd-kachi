# -*- coding: utf-8 -*-
# Tách THUẦN từ scripts/design/gen-car.py (882 dòng → trần 500, L6-debt 2026-09-27). Thân hàm giữ nguyên byte;
# `gen-car.py` chỉ còn là điểm vào CLI + re-export cho render-car.py. `--check` so byte đầu ra: khớp 41/41 sau khi tách.
"""Số & path SVG: chuẩn hoá `d`, serialize tất định, làm phẳng cung/bezier, hộp bao."""
from __future__ import annotations

import math
import re

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
