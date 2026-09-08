#!/usr/bin/env bash
# hud1-nav-hud.sh — Try to get NAV turn-by-turn onto the windshield HUD (BYD DL3, parked test).
#
# Goal #1. The full moving-MAP on HUD is MCU-coding-gated (0x38B00030, dealer-only) — NOT tried here.
# This tries the writable HUD/nav control surface (Setting 0x4C10E0xx + fusion switches) that
# gates turn-by-turn/nav-screen content, then triggers nav (handoff §16):
#   HUD switch 0x4C10E023, HUD mode 0x4C10E025, dynamic-navi 0x4C10E03A, navi-screen 0x4C10E015,
#   map-sending 0x4C10E01D, nav-fusion 0x8e2fcdbf, + instr HUD-nav-map SET 0x32B1102E / guide 0x43F01010.
#
# NOTE: fission_screencap captures the CLUSTER only — the windshield HUD is a SEPARATE display and
# CANNOT be screencapped. You MUST look at the physical HUD with your eyes for this test.
#
# Usage:  VEH=<ip:port> NAVOPEN_JAR=<path> ./hud1-nav-hud.sh
set -u

ADB="${ADB:-adb}"
S="${VEH:-}"
JAR_LOCAL="${NAVOPEN_JAR:-}"
CAP="${CAP:-10}"

command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
[ -x "$ADB" ] || command -v "$ADB" >/dev/null 2>&1 || { echo "FATAL: adb not found (set ADB=...)"; exit 1; }
if [ -z "$S" ]; then S="$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')"; fi
[ -n "$S" ] || { echo "FATAL: no adb device (set VEH=ip:port)"; exit 1; }
if [ -z "$JAR_LOCAL" ]; then
  for c in "$(dirname "$0")/../../../apks/navopen-v3.jar" "$(dirname "$0")/navopen-v3.jar" "./navopen-v3.jar"; do
    [ -f "$c" ] && { JAR_LOCAL="$c"; break; }
  done
fi
[ -f "$JAR_LOCAL" ] || { echo "FATAL: navopen-v3.jar not found (set NAVOPEN_JAR=path)"; exit 1; }
echo "device=$S  jar=$JAR_LOCAL  cap=${CAP}s"

cap(){ "$ADB" -s "$S" "$@" & local p=$!; ( sleep "$CAP"; kill -9 "$p" 2>/dev/null ) & local w=$!; wait "$p" 2>/dev/null; local rc=$?; kill -9 "$w" 2>/dev/null; return $rc; }
NAVCP="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"
nav(){ cap shell "$NAVCP $*"; }
read_val(){ nav getraw "$1" "$2" 2>/dev/null | sed -n 's/.*= *\(-\{0,1\}[0-9][0-9]*\).*/\1/p' | tail -1; }

echo "== push navopen =="; cap push "$JAR_LOCAL" /data/local/tmp/navopen.jar >/dev/null 2>&1 && echo "   ok"

echo "== SAVE originals (Setting 0x4C10E0xx + fusion) =="
declare -a IDS=(4C10E023 4C10E025 4C10E03C 4C10E03A 4C10E015 4C10E01D 8e2fcdbf)
declare -a NM=(HUD_switch HUD_mode HUD_mode_choice dyn_navi navi_screen map_sending nav_fusion)
declare -a OLD=()
for i in "${!IDS[@]}"; do v="$(read_val setting "${IDS[$i]}")"; OLD[$i]="$v"; echo "   ${NM[$i]} 0x${IDS[$i]} = ${v:-?}"; done
echo "   (map CONFIG 0x38B00030, read-only coding): $(read_val instr 38B00030)"

echo "== ENABLE HUD + nav surface =="
nav multi setting 4C10E023 1 setting 4C10E03A 1 setting 4C10E015 1 setting 4C10E01D 1 setting 8e2fcdbf 1 >/dev/null
nav setraw setting 4C10E025 1 >/dev/null   # HUD mode (try 1; 2/3 are alt modes)
echo "   HUD on + dynamic-navi + navi-screen + map-sending + nav-fusion enabled"

echo "== TRIGGER nav content =="
nav setraw instr 32B1102E 1 >/dev/null     # INSTRUMENT_HUD_NAVIGATION_MAP_SET (distinct from coding CONFIG)
nav ac 1000 39 >/dev/null                  # built-in 'Simple navigation' (cluster)
echo ""
echo ">> KEY STEP for turn-by-turn (found in nav app RE, handoff §18):"
echo ">>   1. HUD switch is now ON (0x4C10E023) -> the map app should now see a HUD."
echo ">>   2. Open the BYD map app (BydAutoTMap) -> Settings -> enable the 'HUD' /"
echo ">>      'use HUD view' toggle (pref PREFKEY_TMAP_SETTING_G_USE_HUD_VIEW)."
echo ">>   3. Start navigating. The app calls sendSimpleGuidanceInfo -> HUD."
echo ">> !!! LOOK AT THE WINDSHIELD HUD for arrow + distance + road name !!!"
echo ">>     (screencap can't see the HUD — use your eyes)"
echo "   Does any nav arrow / distance / nav-screen appear on the HUD? (y = win)"

# cluster screencap too, in case content lands on the cluster's HUD region
cap shell "fission_screencap -d 1 -p /data/local/tmp/hud1.png" >/dev/null 2>&1
cap pull /data/local/tmp/hud1.png ./hud1_cluster.png >/dev/null 2>&1 && echo "   (cluster shot -> ./hud1_cluster.png)"

echo "== try HUD mode 2 then 3 (alt W-modes) — watch HUD after each =="
for m in 2 3; do nav setraw setting 4C10E025 "$m" >/dev/null; echo "   HUD mode=$m — look at HUD"; sleep 2; done

echo "== RESTORE =="
for i in "${!IDS[@]}"; do
  ov="${OLD[$i]:-}"
  if [ -n "$ov" ]; then nav setraw setting "${IDS[$i]}" "$ov" >/dev/null; fi
done
echo "   restored Setting surface to saved values."
echo "DONE #1. Reboot fully cleans if anything looks off."
echo "REMINDER: full moving-map on HUD needs dealer UDS coding (0x38B00030) — not settable here."
