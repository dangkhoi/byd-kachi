#!/usr/bin/env bash
# 50-keys.sh — W5 tầng 1 (CLAUDE.md §14): ĐO PHÍM VÔ-LĂNG THẬT trước khi viết một dòng code nào.
#
# Ba câu hỏi phải trả lời bằng SỐ, không bằng suy đoán (spec kachi-camera-context-keys.html §6):
#   Q1. Mỗi phím (tăng âm · giảm âm · trái · phải vô-lăng) phát scancode/keycode NÀO?
#   Q2. Phím có TỚI `NavAccessibilityService.onKeyEvent` khi app camera đang tiền cảnh không?
#       → bằng chứng = dòng logcat `NavAccess: onKeyEvent DOWN keycode=<n> (<tên>)` (nó log MỌI phím).
#   Q3. Lệnh đổi góc (`camera_view`, feature 3001) có ăn khi app cam đang mở không?
#
# DÙNG:  scripts/vehicle/kachi/50-keys.sh [<ip-xe>:5555]        (SECS=25 để đổi thời lượng bắt)
set -uo pipefail
. "$(dirname "$0")/_common.sh"

OUT="$(k_out)"; TARGET="$(k_target "${1:-}")" || exit 4; export KACHI_TARGET="$TARGET"
SECS="${SECS:-25}"
echo "carlog: $OUT · xe: $TARGET"; k_hr

# ── A. Thiết bị input nào có phím ──────────────────────────────────────────────────────────
echo "[A] getevent -lp — thiết bị input + khả năng (chỉ đọc)"
k_cap "50-getevent-lp.txt" "getevent -lp"
grep -iE "add device|name:|KEY_VOLUME|KEY_UP|KEY_LEFT|KEY_RIGHT|KEY_ENTER|KEY_MEDIA" "$OUT/50-getevent-lp.txt" 2>/dev/null | head -40
if [ ! -s "$OUT/50-getevent-lp.txt" ] || grep -qi "permission denied" "$OUT/50-getevent-lp.txt" 2>/dev/null; then
  k_unk "getevent bị từ chối (SELinux / không root trên ROM này) ⇒ BỎ Q1 đường getevent,"
  k_unk "dùng đường logcat NavAccess ở mục [C] — nó cho keycode Android, đủ để gán phím."
fi
k_cap "50-input-devices.txt" "dumpsys input | sed -n '1,120p'"

# ── B. Bắt sự kiện thô trong lúc bấm ───────────────────────────────────────────────────────
k_hr; echo "[B] getevent -lt trong $SECS giây — BẤM LẦN LƯỢT 4 phím (mỗi phím: 1 nhấp ngắn, rồi GIỮ 1 giây)"
k_say "Thứ tự đề nghị, đọc to khi bấm để khớp với mốc thời gian: tăng âm → giảm âm → trái → phải."
k_pause "Sẵn sàng bấm? Enter để bắt đầu bắt"
( k_sh "getevent -lt" > "$OUT/50-getevent-lt.txt" 2>&1 ) &
GE_PID=$!
i=0
while [ "$i" -lt "$SECS" ]; do
  printf '\r  ⏱ còn %2ds — bấm phím đi…  ' "$((SECS - i))"
  sleep 1
  i=$((i + 1))
done
printf '\n'
kill "$GE_PID" 2>/dev/null || true
wait "$GE_PID" 2>/dev/null || true
if [ -s "$OUT/50-getevent-lt.txt" ]; then
  k_ok "→ 50-getevent-lt.txt ($(wc -l < "$OUT/50-getevent-lt.txt" | tr -d ' ') dòng)"
  grep -E "EV_KEY" "$OUT/50-getevent-lt.txt" 2>/dev/null | head -30
else
  k_warn "không bắt được sự kiện nào (bị chặn, hoặc phím không đi qua /dev/input)"
fi

