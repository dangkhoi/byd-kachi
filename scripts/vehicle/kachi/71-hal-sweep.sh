#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 71-hal-sweep.sh — QUÉT TỪNG CONTROL HAL + GHI NHẬN (grab-list §9: id↔route↔device↔giá trị↔nhận/chặn)
#
# VÌ SAO CÓ BƯỚC NÀY (CLAUDE.md §2 · §14): [ĐO] 2026-09-14 chứng minh HAL write KHÔNG all-or-nothing —
#   named-method (setBodyWindowCtrlState/setAutoCleanAirState/setSeatVentilatingState) CHẠY; feature-id
#   có cái chạy (gió `1de0000c`) cái chặn (đèn đọc `0x4f50003a` device 1004 "no permission"); AC nhiệt độ
#   "xe không nhận lệnh". Phải đo TỪNG control rồi ghi lại — đó chính là grab-list §9.
#
# CÁCH ĐO (không đoán, không all-or-nothing): với mỗi control, bắn qua CẦU KIỂM THỬ lệnh `ctl` — cầu đi
#   qua ĐÚNG applier mà một cú chạm ô nút đi (`CarControlAdapter.actByKind`), rồi đọc kết quả THẬT của HAL
#   trong tiến trình (`HalWriteProbe`): route (named/feature/…), device, accepted, hal_line (rc / ngoại lệ).
#
# HAI PHA:
#   READ  — `state` (ảnh chụp an toàn) + sinh bảng id→kind từ `:core`. Giá trị 123 datum: xem 20-datums.sh.
#   WRITE — bắn từng control KHÔNG denylist: `ctl id on` → đọc accepted+hal_line → nếu TOGGLE thì `ctl id off`
#           khôi phục. Ghi CSV `carlog/hal-sweep.csv`.
#
# ⚠ DENYLIST (CLAUDE.md §4 phạm vi tường minh): control mở/khoá THÂN XE (lock/door/trunk/kính/nóc/rèm)
#   KHÔNG BAO GIỜ tự bắn — chỉ liệt kê để owner tự bấm bằng tay. Tập này = `CtlSafetyPolicy.CONFIRM_REQUIRED`
#   (`:core`), và `TestBridgeSafetyContractTest.script_sweep_co_denylist_khop_policy` canh cho khỏi lệch.
#
# DÙNG:  scripts/vehicle/kachi/71-hal-sweep.sh [<ip-xe>:5555]
#        AUTO=1   …   (không hỏi hiệu-quả-vật-lý sau mỗi control, chỉ ghi accepted+hal_line)
#        SKIP="readl fan"  …   (bỏ thêm vài control)
# ─────────────────────────────────────────────────────────────────────────────────────────────────
set -uo pipefail
. "$(dirname "$0")/_common.sh"

OUT="$(k_out)"; ROOT="$(k_root)"
TARGET="$(k_target "${1:-}")" || exit 4; export KACHI_TARGET="$TARGET"
CTLREG="$ROOT/core/src/main/kotlin/com/byd/clusternav/launcher/ControlRegistry.kt"
CSV="$OUT/hal-sweep.csv"
AUTO="${AUTO:-0}"
SKIP="${SKIP:-}"

# DENYLIST — PHẢI khớp CtlSafetyPolicy.CONFIRM_REQUIRED (core). Đổi một chỗ thì đổi cả hai (test canh).
DENYLIST="lock door trunk hood sunroof sunshade window windows_all win_lf win_rf win_lr win_rr"

[ -f "$CTLREG" ] || { k_bad "thiếu $CTLREG"; exit 2; }
echo "carlog: $OUT · xe: $TARGET"; k_hr
trap 'k_log_stop' EXIT

# ── A. Cổng chế độ kiểm thử ──────────────────────────────────────────────────────────────────
echo "[A] Cầu kiểm thử phải SỐNG (không có cầu ⇒ không bắn được control nào)"
k_log_start 71
k_say "bản đang cài: $(k_installed_version) ($(k_installed_code))"
if ! k_test_gate; then
  k_warn "cầu không sống ⇒ KHÔNG chạy sweep (không có đường tay cho việc bắn 60+ control)."
  k_todo "Bật 'Chế độ kiểm thử qua adb' rồi chạy lại bước này."
  k_log_stop; exit 0
