#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fine-tune zipformer-transducer tiếng Việt trên dữ liệu lệnh xe (spec `kachi-voice-finetune` P3).

## Nền và vì sao chọn nó

`g-group-ai-lab/gipformer1.5-65M-rnnt` — **MIT**, không gated, có `model.pt` **thật** (checkpoint icefall
`checkpoint-iter-272000-avg-6`, 279 MB, 69.65 M tham số). [ĐO] `tokens.txt` và `bpe.model` của nó **trùng
sha256 từng byte** với mô hình đang ship ⇒ fine-tune xong không phải sinh lại 2 241 dòng hotword, không phải
đụng `VoiceSynonyms`/`VoiceIntentParser`.

Ba nền khác đã loại, mỗi cái vì một lý do cứng:
 • `hynt/Zipformer-30M` — **CC-BY-NC-ND**: ND = cấm phái sinh, mà fine-tune CHÍNH LÀ phái sinh. Và nó chỉ có
   `jit_script.pt` (TorchScript triển khai), không phải checkpoint huấn luyện.
 • `zzasdf/icefall-asr-gigaspeech2-vi-zipformer` — Apache-2.0, checkpoint thật, nhưng là bản **150 M** ⇒
   int8 **157 MB**, gấp đôi trần 80 MB của R5.
 • `hataphu/…` — MIT nhưng kho **gated** (401 `GatedRepo`), và chỉ có ONNX.

## Kiến trúc — đọc TỪ CHECKPOINT, không đoán

[ĐO] suy từ chính hình dạng tensor trong `model.pt` (CLAUDE.md §3 — không tin trí nhớ về cấu hình mặc định):

    num_encoder_layers  2,2,3,4,3,2        encoder_dim     192,256,384,512,384,256
    feedforward_dim     384,576,768,1152,768,576
    num_heads           4,4,4,8,4,4        (suy từ in_proj = num_heads*(2*query_head_dim + pos_head_dim))
    downsampling_factor 1,2,4,8,4,2        decoder/joiner dim 512   vocab 2000   có nhánh CTC

Script **kiểm lại bằng `load_state_dict(strict=True)`** — sai một chiều là hỏng ngay, không âm thầm chạy tiếp.

## Kỷ luật chống hỏng mô hình nền

Fine-tune trên dữ liệu hẹp rất dễ **quên kiến thức nền** (mô hình mất khả năng nghe câu ngoài danh mục).
Ba chốt:
 1. **LR thấp** và `--freeze-encoder-stacks` tuỳ chọn (đóng băng vài tầng đầu).
 2. **Trộn dữ liệu phổ thông** nếu có (`--extra-manifest`) — mặc định là `fpt_fosd` (CC-BY-4.0, giọng THẬT).
 3. **Dừng sớm theo dev**, và **giữ nguyên checkpoint gốc** để so từng epoch trên cùng bộ đo.