# ── C. Phím có TỚI app không (đường quyết định của W5) ─────────────────────────────────────
k_hr; echo "[C] logcat NavAccess — phím có tới onKeyEvent của app không"
k_say "ĐIỀU KIỆN: dịch vụ Hỗ trợ (accessibility) của Kachi phải ĐANG BẬT, nếu không mục này vô nghĩa."
printf '  enabled_accessibility_services = %s\n' "$(k_sh 'settings get secure enabled_accessibility_services' 2>/dev/null | tr -d '\r')"
k_pause "Bấm lại 4 phím đó MỘT LƯỢT NỮA (lần này ở màn thường), rồi Enter"
k_logcat_slice "50-logcat-navaccess-home.txt" NavAccess
grep -E "onKeyEvent DOWN keycode=" "$OUT/50-logcat-navaccess-home.txt" 2>/dev/null | tail -20

# ── D. Ngữ cảnh CAMERA — đúng câu hỏi của W5 ───────────────────────────────────────────────
k_hr; echo "[D] Lặp lại KHI APP CAMERA 360 ĐANG TIỀN CẢNH"
k_todo "Mở camera 360 của xe (nút cam trên Kachi, hoặc thao tác của xe)."
k_pause "Camera đã hiện? Enter để chụp app tiền cảnh"
k_cap "50-focus-camera.txt" "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"
k_say "→ package app cam THẬT nằm ở dòng trên — ghi lại, spec cần nó cho danh sách ngữ cảnh."
k_pause "Bấm 4 phím đó trong lúc cam đang mở, rồi Enter"
k_logcat_slice "50-logcat-navaccess-camera.txt" NavAccess
grep -E "onKeyEvent DOWN keycode=" "$OUT/50-logcat-navaccess-camera.txt" 2>/dev/null | tail -20
k_say "KHÔNG có dòng nào ⇒ app cam (hoặc hệ) NUỐT phím trước dịch vụ Hỗ trợ ⇒ W5 R1 không thoả"
k_say "⇒ theo §14 thì KHÔNG được viết code ngữ cảnh; ghi 'chưa làm được' + điều kiện mở khoá."

k_hr; echo "[E] Q3 — lệnh đổi góc có ăn khi cam đang mở không"
k_todo "Trong lúc cam mở: bấm ô 'Góc camera › Trái' (hoặc nút camera_view) trên Kachi."
k_todo "Ghi: cam có đổi góc không? Có báo lỗi gì không? (đừng đoán — không thấy đổi thì ghi 'không đổi')"
k_pause "Xong thì Enter"
k_adb exec-out screencap -p > "$OUT/50-screen-camera.png" 2>/dev/null || true
[ -s "$OUT/50-screen-camera.png" ] || rm -f "$OUT/50-screen-camera.png"

cat > "$OUT/50-keys-table.md" <<'MD'
# W5 — bảng đo phím (điền tay, trống = CHƯA ĐO)

| Phím | scancode (getevent) | keycode (logcat NavAccess) | Tới onKeyEvent ở màn thường? | Tới onKeyEvent khi cam mở? | Có mã riêng khi GIỮ? |
|---|---|---|---|---|---|
| Tăng âm |  |  |  |  |  |
| Giảm âm |  |  |  |  |  |
| Trái vô-lăng |  |  |  |  |  |
| Phải vô-lăng |  |  |  |  |  |

- Package app camera (từ `50-focus-camera.txt`): `________`
- Q3 — `camera_view` đổi góc khi cam đang mở: ☐ có ☐ không ☐ chưa thử
- OQ2 spec — khi CarPlay/AA đang chiếu, app cam còn nhận phím không: ☐ có ☐ không ☐ chưa thử
MD
k_ok "→ 50-keys-table.md (điền tay)"

k_hr; echo "XONG bước 5. Tiếp: 60-cast.sh"
k_note "50-keys: đã bắt getevent + logcat NavAccess (thường + ngữ cảnh cam)"
