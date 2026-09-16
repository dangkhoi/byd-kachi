# -*- coding: utf-8 -*-
"""
═══ V-pack · BÁO CÁO NGHIỆM THU GÓI TRÊN MÁY (không cần xe) ═══════════════════════════════════════════
Spec: docs/specs/kachi-voice-clone.html §6 — V-pack (ASR đọc lại từng clip) + V-nghe (owner nghe mẫu).

Chạy:  SP=… REPO=… /tmp/tts-venv/bin/python scripts/voice/clone/validate-pack.py \
         --pack voice/tts/kachi-giong-be-v1 [--spot 10]

In ra bốn thứ, và chỉ bốn thứ, vì đó là bốn thứ quyết định gói có ship được không:
  1. **cỡ + thời lượng** theo tầng — đối chiếu trần R-nf4;
  2. **tỉ lệ ASR đọc lại đúng** theo tầng — phép kiểm bằng máy duy nhất chạy hết được 1 600 clip;
  3. **danh sách clip còn lệch** — không giấu; mỗi dòng là một thứ owner có quyền biết;
  4. **10 clip rút ngẫu nhiên** (hạt cố định ⇒ chạy lại ra đúng 10 clip ấy) để owner nghe bằng tai.
     Máy chấm được *"có đúng chữ không"*; *"nghe có giống bé không"* thì chỉ tai owner chấm được (G1).
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import random
import sys

REPO = os.environ.get("REPO") or os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))

TIERS = ("fixed", "head", "unit", "num")


def main() -> int:
    ap = argparse.ArgumentParser()
    sp = os.environ.get("SP", "")
    ap.add_argument("--pack", default=os.path.join(REPO, "voice", "tts", "kachi-giong-be-v1"))
    ap.add_argument("--progress", default=os.path.join(sp, "voice-clone", "pack-wav", "progress.tsv"))
    ap.add_argument("--spot", type=int, default=10)
    ap.add_argument("--seed", type=int, default=20260917)
    args = ap.parse_args()

    man = json.load(open(os.path.join(args.pack, "manifest.json"), encoding="utf-8"))
    clips = man["clips"]

    heard: dict[str, str] = {}
    if os.path.exists(args.progress):
        for r in csv.DictReader(open(args.progress, encoding="utf-8"), delimiter="\t"):
            heard[r["id"]] = r["asr"]

    print("GÓI  %s · %s · dựng %s" % (man["id"], man["label"], man["built"]))
    print("%-6s %6s %12s %10s %12s %12s" % ("tầng", "clip", "byte", "giây", "ASR chặt", "nghe-đúng"))
    tot = [0, 0, 0, 0]
    for t in TIERS:
        sel = [c for c in clips if c["tier"] == t]
        if not sel:
            continue
        b = sum(c["bytes"] for c in sel)
        ms = sum(c["ms"] for c in sel)
        ok = sum(1 for c in sel if c["asrOk"])
        near = sum(1 for c in sel if c.get("asrNear", c["asrOk"]))
        print("%-6s %6d %12d %10.0f %8d/%-4d %8d/%-4d"
              % (t, len(sel), b, ms / 1000, ok, len(sel), near, len(sel)))
        tot[0] += len(sel)
        tot[1] += b
        tot[2] += ms
        tot[3] += ok
    print("%-6s %6d %12d %10.0f %8d/%-4d  = %.1f %%"
          % ("TỔNG", tot[0], tot[1], tot[2] / 1000, tot[3], tot[0], 100.0 * tot[3] / max(1, tot[0])))
    print("   audio %.2f MB · cả bảng %.2f MB · %.1f phút tiếng"
          % (tot[1] / 1e6,
             sum(os.path.getsize(os.path.join(dp, f)) for dp, _d, fs in os.walk(args.pack) for f in fs) / 1e6,
             tot[2] / 60000))

    tone = [c for c in clips if not c["asrOk"] and c.get("asrNear", False)]
    bad = [c for c in clips if not c.get("asrNear", c["asrOk"])]
    print("\nLỆCH DẤU THANH / CÁCH VIẾT — %d clip (đúng tiếng, ASR ghi khác dấu):" % len(tone))
    for c in sorted(tone, key=lambda c: (c["tier"], c["text"]))[:40]:
        print("  [%-5s] %-42s → %s" % (c["tier"], c["say"][:42], c.get("asrHeard") or heard.get(c["id"], "?")))
    if len(tone) > 40:
        print("  … còn %d dòng nữa (xem manifest.json, asrOk=false asrNear=true)" % (len(tone) - 40))
    print("\nCÒN LỆCH THẬT — %d clip (ASR nghe ra một TIẾNG KHÁC):" % len(bad))
    for c in sorted(bad, key=lambda c: (c["tier"], c["text"])):
        print("  [%-5s] %-42s → %s" % (c["tier"], c["say"][:42], c.get("asrHeard") or heard.get(c["id"], "?")))

    rnd = random.Random(args.seed)
    spot = rnd.sample(clips, min(args.spot, len(clips)))
    print("\nNGHE THỬ — %d clip rút ngẫu nhiên (hạt %d):" % (len(spot), args.seed))
    for c in spot:
        print("  %-44s %s" % (c["say"][:44], os.path.join(args.pack, c["file"])))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
