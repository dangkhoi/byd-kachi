# Runbook on-car — Kachi 2.15 (vc116): Camera-theo-xi-nhan + Hey Kachi ASR

> **Trạng thái**: chờ side-load & test trên xe · **Ngày**: 2026-09-22 · **APK**: `apk/Kachi-2.15-release.apk`
> (sha256 `8524b2f9f9f8757526197614b8ac3b4897fca81546412e65ca797dc6c882c48d`, `com.byd.launcher`, KHÔNG debuggable).
> **KHÔNG OTA / KHÔNG FF main** — owner side-load thử trên xe trước.
>
> Hai tính năng MỚI, cả hai **mặc định**: camera TẮT, Hey Kachi engine = ASR. Runbook này chạy TỪNG test case
> để lấy kết quả PASS/FAIL — KHÔNG phải để dò/khám phá. Nền RE: `docs/diagnostics/camera-panorama-RE-2026-09-22.md`
> (§5 = 10 option camera) + `docs/specs/kachi-wake-no-train.html` (Hey Kachi).

## Chuẩn bị (1 lần)
1. Side-load: gỡ bản cũ nếu khác khoá ký, cài `Kachi-2.15-release.apk`. Đặt Kachi làm màn hình chính (Cài đặt › Màn hình chính) để overlay + poll xi-nhan chạy.
2. Bật log DIAG nếu cần đo CPU (Hệ thống › Nâng cao › log). Chuẩn bị `adb`/logcat lọc tag `KachiCamera` + `KachiWakeAsr`.

---

## PHẦN A — CAMERA THEO XI-NHAN

### A0. Bật tính năng
- Cài đặt › **Tiện nghi xe** › mục *"Camera theo xi-nhan"* › bật *"Bật camera khi xi-nhan (đang phát triển)"*.
- **PASS** nếu công tắc lưu (mở lại vẫn bật). **FAIL** nếu tắt lại.

### A1. Xi-nhan trái → overlay + camera bên trái
- Đỗ xe (an toàn), bật xi-nhan TRÁI. Quan sát: overlay nổi bên TRÁI màn Kachi + có video camera trái.
- Logcat `KachiCamera`: `xi-nhan LEFT → camera ... overlay LEFT` + `open(...) op=.. mode=.. out=..`.
- **PASS**: overlay hiện bên trái VÀ có hình camera. **PARTIAL**: overlay hiện (nền đen) nhưng KHÔNG có video ⇒ tín hiệu LVDS không đổ vào SurfaceView của app ⇒ chuyển **A5 (option B–J)**. **FAIL**: overlay không hiện.

### A2. Xi-nhan phải → overlay + camera bên phải (gương của A1)
- Bật xi-nhan PHẢI. Overlay nổi bên PHẢI + camera phải. Ghi PASS/PARTIAL/FAIL như A1.

### A3. Tắt xi-nhan → overlay đóng
- Tắt xi-nhan. Overlay biến mất trong ~1–2s (`KachiCamera: close`). **PASS/FAIL**.

### A4. Đèn khẩn (cả 2 xi-nhan) → KHÔNG mở camera
- Bật đèn khẩn cấp (hazard, cả 2 nháy). `CameraSignalPolicy.turnOf(true,true)=NONE` ⇒ không overlay. **PASS/FAIL**.

