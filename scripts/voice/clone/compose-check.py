# -*- coding: utf-8 -*-
"""
═══ T5 · NGHE THỬ CHỖ GHÉP — câu CÓ SỐ dựng từ ba mảnh (đầu câu + số + đuôi) ══════════════════════════
Spec: docs/specs/kachi-voice-clone.html — T5, §4.3 (ghép ba clip, crossfade 15 ms), OQ2.

Chạy:  SP=… REPO=… /tmp/tts-venv/bin/python scripts/voice/clone/compose-check.py \
         --pack $SP/voice-clone/t5-wav --out $SP/voice-clone/t5-compose

Làm ba việc, theo đúng thứ tự của một phép đo:
  1. **ghép** 10 câu mẫu từ chính các mảnh trong gói (cùng luật mà `ClipSpeaker` sẽ dùng trên xe);
  2. **đọc lại** bằng bộ nhận dạng đang ship — chỗ ghép hỏng thì ASR nghe hụt/nhòe đúng ở chỗ nối;
  3. ghi WAV + AAC cho owner **nghe bằng tai** — máy chấm được *"có đúng chữ không"*, không chấm được
     *"có nghe như cắt dán không"* (G1 là tai owner, §2 của spec).

## Vì sao khoá theo CHỮ, không theo mã clip
Mã clip (`head/0082`) đổi mỗi lần registry mọc thêm một nhãn. Chữ (*"Pin (SOC):"*) thì không — và nếu
nó đổi thật thì bài này **phải** đỏ, vì lúc đó câu owner từng nghe không còn tồn tại.
"""
from __future__ import annotations

import argparse
import csv
import os
import subprocess
import sys

import soundfile as sf

REPO = os.environ.get("REPO") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
sys.path.insert(0, os.path.join(REPO, "scripts", "voice"))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from viclip import Judge, same, stitch, trim_silence  # noqa: E402

# (đầu câu, số, đuôi) — chữ CHÍNH XÁC như trong clips.tsv. Đuôi "" ⇒ câu chỉ có hai mảnh.
CASES = [
    ("Pin (SOC):", "46", " %"),
    ("Tầm hoạt động EV:", "257", " km"),
    ("Tốc độ:", "60", " km/h"),
    ("Đã đặt Nhiệt độ", "24", ", chưa kiểm trên xe"),
    ("Đã đặt Âm lượng", "12", ""),
    ("Đã tăng Gió", "1", " nấc, chưa kiểm trên xe"),
    ("Đã xong", "3", " việc"),
    ("Áp lốp trước-phải:", "220", " kPa"),
    ("Odo tổng:", "999", " km"),
    ("Nhiệt lốp trước-trái:", "33", " °C"),
]


