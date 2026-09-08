#!/usr/bin/env bash
# nav-screen-mode-probe.sh — Map SET_NAVI_SCREEN_STATUS_SET (0x4C10E015) value <-> cluster nav-display mode
# (Đơn giản / Màn hình nhỏ / Toàn màn hình / OFF) on a BYD DiLink 3 head unit. PARKED test (P + handbrake).
#
# WHY (RE 2026-08-13): the value->layout meaning is decided by the CLUSTER MCU/Qt firmware, NOT the
# decompiled Android Java — AmapService.setNaviScreenStatus() is a thin passthrough and AMAP itself only ever
# writes value 3 (its default/reset). So the value<->menu map can't be read off-car; learn it on-car.
#
#   MODE=readback : YOU open the OEM "nav on cluster" menu and pick each option by hand; after each pick the
#                   script READS BACK (navopen getraw) 0x4C10E015 + companions -> the ground-truth map.
#                   (Best method: let the OEM tell us the numbers.)
#   MODE=sweep    : the script WRITES 0x4C10E015 = 0..7, screenshots the cluster after each, reads it back,
#                   then a 0x4C10E01D (map-sending) x nav-screen combo, then RESTORES the originals.
#
# A "mode" may be a COMBINATION of Setting features: 0x4C10E015 navi-screen, 0x4C10E01D map-sending,
# 0x4C10E03A dynamic-navi. The sweep's combo pass tests the most likely pair (navi-screen x map-sending).
#
# Usage:
#   VEH=<ip:port> MODE=readback bash scripts/vehicle/nav-screen-mode-probe.sh
#   VEH=<ip:port> MODE=sweep    bash scripts/vehicle/nav-screen-mode-probe.sh
# navopen jar: auto-discovered + pushed, or assumed already at /data/local/tmp/navopen.jar.
# Parked only. A physical power-cycle fully cleans anything left behind.
set -u

ADB="${ADB:-adb}"
S="${VEH:-}"
MODE="${MODE:-readback}"
JAR_LOCAL="${NAVOPEN_JAR:-}"
OUT="${OUT:-./nav-mode-probe}"
SETTLE="${SETTLE:-2}"

command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
[ -x "$ADB" ] || command -v "$ADB" >/dev/null 2>&1 || { echo "FATAL: adb not found (set ADB=...)"; exit 1; }
if [ -z "$S" ]; then S="$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')"; fi
[ -n "$S" ] || { echo "FATAL: no adb device (set VEH=ip:port)"; exit 1; }
mkdir -p "$OUT"

NAVCP="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"
nav(){ "$ADB" -s "$S" shell "$NAVCP $*"; }
# Parse an int out of navopen getraw output (mirror hud1-nav-hud.sh read_val).
read_val(){ nav getraw "$1" "$2" 2>/dev/null | sed -n 's/.*= *\(-\{0,1\}[0-9][0-9]*\).*/\1/p' | tail -1; }
shot(){ "$ADB" -s "$S" shell "fission_screencap -d 0 -p /data/local/tmp/p.png" >/dev/null 2>&1; \
        "$ADB" -s "$S" pull /data/local/tmp/p.png "$OUT/$1" >/dev/null 2>&1 && echo "   shot -> $OUT/$1"; }

# Push navopen if a local jar is found; otherwise assume it is already on the device.
if [ -z "$JAR_LOCAL" ]; then
  for c in apks/navopen-v3.jar "$(dirname "$0")/../../apks/navopen-v3.jar" ./navopen-v3.jar; do
    [ -f "$c" ] && { JAR_LOCAL="$c"; break; }
  done
fi
if [ -n "$JAR_LOCAL" ] && [ -f "$JAR_LOCAL" ]; then
  "$ADB" -s "$S" push "$JAR_LOCAL" /data/local/tmp/navopen.jar >/dev/null 2>&1 && echo "pushed navopen ($JAR_LOCAL)"
else
  echo "note: local navopen jar not found; assuming /data/local/tmp/navopen.jar already on device"
fi
echo "device=$S  mode=$MODE  out=$OUT"

