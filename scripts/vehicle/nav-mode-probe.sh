#!/usr/bin/env bash
# nav-mode-probe.sh — PROBE (item 5): map the OEM cluster AMAP nav DISPLAY modes to clusterDebug opcodes.
# NON-ROOT, parked. Self-contained: `service call AutoContainer 2 i32 1000 i32 <op> s16 ""` (clusterDebug
# opcodes) + `fission_screencap` of the CLUSTER after each step. Final proof = your eyes + the screencaps.
#
# ┌─ SAFETY (READ BEFORE RUNNING) ───────────────────────────────────────────────────────────────────┐
# │ • PARKED ONLY. Gear in P, handbrake ON, engine/ready may be on but the car MUST NOT move.          │
# │ • Do NOT run while driving or with anyone waiting to drive. This repaints the instrument cluster.  │
# │ • NON-ROOT. Uses only `service call` + `fission_screencap` + (optional) `am broadcast`. No su.     │
# │ • CLEANUP = PHYSICAL POWER-CYCLE. Opcodes are applied CUMULATIVELY and some (theme/mode) persist.   │
# │   A physical head-unit power-cycle fully resets the cluster. `adb reboot` is NOT accepted as clean. │
# │   Also restore your preferred nav mode from the OEM AMAP cluster menu after the power-cycle.        │
# │ • AVOID opcodes (NEVER issue — this script never does): 1 (disconnect cluster video), 18 (cast OFF),│
# │   41 (stress test), 91 (SIGABRT), 92 (SIGSEGV). Only 39/8/9/34/35/6/7 are issued below.            │
# │ • This script PINS the target to $VEH and REFUSES to run against an emulator.                       │
# └────────────────────────────────────────────────────────────────────────────────────────────────────┘
#
# GOAL: The OEM cluster AMAP nav menu offers display modes — Đơn giản / Màn hình nhỏ / Toàn màn hình / OFF.
#   We want to DRIVE those from the app via clusterDebug opcodes. Known:
#     • op 39 = "简易导航 / Simple navigation" (RE from ClusterDebug.apk SecondActivity list 0-107,
#       docs/_handoff/hud-cluster-injection-findings-2026-08-10.md §13.1). Confirmed on-car 2026-08-12 to
#       show arrow + distance + road + ETA in the cluster CENTER → the "Đơn giản" / center+ETA mode.
#   UNKNOWN (what this probe hunts): the opcode for "Màn hình nhỏ" — the small nav strip pinned at the TOP.
#
# METHOD (PARKED, with a nav source ALREADY navigating — GMaps or VietMap actively guiding):
#   1) Baseline screencap of the current cluster (nav active, whatever mode it is in now).
#   2) Issue op 39 (Simple navigation) + screencap → expect arrow + distance + road + ETA in the CENTER.
#   3) Sweep a SMALL set of SAFE candidate opcodes, screencapping each, to find "Màn hình nhỏ":
#         8, 9  = classic / tech dashboard style   (may reposition/resize the nav area)
#        34, 35 = Di3.0 / Di4.0 mode               (may reflow the nav layout)
#         6, 7  = day / night theme                (ALSO a ch1000 sanity — proves the channel works)
#      NOTE: opcodes are CUMULATIVE. To isolate ONE op, power-cycle then run with ONLY=<op> (see USAGE).
#   4) Print a result table template (op → observed nav layout) for the operator to fill in.
#
# clusterDebug opcode reference (via AutoContainer 1000-channel, RE'd from ClusterDebug.apk):
#   39=simple navigation · 6/7=day/night · 8/9=classic/tech dashboard · 34/35=Di3.0/Di4.0 mode
#   12/13=show/hide ADAS · 16/17=cast full/half · 45=show ADAS self-learning result
#   AVOID: 1 (disconnect video) · 18 (cast off) · 41 (stress test) · 91/92 (SIGABRT/SIGSEGV)
#
# PRECONDITION (do this FIRST): have a nav source ACTIVELY navigating so the cluster shows nav now.
#   (GMaps/VietMap guiding → ClusterBroadcaster feeds AmapService, OR the OEM AMAP nav is running.)
#   Confirm on the cluster that nav is visible. THEN run this.
#
# USAGE:
#   VEH=<ip:port> bash nav-mode-probe.sh                 # full sequence: baseline → 39 → 8,9,34,35,6,7
#   VEH=<ip:port> ONLY=8 bash nav-mode-probe.sh          # isolate ONE candidate: baseline → op 8 only
#   VEH=<ip:port> DISP=1 bash nav-mode-probe.sh          # if baseline shows the IVI/home, flip the display
#   VEH=<ip:port> NAVBCAST=1 bash nav-mode-probe.sh      # also re-assert a synthetic nav frame each step
#   (SVC=auto_container for DiLink5; default SVC=AutoContainer covers DiLink 2/3/4.)
# CLEAN: physical power-cycle fully resets the cluster; then restore your nav mode in the OEM AMAP menu.
#
# This file is executable-style (shebang) but ships without +x — RUN IT WITH:  bash nav-mode-probe.sh
set -u

