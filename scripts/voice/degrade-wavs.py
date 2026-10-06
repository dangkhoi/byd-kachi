#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bản "khó nghe" của một thư mục WAV có `cases.tsv` — cho `hotword-matrix.py` (VOICE-ALT-LABEL-HOTWORD, 2.93).

## Vì sao cần
[ĐO host 2026-10-06] bộ TTS sạch (`say -v Linh`) gần trần: 26/28 câu *"mở <nhãn app>"* đúng chữ NGAY CẢ KHÔNG hotword ⇒
ma trận trên bộ sạch không phân biệt được "hotword có ích" với "vô hại". Hotword chỉ đổi kết quả khi biên âm học giữa
đường đúng và đường sai hẹp lại — đúng thứ ồn cabin / nói nhanh / mic xe làm. Công cụ này làm hẹp biên ấy một cách
TẤT ĐỊNH để cùng một lệnh cho cùng một bộ WAV ở mọi lượt đo.

## Không phép mới — dùng lại `ft/augment.py` (DRY)
Mỗi biến thể = `augment.apply_variant` (ồn máy/đường tổng hợp · nhạc · nhanh/chậm · cao/trầm giọng · vọng · băng thông
mic), hạt giống băm từ `id + tên biến thể` (`augment.rng_for`) ⇒ chạy lại ra y hệt từng mẫu. Ồn là ồn TỔNG HỢP —
[ĐOÁN] giống cabin thật tới đâu, xem cảnh báo đầu `ft/augment.py`.

## `--tails` — ĐUÔI sau câu nói (ca `w26`, spec 2.93 §4.11)
Thay vì làm khó cả câu, nối 2 s đuôi (`--tail-ms`): `duoi_im` (số 0 — như `w26`) · `duoi_may` / `duoi_duong` / `duoi_nhac`
(ồn LIÊN TỤC cả câu lẫn đuôi ở −15/−10/−12 dB so với tiếng — cabin thật: nói xong thì chỉ còn ồn). Đo bằng
`hotword-matrix.py --trim vad` (đường app) và `--trim none` (đường lùi RMS: giải mã nguyên cửa sổ).

Dùng (python của venv sherpa, có numpy):
  <venv>/bin/python -I scripts/voice/degrade-wavs.py --src /tmp/kachi-voice-wav --dst /tmp/kachi-voice-wav-deg
  <venv>/bin/python -I scripts/voice/degrade-wavs.py --src <W> --dst <W-tail> --tails duoi_im duoi_may duoi_duong duoi_nhac
Ra: `<dst>/<id>_<biến thể>.wav` + `<dst>/cases.tsv` (cùng câu tham chiếu) ⇒ `hotword-matrix.py --wav <dst>`.
"""
from __future__ import annotations

import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "ft"))   # `python -I` không tự thêm thư mục script

import augment  # noqa: E402

DEFAULT_VARIANTS = [v for v in augment.VARIANTS if v != "goc"]

# ── `--tails`: ĐUÔI sau câu nói (2.93, ca `w26` — đuôi không phải tiếng đi vào bộ giải mã) ─────────────────────────────
# tên → (loại ồn của `augment.synth_noise` | "zero", mức so với RMS tiếng nói (dB), ồn LIÊN TỤC cả câu hay chỉ ở đuôi).
# Liên tục = cabin thật: máy/đường/nhạc chạy suốt, người lái nói xong thì chỉ còn ồn — đúng thứ VAD phải phân biệt.
TAILS = {
    "duoi_im": ("zero", None, False),
    "duoi_may": ("engine", -15.0, True),
    "duoi_duong": ("road", -10.0, True),
    "duoi_nhac": ("music", -12.0, True),
}


def with_tail(x, kind: str, gen, tail_ms: int):
    """Nối [tail_ms] đuôi vào câu [x] (float32 [-1, 1]) theo [TAILS] — tất định theo [gen]."""
    import numpy as np
    noise_kind, db, continuous = TAILS[kind]
    y = np.concatenate([x, np.zeros(int(tail_ms * augment.SR / 1000), dtype="float32")])
    if noise_kind == "zero":
        return y
    speech_rms = float(np.sqrt(np.mean(x ** 2)) + 1e-9)
    nz = augment.synth_noise(len(y), gen, noise_kind)
    nz = nz * (speech_rms * 10 ** (db / 20.0) / (float(np.sqrt(np.mean(nz ** 2))) + 1e-9))
    if not continuous:
        nz[: len(x)] = 0.0
    return (y + nz).astype("float32")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--src", required=True, help="thư mục WAV có cases.tsv")
    ap.add_argument("--dst", required=True, help="thư mục ra (tạo mới nếu chưa có)")
    ap.add_argument("--variants", nargs="+", default=DEFAULT_VARIANTS, choices=augment.VARIANTS)
    ap.add_argument("--tails", nargs="+", choices=sorted(TAILS), help="thay cho --variants: nối đuôi sau câu (xem TAILS)")
    ap.add_argument("--tail-ms", type=int, default=2000, help="độ dài đuôi (mặc định 2 s — như w26)")
    a = ap.parse_args()

    tsv = os.path.join(a.src, "cases.tsv")
    if not os.path.isfile(tsv):
        sys.exit(f"thiếu {tsv}")
    os.makedirs(a.dst, exist_ok=True)
    rows = []
    for line in open(tsv, encoding="utf-8"):
        if "\t" not in line:
            continue
        wid, text = line.rstrip("\n").split("\t", 1)
        x = augment.read_wave(os.path.join(a.src, f"{wid}.wav"))
        for v in (a.tails or a.variants):
            uid = f"{wid}_{v}"
            gen = augment.rng_for(wid, v)
            y = with_tail(x, v, gen, a.tail_ms) if a.tails else augment.apply_variant(x, v, gen)
            augment.write_wave(os.path.join(a.dst, f"{uid}.wav"), y)
            rows.append(f"{uid}\t{text}")
    with open(os.path.join(a.dst, "cases.tsv"), "w", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")
    k = len(a.tails or a.variants)
    print(f"== {len(rows)} WAV ({len(rows) // max(1, k)} câu × {k} biến thể) tại {a.dst}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