def main() -> int:
    ap = argparse.ArgumentParser()
    spdir = os.environ.get("SP", "")
    ap.add_argument("--clips", default=os.path.join(spdir, "voice-clone", "clips.tsv"))
    ap.add_argument("--pack", default=os.path.join(spdir, "voice-clone", "t5-wav"))
    ap.add_argument("--out", default=os.path.join(spdir, "voice-clone", "t5-compose"))
    ap.add_argument("--asr-model", default=os.path.join(spdir, "model"))
    ap.add_argument("--hotwords", default=os.path.join(REPO, "core", "build", "hotwords", "hotwords-phrases.txt"))
    ap.add_argument("--emit-whole", default="",
                    help="ghi 10 câu NGUYÊN VẸN ra một clips.tsv để sinh bằng synth-pack.py (bước 1 của phép so)")
    ap.add_argument("--whole-pack", default="",
                    help="thư mục WAV của 10 câu nguyên vẹn đã sinh — có thì in bảng SO SÁNH ghép vs nguyên câu")
    args = ap.parse_args()

    rows = list(csv.DictReader(open(args.clips, encoding="utf-8"), delimiter="\t"))
    by_text = {(r["tier"], r["text"]): r for r in rows}

    # ── bước 1 của phép so: ghi 10 câu NGUYÊN VẸN ra một clips.tsv ────────────────────────────────────
    # Máy chỉ chấm được *"có đúng chữ không"*. Câu hỏi thật của T5 — *"chỗ ghép nghe có như cắt dán
    # không"* — chỉ tai owner trả lời được, và trả lời được **khi có cái để so**: cùng một câu, một bản
    # ghép ba mảnh và một bản đọc liền. Không có bản đọc liền thì owner đang chấm một thứ không có mốc.
    if args.emit_whole:
        import hashlib
        with open(args.emit_whole, "w", encoding="utf-8") as f:
            f.write("id\ttext\ttier\tsay\n")
            for head, num, tail in CASES:
                keys = [("head", head), ("num", num)] + ([("unit", tail)] if tail else [])
                if any(k not in by_text for k in keys):
                    continue
                say = " ".join(by_text[k]["say"].strip().strip(",").strip() for k in keys)
                cid = "fixed/w%s" % hashlib.sha256(say.encode()).hexdigest()[:9]
                f.write("%s\t%s\tfixed\t%s\n" % (cid, say, say))
        print("đã ghi %s — sinh bằng synth-pack.py --clips %s --out <thư mục>" % (args.emit_whole, args.emit_whole))
        return 0

    import hashlib
    os.makedirs(args.out, exist_ok=True)
    judge = Judge(args.asr_model, args.hotwords)

    def whole_wav(say: str) -> str | None:
        """Bản ĐỌC LIỀN của cùng câu — mốc để owner so với bản ghép. `None` khi chưa sinh."""
        if not args.whole_pack:
            return None
        p = os.path.join(args.whole_pack, "fixed", "w%s.wav" % hashlib.sha256(say.encode()).hexdigest()[:9])
        return p if os.path.exists(p) else None

    ok_n = whole_ok = whole_n = 0
    print("%-46s %-6s %s" % ("câu ghép", "ASR", "nghe ra"))
    for i, (head, num, tail) in enumerate(CASES, 1):
        keys = [("head", head), ("num", num)] + ([("unit", tail)] if tail else [])
        missing = [k for k in keys if k not in by_text]
        if missing:
            print("  ⚠ thiếu trong clips.tsv: %s" % missing)
            continue
        parts, says, sr = [], [], None
        for k in keys:
            r = by_text[k]
            p = os.path.join(args.pack, r["id"] + ".wav")
            if not os.path.exists(p):
                print("  ⚠ chưa sinh: %s (%s)" % (r["id"], r["text"]))
                parts = []
                break
            x, sr = sf.read(p, dtype="float32")
            parts.append(trim_silence(x, sr))
            says.append(r["say"])
        if not parts:
            continue
        y = stitch(parts, sr)
        want = " ".join(s.strip().strip(",").strip() for s in says)
        heard = judge.hear(y, sr)
        ok = same(want, heard)
        ok_n += ok
        base = os.path.join(args.out, "%02d_ghep" % i)
        sf.write(base + ".wav", y, sr, subtype="PCM_16")
        subprocess.run(["afconvert", "-f", "adts", "-d", "aac", "-b", "32000", base + ".wav", base + ".aac"],
                       check=False, capture_output=True)
        wp = whole_wav(want)
        if wp:
            wx, wsr = sf.read(wp, dtype="float32")
            wx = trim_silence(wx, wsr)
            wheard = judge.hear(wx, wsr)
            wok = same(want, wheard)
            whole_ok += wok
            whole_n += 1
            wbase = os.path.join(args.out, "%02d_lien" % i)
            sf.write(wbase + ".wav", wx, wsr, subtype="PCM_16")
            subprocess.run(["afconvert", "-f", "adts", "-d", "aac", "-b", "32000",
                            wbase + ".wav", wbase + ".aac"], check=False, capture_output=True)
            print("%-46s %-6s %s   | đọc liền: %-6s %s"
                  % (want[:44], "ĐÚNG" if ok else "LỆCH", heard[:28], "ĐÚNG" if wok else "LỆCH", wheard[:28]))
            continue
        print("%-46s %-6s %s" % (want[:44], "ĐÚNG" if ok else "LỆCH", heard))
        if not ok:
            print("%-46s %-6s mong đợi: %s" % ("", "", want))
    print("\n→ ghép ba mảnh: ASR đọc lại đúng %d/%d" % (ok_n, len(CASES)))
    if whole_n:
        print("→ cùng câu ĐỌC LIỀN:  ASR đọc lại đúng %d/%d  (mốc để so — chênh lệch ở đây là cái giá"
              " của phép ghép, đo được bằng máy)" % (whole_ok, whole_n))
    print("→ WAV+AAC ở %s — nghe cặp NN_ghep / NN_lien để trả lời OQ2" % args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
