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
# Đa dạng như ĐỜI THẬT: biến thể cách nói · thêm lịch sự · câu dài · đọc tắt · số nhà đủ dạng.
CASES=$(cat <<'EOF'
n01	dẫn đường đến sáu chín hoàng văn thái	Nav
n02	dẫn tới chợ bến thành	Nav
n03	dẫn đến sân bay tân sơn nhất	Nav
n04	dẫn đường đến một trăm ba mươi tư a điện biên phủ	Nav
n05	dẫn đường đến một hai ba xẹt ba tư huỳnh tấn phát	Nav
n06	cho tôi dẫn đường đến bảy trăm hai mươi lý thường kiệt nhé	Nav
n07	dẫn đường đến một nghìn tám trăm chín mươi tám cách mạng tháng tám	Nav
n08	dẫn đường về nhà	Nav
n09	làm ơn dẫn đường đến quận một	Nav
n10	chỉ đường tới bệnh viện chợ rẫy	Nav
n11	dẫn đường đến hai ba xuyệt năm nguyễn văn cừ	Nav
n12	dẫn đường tới vincom đồng khởi	Nav
n13	dẫn tôi đến công viên tao đàn	Nav
n14	đưa tôi tới landmark tám mốt	Nav
n15	tìm đường đến ga sài gòn	Nav
n16	dẫn đường đến năm trăm linh năm nguyễn trãi	Nav
n17	dẫn đường đến hai mươi bảy nguyễn huệ quận một	Nav
n18	dẫn đường về cơ quan	Nav
n19	dẫn đường đến trường đại học bách khoa	Nav
n20	navigate to bitexco	Nav
c01	bật đèn đọc	Control
c02	tắt đèn đọc	Control
c03	mở điều hòa	Control
c04	tắt điều hòa	Control
c05	tăng nhiệt độ hai mươi tư độ	Control
c06	giảm nhiệt độ	Control
c07	mở kính	Control
c08	đóng cửa sổ trời	Control
c09	bật ghế mát	Control
c10	tắt đèn ban ngày	Control
c11	bật sưởi ghế	Control
c12	đóng cốp	Control
c13	bật lọc bụi	Control
c14	tăng gió lên	Control
c15	giảm âm lượng	Control
c16	bật đèn pha	Control
c17	tắt đèn pha	Control
c18	mở hết kính	Control
c19	làm ơn bật điều hòa giúp tôi	Control
c20	đặt nhiệt độ hai lăm độ	Control
c21	bật ghế sưởi bên phụ	Control
c22	đóng tất cả cửa kính	Control
r01	nhiệt độ bao nhiêu	Read
r02	pin còn bao nhiêu phần trăm	Read
r03	xem áp suất lốp	Read
r04	xem tốc độ	Read
r05	bụi mịn bao nhiêu	Read
r06	còn bao nhiêu cây số	Read
r07	nhiệt độ ngoài trời bao nhiêu	Read
r08	mức xăng còn bao nhiêu	Read
r09	tốc độ hiện tại thế nào	Read
r10	xem quãng đường đã đi	Read
m01	mở nhạc sơn tùng	Media
m02	phát bài hạ còn vương nắng	Media
m03	mở nhạc trên youtube	Media
m04	dừng nhạc	Media
m05	bài tiếp theo	Media
m06	bài trước	Media
m07	phát nhạc trịnh	Media
m08	mở nhạc trẻ	Media
m09	tạm dừng	Media
m10	phát nhạc trên youtube music	Media
l01	mở youtube	Launcher
l02	mở cài đặt	Launcher
l03	mở danh sách ứng dụng	Launcher
e01	tạm biệt	EndSession
e02	xong rồi	EndSession
e03	cảm ơn	EndSession
e04	thôi	EndSession
e05	đủ rồi	EndSession
e06	tắt đi	EndSession
e07	thoát	EndSession
e08	cảm ơn nhé	EndSession
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
: > "$WD/findings.txt"; : > "$WD/perf.tsv"
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
  prev=$(printf '%s' "$j" | grep -oE '"preview":"[^"]*"' | head -1 | sed 's/"preview":"//;s/"$//')
  # ĐO TTS synth thật (feed preview qua Piper) — full-chain = decode+parse + synth
  synth=""
  if [ -n "$prev" ] && [ "${TTS_ON:-1}" = 1 ]; then
    jt=$($ADB shell "$BRIDGE --es cmd tts --es text \"'$prev'\"" </dev/null 2>&1)
    synth=$(printf '%s' "$jt" | grep -oE '"synth_ms":[0-9]*' | head -1 | sed 's/"synth_ms"://')
  fi
  # phân loại QUALITY
  status="PASS"
  if [ "$want" != "-" ] && [ "$kind" != "$want" ]; then
    if [ "$heard" != "$s" ]; then status="MISHEAR"; else status="FAIL"; fi
  fi
  case "$status" in PASS) pass=$((pass+1));; FAIL) fail=$((fail+1));; MISHEAR) mishear=$((mishear+1));; esac
  printf '%-4s %-8s want=%-10s got=%-10s dec=%-4s synth=%-5s | said[%s] heard[%s]\n' "$id" "$status" "$want" "${kind:-?}" "${ms:-?}" "${synth:-—}" "$s" "$heard"
  # PERFORMANCE: nhóm (chữ đầu id) + ms decode+parse
  printf '%s\t%s\t%s\n' "${id:0:1}" "${ms:-}" "${synth:-}" >> "$WD/perf.tsv"
  [ "$status" != "PASS" ] && printf '%s\t%s\tsaid=%s\theard=%s\twant=%s\tgot=%s\n' "$id" "$status" "$s" "$heard" "$want" "${kind:-?}" >> "$WD/findings.txt"
  sleep 1
