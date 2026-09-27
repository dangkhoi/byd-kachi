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


def horizon_deviation(path: str, axis: str = "auto") -> tuple[float, float, int, tuple[int, int], str, int]:
    """Do lech toi da + trung binh (px) cua chan troi so voi duong thang khop tot nhat.

    Tra `(max_dev, mean_dev, so_pixel, (w, h), truc, be_ngang_vuong_goc)`.

    ## Vi sao phai biet TRUC (2.75)
    Chan troi cua `CameraDewarpTestPattern` nam NGANG trong o nguon. Khung ra co the da bi `uRotation` xoay ±90
    (mac dinh cua CA HAI ben guong: trai ↺−90 / phai ↻+90), luc do chan troi thanh gan DOC — khop `y = a*x + b`
    theo tung cot se lay trung binh ca mot cot day va cho ra mot con so vo nghia. `axis="y"` khop `x = a*y + b`
    theo tung HANG, tuc cung phep do nhung doc theo truc dai cua net.

    `axis="auto"` chon truc co NHIEU vach hon (nhieu cot co pixel hon ⇒ net nam ngang, va nguoc lai). Nguong
    tuyet doi thi lay be rong VUONG GOC voi net (`h` cho net ngang, `w` cho net doc), khong phai luon lay `h`.
    """
    img = Image.open(path).convert("RGB")
    w, h = img.size
    arr = np.asarray(img, dtype=np.int16)
    dist = np.abs(arr - HORIZON).max(axis=2)
    mask = dist <= COLOR_TOL
    n = int(mask.sum())
    if n < MIN_PIXELS:
        return (float("nan"), float("nan"), n, (w, h), axis, h)

    cols = np.where(mask.any(axis=0))[0]
    rows = np.where(mask.any(axis=1))[0]
    if axis == "auto":
        axis = "x" if len(cols) >= len(rows) else "y"

    if axis == "x":
        # Net gan NGANG: mot diem cho moi cot, khop y theo x, lech doc theo y ⇒ nguong theo be CAO.
        along, perp = cols, h
        ts = np.array([np.flatnonzero(mask[:, c]).mean() for c in cols], dtype=np.float64)
    else:
        # Net gan DOC (khung da xoay ±90): mot diem cho moi hang, khop x theo y, lech doc theo x ⇒ nguong be NGANG.
        along, perp = rows, w
        ts = np.array([np.flatnonzero(mask[r, :]).mean() for r in rows], dtype=np.float64)

    us = along.astype(np.float64)
    # `polyfit` bac 1 = binh phuong toi thieu; net gan song song truc `us` nen lech theo `ts` ≈ vuong goc.
    a, b = np.polyfit(us, ts, 1)
    dev = np.abs(ts - (a * us + b))
    return (float(dev.max()), float(dev.mean()), n, (w, h), axis, perp)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--before", required=True, help="PNG chup voi camera_dewarp_amount = 0")
    ap.add_argument("--after", required=True, help="PNG chup voi camera_dewarp_amount = 100")
    ap.add_argument("--out", help="ghi bao cao chi tiet ra tep")
    ap.add_argument("--axis", default="auto", choices=("auto", "x", "y"),
                    help="truc khop net chan troi: auto (mac dinh), x = net ngang, y = net doc (khung xoay ±90)")
    ap.add_argument("--label", default="", help="nhan in kem (vd `rot=L90`) de doc bao cao nhieu luot")
    args = ap.parse_args()

    lines: list[str] = []

    def say(s: str) -> None:
        lines.append(s)
        print(s)

    b_max, b_mean, b_n, b_size, b_axis, _ = horizon_deviation(args.before, args.axis)
    # MOT truc cho ca hai anh: `auto` chon theo tung anh se so hai con so do tren HAI truc khac nhau (nguong ti so
    # thanh vo nghia) neu phep nan doi du hinh dang net de hai lan chon lech nhau. Chot theo anh TRUOC — do la anh
    # con giu nguyen hinh dang nguon.
    a_max, a_mean, a_n, a_size, a_axis, a_perp = horizon_deviation(args.after, b_axis)

    say(f"=== DO THANG CUA CHAN TROI (px lech so voi duong khop) {args.label} ===")
    say(f"  truoc (amount=0)   {b_size[0]}x{b_size[1]}  truc={b_axis}  pixel={b_n:6d}  max={b_max:7.2f}  mean={b_mean:7.2f}")
    say(f"  sau   (amount=100) {a_size[0]}x{a_size[1]}  truc={a_axis}  pixel={a_n:6d}  max={a_max:7.2f}  mean={a_mean:7.2f}")

    if np.isnan(b_max) or np.isnan(a_max):
        say(f"  KHONG DO DUOC: can >= {MIN_PIXELS} pixel mau chan troi trong CA HAI anh.")
        say("  Nghia la: khung den / chua co khung / crop khong chua chan troi. Xem logcat -s KachiCamera.")
        verdict = False
    else:
        limit_px = MAX_AFTER_FRAC * a_perp
        ok_ratio = a_max * RATIO <= b_max
        ok_abs = a_max <= limit_px
        say("")
        say(f"  nguong 1 (ti so):     sau*{RATIO:g} <= truoc   ⇒ {a_max * RATIO:7.2f} <= {b_max:7.2f}  {'DAT' if ok_ratio else 'KHONG DAT'}")
        say(f"  nguong 2 (tuyet doi): sau <= {MAX_AFTER_FRAC:g} * be vuong goc ⇒ {a_max:7.2f} <= {limit_px:7.2f}  {'DAT' if ok_abs else 'KHONG DAT'}")
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
