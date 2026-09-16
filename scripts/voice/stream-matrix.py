#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Đo bộ nhận dạng **STREAMING** (`sherpa_onnx.OnlineRecognizer`) trên HOST, so thẳng với bộ **OFFLINE** đang
ship (`zipformer-vi-2025-04-20`) bằng đúng corpus + đúng phép chuẩn hoá của `mishear-table.py`.

## Vì sao có tệp này
[ĐO xe 2026-09-16] Đường offline hiện tại chờ hết trần 8.4 s rồi mới giải mã ~2 s ⇒ 5–6 s trễ sau khi người
nói xong. Bộ streaming *"nghe đến đâu xử đến đó"* + **endpoint tự động** của sherpa về lý thuyết cắt gần hết
khoảng chờ đó. Tệp này trả lời ba câu, bằng số, KHÔNG cần xe:

 1. **Nghe có đúng bằng không?** — cùng corpus, cùng `norm()` (`vi_text.py`) ⇒ số ghép thẳng được vào
    `docs/diagnostics/voice-mishear-2026-09-16.md`.
 2. **Endpoint có sớm hơn trần 8.4 s không, và có cắt ngang câu không?** — với mỗi WAV ghi lại
    *thời điểm audio* endpoint nổ so với *điểm hết tiếng* đo bằng RMS. Âm ⇒ cắt giữa câu (hỏng),
    dương nhỏ ⇒ đúng thứ cần.
 3. **Có chạy nổi trên TRINKET không?** — RTF + RSS đỉnh (đo ngoài bằng `/usr/bin/time -l`), cỡ tệp.

## Mô phỏng thời gian thực
Audio được nạp theo **khối 320 ms** (`--chunk-ms`) đúng như `VoiceCapture` đọc `AudioRecord`, và sau mỗi khối
gọi `decode_stream` cho tới khi `is_ready()` hết. Không `sleep` — ta đo *chi phí tính toán*, không mô phỏng
đồng hồ treo tường; RTF < 1 nghĩa là trên máy này nó theo kịp luồng mic.

⚠ Mức bằng chứng: đây là **CPU host (Apple Silicon)**, KHÔNG phải Qualcomm TRINKET của xe. Số RTF/độ trễ ở
đây chỉ dùng để **loại trừ** (RTF host đã > 1 thì trên xe chắc chắn hỏng), không dùng để hứa hẹn.
Giọng TTS ≠ giọng thật + mic 4 kênh trên xe (CLAUDE.md §2).

## Đệm im lặng — vì sao phải có, và vì sao mặc định là `zero`
WAV của corpus cắt sát tiếng nói: không có khoảng lặng đuôi thì endpoint **không bao giờ** nổ, mà bộ mã hoá
streaming cũng chưa kịp nhả mấy token cuối (đã [ĐO]: `"bật đèn đọc"` ra thành `"bật đèn"`). Trên xe thì mic
vẫn chạy nên khoảng đó luôn tồn tại. Nên ta nối thêm `--lead-ms` / `--tail-ms`.

Ba kiểu đệm, và [ĐO host 2026-09-16] nói thẳng kiểu nào dùng được:
 • `zero` (**mặc định**) — im lặng tuyệt đối. Không giống mic thật, nhưng là kiểu **duy nhất** chạy được với
   corpus này: 13/25 ở `lead 300` (so với `room` 0/25).
 • `room` — lát lại đoạn 100 ms *êm nhất của chính WAV*. Nghe có vẻ đúng hơn, nhưng corpus **không có** đoạn
   im nào (`speech_bounds` trả về `start = 0.00` ở mọi WAV) ⇒ nó lát lại một mẩu **tiếng nói**, và mô hình đa
   ngữ đọc mẩu đó thành `那么` · `こん` ⇒ **0/25**. Chỉ dùng khi WAV có khoảng lặng thật ở đầu.
 • `noise` — nhiễu trắng biên độ nhỏ. [ĐO] kém `zero`.

