#!/usr/bin/env bash
# ═══ E2E "DẠY TÊN APP BẰNG GIỌNG" TRÊN MÁY ẢO — 2.91 VOICE-APP-NAMES (spec §6 V-emu, task V1) ═══════════════════════
#
# Đường ống đo: WAV TTS (macOS `say -v Linh`) → cầu `teach` (ĐÚNG đường giải mã của `wav`: VoiceWavProbe + recognizer +
# hotword của phiên lệnh) → TeachSample + TeachGuard → `op=save` (máy dò hồi quy + cổng ViewModel) → `wav`/`say` lại ⇒
# OpenApp đúng gói; va chạm lệnh xe bị CHẶN; đổi hồ sơ ⇒ mất tên; gỡ app ⇒ tên mồ côi; cài lại ⇒ dùng lại.
#
# ⚠ Mức bằng chứng (CLAUDE.md §2): giọng TỔNG HỢP + máy ảo API 29 ⇒ chỉ nói về ĐƯỜNG ỐNG (thu → giải mã → lưu → hotword →
# hiểu → mở), KHÔNG nói về độ chính xác giọng thật trên xe (E1/V5 🚗).
#
# Dùng: scripts/emulator/voice-teach-e2e.sh [--serial emulator-5554] [--apk <vehicleTest.apk>] [--out <dir>]
#                                          [--pkgs "com.a com.b"]   # mặc định: tự chọn 2 app nhãn Latin, ngoài bảng đích
set -uo pipefail

SERIAL="emulator-5554"
APK=""
OUT="/tmp/kachi-voice-teach"
PKG="com.byd.launcher"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PICK=""
while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2;;
    --apk) APK="$2"; shift 2;;
    --out) OUT="$2"; shift 2;;
    --pkgs) PICK="$2"; shift 2;;
    *) echo "tham số lạ: $1" >&2; exit 2;;
  esac
done
mkdir -p "$OUT"; chmod 700 "$OUT" 2>/dev/null || true
# shellcheck source=voice-common.sh
. "$HERE/voice-common.sh"

LOG="$OUT/teach-results.tsv"
: > "$LOG"
PASS=0; FAIL=0
check() {   # check <id> <mô tả> <điều kiện 0/1> <chi tiết>
  if [ "$3" = "1" ]; then PASS=$((PASS+1)); printf '  ✅ %s %s\n' "$1" "$2"; else FAIL=$((FAIL+1)); printf '  ❌ %s %s — %s\n' "$1" "$2" "$4"; fi
  printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >> "$LOG"
}
jq_py() { python3 -c "import json,sys; d=json.loads(sys.stdin.read() or '{}'); $1"; }
# has <chuỗi> <mẩu>… — in 1 nếu chuỗi chứa MỌI mẩu theo thứ tự, 0 nếu không (thay `case` trong `$(…)`: bash 3.2 vỡ ở `)`).
has() { python3 - "$@" <<'PY'
import sys
s, parts = sys.argv[1], sys.argv[2:]
i = 0
for p in parts:
    j = s.find(p, i)
    if j < 0:
        print(0); sys.exit()
    i = j + len(p)
print(1)
PY
}

TEST_MODE_ON=0
ORIG_PROFILE=""
UNINSTALLED=""
cleanup() {
  local rc=$?
  if [ -n "$UNINSTALLED" ]; then adbs shell cmd package install-existing "$UNINSTALLED" >/dev/null 2>&1 || true; fi
  if [ "$TEST_MODE_ON" = "1" ]; then
    start_home
    bridge "--es cmd teach_clear" >/dev/null || true
    if [ -n "$ORIG_PROFILE" ]; then bridge "--es cmd profile --es name $(shq "$ORIG_PROFILE")" >/dev/null || true; fi
    disable_test_mode
    echo "── đã xoá tên đã dạy của lượt chạy + tắt chế độ kiểm thử"
  fi
  adbs shell "rm -f /sdcard/Android/data/$PKG/files/wavin/teach-*" >/dev/null 2>&1 || true
  return $rc
}
trap cleanup EXIT

