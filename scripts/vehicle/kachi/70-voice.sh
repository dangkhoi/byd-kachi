#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 70-voice.sh — V1 · ĐO GIỌNG NÓI THẬT TRÊN XE (playbook §2.16 · spec kachi-voice-command.html T18/T25)
#
# Bốn số đo mà off-car KHÔNG thể cho, và cả V1 đang chờ chúng:
#   1. **tỉ lệ nghe đúng** bằng giọng người thật, ở ba mức ồn (tắt máy · nổ máy+điều hoà · lăn bánh);
#   2. **độ trễ** từ lúc mic mở tới lúc có chữ (đo bằng MỐC LOGCAT của máy, không bằng cảm giác);
#   3. **CPU/RAM** mà một phiên nghe chiếm trên đầu xe (Vosk chạy TẠI MÁY — đây là cái giá của nó);
#   4. **lượt 2 (từ vựng mở)** có đọc ra tên bài / điểm đến không (V1.1, R15–R17).
#
# TỰ ĐỘNG TỚI ĐÂU: mọi thứ trừ hai việc mà máy không làm hộ được — **bật công tắc chế độ kiểm thử**
# và **nói vào micro**. Không có `input tap` theo toạ độ ở đây (xe 1920×720 ≠ máy ảo — xem _common.sh).
#
# WAV ĐỐI CHỨNG (không cần micro, dùng để tách "mô hình nghe kém" khỏi "micro/ồn kém"):
#   say -v Linh -o /tmp/k.aiff "bật đèn đọc"
#   afconvert -f WAVE -d LEI16@16000 -c 1 /tmp/k.aiff scripts/vehicle/kachi/wav/01-bat-den-doc.wav
# Script tự sinh 3 tệp ấy nếu máy có `say`+`afconvert`. **KHÔNG commit tệp WAV** (xem wav/.gitignore).
#
# DÙNG:  scripts/vehicle/kachi/70-voice.sh [<ip-xe>:5555]
#        PHASES="1"  scripts/vehicle/kachi/70-voice.sh …   (chỉ chạy pha 1 — xe tắt máy)
# ─────────────────────────────────────────────────────────────────────────────────────────────────
set -uo pipefail
. "$(dirname "$0")/_common.sh"

OUT="$(k_out)"; DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET="$(k_target "${1:-}")" || exit 4; export KACHI_TARGET="$TARGET"
CSV="$OUT/voice-accuracy.csv"
TMPL="$OUT/.voice-slice.txt"
WAVDIR="$DIR/wav"
EXTDIR="/sdcard/Android/data/$KACHI_PKG/files"
PHASES="${PHASES:-1 2 3}"
trap 'k_log_stop' EXIT
echo "carlog: $OUT · xe: $TARGET"; k_hr

# ── A. Điều kiện — đọc hết bằng máy, không hỏi người ─────────────────────────────────────────
echo "[A] Điều kiện chạy được phiên nghe"
k_log_start 70
k_state_snap 70 before
k_say "bản đang cài: $(k_installed_version) ($(k_installed_code))"

GRANTED="$(k_sh "dumpsys package $KACHI_PKG" 2>/dev/null | grep -c "android.permission.RECORD_AUDIO: granted=true" | tr -d ' ')"
if [ "${GRANTED:-0}" -gt 0 ] 2>/dev/null; then
  k_ok "RECORD_AUDIO đã cấp"
else
  k_warn "RECORD_AUDIO CHƯA cấp — phiên nghe sẽ không mở được micro"
  k_say "Hai đường, chọn một:"
  k_say "  (a) trong app: Cài đặt › Hệ thống & quyền › nút 'Cấp quyền/Sửa ngay' (PermissionPreflight tự chạy pm grant)"
  k_say "  (b) từ đây   : adb -s $TARGET shell pm grant $KACHI_PKG android.permission.RECORD_AUDIO"
  if k_confirm "cấp RECORD_AUDIO cho $KACHI_PKG bằng pm grant" \
               "adb -s $TARGET shell pm revoke $KACHI_PKG android.permission.RECORD_AUDIO"; then
    k_timed "pm grant RECORD_AUDIO" k_sh "pm grant $KACHI_PKG android.permission.RECORD_AUDIO" 2>&1 | tail -2
    GRANTED="$(k_sh "dumpsys package $KACHI_PKG" 2>/dev/null | grep -c "android.permission.RECORD_AUDIO: granted=true" | tr -d ' ')"
    [ "${GRANTED:-0}" -gt 0 ] 2>/dev/null && k_ok "đã cấp" || k_bad "vẫn chưa cấp được — chép nguyên văn lỗi về"
  fi