⚠ Và chính chỗ này đẻ ra phát hiện lớn nhất của phiên: đuôi im lặng **cũng giết** mô hình offline đang ship —
22/25 (đuôi 0 s) → 15/25 (0.75 s) → **6/25 (4 s)**. Xem `docs/diagnostics/voice-stream-eval-2026-09-16.md` §6.

Dùng:
  /tmp/sherpa-venv/bin/python scripts/voice/stream-matrix.py --model <DIR_STREAM> \\
      --wav /tmp/kachi-voice-wav [--corpus /tmp/kachi-voice-corpus] \\
      --hotwords none core/build/hotwords/hotwords-phrases.txt --lower-hotwords \\
      --dump /tmp/out.tsv [--limit 600] [--summary-only]

  `--model` là thư mục có encoder*.onnx · decoder*.onnx · joiner*.onnx · tokens.txt (+ `bpe_vocab.txt` nếu
  dùng hotword; sinh từ `bpe.model` bằng `--make-bpe-vocab`).
"""
from __future__ import annotations

import argparse
import collections
import glob
import os
import random
import sys
import time
import wave

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
from vi_text import norm  # noqa: E402  — CÙNG hàm với mishear-table.py, để số ghép được


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


# ── bộ nhận dạng ────────────────────────────────────────────────────────────────────────────────────

def find(model_dir: str, stem: str) -> str:
    hits = sorted(glob.glob(os.path.join(model_dir, f"{stem}*.onnx")))
    if not hits:
        sys.exit(f"{model_dir}: không thấy {stem}*.onnx")
    return hits[0]


def make_offline_recognizer(model_dir: str, hotwords_file: str | None, score: float, a):
    """Bộ OFFLINE đang ship — dùng cho `--mode offline-vad` (phương án C). Cùng tham số `VoiceRecognizer.kt`."""
    import sherpa_onnx
    kw = dict(
        tokens=os.path.join(model_dir, "tokens.txt"),
        encoder=find(model_dir, "encoder"), decoder=find(model_dir, "decoder"), joiner=find(model_dir, "joiner"),
        num_threads=a.threads, sample_rate=16000, feature_dim=80,
        decoding_method=a.decoding, max_active_paths=4, provider="cpu", debug=False,
    )
    if hotwords_file:
        kw.update(hotwords_file=hotwords_file, hotwords_score=score, modeling_unit="bpe",
                  bpe_vocab=os.path.join(model_dir, "bpe_vocab.txt"))
    return sherpa_onnx.OfflineRecognizer.from_transducer(**kw)


def make_vad(a):
    """Silero VAD của sherpa (~0.6 MB) — bộ ngắt câu *thay cho* endpointer RMS của `VoiceCapture`."""
    import sherpa_onnx
    cfg = sherpa_onnx.VadModelConfig()
    cfg.silero_vad.model = a.vad_model
    cfg.silero_vad.threshold = a.vad_threshold
    cfg.silero_vad.min_silence_duration = a.vad_min_silence
    cfg.silero_vad.min_speech_duration = 0.10
    cfg.silero_vad.max_speech_duration = 12.0
    cfg.sample_rate = 16000
    cfg.num_threads = 1
    cfg.provider = "cpu"
    return sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=30)


def make_recognizer(model_dir: str, hotwords_file: str | None, score: float, a):
    import sherpa_onnx
    kw = dict(
        tokens=os.path.join(model_dir, "tokens.txt"),
        encoder=find(model_dir, "encoder"), decoder=find(model_dir, "decoder"), joiner=find(model_dir, "joiner"),
        num_threads=a.threads, sample_rate=16000, feature_dim=80,
        decoding_method=a.decoding, max_active_paths=4, provider="cpu", debug=False,
        enable_endpoint_detection=True,
        rule1_min_trailing_silence=a.rule1, rule2_min_trailing_silence=a.rule2,
        rule3_min_utterance_length=a.rule3,
    )
    if hotwords_file:
        kw.update(hotwords_file=hotwords_file, hotwords_score=score, modeling_unit="bpe",
                  bpe_vocab=os.path.join(model_dir, "bpe_vocab.txt"))
    return sherpa_onnx.OnlineRecognizer.from_transducer(**kw)


def prep_hotwords(path: str, lower: bool, model_dir: str, out_dir: str) -> tuple[str, int, int]:
    """Viết lại tệp hotword cho BPE của mô hình đang đo. Trả (đường dẫn, số dòng, số dòng RỤNG).

    "Rụng" = dòng mà bộ BPE phải bẻ thành **toàn mảnh một ký tự** (không có mảnh nào ≥ 2 ký tự chữ).
    Dòng như vậy vẫn mã hoá được nhưng nó bias một đường token mà mô hình gần như không bao giờ đi —
    đúng cái bẫy mà `mishear-table.tone_audit` đã bắt được ở mô hình offline.
    """
    import sentencepiece as spm
    bpe = os.path.join(model_dir, "bpe.model")
    lines = [l.strip() for l in open(path, encoding="utf-8") if l.strip()]
    if lower:
        lines = [l.lower() for l in lines]
    dropped = 0
    if os.path.isfile(bpe):
        sp = spm.SentencePieceProcessor(model_file=bpe)
        for l in lines:
            pieces = [p.lstrip("▁") for p in sp.encode(l, out_type=str)]
            if not any(len(p) >= 2 for p in pieces):
                dropped += 1
    out = os.path.join(out_dir, os.path.basename(path) + (".lower" if lower else "") + ".txt")
    open(out, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    return out, len(lines), dropped


# ── nạp danh mục ────────────────────────────────────────────────────────────────────────────────────

def load_items(corpus: str | None, wav_dir: str | None):
    items = []
    if corpus and os.path.isfile(os.path.join(corpus, "manifest.tsv")):
        for line in open(os.path.join(corpus, "manifest.tsv"), encoding="utf-8"):
            if line.startswith("#") or "\t" not in line:
                continue
            w, uid, rid, ikind, region, style, voice, rate, tier, ref = line.rstrip("\n").split("\t")
            items.append(dict(path=os.path.join(corpus, w), uid=uid, id=rid, kind=ikind, region=region,
                              style=style, voice=voice, rate=rate, tier=tier, ref=ref, src="corpus"))
    if wav_dir and os.path.isfile(os.path.join(wav_dir, "cases.tsv")):
        for line in open(os.path.join(wav_dir, "cases.tsv"), encoding="utf-8"):
            if "\t" in line:
                wid, ref = line.rstrip("\n").split("\t", 1)
                items.append(dict(path=os.path.join(wav_dir, f"{wid}.wav"), uid=wid, id=f"wav_{wid}",
                                  kind="wav25", region="chung", style="ngan", voice="linh", rate="180",
                                  tier="W", ref=ref, src="wav25"))
    return items


def stratified(items, limit: int, seed: int = 7):
    """Lấy mẫu giữ nguyên tỉ lệ (intent_kind × giọng × tốc độ). 25 WAV cũ luôn giữ đủ."""
    keep = [it for it in items if it["src"] != "corpus"]
    pool = [it for it in items if it["src"] == "corpus"]
    if limit <= 0 or len(pool) <= limit:
        return keep + pool
    buckets = collections.defaultdict(list)
    for it in pool:
        buckets[(it["kind"], it["voice"], it["rate"])].append(it)
    rnd = random.Random(seed)
    for v in buckets.values():
        rnd.shuffle(v)
    out, keys = [], sorted(buckets)
    i = 0
    while len(out) < limit:                       # round-robin ⇒ mỗi ô ít nhất một mẫu trước khi lấy thêm
        k = keys[i % len(keys)]
        if buckets[k]:
            out.append(buckets[k].pop())
        i += 1
        if all(not buckets[k] for k in keys):
            break
    return keep + out


# ── vòng đo ─────────────────────────────────────────────────────────────────────────────────────────

def run_one(rec, x, a):
    """Nạp `x` theo khối `chunk-ms`; trả dict số đo cho MỘT WAV."""
    import numpy as np
    sr = 16000
    lead = room_tone(x, int(a.lead_ms * sr / 1000), a.pad)
    tail = room_tone(x, int(a.tail_ms * sr / 1000), a.pad)
    sp0, sp1 = speech_bounds(x)
    sp1 += a.lead_ms / 1000.0                     # quy về trục thời gian của luồng đã nối đệm đầu
    audio = np.concatenate([lead, x, tail])
    chunk = int(a.chunk_ms * sr / 1000)

    st = rec.create_stream()
    compute = 0.0
    last_chunk_ms = 0.0
    ep_t = None          # thời điểm audio (giây, trục luồng) endpoint nổ
    ep_text = None
    ep_compute = 0.0
    for off in range(0, len(audio), chunk):
        t0 = time.perf_counter()
        st.accept_waveform(sr, audio[off:off + chunk].copy())
        while rec.is_ready(st):
            rec.decode_stream(st)
        dt = time.perf_counter() - t0
        compute += dt
        last_chunk_ms = dt * 1000
        if ep_t is None and rec.is_endpoint(st):
            txt = rec.get_result(st).strip()
            if txt:                               # endpoint trên im lặng thuần (chưa có chữ) thì bỏ qua
                ep_t = min(off + chunk, len(audio)) / sr
                ep_text = txt
                ep_compute = compute
    t0 = time.perf_counter()
    st.input_finished()
    while rec.is_ready(st):
        rec.decode_stream(st)
    compute += time.perf_counter() - t0
    dur = len(audio) / sr
    return dict(final=rec.get_result(st).strip(), ep_text=ep_text, ep_t=ep_t,
                speech_end=sp1, dur=dur, wav_dur=len(x) / sr, compute=compute,
                rtf=compute / dur, last_chunk_ms=last_chunk_ms,
                ep_lag=None if ep_t is None else ep_t - sp1,
                ep_compute=ep_compute)


def run_one_vad(rec, vad, x, a):
    """Phương án C: mô hình OFFLINE đang ship + Silero VAD làm bộ ngắt câu (thay endpointer RMS).

    Cùng mô phỏng khối 320 ms. VAD đóng đoạn ⇒ đó là "endpoint"; chỉ phần audio VAD giữ lại mới đưa vào
    bộ giải mã (khác đường hiện tại: hiện tại nạp **cả** cửa sổ tới trần 8.4 s, kể cả im lặng).
    """
    import numpy as np
    sr = 16000
    lead = room_tone(x, int(a.lead_ms * sr / 1000), a.pad)
    tail = room_tone(x, int(a.tail_ms * sr / 1000), a.pad)
    sp0, sp1 = speech_bounds(x)
    sp1 += a.lead_ms / 1000.0
    audio = np.concatenate([lead, x, tail])
    chunk = int(a.chunk_ms * sr / 1000)

    vad.reset()
    compute = 0.0
    ep_t, seg = None, None
    for off in range(0, len(audio), chunk):
        t0 = time.perf_counter()
        vad.accept_waveform(audio[off:off + chunk].copy())
        dt = time.perf_counter() - t0
        compute += dt
        if ep_t is None and not vad.empty():
            ep_t = min(off + chunk, len(audio)) / sr
            # Ba cách nạp, KHÁC NHAU ở chỗ cắt — và [ĐO] chỗ cắt quyết định độ chính xác nhiều hơn cả
            # mô hình (đuôi im lặng tuyệt đối 4 s kéo 22/25 → 6/25 trên chính mô hình đang ship):
            #  `head`    — từ đầu cửa sổ tới HẾT đoạn VAD + `--vad-margin`. Giữ trọn phần đầu câu (VAD mở
            #              đoạn muộn) mà vẫn cắt sát đuôi. Đây là cách nên dùng.
            #  `window`  — từ đầu tới lúc VAD *chốt*, tức dính thêm cả `min_silence`.
            #  `segment` — chỉ đoạn VAD giữ; [ĐO] nuốt mất từ ĐẦU câu.
            if a.vad_feed == "segment":
                seg = np.array(vad.front.samples, dtype="float32")
            elif a.vad_feed == "head":
                end = vad.front.start + len(vad.front.samples) + int(a.vad_margin * sr)
                seg = audio[:max(1, min(len(audio), end))].copy()
            else:
                seg = audio[:int(ep_t * sr)].copy()
            vad.pop()
    if seg is None:                                 # VAD không đóng đoạn nào ⇒ lấy nguyên cửa sổ (như trần)
        seg = audio
    t0 = time.perf_counter()
    st = rec.create_stream()
    st.accept_waveform(sr, seg)
    rec.decode_stream(st)
    dec = time.perf_counter() - t0
    compute += dec
    dur = len(audio) / sr
    txt = st.result.text.strip()
    return dict(final=txt, ep_text=txt, ep_t=ep_t, speech_end=sp1, dur=dur, wav_dur=len(x) / sr,
                compute=compute, rtf=compute / dur, last_chunk_ms=dec * 1000,
                ep_lag=None if ep_t is None else ep_t - sp1, ep_compute=compute)


def pct(ok: int, n: int) -> str:
    return f"{100.0*ok/n:.1f}%" if n else "—"


def table(rows, head):
    out = ["| " + " | ".join(head) + " |", "|" + "|".join(["---"] * len(head)) + "|"]
    out += ["| " + " | ".join(str(c) for c in r) + " |" for r in rows]
    return "\n".join(out)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", required=True)
    ap.add_argument("--mode", default="stream", choices=["stream", "offline-vad"],
                    help="stream = OnlineRecognizer; offline-vad = mô hình OFFLINE + Silero VAD (phương án C)")
    ap.add_argument("--vad-model", default="", help="silero_vad.onnx (bắt buộc với --mode offline-vad)")
    ap.add_argument("--vad-threshold", type=float, default=0.5)
    ap.add_argument("--vad-min-silence", type=float, default=0.15,
                    help="[ĐO] 0.15 s là điểm đã chốt bằng lưới: 21/25 · 0 ca cắt giữa câu · endpoint p50 660 ms")
    ap.add_argument("--vad-margin", type=float, default=0.0,
                    help="chỉ với --vad-feed head: chừa thêm bao nhiêu giây sau đuôi đoạn VAD")
    ap.add_argument("--vad-feed", default="head", choices=["head", "window", "segment"],
                    help="window = nạp cả cửa sổ mic tới lúc VAD chốt (VAD chỉ làm đồng hồ); "
                         "segment = chỉ nạp đoạn VAD giữ lại")
    ap.add_argument("--corpus", default="")
    ap.add_argument("--wav", default="/tmp/kachi-voice-wav")
    ap.add_argument("--hotwords", nargs="+", default=["none"])
    ap.add_argument("--lower-hotwords", action="store_true",
                    help="hạ chữ thường tệp hotword — BPE đa ngữ chia cụm tiếng Việt CHỮ THƯỜNG tốt hơn HOA")
    ap.add_argument("--score", type=float, default=3.0)
    ap.add_argument("--decoding", default="modified_beam_search")
    ap.add_argument("--threads", type=int, default=2)
    ap.add_argument("--chunk-ms", type=int, default=320)
    ap.add_argument("--lead-ms", type=int, default=300,
                    help="[ĐO] 300 ms là tốt nhất cho --mode stream; với --mode offline-vad thì dùng 0")
    ap.add_argument("--tail-ms", type=int, default=2000)
    ap.add_argument("--pad", default="zero", choices=["zero", "room", "noise"],
                    help="kiểu đệm im lặng — xem phần 'Đệm im lặng' ở đầu tệp; `room` chỉ đúng khi WAV có "
                         "khoảng lặng thật")
    ap.add_argument("--rule1", type=float, default=2.4, help="im lặng đuôi khi CHƯA có chữ nào")
    ap.add_argument("--rule2", type=float, default=0.8, help="im lặng đuôi khi ĐÃ có chữ ⇒ luật quyết định")
    ap.add_argument("--rule3", type=float, default=20.0)
    ap.add_argument("--limit", type=int, default=0, help="lấy mẫu phân tầng ≤ N WAV corpus (0 = tất cả)")
    ap.add_argument("--dump", default="")
    ap.add_argument("--summary-only", action="store_true")
    ap.add_argument("--make-bpe-vocab", action="store_true",
                    help="sinh bpe_vocab.txt từ bpe.model rồi thoát (cần cho hotword)")
    a = ap.parse_args()

    if a.make_bpe_vocab:
        import sentencepiece as spm
        sp = spm.SentencePieceProcessor(model_file=os.path.join(a.model, "bpe.model"))
        out = os.path.join(a.model, "bpe_vocab.txt")
        with open(out, "w", encoding="utf-8") as f:
            for i in range(sp.get_piece_size()):
                f.write(f"{sp.id_to_piece(i)} {sp.get_score(i)}\n")
        print(f"== ghi {out} ({sp.get_piece_size()} mảnh)")
        return 0

    items = stratified(load_items(a.corpus or None, a.wav), a.limit)
    print(f"== {len(items)} WAV · {len(a.hotwords)} cấu hình · chunk {a.chunk_ms} ms · pad {a.pad} "
          f"lead {a.lead_ms} tail {a.tail_ms} · rule2 {a.rule2}s", flush=True)

    waves = {}
    for it in items:
        if it["path"] not in waves:
            waves[it["path"]] = read_wave(it["path"])

    results: dict[str, list[dict]] = {}
    hot_info: dict[str, tuple[int, int]] = {}
    for h in a.hotwords:
        name = "none" if h == "none" else os.path.basename(h) + (".lower" if a.lower_hotwords else "")
        path = None
        if h != "none":
            path, n, drop = prep_hotwords(h, a.lower_hotwords, a.model, os.path.dirname(a.dump) or "/tmp")
            hot_info[name] = (n, drop)
            print(f"### {name}: {n} dòng · {drop} dòng BPE bẻ vụn thành ký tự lẻ", flush=True)
        if a.mode == "offline-vad":
            if not a.vad_model:
                sys.exit("--mode offline-vad cần --vad-model <silero_vad.onnx>")
            rec, vad = make_offline_recognizer(a.model, path, a.score, a), make_vad(a)
        else:
            rec, vad = make_recognizer(a.model, path, a.score, a), None
        t0 = time.time()
        rows = []
        for k, it in enumerate(items):
            rows.append(run_one_vad(rec, vad, waves[it["path"]], a) if vad
                        else run_one(rec, waves[it["path"]], a))
            if k and k % 200 == 0:
                print(f"  [{name}] {k}/{len(items)} … {time.time()-t0:.0f}s", flush=True)
        print(f"  [{name}] xong {len(items)} trong {time.time()-t0:.0f}s", flush=True)
        results[name] = rows

    names = list(results)

    # ── tổng ────────────────────────────────────────────────────────────────────────────────────────
    overall = []
    for nm in names:
        rows = results[nm]
        okf = sum(1 for it, r in zip(items, rows) if norm(r["final"]) == norm(it["ref"]))
        oke = sum(1 for it, r in zip(items, rows)
                  if norm(r["ep_text"] or r["final"]) == norm(it["ref"]))
        okw = sum(1 for it, r in zip(items, rows)
                  if it["src"] == "wav25" and norm(r["ep_text"] or r["final"]) == norm(it["ref"]))
        nw = sum(1 for it in items if it["src"] == "wav25")
        rtf = sum(r["compute"] for r in rows) / sum(r["dur"] for r in rows)
        overall.append([nm, len(items), f"{okf} ({pct(okf, len(items))})", f"{oke} ({pct(oke, len(items))})",
                        f"{okw}/{nw}", f"{rtf:.3f}"])
    print(table(overall, ["cấu hình", "WAV", "đúng (cuối luồng)", "đúng (tại endpoint)", "25 WAV cũ", "RTF"]))

    # ── endpoint ────────────────────────────────────────────────────────────────────────────────────
    for nm in names:
        rows = results[nm]
        lags = [r["ep_lag"] for r in rows if r["ep_lag"] is not None]
        never = sum(1 for r in rows if r["ep_t"] is None)
        midcut = sum(1 for r in rows if r["ep_lag"] is not None and r["ep_lag"] < 0)
        if lags:
            lags_s = sorted(lags)
            q = lambda p: lags_s[min(len(lags_s) - 1, int(p * len(lags_s)))]  # noqa: E731
            print(f"### endpoint [{nm}]: nổ {len(lags)}/{len(rows)} · không nổ {never} · cắt sớm {midcut}"
                  f" · trễ sau khi hết tiếng p50 {q(.5)*1000:.0f} ms p90 {q(.9)*1000:.0f} ms"
                  f" max {lags_s[-1]*1000:.0f} ms min {lags_s[0]*1000:.0f} ms")
        lat = [(r["ep_lag"] or 0) * 1000 + r["last_chunk_ms"] for r in rows if r["ep_lag"] is not None]
        if lat:
            lat.sort()
            print(f"###   trễ THẤY ĐƯỢC (chờ audio + tính khối cuối): p50 {lat[len(lat)//2]:.0f} ms "
                  f"p90 {lat[int(.9*len(lat))]:.0f} ms")

    if a.dump:
        with open(a.dump, "w", encoding="utf-8") as f:
            f.write("cfg\tuid\tid\tkind\tvoice\trate\tref\tfinal\tep_text\tep_t\tspeech_end\tep_lag\t"
                    "rtf\tlast_chunk_ms\twav_dur\n")
            for nm in names:
                for it, r in zip(items, results[nm]):
                    f.write("\t".join(str(v) for v in [
                        nm, it["uid"], it["id"], it["kind"], it["voice"], it["rate"], it["ref"],
                        r["final"], r["ep_text"] or "", "" if r["ep_t"] is None else f"{r['ep_t']:.2f}",
                        f"{r['speech_end']:.2f}", "" if r["ep_lag"] is None else f"{r['ep_lag']:.2f}",
                        f"{r['rtf']:.3f}", f"{r['last_chunk_ms']:.1f}", f"{r['wav_dur']:.2f}"]) + "\n")
        print(f"== ghi {a.dump}")

    if a.summary_only:
        return 0

    # ── chia nhóm giống mishear-table.py để số ghép được ────────────────────────────────────────────
    def group(key, nm):
        g = collections.defaultdict(lambda: [0, 0])
        for it, r in zip(items, results[nm]):
            if it["src"] != "corpus":
                continue
            k = key(it)
            g[k][1] += 1
            g[k][0] += norm(r["ep_text"] or r["final"]) == norm(it["ref"])
        return g

    for label, key, sortk in [("giọng · tốc độ", lambda it: f"{it['voice']} {it['rate']}",
                               lambda s: (s.split()[0], float(s.split()[1]))),
                              ("intent_kind", lambda it: it["kind"], lambda s: s),
                              ("region", lambda it: it["region"], lambda s: s),
                              ("style", lambda it: it["style"], lambda s: s)]:
        g = {nm: group(key, nm) for nm in names}
        print(f"\n### theo {label}")
        print(table([[k] + [pct(*reversed(g[nm][k])) if False else pct(g[nm][k][0], g[nm][k][1]) for nm in names]
                     + [g[names[0]][k][1]] for k in sorted(g[names[0]], key=sortk)],
                    [label] + names + ["số WAV"]))

    primary = names[-1]
    mis = collections.Counter()
    for it, r in zip(items, results[primary]):
        a_, b_ = norm(it["ref"]), norm(r["ep_text"] or r["final"])
        if a_ != b_:
            mis[(a_, b_)] += 1
    print(f"\n### 30 cặp NGHE NHẦM hay gặp nhất [{primary}]")
    print(table([[r, h, c] for (r, h), c in mis.most_common(30)], ["ref", "hyp", "lần"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
