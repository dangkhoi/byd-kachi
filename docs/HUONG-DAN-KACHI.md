# Kachi launcher — Hướng dẫn sử dụng · User guide

> **Trạng thái**: Current · **Cập nhật**: 2026-09-25 (bản **2.65 (166)**, package `com.byd.launcher`) · **Mục đích**: Hướng dẫn dùng Kachi cho anh em (cài/OTA · màn hình chính · hồ sơ · ảnh · giọng nói · camera xi-nhan · automation · restart · lấy log). VI trước, EN sau. Tên mục lấy đúng từ Cài đặt trong app (`SettingsCatalogGroups.kt` / `strings_kachi.xml`).
> Đời ClusterNav 1.x xem `HUONG-DAN.md` (Historical). Kỹ thuật/phát triển: `PROJECT-BACKLOG.md`, `CLOSEOUT-2026-09-25.md`.

---

## (VI) Tiếng Việt

### 1. Cài lần đầu + cập nhật (OTA)
- **Cài lần đầu**: tải `apk/Kachi-2.67-release.apk` (nút Raw/Download trên GitHub `dangkhoi/byd-kachi`, nhánh `main`) → chép vào xe → cài bằng **adb** (`adb install -r Kachi-2.67-release.apk`). ⚠ Chép vào xe rồi **tap** để cài có thể bị ROM DiLink báo *"Fail in installation of desktop apps"* (app là launcher) — cài bằng adb thì qua.
- **Cập nhật về sau**: *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* — app tự tải bản mới từ `apk/` trên `main` rồi cài đè qua dadb loopback, không cần laptop. Công tắc *Tự động cập nhật* (cùng mục) = mở Kachi thì tự dò, có bản mới sẽ hỏi trước khi tải.
- Bản cài trước 1.41 (khoá ký cũ) phải gỡ (`pm uninstall com.byd.launcher`) rồi cài tay một lần.

### 2. Đặt Kachi làm màn hình chính
- *Cài đặt › Hệ thống & quyền › Màn hình chính › Đặt Kachi làm màn hình chính*. ROM BYD không hiện hộp chọn HOME nên Kachi tự đặt qua adb loopback; bấm Home trên màn → Kachi lên.
- Bỏ: cùng mục, nút **Bỏ chọn Kachi làm màn hình chính** → về launcher gốc, nổ máy lại vẫn giữ.

### 3. Hồ sơ tài xế
- *Cài đặt › Hồ sơ tài xế*: mỗi hồ sơ giữ riêng bố cục, nội dung ô, thanh nút, chip, hình nền, giao diện, đơn vị, ngôn ngữ (trừ nhóm Hệ thống & quyền, khung chiếu cụm, Giới thiệu).
- **Thêm hồ sơ…** (bản sao của hồ sơ đang dùng) · **Đổi tên hồ sơ** · **Xoá** (nút cạnh từng hồ sơ) · **Hồ sơ lúc nổ máy** = *Gần nhất* hoặc một hồ sơ cố định.
- **Xuất hồ sơ (backup)** → file trong `Android/data/com.byd.launcher/files/profiles/`. **Nhập hồ sơ từ file**: chép file vào cùng thư mục rồi bấm Nhập; trùng tên thì tự thành `<tên> 2`, `<tên> 3`… (không bị từ chối).
- Đổi nhanh: chạm chip hồ sơ ở thanh trên, hoặc nói *"chọn hồ sơ X"*.

### 4. Ảnh xe · hình nền · trình chiếu ảnh (3 thư mục)
- Thư mục trên thẻ (không cần quyền): `Android/data/com.byd.launcher/files/car/` (ảnh xe top-down) · `.../wallpapers/` (hình nền) · `.../photos/` (widget trình chiếu ảnh).
- Mỗi mục trong *Cài đặt › Màn hình chính* có nút **Sao chép đường dẫn thư mục** — dán vào trình quản lý tệp/adb push rồi chép ảnh vào. Ảnh xe mặc định là `seal-3` (không logo).

### 5. Giọng nói (*Cài đặt › Giọng nói*)
- Nghe **tại máy** (sherpa-onnx, mô hình tiếng Việt `zipformer-vi`): *Nhận dạng giọng nói (tại máy) › Tải mô hình tiếng Việt* — tải một lần (~266 MB), không gửi ra mạng. Xe không có mạng: chép cả cây thư mục gói vào `Android/data/com.byd.launcher/files/sherpa/import/<gói>/` rồi bấm Tải.
- **Giọng đọc offline** (Piper): tải gói trong cùng nhóm; tốc độ đọc chỉnh được.
- Gọi lệnh: ô *Nói với xe* / nút mic thanh trên / phím vô-lăng đã gán (*Cài đặt › Phím vô-lăng*). Overlay voice là cửa sổ **độc lập** — đang mở app khác toàn màn thì chỉ overlay lên, không kéo launcher lên (từ 2.62).
- **Hey Kachi** (thử nghiệm, mặc định tắt): *Cài đặt › Giọng nói › Hey Kachi* — nói câu gọi khi màn sáng để mở lệnh thoại. Cách nghe mặc định = ASR (không cần train); nghe nhầm nhiều lần thì tự tắt.
- Ví dụ lệnh: *"đặt nhiệt độ hai mươi hai"*, *"mở kính lái"*, *"dẫn đường tới … bằng vietmap"*, *"phát bài … trên youtube"*, *"bố cục 2 cột"*.