fi
k_cap "70-perm-record-audio.txt" "dumpsys package $KACHI_PKG | grep -i RECORD_AUDIO"

# Mô hình đã cài chưa. ⚠ Bản release KHÔNG debuggable ⇒ `run-as` báo "not debuggable" và shell KHÔNG
# đọc được /data/data/$KACHI_PKG/files/vosk. Ba đường gián tiếp, theo thứ tự tin cậy:
#   1. cầu kiểm thử (`state`) — nói thẳng ra;  2. logcat KachiVoiceModel/KachiVoiceEngine;
#   3. hàng Cài đặt › Hệ thống & quyền › Nâng cao › Nhận dạng giọng nói (tại máy) — đọc bằng mắt.
echo; echo "[A2] Mô hình nhận dạng (vosk-model-small-vn-0.4 · 32 MB nén · 51 MB trên đĩa)"
MODEL_READY=""
# ⚠ [CHƯA BIẾT] tên trường JSON của cầu (`model_ready` · `heard` · `grammar_text` · `free_text`) là
#   GIẢ ĐỊNH tại lúc viết — `KachiTestBridge` đang dựng ở nhánh khác. Trường sai ⇒ chuỗi rỗng ⇒ script
#   tự rơi xuống đường TAY, không chết. Khi cầu lên bản chính: đối chiếu tên trường rồi sửa ĐÚNG MỘT
#   chỗ này (và ở [C]/voice_one), đừng thêm nhánh mới.
if k_test state; then MODEL_READY="$(k_json "$K_TEST_JSON" model_ready)"; fi
if [ -n "$MODEL_READY" ]; then
  k_ok "cầu nói: model_ready=$MODEL_READY"
else
  k_warn "chưa hỏi được cầu ⇒ đọc bằng mắt trên màn Cài đặt (đường dưới đây tự mở)"
  k_sh "am start -n $KACHI_HOME_COMP --es open_settings_group system" >/dev/null 2>&1 || true
  k_todo "Cài đặt › Hệ thống & quyền › **Nâng cao** › *Nhận dạng giọng nói (tại máy)*"
  k_todo "Đã cài  ⇒ hàng ghi 'Đã cài vosk-model-small-vn-0.4 · 19529 từ · 51 MB trên đĩa' + nút **Gỡ mô hình**"
  k_todo "Chưa cài⇒ bấm **Tải mô hình tiếng Việt**. Gói 32 MB, tải bằng MẠNG CỦA XE (hoặc hotspot điện thoại)."
  k_say  "   Không có đường cài từ tệp cục bộ: VoiceModelStore chỉ nhận HTTPS (HttpConn.open) ⇒ phải có mạng."
  k_say  "   Ước lượng: 10 Mbps ≈ 30 s · 4 Mbps ≈ 70 s · 1 Mbps ≈ 4,5 phút, cộng ~10–20 s giải nén."
  k_say  "   sha256 được kiểm TRONG lúc cài (hàng hiện 'Đang kiểm gói (sha256)…'); bằng chứng cài xong"
  k_say  "   là con số **19529 từ**, không phải chuỗi sha (màn Cài đặt không in sha)."
  k_pause "Mô hình đã ở trạng thái 'Đã cài' thì Enter"
  k_shot 70 "hàng Nhận dạng giọng nói (tại máy)"
fi
k_say "Nút mic trên thanh trên CHỈ hiện khi mô hình đã cài (KachiTopStrip.refreshVoicePill) —"
k_say "vắng nút mic khi chưa cài là ĐÚNG THIẾT KẾ, không phải lỗi."

