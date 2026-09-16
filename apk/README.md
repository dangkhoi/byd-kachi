# apk/ — kênh OTA của Kachi

> **Trạng thái**: Current · **Cập nhật**: 2026-09-17 · **Mục đích**: Thư mục APK phát hành để app **tự cập nhật qua mạng (OTA)** xuống xe — cùng cơ chế ClusterNav 2.0 đã dùng.

**(VI)** App trên xe (`UpdateChecker`) hỏi GitHub Contents API thư mục này trên nhánh `main` của repo `dangkhoi/byd-kachi`,
tìm tệp **`Kachi-<ver>-release.apk`** có phiên bản lớn hơn bản đang cài, tải về rồi cài qua dadb loopback (`pm install -r`)
— không cần ADB/laptop, không cần bấm qua trình cài đặt hệ thống.

- Chỉ để **một** tệp mới nhất (bản cũ xoá đi cho repo nhẹ; lịch sử vẫn trong git).
- Tên tệp **đúng khuôn** `Kachi-<major.minor[.patch]>-release.apk` — tên có lát cắt của bộ thu T10 (`…-<slice>-<sourceId>-release.apk`)
  KHÔNG phải bản OTA và bị bỏ qua (`UpdateCheckerTest`).
- Ký bằng **khoá riêng của Kachi** (từ 1.41, L2 — `~/.kachi/kachi-release.keystore` + `keystore.properties` gitignored;
  fingerprint SHA-256 `92:57:49:9B:61:69:D7:AC:A2:F0:27:D7:0F:1F:D8:E1:B8:13:7A:B4:F2:F3:44:2F:00:B0:08:4A:26:BB:99:17`).
  Bản Kachi cài trước 1.41 (ký khoá cũ / debug) **không** cập nhật đè được — gỡ rồi cài tay một lần, sau đó OTA bình thường.
- **1.69 (70) — 2026-09-17** (`Kachi-1.69-release.apk`, 37,7 MB, sha256 `a3ad5ed7…4db1fe`, thay 1.66). Voice: hết vòng lặp
  "ừ/ừm" và mic chồng phiên (gốc của "YouTube không lướt được" + "nói xong 5–6 s mới chạy"), ngắt câu bằng Silero VAD
  (asset 0,64 MB trong APK) + cắt đuôi im lặng, số THẬT trước khi tăng/giảm (17/47 nút), tên app kiểu Việt
  ("gu gồ máp", "du túp", app lạ như ChatGPT tự sinh), chịu lỗi chính tả (cốp/cấp, đọc/độc, pin/bên…), hỏi lại
  "lọc bụi hay lọc ngay", xuất nhật ký voice (Cài đặt › Voice; bridge `voice_dump` cần `auto_confirm`), Piper đọc
  "Kachi" đúng. Vuốt trong ô app: dự phòng theo cử chỉ (daemon chạm bị SELinux chặn trên xe). Visual: bề mặt 3 tầng +
  tint lĩnh vực, icon hoa anh đào, bỏ vạch sáng đỉnh nút. Gỡ toàn bộ ADAS/an toàn + 19 mục owner đánh NO (còn 47 nút ·
  100 thông tin). Mô hình NGHE **không đổi** (zipformer-vi; giọng thật owner 28/30). **Gói giọng ĐỌC** Piper vẫn tải
  trong app một lần. Cấu hình cũ trỏ mã đã gỡ tự dọn khi mở app (log `KachiWorkspace [dọn ô]`). 🚗 chưa đo trên xe:
  độ trễ nói→chạy, vuốt trong ô, cốp/AC AUTO (spec S), ngưỡng VAD (`voice_endpoint_floor_cap` chỉnh qua bridge).
- [ĐO 2026-09-13 17:55] Lần ba: 1.43 → 1.44 cùng đường (deep-link `open_settings_group=system` → Kiểm tra cập nhật) — `versionName=1.44`, launcher resume.
- [ĐO 2026-09-13 17:04] Lần hai: 1.42 → 1.43 qua Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật — dialog "New version: v1.43" → cài → `versionName=1.43`, KachiHomeActivity resume.
- [ĐO 2026-09-13] Đã kiểm end-to-end trên máy ảo: 1.41 → 1.42 (thấy bản mới, tải, cài qua dadb, tự mở lại sau 5 s). Máy ảo cần
  `adb tcpip 5555` + `adb reverse tcp:5555 tcp:5555` để app nối được loopback; xe thật có adbd mạng sẵn.
- ⚠ **Từ 1.59 APK còn ~35 MB** ([ĐO] 53 MB ở 1.58 → 35 MB): V2 pha NGHE mang `libsherpa-onnx-jni.so` +
  `libonnxruntime.so`. Từ 1.59 **chỉ chở `arm64-v8a`** (bỏ `armeabi-v7a`, −18 MB) — mọi dump xe chỉ thấy lib
  arm64, đầu xe DiLink 3/4/5 (Android 10/12) đều SoC 64-bit. Nếu một đời DiLink 32-bit-only báo lỗi cài
  `INSTALL_FAILED_NO_MATCHING_ABIS` → thêm lại `armeabi-v7a` trong `app/build.gradle.kts` (xem chú thích ở đó).
- ⚠ **Mô hình nhận dạng KHÔNG nằm trong APK.** `zipformer-vi-2025-04-20` (sherpa-onnx, ~266 MB) tải riêng **một
  lần** vào `filesDir/sherpa/` qua *Cài đặt › Hệ thống & quyền › Nâng cao › Nhận dạng giọng nói (tại máy)*. Lý do:
  nhét vào APK thì **mỗi bản vá một dòng chữ** cũng bắt người dùng tải lại cả mô hình. Gói được ghim **sha256 +
  kích thước** (xem `VoiceModelManifest`), và có đường **gỡ** ngay cạnh nút tải.
- ⚠ **Cài tay báo "Fail in installation of desktop apps"**: xảy ra khi **CHÉP APK vào xe rồi TAP để cài** — trình
  cài GUI của ROM DiLink từ chối một APK tap-vào trở thành app **launcher/home (desktop)**. Cách sửa CHẮC: cài
  bằng **adb**, đừng tap → `adb install -r Kachi-<ver>-release.apk` (`pm install` bỏ qua cổng GUI này; đây cũng là
  đường OTA dùng). Xem `docs/diagnostics/oncar-bugs-2026-09-15.md`.
- ⚠ **(Trường hợp khác) signature mismatch**: nếu xe đang có bản Kachi ký **khoá khác** (bản trước 1.41 / debug),
  đè lên sẽ `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ⇒ `pm uninstall com.byd.launcher` một lần rồi cài lại.
- Build: `./gradlew :app:assembleRelease` (cần `keystore.properties` ở gốc repo; thiếu ⇒ build release fail có chủ ý),
  rồi `cp app/build/outputs/apk/release/app-release.apk apk/Kachi-<ver>-release.apk`, commit + push lên `main`.

**(EN)** The on-car app polls this folder (GitHub Contents API, branch `main`, repo `dangkhoi/byd-kachi`) for a newer
`Kachi-<ver>-release.apk`, downloads it and installs over the dadb loopback (`pm install -r`). Keep a single latest file,
signed with Kachi's own key (from 1.41). Builds signed with an older key must be uninstalled once before OTA works.
