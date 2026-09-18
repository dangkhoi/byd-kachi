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
- **1.74 (75) — 2026-09-18** (`Kachi-1.74-release.apk`, 38,0 MB, sha256 `abe05f3e…8286e`, thay 1.73). Voice dẫn
  đường + kính 50% (owner yêu cầu). **(1) App dẫn đường MẶC ĐỊNH**: Cài đặt › Dẫn đường có mục *"App dẫn đường mặc
  định"* (Google Maps / VietMap / Waze, mặc định **Google Maps**). Nói *"dẫn đường …"* KHÔNG nêu app → dùng app
  mặc định; **có** nêu tên → đúng app đó. **BỎ fallback chéo**: VietMap geocode hỏng KHÔNG còn tự nhảy sang Google
  Maps nữa — mở CHÍNH app đã chọn + báo *"chưa tra được điểm đến"* (owner: *"cái nào ra cái đó thôi"*). **(2) Kính
  50%**: *"mở một nửa kính"* / *"mở kính 50%"* nay hạ kính tới **~nửa** (HAL `WINDOW_OPEN_HALF`, đã đo per-window;
  ca cả-4-kính chờ xác nhận trên xe) thay vì mở hết. **[ĐO] 5 module 0 đỏ** (core 2226 · app · car-int 61 · offcar
  99 · contracts 22). 🚗 owner test xe: chọn app mặc định · «dẫn bằng vietmap» không nhảy GMaps · «mở một nửa kính».
