#!/usr/bin/env bash
# hud3-speedlimit.sh — Inject a CUSTOM speed-limit number on the BYD DL3 cluster (parked test).
#
# Goal #3. Path proven off-car (system.img RE, handoff §13/§15/§16):
#   - ISA speed-limit VALUE  = statistic 0x4B40001C  (settable; same device as the traffic-sign
#     icon 0x4B400064 that already rendered on the cluster).
#   - SLA fusion overwrites it every cycle -> disable via safety-aid fusion switch 0xd61b6746,
#     and/or SLA state 0x38500022.
# It tries 3 combos and screencaps the cluster after each so you can see which (if any) shows
# the number. Every adb call is hard-timeout guarded (macOS has no `timeout`). Reboot fully cleans.
#
# Usage:   VEH=<ip:port> NAVOPEN_JAR=<path> ./hud3-speedlimit.sh [SPEED_KPH] [SIGN_TYPE]
#   SPEED_KPH  number to inject (default 60)
#   SIGN_TYPE  optional traffic-sign type to co-inject via 0x4B400064 (default 0 = leave)
set -u

SPEED="${1:-60}"
SIGNTYPE="${2:-0}"
ADB="${ADB:-adb}"
S="${VEH:-}"
JAR_LOCAL="${NAVOPEN_JAR:-}"
CAP="${CAP:-10}"                 # per-command hard timeout (s)

command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
[ -x "$ADB" ] || command -v "$ADB" >/dev/null 2>&1 || { echo "FATAL: adb not found (set ADB=...)"; exit 1; }

if [ -z "$S" ]; then S="$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')"; fi
[ -n "$S" ] || { echo "FATAL: no adb device (set VEH=ip:port or connect adb)"; exit 1; }

if [ -z "$JAR_LOCAL" ]; then
  for c in "$(dirname "$0")/../../../apks/navopen-v3.jar" "$(dirname "$0")/navopen-v3.jar" "./navopen-v3.jar"; do
    [ -f "$c" ] && { JAR_LOCAL="$c"; break; }
  done
fi
[ -f "$JAR_LOCAL" ] || { echo "FATAL: navopen-v3.jar not found (set NAVOPEN_JAR=path)"; exit 1; }
echo "device=$S  jar=$JAR_LOCAL  speed=$SPEED  signtype=$SIGNTYPE  cap=${CAP}s"

# --- timeout-guarded adb (macOS lacks `timeout`) ---
cap(){ "$ADB" -s "$S" "$@" & local p=$!; ( sleep "$CAP"; kill -9 "$p" 2>/dev/null ) & local w=$!; wait "$p" 2>/dev/null; local rc=$?; kill -9 "$w" 2>/dev/null; return $rc; }
NAVCP="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"
nav(){ cap shell "$NAVCP $*"; }
read_val(){ nav getraw "$1" "$2" 2>/dev/null | sed -n 's/.*= *\(-\{0,1\}[0-9][0-9]*\).*/\1/p' | tail -1; }
shot(){ cap shell "fission_screencap -d 1 -p /data/local/tmp/hud3_$1.png" >/dev/null 2>&1; cap pull "/data/local/tmp/hud3_$1.png" "./hud3_$1.png" >/dev/null 2>&1 && echo "   cluster screencap -> ./hud3_$1.png"; }

echo "== push navopen =="; cap push "$JAR_LOCAL" /data/local/tmp/navopen.jar >/dev/null 2>&1 && echo "   ok"

echo "== SAVE originals =="
O_SLA="$(read_val adas 38500022)";      echo "   SLA state    0x38500022 = ${O_SLA:-?}"
O_FUS="$(read_val setting d61b6746)";   echo "   fusion sw    0xd61b6746 = ${O_FUS:-?}"
O_VAL="$(read_val statistic 4B40001C)"; echo "   speed-limit  0x4B40001C = ${O_VAL:-?}"
shot 00_before

inject(){ # $1 = label
  for i in 1 2 3 4 5 6; do
    if [ "$SIGNTYPE" != "0" ]; then nav multi statistic 4B40001C "$SPEED" statistic 4B400064 "$SIGNTYPE" >/dev/null
    else nav setraw statistic 4B40001C "$SPEED" >/dev/null; fi
    sleep 0.4
  done
  echo -n "   after $1: 0x4B40001C now = "; read_val statistic 4B40001C
  shot "$1"
}

echo "== COMBO C (control): inject with current SLA/fusion state =="
inject 10_comboC
echo ">> LOOK at cluster speed-limit sign (./hud3_10_comboC.png). If number shows -> done."

echo "== COMBO A (bet): safety-aid FUSION OFF, SLA on, inject =="
nav setraw setting d61b6746 0 >/dev/null
inject 20_comboA_fusionoff
echo ">> LOOK again (./hud3_20_comboA_fusionoff.png)."

echo "== COMBO B: ISLA switch off (0x38500044=0) + fusion off, inject =="
nav setraw adas 38500044 0 >/dev/null
inject 30_comboB_islaoff
echo ">> LOOK again (./hud3_30_comboB_islaoff.png)."

echo "== RESTORE =="
[ -n "${O_FUS:-}" ] && nav setraw setting d61b6746 "$O_FUS" >/dev/null || nav setraw setting d61b6746 1 >/dev/null
nav setraw adas 38500044 1 >/dev/null
[ -n "${O_SLA:-}" ] && nav setraw adas 38500022 "$O_SLA" >/dev/null
echo "   restored fusion=${O_FUS:-1}, ISLA=1, SLA=${O_SLA:-?}."
echo "DONE #3. If the sign got messy, a head-unit reboot fully cleans it."
