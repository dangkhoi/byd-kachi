# 0001 — Chiến lược nguồn dẫn đường per-app

> **Trạng thái**: Accepted · **Ngày**: 2026-08-19 · **Mục đích**: Chốt cách lấy dữ liệu nav từ mỗi app (GMaps / VietMap / Waze) theo kênh riêng + kiến trúc adapter trung lập.

## Context

ClusterNav phải bơm dữ liệu dẫn đường (hướng rẽ, cự ly, tên đường, số lối ra vòng xuyến, làn) lên cụm/HUD, nhưng **mỗi app nav phơi dữ liệu qua một kênh khác nhau, với độ tin cậy khác nhau** — không có một kênh chung.

Bằng chứng (phiên 2026-08-17/18, `docs/specs/v2-accessibility-navsource-handoff.html` + "Ghi chú nguồn nav" trong `docs/PROJECT-BACKLOG.md`):

- **GMaps — notification (nền, luôn có):** đo thật trên emulator, extras chỉ gồm `android.title` (cự ly thô) · `android.subText` (ETA) · `android.text` (CHỈ tên đường) · `android.largeIcon` (bitmap mũi tên 72×72) · `contentView=null`. **KHÔNG** có field số-lối-ra, **KHÔNG** có làn → classifier đọc ra HƯỚNG (từ bitmap), không ra SỐ. Đủ cho maneuver + cự ly + đường; đang dùng (✅).
- **VietMap — 3 kênh:** widget (speed/limit/upcoming, chạy **NỀN**, ✅ đang dùng) + accessibility content-desc (turn/đường/ETA, **chỉ foreground**) + camera (chỉ trên map SurfaceView → cần screen-capture).
- **Waze — không có kênh data nền:** nav phải lấy qua **screen-capture mũi tên** (học OpenBYD `WazeArrowCaptureService`), hoặc WazeMod HLP-logcat (cần WazeMod cấu hình), hoặc ESP32 HUD.
- **OpenBYD 2.3 (RE)** đọc thẳng MÀN HÌNH qua `getWindowsOnAllDisplays()` (đọc cả khi app không focus, miễn đang vẽ), dùng **view-id riêng từng app** với 3 manager: `GoogleMapsManager` · `WazeManager` (+`WazeArrowCaptureService`) · `YandexManager` (có `:id/exit_number_text`). ⇒ đây là mô hình đã proven cho đa-app.

## Decision

Áp dụng **chiến lược nguồn nav per-app**: chuẩn hoá về một `NavReading` **trung lập** (`{maneuver, distanceMeters, roadName, exitNumber?, lane?, etaText?}`), với **một adapter cho mỗi app** (`GMapsAdapter` / `WazeAdapter` / `VietMapAdapter`) dispatch theo `packageName`. Chọn kênh tốt nhất khả dụng cho từng app:

- **GMaps → notification** làm nguồn chính (chạy nền, ổn định); mũi tên phân loại từ bitmap.
- **VietMap → widget** (nền, speed/limit/upcoming) + **accessibility** (khi foreground, giàu hơn: turn/đường/ETA).
- **Waze → screen-capture** (feature lớn B3) là con đường chính cho nav; chưa có kênh nền.
- **Accessibility/screen-read/widget = "booster"** khi app đang vẽ; **notification = fallback bền** khi app nav chạy nền hẳn.

Thêm app mới = thêm 1 adapter, **không đụng lõi**.

## Consequences

- **Được:** mở rộng đa-app không phải viết lại lõi; tái dùng encoder 1.30 (`Maneuver.toAmapIcon/toHudIcon`, đường CAN 25–34 cho số lối ra); privacy giữ nguyên (chỉ đọc màn của chính máy → đẩy lên cụm của chính máy, không gửi đi đâu).
- **Mất / trade-off:** view-id gãy khi app update → **bảo trì phản ứng** (lấy APK → emulator → dump view-id → sửa adapter). Accessibility **chỉ chạy khi app foreground/visible** → dữ liệu giàu của VietMap/Waze bị gate theo foreground; chạy nền thì rơi về notification (nghèo hơn).
- **Việc phát sinh:** Waze full-nav phụ thuộc feature screen-capture lớn (`PROJECT-BACKLOG.md` **B3**, 4 case, chưa làm — cần spec riêng trước). GMaps số-lối-ra cần verify contentDescription có "take the Nth exit" (T0 trong spec navsource).
- Kiến trúc `NavScreenSource + AdapterRegistry` mới **chưa implement** (spec ở trạng thái HANDOFF); ADR này chốt HƯỚNG để các phiên sau bám theo.

## Status

Accepted — hướng kiến trúc đã chốt (nguồn: RE OpenBYD 2.3 + đo emulator 2026-08-17). Phần screen-capture (Waze/VietMap camera) còn chờ spec B3. Đổi trạng thái nếu một kênh chứng minh bất khả thi trên xe (kèm bằng chứng + điều kiện mở khoá theo `trace-den-tan-cung.md`).

## Date

2026-08-19

---

**Tham chiếu:** `docs/specs/v2-accessibility-navsource-handoff.html` · `docs/specs/waze-vietmap-signal-revival.html` · `docs/specs/vietmap-widget-bridge.html` · `docs/PROJECT-BACKLOG.md` (A3/A4/A5, B3) · RE `~/Library/Caches/clusternav-re/openbyd-2.3/sources/com/sr/openbyd/services/{BydAccessibilityService,GoogleMapsManager,WazeManager,YandexManager}.java`.
