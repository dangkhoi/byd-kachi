#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────────────────────────
# _common.sh — nền chung cho bộ script LÊN XE của Kachi 1.47.
#
# VÌ SAO KHÔNG `source ../common.sh`: tệp đó `set -euo pipefail` (một lệnh dò trả khác 0 là chết cả
# phiên — đúng thứ phải tránh khi đang đo trên xe) và `require_candidate` buộc phải có
# `docs/_handoff/vehicle-candidate.json` của dây chuyền ClusterNav cũ. Bộ này chỉ mượn Ý TƯỞNG
# (chọn thiết bị tường minh · thư mục bằng chứng · không hardcode serial), không mượn ràng buộc.
#
# LUẬT (CLAUDE.md §4): mọi lệnh ĐỔI STATE hệ thống phải đi qua `k_confirm` và phải khai lệnh hoàn
# tác ngay bên cạnh. Lệnh chỉ ĐỌC thì chạy thẳng.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
set -uo pipefail

KACHI_PKG="${KACHI_PKG:-com.byd.launcher}"
KACHI_HOME_COMP="${KACHI_HOME_COMP:-$KACHI_PKG/com.byd.clusternav.launcher.KachiHomeActivity}"
ADB="${ADB:-adb}"

k_root() { cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd; }

# ── in ấn ────────────────────────────────────────────────────────────────────────────────────────
k_hr()   { printf '%s\n' "──────────────────────────────────────────────────────────────"; }
k_say()  { printf '  %s\n' "$*"; }
k_ok()   { printf '  [OK]   %s\n' "$*"; }
k_bad()  { printf '  [FAIL] %s\n' "$*"; }
k_warn() { printf '  [WARN] %s\n' "$*"; }
k_todo() { printf '  [TAY]  %s\n' "$*"; }   # việc phải làm bằng tay trên xe
k_unk()  { printf '  [CHƯA BIẾT] %s\n' "$*"; }

# ── thiết bị: KHÔNG hardcode IP/serial ───────────────────────────────────────────────────────────
# Thứ tự: tham số 1 → $KACHI_TARGET → $ADB_SERIAL → thiết bị adb DUY NHẤT đang nối.
k_target() {
  local want="${1:-${KACHI_TARGET:-${ADB_SERIAL:-}}}"
  if [ -n "$want" ]; then KACHI_TARGET="$want"; export KACHI_TARGET; printf '%s\n' "$want"; return 0; fi
  local devs count
  devs="$("$ADB" devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}')"
  count="$(printf '%s\n' "$devs" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [ "$count" = "1" ]; then
    KACHI_TARGET="$(printf '%s\n' "$devs" | sed '/^$/d')"; export KACHI_TARGET
    printf '%s\n' "$KACHI_TARGET"; return 0
  fi
  echo "ERROR: chưa xác định được xe. Truyền tham số 1 (vd <ip-xe>:5555) hoặc đặt KACHI_TARGET=" >&2
  return 4
}

k_adb() { if [ -n "${KACHI_TARGET:-}" ]; then "$ADB" -s "$KACHI_TARGET" "$@"; else "$ADB" "$@"; fi; }
k_sh()  { k_adb shell "$@"; }

# ── thư mục bằng chứng ───────────────────────────────────────────────────────────────────────────
# Dùng lại thư mục của phiên đang chạy nếu có ($KACHI_OUT, hoặc carlog-kachi-* mới nhất < 8 giờ),
# để 8 script rời vẫn đổ vào MỘT chỗ khi chạy tay từng cái.
k_out() {
  if [ -n "${KACHI_OUT:-}" ]; then mkdir -p "$KACHI_OUT"; printf '%s\n' "$KACHI_OUT"; return 0; fi
  local root recent
  root="$(k_root)"
  recent="$(find "$root/docs/diagnostics" -maxdepth 1 -type d -name 'carlog-kachi-*' -mmin -480 2>/dev/null | sort | tail -1)"
  if [ -n "$recent" ]; then KACHI_OUT="$recent"; else
    KACHI_OUT="$root/docs/diagnostics/carlog-kachi-$(date +%Y%m%d-%H%M)"
  fi
  export KACHI_OUT
  mkdir -p "$KACHI_OUT"
  printf '%s\n' "$KACHI_OUT"
}

