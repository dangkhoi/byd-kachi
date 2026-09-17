#!/usr/bin/env python3
# Benchmark off-car: so gói NGHE trên bộ giữ-riêng GIỌNG THẬT (270 câu, 3 người × 3 lượt × 30 câu).
# Đo "khớp câu" (transcript chuẩn hoá bỏ dấu+thường) làm proxy cho "đúng ý định" — parser :core tolerant,
# transcript đúng ⇒ gần chắc intent đúng. Chạy: /tmp/sherpa-venv/bin/python bench-intent.py <model_dir> <nhãn>
import os, sys, glob, wave, unicodedata, re
import numpy as np, sherpa_onnx

M = sys.argv[1]
LABEL = sys.argv[2] if len(sys.argv) > 2 else os.path.basename(M)
CASES = sys.argv[3] if len(sys.argv) > 3 else "/tmp/kachi-bench/cases.tsv"
WAVDIR = sys.argv[4] if len(sys.argv) > 4 else "/tmp/kachi-bench/wav"
HOT = "core/build/hotwords/hotwords-phrases.txt"

def pick(s):
    for c in (f"{s}.int8.onnx", f"{s}.onnx"):
        if os.path.isfile(os.path.join(M, c)): return os.path.join(M, c)
    g = sorted(glob.glob(os.path.join(M, f"{s}*.onnx")))
    return g[0] if g else ""

def norm(t):
    t = unicodedata.normalize("NFD", t.lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9\s]", " ", t)).strip()

rec = sherpa_onnx.OfflineRecognizer.from_transducer(
    tokens=M + "/tokens.txt", encoder=pick("encoder"), decoder=pick("decoder"), joiner=pick("joiner"),
    num_threads=2, decoding_method="modified_beam_search", max_active_paths=4,
    hotwords_file=HOT, hotwords_score=3.0, modeling_unit="bpe", bpe_vocab=M + "/bpe_vocab.txt",
)

cases = {}
for line in open(CASES):
    line = line.rstrip("\n")
    if not line or line.startswith("#"): continue
    uid, text = line.split("\t", 1)
    cases[uid] = text

ok = 0; n = 0; miss = []
for uid, want in cases.items():
    wp = os.path.join(WAVDIR, uid + ".wav")
    if not os.path.isfile(wp): continue
    with wave.open(wp, "rb") as f:
        d = np.frombuffer(f.readframes(f.getnframes()), dtype="<i2").astype("float32") / 32768.
    st = rec.create_stream(); st.accept_waveform(16000, d); rec.decode_stream(st)
    got = st.result.text.strip()
    n += 1
    if norm(got) == norm(want):
        ok += 1
    else:
        miss.append((uid, want, got))

print(f"{LABEL}\t{ok}/{n} = {100.0*ok/n:.1f}%")
for uid, want, got in miss[:40]:
    print(f"  MISS {uid}: muốn «{want}» ⇒ nghe «{got}»")
