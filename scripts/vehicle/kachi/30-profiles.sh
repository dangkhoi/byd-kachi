#!/usr/bin/env bash
# 30-profiles.sh — S4/U13: ĐỔI HỒ SƠ có áp applier THẬT không, và mất bao lâu.
#
# CƠ CHẾ (đọc từ source, `ClusterNavBridgeReapply.kt`):
#   switchProfile → chụp hồ sơ cũ → đặt con trỏ → áp hồ sơ mới → broadcastLang → bridge.reapplyAll()
#   reapplyAll gọi 8 applier: nav.master · nav.clusterMode · badge.enabled/upcoming/alertChip/layout ·
#   bubble.pos · seat · pm25.
# ⚠⚠ `ClusterNavReapply` CHỈ ghi log KHI MỘT APPLIER NÉM (`Log.w` trong `step()`); im lặng = không ai
#   ném, KHÔNG phải "không chạy". Bằng chứng DƯƠNG nằm ở log của chính applier:
#     SeatComfort  "áp xong: N ghế, mode=…"      · Pm25Filter "bật/tắt lọc PM2.5 xong"
#     VmOverlayPos "gửi VM_BUBBLE_POS x=… y=…"   · NavigationSpeedSign / NavRepository
#
# DÙNG:  scripts/vehicle/kachi/30-profiles.sh [<ip-xe>:5555]
set -uo pipefail
. "$(dirname "$0")/_common.sh"

OUT="$(k_out)"; TARGET="$(k_target "${1:-}")" || exit 4; export KACHI_TARGET="$TARGET"
TAGS="ClusterNavReapply SeatComfort Pm25Filter VmOverlayPos NavigationSpeedSign NavRepository ClusterNavBridge RecircOnStart"
echo "carlog: $OUT · xe: $TARGET"; k_hr

# ── A. V-mig — xe này CÓ CẢNH cũ không, migration ra hồ sơ đúng chưa ────────────────────────
echo "[A] V-mig · migration cảnh → hồ sơ (chỉ quan sát UI — prefs nằm ở /data/data, bản release"
echo "    KHÔNG debuggable nên không run-as được)"
k_todo "Mở Kachi › Cài đặt › Hồ sơ tài xế. Ghi lại DANH SÁCH hồ sơ đang có."
k_todo "Nếu xe từng chạy bản ≤1.46 và có cảnh: mỗi cảnh phải thành MỘT hồ sơ cùng tên, và"
k_todo "hồ sơ 'nổ máy' phải trỏ đúng cảnh khởi động cũ. Thiếu = FAIL, ghi tên cảnh đã mất."
k_pause "Chụp màn danh sách hồ sơ (ảnh gửi về) rồi quay lại đây"
k_adb exec-out screencap -p > "$OUT/30-profiles-list.png" 2>/dev/null || true
[ -s "$OUT/30-profiles-list.png" ] && k_ok "30-profiles-list.png" || rm -f "$OUT/30-profiles-list.png"

# ── B. V-switch — đổi hồ sơ, đo applier + độ trễ ───────────────────────────────────────────
k_hr; echo "[B] V-switch · đổi hồ sơ trên chip thanh trên"
k_say "Trước khi đổi: ghi giúp 6 thứ (để so SAU khi đổi):"
k_say "  1 chủ đề sáng/tối · 2 đơn vị · 3 hình nền · 4 bố cục + nội dung từng ô · 5 thanh nút · 6 ngôn ngữ"
# shellcheck disable=SC2086
k_logcat_slice "30-logcat-before.txt" $TAGS
k_adb exec-out screencap -p > "$OUT/30-screen-before.png" 2>/dev/null || true
[ -s "$OUT/30-screen-before.png" ] || rm -f "$OUT/30-screen-before.png"

T0_HOST="$(date +%s)"
T0_DEV="$(k_sh 'date +%s' 2>/dev/null | tr -d '\r')"
k_pause "CHẠM chip hồ sơ trên thanh trên → chọn hồ sơ KHÁC. Bấm Enter NGAY sau khi chạm"
T1_HOST="$(date +%s)"
k_say "chờ 4 giây cho applier chạy xong…"
k_adb shell "sleep 4" >/dev/null 2>&1 || true
k_adb exec-out screencap -p > "$OUT/30-screen-after.png" 2>/dev/null || true
[ -s "$OUT/30-screen-after.png" ] || rm -f "$OUT/30-screen-after.png"
# shellcheck disable=SC2086
k_logcat_slice "30-logcat-after.txt" $TAGS

{
  echo "t0_host=$T0_HOST t1_host=$T1_HOST delta_host_s=$((T1_HOST - T0_HOST))"
  echo "t0_device_epoch=${T0_DEV:-?}"
  echo "# delta_host = thời gian người thao tác, KHÔNG phải độ trễ applier."
  echo "# Độ trễ applier = khoảng cách giữa dòng log applier ĐẦU và CUỐI dưới đây."
} > "$OUT/30-switch-timing.txt"

