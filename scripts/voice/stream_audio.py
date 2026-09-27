#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bốn hàm audio của `stream-matrix.py` — đọc WAV 16 kHz mono PCM16, RMS theo khung, biên tiếng nói, đệm im lặng.

Tách THUẦN từ `stream-matrix.py` (529 dòng → trần 500, L6-debt 2026-09-27): thân hàm giữ nguyên byte. Cùng thư mục,
nạp qua `sys.path.insert(0, HERE)` như `vi_text` — không phải gói mới. `numpy` nhập trong từng hàm (như bản gốc) để
`--help` chạy được ngoài venv sherpa.
"""
from __future__ import annotations

import sys
import wave


# ── audio ───────────────────────────────────────────────────────────────────────────────────────────

def read_wave(path: str):
    import numpy as np
    with wave.open(path, "rb") as f:
        if f.getframerate() != 16000 or f.getnchannels() != 1 or f.getsampwidth() != 2:
            sys.exit(f"{path}: cần PCM16 · mono · 16 kHz")
        data = f.readframes(f.getnframes())
    return np.frombuffer(data, dtype="<i2").astype("float32") / 32768.0


def rms_frames(x, win: int = 320):
    """RMS theo khung 20 ms — dùng cho cả [speech_end] lẫn chọn đoạn tiếng phòng."""
    import numpy as np
    n = len(x) // win * win
    if n == 0:
        return np.zeros(1, dtype="float32")
    return np.sqrt((x[:n].reshape(-1, win) ** 2).mean(axis=1) + 1e-12)


def speech_bounds(x, win: int = 320, rel_db: float = 25.0):
    """(đầu, cuối) tiếng nói tính bằng GIÂY, ngưỡng = đỉnh RMS − `rel_db` dB, có đáy tuyệt đối.

    Ngưỡng tương đối vì corpus có cả giọng `say` lẫn `piper` lẫn bản nén thời gian — mức thu khác nhau.
    Đáy tuyệt đối (−60 dBFS) chặn trường hợp WAV gần như im hoàn toàn thì mọi khung đều "là tiếng".
    """
    import numpy as np
    r = rms_frames(x, win)
    thr = max(r.max() * (10.0 ** (-rel_db / 20.0)), 10.0 ** (-60.0 / 20.0))
    idx = np.nonzero(r >= thr)[0]
    if len(idx) == 0:
        return 0.0, len(x) / 16000.0
    return idx[0] * win / 16000.0, (idx[-1] + 1) * win / 16000.0


def room_tone(x, n: int, mode: str, win: int = 320):
    """`n` mẫu "im lặng" nối vào đuôi/đầu. `room` = lát lại đoạn 100 ms ÊM NHẤT của chính WAV đó."""
    import numpy as np
    if n <= 0:
        return np.zeros(0, dtype="float32")
    if mode == "zero":
        return np.zeros(n, dtype="float32")
    if mode == "noise":
        return (np.random.RandomState(0).randn(n) * 3e-4).astype("float32")
    r = rms_frames(x, win)
    k = 5                                     # 5 khung × 20 ms = 100 ms
    if len(r) <= k:
        return np.zeros(n, dtype="float32")
    sums = np.convolve(r, np.ones(k), "valid")
    i = int(sums.argmin()) * win
    seg = x[i:i + k * win]
    if len(seg) == 0:
        return np.zeros(n, dtype="float32")
    return np.tile(seg, n // len(seg) + 1)[:n].astype("float32").copy()