# ── B. Năng lực giọng của đầu xe (§2.14 — đọc thuần, gộp vào một tệp) ───────────────────────
k_hr; echo "[B] Năng lực giọng của đầu xe → voice-capability.txt"
{
  echo "# voice-capability.txt — $(date +%FT%T%z) · xe $TARGET · Kachi $(k_installed_version)"
  echo; echo "== gói liên quan giọng nói/TTS =="
  k_sh "pm list packages | grep -iE 'google|gms|speech|tts|iflytek|baidu|byd.*voice|kiki'" 2>&1
  echo; echo "== voice_recognition_service =="; k_sh "settings get secure voice_recognition_service" 2>&1
  echo; echo "== tts_default_synth =="; k_sh "settings get secure tts_default_synth" 2>&1
  echo; echo "== CPU / RAM =="
  k_sh "cat /proc/cpuinfo | grep -c processor" 2>&1
  k_sh "head -3 /proc/meminfo" 2>&1
  echo; echo "== ABI =="; k_sh "getprop ro.product.cpu.abilist" 2>&1
  echo; echo "== luồng vào audio (mic cho app thường) =="
  k_sh "dumpsys media.audio_flinger | grep -i -A3 'input'" 2>&1 | head -40
  echo; echo "== RECORD_AUDIO của Kachi =="; k_sh "dumpsys package $KACHI_PKG | grep -i RECORD_AUDIO" 2>&1
} > "$OUT/voice-capability.txt" 2>&1
k_say "→ voice-capability.txt ($(wc -l < "$OUT/voice-capability.txt" | tr -d ' ') dòng)"

