#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HARNESS "Hey Kachi" — bước 2/3: dựng bộ WAV thử từ TTS (`gen-tts.sh`): cabin yên / nhạc KHÔNG lời / nhạc CÓ lời, + 3 tệp dài
45 s để đo CPU (im · nhạc không lời · nhạc có lời).

Nhạc là TỔNG HỢP (hợp âm sin + bass + kick/hi-hat nhiễu) — không có bản quyền, không có tệp nhạc nào trong repo. Lời = `lyrics.wav`
của TTS lặp vòng. Mức: giọng RMS 0,12 (≈ +3,5 dB trên nhạc 0,08), lời ≈ −4 dB dưới nhạc. Hạt giống cố định ⇒ chạy lại ra đúng bộ.

Dùng: mix.py <thư-mục-tts> <thư-mục-ra>   (cần numpy)
Ra:  clean_<pos|neg>_<i>_<rate>.wav · instr_pos_… · vocal_pos_… · long_silence.wav · long_instr.wav · long_vocal.wav
Đọc kết quả: `run.sh` rồi `score.py`. Bộ số 3.1.9: `docs/diagnostics/oncar-perf-2026-10-15-evening.md` §G2/§G6.
"""
import glob
import os
import sys
import wave

import numpy as np

src, out = sys.argv[1], sys.argv[2]
SR = 16000
rng = np.random.default_rng(20261015)


def rd(p):
    with wave.open(p) as w:
        return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768


def wr(p, x):
    x = np.clip(x, -1, 1)
    with wave.open(p, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())


def rms(x):
    return float(np.sqrt(np.mean(x * x)) + 1e-12)


def music(sec):
    t = np.arange(int(sec * SR)) / SR
    chords = [[220, 277.2, 329.6], [196, 246.9, 293.7], [174.6, 220, 261.6], [196, 246.9, 293.7]]
    x = np.zeros_like(t)
    for k, ch in enumerate(chords * int(sec // 8 + 1)):
        a, b = int(k * 2 * SR), int((k + 1) * 2 * SR)
        if a >= len(t):
            break
        tt = t[a:b]
        for f in ch:
            for h in (1, 2, 3):
                x[a:b] += 0.08 / h * np.sin(2 * np.pi * f * h * tt)
        x[a:b] += 0.15 * np.sin(2 * np.pi * ch[0] / 2 * tt)                       # bass
    for beat in np.arange(0, sec, 0.5):                                            # kick + hi-hat
        a = int(beat * SR); n = min(int(0.12 * SR), len(t) - a)
        if n <= 0:
            continue
        env = np.exp(-np.arange(n) / (0.03 * SR))
        x[a:a + n] += 0.5 * env * np.sin(2 * np.pi * 60 * np.arange(n) / SR)
        b = int((beat + 0.25) * SR); m = min(int(0.04 * SR), len(t) - b)
        if m > 0:
            x[b:b + m] += 0.08 * rng.standard_normal(m) * np.exp(-np.arange(m) / (0.01 * SR))
    return x


def cabin(n):
    return 0.003 * rng.standard_normal(n).astype(np.float32)


lyr = rd(os.path.join(src, "lyrics.wav"))


def vocal_music(sec):
    m = music(sec); n = len(m)
    v = np.tile(lyr, n // len(lyr) + 1)[:n]
    return m / rms(m) * 0.08 + v / rms(v) * 0.05                                   # lời ≈ −4 dB dưới nhạc


def instr(sec):
    m = music(sec)
    return m / rms(m) * 0.08


os.makedirs(out, exist_ok=True)
for p in sorted(glob.glob(os.path.join(src, "pos_*.wav")) + glob.glob(os.path.join(src, "neg_*.wav"))):
    s = rd(p); s = s / rms(s) * 0.12                                               # giọng RMS 0,12
    name = os.path.basename(p)[:-4]
    clean = np.concatenate([np.zeros(int(1.0 * SR)), s, np.zeros(int(1.5 * SR))]).astype(np.float32)
    n = len(clean)
    wr(os.path.join(out, f"clean_{name}.wav"), clean + cabin(n))
    if name.startswith("pos"):
        off = int(rng.integers(0, 8 * SR))
        wr(os.path.join(out, f"instr_{name}.wav"), clean + instr(30)[off:off + n])
        wr(os.path.join(out, f"vocal_{name}.wav"), clean + vocal_music(30)[off:off + n])
L = 45
wr(os.path.join(out, "long_silence.wav"), cabin(L * SR))
wr(os.path.join(out, "long_instr.wav"), instr(L))
wr(os.path.join(out, "long_vocal.wav"), vocal_music(L))
print(f"✓ {len(glob.glob(os.path.join(out, '*.wav')))} WAV → {out}")
