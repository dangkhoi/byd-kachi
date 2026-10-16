#!/usr/bin/env bash
# ═══ HARNESS "Hey Kachi" — bước 3/3: đẩy bộ WAV qua đúng engine wake đang ship trên MÁY ẢO (bản vehicleTest) ══════════
#
# Mỗi tệp đi qua cầu kiểm thử `kws --es name <engine> [--ei v <ms>]` (`TestBridgeKws.runAsr`): khung 100 ms → `VoiceWakeController`
# → `feed`/`observe`/`finish` → `onInferenceCost`, đồng hồ = thời lượng tiếng (VOICE-WAKE-CPU-BUDGET). In TSV `tên \t json`.
#  • engine `asr` = bản ship (cổng tiếng người + ngân sách + đuôi câu) · `asr-base` = trước 3.1.9 (cổng RMS + trần 40 khung/10 s).
#  • `v` = giá CPU giả mỗi lượt giải mã (ms) — máy ảo nhanh hơn xe nhiều; giá xe [ĐO 15/10] ≈ 1350.
#
# Dùng: run.sh <out.tsv> <asr|asr-base> [v-ms|-] <wav…>        (SERIAL=emulator-5554 mặc định; đọc bằng `score.py`)
set -uo pipefail
OUT=${1:?tệp TSV ra}; ENGINE=${2:?asr|asr-base}; V=${3:-}; shift 3 || { echo "thiếu tham số" >&2; exit 2; }
[ "$V" = "-" ] && V=""
SERIAL="${SERIAL:-emulator-5554}"; PKG=com.byd.launcher; ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../emulator" && pwd)"   # voice-common.sh + voice_e2e_json.py
# shellcheck source=../../emulator/voice-common.sh
source "$HERE/voice-common.sh"
require_emulator "harness wake"
: > "$OUT"
for f in "$@"; do
  adbs push "$f" /data/local/tmp/w.wav >/dev/null 2>&1 || { echo "push hỏng: $f" >&2; continue; }
  extra=""; [ -n "$V" ] && extra="--ei v $V"
  j="$(bridge "--es cmd kws --es path /data/local/tmp/w.wav --es name $ENGINE $extra")"
  printf '%s\t%s\n' "$(basename "$f" .wav)" "$j" | tee -a "$OUT"
done
