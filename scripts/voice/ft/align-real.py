#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Cắt bản thu giọng THẬT thành 30 câu × 3 lượt, bằng chính mốc *"câu &lt;số&gt;"* người đọc nói ra.

## Vì sao phải cắt bằng máy, và cắt kiểu này

Bản thu là **một** tệp liền 200 s: người đọc nói *"câu một"* rồi mới đọc câu, hết 30 câu thì quay lại câu 1
cho lượt sau. Muốn chấm được thì phải biết mỗi câu bắt đầu/kết thúc ở giây nào — mà VAD thuần **không** làm
được: ở lượt đọc nhanh, mốc và câu dính liền không có khoảng lặng nào ở giữa.

Nên thuật toán ở đây không cắt theo khoảng lặng mà cắt theo **mốc số đếm**:

 1. Silero VAD lấy các đoạn có tiếng (chỉ để khỏi giải mã cả vùng im lặng).
 2. Giải mã từng đoạn **kèm mốc thời gian từng token** (`result.timestamps`) → một dòng token có thời gian
    chạy suốt cả tệp.
 3. Quét dòng token tìm chuỗi chữ-số của **đúng** số thứ tự đang chờ (1, rồi 2, … 30, rồi quay lại 1).
    Khớp đúng số đang chờ ⇒ tránh hẳn chuyện *"mười"* của câu 10 ăn nhầm vào *"mười một"* của câu 11:
    ta luôn thử chuỗi **dài nhất** tại mỗi vị trí trước.
 4. Câu thứ N = đoạn audio từ **hết** mốc N tới **đầu** mốc N+1.

Mốc bị nghe nhầm phần chữ (*"khu chín"*, *"con mười lăm"*) không sao — ta chỉ khớp phần **số**, và phần số
là thứ mô hình nghe đúng trong mọi trường hợp đã quan sát [ĐO 2026-09-16].

## Đây là bộ GIỮ LẠI — luật dùng

Bộ này **không bao giờ** được đưa vào huấn luyện, và **không** được dùng để chọn `lm_scale`, chọn epoch, hay
chọn bất cứ tham số nào (spec `kachi-voice-finetune` R-nf1). Chấm **một lần** ở cuối mỗi pha.
Tệp gốc là dữ liệu cá nhân: nằm ngoài repo, **không** chép vào repo, **không** kèm vào báo cáo.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, os.path.join(REPO, "scripts", "voice"))

from vi_text import norm as vi_norm, vi_number  # noqa: E402

# 30 câu của `docs/diagnostics/voice-recording-campaign-2026-09-16.md` §4, đúng thứ tự.
REFS = [
    "lọc ngay", "bật lọc bụi", "điều hoà", "mở Google Map", "bật đèn đọc", "mở YouTube",
    "mở máy lạnh", "bật điều hoà hai mươi hai độ", "nhiệt độ hai mươi bốn độ", "tăng gió",
    "giảm âm lượng", "mở kính bên lái", "hạ kiếng trước trái", "đóng hết kính", "mở hết kính giùm",
    "xem pin", "pin còn bao nhiêu", "còn đi được bao xa", "xem áp suất lốp trước trái",
    "bật ghế sưởi", "mở cốp sau", "mở cửa hậu", "phát nhạc", "dừng nhạc", "bài tiếp theo",
    "dẫn đường đến chợ Bến Thành", "mở quây", "mở du túp", "chế độ lái thể thao",
    "hôm nay trời đẹp quá",
]


def read_wave(path: str) -> np.ndarray:
    with wave.open(path, "rb") as f:
        if f.getframerate() != 16000 or f.getnchannels() != 1 or f.getsampwidth() != 2:
            sys.exit(f"{path}: cần PCM16 · mono · 16 kHz")
        return np.frombuffer(f.readframes(f.getnframes()), dtype="<i2").astype("float32") / 32768.0


def write_wave(path: str, data: np.ndarray):
    with wave.open(path, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(16000)
        f.writeframes((np.clip(data, -1, 1) * 32767).astype("<i2").tobytes())


def number_words(n: int) -> list[str]:
    return vi_number(n).split()


def vad_segments(wav: np.ndarray, vad_model: str, min_silence: float) -> list[tuple[float, float]]:
    import sherpa_onnx
    cfg = sherpa_onnx.VadModelConfig()
    cfg.silero_vad.model = vad_model
    cfg.silero_vad.threshold = 0.5
    cfg.silero_vad.min_silence_duration = min_silence
    cfg.silero_vad.min_speech_duration = 0.10
    cfg.sample_rate = 16000
    vad = sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=300)
    out: list[tuple[float, float]] = []
    win = 512
    for i in range(0, len(wav), win):
        vad.accept_waveform(wav[i:i + win])
        while not vad.empty():
            s = vad.front
            out.append((s.start / 16000.0, (s.start + len(s.samples)) / 16000.0))
            vad.pop()
    vad.flush()
    while not vad.empty():
        s = vad.front
        out.append((s.start / 16000.0, (s.start + len(s.samples)) / 16000.0))
        vad.pop()
    return out


