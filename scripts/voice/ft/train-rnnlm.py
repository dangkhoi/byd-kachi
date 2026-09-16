#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Huấn luyện RNN-LM cho hợp nhất LM nông của sherpa-onnx (spec `kachi-voice-finetune` P1.3).

## Vì sao tự huấn luyện thay vì tải về

Hợp nhất LM nông đòi LM dùng **đúng** bộ token của mô hình ASR. [ĐO 2026-09-16] `zipformer-vi` đang ship và
`vi-30M` đều có vocab 2 000, cùng `<blk>=0 <sos/eos>=1 <unk>=2`, nhưng **thứ tự khác nhau** (id 3 của bản
đang ship là `▁MỘT`, của vi-30M là `▁HAI`). Mọi RNN-LM tải sẵn trên mạng đều gắn với bộ token của mô hình
khác ⇒ không tái dùng được. Nên LM phải sinh ra từ **chính `bpe.model` của mô hình đích**.

## Kiến trúc — chép đúng `icefall/rnn_lm/model.py::RnnLmModel` (Apache-2.0)

    Embedding(vocab, emb) → LSTM(emb, hidden, layers, batch_first=True) → Linear(hidden, vocab)

Không thêm bớt gì. Lý do cứng: tệp ONNX xuất ra phải **khớp giao diện** mà `sherpa-onnx/csrc/offline-rnn-lm.cc`
gọi (`x, x_lens → nll`), và cách tính `nll` của icefall là cộng cross-entropy theo cả câu có mặt nạ đệm.
Tự nghĩ ra kiến trúc khác thì con số `nll` mang nghĩa khác và `lm_scale` không còn so được với tài liệu k2.

## Chữ HOA — cái bẫy đắt nhất ở đây

