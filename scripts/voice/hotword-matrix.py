#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ma trận hotword × WAV trên HOST (không cần máy ảo/xe) — công cụ đã chốt thiết kế `kachi-voice-hotword-phrases`.

Vì sao trên host: cùng sherpa-onnx 1.13.8 + cùng model `zipformer-vi-2025-04-20` + cùng tham số như
`VoiceRecognizer.kt` / `SherpaModelCatalog.kt` (modified_beam_search · beam 4 · score 3.0 · bpe) ⇒ một tệp hotword
đổi là biết ngay nghe khác đi thế nào, trước khi tốn một vòng build + E2E máy ảo. [ĐO] 2026-09-16: 5 ma trận (31
cấu hình tệp, mỗi cấu hình 25 WAV — log `matrix2..6_stdout.log`) chạy bằng công cụ này là thứ bác giả thuyết "loãng" và chốt luật "chỉ cụm, không từ rời, không tiền tố" (doc
`docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §6.3 phần [ĐO host 2026-09-16]).

Chuẩn bị (một lần):
  python3 -m venv /tmp/sherpa-venv && /tmp/sherpa-venv/bin/pip install sherpa-onnx==1.13.8 numpy
  MODEL_DIR = thư mục có encoder.onnx · decoder.onnx · joiner.onnx · tokens.txt (tải như `VoiceModelStore`)
             + bpe_vocab.txt (chép từ app/src/main/assets/voice/zipformer-vi-2025-04-20.bpe_vocab.txt)
  WAV: scripts/emulator/voice-wavgen.sh /tmp/kachi-voice-wav   (25 câu `say -v Linh`, kèm cases.tsv)
  Tệp hotword THẬT của bản build: chạy `./gradlew :core:test --tests '*SherpaBiasingCoverageTest*'` ⇒
             core/build/hotwords/hotwords-phrases.txt (dump từ chính SherpaBiasing.hotwordsFile()).

Dùng:
  /tmp/sherpa-venv/bin/python scripts/voice/hotword-matrix.py --model <MODEL_DIR> [--wav /tmp/kachi-voice-wav]
        [--score 3.0] none core/build/hotwords/hotwords-phrases.txt [tệp-khác.txt ...]
  `none` = chạy không hotword (mốc). Mỗi tệp: in từng câu ref/hyp, tổng đúng nguyên văn, và thời gian
  createStream(hotwords) — chi phí MỖI phiên nghe. Cuối cùng là bảng ✓/✗ theo câu để so cột.

⚠ Mức bằng chứng: giọng TTS macOS ≠ giọng thật + mic 4 kênh trên xe (CLAUDE.md §2). Số ở đây nói về
  model + hotword, không nói về độ chính xác trên xe.
"""
from __future__ import annotations  # `str | None` chạy được cả trên python 3.9 (macOS hệ thống)

import argparse
import os
import re
import sys
import time
import unicodedata
import wave


def norm(s: str) -> str:
    return re.sub(r"[^\w\s]", "", unicodedata.normalize("NFC", s).lower()).strip()


def read_wave(path: str):
    import numpy as np
    with wave.open(path, "rb") as f:
        if f.getframerate() != 16000 or f.getnchannels() != 1 or f.getsampwidth() != 2:
            sys.exit(f"{path}: cần PCM16 · mono · 16 kHz (voice-wavgen.sh sinh đúng khuôn)")
        data = f.readframes(f.getnframes())
    return np.frombuffer(data, dtype=np.int16).astype(np.float32) / 32768.0


def make_recognizer(model_dir: str, hotwords_file: str | None, score: float):
    import sherpa_onnx
    kw = dict(
        tokens=os.path.join(model_dir, "tokens.txt"),
        encoder=os.path.join(model_dir, "encoder.onnx"),
        decoder=os.path.join(model_dir, "decoder.onnx"),
        joiner=os.path.join(model_dir, "joiner.onnx"),
        num_threads=2, sample_rate=16000, feature_dim=80,
        decoding_method="modified_beam_search", max_active_paths=4, provider="cpu", debug=False,
    )
    if hotwords_file:
        kw.update(hotwords_file=hotwords_file, hotwords_score=score, modeling_unit="bpe",
                  bpe_vocab=os.path.join(model_dir, "bpe_vocab.txt"))
    return sherpa_onnx.OfflineRecognizer.from_transducer(**kw)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", required=True)
    ap.add_argument("--wav", default="/tmp/kachi-voice-wav")
    ap.add_argument("--score", type=float, default=3.0)
    ap.add_argument("sets", nargs="+", help="`none` hoặc đường dẫn tệp hotword (HOA có dấu, mỗi dòng một cụm)")
    a = ap.parse_args()

    cases = os.path.join(a.wav, "cases.tsv")
    if not os.path.isfile(cases):
        sys.exit(f"thiếu {cases} — chạy scripts/emulator/voice-wavgen.sh {a.wav} trước")
    ref = dict(l.rstrip("\n").split("\t", 1) for l in open(cases, encoding="utf-8") if "\t" in l)
    wavs = {k: read_wave(os.path.join(a.wav, f"{k}.wav")) for k in sorted(ref)}

    table: dict[tuple[str, str], bool] = {}
    names: list[str] = []
    for s in a.sets:
        path = None if s == "none" else s
        name = "none" if path is None else os.path.basename(path)
        names.append(name)
        n = sum(1 for l in open(path, encoding="utf-8") if l.strip()) if path else 0
        rec = make_recognizer(a.model, path, a.score)
        if path:
            text = open(path, encoding="utf-8").read()
            ts = []
            for _ in range(5):
                t0 = time.perf_counter(); rec.create_stream(text); ts.append((time.perf_counter() - t0) * 1000)
            print(f"### {name}: createStream(hotwords {n} dòng) median {sorted(ts)[2]:.1f} ms")
        ok = 0
        for wid, samples in wavs.items():
            st = rec.create_stream()
            st.accept_waveform(16000, samples)
            rec.decode_stream(st)
            hyp = st.result.text.strip()
            hit = norm(hyp) == norm(ref[wid])
            ok += hit
            table[(name, wid)] = hit
            print(f"[{name:28s} n={n:4d}] {wid} {'✓' if hit else '✗'} ref={ref[wid]!r} hyp={hyp!r}", flush=True)
        print(f"### {name} ({n} dòng): đúng nguyên văn {ok}/{len(wavs)}", flush=True)

    print("\n=== BẢNG TÓM TẮT (✓ = nghe đúng nguyên văn) ===")
    print("wav  | " + " | ".join(names))
    for wid in sorted(ref):
        print(f"{wid:4s} | " + " | ".join(("✓" if table[(n, wid)] else "✗").center(len(n)) for n in names))
    return 0


if __name__ == "__main__":
    sys.exit(main())
