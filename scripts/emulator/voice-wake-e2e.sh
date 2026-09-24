#!/usr/bin/env bash
# ═══ HARNESS WAKE 'HEY KACHI' — profiling nghe→match, hàng trăm biến thể (owner 2026-09-24) ═════════════════════
# Sinh biến thể wake (hey kachi / kachi / ok kachi + biến âm + tốc độ + giọng) → say → WAV → T-BRIDGE `wake`
# → thu heard/wake(bool)/decode_ms. Có nhóm ÂM-THƯỜNG (không wake) để đo FALSE-ACCEPT.
#
# Owner nghi "nói 2-3 lần mới lên": tách 2 câu hỏi — (a) tỉ lệ WAKE (model NGHE + matcher NHẬN) ·
# (b) decode nhanh/chậm. Bước fire→lên-UI (activity) đo riêng.
#
# Dùng:  scripts/emulator/voice-wake-e2e.sh [SERIAL] [VOICES]   (VOICES vd "Linh" hoặc "Linh Lekha")
set -uo pipefail
SERIAL="${1:-emulator-5554}"
VOICES="${2:-Linh}"
ADB="$HOME/Library/Android/sdk/platform-tools/adb -s $SERIAL"
B="am broadcast -a com.byd.launcher.TEST -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge"
WD=/tmp/kachi-wake-e2e; mkdir -p "$WD"

# id \t câu \t nhóm(POS=phải wake / NEG=không được wake)
WAKE_POS="hey kachi|hey ka chi|hê kachi|hây kachi|kachi|ka chi|ca chi|ok kachi|ô kê kachi|okay kachi|hey ca chi|hey cà chi|hê ca chi|này kachi|kachi ơi|hey kachi ơi|kachi kachi|hey kachy|hey ka-chi|kạ chi"
WAKE_NEG="xin chào|hôm nay trời đẹp|mở nhạc đi|bật điều hòa|cảm ơn nhé|dẫn đường về nhà|một cách khác|ca sĩ hát hay|cái ghế này|khách sạn|kê khai thuế|con cá chình|các anh|cà phê sữa|kỳ nghỉ|cách mạng"

# tốc độ đọc (giả lập nói nhanh/chậm — như đời thật)
RATES="${RATES:-180 140 220}"

# bật bridge
$ADB root >/dev/null 2>&1; sleep 1
BID=$($ADB shell cat /proc/sys/kernel/random/boot_id 2>/dev/null|tr -d '\r')
UP=$($ADB shell 'cat /proc/uptime|cut -d" " -f1' 2>/dev/null|tr -d '\r')
UPMS=$(python3 -c "print(int(float('${UP:-0}')*1000))" 2>/dev/null||echo 0)
if [ -n "$BID" ] && [ "$UPMS" != 0 ]; then
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <string name="test_bridge_until">%s:%s</string>\n</map>\n' "$BID" "$((UPMS+3600000))" > "$WD/tb.xml"
  $ADB push "$WD/tb.xml" /data/local/tmp/tb.xml >/dev/null 2>&1
  $ADB shell "su 0 sh -c 'cp /data/local/tmp/tb.xml /data/data/com.byd.launcher/shared_prefs/kachi_test_bridge.xml; chown u0_a163:u0_a163 /data/data/com.byd.launcher/shared_prefs/kachi_test_bridge.xml'" >/dev/null 2>&1
  $ADB shell am force-stop com.byd.launcher >/dev/null 2>&1; sleep 2
  $ADB shell am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity >/dev/null 2>&1; sleep 4
fi

pos_hit=0; pos_n=0; neg_bad=0; neg_n=0; n=0
: > "$WD/perf.txt"; : > "$WD/miss.txt"; : > "$WD/falsepos.txt"
one() { # $1 câu  $2 nhóm  $3 voice  $4 rate
  say -v "$3" -r "$4" -o "$WD/w.aiff" "$1" 2>/dev/null
  afconvert -f WAVE -d LEI16@16000 -c 1 "$WD/w.aiff" "$WD/w.wav" 2>/dev/null
  $ADB push "$WD/w.wav" /data/local/tmp/w.wav </dev/null >/dev/null 2>&1
  local j; j=$($ADB shell "$B --es cmd wav --es path /data/local/tmp/w.wav" </dev/null 2>&1)
  local heard wake ms
  heard=$(printf '%s' "$j"|grep -oE '"heard":"[^"]*"'|head -1|sed 's/"heard":"//;s/"$//')
  wake=$(printf '%s' "$j"|grep -oE '"wake":(true|false)'|head -1|sed 's/"wake"://')
  ms=$(printf '%s' "$j"|grep -oE '"ms":[0-9]*'|head -1|sed 's/"ms"://')
  n=$((n+1)); [ -n "${ms:-}" ] && echo "$ms" >> "$WD/perf.txt"
  if [ "$2" = POS ]; then
    pos_n=$((pos_n+1))
    if [ "$wake" = true ]; then pos_hit=$((pos_hit+1)); else printf '%s\t%s\theard=%s\n' "$3/$4" "$1" "$heard" >> "$WD/miss.txt"; fi
  else
    neg_n=$((neg_n+1))
    if [ "$wake" = true ]; then neg_bad=$((neg_bad+1)); printf '%s\t%s\theard=%s\n' "$3/$4" "$1" "$heard" >> "$WD/falsepos.txt"; fi
  fi
}

for v in $VOICES; do for r in $RATES; do
  while IFS= read -r s; do [ -n "$s" ] && one "$s" POS "$v" "$r"; done < <(printf '%s' "$WAKE_POS" | tr '|' '\n')
  while IFS= read -r s; do [ -n "$s" ] && one "$s" NEG "$v" "$r"; done < <(printf '%s' "$WAKE_NEG" | tr '|' '\n')
done; done

echo "═══════════════════════════════════════════════"
echo "WAKE — tổng $n biến thể · giọng[$VOICES] tốc độ[$RATES]"
echo "① WAKE-RATE (POS phải nổ): $pos_hit/$pos_n ($((pos_hit*100/pos_n))%)"
echo "② FALSE-ACCEPT (NEG không được nổ): $neg_bad/$neg_n"
echo "③ decode-ms:"; python3 -c "
import sys
a=sorted(int(x) for x in open('$WD/perf.txt') if x.strip())
if a:
    import math
    p=lambda q:a[min(len(a)-1,int(math.ceil(q/100*len(a))-1))]
    print(f'   n={len(a)} p50={p(50)} p95={p(95)} max={max(a)} ms')
"
echo "── MISS (POS không nổ — model nghe/matcher trượt) ──"; sort -u "$WD/miss.txt" 2>/dev/null | head -30
echo "── FALSE-ACCEPT (NEG nổ nhầm) ──"; sort -u "$WD/falsepos.txt" 2>/dev/null | head -20
