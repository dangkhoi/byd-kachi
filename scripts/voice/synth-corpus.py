#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Đọc corpus `data/variants.tsv` thành WAV 16 kHz mono PCM16 — nhiều GIỌNG, nhiều TỐC ĐỘ.

## Vì sao phải nhiều tốc độ (câu hỏi gốc của tester bản 1.66: *"phải nói chậm mới nhận"*)
Nói *"phải nói chậm"* là một **giả thuyết**, không phải phép đo: có thể model hỏng ở tốc độ nhanh, cũng có thể
nó hỏng ở đúng mấy từ đó bất kể nhanh chậm. Cách tách hai khả năng ấy ra là đọc **cùng một câu** ở 5 tốc độ rồi
so tỉ lệ nghe đúng. Đó là việc của tệp này; kết luận nằm ở `mishear-table.py`.

## Ba nguồn giọng, ba mức bằng chứng khác nhau
 • `say -v Linh` (macOS, giọng Việt nữ) ở -r 140/180/220/260/300 — giọng TTS, đọc rõ, KHÔNG có ồn đường.
 • `piper vi_VN-vais1000-medium` qua chính sherpa-onnx (`voice/tts/piper-…`, cùng gói app đang dùng để NÓI) ở
   speed 0.9/1.0/1.3 — giọng thứ hai, phát âm khác hẳn `Linh`, nên câu nào SAI ở cả hai giọng là câu sai vì
   **từ vựng/hotword**, còn câu chỉ sai ở một giọng là sai vì **âm học của giọng đó**.
 • Biến thể "nói nhanh / nuốt chữ" 1.3× · 1.6× dựng bằng overlap-add thuần numpy từ chính WAV `Linh` 180.
   ⚠ Đây là **âm tổng hợp từ âm tổng hợp** — nó mô phỏng *nhịp* nói nhanh, KHÔNG mô phỏng cách người thật
   nuốt phụ âm cuối. Mọi số đo trên nhánh này đọc ở mức [SUY], không phải [ĐO].

⚠⚠ Cả ba đều KHÔNG phải giọng người + mic 4 kênh trên xe (CLAUDE.md §2 · KDoc `voice-wavgen.sh`). Bộ này trả
lời được *"model + hotword có nghe ra cụm này không"*, KHÔNG trả lời được *"trên xe có nghe được không"*. Phần
đó phải do người đọc thật — xem `docs/diagnostics/voice-recording-campaign-2026-09-16.md`.

Dùng:
  /tmp/sherpa-venv/bin/python scripts/voice/synth-corpus.py [--out /tmp/kachi-voice-corpus]
        [--per-id 2] [--no-piper] [--limit N]
