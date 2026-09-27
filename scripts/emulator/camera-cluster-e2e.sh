#!/usr/bin/env bash
# ═══ HÌNH "THEO CỤM" — vòng kiểm E2E TRÊN MÁY ẢO với một display cụm GIẢ 1920×720 ══════════════════════════════════
#
# R4 · 2.76 · `docs/diagnostics/offcar-2026-09-27/camera-cluster-band.md` · làn L2.
#
# ## Vòng này chứng minh cái gì
# CHỨNG MINH: với `camera_shape=CLUSTER` + `camera_on_cluster=1`, cửa sổ camera được **dựng trên display cụm** (không
# phải màn chính) và **nằm trọn trong dải giữa** đã đo ở xe — kiểm bằng SỐ đọc từ `dumpsys window` (khung cửa sổ trên
# display 1) so với dải `[140,136)-(1780,560)`, cho CẢ hai bên xi-nhan. Ảnh PNG (cửa sổ soi của overlay display, phóng
# ×2 về 1920×720, kẻ dải + vùng hệ thống đã đo) chỉ là bằng chứng bằng mắt.
# KHÔNG chứng minh: hệ thống của xe có thật sự che đúng những hàng đó — đó là 🚗 (một dòng trong runbook 2.76).
#
# ## Vì sao dựng được display cụm ở máy ảo mà không cần xe
# `settings put global overlay_display_devices 1920x720/320` tạo một display **OVERLAY** có `FLAG_PRESENTATION`
# ([ĐO] AOSP 10 `OverlayDisplayAdapter.java:352`), và `DisplayManager.getDisplays(DISPLAY_CATEGORY_PRESENTATION)` gom
# cả TYPE_OVERLAY (`DisplayManager.java:290-295`) ⇒ `CameraOverlayView.clusterCtx` chọn nó **y hệt** cách nó chọn VD
# `fission_bg_xdjaVirtualSurface` trên xe (cùng cờ, cùng cỡ 1920×720). Display này tự hiện thành một cửa sổ soi
# 960×360 ở góc trên-trái màn chính ⇒ `screencap` màn chính rồi cắt là có ảnh.
#
# ## Dùng
#   scripts/emulator/camera-cluster-e2e.sh [--serial emulator-5554] [--skip-build] [--out <dir>] [--keep]
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
PKG="com.byd.launcher"
HOME_ACT="$PKG/com.byd.clusternav.launcher.KachiHome"
SERIAL="emulator-5554"
OUT="${TMPDIR:-/tmp}/kachi-camera-cluster-$(date -u +%Y%m%d-%H%M%S)"
SKIP_BUILD=0
KEEP=0
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
GRADLE="${GRADLE_LOCKED:-}"
# Dải Seal DL3 — cùng số với `ClusterBandSpec.SEAL_DL3` (:core). Đổi ở đó thì đổi ở đây (bài canh `:core` ghim số).
BAND_L=140; BAND_T=136; BAND_R=1780; BAND_B=560
# Mép NGOÀI cong bên trái (2.77) = `ClusterBandSpec.SEAL_DL3.leftEdge`: 9 mẫu chia đều từ BAND_T tới BAND_B.
# Cửa sổ ĐẦU TRÁI được phép trượt ra tới mẫu nhỏ nhất (mặt nạ cắt phần thừa) ⇒ chấm điểm theo mẫu đó, không theo BAND_L.
BAND_EL="42,18,19,31,41,50,61,74,89"
# Mép NGOÀI cong bên PHẢI (2.78) = `ClusterBandSpec.SEAL_DL3.rightEdge`, cùng 9 hàng. Cửa sổ ĐẦU PHẢI có mép phải ở
# mẫu LỚN NHẤT được phép (hướng "ra ngoài" bên phải là x lớn hơn) ⇒ chấm điểm theo mẫu đó, không theo BAND_R.
BAND_ER="1833,1871,1876,1872,1866,1856,1843,1823,1800"
MIRROR_W=960; MIRROR_H=360   # cửa sổ soi của overlay display trên màn chính (Android 10: nửa cỡ)

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2;;
    --out) OUT="$2"; shift 2;;
    --skip-build) SKIP_BUILD=1; shift;;
    --keep) KEEP=1; shift;;
    *) echo "tham số lạ: $1" >&2; exit 2;;
  esac
done

