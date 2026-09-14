#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────────────────────────
# _common.sh — nền chung cho bộ script LÊN XE của Kachi 1.53.
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

# ═════════════════════════════════════════════════════════════════════════════════════════════════
# TỰ ĐỘNG HOÁ BUỔI ĐO (thêm 2026-09-14 cho bộ 1.53) — owner: *"chuẩn bị toàn bộ script test
# automation trên xe thông qua adb, log liếc các loại"*.
#
# Năm thứ mọi bước từ nay đều phải có, để buổi test không phụ thuộc trí nhớ người chạy:
#   1. logcat NỀN chạy suốt bước                → k_log_start / k_log_stop   → logcat-<bước>.txt
#   2. ảnh màn sau MỖI thao tác                 → k_shot                     → shot-<bước>-<n>.png
#   3. dumpsys trước/sau + diff                 → k_state_snap / k_state_diff→ diff-<bước>.txt
#   4. thời gian từng lệnh (ms)                 → k_timed                    → session-notes.txt
#   5. KHÔNG `input tap` theo toạ độ            → k_test (cầu kiểm thử) + deep link
#
# ⚠ Vì sao CẤM `input tap <x> <y>`: cụm màn giữa của xe là **1920×720**, máy ảo dựng kịch bản là
#   1080×2340 — một toạ độ đúng ở nhà là một cú chạm nhầm hàng trên xe (CLAUDE.md §15 đã trả giá
#   bằng hơn chục vòng chụp-đoán-tap). Đường đúng: deep link `--es open_settings_group <nhóm>` cho
#   màn Cài đặt, và **cầu kiểm thử** [k_test] cho mọi thứ còn lại.
# ═════════════════════════════════════════════════════════════════════════════════════════════════

# ── thời gian mili-giây trên MÁY CHẠY SCRIPT (macOS `date` không có %N) ───────────────────────────
# perl trước python3: khởi động nhanh hơn ~25 ms, mà chính con số đó là sai số của phép đo.
k_now_ms() {
  if command -v perl >/dev/null 2>&1; then
    perl -MTime::HiRes=time -e 'printf "%d\n", time()*1000'
  elif command -v python3 >/dev/null 2>&1; then
    python3 -c 'import time; print(int(time.time()*1000))'
  else
    echo $(( $(date +%s) * 1000 ))
  fi
}

# k_timed "<nhãn>" <lệnh...>  — chạy lệnh, ghi "⏱ nhãn = N ms" vào session-notes.txt, giữ nguyên rc.
# Sai số ~10–30 ms (một lần gọi perl mỗi đầu). Đủ để so "1 giây hay 8 giây", KHÔNG đủ để so 20 ms.
k_timed() {
  local label="$1"; shift
  local t0 t1 rc
  t0="$(k_now_ms)"
  "$@"; rc=$?
  t1="$(k_now_ms)"
  k_note "⏱ $label = $((t1 - t0)) ms (rc=$rc)"
  printf '  ⏱ %-46s %6s ms\n' "$label" "$((t1 - t0))"
  return $rc
}

# ── logcat NỀN cho một bước ──────────────────────────────────────────────────────────────────────
# Bộ tag mặc định = mọi tag mà 1.53 thật sự ghi (đọc từ source, không đoán).
KACHI_LOG_TAGS="${KACHI_LOG_TAGS:-KachiVoiceSession KachiVoiceRec KachiVoiceEngine KachiVoiceMic \
KachiVoiceModel KachiVoiceWav KachiVoiceIntents KachiVoiceGeo KachiVd KachiTest ClusterNavReapply \
ClusterNavBridge ActivityTaskManager ActivityManager NavAccess VmOverlayPos SeatComfort Pm25Filter \
NavigationSpeedSign NavRepository Preflight CastLifecycle ClusterCastBubble KachiAutostart \
KachiAutostartSvc UpdateRelaunch}"

K_LOG_PID=""
K_LOG_STEP=""

