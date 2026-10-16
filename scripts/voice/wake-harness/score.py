#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HARNESS "Hey Kachi" — đọc TSV của `run.sh`: tỉ lệ nổ theo nhóm (clean/instr/vocal × pos/neg), lượt giải mã & CPU mỗi tệp, và
dòng riêng cho 3 tệp dài (đo CPU khi nhạc phát). Bảng ở `docs/diagnostics/oncar-perf-2026-10-15-evening.md` §G2/§G6 in từ đây.

Dùng: score.py <a.tsv> [b.tsv …]
"""
import collections
import json
import sys

for p in sys.argv[1:]:
    rows = {}
    for line in open(p, encoding="utf-8"):
        name, j = line.rstrip("\n").split("\t", 1)
        try:
            rows[name] = json.loads(j)
        except Exception:
            rows[name] = {"ok": False}
    g = collections.defaultdict(lambda: [0, 0, 0, 0, 0])   # n · nổ · lượt giải mã · cpu_ms · charged_ms
    print(p.split("/")[-1])
    for k, r in sorted(rows.items()):
        if not r.get("ok"):
            print("  FAIL", k, r); continue
        cond, kind = k.split("_")[0], k.split("_")[1]
        cls = ("long:" if cond == "long" else cond + ":") + kind
        a = g[cls]; a[0] += 1; a[1] += 1 if r["wake"] else 0; a[2] += r["decodes"]; a[3] += r["cpu_ms"]; a[4] += r["charged_ms"]
        if cond == "long":
            print(f"  {k}: audio={r['audio_ms']} decodes={r['decodes']} skip={r['skipped_no_speech']} run={r['frames_run']} "
                  f"obs={r['frames_observe']} finish={r.get('frames_finish', 0)} cpu={r['cpu_ms']} charged={r['charged_ms']} fires={r['fires']}")
    for cls in sorted(g):
        n, w, d, c, ch = g[cls]
        print(f"  {cls:12s} n={n:3d} wake={w:3d} ({100 * w / n:5.1f}%) decodes/file={d / n:5.2f} cpu_ms/file={c / n:6.1f} charged/file={ch / n:7.1f}")
