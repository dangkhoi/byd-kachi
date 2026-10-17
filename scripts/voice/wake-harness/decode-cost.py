#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HARNESS "Hey Kachi" — GIÁ CPU một lượt giải mã theo tuỳ chọn onnxruntime (WAKE-DECODE-COST, 3.1.12).

Dựng recognizer host cùng tham số `VoiceEngine.build` (transducer int8, beam 4, `modified_beam_search`) với `num_threads` và
một chế độ quay chờ, rồi đo MỖI lượt giải mã: CPU cả tiến trình (`getrusage`, gồm worker onnxruntime) và thời gian tường.
  • kind=wake: cửa sổ 1,6 s mỗi 500 ms từ mốc 500 ms (đúng nhịp `VoiceWakeAsr`, KHÔNG cổng — cổng không đổi giá một lượt).
  • kind=cmd : nguyên câu (3,3–3,6 s) + từng cặp câu nối (~7 s) — dạng giải mã phiên lệnh.
Sau mỗi lượt ngủ 50 ms TRƯỚC khi đọc CPU lần hai: phần worker còn quay chờ sau `Run()` là một phần giá thật (nhịp thật 500 ms).
Chế độ: on = mặc định (`cpu`) · stop = `session.force_spinning_stop=1` · off = `session.{intra,inter}_op.allow_spinning=0`,
qua `provider="cpu:<tệp>"` + dòng `SessionConfig.<khoá>` (sherpa-onnx `csrc/session.cc` v1.13.8).
Ghi chữ ra từng lượt vào `<out>.json` để so trùng byte giữa các chế độ (tỉ lệ nổ không đổi ⇔ chữ không đổi).

⚠ Mức bằng chứng: host ≠ xe (8 lõi ARM). Chỉ dùng TỈ LỆ giữa các chế độ, không dùng số tuyệt đối.

Dùng: <venv>/bin/python -I decode-cost.py <thư-mục-mô-hình> <wake|cmd> <luồng> <on|stop|off> <out-prefix> <wav…>
      (venv: pip install sherpa-onnx==1.13.8 numpy · wav: `mix.py` thư mục mặc định, clean_pos_* + clean_neg_*)
"""
import json
import os
import resource
import sys
import tempfile
import time
import wave

import numpy as np
import sherpa_onnx

SR = 16000
WIN, EVERY, MIN = SR * 1600 // 1000, SR * 500 // 1000, SR * 500 // 1000
CFG = {
    "on": None,
    "stop": "SessionConfig.session.force_spinning_stop=1\n",   # = SherpaModelCatalog.ORT_SESSION_CONFIG
    "off": "SessionConfig.session.intra_op.allow_spinning=0\nSessionConfig.session.inter_op.allow_spinning=0\n",
}


def cpu_s():
    r = resource.getrusage(resource.RUSAGE_SELF)
    return r.ru_utime + r.ru_stime


def rd(p):
    with wave.open(p) as w:
        return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32)


def main():
    model, kind, threads, mode, out = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4], sys.argv[5]
    files = sys.argv[6:]
    prov = "cpu"
    if CFG[mode]:
        fd, path = tempfile.mkstemp(suffix=".cfg")
        os.write(fd, CFG[mode].encode()); os.close(fd)
        prov = "cpu:" + path
    rec = sherpa_onnx.OfflineRecognizer.from_transducer(
        encoder=f"{model}/encoder.int8.onnx", decoder=f"{model}/decoder.onnx", joiner=f"{model}/joiner.int8.onnx",
        tokens=f"{model}/tokens.txt", num_threads=threads, sample_rate=SR, feature_dim=80,
        decoding_method="modified_beam_search", max_active_paths=4, provider=prov)

    def dec(seg):
        st = rec.create_stream(); st.accept_waveform(SR, seg / 32768.0); rec.decode_stream(st)
        return st.result.text.strip().lower()

    if kind == "wake":
        segs = []
        for f in files:
            pcm = rd(f)
            segs += [(os.path.basename(f), pcm[max(0, e - WIN):e]) for e in range(MIN, len(pcm) + 1, EVERY)]
    else:
        pcms = [(os.path.basename(f), rd(f)) for f in files]
        segs = pcms + [(a[0] + "+" + b[0], np.concatenate([a[1], b[1]])) for a, b in zip(pcms[::2], pcms[1::2])]
    dec(segs[0][1])   # khởi động
    groups, texts = {}, []
    for name, seg in segs:
        g = "wake" if kind == "wake" else ("short" if len(seg) < 5 * SR else "long")
        c0, t0 = cpu_s(), time.perf_counter()
        texts.append([name, dec(seg)])
        t1 = time.perf_counter(); time.sleep(0.05); c1 = cpu_s()
        groups.setdefault(g, ([], []))[0].append((t1 - t0) * 1000); groups[g][1].append((c1 - c0) * 1000)
    res = {"kind": kind, "threads": threads, "mode": mode}
    for g, (w, c) in groups.items():
        res[g] = {"n": len(w), "wall_mean": round(float(np.mean(w)), 1), "wall_p95": round(float(np.percentile(w, 95)), 1),
                  "cpu_mean": round(float(np.mean(c)), 1), "cpu_p95": round(float(np.percentile(c, 95)), 1)}
    print(json.dumps(res, ensure_ascii=False))
    with open(f"{out}-{kind}-{threads}-{mode}.json", "w", encoding="utf-8") as f:
        json.dump(texts, f, ensure_ascii=False)


main()