Kết quả: <out>/*.wav + <out>/manifest.tsv (cột: wav · uid · id · intent_kind · region · style · voice · rate ·
tier · ref). `tier=A` là câu đọc đủ mọi giọng/tốc độ (câu tester báo sai, mọi tên app, 25 câu của ma trận cũ).
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
import unicodedata
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
REPO = os.path.dirname(os.path.dirname(HERE))
PIPER_DIR = os.path.join(REPO, "voice/tts/piper-vi_VN-vais1000-medium")

SAY_RATES_A = [140, 180, 220, 260, 300]
SAY_RATES_B = [180, 260]
PIPER_SPEEDS_A = [0.9, 1.0, 1.3]
PIPER_SPEEDS_B = [1.0]
STRETCH_A = [1.3, 1.6]

# Câu tester bản 1.66 báo SAI (owner chuyển lời, 2026-09-16) + câu đối chứng nghe ĐÚNG. Giữ nguyên văn.
TESTER = [
    "lọc ngay", "điều hoà", "lọc bụi", "Google Map",
    "Google", "bật đèn đọc", "mở YouTube", "bật lọc bụi", "bật điều hoà", "lọc ngay đi",
]


def read_tsv(path: str, ncol: int) -> list[list[str]]:
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            p = line.split("\t")
            if len(p) == ncol:
                out.append([x.strip() for x in p])
    return out


def write_wav(path: str, samples, rate: int = 16000) -> None:
    import numpy as np
    x = np.clip(np.asarray(samples, dtype="float32"), -1.0, 1.0)
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(rate)
        f.writeframes((x * 32767.0).astype("<i2").tobytes())


def read_wav(path: str):
    import numpy as np
    with wave.open(path, "rb") as f:
        n, sr = f.getnframes(), f.getframerate()
        data = f.readframes(n)
    return np.frombuffer(data, dtype="<i2").astype("float32") / 32768.0, sr


def resample_to_16k(x, sr_in: int):
    """Hạ tần số bằng sinc cửa sổ Hamming + nội suy tuyến tính (numpy thuần, không thêm phụ thuộc)."""
    import numpy as np
    if sr_in == 16000:
        return x
    cut = 0.45 * 16000 / sr_in           # chặn trên Nyquist đích ⇒ không gấp phổ (alias)
    n = 63
    t = np.arange(n) - (n - 1) / 2
    h = np.sinc(2 * cut * t) * np.hamming(n)
    h /= h.sum()
    y = np.convolve(x, h, mode="same")
    idx = np.arange(0, len(y), sr_in / 16000.0)
    return np.interp(idx, np.arange(len(y)), y).astype("float32")


def ola_faster(x, rate: float):
    """Nén thời gian giữ cao độ bằng overlap-add (rate > 1 ⇒ ngắn hơn / nói nhanh hơn).

    Cố ý dùng OLA thô chứ không phải phase-vocoder: nó để lại đúng thứ nhiễu "nuốt chữ" mà ta muốn thử, và
    không cần thêm scipy/librosa vào máy đang chạy. Kết quả là dữ liệu [SUY], xem KDoc đầu tệp.
    """
    import numpy as np
    N, hs = 1024, 256
    ha = max(1, int(round(hs * rate)))
    w = np.hanning(N).astype("float32")
    out = np.zeros(int(len(x) / rate) + N, dtype="float32")
    norm = np.zeros_like(out)
    i = j = 0
    while i + N < len(x) and j + N < len(out):
        out[j:j + N] += x[i:i + N] * w
        norm[j:j + N] += w
        i += ha
        j += hs
    norm[norm < 1e-6] = 1.0
    return (out / norm)[: int(len(x) / rate)]


def select(rows, per_id: int, apps):
    """Chọn mẫu TẤT ĐỊNH: tier A (phải đọc đủ giọng) + tier B (trải đều theo mã)."""
    tester_l = {unicodedata.normalize("NFC", t).lower() for t in TESTER}
    app_first: dict[str, list[str]] = {}
    for _k, _r, _s, name in apps:
        app_first.setdefault(name.lower(), []).append(name)

    chosen: list[tuple[str, list[str]]] = []   # (tier, row)
    seen: set[str] = set()
    by_id: dict[str, list[list[str]]] = {}
    for r in rows:
        by_id.setdefault(r[0], []).append(r)

    def take(tier, r):
        key = r[4].lower()
        if key in seen:
            return
        seen.add(key)
        chosen.append((tier, r))

    # A1 — đúng câu tester báo sai.
    for r in rows:
        if r[4].lower() in tester_l:
            take("A", r)
    # A2 — MỌI cách đọc tên app, mỗi cách một câu *"mở …"* (khuôn ngắn nhất ⇒ lỗi chỉ có thể ở tên).
    for r in rows:
        if r[1] == "app" and r[4].lower().startswith("mở ") and r[4][3:].lower() in app_first:
            take("A", r)
    # A3 — 25 câu của ma trận host cũ: nối được số mới với số đã công bố (§6.3 doc E2E 09-15).
    old = os.path.join("/tmp/kachi-voice-wav", "cases.tsv")
    if os.path.isfile(old):
        for line in open(old, encoding="utf-8"):
            if "\t" in line:
                wid, text = line.rstrip("\n").split("\t", 1)
                take("A", [f"old_{wid}", "old_case", "chung", "ngan", text])
    # B — trải đều theo mã: lấy `per_id` câu ở các vị trí cách đều nhau trong khối của mã đó.
    for rid in sorted(by_id):
        blk = by_id[rid]
        step = max(1, len(blk) // per_id)
        for k in range(per_id):
            i = min(len(blk) - 1, k * step)
            take("B", blk[i])
    return chosen


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--variants", default=os.path.join(DATA, "variants.tsv"))
    ap.add_argument("--out", default="/tmp/kachi-voice-corpus")
    ap.add_argument("--per-id", type=int, default=2)
    ap.add_argument("--limit", type=int, default=0, help="chỉ đọc N câu đầu (thử nhanh)")
    ap.add_argument("--no-piper", action="store_true")
    a = ap.parse_args()

    rows = read_tsv(a.variants, 5)
    apps = read_tsv(os.path.join(DATA, "apps.tsv"), 4)
    chosen = select(rows, a.per_id, apps)
    if a.limit:
        chosen = chosen[: a.limit]
    os.makedirs(a.out, exist_ok=True)

    tts = None
    if not a.no_piper:
        try:
            import sherpa_onnx
            tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
                model=sherpa_onnx.OfflineTtsModelConfig(
                    vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                        model=os.path.join(PIPER_DIR, "vi_VN-vais1000-medium.onnx"),
                        tokens=os.path.join(PIPER_DIR, "tokens.txt"),
                        data_dir=os.path.join(PIPER_DIR, "espeak-ng-data")),
                    num_threads=2, provider="cpu"),
                max_num_sentences=1))
        except Exception as e:                                  # noqa: BLE001 — thiếu model/gói thì báo rõ
            print(f"⚠ Không dựng được piper ({e}) — chỉ dùng `say`.", file=sys.stderr)
            tts = None

    man = open(os.path.join(a.out, "manifest.tsv"), "w", encoding="utf-8")
    man.write("# wav\tuid\tid\tintent_kind\tregion\tstyle\tvoice\trate\ttier\tref\n")
    seq: dict[str, int] = {}
    n_wav = 0
    for tier, (rid, ikind, region, style, text) in chosen:
        seq[rid] = seq.get(rid, 0) + 1
        uid = f"{rid}-{seq[rid]:02d}"
        say_rates = SAY_RATES_A if tier == "A" else SAY_RATES_B
        piper_speeds = PIPER_SPEEDS_A if tier == "A" else PIPER_SPEEDS_B

        base180 = None
        for r in say_rates:
            name = f"{uid}__linh__{r}.wav"
            path = os.path.join(a.out, name)
            if not os.path.isfile(path):
                subprocess.run(["say", "-v", "Linh", "-r", str(r), text, "-o", path,
                                "--file-format=WAVE", "--data-format=LEI16@16000"], check=True)
            man.write(f"{name}\t{uid}\t{rid}\t{ikind}\t{region}\t{style}\tlinh\t{r}\t{tier}\t{text}\n")
            n_wav += 1
            if r == 180:
                base180 = path

        if tts is not None:
            for sp in piper_speeds:
                name = f"{uid}__piper__{sp}.wav"
                path = os.path.join(a.out, name)
                if not os.path.isfile(path):
                    au = tts.generate(text, sid=0, speed=sp)
                    write_wav(path, resample_to_16k(
                        __import__("numpy").asarray(au.samples, dtype="float32"), au.sample_rate))
                man.write(f"{name}\t{uid}\t{rid}\t{ikind}\t{region}\t{style}\tpiper\t{sp}\t{tier}\t{text}\n")
                n_wav += 1

        if tier == "A" and base180:
            x, sr = read_wav(base180)
            for st in STRETCH_A:
                name = f"{uid}__linh-ola__{st}.wav"
                path = os.path.join(a.out, name)
                if not os.path.isfile(path):
                    write_wav(path, ola_faster(x, st), sr)
                man.write(f"{name}\t{uid}\t{rid}\t{ikind}\t{region}\t{style}\tlinh-ola\t{st}\t{tier}\t{text}\n")
                n_wav += 1
        if n_wav % 200 < 8:
            print(f"… {n_wav} WAV", flush=True)
    man.close()
    a_n = sum(1 for t, _ in chosen if t == "A")
    print(f"== {len(chosen)} câu ({a_n} tier A · {len(chosen)-a_n} tier B) ⇒ {n_wav} WAV tại {a.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
