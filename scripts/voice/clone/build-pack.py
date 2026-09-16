# -*- coding: utf-8 -*-
"""
═══ T4 · ĐÓNG GÓI "Giọng Kachi bé" — WAV của xưởng → gói ADTS AAC ghim sha256 ═════════════════════════
Spec: docs/specs/kachi-voice-clone.html — T4, §4.4 (vì sao ADTS thô chứ không .m4a), §4.5 (bố cục gói),
R5 (đi đúng kênh `VoicePack`/`VoiceModelStore` đang có, bảng ghim NGOÀI mã).

Chạy:  SP=… REPO=… /tmp/tts-venv/bin/python scripts/voice/clone/build-pack.py \
         --wav $SP/voice-clone/pack-wav --dst voice/tts/kachi-giong-be-v1

Sinh ra:
  voice/tts/kachi-giong-be-v1/{fixed,head,unit,num}/*.aac   ADTS AAC-LC 32 kbps · 24 kHz · mono
  voice/tts/kachi-giong-be-v1/manifest.json                 {id, text, tier, file, bytes, sha256, ms}
  voice/tts/kachi-giong-be-v1/index.tsv · num.tsv           bảng TRA của xe (chuỗi chính xác → tệp)
  voice/tts/kachi-giong-be-v1/LICENSE-NOTES.md              ghi công + đồng thuận (§4.8, R7)
  voice/tts/kachi-giong-be-v1.sha256.tsv                    bảng ghim, ĐÚNG khuôn gói Piper

## Vì sao ADTS thô, không .m4a — [ĐO], không phải sở thích
Cùng một clip 0,75 s, cùng `-b 32000`: `.m4a` ra 7 643 byte (81 kbps thực!), ADTS `.aac` ra 3 680 byte
(39 kbps thực). Phần thừa là khối `moov` của MP4 — vô hại với một tệp nhạc 4 phút, nhưng nhân với ~1 600
tệp tí hon thì nó là ~4 MB rác, gần nửa gói.

## Vì sao CẮT LẶNG trước khi nén
Hai lý do, và lý do thứ hai mới là lý do thật: (a) lặng đầu/cuối vẫn tốn byte; (b) một câu có số phát
bằng **ba clip nối liền** — mỗi mảnh mang theo quãng lặng riêng thì câu nghe thành ba câu rời. Luật cắt
+ crossfade ở `viclip.py`, dùng chung với phép nghe thử T5 và (sau này) với `ClipSpeaker` trên xe.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
from datetime import date

import numpy as np
import soundfile as sf

REPO = os.environ.get("REPO") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from viclip import PEAK, trim_silence  # noqa: E402

PACK_ID = "kachi-giong-be-v1"
BITRATE = 32000
TIER_DIR = {"fixed": "fixed", "head": "head", "unit": "unit", "num": "num"}

LICENSE_NOTES = """# Giọng Kachi bé — ghi công, giấy phép, đồng thuận

Gói này là **audio đã tổng hợp**. Không có một mẫu thu gốc nào trong đây, và cũng không có trọng số mô hình.

## Mô hình đã dùng để sinh gói

| | |
|---|---|
| Bộ tổng hợp | **F5-TTS** — `SWivid/F5-TTS` (MIT) |
| Trọng số tiếng Việt | **`toandev/F5-TTS-Vietnamese`** — fine-tune `F5TTS_Base` trên `capleaf/viVoice` |
| Giấy phép trọng số | **CC-BY-NC-4.0** — ghi công · **phi thương mại** · không có điều khoản chia-sẻ-tương-tự |
| Bộ dữ liệu huấn luyện | **`capleaf/viVoice`** |
| Bộ mã hoá âm | Vocos (mel 24 kHz) |
| Ngày sinh gói | {built} |

Điều kiện **NC** (phi thương mại) là điều kiện dự án này đã tự khẳng định một lần cho mô hình nghe
(`voice/README.md`): Kachi là một dự án **phi lợi nhuận**, phát hành công khai, không bán. Vì giấy phép
**không** có điều khoản chia-sẻ-tương-tự, gói audio sinh ra **không** bị buộc mang lại đúng giấy phép ấy —
nhưng nghĩa vụ **ghi công** thì vẫn còn, và nó nằm ở đây, ở `manifest.json`, và ở màn CREDITS trong app
(cả ba đọc **cùng một** trường dữ liệu — R7 của spec).

## Đồng thuận

