#!/usr/bin/env python3
"""Per-app / per-channel drive-log analyzer for ClusterNav data-collection drives.

Reads the pullable CSVs written by the data-collection build and prints, for EACH nav app
(GMaps / VietMap / Waze / WazeMod), what each CHANNEL actually captured — so we conclude
"app X yields data on channel Y (or not)" from NUMBERS, not assumptions.

Channels:
  nav_access_log_*.csv     a11y (foreground app text + voice/announcement), source-tagged by pkg
  nav_notif_raw_*.csv      RAW notifications from all 5 pkgs (before the nav-only drop)
  nav_notif_log_*.csv      parsed nav notifications (post nav-gate)
  vietmap_signal_*.csv     VietMap widget (speed/limit/alerts + upcoming limit+distance)
  nav_arrow_*.csv          GMaps maneuver/arrow classification
  nav_log_*.csv            GMaps distance interpolation vs on-screen ground-truth

Usage:
  python3 analyze_drive_logs.py <folder>          # analyze one drive folder
  python3 analyze_drive_logs.py <folder> --samples 5
"""
import csv
import glob
import os
import sys
from collections import Counter, defaultdict

PKG_LABEL = {
    "com.google.android.apps.maps": "GMaps",
    "app.revanced.android.apps.maps": "GMaps(ReVanced)",
    "vn.vietmap.live": "VietMap",
    "com.waze": "Waze",
    "com.chisadin.wazemod": "WazeMod",
}
CHANNELS = ["nav_access", "nav_notif_raw", "nav_notif", "vietmap_signal", "nav_arrow", "nav_log"]


def find_csvs(folder, prefix):
    # search folder recursively so files/ or diag/ layouts both work
    hits = glob.glob(os.path.join(folder, "**", prefix + "*.csv"), recursive=True)
    return sorted(hits)


def read_rows(files):
    rows = []
    for f in files:
        try:
            with open(f, newline="", encoding="utf-8", errors="replace") as fh:
                for r in csv.DictReader(fh):
                    rows.append(r)
        except Exception as e:  # noqa: BLE001 — diagnostic tool, keep going
            print(f"  (warn: could not read {os.path.basename(f)}: {e})")
    return rows


def label(pkg):
    return PKG_LABEL.get(pkg, pkg or "(empty)")


def trunc(s, n=70):
    s = (s or "").replace("\n", " ").strip()
    return s if len(s) <= n else s[: n - 1] + "…"


def analyze_pkg_stream(rows, text_fields, samples):
    """Group by pkg → row count + distinct non-empty text + sample rows."""
    by_pkg = defaultdict(list)
    for r in rows:
        by_pkg[r.get("pkg", "")].append(r)
    out = {}
    for pkg, rs in by_pkg.items():
        distinct = set()
        sample_texts = []
        for r in rs:
            joined = " | ".join(trunc(r.get(fld, ""), 90) for fld in text_fields if r.get(fld, "").strip())
            if joined and joined not in distinct:
                distinct.add(joined)
                if len(sample_texts) < samples:
                    sample_texts.append(joined)
        out[pkg] = {"rows": len(rs), "distinct": len(distinct), "samples": sample_texts}
    return out


