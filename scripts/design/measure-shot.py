#!/usr/bin/env python3
"""Đo ảnh chụp màn hình launcher: độ chói TB + dò VẠCH 1px (cột/hàng sáng cô lập).

Sinh cho UX-OVERHAUL WP1 (2026-09-20). Phép dò vạch dùng ĐÚNG tiêu chí của WP1 §6.2:
một cột/hàng là "vạch 1px" nếu độ chói TB của nó cao hơn CẢ HAI hàng/cột kề một
biên độ >= DELTA và đoạn sáng đó dài >= MIN_RUN pixel (để không đếm chữ/icon).
"""
import struct
import sys
import zlib


def read_png(path):
    d = open(path, 'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n', 'không phải PNG'
    i, idat, w, h, ct = 8, b'', None, None, None
    while i < len(d):
        ln = struct.unpack('>I', d[i:i + 4])[0]
        typ = d[i + 4:i + 8]
        data = d[i + 8:i + 8 + ln]
        i += 12 + ln
        if typ == b'IHDR':
            w, h, _bd, ct = struct.unpack('>IIBB', data[:10])
        elif typ == b'IDAT':
            idat += data
        elif typ == b'IEND':
            break
    raw = zlib.decompress(idat)
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    stride = w * ch
    out, prev, p = bytearray(), bytearray(stride), 0
    for _y in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for x in range(ch, stride):
                line[x] = (line[x] + line[x - ch]) & 255
        elif f == 2:
            for x in range(stride):
                line[x] = (line[x] + prev[x]) & 255
        elif f == 3:
            for x in range(stride):
                a = line[x - ch] if x >= ch else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 255
        elif f == 4:
            for x in range(stride):
                a = line[x - ch] if x >= ch else 0
                b = prev[x]
                c = prev[x - ch] if x >= ch else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 255
        out += line
        prev = line
    return w, h, ch, bytes(out)


def lum_grid(w, h, ch, px):
    return [[0.2126 * px[(y * w + x) * ch] + 0.7152 * px[(y * w + x) * ch + 1]
             + 0.0722 * px[(y * w + x) * ch + 2] for x in range(w)] for y in range(h)]


DELTA = 12.0      # biên độ sáng hơn hai bên mới tính là vạch
MIN_RUN = 200     # đoạn sáng liên tục tối thiểu (px) — chữ/icon ngắn hơn nhiều


def rows_1px(L, w, h, y0, y1):
    """Hàng y là vạch ngang 1px nếu sáng hơn y-1 và y+1 trên một đoạn dài."""
    hits = []
    for y in range(max(y0, 1), min(y1, h - 1)):
        run = best = 0
        for x in range(w):
            if L[y][x] - L[y - 1][x] >= DELTA and L[y][x] - L[y + 1][x] >= DELTA:
                run += 1
                best = max(best, run)
            else:
                run = 0
        if best >= MIN_RUN:
            hits.append((y, best))
    return hits


def cols_1px(L, w, h, y0, y1):
    """Cột x là vạch dọc 1px nếu sáng hơn x-1 và x+1 trên một đoạn dài trong dải [y0,y1)."""
    hits = []
    span = min(y1, h) - max(y0, 0)
    need = min(MIN_RUN, max(8, span // 2))
    for x in range(1, w - 1):
        run = best = 0
        for y in range(max(y0, 0), min(y1, h)):
            if L[y][x] - L[y][x - 1] >= DELTA and L[y][x] - L[y][x + 1] >= DELTA:
                run += 1
                best = max(best, run)
            else:
                run = 0
        if best >= need:
            hits.append((x, best))
    return hits


def main(path, bands):
    w, h, ch, px = read_png(path)
    L = lum_grid(w, h, ch, px)
    mean = sum(sum(r) for r in L) / (w * h)
    print(f'{path}')
    print(f'  {w}x{h}  meanL = {mean:.1f}  ->  {"TỐI" if mean < 60 else ("SÁNG" if mean > 200 else "GIỮA")}')
    for name, y0, y1 in bands:
        rh = rows_1px(L, w, h, y0, y1)
        cv = cols_1px(L, w, h, y0, y1)
        print(f'  [{name:10s} y {y0:4d}..{y1:4d}]  vạch NGANG 1px = {len(rh)}'
              f'{" " + str(rh[:6]) if rh else ""}   vạch DỌC 1px = {len(cv)}'
              f'{" " + str([c[0] for c in cv[:8]]) if cv else ""}')
    return mean


if __name__ == '__main__':
    # Dải đo bám bố cục màn chính 1920x1080 @ density 240 (xem WP1 §6.2).
    BANDS = [('TOP-STRIP', 20, 110), ('WORKSPACE', 112, 878), ('DOCK', 880, 1060)]
    for p in sys.argv[1:]:
        main(p, BANDS)
        print()
