#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HARNESS "Hey Kachi" — quét CỔNG TIẾNG NGƯỜI trên host (WAKE-INTERMITTENT-1017): câu gọi có còn TỚI được ASR không, ở mức micro
xe và SNR thấp, theo từng cặp (ngưỡng VAD, hold).

Đo ĐÚNG MỘT tầng: nhánh quyết định trước `rec.decodeAll` của `VoiceWakeAsr.decodeWindow`. Không giải mã ASR, không khớp chữ,
không ngân sách (coi như luôn đủ) — tách riêng phần cổng khỏi phần mô hình nghe. Dựng lại trên host, theo đúng mã ship:
  • khung 100 ms (1 600 mẫu) · `VoiceWakeGate` (ngưỡng max(2 × nền, 120), nền chỉ học lúc im, α 0,05) ·
  • đuôi câu `VoiceWakeController.tail` (800 ms OBSERVE rồi một FINISH) ·
  • `VoiceWakeAsr`: cửa sổ 1,6 s, nhịp 500 ms, MIN 500 ms, FINISH ≥ 200 ms tiếng mới, cổng RMS 0,010 ·
  • `VoiceWakeSpeechGate`: Silero v4 (asset `app/src/main/assets/voice/silero_vad.onnx`, `x/h/c`) trên cửa sổ 512 mẫu của MỌI
    khung engine nhận, trạng thái LSTM giữ liền; mở khi có cửa sổ ≥ ngưỡng trong `hold` mẫu gần nhất.
⚠ Mức bằng chứng: bản dựng lại (Python + onnxruntime) của mã Kotlin — số đo của CỔNG trên bộ WAV tổng hợp, không phải số của xe.

Một tệp câu gọi "tới ASR" khi có ít nhất một lượt giải mã qua cả hai cổng mà cửa sổ phủ ≥ 60 % câu. "Mất" = tới ASR khi KHÔNG có
cổng tiếng người (cùng mọi thứ khác) nhưng không tới khi có cổng. Tệp dài 45 s: đếm lượt giải mã qua cổng (giá CPU).

Dùng: <venv>/bin/python -I gate-sweep.py <thư-mục-mix> <thư-mục-tts> [ngưỡng,…] [hold_ms,…]
      (venv: pip install onnxruntime numpy · thư-mục-mix: `mix.py` · thư-mục-tts: `gen-tts.sh`, để biết độ dài câu)
