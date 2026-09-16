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
#
# ⚠ (V) FEATURE-FILTER 2026-09-17 — ĐÃ BỎ `w19` *"chế độ lái thể thao"* vì nút `drive_mode` đã bị owner chấm NO
#   và xoá khỏi registry. Lượt (V) đã bỏ ba ca T1 cùng nút ấy (`voice-cases.tsv:21-24`) nhưng **bỏ sót ca T2 này**.
#   [ĐO máy ảo 2026-09-17] hệ quả không phải một ca đỏ vô hại mà là một con số **nói sai về mô hình**: nhãn bị xoá
#   kéo theo cụm hotword *"CHẾ ĐỘ LÁI THỂ THAO"* biến mất khỏi bảng biasing ⇒ mô hình thôi được kéo về đuôi câu và
#   nghe ra *"chế độ lái"*, làm T2 tụt **24/28 → 23/28**. Con số ấy trông như mô hình kém đi, trong khi thật ra là
#   bộ ca hỏi một khả năng KHÔNG CÒN TỒN TẠI. Tổng ca T2: 28 → **27**. Id KHÔNG đánh số lại (id là khoá tra ngược
#   về các lượt đo cũ — cùng luật đã ghi ở `voice-cases.tsv`).
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

# ═══ BA CA "ĐUÔI IM LẶNG" — w26/w27/w28 (VOICE-HOTFIX 1.69) ═════════════════════════════════════════════
#
# Vì sao phải có: [ĐO host] `docs/diagnostics/voice-stream-eval-2026-09-16.md` §6 — nối thêm im lặng vào CUỐI
# một câu, **không đổi gì khác**, kéo bộ 25 WAV từ **22/25 xuống 6/25** (0,4 s ⇒ 18 · 0,75 s ⇒ 15 · 1,5 s ⇒ 11 ·
# 4 s ⇒ 6). Mà đó đúng là thứ đường đang chạy trên xe nạp vào mô hình: [ĐO xe] cửa sổ chốt ở `chot=4200ms`
# trong khi người ta nói xong ở 1–2 s ⇒ **2–3 giây đuôi không phải tiếng nói** đi thẳng vào bộ giải mã, lượt
# nào cũng thế. Ba tệp này tái hiện đúng ca ấy, nên chúng là bài canh cho phép **cắt tại điểm ngắt câu**:
# trước bản vá chúng nghe sai, sau bản vá phải nghe đúng như bản không có đuôi.
#
# Dùng chính câu của w02/w12/w20 (ba câu ngắn, đã biết chắc nghe đúng khi không có đuôi) để phần khác biệt duy
# nhất giữa hai bên là **cái đuôi** — nếu lấy câu mới thì một ca đỏ không nói được là do đuôi hay do câu.
python3 - "$OUT" <<'PYGEN'
import struct, sys, wave, pathlib
out = pathlib.Path(sys.argv[1])
# id nguồn → (id mới, số giây im lặng nối thêm)
for src, dst, tail in (("w02", "w26", 2.0), ("w12", "w27", 3.0), ("w20", "w28", 4.0)):
    s = out / f"{src}.wav"
    if not s.is_file():
        print(f"  ⚠ thiếu {s} — bỏ qua {dst}")
        continue
    with wave.open(str(s), "rb") as w:
        params, frames = w.getparams(), w.readframes(w.getnframes())
    # Im lặng SỐ TUYỆT ĐỐI (mẫu = 0). ⚠ Đây là giới hạn đã biết của bài canh này: trên mic thật đuôi là
    # tiếng nền cabin, không phải số 0 — xem cảnh báo cuối §6 của doc. Nó vẫn đo đúng thứ cần đo (cửa sổ có
    # được CẮT hay không), chỉ không thay được một lượt đo trên xe.
    pad = b"\x00" * int(tail * params.framerate * params.sampwidth * params.nchannels)
    with wave.open(str(out / f"{dst}.wav"), "wb") as w:
        w.setparams(params)
        w.writeframes(frames + pad)
    print(f"  {dst}  = {src} + {tail:g}s im lặng  ({(out / (dst + '.wav')).stat().st_size} bytes)")
PYGEN
# Ba ca mới vào danh sách câu — cùng CÂU GỐC, để bảng kết quả so được trực tiếp với w02/w12/w20.
{
  printf 'w26\tbật đèn đọc\n'
  printf 'w27\txem pin\n'
  printf 'w28\tlọc ngay\n'
} >> "$OUT/cases.tsv"

echo "== $n tệp WAV 16 kHz mono PCM16 tại $OUT (danh sách câu: $OUT/cases.tsv)"
