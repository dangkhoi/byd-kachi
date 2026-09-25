# Phiên 2026-09-25 — Wrap-up + Plan off-car

> Trên xe cả phiên (owner lái/đậu, báo + test; tôi cài qua adb_raw + capture). Nhánh `feat/voice-hotword-phrases` = `main` = origin. APK cuối: **2.53 (vc154)** OTA trên main. Full 5 module 0 fail.

## ĐÃ LÀM (verified on-car unless noted)

### Camera 360 theo xi-nhan — XONG (owner: "ngon lành")
- Đọc xi-nhan qua **HAL helper chạy uid shell** (`app_process` push qua dadb → register `AbsBYDAutoLightListener` → socket `127.0.0.1:19322` → `HalSignalClient` subscribe realtime). Mô hình kinex (RE `jadx-kinex`). Files: `hal-helper/KachiHalMain.java` (→ `assets/kachi_hal.jar` qua `scripts/build-hal-helper.sh`), `HalHelperLauncher.kt`, `HalSignalClient.kt`.
- Overlay: TextureView (bo góc + video composite), vuông 50% cao, góc trên (chính 14% / cụm 6%), lên cụm qua onCluster, vị trí từng bên `camera_pos_left/right`.
- Cam gương = **cameraId 1 (fisheye) + crop** trái x[0.25-0.35]/phải x[0.65-0.75] (owner: id 0/1 mở được, 2-5 không). Bỏ 10 options LVDS + picker + TurnSignalListener + poll.
- Fix: threading (socket→main post), tắt-xi-nhan-không-tắt-cam (FGS tick(false,false) để HOLD tự hết, né evt sticky).

### Bind key — TỰ-HEAL TRIỆT ĐỂ (verified: gỡ a11y → 40s tự re-bound)
- Gốc [ĐO xe]: a11y ENABLED nhưng KHÔNG BOUND; watchdog qua AlarmManager broadcast bị ROM DiLink **`ssc_skip` DROP** dù FGS chạy.
- Fix: **watchdog IN-PROCESS** trong `VoiceKeyKeepAliveService` FGS (Handler 30s → `isAccessibilityBound` → `grantAccessibility` idempotent). Không broadcast → ROM không drop. broadcast/alarm giữ làm lưới phụ.

### NEEDS_CAR sweep — 3 datum lên số [ĐO xe]
- `gear`=getCurrentGear=3 · `pm25_outside`=getPM2p5Value ô[1]=18 · `cabin_temp`=getTemprature(1)=24 (đổi từ feature-id chết).
- trip_km/trip_hours lên (getCurrentJourneyDriveMileage/Time). tyre_t_* + tyre_p_* lên (widget lốp OK — owner xác nhận; Feature route).

### UI fixes
- Widget/hình xe giật khi refresh: WorkspaceView chỉ requestLayout khi đổi CẤU TRÚC; TyreBoardView/RingView/DoorBoardView chỉ invalidate khi data đổi.
- Bỏ thanh trắng dưới header: OverlayHeads dùng `centered` (trong suốt) thay `strip`.

## PLAN OFF-CAR (tồn — ưu tiên giảm dần)

### 1. Hình xe NHÁY 1-2s/lần (owner báo cuối, "trace sau") — P1
[ĐO xe đậu] widget đứng yên (2 ảnh byte-identical) NHƯNG hình xe nhấp ~1-2s/lần. Nghi: data lốp đổi mỗi 1-2s (noise) → set() khác → invalidate → onDraw repaint hình xe (vị trí cố định nên không dịch, nhưng repaint = nhấp). **Fix triệt để**: tách hình xe thành **ImageView layer riêng** (vẽ 1 lần, KHÔNG repaint khi số đổi), số là TextView đè lên — bỏ Canvas onDraw cho phần xe. Trace trước: log onDraw fire + so readings qua 5 nhịp để xác nhận noise.

### 2. GỠ DATUM CHẾT khỏi giao diện + code — P2
[ĐO xe] không trả giá trị trên ROM/trim này: `batt_temp`, `tailgate_status`, `sunroof_pos`, `volt_12v_level`, `ev_mileage_km`, `trip_kwh`, `target_soc`. Gỡ khỏi: TelemetryRegistry + CarStatus + CarDataAdapter + TelemetryReadout + CapabilityIcons/Descriptions/Groups + widget/board + count test. **GIỮ**: gear, pm25_outside, cabin_temp, tyre_t_*, tyre_p_*.

### 3. TYRE WIDGET LAYOUT LỆCH — P2 (cosmetic)
[ĐO chụp]: (A) chữ 4 thẻ dán sát mép trên (chừa ~66px dưới); (B) nút ⇄ ở GIỮA panel thay vì góc phải; (C) ảnh xe nhô 65px lên khỏi khối thẻ; (D) gương xe khít 0px mép thẻ; (E) dải trống 153px đáy panel + dock trống 2 đầu. Sửa trong TyreBoardView.onDraw: căn giữa chữ theo thẻ, ⇄ về góc phải, căn ảnh xe theo khối.

### 4. Lệch khung khởi động (taskbar ROM) — P3 [CHƯA lặp lại phiên này]
Lần trước owner báo, thử relayout → giật → revert. Cần dumpsys window bounds lúc start vs sau Home để trace đúng.

## LƯU Ý HẠ TẦNG
- On-car: `python3 scripts/vehicle/kachi/adb_raw.py 172.20.10.8 5555 shell/push` (mạng chập chờn, wrap retry). Test mode bridge bật ~55-60 phút.
- HAL probe: `hal --es op get/getid` — nhưng đường generic get(id) KHÁC đường nhịp app (Feature route/EventValue): probe getid trả sentinel không có nghĩa app không đọc được (bài học tyre temp).
