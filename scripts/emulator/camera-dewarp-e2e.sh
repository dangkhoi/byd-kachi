#!/usr/bin/env bash
# ═══ NẮN MÉO CAMERA (GL) — vòng kiểm E2E TRÊN MÁY ẢO, không cần xe ══════════════════════════════════════════════
#
# R8-B · 2.74 · `docs/diagnostics/offcar-2026-09-26/camera-dewarp-gl.md`.
#
# ## Vòng này chứng minh cái gì (và KHÔNG chứng minh cái gì)
# CHỨNG MINH: cả đường vẽ mới chạy **thật** trên một GPU thật — EGL14 dựng được, shader biên dịch được, mười uniform
# vào đúng chỗ, `eglSwapBuffers` ra khung, `getBitmap` chụp lại được, teardown không crash — và phép nắn **làm đúng
# việc của nó**: một đường thẳng của thế giới bị ống kính bẻ cong trở lại **thẳng**, đo bằng số (px).
# KHÔNG chứng minh: `K`/`F` thật của camera BYD. Ảnh vào là ảnh **tổng hợp**, sinh bằng chính mô hình đang kiểm
# (`CameraDewarpTestPattern`), nên nó nói *"cài đặt đúng"* chứ không nói gì về ống kính thật. Tham số chốt bằng một
# khung `5120×960` chụp từ xe (`camera-dewarp-math.md` §3.2 D1). Xem CAM-B1..B4 trong runbook.
#
# ## Vì sao phải có vòng này (CLAUDE.md §14 · §8)
# Máy ảo **không có** `android.hardware.AVMCamera` ⇒ nếu không có producer giả thì hơn 900 dòng GL mới sẽ lên xe mà
# **chưa từng vẽ một pixel**. Lệnh `camera_synth` thay ĐÚNG một mắt của chuỗi (producer); mọi mắt còn lại là đường
# thật, kể cả `Surface`, `SurfaceTexture`, texture OES và cửa ra `TextureView`.
#
# ## Dùng
#   scripts/emulator/camera-dewarp-e2e.sh [--serial emulator-5554] [--skip-build] [--out <dir>] [--keep]
#                                         [--strip-png <tep.png>]
#
# ## 2.75 — thêm XOAY và DỊCH CỬA SỔ vào vòng kiểm
# Vòng 2.74 chỉ đo ở `camera_rot_left = 0`, trong khi **mặc định của cả hai bên gương là ±90** (trái ↺ −90 / phải
# ↻ +90) — tức ca owner thật sự nhìn chưa bao giờ được đo. 2.75 đo cả `L90` và `R90` (phép đo tự chọn trục qua
# `camera_dewarp_check.py --axis auto`), và đo thêm một lượt **có dịch cửa sổ** để chứng minh dịch **không làm cong**.
#
# `--strip-png <tep>` đẩy một khung fisheye **THẬT chụp từ xe** vào `getExternalFilesDir` rồi bơm nó qua đúng đường
# GL ấy (`camera_synth --es name file:<tên>`) — ảnh thật không có chân trời màu nên lượt này **chỉ chụp ảnh** để xem
# bằng mắt, không chấm ĐẠT/KHÔNG ĐẠT.
#
# `--keep` giữ chế độ kiểm thử + prefs sau khi chạy (để soi tay); mặc định DỌN sạch, kể cả khi script chết giữa chừng.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
PKG="com.byd.launcher"
HOME_ACT="$PKG/com.byd.clusternav.launcher.KachiHome"
SERIAL="emulator-5554"
OUT="${TMPDIR:-/tmp}/kachi-camera-dewarp-$(date -u +%Y%m%d-%H%M%S)"
SKIP_BUILD=0
KEEP=0
STRIP_PNG=""
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
GRADLE="${GRADLE_LOCKED:-}"

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2;;
    --out) OUT="$2"; shift 2;;
    --skip-build) SKIP_BUILD=1; shift;;
    --keep) KEEP=1; shift;;
    --strip-png) STRIP_PNG="$2"; shift 2;;
    *) echo "tham số lạ: $1" >&2; exit 2;;
  esac
done

mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true
die() { echo "✗ $*" >&2; exit 1; }
note() { echo "── $*"; }
adbs() { "$ADB" -s "$SERIAL" "$@" </dev/null; }
adbs_stdin() { "$ADB" -s "$SERIAL" "$@"; }

# Bọc một chuỗi cho shell của THIẾT BỊ — cùng hàm với `voice-e2e.sh` (một dấu `'` trong giá trị thoát ra khỏi cặp
# nháy và chạy lệnh tuỳ ý trên máy).
shq() { local s=$1 q="'"; s=${s//$q/$q\\$q$q}; printf '%s' "$q$s$q"; }

# ═══ CHỐT: KHÔNG chạy trên XE THẬT ══════════════════════════════════════════════════════════════════════════════
# Vòng này **ghi prefs camera** và **bật ảnh tổng hợp** thay camera. Trên xe đang lăn bánh đó là một ảnh vẽ sẵn ở
# đúng chỗ đáng lẽ là camera gương — CLAUDE.md §4 (lệnh đổi state phải nêu tường minh nó nhắm cái gì).
case "$SERIAL" in
  emulator-*) ;;
  *) [ "${ALLOW_NON_EMULATOR:-}" = "YES" ] || die "«${SERIAL}» không phải máy ảo — từ chối bật ảnh tổng hợp thay camera.
   Đặt ALLOW_NON_EMULATOR=YES nếu thật sự có chủ ý." ;;
esac

# Trình Python có CẢ numpy và PIL — phép đo cuối cần cả hai; macOS có numpy ở /usr/bin/python3, PIL ở brew.
PY=""
for cand in /usr/bin/python3 python3 /opt/homebrew/bin/python3; do
  command -v "$cand" >/dev/null 2>&1 || [ -x "$cand" ] || continue
  if "$cand" -c "import numpy, PIL" >/dev/null 2>&1; then PY="$cand"; break; fi
done
[ -n "$PY" ] || die "không có trình Python nào có cả numpy và PIL — phép đo độ thẳng cần cả hai"
note "python = $PY"

TEST_MODE_ON=0
cleanup() {
  local rc=$?
  if [ "$TEST_MODE_ON" = "1" ] && [ "$KEEP" = "0" ]; then
    bridge "--es cmd camera_synth --es name off" >/dev/null 2>&1 || true
    for k in camera_render camera_span camera_shape camera_dewarp_amount camera_dewarp_focal \
             camera_dewarp_k camera_dewarp_scale camera_dewarp_cx camera_dewarp_cy \
             camera_gl_texmatrix camera_rot_left camera_signal_enabled \
             camera_dewarp_pan_x camera_dewarp_pan_y; do
      bridge "--es cmd prefs_set --es key $k" >/dev/null 2>&1 || true
    done
    adbs shell am force-stop "$PKG" >/dev/null 2>&1 || true
    adbs shell "run-as $PKG rm -f shared_prefs/kachi_test_bridge.xml" >/dev/null 2>&1 || true
    # Owner đang nhìn máy ảo ⇒ để lại màn HOME của Kachi, không để một màn trống.
    adbs shell am start -n "$HOME_ACT" >/dev/null 2>&1 || true
    echo "── đã dọn: chế độ kiểm thử TẮT, prefs về mặc định, Kachi home ở tiền cảnh"
  fi
  return $rc
}
trap cleanup EXIT

# ── Bắn một lệnh cầu kiểm thử; in JSON thuần ────────────────────────────────────────────────────────────────────
bridge() { adbs shell "am broadcast -a $PKG.TEST -p $PKG $*" 2>&1 | "$PY" "$HERE/voice_e2e_json.py" extract; }

# Doc MOT truong cua loi dap — dung lai `voice_e2e_json.py get` (mot bo phan tich JSON cho ca hai harness; mot ban
# thu hai bang sed/python inline la cho hong im lang dau tien voi chuoi tieng Viet + ngoac long).
# In "True"/"False" cho Boolean (repr cua Python) — cac phep so sanh duoi day theo dung do.
jget() { "$PY" "$HERE/voice_e2e_json.py" get "$1"; }