mkdir -p "$OUT"; chmod 700 "$OUT" 2>/dev/null || true
die() { echo "✗ $*" >&2; exit 1; }
note() { echo "── $*"; }
adbs() { "$ADB" -s "$SERIAL" "$@" </dev/null; }
adbs_stdin() { "$ADB" -s "$SERIAL" "$@"; }
shq() { local s=$1 q="'"; s=${s//$q/$q\\$q$q}; printf '%s' "$q$s$q"; }

# ═══ CHỐT: KHÔNG chạy trên XE THẬT — vòng này ghi prefs camera, bật ảnh tổng hợp và TẠO một display giả ═════════════
case "$SERIAL" in
  emulator-*) ;;
  *) [ "${ALLOW_NON_EMULATOR:-}" = "YES" ] || die "«$SERIAL» không phải máy ảo — từ chối tạo overlay display + ảnh tổng hợp." ;;
esac

PY=""
for cand in python3 /opt/homebrew/bin/python3 /usr/bin/python3; do
  command -v "$cand" >/dev/null 2>&1 || continue
  if "$cand" -c "import PIL" >/dev/null 2>&1; then PY="$cand"; break; fi
done
[ -n "$PY" ] || die "cần một python3 có Pillow (vẽ dải lên ảnh)"

TEST_MODE_ON=0
OVERLAY_ON=0
cleanup() {
  local rc=$?
  if [ "$KEEP" = "0" ]; then
    if [ "$TEST_MODE_ON" = "1" ]; then
      bridge "--es cmd camera --es name none" >/dev/null 2>&1 || true
      bridge "--es cmd camera_synth --es name off" >/dev/null 2>&1 || true
      for k in camera_render camera_span camera_shape camera_on_cluster camera_dewarp_amount camera_rot_left \
               camera_rot_right camera_pos_left camera_pos_right camera_signal_enabled; do
        bridge "--es cmd prefs_set --es key $k" >/dev/null 2>&1 || true
      done
      adbs shell am force-stop "$PKG" >/dev/null 2>&1 || true
      adbs shell "run-as $PKG rm -f shared_prefs/kachi_test_bridge.xml" >/dev/null 2>&1 || true
    fi
    # Display giả là state NGOÀI tiến trình (sống qua force-stop) ⇒ luôn trả về, kể cả khi script chết giữa chừng.
    [ "$OVERLAY_ON" = "1" ] && adbs shell settings put global overlay_display_devices none >/dev/null 2>&1 || true
    adbs shell am start -n "$HOME_ACT" >/dev/null 2>&1 || true
    echo "── đã dọn: chế độ kiểm thử TẮT, prefs về mặc định, overlay display gỡ"
  fi
  return $rc
}
trap cleanup EXIT

bridge() { adbs shell "am broadcast -a $PKG.TEST -p $PKG $*" 2>&1 | "$PY" "$HERE/voice_e2e_json.py" extract; }
jget() { "$PY" "$HERE/voice_e2e_json.py" get "$1"; }

# ── 0. Thiết bị + bản cài ───────────────────────────────────────────────────────────────────────────────────────
adbs get-state >/dev/null 2>&1 || die "không thấy thiết bị $SERIAL"
if [ "$SKIP_BUILD" = "0" ]; then
  [ -n "$GRADLE" ] || GRADLE="$REPO/gradlew"
  note "build :app:assembleVehicleTest"
  if [ "$(basename "$GRADLE")" != "gradlew" ]; then "$GRADLE" :app:assembleVehicleTest -q || die "build hỏng"
  else ( cd "$REPO" && JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}" ./gradlew :app:assembleVehicleTest -q ) || die "build hỏng"; fi
fi
APK="$(ls -t "$REPO"/app/build/outputs/apk/vehicleTest/*.apk 2>/dev/null | head -1)"
[ -n "$APK" ] || die "không thấy APK vehicleTest (bỏ --skip-build?)"
note "cài $(basename "$APK")"
adbs install -r "$APK" >/dev/null || die "cài APK hỏng (khác chữ ký? xem memory kachi-signing-ota)"
adbs shell run-as "$PKG" true 2>/dev/null || die "bản cài KHÔNG debuggable ⇒ không bật được chế độ kiểm thử"

