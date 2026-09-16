#!/usr/bin/env bash
# ═══ Dựng gói + nghiệm thu, một lệnh — chạy SAU khi `synth-pack.py` xong (hoặc để xem gói giữa chừng) ══
# Spec: docs/specs/kachi-voice-clone.html — T4 (đóng gói) · T5 (nghe thử chỗ ghép) · §6 (V-pack, V-nghe).
#
# Dùng:  SP=<scratchpad> REPO=<repo> bash scripts/voice/clone/finish-pack.sh [--allow-missing]
#
# Ba bước, theo đúng thứ tự phụ thuộc — bước sau đọc kết quả bước trước, không bước nào đoán:
#   1. build-pack.py     WAV → ADTS AAC + manifest.json + index.tsv/num.tsv + bảng ghim sha256
#   2. validate-pack.py  cỡ · thời lượng · tỉ lệ ASR theo tầng · danh sách còn lệch · 10 clip nghe thử
#   3. compose-check.py  T5: 10 câu có số dựng bằng ba mảnh, kèm bản ĐỌC LIỀN cùng câu để owner so
#
# ⚠ Bước 3 cần 10 câu nguyên vẹn đã sinh. Lệnh in ra ở cuối nói rõ phải chạy gì; cố ý KHÔNG tự chạy
#   `synth-pack.py` ở đây vì nó chiếm GPU hàng giờ và người chạy phải biết mình đang bật cái gì.
set -euo pipefail

: "${SP:?cần SP=<scratchpad>}"
: "${REPO:?cần REPO=<đường dẫn repo>}"
# Mặc định dùng venv CHỈ-NGHE (sherpa + soundfile + numpy), không dùng venv của F5.
# Lý do: ba bước dưới đây **không cần GPU**, mà `synth-pack.py` thì có thể đang chạy hàng giờ trên MPS —
# nạp thêm một bản torch/F5 nữa chỉ để đóng gói là tự giành tài nguyên với chính mình.
PY=${PY:-/tmp/sherpa-venv/bin/python}
PACK="$REPO/voice/tts/kachi-giong-be-v1"
EXTRA=("$@")

echo "── 1/3 · đóng gói ────────────────────────────────────────────────────────────"
"$PY" "$REPO/scripts/voice/clone/build-pack.py" --dst "$PACK" "${EXTRA[@]}"

echo
echo "── 2/3 · nghiệm thu trên máy ─────────────────────────────────────────────────"
"$PY" "$REPO/scripts/voice/clone/validate-pack.py" --pack "$PACK"

echo
echo "── 3/3 · T5 · chỗ ghép ───────────────────────────────────────────────────────"
WHOLE_TSV="$SP/voice-clone/t5-whole.tsv"
WHOLE_WAV="$SP/voice-clone/t5-whole-wav"
"$PY" "$REPO/scripts/voice/clone/compose-check.py" --emit-whole "$WHOLE_TSV" >/dev/null
if [ -d "$WHOLE_WAV/fixed" ]; then
  "$PY" "$REPO/scripts/voice/clone/compose-check.py" --pack "$SP/voice-clone/pack-wav" --whole-pack "$WHOLE_WAV"
else
  "$PY" "$REPO/scripts/voice/clone/compose-check.py" --pack "$SP/voice-clone/pack-wav"
  echo
  echo "⚠ chưa có bản ĐỌC LIỀN để so. Sinh (10 câu, ~3 phút GPU) rồi chạy lại:"
  echo "   $PY $REPO/scripts/voice/clone/synth-pack.py --clips $WHOLE_TSV --out $WHOLE_WAV"
fi
