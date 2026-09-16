#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Tổng hợp WAV huấn luyện từ nhiều giọng TTS (spec P2.2), chạy song song theo lõi.

Giọng dùng và vì sao [ĐO 2026-09-16 — có mặt thật trên máy này]:

| khoá | nguồn | số giọng | ghi chú |
|---|---|---|---|
| `vivos` | Piper `vi_VN-vivos-x_low` | **65** | multi-speaker — nguồn ĐA DẠNG GIỌNG duy nhất có sẵn; 16 kHz gốc |
| `vais` | Piper `vi_VN-vais1000-medium` | 1 | chính giọng app đang dùng để NÓI |
| `h25` | Piper `vi_VN-25hours_single-low` | 1 | giọng thứ ba, khác chất |
| `linh` | macOS `say -v Linh` | 1 | giọng của corpus cũ ⇒ số ghép được với các tệp diagnostics trước |
| `edge` | Microsoft Edge TTS | 2 | `vi-VN-HoaiMyNeural` (nữ) · `vi-VN-NamMinhNeural` (nam) — **cần mạng** |

⚠ **Giới hạn phải nói ra**: đây vẫn là giọng MÁY. [ĐO] đêm nay: corpus TTS xếp hạng các mô hình **ngược**
với bộ giọng người thật (gipformer thua A 0.5 điểm trên TTS, hơn 4.8 điểm trên người thật). Nên dữ liệu
này dùng để **dạy từ vựng lệnh**, không dùng để dạy *"tiếng người nói thế nào"* — phần đó là việc của
dữ liệu thật (`--real-speech` ở `make-manifest.py`) và của tăng cường (`augment.py`).

`say -v Linh` là tài sản của Apple: chỉ dùng **sinh dữ liệu huấn luyện trên máy này**, không phân phối lại WAV.
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import hashlib
import os
import subprocess
import sys
import wave

import numpy as np


def read_sentences(path: str, splits: set[str]) -> list[tuple[str, str, str, str]]:
    out = []
    for line in open(path, encoding="utf-8"):
        if line.startswith("#"):
            continue
        p = line.rstrip("\n").split("\t")
        if len(p) >= 6 and p[5] in splits:
            out.append((p[0], p[1], p[4], p[5]))   # id, kind, text, split
    return out


def write_wave(path: str, x: np.ndarray, sr: int = 16000):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(sr)
        f.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())


def trim(x: np.ndarray, sr: int = 16000, thr_db: float = -45.0) -> np.ndarray:
    """Cắt im lặng đầu/đuôi. BẮT BUỘC, không phải làm đẹp: [ĐO `voice-stream-eval` §6] nối 1.5 s im lặng
    vào đuôi kéo 22/25 xuống 11/25. Để đuôi rỗng trong dữ liệu huấn luyện là dạy mô hình đẻ token rác."""
    if len(x) == 0:
        return x
    win = 320
    n = len(x) // win
    if n == 0:
        return x
    e = 20 * np.log10(np.maximum(np.abs(x[:n * win].reshape(n, win)).max(axis=1), 1e-7))
    keep = np.where(e > thr_db)[0]
    if len(keep) == 0:
        return x
    a = max(0, keep[0] * win - win)
    b = min(len(x), (keep[-1] + 2) * win)
    return x[a:b]


class PiperVoice:
    def __init__(self, model_dir: str, data_dir: str, threads: int = 1):
        import sherpa_onnx
        j = [f for f in os.listdir(model_dir) if f.endswith(".onnx")][0]
        cfg = sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                    model=os.path.join(model_dir, j),
                    tokens=os.path.join(model_dir, "tokens.txt"),
                    data_dir=data_dir),
                num_threads=threads, provider="cpu"),
            max_num_sentences=1)
        self.tts = sherpa_onnx.OfflineTts(cfg)
        self.n_speakers = self.tts.num_speakers

    def say(self, text: str, sid: int, speed: float):
        a = self.tts.generate(text, sid=sid, speed=speed)
        return np.asarray(a.samples, dtype="float32"), a.sample_rate


def resample(x: np.ndarray, sr_in: int, sr_out: int = 16000) -> np.ndarray:
    if sr_in == sr_out:
        return x
    n = int(round(len(x) * sr_out / sr_in))
    return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype("float32")


def ensure_piper_tokens(model_dir: str):
    """Piper phát hành `.onnx.json`; sherpa-onnx cần `tokens.txt` tách ra từ `phoneme_id_map`."""
    tp = os.path.join(model_dir, "tokens.txt")
    if os.path.isfile(tp):
        return
    import json
    j = [f for f in os.listdir(model_dir) if f.endswith(".onnx.json")][0]
    d = json.load(open(os.path.join(model_dir, j), encoding="utf-8"))
    with open(tp, "w", encoding="utf-8") as f:
        for sym, ids in d["phoneme_id_map"].items():
            f.write(f"{sym} {ids[0]}\n")