fi

# ── B. Danh mục control từ :core (id · nhãn · kind) — KHÔNG chép tay (CLAUDE.md §7) ───────────
echo "[B] Đọc danh mục control từ registry (:core)"
CTLLIST="$OUT/.ctl-list.tsv"
python3 - "$CTLREG" > "$CTLLIST" <<'PY'
import re, sys
src = open(sys.argv[1], encoding="utf-8").read()
for b in re.split(r"ControlDef\(", src)[1:]:
    m = re.match(r'\s*"([a-z0-9_]+)"\s*,\s*"([^"]*)"', b)
    if not m:
        continue
    kind = re.search(r"ControlKind\.(\w+)", b)
    print("%s\t%s\t%s" % (m.group(1), m.group(2), kind.group(1) if kind else "?"))
PY
NCTL="$(wc -l < "$CTLLIST" | tr -d ' ')"
[ "${NCTL:-0}" -gt 0 ] || { k_bad "không đọc được control nào từ registry"; exit 2; }
k_ok "đọc $NCTL control từ ControlRegistry"

# ── C. Pha READ (an toàn) ────────────────────────────────────────────────────────────────────
k_hr; echo "[C] READ sweep — ảnh chụp an toàn (không đổi state)"
if k_test state; then
  k_say "state → $K_TEST_JSON (launcher + quyền + màn ảo; giá trị 123 datum: chạy 20-datums.sh)"
fi

# ── D. Pha WRITE có kiểm soát ────────────────────────────────────────────────────────────────
k_hr; echo "[D] WRITE sweep — bắn từng control KHÔNG denylist, ghi accepted + hal_line"
k_say "⚠ Mỗi control dưới đây ĐỔI STATE xe thật. TOGGLE sẽ tự bật rồi tắt lại để khôi phục."
k_say "⚠ 'Khôi phục' của TOGGLE là ĐẶT VỀ 0, KHÔNG phải về giá trị trước sweep: control nào vốn đang"
k_say "   BẬT (sưởi ghế · lọc bụi · đèn đọc…) sẽ bị TẮT sau lượt quét. Ghi lại trước khi chạy."
[ "$AUTO" = "1" ] && k_say "(AUTO=1 — chỉ ghi accepted+hal_line, KHÔNG hỏi hiệu quả vật lý)"

# CLAUDE.md §4 + KDoc `k_confirm` (_common.sh): MỌI lệnh đổi state hệ thống phải đi qua cổng xác nhận
# và phải khai lệnh hoàn tác ngay bên cạnh. Mọi bước GHI khác của bộ này (30-profiles · 40-ota ·
# 60-cast · 20-datums) đều có cổng; riêng vòng sweep này bắn 60+ control mà chỉ in một dòng cảnh báo,
# và với AUTO=1 thì không hỏi gì cả. Một cổng cho CẢ vòng (không hỏi lại từng control).
if ! k_confirm "bắn $NCTL control (trừ denylist) lên HAL xe thật — mỗi control đổi state một lần" \
               "TOGGLE tự đặt về 0 sau mỗi ca (xem cảnh báo trên); STEP/SELECT owner chỉnh tay; đường cuối: tắt máy xe rồi nổ lại"; then
  k_warn "bỏ qua WRITE sweep theo yêu cầu — chỉ còn pha READ ở trên."
  k_log_stop; exit 0
fi