echo; echo "Dòng applier MỚI xuất hiện sau lượt đổi:"
if [ -f "$OUT/30-logcat-before.txt" ] && [ -f "$OUT/30-logcat-after.txt" ]; then
  diff "$OUT/30-logcat-before.txt" "$OUT/30-logcat-after.txt" 2>/dev/null \
    | sed -n 's/^> //p' > "$OUT/30-applier-lines.txt" || true
  if [ -s "$OUT/30-applier-lines.txt" ]; then
    sed -n '1,40p' "$OUT/30-applier-lines.txt"
    FIRST="$(awk 'NR==1{print $2}' "$OUT/30-applier-lines.txt")"
    LAST="$(awk 'END{print $2}' "$OUT/30-applier-lines.txt")"
    k_ok "applier CÓ chạy — dòng đầu $FIRST · dòng cuối $LAST (độ trễ = hiệu hai mốc này)"
    { echo "applier_first=$FIRST"; echo "applier_last=$LAST"; } >> "$OUT/30-switch-timing.txt"
  else
    k_warn "KHÔNG có dòng applier nào mới."
    k_say "Nghĩa đúng: (a) hồ sơ mới trùng cấu hình hồ sơ cũ ⇒ applier chạy nhưng không có gì để nói;"
    k_say "hoặc (b) reapplyAll không được gọi. Phân biệt bằng cách đổi sang hồ sơ có GHẾ/LỌC BỤI khác."
    k_unk "chưa kết luận — ghi vào biên bản là CHƯA ĐO, không được suy."
  fi
fi
THROWN="$(grep -c "ClusterNavReapply" "$OUT/30-logcat-after.txt" 2>/dev/null | tr -d " ")"
if [ "${THROWN:-0}" -gt 0 ] 2>/dev/null; then
  grep -n "ClusterNavReapply" "$OUT/30-logcat-after.txt" | head -10
  k_bad "có $THROWN dòng applier NÉM — lỗi thật, chép nguyên văn về"
else
  k_ok "không applier nào ném (tag ClusterNavReapply im lặng)"
fi

k_todo "So 6 thứ đã ghi ở trên. Ghi cái nào ĐỔI THEO hồ sơ, cái nào KHÔNG."
k_todo "Kiểm riêng: biển báo tốc độ · bong bóng VietMap · phím vô-lăng · ghế · lọc bụi · tự chiếu."
k_say "Theo thiết kế S4: cast_enabled theo XE (KHÔNG đổi theo hồ sơ) — cụm KHÔNG được tối đi/sáng lên"
k_say "khi chạm chip. Nếu nó đổi ⇒ đúng lỗi S4-OQ2, báo ngay."
k_say "seat_level_1..3 CHƯA theo hồ sơ (nợ S4-SEAT) — ghế 2/3/4 không đổi là ĐÚNG bản này."

# ── C. Chip 8 + ô Ứng dụng/Cài đặt trên thanh nút ───────────────────────────────────────────
k_hr; echo "[C] Thanh trên (S4 R7) — chip hồ sơ + Apps/Cài đặt"
k_todo "Thanh trên CHỈ còn: chip hồ sơ (mở bộ chọn) + icon lưới (Apps) + icon bánh răng (Cài đặt)."
k_todo "KHÔNG còn 5 nút đổi bố cục. Tối đa 8 chip. Chụp một ảnh thanh trên."
k_pause "Xong thì Enter"
k_adb exec-out screencap -p > "$OUT/30-screen-topstrip.png" 2>/dev/null || true
[ -s "$OUT/30-screen-topstrip.png" ] || rm -f "$OUT/30-screen-topstrip.png"

# ── D. P7 — hồ sơ lúc nổ máy + reboot VẬT LÝ ───────────────────────────────────────────────
k_hr; echo "[D] P7 · hồ sơ lúc nổ máy (chỉ nhận reboot VẬT LÝ làm bằng chứng — ARCH-🚗 mục 6)"
k_say "⚠ KHÔNG dùng 'adb reboot' để chứng minh mục này: lệnh khởi động MỀM không thay được việc tắt máy."
if k_confirm "TẮT MÁY XE hẳn (rút chìa/khoá nguồn) rồi nổ lại — sẽ MẤT kết nối adb vài phút" \
             "không cần hoàn tác; nếu adb không về: chạy lại 00-connect.sh sau khi xe lên"; then
  k_pause "Tắt máy, đợi hết, nổ lại, chờ launcher lên. Enter khi màn Kachi đã hiện"
  "$ADB" connect "$TARGET" >/dev/null 2>&1 || true
  k_adb exec-out screencap -p > "$OUT/30-screen-after-coldboot.png" 2>/dev/null || true
  [ -s "$OUT/30-screen-after-coldboot.png" ] || rm -f "$OUT/30-screen-after-coldboot.png"
  # shellcheck disable=SC2086
  k_logcat_slice "30-logcat-coldboot.txt" KachiAutostart KachiAutostartSvc BootSetup NavRebind $TAGS
  k_todo "Kỳ vọng: đúng HỒ SƠ NỔ MÁY · app vào đúng ô · thanh nút đúng · không hộp thoại lạ."
fi

k_hr; echo "XONG bước 3. Tiếp: 40-ota.sh"
k_note "30-profiles: đã đo V-switch (+ V-mig/P7 nếu owner chạy)"
