#!/usr/bin/env bash
# cast-nav-overlay-probe.sh — PROBE (item 3): can the OEM nav overlay (AMapService) sit ON TOP of a cast app?
# NON-ROOT, parked. Self-contained: `service call AutoContainer` (clusterDebug opcodes) + a nav re-broadcast
# + `fission_screencap -d 1`. Final proof = your eyes + the screencaps. Nothing here needs navopen or root.
#
# WHY THIS CAN WORK (RE, docs/_handoff/hud-cluster-injection-findings-2026-08-10.md §1,§11,§13):
#   The nav overlay AND the cast projection are composited by the SAME OEM Qt cluster compositor.
#   That compositor already keeps CAN content (km/h) ON TOP of the projection — that is exactly what
#   "curved / keepKmh" (opcode 30/16) does. So keeping the NAV layer on top of a cast app is
#   architecturally possible; this probe finds WHICH clusterDebug opcode / sequence re-asserts it.
#
# clusterDebug opcodes (via AutoContainer 1000-channel, RE'd from ClusterDebug.apk):
#   39=simple navigation · 12/13=show/hide ADAS · 16/17/18=cast full/half/OFF · 30/31=curved/flat style · 0=refresh
#   AVOID: 1 (disconnect cluster video), 18 (cast off), 41 (stress test). This script only uses 39/12/17/16.
#
# PRECONDITION (do in the app FIRST): open ClusterNav → turn Cast ON → cast an app (VietMap/GMaps) to the
#   cluster, and have a nav source navigating (so ClusterBroadcaster feeds AMapService). Confirm on the
#   cluster that nav is currently COVERED by the cast app. THEN run this.
#
# USAGE:  VEH=<ip:port> ./cast-nav-overlay-probe.sh        (SVC=AutoContainer default; SVC=auto_container for DiLink5)
# CLEAN:  physical power-cycle fully resets the cluster (the script restores full-cast with op 16 at the end).
set -u

ADB="${ADB:-adb}"
S="${VEH:-}"
CAP="${CAP:-12}"                 # per-adb-call hard timeout (s) — macOS has no `timeout`
OUT="${OUT:-.}"                  # where to pull screencaps
SVC="${SVC:-AutoContainer}"      # DiLink 2/3/4 = AutoContainer · DiLink 5 = auto_container
PAUSE="${PAUSE:-3}"              # seconds to let the cluster settle before each screencap
DISP="${DISP:-1}"                # fission_screencap display index for the CLUSTER.
                                 # NOTE: tool help says 0:ivi 1:cluster, but on some DiLink3.0 trims
                                 # this is INVERTED (verified 2026-08-12: -d 0 = cluster, -d 1 = ivi).
                                 # If cnp_00_baseline shows the IVI home, re-run with DISP=0.

command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
command -v "$ADB" >/dev/null 2>&1 || [ -x "$ADB" ] || { echo "FATAL: adb not found (set ADB=...)"; exit 1; }
if [ -z "$S" ]; then S="$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')"; fi
[ -n "$S" ] || { echo "FATAL: no adb device (set VEH=ip:port)"; exit 1; }
mkdir -p "$OUT"
echo "device=$S  svc=$SVC  out=$OUT  cap=${CAP}s"

# timeout-guarded adb (background + kill; macOS-safe)
cap(){ "$ADB" -s "$S" "$@" & local p=$!; ( sleep "$CAP"; kill -9 "$p" 2>/dev/null ) & local w=$!; wait "$p" 2>/dev/null; local rc=$?; kill -9 "$w" 2>/dev/null; return $rc; }

ac(){ # $1 = clusterDebug opcode
  echo "   AutoContainer 1000/$1"
  cap shell "service call $SVC 2 i32 1000 i32 $1 s16 ''" 2>&1 | tail -1
}
navbcast(){ # re-assert a nav frame so AMapService (re)draws the overlay
  cap shell "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 \
    --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ei NEW_ICON 3 \
    --ei SEG_REMAIN_DIS 300 --es NEXT_ROAD_NAME 'PROBE Road' --ei ROUTE_REMAIN_DIS 5000 \
    --ei ROUTE_REMAIN_TIME 300 --es SEG_REMAIN_DIS_AUTO '300 m' --es ROUTE_REMAIN_DIS_AUTO '5.0 km' \
    --es ROUTE_REMAIN_TIME_AUTO '5 min' --es ROUTE_REMAIN_TIME_STRING '5 min'" >/dev/null 2>&1 && echo "   nav frame broadcast"
}
shot(){ # $1 = tag
  cap shell "fission_screencap -d $DISP -p /data/local/tmp/cnp_$1.png" >/dev/null 2>&1
  cap pull "/data/local/tmp/cnp_$1.png" "$OUT/cnp_$1.png" >/dev/null 2>&1 && echo "   cluster screencap (-d $DISP) -> $OUT/cnp_$1.png"
}

echo "== PRECONDITION check: an app must already be CAST on the cluster + a nav source navigating. =="
echo "== 0) baseline (expect: nav COVERED by the cast app) =="; sleep "$PAUSE"; shot 00_baseline
echo "== A) re-broadcast nav frame — does just re-asserting nav pop it back on top? =="; navbcast; sleep "$PAUSE"; shot 01_navbcast
echo "== B) op 39 (simple navigation) + re-broadcast — does 39 raise the nav layer? =="; ac 39; navbcast; sleep "$PAUSE"; shot 02_simplenav39
echo "== C) op 12 (show ADAS) + re-broadcast — does showing ADAS bring the overlay forward? =="; ac 12; navbcast; sleep "$PAUSE"; shot 03_adas12_nav
echo "== D) op 17 (cast HALF) — does half-cast reserve a strip that shows gauges/nav beside the app? =="; ac 17; sleep "$PAUSE"; shot 04_casthalf17
echo "== restore full cast (op 16) =="; ac 16; sleep 1

cat <<'NEXT'
------------------------------------------------------------------
COMPARE the screencaps ($OUT/cnp_*.png). The WINNING step is whichever shows the nav overlay
(arrow + distance + road name) ON TOP of the cast app:
  cnp_00_baseline    : nav covered (starting point)
  cnp_01_navbcast    : re-assert nav only
  cnp_02_simplenav39 : clusterDebug 39 (simple navigation)
  cnp_03_adas12_nav  : show ADAS (12) + nav
  cnp_04_casthalf17  : cast HALF (17) — app in one half, gauges/nav in the other?
Report which step won. I will wire that exact opcode/sequence into the cast flow
(issued right after AppMover.castToCluster succeeds) so nav stays visible while casting.
If NONE keeps nav on top -> this trim's compositor pins full-cast above nav; fallback = cast-HALF
layout, or nav-only (Cast off) for guaranteed nav.
CLEAN: physical power-cycle fully resets the cluster (op 16 already restored full cast).
------------------------------------------------------------------
NEXT
echo "DONE (probe). Only opcodes 39/12/17/16 + a nav broadcast were issued. Power-cycle to fully clean."
