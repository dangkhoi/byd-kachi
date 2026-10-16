#!/usr/bin/env bash
# ═══ HARNESS "Hey Kachi" — bước 1/3: đọc câu gọi / câu thường bằng TTS macOS thành WAV 16 kHz mono PCM16 ═══════════
#
# VOICE-WAKE-CPU-BUDGET · VOICE-WAKE-TAIL-UNDECODED (3.1.9) — bộ số ở `docs/diagnostics/oncar-perf-2026-10-15-evening.md`
# §G2/§G6 được đo bằng harness này (soát Pass 9: đưa vào repo để chạy lại được; trước đó chỉ nằm ở thư mục tạm của phiên).
#
# ⚠ Mức bằng chứng (CLAUDE.md §2): giọng TỔNG HỢP `say -v Linh` ≠ giọng thật + mic xe; harness đo ĐƯỜNG QUYẾT ĐỊNH
# (cổng · ngân sách · đuôi câu) trên cùng một bộ WAV, không đo độ nhạy tuyệt đối trên xe.
#
# Dùng: gen-tts.sh <thư-mục-ra>      (cần macOS `say` + `afconvert`; VOICE=Linh mặc định; RATES="140 180 220")
# Ra:   <ra>/pos_<i>_<rate>.wav · neg_<i>_<rate>.wav · lyrics.wav (lời cho "nhạc có lời" — chữ trung tính, không bản quyền)
set -euo pipefail
OUT=${1:?thư mục ra}; HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VOICE="${VOICE:-Linh}"; RATES="${RATES:-140 180 220}"
mkdir -p "$OUT"
tts() {   # tts <text> <rate> <wav>
  local aiff; aiff="$(mktemp -t kachi-tts).aiff"
  say -v "$VOICE" -r "$2" "$1" -o "$aiff"
  afconvert -f WAVE -d LEI16@16000 -c 1 "$aiff" "$3"
  rm -f "$aiff"
}
gen() {   # gen <prefix> <list-file>
  local i=0 text
  while IFS= read -r text; do
    [ -z "$text" ] && continue
    for r in $RATES; do tts "$text" "$r" "$OUT/${1}_${i}_${r}.wav"; done
    i=$((i+1))
  done < "$2"
  echo "$1: $i câu × $(echo $RATES | wc -w | tr -d ' ') tốc độ"
}
gen pos "$HERE/pos.txt"
gen neg "$HERE/neg.txt"
# Lời hát giả: vài câu trung tính đọc liền (được lặp vòng khi trộn — `mix.py`), tốc độ thường.
tts "bầu trời xanh trong nắng mai, con đường dài theo gió bay, ta đi qua phố quen, nhớ về một ngày xa" 180 "$OUT/lyrics.wav"
echo "lyrics.wav ✓ → $OUT"
