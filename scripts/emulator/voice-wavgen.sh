#!/usr/bin/env bash
# ═══ Sinh bộ WAV tiếng Việt cho tầng T2 (audio → nhận dạng → ý định) ══════════════════════════════════════════
#
# Vì sao macOS TTS: máy ảo KHÔNG có micro thật, và `VoiceWavProbe` (app/.../voice/VoiceWavProbe.kt) cố ý KHÔNG
# có bộ chuyển đổi — nó chỉ nhận **WAV PCM 16-bit · 1 kênh · 16 kHz** (readHeader: format==1, channels==1,
# rate==VoiceCapture.SAMPLE_RATE=16000, bits==16). Đúng khuôn đó là việc của `afconvert` dưới đây.
#
# ⚠ Mức bằng chứng (CLAUDE.md §2): giọng TỔNG HỢP `say -v Linh` ≠ giọng thật + mic 4 kênh + ồn đường. Kết quả
#   T2 nói về "model + ngữ pháp + đường WAV→intent", KHÔNG nói về độ chính xác trên xe thật.
#
# Dùng:  scripts/emulator/voice-wavgen.sh [OUTDIR]
# Mặc định OUTDIR = /tmp/kachi-voice-wav (WAV ~100–300 kB/câu ⇒ KHÔNG commit vào repo).
set -euo pipefail

OUT="${1:-/tmp/kachi-voice-wav}"
VOICE="${VOICE:-Linh}"
mkdir -p "$OUT"

# id<TAB>câu — id là tên tệp (ASCII), câu là tiếng Việt có dấu đưa cho TTS.
CASES=$(cat <<'EOF'
w01	mở YouTube
w02	bật đèn đọc
w03	tắt đèn đọc
w04	mở kính trước trái
w05	đóng kính trước trái
w06	nhiệt độ hai mươi bốn độ
w07	dẫn đường đến Bitexco
w08	phát nhạc
w09	dừng nhạc
w10	đưa YouTube vào ô số hai
w11	mở khoá cửa
w12	xem pin
w13	tăng âm lượng
w14	giảm nhiệt độ
w15	bật sưởi ghế
w16	đóng hết kính
w17	mở cài đặt
w18	bài tiếp theo
w19	chế độ lái thể thao
w20	lọc ngay
w21	hôm nay trời đẹp quá
w22	pin còn bao nhiêu
w23	bật lọc bụi và tắt đèn đọc
w24	dẫn đường tới chợ Bến Thành bằng Waze
w25	đổi sang hồ sơ Chính
EOF
)

printf '%s\n' "$CASES" > "$OUT/cases.tsv"
n=0
while IFS=$'\t' read -r id text; do
  [ -z "${id:-}" ] && continue
  aiff="$OUT/$id.aiff"; wav="$OUT/$id.wav"
  say -v "$VOICE" "$text" -o "$aiff"
  afconvert -f WAVE -d LEI16@16000 -c 1 "$aiff" "$wav"
  rm -f "$aiff"
  n=$((n+1))
  printf '%-5s %-46s %s\n' "$id" "$text" "$(stat -f%z "$wav") bytes"
done <<< "$CASES"

echo "== $n tệp WAV 16 kHz mono PCM16 tại $OUT (danh sách câu: $OUT/cases.tsv)"
