#!/usr/bin/env bash
# hud3-speedlimit-v4.sh — Custom speed-limit number on the BYD DL3 cluster (#3), NON-ROOT, parked.
#
# Uses navopen-v4 (Door A + Door B). Two phases; final visual proof is on-car.
#
#   PHASE 1 (recon, always safe — read-only):
#     Door A: read /collect2/byd_datasource_config.xml + /collect2/dataCollect/datacollectioncfg
#             via DiCarServer's privileged ICollect2FileStoreService (bypasses uid=2000 deny).
#             -> gives the SLA/traffic-sign CAN id + bit layout, and the collected-id list.
#     Door B (optional, set CANIDS): sniff whole CAN frames on-device to confirm the SLA frame.
#     Also dumps `dumpsys package providers` so you can override AUTH if the default is wrong.
#
#   PHASE 2 (inject, set FRAME): replay the SLA frame with your number via the TEST device
#     (BYDAutoTestDevice 0xAA00020F). Screencaps the cluster before/after. Reboot fully cleans.
#
# Every adb call is hard-timeout guarded (macOS has no `timeout`).
#
# USAGE
#   Recon:   VEH=<ip:port> ./hud3-speedlimit-v4.sh
#   Sniff:   VEH=<ip:port> CANIDS=0x2D5,0x234 SECS=20 ./hud3-speedlimit-v4.sh
#   Inject:  VEH=<ip:port> FRAME=00,00,02,34,00,00,58 ./hud3-speedlimit-v4.sh   # 0x58=88
#   (FRAME = the whole CAN frame as comma-hex; the CAN id is encoded inside the bytes,
#    exactly like ClusterDebug's `--es wholeFrame`. Build it from the Phase-1 config.)
set -u

ADB="${ADB:-adb}"
S="${VEH:-}"
JAR_LOCAL="${NAVOPEN_JAR:-}"
CAP="${CAP:-12}"                       # per-command hard timeout (s) for quick calls
AUTH="${AUTH:-content://com.byd.car.server.provider.CarServiceProvider}"
CFG_A="${CFG_A:-/collect2/byd_datasource_config.xml}"
CFG_B="${CFG_B:-/collect2/dataCollect/datacollectioncfg}"
CANIDS="${CANIDS:-}"                    # Door B: comma hex canids to register+sniff (optional)
SECS="${SECS:-20}"                     # Door B sniff seconds
FRAME="${FRAME:-}"                      # Phase 2: whole CAN frame comma-hex to inject (optional)
HOLD="${HOLD:-8}"                       # inject repeat count (beat the real-signal refresh)

command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
command -v "$ADB" >/dev/null 2>&1 || [ -x "$ADB" ] || { echo "FATAL: adb not found (set ADB=...)"; exit 1; }

if [ -z "$S" ]; then S="$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')"; fi
[ -n "$S" ] || { echo "FATAL: no adb device (set VEH=ip:port)"; exit 1; }

if [ -z "$JAR_LOCAL" ]; then
  for c in "$(dirname "$0")/../../../apks/navopen-v4.jar" "$(dirname "$0")/navopen-v4.jar" "./navopen-v4.jar"; do
    [ -f "$c" ] && { JAR_LOCAL="$c"; break; }
  done
fi
[ -f "$JAR_LOCAL" ] || { echo "FATAL: navopen-v4.jar not found (set NAVOPEN_JAR=path)"; exit 1; }
echo "device=$S  jar=$JAR_LOCAL  auth=$AUTH  cap=${CAP}s"

