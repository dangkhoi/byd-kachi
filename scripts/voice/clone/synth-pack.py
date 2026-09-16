# -*- coding: utf-8 -*-
"""
═══ XƯỞNG — SINH TỪNG CLIP CỦA GÓI "Giọng Kachi bé" BẰNG F5-TTS, CHẠY HOÀN TOÀN TRÊN MÁY ══════════════
Spec: docs/specs/kachi-voice-clone.html — R1 (xưởng tại máy, cùng seed ⇒ cùng audio, không API đám mây),
§4.6 (số đo pha xưởng), §6 V-pack (đọc lại TỪNG clip bằng chính bộ nhận dạng đang ship).

Chạy:
  SP=<scratchpad> REPO=<repo> /tmp/tts-venv/bin/python scripts/voice/clone/synth-pack.py \
      --clips $SP/voice-clone/clips.tsv --out $SP/voice-clone/pack-wav [--only fixed,head] [--limit 10]

## Bốn thứ khiến script này KHÔNG phải một vòng for đơn giản
 1. **Nối lại được.** ~1 600 clip × vài giây = vài giờ; máy ngủ, Terminal đóng, một `KeyboardInterrupt` —
    chạy lại phải đi tiếp. Trạng thái nằm ở `progress.tsv` (chỉ ghi thêm) + chính các tệp WAV đã ghi.
 2. **Trọng tài bằng máy.** Mỗi clip được ĐỌC LẠI bằng `zipformer-vi` + hotword thật (đúng bộ đang ship,
    `scripts/voice/hotword-matrix.py`). Sai chữ ⇒ sinh lại với seed khác, tối đa [SEEDS] lượt. Đây là
    phép duy nhất bắt được lỗi *"mô hình nuốt chữ"* trên ~1 600 clip mà tai người không nghe hết nổi.
 3. **Seed theo clip, không theo lượt chạy** — `seed = sha256(id)` ⇒ chạy lại hai lần ra audio y hệt
    (R1), và một clip phải sinh lại không kéo theo clip khác đổi giọng.
 4. **Gộp lượt suy diễn** (`--batch`). [ĐO] một lượt `infer` tốn ~20 s **bất kể câu dài hay ngắn**: mô
    hình sinh lại cả clip mẫu 8,29 s rồi mới cắt bỏ phần đầu. Với 1 000 clip số (~1 s mỗi clip) thì 93 %
    giờ máy là để sinh đi sinh lại cùng một clip mẫu. Gộp ~10 câu một lượt rồi cắt theo quãng lặng
    ([viclip.split_on_silence]) ⇒ cùng cái giá ấy chia cho 10. An toàn **vì** có (2) ở sau: mảnh nào cắt
    trượt thì ASR nghe ra chữ khác và nó bị sinh lại riêng.

## Bẫy đã trả giá — chép lại ở đây để người sau khỏi mất một giờ nữa
 • `torchaudio 2.11` đẩy đọc/ghi audio sang `torchcodec` (cần FFmpeg dựng sẵn). Xưởng chỉ đụng WAV PCM ⇒
   thay hai hàm bằng bản `soundfile` **trước khi** `f5_tts` import chúng (spec §9).
 • Clip mẫu phải **ngắn hơn ~12 s và kết bằng một quãng lặng**, nếu không F5 nối đuôi của chính clip mẫu
   vào cuối câu ngắn (*"đã bật đèn đọc"* → *"đã bật đèn quá"*). Dùng `be-refB.wav` 8,29 s (§4.6).
 • Câu không có dấu kết cũng bị mọc đuôi ⇒ thêm dấu chấm ([viclip.gen_text]).
 • Câu NGẮN bị F5 **bỏ đói thời lượng** (ước theo byte) ⇒ nới bằng `speed`, KHÔNG bằng
   `fix_duration` — xem chú thích tại [Shop.SPEEDS] về vì sao `fix_duration` bị gỡ.
"""
from __future__ import annotations

import argparse
import contextlib
import csv
import io
import os
import sys
import time

import numpy as np
import soundfile as sf
import torch

# ── thay torchaudio.load/save bằng soundfile TRƯỚC khi f5_tts import (xem KDoc) ───────────────────────
import torchaudio as _ta


def _ta_load(path, *a, **k):
    x, sr = sf.read(str(path), dtype="float32", always_2d=True)
    return torch.from_numpy(x.T.copy()), sr


def _ta_save(path, tensor, sr, *a, **k):
    x = tensor.detach().cpu().numpy()
    sf.write(str(path), x.T if x.ndim > 1 else x, sr, subtype="PCM_16")


