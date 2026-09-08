#!/usr/bin/env python3
"""Analyze ClusterNav turn-distance logs (nav_log_*.csv) — data-driven interp tuning (J2).

Compares our interpolation against the GMaps on-screen distance (ground truth read via
accessibility) so the interpolator can be tuned from data instead of guesswork.

CSV columns (written by NavDistanceLog):
  t_ms,rawGmaps_m,projected_m,display_m,closing_mps,speed_mps,screenRead_m,screenRead_age_ms,road,key
Older logs (8-col, no screenRead_*) are still accepted: screen-read metrics are skipped,
jump detection still runs. Columns are read BY HEADER NAME, so column order/additions are safe.

Stdlib only (Python 3.7+). Usage:
  python3 scripts/analyze-nav-distance-log.py nav_log_*.csv
  python3 scripts/analyze-nav-distance-log.py --fresh-ms 1500 --jump-m 40 path/to/log.csv
  adb pull /sdcard/Android/data/com.byd.clusternav/files/    # then point at the pulled *.csv
"""
import argparse
import csv
import glob
import math
import os
import sys


def _to_float(v):
    """Parse a CSV cell to float; return None for blank/unparseable."""
    if v is None:
        return None
    s = str(v).strip()
    if s == "":
        return None
    try:
        return float(s)
    except ValueError:
        return None


def _stats(values):
    """Summary stats for a list of floats. Returns dict; n==0 when empty."""
    n = len(values)
    if n == 0:
        return {"n": 0}
    s = sorted(values)

    def pct(p):
        if n == 1:
            return s[0]
        idx = min(n - 1, max(0, int(round((p / 100.0) * (n - 1)))))
        return s[idx]

    mean = sum(values) / n
    rms = math.sqrt(sum(v * v for v in values) / n)
    mean_abs = sum(abs(v) for v in values) / n
    return {
        "n": n, "mean": mean, "rms": rms, "mean_abs": mean_abs,
        "p50": pct(50), "p95": pct(95), "min": s[0], "max": s[-1],
    }


def _fmt(st):
    if st["n"] == 0:
        return "  (no samples)"
    return ("  n={n}  mean={mean:+.1f}  mean|·|={mean_abs:.1f}  rms={rms:.1f}  "
            "p50={p50:+.1f}  p95={p95:+.1f}  min={min:+.1f}  max={max:+.1f}").format(**st)


def _expand(paths):
    out = []
    for p in paths:
        matched = glob.glob(p)
        if matched:
            out.extend(sorted(matched))
        elif os.path.exists(p):
            out.append(p)
        else:
            print("WARN: no such file/glob: {}".format(p), file=sys.stderr)
    return out


def load_rows(files):
    """Read all rows from all files into a list of dicts (typed), tagged with source file."""
    rows = []
    have_screenread = False
    for f in files:
        try:
            with open(f, newline="", encoding="utf-8", errors="replace") as fh:
                reader = csv.DictReader(fh)
                if reader.fieldnames and "screenRead_m" in reader.fieldnames:
                    have_screenread = True
                for r in reader:
                    rows.append({
                        "src": os.path.basename(f),
                        "t_ms": _to_float(r.get("t_ms")),
                        "rawGmaps_m": _to_float(r.get("rawGmaps_m")),
                        "projected_m": _to_float(r.get("projected_m")),
                        "display_m": _to_float(r.get("display_m")),
                        "speed_mps": _to_float(r.get("speed_mps")),
                        "closing_mps": _to_float(r.get("closing_mps")),
                        "screenRead_m": _to_float(r.get("screenRead_m")),
                        "screenRead_age_ms": _to_float(r.get("screenRead_age_ms")),
                        "road": (r.get("road") or "").strip(),
                        "key": (r.get("key") or "").strip(),
                    })
        except OSError as e:
            print("ERROR reading {}: {}".format(f, e), file=sys.stderr)
    return rows, have_screenread