NS=4C10E015; MS=4C10E01D; DN=4C10E03A; EN=4C10E040   # navi-screen, map-sending, dynamic-navi, easy-navi-map-type
# EN (0x4C10E040 SETTING_EASY_NAVI_SIGNAL_MAP_TYPE) added 2026-08-14: "EASY_NAVI" ≈ "Đơn giản" — RE-found
# companion (DiCarServer Setting.java), never tested on-car. Read it alongside 015/01D/03A.
show_all(){ echo "   navi_screen(0x$NS)=$(read_val setting $NS)  map_sending(0x$MS)=$(read_val setting $MS)  dyn_navi(0x$DN)=$(read_val setting $DN)  easy_navi(0x$EN)=$(read_val setting $EN)"; }

case "$MODE" in
  readback)
    echo "== READBACK — pick each OEM menu option by hand, then press ENTER =="
    echo "current values:"; show_all
    TABLE="$OUT/value-map.txt"; : > "$TABLE"
    for label in "Don gian (Giua + ETA)" "Man hinh nho" "Toan man hinh" "OFF"; do
      echo ""
      read -r -p ">> In the car: open the OEM nav-on-cluster menu, pick [$label], then press ENTER... " _
      ns="$(read_val setting $NS)"; ms="$(read_val setting $MS)"; dn="$(read_val setting $DN)"; en="$(read_val setting $EN)"
      line="$(printf '%-22s -> 0x%s=%s  0x%s=%s  0x%s=%s  0x%s=%s' "$label" "$NS" "${ns:-?}" "$MS" "${ms:-?}" "$DN" "${dn:-?}" "$EN" "${en:-?}")"
      echo "   $line"; echo "$line" >> "$TABLE"
      shot "menu-$(echo "$label" | tr ' /()+' '______').png"
    done
    echo ""; echo "== value<->menu map =="; cat "$TABLE"
    echo "Send $TABLE back to set the app's NAV_SCREEN_* values + selector labels correctly."
    ;;
  sweep)
    echo "== SWEEP — save originals =="
    o_ns="$(read_val setting $NS)"; o_ms="$(read_val setting $MS)"
    echo "   saved navi_screen(0x$NS)=${o_ns:-?}  map_sending(0x$MS)=${o_ms:-?}"
    echo "== write 0x$NS = 0..7, screenshot the cluster after each =="
    for v in 0 1 2 3 4 5 6 7; do
      nav setraw setting $NS "$v" >/dev/null 2>&1; sleep "$SETTLE"
      echo " 0x$NS=$v (readback=$(read_val setting $NS))"; shot "sweep-$NS-$v.png"
    done
    echo "== combo: navi_screen {1,3} x map_sending {0,1} =="
    for ns in 1 3; do for ms in 0 1; do
      nav setraw setting $NS "$ns" >/dev/null 2>&1; nav setraw setting $MS "$ms" >/dev/null 2>&1; sleep "$SETTLE"
      echo " navi_screen=$ns map_sending=$ms"; shot "combo-ns${ns}-ms${ms}.png"
    done; done
    echo "== easy_navi (0x$EN) = 0..3 (RE 'EASY_NAVI' ≈ Đơn giản — first on-car test) =="
    o_en="$(read_val setting $EN)"
    for v in 0 1 2 3; do
      nav setraw setting $EN "$v" >/dev/null 2>&1; sleep "$SETTLE"
      echo " 0x$EN=$v (readback=$(read_val setting $EN))"; shot "sweep-$EN-$v.png"
    done
    [ -n "${o_en:-}" ] && nav setraw setting $EN "$o_en" >/dev/null 2>&1
    echo "== restore originals =="
    [ -n "${o_ns:-}" ] && nav setraw setting $NS "$o_ns" >/dev/null 2>&1
    [ -n "${o_ms:-}" ] && nav setraw setting $MS "$o_ms" >/dev/null 2>&1
    echo "   restored navi_screen=${o_ns:-?} map_sending=${o_ms:-?}  (physical power-cycle fully cleans)"
    echo "Compare $OUT/*.png to see which value renders which layout."
    ;;
  *) echo "FATAL: MODE must be 'readback' or 'sweep'"; exit 1;;
esac
echo "DONE. Parked-only; physical power-cycle to fully clean."
