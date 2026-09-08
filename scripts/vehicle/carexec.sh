#!/usr/bin/env bash
# Vỏ mỏng gọi runner :car-integration. KHÔNG chứa logic thiết bị — mọi lệnh gửi xuống xe đều do
# CarExecCatalog trong :core khai báo. Vỏ này chỉ lo: tham số, thư mục evidence, và in ra cho dễ đọc.
#
# Vì sao cần vỏ: gõ "./gradlew --offline -q :car-integration:run --args=..." mười lần một phiên thì dễ sai.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@17}"
export JAVA_HOME
# Đường dẫn TUYỆT ĐỐI. 2026-07-27: bản tương đối bị Gradle giải theo thư mục module, nên `verdict`
# ghi vào car-integration/docs/... trong khi file git theo dõi nằm ở gốc — runner in "đã ghi" mà sổ
# thật vẫn rỗng. Một buổi ra xe suýt mất trắng vì thế.
LEDGER="${CAREXEC_LEDGER:-$ROOT/docs/refactor-car-execution/verdicts.tsv}"
HOST="${CAR_HOST:-}"

usage() {
  cat <<'EOF'
carexec.sh — chạy đánh giá capability trên xe, không cần APK

  CAR_HOST=<ip> scripts/vehicle/carexec.sh steps
  CAR_HOST=<ip> scripts/vehicle/carexec.sh observe
  CAR_HOST=<ip> scripts/vehicle/carexec.sh run <candidate> [--pkg P --comp C --display D ...]
                scripts/vehicle/carexec.sh verdict <candidate> <ok|fail> [--note "..."]
                scripts/vehicle/carexec.sh scenarios
                scripts/vehicle/carexec.sh scenario <id>
  CAR_HOST=<ip> scripts/vehicle/carexec.sh scenario <id> --run [--from N]

Ledger: $LEDGER (append-only)
CAR_HOST bắt buộc cho các lệnh cần nói với xe; lệnh chỉ đọc catalog thì không cần.
EOF
}

[[ $# -ge 1 ]] || { usage; exit 2; }
command="$1"; shift || true

needs_car=0
case "$command" in
  observe|run|scenario) needs_car=1 ;;
  steps|scenarios|plan|e2e|verdict) needs_car=0 ;;
  -h|--help|help) usage; exit 0 ;;
  *) echo "ERROR: lệnh không biết: $command" >&2; usage >&2; exit 2 ;;
esac
# Ba chế độ chạy được off-car dù lệnh gốc thuộc nhóm cần xe:
#   scenario <id>            — chỉ in kịch bản, không --run
#   observe --recorded       — quan sát trên fixture đã ghi
#   run <candidate> --dry-run — in lệnh sẽ gửi, không gửi
if [[ "$command" == "scenario" ]] && [[ " $* " != *" --run "* ]]; then needs_car=0; fi
if [[ " $* " == *" --recorded "* || " $* " == *" --dry-run "* ]]; then needs_car=0; fi

if [[ "$needs_car" -eq 1 && -z "$HOST" ]]; then
  echo "ERROR: cần CAR_HOST=<ip-head-unit> cho lệnh '$command'" >&2
  exit 2
fi

args="$command"
# Bọc dấu ngoặc cho tham số có khoảng trắng. 2026-07-27: bản trước nối "$*" thành một chuỗi rồi
# giao cho Gradle, mà Gradle cắt --args theo khoảng trắng — nên `--note "câu dài"` chỉ còn chữ đầu.
# Ghi chú bị cắt ÂM THẦM còn tệ hơn không có, vì sổ vẫn trông như đã ghi đủ.
for one in "$@"; do
  case "$one" in
    *" "*) args="$args \"$one\"" ;;
    *)     args="$args $one" ;;
  esac
done
[[ -n "$HOST" ]] && args="$args --host $HOST"
args="$args --ledger $LEDGER"

# Mọi lần chạy đều để lại vết. Một phiên trên xe mà không có bằng chứng thì lần sau phải làm lại từ đầu.
EVIDENCE_DIR="${CAREXEC_EVIDENCE:-oncar-carexec-$(date -u +%Y%m%d)}"
mkdir -p "$EVIDENCE_DIR"
STAMP="$(date -u +%H%M%S)"
LOG="$EVIDENCE_DIR/${STAMP}-${command}${1:+-$(echo "$1" | tr '/.' '__')}.log"

echo "→ $args"
echo "   lưu vào $LOG"
{
  echo "# $(date -u +%FT%TZ)  $args"
  ./gradlew --offline -q :car-integration:run --args="$args"
} 2>&1 | tee "$LOG"