require_emulator
adbs get-state >/dev/null 2>&1 || die "không thấy thiết bị $SERIAL"
if [ -n "$APK" ]; then note "cài $APK"; adbs install -r "$APK" >/dev/null || die "cài APK hỏng"; fi
detect_data_mode
note "đường ghi dữ liệu app: $MODE"
TEST_MODE_ON=1
enable_test_mode
ST="$(state_json)"
[ "$(printf '%s' "$ST" | json_get test_mode_minutes_left)" -gt 0 ] 2>/dev/null || die "chế độ kiểm thử vẫn TẮT: $(printf '%s' "$ST" | head -c 300)"
READY="$(printf '%s' "$ST" | json_get voice_model.ready)"
[ "$READY" = "True" ] || [ "$READY" = "true" ] || die "mô hình nghe chưa sẵn sàng (chạy voice-e2e.sh --modeldir trước)"
ORIG_PROFILE="$(printf '%s' "$ST" | json_get profile.active)"
VER="$(printf '%s' "$ST" | json_get app.version_name)"
note "Kachi $VER · hồ sơ «${ORIG_PROFILE}»"
bridge "--es cmd teach_clear" >/dev/null

# ── 1. Chọn app: nhãn Latin, KHÔNG thuộc bảng đích (Kachi chưa có cách gọi nào), đang có trên máy ảo ──────────────
TARGETS="com.google.android.youtube com.google.android.apps.youtube.music com.spotify.music com.zing.mp3 com.google.android.apps.maps com.waze vn.vietmap.vietmapgps"
if [ -z "$PICK" ]; then
  # Ưu tiên nhãn tiếng Anh ≥ 2 âm tiết khi đọc bằng giọng Việt — [ĐO máy ảo 06/10] "Chrome" ⇒ mô hình in «cơm», "Tệp" ⇒
  # «thể»: tên 3 chữ cái cần ≥ 2 lượt giống hệt (spec §7 OQ4) ⇒ đo riêng ở mục 10. Danh sách chỉ là THỨ TỰ THỬ.
  for p in com.google.android.apps.docs com.android.chrome com.google.android.music ai.zalo.kiki.car com.google.android.gm com.android.documentsui; do
    case " $TARGETS " in *" $p "*) continue;; esac
    adbs shell pm path "$p" >/dev/null 2>&1 && PICK="$PICK $p"
    [ "$(echo $PICK | wc -w)" -ge 2 ] && break
  done
