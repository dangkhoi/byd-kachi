#!/usr/bin/env bash
# 10-baseline.sh — ẢNH CHỤP TRẠNG THÁI GỐC trước khi chạm bất cứ thứ gì.
#
# Mọi lệnh ở đây chỉ ĐỌC, trừ ĐÚNG MỘT bước có cổng xác nhận: mở màn Kachi (đổi app tiền cảnh).
# Không có baseline thì sau buổi test không ai chứng minh được thứ gì đã đổi — đó là bài học của
# CLAUDE.md §5 (state đổi ra ngoài sống dai hơn tiến trình).
#
# DÙNG:  scripts/vehicle/kachi/10-baseline.sh [<ip-xe>:5555]
set -uo pipefail
. "$(dirname "$0")/_common.sh"

OUT="$(k_out)"; TARGET="$(k_target "${1:-}")" || exit 4; export KACHI_TARGET="$TARGET"
echo "carlog: $OUT · xe: $TARGET"; k_hr

# ── A. Khung hệ thống ───────────────────────────────────────────────────────────────────────
echo "[A] Khung hệ thống (chỉ đọc)"
k_cap "10-am-stack-list.txt"        "am stack list"
k_cap "10-window-windows.txt"       "dumpsys window windows"
k_cap "10-window-focus.txt"         "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
k_cap "10-activity-activities.txt"  "dumpsys activity activities"
k_cap "10-activity-top.txt"         "dumpsys activity top | head -80"

# ── B. Cờ quyết định hành vi cửa sổ (freeform / resizable / thanh trạng thái) ───────────────
echo; echo "[B] Cờ hệ thống liên quan bố cục ô + freeform (U8a · P2/P3.1 · D-emu)"
{
  for s in enable_freeform_support force_resizable_activities development_settings_enabled adb_enabled; do
    printf '%-34s %s\n' "global/$s" "$(k_sh "settings get global $s" 2>/dev/null | tr -d '\r')"
  done
  for s in enabled_accessibility_services accessibility_enabled enabled_notification_listeners default_input_method; do
    printf '%-34s %s\n' "secure/$s" "$(k_sh "settings get secure $s" 2>/dev/null | tr -d '\r')"
  done
} | tee "$OUT/10-settings-flags.txt"

# ── C. Quyền của Kachi (vòng kiểm PermissionPreflight) ──────────────────────────────────────
echo; echo "[C] Quyền Kachi — cùng 5 điều kiện mà PermissionPreflight đọc"
{
  echo "== appops SYSTEM_ALERT_WINDOW =="
  k_sh "appops get $KACHI_PKG SYSTEM_ALERT_WINDOW" 2>&1 | tr -d '\r'
  echo; echo "== notification listener =="
  k_sh "settings get secure enabled_notification_listeners" 2>&1 | tr -d '\r'
  echo; echo "== accessibility =="
  k_sh "settings get secure enabled_accessibility_services" 2>&1 | tr -d '\r'
  k_sh "settings get secure accessibility_enabled" 2>&1 | tr -d '\r'
  echo; echo "== HOME mặc định =="
  k_sh "cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME" 2>&1 | tr -d '\r'
  echo; echo "== icon LAUNCHER của Kachi (phải đúng 1) =="
  k_sh "pm query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $KACHI_PKG" 2>&1 | tr -d '\r' | grep -c "KachiHomeActivity" 2>/dev/null || true
  echo; echo "== quyền đã cấp =="
  k_sh "dumpsys package $KACHI_PKG | sed -n '/requested permissions/,/User 0/p'" 2>&1 | tr -d '\r'
} > "$OUT/10-permissions.txt" 2>&1
k_say "→ 10-permissions.txt"

# ── D. Dấu vết app của chính mình trên đĩa (bản RELEASE ⇒ KHÔNG run-as được) ────────────────
echo; echo "[D] Tệp app tự ghi (đọc qua đường EXTERNAL — bản release không debuggable nên không run-as)"
k_sh "ls -l /sdcard/Android/data/$KACHI_PKG/files/ 2>/dev/null" 2>&1 | tee "$OUT/10-appfiles-ls.txt"
k_say "diag/  = ClusterDiag tự chụp sau MỖI lần chiếu thành công (ClusterCast.autoDiag) — không có nút bấm riêng"
k_say "castlog/ = TEE log chiếu (RT1.6)"
k_unk "Android 12 (DL5) có thể chặn shell đọc /sdcard/Android/data — nếu ls rỗng, lấy qua app Quản lý tệp trên xe"

# ── E. Ảnh màn + logcat gốc ─────────────────────────────────────────────────────────────────
echo; echo "[E] Ảnh màn + logcat gốc"
k_adb exec-out screencap -p > "$OUT/10-screen-baseline.png" 2>/dev/null || k_warn "screencap hụt"
[ -s "$OUT/10-screen-baseline.png" ] && k_ok "10-screen-baseline.png" || rm -f "$OUT/10-screen-baseline.png"
k_adb logcat -d -v threadtime > "$OUT/10-logcat-baseline.txt" 2>&1 || true
k_say "→ 10-logcat-baseline.txt"

# ── F. (ĐỔI STATE) mở Kachi › Hệ thống & quyền ─────────────────────────────────────────────
echo; k_hr
if k_confirm "mở màn Kachi › Cài đặt › Hệ thống & quyền (đổi app TIỀN CẢNH của màn giữa)" \
             "bấm phím Home của xe, hoặc mở lại app đang dùng trước đó" read; then
  k_sh "am start -n $KACHI_HOME_COMP --es open_settings_group system" 2>&1 | tail -2
  k_say "chờ 2 giây cho màn dựng…"; k_adb shell "sleep 2" >/dev/null 2>&1 || true
  k_adb exec-out screencap -p > "$OUT/10-screen-settings-system.png" 2>/dev/null || true
  [ -s "$OUT/10-screen-settings-system.png" ] || rm -f "$OUT/10-screen-settings-system.png"
  k_ok "đã mở (ảnh: 10-screen-settings-system.png) — đây là nơi có Kiểm tra cập nhật + Chẩn đoán"
  k_logcat_slice "10-logcat-preflight.txt" Preflight KACHI Kachi
fi

k_hr
echo "XONG bước 1. Tiếp: 20-datums.sh (đọc dữ liệu xe) — vẫn là bước ĐỌC."
k_note "10-baseline: đã chụp khung hệ thống + quyền + cờ"