def analyze(rows, have_screenread, fresh_ms, jump_m, min_speed):
    total = len(rows)
    print("=" * 68)
    print("ClusterNav nav-distance log analysis")
    print("=" * 68)
    print("rows: {}   fresh window: {} ms   jump: >{} m   moving: >{} m/s".format(
        total, fresh_ms, jump_m, min_speed))
    if total == 0:
        print("\nNo rows to analyze.")
        return

    # --- Error vs ground-truth (screen-read) ---
    if have_screenread:
        err_proj, err_disp, err_raw = [], [], []
        fresh = 0
        for r in rows:
            sr = r["screenRead_m"]
            age = r["screenRead_age_ms"]
            if sr is None or sr < 0 or age is None or age < 0 or age > fresh_ms:
                continue
            fresh += 1
            if r["projected_m"] is not None and r["projected_m"] >= 0:
                err_proj.append(r["projected_m"] - sr)
            if r["display_m"] is not None and r["display_m"] >= 0:
                err_disp.append(r["display_m"] - sr)
            if r["rawGmaps_m"] is not None and r["rawGmaps_m"] >= 0:
                err_raw.append(r["rawGmaps_m"] - sr)

        print("\n-- Error vs GMaps on-screen distance (ground truth), fresh rows: {} --".format(fresh))
        print("projected − screen (our interp lag/lead):")
        print(_fmt(_stats(err_proj)))
        print("display   − screen (what the driver sees):")
        print(_fmt(_stats(err_disp)))
        print("rawGmaps  − screen (notification staleness):")
        print(_fmt(_stats(err_raw)))

        st = _stats(err_proj)
        if st["n"] >= 20:
            print("\n-- Tuning hints (projected vs screen) --")
            if st["mean"] <= -8:
                print("  * projected reads LOW by ~{:.0f}m on average → decrementing too fast.".format(-st["mean"]))
                print("    Try: lower FACTOR (e.g. 0.90) or verify SpeedProvider over-read.")
            elif st["mean"] >= 8:
                print("  * projected reads HIGH by ~{:.0f}m on average → decrementing too slow.".format(st["mean"]))
                print("    Try: raise FACTOR toward 1.0, or check MAX_EXTRAPOLATE_MS stalls.")
            else:
                print("  * mean bias small (|{:+.1f}|m) — core rate looks fine.".format(st["mean"]))
            if st["p95"] - st["p50"] >= 30:
                print("  * wide spread (p95−p50={:.0f}m) → corrections jumpy; consider SLEW_MIN / quantize bands.".format(
                    st["p95"] - st["p50"]))
        else:
            print("\n  (need >=20 fresh samples for tuning hints; collect more on-car data.)")
    else:
        print("\n-- No screenRead_m column (old log). Skipping ground-truth error metrics. --")
        print("   Re-pull logs from a build that writes screenRead_m to enable interp tuning.")

    # --- Jump detection on the displayed number (time-ordered per source) ---
    by_src = {}
    for r in rows:
        by_src.setdefault(r["src"], []).append(r)
    jumps = []
    for src, rs in by_src.items():
        rs = [r for r in rs if r["t_ms"] is not None]
        rs.sort(key=lambda r: r["t_ms"])
        prev = None
        for r in rs:
            d = r["display_m"]
            if d is not None and d >= 0 and prev is not None:
                pd, spd = prev
                if spd is not None and spd > min_speed and abs(d - pd) > jump_m:
                    jumps.append((src, r["t_ms"], pd, d, r["road"]))
            if d is not None and d >= 0:
                prev = (d, r["speed_mps"])
    print("\n-- Display jumps (|Δ| > {}m while moving): {} --".format(jump_m, len(jumps)))
    for src, t, a, b, road in jumps[:15]:
        print("  {}  t={:.0f}  {:.0f} -> {:.0f} m  ({:+.0f})  road='{}'".format(
            src, t, a, b, b - a, road[:36]))
    if len(jumps) > 15:
        print("  ... and {} more".format(len(jumps) - 15))
    print("\nDone.")


def main(argv=None):
    ap = argparse.ArgumentParser(description="Analyze ClusterNav nav-distance logs.")
    ap.add_argument("paths", nargs="+", help="CSV file(s) or glob(s), e.g. nav_log_*.csv")
    ap.add_argument("--fresh-ms", type=int, default=1500,
                    help="max screen-read age to count as ground truth (default 1500)")
    ap.add_argument("--jump-m", type=float, default=40.0,
                    help="display delta over this (m) counts as a jump (default 40)")
    ap.add_argument("--min-speed", type=float, default=2.0,
                    help="min speed (m/s) to count a jump as real (default 2.0)")
    args = ap.parse_args(argv)

    files = _expand(args.paths)
    if not files:
        print("No input files found.", file=sys.stderr)
        return 2
    print("Files: {}".format(", ".join(os.path.basename(f) for f in files)))
    rows, have_sr = load_rows(files)
    analyze(rows, have_sr, args.fresh_ms, args.jump_m, args.min_speed)
    return 0


if __name__ == "__main__":
    sys.exit(main())