# ── chụp một lệnh ĐỌC vào tệp ────────────────────────────────────────────────────────────────────
# k_cap <tên-tệp> <lệnh shell trên xe...>   — luôn `|| true`, một lệnh hụt không được giết phiên đo.
k_cap() {
  local name="$1"; shift
  local out; out="$(k_out)"
  {
    printf '# $ adb shell %s\n' "$*"
    printf '# %s\n\n' "$(date +%FT%T%z)"
    k_sh "$@" 2>&1 || true
  } > "$out/$name"
  k_say "→ $name  ($(wc -l < "$out/$name" | tr -d ' ') dòng)"
}

# ── ghi một dòng vào sổ phiên ────────────────────────────────────────────────────────────────────
k_note() { local out; out="$(k_out)"; printf '%s  %s\n' "$(date +%FT%T%z)" "$*" >> "$out/session-notes.txt"; }

# ── cổng xác nhận cho MỌI lệnh đổi state (CLAUDE.md §4) ──────────────────────────────────────────
# k_confirm "<việc sắp làm>" "<lệnh/động tác HOÀN TÁC>"   → 0 = đồng ý, 1 = bỏ qua.
# KACHI_YES=1 chỉ bỏ qua hỏi cho việc ĐỌC/mở màn (tham số 3 = "read"); việc GHI lên xe (cài đè, đổi hồ sơ,
# tắt máy, chiếu cụm) LUÔN hỏi — scan bảo mật 2026-09-14 chỉ ra auto-yes từng phủ cả bước tắt máy vật lý (§4).
k_confirm() {
  local what="$1" undo="${2:-(không có — đừng chạy nếu chưa rõ)}" kind="${3:-write}"
  printf '\n  ⚠ ĐỔI STATE: %s\n     HOÀN TÁC : %s\n' "$what" "$undo"
  if [ "${KACHI_YES:-0}" = "1" ] && [ "$kind" = "read" ]; then k_say "(KACHI_YES=1 — bỏ qua hỏi, bước đọc)"; k_note "ĐỔI STATE (auto-yes, read): $what | hoàn tác: $undo"; return 0; fi
  local ans=""
  read -r -p "     Làm không? [y/N] " ans
  case "$ans" in
    y|Y|yes|YES) k_note "ĐỔI STATE: $what | hoàn tác: $undo"; return 0 ;;
    *) k_say "bỏ qua."; k_note "BỎ QUA: $what"; return 1 ;;
  esac
}

# ── chờ người thao tác trên xe ───────────────────────────────────────────────────────────────────
k_pause() { local _x=""; printf '\n'; read -r -p "  ⏸ $* → xong bấm Enter… " _x; }

# ── phiên bản Kachi ĐANG CÀI (CLAUDE.md §9: đọc từ máy, KHÔNG đoán) ──────────────────────────────
k_installed_version() {
  k_sh "dumpsys package $KACHI_PKG" 2>/dev/null \
    | sed -n 's/.*versionName=\([^ ]*\).*/\1/p' | head -1 | tr -d '\r'
}
k_installed_code() {
  k_sh "dumpsys package $KACHI_PKG" 2>/dev/null \
    | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r'
}

# ── VD cụm: đo, không đoán (cùng awk với scripts/on-car-verify.sh) ───────────────────────────────
k_find_vd() {
  k_sh "dumpsys display" 2>/dev/null | awk '
    tolower($0) ~ /fission|xdja/ {
      if (match($0,/displayId ([0-9]+)/)) { print substr($0,RSTART+10,RLENGTH-10); exit }
      if (match($0,/mDisplayId=([0-9]+)/)) { print substr($0,RSTART+11,RLENGTH-11); exit }
    }' | head -1 | tr -d '\r'
}

# ── logcat: cắt một lát theo tag, cho các bước đo hành vi ────────────────────────────────────────
# k_logcat_slice <tệp> <tag...>
k_logcat_slice() {
  local name="$1"; shift
  local out args t; out="$(k_out)"; args=""
  for t in "$@"; do args="$args $t:I"; done
  # shellcheck disable=SC2086
  k_adb logcat -d -v threadtime -s $args > "$out/$name" 2>&1 || true
  k_say "→ $name  ($(wc -l < "$out/$name" | tr -d ' ') dòng)"
}
