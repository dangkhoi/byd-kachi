#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Tăng cường WAV huấn luyện: tốc độ · cao độ · ồn cabin · nhạc nền · vọng · băng thông mic (spec P2.3).

## Vì sao từng phép, và vì sao KHÔNG có phép nào khác

Không tăng cường bừa. Mỗi phép dưới đây ứng với **một** chỗ thủng đo được trên bộ giữ lại giọng thật
đêm 2026-09-16 (3 người × 3 lượt × 30 câu):

| Phép | Chỗ thủng nó nhắm vào | Bằng chứng |
|---|---|---|
| **tốc độ 0.85–1.25** | lượt **B nhanh**: owner chỉ 14–15/30 đúng ý định, thấp hơn lượt thường 15 câu | [ĐO] §3 `voice-ft` |
| **cao độ ±2 nửa cung** | giọng **trẻ em/thiếu niên** (`dau`): 22–25/30 ở MỌI mô hình — thấp nhất trong 3 người | [ĐO] |
| **ồn cabin + ù động cơ** | lượt **C có ồn/nhạc**: 17–28/30 tuỳ người | [ĐO] |
| **nhạc nền (SNR 5–20 dB)** | lượt C của giọng Nam là *"có nhạc"* | [ĐO] |
| **vọng phòng nhỏ** | cabin xe là hộp kín có phản xạ; mic vô-lăng cách miệng ~50 cm | [SUY] |
| **cắt băng thông 300–7000 Hz** | mic xe không phải mic studio | [SUY] |

**Ồn là ồn TỔNG HỢP** (hồng + ù động cơ có hài + rung đường), **không** phải thu từ cabin thật.
[ĐOÁN] nó giống ồn cabin thật tới đâu — chưa kiểm. Cách chốt: thu 30 s ồn nền trong xe qua `ClusterDiag`
rồi thay `synth_noise()` bằng bộ ồn thật, chạy lại pha này. Đến lúc đó, đây là giới hạn phải nói ra.

**Không** thêm im lặng vào đầu/đuôi: [ĐO `voice-stream-eval` §6] đuôi im lặng 1.5 s kéo 22/25 → 11/25.
Dạy mô hình bằng đuôi rỗng là dạy nó đẻ token rác.

Mọi phép ngẫu nhiên đều băm từ **uid + tên biến thể** ⇒ chạy lại ra y hệt (spec R7).
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import hashlib
import os
import sys
import wave

import numpy as np

SR = 16000


def rng_for(*parts: str) -> np.random.Generator:
    h = hashlib.sha256("|".join(parts).encode()).digest()
    return np.random.default_rng(int.from_bytes(h[:8], "big"))


def read_wave(path: str) -> np.ndarray:
    with wave.open(path, "rb") as f:
        return np.frombuffer(f.readframes(f.getnframes()), dtype="<i2").astype("float32") / 32768.0


def write_wave(path: str, x: np.ndarray):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())


def resample_lin(x: np.ndarray, factor: float) -> np.ndarray:
    """Đổi tốc độ bằng nội suy — đổi CẢ cao độ (giống người nói nhanh hơn/chậm hơn thật)."""
    n = max(1, int(round(len(x) / factor)))
    return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype("float32")


def pitch_shift(x: np.ndarray, semitones: float) -> np.ndarray:
    """Đổi cao độ, GIỮ nguyên độ dài: đổi mẫu rồi kéo/nén lại bằng chồng-cộng cửa sổ Hann.

    Cần giữ độ dài vì đây là phép nhắm vào **chất giọng** (trẻ em/nữ cao), không nhắm vào tốc độ —
    hai thứ đó phải tách ra thì mới biết phép nào có tác dụng.
    """
    r = 2.0 ** (semitones / 12.0)
    y = resample_lin(x, r)                      # đổi cao độ + độ dài
    return time_stretch(y, len(x))              # kéo về đúng độ dài cũ