### 6. Camera theo xi-nhan (Seal · Sealion 6)
- *Cài đặt › Tiện nghi xe › Camera theo xi-nhan › Bật camera khi xi-nhan*: xi-nhan trái → camera trái nổi góc màn, phải → camera phải. Chọn camera từng bên (Sealion 6 chọn cam 0), vị trí từng bên, và **Hiện camera lên màn cụm**.
- **Xoay video** (*Cài đặt › Tiện nghi xe › Xoay video*): mặc định *Theo bên* (xi-nhan trái xoay ↺ 90°, phải xoay ↻ 90° — vì hình gương cắt từ camera 360 vốn nằm ngang). Nếu xe bạn hình vẫn ngang hay lộn đầu, chọn *Không xoay* / *↺ 90°* / *↻ 90°* / *180°*. Nếu **một bên đúng mà bên kia lộn đầu** thì chọn *Theo bên, ngược lại*.
- Đã kiểm trên xe Seal (2.48) và Sealion 6 (2.6x). Xe khác chưa đo.

### 7. Automation
- **Mưa thì tự bật sấy kính**: *Cài đặt › Tiện nghi xe › Tự sấy kính khi mưa* — đọc cảm biến mưa mỗi 5 phút, chọn sấy trước / sau + gương; hết mưa tự tắt (chỉ tắt cái Kachi bật). 🚗 chưa gặp buổi mưa thật để xác nhận.
- **Tự dẫn đường theo lịch**: *Cài đặt › Dẫn đường & cụm đồng hồ › Tự dẫn đường theo lịch* — khung giờ × thứ × "chỉ khi có GPS" × điểm đến trong **Sổ địa chỉ** × app dẫn đường; mỗi khung chạy 1 lần/ngày. Ví dụ 07:30–09:00 T2–T6 → công ty.

### 8. Khởi động lại launcher
- *Cài đặt › Hệ thống & quyền › Khởi động lại launcher* — khi launcher có lỗi hiển thị, không cần khởi động lại đầu xe.

### 9. Lấy log gửi về khi gặp lỗi
- Log ghi ra thẻ: `/sdcard/Android/data/com.byd.launcher/files/kachi-logs/` (`usage-*.log` suốt phiên · `snapshot-*.log` khi bấm *Chụp log ngay* · `captest-report.txt`).
- Lấy về: `adb pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ ./kachi-logs/` hoặc chép cả thư mục bằng trình quản lý tệp → gửi kèm **phiên bản** (Cài đặt › Giới thiệu) + mô tả lỗi + ảnh chụp màn.

---

## (EN) English

### 1. First install + OTA
- **First install**: download `apk/Kachi-2.67-release.apk` from GitHub `dangkhoi/byd-kachi` (`main`), copy to the car and install with **adb** (`adb install -r …`). Tapping the APK on the head unit may fail with *"Fail in installation of desktop apps"* (it is a launcher); adb bypasses that gate.
- **Updates**: *Settings › System & permissions › Check for update* — the app fetches the newer `apk/` build and installs it over the dadb loopback; no laptop. *Auto update* toggle = check on launch, ask before download.
- Builds installed before 1.41 (old signing key) must be uninstalled once.

### 2. Set Kachi as home
- *Settings › System & permissions › Home screen › Set Kachi as home* (the BYD ROM shows no HOME chooser, so Kachi sets it via adb loopback). *Unset* in the same place returns to the stock launcher and survives reboot.

### 3. Driver profiles
- *Settings › Driver profiles*: each profile keeps its own layout, slots, button bar, chips, wallpaper, theme, units, language. **Add** (copy of current) · **Rename** · **Delete** · **Boot profile** (last used or fixed).
- **Export (backup)** writes to `Android/data/com.byd.launcher/files/profiles/`; **Import from file** reads that folder; duplicate names become `<name> 2`, `<name> 3`.

### 4. Car image · wallpapers · photos
- Three folders on the SD card: `files/car/`, `files/wallpapers/`, `files/photos/`. Each row in *Settings › Home screen* has **Copy folder path**; drop images there.

### 5. Voice (*Settings › Voice*)
- On-device recognition (sherpa-onnx, Vietnamese `zipformer-vi`): download the model once (~266 MB) or side-load into `files/sherpa/import/<pack>/`. Offline reply voice (Piper) is a separate pack.
- Trigger: *Talk to the car* tile, mic button, or a bound steering-wheel key. The voice overlay is an independent window — it does not pull the launcher to the front (2.62+).
- **Hey Kachi** (experimental, default off): hands-free trigger while the screen is on; ASR engine by default; auto-disables after repeated false accepts.

### 6. Turn-signal camera (Seal · Sealion 6)
- *Settings › Car comfort › Turn-signal camera*: left signal → left camera overlay, right → right. Per-side camera id and position; optional cluster display. Verified on Seal (2.48) and Sealion 6 (2.6x) only.
- **Rotate video** (*Settings › Car comfort › Rotate video*): default *By side* (left signal ↺ 90°, right ↻ 90° — the mirror crop of the 360 camera is sideways). If the picture is still sideways or upside down, pick *No rotation* / *↺ 90°* / *↻ 90°* / *180°*. If one side looks right and the other is upside down, pick *By side, swapped*.

### 7. Automation
- **Rain → defrost** (*Car comfort*): polls the rain sensor every 5 min, turns on the chosen defrosters, turns off only what Kachi turned on. 🚗 not yet confirmed in real rain.
- **Scheduled navigation** (*Navigation & cluster*): time window × weekdays × GPS-only × saved place × nav app, once per window per day.

### 8. Restart launcher
- *Settings › System & permissions › Restart launcher*.

### 9. Logs
- `/sdcard/Android/data/com.byd.launcher/files/kachi-logs/` (`usage-*.log`, `snapshot-*.log`, `captest-report.txt`). Pull with `adb pull …/kachi-logs/ ./kachi-logs/` and send with the app version (*Settings › About*) and a screenshot.
