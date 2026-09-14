# Buổi test trên xe — 2026-09-14 (BYD DiLink3.0) · phát hiện DURABLE

> Kết nối: adb qua hotspot; adb daemon Mac báo "No route to host" (nghi quyền Mạng-cục-bộ macOS) ⇒ dùng cầu TCP
> `127.0.0.1:5556 → xe:5555`. Bằng chứng thô ở `carlog-kachi-20260914-2044/` (gitignored). Bản cài: 1.49→1.53→1.54.

## Xe
BYD AUTO **DiLink3.0**, Android **10** (QKQ1.210910.001 release-keys), **arm64-v8a** (+armeabi-v7a,armeabi), RAM 7.8 GB, 8 nhân.
Kachi uid **10135** (KHÔNG system), chữ ký **177b2fc5** (KHÔNG platform). Launcher gốc com.android.launcher3. Có mạng.

## App-vào-ô: CHẠY trên xe [ĐO]
VietMap chạy trong ô, YouTube phát MV trong ô, bảng ADAS ô khác — qua ActivityView của ROM, **không cần dadb**. Đây là điều D-emu/H1 nghi ngờ nhưng hoá ra host được. (Waze H1 vẫn cần đo riêng.)

## HAL đọc: CHẠY. HAL ghi: THEO TỪNG CHỨC NĂNG [ĐO]
- Đọc: pin 61–62%, ~344 km, PM2.5 in 75/out 92 lvl 2·3, radar 6 vùng =1, đèn, MCU=1, ngoài 31°C… (`hal-read-snapshot.txt`).
- Ghi CHẠY: `setBodyWindowCtrlState` (kính), `setAutoCleanAirState` (lọc bụi), `setSeatVentilatingState` (ghế), feature-id `1de0000c` (gió).
- Ghi HỎNG: feature **0x4f50003a** (đèn đọc) → "no permission to use the feature ... device 1004"; nhiệt độ AC → "xe không nhận lệnh".
- BYDAUTO_*_SET là **signature perm**; com.byd.carsettings (platform) giữ 48, Kachi giữ **0** — nhưng nhiều write vẫn lọt qua named-method.
- ⇒ Cần **grab-list**: từng control → route (named/feature-id) → device → nhận/từ chối. Công cụ: cầu `ctl` + `71-hal-sweep.sh` (đang dựng).
- RE off-car: `framework.jar` (29 MB) + `libbydauto.so`/`libbydautoservice.so` đã pull về scratchpad (BYDAutoFeatureIds + checkDeviceFeatures + device↔feature).

## Cast (chiếu cụm): REGRESSION — gốc đã tìm [ĐO]
- Cụm = **display 2** `fission_bg_xdjaVirtualSurface` (owner com.xdja.containerservice uid 1000, FLAG_OWN_CONTENT_ONLY).
- Launcher wire **SimpleCast** → `am start --display 1 --windowingMode 5` ⇒ (1) hardcode display 1 SAI (cụm=2); (2) shell uid 2000 bị **SecurityException Permission Denial launchDisplayId** khi đẩy activity lên VD của uid khác.
- Đường PROVEN (app cũ com.byd.clusternav2, owner xác nhận chạy) = **ClusterCast.kt** `service call AutoContainer` + ClusterProfile castSeq — CÒN trong repo, KHÔNG được wire. ⇒ X2 sửa: wire ClusterCast + dò display cụm động.
- Geometry/DPI + bóng VietMap KHÔNG hỏng riêng — gated `castOn/geometryTargets()`; sửa cast là hiện lại.

## Đặt Kachi làm màn hình chính [ĐO]
`cmd package set-home-activity com.byd.launcher/...KachiHomeActivity` từ shell ⇒ Success; bấm Home → Kachi lên. ROM **không** hiện hộp chọn HOME khi bấm nút Home ⇒ cần nút trong Cài đặt (S5 đang dựng) để app tự gọi lệnh qua dadb. [CHƯA BIẾT] sống qua reboot không (P7).

## Kênh dadb chưa mở (F4)
adbd nghe `:::5555`; nhưng lần mở đầu Kachi tự bung phiên dadb → hệ thống hiện `UsbDebuggingActivity`, rồi chính màn Kachi fullscreen **gỡ** cửa sổ đó (WindowManager removeWindow 20:49:16) ⇒ chưa ai bấm "Luôn cho phép" ⇒ kênh câm ⇒ tự-cấp-quyền/OTA/nút-set-home đều chờ. F4 đang sửa: hoãn dò tới khi vẽ xong + dải nhắc.

## Voice STT: mô hình nhỏ tại máy KHÔNG ĐỦ [ĐO]
Model vosk-small-vn tải xong, mic mở nguồn 6 (VOICE_RECOGNITION), mic xe là **mảng 4 kênh 16 kHz**. Owner nói cả câu ⇒ Vosk ra `"một"`. Gõ chữ thì chạy đúng. ⇒ đổi tầng nghe: nghiên cứu mô hình VN tốt hơn tại máy (PhoWhisper/wav2vec2/sherpa-onnx) hoặc Google SpeechRecognizer (không key). Owner: "đừng bỏ on-device sớm". Xem `docs/diagnostics/vn-stt-ondevice-research-2026-09-14.md`.

## Việc mai (test tiếp)
Chạy `71-hal-sweep.sh` lấy grab-list HAL; verify cast sau X2; nút set-home + công tắc boot; F4 mở kênh dadb (bấm Luôn cho phép); voice theo hướng đã chốt; reboot vật lý kiểm home + autostart; Waze H1; getevent phím vô-lăng W5.
