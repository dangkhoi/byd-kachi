# Kachi launcher (byd-launcher)

> [!IMPORTANT]
> **(VI) ĐÂY LÀ `byd-launcher` — BẢN FORK của ClusterNav 2.0 (`byd-cluster-2`) để phát triển dần thành LAUNCHER cho xe BYD DiLink.** Tên launcher: **Kachi** (repo `github.com/dangkhoi/byd-kachi`; nhãn app hiện là "Kachi"). Mục tiêu: GIỮ NGUYÊN mọi tính năng ClusterNav (gom vào mục **Cài đặt / Settings**) + màn hình chính (**HOME**) + nhiều app/tiện ích đi kèm kiểu Dudu (control cửa–kính–đèn–gạt mưa–AC, widget đồng hồ, lưới app…). `applicationId` đổi thành **`com.byd.launcher`** để cài **SONG SONG**, KHÔNG đụng bản ClusterNav 2.0 (`com.byd.clusternav2`) đang chạy ổn trên xe. Fork từ ClusterNav **v1.38 (versionCode 39)**. Lộ trình: `docs/specs/launcher-foundation.html` + `docs/PROJECT-BACKLOG.md`. Hai mục **Tính năng** và **Chi tiết tính năng** bên dưới đã viết lại cho Kachi 2.73 (174); phần changelog theo phiên bản là **lineage ClusterNav**, giữ làm lịch sử.
>
> **(EN) THIS IS `byd-launcher` — a FORK of ClusterNav 2.0 (`byd-cluster-2`) being grown into a BYD DiLink car LAUNCHER.** The launcher is named **Kachi** (repo `github.com/dangkhoi/byd-kachi`; the app label now reads "Kachi"). Goal: KEEP every ClusterNav feature (consolidated into a **Settings** area) + a **HOME** screen + many bundled apps/utilities (Dudu-style: window/door/light/wiper/AC controls, gauge widgets, an app grid…). `applicationId` is changed to **`com.byd.launcher`** so it installs **SIDE BY SIDE**, leaving the stable ClusterNav 2.0 (`com.byd.clusternav2`) on the car untouched. Forked from ClusterNav **v1.38 (versionCode 39)**. Roadmap: `docs/specs/launcher-foundation.html` + `docs/PROJECT-BACKLOG.md`. The **Features** and **Feature details** sections below are current for Kachi 2.73 (174); the per-version changelog further down is **ClusterNav lineage**, kept as history.

> Song ngữ: các mục hướng đến người dùng viết tiếng Việt trước, English sau. Changelog theo phiên bản giữ nguyên tiếng Anh, có một dòng dẫn tiếng Việt.
> Bilingual: user-facing sections are Vietnamese first, then English. Per-version changelog entries stay in English, with a one-line Vietnamese intro.

> [!CAUTION]
> **(VI) KACHI LAUNCHER — bản hiện tại: `2.73` (versionCode 174, 2026-09-26; OTA `apk/Kachi-2.73-release.apk`)** — `com.byd.launcher`, MỘT icon "Kachi" duy nhất (LAUNCHER = KachiHomeActivity), mọi cấu hình ClusterNav đã gộp vào Kachi Settings (11 nhóm); hướng dẫn dùng: `docs/HUONG-DAN-KACHI.md`; đóng dự án: `docs/CLOSEOUT-2026-09-25.md`; màn ClusterNav cũ chỉ còn là "màn nâng cao" mở từ Hệ thống › Nâng cao. Dòng dưới là trạng thái nền tảng ClusterNav 2.0 kế thừa.
> **(EN) KACHI LAUNCHER — current: `2.73` (versionCode 174, 2026-09-26; OTA `apk/Kachi-2.73-release.apk`)** — `com.byd.launcher`, a single "Kachi" icon (LAUNCHER = KachiHomeActivity); every ClusterNav setting now lives in Kachi Settings (11 groups); user guide `docs/HUONG-DAN-KACHI.md`; closeout `docs/CLOSEOUT-2026-09-25.md`; the old ClusterNav screen is an "advanced screen" opened from System › Advanced. The lines below describe the inherited ClusterNav 2.0 baseline.
>
> **(VI) OTA của Kachi (L2, 2026-09-13):** app tự dò `apk/Kachi-<ver>-release.apk` trên `main` của repo này (`dangkhoi/byd-kachi`) và cài qua dadb loopback — xem `apk/README.md`. Khoá ký RIÊNG của Kachi (keystore ngoài repo, `keystore.properties` gitignored); bản cài trước 1.41 phải gỡ rồi cài tay một lần.
> **(EN) Kachi OTA (L2, 2026-09-13):** the app polls `apk/Kachi-<ver>-release.apk` on this repo's `main` (`dangkhoi/byd-kachi`) and installs over the dadb loopback — see `apk/README.md`. Kachi has its own signing key; builds installed before 1.41 must be uninstalled once.
>
> **(VI) LỊCH SỬ PRE-FORK — KHÔNG phải trạng thái hiện tại (xem dòng Kachi ở trên): ClusterNav 2.0 `1.38` (versionCode 39) — app ĐỘC LẬP "Cluster Nav 2.0" (`com.byd.clusternav2`), TÁCH HOÀN TOÀN khỏi app cũ ClusterNav (`com.byd.clusternav`): khác package + khác khoá ký → cài SONG SONG trên xe, không đụng/ghi đè app cũ. Nền tảng mới `byd-cluster-2` (fork từ codebase 1.30, đã khôi phục tín hiệu Waze/VietMap); bản tự-cập-nhật OTA để thử nghiệm.** Các bộ test core/app/car-integration + offcar-planner build **xanh off-car** và release APK build sạch, đã ký; mọi bề mặt test/ghi-thiết-bị (bộ dò T10) chỉ nằm trong build type `vehicleTest` — release APK **không có bề mặt test nào được export/tiếp cận được** (xác minh bằng `aapt2` trên bản build này). Từ `1.11`, chủ sở hữu đăng mỗi `apk/ClusterNav-<ver>-release.apk` lên `main` để app **tự cập nhật qua mạng (OTA)** xuống xe để thử — không cần ADB/laptop. **Đây là kênh thử nghiệm trên xe của riêng chủ sở hữu, KHÔNG phải bản phát hành công khai được hỗ trợ, và tách biệt với quy trình ứng viên exact-source `collectAuthorizedApk` / Stage-11 (vốn là một cổng riêng).** Đây là một thử nghiệm sở thích, không cam kết an toàn lái xe, tương thích, khả năng hoàn tác hay mức độ sẵn sàng sản xuất — cài đặt tự chịu rủi ro. Ứng viên vehicle-test `1.04` vẫn bị **blocklist theo SHA-256** trong on-car guard (nó từng export bề mặt T10 `TEST_ADAS_*` / `TEST_SPEED_LIMIT`) và **không còn được giữ trong `apk/`**; bản release hiện tại không export bề mặt nào như vậy.
>
> **(EN) PRE-FORK LINEAGE — NOT the current state (see the Kachi line above): ClusterNav 2.0 `1.38` (versionCode 39) — STANDALONE app "Cluster Nav 2.0" (`com.byd.clusternav2`), FULLY SEPARATE from the legacy ClusterNav app (`com.byd.clusternav`): different package + different signing key → installs SIDE BY SIDE on the car, never overwrites the old app. New `byd-cluster-2` baseline (fork of the 1.30 codebase with Waze/VietMap signals revived); OTA self-test build.** The core/app/car-integration + offcar-planner suites **build green off-car** and the release APK builds cleanly and is signed, with all test/instrument-write surfaces (the T10 probe harness) confined to the `vehicleTest` build type — the release APK has **no exported/reachable test surface** (verified with `aapt2` on this build). From `1.11` the owner publishes each plain `apk/ClusterNav-<ver>-release.apk` to `main` so the app self-updates **over-the-air (OTA)** onto the car for testing — no ADB/laptop needed. **This is the owner's own iterative on-car test channel, not a supported public release, and is separate from the formal exact-source `collectAuthorizedApk` / Stage-11 candidate process (which remains its own distinct gate).** It is a hobby experiment with no driving-safety, compatibility, reversibility, or production-readiness claim — install at your own risk. The `1.04` vehicle-test candidate stays **blocklisted by SHA-256** in the on-car guard (it exported the T10 `TEST_ADAS_*` / `TEST_SPEED_LIMIT` surface) and is **no longer kept in `apk/`**; the current release exports no such surface.

**(VI)** ClusterNav là một thử nghiệm cá nhân mang tính sở thích của **Đăng Khôi · `dangkhoi`**, để khám phá dẫn đường và chiếu màn hình lên cụm đồng hồ trên phần cứng BYD DiLink. Dự án không liên kết với BYD và không đưa ra cam kết nào về an toàn lái xe, tương thích, khả năng hoàn tác hay mức độ sẵn sàng sản xuất.

**(EN)** ClusterNav is a personal hobby experiment by **Đăng Khôi · `dangkhoi`** for exploring navigation and cluster projection on BYD DiLink hardware. It is not affiliated with BYD and makes no driving-safety, compatibility, reversibility, or production-readiness claim.

## Tính năng · Features

**(VI)** Mục lục tính năng của **Kachi 2.73 (174)** — chi tiết từng nhóm (kèm đường dẫn menu thật) ở phần **Chi tiết tính năng** bên dưới. Hướng dẫn dùng đầy đủ: [`docs/HUONG-DAN-KACHI.md`](docs/HUONG-DAN-KACHI.md). Danh mục máy-sinh của **mọi** chức năng kèm status trên xe: [`docs/kachi-feature-catalog.html`](docs/kachi-feature-catalog.html). Ký hiệu **🚗** = code xong nhưng chưa đo trên xe thật.

*1 · Màn hình chính & ô*
- **5 bố cục sẵn + bố cục tự vẽ** — 1 ô · 2 cột · 2 hàng · 3 ô · 4 ô, hoặc tự vẽ khung trên lưới 12×6
- **Ô làm việc** — mỗi ô chứa widget Kachi, widget Android của app khác, hoặc **một app thật chạy trong ô** (màn ảo)
- **Thanh trạng thái & thanh nút xe** — chọn chip, ẩn/hiện nhãn, sắp lại vị trí item, đặt thanh nút ở 4 viền
- **Hiển thị & đơn vị** — đơn vị đo, giao diện sáng/tối, màu sắc, ngôn ngữ (Theo xe / VI / EN), kính thật (làm mờ nền)

