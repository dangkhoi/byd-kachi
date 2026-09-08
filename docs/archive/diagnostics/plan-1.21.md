# ClusterNav 1.21 — Plan

> Ghi 2026-08-15. Nguồn: yêu cầu owner sau khi ship 1.20. Chưa implement.

## Item 1 — Headless auto-start (chạy NỀN, không bung MainActivity) · GIỮ auto-cast
**Goal:** boot → app làm việc (nav lên cụm · voice-key · auto-cast app đã cấu hình) mà **KHÔNG mở UI MainActivity** trên màn chính. Bonus: né luôn size-compat dudu (MainActivity không auto-foreground).

**Design (đã phân tích + trace code 2026-08-15):**
- **Bỏ `launchHome()`** trong `RebindReceiver` (BOOT_COMPLETED; cân nhắc cả MY_PACKAGE_REPLACED — có thể vẫn mở 1 lần sau OTA).
- **Dời accessibility grant + force-bind** (hiện `MainActivity.onCreate` L93/231) → **boot foreground-service** (dadb grant ~3–5s > ngân sách ~10s của receiver → foreground service an toàn hơn). **Đây là thứ DUY NHẤT thật sự phải dời.**
- **Nav pipeline: đã headless** — `NavNotificationListener.onListenerConnected → NavRepository.setPermission(GRANTED) → connect()`. Re-assert `setOutputEnabled(CLUSTER_LANE, true)` trong boot-service cho chắc (bù case pref cũ).
- **Auto-cast lên cụm: GIỮ NGUYÊN — đã headless sẵn** ✓ (yêu cầu owner). `FloatingBubbleService` là **sole autostart driver (R1, AtomicBoolean single-dispatch)**; `RebindReceiver.castBootWork()` đã start nó headless khi `castEnabled()`; `CastAutostart` (MainActivity) chỉ setup-UI, `CastAutomationService` là no-op. → KHÔNG cần MainActivity.
- **Toggle "Tự khởi động nền"** (mặc định ON) — giữ cả lựa chọn auto-open cũ (1.14 I5) nếu owner muốn.
- MainActivity chỉ mở khi **bấm icon** (settings).

**Verify on-car:** reboot mà KHÔNG mở app → nav lên cụm? giữ mic ra Kiki? auto-cast app đã set lên cụm? (đọc NavArrowLog + thử mic).
**Files:** `RebindReceiver.kt`, `MainActivity.kt` (relocate), boot foreground-service mới, toggle pref + UI, tests.

## Item 2 — Distance quantize (số lẻ 525m/725m) · [backlog #4] · ⬇️ LOW / OPTIONAL (owner review 2026-08-15)
**Status:** CHƯA trong 1.20 (`NavParse.kt` không đổi 1.19→1.20). 525m còn hiện = **expected**.
**Đánh giá lại (owner + xác nhận code):**
- Near-turn **<300m ĐÃ là bậc 10m** → countdown quan trọng lúc sắp rẽ đã **50-40-30-20-10** ✓ (không cần đụng).
- Số lẻ 25m **chỉ ở 300m–1km** (xa, chưa cần chính xác).
- ⇒ "làm tròn **lên** 50m" = **THÔ hơn** ở tầm xa chỉ để đẹp mắt → **không đáng** ("không nhiều ý nghĩa"). ❌ **BỎ hướng 50m.**
**Nếu vẫn muốn dọn số lẻ tầm xa (thuần thẩm mỹ):** chỉ nên đổi **hiển thị "0,x km"** ở ≥ ngưỡng (vd ≥500m) như GMaps — tròn + KHÔNG làm thô các bậc mét chỗ gần. KHÔNG đổi bậc <300m.
**Quyết định:** để **LOW/optional**. Mặc định **GIỮ 25m** (mịn hơn); chỉ làm km-display nếu owner thấy số lẻ khó chịu. **Tuyệt đối không coarsen sang 50m mét.**
**File (nếu làm km-display):** `core/.../navigation/NavParse.kt` (chỉ thêm nhánh format km ở tầm xa) + `NavParseTest.kt`.

## Item 3 — Carry-over (chờ on-car, không thuộc code 1.21 tối thiểu)
- Track B icon verify on-car (merge / **tunnel** / **roundabout-exit** — GMaps có expose không) → `docs/diagnostics/oncar-session-plan-2026-08-15.md`.
- Track A 4-mode probes (`0x4C10A018` + warm-restart + CAN 4/6) → `docs/diagnostics/oncar-runbook-4mode-track-a-probes-2026-08-14.md`.
- dudu size-compat ROOT (pending câu B / bisect trên xe anh em) → `docs/diagnostics/dudu-mainactivity-sizecompat-2026-08-14.md`.
- Deferred parity: `ROUNDABOUT.toHudIcon` 15→13, `CONTINUE` 11→12 (cần on-car confirm HUD glyph).

## Thứ tự đề xuất
1. Item 2 (distance quantize) — nhỏ, off-car, có thể làm ngay (chỉ 1 nhánh + test).
2. Item 1 (headless auto-start) — vừa, cần verify on-car.
3. Item 3 — theo phiên on-car.
