#!/usr/bin/env python3
"""Kiểm kê mọi lời gọi dp()/dpi() có SỐ TRẦN trong tầng vẽ launcher.

Mục đích: trước khi chốt thang khoảng cách, phải biết mỗi con số đang giữ
VAI TRÒ gì. Chọn bậc theo vai trò, không theo "gần nhất về số học".
Chạy: python3 scripts/spacing-inventory.py
"""
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

SRC = Path("app/src/main/java/com/byd/clusternav/launcher")

# dp(12) | dpi(context, 12) | dp(ctx, 12f) — chỉ bắt đối số CUỐI là số trần.
CALL = re.compile(r"\bdp(i?)\(\s*(?:[A-Za-z_][A-Za-z0-9_.]*\s*,\s*)?(\d+)f?\s*\)")

# Vai trò suy từ ngữ cảnh dòng. Thứ tự QUAN TRỌNG: mẫu hẹp phải xét trước.
ROLES = [
    ("bán kính góc",      r"cornerRadius|RADIUS|cornerRadii|radius"),
    ("nét viền",          r"setStroke|strokeWidth"),
    ("đích chạm / cỡ ô",  r"LayoutParams\(|minimumWidth|minimumHeight|minWidth|minHeight"),
    ("chiều cao hàng",    r"\bheight\b|rowHeight|barHeight|\.height ="),
    ("khe giữa thẻ",      r"setMargins|topMargin|bottomMargin|marginStart|marginEnd|leftMargin|rightMargin|gap"),
    ("lề trong thẻ",      r"setPadding|padding|setPaddingRelative"),
    ("khe icon–chữ",      r"compoundDrawablePadding|drawablePadding"),
]


def role_of(line: str) -> str:
    for name, pat in ROLES:
        if re.search(pat, line, re.I):
            return name
    return "khác / chưa rõ"


def main() -> int:
    if not SRC.is_dir():
        print(f"KHÔNG thấy {SRC} — chạy từ gốc repo", file=sys.stderr)
        return 2

    by_num = Counter()
    by_role = Counter()
    num_role = defaultdict(Counter)
    by_file = Counter()
    rows = []

    for path in sorted(SRC.glob("*.kt")):
        raw = path.read_text(encoding="utf-8")
        # Bỏ chú thích KHỐI (giữ số dòng) rồi bỏ chú thích cuối dòng: KDoc của KachiSpace có nhắc `dp(7)`,
        # `dp(30)`… như VÍ DỤ. Không bỏ thì bộ đếm báo "còn số trần" ở đúng tệp khai thang — số đo tự nói sai.
        code = re.sub(r"/\*.*?\*/", lambda m: re.sub(r"[^\n]", " ", m.group(0)), raw, flags=re.S)
        for lineno, line in enumerate(code.splitlines(), 1):
            line = line.split("//")[0]
            if not line.strip():
                continue
            for m in CALL.finditer(line):
                n = int(m.group(2))
                role = role_of(line)
                by_num[n] += 1
                by_role[role] += 1
                num_role[n][role] += 1
                by_file[path.name] += 1
                rows.append((path.name, lineno, n, role))

    print("=" * 72)
    print("BẢNG 1 — SỐ · TẦN SỐ · VAI TRÒ (nguồn để chốt thang)")
    print("=" * 72)
    print(f"{'dp':>5}  {'lần':>4}   vai trò (tần số)")
    print("-" * 72)
    for n in sorted(by_num):
        detail = ", ".join(f"{r} x{c}" for r, c in num_role[n].most_common())
        print(f"{n:>5}  {by_num[n]:>4}   {detail}")

    print()
    print("=" * 72)
    print("BẢNG 2 — TỔNG THEO VAI TRÒ")
    print("=" * 72)
    for r, c in by_role.most_common():
        nums = sorted({n for n in by_num if r in num_role[n]})
        print(f"{c:>4}  {r:<22} số đang dùng: {nums}")

    print()
    print("=" * 72)
    print("BẢNG 3 — THEO TỆP")
    print("=" * 72)
    for f, c in by_file.most_common():
        print(f"{c:>4}  {f}")

    print()
    print(f"TỔNG lời gọi số trần: {sum(by_num.values())}")
    print(f"Số phân biệt: {len(by_num)} → {sorted(by_num)}")
    print(f"Tệp có số trần: {len(by_file)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
