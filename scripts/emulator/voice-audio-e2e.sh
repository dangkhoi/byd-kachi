#!/usr/bin/env bash
# ═══ HARNESS E2E AUDIO — "như người thật" (owner 2026-09-24) ══════════════════════════════════════════════════
# Sinh câu tiếng Việt → macOS `say -v Linh` → WAV 16kHz → feed qua T-BRIDGE `wav` (đi ĐÚNG đường ASR decode thật)
# → thu heard/kind/preview + decode-ms → so kind mong đợi → phân loại PASS/FAIL/MISHEAR → in FINDINGS.
#
# KHÁC `say` (voice-cases.tsv): `say` feed TEXT (bỏ qua nghe/decode). `wav` đi qua model nghe THẬT ⇒ đo được
# lỗi ASR (nghe sai) lẫn lỗi parser. Đây là thứ owner muốn: "test như thật, vài trăm case".
#
# Dùng:  scripts/emulator/voice-audio-e2e.sh [SERIAL]
# Cần: emulator có Kachi 2.23 + voice model tải + testbridge BẬT (script tự bật qua prefs nếu có root).
set -uo pipefail
SERIAL="${1:-emulator-5554}"
ADB="$HOME/Library/Android/sdk/platform-tools/adb -s $SERIAL"
VOICE="${VOICE:-Linh}"
WD=/tmp/kachi-audio-e2e; mkdir -p "$WD"
BRIDGE="am broadcast -a com.byd.launcher.TEST -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge"

# id \t câu \t kind_mong_đợi(Nav/Control/Read/Media/Launcher/Profile/EndSession/OpenApp; '-'=không kiểm)
CASES=$(cat <<'EOF'
n01	dẫn đường đến sáu chín hoàng văn thái	Nav
n02	dẫn tới chợ bến thành	Nav
n03	dẫn đến sân bay tân sơn nhất	Nav
n04	dẫn đường đến một trăm ba mươi tư a điện biên phủ	Nav
n05	dẫn đường đến một hai ba xẹt ba tư huỳnh tấn phát	Nav
n06	dẫn đường đến bảy trăm hai mươi lý thường kiệt	Nav
n07	dẫn đường đến một nghìn tám trăm chín mươi tám cách mạng tháng tám	Nav
n08	dẫn đường về nhà	Nav
n09	dẫn đường đến quận một	Nav
n10	chỉ đường tới bệnh viện chợ rẫy	Nav
n11	dẫn đường đến hai ba xuyệt năm nguyễn văn cừ	Nav
n12	dẫn đường tới vincom đồng khởi	Nav
c01	bật đèn đọc	Control
c02	tắt đèn đọc	Control
c03	mở điều hòa	Control
c04	tắt điều hòa	Control
c05	tăng nhiệt độ hai mươi tư độ	Control
c06	giảm nhiệt độ	Control
c07	mở kính lái	Control
c08	đóng cửa sổ trời	Control
c09	bật ghế mát	Control
c10	tắt đèn ban ngày	Control
c11	bật sưởi ghế	Control
c12	mở cốp	Control
c13	khoá cửa	Control
c14	bật lọc bụi	Control
r01	nhiệt độ bao nhiêu	Read
r02	pin còn bao nhiêu	Read
r03	áp suất lốp thế nào	Read
r04	xem tốc độ	Read
r05	bụi mịn bao nhiêu	Read
m01	mở nhạc sơn tùng	Media
m02	phát bài hạ còn vương nắng	Media
m03	mở nhạc trên youtube	Media
m04	dừng nhạc	Media
m05	bài tiếp theo	Media
l01	mở youtube	Launcher
l02	mở bản đồ	Launcher
l03	mở cài đặt	Launcher
e01	tạm biệt	EndSession
e02	xong rồi	EndSession
e03	cảm ơn	EndSession
e04	thôi	EndSession
e05	đủ rồi	EndSession
EOF
)

# bật testbridge (best-effort, cần root emulator)
$ADB root >/dev/null 2>&1; sleep 1
BID=$($ADB shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r')
UP=$($ADB shell 'cat /proc/uptime | cut -d" " -f1' 2>/dev/null | tr -d '\r')
UPMS=$(python3 -c "print(int(float('${UP:-0}')*1000))" 2>/dev/null || echo 0)
if [ -n "$BID" ] && [ "$UPMS" != 0 ]; then
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <string name="test_bridge_until">%s:%s</string>\n</map>\n' "$BID" "$((UPMS+3600000))" > "$WD/tb.xml"
  $ADB push "$WD/tb.xml" /data/local/tmp/tb.xml >/dev/null 2>&1
  $ADB shell "su 0 sh -c 'cp /data/local/tmp/tb.xml /data/data/com.byd.launcher/shared_prefs/kachi_test_bridge.xml; chown u0_a163:u0_a163 /data/data/com.byd.launcher/shared_prefs/kachi_test_bridge.xml'" >/dev/null 2>&1
  $ADB shell am force-stop com.byd.launcher >/dev/null 2>&1; sleep 2
  $ADB shell am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity >/dev/null 2>&1; sleep 4
fi

pass=0; fail=0; mishear=0; total=0
: > "$WD/findings.txt"
while IFS=$'\t' read -r -u 3 id s want; do
  [ -z "${id:-}" ] && continue
  total=$((total+1))
  say -v "$VOICE" -o "$WD/$id.aiff" "$s" 2>/dev/null
  afconvert -f WAVE -d LEI16@16000 -c 1 "$WD/$id.aiff" "$WD/$id.wav" 2>/dev/null
  $ADB push "$WD/$id.wav" /data/local/tmp/e.wav </dev/null >/dev/null 2>&1
  j=$($ADB shell "$BRIDGE --es cmd wav --es path /data/local/tmp/e.wav" </dev/null 2>&1)
  heard=$(printf '%s' "$j" | grep -oE '"heard":"[^"]*"' | head -1 | sed 's/"heard":"//;s/"$//')
  kind=$(printf '%s' "$j" | grep -oE '"kind":"[^"]*"' | head -1 | sed 's/"kind":"//;s/"$//')
  ms=$(printf '%s' "$j" | grep -oE '"ms":[0-9]*' | head -1 | sed 's/"ms"://')
  # phân loại
  status="PASS"
  if [ "$want" != "-" ] && [ "$kind" != "$want" ]; then
    if [ "$heard" != "$s" ]; then status="MISHEAR"; else status="FAIL"; fi
  fi
  case "$status" in PASS) pass=$((pass+1));; FAIL) fail=$((fail+1));; MISHEAR) mishear=$((mishear+1));; esac
  printf '%-4s %-8s want=%-10s got=%-10s ms=%-4s | said[%s] heard[%s]\n' "$id" "$status" "$want" "${kind:-?}" "${ms:-?}" "$s" "$heard"
  if [ "$status" != "PASS" ]; then
    printf '%s\t%s\tsaid=%s\theard=%s\twant=%s\tgot=%s\n' "$id" "$status" "$s" "$heard" "$want" "${kind:-?}" >> "$WD/findings.txt"
  fi
  sleep 1
done 3<<< "$CASES"

echo "═══════════════════════════════════════════════"
echo "TỔNG $total · PASS $pass · FAIL(parser) $fail · MISHEAR(ASR nghe sai) $mishear"
echo "── FINDINGS (câu không đạt) ──"
cat "$WD/findings.txt"