# k_log_start <bước>   — mở `adb logcat -v time` NỀN, lọc theo KACHI_LOG_TAGS, ghi logcat-<bước>.txt.
# Gọi lại khi đang chạy ⇒ đóng cái cũ trước (không bao giờ để hai luồng ghi cùng một tệp).
k_log_start() {
  local step="$1" out args t
  out="$(k_out)"
  [ -n "$K_LOG_PID" ] && k_log_stop
  args=""
  for t in $KACHI_LOG_TAGS; do args="$args $t:V"; done
  # `-T 1` = chỉ lấy từ NGAY BÂY GIỜ trở đi: không nuốt lại đệm của bước trước (bước trước đã có tệp
  # riêng rồi, chép lại chỉ làm biên bản dài ra mà không thêm bằng chứng nào).
  # shellcheck disable=SC2086
  k_adb logcat -v time -T 1 -s $args "*:S" > "$out/logcat-$step.txt" 2>&1 &
  K_LOG_PID=$!
  K_LOG_STEP="$step"
  printf '%s\n' "$K_LOG_PID" > "$out/.logpid-$step"
  k_say "logcat nền → logcat-$step.txt (pid $K_LOG_PID)"
}

# k_log_stop — đóng luồng logcat nền. An toàn khi chưa mở. Luôn gọi được nhiều lần.
k_log_stop() {
  [ -n "$K_LOG_PID" ] || return 0
  kill "$K_LOG_PID" >/dev/null 2>&1 || true
  wait "$K_LOG_PID" 2>/dev/null || true
  local out; out="$(k_out)"
  [ -n "$K_LOG_STEP" ] && rm -f "$out/.logpid-$K_LOG_STEP"
  if [ -n "$K_LOG_STEP" ] && [ -f "$out/logcat-$K_LOG_STEP.txt" ]; then
    k_say "→ logcat-$K_LOG_STEP.txt ($(wc -l < "$out/logcat-$K_LOG_STEP.txt" | tr -d ' ') dòng)"
  fi
  K_LOG_PID=""; K_LOG_STEP=""
}

# ── ảnh màn sau MỖI thao tác ─────────────────────────────────────────────────────────────────────
# Số thứ tự giữ trong TỆP chứ không trong biến: 8 script rời cùng đổ vào một carlog, và chạy lại
# `ONLY="70"` không được ghi đè ảnh của lượt trước.
# k_shot <bước> [nhãn]
k_shot() {
  local step="$1" label="${2:-}" out n f
  out="$(k_out)"
  n="$(cat "$out/.shotn-$step" 2>/dev/null || echo 0)"
  n=$((n + 1)); printf '%s\n' "$n" > "$out/.shotn-$step"
  f="$out/shot-$step-$(printf '%02d' "$n").png"
  k_adb exec-out screencap -p > "$f" 2>/dev/null || true
  if [ -s "$f" ]; then
    k_say "→ $(basename "$f")${label:+  ($label)}"
    [ -n "$label" ] && k_note "ảnh $(basename "$f"): $label"
  else
    rm -f "$f"; k_warn "screencap hụt (ROM chặn? màn đang tắt?)"
  fi
}

# ── dumpsys TRƯỚC/SAU + diff ─────────────────────────────────────────────────────────────────────
# Chụp đúng 5 mặt cắt mà mọi bug của dự án từng hiện ra: stack · display (kể cả `kachi-slot-*`) ·
# activity · cửa sổ · quyền. Không chụp `dumpsys` đầy đủ: nó dài hàng chục nghìn dòng và diff của nó
# toàn nhiễu đồng hồ, tức là một tệp không ai đọc.
# k_state_snap <bước> <before|after>
k_state_snap() {
  local step="$1" when="$2" out; out="$(k_out)"
  {
    echo "== am stack list =="
    k_sh "am stack list" 2>&1
    echo; echo "== dumpsys display · dòng định danh =="
    k_sh "dumpsys display | grep -E 'mDisplayId|uniqueId|kachi-slot|mState|state=' " 2>&1
    echo; echo "== dumpsys window displays · Display + mCurrentFocus =="
    k_sh "dumpsys window displays | grep -E 'Display:|init=|mCurrentFocus|mFocusedApp|windowing'" 2>&1
    echo; echo "== dumpsys activity activities · dòng khung =="
    k_sh "dumpsys activity activities | grep -E 'Stack #|taskId=|mResumedActivity|displayId=|mBounds'" 2>&1
    echo; echo "== quyền của $KACHI_PKG =="
    k_sh "dumpsys package $KACHI_PKG | sed -n '/requested permissions/,/User 0/p'" 2>&1
  } > "$out/.state-$step-$when.txt" 2>&1
  k_say "chụp trạng thái [$when] của bước $step"
}