# ── 0. Thiết bị + bản cài ───────────────────────────────────────────────────────────────────────────────────────
adbs get-state >/dev/null 2>&1 || die "không thấy thiết bị $SERIAL"
if [ "$SKIP_BUILD" = "0" ]; then
  [ -n "$GRADLE" ] || GRADLE="$HERE/../../gradlew"
  note "build :app:assembleVehicleTest"
  if [ -x "$GRADLE" ] && [ "$(basename "$GRADLE")" != "gradlew" ]; then
    "$GRADLE" :app:assembleVehicleTest -q || die "build hỏng"
  else
    ( cd "$REPO" && JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}" ./gradlew :app:assembleVehicleTest -q ) || die "build hỏng"
  fi
fi
APK="$(ls -t "$REPO"/app/build/outputs/apk/vehicleTest/*.apk 2>/dev/null | head -1)"
[ -n "$APK" ] || die "không thấy APK vehicleTest (bỏ --skip-build?)"
note "cài $(basename "$APK")"
adbs install -r "$APK" >/dev/null || die "cài APK hỏng (khác chữ ký? xem memory kachi-signing-ota)"
adbs shell run-as "$PKG" true 2>/dev/null || die "bản cài KHÔNG debuggable ⇒ không bật được chế độ kiểm thử"

# ── 1. Bật chế độ kiểm thử (cùng kỹ thuật `voice-e2e.sh`: dựng lại `TestBridgeWindow.encode` từ boot_id + uptime) ─
enable_test_mode() {
  local boot up until_ms tmp
  boot="$(adbs shell cat /proc/sys/kernel/random/boot_id | tr -d '\r\n')"
  up="$(adbs shell cat /proc/uptime | tr -d '\r' | cut -d' ' -f1)"
  until_ms="$("$PY" -c "import sys;print(int(float(sys.argv[1])*1000)+3600000-120000)" "$up")"
  tmp="$OUT/kachi_test_bridge.xml"
  cat > "$tmp" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="test_bridge_until">$boot:$until_ms</string>
</map>
XML
  # force-stop TRƯỚC khi ghi: bản SharedPreferences trong RAM sẽ thắng nếu ghi dưới chân tiến trình đang chạy.
  adbs shell am force-stop "$PKG"
  adbs shell "run-as $PKG mkdir -p shared_prefs"
  adbs_stdin shell "run-as $PKG sh -c 'cat > shared_prefs/kachi_test_bridge.xml'" < "$tmp" || die "ghi prefs hỏng"
  adbs shell am start -n "$HOME_ACT" >/dev/null
  sleep 4
}
note "bật chế độ kiểm thử"
enable_test_mode
TEST_MODE_ON=1
LEFT="$(bridge "--es cmd state" | jget test_mode_minutes_left)"
[ "${LEFT:-0}" -gt 0 ] 2>/dev/null || die "chế độ kiểm thử vẫn TẮT (còn '$LEFT' phút)"
note "chế độ kiểm thử: còn $LEFT phút"

pset() {
  local json; json="$(bridge "--es cmd prefs_set --es key $1 --es text $(shq "$2")")"
  local back; back="$(printf '%s' "$json" | jget read_back)"
  [ "$(printf '%s' "$json" | jget ok)" = "True" ] || die "prefs_set $1=$2 hỏng: $json"
  echo "   $1 = $back"
}

# Ảnh chụp khung: bắn lệnh, đọc `path`, kéo về, in các trường quan trọng.
grab() {
  local name="$1" arg="${2:-}" json path
  if [ -n "$arg" ]; then json="$(bridge "--es cmd camera_frame --es name $(shq "$arg")")"
  else json="$(bridge "--es cmd camera_frame")"; fi
  path="$(printf '%s' "$json" | jget path)"
  if [ -z "$path" ]; then
    echo "   ✗ $name: $(printf '%s' "$json" | head -c 400)"
    return 1
  fi
  adbs pull "$path" "$OUT/$name.png" >/dev/null 2>&1 || { echo "   ✗ $name: pull hỏng"; return 1; }
  printf '   ✓ %-10s %sx%s content=%s render=%s %s | gl: %s\n' \
    "$name" "$(printf '%s' "$json" | jget width)" "$(printf '%s' "$json" | jget height)" \
    "$(printf '%s' "$json" | jget content)" "$(printf '%s' "$json" | jget render)" \
    "$(printf '%s' "$json" | jget gl_stats)" "$(printf '%s' "$json" | jget gl)"
  printf '%s' "$json" > "$OUT/$name.json"
}