*2 · Dẫn đường & chiếu cụm*
- **Dẫn đường lên cụm + HUD** — chỉ đường trên cụm (làn zin + "Giữa + ETA") và HUD kính lái; hướng rẽ vòng xuyến + số lối ra; chạy chữ tên đường dài
- **Biển báo tốc độ trên cụm** — tốc độ/giới hạn + giới hạn sắp tới + chip cảnh báo camera; chỉnh cỡ và vị trí
- **Bong bóng VietMap trên cụm** — hiện bóng VietMap, kéo-thả chỉnh vị trí
- **Chiếu màn lên cụm (Cluster Cast)** — chiếu app đang mở lên cụm: toàn màn hoặc chia đôi chỉnh tỉ lệ; tự chiếu khi nổ máy; nút nổi; cứu hộ cụm
- **Sổ địa chỉ + app dẫn đường mặc định** — lưu điểm đến theo hồ sơ, nói tên là đi

*3 · Camera theo xi-nhan (Seal · Sealion 6)*
- **Bật camera khi xi-nhan** — xi-nhan trái → camera trái nổi ở góc màn, phải → camera phải; giữ tới khi đèn xi-nhan tắt
- **Chọn cam + vị trí từng bên** — id camera 0–5 mỗi bên, góc trên-trái / trên-phải mỗi bên
- **Xoay video trái/phải độc lập** — Không xoay · ↺ 90° · ↻ 90° · 180°, đặt riêng từng bên
- **Khung đúng tỉ lệ + chọn cách kết xuất** — khung co theo tỉ lệ hình sau khi xoay; TextureView (mặc định) hoặc SurfaceView
- **Hiện camera lên màn cụm** — thay vì màn chính

*4 · Tiện nghi xe*
- **Nổ máy thì tự lấy gió trong** — xe quên chế độ này mỗi lần khởi động, Kachi bật lại
- **Ghế mát / sưởi tự động** — chế độ Làm mát hoặc Sưởi, mức từng ghế; chạm sơ đồ ghế để đổi mức
- **Tự lọc bụi mịn PM2.5** — tự chạy lọc khi bụi lên cao, kèm nút **Lọc ngay một lượt**
- **Mưa thì tự bật sấy kính** — đọc cảm biến mưa mỗi 5 phút, bật sấy kính trước / sau + gương, hết mưa thì tắt (🚗)
- **Nút xe trên thanh nút + giọng nói** — kính, cốp, cửa sổ trời, đèn, điều hoà, nhiệt độ, quạt… đi qua cùng một bộ đăng ký nút

*5 · Giọng nói*
- **Nghe tại máy, không gửi ra mạng** — sherpa-onnx + mô hình tiếng Việt `zipformer-vi`, tải một lần (hoặc side-load qua thẻ)
- **Ba lối gọi** — ô *Nói với xe* · nút mic trên thanh trạng thái · phím vô-lăng gán đích *Kachi nghe (tại máy)*
- **"Hey Kachi" rảnh tay** *(thử nghiệm, mặc định tắt)* — nói câu gọi khi màn sáng; nghe nhầm nhiều lần thì tự tắt
- **Lệnh theo nhóm** — nút xe · gói lệnh · đọc thông tin xe · mở/đóng app (kèm *"vào ô số N"*) · nhạc/YouTube · dẫn đường · đổi hồ sơ · đổi bố cục · câu ghép với *và* / *rồi*
- **Giọng đọc offline + hỏi xác nhận** — gói Piper đọc phản hồi tại máy; tự chọn việc nào phải hỏi lại trước khi bắn
- **Nghe chắc hơn ở 2.73** — tên app/hồ sơ nghe hơi lệch vẫn hiểu; *Back* không còn huỷ lượt nghe

*6 · Hồ sơ tài xế*
- **Mỗi hồ sơ một bộ cấu hình** — bố cục, nội dung ô, chip, thanh nút, hình nền, giao diện, đơn vị, ngôn ngữ
- **Thêm (bản sao) · đổi tên · xoá · hồ sơ lúc nổ máy** — đổi nhanh bằng chip trên thanh trên hoặc bằng giọng
- **Xuất / nhập hồ sơ** — file trên thẻ, trùng tên thì tự đánh số

*7 · Ảnh xe · hình nền · trình chiếu*
- **Ba thư mục trên thẻ, không cần quyền** — ảnh xe top-down · hình nền · ảnh cho widget trình chiếu
- **Nút "Sao chép đường dẫn thư mục"** — dán vào trình quản lý tệp rồi chép ảnh vào
- **Hình xe vẽ bằng vector** — chọn model và màu sơn, dùng được khi chưa có ảnh riêng

*8 · Automation*
- **Mưa thì tự bật sấy kính** — xem nhóm 4 (🚗)
- **Tự dẫn đường theo lịch** — khung giờ × thứ × "chỉ khi có GPS" × điểm đến trong Sổ địa chỉ × app dẫn đường, mỗi khung 1 lần/ngày (🚗)
- **Khởi động theo xe** — tự mở Kachi khi nổ máy · chạy dịch vụ nền khi nổ máy · giữ Kachi làm màn hình chính khi nổ máy

*9 · Hệ thống*
- **Đặt / bỏ Kachi làm màn hình chính** — ROM BYD không hiện hộp chọn HOME nên Kachi tự đặt qua dadb loopback
- **Cập nhật OTA trong xe** — *Kiểm tra cập nhật* tải `apk/Kachi-<ver>-release.apk` mới hơn rồi cài đè; kèm công tắc *Tự động cập nhật*
- **Quyền còn thiếu + tự cấp** — cấp quyền notification / floating qua dadb, không cần laptop
- **Khởi động lại launcher · Dừng toàn bộ dẫn đường · Cứu hộ cụm** — gỡ rối không phải khởi động lại đầu xe
- **Chẩn đoán & log** — màn Chẩn đoán cụm, log ghi ra thẻ, báo cáo kiểm-từng-chức-năng; anh em chỉ cần chụp màn gửi về
- **Chế độ kiểm thử qua adb** — chỉ bật được bằng tay trong xe, tự tắt sau 60 phút, chết theo lần nổ máy

*10 · Hiệu năng & an toàn*
- **Chỉ đọc HAL khi màn hình đang bày** — vòng poll đọc theo nhu cầu, datum đọc ra `null` thì nguội dần rồi thử lại giãn cách
- **Nhẹ hẳn so với đời 2.5x** — [ĐO xe Seal 2026-09-26, 2.58 → 2.69] PSS launcher 245,6 → **67,1 MB**, native 220 → 29 MB, luồng 58 → 43, khung janky 56 % → 28,6 %
- **Ngân sách shell + tiết chế log** — hạn lệnh shell mỗi phút; dòng log trùng trong 10 s gộp lại (mức W/E/F/A không bao giờ bị bỏ)
- **Trả RAM native cho hệ (2.73, nội bộ)** — tiến trình nghe gọi `mallopt`; mức tiết kiệm thật 🚗 chưa đo trên xe
- **Fail-safe theo thiết kế** — chỉ tắt lại cái Kachi tự bật; guard đặt ở tầng thi hành; **không** mock GPS, quyền location chỉ ĐỌC

**(EN)** Feature index for **Kachi 2.73 (174)** — each group is detailed under **Feature details** below (with the real menu paths). Full user guide: [`docs/HUONG-DAN-KACHI.md`](docs/HUONG-DAN-KACHI.md). A machine-generated catalog of **every** feature with its on-car status: [`docs/kachi-feature-catalog.html`](docs/kachi-feature-catalog.html). **🚗** = built but never measured on a real car.

*1 · Home screen & slots*
- **5 preset layouts + a custom one** — 1 slot · 2 columns · 2 rows · 3 slots · 4 slots, or draw your own frames on a 12×6 grid
- **Work slots** — each slot holds a Kachi widget, another app's Android widget, or **a real app running inside the slot** (virtual display)
- **Status bar & car button bar** — pick chips, show/hide labels, reorder items, dock the button bar to any of the 4 edges
- **Display & units** — units, light/dark theme, colours, language (By-car / VI / EN), real glass (blur the backdrop)

*2 · Navigation & cluster casting*
- **Navigation on the cluster + HUD** — stock lane + centre "Giữa + ETA" and the windshield HUD; roundabout exit direction + number; long road-name marquee
- **Speed badge on the cluster** — current speed/limit + upcoming limit + a camera-alert chip; adjustable size and position
- **VietMap bubble on the cluster** — show the bubble, drag to reposition
- **Cluster Cast** — cast the foreground app to the cluster: full screen or split with an adjustable ratio; autostart on engine start; floating button; cluster rescue
- **Address book + default nav app** — per-profile saved destinations; say the name and it navigates

*3 · Turn-signal camera (Seal · Sealion 6)*
- **Camera on turn signal** — left signal → left camera overlay, right → right; held until the signal lamp goes off
- **Per-side camera id and corner** — camera id 0–5 and top-left / top-right per side
- **Independent left/right rotation** — none · ↺ 90° · ↻ 90° · 180°, set per side
- **Aspect-correct frame + render path** — the frame follows the crop's aspect after rotation; TextureView (default) or SurfaceView
- **Show the camera on the cluster display** — instead of the main screen

*4 · Car comfort*
- **Recirculation on engine start** — the car forgets it every start; Kachi turns it back on
- **Automatic seat cooling / heating** — Cool or Heat mode, a level per seat; tap the seat diagram to cycle
- **PM2.5 auto-filter** — purifies when dust rises, plus a **Purify now** button
- **Auto-defrost when it rains** — polls the rain sensor every 5 min, turns on front / rear + mirror defrost, turns it off when the rain stops (🚗)
- **Car buttons on the bar + by voice** — windows, trunk, sunroof, lights, AC, temperature, fan… all through one control registry