# k_state_diff <bước>  — so before/after, ghi diff-<bước>.txt. Không có before ⇒ nói ra, không đoán.
k_state_diff() {
  local step="$1" out b a; out="$(k_out)"
  b="$out/.state-$step-before.txt"; a="$out/.state-$step-after.txt"
  if [ ! -f "$b" ] || [ ! -f "$a" ]; then
    k_warn "thiếu ảnh trạng thái trước/sau của bước $step ⇒ không có diff (ghi CHƯA ĐO)"
    return 0
  fi
  {
    echo "# diff trạng thái hệ thống quanh bước $step — dòng '<' là TRƯỚC, dòng '>' là SAU"
    echo "# $(date +%FT%T%z)"
    echo "# Nhiễu đã biết: đồng hồ và mLastWakeTime đổi mỗi lượt đọc — đọc dòng có ý nghĩa, đừng đọc số lượng dòng."
    echo
    diff "$b" "$a" 2>&1 || true
  } > "$out/diff-$step.txt"
  local n; n="$(grep -c -E '^[<>]' "$out/diff-$step.txt" 2>/dev/null | tr -d ' ')"
  k_say "→ diff-$step.txt (${n:-0} dòng đổi)"
}

# ═════════════════════════════════════════════════════════════════════════════════════════════════
# CẦU KIỂM THỬ QUA ADB (`KachiTestBridge`) — thay cho `input tap` theo toạ độ
#
# Giao thức (broadcast, kết quả trả bằng `setResultData` + một tệp JSON):
#   adb shell am broadcast -a com.byd.launcher.TEST -p com.byd.launcher \
#       --es cmd <say|wav|listen|state|profiles|profile|preset|slot|slot_clear|open|prefs|reapply|diag> \
#       [--es text … | --es path … | --es name … | --es pkg …] [--ei n <số>] [--ez auto_confirm true]
#   ⇒ JSON mới nhất ở /sdcard/Android/data/com.byd.launcher/files/test/
#
# ⚠ Cầu CHỈ sống khi công tắc **Cài đặt › Hệ thống & quyền › Nâng cao › Chế độ kiểm thử qua adb**
#   đang bật, và **tự tắt sau 60 phút**. Đó là thiết kế đúng (một cửa thi hành lệnh mở vĩnh viễn trên
#   xe đang chạy là một lỗ), nên script phải chịu được ca "cầu không trả lời" mà không chết.
#
# ✅ [ĐO 2026-09-14] Cầu ĐÃ CÓ trong mã (spec `docs/specs/kachi-test-bridge.html`), chạy thật trên
#   máy ảo với bản 1.53/54: 13 lệnh + 6 ca lỗi. ⚠ Nhưng bản nằm ở `apk/` có thể vẫn là bản cũ hơn —
#   và **chưa ai đo trên xe** (shell của ROM BYD gửi vào receiver `exported` được không). Vì vậy mọi
#   chỗ gọi [k_test] ở bộ này GIỮ NGUYÊN **đường tay đi kèm**: cầu trả lời thì chạy thẳng, không trả
#   lời thì in đúng việc cần làm bằng tay. Không bước nào được *phụ thuộc* vào cầu.
# ═════════════════════════════════════════════════════════════════════════════════════════════════

KACHI_TEST_ACTION="${KACHI_TEST_ACTION:-com.byd.launcher.TEST}"

# Kết quả lượt gọi cầu gần nhất — chỗ gọi đọc hai biến này thay vì phân tích lại chuỗi.
K_TEST_DATA=""      # nội dung `setResultData` (một dòng)
K_TEST_JSON=""      # đường dẫn tệp JSON đã kéo về carlog, "" nếu không có
K_TEST_OK=0         # 1 = cầu có trả lời