# Dựng lại overlay để prefs mới ăn (bộ uniform bất biến trong một lượt overlay — đúng khuôn mọi pref camera).
retrigger() {
  bridge "--es cmd camera --es name none" >/dev/null
  sleep 1
  bridge "--es cmd camera --es name left" >/dev/null
  sleep "${1:-6}"
}

# ── 2. Bộ prefs: đường GL, trọn dải, KHÔNG xoay (hình học đơn giản nhất cho phép đo), độ nắn 0 ───────────────────
note "đặt prefs"
pset camera_signal_enabled 1
pset camera_render GL
pset camera_span STRIP
pset camera_shape RECT
# Xoay 0: phép đo độ thẳng đọc chân trời NGANG. Xoay ±90 vẫn đúng (bài `:core` đã ghim 16 tổ hợp) nhưng nó làm bài
# đo phải biết trục nào là trục nào — một biến số không cần trong lượt kiểm CÀI ĐẶT.
pset camera_rot_left 0
pset camera_strip_left 1
pset camera_dewarp_amount 0

# ── 3. Bơm ảnh tổng hợp + dựng overlay ──────────────────────────────────────────────────────────────────────────
note "bật ảnh tổng hợp (camera_synth)"
SYNTH="$(bridge "--es cmd camera_synth --es name on")"
[ "$(printf '%s' "$SYNTH" | jget applied)" = "True" ] || die "camera_synth không bật được: $SYNTH"
retrigger 8

note "chụp: độ nắn 0 % (ảnh thô qua đúng đường GL)"
grab a000 || die "không chụp được khung nào ⇒ đường GL chưa vẽ (xem \`adb logcat -s KachiCamera\`)"
note "chụp: khung THÔ qua FBO (--es name raw)"
grab raw raw || echo "   (không sao — phép đo chính không cần khung thô)"

# ── 4. Độ nắn 100 % ─────────────────────────────────────────────────────────────────────────────────────────────
note "đặt độ nắn 100 % rồi dựng lại"
pset camera_dewarp_amount 100
retrigger 8
note "chụp: độ nắn 100 %"
grab a100 || die "không chụp được khung ở độ nắn 100 %"

# ── 5. Hình TRÒN (mong muốn của owner — phải ăn CẢ trên đường GL) ────────────────────────────────────────────────
note "đổi hình khung sang TRÒN rồi dựng lại"
pset camera_shape ROUND
retrigger 8
grab round || echo "   ✗ hình TRÒN không chụp được — đây là một PHÁT HIỆN, ghi vào runbook"
pset camera_shape RECT

# ── 6. Phép đo bằng số ──────────────────────────────────────────────────────────────────────────────────────────
note "đo độ thẳng của chân trời (numpy)"
"$PY" "$HERE/camera_dewarp_check.py" --before "$OUT/a000.png" --after "$OUT/a100.png" --out "$OUT/report.txt" \
  ${KEEP:+} | tee -a "$OUT/summary.txt"
RC=${PIPESTATUS[0]}