# timeout-guarded adb (macOS lacks `timeout`); cap2 takes a custom timeout for long calls (sniff).
cap(){ "$ADB" -s "$S" "$@" & local p=$!; ( sleep "$CAP"; kill -9 "$p" 2>/dev/null ) & local w=$!; wait "$p" 2>/dev/null; local rc=$?; kill -9 "$w" 2>/dev/null; return $rc; }
cap2(){ local t="$1"; shift; "$ADB" -s "$S" "$@" & local p=$!; ( sleep "$t"; kill -9 "$p" 2>/dev/null ) & local w=$!; wait "$p" 2>/dev/null; local rc=$?; kill -9 "$w" 2>/dev/null; return $rc; }
NAVCP="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"
nav(){ cap shell "$NAVCP $*"; }
shot(){ cap shell "fission_screencap -d 1 -p /data/local/tmp/hud3_$1.png" >/dev/null 2>&1; cap pull "/data/local/tmp/hud3_$1.png" "./hud3_$1.png" >/dev/null 2>&1 && echo "   cluster screencap -> ./hud3_$1.png"; }

echo "== push navopen-v4 =="; cap push "$JAR_LOCAL" /data/local/tmp/navopen.jar >/dev/null 2>&1 && echo "   ok"

# ---------------- PHASE 2: INJECT (only if FRAME set) ----------------
if [ -n "$FRAME" ]; then
  echo "== PHASE 2: INJECT wholeFrame=$FRAME via TEST 0xAA00020F (parked) =="
  shot 00_before_inject
  for i in $(seq 1 "$HOLD"); do nav setbytes test AA00020F "$FRAME" >/dev/null 2>&1; sleep 0.4; done
  echo "   injected ${HOLD}x (DOWN 0xAA00020F). Trying UP 0xAA000210 once too..."
  nav setbytes test AA000210 "$FRAME" >/dev/null 2>&1
  shot 10_after_inject
  echo ">> LOOK at the cluster speed-limit sign + ./hud3_10_after_inject.png . If it shows your number -> #3 SOLVED."
  echo "   (If the sign is messy afterwards, a head-unit power-cycle fully cleans it.)"
  exit 0
fi

# ---------------- PHASE 1: RECON (safe reads) ----------------
echo "== Door A: providers (override AUTH if a different collect2/spi authority appears) =="
cap shell "dumpsys package providers 2>/dev/null | grep -iE 'byd|spi|collect2|CarServiceProvider' | head -40" 2>/dev/null || true

echo "== Door A: read $CFG_A =="
nav readcfg "$CFG_A" "$AUTH" | tee ./doorA_datasource_config.txt
echo "== Door A: read $CFG_B =="
nav readcfg "$CFG_B" "$AUTH" | tee ./doorA_datacollectioncfg.txt

if [ -n "$CANIDS" ]; then
  echo "== Door B: sniff $SECS s, registering canids=$CANIDS =="
  cap2 "$((SECS+15))" shell "$NAVCP canmon $SECS $CANIDS" | tee ./doorB_canmon.txt
else
  echo "== Door B: skipped (set CANIDS=0x..,0x.. to sniff). Passive try (no register table): =="
  cap2 "$((SECS+15))" shell "$NAVCP canmon $SECS" | tee ./doorB_canmon.txt || true
fi

cat <<'NEXT'

------------------------------------------------------------------
NEXT (build the inject frame, then run Phase 2):
  1. Open ./doorA_datasource_config.txt — find the traffic-sign / speed-limit entry:
     its CAN arbitration id + start-bit/length/factor, and data-item id 564 (0x234).
     (Cross-check ./doorA_datacollectioncfg.txt for the same canid in the collected list,
      and ./doorB_canmon.txt for a live frame whose bytes track the real road limit.)
  2. Encode your number (e.g. 88) into the frame's data bytes at the right bit position,
     prefixed by the CAN id, as comma-hex (this is the ClusterDebug `wholeFrame`).
  3. Inject + self-verify:
       VEH=<ip:port> FRAME=<id..,..,val,..> ./hud3-speedlimit-v4.sh
  If Door A returned "query -> null" or "no binder", the SPI authority differs:
  re-run with AUTH=content://<authority-from-the-providers-dump-above>.
------------------------------------------------------------------
NEXT
echo "DONE (recon). No writes performed; nothing to restore."