_ta.load, _ta.save = _ta_load, _ta_save

REPO = os.environ.get("REPO") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from viclip import (  # noqa: E402
    PEAK, Judge, clip_seed, close, gen_text, same, split_on_silence, trim_silence,
)

# Số lượt sinh tối đa cho MỘT clip (lượt 1 + tối đa 1 lượt sinh lại).
#
# ⚠ Con số này là **cái núm đắt nhất** của cả xưởng, và nó được chọn bằng phép đếm chứ không bằng cảm
# giác. [ĐO 2026-09-17, 14 clip phải đi đường sinh riêng]: lượt 2 cứu được 2 clip, lượt 3 cứu thêm 1 —
# tức 3 clip được cứu, đổi bằng 28 lượt suy diễn thừa (~9 lượt ≈ 3 phút máy cho MỖI clip cứu được).
# Trên cả gói, để 3 là trả thêm khoảng 4 giờ máy cho ~75 clip. Để 2 là trả ~2 giờ cho ~50 clip.
#
# Chỗ này KHÔNG mất gì im lặng: clip không cứu được vẫn **vào gói** và vẫn bị **liệt kê đích danh**
# trong `manifest.json` + báo cáo nghiệm thu. Ai muốn đổi cán cân thì chạy lại đúng những clip ấy —
# `--ids <danh sách>` + `SEEDS` cao hơn, không phải dựng lại cả gói.
SEEDS = 2
# Tổng số lượt một clip được thử **qua mọi lần chạy** trước khi coi là nguội — xem chú thích ở chỗ dùng.
SETTLE = 4
NFE = 32              # bước flow-matching — cùng con số đã dùng cho 6 mẫu owner duyệt
# Trần gộp. 170 byte < `max_chars` mà `infer_process` tự tính cho clip mẫu 8,29 s (~215) ⇒ F5 giữ NGUYÊN
# một khối, không tự cắt rồi crossfade 0,15 s — thứ sẽ làm nhoè đúng những chỗ ta cần cắt.
BATCH_BYTES, BATCH_ITEMS = 140, 4
BATCH_SPEED = 0.9    # chậm nhẹ ⇒ quãng lặng giữa hai câu rộng ra, chỗ cắt rõ hơn
# Gộp **mọi tầng**, 4 mảnh một lượt.
#
# Lượt đo thứ nhất (`probe-batch.py`, còn ép `fix_duration` và còn cắt theo "quãng lặng dài nhất") kết luận
# nhầm rằng **số** không gộp được: 4/8 với 8 chữ số, 2/6 với 6 số hàng chục. Sau khi gỡ `fix_duration` và
# cắt theo **vị trí mong đợi**, đo lại (`probe-num.py`, 20 clip số):
#   • gộp 4 @ speed 0,90 → **15/20 đúng · 6,1 s/clip**
#   • sinh riêng        → **8/10 đúng · 16,7 s/clip**
# ⇒ gộp **nhanh gấp 2,7 lần** mà tỉ lệ đúng tương đương; mảnh nào lệch vẫn được sinh lại riêng ở sau.
# Bài học ghi luôn ở đây: kết luận "số không gộp được" của lượt đo đầu là **hệ quả của hai lỗi khác**,
# không phải một tính chất của tầng số — đúng bệnh mà `CLAUDE.md` §2 cảnh báo (quy kết ≠ cơ chế).
BATCH_TIERS = ("fixed", "head", "unit", "num")


def norm_peak(wav) -> np.ndarray:
    x = np.asarray(wav, dtype=np.float32)
    return x / max(float(np.abs(x).max()), 1e-9) * PEAK


