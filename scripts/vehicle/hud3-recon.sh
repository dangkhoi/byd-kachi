#!/usr/bin/env bash
# hud3-recon.sh — READ-ONLY on-car recon to enable the custom cluster speed-limit (#3) via CAN-frame inject.
#
# Background (handoff §22-23): the cluster sign value is fed by CAN -> data provider -> ZMQ
# (tcp://192.168.195.2:8889 / .3:6666) -> SetDataItem -> widget. To inject a custom number we send the
# speed-limit CAN frame with our value via the TEST device (navopen setbytes test 0xAA00020F). This script
# gathers the two missing values (the signal's CAN id + bit layout, and the data-item id) from
# /collect2/byd_datasource_config.xml, plus ZMQ/network topology, and reproduces opcode 2 (the 60 artifact).
#
# Everything here is READ-ONLY except the opcode-2 reproduce at the end (which you can skip). Parked only.
# Usage:  VEH=<ip:port> NAVOPEN_JAR=<path> ./hud3-recon.sh
set -u
ADB="${ADB:-adb}"; S="${VEH:-}"; JAR_LOCAL="${NAVOPEN_JAR:-}"; CAP="${CAP:-12}"
command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
if [ -z "$S" ]; then S="$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1; exit}')"; fi
[ -n "$S" ] || { echo "FATAL: no adb device (set VEH=ip:port)"; exit 1; }
if [ -z "$JAR_LOCAL" ]; then for c in "$(dirname "$0")/../../../apks/navopen-v3.jar" "./navopen-v3.jar"; do [ -f "$c" ] && { JAR_LOCAL="$c"; break; }; done; fi
echo "device=$S  jar=${JAR_LOCAL:-<none>}"
cap(){ "$ADB" -s "$S" "$@" & local p=$!; ( sleep "$CAP"; kill -9 "$p" 2>/dev/null )& local w=$!; wait "$p" 2>/dev/null; local r=$?; kill -9 "$w" 2>/dev/null; return $r; }
NAV="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"
[ -f "$JAR_LOCAL" ] && cap push "$JAR_LOCAL" /data/local/tmp/navopen.jar >/dev/null 2>&1

echo "==================== 1. DATASOURCE CONFIG (CAN id + data-item id) ===================="
echo ">> /collect2/byd_datasource_config.xml (the master key):"
cap shell "ls -la /collect2/ 2>/dev/null; echo '---'; cat /collect2/byd_datasource_config.xml 2>/dev/null | head -c 200" >/dev/null 2>&1
cap shell "cat /collect2/byd_datasource_config.xml 2>/dev/null | grep -iE -A2 -B2 'sign|speed.?limit|tsr|sla|limit|overspeed|traffic' | head -80"
echo ">> if empty, locate it:"
cap shell "find / -name 'byd_datasource_config.xml' 2>/dev/null; find /collect* /data /vendor /odm -iname '*datasource*' 2>/dev/null | head"
echo ">> pull full config for off-car study:"
cap pull /collect2/byd_datasource_config.xml ./byd_datasource_config.xml 2>&1 | tail -1

echo "==================== 2. ZMQ / NETWORK topology (path-B feasibility) ===================="
cap shell "ip addr 2>/dev/null | grep -E 'inet |195\\.' | head"
cap shell "netstat -anp 2>/dev/null | grep -E '8889|6666|192.168.195' | head"
cap shell "ps -A 2>/dev/null | grep -iE 'cluster|fission|datasource|byd' | head"

echo "==================== 3. current sign source + SLA state (baseline reads) ===================="
[ -f "$JAR_LOCAL" ] && cap shell "$NAV getraw adas 2D500020" 2>&1 | grep -i 'get '
[ -f "$JAR_LOCAL" ] && cap shell "$NAV getraw adas 38500022" 2>&1 | grep -i 'get '

echo "==================== 4. reproduce the '60' artifact (opcode 2) + screencap ===================="
echo ">> ac 1000 2 (all warning lamps + sign=60 test pattern). Skip with Ctrl-C. Power-cycle ready."
[ -f "$JAR_LOCAL" ] && cap shell "$NAV ac 1000 2 \"\"" >/dev/null 2>&1
sleep 1
cap shell "fission_screencap -d 1 -p /data/local/tmp/hud3_op2.png" >/dev/null 2>&1
cap pull /data/local/tmp/hud3_op2.png ./hud3_op2.png >/dev/null 2>&1 && echo "   cluster shot -> ./hud3_op2.png"
echo ">> cleanup: ac 1000 3 (may not fully clean — power-cycle if messy)"
[ -f "$JAR_LOCAL" ] && cap shell "$NAV ac 1000 3 \"\"" >/dev/null 2>&1

echo "==================== DONE ===================="
echo "NEXT: from section 1, read the speed-limit signal's CAN arbitration id + start-bit/len (+ data-item id)."
echo "Then build the CAN frame encoding 88 and inject:"
echo "   \$NAV setbytes test AA00020F <b0,b1,...>   (and AA000210)"
echo "and watch the cluster sign for 88 (hold/repeat if the real signal overwrites)."