done 3<<< "$CASES"

echo "═══════════════════════════════════════════════"
echo "① QUALITY — TỔNG $total · PASS $pass ($((pass*100/total))%) · FAIL(parser) $fail · MISHEAR(ASR) $mishear"
echo "── FINDINGS (câu không đạt) ──"; cat "$WD/findings.txt"
echo "═══════════════════════════════════════════════"
echo "② PERFORMANCE — decode+parse ms (đường ASR thật). p50/p95/max mỗi nhóm + tổng:"
python3 - "$WD/perf.tsv" <<'PYEOF'
import sys, collections, math
rows=[l.rstrip("\n").split("\t") for l in open(sys.argv[1]) if "\t" in l]
names={"n":"Nav","c":"Control","r":"Read","m":"Media","l":"Launcher","e":"EndSession"}
dec=collections.defaultdict(list); syn=collections.defaultdict(list); full=collections.defaultdict(list)
ad=[]; asy=[]; af=[]
for r in rows:
    g=r[0]; d=r[1] if len(r)>1 else ""; s=r[2] if len(r)>2 else ""
    di=int(d) if d.isdigit() else None; si=int(s) if s.isdigit() else None
    if di is not None: dec[g].append(di); ad.append(di)
    if si is not None: syn[g].append(si); asy.append(si)
    if di is not None and si is not None: full[g].append(di+si); af.append(di+si)
def pct(a,p):
    if not a: return 0
    a=sorted(a); return a[min(len(a)-1,int(math.ceil(p/100*len(a))-1))]
def row(lbl,d,s,f):
    return f"  {lbl:<11}{len(d):>4}{pct(d,50):>6}{pct(d,95):>6}{pct(s,50):>7}{pct(s,95):>7}{pct(f,50):>7}{pct(f,95):>7}{(max(f) if f else 0):>7}"
print(f"  {'nhóm':<11}{'n':>4}{'dec50':>6}{'dec95':>6}{'syn50':>7}{'syn95':>7}{'e2e50':>7}{'e2e95':>7}{'e2emx':>7}")
for g in sorted(dec): print(row(names.get(g,g),dec[g],syn[g],full[g]))
print(row("TỔNG",ad,asy,af)+"  (ms · e2e=decode+parse+TTS synth)")
PYEOF
