#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ma trận hotword × WAV trên HOST (không cần máy ảo/xe) — công cụ đã chốt thiết kế `kachi-voice-hotword-phrases`.

Vì sao trên host: cùng sherpa-onnx 1.13.8 + cùng model `zipformer-vi-int8-2025-04-20` + cùng tham số như
`VoiceRecognizer.kt` / `SherpaModelCatalog.kt` (modified_beam_search · beam 4 · score 3.0 · bpe) ⇒ một tệp hotword
đổi là biết ngay nghe khác đi thế nào, trước khi tốn một vòng build + E2E máy ảo. [ĐO] 2026-09-16: 5 ma trận (31
cấu hình tệp, mỗi cấu hình 25 WAV — log `matrix2..6_stdout.log`) chạy bằng công cụ này là thứ bác giả thuyết "loãng" và chốt luật "chỉ cụm, không từ rời, không tiền tố" (doc
`docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §6.3 phần [ĐO host 2026-09-16]).

## 2.93 (VOICE-ALT-LABEL-HOTWORD) — đi ĐÚNG đường của app, đọc nhiều thư mục WAV, chấm theo tầng chữ
 • Hotword nạp theo PHIÊN như `VoiceRecognizer.decode`: recognizer dựng KHÔNG `hotwords_file` (chỉ `modeling_unit=bpe` +
   `bpe_vocab` + `hotwords_score`), mỗi WAV một `create_stream(<nội dung tệp>)` (đúng `recognizer.createStream(hotwords)`).
   Bản trước dựng recognizer theo tệp rồi giải mã bằng `create_stream()` — cùng đồ thị nên số ĐÚNG/SAI cũ vẫn dùng được
   [ĐO source v1.13.8 `csrc/offline-recognizer-transducer-impl.h:163-198` + `:262-281`: hai đường cùng `EncodeHotwords`
   → `ContextGraph`], nhưng phép đo giờ `create_stream(text)` khi ấy cộng thêm CẢ dãy của tệp mặc định (`:178`
   `current.insert(…, hotwords_…)`) ⇒ số ms cũ bị thổi lên.
 • Tên tệp mô hình theo `SherpaModelCatalog` (`encoder.int8.onnx` · `decoder.onnx` · `joiner.int8.onnx`), lùi về tên cũ.
 • `--wav` lặp được (mỗi thư mục một `cases.tsv`); tổng theo TỪNG thư mục ⇒ "câu cũ có tụt không" tách khỏi "câu mới".
 • Ba mức chấm: `✓` đúng chữ (`vi_text.norm`: dấu thanh kiểu mới + số thành chữ) · `≈` chỉ khớp sau BỎ DẤU (đúng phép so
   của tầng chữ `VoiceLexicon` — bộ phân tích vẫn hiểu) · `✗`. Cột "nguyên văn" kiểu cũ in kèm để so với log 09-16.
 • `--ref`: liệt kê từng ca TỤT / LÊN của mỗi tệp so với cột tham chiếu — luật R5: không ca nào được tụt.
 • `--trim vad` (MẶC ĐỊNH từ 2.93): cắt mỗi WAV ĐÚNG như app trước khi giải mã — Silero (asset của app, tham số của
   `VoiceVadTrim`), nạp theo khối 200 ms, cắt tới hết đoạn tiếng cuối (margin 0), lượt không có tiếng ⇒ không giải mã.
   [ĐO host 2026-10-07] bản không cắt báo `w26` *"bật đèn đọc"* + 2 s im lặng ⇒ *"…ĐỌC SÁCH"* — một lỗi app KHÔNG có:
   qua phép cắt của app (và [ĐO máy ảo] T2) ca ấy ra đúng. `--trim none` giữ để so với log cũ / đo riêng mô hình.

Chuẩn bị (một lần):
  python3 -m venv <venv> && <venv>/bin/pip install sherpa-onnx==1.13.8 numpy
  MODEL_DIR = 4 tệp tải như `VoiceModelStore` (URL + sha256 ở `SherpaModelCatalog.ZIPFORMER_VI_INT8`)
             + bpe_vocab.txt (chép từ app/src/main/assets/voice/zipformer-vi-2025-04-20.bpe_vocab.txt)
  WAV: scripts/emulator/voice-wavgen.sh /tmp/kachi-voice-wav   (24 câu + 3 đuôi im lặng `say -v Linh`, kèm cases.tsv)
       scripts/voice/label-wavgen.sh /tmp/kachi-voice-wav-labels (28 câu gọi app bằng nhãn tiếng Việt + câu tự do)
  Tệp hotword THẬT của bản build: chạy `./gradlew :core:test --tests '*SherpaBiasingCoverageTest*'` ⇒
             core/build/hotwords/hotwords-phrases.txt (dump từ chính SherpaBiasing.hotwordsFile()) + các biến thể cạnh nó.

Dùng:
  <venv>/bin/python -I scripts/voice/hotword-matrix.py --model <MODEL_DIR> --wav /tmp/kachi-voice-wav \
        [--wav /tmp/kachi-voice-wav-labels] [--score 3.0] [--ref static] none static=<tệp tĩnh> [tên=<tệp> ...]
  `none` = chạy không hotword (mốc). Mỗi tệp: in từng câu ref/hyp, tổng theo thư mục WAV, và thời gian
  createStream(hotwords) (trung vị · p90 qua `--repeat` lượt) — chi phí MỖI phiên nghe. Cuối: bảng ✓/≈/✗ + ca tụt/lên.

⚠ Mức bằng chứng: giọng TTS macOS ≠ giọng thật + mic 4 kênh trên xe (CLAUDE.md §2). Số ở đây nói về
  model + hotword, không nói về độ chính xác trên xe.
"""
from __future__ import annotations  # `str | None` chạy được cả trên python 3.9 (macOS hệ thống)

import argparse
import hashlib
import os
import re
import sys
import time
import unicodedata
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)   # `python -I` không tự thêm thư mục script — nạp `vi_text` tường minh như các công cụ khác

import vi_text  # noqa: E402

MARK_RANK = {"✓": 2, "≈": 1, "✗": 0}


def norm(s: str) -> str:
    """Phép so "nguyên văn" kiểu cũ (log 09-16): NFC · thường · bỏ dấu câu. Giữ để số so được với lịch sử."""
    return re.sub(r"[^\w\s]", "", unicodedata.normalize("NFC", s).lower()).strip()


def mark(ref: str, hyp: str) -> str:
    if vi_text.norm(hyp) == vi_text.norm(ref):
        return "✓"
    return "≈" if vi_text.tokens(hyp) == vi_text.tokens(ref) else "✗"


def read_wave(path: str):
    import numpy as np
    with wave.open(path, "rb") as f:
        if f.getframerate() != 16000 or f.getnchannels() != 1 or f.getsampwidth() != 2:
            sys.exit(f"{path}: cần PCM16 · mono · 16 kHz (voice-wavgen.sh sinh đúng khuôn)")
        data = f.readframes(f.getnframes())
    return np.frombuffer(data, dtype=np.int16).astype(np.float32) / 32768.0


def model_files(model_dir: str) -> dict[str, str]:
    """Tên theo `SherpaModelCatalog` trước, tên cũ (`encoder.onnx`…) sau."""
    def pick(*names: str) -> str:
        for n in names:
            p = os.path.join(model_dir, n)
            if os.path.isfile(p):
                return p
        sys.exit(f"{model_dir}: thiếu một trong {names}")
    return dict(
        encoder=pick("encoder.int8.onnx", "encoder.onnx"),
        decoder=pick("decoder.onnx", "decoder.int8.onnx"),
        joiner=pick("joiner.int8.onnx", "joiner.onnx"),
        tokens=pick("tokens.txt"),
        bpe_vocab=pick("bpe_vocab.txt"),
    )


def sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def make_recognizer(files: dict[str, str], score: float, beam: int, threads: int):
    """Như `VoiceEngine.build`: KHÔNG `hotwords_file` — hotword đi theo phiên qua `create_stream(text)`."""
    import sherpa_onnx
    return sherpa_onnx.OfflineRecognizer.from_transducer(
        tokens=files["tokens"], encoder=files["encoder"], decoder=files["decoder"], joiner=files["joiner"],
        num_threads=threads, sample_rate=16000, feature_dim=80,
        decoding_method="modified_beam_search", max_active_paths=beam, provider="cpu", debug=False,
        hotwords_score=score, modeling_unit="bpe", bpe_vocab=files["bpe_vocab"],
    )


# ── `--trim vad`: ĐÚNG phép cắt cửa sổ của app (VoiceCapture / VoiceWavProbe → VoiceVadTrim) ──────────────────
# Giữ khớp tay với Kotlin: `VoiceCapture.CHUNK_SAMPLES` = 16 000 / 5 · `VoiceVadTrim.{THRESHOLD, MIN_SPEECH_MS,
# MIN_SILENCE_MS, WINDOW_SIZE, PRE_ROLL_MS, HEAD_SILENCE_CUT_MS}` · asset `voice/silero_vad.onnx` (= `VoiceVad.ASSET_NAME`).
APP_CHUNK = 16000 // 5
VAD_ASSET = os.path.join(os.path.dirname(os.path.dirname(HERE)), "app", "src", "main", "assets", "voice", "silero_vad.onnx")
PRE_ROLL = 300 * 16
HEAD_CUT_ABOVE = 1200 * 16


def vad_window(samples, a) -> tuple[int, int, int]:
    """`(đầu, cuối, số đoạn)` của khúc app đưa vào bộ giải mã: Silero nạp theo khối 200 ms, `flush` ở cuối; cuối = hết
    đoạn tiếng CUỐI (`headTrimSamples`, margin 0 — sherpa đã cắt hangover khỏi đoạn); đầu = chỉ cắt im lặng dẫn đầu
    > 1,2 s, giữ 0,3 s (`headStartSamples`). Không đoạn nào ⇒ `(0, 0, 0)`: lượt chính của app BỎ giải mã (VoiceSilenceGate)."""
    import sherpa_onnx
    cfg = sherpa_onnx.VadModelConfig()
    cfg.silero_vad.model = a.vad
    cfg.silero_vad.threshold = a.vad_threshold
    cfg.silero_vad.min_speech_duration = a.vad_min_speech_ms / 1000
    cfg.silero_vad.min_silence_duration = a.vad_min_silence_ms / 1000
    cfg.silero_vad.window_size = 512
    cfg.sample_rate = 16000
    cfg.num_threads = 1
    vad = sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=60)
    segs: list[tuple[int, int]] = []

    def drain():
        while not vad.empty():
            segs.append((vad.front.start, len(vad.front.samples)))
            vad.pop()
    n = len(samples)
    for at in range(0, n, APP_CHUNK):
        vad.accept_waveform(samples[at:at + APP_CHUNK])
        drain()
    vad.flush()
    drain()
    if not segs:
        return 0, 0, 0
    end = max(1, min(n, max(s + k for s, k in segs)))
    onset = min(s for s, _ in segs)
    start = max(0, onset - PRE_ROLL) if onset > HEAD_CUT_ABOVE else 0
    return start, end, len(segs)


def load_cases(dirs: list[str]):
    """[(thư mục, id, câu)] + {(thư mục, id): mẫu}. Id trùng giữa hai thư mục vẫn tách được nhờ khoá thư mục."""
    cases, wavs, tags = [], {}, set()
    for d in dirs:
        tsv = os.path.join(d, "cases.tsv")
        if not os.path.isfile(tsv):
            sys.exit(f"thiếu {tsv} — chạy voice-wavgen.sh / label-wavgen.sh vào {d} trước")
        tag = os.path.basename(os.path.normpath(d))
        # Senior review wave 2 [P3]: khoá là TÊN thư mục ⇒ hai `--wav` cùng tên (…/a/wav + …/b/wav) từng ghi đè mẫu của nhau
        # trong im lặng — số tụt/lên (luật R5) sai mà không ai thấy. Dừng hẳn thay vì đoán.
        if tag in tags:
            sys.exit(f"hai --wav cùng tên thư mục «{tag}» ({d}) — đổi tên một thư mục để khoá ca không trùng")
        tags.add(tag)
        for line in open(tsv, encoding="utf-8"):
            if "\t" not in line:
                continue
            wid, text = line.rstrip("\n").split("\t", 1)
            cases.append((tag, wid, text))
            wavs[(tag, wid)] = read_wave(os.path.join(d, f"{wid}.wav"))
    return cases, wavs


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", required=True)
    ap.add_argument("--wav", action="append", help="thư mục WAV có cases.tsv (lặp được); mặc định /tmp/kachi-voice-wav")
    ap.add_argument("--score", type=float, default=3.0, help="SherpaModelCatalog.HOTWORDS_SCORE")
    ap.add_argument("--beam", type=int, default=4, help="SherpaModelCatalog.MAX_ACTIVE_PATHS")
    ap.add_argument("--threads", type=int, default=2)
    ap.add_argument("--repeat", type=int, default=15, help="số lượt đo createStream(hotwords) mỗi tệp")
    ap.add_argument("--ref", help="tên cột tham chiếu cho danh sách tụt/lên (mặc định: tệp đầu tiên khác `none`)")
    ap.add_argument("--trim", choices=["none", "vad"], default="vad",
                    help="`vad` (mặc định) = cắt cửa sổ ĐÚNG như app (Silero, chế độ head); `none` = giải mã nguyên tệp "
                         "(chỉ đo MÔ HÌNH — so được với log 09-16, KHÔNG nói về app)")
    ap.add_argument("--vad", default=VAD_ASSET, help="silero_vad.onnx (mặc định: asset của app)")
    ap.add_argument("--vad-threshold", type=float, default=0.45, help="VoiceVadTrim.THRESHOLD")
    ap.add_argument("--vad-min-speech-ms", type=int, default=100, help="VoiceVadTrim.MIN_SPEECH_MS")
    ap.add_argument("--vad-min-silence-ms", type=int, default=600, help="VoiceVadTrim.MIN_SILENCE_MS")
    ap.add_argument("sets", nargs="+", help="`none` · <tệp> · <tên>=<tệp> (HOA có dấu, mỗi dòng một cụm)")
    a = ap.parse_args()

    import sherpa_onnx
    files = model_files(a.model)
    print(f"# sherpa-onnx {sherpa_onnx.__version__} · beam {a.beam} · score {a.score} · luồng {a.threads}")
    for k in ("encoder", "decoder", "joiner", "tokens", "bpe_vocab"):
        print(f"# {k:9s} {os.path.basename(files[k])} sha256={sha256(files[k])}")
    cases, wavs = load_cases(a.wav or ["/tmp/kachi-voice-wav"])
    tags = list(dict.fromkeys(t for t, _, _ in cases))
    rec = make_recognizer(files, a.score, a.beam, a.threads)
    if a.trim == "vad":
        print(f"# cắt cửa sổ: Silero {os.path.basename(a.vad)} sha256={sha256(a.vad)} · ngưỡng {a.vad_threshold} · "
              f"tiếng ≥ {a.vad_min_speech_ms} ms · im ≥ {a.vad_min_silence_ms} ms · khối {APP_CHUNK} mẫu")
        for key, x in wavs.items():
            s0, s1, k = vad_window(x, a)
            wavs[key] = x[s0:s1]
            if k == 0 or s0 > 0 or len(x) - s1 >= 1600:
                print(f"# cắt {key[0]}/{key[1]}: {len(x) * 1000 // 16000} ms → [{s0 * 1000 // 16000}, "
                      f"{s1 * 1000 // 16000}) ms · {k} đoạn{' — KHÔNG tiếng ⇒ app bỏ giải mã' if k == 0 else ''}")
    else:
        print("# ⚠ --trim none: giải mã NGUYÊN tệp — KHÁC app (app cắt tại hết tiếng, VoiceVadTrim); ca có đuôi im lặng nói "
              "về MÔ HÌNH, không về app")

    marks: dict[tuple[str, str, str], str] = {}
    names: list[str] = []
    for s in a.sets:
        name, path = (s.split("=", 1) if "=" in s else (s, None if s == "none" else s))
        if path is not None and name == path:
            name = os.path.basename(path)
        names.append(name)
        text = open(path, encoding="utf-8").read() if path else None
        n = sum(1 for l in text.split("\n") if l.strip()) if text else 0
        if text:
            ts = []
            for _ in range(a.repeat):
                t0 = time.perf_counter(); rec.create_stream(text); ts.append((time.perf_counter() - t0) * 1000)
            ts.sort()
            print(f"### {name}: createStream(hotwords {n} dòng) trung vị {ts[len(ts) // 2]:.1f} ms · "
                  f"p90 {ts[int(len(ts) * 0.9)]:.1f} ms ({a.repeat} lượt)", flush=True)
        per = {t: {"exact": 0, "✓": 0, "≈": 0, "n": 0} for t in tags}
        for tag, wid, ref in cases:
            x = wavs[(tag, wid)]
            if len(x) == 0:
                hyp = ""   # `--trim vad`, không đoạn tiếng nào ⇒ app không giải mã lượt này
            else:
                st = rec.create_stream(text) if text else rec.create_stream()
                st.accept_waveform(16000, x)
                rec.decode_stream(st)
                hyp = st.result.text.strip()
            m = mark(ref, hyp)
            marks[(name, tag, wid)] = m
            p = per[tag]
            p["n"] += 1
            p["exact"] += norm(hyp) == norm(ref)
            p["✓"] += m == "✓"
            p["≈"] += m != "✗"
            print(f"[{name:12s} n={n:4d}] {tag}/{wid} {m} ref={ref!r} hyp={hyp!r}", flush=True)
        for t in tags:
            p = per[t]
            print(f"### {name} ({n} dòng) · {t}: đúng chữ {p['✓']}/{p['n']} · khớp bỏ dấu {p['≈']}/{p['n']} · "
                  f"nguyên văn (kiểu cũ) {p['exact']}/{p['n']}", flush=True)

    print("\n=== BẢNG TÓM TẮT (✓ đúng chữ · ≈ chỉ khớp sau bỏ dấu — tầng chữ vẫn hiểu · ✗ sai) ===")
    print("thư mục/id | " + " | ".join(names))
    for tag, wid, _ in cases:
        print(f"{tag}/{wid} | " + " | ".join(marks[(n, tag, wid)].center(len(n)) for n in names))

    ref = a.ref or next((n for n in names if n != "none"), names[0])
    if ref not in names:
        sys.exit(f"--ref {ref}: không có trong {names}")
    print(f"\n=== TỤT / LÊN so với «{ref}» (luật R5: không ca nào được tụt) ===")
    for n in names:
        if n == ref:
            continue
        down = [f"{t}/{w} {marks[(ref, t, w)]}→{marks[(n, t, w)]}" for t, w, _ in cases
                if MARK_RANK[marks[(n, t, w)]] < MARK_RANK[marks[(ref, t, w)]]]
        up = [f"{t}/{w} {marks[(ref, t, w)]}→{marks[(n, t, w)]}" for t, w, _ in cases
              if MARK_RANK[marks[(n, t, w)]] > MARK_RANK[marks[(ref, t, w)]]]
        print(f"{n}: tụt {len(down)} {down} · lên {len(up)} {up}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