ADB="${ADB:-adb}"
S="${VEH:-}"
CAP="${CAP:-12}"                 # per-adb-call hard timeout (s) — macOS has no `timeout`
OUT="${OUT:-.}"                  # where to pull screencaps
SVC="${SVC:-AutoContainer}"      # DiLink 2/3/4 = AutoContainer · DiLink 5 = auto_container
PAUSE="${PAUSE:-3}"              # seconds to let the cluster settle before each screencap
ONLY="${ONLY:-}"                 # if set to a single opcode, run baseline + just that op (isolation)
NAVBCAST="${NAVBCAST:-0}"        # 1 = re-assert a synthetic nav frame before each screencap (default off:
                                 #     observe the REAL nav source, don't override it)
DISP="${DISP:-0}"                # fission_screencap display index for the CLUSTER.
                                 # DEFAULT 0: on this DiLink3.0 trim `-d 0 = CLUSTER`, `-d 1 = IVI`
                                 # (the tool's own help text is INVERTED; verified 2026-08-12).
                                 # If navmode_00_baseline shows the IVI/home screen, re-run with DISP=1.

# --- adb resolution: prefer PATH, fall back to the macOS SDK location -------------------------------
command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
command -v "$ADB" >/dev/null 2>&1 || [ -x "$ADB" ] || { echo "FATAL: adb not found (set ADB=...)"; exit 1; }

# --- device pinning: use $VEH; else auto-pick the first REAL device; NEVER an emulator --------------
if [ -z "$S" ]; then
  S="$("$ADB" devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1; exit}')"
fi
[ -n "$S" ] || { echo "FATAL: no vehicle adb device (set VEH=ip:port). Refusing to guess."; exit 1; }
case "$S" in
  emulator-*) echo "FATAL: target '$S' is an emulator. This probe only runs on the vehicle."; exit 1;;
esac
# Belt-and-suspenders: refuse a QEMU/emulator even if it connected over TCP with a non-emulator serial.
QEMU="$("$ADB" -s "$S" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r' | tr -d '[:space:]')"
if [ "$QEMU" = "1" ]; then
  echo "FATAL: target '$S' reports ro.kernel.qemu=1 (emulator). Refusing. Pin a real vehicle via VEH=."
  exit 1
fi

mkdir -p "$OUT"
echo "device=$S  svc=$SVC  disp=$DISP  out=$OUT  cap=${CAP}s  navbcast=$NAVBCAST${ONLY:+  ONLY=$ONLY}"

# --- timeout-guarded adb (background + kill; macOS-safe, no `timeout` binary needed) -----------------
cap(){ "$ADB" -s "$S" "$@" & local p=$!; ( sleep "$CAP"; kill -9 "$p" 2>/dev/null ) & local w=$!; wait "$p" 2>/dev/null; local rc=$?; kill -9 "$w" 2>/dev/null; return $rc; }

# --- issue one clusterDebug opcode on the AutoContainer 1000-channel --------------------------------
# HARD SAFETY CHOKE-POINT: refuse the AVOID opcodes on EVERY path — including an operator-supplied
# ONLY=<op>. 1=disconnect cluster video · 18=cast OFF · 41=stress test · 91=SIGABRT · 92=SIGSEGV.
# The header promises these are "never issued"; enforce it at the single point that talks to the bus
# so a mistyped ONLY can never repaint/kill the cluster.
ac(){ # $1 = clusterDebug opcode
  case " 1 18 41 91 92 " in
    *" $1 "*) echo "   REFUSED unsafe opcode '$1' (AVOID list 1/18/41/91/92 — never issued)"; return 0;;
  esac
  echo "   AutoContainer 1000/$1"
  cap shell "service call $SVC 2 i32 1000 i32 $1 s16 ''" 2>&1 | tail -1
}

# --- OPTIONAL: re-assert a synthetic nav frame so AmapService redraws (default OFF) ------------------
navbcast(){
  [ "$NAVBCAST" = "1" ] || return 0
  cap shell "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 \
    --ei EXTRA_STATE 1 --ei EXTRA_IS_FOREGROUND 0 --ez IS_BYD_MAP false --ei NEW_ICON 3 \
    --ei SEG_REMAIN_DIS 300 --es NEXT_ROAD_NAME 'PROBE Road' --ei ROUTE_REMAIN_DIS 5000 \
    --ei ROUTE_REMAIN_TIME 300 --es SEG_REMAIN_DIS_AUTO '300 m' --es ROUTE_REMAIN_DIS_AUTO '5.0 km' \
    --es ROUTE_REMAIN_TIME_AUTO '5 min' --es ROUTE_REMAIN_TIME_STRING '5 min'" >/dev/null 2>&1 \
    && echo "   (synthetic nav frame re-asserted)"
}