def token_stream(wav, segs, model_dir, threads):
    """Giải mã từng đoạn VAD, trả dòng (chữ, t_bắt_đầu, t_kết_thúc) chạy suốt cả tệp."""
    import sherpa_onnx
    rec = sherpa_onnx.OfflineRecognizer.from_transducer(
        tokens=os.path.join(model_dir, "tokens.txt"),
        encoder=os.path.join(model_dir, "encoder.onnx"),
        decoder=os.path.join(model_dir, "decoder.onnx"),
        joiner=os.path.join(model_dir, "joiner.onnx"),
        num_threads=threads, decoding_method="modified_beam_search", max_active_paths=4)
    toks: list[tuple[str, float, float]] = []
    for (t0, t1) in segs:
        st = rec.create_stream()
        st.accept_waveform(16000, wav[int(t0 * 16000):int(t1 * 16000)])
        rec.decode_stream(st)
        r = st.result
        # gộp mảnh BPE thành TỪ: mảnh bắt đầu bằng '▁' (sherpa trả ra dấu cách ở đầu) mở từ mới
        cur, cur_t = "", None
        for piece, ts in zip(r.tokens, r.timestamps):
            if piece.startswith(" ") or piece.startswith("▁"):
                if cur:
                    toks.append((cur.strip().lower(), cur_t + t0, ts + t0))
                cur, cur_t = piece.strip(), ts
            else:
                cur += piece
        if cur:
            toks.append((cur.strip().lower(), (cur_t or 0.0) + t0, t1))
    return toks


def _sim(a: str, b: str) -> float:
    """Giống nhau ở mức CHỮ, đã bỏ dấu — để *"ping"* vẫn kéo được về *"pin"*.

    Khớp đúng-từng-chữ là quá giòn ở đây: [ĐO] người đọc nói tắt số thứ tự (*"hai mốt"* thay
    *"hai mươi mốt"*, *"hâm bảy"* thay *"hai mươi bảy"*), và ở lượt đọc nhanh còn bỏ hẳn hàng chục
    (*"sáu xem phim"* cho câu 16). Nên phải căn theo **toàn cục**, chịu được thay/thiếu/thừa từ.
    """
    import difflib
    da = unicodedata.normalize("NFD", a)
    db = unicodedata.normalize("NFD", b)
    da = "".join(c for c in da if not unicodedata.combining(c)).lower()
    db = "".join(c for c in db if not unicodedata.combining(c)).lower()
    if da == db:
        return 1.0
    return difflib.SequenceMatcher(None, da, db).ratio()


