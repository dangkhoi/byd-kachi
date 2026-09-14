#!/usr/bin/env bash
# 90-collect.sh — GOM BẰNG CHỨNG + dựng biên bản pass/fail để điền tay.
#
# Chạy CUỐI buổi (và chạy lại được nhiều lần). Chỉ ĐỌC + kéo tệp về; không đổi state nào trên xe.
# Thư mục carlog nằm trong .gitignore (`docs/diagnostics/carlog-*/`) ⇒ không lẫn vào commit.
#
# DÙNG:  scripts/vehicle/kachi/90-collect.sh [<ip-xe>:5555]
set -uo pipefail
. "$(dirname "$0")/_common.sh"

OUT="$(k_out)"; TARGET="$(k_target "${1:-}")" || exit 4; export KACHI_TARGET="$TARGET"
echo "carlog: $OUT · xe: $TARGET"; k_hr

echo "[A] Logcat + ảnh màn + khung hệ thống (cuối buổi)"
k_adb logcat -d -v threadtime > "$OUT/90-logcat-full.txt" 2>&1 || true
k_say "→ 90-logcat-full.txt ($(wc -l < "$OUT/90-logcat-full.txt" 2>/dev/null | tr -d ' ') dòng)"
k_logcat_slice "90-logcat-kachi-tags.txt" Kachi KACHI Preflight ClusterNavBridge ClusterNavReapply \
  SeatComfort Pm25Filter VmOverlayPos NavAccess NavRepository NavigationSpeedSign CastLifecycle \
  ClusterCastBubble KachiAutostart KachiAutostartSvc UpdateRelaunch KachiAppWidget
k_adb exec-out screencap -p > "$OUT/90-screen-final.png" 2>/dev/null || true
[ -s "$OUT/90-screen-final.png" ] || rm -f "$OUT/90-screen-final.png"
k_cap "90-am-stack-final.txt" "am stack list"
k_cap "90-window-displays-final.txt" "dumpsys window displays"
k_cap "90-package-final.txt" "dumpsys package $KACHI_PKG"

echo; echo "[B] Kéo tệp app tự ghi (diag của ClusterDiag + castlog)"
mkdir -p "$OUT/from-car"
for d in diag castlog; do
  if k_adb pull "/sdcard/Android/data/$KACHI_PKG/files/$d" "$OUT/from-car/" >/dev/null 2>&1; then
    k_ok "kéo được $d/ ($(find "$OUT/from-car/$d" -type f 2>/dev/null | wc -l | tr -d ' ') tệp)"
  else
    k_warn "không kéo được $d/ — chưa có lần chiếu nào, hoặc ROM chặn shell đọc /sdcard/Android/data"
    k_unk "DL5 (Android 12): nếu chặn, lấy bằng app Quản lý tệp trên xe rồi chép qua USB"
  fi
done

