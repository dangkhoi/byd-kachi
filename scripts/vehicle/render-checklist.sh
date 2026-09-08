#!/usr/bin/env bash
# Vỏ mỏng gọi render_checklist.py. Trạng thái trên trang đọc từ verdicts.tsv, KHÔNG phải
# localStorage — ghi verdict xong (`carexec.sh verdict ...`) thì chạy lại script này, mở file HTML
# (đã ghi sẵn, không cần publish gì) là thấy trạng thái mới.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
python3 scripts/vehicle/render_checklist.py