Đây là **giọng một đứa trẻ, đã được nhân bản từ một bản thu có sự đồng ý**. Chủ dự án (`dangkhoi`) là
**cha của bé** và đã quyết định ngày **2026-09-16** cho phát hành gói này công khai qua kênh OTA của dự án
(*"giọng bé được nhiều người nghe cũng vui… take it easy thôi"* — ghi ở `docs/specs/kachi-voice-clone.html`
§4.8). Ba điều đi kèm quyết định đó, ghi thẳng ra đây để sau này không ai phải đoán:

- **Bản thu gốc không bao giờ vào kho mã.** `voice/record/` nằm trong `.gitignore`; bản thu và mọi bản cắt
  ở lại máy của chủ dự án. Thứ được phát hành là audio đã tổng hợp.
- **Tên gói không mang tên thật của bé** — *"Giọng Kachi bé"* (`kachi-giong-be-v1`).
- **Có đường rút, nhưng không thu hồi được bản đã tải.** Gỡ gói khỏi kho là nó biến mất khỏi mọi bản cài
  sau; bản đã tải về máy người khác thì không. Sự thật này thuộc về quyết định ở trên, không phải thứ
  thiết kế chữa được.

## Không dùng lại giọng này cho mục đích khác

Gói phát hành để Kachi **đọc câu trả lời của chính nó**. Dùng audio ở đây để dựng một giọng nhân bản khác,
để tạo phát ngôn mà người thật chưa từng nói, hoặc cho bất kỳ mục đích thương mại nào — đều **ngoài** phạm
vi đồng thuận đã có, và ngoài điều kiện NC của mô hình.
"""


def sha256_of(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def encode(src_wav: str, dst_aac: str, bitrate: int) -> float:
    """Cắt lặng → chuẩn đỉnh → ADTS AAC. Trả về thời lượng (giây) của audio đã cắt."""
    x, sr = sf.read(src_wav, dtype="float32")
    if x.ndim > 1:
        x = x.mean(1)
    x = trim_silence(x, sr)
    x = x / max(float(np.abs(x).max()), 1e-9) * PEAK
    os.makedirs(os.path.dirname(dst_aac), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        tmp_wav = tmp.name
    try:
        sf.write(tmp_wav, x, sr, subtype="PCM_16")
        subprocess.run(["afconvert", "-f", "adts", "-d", "aac", "-b", str(bitrate), tmp_wav, dst_aac],
                       check=True, capture_output=True)
    finally:
        os.unlink(tmp_wav)
    return len(x) / sr


def main() -> int:
    ap = argparse.ArgumentParser()
    sp = os.environ.get("SP", "")
    ap.add_argument("--clips", default=os.path.join(sp, "voice-clone", "clips.tsv"))
    ap.add_argument("--wav", default=os.path.join(sp, "voice-clone", "pack-wav"))
    ap.add_argument("--dst", default=os.path.join(REPO, "voice", "tts", PACK_ID))
    ap.add_argument("--bitrate", type=int, default=BITRATE)
    ap.add_argument("--allow-missing", action="store_true",
                    help="đóng gói cả khi còn clip chưa sinh (dùng để xem thử cỡ gói giữa chừng)")
    args = ap.parse_args()

    rows = list(csv.DictReader(open(args.clips, encoding="utf-8"), delimiter="\t"))
    prog: dict[str, dict] = {}
    prog_path = os.path.join(args.wav, "progress.tsv")
    if os.path.exists(prog_path):
        for r in csv.DictReader(open(prog_path, encoding="utf-8"), delimiter="\t"):
            prog[r["id"]] = r          # dòng sau đè dòng trước ⇒ lượt sinh MỚI NHẤT thắng

    missing = [r["id"] for r in rows if not os.path.exists(os.path.join(args.wav, r["id"] + ".wav"))]
    if missing and not args.allow_missing:
        print("✗ còn %d clip chưa sinh (vd %s). Chạy synth-pack.py cho xong, hoặc --allow-missing."
              % (len(missing), ", ".join(missing[:5])))
        return 2

    if os.path.isdir(args.dst):
        shutil.rmtree(args.dst)
    os.makedirs(args.dst, exist_ok=True)

    manifest_clips, index_rows, num_rows = [], [], []
    tier_stat: dict[str, list] = {}
    for r in rows:
        cid, tier = r["id"], r["tier"]
        src = os.path.join(args.wav, cid + ".wav")
        if not os.path.exists(src):
            continue
        name = cid.split("/", 1)[1]
        rel = "%s/%s.aac" % (TIER_DIR[tier], name)
        dur = encode(src, os.path.join(args.dst, rel), args.bitrate)
        full = os.path.join(args.dst, rel)
        size = os.path.getsize(full)
        ms = int(round(dur * 1000))
        row = prog.get(cid, {})
        asr_ok = row.get("ok") == "1"              # nghe lại đúng NGUYÊN VĂN (giữ dấu thanh)
        asr_near = row.get("near", row.get("ok")) == "1"   # nghe ra đúng TIẾNG, có thể lệch dấu
        manifest_clips.append({
            "id": cid, "text": r["text"], "tier": tier, "file": rel,
            "bytes": size, "sha256": sha256_of(full), "ms": ms, "say": r["say"],
            "asrOk": asr_ok, "asrNear": asr_near, "asrHeard": row.get("asr", ""),
        })
        (num_rows if tier == "num" else index_rows).append((r["text"], rel, ms))
        st = tier_stat.setdefault(tier, [0, 0, 0.0, 0, 0])
        st[0] += 1
        st[1] += size
        st[2] += dur
        st[3] += asr_ok
        st[4] += asr_near

    # ── bảng TRA của xe: chuỗi CHÍNH XÁC → tệp. Tách num ra bảng riêng vì khoá là một số, không phải câu.
    with open(os.path.join(args.dst, "index.tsv"), "w", encoding="utf-8") as f:
        for text, rel, ms in sorted(index_rows):
            f.write("%s\t%s\t%d\n" % (text, rel, ms))
    with open(os.path.join(args.dst, "num.tsv"), "w", encoding="utf-8") as f:
        for text, rel, ms in sorted(num_rows, key=lambda t: int(t[0])):
            f.write("%s\t%s\t%d\n" % (text, rel, ms))

    total_bytes = sum(c["bytes"] for c in manifest_clips)
    total_ms = sum(c["ms"] for c in manifest_clips)
    manifest = {
        "id": PACK_ID,
        "label": "Giọng Kachi bé",
        "version": 1,
        "built": date.today().isoformat(),
        "format": {"container": "ADTS", "codec": "AAC-LC", "bitrate": args.bitrate,
                   "sampleRate": 24000, "channels": 1},
        "source": {
            "synthesizer": "F5-TTS (SWivid/F5-TTS, MIT)",
            "weights": "toandev/F5-TTS-Vietnamese",
            "weightsLicense": "CC-BY-NC-4.0",
            "dataset": "capleaf/viVoice",
            "vocoder": "Vocos mel-24kHz",
            "consent": "Giọng người thật, đã được chủ dự án (cha của bé) đồng ý phát hành công khai 2026-09-16.",
        },
        "counts": {t: s[0] for t, s in sorted(tier_stat.items())},
        "totalBytes": total_bytes,
        "totalMs": total_ms,
        "clips": manifest_clips,
    }
    with open(os.path.join(args.dst, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    with open(os.path.join(args.dst, "LICENSE-NOTES.md"), "w", encoding="utf-8") as f:
        f.write(LICENSE_NOTES.format(built=manifest["built"]))

    # ── bảng ghim NGOÀI thư mục gói — đúng khuôn gói Piper: <đường dẫn tương đối>\t<bytes>\t<sha256>
    pin = args.dst.rstrip("/") + ".sha256.tsv"
    with open(pin, "w", encoding="utf-8") as f:
        for root, _dirs, files in os.walk(args.dst):
            for fn in sorted(files):
                p = os.path.join(root, fn)
                f.write("%s\t%d\t%s\n" % (os.path.relpath(p, args.dst), os.path.getsize(p), sha256_of(p)))

    print("%-6s %6s %10s %9s %10s %10s" % ("tầng", "clip", "byte", "giây", "ASR chặt", "nghe-đúng"))
    for t in ("fixed", "head", "unit", "num"):
        if t not in tier_stat:
            continue
        n, b, d, ok, near = tier_stat[t]
        print("%-6s %6d %10d %9.0f %7d/%-4d %7d/%-4d" % (t, n, b, d, ok, n, near, n))
    extra = os.path.getsize(pin) + sum(
        os.path.getsize(os.path.join(args.dst, x)) for x in ("manifest.json", "index.tsv", "num.tsv", "LICENSE-NOTES.md"))
    print("%-6s %6d %10d %9.0f   (%.2f MB audio · %.2f MB cả bảng)"
          % ("TỔNG", len(manifest_clips), total_bytes, total_ms / 1000,
             total_bytes / 1e6, (total_bytes + extra) / 1e6))
    if missing:
        print("⚠ %d clip CHƯA sinh, không có trong gói này" % len(missing))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
