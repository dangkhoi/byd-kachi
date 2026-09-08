#!/usr/bin/env bash
# cluster-centre-flatbuffer-probe.sh — VERIFY the B3.render root cause on-car.
#
# Hypothesis (from RE OEM AmapService, docs/diagnostics/cluster-centre-render-rootcause-2026-08-21.md):
#   The cluster-CENTRE ("Giữa + ETA") on this fission cluster renders from a NaviInfo flatbuffer
#   pushed via AutoContainerManager.sendInfo2(4, bytes) — NOT the HAL guide registers. This probe
#   pushes a known-good test NaviInfo flatbuffer and screencaps the cluster to see if it renders.
#
# SAFE: read-only getprop + a nav-content push (sendInfo2) + screencap. No destructive writes.
# NON-HANG: navopen-v4 self-halts (~1s + 9s watchdog); every adb call is watchdog-wrapped.
#
# Usage:  cluster-centre-flatbuffer-probe.sh <car-serial e.g. 192.168.x.x:5555>
set -u
CAR="${1:?usage: $0 <car-serial>}"
ADB="${ADB:-adb}"
JAR="${JAR:-$HOME/Documents/workspaces/experiments/byd/apks/navopen-v5.jar}"
NAV="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"
OUT="${OUT:-/tmp/clcentre-probe}"; mkdir -p "$OUT"
SV="${SV:-3}"    # SET_NAVI_SCREEN_STATUS value (3 default; thử SV=2 nếu 3 không ra centre — soft spot RE 08-14)

# Test NaviInfo flatbuffer (108B) — naviState=1, nextRouteName="Nguyen Hue", curToSegmentDist=250,
# nextTurnIcon=2, routeRemainTime=600, routeRemainDist=3000, eta="12:05", SegRemainDisAuto="250".
# Regenerate:  scripts/vehicle/gen-naviinfo-flatbuffer.py  (flatbuffers lib, slots from RE NaviInfo.java)
HEX="2400000000001e00240020001c0018000000140010000c000800000000000000000004001e0000002000000024000000b80b00005802000002000000fa0000001c0000000100000003000000323530000500000031323a30350000000a0000004e677579656e204875650000"

run(){ ( $ADB -s "$CAR" shell "$1" >"$OUT/o.txt" 2>&1 ) & local B=$!; ( sleep "${2:-9}"; kill -9 $B 2>/dev/null ) & local W=$!; wait $B 2>/dev/null; kill $W 2>/dev/null; cat "$OUT/o.txt"; }

echo "=== 0. connect + push navopen ==="
( $ADB connect "$CAR" >/dev/null 2>&1 ) & sleep 2
$ADB -s "$CAR" push "$JAR" /data/local/tmp/navopen.jar >/dev/null 2>&1 && echo "  jar pushed"

echo "=== 1. CLUSTER TYPE (quyết chẩn đoán) ==="
run "getprop ro.build.system.fission_single_os; getprop ro.product.name; getprop ro.build.system.cluster_type 2>/dev/null" 8
echo "  → !=\"1\" ⇒ centre PHẢI đi flatbuffer (đúng hypothesis). ==\"1\" ⇒ hypothesis sai (CAN/HAL path)."

echo "=== 2. AutoContainer reachability (acprobe) ==="
run "$NAV acprobe" 12

echo "=== 3. WARM-RESTART sequence (0x4C10E015=$SV + navistate 4→2 + flatbuffer keep-alive 12s) + screencap ==="
echo "  ⚠ Chạy với app ClusterNav Nav+HUD OFF (để AmapService/app không giành lại 0x4C10E015)."
# clcentre = 1 process: setSettingRaw(0x4C10E015,SV) + navistate 4, rồi lặp [navistate 2 + sendInfo2(4,flatbuffer)] ~300ms x 12s.
( run "$NAV clcentre 12 $SV $HEX" 24 ) &
CL=$!
sleep 5    # để frame latched giữa keep-alive
run "screencap -d 1 /sdcard/clcentre_mid.png" 8
$ADB -s "$CAR" pull /sdcard/clcentre_mid.png "$OUT/" >/dev/null 2>&1 && echo "  mid screencap → $OUT/clcentre_mid.png ($(wc -c <"$OUT/clcentre_mid.png" 2>/dev/null) bytes)"
wait $CL 2>/dev/null
echo "  → nếu clcentre báo sendInfo2_ok>0 mà mid.png vẫn ~12KB blank: thử SV=2 (SV=2 $0 <car>) hoặc layout door 4C10A018/4C130041 (xem doc §4)."

echo "=== 4. cleanup ==="
run "rm -f /data/local/tmp/navopen.jar /sdcard/clcentre_*.png" 8
echo "  DONE. Xem $OUT/clcentre_mid.png — nếu centre hiện 'Nguyen Hue / 250m / ETA' = HYPOTHESIS CONFIRMED."
echo "  (screencap >100KB + có chữ = render; ~12KB blank = chưa. Đọc ảnh QUA SUB-AGENT, không đọc trực tiếp.)"