# ── 7. XOAY ±90 + DỊCH CỬA SỔ (2.75) ────────────────────────────────────────────────────────────────────────────
# Đây là ca MẶC ĐỊNH trên xe (trái ↺ −90 / phải ↻ +90) mà vòng 2.74 chưa bao giờ đo. Chân trời của ảnh tổng hợp nằm
# NGANG trong ô nguồn ⇒ sau khi xoay ±90 nó thành gần DỌC; `--axis auto` chọn trục theo chính ảnh, nên cùng một phép
# đo dùng được cho cả ba góc mà không phải gõ tay trục nào là trục nào.
RC_ROT=0
measure_rot() {
  local mode="$1" panx="${2:-0}" tag="rot$1"
  [ "$panx" = "0" ] || tag="${tag}-pan${panx}"
  note "── xoay $mode, dịch ngang $panx %"
  pset camera_rot_left "$mode"
  pset camera_dewarp_pan_x "$panx"
  pset camera_dewarp_amount 0
  retrigger 7
  grab "${tag}-a000" || { echo "   ✗ $tag: không chụp được khung amount=0"; RC_ROT=1; return 0; }
  pset camera_dewarp_amount 100
  retrigger 7
  grab "${tag}-a100" || { echo "   ✗ $tag: không chụp được khung amount=100"; RC_ROT=1; return 0; }
  if "$PY" "$HERE/camera_dewarp_check.py" --before "$OUT/${tag}-a000.png" --after "$OUT/${tag}-a100.png" \
       --label "$tag" --out "$OUT/report-$tag.txt" | tee -a "$OUT/summary.txt"; then :; else RC_ROT=1; fi
}

pset camera_dewarp_pan_y 0
measure_rot L90
measure_rot R90
# Dịch cửa sổ PHẢI giữ ảnh thẳng: cùng ngưỡng, cùng phép đo, chỉ thêm `camera_dewarp_pan_x`. Nếu ca này KHÔNG ĐẠT
# trong khi ca trên ĐẠT thì phép dịch đang làm cong — tức nó đã bị nối nhầm vào `uCenter` (xem `CameraGlUniforms`).
measure_rot L90 -20
pset camera_dewarp_pan_x 0
pset camera_rot_left 0

# ── 8. KHUNG THẬT từ xe (tuỳ chọn, chỉ để XEM — ảnh thật không có chân trời màu để chấm điểm) ────────────────────
if [ -n "$STRIP_PNG" ]; then
  if [ ! -f "$STRIP_PNG" ]; then
    echo "   ✗ --strip-png «${STRIP_PNG}» không có thật — bỏ qua mục 8"
  else
    note "đẩy khung THẬT $(basename "$STRIP_PNG") vào getExternalFilesDir rồi bơm qua đúng đường GL"
    REMOTE_DIR="/sdcard/Android/data/$PKG/files"
    REMOTE_NAME="e2e-car-frame.png"
    adbs shell mkdir -p "$REMOTE_DIR" >/dev/null 2>&1 || true
    if adbs push "$STRIP_PNG" "$REMOTE_DIR/$REMOTE_NAME" >/dev/null 2>&1; then
      SYNTH_F="$(bridge "--es cmd camera_synth --es name $(shq "file:$REMOTE_NAME")")"
      echo "   camera_synth file ⇒ $(printf '%s' "$SYNTH_F" | jget file) applied=$(printf '%s' "$SYNTH_F" | jget applied)"
      # Bộ số owner DUYỆT trên xe 27/09: F 55 % · K 100 % · S 130 % · độ nắn 100 %.
      pset camera_dewarp_focal 55
      pset camera_dewarp_k 100
      pset camera_dewarp_scale 130
      pset camera_dewarp_amount 100
      for rot_mode in 0 L90; do
        pset camera_rot_left "$rot_mode"
        retrigger 7
        grab "car-rot$rot_mode" || echo "   ✗ khung thật rot$rot_mode: không chụp được"
      done
      # Cùng bộ số + dịch ra sau 20 % — đúng thứ owner xin (*"dịch 1 tý ra sau"*).
      pset camera_dewarp_pan_x -20
      pset camera_rot_left L90
      retrigger 7
      grab "car-rotL90-pan-20" || echo "   ✗ khung thật rotL90 + dịch: không chụp được"
      pset camera_dewarp_pan_x 0
    else
      echo "   ✗ push hỏng — bỏ qua mục 8"
    fi
  fi
fi

echo
note "bằng chứng: $OUT"
ls -1 "$OUT"/*.png 2>/dev/null | sed 's|^|   |'
[ "$RC" = "0" ] || die "phép đo độ thẳng (rot 0) KHÔNG đạt — xem $OUT/report.txt"
[ "$RC_ROT" = "0" ] || die "phép đo độ thẳng khi XOAY/DỊCH KHÔNG đạt — xem $OUT/report-rot*.txt"
note "ĐẠT"