# ── C. WAV đối chứng — tách "mô hình kém" khỏi "micro/ồn kém" ────────────────────────────────
k_hr; echo "[C] WAV đối chứng (không cần micro — VoiceWavProbe, R14)"
mkdir -p "$WAVDIR"
if ! ls "$WAVDIR"/*.wav >/dev/null 2>&1; then
  if command -v say >/dev/null 2>&1 && command -v afconvert >/dev/null 2>&1; then
    k_say "sinh 3 tệp WAV bằng giọng Linh của macOS…"
    i=0
    for t in "bật đèn đọc" "đặt nhiệt độ hai mươi hai" "dẫn đường tới bitexco bằng vietmap"; do
      i=$((i + 1))
      say -v Linh -o "/tmp/kachi-wav-$i.aiff" "$t" >/dev/null 2>&1 \
        && afconvert -f WAVE -d LEI16@16000 -c 1 "/tmp/kachi-wav-$i.aiff" "$WAVDIR/0$i.wav" >/dev/null 2>&1 \
        && k_ok "0$i.wav — «$t»"
      rm -f "/tmp/kachi-wav-$i.aiff"
    done
  else
    k_warn "máy không có say/afconvert ⇒ bỏ qua WAV. Tự tạo theo hướng dẫn ở đầu tệp này."
  fi
fi
if ls "$WAVDIR"/*.wav >/dev/null 2>&1; then
  for w in "$WAVDIR"/*.wav; do
    k_say "── WAV $(basename "$w")"
    # Đẩy vào CẢ HAI chỗ mà VoiceWavProbe.candidates tìm: Download (chỗ nghĩ tới đầu tiên) và thư mục
    # ngoài của riêng app (đọc được KHÔNG cần quyền bộ nhớ — đường chạy được ở mọi ROM).
    k_adb push "$w" "/sdcard/Download/kachi-voice-test.wav" >/dev/null 2>&1 || true
    k_adb push "$w" "$EXTDIR/kachi-voice-test.wav" >/dev/null 2>&1 || true
    if k_test wav --es path "$EXTDIR/kachi-voice-test.wav"; then
      printf '  lượt 1: %s\n  lượt 2: %s\n' "$(k_json "$K_TEST_JSON" grammar_text)" "$(k_json "$K_TEST_JSON" free_text)"
    else
      k_todo "Cài đặt › Hệ thống & quyền › Nâng cao › **Thử bằng tệp WAV** → chép dòng kết quả vào biên bản"
      k_pause "Xong thì Enter"
      k_shot 70 "kết quả WAV $(basename "$w")"
    fi
  done
else
  k_unk "không có WAV đối chứng ở buổi này — nếu tỉ lệ nghe thấp thì KHÔNG tách được nguyên nhân"
fi

# ── D. Nói THẬT — ba pha ồn tăng dần ────────────────────────────────────────────────────────
# Mốc thời gian của logcat (`-v time`) là `MM-DD HH:MM:SS.mmm` ⇒ độ trễ = hiệu hai mốc TRÊN MÁY,
# không dính sai số mạng adb hay thời gian người bấm Enter.
ms_between() {
  awk '
    function ms(s,  a, b) { split(s, a, ":"); split(a[3], b, "."); return ((a[1]*60 + a[2]) * 60 + b[1]) * 1000 + b[2] }
    /micro mở bằng nguồn/ && t0 == 0 { t0 = ms($2) }
    /nghe được:/                     { t1 = ms($2) }
    END { if (t0 > 0 && t1 >= t0) print t1 - t0; else print "" }
  ' "$1" 2>/dev/null
}
csv_field() { printf '"%s"' "$(printf '%s' "$1" | sed 's/"/""/g')"; }

[ -f "$CSV" ] || printf 'pha,câu,nghe được,ý,đúng?,ms\n' > "$CSV"

voice_one() {
  local phase="$1" say_it="$2" ts heard intent ms ok
  k_hr; printf '  👉 NÓI: «%s»\n' "$say_it"
  ts="$(k_sh 'date "+%m-%d %H:%M:%S.000"' 2>/dev/null | tr -d '\r')"
  if k_test listen; then
    k_say "cầu đã mở phiên nghe — nói NGAY BÂY GIỜ"
  else
    k_todo "Bấm nút mic trên thanh trên (hoặc phím vô-lăng đã gán 'Kachi nghe') rồi nói câu trên"
  fi
  k_pause "Nói xong, khi màn đã hiện phản hồi thì Enter"
  k_adb logcat -d -v time -T "$ts" -s KachiVoiceSession KachiVoiceRec KachiVoiceMic KachiVoiceIntents \
    > "$TMPL" 2>&1 || true
  heard="$(sed -n 's/.*nghe được: "\(.*\)".*/\1/p' "$TMPL" | tail -1)"
  [ -n "$heard" ] || heard="$(k_json "$K_TEST_JSON" heard)"
  ms="$(ms_between "$TMPL")"
  cat "$TMPL" >> "$OUT/70-logcat-utterances.txt"
  k_shot 70 "pha$phase «$say_it»"
  printf '  nghe được : %s\n  độ trễ    : %s ms\n' "${heard:-(logcat không có dòng nghe được)}" "${ms:-?}"
  printf '  Ý mà màn hiện (chép NGUYÊN VĂN dòng → của app, Enter nếu không có): '
  read -r intent < /dev/tty
  printf '  Xe làm ĐÚNG việc không? [y/N/s=bỏ qua] '
  read -r ok < /dev/tty
  case "$ok" in y|Y) ok="Y" ;; s|S) ok="bỏ qua" ;; *) ok="N" ;; esac
  {
    printf '%s,' "$(csv_field "$phase")"
    printf '%s,' "$(csv_field "$say_it")"
    printf '%s,' "$(csv_field "${heard:-}")"
    printf '%s,' "$(csv_field "${intent:-}")"
    printf '%s,' "$(csv_field "$ok")"
    printf '%s\n' "$(csv_field "${ms:-}")"
  } >> "$CSV"
  k_note "voice pha$phase «$say_it» → «${heard:-}» · $ok · ${ms:-?} ms"
}

# run_phase <pha> <tiêu đề> <danh sách câu, mỗi câu một dòng>
# ⚠ Danh sách đi vào bằng THAM SỐ, không bằng stdin: `voice_one` và `k_pause` đều đọc bàn phím, mà
#   một vòng `while read` nuốt stdin sẽ ăn mất chính những câu chưa nói (lỗi kinh điển của shell).
run_phase() {
  local phase="$1" title="$2" list="$3" s old_ifs
  case " $PHASES " in *" $phase "*) : ;; *) k_say "bỏ qua pha $phase (PHASES)"; return 0 ;; esac
  k_hr; echo "[D$phase] $title"
  k_pause "Xe đã ở đúng trạng thái trên chưa? Enter để bắt đầu (Ctrl-C để dừng cả bước)"
  old_ifs="$IFS"; IFS='