- **1.73 (74) — 2026-09-17** (`Kachi-1.73-release.apk`, 38,0 MB, sha256 `953b5510…b5e4c`, thay 1.72). Sửa
  NHẬN-SỐ + câu HỎI từ log xe 81 lượt owner báo (*"chỉnh máy lạnh 24 độ không hiểu, hỏi đi hỏi lại"*, *"nhận diện
  số đang tệ"*). **(A) số nhiệt độ**: «tăng/giảm nhiệt độ 24 độ» trước bị hiểu **±24** (cộng vào 22 = kẹt trần 33)
  → nay **ĐẶT = 24** (số trong dải 17..33 = setpoint); gió/âm lượng giữ tương đối (không phá «giảm âm lượng 2 nấc»).
  **(B) câu hỏi map sai datum**: «chỉ số **bụi mịn** là bao nhiêu» ra `Số` (datum `gear` do chữ *"số"*) → nay **bụi
  mịn** (datum dài nhất thắng + bỏ cụm dẫn *"chỉ số"*); «nhiệt độ / máy lạnh bao nhiêu độ» trước RỖNG/`Âm lượng` →
  nay **nhiệt AC**. **(C)** «tắt bụi mịn» trước MỞ Google Maps → nay **tắt máy lọc**. **[ĐO] 5 module 0 đỏ** (core
  2224 · app 1147/1153 · car-int 61 · offcar 99 · contracts 22). 🚗 owner test xe. **Còn nợ (log)**: «chế độ lái»→đèn
  pha · «xi nhan»→đèn ngày (feature đã gỡ, khớp mờ lạc) · «một nửa kính» (chưa có lệnh ghi ½) · «xăng» (BEV chưa map).
- **1.72 (73) — 2026-09-17** (`Kachi-1.72-release.apk`, 38,0 MB, sha256 `37e8b4e8…6939d`, thay 1.71). Sửa
  END-TO-END 3 lỗi voice→app owner báo từ log xe (159 lượt): **(1) Google Maps chưa dẫn** — `geo:0,0?q=` chỉ MỞ
  màn kết quả; nay `google.navigation:q=<địa chỉ>` (dẫn turn-by-turn, Google tự geocode, không cần Nominatim).
  Và mặc định (không nêu app) nay ưu tiên GMaps dẫn-bằng-chữ thay VietMap (VietMap buộc geocode → kẹt "đang tra
  điểm đến" khi mạng xe treo). **(2) VietMap đơ** — geocode hỏng/timeout nay **lùi về Google Maps dẫn bằng chữ**
  (luôn có dẫn) thay mở VietMap trơn; thêm timeout cứng 7 s chống on-device Geocoder treo vô hạn. **(3) YouTube
  search không phát** — `ACTION_SEARCH` (chỉ mở ô tìm) → `MEDIA_PLAY_FROM_SEARCH` + focus (phát theo tìm), fallback
  ACTION_SEARCH. **[ĐO] 5 module 0 đỏ** (core 2220 · app 1141/1153 · car-int 61 · offcar 99 · contracts 22). 🚗
  owner test xe: GMaps dẫn thật · VietMap không đơ (hoặc tự lùi GMaps) · YouTube phát. **Còn nợ (parser)**: «mở
  &lt;tên ca sĩ&gt; trên youtube» (không có từ "bài/hát") rớt tên; app-hint đầu câu ("dùng google map…") chưa bắt.
- **1.71 (72) — 2026-09-17** (`Kachi-1.71-release.apk`, 38,0 MB, sha256 `c1f4fdd5…9064a`, thay 1.70). Sửa 2 việc
  owner báo, **từ gốc IA (không hotfix)**: (1) **Trạng thái xe đọc REALTIME** — ô điều khiển (nhiệt độ · gió · lấy
  gió trong · cốp…) trước hiển thị mức MẶC ĐỊNH trong RAM (nhiệt 22 · gió 4), chỉ đổi khi bấm trên launcher; nay
  đọc giá trị THẬT của xe theo nhịp poll (`CarStatus.controls` + `CarDataAdapter.readState`, cửa sổ ân hạn 2,5 s
  chống nháy sau khi bấm). Đặt 24°C ở màn BYD gốc ⇒ launcher hiện 24 (nhịp chậm ≤10 s). Chỉ đọc thứ ĐANG HIỆN
  (không phá tối ưu K1). (2) **Màu sáng sủa hơn** — thang bề mặt (16 vai nền) trước là hex đặt tay rời rạc, mood
  "tối tăm" nằm rải; nay derive từ MỘT recipe `SurfaceRamp` (nền deep-indigo ấm `#141b30` thay near-black lạnh
  `#0a0d13`, gradient thẻ sâu hơn có sức sống). Tương phản chữ WCAG giữ nguyên (test khoá). Đây là **thử 1
  version** — 🚗 owner ngắm màu trên xe + đo realtime (đặt nhiệt/gió ở màn xe, xem launcher đổi theo). **[ĐO] 5
  module 0 đỏ** (core 2219 · app 1139/1151 · car-int 61 · offcar 99 · contracts 22).
- **1.70 (71) — 2026-09-17** (`Kachi-1.70-release.apk`, 38,0 MB, sha256 `be34ddc6…6617b`, thay 1.69). VOICE
  tái kiến trúc từ đầu (owner: quan trọng nhất, chưa bao giờ ổn thực tế): máy trạng thái tường minh `VoiceTurnState`
  (8 pha, bất biến chống-loop CHỈ pha nghe mở mic) thay chùm cờ race; VAD hâm sẵn 1 lần/tiến trình (bỏ nghẽn
  nạp-mỗi-lượt của "bấm 1,5 s mới nghe"); chime PCM async (bỏ chặn 3 s). Sửa 2 lỗi owner báo: overlay tắt giữa
  câu → sống tới hết câu đọc; **Piper nói chậm lại** (0.9, núm `voice_tts_speed` chỉnh trên xe). **Chọn giọng
  phản hồi**: số 1 Piper (mặc định) · số 2 giọng bé (clone, tự lùi Piper khi thiếu clip). Chạm trong ô: TCP
  loopback + token (thay unix-socket bị SELinux chặn) + cầu chì daemon + `input` no-retry. Cốp bằng giọng
  (`voiceCtlBackDoor` MỞ=1/ĐÓNG=3, [ĐO xe 09-17]); ghế mát 3 mức; AC AUTO đọc được nhưng GHI vẫn chặn (thiếu
  feature-id trên xe). **Model G** fine-tune (gipformer-vi-ft-ep2, MIT, experimental) + nút chọn — [ĐO benchmark
  270 câu giọng thật: G 177 vs ship 167]; mặc định vẫn giữ, owner quyết trên xe. Visual P1b/P2/P3. 🚗 chưa đo
  trên xe: độ trễ nói→nghe→chạy, overlay không cắt giữa câu, chạm YouTube, chọn model G, cốp/ghế/AC.
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
