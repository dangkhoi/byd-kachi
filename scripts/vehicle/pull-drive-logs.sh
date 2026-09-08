#!/bin/bash
# ============================================================================
#  LẤY LOG LÁI THỬ ClusterNav 2.0 (macOS / Linux)  —  cho người KHÔNG rành IT
# ----------------------------------------------------------------------------
#  Cách dùng:  bash pull-drive-logs.sh            (xe nối bằng cáp USB)
#          hoặc bash pull-drive-logs.sh 192.168.1.50   (xe cùng WiFi, thay IP)
#  Log sẽ được kéo về thư mục trên Desktop, rồi tự mở ra.
#  (Chi tiết cài đặt xem docs/HUONG-DAN-LAY-LOG.md)
# ============================================================================
set -u
PKG="com.byd.clusternav2"
SRC="/sdcard/Android/data/${PKG}/files"
STAMP="$(date +%Y%m%d-%H%M%S)"
DEST="${HOME}/Desktop/clusternav-logs-${STAMP}"
ADB="${ADB:-adb}"

echo "== ClusterNav 2.0 — lấy log lái thử =="

# 1) Có adb chưa?
if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "❌ Chưa có 'adb' trên máy. Mở docs/HUONG-DAN-LAY-LOG.md, làm 'BƯỚC 1 — Cài adb' rồi chạy lại."
  exit 1
fi

# 2) Nối xe qua WiFi nếu có IP; nếu không thì dùng cáp USB.
if [ "${1:-}" != "" ]; then
  echo "→ Nối tới xe qua WiFi: $1:5555"
  "$ADB" connect "$1:5555" || true
fi

# 3) Có thiết bị nào không?
if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "❌ Không thấy xe. Kiểm tra: cáp USB đã cắm (và bấm 'Allow' trên màn xe), HOẶC nhập đúng IP WiFi."
  exit 1
fi

# 4) Kéo log về.
mkdir -p "$DEST"
echo "→ Đang lấy log về: $DEST"
if "$ADB" pull "$SRC" "$DEST" >/dev/null 2>&1; then
  echo "✅ XONG. Log nằm ở: $DEST"
  echo "   Gồm: nav_notif_*.csv (notification), nav_log_*.csv (khoảng cách+nội suy),"
  echo "        nav_arrow_log_*.csv + nav_arrow_pngs_* (mũi tên), diag/ (ảnh chụp màn chính + cụm)."
  open "$DEST" >/dev/null 2>&1 || true   # macOS mở Finder; Linux bỏ qua
else
  echo "❌ Không lấy được log. Thường do: app CHƯA bật 'Nhật ký chi tiết' (nhấn-giữ dòng phiên bản"
  echo "   trên màn hình chính tới khi hiện 'Nhật ký chi tiết: BẬT'), hoặc chưa lái nên chưa có log."
  exit 1
fi
