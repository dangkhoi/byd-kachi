#!/usr/bin/env python3
"""
WP-C · Sinh ảnh xe DEFAULT (bundled) cho các widget tổng hợp có hình xe.

Nguồn: images/seal-3.png (AI-gen, owner xác nhận KHÔNG có logo/nhãn hiệu — clean) — xe top-down DỌC trên nền
"checkerboard giả trong suốt" (alpha thật = 255 hết). Việc:
  1. Flood-fill từ 4 mép để xoá NỀN (checkerboard + đường viền antialias) → alpha=0, GIỮ được các
     điểm sáng bên trong xe (không dùng global color-key nên không thủng lỗ nội thất).
  2. Autocrop về đúng bbox xe (kể cả 2 gương).
  3. Downscale về bề rộng hợp lý cho widget (giữ alpha, không nén mất kênh trong suốt).
  4. Xuất PNG-32 vào app/src/main/assets/car/default-car.png.

Predicate nền (theo [ĐO] sub-agent): sat = max(R,G,B)-min(R,G,B) <= 14 AND lum >= 176.
Flood-fill 4-neighbour từ mọi pixel biên; chỉ nền liên thông với biên bị xoá ⇒ giữ nền-kẹt-hợp-lệ
giữa gương và mui (đó cũng là nền, nên xoá là đúng — flood tới được).
"""
import sys
from collections import deque
from PIL import Image

SRC = "images/seal-3.png"
DST = "app/src/main/assets/car/default-car.png"
TARGET_W = 640          # bề rộng đích (ảnh đã DỌC sẵn — nose UP, không cần xoay)
SAT_MAX = 16            # nới nhẹ so với 14 để bắt hết seam antialias
LUM_MIN = 170           # ô checker tối của seal-3 ≈184 ⇒ ngưỡng phải ≤184 mới bắt được nền
PAD = 12               # đệm quanh bbox sau crop (px, ở độ phân giải gốc)


def is_bg(px):
    r, g, b, a = px
    if a == 0:
        return True
    mx, mn = max(r, g, b), min(r, g, b)
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    return (mx - mn) <= SAT_MAX and lum >= LUM_MIN


def main():
    im = Image.open(SRC).convert("RGBA")
    w, h = im.size
    px = im.load()

    # ── 1. Flood-fill nền từ biên ────────────────────────────────────────────
    bg = bytearray(w * h)  # 1 = nền (sẽ xoá)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            i = y * w + x
            if not bg[i] and is_bg(px[x, y]):
                bg[i] = 1
                q.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            i = y * w + x
            if not bg[i] and is_bg(px[x, y]):
                bg[i] = 1
                q.append((x, y))
    while q:
        x, y = q.popleft()
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h:
                i = ny * w + nx
                if not bg[i] and is_bg(px[nx, ny]):
                    bg[i] = 1
                    q.append((nx, ny))

    cleared = 0
    for y in range(h):
        base = y * w
        for x in range(w):
            if bg[base + x]:
                r, g, b, _ = px[x, y]
                px[x, y] = (r, g, b, 0)
                cleared += 1

    # ── 1b. Despeckle: xoá các CỤM alpha nhỏ tách rời (haze sót không liền border + đốm lẻ). Không có bước này thì
    #     một đốm haze cách xa xe làm autocrop nới bbox lệch (xe lệch tâm). Quét cụm 4-neighbour trên pixel còn đục,
    #     cụm < MIN_BLOB px ⇒ xoá alpha. Xe là cụm lớn nhất nên không bị đụng.
    MIN_BLOB = 400
    seen = bytearray(w * h)
    for sy in range(h):
        for sx in range(w):
            i0 = sy * w + sx
            if seen[i0] or px[sx, sy][3] == 0:
                continue
            comp = []
            stack = [(sx, sy)]
            seen[i0] = 1
            while stack:
                cx, cy = stack.pop()
                comp.append((cx, cy))
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = cx + dx, cy + dy
                    if 0 <= nx < w and 0 <= ny < h:
                        j = ny * w + nx
                        if not seen[j] and px[nx, ny][3] != 0:
                            seen[j] = 1
                            stack.append((nx, ny))
            if len(comp) < MIN_BLOB:
                for (cx, cy) in comp:
                    r, g, b, _ = px[cx, cy]
                    px[cx, cy] = (r, g, b, 0)

    # ── 2. Autocrop về bbox nội dung (alpha>0) ───────────────────────────────
    bbox = im.getbbox()
    if bbox is None:
        print("ERROR: ảnh rỗng sau khi xoá nền", file=sys.stderr)
        sys.exit(1)
    x1, y1, x2, y2 = bbox
    x1 = max(0, x1 - PAD); y1 = max(0, y1 - PAD)
    x2 = min(w, x2 + PAD); y2 = min(h, y2 + PAD)
    im = im.crop((x1, y1, x2, y2))

    # ── 3. Downscale giữ tỉ lệ + alpha (seal-3 đã DỌC sẵn, nose UP — KHÔNG xoay) ─────────────────
    cw, ch = im.size
    if cw > TARGET_W:
        nh = round(ch * TARGET_W / cw)
        im = im.resize((TARGET_W, nh), Image.LANCZOS)

    # ── 4. Thêm lề TRONG SUỐT để gương/nội dung không chạm mép canvas ────────
    #     (crop PAD ở độ phân giải gốc bị co lại sau resize ⇒ gương sát mép. Thêm ~3% mỗi cạnh
    #     bằng canvas trong suốt lớn hơn rồi dán ảnh vào giữa.)
    iw, ih = im.size
    mx = max(round(iw * 0.03), 8)
    my = max(round(ih * 0.03), 8)
    canvas = Image.new("RGBA", (iw + 2 * mx, ih + 2 * my), (0, 0, 0, 0))
    canvas.alpha_composite(im, (mx, my))
    im = canvas

    # ── 5. Xuất ──────────────────────────────────────────────────────────────
    import os
    os.makedirs(os.path.dirname(DST), exist_ok=True)
    im.save(DST, "PNG", optimize=True)
    print(f"nguồn {w}x{h} → crop ({x1},{y1},{x2},{y2}) → xuất {im.size[0]}x{im.size[1]}")
    print(f"nền xoá: {cleared} px ({100*cleared/(w*h):.1f}%)")
    print(f"→ {DST}")


if __name__ == "__main__":
    main()