# Bọc một tham số trong nháy đơn cho shell CỦA XE (adb shell nối các tham số bằng khoảng trắng rồi
# shell bên kia cắt lại — không bọc thì "bật đèn đọc" thành ba tham số).
k_shq() { printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"; }

# k_test <cmd> [tham số am...]  — gọi cầu, in resultData, kéo JSON mới nhất về carlog.
# Ví dụ:  k_test say --es text "bật đèn đọc"
#         k_test slot --ei n 2 --es pkg com.google.android.youtube
k_test() {
  local cmd="$1"; shift
  local out line res newest dest; out="$(k_out)"
  K_TEST_DATA=""; K_TEST_JSON=""; K_TEST_OK=0
  line="am broadcast -a $KACHI_TEST_ACTION -p $KACHI_PKG --es cmd $(k_shq "$cmd")"
  while [ $# -gt 0 ]; do line="$line $(k_shq "$1")"; shift; done
  res="$(k_sh "$line" 2>&1 | tr -d '\r')"
  printf '%s\n' "$res" >> "$out/test-bridge.log"
  K_TEST_DATA="$(printf '%s\n' "$res" | sed -n 's/.*data="\(.*\)".*/\1/p' | head -1)"
  if [ -n "$K_TEST_DATA" ]; then
    K_TEST_OK=1
    k_ok "cầu[$cmd] → $K_TEST_DATA"
  else
    k_warn "cầu[$cmd] không trả dữ liệu — công tắc 'Chế độ kiểm thử qua adb' đã bật chưa? (hoặc bản trên xe chưa có cầu)"
    printf '%s\n' "$res" | sed -n '1,3p' | sed 's/^/      /'
    return 1
  fi
  mkdir -p "$out/test"
  newest="$(k_sh "ls -t /sdcard/Android/data/$KACHI_PKG/files/test/ 2>/dev/null | head -1" 2>/dev/null | tr -d '\r')"
  if [ -n "$newest" ]; then
    dest="$out/test/$newest"
    if k_adb pull "/sdcard/Android/data/$KACHI_PKG/files/test/$newest" "$dest" >/dev/null 2>&1; then
      K_TEST_JSON="$dest"; k_say "   JSON → test/$newest"
    fi
  fi
  return 0
}

# k_test_alive — cầu có sống **VÀ cổng đang mở** không (một lượt `state` vô hại). 0 = dùng được.
#
# ⚠ KHÔNG chỉ hỏi "có trả dữ liệu không": công tắc TẮT thì cầu **vẫn trả** một dòng JSON
#   `{"ok":false,…,"error":"test_mode_off"}` (đó là thiết kế đúng — im lặng thì người đo không biết
#   vì sao). Bản đầu của hàm này chỉ đọc `K_TEST_DATA` nên nó báo "SỐNG" trong khi mọi lệnh sau đó
#   đều rơi ⇒ cả bước chạy sai mà không ai hiểu. Phải đọc `"ok":true`.
k_test_alive() {
  k_test state >/dev/null 2>&1 || return 1
  case "$K_TEST_DATA" in *'"ok":true'*) return 0 ;; *) return 1 ;; esac
}

# k_test_gate — in hướng dẫn bật cầu và thử lại MỘT lần. 0 = sống, 1 = phải làm tay cả bước.
k_test_gate() {
  if k_test_alive; then k_ok "cầu kiểm thử SỐNG — bước này chạy tự động"; return 0; fi
  k_todo "Bật: Kachi › Cài đặt › Hệ thống & quyền › Nâng cao › **Chế độ kiểm thử qua adb**"
  k_todo "(công tắc tự tắt sau 60 phút — bật lại nếu buổi test kéo dài)"
  k_pause "Bật xong thì Enter (bỏ qua nếu bản trên xe chưa có công tắc này)"
  if k_test_alive; then k_ok "cầu kiểm thử SỐNG"; return 0; fi
  k_warn "cầu vẫn im ⇒ bước này chạy TAY. Mọi việc cần làm được in bằng [TAY] dưới đây."
  k_note "cầu kiểm thử KHÔNG trả lời — bước chạy tay"
  return 1
}

# k_json <tệp> <khoá>  — rút một trường chuỗi/số ở mức 1 của JSON, không cần jq (máy owner có thể
# không có). Chuỗi có dấu tiếng Việt đọc được bình thường; mảng/đối tượng lồng thì KHÔNG đọc (trả "").
k_json() {
  [ -f "${1:-}" ] || { printf '\n'; return 0; }
  python3 - "$1" "$2" <<'PY' 2>/dev/null || printf '\n'
import json, sys
try:
    v = json.load(open(sys.argv[1], encoding="utf-8")).get(sys.argv[2], "")
except Exception:
    v = ""
print(v if isinstance(v, (str, int, float)) else "")
PY
}