# CSV: một dòng tiêu đề, rồi mỗi control một dòng. Trường bọc nháy kép, nháy trong nhân đôi.
csvq() { printf '"%s"' "$(printf '%s' "${1:-}" | sed 's/"/""/g')"; }
csv_row() {
  printf '%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "$(csvq "$1")" "$(csvq "$2")" "$(csvq "$3")" "$(csvq "$4")" \
    "$(csvq "$5")" "$(csvq "$6")" "$(csvq "$7")" "$(csvq "$8")" >> "$CSV"
}
echo 'id,label,route,device,value,accepted,hal_line,ghi_chu_owner' > "$CSV"

in_list() { case " $2 " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }

# Đọc trường JSON của lượt ctl gần nhất (cầu đã kéo tệp về $K_TEST_JSON).
ctl_field() { k_json "$K_TEST_JSON" "$1" | tr -d '\r'; }

FIRED=0; DENIED=0; SKIPPED=0
# Đọc HẾT danh mục vào mảng TRƯỚC khi vòng lặp — nếu đọc từ tệp qua stdin thì `adb shell` bên trong (k_test)
# nuốt luôn phần còn lại của stdin ⇒ vòng lặp chết sau control đầu. [ĐO] 2026-09-14 trên emulator.
CTL_ROWS=()
while IFS= read -r row; do [ -n "$row" ] && CTL_ROWS+=("$row"); done < "$CTLLIST"
for row in "${CTL_ROWS[@]}"; do
  IFS=$'\t' read -r id label kind <<< "$row"
  [ -n "$id" ] || continue
  if in_list "$id" "$DENYLIST"; then
    k_todo "DENYLIST — TỰ BẤM tay: $id ($label) [$kind] — mở/khoá thân xe, script không tự bắn"
    csv_row "$id" "$label" "(denylist)" "" "" "SKIP" "" "owner tự bấm — mở/khoá thân xe"
    DENIED=$((DENIED + 1)); continue
  fi
  if in_list "$id" "$SKIP"; then
    k_say "bỏ qua $id (SKIP)"; csv_row "$id" "$label" "" "" "" "SKIP" "" "SKIP env"
    SKIPPED=$((SKIPPED + 1)); continue
  fi

  # Giá trị bắn: TOGGLE/COVER/BUTTON = 1 (mặc định của cầu); STEP/SELECT để cầu tự chọn mặc định.
  k_say "→ ctl $id ($label) [$kind]"
  if [ "$kind" = "STEP" ] || [ "$kind" = "SELECT" ]; then
    k_test ctl --es id "$id" </dev/null >/dev/null 2>&1
  else
    k_test ctl --es id "$id" --ei v 1 </dev/null >/dev/null 2>&1
  fi
  ROUTE="$(ctl_field route)"; DEVICE="$(ctl_field device)"; VAL="$(ctl_field v)"
  ACC="$(ctl_field accepted)"; HAL="$(ctl_field hal_line)"; RPLY="$(ctl_field reply)"
  case "$ACC" in True|true) k_ok "   accepted · route=$ROUTE · hal=$HAL" ;;
    *) k_warn "   NOT accepted · route=$ROUTE · hal=$HAL ($RPLY)" ;; esac
  FIRED=$((FIRED + 1))

  # Khôi phục TOGGLE về tắt (COVER không denylist gần như không có; STEP/SELECT không rõ giá trị cũ ⇒ để owner).
  [ "$kind" = "TOGGLE" ] && k_test ctl --es id "$id" --ei v 0 </dev/null >/dev/null 2>&1

  # Hiệu quả vật lý (owner nhìn mắt) — trừ khi AUTO=1.
  NOTE="$RPLY"
  if [ "$AUTO" != "1" ]; then
    ans=""; read -r -p "     Có thấy XE phản ứng thật không? [y/N/s(kip)] " ans
    case "$ans" in y|Y) NOTE="vật lý: CÓ | $RPLY" ;; s|S) NOTE="vật lý: bỏ qua | $RPLY" ;;
      *) NOTE="vật lý: KHÔNG | $RPLY" ;; esac
  fi
  csv_row "$id" "$label" "$ROUTE" "$DEVICE" "$VAL" "$ACC" "$HAL" "$NOTE"
done

# ── E. Đóng bước ─────────────────────────────────────────────────────────────────────────────
k_hr
k_ok "hal-sweep.csv — bắn $FIRED control · $SKIPPED skip · xem cột accepted/hal_line"
k_say "CSV: $CSV"
k_say "⚠ Control denylist (mở/khoá thân xe) CHƯA đo tự động — bấm tay từng cái rồi điền cột ghi_chú."
k_say "⚠ Giá trị 'accepted' chỉ nói HAL NHẬN lệnh (rc hợp lệ) — hiệu quả vật lý là cột owner tự xác nhận."
k_log_stop
k_note "71-hal-sweep: xong (hal-sweep.csv · logcat-71.txt) — bắn $FIRED control, $DENIED denylist/skip"
echo "XONG bước 71. Grab-list §9 đang ở $CSV — cập nhật ControlRegistry.bindingKey/domain theo cột route/device."