def time_stretch(x: np.ndarray, target: int) -> np.ndarray:
    if len(x) == target or len(x) < 512:
        return x[:target] if len(x) >= target else np.pad(x, (0, target - len(x)))
    win, hop_out = 512, 128
    hop_in = hop_out * len(x) / target
    w = np.hanning(win).astype("float32")
    out = np.zeros(target + win, dtype="float32")
    norm = np.zeros(target + win, dtype="float32")
    for k in range(target // hop_out + 1):
        i = int(k * hop_in)
        if i + win > len(x):
            break
        o = k * hop_out
        out[o:o + win] += x[i:i + win] * w
        norm[o:o + win] += w
    return (out[:target] / np.maximum(norm[:target], 1e-6)).astype("float32")


def synth_noise(n: int, gen: np.random.Generator, kind: str) -> np.ndarray:
    """Ồn cabin TỔNG HỢP — xem cảnh báo ở đầu tệp."""
    if kind == "engine":
        t = np.arange(n) / SR
        f0 = gen.uniform(28, 55)                      # tần số nổ máy quy về dải nghe được
        y = np.zeros(n, dtype="float32")
        for h in (1, 2, 3, 4, 6):
            y += (1.0 / h) * np.sin(2 * np.pi * f0 * h * t + gen.uniform(0, 6.28))
        y += 0.3 * pink(n, gen)                       # rung gầm + gió lốp
        return (y / (np.abs(y).max() + 1e-6)).astype("float32")
    if kind == "road":
        y = pink(n, gen)
        # rung đường: biên độ dập dềnh chậm
        env = 1.0 + 0.4 * np.sin(2 * np.pi * gen.uniform(0.3, 1.2) * np.arange(n) / SR)
        return (y * env / (np.abs(y * env).max() + 1e-6)).astype("float32")
    if kind == "music":
        # "nhạc" tổng hợp: hợp âm + nhịp. KHÔNG dùng nhạc thật (vấn đề bản quyền), và mục đích chỉ là
        # một nguồn che phổ rộng có cấu trúc hài — không phải mô phỏng đúng bản nhạc nào.
        t = np.arange(n) / SR
        root = gen.choice([110.0, 146.8, 164.8, 196.0])
        y = np.zeros(n, dtype="float32")
        for r in (1.0, 1.26, 1.5, 2.0, 3.0):
            y += gen.uniform(0.4, 1.0) * np.sin(2 * np.pi * root * r * t + gen.uniform(0, 6.28))
        beat = (np.sin(2 * np.pi * gen.uniform(1.5, 2.5) * t) > 0.7).astype("float32")
        y = y * (0.6 + 0.4 * beat) + 0.15 * pink(n, gen)
        return (y / (np.abs(y).max() + 1e-6)).astype("float32")
    return pink(n, gen)


def pink(n: int, gen: np.random.Generator) -> np.ndarray:
    """Ồn hồng qua lọc phổ 1/f — gần phổ ồn nền cabin hơn ồn trắng."""
    m = 1 << int(np.ceil(np.log2(max(n, 2))))
    w = gen.standard_normal(m)
    f = np.fft.rfft(w)
    k = np.arange(len(f))
    k[0] = 1
    f = f / np.sqrt(k)
    y = np.fft.irfft(f)[:n]
    return (y / (np.abs(y).max() + 1e-6)).astype("float32")


def mix_snr(sig: np.ndarray, noise: np.ndarray, snr_db: float) -> np.ndarray:
    ps = float(np.mean(sig ** 2)) + 1e-12
    pn = float(np.mean(noise ** 2)) + 1e-12
    g = np.sqrt(ps / (pn * 10 ** (snr_db / 10.0)))
    return sig + g * noise


def reverb(x: np.ndarray, gen: np.random.Generator) -> np.ndarray:
    """Vọng phòng nhỏ: đuôi mũ suy giảm thưa, RT60 0.12–0.35 s (đúng cỡ khoang xe)."""
    rt60 = gen.uniform(0.12, 0.35)
    n = int(rt60 * SR)
    ir = gen.standard_normal(n).astype("float32") * np.exp(-6.9 * np.arange(n) / n).astype("float32")
    ir *= (gen.random(n) < 0.25)          # thưa ⇒ nghe như phản xạ rời rạc, không như ồn
    ir[0] = 1.0
    y = np.convolve(x, ir)[:len(x)]
    return (y / (np.abs(y).max() + 1e-6) * (np.abs(x).max() + 1e-6)).astype("float32")


def bandlimit(x: np.ndarray, lo: float, hi: float) -> np.ndarray:
    f = np.fft.rfft(x)
    fr = np.fft.rfftfreq(len(x), 1.0 / SR)
    f[(fr < lo) | (fr > hi)] = 0
    return np.fft.irfft(f, n=len(x)).astype("float32")


# Danh sách biến thể. Tên biến thể đi vào uid ⇒ đọc log là biết clip nào qua phép gì.
VARIANTS = ["goc", "nhanh", "cham", "cao", "tram", "on_may", "on_duong", "nhac", "vong", "mic"]


def apply_variant(x: np.ndarray, name: str, gen: np.random.Generator) -> np.ndarray:
    if name == "goc":
        return x
    if name == "nhanh":
        return resample_lin(x, gen.uniform(1.10, 1.25))
    if name == "cham":
        return resample_lin(x, gen.uniform(0.85, 0.95))
    if name == "cao":
        return pitch_shift(x, gen.uniform(1.2, 2.5))
    if name == "tram":
        return pitch_shift(x, -gen.uniform(1.2, 2.5))
    if name == "on_may":
        return mix_snr(x, synth_noise(len(x), gen, "engine"), gen.uniform(8, 20))
    if name == "on_duong":
        return mix_snr(x, synth_noise(len(x), gen, "road"), gen.uniform(5, 18))
    if name == "nhac":
        return mix_snr(x, synth_noise(len(x), gen, "music"), gen.uniform(5, 15))
    if name == "vong":
        return reverb(x, gen)
    if name == "mic":
        y = bandlimit(x, gen.uniform(180, 320), gen.uniform(6200, 7400))
        return y * gen.uniform(0.35, 1.0)
    raise ValueError(name)


def job(chunk):
    src_root, dst_root, rows, variants = chunk
    out = []
    for uid, split, text in rows:
        p = os.path.join(src_root, split, f"{uid}.wav")
        if not os.path.isfile(p):
            continue
        x = read_wave(p)
        if len(x) < 800:
            continue
        for v in variants:
            g = rng_for(uid, v)
            try:
                y = apply_variant(x, v, g)
            except Exception as e:
                print(f"!! {uid} {v}: {e}", file=sys.stderr)
                continue
            if len(y) < 400:
                continue
            m = float(np.abs(y).max())
            if m > 1e-6:
                y = y / m * 0.95 * (0.5 + 0.5 * g.random())     # chuẩn đỉnh + đổi mức to nhỏ
            nu = f"{uid}__{v}"
            write_wave(os.path.join(dst_root, split, f"{nu}.wav"), y)
            out.append((nu, split, v, round(len(y) / SR, 3), text))
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--tts-manifest", required=True)
    ap.add_argument("--src", required=True)
    ap.add_argument("--dst", required=True)
    ap.add_argument("--out-manifest", required=True)
    ap.add_argument("--variants", nargs="+", default=VARIANTS)
    ap.add_argument("--per-clip", type=int, default=3,
                    help="mỗi clip gốc lấy mấy biến thể (chọn bằng băm ⇒ tái lập được)")
    ap.add_argument("--workers", type=int, default=12)
    args = ap.parse_args()

    rows = []
    for line in open(args.tts_manifest, encoding="utf-8"):
        if line.startswith("#"):
            continue
        p = line.rstrip("\n").split("\t")
        if len(p) >= 9:
            rows.append((p[0], p[1], p[8]))
    print(f"== {len(rows)} clip gốc × {args.per_clip} biến thể")

    # mỗi clip lấy một TẬP CON biến thể khác nhau ⇒ phủ đều mà không nở gấp 10 lần
    pool = [v for v in args.variants if v != "goc"]
    tasks = []
    for i in range(args.workers):
        sub = rows[i::args.workers]
        tasks.append((args.src, args.dst, sub, None))
    made = []
    with cf.ProcessPoolExecutor(max_workers=args.workers) as ex:
        futs = []
        for (src, dst, sub, _) in tasks:
            grouped: dict[tuple[str, ...], list] = {}
            for uid, split, text in sub:
                g = rng_for(uid, "pick")
                pick = tuple(sorted(g.choice(pool, size=min(args.per_clip, len(pool)),
                                             replace=False).tolist()))
                grouped.setdefault(pick, []).append((uid, split, text))
            for pick, rws in grouped.items():
                futs.append(ex.submit(job, (src, dst, rws, list(pick))))
        for f in cf.as_completed(futs):
            made += f.result()

    with open(args.out_manifest, "w", encoding="utf-8") as f:
        f.write("# uid\tsplit\tvariant\tdur\ttext\n")
        for m in made:
            f.write("\t".join(str(x) for x in m) + "\n")
    tot = sum(m[3] for m in made)
    print(f"== {len(made)} WAV tăng cường · {tot/3600:.2f} giờ · ghi {args.out_manifest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
