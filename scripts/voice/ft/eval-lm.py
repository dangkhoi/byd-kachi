#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Chấm một mô hình (± RNN-LM hợp nhất nông) trên corpus + 25 WAV — spec `kachi-voice-finetune` P1.5.

Khác `mishear-table.py` ở ba chỗ, và chỉ ba chỗ:
 1. nhận `--lm` + `--lm-scale` (tham số `lm=` / `lm_scale=` có thật trong `OfflineRecognizer.from_transducer`
    của sherpa-onnx 1.13.8 — [ĐO] bằng `inspect.signature`, không dựa trí nhớ);
 2. tự nhận tệp `*.int8.onnx` hay `*.onnx`, nên chấm được các gói tải về không cùng cách đặt tên;
 3. **tách cột ĐÃ THẤY / CHƯA THẤY** theo danh sách câu giữ lại của LM (`--holdout`). Đây là cột duy nhất
    nói được điều gì về khái quát hoá; cột "đã thấy" chỉ nói LM đã thuộc bài (spec R-nf1).

Chuẩn hoá chữ dùng chung `vi_text.norm()` với `mishear-table.py` / `stream-matrix.py`, nên số ghép thẳng
được vào `voice-mishear-2026-09-16.md` và `voice-stream-eval-2026-09-16.md`.
"""
from __future__ import annotations

import argparse
import collections
import glob
import json
import os
import sys
import time
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(REPO, "scripts", "voice"))

from vi_text import norm as vi_norm  # noqa: E402

# Bộ phân tích XẤP XỈ dùng lại nguyên si của `mishear-table.py` (DRY — CLAUDE.md §4.1). Nó KHÔNG phải
# `VoiceIntentParser` thật (host không chạy Kotlin), nên cột "ý định" đọc ở mức [SUY]: dùng để xếp loại
# và so giữa các mô hình, KHÔNG dùng để tuyên bố một câu chạy được trên xe.
from importlib.machinery import SourceFileLoader  # noqa: E402

_mt = SourceFileLoader("mishear_table",
                       os.path.join(REPO, "scripts", "voice", "mishear-table.py")).load_module()

try:
    from phonetic_pairs import HEARD_TO_REF  # type: ignore
except ImportError:                                   # bảng chưa sinh ⇒ chạy ở chế độ nghiêm ngặt
    HEARD_TO_REF = {}


def soften(text: str) -> str:
    """Tha đúng những cặp nghe-nhầm ĐÃ ĐO, mô phỏng `VoicePhoneticMatch` của `:core`.

    Vì sao cần: bộ chấm nghiêm ngặt tính *"mở CẤP sau"* là sai ý định, trong khi trên xe
    `VoicePhoneticMatch` vẫn quy nó về `trunk` — tức số của host **thấp hơn** thực tế một cách hệ thống.
    Ở đây chỉ thay **từng từ** theo bảng `OBSERVED` (sinh từ Kotlin, không chép tay), không dựng lại cả
    bảng giá âm tiết: dựng lại là chép logic của người khác sang ngôn ngữ khác rồi để nó trôi.

    ⚠ Thay từng-từ là phép **thô hơn** bảng giá âm tiết của Kotlin: nó có thể phá một câu vốn đã khớp
    (vd `quay` → `quây` làm hỏng một nhánh đang đúng). Nên cột "nới" đếm câu đúng **ở bản gốc HOẶC bản đã
    thay** — như vậy mới thực sự là **cận TRÊN**. [ĐO] không nới: 202/270; nới-thay-thô một chiều: 200/270
    (tụt 2 câu) ⇒ đúng là phải lấy hợp của hai, không lấy riêng bản thay.

    ⇒ "ý định" nghiêm ngặt = **cận DƯỚI**, "ý định (nới)" = **cận TRÊN**. Sự thật trên xe nằm giữa.
    Báo cáo phải đưa cả hai, không được chọn cột đẹp hơn.
    """
    if not HEARD_TO_REF:
        return text
    return " ".join(HEARD_TO_REF.get(w, w) for w in text.split())


def read_wave(path: str) -> np.ndarray:
    with wave.open(path, "rb") as f:
        if f.getframerate() != 16000 or f.getnchannels() != 1 or f.getsampwidth() != 2:
            sys.exit(f"{path}: cần PCM16 · mono · 16 kHz")
        data = f.readframes(f.getnframes())
    return np.frombuffer(data, dtype="<i2").astype("float32") / 32768.0


def pick(model_dir: str, stem: str) -> str:
    """Chọn tệp mô hình: ưu tiên bản không hậu tố, rồi .int8, rồi bất cứ tên nào bắt đầu bằng stem."""
    for cand in (f"{stem}.onnx", f"{stem}.int8.onnx"):
        p = os.path.join(model_dir, cand)
        if os.path.isfile(p):
            return p
    hits = sorted(glob.glob(os.path.join(model_dir, f"{stem}*.onnx")))
    if not hits:
        sys.exit(f"{model_dir}: không thấy tệp {stem}*.onnx")
    return hits[0]


def ensure_bpe_vocab(model_dir: str) -> str:
    p = os.path.join(model_dir, "bpe_vocab.txt")
    if not os.path.isfile(p):
        import sentencepiece as spm
        sp = spm.SentencePieceProcessor(model_file=os.path.join(model_dir, "bpe.model"))
        with open(p, "w", encoding="utf-8") as f:
            for i in range(sp.get_piece_size()):
                f.write(f"{sp.id_to_piece(i)} {sp.get_score(i)}\n")
    return p


def make_recognizer(model_dir: str, hotwords: str | None, score: float,
                    lm: str | None, lm_scale: float, threads: int):
    import sherpa_onnx
    kw = dict(
        tokens=os.path.join(model_dir, "tokens.txt"),
        encoder=pick(model_dir, "encoder"),
        decoder=pick(model_dir, "decoder"),
        joiner=pick(model_dir, "joiner"),
        num_threads=threads, sample_rate=16000, feature_dim=80,
        decoding_method="modified_beam_search", max_active_paths=4, provider="cpu", debug=False,
    )
    if hotwords:
        kw.update(hotwords_file=hotwords, hotwords_score=score, modeling_unit="bpe",
                  bpe_vocab=ensure_bpe_vocab(model_dir))
    if lm:
        # LM chỉ có tác dụng với modified_beam_search — greedy bỏ qua hoàn toàn (đọc
        # sherpa-onnx/csrc/offline-recognizer-transducer-impl.h). Ta vẫn luôn dùng beam search nên không sao.
        kw.update(lm=lm, lm_scale=lm_scale)
    return sherpa_onnx.OfflineRecognizer.from_transducer(**kw)


def load_items(corpus: str | None, wav_dir: str | None) -> list[dict]:
    items: list[dict] = []
    if corpus:
        for line in open(os.path.join(corpus, "manifest.tsv"), encoding="utf-8"):
            if line.startswith("#") or "\t" not in line:
                continue
            w, uid, rid, ikind, region, style, voice, rate, tier, ref = line.rstrip("\n").split("\t")
            items.append(dict(path=os.path.join(corpus, w), uid=uid, id=rid, kind=ikind,
                              region=region, style=style, voice=voice, rate=rate, ref=ref,
                              src="corpus"))
    if wav_dir and os.path.isfile(os.path.join(wav_dir, "cases.tsv")):
        for line in open(os.path.join(wav_dir, "cases.tsv"), encoding="utf-8"):
            if "\t" in line:
                wid, ref = line.rstrip("\n").split("\t", 1)
                items.append(dict(path=os.path.join(wav_dir, f"{wid}.wav"), uid=wid, id=f"wav_{wid}",
                                  kind="wav25", region="chung", style="ngan", voice="linh",
                                  rate="180", ref=ref, src="wav25"))
    return items


def load_real(real_dir: str) -> list[dict]:
    """Bộ GIỮ LẠI giọng người thật (`align-real.py`). Chấm MỘT LẦN, không dùng chọn tham số (R-nf1)."""
    items = []
    for line in open(os.path.join(real_dir, "cases.tsv"), encoding="utf-8"):
        if "\t" not in line:
            continue
        uid, ref = line.rstrip("\n").split("\t", 1)
        pas = uid.split("-")[0]
        items.append(dict(path=os.path.join(real_dir, f"{uid}.wav"), uid=uid, id=uid, kind=pas,
                          region="that", style=pas, voice="nguoi", rate="-", ref=ref,
                          src=f"real/{pas}"))
    return items


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", required=True)
    ap.add_argument("--corpus", default="/tmp/kachi-voice-corpus")
    ap.add_argument("--wav", default="/tmp/kachi-voice-wav")
    ap.add_argument("--hotwords", default=os.path.join(REPO, "core/build/hotwords/hotwords-phrases.txt"),
                    help="`none` để tắt")
    ap.add_argument("--score", type=float, default=3.0)
    ap.add_argument("--lm", default="", help="tệp rnnlm .onnx (rỗng = không dùng)")
    ap.add_argument("--lm-scale", type=float, nargs="+", default=[0.0],
                    help="một hoặc nhiều mức để quét lưới")
    ap.add_argument("--holdout", default="", help="danh sách câu LM CHƯA thấy (1 câu/dòng)")
    ap.add_argument("--threads", type=int, default=2)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--dump", default="")
    ap.add_argument("--label", default="")
    ap.add_argument("--real", default="", help="thư mục bộ giữ lại giọng thật (align-real.py)")
    ap.add_argument("--registry", default=os.path.join(REPO, "core/build/catalog/registry.json"))
    args = ap.parse_args()

    terms = _mt.build_terms(json.load(open(args.registry, encoding="utf-8")))

    hot = None if args.hotwords in ("", "none") else args.hotwords
    items = (load_real(args.real) if args.real
             else load_items(args.corpus or None, args.wav or None))
    if args.limit:
        items = items[:args.limit]
    holdout = set()
    if args.holdout and os.path.isfile(args.holdout):
        holdout = {l.strip() for l in open(args.holdout, encoding="utf-8") if l.strip()}

    scales = args.lm_scale if args.lm else [0.0]
    rows_out = []
    for sc in scales:
        t0 = time.time()
        rec = make_recognizer(args.model, hot, args.score, args.lm or None, sc, args.threads)
        cache: dict[str, str] = {}
        recs = []
        for k, it in enumerate(items):
            p = it["path"]
            if p not in cache:
                st = rec.create_stream()
                st.accept_waveform(16000, read_wave(p))
                rec.decode_stream(st)
                cache[p] = st.result.text.strip()
            recs.append(cache[p])
            if k and k % 500 == 0:
                print(f"  [{sc}] {k}/{len(items)} … {time.time()-t0:.0f}s", flush=True)
        dt = time.time() - t0

        agg = collections.defaultdict(lambda: [0, 0])          # bucket -> [đúng chữ, tổng]
        agg_i = collections.defaultdict(lambda: [0, 0])        # bucket -> [đúng Ý ĐỊNH nghiêm, tổng]
        agg_s = collections.defaultdict(lambda: [0, 0])        # bucket -> [đúng Ý ĐỊNH đã nới, tổng]
        by_kind = collections.defaultdict(lambda: [0, 0])
        by_region = collections.defaultdict(lambda: [0, 0])
        by_style = collections.defaultdict(lambda: [0, 0])
        for it, hyp in zip(items, recs):
            ref_n = vi_norm(it["ref"])
            ok = int(vi_norm(hyp) == ref_n)
            # Ý ĐỊNH: nghe sai chữ mà vẫn trỏ về đúng mã ⇒ trên xe vẫn chạy đúng. Câu tham chiếu không
            # phân tích ra mã nào (vd câu KHÔNG phải lệnh) ⇒ đúng khi bản nghe cũng không ra mã nào.
            ri = _mt.approx_parse(ref_n, terms)[1]
            hi = _mt.approx_parse(vi_norm(hyp), terms)[1]
            oki = int(ri == hi)
            hs = _mt.approx_parse(soften(vi_norm(hyp)), terms)[1]
            oks = int(oki == 1 or ri == hs)      # HỢP của hai ⇒ cận trên thật sự (xem KDoc `soften`)
            seen = "chua_thay" if ref_n in holdout else "da_thay"
            for b in (it["src"], f'{it["src"]}/{seen}'):
                agg[b][0] += ok
                agg[b][1] += 1
                agg_i[b][0] += oki
                agg_i[b][1] += 1
                agg_s[b][0] += oks
                agg_s[b][1] += 1
            agg["TỔNG"][0] += ok
            agg["TỔNG"][1] += 1
            agg_i["TỔNG"][0] += oki
            agg_i["TỔNG"][1] += 1
            agg_s["TỔNG"][0] += oks
            agg_s["TỔNG"][1] += 1
            if it["src"] == "corpus":
                by_kind[it["kind"]][0] += ok
                by_kind[it["kind"]][1] += 1
                by_region[it["region"]][0] += ok
                by_region[it["region"]][1] += 1
                by_style[it["style"]][0] += ok
                by_style[it["style"]][1] += 1

        def p(b):
            ok, n = agg[b]
            oki, oks = agg_i[b][0], agg_s[b][0]
            return (f"chữ {ok}/{n} ({100*ok/n:.1f}%) · ý định {oki}/{n} ({100*oki/n:.1f}%)"
                    f" · nới {oks}/{n} ({100*oks/n:.1f}%)" if n else "—")

        tag = args.label or os.path.basename(args.model.rstrip("/"))
        print(f"== {tag} · lm_scale={sc} · {dt:.0f}s")
        for b in sorted(k for k in agg if k.startswith("real/")):
            print(f"   {b:18s} {p(b)}")
        if any(k.startswith("real/") for k in agg):
            print(f"   {'TỔNG (giữ lại)':18s} {p('TỔNG')}   ← chỉ tiêu CHÍNH R1")
        if "corpus" in agg:
            print(f"   corpus            {p('corpus')}")
        if holdout:
            print(f"   corpus ĐÃ THẤY    {p('corpus/da_thay')}")
            print(f"   corpus CHƯA THẤY  {p('corpus/chua_thay')}   ← số khái quát hoá")
        if "wav25" in agg:
            print(f"   25 WAV            {p('wav25')}")
        print("   intent_kind: " + "  ".join(
            f"{k} {100*v[0]/v[1]:.0f}%({v[1]})" for k, v in sorted(by_kind.items())))
        print("   region     : " + "  ".join(
            f"{k} {100*v[0]/v[1]:.0f}%({v[1]})" for k, v in sorted(by_region.items())))
        print("   style      : " + "  ".join(
            f"{k} {100*v[0]/v[1]:.0f}%({v[1]})" for k, v in sorted(by_style.items())), flush=True)

        rows_out.append(dict(model=tag, lm=os.path.basename(args.lm) if args.lm else "none",
                             lm_scale=sc, seconds=round(dt, 1),
                             agg={k: v for k, v in agg.items()},
                             agg_intent={k: v for k, v in agg_i.items()},
                             agg_intent_soft={k: v for k, v in agg_s.items()},
                             by_kind={k: v for k, v in by_kind.items()},
                             by_region={k: v for k, v in by_region.items()},
                             by_style={k: v for k, v in by_style.items()}))
        if args.dump:
            with open(f"{args.dump}.{sc}.tsv", "w", encoding="utf-8") as f:
                f.write("# uid\tkind\tregion\tstyle\tvoice\trate\tref\thyp\tok\tseen\n")
                for it, hyp in zip(items, recs):
                    ref_n = vi_norm(it["ref"])
                    f.write(f'{it["uid"]}\t{it["kind"]}\t{it["region"]}\t{it["style"]}\t{it["voice"]}\t'
                            f'{it["rate"]}\t{it["ref"]}\t{hyp}\t{int(vi_norm(hyp)==ref_n)}\t'
                            f'{"chua_thay" if ref_n in holdout else "da_thay"}\n')
    if args.dump:
        with open(f"{args.dump}.json", "w", encoding="utf-8") as f:
            json.dump(rows_out, f, ensure_ascii=False, indent=1)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
