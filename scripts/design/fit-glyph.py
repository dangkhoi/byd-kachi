#!/usr/bin/env python3
"""
fit-glyph.py — ÉP một glyph SVG (design/glyph/<id>.svg, lệnh path TUYỆT ĐỐI) vào ô quang học 2..22 kể cả nửa nét, và
nở lên nếu cạnh lớn < 16 (cùng hai luật của IconGeometryContractTest mà gen-icons.py cũng kiểm lúc sinh).

Phép biến hình = MỘT phép co/giãn đều + dịch quanh tâm hộp mực (không kéo lệch), áp cho MỌI path trong tệp — hình giữ
nguyên tỉ lệ, chỉ đổi cỡ/vị trí. Cung `A` đổi rx/ry theo cùng hệ số, cờ giữ nguyên. Nét không đổi (nét là đơn vị dp).

    python3 scripts/design/fit-glyph.py brake mode …      # sửa tại chỗ rồi chạy lại gen-icons.py
"""
from __future__ import annotations

import importlib.util
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("gen_icons", ROOT / "scripts" / "design" / "gen-icons.py")
gi = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gi)

LO, HI, MIN_SIDE = 2.05, 21.95, 16.2
_NUM = r"-?(?:\d+\.?\d*|\.\d+)"


def fmt(v: float) -> str:
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def transform_d(d: str, k: float, cx: float, cy: float, tx: float, ty: float) -> str:
    toks = re.findall(r"[A-Za-z]|" + _NUM, d)
    out, i, cmd = [], 0, None
    while i < len(toks):
        t = toks[i]
        if t.isalpha():
            if t.islower():
                raise SystemExit(f"lệnh tương đối '{t}' — fit-glyph chỉ hỗ trợ path tuyệt đối")
            cmd = t; out.append(t); i += 1; continue
        if cmd == "A":
            rx, ry, rot, fa, fs, x, y = toks[i:i + 7]
            out.append(f"{fmt(float(rx) * k)},{fmt(float(ry) * k)} {rot} {fa} {fs} {fmt((float(x) - cx) * k + cx + tx)},{fmt((float(y) - cy) * k + cy + ty)}")
            i += 7; continue
        if cmd in ("H",):
            out.append(fmt((float(t) - cx) * k + cx + tx)); i += 1; continue
        if cmd in ("V",):
            out.append(fmt((float(t) - cy) * k + cy + ty)); i += 1; continue
        x, y = toks[i], toks[i + 1]
        out.append(f"{fmt((float(x) - cx) * k + cx + tx)},{fmt((float(y) - cy) * k + cy + ty)}")
        i += 2
    return " ".join(out)


def fit(gid: str) -> None:
    p = ROOT / "design" / "glyph" / f"{gid}.svg"
    _, layers = gi.read_glyph(p)
    x0, y0, x1, y1 = gi.measure(layers, 24)
    w, h = x1 - x0, y1 - y0
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    room = HI - LO
    k = min(room / w, room / h, 1.0)                     # co nếu tràn
    if max(w, h) * k < MIN_SIDE:
        k = MIN_SIDE / max(w, h)                         # nở nếu quá nhỏ (vẫn ≤ room vì MIN_SIDE < room)
    # sau khi co/giãn quanh tâm, dịch để hộp nằm trong [LO, HI]
    nx0, nx1 = cx - w * k / 2, cx + w * k / 2
    ny0, ny1 = cy - h * k / 2, cy + h * k / 2
    tx = (LO - nx0) if nx0 < LO else (HI - nx1) if nx1 > HI else 0.0
    ty = (LO - ny0) if ny0 < LO else (HI - ny1) if ny1 > HI else 0.0
    s = p.read_text(encoding="utf-8")
    s = re.sub(r'\bd="([^"]+)"', lambda m: 'd="%s"' % transform_d(m.group(1), k, cx, cy, tx, ty), s)
    p.write_text(s, encoding="utf-8")
    _, layers = gi.read_glyph(p)
    print(f"{gid}: k={k:.3f} dịch=({tx:+.2f},{ty:+.2f}) → hộp mực {tuple(round(v, 2) for v in gi.measure(layers, 24))}")


if __name__ == "__main__":
    for g in sys.argv[1:]:
        fit(g)
