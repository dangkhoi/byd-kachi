#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Gộp manifest + thêm tiếng nói THẬT + chốt đường chia train/dev/holdout (spec P2.4).

## Vì sao phải có tiếng nói THẬT trong dữ liệu fine-tune

[ĐO 2026-09-16] corpus TTS xếp hạng các mô hình **ngược** với bộ giọng người thật: `gipformer1.5` thua bản
đang ship 0.5 điểm trên TTS nhưng hơn **4.8 điểm** trên người thật. Fine-tune trên 100 % giọng máy là đi
tối ưu cho đúng cái thước đã biết là sai. Nên mỗi mẻ huấn luyện phải có một phần tiếng nói người thật —
vừa để chống *quên kiến thức nền*, vừa để mô hình còn nghe được thứ nó sẽ gặp trên xe.

Nguồn tiếng nói thật dùng ở đây: **`doof-ferb/fpt_fosd`** — [ĐO] `cardData.license = "cc-by-4.0"`,
`gated: false`, tải được **không cần token** (HTTP 206 trên ranged GET). Khoảng 100 giờ đọc/phát thanh
tiếng Việt. Đây là tập tiếng Việt thật có giấy phép mở lớn nhất lấy được mà không phải xin quyền.

⚠ **KHÔNG** dùng: `viet_bud500` · `viVoice` · `vivos` (đều CC-BY-**NC**, và hai cái đầu còn gated 401).

## Chia dữ liệu

Chia theo **CÂU**, không theo tệp: cùng một câu ở mọi giọng và mọi mức tăng cường phải nằm cùng một phía.
Dùng lại đúng hàm băm của `lm-text.py`/`gen-sentences.py` ⇒ ba pha (LM · fine-tune · đo) chia y hệt nhau.
`holdout` ở đây là **câu chưa từng huấn luyện** — khác với bộ **giữ lại giọng người thật** (`align-real.py`),
thứ không bao giờ vào bất cứ manifest nào.
"""
from __future__ import annotations

import argparse
import io
import os
import sys
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(REPO, "scripts", "voice"))

from vi_text import norm as vi_norm  # noqa: E402


def write_wave(path: str, x: np.ndarray, sr: int = 16000):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(sr)
        f.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())


def extract_parquet(paths: list[str], out_dir: str, manifest: str, max_hours: float,
                    max_sec: float, dev_every: int):
    """Rút WAV 16 kHz mono + câu chữ từ parquet của `doof-ferb/fpt_fosd`.

    Đọc bằng `pyarrow` theo từng nhóm hàng để không nạp cả 800 MB vào RAM một lúc.
    """
    import pyarrow.parquet as pq
    import soundfile as sf

    n = 0
    tot = 0.0
    rows = []
    for p in paths:
        pf = pq.ParquetFile(p)
        cols = [f.name for f in pf.schema_arrow]
        acol = "audio" if "audio" in cols else cols[0]
        tcol = next((c for c in ("transcription", "text", "sentence", "transcript") if c in cols), None)
        if tcol is None:
            sys.exit(f"{p}: không thấy cột chữ trong {cols}")
        for batch in pf.iter_batches(batch_size=64):
            d = batch.to_pydict()
            for a, t in zip(d[acol], d[tcol]):
                if tot / 3600.0 >= max_hours:
                    break
                raw = a["bytes"] if isinstance(a, dict) else a
                try:
                    x, sr = sf.read(io.BytesIO(raw), dtype="float32")
                except Exception:
                    continue
                if x.ndim > 1:
                    x = x.mean(axis=1)
                if sr != 16000:
                    m = int(round(len(x) * 16000 / sr))
                    x = np.interp(np.linspace(0, len(x) - 1, m), np.arange(len(x)), x).astype("float32")
                dur = len(x) / 16000.0
                if not (0.4 <= dur <= max_sec):
                    continue
                txt = vi_norm(str(t))
                if not txt or len(txt.split()) < 2:
                    continue
                split = "dev" if (n % dev_every == 0) else "train"
                uid = f"fpt{n:06d}"
                write_wave(os.path.join(out_dir, split, f"{uid}.wav"), x)
                rows.append((uid, split, round(dur, 3), txt))
                n += 1
                tot += dur
            if tot / 3600.0 >= max_hours:
                break
        if tot / 3600.0 >= max_hours:
            break

    with open(manifest, "w", encoding="utf-8") as f:
        f.write("# uid\tsplit\tdur\ttext\n")
        for r in rows:
            f.write("\t".join(str(x) for x in r) + "\n")
    print(f"== tiếng nói THẬT: {n} clip · {tot/3600:.2f} giờ → {manifest}")


def summarise(paths: list[str]):
    import collections
    agg = collections.defaultdict(lambda: [0, 0.0])
    for p in paths:
        for line in open(p, encoding="utf-8"):
            if line.startswith("#"):
                continue
            c = line.rstrip("\n").split("\t")
            if len(c) >= 9:
                sp, d = c[1], float(c[5])
            elif len(c) == 5:
                sp, d = c[1], float(c[3])
            elif len(c) == 4:
                sp, d = c[1], float(c[2])
            else:
                continue
            agg[(os.path.basename(p), sp)][0] += 1
            agg[(os.path.basename(p), sp)][1] += d
    tot = 0.0
    for (name, sp), (n, d) in sorted(agg.items()):
        print(f"   {name:28s} {sp:8s} {n:7d} clip  {d/3600:6.2f} h")
        tot += d
    print(f"   {'TỔNG':28s} {'':8s} {'':7s}  {tot/3600:6.2f} h")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    r = sub.add_parser("real", help="rút tiếng nói thật từ parquet fpt_fosd")
    r.add_argument("--parquet", nargs="+", required=True)
    r.add_argument("--out", required=True)
    r.add_argument("--manifest", required=True)
    r.add_argument("--max-hours", type=float, default=20.0)
    r.add_argument("--max-sec", type=float, default=14.0)
    r.add_argument("--dev-every", type=int, default=120)

    s = sub.add_parser("summary", help="thống kê các manifest")
    s.add_argument("--manifest", nargs="+", required=True)

    a = ap.parse_args()
    if a.cmd == "real":
        extract_parquet(a.parquet, a.out, a.manifest, a.max_hours, a.max_sec, a.dev_every)
    else:
        summarise(a.manifest)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
