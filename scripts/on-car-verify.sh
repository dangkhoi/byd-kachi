#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# on-car-verify.sh — VERIFY ClusterNav release TRÊN XE THẬT
#
# Kiểm các luồng KHÔNG unit-test off-xe được (cần dadb + cụm thật): chiếu app lên
# cụm (R1/R2/R3), teardown-guard chống mồ côi khi chiếu CP/AA + đổi app + resize,
# stop trả đồng hồ. Cốt lõi TỰ ĐỘNG = dò "cửa sổ mồ côi" (WM thấy / AM không) sau
# mỗi thao tác — đúng bug P0 phải reboot xe. Phần chạm app UI thì HƯỚNG DẪN (cast
# kích trong app, adb không tap tin cậy).
#
# DÙNG:
#   ./on-car-verify.sh [adb-serial|ip:port]
#   vd:  ./on-car-verify.sh 10.x.x.x:5555      (IP xe — KHÔNG hardcode, repo public)
#        ADB=/path/to/adb ./on-car-verify.sh   (dùng thiết bị adb mặc định)
#
# YÊU CẦU: đã bật USB debugging + adb tcp 5555 trên xe; cùng mạng; bản release đã cài.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

ADB="${ADB:-adb}"
DEV="${1:-${ADB_TARGET:-}}"
PKG="com.byd.clusternav"
OUT="${OUT_DIR:-./on-car-verify-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT"

adbx() { if [ -n "$DEV" ]; then "$ADB" -s "$DEV" "$@"; else "$ADB" "$@"; fi; }
c_pass=0; c_fail=0; c_warn=0
ok()   { echo "  ✅ $*"; c_pass=$((c_pass+1)); }
bad()  { echo "  ❌ $*"; c_fail=$((c_fail+1)); }
warn() { echo "  ⚠  $*"; c_warn=$((c_warn+1)); }
hr()   { echo "──────────────────────────────────────────────────────────"; }
pause(){ echo; read -r -p "⏸  $* → xong thì bấm Enter…" _; }

# ── VD cụm (display có tên fission/xdja) ──
find_vd() {
  adbx shell "dumpsys display" 2>/dev/null | awk '
    /Display Devices|DisplayDeviceInfo|mDisplayId=/ { line=$0 }
    tolower($0) ~ /fission|xdja/ {
      if (match($0,/displayId ([0-9]+)/)) { print substr($0,RSTART+10,RLENGTH-10); exit }
      if (match($0,/mDisplayId=([0-9]+)/)) { print substr($0,RSTART+11,RLENGTH-11); exit }
    }' | head -1
}

# ── ids stack app trên VD theo AM (am stack list) ──
am_ids_on_vd() {
  local vd="$1"
  adbx shell "am stack list" 2>/dev/null \
    | grep -E "^(Stack|RootTask) id=" | grep "displayId=$vd" \
    | sed -E 's/^(Stack|RootTask) id=([0-9]+).*/\2/'
}

