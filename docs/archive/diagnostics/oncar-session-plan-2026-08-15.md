# ON-CAR SESSION PLAN — 2026-08-15 · xe OWNER · bản 1.20

> Xe: BYD Seal DiLink 3.0 · Android 10 · parked-only (số P + phanh tay) · dọn = power-cycle nút nguồn.
> **HỎI LẠI IP** mỗi phiên: `export VEH=<vehicle-ip>:5555`. ĐỪNG đoán.
> **1.20 = 1.19 + Track B icons (merge→straight, off-ramp→slight, TUNNEL, roundabout-exit, bộ đầy đủ) + dudu manifest fix.**
> Nguyên tắc: KHÔNG assume — mỗi claim trace log/readback. Phối hợp: máy verify → agent tự adb; trực quan → hỏi owner 1 câu/chùm.
> ⚠️ Trước phiên: **bản 1.20 phải đã OTA/sideload lên xe** (build release + security-scan + push `apk/` lên main, HOẶC `./gradlew :app:assembleRelease` rồi sideload). Xem báo cáo phiên.

---

## S0 — Cài đặt + sanity (5 phút)
```bash
export VEH=<vehicle-ip>:5555; ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" connect "$VEH"; "$ADB" -s "$VEH" shell dumpsys package com.byd.clusternav | grep -E 'versionName|versionCode'   # kỳ vọng 1.20/120
```
- Mở app → **MainActivity full khung** không (manifest fix mới thêm landscape/resizeable/configChanges — xác nhận KHÔNG regress trên xe owner)? Bật **Nav+HUD** → cụm lên nav bình thường? (sanity, không phải test dudu — xe owner không có dudu.)
- ⚠️ **Voice-key sau reboot:** 1.20 **CHƯA** có code force-bind accessibility (mới fix live 14/8, chưa code). Nếu reboot → mic→Kiki có thể chết lại (enabled-but-not-bound) → phải toggle lại thủ công. **Hỏi owner có muốn implement force-bind vào 1.20 không** (off-car được).

---

## S0.5 — HEADLESS auto-start verify (1.21) ⭐ MỚI
- Đảm bảo toggle **"Tự khởi động nền"** = ON (mặc định) · **Nav+HUD** ON · (nếu dùng) **Cast** ON + app auto-cast đã set.
- **Power-cycle (nút nguồn, reboot thật)** → **KHÔNG mở app**, quan sát:
  - [ ] Cụm tự lên **nav** không? (pipeline headless qua listener)
  - [ ] Giữ **mic → ra Kiki** không? (accessibility force-bind headless — **điểm rủi ro chính**)
  - [ ] App **auto-cast lên cụm** không? (FloatingBubbleService headless)
  - [ ] MainActivity **KHÔNG tự bung** lên màn chính? (đúng mục tiêu headless)
- Nếu voice-key/nav KHÔNG lên headless → `adb logcat -s BootSetup NavAccess NavRebind` xem BootSetupService có chạy + grant không; thử **toggle OFF** (về launchHome cũ) để so sánh.

## S1 — VERIFY Track B icons (tính năng chính của 1.20) ⭐
Cách đọc ground-truth: `NavArrowLog` CSV — cột `small_amap,sig_name,sig_amap,verb_amap,heuristic_amap,final_icon`.
```bash
# sau khi lái qua các tình huống dưới, pull log:
"$ADB" -s "$VEH" pull /sdcard/Android/data/com.byd.clusternav/files/ ./navarrow-2026-08-15/
ls ./navarrow-2026-08-15/nav_arrow_log_*.csv
```
Lái/đi qua + đối chiếu:

