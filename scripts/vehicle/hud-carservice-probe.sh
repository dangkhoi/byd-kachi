#!/usr/bin/env bash
# hud-carservice-probe.sh — probe HUD-nav qua CarService ICarHudManager (KHÔNG phải HAL bydauto thô).
#
# RE (docs/diagnostics/factory-hud-nav-RE-avenues §0.2): tầng điều khiển HUD-nav thật là
# com.byd.car.feature.vision.ICarHudManager (DiCar.getCarManager), KHÔNG phải register HAL 0x38B00030.
#   READ:  getHudConfig(mask) · getHudSupportedModes · isNavigationMapEnabled/Fusion/Dynamic
#   WRITE: setNavigationMapEnabled(true)  → flip=TOGGLE (không cần coding dealer) / fail=coding-gated
# Status.STATUS_FAILED = -2147482648 = SENTINEL provisioning giống HAL (nếu set trả cái này = bị gate).
#
# NON-HANG: navopen-v5 self-halts (~1s + 9s watchdog). Reachability: nếu com.byd.car KHÔNG trên
# classpath app_process → probe báo rõ (cần chạy trong app có SDK). An toàn: 'hud' chỉ ĐỌC.
#
# Usage:
#   hud-carservice-probe.sh <car-serial>            # chỉ ĐỌC năng lực (an toàn)
#   hud-carservice-probe.sh <car-serial> set        # ĐỌC → thử BẬT nav (setNavigationMapEnabled true) → re-read
#                                                    #   (khôi phục: chạy lại với 'off')
#   hud-carservice-probe.sh <car-serial> off         # tắt lại (setNavigationMapEnabled false)
set -u
CAR="${1:?usage: $0 <car-serial> [set|off]}"; MODE="${2:-read}"
ADB="${ADB:-adb}"; JAR="${JAR:-$HOME/Documents/workspaces/experiments/byd/apks/navopen-v5.jar}"
NAV="CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen"
run(){ ( $ADB -s "$CAR" shell "$1" >/tmp/hudp.txt 2>&1 ) & local B=$!; ( sleep "${2:-12}"; kill -9 $B 2>/dev/null ) & local W=$!; wait $B 2>/dev/null; kill $W 2>/dev/null; cat /tmp/hudp.txt; }

( $ADB connect "$CAR" >/dev/null 2>&1 ) & sleep 2
$ADB -s "$CAR" push "$JAR" /data/local/tmp/navopen.jar >/dev/null 2>&1 && echo "  jar pushed"

echo "=== HUD capability (READ, an toàn) ==="
run "$NAV hud" 12

if [ "$MODE" = "set" ]; then
  echo "=== THỬ BẬT nav HUD (setNavigationMapEnabled true) ==="
  echo "  ⚠ Đây là WRITE config HUD (hoàn tác: chạy lại với 'off'). Nếu trả STATUS_FAILED(-2147482648) = bị coding gate."
  run "$NAV hudset true" 12
elif [ "$MODE" = "off" ]; then
  echo "=== TẮT nav HUD (setNavigationMapEnabled false) — khôi phục ==="
  run "$NAV hudset false" 12
fi

echo "=== cleanup ==="
run "rm -f /data/local/tmp/navopen.jar" 8
echo "  DONE."
echo "  Đọc kết quả: com.byd.car NOT on classpath = reachability NO (cần bundle SDK / chạy trong app)."
echo "  isNavigationMapEnabled flip false→true sau hudset = TOGGLE (mở khoá KHÔNG cần coding dealer!)."
echo "  set trả STATUS_FAILED / isNavigationMapEnabled nguyên false = coding-gated (cần VDS2100)."
