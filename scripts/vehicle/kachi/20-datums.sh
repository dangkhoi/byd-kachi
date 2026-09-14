#!/usr/bin/env bash
# 20-datums.sh — SINH BẢNG "phải đo giá trị thật" TỪ REGISTRY (không chép tay) + chuẩn bị chỗ điền.
#
# ⚠ SỰ THẬT PHẢI NÓI TRƯỚC (CLAUDE.md §2): giá trị datum đọc qua HAL bằng **reflection TRONG tiến
#   trình app** (BydHalGateway → BydHal). KHÔNG có đường shell nào đọc hộ — `HalGateway.settingGet`
#   hiện trả null, và adb không gọi được `android.hardware.bydauto.*`. Vậy nên:
#     • Script này SINH bảng (id · nhãn · tier · bindingKey · loại đường) từ :core, để không ai chép tay.
#     • GIÁ TRỊ THẬT đọc bằng MẮT trên màn Kachi (ô/bảng/bộ chọn) rồi điền vào cột trống.
#     • Có một đường dò thô tuỳ chọn qua navopen (app_process) — [CHƯA BIẾT] với id của launcher,
#       mặc định KHÔNG chạy, phải xác nhận vì nó ghi tệp vào /data/local/tmp của xe.
#
# DÙNG:  scripts/vehicle/kachi/20-datums.sh [<ip-xe>:5555]
set -uo pipefail
. "$(dirname "$0")/_common.sh"

OUT="$(k_out)"; ROOT="$(k_root)"
echo "carlog: $OUT"; k_hr

TEL="$ROOT/core/src/main/kotlin/com/byd/clusternav/launcher/TelemetryRegistry.kt"
CTL="$ROOT/core/src/main/kotlin/com/byd/clusternav/launcher/ControlRegistry.kt"
for f in "$TEL" "$CTL"; do [ -f "$f" ] || { k_bad "thiếu $f"; exit 2; }; done

echo "[1] Sinh bảng từ registry (:core) — nguồn sự thật DUY NHẤT"
python3 - "$TEL" "$CTL" "$OUT/20-datums.md" <<'PY'
import re, sys, datetime
tel_p, ctl_p, out_p = sys.argv[1], sys.argv[2], sys.argv[3]

def route(bk: str) -> str:
    """Cùng luật với HalBindingTable.routeOf (core/.../HalBindingTable.kt:181)."""
    if not bk.strip():
        return "None"
    if re.fullmatch(r"-?\d+", bk):
        return "Feature(id số)"
    if "." in bk and bk.index(".") > 0:
        pre = bk.split(".", 1)[0]
        if pre.startswith("BYDAuto"):
            return "NamedMethod"
        if pre in ("AudioManager", "AutoContainer"):
            return "Local"
        return "None"
    if re.fullmatch(r"[a-z][a-z0-9_]*", bk):
        return "Setting(trả null)"
    return "None"

tel_src = open(tel_p, encoding="utf-8").read()
tel = re.findall(
    r't\(\s*"([a-z0-9_]+)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\s*,'
    r'\s*(\w+)\s*,\s*(\w+)\s*,\s*(\w+)\s*,\s*"([^"]*)"', tel_src)

ctl_src = open(ctl_p, encoding="utf-8").read()
ctl = []
for b in re.split(r"ControlDef\(", ctl_src)[1:]:
    m = re.match(r'\s*"([a-z0-9_]+)"\s*,\s*"([^"]*)"', b)
    if not m:
        continue
    kind = re.search(r"ControlKind\.(\w+)", b)
    tier = re.search(r"tier\s*=\s*EvidenceTier\.(\w+)", b)
    bk = re.search(r'bindingKey\s*=\s*"([^"]*)"', b)
    ctl.append((m.group(1), m.group(2), kind.group(1) if kind else "?",
                tier.group(1) if tier else "?", bk.group(1) if bk else ""))

w = open(out_p, "w", encoding="utf-8")
w.write("# Bảng ĐO DATUM + NÚT trên xe — sinh bằng máy từ `:core`\n\n")
w.write("> Sinh %s bởi `scripts/vehicle/kachi/20-datums.sh`. KHÔNG sửa tay cột trái;\n"
        "> chỉ điền hai cột cuối (**Giá trị đo** / **Ghi chú**). Trống = CHƯA ĐO (không được suy).\n\n"
        % datetime.datetime.now().strftime("%Y-%m-%d %H:%M"))