"""
import glob
import os
import sys
import wave

import numpy as np
import onnxruntime as ort

SR = 16000
FRAME = 1600
WIN = SR * 1600 // 1000
EVERY = SR * 500 // 1000
MIN = SR * 500 // 1000
FIN_MIN = SR * 200 // 1000
RMS_GATE = float(os.environ.get("RMS_GATE", "0.010"))   # `VoiceWakeAsr.RMS_GATE` — quét bằng biến môi trường
TAIL_MS = 800
VAD_WIN = 512
HERE = os.path.dirname(os.path.abspath(__file__))
VAD_PATH = os.path.join(HERE, "../../../app/src/main/assets/voice/silero_vad.onnx")


def rd(p):
    with wave.open(p) as w:
        return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32)


class Vad:
    def __init__(self):
        o = ort.SessionOptions(); o.intra_op_num_threads = 1; o.inter_op_num_threads = 1
        self.s = ort.InferenceSession(VAD_PATH, o)
        self.h = np.zeros((2, 1, 64), np.float32); self.c = np.zeros((2, 1, 64), np.float32)

    def prob(self, x):
        p, self.h, self.c = self.s.run(None, {"x": x[None, :].astype(np.float32), "h": self.h, "c": self.c})
        return float(p[0, 0])


def simulate(pcm):
    """Một lượt qua đường quyết định (chưa áp cổng nào). Trả mọi ô giải mã + xác suất VAD theo mẫu đã nạp.

    slots: list of (fed_index_end, wall_start, wall_end, window_rms, wall_positions) — mọi ô giải mã (CHƯA qua cổng RMS)
    vad:   list of (fed_index_end_of_window, prob)
    """
    vad = Vad()
    floor = 120.0
    last_voiced = None; tail_pending = False
    fed_pos = []                      # vị trí tường của từng mẫu đã nạp (để biết cửa sổ phủ đâu)
    window_end = 0                    # số mẫu đã nạp
    since = 0
    carry = []
    vads = []
    slots = []

    def take(a, frame_start):
        nonlocal window_end, since, carry
        fed_pos.extend(range(frame_start, frame_start + len(a)))
        window_end += len(a); since += len(a)
        carry.extend((a / 32768.0).tolist())
        while len(carry) >= VAD_WIN:
            w = np.array(carry[:VAD_WIN], np.float32); carry = carry[VAD_WIN:]
            vads.append((window_end - len(carry), vad.prob(w)))

    def decode():
        nonlocal since
        since = 0                     # `decodeWindow` xoá nhịp TRƯỚC mọi cổng — đúng thứ tự mã ship
        filled = min(window_end, WIN)
        seg = np.array(fed_pos[window_end - filled:window_end])
        r = float(np.sqrt(np.mean((pcm[seg] / 32768.0) ** 2)))
        slots.append((window_end, int(seg[0]), int(seg[-1]), r, seg))   # cổng RMS áp ở bước chấm (quét được)

    for k in range(len(pcm) // FRAME):
        a = pcm[k * FRAME:(k + 1) * FRAME]
        now = k * 100
        rms = float(np.sqrt(np.mean(a * a)))
        thr = max(2.0 * floor, 120.0)
        voiced = rms > thr
        if not voiced:
            floor += 0.05 * (rms - floor)
        if voiced:
            last_voiced = now; tail_pending = True
            take(a, k * FRAME)
            if since >= EVERY and min(window_end, WIN) >= MIN:
                decode()
        elif tail_pending:
            if now - last_voiced < TAIL_MS:
                take(a, k * FRAME)
            else:
                tail_pending = False
                if min(window_end, WIN) >= MIN and since >= FIN_MIN:
                    decode()
    return slots, vads


def gate_open(vads, at, thr, hold):
    """`VoiceWakeSpeechGate.open()` lúc ô giải mã kết thúc ở mẫu đã-nạp thứ `at`."""
    seen = [(e, p) for e, p in vads if e <= at]
    if not seen:
        return True                    # chưa chấm cửa sổ nào ⇒ mở (không đoán thay dữ liệu)
    last = max((e for e, p in seen if p >= thr), default=None)
    # `sinceSpeech` đếm theo cửa sổ ĐÃ CHẤM (phần lẻ < 512 mẫu chưa chấm không tính) — đúng `VoiceWakeSpeechGate.accept`.
    return last is not None and seen[-1][0] - last <= hold


def main():
    mix, tts = sys.argv[1], sys.argv[2]
    thrs = [float(x) for x in (sys.argv[3] if len(sys.argv) > 3 else "0.45,0.35,0.25,0.15").split(",")]
    holds = [int(x) for x in (sys.argv[4] if len(sys.argv) > 4 else "1200,1600,2000").split(",")]
    lens = {os.path.basename(p)[:-4]: len(rd(p)) for p in glob.glob(os.path.join(tts, "*.wav"))}
    res = {}
    for p in sorted(glob.glob(os.path.join(mix, "*.wav"))):
        name = os.path.basename(p)[:-4]
        pcm = rd(p)
        slots, vads = simulate(pcm)
        res[name] = ([q for q in slots if q[3] >= RMS_GATE], vads, slots, pcm)
    cfgs = [(t, h) for t in thrs for h in holds]
    print(f"# {mix}")
    for cond in ("clean", "instr", "vocal"):
        files = [n for n in res if n.startswith(cond + "_pos_")]
        if not files:
            continue
        reach_base = 0; line = []
        per = {c: [0, 0, 0] for c in cfgs}   # tới · mất · ô qua cổng
        for n in files:
            slots, vads = res[n][:2]
            L = lens[n.split("_", 1)[1]]; a, b = SR, SR + L
            def covers(s):
                return max(0, min(b, s[2]) - max(a, s[1])) >= 0.6 * L
            base = any(covers(s) for s in slots)
            reach_base += base
            for (t, h) in cfgs:
                ok = [s for s in slots if gate_open(vads, s[0], t, h * SR // 1000)]
                hit = any(covers(s) for s in ok)
                per[(t, h)][0] += hit; per[(t, h)][1] += (base and not hit); per[(t, h)][2] += len(ok)
        print(f"{cond}: n={len(files)} tới ASR không cổng={reach_base}")
        for c in cfgs:
            t, h = c; r, lost, k = per[c]
            print(f"   ngưỡng {t:.2f} hold {h:5d} ms: tới={r:3d} mất={lost:3d} ô/tệp={k / len(files):5.2f}")
    for n in ("long_instr", "long_vocal", "long_silence"):
        if n in res:
            slots, vads = res[n][:2]
            cells = " · ".join(f"{t:.2f}/{h}={sum(gate_open(vads, s[0], t, h * SR // 1000) for s in slots)}" for t, h in cfgs)
            print(f"{n}: ô qua RMS={len(slots)} · qua cổng {cells}")
    neg = [n for n in res if "_neg_" in n]
    if neg:
        cells = " · ".join(
            f"{t:.2f}/{h}={sum(sum(gate_open(res[n][1], s[0], t, h * SR // 1000) for s in res[n][0]) for n in neg) / len(neg):.2f}"
            for t, h in cfgs)
        print(f"câu thường n={len(neg)}: ô/tệp qua cổng {cells}")
    dump(res, lens)
    texts(res, thrs[0], holds[0])


def dump(res, lens):
    """DUMP=<tệp>: đặc trưng ĐÃ GHI của câu gọi (mọi ô: mẫu-đã-nạp · vị trí · RMS cửa sổ; xác suất VAD) — bài khoá
    `VoiceWakeGateFixtureTest` phát lại qua đúng `VoiceWakeSpeechGate` + `VoiceWakeAsr.RMS_GATE`."""
    out = os.environ.get("DUMP")
    if not out:
        return
    only = os.environ.get("DUMP_ONLY", "_pos_")
    with open(out, "a", encoding="utf-8") as f:
        for n in sorted(res):
            if only not in n:
                continue
            _, vads, slots, _ = res[n]
            L = lens[n.split("_", 1)[1]]
            sl = ";".join(f"{e}:{a}:{b}:{r:.5f}" for e, a, b, r, _ in slots)
            vd = ";".join(f"{e}:{p:.3f}" for e, p in vads)
            f.write(f"{os.path.basename(os.path.normpath(sys.argv[1]))}/{n}\t{SR}:{SR + L}\t{sl}\t{vd}\n")


def texts(res, thr, hold):
    """ASR_MODEL=<thư mục 4 tệp> + TEXTS=<tệp>: giải mã (sherpa-onnx host, cùng tham số `VoiceRecognizer.openFree`) MỌI ô qua
    cả hai cổng, theo thứ tự thời gian ⇒ TSV `tên \t 0 \t chữ|chữ…` cùng khuôn `wake-harness-texts-*.tsv` (bài khớp chữ đọc)."""
    model, out = os.environ.get("ASR_MODEL"), os.environ.get("TEXTS")
    if not model or not out:
        return
    import sherpa_onnx
    rec = sherpa_onnx.OfflineRecognizer.from_transducer(
        encoder=os.path.join(model, "encoder.int8.onnx"), decoder=os.path.join(model, "decoder.onnx"),
        joiner=os.path.join(model, "joiner.int8.onnx"), tokens=os.path.join(model, "tokens.txt"),
        num_threads=4, sample_rate=SR, feature_dim=80, decoding_method="modified_beam_search", max_active_paths=4)
    with open(out, "a", encoding="utf-8") as f:
        for n in sorted(res):
            if not ("_pos_" in n or "_neg_" in n):
                continue
            slots, vads, _, pcm = res[n]
            got = []
            for e, a, b, r, seg in slots:
                if not gate_open(vads, e, thr, hold * SR // 1000):
                    continue
                st = rec.create_stream(); st.accept_waveform(SR, pcm[seg] / 32768.0); rec.decode_stream(st)
                got.append(st.result.text.strip().lower())
            f.write(f"{os.path.basename(os.path.normpath(sys.argv[1]))}/{n}\t0\t{'|'.join(t for t in got if t)}\n")


main()
