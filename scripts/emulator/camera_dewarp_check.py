#!/usr/bin/env python3
"""Do BANG SO phep nan fisheye tu hai anh PNG chup tren may ao (R8-B, 2.74).

Cau hoi duy nhat: *"duong thang cua the gioi co tro lai THANG sau khi nan khong"* — va tra loi bang px,
khong bang mat.

## Vi sao do CHAN TROI, khong do luoi
`CameraDewarpTestPattern` ve mot **chan troi** (mau HORIZON = #FFC24D) la mot duong thang cua the gioi **LECH tam
quang** (`horizonY = 0.35`). Duong thang qua tam quang thi van thang du bi fisheye (no la mot ban kinh), nen chi
duong LECH tam moi bi bi thanh cong — tuc no la phep thu dung. Mot mau rieng, mot duong duy nhat ⇒ tach pixel
khong can nguong phuc tap, va do lech vuong goc so voi duong khop binh phuong toi thieu cho ra mot con so px doc
duoc.

## Nguong dat
Truoc khi nan (amount = 0) do lech phai **lon** (chan troi cong); sau khi nan (amount = 100) phai **nho**. Dat
nguong theo TI SO chu khong theo mot con so px tuyet doi: cua so overlay tren may ao va tren xe khac co, nen mot
nguong px cung se sai o mot trong hai. Yeu cau: `after <= before / RATIO` va `after <= MAX_AFTER_FRAC` cua be cao
khung. Ca hai con so in ra de nguoi doc tu phan xet.
"""
from __future__ import annotations

import argparse
import sys

import numpy as np
from PIL import Image

# Mau chan troi cua `CameraDewarpTestPattern.HORIZON` = 0xFFFFC24D.
HORIZON = np.array([0xFF, 0xC2, 0x4D], dtype=np.int16)

# Sai so mau cho phep cho mot pixel duoc coi la "chan troi": sampler GL noi suy tuyen tinh nen bien net bi tron
# voi mau nen dai. 48/255 du rong de bat loi net va du chat de khong bat nhan trang (#FFFFFF) hay vong RING.
COLOR_TOL = 48

# Sau khi nan, do lech phai nho hon truoc khi nan it nhat bay nhieu lan.
RATIO = 3.0

# Va phai nho hon bay nhieu phan be cao khung (chan troi "thang" trong pham vi ~2 % be cao).
MAX_AFTER_FRAC = 0.02

# Duoi so pixel nay thi khong co du du lieu de khop mot duong — bao KHONG DO DUOC, khong bao DAT.
MIN_PIXELS = 200


def horizon_deviation(path: str) -> tuple[float, float, int, tuple[int, int]]:
    """Do lech toi da + trung binh (px) cua chan troi so voi duong thang khop tot nhat.

    Tra `(max_dev, mean_dev, so_pixel, (w, h))`. Khop `y = a*x + b` bang binh phuong toi thieu tren TAM cua moi
    cot (chu khong tren moi pixel): net day 3 px, lay tam cot thi mot net day khong tu bien thanh do lech.
    """
    img = Image.open(path).convert("RGB")
    w, h = img.size
    arr = np.asarray(img, dtype=np.int16)
    dist = np.abs(arr - HORIZON).max(axis=2)
    mask = dist <= COLOR_TOL
    n = int(mask.sum())
    if n < MIN_PIXELS:
        return (float("nan"), float("nan"), n, (w, h))

    cols = np.where(mask.any(axis=0))[0]
    ys = np.array([np.flatnonzero(mask[:, c]).mean() for c in cols], dtype=np.float64)
    xs = cols.astype(np.float64)
    # `polyfit` bac 1 = binh phuong toi thieu; do lech doc theo y (chan troi gan nam ngang nen y-lech ≈ vuong goc).
    a, b = np.polyfit(xs, ys, 1)
    dev = np.abs(ys - (a * xs + b))
    return (float(dev.max()), float(dev.mean()), n, (w, h))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--before", required=True, help="PNG chup voi camera_dewarp_amount = 0")
    ap.add_argument("--after", required=True, help="PNG chup voi camera_dewarp_amount = 100")
    ap.add_argument("--out", help="ghi bao cao chi tiet ra tep")
    args = ap.parse_args()

    lines: list[str] = []

    def say(s: str) -> None:
        lines.append(s)
        print(s)

    b_max, b_mean, b_n, b_size = horizon_deviation(args.before)
    a_max, a_mean, a_n, a_size = horizon_deviation(args.after)

    say("=== DO THANG CUA CHAN TROI (px lech so voi duong khop) ===")
    say(f"  truoc (amount=0)   {b_size[0]}x{b_size[1]}  pixel={b_n:6d}  max={b_max:7.2f}  mean={b_mean:7.2f}")
    say(f"  sau   (amount=100) {a_size[0]}x{a_size[1]}  pixel={a_n:6d}  max={a_max:7.2f}  mean={a_mean:7.2f}")

    if np.isnan(b_max) or np.isnan(a_max):
        say(f"  KHONG DO DUOC: can >= {MIN_PIXELS} pixel mau chan troi trong CA HAI anh.")
        say("  Nghia la: khung den / chua co khung / crop khong chua chan troi. Xem logcat -s KachiCamera.")
        verdict = False
    else:
        limit_px = MAX_AFTER_FRAC * a_size[1]
        ok_ratio = a_max * RATIO <= b_max
        ok_abs = a_max <= limit_px
        say("")
        say(f"  nguong 1 (ti so):     sau*{RATIO:g} <= truoc   ⇒ {a_max * RATIO:7.2f} <= {b_max:7.2f}  {'DAT' if ok_ratio else 'KHONG DAT'}")
        say(f"  nguong 2 (tuyet doi): sau <= {MAX_AFTER_FRAC:g} * be cao ⇒ {a_max:7.2f} <= {limit_px:7.2f}  {'DAT' if ok_abs else 'KHONG DAT'}")
        verdict = ok_ratio and ok_abs

    say("")
    say(f"KET LUAN: {'DAT' if verdict else 'KHONG DAT'}")
    say("⚠ Anh vao la anh TONG HOP, sinh bang chinh mo hinh dang kiem ⇒ vong nay chung minh CAI DAT dung,")
    say("  KHONG noi gi ve ong kinh that. Tham so chot bang mot khung 5120x960 chup tu xe (CAM-B1..B4).")

    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")
    return 0 if verdict else 1


if __name__ == "__main__":
    sys.exit(main())
