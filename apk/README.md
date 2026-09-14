# apk/ — kênh OTA của Kachi

> **Trạng thái**: Current · **Cập nhật**: 2026-09-13 · **Mục đích**: Thư mục APK phát hành để app **tự cập nhật qua mạng (OTA)** xuống xe — cùng cơ chế ClusterNav 2.0 đã dùng.

**(VI)** App trên xe (`UpdateChecker`) hỏi GitHub Contents API thư mục này trên nhánh `main` của repo `dangkhoi/byd-kachi`,
tìm tệp **`Kachi-<ver>-release.apk`** có phiên bản lớn hơn bản đang cài, tải về rồi cài qua dadb loopback (`pm install -r`)
— không cần ADB/laptop, không cần bấm qua trình cài đặt hệ thống.

- Chỉ để **một** tệp mới nhất (bản cũ xoá đi cho repo nhẹ; lịch sử vẫn trong git).
- Tên tệp **đúng khuôn** `Kachi-<major.minor[.patch]>-release.apk` — tên có lát cắt của bộ thu T10 (`…-<slice>-<sourceId>-release.apk`)
  KHÔNG phải bản OTA và bị bỏ qua (`UpdateCheckerTest`).
- Ký bằng **khoá riêng của Kachi** (từ 1.41, L2 — `~/.kachi/kachi-release.keystore` + `keystore.properties` gitignored;
  fingerprint SHA-256 `92:57:49:9B:61:69:D7:AC:A2:F0:27:D7:0F:1F:D8:E1:B8:13:7A:B4:F2:F3:44:2F:00:B0:08:4A:26:BB:99:17`).
  Bản Kachi cài trước 1.41 (ký khoá cũ / debug) **không** cập nhật đè được — gỡ rồi cài tay một lần, sau đó OTA bình thường.
- [ĐO 2026-09-13 17:55] Lần ba: 1.43 → 1.44 cùng đường (deep-link `open_settings_group=system` → Kiểm tra cập nhật) — `versionName=1.44`, launcher resume.
- [ĐO 2026-09-13 17:04] Lần hai: 1.42 → 1.43 qua Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật — dialog "New version: v1.43" → cài → `versionName=1.43`, KachiHomeActivity resume.
- [ĐO 2026-09-13] Đã kiểm end-to-end trên máy ảo: 1.41 → 1.42 (thấy bản mới, tải, cài qua dadb, tự mở lại sau 5 s). Máy ảo cần
  `adb tcpip 5555` + `adb reverse tcp:5555 tcp:5555` để app nối được loopback; xe thật có adbd mạng sẵn.
- ⚠ **Từ 1.49 APK nặng ~27 MB** (trước đó ~9 MB): V1 pha NGHE mang `libvosk.so` cho **hai** ABI (`arm64-v8a` +
  `armeabi-v7a`; x86/x86_64 đã lọc bỏ, −19 MB). OTA vẫn chạy đúng đường cũ, chỉ tải lâu hơn trên mạng 4G của xe.
- ⚠ **Mô hình nhận dạng KHÔNG nằm trong APK.** `vosk-model-small-vn-0.4` (**32 MB** nén, **51 MB** trên đĩa,
  Apache-2.0) tải riêng **một lần** từ `alphacephei.com` qua *Cài đặt › Hệ thống & quyền › Nâng cao › Nhận dạng giọng
  nói (tại máy)*. Lý do: nhét vào APK thì **mỗi bản vá một dòng chữ** cũng bắt người dùng tải lại 32 MB. Gói được
  ghim **sha256 + kích thước** (xem `VoiceModelManifest`), và có đường **gỡ** ngay cạnh nút tải.
  - TODO(owner): tải `vosk-model-small-vn-0.4.zip` lên GitHub Release `model-vn-0.4` của repo này để có **đường lùi**
    khi alphacephei chết. Tên tệp giữ NGUYÊN; sha256 phải khớp, không khớp thì app tự từ chối.
- Build: `./gradlew :app:assembleRelease` (cần `keystore.properties` ở gốc repo; thiếu ⇒ build release fail có chủ ý),
  rồi `cp app/build/outputs/apk/release/app-release.apk apk/Kachi-<ver>-release.apk`, commit + push lên `main`.

**(EN)** The on-car app polls this folder (GitHub Contents API, branch `main`, repo `dangkhoi/byd-kachi`) for a newer
`Kachi-<ver>-release.apk`, downloads it and installs over the dadb loopback (`pm install -r`). Keep a single latest file,
signed with Kachi's own key (from 1.41). Builds signed with an older key must be uninstalled once before OTA works.