*5 · Voice*
- **On-device recognition, nothing leaves the car** — sherpa-onnx with the Vietnamese `zipformer-vi` model, downloaded once (or side-loaded from the SD card)
- **Three ways in** — the *Talk to the car* tile · the mic button in the status bar · a steering-wheel key bound to *Kachi listens (on-device)*
- **Hands-free "Hey Kachi"** *(experimental, default off)* — say the wake phrase while the screen is on; auto-disables after repeated false accepts
- **Commands by group** — car buttons · macros · read vehicle data · open/close apps (including *"into slot N"*) · music/YouTube · navigation · switch profile · switch layout · compound sentences with *và* / *rồi*
- **Offline reply voice + confirmation gate** — a Piper pack speaks replies on-device; you choose which actions must ask first
- **More robust in 2.73** — slightly misheard app/profile names still resolve; *Back* no longer cancels the listening turn

*6 · Driver profiles*
- **One configuration set per profile** — layout, slot contents, chips, button bar, wallpaper, theme, units, language
- **Add (a copy) · rename · delete · profile on engine start** — switch from the chip in the top bar or by voice
- **Export / import** — files on the SD card; duplicate names get numbered

*7 · Car image · wallpapers · slideshow*
- **Three folders on the SD card, no permission needed** — top-down car image · wallpapers · slideshow photos
- **A "Copy folder path" button** — paste it into a file manager and drop images in
- **Vector-drawn car artwork** — pick the model and paint colour when you have no photo of your own

*8 · Automation*
- **Auto-defrost when it rains** — see group 4 (🚗)
- **Scheduled navigation** — time window × weekdays × "only with GPS" × a saved place × a nav app, once per window per day (🚗)
- **Start with the car** — auto-start Kachi on engine start · run the background service on engine start · keep Kachi as home on engine start

*9 · System*
- **Set / unset Kachi as home** — the BYD ROM shows no HOME chooser, so Kachi sets it over the dadb loopback
- **In-car OTA update** — *Check for updates* fetches a newer `apk/Kachi-<ver>-release.apk` and installs it over the top; plus an *Auto update* toggle
- **Missing permissions + self-grant** — notification / floating-window grants over dadb, no laptop
- **Restart launcher · Stop all navigation · Cluster rescue** — recover without rebooting the head unit
- **Diagnostics & logs** — a cluster diagnostics screen, logs written to the SD card, a capability-test report; testers only need to send a screenshot
- **ADB test mode** — can only be switched on by hand in the car, self-expires after 60 min, dies with the ignition cycle

*10 · Performance & safety*
- **HAL is read only for what the screen shows** — demand-gated polling; a datum that reads `null` goes cold and is retried with backoff
- **Much lighter than the 2.5x line** — [measured, Seal car 2026-09-26, 2.58 → 2.69] launcher PSS 245.6 → **67.1 MB**, native 220 → 29 MB, threads 58 → 43, janky frames 56 % → 28.6 %
- **Shell budget + log throttling** — a cap on shell commands per minute; identical log lines within 10 s collapse (W/E/F/A are never dropped)
- **Returning native memory to the OS (2.73, internal)** — the listening process calls `mallopt`; the real saving is 🚗 not yet measured on a car
- **Fail-safe by design** — Kachi only turns off what it turned on; guards live in the execution layer; **no** mock GPS — location permission is read-only

## Target product baseline — exactly two tracks · Mục tiêu sản phẩm — đúng hai nhánh

**(VI)** Sản phẩm chốt ở đúng hai nhánh:

1. **Navigation + HUD** — một nguồn/phiên dẫn đường có thẩm quyền duy nhất, với đầu ra Cluster-lane và HUD độc lập.
2. **Cluster Cast** — trạng thái bền, nhật ký (journal), thực thi, khôi phục, UI và pipeline rollout độc lập.

Hai nhánh có thể dùng chung một APK để đóng gói, nhưng không được dùng chung điều khiển runtime, trạng thái thay đổi được, transport live, executor, journal, vòng đời hay khôi phục. Home là bộ render/dispatcher, không phải orchestrator.

**(EN)** The product settles on exactly two tracks:

1. **Navigation + HUD** — one authoritative navigation source/session with independent Cluster-lane and HUD outputs.
2. **Cluster Cast** — an independent durable state, journal, execution, recovery, UI and rollout pipeline.

The tracks may share one APK as packaging, but they must not share runtime control, mutable state, live transport, executor, journal, lifecycle or recovery. Home is a renderer/dispatcher, not an orchestrator.

**(VI) GPS Dead Reckon và mock-location đã bị gỡ bỏ.** Ngày 2026-07-27 chủ sở hữu kết thúc thử nghiệm này: nó hỏng quá thường xuyên nên không giữ, và một lần thử trong tương lai nên bắt đầu từ một cách tiếp cận mới thay vì nguồn này. Sáu file (1.096 dòng) đã bị xoá khỏi working tree; lịch sử git là bản ghi duy nhất còn lại, và đó cũng là nơi để rollback. Đừng chọn ClusterNav làm app mock-location — nó không còn đóng vai trò đó được nữa.

**(EN) GPS Dead Reckon and mock-location are removed.** On 2026-07-27 the owner ended the experiment: it failed too often to keep, and a future attempt should start from a new approach rather than this source. The six files (1,096 lines) are deleted from the working tree; git history remains the only record, which is where rollback belongs. Do not select ClusterNav as the mock-location app — it can no longer act as one.

## Downloads and installation · Tải về và cài đặt

**(VI)** **Cài lần đầu (Kachi):** app ĐỘC LẬP `com.byd.launcher`, cài SONG SONG với ClusterNav cũ — **không cần gỡ**; tải `apk/Kachi-2.73-release.apk` (nút **Raw**/Download trên GitHub, nhánh `main`) rồi cài bằng `adb install -r` (tap trên đầu xe có thể bị ROM chặn vì đây là launcher — xem `docs/HUONG-DAN-KACHI.md` §1). Sau đó cập nhật qua **OTA**: *Cài đặt › Hệ thống & quyền › Kiểm tra cập nhật* — app tự dò `apk/Kachi-<ver>-release.apk` mới hơn trên `main` và cài qua dadb loopback (`-r`, khoá ký riêng của Kachi) — không cần ADB/laptop. Để build cùng bản release từ nguồn: `./gradlew :app:assembleRelease`.

**(EN)** **First install (Kachi):** a STANDALONE app `com.byd.launcher` that installs side by side with the old ClusterNav — **no uninstall needed**; download `apk/Kachi-2.73-release.apk` (GitHub **Raw**/Download, branch `main`) and install with `adb install -r` (tapping it on the head unit may be blocked by the ROM because it is a launcher — see `docs/HUONG-DAN-KACHI.md` §1). Afterwards it updates via **OTA**: *Settings › System & permissions › Check for update* — the app polls this repo's `apk/` on `main` for a newer `Kachi-<ver>-release.apk` and installs it over the dadb loopback (`-r`, Kachi's own signing key) — no ADB/laptop. To build the same release from source: `./gradlew :app:assembleRelease`.

**(VI)** Changelog theo phiên bản dưới đây giữ nguyên tiếng Anh (mô tả kỹ thuật từng bản sửa).

**Lineage (pre-fork ClusterNav 2.0, kept as history — current Kachi version is 2.73 (174) above): 1.38 (versionCode 39) — "Cluster Nav 2.0" (`com.byd.clusternav2`), a standalone app independent of the legacy `com.byd.clusternav` (its own package + its own signing key, installs side by side).** `byd-cluster-2` re-baselines the 1.30 ClusterNav codebase (Waze/VietMap signals revived — see `docs/specs/waze-vietmap-signal-revival.html`) as a fresh **1.0** for a new iteration; the app OTA self-updates from **this** repo's `apk/ClusterNav-<ver>-release.apk` on `main`. The per-version notes below are kept as lineage history.

`1.38` adds a manual **"Lọc ngay" (Clean now)** button to the PM2.5 card and fixes the on-car v1.37 finding that the auto-filter did nothing when the cabin got dusty. **(VI)** `1.38`: thêm nút "Lọc ngay" cho lọc bụi PM2.5 + sửa lỗi trên xe "popup báo bụi hiện mà không tự lọc".

- **"Clean now" button.** The PM2.5 card gets a manual **Lọc ngay / Clean now** button that fires an immediate active purification (`setQuickCleanAirState(1)`) on demand, regardless of the auto switch.
- **Auto-filter now actively cleans when dust rises.** On v1.37 the auto-filter enabled the car's auto-clean mode (`setAutoCleanAirState(1)`) once and trusted the car to keep filtering — but on the owner's trim that mode does not actively purify (while driving the car just shows its "dusty" reminder popup, no filtering). When the switch is on, ClusterNav now polls the PM2.5 level (~45 s) and, when it crosses the heavy threshold (level 5 of 6), fires an active clean (`setQuickCleanAirState(1)`) itself — the same call the new button uses. The popup-suppress call stays best-effort (rejected by this trim, so the popup may still show), but filtering now actually happens.

`1.37` fixes the on-car v1.36 finding that the VietMap cluster bubble never appeared on a silent (background) autostart. **(VI)** `1.37`: sửa lỗi bóng VietMap không lên cụm khi tự khởi động nền (silent) — nay chờ VietMap vào map xong mới hạ nền, và tách autostart ra tiến trình riêng.

- **Silent VietMap autostart now waits until VietMap is actually in its map before backgrounding it.** On v1.36 the bubble-enabled silent path did `launch VietMap → sleep 1.5 s → send to background`. On the car that fixed 1.5 s was too short for VietMap's cold start (Flutter + map SDK + the `VMBluetoothService` that builds the bubble), especially on a slow network, so the app was backgrounded before the map came up and the bubble never built — the owner had to open VietMap by hand, wait for the map, then background it. The silent path now **polls until VietMap is the resumed activity continuously for ≥ 2.5 s** (map up and stable) before backgrounding it, with a 25 s upper bound (it exits early when the network is fast).
- **VietMap autostart moved to its own foreground service (`VietMapAutostartService`).** Because the poll can take several seconds, VietMap autostart no longer runs inline in the boot-setup chain — the other boot features (seats, PM2.5 filter, navigation, voice key) no longer wait on it. The anti-loop guard (single in-flight run + 30 s cooldown) and the cast-default / badge-only paths are unchanged.

