#!/usr/bin/env bash
# run-all.sh — chạy cả bộ THEO ĐÚNG THỨ TỰ RỦI RO TĂNG DẦN, dừng ở mọi chỗ cần tay.
#
#   00 nối + danh tính      (đọc)
#   10 baseline             (đọc; 1 lần mở màn Kachi có xác nhận)
#   20 datum/nút            (đọc + sinh bảng)
#   40 OTA                  (GHI: cài đè app)
#   30 hồ sơ                (GHI: đổi cấu hình xe qua applier; có nhánh reboot vật lý)
#   50 phím                 (đọc; có mở app camera)
#   60 chiếu cụm            (RỦI RO NHẤT: thao tác trên cụm trước mặt người lái)
#   90 gom                  (đọc)
#
# Vì sao OTA (40) TRƯỚC hồ sơ (30): phải test trên ĐÚNG bản định ship, và cài đè xong mới biết
# hồ sơ/cảnh có sống qua lượt nâng cấp không (migration chỉ chạy MỘT lần, đúng lượt đó).
# Vì sao cụm (60) CUỐI: một lần cụm kẹt là phải tắt máy xe — đừng để nó chặn các mục khác.
#
# DÙNG:  scripts/vehicle/kachi/run-all.sh [<ip-xe>:5555]
#        SKIP="40 60" scripts/vehicle/kachi/run-all.sh <ip>:5555     (bỏ bước)
#        ONLY="00 10" scripts/vehicle/kachi/run-all.sh <ip>:5555     (chỉ chạy vài bước)
set -uo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
. "$DIR/_common.sh"

TARGET="$(k_target "${1:-}")" || exit 4
export KACHI_TARGET="$TARGET"
OUT="$(k_out)"; export KACHI_OUT="$OUT"

cat <<BANNER
══════════════════════════════════════════════════════════════
 KACHI 1.47 — BỘ TEST LÊN XE
 xe     : $TARGET
 carlog : $OUT
 playbook: docs/diagnostics/oncar-playbook-kachi-1.47.md
══════════════════════════════════════════════════════════════
 AN TOÀN — đọc trước khi bấm tiếp:
  · Xe ĐỖ, số P, phanh tay. Không chạy bước nào khi xe đang lăn bánh.
  · Mọi bước GHI đều hỏi trước và in sẵn lệnh hoàn tác.
  · Cụm kẹt/mồ côi ⇒ hoàn tác cuối cùng là TẮT MÁY XE rồi nổ lại.
BANNER

STEPS="00-connect 10-baseline 20-datums 40-ota 30-profiles 50-keys 60-cast 90-collect"
SKIP="${SKIP:-}"; ONLY="${ONLY:-}"

run_step() {
  local s="$1" num="${1%%-*}"
  case " $SKIP " in *" $num "*) k_say "bỏ qua $s (SKIP)"; return 0 ;; esac
  if [ -n "$ONLY" ]; then case " $ONLY " in *" $num "*) : ;; *) return 0 ;; esac; fi
  echo
  k_hr
  printf ' ▶ %s\n' "$s"
  k_hr
  local ans=""
  read -r -p "  Chạy bước này? [Y/n/q] " ans
  case "$ans" in
    q|Q) echo "  dừng theo yêu cầu."; return 9 ;;
    n|N) k_say "bỏ qua $s"; k_note "BỎ QUA bước $s"; return 0 ;;
  esac
  bash "$DIR/$s.sh" "$TARGET"
  local rc=$?
  k_note "bước $s kết thúc rc=$rc"
  [ "$rc" = "0" ] || k_warn "$s trả rc=$rc — đọc kỹ phần in ở trên trước khi đi tiếp"
  return 0
}

for s in $STEPS; do
  run_step "$s" || { [ "$?" = "9" ] && break; }
done

echo
k_hr
echo "HẾT BỘ. Biên bản + bằng chứng: $OUT"
echo "Việc còn lại (tay): điền $OUT/README.md · $OUT/20-datums.md · $OUT/50-keys-table.md"