def section(title):
    print("\n" + "=" * 78)
    print(title)
    print("=" * 78)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    folder = sys.argv[1]
    samples = 5
    if "--samples" in sys.argv:
        samples = int(sys.argv[sys.argv.index("--samples") + 1])
    if not os.path.isdir(folder):
        print(f"ERROR: not a folder: {folder}")
        sys.exit(1)

    # Load every channel
    data = {}
    files_found = {}
    prefixes = {
        "nav_access": "nav_access_log_",
        "nav_notif_raw": "nav_notif_raw_",
        "nav_notif": "nav_notif_log_",
        "vietmap_signal": "vietmap_signal_",
        "nav_arrow": "nav_arrow_",
        "nav_log": "nav_log_",
    }
    for ch, pfx in prefixes.items():
        fs = find_csvs(folder, pfx)
        files_found[ch] = fs
        data[ch] = read_rows(fs)

    section(f"DRIVE LOG ANALYSIS — {folder}")
    for ch in CHANNELS:
        print(f"  {ch:16s}: {len(files_found[ch]):3d} file(s), {len(data[ch]):6d} rows")

    # ---- CAPTURE MATRIX: pkg × channel (row counts) ----
    section("CAPTURE MATRIX — rows per app per channel (0 = app gave NOTHING on that channel)")
    pkgs_seen = set()
    per_ch_pkg = {}
    for ch in ("nav_access", "nav_notif_raw", "nav_notif"):
        c = Counter(r.get("pkg", "") for r in data[ch])
        per_ch_pkg[ch] = c
        pkgs_seen |= set(c.keys())
    # vietmap_signal / nav_arrow / nav_log are single-source by nature
    order = ["com.google.android.apps.maps", "app.revanced.android.apps.maps",
             "vn.vietmap.live", "com.waze", "com.chisadin.wazemod"]
    extra = [p for p in pkgs_seen if p not in order]
    cols = [p for p in order if p in pkgs_seen] + extra
    hdr = f"  {'app':22s}" + "".join(f"{'a11y':>9}{'notifRaw':>10}{'notif':>8}")
    print(f"  {'app':22s}{'a11y':>9}{'notifRaw':>10}{'notif':>8}")
    print("  " + "-" * 47)
    for p in cols:
        print(f"  {label(p):22s}"
              f"{per_ch_pkg['nav_access'].get(p,0):>9}"
              f"{per_ch_pkg['nav_notif_raw'].get(p,0):>10}"
              f"{per_ch_pkg['nav_notif'].get(p,0):>8}")
    print(f"\n  vietmap_signal rows: {len(data['vietmap_signal'])}  (VietMap widget — single-source)")
    print(f"  nav_arrow rows:      {len(data['nav_arrow'])}  (GMaps maneuver — single-source)")
    print(f"  nav_log rows:        {len(data['nav_log'])}  (GMaps interp — single-source)")

    # ---- nav_access detail ----
    section("nav_access (a11y) — what each app exposed on-screen / via voice")
    acc = analyze_pkg_stream(data["nav_access"], ["screenRead_road", "screenRead_maneuverHint", "text"], samples)
    for p in sorted(acc, key=lambda x: -acc[x]["rows"]):
        st = acc[p]
        print(f"\n  [{label(p)}] rows={st['rows']} distinct={st['distinct']}")
        for s in st["samples"]:
            print(f"      • {s}")

    # ---- nav_notif_raw detail (isNav/hasDist breakdown) ----
    section("nav_notif_raw — every notification each app posts (nav or NOT)")
    by_pkg = defaultdict(list)
    for r in data["nav_notif_raw"]:
        by_pkg[r.get("pkg", "")].append(r)
    for p in sorted(by_pkg, key=lambda x: -len(by_pkg[x])):
        rs = by_pkg[p]
        nav = sum(1 for r in rs if r.get("isNav", "").lower() == "true")
        dist = sum(1 for r in rs if r.get("hasDist", "").lower() == "true")
        distinct, samp = set(), []
        for r in rs:
            key = trunc(r.get("title", ""), 45) + " || " + trunc(r.get("text", ""), 80)
            if key.strip(" |") and key not in distinct:
                distinct.add(key)
                if len(samp) < samples:
                    samp.append((r.get("isNav", ""), r.get("hasDist", ""), key))
        print(f"\n  [{label(p)}] rows={len(rs)} isNav={nav} hasDist={dist} distinct={len(distinct)}")
        for isnav, hd, key in samp:
            print(f"      • (isNav={isnav},hasDist={hd}) {key}")

    # ---- vietmap_signal coverage ----
    section("vietmap_signal — VietMap widget field coverage (non-empty counts)")
    vs = data["vietmap_signal"]
    if vs:
        all_keys = set()
        for r in vs:
            all_keys |= set(r.keys())

        def nz(field):
            return sum(1 for r in vs if (r.get(field, "") or "").strip() not in ("", "0", "null", "--"))

        def have(field):  # rows that actually carry this column (mixed 13/17-col safe)
            return sum(1 for r in vs if field in r)

        fields = ["currentSpeedKph", "speedLimitKph", "a1Limit", "a1Dist",
                  "upLimit", "upDist", "up2Limit", "up2Dist"]
        present = [f for f in fields if f in all_keys]
        for f in present:
            print(f"  {f:18s}: {nz(f):5d}/{have(f):5d} rows non-empty (of rows carrying the column)")
        missing = [f for f in fields if f not in all_keys]
        if missing:
            print(f"  (columns in NO file: {', '.join(missing)} — older build?)")
        fre6 = Counter(r.get("freshness", "") for r in vs)
        print(f"  freshness: {dict(fre6)}")
    else:
        print("  (no vietmap_signal rows)")

    # ---- nav_arrow maneuver distribution ----
    section("nav_arrow — maneuver / final icon distribution")
    na = data["nav_arrow"]
    if na:
        print("  maneuver:", dict(Counter(r.get("maneuver", "") for r in na).most_common(12)))
        print("  final_icon:", dict(Counter(r.get("final_icon", "") for r in na).most_common(12)))
        print("  arrow_src:", dict(Counter(r.get("arrow_src", "") for r in na)))
    else:
        print("  (no nav_arrow rows)")

    # ---- nav_log interp accuracy ----
    section("nav_log — interpolation vs on-screen ground-truth")
    nl = data["nav_log"]
    diffs = []
    for r in nl:
        try:
            disp = float(r.get("display_m", ""))
            sr = float(r.get("screenRead_m", ""))
            if sr >= 0 and disp >= 0:
                diffs.append(disp - sr)
        except (ValueError, TypeError):
            pass
    if diffs:
        diffs.sort()
        n = len(diffs)
        exact = sum(1 for d in diffs if abs(d) < 1)
        print(f"  paired samples: {n}")
        print(f"  exact (|Δ|<1m): {exact} ({100*exact//n}%)")
        print(f"  median Δ: {diffs[n//2]:.0f} m   mean Δ: {sum(diffs)/n:.1f} m   min/max: {diffs[0]:.0f}/{diffs[-1]:.0f}")
    else:
        print("  (no paired display/screenRead samples)")

    print("\n" + "=" * 78)
    print("DONE. Per-app/per-channel yield above — conclude from the numbers.")
    print("=" * 78)


if __name__ == "__main__":
    main()