# ── ids stack trên VD theo WM (dumpsys window displays) — CHỈ stack có task (bỏ stack rỗng, như WmParse) ──
wm_ids_on_vd() {
  local vd="$1"
  adbx shell "dumpsys window displays" 2>/dev/null | awk -v vd="$vd" '
    function flush(){ if (sid!="" && hastask) print sid; sid=""; hastask=0 }
    /Display: mDisplayId=/ { flush(); split($0,a,"mDisplayId="); cur=((a[2]+0)==vd) }
    cur && /mStackId=/ { flush(); split($0,b,"mStackId="); sid=(b[2]+0) }
    cur && sid!="" && (/taskId=/ || /ActivityRecord\{/) { hastask=1 }
    END { flush() }'
}

# ── MỌI stack id theo AM (bất kỳ display) — để so mồ côi ──
am_all_ids() {
  adbx shell "am stack list" 2>/dev/null \
    | grep -E "^(Stack|RootTask) id=" | sed -E 's/^(Stack|RootTask) id=([0-9]+).*/\2/'
}

# ── CỐT LÕI: dò mồ côi = stack WM trên VD mà stackId KHÔNG có trong AM (bất kỳ đâu). Rỗng = sạch ──
# Khớp WmParse.orphanStacksOn: so với TẬP am toàn cục, KHÔNG phải am-trên-vd.
orphans_on_vd() {
  local vd="$1"
  local amall wm; amall="$(am_all_ids | sort -u)"; wm="$(wm_ids_on_vd "$vd" | sort -u)"
  # AM rỗng hoàn toàn = shell hụt → KHÔNG kết luận mồ côi (chống false-positive, đúng WmParse)
  [ -z "$amall" ] && { echo ""; return; }
  comm -23 <(echo "$wm") <(echo "$amall")   # trong WM-trên-VD mà không trong AM-toàn-cục
}

# ── kiểm + báo mồ côi cho 1 mốc ──
check_orphan() {
  local vd="$1" label="$2"
  local orph; orph="$(orphans_on_vd "$vd" | tr '\n' ' ' | xargs || true)"
  if [ -z "$orph" ]; then ok "[$label] cụm SẠCH — không cửa sổ mồ côi"; else
    bad "[$label] CÓ MỒ CÔI trên VD (stackId: $orph) — WM thấy, AM không → đúng bug P0 phải reboot!"
  fi
}

snapshot() {
  local label="$1" f="$OUT/snap-$label.txt"
  { echo "=== $label @ $(date +%H:%M:%S) ==="
    echo "--- focus ---"; adbx shell "dumpsys window | grep -E 'mCurrentFocus='"
    echo "--- am stack list ---"; adbx shell "am stack list"
    echo "--- window displays (stacks) ---"; adbx shell "dumpsys window displays" | grep -E "mDisplayId=|mStackId=|ActivityRecord\{"
  } > "$f" 2>&1
}

# ═══════════════════════════════════════════════════════════════════════════
echo "ClusterNav — VERIFY TRÊN XE"; hr
[ -n "$DEV" ] && adbx connect "$DEV" >/dev/null 2>&1 || true

# ── 0. PREFLIGHT ──
echo "[0] Preflight"
if ! adbx shell "echo ok" >/dev/null 2>&1; then bad "KHÔNG nối được xe (adb). Kiểm IP/mạng/USB-debug."; echo; echo "Tổng: PASS=$c_pass FAIL=$c_fail"; exit 1; fi
ok "adb nối được"
VER="$(adbx shell "dumpsys package $PKG | grep versionName" | head -1 | tr -d '\r ' )"
[ -n "$VER" ] && ok "app đã cài ($VER)" || bad "app $PKG CHƯA cài"
VD="$(find_vd)"; [ -n "$VD" ] && [ "$VD" -ge 1 ] 2>/dev/null && ok "VD cụm = display $VD" || { bad "không dò thấy VD cụm (fission/xdja)"; VD=""; }

# ── 1. ASSERT CLEAN-RELEASE (Mức-1: không còn máy dò/thu thập/autotest) ──
echo; echo "[1] Clean-release assert (bản release phải sạch research/privacy)"
ACC="$(adbx shell "settings get secure enabled_accessibility_services" | tr -d '\r')"
echo "$ACC" | grep -qi "navprobe" && bad "navprobe accessibility CÒN được bật (privacy!) — bản này KHÔNG sạch" || ok "navprobe accessibility KHÔNG bật"
adbx shell "cmd package resolve-activity --brief -n $PKG/.modules.navprobe.NavProbeActivity" 2>&1 | grep -qiE "no activity|not exported|Exception|error" \
  && ok "NavProbeActivity đã gỡ khỏi APK" || warn "NavProbeActivity có vẻ CÒN (kiểm lại bản cài đúng release chưa)"
adbx shell "dumpsys package $PKG | grep -i WRITE_EXTERNAL_STORAGE" | grep -qi write \
  && warn "còn khai WRITE_EXTERNAL_STORAGE" || ok "không còn WRITE_EXTERNAL_STORAGE"
# cờ freeform (thông tin — quyết định §6b)
echo "  ℹ freeform=$(adbx shell 'settings get global enable_freeform_support' | tr -d '\r') · force_resizable=$(adbx shell 'settings get global force_resizable_activities' | tr -d '\r')"

[ -z "$VD" ] && { hr; echo "Dừng: không có VD cụm để test chiếu."; echo "Tổng: PASS=$c_pass FAIL=$c_fail WARN=$c_warn"; exit 1; }

# ── 2. BASELINE ──
echo; echo "[2] Baseline (chưa chiếu)"
snapshot "00-baseline"; check_orphan "$VD" "baseline"

# ── 3. CHIẾU app THƯỜNG (Vietmap/GMaps) — R1/R2/R3 ──
echo; hr; echo "[3] Chiếu app thường lên cụm (verify R1/R2/R3 + không mồ côi)"
pause "Mở ClusterNav → tick 1 app nav (Vietmap/GMaps) → bấm CHIẾU LÊN CỤM. Đợi app hiện trên cụm"
snapshot "10-cast-normal"
if am_ids_on_vd "$VD" | grep -q .; then ok "có app bám VD cụm (chiếu thành công)"; else warn "chưa thấy app trên VD — chiếu hụt? xem snap 10"; fi
check_orphan "$VD" "sau chiếu app thường"

# ── 4. CHIẾU CP/AA — ĐÚNG BUG P0 ──
echo; hr; echo "[4] Chiếu CarPlay/Android Auto (⚠ đúng luồng gây reboot trước đây)"
echo "    LƯU Ý: chỉ chạy CP/AA BÌNH THƯỜNG trên cụm; teardown-guard phải bê sink khỏi VD khi đổi/tắt."
pause "Cắm phone → CP/AA → trong ClusterNav bấm CHIẾU CP/AA lên cụm (hoặc để app tự). Đợi CP/AA hiện cụm"
snapshot "20-cast-cpaa"; check_orphan "$VD" "sau chiếu CP/AA"
pause "Giờ ĐỔI sang app khác (Vietmap/GMaps) — teardown-guard phải bê CP/AA về màn giữa (giữ phiên)"
snapshot "21-switch-from-cpaa"; check_orphan "$VD" "sau đổi từ CP/AA (điểm mồ côi cũ!)"

# ── 5. STRESS: đổi app + resize LIÊN TỤC (watch-mode tự dò mồ côi) ──
echo; hr; echo "[5] STRESS — watch-mode: mình DÒ MỒ CÔI liên tục trong lúc bạn thao tác"
echo "    Trong ${STRESS_SEC:-60}s tới: ĐỔI QUA LẠI các app + CHỈNH SIZE liên tục (nút mũi tên) càng nhanh càng tốt."
pause "Sẵn sàng bắt đầu stress"
STRESS_SEC="${STRESS_SEC:-60}"; t_end=$(( $(date +%s) + STRESS_SEC )); worst=""; polls=0
while [ "$(date +%s)" -lt "$t_end" ]; do
  o="$(orphans_on_vd "$VD" | tr '\n' ' ' | xargs || true)"
  polls=$((polls+1))
  [ -n "$o" ] && worst="$o"
  printf "\r  ⏱ stress %ds còn lại · poll #%d · orphan: %s   " "$(( t_end - $(date +%s) ))" "$polls" "${o:-none}"
  sleep 2
done
echo
snapshot "30-after-stress"
if [ -z "$worst" ]; then ok "STRESS: KHÔNG mồ côi nào xuất hiện trong $polls lần dò (teardown-guard giữ được)"; else
  bad "STRESS: XUẤT HIỆN mồ côi (stackId: $worst) — teardown-guard KHÔNG chặn hết. Xem snap 30 + cast-log."; fi

# ── 6. STOP — trả đồng hồ ──
echo; hr; echo "[6] Tắt chiếu — trả đồng hồ gốc"
pause "Trong ClusterNav bấm TẮT — TRẢ ĐỒNG HỒ. Đợi đồng hồ gốc về"
snapshot "40-after-stop"
if am_ids_on_vd "$VD" | grep -q .; then warn "VD còn app sau khi tắt — kiểm snap 40"; else ok "VD trả về sạch (không app)"; fi
check_orphan "$VD" "sau khi tắt"

# ── 7. KÉO cast-log (castLogger RT1.6) để phân tích ──
echo; hr; echo "[7] Kéo cast-log + snapshot"
adbx pull "/sdcard/Android/data/$PKG/files/castlog" "$OUT/castlog" >/dev/null 2>&1 && ok "đã kéo cast-log → $OUT/castlog" || warn "không có cast-log (chưa chiếu lần nào?)"

# ── TỔNG KẾT ──
echo; hr; echo "TỔNG KẾT: ✅ PASS=$c_pass · ❌ FAIL=$c_fail · ⚠ WARN=$c_warn"
echo "Snapshot + log: $OUT/"
[ "$c_fail" -eq 0 ] && { echo "→ KHÔNG mồ côi ở mọi mốc. Sẵn sàng merge main + ship (nếu FAIL=0)."; exit 0; } \
                    || { echo "→ CÓ FAIL — KHÔNG merge. Đọc snapshot/cast-log tại $OUT/ để phân tích."; exit 1; }