`1.36` loosens the cramped vertical spacing from the 1.34 shrink. **(VI)** `1.36`: nới lại khoảng cách dọc bị chật sau khi thu gọn ở 1.34.

- **Comfortable vertical spacing.** 1.34 reduced every vertical metric to ~70% while keeping full-size text, so rows/lines felt cramped and stuck together on the car. Vertical paddings, margins and the inter-card gap are raised back to ~90% of the original (comfortable, still a little shorter than the pre-1.33 layout), row min-heights are relaxed (`touch_min` 40→46dp, `gap` 10→13dp), and **`lineSpacingMultiplier` 1.2** is added to the wrapped body/status text so multi-line sentences breathe. Text sizes stay at the 1.32 originals; no view id changed (parity kept); narrow layout seal re-pinned.

`1.35` whitelists the VietMap mod so its cluster bubble stops hitting the locked-IVI block. **(VI)** `1.35`: whitelist VietMap mod để bóng trên cụm hết bị "IVI không hỗ trợ".

- **VietMap bubble no longer blocked by the IVI.** The modded VietMap draws a floating overlay on the cluster; on the locked BYD IVI a package that is not in the global `byd_float_app_list` (and lacks `SYSTEM_ALERT_WINDOW`) gets the *"Hệ thống IVI không hỗ trợ hoạt động này"* toast and the bubble never shows. When the bubble is enabled, autostart now appends `vn.vietmap.live` to `byd_float_app_list` and grants it `SYSTEM_ALERT_WINDOW` over the dadb uid-shell (the same proven recipe the voice-assistant path uses, via a shared `FloatAppList.merge` that never clobbers other apps), once per install. Degrade-safe: a failure never blocks autostart and is retried next time.

`1.34` fixes three issues found testing 1.33 on the car. **(VI)** `1.34`: sửa 3 lỗi phát hiện khi chạy thử 1.33 trên xe.

