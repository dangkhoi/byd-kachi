# RE — Camera theo xi-nhan + overlay (BYDAutoPanoramaDevice)

> **Trạng thái**: Historical — đường cuối là `docs/specs/camera-turn-signal-hal-socket.html`; khớp lại với dòng index · **Ngày**: 2026-09-22 · **Nguồn**: RE `../apk-ref/kinex.apk` (libapp.so strings + jadx) + HAL `../byd/jadx-tmap/sources/android/hardware/bydauto/panorama/BYDAutoPanoramaDevice.java`.
> **Mục đích**: cách kinex lấy camera + điều khiển, để Kachi làm: xi-nhan trái → camera trái → overlay bên trái màn (và phải), + voice, + option overlay lên cụm.

## 1. Kinex làm gì (RE libapp.so)
- Gọi HAL **`BYDAutoPanoramaDevice`** (họ `BYDAuto*Device`, reflection như AC/Light mình đã dùng).
- Tên tính năng: **"Blind spot camera"** (camera điểm mù) — bật theo bên lái, ẩn khi chạy nhanh.
- Flags [ĐO libapp.so]: `PANORAMA_WORK_ON/OFF` · `PANORAMA_OUTPUT_LEFT/RIGHT/FRONT/REAR/FRONT_LEFT/FRONT_RIGHT/REAR_LEFT/REAR_RIGHT/COMPOSE/OFF` · `PANORAMA_OUTPUT_SIGNAL_LVDS/CVBS` · `PANORAMA_RINGHT_CAMERA_SWITCH_ON/OFF` · `PANORAMA_ROTATION_VERTICAL/HORIZONTAL` · `BACK_LINE_MULTIMEDIA`.
- Render: **filament 3D** (`com.google.android.filament`) trên một **SurfaceView** đặt bằng `setSurfaceViewRect(x,y,w,h)` + `setSurfaceViewLayerBackground` (kinex `M/e.java`). Channel Flutter `camera#animate/move/onIdle`.
- Cũng đóng `libsherpa-onnx-jni.so` 22MB (dùng sherpa như Kachi — liên quan việc 1).

## 2. HAL API thật — `BYDAutoPanoramaDevice` (jadx-tmap, đầy đủ)
### Method điều khiển (set)
| Method | Vai |
|---|---|
| `setPanoOperation(int)` | bật/tắt hệ panorama (WORK_ON/OFF) |
| `setPanoOutputState(int)` | **CHỌN VIEW** — dùng hằng `APA_OUTPUT_STATE_*` (2D_FRONT_LEFT=1, 2D_FRONT_RIGHT=2, 2D_REAR_LEFT=3, 2D_REAR_RIGHT=4, LEFT_FRONT=13, RIGHT_FRONT=14…) |
| `setDisplayMode(int)` | DISPLAY_MODE_PANORAMA=0 / FULL_SCREEN=1 / WIDGET=3 / RF_REVERSE=4 / REVERSE=5 / 3D_PANORAMA=6 |
| `setLVDSState(int)` | tuyến video LVDS: PANORMA_RF_VIEW=1 / DRIVING_RECORDER_VIEW=2 / TOP_LIGHT_CAMERA_VIEW=3 |
| `setPanoParams(int,int,int[,int[,int]])` | tham số (nhiều overload) |
| `setRFCameraSwitchState(int)` · `setPanoRotation(int)` · `setPanoramaTransparence(int)` · `setPanoFocusState(int)` · `setAPAAvmMode(int)` |
### Method đọc (get) — cho trạng thái/overlay
`getPanoWorkState()` · `getPanoOutputState()` · `getPanoOutputSignal()` · `getRightCameraSwitchState()` · `getRFCameraSwitchState()` · `getDisplayMode()` · `getLVDSState()` · `getPanoAPAState()` · `getBackLineConfig()` · `getPanoramaOnlineState()`.
### Listener (sự kiện realtime)
`registerListener(AbsBYDAutoPanoramaListener)` — nghe đổi trạng thái (vd vào lùi/đỗ).

## 3. Cơ chế OVERLAY (điểm mấu chốt)
- **KHÔNG có `setSurface(Surface)`** trong HAL ⇒ video KHÔNG hand cho app dạng stream đọc-được. Nó đi qua **LVDS** (tuyến video phần cứng) và được SoC **compose lên một hardware layer**.
- Kinex hiện camera trong app bằng: một **SurfaceView** (hardware layer) đặt tại rect mong muốn (`setSurfaceViewRect`), rồi bật panorama output → **tín hiệu LVDS đổ vào layer của SurfaceView đó**. `setSurfaceViewLayerBackground` = đặt layer camera làm nền của SurfaceView.
- ⇒ Overlay của Kachi = một **SurfaceView `TYPE_APPLICATION_OVERLAY`** đặt bên trái/phải, gọi `setPanoOutputState(LEFT/RIGHT)` + `setDisplayMode(WIDGET)` + `setLVDSState` để tín hiệu camera đổ vào đúng vùng đó.

## 4. Xi-nhan trigger
- Datum có sẵn: `light_left_turn`/`light_right_turn` (`BYDAutoLightDevice.getLightStatus`) — đã đọc được trong `CarStatus.lights.leftTurn/rightTurn`.
- Hoặc `registerListener` của Light device (realtime, không poll).

## 5. Options để LÊN XE TEST (5-10 — runbook)
1. **A — setPanoOutputState + WIDGET + SurfaceView overlay trái/phải** (đường chính, giống kinex).
2. **B — setLVDSState(PANORMA_RF_VIEW) + SurfaceView** (nếu A không đổ tín hiệu vào layer app).
3. **C — setDisplayMode(FULL_SCREEN) trên display phụ/cụm** (overlay lên cụm — owner muốn option này).
4. **D — setAPAAvmMode(ENTER) + output state** (đường APA/AVM).
5. **E — chỉ getPanoOutputState + hiện panorama hệ thống** (fallback: bật cam hệ thống, không overlay app — kém nhất).
6. **F — registerListener bắt sự kiện panorama rồi map** (nếu set không ăn, đọc trạng thái).
7. **G — SurfaceView setZOrderOnTop(true) vs media-overlay** — thử 2 cách z-order để layer camera hiện.
8. **H — vị trí overlay: trái/phải/tuỳ-bên-xi-nhan** (option Setting).
9. **I — overlay lên MÀN CỤM** (display phụ) thay màn chính.
10. **J — CVBS vs LVDS signal** (`PANORAMA_OUTPUT_SIGNAL_*`).

Mỗi option: gọi HAL qua `BydHalGateway.callNamedInt`, đặt SurfaceView, xem camera có hiện trong vùng không (PASS/FAIL). Method signature ĐÃ có (mục 2) ⇒ off-car dựng đủ; xe chỉ để xem tín hiệu có đổ vào layer app không.

## 6. Điều KHÔNG chắc (chỉ xe trả lời)
- LVDS layer có đổ vào SurfaceView của **untrusted_app** không (hay chỉ vào layer hệ thống). ⇒ option A–J để thử.
- `setPanoOutputState` có cần đang ở chế độ đỗ/lùi không (kinex ẩn khi chạy nhanh — có thể là ràng buộc firmware).