w.write("Cách đọc giá trị: mở Kachi → đặt ô/bảng chứa datum đó (hoặc mở **bộ chọn ô** để thấy giá trị\n"
        "trực tiếp trên thẻ) → chụp màn → điền. `—` trên màn = HAL trả null ⇒ ghi `null`, KHÔNG bỏ trống.\n\n")

nc = [t for t in tel if t[6] == "NEEDS_CAR"]
w.write("## 0. Tóm tắt\n\n")
w.write("| | Số |\n|---|---|\n")
w.write("| Datum (telemetry) | %d |\n" % len(tel))
w.write("| — trong đó NEEDS_CAR (chưa từng đo trên xe) | %d |\n" % len(nc))
w.write("| — OVERDRIVE (id số, chưa xác nhận theo trim) | %d |\n" % len([t for t in tel if t[6] == "OVERDRIVE"]))
w.write("| — PROVEN (đã chạy thật) | %d |\n" % len([t for t in tel if t[6] == "PROVEN"]))
w.write("| Nút (control) | %d |\n" % len(ctl))
w.write("| — NEEDS_CAR | %d |\n\n" % len([c for c in ctl if c[3] == "NEEDS_CAR"]))

w.write("## 1. ƯU TIÊN 1 — %d datum NEEDS_CAR (đo trước, đây là danh sách nợ của W1)\n\n" % len(nc))
w.write("| id | Nhãn | Đơn vị | Nhóm | bindingKey | Đường | Giá trị đo | Ghi chú |\n")
w.write("|---|---|---|---|---|---|---|---|\n")
for i, l, le, u, d, s, ti, bk in nc:
    w.write("| `%s` | %s | %s | %s | `%s` | %s |  |  |\n" % (i, l, u or "—", d, bk, route(bk)))

w.write("\n## 2. Toàn bộ %d datum (theo nhóm)\n\n" % len(tel))
for dom in sorted({t[4] for t in tel}):
    w.write("### %s\n\n" % dom)
    w.write("| id | Nhãn | Đơn vị | Tier | bindingKey | Đường | Giá trị đo | Ghi chú |\n")
    w.write("|---|---|---|---|---|---|---|---|\n")
    for i, l, le, u, d, s, ti, bk in tel:
        if d == dom:
            w.write("| `%s` | %s | %s | %s | `%s` | %s |  |  |\n" % (i, l, u or "—", ti, bk, route(bk)))
    w.write("\n")

w.write("## 3. %d nút điều khiển — chỉ ĐỌC/quan sát ở buổi này\n\n" % len(ctl))
w.write("⚠ L-RE2: ba cặp dùng CHUNG feature-id mà nghĩa khác nhau (`cam`/`camera_view` 3001 ·\n"
        "`headl`/`headlight_mode` 1276153912 · `brightness_gear`/`hud_brightness` 1276174360).\n"
        "**KHÔNG tự chọn tham số** — tự nghĩ giá trị chính là nguyên nhân W2-P0. Chỉ ghi nhận cặp nào\n"
        "bấm ra hiệu ứng gì, nếu owner chấp nhận bấm thử.\n\n")
w.write("| id | Nhãn | Kiểu | Tier | bindingKey | Đường | Quan sát | Ghi chú |\n")
w.write("|---|---|---|---|---|---|---|---|\n")
for i, l, k, ti, bk in ctl:
    w.write("| `%s` | %s | %s | %s | `%s` | %s |  |  |\n" % (i, l, k, ti, bk, route(bk)))

dup = {}
for i, l, k, ti, bk in ctl:
    if re.fullmatch(r"-?\d+", bk):
        dup.setdefault(bk, []).append(i)
coll = {k: v for k, v in dup.items() if len(v) > 1}
w.write("\n## 4. feature-id bị DÙNG CHUNG (đo được từ registry, %d cặp)\n\n" % len(coll))
w.write("| feature-id | các nút dùng chung |\n|---|---|\n")
for k in sorted(coll):
    w.write("| `%s` | %s |\n" % (k, ", ".join("`%s`" % x for x in coll[k])))