- **Seat "off" now actually turns the seat off.** Cooling levels 1–2 worked on-car, but cycling a seat to off did nothing — the write path skipped the off level, so `state=1` (off) was never sent. Tapping a seat now writes the current mode's HAL method with the mapped state (off→1 / level-1→2 / level-2→3) for that seat, off included.
- **Text size restored; only height shrunk.** 1.33 scaled everything to ~70% including text, which was hard to read. Text sizes are back to the 1.32 originals (and horizontal metrics restored so text fits); only vertical height (card heights, vertical padding/margins, gauge/dial heights) stays reduced ~70% for the car's large screen.
- **VietMap autostart no longer relaunches when it is already open.** With the bubble enabled, opening ClusterNav relaunched VietMap even when it already had an open activity (wrong behaviour, bubble didn't show). Autostart now checks for an existing VietMap activity record (not just a running process / the resumed app) and skips the relaunch when the activity is already up; the 30 s cooldown + in-flight guard stay.

`1.33` fixes the four on-car findings from the 1.32 test drive, shrinks the UI for the car's large screen, and lands the groundwork for body controls. **(VI)** `1.33`: sửa 4 lỗi phát hiện khi chạy thử 1.32 trên xe + thu nhỏ giao diện cho màn xe + nền cho điều khiển thân xe.

- **Seat cooling/heating now uses the correct HAL.** The 1.32 path wrote a raw feature-id (`0x431010xx`) to the AC device and the HAL returned `NOT_PROVISIONED` on-car even for the front seat. It now drives `BYDAutoSettingDevice.setSeatVentilatingState` / `setSeatHeatingState` (the OEM path) with a 1-based seatID and state 1/2/3 (off / level-1 / level-2); cooling and heating stay mutually exclusive.
- **VietMap autostart no longer loops.** Autostart gained a 30 s cooldown + in-flight guard, skips the relaunch when VietMap is already foreground, and no longer fires on an Activity recreate (theme/language change) — removing the "flash loop" seen on-car.
- **Hero card shows the live street + turn arrow.** `hero_road` now reads the current road and `hero_nav_icon` the live maneuver from the navigation state (they previously showed the source-status line and a static arrow) — a UI-wiring fix; the nav signal itself was already correct.
- **PM2.5 popup-suppress is best-effort.** `enablePurificationFunctionPrompt` is rejected on this trim; it is now logged and never blocks the working auto-clean (`setAutoCleanAirState`, which reads `rc=0` on-car).
- **UI shrunk to ~70% for the car's large screen**, and the doubled hairline above rounded card borders was removed.
- **Body-control core (windows / trunk)** landed over `BYDAutoBodyworkDevice` (`setBodyWindowCtrlState` window 1–4 / state 0–1, `setHetchDoorStatus`) — core only, no UI yet.
- **On-car HAL probe toolkit** (vehicleTest build only, excluded from the release APK) for firing HAL calls / named presets over `adb` while testing on the car.

`1.32` bundles this session's work onto the 1.31 OTA baseline — three new comfort/UX features, a full UI overhaul, bilingual text, and a light mode. **(VI)** `1.32` gộp: 3 tính năng mới + đại tu giao diện "cockpit" + song ngữ Việt/Anh + light mode.

- **Auto seat cooling/heating** — applies a saved per-seat cool/heat level (Seal 2 seats / Han 4 seats) over the BYDAuto AC HAL ~5 s after the app opens / engine start; a top-down seat diagram lets you tap a seat to cycle its level. Cool and heat are mutually exclusive (matches the HAL).
- **PM2.5 auto-filter** — when the cabin PM2.5 level crosses the heavy threshold, purification is auto-enabled (reflection into the AC service); a ring gauge shows the current level.
- **Voice-key binding-status indicator** — shows whether the physical-button → assistant accessibility binding is live, with a "Kiểm tra / Sửa ngay" (Check / Fix now) action that re-grants + rebinds.
- **Level-2 "cockpit" UI overhaul** — a reusable `Cockpit.*` design system matching the visual mockup: hero status cards (turn tile + a real km/h speed dial + a cast split-preview), a two-column feature board of grouped list-rows, and custom seat / PM2.5-gauge / segmented views. Buttons get a compact style; set-once controls are grouped.
- **Bilingual VI/EN** — a runtime view-tree localizer (no `strings.xml` churn, byte-seal intact) plus a **Ngôn ngữ / Language** selector (Theo xe / VI / EN).
- **Light mode** — a full day/night palette (framework theme, no AppCompat) driven by an **Giao diện / Interface** selector (Theo xe / Sáng / Tối); every custom view adapts via `@color`.
- **Hero shows real vehicle speed** — the hero km/h reads the vehicle speed HAL directly (`BYDAutoSpeedDevice`), independent of the navigation source; degrades to "—" off-car.
- **VietMap-bubble autostart fix** — when the bubble is enabled it always launches the VietMap activity then returns to background (the bubble needs the activity open, not just the process) — on-car verify. `1.30` is the project-closeout build — six fixes from the 2026-08-16 on-car session, after which the docs were reorganized and the experiment was wrapped up. **(VI)** `1.30` là bản đóng dự án `clusternav-closeout-1.28` được nâng lên bản cuối (đổi số hiệu, giữ nguyên slug/link). **(EN)** `1.30` is that closeout build promoted to final (renumbered; slug/links kept). The six fixes:

- **Roundabout shows the exit direction + exit number** — the cluster/HUD now shows a roundabout's **exit direction** (left / right / straight / u-turn — CCW by default, CW for left-hand-traffic) and **exit number**, instead of a generic "enter roundabout". Uses directional `Maneuver` members; the CAN turn-id map was cross-validated against OpenBYD `w40` / `HudController` and checked on-car.
- **Less HUD/centre keep-alive churn + faster re-assert (400→250 ms)** — the keep-alive now re-asserts **content only** (icon / distance / road), not status / screen-mode / SDK every tick, and re-asserts faster (**400→250 ms**, the 180 s max-age backstop kept); any residual flicker is the OEM render-layer.
- **Voice-key recovers with an OFF→ON toggle** — if the physical-button → assistant ("Nút vật lý → Trợ lý") stops working after a reboot, flipping it **OFF then ON** resets the accessibility grant and force-rebinds (with a grant timeout so it can't hang) — no app restart.
- **Cluster display-mode selector is now just ON/OFF** — reduced to **"Bật (Giữa + ETA) / Tắt"**; the three dead layout modes (Toàn / Nhỏ / OFF-only) were removed since they can't switch live without root.
- **No more oversea-feature log spam** — non-provisioned oversea features are cached after their first runtime rejection so they stop spamming per-frame logs; cars that do provision oversea (e.g. Sealion 6) still write normally.
- **Boot naviState ordering verified** — confirmed the broadcast `naviState=1` happens-before the HAL write on every frame — no gap, no change needed.

See the [project closeout (1.30)](docs/CLOSEOUT-2026-08-16.md) for the final evaluation and known limitations (notably: the windshield HUD needs a **vehicle** coding flag `0x38B00030=1`, not an app change).

`1.18` adds a physical-button → Kiki mapping and a split-cast re-pin watchdog, plus two carried-in fixes:

- **Steering mic button (long-press = keycode 328) → Kiki** — the "Nút vật lý → Trợ lý" feature gains **Kiki (`ai.zalo.kiki.car`)** as a launch target and makes it the default (default keycode **328**, gesture **Press**); short-press still opens the car's own assistant (小迪). Like the earlier Gemini path this opens the Kiki app — whether it auto-listens is being confirmed on-car.
- **Split-cast re-pin watchdog** — when a cast app is pulled off the cluster (e.g. asking Kiki to navigate with Google Maps launches GMaps' nav on the main display, blanking its cluster slot), ClusterNav now re-casts it back to its slot from the 2 s bubble loop, **debounced + cooldown-guarded** so driving is never yanked on a transient read; CarPlay/Android Auto are skipped. Whether the relaunch preserves the active GMaps navigation (vs. showing the app home) is being confirmed on-car.
- **Accessibility booster self-grant on Nav+HUD** — a reboot clears `enabled_accessibility_services`; turning Nav+HUD on (or opening the app while it is on) now re-grants the screen-read booster over dadb when missing, so distance-tuning ground-truth is no longer silently lost.
- **Marquee-off road names abbreviate** — with the marquee toggle off, long road names are shortened via `NavFormat.fitRoadName` (e.g. "Trần Trọng Kim" → "T.T.Kim") instead of a hard firmware cut.

`1.17` fixes the physical-button → Gemini path found on-car:

- **"Google / Gemini" voice-key target opens Gemini directly** — it now launches the Gemini app (`com.google.android.apps.bard`, which brings up the in-car voice surface) instead of a generic `ACTION_ASSIST` intent that hit a chooser and opened Bluetooth on this head unit. Combined with a long-press-mic mapping (learn the button, gesture **Press** — the firmware emits a distinct code for the hold), the steering-wheel voice button can open Gemini while short-press still opens the car's own assistant. Enabling Gemini as the *system* assistant is a separate device setting; see `docs/diagnostics/gemini-assistant-voicekey-oncar-2026-08-13.md`.

`1.16` applies the first data-driven interp fix from on-car `1.15` logs:

- **Distance-to-turn now rounds like Google** — the cluster distance quantizer **rounds to the nearest step** instead of flooring. On-car data (n=3239 moving samples) showed flooring made the cluster read **~34 m less** than Google Maps (bias piled exactly on the floor buckets −10/−25/−100 m); rounding removes that downward half. The interpolation FACTOR is left unchanged pending the on-screen Google distance now being captured as ground-truth for the next tuning pass.

`1.15` adds two fixes from on-car `1.14` testing:

- **HUD keep-alive** — the windshield HUD / cluster centre ("Giữa + ETA") no longer blanks for ~1s on long straights with no turn. The HAL nav path now has a 400 ms heartbeat that re-asserts the last frame (bypassing dedup), so the OEM display never times out — matching the cluster-lane path which already had one.
- **Turn-distance comparison log** — the nav CSV now records the on-screen Google Maps distance (accessibility ground-truth) next to our interpolation, so the km→turn algorithm can be tuned from data (offline analyzer: `scripts/analyze-nav-distance-log.py`). No interpolation parameters changed yet.

`1.14` fixes five issues found testing 1.12 on the car:

- **HUD turn arrows no longer mirrored.** The windshield HUD reads the CAN turn-id table while the cluster lane uses the AMAP table; the app was sending the AMAP code to the HUD feature, flipping left↔right. It now sends `Maneuver.toHudIcon()` (CAN) to `INSTRUMENT_GUIDE_INFO_SIMPLE_SET`. (Cluster arrows were and stay correct; on-car re-confirms the centre view.)
- **Smooth marquee for long road names** — re-enabled (default on, with a toggle); the scroll offset is now time-based (even, slow) instead of the old uneven per-emission stepping that looked jerky.
- **Interpolated distance steps by 10 m** (was 5 m) to match Google Maps' granularity.
- **Cluster display-mode selector applies immediately** (re-asserts nav status 4→2 on change) and **OFF** now clears the centre-nav instead of writing an ineffective `screen=0`. (Exact value↔menu mapping is still being confirmed on-car.)
- **App auto-opens on car boot** (not just the floating button); the floating bubble starts only when Cluster Cast is enabled.

`1.13` (included) fixed notification-permission granting on the locked IVI and added an optional physical-button voice-assistant trigger:

- **In-app notification-access grant.** The head unit can't open Android's "Notification access" settings screen — a locked-IVI `startActivity` just shows the system toast *"Hệ thống IVI không hỗ trợ hoạt động này."* The listener permission is really an ADB permission (`settings secure enabled_notification_listeners`), so the app now grants it itself over the dadb uid-shell (`cmd notification allow_listener`), the same proven path used for reconnect. The system-settings screen remains only as a last-resort fallback.
- **Nav+HUD defaults OFF.** The master switch now starts **OFF**, so opening the app touches no ADB; the grant + connect run only when you turn Nav+HUD on (fewer concurrent dadb sessions). Once granted, the permission persists across reboots.
- **Physical button → voice assistant (optional, default OFF).** Map a hardware button + gesture (nhấn / nhấn-giữ) to launch a voice assistant (Google/Gemini · BYD 小迪 · speech recognizer). The existing accessibility service captures the key via `onKeyEvent` and **only** consumes the exact configured combo, so the button's native function is preserved; a "learn key" mode captures an unknown keycode on-car.

`1.12` earlier added the in-app **cluster nav-display mode selector** (Đơn giản / Toàn màn hình / Màn hình nhỏ / OFF) that drives the OEM nav-on-cluster setting (`SET_NAVI_SCREEN_STATUS_SET`, `0x4C10E015`) over the BYDAuto HAL, so navigation renders in the cluster **centre** ("Giữa + ETA") instead of only the small top strip — replacing the clusterDebug op39 path (a no-op for the centre view on this trim). The app self-updates **over-the-air**: it polls this repo's `apk/` folder on `main` for a newer `ClusterNav-<ver>-release.apk` and installs it via the on-device dadb loopback (`-r`, same signing key) — no ADB/laptop. To build the same release from source: `./gradlew :app:assembleRelease`. The formal exact-source vehicle candidate is a separate flow (the authorized `collectAuthorizedApk` pipeline; see the build context below).

> ⚠️ **(VI)** Ứng viên vehicle-test `1.04` (`ClusterNav-1.04-v104-527589f2d16a-release.apk`) có trước đợt hardening WARN-1 và từng export bề mặt ADAS/ghi-thiết-bị T10 `TEST_ADAS_*` / `TEST_SPEED_LIMIT` — nó bị **blocklist theo SHA-256 trong on-car install guard** (guard sẽ từ chối) và **không còn được giữ trong `apk/`** (các bản cũ đã cất đi; lịch sử git là bản ghi). **Đừng cài nó.** Bản release `1.30` hiện tại không có bề mặt test nào được export/tiếp cận được (xác minh bằng `aapt2` trên bản build này).
>
> ⚠️ **(EN)** The `1.04` vehicle-test candidate (`ClusterNav-1.04-v104-527589f2d16a-release.apk`) predates the WARN-1 hardening and exported the T10 `TEST_ADAS_*` / `TEST_SPEED_LIMIT` ADAS/instrument-write surface — it is **blocklisted by SHA-256 in the on-car install guard** (which refuses it) and is **no longer kept in `apk/`** (older builds are shelved; git history remains the record). **Do not install it.** The current `1.30` release has no exported/reachable test surface (verified with `aapt2` on this build).

## Chi tiết tính năng · Feature details

**(VI)** Tên nhóm và tên hàng dưới đây lấy **đúng nguyên văn** từ màn Cài đặt (`SettingsCatalogGroups.kt` · `strings_kachi.xml`). Cài đặt có **11 nhóm** trên rail bên trái: Màn hình chính · Thanh trạng thái & thanh nút · Hiển thị & đơn vị · Hồ sơ tài xế · Dẫn đường & cụm đồng hồ · Chiếu màn lên cụm · Phím vô-lăng · Tiện nghi xe · Giọng nói · Hệ thống & quyền · Giới thiệu.

*1 · Màn hình chính & ô*
- **Bố cục** — *Cài đặt › Màn hình chính › Bố cục sẵn*: 1 ô · 2 cột · 2 hàng · 3 ô · 4 ô. *Bố cục tự vẽ* + *Vẽ bố cục riêng…* mở bảng vẽ lưới **12 cột × 6 dòng**: kéo để dời, kéo góc để đổi cỡ, khung đè nhau tô đỏ, nút **Lưu** bị chặn khi bố cục còn lỗi.
- **Nội dung ô** — mỗi ô nhận: widget Kachi dựng tay (Tổng hợp · Xe · pin · đồng hồ · trình chiếu ảnh…), widget Android của app khác, hoặc **một app thật** chạy trong ô qua màn ảo (chạm ô trống → *Mở ứng dụng*). App đóng thì ô hiện *"App đã đóng — chạm để mở lại"*.
- **Thanh trạng thái & thanh nút** — *Cài đặt › Thanh trạng thái & thanh nút*: **Chip thanh trạng thái** · **Hiện nhãn trên thanh trên** (tắt = chỉ icon + số) · **Vị trí trên thanh trên** · **Hiện thanh nút xe** · **Viền đặt thanh nút** (4 viền) · **Nút trên thanh nút xe** · **Vị trí trên thanh nút xe**.
- **Hiển thị & đơn vị** — *Cài đặt › Hiển thị & đơn vị*: **Đơn vị hiển thị** · **Giao diện sáng/tối** · **Màu sắc** · **Ngôn ngữ** (Theo xe / VI / EN) · **Kính thật (làm mờ nền)**.

*2 · Dẫn đường & chiếu cụm*
- **Dẫn đường lên cụm** — *Cài đặt › Dẫn đường & cụm đồng hồ*: **Dẫn đường lên cụm** (công tắc chính) · **Chế độ hiện trên cụm** · **Chạy chữ tên đường** · **App dẫn đường mặc định** · **Kết nối lại nguồn dẫn đường**. Có hai đường ra độc lập: làn zin trên cụm và cụm-giữa "Giữa + ETA", cộng HUD kính lái; hướng rẽ vòng xuyến kèm **số lối ra**.
- **Biển báo tốc độ** — cùng nhóm: **Biển báo tốc độ** · **Giới hạn sắp tới** · **Chip cảnh báo camera** · **Cỡ biển báo** · **Vị trí biển báo** (kéo-thả).
- **Bong bóng VietMap** — **Bong bóng VietMap** · **Vị trí bong bóng**.
- **Sổ địa chỉ** — cùng nhóm: **Sổ địa chỉ** · **Thêm địa chỉ…** — mỗi mục là *tên + văn bản địa chỉ + lat/lng tuỳ chọn*, lưu **theo hồ sơ**; toạ độ dán tay từ app bản đồ (Kachi **không** xin quyền GPS để ghi). Nói *"về nhà"* / *"đi công ty"* là đi.
- **Chiếu màn lên cụm** — *Cài đặt › Chiếu màn lên cụm*: **Bật chiếu màn** · **Hiện nút nổi chiếu cụm** · **Tỉ lệ chia đôi** · **Tự chiếu khi nổ máy** (+ **App tự chiếu toàn màn** / **Tự chiếu chia đôi** / **App bên trái** / **App bên phải**) · **Chiếu ngay: toàn màn, trái, phải, dừng** · **Cứu hộ cụm**. CarPlay / Android Auto luôn chiếu toàn màn.
- **Tự dẫn đường theo lịch** *(🚗)* — **Tự dẫn đường theo lịch**: khung giờ × thứ trong tuần × *chỉ khi có GPS* × một điểm trong **Sổ địa chỉ** × app dẫn đường; mỗi khung chạy **1 lần/ngày**.

*3 · Camera theo xi-nhan (đã đo trên Seal và Sealion 6)*
- Tất cả ở *Cài đặt › Tiện nghi xe › **Camera theo xi-nhan***: **Bật camera khi xi-nhan** · **Hiện camera lên màn cụm** · **Xi-nhan trái hiện ở** / **Xi-nhan phải hiện ở** (góc trên-trái / trên-phải, mặc định trái TL, phải TR) · **Cam xi-nhan trái** / **Cam xi-nhan phải** (id 0–5; Sealion 6 dùng cam 0).
- **Xoay video từng bên** (2.71) — hai hàng **Xi-nhan trái: xoay video** / **Xi-nhan phải: xoay video**, mỗi hàng chọn *Không xoay · ↺ 90° · ↻ 90° · 180°*; mặc định **trái ↺ 90°, phải ↻ 90°** (dải gương cắt từ camera 360 vốn nằm ngang). Hai bên độc lập hoàn toàn. Chiều xoay mặc định **đã xác nhận bằng mắt trên xe Seal** (2026-09-26).
- **Giữ tới khi đèn xi-nhan tắt** (2.70) — trước đó overlay tắt sau ~1 s. [ĐO xe Seal 2026-09-26] mở 57,18 s → đóng 62,41 s, đèn tắt 62,33 s (trễ 87 ms). Kèm lưới an toàn: helper HAL chết giữa lúc đèn còn bật thì Kachi coi như OFF và đóng overlay (2.72, 🚗 chưa chốt trên xe).
- **Khung đúng tỉ lệ** (2.73, 🚗) — ô vuông cũ chỉ còn là *vùng cho phép*; cửa sổ thật là hình lớn nhất **đúng tỉ lệ crop sau khi xoay**, căn giữa ⇒ hết viền đen, hết kéo méo. Xoay ±90° cho khung **ngang**.
- **Kết xuất camera** (2.73, 🚗) — **Kết xuất camera**: *TextureView (mặc định)* | *SurfaceView (nhẹ hơn, xoay nhờ HAL — có thể không xoay)*. Mặc định **không đổi**; đây là cờ để đo nguồn giật khi xe chạy.

*4 · Tiện nghi xe*
- *Cài đặt › Tiện nghi xe* gồm bốn khối: **Lấy gió trong** (*Nổ máy thì tự lấy gió trong* — xe quên chế độ này mỗi lần khởi động) · **Ghế mát / sưởi** (*Tự chỉnh ghế theo nhiệt độ* + *Chế độ*: Làm mát / Sưởi + **mức từng ghế**, chạm sơ đồ ghế: tắt → mức 1 → mức 2; mát và sưởi loại trừ nhau theo HAL) · **Lọc bụi mịn** (*Tự lọc khi không khí bẩn* + **Lọc ngay một lượt** + dòng *Bụi mịn hiện tại*) · **Tự sấy kính khi mưa**.
- **Mưa thì tự bật sấy kính** *(🚗 chưa gặp buổi mưa thật)* — đọc cảm biến mưa mỗi 5 phút; chọn **Sấy kính trước** và/hoặc **Sấy kính sau + gương**; hết mưa thì tắt, và **chỉ tắt cái Kachi bật** — bạn tự bật thì Kachi không đụng.
- **Nút xe khác** — kính từng cửa, mở/đóng hết kính, cốp, cửa sổ trời, đèn, điều hoà (AUTO / nhiệt độ / gió), lọc khí… đều là nút của **một bộ đăng ký duy nhất** (`ControlRegistry`), nên hiện được trên **thanh nút xe**, đọc được trạng thái, và gọi được **bằng giọng** — không có bảng thứ hai. Status thật từng nút (🟢 / ⚠ / ❌ / 🚗) ở [`docs/kachi-feature-catalog.html`](docs/kachi-feature-catalog.html).

*5 · Giọng nói*
- **Nghe tại máy** — *Cài đặt › Giọng nói › Nhận dạng giọng nói (tại máy)*: **Tải mô hình tiếng Việt** (sherpa-onnx + `zipformer-vi`, ~266 MB, sha256 ghim) · **Cập nhật mô hình** · **Gỡ mô hình**. Xe không có mạng: chép cả cây thư mục gói vào `Android/data/com.byd.launcher/files/sherpa/import/<gói>/` rồi bấm Tải — máy vẫn kiểm sha256. Không gửi tiếng nói ra mạng.
- **Ba lối gọi** — ô *Nói với xe* trên màn chính · **nút mic trên thanh trạng thái** · phím vô-lăng gán đích *Kachi nghe (tại máy)* (*Cài đặt › Phím vô-lăng*). Tấm chữ voice là **cửa sổ độc lập**: đang mở app khác toàn màn thì chỉ overlay lên, không kéo launcher ra trước.
- **"Hey Kachi"** *(thử nghiệm, mặc định tắt)* — *Cài đặt › Giọng nói › Hey Kachi*: **"Hey Kachi" — gọi bằng giọng** + **Cách nghe "Hey Kachi": ASR (không cần train)**. Nghe nền khi màn sáng; nghe nhầm nhiều lần thì tự tắt kèm thông báo. Khi bật, nút mic cũng đi qua tiến trình nghe nên **không phải chờ nạp mô hình** lần đầu.
- **Giọng đọc + xác nhận** — **Đọc phản hồi bằng giọng** · **Ưu tiên giọng offline** · **Giọng đọc offline (tại máy)** (gói Piper, tải/cập nhật/gỡ, side-load được) · **App nhạc mặc định** · **Hỏi xác nhận trước khi chạy** (mặc định **rỗng** = Kachi chạy luôn; tự tích việc nào thì việc đó hỏi lại) · **Đọc to câu hỏi xác nhận** · **Nguồn micro** (*Tự chọn* thử MIC trước rồi tới nguồn có khử ồn).
- **Nhóm lệnh** — nút xe (*"bật đèn đọc"*, *"mở kính lái"*, *"đặt nhiệt độ hai mươi bốn độ"*, *"tăng gió"*) · gói lệnh (*"đóng hết kính"*) · đọc thông tin (*"xem pin"*, *"nhiệt độ ngoài trời bao nhiêu"*, *"đọc tầm hoạt động"*) · app (*"mở YouTube"*, *"đóng YouTube"*, *"mở YouTube vào ô số 9"*) · nhạc (*"phát bài Diễm Xưa"*, *"bài tiếp theo"*) · dẫn đường (*"dẫn đường tới chợ Bến Thành bằng Waze"*) · hồ sơ (*"chuyển sang hồ sơ Test"*) · bố cục (*"đổi bố cục 4 ô"*) · câu ghép với *và* / *rồi*.
- **Nghe chắc hơn ở 2.73** *(🚗 chưa chốt bằng giọng thật trên xe)* — vế *"vào ô số N"* không còn rụng; tên app nghe lệch nhẹ (*"youtubex"*, *"vietp"*) vẫn khớp; tên hồ sơ tiếng Anh vẫn khớp, thiếu tên thì Kachi **hỏi lại**. Bảng đầy đủ câu ↔ phản hồi thật ở [`docs/kachi-feature-catalog.html`](docs/kachi-feature-catalog.html).
- **Huỷ lượt nghe** — **chạm ra ngoài tấm chữ**, hoặc chờ trần 8 s. Từ **2.73 nút Back không còn huỷ** (nó đi tới app phía sau) — đổi có chủ ý để thanh điều hướng của xe không trồi lên lúc Kachi đọc phản hồi.

*6 · Hồ sơ tài xế*
- *Cài đặt › Hồ sơ tài xế*: **Danh sách hồ sơ** · **Hồ sơ đang dùng** · **Hồ sơ lúc nổ máy** (*Gần nhất* hoặc một hồ sơ cố định) · **Thêm hồ sơ (bản sao)**; đổi tên và xoá ở nút cạnh từng hồ sơ.
- Mọi thiết lập ở Cài đặt lưu **theo hồ sơ**, **trừ** nhóm *Hệ thống & quyền*, khung hình khi chiếu lên cụm, và *Giới thiệu*.
- **Xuất hồ sơ (backup)** / **Nhập hồ sơ từ file** qua `Android/data/com.byd.launcher/files/profiles/`; trùng tên tự thành `<tên> 2`, `<tên> 3`… Đổi nhanh bằng chip hồ sơ trên thanh trên hoặc bằng giọng.

*7 · Ảnh xe · hình nền · trình chiếu*
- Ba thư mục trên thẻ, **không cần quyền**: `files/car/` (ảnh xe top-down) · `files/wallpapers/` (hình nền) · `files/photos/` (widget trình chiếu). Mỗi hàng trong *Cài đặt › Màn hình chính* có nút **Sao chép đường dẫn thư mục**.
- Chưa có ảnh riêng thì Kachi vẽ **hình xe bằng vector** (chọn model và màu sơn); ảnh xe mặc định là `seal-3`.

*8 · Automation*
- **Mưa thì tự bật sấy kính** (🚗) và **Tự dẫn đường theo lịch** (🚗) — xem nhóm 4 và 2. Cả hai chạy trên một nhịp nền nhẹ và **chỉ hoàn tác cái mình bật**.
- **Khởi động theo xe** — *Cài đặt › Hệ thống & quyền*: **Tự mở Kachi khi nổ máy** · **Chạy dịch vụ nền khi nổ máy** · **Giữ Kachi làm màn hình chính khi nổ máy** (mặc định tắt).

*9 · Hệ thống & quyền · Giới thiệu*
- **Quyền còn thiếu** — app tự cấp qua dadb uid-shell (notification listener, cửa sổ nổi…), không cần laptop; màn cài đặt hệ thống chỉ là phương án dự phòng vì ROM DiLink chặn nhiều màn đó.
- **Màn hình chính** — **Đặt Kachi làm màn hình chính** / **Bỏ chọn Kachi làm màn hình chính**. ROM BYD không hiện hộp chọn HOME nên Kachi tự đặt qua dadb loopback; bỏ chọn thì về launcher gốc và giữ qua lần nổ máy sau.
- **Cập nhật** — **Kiểm tra cập nhật** + **Tự động cập nhật** (mở Kachi thì tự dò; có bản mới sẽ **hỏi trước** khi tải và cài đè; không có bản mới thì im lặng).
- **Bảo trì** — **Khởi động lại launcher** · **Dừng toàn bộ dẫn đường** · **Cứu hộ cụm**.
- **Nâng cao** — màn ClusterNav cũ (chỉ còn là "màn nâng cao"), **Chẩn đoán cụm** (`DiagActivity` tự chụp dữ liệu, anh em chỉ cần gửi ảnh màn), **Kiểm tra từng chức năng xe** (chạy → OK/Không OK → ghi báo cáo), **Gõ lệnh chữ** (thử bộ hiểu ý không cần nói), **Nhận dạng tệp WAV thử**.
- **Chế độ kiểm thử qua adb** — cho máy tính gửi lệnh thử vào Kachi (nói một câu, đổi hồ sơ, gắn app vào ô, đọc trạng thái). Chỉ bật được **bằng tay trong xe**, **tự tắt sau 60 phút**, và **chết theo lần nổ máy**; mọi lệnh đều được ghi nhật ký.
- **Giới thiệu** — **Phiên bản và giấy phép** · **Miễn trừ trách nhiệm**. Số hiệu bản hiện cả ở đây, trong log phiên và trong tên tệp log.

*10 · Hiệu năng & ranh giới an toàn*
- **Đọc HAL theo nhu cầu** — vòng poll chỉ đọc datum mà màn hình đang bày; datum đọc ra `null` ba lần thì nguội và thử lại giãn dần (60 s → ×2 → trần 10 phút) — cố ý **không** cấm vĩnh viễn vì `null` không chứng minh *"xe không có"*.
- **Tiết chế log + ngân sách shell** — dòng log trùng y nguyên trong 10 s gộp lại kèm hậu tố *[+N lặp]* (mức W/E/F/A **không bao giờ** bị bỏ); số lệnh shell mỗi phút có trần.
- **Ranh giới cứng** — **không** mock location / dead-reckon (đã gỡ hẳn 2026-07-27, quyền location chỉ **ĐỌC**); guard đặt ở **tầng thi hành** chứ không ở UI; mọi thứ đổi ra ngoài tiến trình đều có đường trả lại chạy được cả khi tiến trình đã chết.

**(EN)** The group and row names below are quoted **verbatim** from the Settings screen (`SettingsCatalogGroups.kt` · `strings_kachi.xml`). Settings has **11 groups** in the left rail: Home screen · Status bar & button bar · Display & units · Driver profiles · Navigation & cluster · Cluster cast · Steering-wheel keys · Car comfort · Voice · System & permissions · About.

*1 · Home screen & slots*
- **Layout** — *Settings › Home screen › Preset layout*: 1 slot · 2 columns · 2 rows · 3 slots · 4 slots. *Custom layout* + *Draw your own layout…* opens a **12 × 6 grid** editor: drag to move, drag the corner to resize, overlapping frames turn red, **Save** is blocked while the layout is invalid.
- **Slot contents** — each slot takes a hand-built Kachi widget (Board · Car · battery · clock · photo slideshow…), another app's Android widget, or **a real app** running in the slot on a virtual display (tap an empty slot → *Open app*). When the app closes the slot reads *"App closed — tap to reopen"*.
- **Status bar & button bar** — *Settings › Status bar & button bar*: **Status-bar chips** · **Show chip labels** (off = icon + value only) · **Status-bar item order** · **Show the car bar** · **Button bar edge** (any of 4) · **Buttons on the car bar** · **Car-bar item order**.
- **Display & units** — *Settings › Display & units*: **Display units** · **Light / dark theme** · **Colours** · **Language** (By-car / VI / EN) · **Real glass (blur the backdrop)**.

*2 · Navigation & cluster casting*
- **Navigation on the cluster** — *Settings › Navigation & cluster*: **Navigation on the cluster** (master switch) · **Cluster display mode** · **Scroll long street names** · **Default navigation app** · **Reconnect the navigation source**. Two independent outputs — the stock cluster lane and the cluster centre "Giữa + ETA" — plus the windshield HUD; roundabouts carry the **exit number**.
- **Speed badge** — same group: **Speed limit badge** · **Upcoming limit** · **Camera alert chip** · **Badge size** · **Badge position** (drag).
- **VietMap bubble** — **VietMap bubble** · **Bubble position**.
- **Address book** — **Address book** · **Add an address…** — each entry is *name + address text + optional lat/lng*, stored **per profile**; coordinates are pasted by hand from a map app (Kachi asks for **no** GPS write permission). Say *"về nhà"* / *"đi công ty"* and it navigates.
- **Cluster cast** — *Settings › Cluster cast*: **Enable casting** · **Show the floating cast button** · **Split ratio** · **Autostart on engine start** (+ **Full-screen autostart app** / **Autostart split view** / **Left-hand app** / **Right-hand app**) · **Cast now: full, left, right, stop** · **Cluster rescue**. CarPlay / Android Auto always cast full-screen.
- **Scheduled navigation** *(🚗)* — time window × weekdays × *only with GPS* × one **Address book** entry × a nav app; fires **once per window per day**.

*3 · Turn-signal camera (measured on Seal and Sealion 6)*
- All under *Settings › Car comfort › **Turn-signal camera***: **Camera on turn signal** · **Show the camera on the cluster** · **Left signal shows at** / **Right signal shows at** (top-left / top-right; default left TL, right TR) · **Left signal camera** / **Right signal camera** (id 0–5; Sealion 6 uses cam 0).
- **Per-side rotation** (2.71) — two rows **Left signal: rotate video** / **Right signal: rotate video**, each *No rotation · ↺ 90° · ↻ 90° · 180°*; defaults **left ↺ 90°, right ↻ 90°** (the mirror crop of the 360 camera is sideways). The sides are fully independent. The default directions were **confirmed by eye on a Seal** (2026-09-26).
- **Held until the signal lamp goes off** (2.70) — the overlay used to close after ~1 s. [measured, Seal 2026-09-26] shown at 57.18 s → closed at 62.41 s, lamp off at 62.33 s (87 ms lag). Plus a safety net: if the HAL helper dies while the lamp is still on, Kachi treats it as OFF and closes the overlay (2.72, 🚗 not yet confirmed on a car).
- **Aspect-correct frame** (2.73, 🚗) — the old square is now only the *allowed area*; the real window is the largest rectangle **matching the crop's aspect after rotation**, centred ⇒ no black bars, no stretching. A ±90° rotation gives a **landscape** frame.
- **Camera rendering** (2.73, 🚗) — **Camera rendering**: *TextureView (default)* | *SurfaceView (lighter, rotates via the HAL — may not rotate)*. The default is **unchanged**; this is a flag for measuring the source of the stutter while driving.

*4 · Car comfort*
- *Settings › Car comfort* has four blocks: **Recirculation** (*Recirculation on engine start* — the car forgets it every start) · **Seat cool / heat** (*Adjust seats by temperature* + *Mode*: Cool / Heat + **a level per seat**; tap the seat diagram: off → level 1 → level 2; cool and heat are mutually exclusive per the HAL) · **PM2.5 filter** (*Purify when the air is dirty* + **Purify now** + a *current dust level* line) · **Auto-defrost when it rains**.
- **Rain → defrost** *(🚗 no real rain session yet)* — polls the rain sensor every 5 min; pick **Front windscreen defrost** and/or **Rear + mirrors**; turns off when the rain stops, and **only turns off what Kachi turned on** — if you switched it on, Kachi leaves it alone.
- **Other car buttons** — individual windows, all windows, trunk, sunroof, lights, AC (AUTO / temperature / fan), air purification… all come from **one registry** (`ControlRegistry`), so they can appear on the **car button bar**, be read back, and be driven **by voice** — there is no second table. Per-button on-car status (🟢 / ⚠ / ❌ / 🚗) lives in [`docs/kachi-feature-catalog.html`](docs/kachi-feature-catalog.html).

*5 · Voice*
- **On-device recognition** — *Settings › Voice › Speech recognition (on-device)*: **Download the Vietnamese model** (sherpa-onnx + `zipformer-vi`, ~266 MB, sha256-pinned) · **Update** · **Remove**. With no network in the car, copy the whole pack tree into `Android/data/com.byd.launcher/files/sherpa/import/<pack>/` and press Download — the sha256 check still runs. No audio leaves the car.
- **Three ways in** — the *Talk to the car* tile on the home screen · the **mic button in the status bar** · a steering-wheel key bound to *Kachi listens (on-device)* (*Settings › Steering-wheel keys*). The voice card is an **independent window**: over a full-screen app it only overlays, it does not pull the launcher forward.
- **"Hey Kachi"** *(experimental, default off)* — *Settings › Voice › Hey Kachi*: the wake-word switch plus **Wake engine: ASR (no training needed)**. Listens in the background while the screen is on; auto-disables with a notice after repeated false accepts. With it on, the mic button also goes through the listening process, so the **first press no longer waits for a model load**.
- **Reply voice + confirmation** — **Speak replies out loud** · **Prefer the offline voice** · **Offline voice pack (on-device)** (Piper; download / update / remove, side-loadable) · **Default music app** · **Ask before running** (default **empty** = Kachi just runs; tick an action and that action asks first) · **Read confirmation questions aloud** · **Microphone source** (*Auto* tries MIC first, then a noise-cancelling source).
- **Command groups** — car buttons (*"bật đèn đọc"*, *"mở kính lái"*, *"đặt nhiệt độ hai mươi bốn độ"*, *"tăng gió"*) · macros (*"đóng hết kính"*) · read vehicle data (*"xem pin"*, *"nhiệt độ ngoài trời bao nhiêu"*, *"đọc tầm hoạt động"*) · apps (*"mở YouTube"*, *"đóng YouTube"*, *"mở YouTube vào ô số 9"*) · music (*"phát bài Diễm Xưa"*, *"bài tiếp theo"*) · navigation (*"dẫn đường tới chợ Bến Thành bằng Waze"*) · profiles (*"chuyển sang hồ sơ Test"*) · layouts (*"đổi bố cục 4 ô"*) · compound sentences with *và* / *rồi*.
- **More robust in 2.73** *(🚗 not yet confirmed by live speech on a car)* — the *"into slot N"* clause no longer drops; slightly misheard app names (*"youtubex"*, *"vietp"*) still resolve; English profile names resolve, and a missing name makes Kachi **ask back**. The full sentence ↔ real-reply table is in [`docs/kachi-feature-catalog.html`](docs/kachi-feature-catalog.html).
- **Cancelling a turn** — **tap outside the card**, or wait out the 8 s ceiling. Since **2.73 Back no longer cancels** (it goes to the app behind) — a deliberate change so the car's navigation bar stops popping up while Kachi speaks.

*6 · Driver profiles*
- *Settings › Driver profiles*: **Profile list** · **Active profile** · **Profile on engine start** (last used, or a fixed one) · **Add profile (a copy)**; rename and delete sit next to each profile.
- Every Settings value is stored **per profile**, **except** *System & permissions*, the cluster-cast geometry, and *About*.
- **Export (backup)** / **Import from file** via `Android/data/com.byd.launcher/files/profiles/`; duplicate names become `<name> 2`, `<name> 3`… Switch quickly from the profile chip in the top bar or by voice.

*7 · Car image · wallpapers · slideshow*
- Three folders on the SD card, **no permission needed**: `files/car/` (top-down car image) · `files/wallpapers/` · `files/photos/` (slideshow widget). Every row in *Settings › Home screen* carries a **Copy folder path** button.
- With no image of your own, Kachi draws **vector car artwork** (pick the model and paint colour); the default car image is `seal-3`.

*8 · Automation*
- **Rain → defrost** (🚗) and **Scheduled navigation** (🚗) — see groups 4 and 2. Both run on one light background tick and **only undo what they turned on**.
- **Start with the car** — *Settings › System & permissions*: **Auto-start Kachi on engine start** · **Run background service on engine start** · **Keep Kachi as home screen on engine start** (default off).

*9 · System & permissions · About*
- **Missing permissions** — the app self-grants over the dadb uid-shell (notification listener, floating window…), no laptop needed; the system settings screen is only a fallback because the DiLink ROM blocks many of those screens.
- **Home screen** — **Set Kachi as home** / **Unset Kachi as home**. The BYD ROM shows no HOME chooser, so Kachi sets it over the dadb loopback; unsetting returns to the stock launcher and survives the next start.
- **Update** — **Check for updates** + **Auto update** (checks when you open Kachi; **asks first** before downloading and installing over the top; stays silent when there is nothing new).
- **Maintenance** — **Restart launcher** · **Stop all navigation** · **Cluster rescue**.
- **Advanced** — the old ClusterNav screen (now just an "advanced screen"), **Cluster diagnostics** (`DiagActivity` captures the data itself — testers only send a screenshot), **Per-feature car capability test** (run → OK / not OK → write a report), **Type a command** (test the intent parser without speaking), **Recognise a test WAV**.
- **ADB test mode** — lets a computer send test commands into Kachi (say a sentence, switch profile, pin an app into a slot, read state). It can only be switched on **by hand in the car**, **self-expires after 60 min**, and **dies with the ignition cycle**; every command is journalled.
- **About** — **Version and licence** · **Disclaimer**. The version appears here, in the session log, and in the log file name.

*10 · Performance & safety boundaries*
- **Demand-gated HAL reads** — the poll loop only reads the data the screen is showing; a datum that reads `null` three times goes cold and is retried with backoff (60 s → ×2 → 10 min ceiling) — deliberately **not** banned for good, because `null` does not prove *"this car does not have it"*.
- **Log throttling + shell budget** — identical log lines within 10 s collapse with a *[+N repeats]* suffix (W/E/F/A are **never** dropped); the number of shell commands per minute is capped.
- **Hard boundaries** — **no** mock location / dead-reckoning (removed for good on 2026-07-27; location permission is **read-only**); guards live in the **execution layer**, not the UI; anything changed outside the process has a restore path that works even after the process has died.

> ⚠️ **(VI)** Đây là một thử nghiệm sở thích. Không cam kết an toàn lái xe, tương thích, khả năng hoàn tác hay sẵn sàng sản xuất. Cài đặt tự chịu rủi ro. Không liên kết với BYD.
>
> ⚠️ **(EN)** This is a hobby experiment. No driving-safety, compatibility, reversibility, or production-readiness claim. Install at your own risk. Not affiliated with BYD.

## Documentation · Tài liệu

**(VI)** Bộ tài liệu canonical (song ngữ khi hướng đến người dùng):

**(EN)** The canonical documentation set (bilingual where user-facing):

**Kachi (hiện hành · current):**

- [Docs index (INDEX canonical)](docs/README.md) — bản đồ MỌI tài liệu hiện hành theo 9-loại taxonomy. Đọc file này trước, rồi mở doc cụ thể. · The canonical map of every current document — read it first.
- [Hướng dẫn dùng Kachi · Kachi user guide](docs/HUONG-DAN-KACHI.md) — cài/OTA · đặt HOME · màn hình chính · hồ sơ · cấu hình từng nhóm Cài đặt · **bảng lệnh giọng nói theo nhóm** · camera xi-nhan · automation · lấy log · FAQ (VI + EN).
- [Danh mục chức năng (máy sinh) · Feature catalog](docs/kachi-feature-catalog.html) — bảng **mọi** chức năng: diễn giải · voice command · phản hồi thật · **status trên xe** (🟢 / ⚠ / ❌ / 🚗). Sinh bằng `scripts/docs/feature-catalog.py` từ bộ đăng ký + `docs/catalog/*.json`.
- [Project backlog](docs/PROJECT-BACKLOG.md) — nguồn task **DUY NHẤT** + nhật ký theo ngày. · The single source of tasks.
- [Kachi project closeout (2.66)](docs/CLOSEOUT-2026-09-25.md) — final state, architecture, known limitations, build/OTA, open items (VI + EN).

**ClusterNav (lineage · historical, giữ làm ngữ cảnh):**

- [Project closeout (1.30, ClusterNav)](docs/CLOSEOUT-2026-08-16.md) — final evaluation, the six 1.30 fixes, and honest known limitations (VI + EN).
- [User guide (1.30 ClusterNav)](docs/HUONG-DAN.md) — ClusterNav 1.30 usage; superseded for Kachi by `docs/HUONG-DAN-KACHI.md`.
- [Two-track final plan](docs/specs/clusternav-two-track-final-plan.html) — derived orchestration and evidence gates.
- [Cluster Cast re-baseline](docs/specs/cluster-cast-rebaseline.html) — canonical Cast contracts.
- [Navigation/UX re-baseline](docs/specs/clusternav-uxui-rebaseline.html) — two-card target UX and Navigation contracts.
- [Dead Reckon revalidation](docs/specs/dead-reckon-revalidation.html) — the REMOVE decision and deferred review debt.
- [Vehicle Test V2 checklist](docs/diagnostics/VEHICLE-TEST-V2.md) — prepared operator scripts and Stage 11 matrix; execution remains NOT STARTED.

**(VI)** Các handoff phiên làm việc và review lịch sử nay nằm trong `docs/archive/` (lịch sử git được giữ nguyên). Các file cũ hơn trong `docs/diagnostics/`, `docs/reference/`, và các spec trước đây mô tả các bản build hoặc điều tra lịch sử — chỉ là ngữ cảnh, trừ khi một spec hiện hành promote một mục thành cổng exact-source/exact-build mới.

**(EN)** Historical session handoffs and reviews now live under `docs/archive/` (git history preserved). Older files under `docs/diagnostics/`, `docs/reference/`, and previous specs describe historical builds or investigations. They are context only unless a current spec explicitly promotes an item into a new exact-source/exact-build gate.

## Developer build context · Ngữ cảnh build cho lập trình viên

**(VI)** Dự án Android dùng JDK 17 và Android SDK compileSdk/targetSdk 37, minSdk 29 (build-tools 36). Hệ Cast dùng kiến trúc projection-first đơn giản hoá: mô hình 4 trạng thái (IDLE → PROJECTING → CASTING → RETURNING), một nút nổi duy nhất để cast/return, không có state machine hay pipeline khôi phục phức tạp. Build bằng `./gradlew :app:assembleRelease`.

**(EN)** The Android project uses JDK 17 and Android SDK compileSdk/targetSdk 37, minSdk 29 (build-tools 36). The Cast subsystem uses a simplified projection-first architecture: 4-state model (IDLE → PROJECTING → CASTING → RETURNING), single floating button for cast/return, no complex state machines or recovery pipelines. Build with `./gradlew :app:assembleRelease`.

## Safety and evidence boundaries · Ranh giới an toàn và bằng chứng

**(VI)**
- Bắt buộc reboot bằng nút nguồn vật lý khi một bài test yêu cầu reboot head-unit thật; `adb reboot` không được chấp nhận là bằng chứng tương đương.
- Không merge vào `main` trước khi có PASS exact-build trên xe và uỷ quyền merge rõ ràng.
- Không commit/push khi chưa chạy quét dữ liệu nhạy cảm bắt buộc cho public-repository.
- Kết quả helper/unit lịch sử không thể đóng các cổng V2, UX, release hay vehicle hiện hành.

**(EN)**
- Physical power-button reboot is required when a test calls for a real head-unit reboot; `adb reboot` is not accepted as equivalent evidence.
- No merge to `main` before final exact-build on-car PASS and explicit merge authorization.
- No commit/push without the mandatory public-repository sensitive-data scan.
- Historical helper/unit results cannot close current V2, UX, release or vehicle gates.

## Credits · Ghi công

**(VI)** Xem [CREDITS.md](CREDITS.md). Dự án dùng [`dadb`](https://github.com/mobile-dev-inc/dadb) theo giấy phép Apache-2.0.

**(EN)** See [CREDITS.md](CREDITS.md). The project uses [`dadb`](https://github.com/mobile-dev-inc/dadb) under Apache-2.0.

## License · Giấy phép

[MIT](LICENSE).