echo; echo "[C] Biên bản"
VER="$(k_installed_version)"; CODE="$(k_installed_code)"
README="$OUT/README.md"
{
  cat <<HDR
# Biên bản buổi test trên xe — Kachi ${VER:-?} (${CODE:-?})

> Thư mục sinh bởi \`scripts/vehicle/kachi/*\` · ngày $(date +%F) · xe \`$TARGET\`
> Quy ước bằng chứng (CLAUDE.md §2): **[ĐO]** đọc được từ máy · **[SUY]** suy luận khớp hiện tượng ·
> **[ĐOÁN]** mới chỉ hợp lý · **[CHƯA BIẾT]** không có dữ liệu. Ô trống = CHƯA ĐO, KHÔNG được suy.

## 0. Bối cảnh
| | |
|---|---|
| Kachi versionName / versionCode | ${VER:-?} / ${CODE:-?} |
| Model / ROM | (điền từ \`connect.txt\`) |
| VD cụm đo được | (điền từ \`connect.txt\`) |
| Người chạy | |
| Xe đỗ, số P, phanh tay | ☐ |

## 1. Checklist pass/fail (điền tay)

| # | Mục | Nguồn | Kết quả | Bằng chứng | Ghi chú |
|---|-----|-------|---------|-----------|---------|
| 1 | Nối adb + đọc được versionName | 00 | ☐ đạt ☐ hỏng | connect.txt | |
| 2 | VD cụm dò được | 00 | ☐ đạt ☐ hỏng ☐ không có cụm | 00-display.txt | |
| 3 | Vòng kiểm quyền đủ 5 điều kiện | 10 | ☐ đạt ☐ thiếu | 10-permissions.txt | |
| 4 | Tự cấp quyền qua dadb từ Settings | S2 | ☐ đạt ☐ hỏng | ảnh | |
| 5 | Ghế · lọc bụi PM2.5 ghi HAL thật | S2/W1 | ☐ đạt ☐ hỏng | 30-logcat-after.txt | |
| 6 | Biển báo tốc độ + bong bóng đúng vị trí | S2 | ☐ đạt ☐ hỏng | ảnh | |
| 7 | Khung/DPI khi ĐANG chiếu áp đúng | S3 | ☐ đạt ☐ hỏng | ảnh | |
| 8 | Preview cụm trong Settings | S3 | ☐ đạt ☐ hỏng | ảnh | |
| 9 | Học phím vô-lăng | S3/W5 | ☐ đạt ☐ hỏng | 50-* | |
| 10 | Tự khởi động nền (headless) | S3/P7 | ☐ đạt ☐ hỏng | 30-logcat-coldboot.txt | |
| 11 | OTA bản đang cài → 1.47 trên xe | L2 | ☐ đạt ☐ hỏng | 40-ota-result.txt | |
| 12 | Màu trạng thái thật: lốp / cửa / radar | U7/U9 | ☐ đạt ☐ hỏng | ảnh + 20-datums.md | |
| 13 | Dấu "chưa kiểm" đúng chỗ (U10) | U10 | ☐ đạt ☐ hỏng | ảnh | |
| 14 | Câu lỗi OTA nói đúng nguyên nhân (U11) | U11 | ☐ đạt ☐ hỏng | 40-* | |
| 15 | Kéo bong bóng TRONG LÚC đang chiếu | U12 | ☐ đạt ☐ hỏng | ảnh | |
| 16 | Đổi hồ sơ áp applier THẬT | U13/S4 | ☐ đạt ☐ hỏng | 30-applier-lines.txt | |
| 17 | Độ trễ đổi hồ sơ (giây) | S4 | ____ s | 30-switch-timing.txt | |
| 18 | \`cast_enabled\` KHÔNG đổi theo hồ sơ | S4-OQ2 | ☐ đúng ☐ SAI | quan sát cụm | |
| 19 | Migration cảnh → hồ sơ | S4 V-mig | ☐ đạt ☐ hỏng ☐ xe không có cảnh | 30-profiles-list.png | |
| 20 | Hồ sơ lúc nổ máy sau reboot VẬT LÝ | P7 | ☐ đạt ☐ hỏng ☐ chưa thử | 30-screen-after-coldboot.png | |
| 21 | Thanh trên: chip hồ sơ + Apps + Cài đặt, ≤8 chip | S4 R7 | ☐ đạt ☐ hỏng | 30-screen-topstrip.png | |
| 22 | W2: kính ½ · gạt mưa bảo trì · đèn đọc tất cả | W2 | ☐ đạt ☐ hỏng ☐ chưa thử | | |
| 23 | W5: phím tới onKeyEvent khi cam mở | W5 | ☐ có ☐ KHÔNG ☐ chưa đo | 50-keys-table.md | |
| 24 | W5: \`camera_view\` ăn khi cam mở | W5 | ☐ có ☐ không ☐ chưa đo | 50-keys-table.md | |
| 25 | L-RE2: 3 cặp trùng feature-id (chỉ quan sát) | L-RE2 | ☐ đã ghi ☐ chưa | 20-datums.md §4 | |
| 26 | U8(a): thanh trạng thái khi freeform | U8 | ☐ có ☐ không ☐ chưa đo | ảnh | |
| 27 | X1: KHÔNG cửa sổ mồ côi ở mọi mốc | X1 | ☐ đạt ☐ HỎNG | 60-oncar-verify/ | |
| 28 | X1: tắt chiếu trả đồng hồ về sạch | X1 | ☐ đạt ☐ hỏng | 60-am-stack-after.txt | |
| 29 | D-emu: app từ chối màn phụ vào được ô? | D-emu | ☐ vào ô ☐ nhảy toàn màn ☐ chưa thử | ảnh | |
| 30 | 10 datum NEEDS_CAR đọc ra số | W1 | ____/10 | 20-datums.md §1 | |

## 2. Lỗi gặp (mỗi lỗi một dòng, kèm bằng chứng)
| # | Triệu chứng | Lặp lại được? | Tệp/ảnh | Mức | Nghi nguyên nhân (ghi rõ [SUY]/[ĐOÁN]) |
|---|---|---|---|---|---|
| 1 | | | | | |

## 3. Thứ CHƯA ĐO ĐƯỢC (và vì sao)
- 

## 4. Việc phải làm sau buổi (đưa vào PROJECT-BACKLOG)
- 
HDR
} > "$README"
k_ok "→ README.md (biên bản, điền tay)"

echo; echo "[D] Kiểm kê tệp"
( cd "$OUT" && ls -la ) | tee "$OUT/90-manifest.txt" | tail -25
if command -v shasum >/dev/null 2>&1; then
  ( cd "$OUT" && find . -maxdepth 1 -type f ! -name SHA256SUMS.txt -exec shasum -a 256 {} \; ) > "$OUT/SHA256SUMS.txt" 2>/dev/null || true
fi

k_hr
echo "GOM XONG: $OUT"
k_say "⚠ Trước khi gửi/chép ra ngoài: tệp có thể chứa IP xe / VIN / tên tài khoản — soát rồi hãy chia sẻ."
k_say "Bước cuối của quy trình (CLAUDE.md §13.9): rút kết luận → cập nhật docs/README.md + PROJECT-BACKLOG.md"
k_say "+ .kiro/steering/project-context.md NGAY trong phiên."
