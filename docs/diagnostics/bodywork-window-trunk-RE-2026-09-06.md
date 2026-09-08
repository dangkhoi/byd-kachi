# RE điều khiển CỬA SỔ / CỐP (thân xe) — feature MỚI, implement OFF-CAR (2026-09-06)

> **Trạng thái**: RE NOTED (owner yêu cầu trên xe "lấy thông tin mở cửa sổ / mở 50% / mở cốp để implement off-car sau"). Nguồn: hằng + signature từ OpenBYD `BYDAutoBodyworkDevice.java` + `BYDAutoFeatureIds.java` (`/tmp/openbyd-jadx`) + app OEM `com.byd.carsettings` (dùng `BYDAutoBodyworkDevice.getInstance(ctx)`). CHƯA implement, CHƯA test-fire (ROM khóa chặn shell app_process — xem note seat/vietmap).

## Device
`android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice` = **`BydHal.BODYWORK`** (ClusterNav ĐÃ có FQN const). Lấy: `BYDAutoBodyworkDevice.getInstance(ctx)`. Ghi qua method-TÊN (như PM2.5/ghế-đúng), KHÔNG raw feature-id.

## CỬA SỔ
- **1 cửa:** `setBodyWindowCtrlState(int window, int state)` → rc.
  - `window` (BODYWORK_CMD_WINDOW_*): **1=trái-trước · 2=phải-trước · 3=trái-sau · 4=phải-sau**.
  - `state` (BODYWORK_STATE_*): **0=đóng (CLOSED) · 1=mở (OPEN)**.
- **Cả 4 cửa:** `setAllWindowState(int lf, int rf, int lr, int rr)` (mỗi arg 0/1).
- **Đọc:** `getWindowState(int window)` · `getWindowOpenPercent(int window)` (0–100) · `getWindowPermitState()` (được phép điều khiển không).
- **Mưa tự đóng:** `setRainCloseWindow(int)` (ON=1/OFF=2), `getRainCloseWindow()`, `FEATURE_RAIN_CLOSE_WINDOW`.

### ⚠ "Mở 50%" — CHƯA rõ, cần RE/test thêm
State cửa sổ trong HAL định nghĩa **nhị phân 0/1** (đóng/mở); `getWindowOpenPercent` chỉ ĐỌC %. CHƯA thấy đường SET % trực tiếp. Giả thuyết off-car (test trên xe khi implement):
1. Thử `setBodyWindowCtrlState(window, <0..100>)` — biết đâu arg2 nhận % (không chỉ 0/1).
2. Nếu chỉ 0/1: "mở 50%" = mở (state=1) rồi **hẹn giờ** đọc `getWindowOpenPercent` tới ~50 rồi gửi đóng (state=0) để dừng — kiểu bám %.
3. Tìm app OEM có nút "mở hé/ventilate" xem nó gọi gì (chưa thấy trong AC/CarSetting đã pull — có thể ở `com.byd.car.server`/carsettings.plugins).

## CỐP (hatch / tailgate)
- **Set:** `setHetchDoorStatus(int status)` (chú ý typo "Hetch" trong API) → rc.
  - Hằng: **`CLOSE_HETCH_DOOR = 2`** (đóng). MỞ ≈ **1** ([SUY] theo mẫu, cần xác nhận trên xe — có thể có hằng `OPEN_HETCH_DOOR`).
- **Đọc:** `getHatchDoorStatus()` · `getHatchDoorMotorFaultStatus()`.
- Đường thay thế: door cmd `BODYWORK_CMD_DOOR_LUGGAGE_DOOR = 6` nếu setHetchDoorStatus không ăn.

## Cửa/nắp khác (bonus, cùng device)
- Door state đọc: `getDoorState(int)`; door cmd: LF=1·RF=2·LR=3·RR=4·**HOOD=5**·**LUGGAGE=6**·**FUEL_TANK_CAP=7** (BODYWORK_CMD_DOOR_*).
- Sunroof: `getSunroofPosition/State/InitState` (RE thêm nếu làm cửa sổ trời).

## Việc off-car (khi implement)
1. Thêm helper `BydHal` gọi method-tên trên BODYWORK device: `callSet2Int(dev,"setBodyWindowCtrlState",window,state)` + `callSet1Int(dev,"setHetchDoorStatus",status)` (reflection, degrade-safe, log rc — như `Pm25FilterApplier.acInt`).
2. UI: nút Mở/Đóng từng cửa (hoặc tất cả) + nút mở cốp; "mở 50%" để sau khi xác nhận cơ chế %.
3. Kiểm `getWindowPermitState()` trước khi ghi (xe có thể cấm khi đang chạy / khóa trẻ em).
4. **AN TOÀN:** mở cửa/cốp khi đang chạy = rủi ro — cân nhắc gate theo tốc độ/số P; đây là quyết định owner.
5. Verify trên xe (ghi HAL = car-only; ROM chặn shell nên phải chạy trong app ClusterNav — uid app ghi được BODYWORK như OEM).
