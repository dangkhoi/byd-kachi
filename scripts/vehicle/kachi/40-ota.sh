#!/usr/bin/env bash
# 40-ota.sh — L2: KÊNH OTA trên xe thật (bản đang cài → 1.47).
#
# Đường OTA của Kachi (`UpdateChecker.kt`): hỏi GitHub Contents API thư mục `apk` nhánh `main` của
# repo `dangkhoi/byd-kachi`, tìm `Kachi-<ver>-release.apk` mới hơn bản đang cài, tải, rồi cài qua
# **dadb loopback** (`pm install -r`) — KHÔNG qua trình cài đặt hệ thống.
# ⇒ Ba thứ phải đúng cùng lúc: (1) xe có mạng ra GitHub, (2) adbd loopback 5555 sống,
#   (3) bản đang cài ký CÙNG khoá Kachi (từ 1.41). Sai (3) ⇒ pm từ chối, phải gỡ + cài tay MỘT lần.
#
# DÙNG:  scripts/vehicle/kachi/40-ota.sh [<ip-xe>:5555]
set -uo pipefail
. "$(dirname "$0")/_common.sh"

OUT="$(k_out)"; TARGET="$(k_target "${1:-}")" || exit 4; export KACHI_TARGET="$TARGET"
REPO="${KACHI_OTA_REPO:-dangkhoi/byd-kachi}"
BRANCH="${KACHI_OTA_BRANCH:-main}"
echo "carlog: $OUT · xe: $TARGET · kênh: $REPO@$BRANCH/apk"; k_hr

# ── A. Bản đang cài (đọc từ máy — §9) ───────────────────────────────────────────────────────
echo "[A] Bản đang cài trên xe"
CUR="$(k_installed_version)"; CURC="$(k_installed_code)"
if [ -z "$CUR" ]; then
  k_bad "$KACHI_PKG chưa cài. Cài tay MỘT lần: adb -s $TARGET install -r apk/Kachi-1.47-release.apk"
  k_say "(nếu báo INSTALL_FAILED_UPDATE_INCOMPATIBLE: bản cũ ký khoá khác ⇒ gỡ rồi cài lại —"
  k_say " gỡ là MẤT prefs, nên chụp cấu hình trước.)"
else
  k_ok "đang cài: $CUR ($CURC)"
fi

# ── B. Kênh OTA nhìn thấy gì ────────────────────────────────────────────────────────────────
k_hr; echo "[B] Kênh OTA (hỏi GitHub từ MÁY BẠN — chỉ để biết kỳ vọng; app hỏi bằng mạng của XE)"
API="https://api.github.com/repos/$REPO/contents/apk?ref=$BRANCH"
if command -v curl >/dev/null 2>&1; then
  curl -fsSL "$API" > "$OUT/40-ota-listing.json" 2>"$OUT/40-ota-listing.err" || true
  if [ -s "$OUT/40-ota-listing.json" ]; then
    python3 - "$OUT/40-ota-listing.json" "${CUR:-}" <<'PY'
import json, re, sys
try:
    items = json.load(open(sys.argv[1], encoding="utf-8"))
except Exception as e:
    print("  [WARN] không đọc được listing: %s" % e); raise SystemExit(0)
cur = sys.argv[2] if len(sys.argv) > 2 else ""
RE = re.compile(r"(?:Kachi|ClusterNav)-([0-9]+(?:\.[0-9]+)*)-release\.apk$")   # = UpdateChecker.RE_APK
def key(v): return [int(x) for x in v.split(".")]
found = []
for it in items if isinstance(items, list) else []:
    m = RE.fullmatch(it.get("name", ""))
    if m:
        found.append((m.group(1), it.get("name"), it.get("size", 0)))
if not found:
    print("  [FAIL] kênh KHÔNG có tệp nào đúng khuôn Kachi-<ver>-release.apk")
    raise SystemExit(0)
