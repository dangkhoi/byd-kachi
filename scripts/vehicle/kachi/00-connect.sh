#!/usr/bin/env bash
# 00-connect.sh — NỐI + CHỤP DANH TÍNH XE. Toàn bộ bước này chỉ ĐỌC (không đổi state).
#
# Trả lời đúng 4 câu trước khi làm bất cứ gì khác:
#   1. adb có vào được xe không?
#   2. Kachi đang cài bản NÀO (đọc từ máy — CLAUDE.md §9 cấm đoán)?
#   3. Xe đời nào (ROM/Android/model) — DL3 hay DL5?
#   4. VD cụm là display mấy (ĐO, không lấy cờ RAM)?
#
# DÙNG:  scripts/vehicle/kachi/00-connect.sh [<ip-xe>:5555]
#        KACHI_TARGET=<ip-xe>:5555 scripts/vehicle/kachi/00-connect.sh
set -uo pipefail
. "$(dirname "$0")/_common.sh"

OUT="$(k_out)"; echo "carlog: $OUT"; k_hr

TARGET="$(k_target "${1:-}")" || exit 4
echo "xe = $TARGET"
case "$TARGET" in
  *:*) k_say "adb connect $TARGET"; "$ADB" connect "$TARGET" >/dev/null 2>&1 || true ;;
esac
export KACHI_TARGET="$TARGET"

if ! k_sh "echo ok" >/dev/null 2>&1; then
  k_bad "KHÔNG nối được xe qua adb."
  k_say "Kiểm: cùng mạng WiFi? · cắm CarPlay/AA thì đầu xe TẮT WiFi (rút ra đo trước) ·"
  k_say "      adbd nghe cổng 5555 chưa (xem docs/diagnostics/oncar-playbook-kachi-1.47.md §5)."
  exit 1
fi
k_ok "adb nối được"
{
  echo "target=$TARGET"
  echo "time_host=$(date +%FT%T%z)"
  echo "adb_version=$("$ADB" version 2>&1 | head -1)"
} > "$OUT/connect.txt"

# ── 1. Phiên bản Kachi ĐANG CÀI ─────────────────────────────────────────────────────────────
k_hr; echo "[1] Kachi đang cài (đọc từ máy)"
VER="$(k_installed_version)"; CODE="$(k_installed_code)"
if [ -n "$VER" ]; then k_ok "Kachi $KACHI_PKG versionName=$VER versionCode=${CODE:-?}"
else k_bad "$KACHI_PKG CHƯA cài trên xe — xem playbook §2.L2 (cài tay lần đầu)"; fi
{ echo "kachi_pkg=$KACHI_PKG"; echo "kachi_versionName=${VER:-absent}"; echo "kachi_versionCode=${CODE:-absent}"; } >> "$OUT/connect.txt"
k_cap "00-package-kachi.txt" "dumpsys package $KACHI_PKG"

# Chữ ký: bản 1.47 ký khoá Kachi riêng (L2). Khác khoá ⇒ OTA/cài đè sẽ từ chối.
k_cap "00-package-signature.txt" "dumpsys package $KACHI_PKG | grep -iE 'signatures|pkgFlags|DEBUGGABLE'"

# ── 2. Danh tính xe ─────────────────────────────────────────────────────────────────────────
k_hr; echo "[2] Danh tính xe (ROM / đời DiLink)"
k_cap "00-getprop.txt" "getprop"
for p in ro.product.model ro.product.name ro.product.device ro.product.cpu.abi ro.product.cpu.abilist ro.build.version.release ro.build.version.sdk ro.build.fingerprint ro.build.display.id; do
  v="$(k_sh "getprop $p" 2>/dev/null | tr -d '\r')"
  printf '  %-28s %s\n' "$p" "${v:-?}"
  printf '%s=%s\n' "$p" "${v:-}" >> "$OUT/connect.txt"
done
k_say "(DL3 ≈ Android 10 · DL5 ≈ Android 12 — khác biệt đời xe phải nằm ở ClusterProfile, CLAUDE.md §7)"

# ── 3. Kênh adbd ────────────────────────────────────────────────────────────────────────────
k_hr; echo "[3] Kênh adbd (chỉ đọc — app dùng loopback 5555 cho dadb)"
for s in "getprop service.adb.tcp.port" "settings get global adb_enabled" "settings get global development_settings_enabled"; do
  printf '  %-46s %s\n' "$s" "$(k_sh "$s" 2>/dev/null | tr -d '\r')"
done
k_say "adbd KHÔNG nghe tcp ⇒ kênh dadb trong app (cài OTA · tự cấp quyền · lệnh cửa sổ) sẽ câm."

# ── 4. Display + VD cụm ─────────────────────────────────────────────────────────────────────
k_hr; echo "[4] Display (ĐO VD cụm)"
k_cap "00-display.txt" "dumpsys display"
k_cap "00-window-displays.txt" "dumpsys window displays"
VD="$(k_find_vd)"
if [ -n "$VD" ] && [ "$VD" -ge 1 ] 2>/dev/null; then
  k_ok "VD cụm = display $VD (khớp fission/xdja)"
  echo "cluster_vd=$VD" >> "$OUT/connect.txt"
else
  k_warn "không dò thấy VD cụm (fission/xdja). Cụm có thể chưa dựng — mọi bước cast phải ghi CHƯA ĐO."
  echo "cluster_vd=" >> "$OUT/connect.txt"
fi
k_sh "dumpsys display | grep -E 'mDisplayId=|uniqueId|displayId '" > "$OUT/00-display-ids.txt" 2>&1 || true

k_hr
echo "XONG bước 0. Tiếp: scripts/vehicle/kachi/10-baseline.sh"
k_note "00-connect: target=$TARGET kachi=${VER:-absent}(${CODE:-?}) vd=${VD:-none}"