fi
set -- $PICK
[ $# -ge 2 ] || die "không tìm được 2 app nhãn Latin ngoài bảng đích trên máy ảo (truyền --pkgs)"
APP1=$1; APP2=$2
label_of() { bridge "--es cmd teach_text --es pkg $(shq "$1") --es text $(shq zzzz)" | json_get label; }
L1="$(label_of "$APP1")"; L2="$(label_of "$APP2")"
note "app thử: $APP1 «${L1}» · $APP2 «${L2}»"
[ -n "$L1" ] && [ -n "$L2" ] || die "không đọc được nhãn app (cầu teach_text)"

# ── 2. WAV: 3 lượt dạy (3 tốc độ) + 1 lượt thử (tốc độ khác) + câu có mệnh đề ô ─────────────────────────────────────
#
# Câu đưa cho TTS = cách một người Việt ĐỌC cái tên (giọng `Linh` chỉ đọc chữ Việt). [ĐO máy ảo 06/10, lượt 1–2] đưa nhãn
# tiếng Anh trần ("mở Gmail" · "mở Drive" · "mở Chrome") thì mô hình in MỘT âm tiết ≤ 3 chữ cái («ghe» · «nay» · «cơm»)
# — ghi ở docs/diagnostics/voice-app-names-emu-2026-10-06.md; quyết định điều phối 2.91 (spec §7 OQ4): 3 chữ cái lưu được
# khi ≥ 2 lượt ra ĐÚNG chuỗi ấy + qua mọi cổng ⇒ đo ở mục 10 bằng nhãn TRẦN. Bảng dưới là cách đọc Việt hoá (không phải bảng
# của Kachi — Kachi không biết bảng này); gói ngoài bảng ⇒ đọc nhãn trần.
spoken_of() {
  case "$1" in
    com.google.android.gm) printf 'gờ meo';;
    com.google.android.apps.docs) printf 'gu gồ đờ rai';;
    com.android.chrome) printf 'cờ rôm';;
    com.google.android.music) printf 'pờ lây mi u dích';;
    ai.zalo.kiki.car) printf 'ki ki';;
    *) printf '%s' "$2";;
  esac
}
S1="$(spoken_of "$APP1" "$L1")"; S2="$(spoken_of "$APP2" "$L2")"
note "câu đọc cho TTS: «mở ${S1}» · «mở ${S2}»"
WAVS="$OUT/wav"; mkdir -p "$WAVS"
gen() { local id=$1 rate=$2 text=$3; say -v Linh -r "$rate" "$text" -o "$WAVS/$id.aiff" && afconvert -f WAVE -d LEI16@16000 -c 1 "$WAVS/$id.aiff" "$WAVS/$id.wav" && rm -f "$WAVS/$id.aiff"; }
DST="/sdcard/Android/data/$PKG/files/wavin"
adbs shell "mkdir -p $DST"
for n in 1 2; do
  eval "SP=\$S$n"
  gen "teach-$n-a" 165 "mở $SP"; gen "teach-$n-b" 185 "mở $SP"; gen "teach-$n-c" 205 "mở $SP"; gen "teach-$n-t" 175 "mở $SP"
  gen "teach-$n-s" 180 "đưa $SP vào ô số hai"
done
# Mục 10 — nhãn TRẦN của app 2 (ra tên NGẮN 3 chữ cái ở [ĐO 06/10]) · Gmail nếu có (ra «ghe» = «ghế» ⇒ va chạm lệnh ghế).
gen "teach-short-a" 165 "mở $L2"; gen "teach-short-b" 205 "mở $L2"; gen "teach-short-t" 185 "mở $L2"
GMAIL="com.google.android.gm"
HAS_GMAIL=0
if [ "$APP1" != "$GMAIL" ] && [ "$APP2" != "$GMAIL" ] && adbs shell pm path "$GMAIL" >/dev/null 2>&1; then
  HAS_GMAIL=1; gen "teach-gm-a" 165 "mở Gmail"; gen "teach-gm-b" 205 "mở Gmail"
