# contrast.py <png> <uiautomator.xml> [y_min y_max] : WCAG ratio of every text node vs its LOCAL background.
# bg  = median colour of a 3 px ring just OUTSIDE the node bounds (what the glyphs actually sit on — card, veil, photo);
# ink = among the pixels INSIDE the bounds that differ from bg (ratio >= 1.3 — the glyph strokes incl. anti-aliasing), the
#       80th percentile by contrast = stroke core (a box is often much wider than its text, so a box percentile would
#       measure background).
# Prints the worst nodes and the minimum; text nodes only (icons are graphics, measured separately).
import re, sys
from PIL import Image

def lum(c):
    def ch(v):
        v /= 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = c[:3]
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)

def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)

im = Image.open(sys.argv[1]).convert("RGB"); px = im.load(); W, H = im.size
x = open(sys.argv[2], encoding="utf-8").read()
ymin, ymax = (int(sys.argv[3]), int(sys.argv[4])) if len(sys.argv) > 4 else (60, 930)
rows = []
for m in re.finditer(r'text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x):
    t = m.group(1).strip()
    a, b, c, d = map(int, m.groups()[1:])
    if not t or c - a < 4 or d - b < 4 or b < ymin or d > ymax:
        continue
    ring = []
    for i in range(max(0, a - 3), min(W, c + 3)):
        for j in (max(0, b - 3), min(H - 1, d + 2)):
            ring.append(px[i, j])
    for j in range(max(0, b - 3), min(H, d + 3)):
        for i in (max(0, a - 3), min(W - 1, c + 2)):
            ring.append(px[i, j])
    ring.sort(key=lum)
    bg = ring[len(ring) // 2]
    rs = sorted(r for r in (ratio(px[i, j], bg) for i in range(a, c) for j in range(b, d)) if r >= 1.3)
    if len(rs) < 6:
        continue
    rows.append((rs[int(len(rs) * 0.8)], t, (a, b, c, d), bg))
rows.sort()
for r, t, box, bg in rows[:8]:
    print(f"{r:5.2f}  {t[:28]!r:32s} {box} bg={bg}")
print(f"MIN {rows[0][0]:.2f} over {len(rows)} text nodes" if rows else "no nodes")