class Shop:
    """Một lượt gọi F5 — gói lại để chỗ gọi không phải nhớ ref/nfe/seed/nhịp đọc ở bốn nơi."""

    def __init__(self, api, ref_wav: str, ref_text: str, ref_sec: float) -> None:
        self.api, self.ref, self.ref_text, self.ref_sec = api, ref_wav, ref_text, ref_sec

    def infer(self, gen: str, seed: int, speed: float = 1.0):
        # F5 in thẳng `gen_text …` ra stdout ⇒ nuốt, nhật ký của xưởng mới đọc được.
        with contextlib.redirect_stdout(io.StringIO()):
            wav, sr, _ = self.api.infer(
                ref_file=self.ref, ref_text=self.ref_text, gen_text=gen, speed=speed, nfe_step=NFE,
                cfg_strength=2.0, sway_sampling_coef=-1, seed=seed, remove_silence=False,
                fix_duration=None, show_info=lambda *a, **k: None)
        # [ĐO 2026-09-17] không nhả thì thời gian một lượt bò từ 5,8 s lên 15,6 s sau 8 lượt —
        # bộ nhớ đệm của MPS phình ra. Nhả sau mỗi lượt là vài ms, rẻ hơn nhiều lần so với cái dốc ấy.
        if torch.backends.mps.is_available():
            torch.mps.empty_cache()
        return norm_peak(wav), sr

    # Lượt sinh lại KHÔNG chỉ đổi seed — đổi cả **nhịp đọc**.
    #
    # ⚠ [ĐO host 2026-09-17, A/B 10 câu] `fix_duration` (ép thẳng tổng số khung) **không** chữa được bệnh
    # nuốt tiếng đầu — *"Bài tiếp theo"* mất chữ *"Bài"* ở CẢ hai chế độ — và còn làm hỏng thêm chỗ đang
    # đúng (*"pin"*: không ép ⇒ ĐÚNG, ép ⇒ ASR nghe *"PEN"*). Nó bị gỡ khỏi xưởng. Cái núm đúng là
    # `speed`: F5 chia nó vào **chính công thức ước độ dài** của mình
    # (`duration = ref_len + ref_len/ref_bytes × gen_bytes / speed`), nên câu dài ra mà rendering không bị
    # ép lệch khỏi mốc cắt `ref_audio_len` — đúng chỗ mà `fix_duration` làm mất tiếng đầu.
    #
    # Ba nhịp: nhịp đã sinh ra 6 mẫu owner duyệt trước, rồi chậm hơn, rồi chậm hẳn.
    SPEEDS = (1.0, 0.85, 0.72)

    def one(self, say: str, seed: int, attempt: int = 0):
        return self.infer(gen_text(say), seed, self.SPEEDS[attempt % len(self.SPEEDS)])

    def many(self, says: list[str], seed: int):
        """Sinh gộp rồi cắt; `None` khi không cắt được đúng số mảnh (chỗ gọi lùi về sinh từng câu)."""
        # ⚠ KHÔNG ép thời lượng cho lượt gộp. [ĐO 2026-09-17] ép rộng tay (tổng ước + 0,32 s mỗi mối)
        # làm F5 **lấp chỗ thừa bằng đuôi của chính clip mẫu**: cả lượt mở đầu bằng *"mở cửa hậu"* —
        # đúng bệnh "mọc đuôi" của §4.6 nhưng ở ĐẦU câu — và mọi mảnh lệch đi một nấc.
        gen = " ".join(gen_text(s) for s in says)
        x, sr = self.infer(gen, seed, BATCH_SPEED)
        # Trọng số cắt = số byte chữ của từng mảnh — chính đại lượng F5 dùng để chia thời lượng.
        parts = split_on_silence(x, sr, len(says),
                                 weights=[float(len(gen_text(s).encode("utf-8"))) for s in says])
        return (None if parts is None else [norm_peak(p) for p in parts]), sr