[ĐO] `bpe.model` của cả hai mô hình chỉ có mảnh **CHỮ HOA CÓ DẤU**: `sp.encode("bật đèn đọc")` ra
`['▁','bật','▁','đèn','▁','đọc']` (6 mảnh, rơi về byte-fallback), còn `sp.encode("BẬT ĐÈN ĐỌC")` ra
`['▁BẬT','▁ĐÈN','▁ĐỌC']` (3 mảnh, đúng). Huấn luyện LM trên chữ thường ⇒ LM học một không gian token mà
bộ giải mã **không bao giờ** đi vào ⇒ điểm LM là nhiễu thuần tuý. Nên mọi câu được `.upper()` trước khi mã hoá.
"""
from __future__ import annotations

import argparse
import math
import os
import time

import sentencepiece as spm
import torch
import torch.nn.functional as F


class RnnLmModel(torch.nn.Module):
    """Chép từ `icefall/rnn_lm/model.py` (Apache-2.0), bỏ phần streaming không dùng tới."""

    def __init__(self, vocab_size: int, embedding_dim: int, hidden_dim: int,
                 num_layers: int, tie_weights: bool = True):
        super().__init__()
        self.vocab_size = vocab_size
        self.input_embedding = torch.nn.Embedding(vocab_size, embedding_dim)
        self.rnn = torch.nn.LSTM(input_size=embedding_dim, hidden_size=hidden_dim,
                                 num_layers=num_layers, batch_first=True)
        self.output_linear = torch.nn.Linear(hidden_dim, vocab_size)
        if tie_weights:
            assert embedding_dim == hidden_dim, (embedding_dim, hidden_dim)
            self.output_linear.weight = self.input_embedding.weight

    def forward(self, x: torch.Tensor, y: torch.Tensor, lengths: torch.Tensor) -> torch.Tensor:
        """x (N,L) có SOS ở đầu · y (N,L) là x dịch trái + EOS · lengths (N,) độ dài thật.

        Trả (N,L) negative log-likelihood, ô đệm đặt 0 — đúng như icefall.
        """
        emb = self.input_embedding(x)
        rnn_out, _ = self.rnn(emb)
        logits = self.output_linear(rnn_out)
        nll = F.cross_entropy(logits.reshape(-1, self.vocab_size), y.reshape(-1), reduction="none")
        mask = (torch.arange(x.size(1), device=x.device)[None, :] >= lengths[:, None]).reshape(-1)
        nll = nll.masked_fill(mask, 0.0)
        return nll.reshape(x.size(0), -1)


def encode_file(path: str, sp: spm.SentencePieceProcessor, max_len: int) -> list[list[int]]:
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if not s:
                continue
            ids = sp.encode(s.upper(), out_type=int)
            if 1 <= len(ids) <= max_len:
                out.append(ids)
    return out


def batches(data: list[list[int]], bs: int, sos: int, eos: int, blank: int, shuffle: bool, gen):
    idx = torch.randperm(len(data), generator=gen).tolist() if shuffle else list(range(len(data)))
    # xếp theo độ dài trong từng khối lớn ⇒ ít ô đệm hơn, nhanh hơn, mà vẫn còn ngẫu nhiên giữa các khối
    chunk = bs * 40
    for c0 in range(0, len(idx), chunk):
        block = sorted(idx[c0:c0 + chunk], key=lambda i: len(data[i]))
        for b0 in range(0, len(block), bs):
            rows = [data[i] for i in block[b0:b0 + bs]]
            L = max(len(r) for r in rows) + 1
            x = torch.full((len(rows), L), blank, dtype=torch.int64)
            y = torch.full((len(rows), L), blank, dtype=torch.int64)
            lens = torch.zeros(len(rows), dtype=torch.int64)
            for i, r in enumerate(rows):
                x[i, 0] = sos
                x[i, 1:len(r) + 1] = torch.tensor(r, dtype=torch.int64)
                y[i, :len(r)] = torch.tensor(r, dtype=torch.int64)
                y[i, len(r)] = eos
                lens[i] = len(r) + 1
            yield x, y, lens


@torch.no_grad()
def evaluate(model, data, bs, sos, eos, blank, device, gen) -> float:
    model.eval()
    tot_nll = 0.0
    tot_tok = 0
    for x, y, lens in batches(data, bs, sos, eos, blank, False, gen):
        nll = model(x.to(device), y.to(device), lens.to(device))
        tot_nll += float(nll.sum())
        tot_tok += int(lens.sum())
    return math.exp(tot_nll / max(tot_tok, 1))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--bpe", required=True, help="bpe.model CỦA MÔ HÌNH ĐÍCH")
    ap.add_argument("--tokens", required=True, help="tokens.txt của mô hình đích (để chốt vocab)")
    ap.add_argument("--train", required=True)
    ap.add_argument("--dev", required=True)
    ap.add_argument("--out", required=True, help="thư mục ghi checkpoint")
    ap.add_argument("--emb", type=int, default=512)
    ap.add_argument("--hidden", type=int, default=512)
    ap.add_argument("--layers", type=int, default=3)
    ap.add_argument("--batch", type=int, default=128)
    ap.add_argument("--epochs", type=int, default=30)
    ap.add_argument("--lr", type=float, default=2e-3)
    ap.add_argument("--max-len", type=int, default=80)
    ap.add_argument("--patience", type=int, default=4)
    ap.add_argument("--device", default="mps")
    ap.add_argument("--seed", type=int, default=20260916)
    args = ap.parse_args()

    torch.manual_seed(args.seed)
    gen = torch.Generator().manual_seed(args.seed)
    os.makedirs(args.out, exist_ok=True)

    sp = spm.SentencePieceProcessor()
    sp.load(args.bpe)
    vocab = sum(1 for _ in open(args.tokens, encoding="utf-8"))
    assert sp.get_piece_size() == vocab, (sp.get_piece_size(), vocab)
    blank, sos, eos = 0, 1, 1  # đúng quy ước icefall: <blk>=0, <sos/eos>=1 dùng cho cả sos lẫn eos

    tr = encode_file(args.train, sp, args.max_len)
    dv = encode_file(args.dev, sp, args.max_len)
    print(f"== vocab {vocab} · train {len(tr)} câu · dev {len(dv)} câu")

    device = torch.device(args.device if (args.device != "mps" or torch.backends.mps.is_available())
                          else "cpu")
    model = RnnLmModel(vocab, args.emb, args.hidden, args.layers, tie_weights=(args.emb == args.hidden))
    nparam = sum(p.numel() for p in model.parameters())
    print(f"== tham số {nparam/1e6:.2f} M ⇒ fp32 ≈ {nparam*4/1e6:.1f} MB · int8 ≈ {nparam/1e6:.1f} MB")
    model.to(device)

    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=args.epochs)

    best = float("inf")
    bad = 0
    t0 = time.time()
    for ep in range(1, args.epochs + 1):
        model.train()
        tot, ntok, nb = 0.0, 0, 0
        for x, y, lens in batches(tr, args.batch, sos, eos, blank, True, gen):
            x, y, lens = x.to(device), y.to(device), lens.to(device)
            nll = model(x, y, lens)
            loss = nll.sum() / lens.sum()
            opt.zero_grad(set_to_none=True)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            opt.step()
            tot += float(nll.sum())
            ntok += int(lens.sum())
            nb += 1
        sched.step()
        ppl_tr = math.exp(tot / max(ntok, 1))
        ppl_dv = evaluate(model, dv, args.batch, sos, eos, blank, device, gen)
        mark = ""
        if ppl_dv < best - 1e-4:
            best, bad = ppl_dv, 0
            torch.save({"model": model.state_dict(), "vocab_size": vocab, "emb": args.emb,
                        "hidden": args.hidden, "layers": args.layers,
                        "tie": args.emb == args.hidden, "dev_ppl": ppl_dv, "epoch": ep},
                       os.path.join(args.out, "best.pt"))
            mark = "  ← lưu"
        else:
            bad += 1
        print(f"ep {ep:3d}  ppl_train {ppl_tr:8.3f}  ppl_dev {ppl_dv:8.3f}  "
              f"lr {sched.get_last_lr()[0]:.2e}  {time.time()-t0:6.1f}s{mark}")
        if bad >= args.patience:
            print(f"== dừng sớm ở epoch {ep} (dev không cải thiện {args.patience} lượt)")
            break
    print(f"== dev perplexity tốt nhất: {best:.3f} · tổng {time.time()-t0:.1f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