'
  for s in $list; do [ -n "$s" ] && voice_one "$phase" "$s"; done
  IFS="$old_ifs"
}

run_phase 1 "Pha 1 — xe ĐỖ, TẮT MÁY, cửa đóng (nền im nhất có thể). 10 câu chuẩn spec §6.1" "$(cat <<'P1'
bật đèn đọc
đặt nhiệt độ hai mươi hai
tăng âm lượng tối đa
đóng hết kính
mở khoá cửa
xem pin
pin còn bao nhiêu
chỉnh chế độ đèn pha sang auto
mở ứng dụng vtv go
bật đèn đọc và tắt đèn pha
P1
)"

run_phase 2 "Pha 2 — NỔ MÁY, ĐIỀU HOÀ mức thường, xe vẫn ĐỖ (số P, phanh tay). 10 câu, có V1.1" "$(cat <<'P2'
bật đèn đọc
đặt nhiệt độ hai mươi hai
mở khoá cửa
đóng hết kính
mở youtube vào ô hai
phát bài diễm xưa bằng youtube music
dẫn đường tới bitexco bằng vietmap
dẫn đường tới bitexco bằng google maps
bật đèn đọc và tắt đèn pha
pin còn bao nhiêu
P2
)"

run_phase 3 "Pha 3 — ĐANG LĂN BÁNH. ⚠ NGƯỜI NGỒI GHẾ PHỤ nói và bấm, KHÔNG phải người lái. 5 câu vô hại" "$(cat <<'P3'
pin còn bao nhiêu
xem nhiệt độ trong xe
đặt nhiệt độ hai mươi hai
tăng âm lượng
dẫn đường tới bitexco bằng vietmap
P3
)"

# ── E. CPU / RAM trong lúc nghe ─────────────────────────────────────────────────────────────
k_hr; echo "[E] CPU/RAM của một phiên nghe (Vosk giải mã TẠI MÁY — đây là cái giá của nó)"
k_sh "dumpsys meminfo $KACHI_PKG | grep -E 'TOTAL|Native Heap|Dalvik Heap'" 2>&1 | tee "$OUT/70-meminfo-idle.txt"
if k_test listen; then k_say "phiên nghe đang mở — đo NGAY"; else k_todo "Bấm nút mic rồi nói một câu DÀI"; fi
k_timed "top trong lúc nghe" k_sh "top -n 1 -b 2>/dev/null | grep -i 'byd.launcher' || top -n 1 | grep -i 'byd.launcher'" \
  2>&1 | tee "$OUT/70-top-listening.txt"
k_sh "dumpsys meminfo $KACHI_PKG | grep -E 'TOTAL|Native Heap'" 2>&1 | tee "$OUT/70-meminfo-listening.txt"
k_say "So hai tệp meminfo: phần chênh = RAM mà mô hình + bộ giải mã chiếm (mô hình nạp bằng mmap"
k_say "nên phần lớn là bộ nhớ tệp, không phải heap — đọc dòng TOTAL, đừng đọc mỗi Dalvik)."

# ── F. Đóng bước ────────────────────────────────────────────────────────────────────────────
k_hr
k_state_snap 70 after
k_state_diff 70
k_log_stop
rm -f "$TMPL"
if [ -f "$CSV" ]; then
  TOT="$(($(wc -l < "$CSV" | tr -d ' ') - 1))"
  YES="$(grep -c '"Y"' "$CSV" 2>/dev/null | tr -d ' ')"
  k_ok "voice-accuracy.csv — ${YES:-0}/${TOT:-0} câu đúng"
  k_say "⚠ Tỉ lệ này KHÔNG được suy sang câu chưa nói. Câu nào chưa đo thì để trống, đúng luật §2 playbook."
fi
k_say "Dọn WAV thử trên xe (hoàn tác):"
k_say "  adb -s $TARGET shell rm -f /sdcard/Download/kachi-voice-test.wav $EXTDIR/kachi-voice-test.wav"
echo "XONG bước 7. Tiếp: 60-cast.sh (rủi ro cao nhất — chiếu cụm)"
k_note "70-voice: xong (voice-capability.txt · voice-accuracy.csv · logcat-70.txt · diff-70.txt)"
