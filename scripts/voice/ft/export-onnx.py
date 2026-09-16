#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Xuất mô hình đã fine-tune sang ONNX cho sherpa-onnx + lượng tử int8 (spec P3.5).

Dùng thẳng `export-onnx.py` của icefall (Apache-2.0) thay vì tự viết: nó là thứ sinh ra **chính** các
tệp `encoder/decoder/joiner.onnx` mà sherpa-onnx đang nạp, kèm đúng metadata (`context_size`,
`decode_chunk_len`…). Tự viết lại nghĩa là tự nhận rủi ro lệch một trường metadata mà không ai thấy cho
tới khi chạy trên xe.

Việc của tệp này chỉ là ba thứ icefall không làm hộ:
 1. dựng đúng kiến trúc đọc từ checkpoint (chia sẻ `ARCH` với `finetune.py` — DRY),
 2. đóng gói `tokens.txt` + `bpe.model` đi kèm (sherpa cần cả hai để bật hotword),
 3. **kiểm bằng cách NẠP THẬT vào sherpa-onnx và giải mã một WAV** — xuất không lỗi ≠ nạp được
    (CLAUDE.md §8).
"""
from __future__ import annotations

import argparse
import os
import shutil
import sys

import torch

ICEFALL = os.environ.get("ICEFALL_ROOT", "/tmp/icefall")
sys.path.insert(0, ICEFALL)
sys.path.insert(0, os.path.join(ICEFALL, "egs/librispeech/ASR/zipformer"))

from importlib.machinery import SourceFileLoader  # noqa: E402

_ft = SourceFileLoader(
    "ft_finetune", os.path.join(os.path.dirname(os.path.abspath(__file__)), "finetune.py")
).load_module()


def add_meta(path: str, meta: dict):
    import onnx
    m = onnx.load(path)
    while len(m.metadata_props):
        m.metadata_props.pop()
    for k, v in meta.items():
        e = m.metadata_props.add()
        e.key, e.value = k, str(v)
    onnx.save(m, path)


if __name__ == "__main__":
    # icefall `export-onnx.py` là script CLI chứ không phải thư viện; gọi nó qua subprocess với
    # đúng cờ kiến trúc là cách ÍT rủi ro nhất (không phải chép lại 400 dòng logic xuất).
    import subprocess
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--base", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--test-wav", default="")
    a = ap.parse_args()

    os.makedirs(a.out, exist_ok=True)
    exp = os.path.join(a.out, "exp")
    os.makedirs(exp, exist_ok=True)
    # icefall đòi checkpoint tên `epoch-<N>.pt` trong `--exp-dir`
    shutil.copy2(a.ckpt, os.path.join(exp, "epoch-99.pt"))
    data = os.path.join(a.out, "data", "lang_bpe_2000")
    os.makedirs(data, exist_ok=True)
    for f in ("tokens.txt", "bpe.model"):
        shutil.copy2(os.path.join(a.base, f), os.path.join(data, f))

    A = _ft.ARCH
    cmd = [
        "/tmp/icefall-venv/bin/python",
        os.path.join(ICEFALL, "egs/librispeech/ASR/zipformer/export-onnx.py"),
        "--tokens", os.path.join(data, "tokens.txt"),
        "--use-averaged-model", "0", "--epoch", "99", "--avg", "1",
        "--exp-dir", exp,
        "--num-encoder-layers", ",".join(map(str, A["num_encoder_layers"])),
        "--downsampling-factor", ",".join(map(str, A["downsampling_factor"])),
        "--feedforward-dim", ",".join(map(str, A["feedforward_dim"])),
        "--num-heads", ",".join(map(str, A["num_heads"])),
        "--encoder-dim", ",".join(map(str, A["encoder_dim"])),
        "--query-head-dim", ",".join([str(A["query_head_dim"])] * 6),
        "--value-head-dim", ",".join([str(A["value_head_dim"])] * 6),
        "--pos-head-dim", ",".join([str(A["pos_head_dim"])] * 6),
        "--pos-dim", str(A["pos_dim"]),
        "--encoder-unmasked-dim", ",".join(map(str, A["encoder_unmasked_dim"])),
        "--cnn-module-kernel", ",".join(map(str, A["cnn_module_kernel"])),
        "--decoder-dim", "512", "--joiner-dim", "512",
        "--causal", "0", "--use-transducer", "1", "--use-ctc", "1",
    ]
    env = dict(os.environ, PYTHONPATH=f"{ICEFALL}:{os.path.join(ICEFALL,'egs/librispeech/ASR/zipformer')}")
    print("== chạy icefall export-onnx.py")
    r = subprocess.run(cmd, env=env, cwd=a.out)
    if r.returncode != 0:
        sys.exit(f"export-onnx.py thất bại (exit {r.returncode})")

    # gom tệp về đúng tên sherpa-onnx mong đợi + lượng tử int8
    import glob
    from onnxruntime.quantization import QuantType, quantize_dynamic
    got = {}
    for stem in ("encoder", "decoder", "joiner"):
        hits = [p for p in glob.glob(os.path.join(exp, f"{stem}-*.onnx")) if ".int8." not in p]
        if not hits:
            sys.exit(f"không thấy {stem}-*.onnx trong {exp}")
        dst = os.path.join(a.out, f"{stem}.onnx")
        shutil.copy2(sorted(hits)[0], dst)
        q = os.path.join(a.out, f"{stem}.int8.onnx")
        quantize_dynamic(model_input=dst, model_output=q,
                         op_types_to_quantize=["MatMul", "Gemm"], weight_type=QuantType.QInt8)
        got[stem] = (os.path.getsize(dst), os.path.getsize(q))
        print(f"   {stem}: fp32 {got[stem][0]:,} B · int8 {got[stem][1]:,} B")
    for f in ("tokens.txt", "bpe.model"):
        shutil.copy2(os.path.join(a.base, f), os.path.join(a.out, f))
    tot8 = sum(v[1] for v in got.values()) + os.path.getsize(os.path.join(a.out, "tokens.txt"))
    print(f"== gói int8: {tot8/1e6:.1f} MB  (trần R5 = 80 MB) "
          f"{'✔ ĐẠT' if tot8 <= 80e6 else '✘ VƯỢT'}")

    # KIỂM NẠP THẬT — xuất sạch không có nghĩa là sherpa nạp được
    if a.test_wav:
        import wave
        import numpy as np
        import sherpa_onnx
        rec = sherpa_onnx.OfflineRecognizer.from_transducer(
            tokens=os.path.join(a.out, "tokens.txt"),
            encoder=os.path.join(a.out, "encoder.int8.onnx"),
            decoder=os.path.join(a.out, "decoder.onnx"),
            joiner=os.path.join(a.out, "joiner.int8.onnx"),
            num_threads=2, decoding_method="modified_beam_search", max_active_paths=4)
        with wave.open(a.test_wav, "rb") as f:
            x = np.frombuffer(f.readframes(f.getnframes()), dtype="<i2").astype("float32") / 32768.
        st = rec.create_stream()
        st.accept_waveform(16000, x)
        rec.decode_stream(st)
        print(f"== [kiểm nạp] sherpa-onnx giải mã ra: {st.result.text!r}")
    shutil.rmtree(exp, ignore_errors=True)
