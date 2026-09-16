#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Xuất RNN-LM sang ONNX đúng giao diện sherpa-onnx đòi + lượng tử hoá int8 (spec P1.4).

Giao diện KHÔNG được sai một ly — đọc thẳng từ nguồn C++ (`csrc/offline-lm.cc`, `csrc/offline-rnn-lm.cc`):

    vào : x      int64 [N, L]   chuỗi token của từng giả thuyết, KHÔNG kèm sos/eos
          x_lens int64 [N]      độ dài thật
    ra  : nll    float32 [N]    negative log-likelihood cả câu

`OfflineLM::ComputeLMScore` dựng `x` bằng cách bỏ `context_size` token đệm đầu của mỗi giả thuyết rồi đệm 0
cho bằng nhau, và nó **lấy tên vào/ra từ chính tệp ONNX** (`GetInputNames`) — nên thứ tự và kiểu mới là thứ
ràng buộc, còn tên chỉ cần tồn tại. Ta vẫn đặt đúng `x` / `x_lens` / `nll` như icefall để đọc log cho dễ.

Bọc SOS/EOS nằm **bên trong** đồ thị ONNX (lớp `RnnLmModelWrapper` của icefall) — sherpa không tự thêm.
Quên chỗ này thì LM chấm một chuỗi khác chuỗi nó được huấn luyện, điểm ra vẫn là số thật nhưng vô nghĩa.
"""
from __future__ import annotations

import argparse
import os

import onnx
import torch

from importlib.machinery import SourceFileLoader

_train = SourceFileLoader(
    "train_rnnlm", os.path.join(os.path.dirname(os.path.abspath(__file__)), "train-rnnlm.py")
).load_module()
RnnLmModel = _train.RnnLmModel


class RnnLmModelWrapper(torch.nn.Module):
    """Chép từ `icefall/rnn_lm/export-onnx.py` (Apache-2.0)."""

    def __init__(self, model: RnnLmModel, sos_id: int, eos_id: int):
        super().__init__()
        self.model = model
        self.sos_id = sos_id
        self.eos_id = eos_id

    def forward(self, x: torch.Tensor, x_lens: torch.Tensor) -> torch.Tensor:
        N = x.size(0)
        sos = torch.full((1,), fill_value=self.sos_id, dtype=x.dtype).expand(N, 1)
        sos_x = torch.cat([sos, x], dim=1)
        pad = torch.zeros((1,), dtype=x.dtype).expand(N, 1)
        x_eos = torch.cat([x, pad], dim=1)
        row = torch.arange(0, N, dtype=x.dtype)
        x_eos[row, x_lens] = self.eos_id
        return self.model(x=sos_x, y=x_eos, lengths=x_lens + 1).to(torch.float32).sum(dim=1)


def add_meta(path: str, meta: dict[str, str]):
    m = onnx.load(path)
    while len(m.metadata_props):
        m.metadata_props.pop()
    for k, v in meta.items():
        e = m.metadata_props.add()
        e.key, e.value = k, str(v)
    onnx.save(m, path)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--out", required=True, help="tệp .onnx fp32 sẽ ghi")
    ap.add_argument("--int8", default="", help="tệp .onnx int8 (rỗng = bỏ qua)")
    ap.add_argument("--opset", type=int, default=13)
    args = ap.parse_args()

    ck = torch.load(args.ckpt, map_location="cpu", weights_only=False)
    model = RnnLmModel(ck["vocab_size"], ck["emb"], ck["hidden"], ck["layers"], ck["tie"])
    model.load_state_dict(ck["model"])
    model.eval()

    wrapper = RnnLmModelWrapper(model, sos_id=1, eos_id=1)
    N, L = 1, 20
    x = torch.randint(low=3, high=ck["vocab_size"], size=(N, L), dtype=torch.int64)
    x_lens = torch.full((N,), fill_value=L, dtype=torch.int64)

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    torch.onnx.export(
        wrapper, (x, x_lens), args.out, verbose=False, opset_version=args.opset,
        input_names=["x", "x_lens"], output_names=["nll"],
        dynamic_axes={"x": {0: "N", 1: "L"}, "x_lens": {0: "N"}, "nll": {0: "N"}},
    )
    add_meta(args.out, dict(model_type="rnnlm", version="1", model_author="kachi/dangkhoi",
                            comment="rnnlm without state; domain=kachi car commands + general vi",
                            sos_id=1, eos_id=1, vocab_size=ck["vocab_size"],
                            dev_ppl=round(float(ck.get("dev_ppl", 0)), 4),
                            epoch=ck.get("epoch", -1)))
    print(f"== fp32 {args.out}  {os.path.getsize(args.out):,} B")

    if args.int8:
        # Cùng cách k2 lượng tử hoá mô hình ASR: dynamic quant, chỉ MatMul/Gemm, giữ nguyên LSTM nếu ORT
        # không đỡ được — onnxruntime tự bỏ qua op không lượng tử hoá được thay vì hỏng đồ thị.
        from onnxruntime.quantization import QuantType, quantize_dynamic
        quantize_dynamic(model_input=args.out, model_output=args.int8,
                         op_types_to_quantize=["MatMul", "Gemm"], weight_type=QuantType.QInt8)
        add_meta(args.int8, dict(model_type="rnnlm", version="1", model_author="kachi/dangkhoi",
                                 comment="int8 dynamic quant", sos_id=1, eos_id=1,
                                 vocab_size=ck["vocab_size"]))
        print(f"== int8 {args.int8}  {os.path.getsize(args.int8):,} B")

    # Kiểm ngay tại chỗ: chạy thử onnxruntime với N=3 độ dài khác nhau. Xuất không lỗi KHÔNG có nghĩa là
    # nạp được (CLAUDE.md §8 — hàm mới phải thấy nó CHẠY, không phải thấy nó compile).
    import numpy as np
    import onnxruntime as ort
    for p in filter(None, [args.out, args.int8]):
        sess = ort.InferenceSession(p, providers=["CPUExecutionProvider"])
        xx = np.array([[5, 6, 7, 0, 0], [8, 9, 10, 11, 12], [3, 0, 0, 0, 0]], dtype=np.int64)
        ll = np.array([3, 5, 1], dtype=np.int64)
        nll = sess.run(["nll"], {"x": xx, "x_lens": ll})[0]
        print(f"   [kiểm] {os.path.basename(p)} nll={np.round(nll, 3).tolist()}  "
              f"shape={nll.shape} dtype={nll.dtype}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