`--probe N` đo thông lượng trên N phút audio rồi **thoát** — làm việc này TRƯỚC khi cam kết chạy cả đêm
(spec P3.3). Không có GPU: mọi ước lượng phải từ phép đo thật, không từ cảm giác.
"""
from __future__ import annotations

import argparse
import math
import os
import random
import sys
import time
import wave

import numpy as np
import torch

ICEFALL = os.environ.get("ICEFALL_ROOT", "/tmp/icefall")
sys.path.insert(0, ICEFALL)
sys.path.insert(0, os.path.join(ICEFALL, "egs/librispeech/ASR/zipformer"))

import sentencepiece as spm  # noqa: E402
import torchaudio  # noqa: E402

# ── kiến trúc đọc từ checkpoint ────────────────────────────────────────────────────────────────────
ARCH = dict(
    num_encoder_layers=(2, 2, 3, 4, 3, 2),
    downsampling_factor=(1, 2, 4, 8, 4, 2),
    encoder_dim=(192, 256, 384, 512, 384, 256),
    # ⚠ `feedforward_dim` KHÔNG đọc thẳng được từ hình dạng tensor: icefall dựng 3 khối feed-forward với
    # hệ số khác nhau — ff1 = (ffd*3)//4, ff2 = ffd, ff3 = (ffd*5)//4 (`zipformer.py::Zipformer2EncoderLayer`).
    # [ĐO] checkpoint có ff1 = 384,576,768,1152,768,576 ⇒ ffd = ×4/3 = 512,768,1024,1536,1024,768.
    # Lần đầu đặt thẳng 384,… và `load_state_dict` báo sai hình dạng ngay — đúng lý do §8 CLAUDE.md
    # bắt phải kiểm bằng `strict`, chứ không để nó âm thầm nạp một phần.
    feedforward_dim=(512, 768, 1024, 1536, 1024, 768),
    num_heads=(4, 4, 4, 8, 4, 4),
    encoder_unmasked_dim=(192, 192, 256, 256, 256, 192),
    cnn_module_kernel=(31, 31, 15, 15, 15, 31),
    query_head_dim=32, pos_head_dim=4, value_head_dim=12, pos_dim=48,
)


def build_model(vocab: int, device: torch.device):
    from zipformer import Zipformer2
    from subsampling import Conv2dSubsampling
    from decoder import Decoder
    from joiner import Joiner
    from model import AsrModel

    enc_embed = Conv2dSubsampling(in_channels=80, out_channels=ARCH["encoder_dim"][0],
                                  dropout=0.1, layer1_channels=8, layer2_channels=32,
                                  layer3_channels=128)
    encoder = Zipformer2(
        output_downsampling_factor=2,
        downsampling_factor=ARCH["downsampling_factor"],
        num_encoder_layers=ARCH["num_encoder_layers"],
        encoder_dim=ARCH["encoder_dim"],
        encoder_unmasked_dim=ARCH["encoder_unmasked_dim"],
        query_head_dim=(ARCH["query_head_dim"],) * 6,
        pos_head_dim=(ARCH["pos_head_dim"],) * 6,
        value_head_dim=(ARCH["value_head_dim"],) * 6,
        pos_dim=ARCH["pos_dim"],
        num_heads=ARCH["num_heads"],
        feedforward_dim=ARCH["feedforward_dim"],
        cnn_module_kernel=ARCH["cnn_module_kernel"],
        dropout=None, warmup_batches=4000.0, causal=False,
    )
    decoder = Decoder(vocab_size=vocab, decoder_dim=512, blank_id=0, context_size=2)
    joiner = Joiner(encoder_dim=512, decoder_dim=512, joiner_dim=512, vocab_size=vocab)
    m = AsrModel(encoder_embed=enc_embed, encoder=encoder, decoder=decoder, joiner=joiner,
                 encoder_dim=max(ARCH["encoder_dim"]), decoder_dim=512, vocab_size=vocab,
                 use_transducer=True, use_ctc=True)
    return m.to(device)


# ── dữ liệu ────────────────────────────────────────────────────────────────────────────────────────
def fbank(x: torch.Tensor) -> torch.Tensor:
    """80 chiều fbank kiểu Kaldi — đúng thứ icefall/sherpa dùng (`feature_dim=80`, `snip_edges=False`)."""
    return torchaudio.compliance.kaldi.fbank(
        x.unsqueeze(0), num_mel_bins=80, frame_length=25.0, frame_shift=10.0,
        sample_frequency=16000, dither=0.0, energy_floor=1e-10, snip_edges=False)


class WavSet(torch.utils.data.Dataset):
    def __init__(self, rows, sp, max_sec=14.0):
        self.rows = [r for r in rows if r[2] <= max_sec]
        self.sp = sp

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, i):
        path, text, _dur = self.rows[i]
        with wave.open(path, "rb") as f:
            x = np.frombuffer(f.readframes(f.getnframes()), dtype="<i2").astype("float32") / 32768.0
        feat = fbank(torch.from_numpy(x.copy()))
        ids = self.sp.encode(text.upper(), out_type=int)
        return feat, ids


def collate(batch):
    feats = [b[0] for b in batch]
    ids = [b[1] for b in batch]
    T = max(f.size(0) for f in feats)
    x = torch.zeros(len(feats), T, 80)
    xl = torch.zeros(len(feats), dtype=torch.int64)
    for i, f in enumerate(feats):
        x[i, :f.size(0)] = f
        xl[i] = f.size(0)
    import k2
    y = k2.RaggedTensor(ids)
    return x, xl, y


def load_manifest(paths: list[str], splits: set[str], root: str | None) -> list[tuple[str, str, float]]:
    """Đọc manifest của `tts-batch.py` (9 cột) hoặc `augment.py` (5 cột) hoặc real-speech (3 cột)."""
    out = []
    for p in paths:
        base = root or os.path.dirname(os.path.abspath(p))
        for line in open(p, encoding="utf-8"):
            if line.startswith("#"):
                continue
            c = line.rstrip("\n").split("\t")
            if len(c) >= 9:
                uid, split, dur, text = c[0], c[1], float(c[5]), c[8]
            elif len(c) == 5:
                uid, split, dur, text = c[0], c[1], float(c[3]), c[4]
            elif len(c) == 4:
                uid, split, dur, text = c[0], c[1], float(c[2]), c[3]
            else:
                continue
            if split not in splits:
                continue
            wav = os.path.join(base, split, f"{uid}.wav")
            if os.path.isfile(wav):
                out.append((wav, text, dur))
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", required=True, help="thư mục nền (có model.pt, tokens.txt, bpe.model)")
    ap.add_argument("--manifest", nargs="+", required=True)
    ap.add_argument("--audio-root", nargs="+", required=True,
                    help="gốc audio tương ứng từng manifest")
    ap.add_argument("--out", required=True)
    ap.add_argument("--probe", type=float, default=0.0,
                    help="đo thông lượng trên N PHÚT audio rồi thoát (spec P3.3)")
    ap.add_argument("--epochs", type=int, default=3)
    ap.add_argument("--batch", type=int, default=8)
    ap.add_argument("--lr", type=float, default=0.0045,
                    help="base-lr của ScaledAdam (icefall fine-tune dùng 0.0045; từ đầu là 0.045)")
    ap.add_argument("--lr-batches", type=float, default=5000)
    ap.add_argument("--lr-epochs", type=float, default=3.0)
    ap.add_argument("--device", default="cpu", help="cpu | mps (k2 chạy CPU, xem §Ghi chú)")
    ap.add_argument("--workers", type=int, default=6)
    ap.add_argument("--prune-range", type=int, default=5)
    ap.add_argument("--am-scale", type=float, default=0.0)
    ap.add_argument("--lm-scale", type=float, default=0.25)
    ap.add_argument("--simple-loss-scale", type=float, default=0.5)
    ap.add_argument("--ctc-scale", type=float, default=0.0,
                    help="0 = bỏ nhánh CTC khi fine-tune (ta chỉ giải mã bằng transducer)")
    ap.add_argument("--freeze-stacks", type=int, default=0,
                    help="đóng băng N tầng encoder đầu (chống quên kiến thức nền)")
    ap.add_argument("--max-sec", type=float, default=14.0)
    ap.add_argument("--seed", type=int, default=20260916)
    ap.add_argument("--limit-train", type=int, default=0)
    ap.add_argument("--log-every", type=int, default=20)
    ap.add_argument("--batch-count-start", type=float, default=8000.0,
                    help="giá trị `batch_count` ban đầu — phải VƯỢT warmup_batches (4000) vì nền đã hội tụ")
    ap.add_argument("--save-every", type=int, default=150,
                    help="lưu checkpoint giữa epoch. Mẻ chạy qua đêm bị ngắt giữa epoch mà chỉ lưu ở "
                         "cuối epoch thì mất SẠCH — không có gì để chấm, không có gì để tiếp.")
    ap.add_argument("--torch-threads", type=int, default=0,
                    help="0 = để torch tự chọn. [ĐO] mặc định chỉ dùng ~5/15 lõi trên máy này ⇒ đặt tay.")
    args = ap.parse_args()

    torch.manual_seed(args.seed)
    random.seed(args.seed)
    if args.torch_threads:
        torch.set_num_threads(args.torch_threads)
    print(f"== torch dùng {torch.get_num_threads()} luồng", flush=True)
    os.makedirs(args.out, exist_ok=True)
    device = torch.device(args.device)

    sp = spm.SentencePieceProcessor()
    sp.load(os.path.join(args.base, "bpe.model"))
    vocab = sum(1 for _ in open(os.path.join(args.base, "tokens.txt"), encoding="utf-8"))
    assert sp.get_piece_size() == vocab, (sp.get_piece_size(), vocab)

    tr_rows, dv_rows = [], []
    for mf, root in zip(args.manifest, args.audio_root):
        tr_rows += load_manifest([mf], {"train"}, root)
        dv_rows += load_manifest([mf], {"dev"}, root)
    random.shuffle(tr_rows)
    if args.limit_train:
        tr_rows = tr_rows[:args.limit_train]
    print(f"== train {len(tr_rows)} clip ({sum(r[2] for r in tr_rows)/3600:.2f} h) · "
          f"dev {len(dv_rows)} clip ({sum(r[2] for r in dv_rows)/3600:.2f} h)", flush=True)

    model = build_model(vocab, device)
    sd = torch.load(os.path.join(args.base, "model.pt"), map_location="cpu",
                    weights_only=False)["model"]
    missing, unexpected = model.load_state_dict(sd, strict=False)
    if missing or unexpected:
        print(f"!! thiếu {len(missing)}: {missing[:5]}\n!! thừa {len(unexpected)}: {unexpected[:5]}")
        # Không im lặng đi tiếp: sai kiến trúc thì mọi số sau đó vô nghĩa (CLAUDE.md §8).
        if len(missing) > 4 or len(unexpected) > 4:
            sys.exit("kiến trúc KHÔNG khớp checkpoint — dừng")
    print(f"== nạp checkpoint OK · {sum(p.numel() for p in model.parameters())/1e6:.2f} M tham số",
          flush=True)

    if args.freeze_stacks:
        for i in range(args.freeze_stacks):
            for p in model.encoder.encoders[i].parameters():
                p.requires_grad = False
        for p in model.encoder_embed.parameters():
            p.requires_grad = False
        print(f"== đóng băng encoder_embed + {args.freeze_stacks} tầng encoder đầu")

    ds_tr = WavSet(tr_rows, sp, args.max_sec)
    ds_dv = WavSet(dv_rows, sp, args.max_sec)
    dl_tr = torch.utils.data.DataLoader(ds_tr, batch_size=args.batch, shuffle=True,
                                        num_workers=args.workers, collate_fn=collate,
                                        drop_last=True, persistent_workers=args.workers > 0)
    dl_dv = torch.utils.data.DataLoader(ds_dv, batch_size=args.batch, shuffle=False,
                                        num_workers=max(1, args.workers // 2), collate_fn=collate)

    # ⚠ `ScaledAdam` KHÔNG nhận `model.parameters()` kiểu Adam thường: icefall nạp nó bằng
    # `get_parameter_groups_with_lrs(model, lr=..., include_names=True)` (`train.py:1269`), và `lr` ở đây là
    # **hệ số tương đối**, không phải bước học tuyệt đối. Bản fine-tune chính thức của icefall dùng
    # `--base-lr 0.0045` (`finetune.py:35`), tức 1/10 của 0.045 khi huấn luyện từ đầu.
    # Đặt 2.5e-4 kiểu Adam vào đây là gần như KHÔNG học gì — đọc nguồn thay vì suy từ thói quen (§3).
    # ⚠ **Bắt buộc**: icefall gọi `set_batch_count(model, …)` MỖI batch (`train.py:1106`). Zipformer dùng
    # `batch_count` để nội suy hàng loạt `ScheduledFloat` và lịch **bỏ tầng theo warmup**
    # (`zipformer.py:185` — `warmup_begin/warmup_end` tính theo chỉ số batch). Không đặt thì `batch_count`
    # đứng ở 0 ⇒ mô hình chạy đúng chế độ *"vừa khởi tạo, đang warm-up"*: ngẫu nhiên che bớt tầng, các
    # tham số lịch ở giá trị đầu. Đem chế độ đó áp lên một checkpoint ĐÃ HỘI TỤ là **phá** nó, chứ không
    # phải fine-tune nó — và sẽ không có lỗi nào báo ra, chỉ có số cuối cùng tệ đi mà không rõ vì sao.
    # ⇒ bắt đầu từ `--batch-count-start` (mặc định 2× warmup_batches) rồi tăng đều: mô hình được đối xử
    # như thứ nó thật sự là — một mô hình đã qua warmup từ lâu.
    from train import set_batch_count
    from optim import ScaledAdam, Eden
    from icefall.utils import get_parameter_groups_with_lrs
    opt = ScaledAdam(get_parameter_groups_with_lrs(model, lr=args.lr, include_names=True),
                     lr=args.lr, clipping_scale=2.0)
    sched = Eden(opt, args.lr_batches, args.lr_epochs)

    def run_batch(b, train: bool):
        x, xl, y = b
        x, xl = x.to(device), xl.to(device)
        # [ĐO] `AsrModel.forward` của icefall bản hiện tại trả **5** giá trị
        # (`model.py:481`: simple · pruned · ctc · attention_decoder · cr) — không phải 3 như bản cũ.
        # Đọc từ nguồn thay vì nhớ, đúng CLAUDE.md §3; lần đầu đoán 3 và nó nổ ngay ở batch đầu.
        simple, pruned, ctc, _att, _cr = model(x=x, x_lens=xl, y=y, prune_range=args.prune_range,
                                               am_scale=args.am_scale, lm_scale=args.lm_scale)
        loss = args.simple_loss_scale * simple + pruned
        if args.ctc_scale > 0:
            loss = loss + args.ctc_scale * ctc
        n = int(xl.sum())
        return loss, n

    # ── đo thông lượng rồi thoát ──────────────────────────────────────────────────────────────────
    if args.probe > 0:
        model.train()
        want = args.probe * 60.0
        done_audio = 0.0
        t0 = time.time()
        nb = 0
        for b in dl_tr:
            set_batch_count(model, args.batch_count_start + nb)
            loss, _ = run_batch(b, True)
            opt.zero_grad(set_to_none=True)
            loss.backward()
            opt.step()
            done_audio += float(b[1].sum()) * 0.01     # khung 10 ms
            nb += 1
            if nb % 5 == 0:
                el = time.time() - t0
                print(f"   probe {nb} batch · {done_audio:.0f}s audio · {el:.0f}s · "
                      f"{done_audio/el:.2f}× realtime", flush=True)
            if done_audio >= want:
                break
        el = time.time() - t0
        rate = done_audio / el
        tot_h = sum(r[2] for r in tr_rows) / 3600.0
        print(f"\n== THÔNG LƯỢNG: {rate:.2f}× realtime ({done_audio:.0f}s audio trong {el:.0f}s, "
              f"{nb} batch, batch={args.batch}, device={args.device})")
        print(f"== 1 epoch trên {tot_h:.2f} h dữ liệu ⇒ **{tot_h*3600/rate/3600:.2f} giờ**")
        print(f"== {args.epochs} epoch ⇒ **{args.epochs*tot_h*3600/rate/3600:.2f} giờ**")
        return 0

    best = float("inf")
    t0 = time.time()
    for ep in range(1, args.epochs + 1):
        model.train()
        tot, ntok, nb = 0.0, 0, 0
        for b in dl_tr:
            set_batch_count(model, args.batch_count_start + (ep - 1) * len(dl_tr) + nb)
            loss, n = run_batch(b, True)
            opt.zero_grad(set_to_none=True)
            loss.backward()
            opt.step()
            sched.step_batch(nb)
            tot += float(loss)
            ntok += n
            nb += 1
            if args.save_every and nb % args.save_every == 0:
                torch.save({"model": model.state_dict(), "epoch": ep, "batch": nb, "dev": -1.0},
                           os.path.join(args.out, "latest.pt"))
            if nb % args.log_every == 0:
                el = time.time() - t0
                # in cả tốc độ: không có nó thì không biết mẻ chạy đêm có kịp sáng hay không
                aud = sum(float(x) for x in [0]) or None
                print(f"   ep{ep} b{nb}/{len(dl_tr)} loss {tot/max(ntok,1):.4f} "
                      f"{el:.0f}s ({nb/el*60:.1f} batch/phút, còn "
                      f"{(len(dl_tr)-nb)/max(nb/el,1e-9)/60:.0f} phút cho epoch này)", flush=True)
        sched.step_epoch(ep)
        model.eval()
        dtot, dn = 0.0, 0
        set_batch_count(model, args.batch_count_start + ep * len(dl_tr))
        with torch.no_grad():
            for b in dl_dv:
                loss, n = run_batch(b, False)
                dtot += float(loss)
                dn += n
        dev = dtot / max(dn, 1)
        mark = ""
        if dev < best:
            best = dev
            torch.save({"model": model.state_dict(), "epoch": ep, "dev": dev},
                       os.path.join(args.out, "best.pt"))
            mark = "  ← lưu"
        torch.save({"model": model.state_dict(), "epoch": ep, "dev": dev},
                   os.path.join(args.out, f"epoch-{ep}.pt"))
        print(f"ep {ep}  train {tot/max(ntok,1):.4f}  dev {dev:.4f}  "
              f"{time.time()-t0:.0f}s{mark}", flush=True)
    print(f"== xong · dev tốt nhất {best:.4f} · {time.time()-t0:.0f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
