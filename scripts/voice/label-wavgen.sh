#!/usr/bin/env bash
# ═══ Sinh bộ WAV cho ma trận hotword trên host (2.93 — VOICE-ALT-LABEL-HOTWORD và ba việc nối tiếp) ═════════════════
#
# Bổ sung cho `scripts/emulator/voice-wavgen.sh` (24 câu lệnh + 3 đuôi im lặng): bộ ấy KHÔNG có câu nào gọi app bằng
# NHÃN tiếng Việt, câu hỏi tầm đi, tên app tiếng Anh, hay lệnh ngắn là tiền tố của một cụm dài hơn — bốn thứ mà lượt
# 2.93 cần đo. Cùng khuôn: `say -v Linh` → `afconvert` PCM16 · mono · 16 kHz (đúng thứ `VoiceWavProbe` nhận).
#
# Bốn bộ (tham số 2; id không đánh số lại — id là khoá tra ngược về các lượt đo, cùng luật `voice-cases.tsv`):
#   nhan (mặc định)  n01–n21 · n25–n28 câu GỌI APP bằng nhãn tiếng Việt (có câu có ô — bẫy `w10` nuốt mệnh đề ô);
#                    n22–n24 câu TỰ DO chứa chữ của nhãn — canh hotword CHÈN lệnh (cùng vai `w21`)
#   hoi              q01–q10 câu hỏi tầm đi / pin: MỞ ĐẦU bằng *"còn"* (dòng `CÒN ĐI ĐƯỢC BAO NHIÊU` giúp) và có *"còn"*
#                    GIỮA câu (`w22` *"pin còn bao nhiêu"*) — đo việc gỡ hai dòng `CÒN …` (spec 2.93 §4.11)
#   ten              f01–f10 tên app tiếng Anh đọc THEO ÂM VIỆT (đúng dạng `VoiceAppPhonetics`) · e01–e08 cùng tên viết
#                    tiếng Anh (giọng TTS tự đọc) · g01–g03 câu tự do gần âm (*"tích tắc"*) — VOICE-PHONETIC-LABEL-HOTWORD
#   lenh             p01–p14 lệnh NGẮN là tiền tố theo từ của một cụm hotword DÀI hơn mang việc khác (*"bật điều hòa"* ⊂
#                    *"BẬT ĐIỀU HÒA TỰ ĐỘNG"*) — đo "hotword nối nốt cụm vào đuôi" (ca `w26`); dùng kèm `degrade-wavs.py --tails`
#
# ⚠ Mức bằng chứng (CLAUDE.md §2): giọng TỔNG HỢP ≠ giọng người + mic 4 kênh + ồn cabin. Số đo nói về
#   mô hình + tệp hotword, KHÔNG nói về độ chính xác trên xe.
#
# Dùng:  scripts/voice/label-wavgen.sh [OUTDIR] [nhan|hoi|ten|lenh]   (mặc định /tmp/kachi-voice-wav-labels · nhan)
# Rồi:   scripts/voice/hotword-matrix.py --model <MODEL_DIR> --wav <wavgen OUT> --wav <OUTDIR> none <tệp…>
set -euo pipefail

OUT="${1:-/tmp/kachi-voice-wav-labels}"
SET="${2:-nhan}"
VOICE="${VOICE:-Linh}"
mkdir -p "$OUT"

case "$SET" in
nhan) CASES=$(cat <<'EOF'
n01	mở máy ảnh
n02	mở danh bạ
n03	mở điện thoại
n04	mở đồng hồ
n05	mở lịch
n06	mở tin nhắn
n07	mở thư viện
n08	mở hình nền
n09	mở máy tính
n10	mở trình duyệt
n11	mở ghi âm
n12	mở thời tiết
n13	mở âm nhạc
n14	mở tệp
n15	mở ảnh
n16	mở bản đồ
n17	đưa danh bạ vào ô số hai
n18	đưa máy ảnh vào ô số ba
n19	mở hướng dẫn sử dụng
n20	mở cửa hàng play
n21	mở tìm kiếm bằng giọng nói
n22	hôm qua tôi gọi điện thoại cho mẹ
n23	lịch học tuần này thế nào
n24	máy tính của tôi hết pin rồi
n25	mở ghi chú
n26	mở tin tức
n27	mở camera hành trình
n28	đưa thời tiết vào ô số một
EOF
) ;;
hoi) CASES=$(cat <<'EOF'
q01	còn đi được bao nhiêu
q02	còn chạy được bao nhiêu
q03	xe còn đi được bao nhiêu
q04	còn đi được bao xa
q05	pin còn bao nhiêu phần trăm
q06	còn bao nhiêu phần trăm pin
q07	xe còn bao nhiêu điện
q08	quãng đường còn lại bao nhiêu
q09	tầm hoạt động còn bao nhiêu
q10	pin còn bao nhiêu
EOF
) ;;
ten) CASES=$(cat <<'EOF'
f01	mở za lô
f02	mở gia lô
f03	mở nét phờ lích
f04	mở tích tốc
f05	mở phây búc
f06	mở chát gi pi ti
f07	mở an đroi ô tô
f08	mở ca plây
f09	đưa za lô vào ô số hai
f10	đưa nét phờ lích vào ô số ba
e01	mở Zalo
e02	mở Netflix
e03	mở TikTok
e04	mở Facebook
e05	mở ChatGPT
e06	mở Android Auto
e07	mở CarPlay
e08	đưa Netflix vào ô số hai
g01	đồng hồ kêu tích tắc
g02	ăn phở bò ngon quá
g03	ca sĩ hát hay quá
EOF
) ;;
lenh) CASES=$(cat <<'EOF'
p01	bật điều hòa
p02	tắt điều hòa
p03	bật quạt
p04	mở cửa
p05	mở cốp
p06	bật đèn
p07	tắt đèn
p08	mở kính
p09	đóng kính
p10	bật sấy kính
p11	mở camera
p12	tắt camera
p13	giảm quạt
p14	bật sấy
EOF
) ;;
*) echo "bộ câu lạ: $SET (nhan|hoi|ten|lenh)" >&2; exit 2 ;;
esac

printf '%s\n' "$CASES" > "$OUT/cases.tsv"
n=0
while IFS=$'\t' read -r id text; do
  [ -z "${id:-}" ] && continue
  aiff="$OUT/$id.aiff"; wav="$OUT/$id.wav"
  say -v "$VOICE" "$text" -o "$aiff"
  afconvert -f WAVE -d LEI16@16000 -c 1 "$aiff" "$wav"
  rm -f "$aiff"
  n=$((n+1))
  printf '%-5s %-42s %s\n' "$id" "$text" "$(stat -f%z "$wav") bytes"
done <<< "$CASES"
echo "== $n tệp WAV 16 kHz mono PCM16 tại $OUT (bộ $SET · danh sách câu: $OUT/cases.tsv)"