# ── 1. Display cụm giả 1920×720 ─────────────────────────────────────────────────────────────────────────────────
note "tạo overlay display 1920x720/320"
adbs shell settings put global overlay_display_devices 1920x720/320
OVERLAY_ON=1
sleep 3
adbs shell dumpsys display | grep -q '"Overlay #1"' || die "overlay display không xuất hiện (dumpsys display)"
CLUSTER_ID="$(adbs shell dumpsys display | tr -d '\r' | "$PY" -c '
import re,sys; t=sys.stdin.read()
m=re.search(r"mDisplayId=(\d+)\s*\n(?:.*\n){0,6}?.*Overlay #1", t); print(m.group(1) if m else "")')"
[ -n "$CLUSTER_ID" ] || die "không đọc được displayId của overlay display"
note "overlay display = displayId $CLUSTER_ID"

# ── 2. Chế độ kiểm thử (cùng kỹ thuật `camera-dewarp-e2e.sh`) ───────────────────────────────────────────────────
enable_test_mode() {
  local boot up until_ms tmp
  boot="$(adbs shell cat /proc/sys/kernel/random/boot_id | tr -d '\r\n')"
  up="$(adbs shell cat /proc/uptime | tr -d '\r' | cut -d' ' -f1)"
  until_ms="$("$PY" -c "import sys;print(int(float(sys.argv[1])*1000)+3600000-120000)" "$up")"
  tmp="$OUT/kachi_test_bridge.xml"
  printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" "<map>" \
    "    <string name=\"test_bridge_until\">$boot:$until_ms</string>" "</map>" > "$tmp"
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

pset() {
  local json; json="$(bridge "--es cmd prefs_set --es key $1 --es text $(shq "$2")")"
  [ "$(printf '%s' "$json" | jget ok)" = "True" ] || die "prefs_set $1=$2 hỏng: $json"
  echo "   $1 = $(printf '%s' "$json" | jget read_back)"
}

# ── 3. Prefs: theo cụm + chiếu cụm + GL (để có ảnh tổng hợp) + đứng (rot 0, R2 L1) ─────────────────────────────
note "đặt prefs"
pset camera_signal_enabled 1
pset camera_on_cluster 1
pset camera_shape CLUSTER
pset camera_render GL
pset camera_span STRIP
pset camera_rot_left 0
pset camera_rot_right 0
pset camera_pos_left TL
pset camera_pos_right TR
pset camera_dewarp_amount 0
SYNTH="$(bridge "--es cmd camera_synth --es name on")"
[ "$(printf '%s' "$SYNTH" | jget applied)" = "True" ] || die "camera_synth không bật được: $SYNTH"

# ── 4. Một bên: ép xi-nhan, đọc khung cửa sổ trên display cụm, chụp + kẻ dải, chấm bằng số ────────────────────
RC=0
one_side() {
  local side="$1" tag="$2"
  note "xi-nhan $side"
  bridge "--es cmd camera --es name none" >/dev/null; sleep 1
  bridge "--es cmd camera --es name $side" >/dev/null; sleep 6
  adbs shell dumpsys window windows | tr -d '\r' > "$OUT/windows-$tag.txt"
  adbs exec-out screencap -p > "$OUT/screen-$tag.png"
  adbs logcat -d -s KachiCamera | grep "overlay show" | tail -1 > "$OUT/overlay-show-$tag.txt" || true
  cat "$OUT/overlay-show-$tag.txt" | sed 's/^/   /'
  "$PY" - "$OUT" "$tag" "$CLUSTER_ID" "$BAND_L" "$BAND_T" "$BAND_R" "$BAND_B" "$MIRROR_W" "$MIRROR_H" "$BAND_EL" "$BAND_ER" <<'PYEOF' || RC=1
import re, sys
from PIL import Image, ImageDraw
out, tag, did, L, T, R, B, MW, MH = sys.argv[1], sys.argv[2], int(sys.argv[3]), *map(int, sys.argv[4:10])
# Cửa sổ chỉ mang bảng của BÊN nó đứng — y như `CameraClusterBand.place` (Placement.leftEdge/rightEdge).
edgeL = [int(v) for v in sys.argv[10].split(",")] if tag == "left" else []
edgeR = [int(v) for v in sys.argv[11].split(",")] if tag == "right" else []
def edge_at(edge, y, flat):
    if not edge: return flat
    t = max(0.0, min(1.0, (y - T) / float(B - T))) * (len(edge) - 1)
    i = min(int(t), len(edge) - 2)
    return round(edge[i] + (t - i) * (edge[i + 1] - edge[i]))
t = open(f"{out}/windows-{tag}.txt", encoding="utf-8", errors="replace").read()
# Cửa sổ overlay camera của Kachi trên display cụm: block "Window #n Window{... com.byd.launcher}" có mDisplayId=<did> + ty=APPLICATION_OVERLAY.
blocks = re.split(r"\n(?=  Window #\d+ )", t)
frame = None
for b in blocks:
    if "com.byd.launcher" not in b or f"mDisplayId={did} " not in b or "APPLICATION_OVERLAY" not in b: continue
    m = re.search(r"mFrame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b) or re.search(r"Frames: containing=.*?\n.*?frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    if m: frame = tuple(map(int, m.groups())); break
if frame is None:
    print(f"   ✗ {tag}: không thấy cửa sổ APPLICATION_OVERLAY của {'Kachi'} trên display {did}"); sys.exit(1)
x0, y0, x1, y1 = frame
outerL = min(edgeL) if edgeL else L
outerR = max(edgeR) if edgeR else R
inside = outerL <= x0 and T <= y0 and x1 <= outerR and y1 <= B
print(f"   khung cửa sổ trên display {did}: [{x0},{y0}]-[{x1},{y1}] ({x1-x0}x{y1-y0}) · dải [{L},{T}]-[{R},{B}]"
      f" · mép ngoài cho phép {outerL}…{outerR} ⇒ {'TRONG DẢI' if inside else 'RA NGOÀI'}")
# Ảnh: cắt cửa sổ soi (góc trên-trái màn chính), phóng về 1920×720, kẻ dải xanh + vùng hệ thống đo được (đỏ) + khung (vàng).
im = Image.open(f"{out}/screen-{tag}.png").convert("RGB").crop((0, 0, MW, MH)).resize((1920, 720), Image.NEAREST)
d = ImageDraw.Draw(im)
d.rectangle([0, 0, 1919, 130], outline=(220, 40, 40), width=3)      # thanh trên hệ thống [ĐO ≈ 0..130–140]
d.rectangle([0, 567, 1919, 719], outline=(220, 40, 40), width=3)    # thanh dưới hệ thống [ĐO chữ từ ≈ 567–578]
d.rectangle([1798, 165, 1919, 330], outline=(220, 40, 40), width=2) # cột icon phải (biển 30/ADAS) [ĐO]
d.rectangle([L, T, R - 1, B - 1], outline=(40, 200, 80), width=3)   # dải vẽ được (tường thẳng 2.76)
for e, flat in ((edgeL, L), (edgeR, R)):                            # mép ngoài cong đo từ ảnh cụm (2.77 + 2.78)
    if e: d.line([(edge_at(e, y, flat), y) for y in range(T, B + 1, 4)], fill=(60, 220, 255), width=3)
d.rectangle([x0, y0, x1 - 1, y1 - 1], outline=(250, 210, 40), width=3)  # cửa sổ camera (từ dumpsys)
d.text((L + 8, T + 6), f"dai {L},{T}-{R},{B}  cua so {x0},{y0}-{x1},{y1}  {'OK' if inside else 'FAIL'}", fill=(0, 0, 0))
im.save(f"{out}/cluster-{tag}.png")
sys.exit(0 if inside else 1)
PYEOF
}
one_side left  left
one_side right right
# Thoái trên màn chính: cùng hình CLUSTER nhưng KHÔNG chiếu cụm ⇒ cửa sổ phải nằm trên display 0 (chữ nhật 2.73).
note "thoái trên màn chính (camera_on_cluster 0)"
pset camera_on_cluster 0
bridge "--es cmd camera --es name none" >/dev/null; sleep 1
bridge "--es cmd camera --es name left" >/dev/null; sleep 6
adbs shell dumpsys window windows | tr -d '\r' > "$OUT/windows-main.txt"
adbs logcat -d -s KachiCamera | grep "overlay show" | tail -1 | sed 's/^/   /' || true
if grep -q "overlay show corner=TL side=LEFT cluster=false rot=0 hình=RECT" <(adbs logcat -d -s KachiCamera | grep "overlay show" | tail -1); then
  echo "   ✓ màn chính: hình thật = RECT (thoái đúng)"
else
  echo "   ✗ màn chính: dòng log không nói hình=RECT"; RC=1
fi
adbs exec-out screencap -p > "$OUT/screen-main.png"

echo
note "bằng chứng: $OUT"
ls -1 "$OUT"/*.png 2>/dev/null | sed 's|^|   |'
[ "$RC" = "0" ] || die "cửa sổ RA NGOÀI dải hoặc thoái sai — xem $OUT"
note "ĐẠT"