def job_piper(args_tuple):
    key, model_dir, data_dir, out_dir, rows, speeds = args_tuple
    v = PiperVoice(model_dir, data_dir)
    made = []
    for rid, kind, text, split in rows:
        h = hashlib.sha256(text.encode()).digest()
        sid = h[0] % max(v.n_speakers, 1)
        sp = speeds[h[1] % len(speeds)]
        try:
            x, sr = v.say(text, sid, sp)
        except Exception as e:
            print(f"!! {key} {rid}: {e}", file=sys.stderr)
            continue
        if len(x) < 800:
            continue
        x = trim(resample(x, sr))
        uid = f"{rid}__{key}{sid}_{sp}__{h[:3].hex()}"
        write_wave(os.path.join(out_dir, split, f"{uid}.wav"), x)
        made.append((uid, split, key, f"{key}{sid}", sp, len(x) / 16000.0, rid, kind, text))
    return made


def job_say(args_tuple):
    key, out_dir, rows, rates = args_tuple
    made = []
    for rid, kind, text, split in rows:
        h = hashlib.sha256(text.encode()).digest()
        r = rates[h[1] % len(rates)]
        aiff = os.path.join("/tmp", f"say_{os.getpid()}.aiff")
        try:
            subprocess.run(["say", "-v", "Linh", "-r", str(r), "-o", aiff, text],
                           check=True, capture_output=True, timeout=30)
            wavp = aiff + ".wav"
            subprocess.run(["afconvert", "-f", "WAVE", "-d", "LEI16@16000", "-c", "1", aiff, wavp],
                           check=True, capture_output=True, timeout=30)
            with wave.open(wavp, "rb") as f:
                x = np.frombuffer(f.readframes(f.getnframes()), dtype="<i2").astype("float32") / 32768.
        except Exception as e:
            print(f"!! say {rid}: {e}", file=sys.stderr)
            continue
        finally:
            for p in (aiff, aiff + ".wav"):
                if os.path.exists(p):
                    os.remove(p)
        x = trim(x)
        uid = f"{rid}__linh_{r}__{h[:3].hex()}"
        write_wave(os.path.join(out_dir, split, f"{uid}.wav"), x)
        made.append((uid, split, "linh", "linh", r, len(x) / 16000.0, rid, kind, text))
    return made


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sentences", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--splits", nargs="+", default=["train", "dev"])
    ap.add_argument("--voices", nargs="+", default=["vivos", "vais", "h25", "linh"])
    ap.add_argument("--tts-root", required=True, help="thư mục chứa vivos/ 25hours/")
    ap.add_argument("--vais-dir", default="")
    ap.add_argument("--espeak", required=True, help="espeak-ng-data (lấy từ gói piper của app)")
    ap.add_argument("--workers", type=int, default=12)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--manifest", required=True)
    args = ap.parse_args()

    rows = read_sentences(args.sentences, set(args.splits))
    if args.limit:
        rows = rows[:args.limit]
    print(f"== {len(rows)} câu × {len(args.voices)} nhóm giọng")

    tasks = []
    for key in args.voices:
        if key == "linh":
            continue
        d = {"vivos": os.path.join(args.tts_root, "vivos"),
             "h25": os.path.join(args.tts_root, "25hours"),
             "vais": args.vais_dir}[key]
        ensure_piper_tokens(d)
        speeds = {"vivos": [0.9, 1.0, 1.15], "h25": [0.95, 1.1], "vais": [1.0, 1.25]}[key]
        chunks = [rows[i::args.workers] for i in range(args.workers)]
        tasks += [(key, d, args.espeak, args.out, c, speeds) for c in chunks if c]

    made = []
    with cf.ProcessPoolExecutor(max_workers=args.workers) as ex:
        for r in ex.map(job_piper, tasks):
            made += r
        if "linh" in args.voices:
            chunks = [rows[i::args.workers] for i in range(args.workers)]
            for r in ex.map(job_say, [("linh", args.out, c, [150, 180, 210, 250]) for c in chunks if c]):
                made += r

    os.makedirs(os.path.dirname(os.path.abspath(args.manifest)), exist_ok=True)
    with open(args.manifest, "w", encoding="utf-8") as f:
        f.write("# uid\tsplit\tvoice_group\tvoice\tspeed\tdur\tid\tkind\ttext\n")
        for m in made:
            f.write("\t".join(str(x) for x in m) + "\n")
    tot = sum(m[5] for m in made)
    print(f"== {len(made)} WAV · {tot/3600:.2f} giờ · ghi {args.manifest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
