#!/usr/bin/env bash
# perf-snapshot.sh — chụp một lát hiệu năng của MỌI tiến trình Kachi trên máy ảo/xe, cùng một cách đo cho
# TRƯỚC và SAU (spec docs/specs/kachi-closeout-hardening.html R2). Không cần công cụ ngoài, chỉ adb.
#
#   scripts/emulator/perf-snapshot.sh <label> [window_s] [serial]
#
# Đo trong cửa sổ window_s (mặc định 60 s): CPU % từng tiến trình tính từ /proc/<pid>/stat (utime+stime, CLK_TCK=100)
# — chính xác hơn `top` vì lấy đúng cửa sổ; PSS/RSS (`dumpsys meminfo`); số luồng (`ps -T`); khung/jank
# (`dumpsys gfxinfo`); wakelock (`dumpsys power`); alarm/job đăng ký; dòng logcat của app trong cửa sổ;
# dòng KachiPerf. Kết quả: markdown ra stdout + JSON ở $OUT_DIR/<label>.json (mặc định docs/diagnostics/perf-closeout-2026-09-25/).
set -euo pipefail
LABEL="${1:?label}"; WINDOW="${2:-60}"; SERIAL="${3:-}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
[[ -n "$SERIAL" ]] && ADB="$ADB -s $SERIAL"
PKG="${PKG:-com.byd.launcher}"
OUT_DIR="${OUT_DIR:-docs/diagnostics/perf-closeout-2026-09-25}"
mkdir -p "$OUT_DIR"
# `< /dev/null`: adb shell đọc stdin ⇒ trong vòng `while read` nó nuốt hết dòng còn lại của process substitution.
sh() { $ADB shell "$@" 2>/dev/null </dev/null | tr -d '\r'; }

pids() { sh ps -A -o PID,NAME | awk -v p="$PKG" '$2 ~ "^"p {print $1" "$2}'; }
cpu_ticks() { sh cat /proc/"$1"/stat | awk '{print $14+$15}'; }   # utime+stime, jiffies

# bash 3.2 (macOS) không có mảng kết hợp ⇒ ba mảng chỉ số song song.
PIDS=(); NAMES=(); T0=(); T1=()
while read -r pid name; do [[ -n "$pid" ]] && { PIDS+=("$pid"); NAMES+=("$name"); T0+=("$(cpu_ticks "$pid" || echo 0)"); }; done < <(pids)
NPROC=$(sh cat /proc/cpuinfo | grep -c '^processor')
LOG_MARK=$(sh date +%s)
$ADB logcat -c 2>/dev/null || true
sleep "$WINDOW"
for i in "${!PIDS[@]}"; do T1+=("$(cpu_ticks "${PIDS[$i]}" || echo "${T0[$i]}")"); done

json="{\"label\":\"$LABEL\",\"window_s\":$WINDOW,\"nproc\":$NPROC,\"procs\":["
echo "### $LABEL — cửa sổ ${WINDOW}s, $NPROC lõi, $(date '+%Y-%m-%d %H:%M')"
echo
echo "| Tiến trình | PID | CPU % (1 lõi) | PSS KB | RSS KB | Luồng |"
echo "|---|---|---|---|---|---|"
first=1
for i in "${!PIDS[@]}"; do
  pid=${PIDS[$i]}; name=${NAMES[$i]}
  d=$(( ${T1[$i]} - ${T0[$i]} ))
  cpu=$(awk -v d="$d" -v w="$WINDOW" 'BEGIN{printf "%.2f", d/100/w*100}')
  pss=$(sh dumpsys meminfo "$pid" | awk '/TOTAL PSS:/{p=$3} /^ *TOTAL +[0-9]/{if(!p)p=$2} END{print p+0}')
  rss=$(sh cat /proc/"$pid"/status | awk '/^VmRSS:/{print $2+0}')   # Android 10 meminfo không in RSS
  thr=$(sh ps -T -p "$pid" | awk 'NR>1' | wc -l | tr -d ' ')
  echo "| $name | $pid | $cpu | $pss | $rss | $thr |"
  [[ $first == 1 ]] || json+=","; first=0
  json+="{\"name\":\"$name\",\"pid\":$pid,\"cpu_pct\":$cpu,\"pss_kb\":$pss,\"rss_kb\":$rss,\"threads\":$thr}"
done
json+="]"

gfx=$(sh dumpsys gfxinfo "$PKG" | awk '/Total frames rendered:/{t=$4} /Janky frames:/{j=$3; p=$4} END{print t+0" "j+0" "p}')
wl=$(sh dumpsys power | grep -c "$PKG" || true)
alarms=$(sh dumpsys alarm | grep -c "$PKG" || true)
jobs=$(sh dumpsys jobscheduler | grep -c "JOB #u0a[0-9]*/[0-9]* .*$PKG" || true)
loglines=$($ADB logcat -d -v brief 2>/dev/null | grep -c . || true)
applog=$(for pid in "${PIDS[@]}"; do $ADB logcat -d -v brief --pid="$pid" 2>/dev/null; done | grep -c . || true)
perf=$($ADB logcat -d -s KachiPerf 2>/dev/null | tail -3 | tr -d '\r' || true)
echo
echo "- Khung vẽ (gfxinfo $PKG): tổng/janky/%: $gfx"
echo "- Dòng \`$PKG\` trong dumpsys power (wakelock): $wl · alarm đăng ký: $alarms · job: $jobs"
echo "- Logcat trong cửa sổ: tổng $loglines dòng · của app $applog dòng"
echo "- KachiPerf: ${perf:-(chưa có dòng)}"
json+=",\"gfx\":\"$gfx\",\"wakelock_lines\":$wl,\"alarms\":$alarms,\"jobs\":$jobs,\"log_total\":$loglines,\"log_app\":$applog}"
echo "$json" > "$OUT_DIR/$LABEL.json"
echo
echo "_JSON: $OUT_DIR/$LABEL.json_"
