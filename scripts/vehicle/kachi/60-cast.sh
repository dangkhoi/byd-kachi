#!/usr/bin/env bash
# 60-cast.sh — X1 / ARCH-🚗: KACHI SỐNG CHUNG VỚI CHIẾU-CỤM.
#
# Không viết lại máy dò: gọi lại `scripts/on-car-verify.sh` — nó đã khoá đúng bug P0 "cửa sổ mồ côi"
# (WM thấy / AM không ⇒ chỉ tắt máy xe mới sạch). Tệp đó nay nhận PKG qua biến môi trường; ở đây
# truyền PKG=com.byd.launcher. HÀNH VI của nó KHÔNG bị đổi — kể cả câu nhắc còn viết "ClusterNav",
# ánh xạ sang màn Kachi ở phần in dưới đây.
#
# ⚠ Bước này là bước RỦI RO NHẤT của buổi test: nó thao tác trên cụm trước mặt người lái.
#    CHỈ chạy khi xe ĐỖ, số P, phanh tay. Hoàn tác cuối cùng luôn là: TẮT CHIẾU → TRẢ ĐỒNG HỒ.
#
# DÙNG:  scripts/vehicle/kachi/60-cast.sh [<ip-xe>:5555]
set -uo pipefail
. "$(dirname "$0")/_common.sh"

OUT="$(k_out)"; ROOT="$(k_root)"
TARGET="$(k_target "${1:-}")" || exit 4; export KACHI_TARGET="$TARGET"
VERIFY="$ROOT/scripts/on-car-verify.sh"
echo "carlog: $OUT · xe: $TARGET"; k_hr

[ -f "$VERIFY" ] || { k_bad "không thấy $VERIFY"; exit 2; }

cat <<'MAP'
  Ánh xạ câu nhắc của on-car-verify.sh → màn Kachi 1.47:
    "Mở ClusterNav → tick 1 app nav → CHIẾU LÊN CỤM"
        = Kachi › Cài đặt › Chiếu màn lên cụm  (chọn app + bật chiếu)
    "bấm TẮT — TRẢ ĐỒNG HỒ"
        = cùng nhóm đó, nút tắt chiếu / nút nổi trên màn
  Ba mục on-car-verify [1] nói về bản ClusterNav sạch (navprobe / WRITE_EXTERNAL_STORAGE):
  với gói Kachi chúng chỉ là thông tin, KHÔNG phải tiêu chí đạt/không.
MAP

k_hr
echo "[A] Trạng thái cụm TRƯỚC (đọc)"
VD="$(k_find_vd)"
[ -n "$VD" ] && k_ok "VD cụm = display $VD" || k_warn "chưa dò thấy VD cụm — on-car-verify sẽ tự dừng ở mốc đó"
k_cap "60-am-stack-before.txt" "am stack list"

k_hr
if ! k_confirm "chạy on-car-verify.sh (chiếu app lên CỤM, chiếu CP/AA, stress đổi app + resize)" \
               "trong app bấm TẮT — TRẢ ĐỒNG HỒ; nếu cụm kẹt: tắt máy xe rồi nổ lại"; then
  echo "bỏ qua bước cast."; exit 0
fi

export PKG="$KACHI_PKG"
export OUT_DIR="$OUT/60-oncar-verify"
export ADB
k_say "chạy: PKG=$PKG OUT_DIR=$OUT_DIR $VERIFY $TARGET"
bash "$VERIFY" "$TARGET"
RC=$?
k_say "on-car-verify.sh kết thúc rc=$RC (0 = không mồ côi ở mọi mốc)"
k_note "60-cast: on-car-verify rc=$RC"

k_hr; echo "[B] Sau khi tắt chiếu — kiểm cụm đã trả về sạch"
k_cap "60-am-stack-after.txt" "am stack list"
k_cap "60-window-displays-after.txt" "dumpsys window displays"
k_say "So 60-am-stack-before.txt với 60-am-stack-after.txt: stack trên VD phải về như cũ."

k_hr; echo "[C] Việc quan sát bằng mắt (không lệnh nào thay được)"
k_todo "S3 · khung/DPI khi ĐANG chiếu: chỉnh 4 mép + DPI ở Chiếu cụm — khung có co đúng không?"
k_todo "S3 · preview cụm nhúng trong Settings có vẽ đúng khung đang chiếu không?"
k_todo "U12 · KÉO bong bóng VietMap TRONG LÚC đang chiếu: kéo được, nhả đúng chỗ, sống qua lần mở sau?"
k_todo "U8(a) · khi cửa sổ ô ở chế độ freeform có focus: thanh trạng thái Android có hiện lại ở mép trên không?"
k_todo "D-emu · thử đưa app TỪ CHỐI màn phụ (Waze/GMaps) vào một ô: trên xe nó vào ô hay nhảy toàn màn?"
k_pause "Đã quan sát + chụp ảnh xong thì Enter"
k_adb exec-out screencap -p > "$OUT/60-screen-after-cast.png" 2>/dev/null || true
[ -s "$OUT/60-screen-after-cast.png" ] || rm -f "$OUT/60-screen-after-cast.png"

k_hr; echo "XONG bước 6. Tiếp: 90-collect.sh"