for v, n, s in sorted(found, key=lambda x: key(x[0])):
    print("  kênh có: %-34s %s  (%.1f MB)" % (n, v, s / 1048576.0))
newest = max(found, key=lambda x: key(x[0]))
print("  mới nhất trên kênh = %s" % newest[0])
if cur:
    if key(newest[0]) > key(cur):
        print("  [OK]   app trên xe (%s) SẼ thấy bản mới %s" % (cur, newest[0]))
    elif key(newest[0]) == key(cur):
        print("  [WARN] xe đã ở đúng bản mới nhất (%s) ⇒ nút Kiểm tra cập nhật sẽ báo 'đã mới nhất'." % cur)
        print("         Muốn test đường cài thật thì phải hạ bản trên xe trước (xem playbook §2.L2).")
    else:
        print("  [WARN] xe (%s) MỚI HƠN kênh (%s) — kênh chưa đăng bản này." % (cur, newest[0]))
PY
  else
    k_warn "không gọi được GitHub API từ máy bạn (xem 40-ota-listing.err) — vẫn test được trên xe."
  fi
else
  k_warn "máy không có curl; bỏ qua bước hỏi kênh."
fi

# ── C. Điều kiện kênh shell của app ─────────────────────────────────────────────────────────
k_hr; echo "[C] Điều kiện để app tự cài được (dadb loopback)"
printf '  %-40s %s\n' "getprop service.adb.tcp.port" "$(k_sh 'getprop service.adb.tcp.port' 2>/dev/null | tr -d '\r')"
printf '  %-40s %s\n' "settings get global adb_enabled" "$(k_sh 'settings get global adb_enabled' 2>/dev/null | tr -d '\r')"
k_say "Lần đầu app mở kênh dadb, đầu xe hiện hộp 'Cho phép gỡ lỗi USB' — PHẢI bấm Cho phép (tick"
k_say "'luôn cho phép'), nếu không đường cài đứng im mà không báo lỗi rõ."

# ── D. Chạy thật (ĐỔI STATE: cài đè app) ────────────────────────────────────────────────────
k_hr; echo "[D] Chạy thật"
k_say "Đường trong app: Cài đặt › Hệ thống & quyền › Bảo trì › **Kiểm tra cập nhật**"
if k_confirm "mở thẳng nhóm Hệ thống & quyền trên xe (đổi app tiền cảnh)" "bấm Home trên xe" read; then
  k_sh "am start -n $KACHI_HOME_COMP --es open_settings_group system" >/dev/null 2>&1 || true
fi
k_todo "Bấm Kiểm tra cập nhật. Ghi NGUYÊN VĂN câu app trả lời (đúng câu chữ là dữ liệu — U11)."
k_todo "Nếu có bản mới: bấm tải + cài. App tự mở lại sau ~5 s."
k_pause "Xong (hoặc đã ghi câu lỗi) thì Enter"

k_logcat_slice "40-logcat-ota.txt" UpdateRelaunch Kachi KACHI Preflight
NEW="$(k_installed_version)"; NEWC="$(k_installed_code)"
{
  echo "before=${CUR:-absent} (${CURC:-?})"
  echo "after=${NEW:-absent} (${NEWC:-?})"
  echo "channel_repo=$REPO branch=$BRANCH"
} > "$OUT/40-ota-result.txt"
if [ -n "$NEW" ] && [ "$NEW" != "${CUR:-}" ]; then k_ok "versionName $CUR → $NEW  ⇒ OTA CHẠY THẬT TRÊN XE"
elif [ -n "$NEW" ]; then k_warn "versionName không đổi ($NEW) — ghi rõ lý do (đã mới nhất / cài hụt / chưa bấm)"
else k_bad "sau bước này app không còn đọc được versionName — kiểm ngay"; fi

k_hr; echo "XONG bước 4. Tiếp: 50-keys.sh"
k_note "40-ota: ${CUR:-absent} → ${NEW:-absent}"