| Tình huống | Kỳ vọng cụm | Kỳ vọng `final_icon` | Ghi chú |
|---|---|---|---|
| **Merge / nhập làn** (bug cũ) | mũi tên **THẲNG** (hết rẽ phải) | **9** (không phải 5) | Đây là fix chính owner báo |
| **Off-ramp** (nhánh rẽ ra) | **slight** (chếch nhẹ) | 4/5 (không phải 2/3 cua gắt) | |
| **Hầm (tunnel)** — sắp vào hầm | icon **hầm** | **16** | ⭐ **Câu quyết định: GMaps CÓ expose tunnel cho mình không?** Nếu `small_amap`/`sig_name` KHÔNG có tunnel → giới hạn nguồn (GMaps không đưa), không phải cụm. Nếu có mà cụm không vẽ → lỗi mình. |
| **Vòng xuyến có số lối ra** | vòng xuyến + **số exit N** | 11/12 + ROUNG_ABOUT_NUM → CAN 25..34 | Verify GMaps có đưa exit-num không |
| Rẽ trái/phải/thẳng thường | như cũ | 2/3/9 | regression guard — không được đổi |

**❓ Hỏi owner mỗi tình huống:** cụm vẽ đúng icon không? (đặc biệt: merge ra thẳng chưa; hầm có hiện không).
→ Nếu `final_icon` đúng mà cụm sai → boundary/HAL; nếu source (`small_amap`) trống → GMaps không expose.

---

## S2 — Track A 4-mode probes (thăm dò, không gấp)
> ✅ **ĐÃ CHẠY 2026-08-16** — kết quả đầy đủ ở `oncar-runbook-4mode-track-a-probes-2026-08-14.md` §"EXECUTED 2026-08-16". Tóm tắt: 4-mode switch live **KHÔNG khả thi** (no-root wall); phát hiện **naviState gate** (centre chỉ render khi nav session sống); content điều khiển 100%.
> Theo runbook có sẵn: **`docs/diagnostics/oncar-runbook-4mode-track-a-probes-2026-08-14.md`** — chạy tuần tự P0→P3 + P-ICON.
Tóm tắt ưu tiên:
- **P0** (read-only): `getraw setting 4C10E015` + `getraw instr 40C03032` ở CẢ 4 trạng thái layout → pin value↔layout.
- **P2** (ứng viên mạnh): `setraw instr 4C10A018 <0..4>` (INSTRUMENT_NAVI_TYPE_SET) — nếu đổi được layout live ⇒ **switch 4-mode thật**. Rồi `4C130041`, `4C10E020`.
- **P1**: replay warm-restart trigger (015=v + naviStatus + push NaviInfo) → nếu land centre ⇒ đường "centre đáng tin sau reboot".
- **P-ICON-A**: `setraw instr 43F01010 4` rồi `6` (2 khe CAN trống) — có phải glyph **merge/keep** không? Nếu có ⇒ target merge riêng (nâng cấp Track B).
- ⚠️ Sweep có thể loạn cụm → power-cycle khôi phục. An toàn: đỗ, số P.

---

## S3 — Dọn + báo lại
- Power-cycle nút nguồn trước khi rời.
- Báo lại checklist:
  - [ ] S0: 1.20 cài OK? MainActivity full khung? Nav+HUD lên cụm?
  - [ ] S1: merge→thẳng? off-ramp→slight? **hầm có hiện + GMaps có expose?** vòng xuyến có số lối ra?
  - [ ] S2: `4C10A018` có đổi layout không? warm-restart có land centre? CAN 4/6 là glyph gì?
- Kết quả S1 (đặc biệt tunnel/roundabout expose) → chốt Track B; S2 → chốt hướng 4-mode (viable lever hay chấp nhận giới hạn).

---

## Tham chiếu
- Track B design + bảng icon: `docs/diagnostics/re-maneuver-icon-tables-2026-08-14.md`
- Track A probe chi tiết: `docs/diagnostics/oncar-runbook-4mode-track-a-probes-2026-08-14.md`
- 4-mode cơ chế: `docs/diagnostics/re-4mode-amap-layout-mechanism-2026-08-14.md`
- dudu (xe anh em, riêng): `docs/diagnostics/dudu-mainactivity-sizecompat-2026-08-14.md`