def pack_batches(rows: list[dict]) -> list[list[dict]]:
    """Gom các clip liền nhau thành lượt gộp — theo SỐ BYTE, vì đó là thứ `chunk_text` của F5 đếm."""
    out: list[list[dict]] = []
    cur, size = [], 0
    for r in rows:
        if r["tier"] not in BATCH_TIERS:
            if cur:
                out.append(cur)
                cur, size = [], 0
            out.append([r])
            continue
        b = len(gen_text(r["say"]).encode("utf-8")) + 1
        if cur and (size + b > BATCH_BYTES or len(cur) >= BATCH_ITEMS):
            out.append(cur)
            cur, size = [], 0
        cur.append(r)
        size += b
    if cur:
        out.append(cur)
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    sp = os.environ.get("SP", "")
    ap.add_argument("--clips", default=os.path.join(sp, "voice-clone", "clips.tsv"))
    ap.add_argument("--out", default=os.path.join(sp, "voice-clone", "pack-wav"))
    ap.add_argument("--ref", default=os.path.join(sp, "voice-clone", "ref", "be-refB.wav"))
    ap.add_argument("--ckpt", default=os.path.join(sp, "voice-clone", "model-toandev", "model.safetensors"))
    ap.add_argument("--vocab", default=os.path.join(sp, "voice-clone", "model-toandev", "vocab.txt"))
    ap.add_argument("--asr-model", default=os.path.join(sp, "model"))
    ap.add_argument("--hotwords", default=os.path.join(REPO, "core", "build", "hotwords", "hotwords-phrases.txt"))
    ap.add_argument("--only", default="", help="lọc theo tầng, vd fixed,head")
    ap.add_argument("--ids", default="", help="lọc theo id, cách nhau bằng dấu phẩy")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--device", default=None)
    ap.add_argument("--batch", type=int, default=BATCH_ITEMS, help="0 hoặc 1 ⇒ sinh từng câu một")
    ap.add_argument("--no-resume", action="store_true")
    args = ap.parse_args()

    with open(args.clips, encoding="utf-8") as f:
        rows = list(csv.DictReader(f, delimiter="\t"))
    if args.only:
        keep = set(args.only.split(","))
        rows = [r for r in rows if r["tier"] in keep]
    if args.ids:
        keep = set(args.ids.split(","))
        rows = [r for r in rows if r["id"] in keep]

    os.makedirs(args.out, exist_ok=True)
    prog_path = os.path.join(args.out, "progress.tsv")
    ok_before: set[str] = set()
    tries: dict[str, int] = {}
    if os.path.exists(prog_path) and not args.no_resume:
        with open(prog_path, encoding="utf-8") as f:
            for r in csv.DictReader(f, delimiter="\t"):
                # Nối lại theo mức **nghe ra đúng tiếng** ([viclip.close]), không theo mức chặt: một clip
                # chỉ lệch dấu thanh thì sinh lại cũng ra thế (§4.6) ⇒ giữ, đừng đốt lại ba lượt máy.
                (ok_before.add if r.get("near", r["ok"]) == "1" else ok_before.discard)(r["id"])
                tries[r["id"]] = tries.get(r["id"], 0) + 1
    # ⚠ Clip **không cứu được** phải NGUỘI lại, nếu không mỗi lần nối lại là đốt lại đúng chúng.
    # [ĐO 2026-09-17] năm clip đầu bảng chữ cái (*"Bài tiếp theo"* · *"Bố cục 2 cột"* …) hỏng ổn định;
    # qua sáu lượt nối lại, chúng ngốn ~50 lượt suy diễn mà không lần nào đúng — và vì luôn nằm đầu danh
    # sách, chúng chặn cả lượt dựng ở ngay vạch xuất phát. Sau [SETTLE] lượt thì giữ bản tốt nhất đã có
    # và đi tiếp; danh sách của chúng vẫn nằm trong `manifest.json` để sinh lại có chủ đích bằng `--ids`.
    settled = {cid for cid, k in tries.items() if k >= SETTLE
               and os.path.exists(os.path.join(args.out, cid + ".wav"))}
    # Nối lại = bỏ qua clip đã NGHE RA ĐÚNG TIẾNG và còn tệp. Clip ASR nghe ra tiếng khác thì làm lại —
    # lần chạy sau có thể mang theo một bản vá (chữ đọc khác, nhịp khác) đúng vào chỗ nó hỏng.
    todo = [r for r in rows
            if (r["id"] not in ok_before and r["id"] not in settled)
            or not os.path.exists(os.path.join(args.out, r["id"] + ".wav"))]
    # ⚠ Clip đã thử hỏng thì xuống CUỐI hàng. Bảng chuỗi sắp theo chữ cái, nên những clip khó nhất lại
    # tình cờ nằm ngay đầu (*"Bài…"* · *"Bố…"* · *"Bụi…"*); để nguyên thứ tự thì mỗi lượt chạy tiêu mấy
    # phút đầu tiên vào đúng chúng, và người đứng xem tưởng cả lượt dựng đang treo. Thứ tự trong một tầng
    # không ảnh hưởng kết quả (seed theo băm của clip, không theo vị trí), nên đây là một phép đổi **rẻ**.
    todo.sort(key=lambda r: (tries.get(r["id"], 0), 0))
    if args.limit:
        todo = todo[:args.limit]
    print("[kế hoạch] %d clip · đã xong %d · nguội sau %d lượt %d · còn %d"
          % (len(rows), len(rows) - len(todo), SETTLE, len(settled - ok_before), len(todo)), flush=True)
    if not todo:
        return 0

    judge = Judge(args.asr_model, args.hotwords)
    ref_text = open(args.ref.replace(".wav", ".txt"), encoding="utf-8").read().strip().lower()
    ref_x, ref_sr = sf.read(args.ref, dtype="float32")
    ref_sec = len(ref_x) / ref_sr

    t0 = time.time()
    from f5_tts.api import F5TTS
    api = F5TTS(model="F5TTS_Base", ckpt_file=args.ckpt, vocab_file=args.vocab, device=args.device)
    shop = Shop(api, args.ref, ref_text, ref_sec)
    print("[nạp] %.1fs · device=%s · sr=%d · ref %.2fs" % (time.time() - t0, api.device,
                                                          api.target_sample_rate, ref_sec), flush=True)

    prog = open(prog_path, "a", encoding="utf-8")
    if prog.tell() == 0:
        prog.write("id\ttier\tsay\tseed\tattempt\tasr\tok\tnear\tdur\tmode\n")
        prog.flush()

    def record(r: dict, x: np.ndarray, sr: int, seed: int, attempt: int, heard: str, mode: str) -> None:
        dst = os.path.join(args.out, r["id"] + ".wav")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        sf.write(dst, x, sr, subtype="PCM_16")
        prog.write("%s\t%s\t%s\t%d\t%d\t%s\t%d\t%d\t%.2f\t%s\n"
                   % (r["id"], r["tier"], r["say"].replace("\t", " "), seed, attempt,
                      heard.replace("\t", " "), int(same(r["say"], heard)), int(close(r["say"], heard)),
                      len(x) / sr, mode))
        prog.flush()

    def solo(r: dict) -> bool:
        """Sinh một câu, tối đa [SEEDS] lượt (mỗi lượt một seed + một nhịp đọc khác).

        Dừng ở lượt đầu tiên **nghe ra đúng chuỗi tiếng** ([viclip.close]) — không đòi khớp cả dấu thanh.
        Xem KDoc [viclip.close]: lệch mỗi dấu thanh thì sinh lại cũng ra thế (đã [ĐO] §4.6), nên đòi khớp
        dấu ở cổng sinh lại chỉ đốt thêm hai lượt máy cho một clip không sửa được. Con số **báo cáo** vẫn
        là mức chặt ([viclip.same]) và vẫn ghi vào `progress.tsv`.
        """
        best = None
        for attempt in range(SEEDS):
            seed = clip_seed(r["id"], attempt)
            x, sr = shop.one(r["say"], seed, attempt)
            heard = judge.hear(x, sr)
            rank = (same(r["say"], heard), close(r["say"], heard))
            if best is None or rank > best[4]:
                best = (x, sr, seed, attempt, rank, heard)
            if rank[1]:
                break
        x, sr, seed, attempt, rank, heard = best
        record(r, x, sr, seed, attempt, heard, "solo%d" % (attempt + 1))
        return rank[1]

    batches = pack_batches(todo) if args.batch > 1 else [[r] for r in todo]
    t_start, n_ok, n_bad, n_done = time.time(), 0, 0, 0
    for bi, batch in enumerate(batches, 1):
        if len(batch) == 1:
            ok = solo(batch[0])
            n_ok += ok
            n_bad += not ok
            n_done += 1
        else:
            seed = clip_seed(batch[0]["id"], 0)
            parts, sr = shop.many([r["say"] for r in batch], seed)
            if parts is None:
                print("  ⚠ lượt gộp %d câu không cắt được theo lặng ⇒ sinh riêng" % len(batch), flush=True)
                for r in batch:                       # cắt trượt ⇒ lùi về sinh từng câu
                    ok = solo(r)
                    n_ok += ok
                    n_bad += not ok
                    n_done += 1
            else:
                for r, p in zip(batch, parts):
                    p = trim_silence(p, sr)
                    heard = judge.hear(p, sr)
                    if close(r["say"], heard):
                        record(r, p, sr, seed, 0, heard, "batch%d" % len(batch))
                        n_ok += 1
                    else:
                        ok = solo(r)                  # mảnh lệch ⇒ sinh lại RIÊNG, không kéo cả lượt
                        n_ok += ok
                        n_bad += not ok
                    n_done += 1
        el = time.time() - t_start
        eta = el / max(1, n_done) * (len(todo) - n_done)
        print("[lượt %4d/%4d · clip %4d/%4d] đúng %d · lệch %d · %.1f s/clip · còn ~%.0f phút"
              % (bi, len(batches), n_done, len(todo), n_ok, n_bad, el / max(1, n_done), eta / 60), flush=True)
    prog.close()
    print("[xong] %d clip · đúng %d · lệch %d · %.1f phút"
          % (n_done, n_ok, n_bad, (time.time() - t_start) / 60))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