def align_global(words: list[str], expect: list[tuple[str, int]], gap: float = -0.62):
    """Căn TOÀN CỤC (Needleman-Wunsch) dòng từ nghe được với kịch bản đã biết.

    `expect` là [(từ, nhãn)] với nhãn = chỉ số câu (hoặc -1 cho từ của mốc số đếm).
    Trả `hits[nhãn] = [chỉ số từ nghe được]`, theo đúng thứ tự thời gian.

    Vì sao căn toàn cục chứ không dò mốc: dò mốc chỉ cần **một** mốc nghe sai là mất đồng bộ cả đoạn
    sau — [ĐO] đúng chuyện đã xảy ra ở lượt 2 và 3 (mất 60/90 câu). Căn toàn cục dùng được **mọi** từ
    làm neo, kể cả khi số thứ tự hỏng hoàn toàn.
    """
    n, m = len(words), len(expect)
    NEG = -1e9
    prev = [0.0] + [gap * (j + 1) for j in range(m)]
    bt = np.zeros((n + 1, m + 1), dtype=np.int8)     # 0=chéo 1=lên(bỏ từ nghe) 2=trái(bỏ từ kịch bản)
    bt[0, 1:] = 2
    bt[1:, 0] = 1
    for i in range(1, n + 1):
        cur = [prev[0] + gap] + [NEG] * m
        wi = words[i - 1]
        for j in range(1, m + 1):
            d = prev[j - 1] + (2.0 * _sim(wi, expect[j - 1][0]) - 1.0)
            u = prev[j] + gap
            l = cur[j - 1] + gap
            best = d
            k = 0
            if u > best:
                best, k = u, 1
            if l > best:
                best, k = l, 2
            cur[j] = best
            bt[i, j] = k
        prev = cur
    hits: dict[int, list[int]] = {}
    i, j = n, m
    while i > 0 or j > 0:
        k = bt[i, j]
        if i > 0 and j > 0 and k == 0:
            hits.setdefault(expect[j - 1][1], []).append(i - 1)
            i -= 1
            j -= 1
        elif i > 0 and (j == 0 or k == 1):
            i -= 1
        else:
            j -= 1
    for v in hits.values():
        v.reverse()
    return hits


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--wav", required=True)
    ap.add_argument("--model", required=True, help="mô hình dùng ĐỂ CẮT (không phải để chấm)")
    ap.add_argument("--vad", default="/tmp/silero_vad.onnx")
    ap.add_argument("--out", required=True)
    ap.add_argument("--passes", type=int, default=3)
    ap.add_argument("--pass-names", nargs="+", default=["A_thuong", "B_nhanh", "C_on"])
    ap.add_argument("--min-silence", type=float, default=0.12)
    ap.add_argument("--threads", type=int, default=6)
    ap.add_argument("--pad-end", type=float, default=0.12, help="giữ thêm chút đuôi cho an toàn")
    args = ap.parse_args()

    wav = read_wave(args.wav)
    segs = vad_segments(wav, args.vad, args.min_silence)
    print(f"== VAD {len(segs)} đoạn trên {len(wav)/16000:.1f} s")
    toks = token_stream(wav, segs, args.model, args.threads)
    print(f"== {len(toks)} từ có mốc thời gian")

    # Kịch bản đã biết: mỗi lượt = 30 lần (mốc "câu <số>" + câu). Mốc mang nhãn -1 ⇒ KHÔNG cắt vào clip.
    # Nhãn: +key cho từ của CÂU, −key cho từ của MỐC ("câu <số>"). Cắt theo khoảng MỐC→MỐC chứ không
    # theo đúng những từ khớp được: ở lượt đọc nhanh nhiều câu không có từ nào nghe đúng, và nếu cắt theo
    # từ khớp thì biên nhảy sang câu bên cạnh — [ĐO] đã ra những đoạn vô nghĩa kiểu *"bao nhiêu tết"*.
    expect: list[tuple[str, int]] = []
    for p in range(args.passes):
        for s in range(1, len(REFS) + 1):
            key = p * len(REFS) + s
            expect.append(("câu", -key))
            for w in number_words(s):
                expect.append((w, -key))
            for w in vi_norm(REFS[s - 1]).split():
                expect.append((w, key))
    words = [t[0] for t in toks]
    hits = align_global(words, expect)
    got = sum(1 for k in hits if k > 0)
    print(f"== căn toàn cục: {len(words)} từ nghe được ↔ {len(expect)} từ kịch bản; "
          f"{got}/{args.passes*len(REFS)} câu có neo · "
          f"{sum(1 for k in hits if k < 0)}/{args.passes*len(REFS)} mốc có neo")

    os.makedirs(args.out, exist_ok=True)
    rows = []
    n_all = args.passes * len(REFS)
    for p in range(args.passes):
        for s in range(1, len(REFS) + 1):
            key = p * len(REFS) + s
            mk, sent = hits.get(-key), hits.get(key)
            nxt_mk = hits.get(-(key + 1)) if key < n_all else None
            if mk:
                t0 = toks[mk[-1]][2] - 0.02
            elif sent:
                t0 = toks[sent[0]][1] - 0.08
            else:
                print(f"!! bỏ {args.pass_names[p]}/{s:02d}: không neo được mốc lẫn câu")
                continue
            if nxt_mk:
                t1 = toks[nxt_mk[0]][1] - 0.04
            elif sent:
                t1 = toks[sent[-1]][2] + args.pad_end
            else:
                t1 = t0 + 2.5
            if sent:
                # Có neo câu ⇒ siết biên về đúng câu, nhưng KHÔNG vượt qua mốc kế bên. Nới rộng thì lọt
                # từ của mốc sau vào clip (*"…PHÁT NHẠC CON"*), siết quá thì cụt đầu/đuôi câu — lấy giao.
                t0 = max(t0, toks[sent[0]][1] - 0.15)
                t1 = min(t1, toks[sent[-1]][2] + args.pad_end)
            t0 = max(0.0, t0)
            t1 = min(t1, len(wav) / 16000.0)
            if t1 - t0 < 0.25:
                print(f"!! bỏ {args.pass_names[p]}/{s:02d}: đoạn quá ngắn ({t1-t0:.2f}s)")
                continue
            name = f"{args.pass_names[p] if p < len(args.pass_names) else f'P{p}'}-{s:02d}"
            write_wave(os.path.join(args.out, f"{name}.wav"),
                       wav[int(t0 * 16000):int(t1 * 16000)])
            rows.append(dict(uid=name,
                             pas=args.pass_names[p] if p < len(args.pass_names) else f"P{p}",
                             idx=s, t0=round(t0, 3), t1=round(t1, 3), ref=REFS[s - 1]))

    with open(os.path.join(args.out, "cases.tsv"), "w", encoding="utf-8") as f:
        for r in rows:
            f.write(f'{r["uid"]}\t{r["ref"]}\n')
    with open(os.path.join(args.out, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, indent=1)
    print(f"== ghi {len(rows)} tệp vào {args.out}")
    for pn in args.pass_names:
        n = sum(1 for r in rows if r["pas"] == pn)
        print(f"   {pn}: {n}/30")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
