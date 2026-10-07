#!/usr/bin/env bash
# thread-wakeups.sh — số lần THỨC (voluntary_ctxt_switches) theo từng luồng của Kachi trong một cửa sổ (2.96 R18,
# docs/diagnostics/perf-inventory-2026-10-07.md). Khoá theo tid (nhiều luồng trùng tên), cộng theo tên.
#
#   scripts/emulator/thread-wakeups.sh [window_s] [serial]
set -euo pipefail
W="${1:-60}"; SERIAL="${2:-}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
[[ -n "$SERIAL" ]] && ADB="$ADB -s $SERIAL"
PKG="${PKG:-com.byd.launcher}"
pid=$($ADB shell pidof "$PKG" </dev/null | tr -d '\r')
[[ -n "$pid" ]] || { echo "không thấy tiến trình $PKG" >&2; exit 1; }
snap() { $ADB shell "for t in /proc/$pid/task/*; do echo \${t##*/} \$(cat \$t/comm | tr ' ' _) \$(grep '^voluntary_ctxt_switches' \$t/status | awk '{print \$2}'); done" </dev/null | tr -d '\r'; }
t0=$(mktemp); t1=$(mktemp); trap 'rm -f "$t0" "$t1"' EXIT
snap > "$t0"; sleep "$W"; snap > "$t1"
awk -v w="$W" 'NR==FNR{a[$1]=$3; next} {d=$3-(a[$1]+0); if(d>0){s[$2]+=d; tot+=d}}
  END{for(k in s) printf "%-28s %6d (%.2f/s)\n",k,s[k],s[k]/w; printf "TOTAL %d (%.2f/s) pid='"$pid"'\n",tot,tot/w}' "$t0" "$t1" | sort -k2 -nr