# --- screencap the CLUSTER and pull to ./navmode_<tag>.png ------------------------------------------
shot(){ # $1 = tag
  cap shell "fission_screencap -d $DISP -p /data/local/tmp/navmode_$1.png" >/dev/null 2>&1
  cap pull "/data/local/tmp/navmode_$1.png" "$OUT/navmode_$1.png" >/dev/null 2>&1 \
    && echo "   cluster screencap (-d $DISP) -> $OUT/navmode_$1.png" \
    || echo "   WARN: screencap/pull failed (tag=$1). Is DISP right? try DISP=1."
}

# --- describe a candidate opcode (bash-3.2 safe; no associative arrays) ------------------------------
desc(){ case "$1" in
  39) echo "Simple navigation (简易导航) — expect arrow+distance+road+ETA in CENTER";;
  8)  echo "classic dashboard style — may reposition/resize the nav area";;
  9)  echo "tech dashboard style — may reposition/resize the nav area";;
  34) echo "Di3.0 mode — may reflow the nav layout";;
  35) echo "Di4.0 mode — may reflow the nav layout";;
  6)  echo "day theme (ch1000 sanity — proves the channel works)";;
  7)  echo "night theme (ch1000 sanity — proves the channel works)";;
  *)  echo "candidate opcode $1";;
esac; }

# --- run one candidate: label → issue op → (opt) re-assert nav → settle → screencap -----------------
probe(){ # $1 = opcode, $2 = zero-padded 2-digit opcode for the filename tag
  echo "== op $1 : $(desc "$1") =="
  ac "$1"
  navbcast
  sleep "$PAUSE"
  shot "op$2"
}

echo "== PRECONDITION: a nav source must be ACTIVELY navigating so the cluster shows nav NOW. =="
echo "== 0) baseline — current cluster / current nav mode =="
navbcast; sleep "$PAUSE"; shot "00_baseline"

if [ -n "$ONLY" ]; then
  # Isolation mode: baseline + exactly one candidate. Pad the tag to 2 digits.
  printf -v tag '%02d' "$ONLY" 2>/dev/null || tag="$ONLY"
  probe "$ONLY" "$tag"
else
  # Step 2: the known-good anchor.
  probe 39 39
  # Step 3: the SMALL safe candidate sweep (cumulative). None are in the AVOID list.
  for op in 8 9 34 35 6 7; do
    printf -v tag '%02d' "$op"
    probe "$op" "$tag"
  done
  # Courtesy restore to DAY theme (op 6). A physical power-cycle is still required for a full reset.
  echo "== restore day theme (op 6) — cosmetic; power-cycle still required for a full reset =="
  ac 6; sleep 1
fi

cat <<'NEXT'
------------------------------------------------------------------------------------------------------
COMPARE the screencaps ($OUT/navmode_*.png) side by side and FILL IN the table below.
We are mapping the OEM AMAP nav DISPLAY menu modes to opcodes:
    Đơn giản      = simple / center + ETA          (op 39 confirmed 2026-08-12)
    Màn hình nhỏ  = small nav STRIP at the TOP      (UNKNOWN — the target of this probe)
    Toàn màn hình = full-screen nav map
    OFF           = nav hidden on the cluster

  screencap                         op   candidate meaning                         observed nav layout (FILL IN)
  --------------------------------  ---  ----------------------------------------  ------------------------------
  navmode_00_baseline.png            —   (starting mode, before any op)            ____________________________
  navmode_op39.png                  39   Simple nav → center + arrow + ETA         ____________________________
  navmode_op08.png                   8   classic dashboard                         ____________________________
  navmode_op09.png                   9   tech dashboard                            ____________________________
  navmode_op34.png                  34   Di3.0 mode                                ____________________________
  navmode_op35.png                  35   Di4.0 mode                                ____________________________
  navmode_op06.png                   6   day theme (sanity)                        ____________________________
  navmode_op07.png                   7   night theme (sanity)                      ____________________________

FOR EACH: note whether the nav is CENTER / SMALL-STRIP-AT-TOP / FULL-SCREEN / OFF / UNCHANGED, and any
resize or repositioning. The WINNER for "Màn hình nhỏ" is whichever op moves nav to a small strip at the TOP.
Report which op → which mode. That opcode gets wired into the app so the user can pick the nav display mode.

If NONE of 8/9/34/35 produce "Màn hình nhỏ": the small-strip layout is likely NOT a ch1000 opcode on this
trim (it may be an OEM AMAP in-menu setting only) → report that so we stop hunting it via clusterDebug.
Sanity: op 6/7 MUST visibly flip day↔night — if they don't, the 1000-channel isn't taking effect (re-check
SVC / that nav is active / DISP).

CLEAN: PHYSICAL power-cycle the head unit (adb reboot is NOT accepted), then restore your preferred nav
mode from the OEM AMAP cluster menu. Only opcodes 39/8/9/34/35/6/7 were issued — none from the AVOID list.
------------------------------------------------------------------------------------------------------
NEXT
echo "DONE (probe). Issued only safe opcodes (39/8/9/34/35/6/7) + optional nav broadcast. Power-cycle to fully clean."