fi
for f in "$WAVS"/*.wav; do adbs push "$f" "$DST/$(basename "$f")" >/dev/null 2>&1; done

wav_intents() { bridge "--es cmd wav --es path $(shq "$DST/$1.wav")"; }
kinds() { jq_py "print(','.join(i.get('kind','?') for i in d.get('intents') or []))"; }
previews() { jq_py "print(' / '.join(i.get('preview','') for i in d.get('intents') or []))"; }

# ── 3. Mốc gốc: trước khi dạy ────────────────────────────────────────────────────────────────────────────────────
for n in 1 2; do
  eval "L=\$L$n"
  J="$(wav_intents "teach-$n-t")"
  printf 'before\tteach-%s-t\t%s\n' "$n" "$J" >> "$OUT/raw.tsv"
  note "trước khi dạy «${L}»: nghe «$(printf '%s' "$J" | json_get heard)» ⇒ $(printf '%s' "$J" | previews)"
done

# ── 4. Dạy: 3 WAV/app, xem phán quyết; lưu lượt ĐẦU có phán quyết MỚI/CẢNH BÁO (hộp dạy: CẢNH BÁO ⇒ "Vẫn lưu", OQ9) ──
declare -a NAMES KNOWN
for n in 1 2; do
  eval "P=\$APP$n; L=\$L$n"
  SAVEV=""; KNOWN[$n]=0
  for v in a b c; do
    J="$(bridge "--es cmd teach --es pkg $(shq "$P") --es path $(shq "$DST/teach-$n-$v.wav")")"
    printf 'teach\tteach-%s-%s\t%s\n' "$n" "$v" "$J" >> "$OUT/raw.tsv"
    VD="$(printf '%s' "$J" | json_get verdict)"
    echo "  [$n$v] nghe «$(printf '%s' "$J" | json_get heard)» ⇒ tên «$(printf '%s' "$J" | json_get name)» · $VD $(printf '%s' "$J" | json_get reasons)"
    case "$VD" in NEW|WARN) [ -n "$SAVEV" ] || SAVEV=$v;; ALREADY) KNOWN[$n]=1; [ -n "${NAMES[$n]:-}" ] || NAMES[$n]="$(printf '%s' "$J" | json_get name)";; esac
  done
  if [ -n "$SAVEV" ]; then
    J="$(bridge "--es cmd teach --es pkg $(shq "$P") --es path $(shq "$DST/teach-$n-$SAVEV.wav") --es op save")"
    printf 'save\tteach-%s-%s\t%s\n' "$n" "$SAVEV" "$J" >> "$OUT/raw.tsv"
    NAMES[$n]="$(printf '%s' "$J" | json_get name)"; KNOWN[$n]=0
    check "S$n" "lưu tên «${NAMES[$n]}» cho $P" "$( [ "$(printf '%s' "$J" | json_get saved)" = "True" ] && echo 1 || echo 0)" "$(printf '%s' "$J" | head -c 300)"
  elif [ "${KNOWN[$n]}" = "1" ]; then
    check "S$n" "«${NAMES[$n]}» Kachi ĐÃ HIỂU SẴN ⇒ không lưu (đúng luật)" 1 ""
  else
    check "S$n" "có ít nhất một lượt dạy lưu được cho $P" 0 "mọi lượt bị CHẶN/dưới sàn — xem raw.tsv"
  fi
done
ST="$(state_json)"
note "tên đã dạy (đếm): $(printf '%s' "$ST" | jq_py "print(d.get('state',d).get('voice_names'))")"

# ── 5. Sau khi dạy: WAV thử (tốc độ khác) + say chuỗi đã nghe + mệnh đề ô + đóng ─────────────────────────────────────
for n in 1 2; do
  eval "P=\$APP$n; L=\$L$n"
  NAME="${NAMES[$n]}"
  J="$(wav_intents "teach-$n-t")"
  printf 'after\tteach-%s-t\t%s\n' "$n" "$J" >> "$OUT/raw.tsv"
  H="$(printf '%s' "$J" | json_get heard)"; PV="$(printf '%s' "$J" | previews)"
  check "W$n" "WAV thử (tốc độ khác) «${H}» ⇒ mở «${L}»" "$(has "$PV" "$L")" "$PV"
  J="$(bridge "--es cmd say --es text $(shq "mở $NAME")")"
  check "T$n" "say «mở ${NAME}» ⇒ OpenApp «${L}»" "$( [ "$(printf '%s' "$J" | kinds)" = "OpenApp" ] && has "$(printf '%s' "$J" | previews)" "$L" || echo 0)" "$(printf '%s' "$J" | previews)"
  J="$(bridge "--es cmd say --es text $(shq "đưa $NAME vào ô số hai")")"
  check "O$n" "say «đưa $NAME vào ô số hai» ⇒ OpenApp ô 2" "$(has "$(printf '%s' "$J" | previews)" "$L" "2")" "$(printf '%s' "$J" | previews) · $(printf '%s' "$J" | jq_py "print(d.get('replies'))")"
  J="$(wav_intents "teach-$n-s")"
  # ĐO độ ổn định (KHÔNG tính PASS/FAIL): câu KHÁC ("đưa … vào ô số hai") có ra CÙNG chuỗi tên như lúc dạy không.
  echo "  ℹ WS$n câu có mệnh đề ô: nghe «$(printf '%s' "$J" | json_get heard)» ⇒ $(printf '%s' "$J" | previews) (tên đã dạy «${NAME}»)"
  printf 'INFO\tWS%s\t%s\n' "$n" "$J" >> "$OUT/raw.tsv"
  J="$(bridge "--es cmd say --es text $(shq "đóng $NAME")")"
  check "C$n" "say «đóng ${NAME}» ⇒ Unknown (APP_CLOSE, không mở app)" "$( [ "$(printf '%s' "$J" | kinds)" = "Unknown" ] && echo 1 || echo 0)" "$(printf '%s' "$J" | previews)"
  start_home
done

# ── 6. Va chạm lệnh xe ⇒ CHẶN; câu lệnh xe không đổi ──────────────────────────────────────────────────────────────
for t in "đèn đọc" "nhiệt" "xem phim" "cài đặt"; do
  J="$(bridge "--es cmd teach_text --es pkg $(shq "$APP1") --es text $(shq "$t") --es op save")"
  check "B:$t" "teach_text «${t}» ⇒ BLOCK, không lưu" "$( [ "$(printf '%s' "$J" | json_get verdict)" = "BLOCK" ] && [ "$(printf '%s' "$J" | json_get saved)" = "False" ] && echo 1 || echo 0)" "$(printf '%s' "$J" | json_get verdict) $(printf '%s' "$J" | json_get reasons)"
done
J="$(bridge "--es cmd say --es text $(shq "bật đèn đọc")")"
check "R1" "say «bật đèn đọc» vẫn ⇒ Control" "$( [ "$(printf '%s' "$J" | kinds)" = "Control" ] && echo 1 || echo 0)" "$(printf '%s' "$J" | previews)"

# ── 7. Đổi hồ sơ ⇒ bộ tên của hồ sơ kia (rỗng); đổi về ⇒ chạy lại ───────────────────────────────────────────────────
OTHER="$(state_json | python3 "$HERE/voice_e2e_json.py" pick_profile)"
if [ "${KNOWN[1]}" = "1" ]; then
  echo "  ⚠ app 1 Kachi đã hiểu sẵn (không có tên đã dạy) — ca đổi hồ sơ không đo được gì, bỏ"
elif [ -n "$OTHER" ] && [ "$OTHER" != "$ORIG_PROFILE" ]; then
  bridge "--es cmd profile --es name $(shq "$OTHER")" >/dev/null; sleep 1
  J="$(bridge "--es cmd say --es text $(shq "mở ${NAMES[1]}")")"
  check "P1" "hồ sơ «${OTHER}»: «mở ${NAMES[1]}» KHÔNG mở «${L1}»" "$( [ "$(has "$(printf '%s' "$J" | previews)" "$L1")" = "1" ] && echo 0 || echo 1)" "$(printf '%s' "$J" | previews)"
  bridge "--es cmd profile --es name $(shq "$ORIG_PROFILE")" >/dev/null; sleep 1
  J="$(bridge "--es cmd say --es text $(shq "mở ${NAMES[1]}")")"
  check "P2" "về «${ORIG_PROFILE}»: «mở ${NAMES[1]}» mở lại «${L1}»" "$(has "$(printf '%s' "$J" | previews)" "$L1")" "$(printf '%s' "$J" | previews)"
else
  echo "  ⚠ máy ảo chỉ có một hồ sơ — bỏ ca đổi hồ sơ (không tự tạo hồ sơ)"
fi

# ── 8. Gỡ app (user 0) ⇒ tên mồ côi, không mở; cài lại ⇒ dùng lại ───────────────────────────────────────────────────
if [ "${KNOWN[2]}" = "1" ]; then
  echo "  ⚠ app 2 Kachi đã hiểu sẵn — ca gỡ/cài lại không đo tên đã dạy, bỏ"
elif adbs shell pm uninstall --user 0 "$APP2" 2>/dev/null | grep -q Success; then
  UNINSTALLED="$APP2"
  J="$(bridge "--es cmd say --es text $(shq "mở ${NAMES[2]}")")"
  check "U1" "gỡ $APP2: «mở ${NAMES[2]}» không mở «${L2}»" "$( [ "$(has "$(printf '%s' "$J" | previews)" "$L2")" = "1" ] && echo 0 || echo 1)" "$(printf '%s' "$J" | previews)"
  adbs shell cmd package install-existing "$APP2" >/dev/null 2>&1; UNINSTALLED=""
  J="$(bridge "--es cmd say --es text $(shq "mở ${NAMES[2]}")")"
  check "U2" "cài lại $APP2: «mở ${NAMES[2]}» mở lại «${L2}»" "$(has "$(printf '%s' "$J" | previews)" "$L2")" "$(printf '%s' "$J" | previews)"
else
  echo "  ⚠ không gỡ được $APP2 cho user 0 — bỏ ca gỡ/cài lại"
fi

# ── 9. Xoá ⇒ hết tên ────────────────────────────────────────────────────────────────────────────────────────────────
J="$(bridge "--es cmd teach_clear --es pkg $(shq "$APP1")")"
J="$(bridge "--es cmd say --es text $(shq "mở ${NAMES[1]}")")"
[ "${KNOWN[1]}" = "1" ] || check "D1" "teach_clear $APP1 ⇒ «mở ${NAMES[1]}» không còn mở «${L1}»" "$( [ "$(has "$(printf '%s' "$J" | previews)" "$L1")" = "1" ] && echo 0 || echo 1)" "$(printf '%s' "$J" | previews)"

# ── 10. Tên NGẮN 3 chữ cái (quyết định điều phối 2.91 — spec §7 OQ4 · §9 F1) ───────────────────────────────────────
# Luật: MỘT lượt ⇒ CHẶN (SHORT_ONE_TAKE); lượt thứ hai ra ĐÚNG chuỗi ⇒ hết chặn (còn CẢNH BÁO một từ ⇒ cầu lưu như "Vẫn lưu")
# ⇒ "mở <tên>" mở đúng app; va chạm lệnh xe vẫn CHẶN dù lặp lại. TTS ra chuỗi khác 3 chữ cái ⇒ ghi ℹ, không tính PASS/FAIL.
letters() { python3 -c "import sys,unicodedata; print(sum(1 for c in unicodedata.normalize('NFD', sys.argv[1]) if c.isalpha() and not unicodedata.combining(c)))" "$1"; }
bridge "--es cmd teach_clear --es pkg $(shq "$APP2")" >/dev/null   # bỏ «${NAMES[2]:-}» khỏi hotword: nó kéo câu thử về chuỗi cũ (F2)
J="$(bridge "--es cmd teach --es pkg $(shq "$APP2") --es path $(shq "$DST/teach-short-a.wav")")"
printf 'short\tteach-short-a\t%s\n' "$J" >> "$OUT/raw.tsv"
SN="$(printf '%s' "$J" | json_get name)"
echo "  [short-a] nghe «$(printf '%s' "$J" | json_get heard)» ⇒ tên «${SN}» · $(printf '%s' "$J" | json_get verdict) $(printf '%s' "$J" | json_get reasons) · lượt $(printf '%s' "$J" | json_get takes)"
if [ -z "$SN" ] || [ "$SN" = "None" ] || [ "$(letters "$SN")" != "3" ]; then
  echo "  ℹ «mở ${L2}» lượt này không ra tên 3 chữ cái — bỏ phần tên ngắn (không tính PASS/FAIL)"
else
  check "SH1" "MỘT lượt «${SN}» (3 chữ cái) ⇒ CHẶN, chưa lưu" "$( [ "$(printf '%s' "$J" | json_get verdict)" = "BLOCK" ] && [ "$(has "$(printf '%s' "$J" | json_get reasons)" SHORT_ONE_TAKE)" = "1" ] && [ "$(printf '%s' "$J" | json_get saved)" = "False" ] && echo 1 || echo 0)" "$(printf '%s' "$J" | head -c 300)"
  J="$(bridge "--es cmd teach --es pkg $(shq "$APP2") --es path $(shq "$DST/teach-short-b.wav") --es op save")"
  printf 'short\tteach-short-b\t%s\n' "$J" >> "$OUT/raw.tsv"
  SN2="$(printf '%s' "$J" | json_get name)"
  echo "  [short-b] nghe «$(printf '%s' "$J" | json_get heard)» ⇒ tên «${SN2}» · $(printf '%s' "$J" | json_get verdict) $(printf '%s' "$J" | json_get reasons) · lượt $(printf '%s' "$J" | json_get takes) · lưu $(printf '%s' "$J" | json_get saved)"
  if [ "$SN2" = "$SN" ]; then
    check "SH2" "lượt 2 ra ĐÚNG «${SN}» ⇒ lưu được" "$( [ "$(printf '%s' "$J" | json_get saved)" = "True" ] && echo 1 || echo 0)" "$(printf '%s' "$J" | head -c 300)"
    J="$(bridge "--es cmd say --es text $(shq "mở $SN")")"
    check "SH3" "say «mở ${SN}» ⇒ OpenApp «${L2}»" "$( [ "$(printf '%s' "$J" | kinds)" = "OpenApp" ] && has "$(printf '%s' "$J" | previews)" "$L2" || echo 0)" "$(printf '%s' "$J" | previews)"
    J="$(wav_intents "teach-short-t")"
    echo "  ℹ SHT WAV thử 185 wpm «$(printf '%s' "$J" | json_get heard)» ⇒ $(printf '%s' "$J" | previews)"
    printf 'INFO\tSHT\t%s\n' "$J" >> "$OUT/raw.tsv"
    ST="$(state_json)"
    note "R-nf6 bảng gọi app: build $(printf '%s' "$ST" | json_get voice_names.index_build_us) µs · cả appIndex $(printf '%s' "$ST" | json_get voice_names.index_total_us) µs"
  else
    check "SH2" "lượt 2 ra chuỗi KHÁC («${SN2}») ⇒ không lưu tên ngắn một lượt" "$( [ "$(printf '%s' "$J" | json_get saved)" = "False" ] && echo 1 || echo 0)" "$(printf '%s' "$J" | head -c 300)"
  fi
  J="$(bridge "--es cmd say --es text $(shq "bật đèn đọc")")"
  check "SH4" "sau tên ngắn: say «bật đèn đọc» vẫn ⇒ Control" "$( [ "$(printf '%s' "$J" | kinds)" = "Control" ] && echo 1 || echo 0)" "$(printf '%s' "$J" | previews)"
fi
if [ "$HAS_GMAIL" = "1" ]; then
  J="$(bridge "--es cmd teach --es pkg $(shq "$GMAIL") --es path $(shq "$DST/teach-gm-a.wav")")"
  printf 'short\tteach-gm-a\t%s\n' "$J" >> "$OUT/raw.tsv"
  J="$(bridge "--es cmd teach --es pkg $(shq "$GMAIL") --es path $(shq "$DST/teach-gm-b.wav") --es op save")"
  printf 'short\tteach-gm-b\t%s\n' "$J" >> "$OUT/raw.tsv"
  GN="$(printf '%s' "$J" | json_get name)"
  echo "  [gmail] nghe «$(printf '%s' "$J" | json_get heard)» ⇒ tên «${GN}» · $(printf '%s' "$J" | json_get verdict) $(printf '%s' "$J" | json_get reasons) · lượt $(printf '%s' "$J" | json_get takes)"
  if [ "$GN" = "ghe" ]; then
    check "SHG" "«ghe» (= «ghế») ⇒ CHẶN dù đã 2 lượt (tiền tố lệnh ghế), không lưu" "$( [ "$(printf '%s' "$J" | json_get verdict)" = "BLOCK" ] && [ "$(has "$(printf '%s' "$J" | json_get reasons)" COMMAND_PREFIX)" = "1" ] && [ "$(printf '%s' "$J" | json_get saved)" = "False" ] && echo 1 || echo 0)" "$(printf '%s' "$J" | head -c 300)"
  else
    echo "  ℹ «mở Gmail» lượt này ra «${GN}» (không phải «ghe») — bỏ ca va chạm"
  fi
fi

echo
echo "══ KẾT QUẢ: $PASS PASS · $FAIL FAIL — bảng: $LOG · JSON thô: $OUT/raw.tsv"
[ "$FAIL" = "0" ]
