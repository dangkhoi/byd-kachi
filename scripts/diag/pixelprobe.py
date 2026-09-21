#!/usr/bin/env python3
"""Đo ĐIỂM ẢNH của ảnh chụp máy ảo — KHÔNG phụ thuộc Pillow (máy owner không cài).

Vì sao đo thay vì nhìn: phiên này không có công cụ sinh sub-agent, mà luật
`.kiro/steering/image-reading-subagent.md` cấm đọc ảnh lớn trực tiếp trong phiên chính.
Nên bằng chứng hình ảnh của lượt WP2 là **phép đo pixel** (cùng cách WP1 đã dùng), và
owner nhìn ảnh là bước xác nhận cuối.

Dùng: pixelprobe.py <png> [--box x1,y1,x2,y2 nhãn] ...
"""
import struct
import sys
import zlib


def read_png(path):
    p = open(path, "rb").read()
    assert p[:8] == b"\x89PNG\r\n\x1a\n", "không phải PNG"
    i, w, h, ct, idat = 8, None, None, None, b""
    while i < len(p):
        ln = struct.unpack(">I", p[i:i + 4])[0]
        typ = p[i + 4:i + 8]
        data = p[i + 8:i + 8 + ln]
        i += 12 + ln
        if typ == b"IHDR":
            w, h, _bd, ct = struct.unpack(">IIBB", data[:10])
        elif typ == b"IDAT":
            idat += data
    raw = zlib.decompress(idat)
    ch = {0: 1, 2: 3, 4: 2, 6: 4}[ct]
    stride = w * ch
    out = bytearray()
    prev = bytearray(stride)
    o = 0
    for _y in range(h):
        f = raw[o]
        o += 1
        line = bytearray(raw[o:o + stride])
        o += stride
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


def lum(px, ch, stride, x, y):
    o = y * stride + x * ch
    return 0.2126 * px[o] + 0.7152 * px[o + 1] + 0.0722 * px[o + 2]


def rgb(px, ch, stride, x, y):
    o = y * stride + x * ch
    return px[o], px[o + 1], px[o + 2]


def box_stats(path, x1, y1, x2, y2):
    w, h, ch, px = read_png(path)
    stride = w * ch
    vals, cols = [], []
    for y in range(max(0, y1), min(h, y2)):
        for x in range(max(0, x1), min(w, x2)):
            vals.append(lum(px, ch, stride, x, y))
            cols.append(rgb(px, ch, stride, x, y))
    n = len(vals)
    return {
        "n": n,
        "mean": sum(vals) / n,
        "max": max(vals),
        "p90": sorted(vals)[int(n * 0.9)],
        "bright": sum(1 for v in vals if v > 120),
        "meanRGB": tuple(round(sum(c[i] for c in cols) / n, 1) for i in range(3)),
    }


def row_profile(path, x1, x2, y1, y2):
    """Độ chói trung bình theo TỪNG hàng — để tìm dải vạch mức mà không đoán toạ độ."""
    w, h, ch, px = read_png(path)
    stride = w * ch
    out = []
    for y in range(max(0, y1), min(h, y2)):
        vals = [lum(px, ch, stride, x, y) for x in range(max(0, x1), min(w, x2))]
        out.append((y, sum(vals) / len(vals), max(vals)))
    return out


if __name__ == "__main__":
    print(__doc__)