### A5. Nếu A1/A2 = PARTIAL (overlay đen, không video) — thử 10 option (RE §5)
Chạy lần lượt tới khi có video. Ghi option nào PASS.
- **A** (mặc định 2.15): `setPanoOutputState(view)` + SurfaceView `setZOrderOnTop(true)`. — kết quả A1/A2 ở trên.
- **B**: đổi `setZOrderOnTop` → `setZOrderMediaOverlay(true)` (sửa `CameraOverlayView`).
- **C**: gọi `setLVDSState(LVDS_PANORMA_RF_VIEW=1)` trước `setPanoOutputState` (bật `PanoramaHal.setLvds(1)`).
- **D**: `setDisplayMode(FULL_SCREEN=1)` thay `WIDGET=3` (xem tín hiệu có ra không, rồi thu nhỏ sau).
- **E**: truyền `holder.surface` cho HAL nếu ROM có `setSurface`/`setDisplaySurface` (RE nói kinex KHÔNG có — thử reflect).
- **F**: đăng ký `registerListener(AbsBYDAutoPanoramaListener)` rồi bật output (một số ROM cần listener mới stream).
- **G**: overlay trên **display cụm** (`onCluster=true`) — owner muốn tuỳ chọn chiếu lên cụm.
- **H**: `setPanoOperation(WORK_ON)` trước, chờ `getPanoWorkState`=ON rồi mới `setPanoOutputState`.
- **I**: whitelist `com.byd.launcher` vào `byd_float_app_list` + appops SYSTEM_ALERT_WINDOW (như bóng VietMap) — nếu overlay bị IVI chặn.
- **J**: nếu tất cả fail → dùng đúng cơ chế kinex: SurfaceView + filament layer, cần đọc thêm `../jadx-kinex M/e.java setSurfaceViewLayerBackground`.

### A6. Không ảnh hưởng lái xe
- Đang đi (tốc độ >0), bật xi-nhan: overlay hiện có che thông tin quan trọng không? Ghi nhận (owner quyết giữ/ẩn khi chạy).

---

## PHẦN B — HEY KACHI (engine ASR no-train)

### B0. Bật + chọn engine
- Cài đặt › **Giọng nói** › bật *"Hey Kachi"* (`switch wake`).
- Mục *"Cách nghe Hey Kachi: ASR (không cần train)"* — mặc định BẬT (ASR). **PASS** nếu lưu.

### B1. Nghe đúng "Hey Kachi" (ASR)
- Màn sáng, cabin yên. Nói **"Hey Kachi"** 10 lần (giọng bình thường). Đếm số lần Kachi mở phiên nghe (overlay *Đang nghe*).
- Logcat `KachiWakeAsr`: `WAKE khớp: "..."` mỗi lần trúng.
- **Ghi**: hit `__/10`. Nói thêm biến thể **"Kachi ơi"**, **"OK Kachi"** — ghi hit từng loại.
- **PASS** nếu hit ≥ 7/10 (tốt hơn KWS gigaspeech). Ghi con số thật.

### B2. False-accept khi nói chuyện thường
- Nói chuyện/nghe nhạc 2 phút KHÔNG nói "kachi". Đếm số lần Kachi tự mở nhầm.
- **PASS** nếu ≤ 1 lần/2 phút. Nhiều hơn ⇒ siết `WakeAsrMatcher` (bỏ bớt HEAD lỏng như `ga/co/cu`).

### B3. CPU khi giải mã cửa sổ (~2 lần/giây)
- Bật Hey Kachi ASR, để chạy nền 3 phút. Đo CPU tiến trình `:wake` (top/dumsys cpuinfo).
- **Ghi**: CPU% trung bình. **PASS** nếu < 15% một lõi (chấp nhận được cho nền). Cao hơn ⇒ tăng `DECODE_EVERY_MS` (500→800/1000) trong `VoiceWakeAsr`.

### B4. So với KWS
- Tắt ASR (chọn KWS ở mục engine), lặp B1. Ghi hit KWS `__/10`. So ASR vs KWS — engine nào nghe "kachi" tốt hơn thì để mặc định.

### B5. Sau khi wake → phiên lệnh chạy
- Wake trúng → nói "bật đèn đọc". Kachi có nhận lệnh không (đường proven). **PASS/FAIL**.

---

## Tổng kết điền sau khi test
| Mã | Kết quả | Ghi chú (số đo) |
|----|---------|-----------------|
| A0 |  |  |
| A1 |  | option nào PASS |
| A2 |  |  |
| A3 |  |  |
| A4 |  |  |
| A5 |  | option video: __ |
| B1 | hit __/10 |  |
| B2 | __ lần/2ph |  |
| B3 | CPU __% |  |
| B4 | KWS __/10 |  |
| B5 |  |  |