w.close()
print("telemetry=%d needs_car=%d controls=%d collisions=%d" % (len(tel), len(nc), len(ctl), len(coll)))
PY
rc=$?
[ "$rc" = "0" ] && k_ok "→ 20-datums.md" || { k_bad "sinh bảng hụt (rc=$rc)"; exit 2; }

# ── 2. Chụp chỗ nào đọc được bằng shell THẬT ────────────────────────────────────────────────
k_hr; echo "[2] Thứ shell ĐỌC ĐƯỢC thật (không phải HAL, nhưng có ích để đối chiếu)"
TARGET="$(k_target "${1:-}")" || exit 4; export KACHI_TARGET="$TARGET"
k_cap "20-hal-services.txt" "service list | grep -iE 'byd|auto|cluster|hud|car'"
k_cap "20-hal-features.txt" "pm list features | grep -iE 'byd|auto|car'"
k_say "Dịch vụ BYDAuto có mặt ⇒ lớp HAL tồn tại; KHÔNG suy ra được từng feature-id đọc được hay không."

# ── 3. (TUỲ CHỌN · ĐỔI STATE) dò thô qua navopen ────────────────────────────────────────────
k_hr; echo "[3] Dò thô feature-id qua navopen (TUỲ CHỌN)"
JAR="${NAVOPEN_JAR:-}"
if [ -z "$JAR" ]; then
  k_say "bỏ qua — đặt NAVOPEN_JAR=<đường dẫn navopen-v*.jar> nếu muốn thử."
  k_unk "navopen getraw đã proven cho THANH GHI HUD (0x38B000xx), CHƯA proven cho id của launcher;"
  k_unk "nó còn cần biết id thuộc THIẾT BỊ nào — HalBindingTable đang chọn best-effort theo Domain."
elif [ ! -f "$JAR" ]; then
  k_bad "không thấy tệp $JAR"
else
  if k_confirm "đẩy $(basename "$JAR") vào /data/local/tmp/navopen.jar trên xe rồi chạy getraw" \
               "adb shell rm -f /data/local/tmp/navopen.jar"; then
    k_adb push "$JAR" /data/local/tmp/navopen.jar >/dev/null 2>&1 \
      && k_ok "đã đẩy" || k_bad "đẩy hụt"
    k_say "ví dụ đọc một thanh ghi (hex): "
    k_say "  adb -s $TARGET shell 'CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen getraw instr 38B00030'"
    k_say "Ghi từng lệnh + output vào $OUT/20-navopen-manual.txt rồi dọn bằng lệnh hoàn tác ở trên."
  fi
fi

# ── 4. Giá trị datum QUA CẦU KIỂM THỬ (nếu cầu trả) ────────────────────────────────────────
# [ĐO từ source] giá trị datum chỉ đọc được TRONG tiến trình app (`BydHalGateway`→`BydHal`, reflection)
# — KHÔNG có đường shell nào đọc hộ. Cầu kiểm thử chạy TRONG app nên nó là đường duy nhất lấy được số
# này bằng máy thay vì bằng mắt. Cầu im ⇒ vẫn phải đọc trên màn và điền tay (đó là đường gốc).
k_hr; echo "[4] Thử lấy giá trị datum qua cầu kiểm thử (lệnh state)"
if k_test state; then
  k_say "JSON ở $K_TEST_JSON — nếu nó có mục datum, chép thẳng vào cột 'Giá trị đo' của 20-datums.md"
  k_todo "Đối chiếu vài dòng với số HIỆN TRÊN MÀN: lệch ⇒ báo ngay, đó là lỗi tầng đọc chứ không phải HAL"
else
  k_todo 'Đọc giá trị bằng MẮT trên thẻ ô / bộ chọn ô rồi điền 20-datums.md (màn hiện — ⇒ ghi null)'
fi

k_hr
echo "XONG bước 2. Bảng cần điền tay: $OUT/20-datums.md"
k_note "20-datums: đã sinh bảng đo datum/control"
