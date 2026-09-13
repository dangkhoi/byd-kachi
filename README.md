# ClusterNav

> [!IMPORTANT]
> **(VI) ĐÂY LÀ `byd-launcher` — BẢN FORK của ClusterNav 2.0 (`byd-cluster-2`) để phát triển dần thành LAUNCHER cho xe BYD DiLink.** Tên launcher: **Kachi** (repo `github.com/dangkhoi/byd-kachi`; nhãn app hiện là "Kachi"). Mục tiêu: GIỮ NGUYÊN mọi tính năng ClusterNav (gom vào mục **Cài đặt / Settings**) + màn hình chính (**HOME**) + nhiều app/tiện ích đi kèm kiểu Dudu (control cửa–kính–đèn–gạt mưa–AC, widget đồng hồ, lưới app…). `applicationId` đổi thành **`com.byd.launcher`** để cài **SONG SONG**, KHÔNG đụng bản ClusterNav 2.0 (`com.byd.clusternav2`) đang chạy ổn trên xe. Fork từ ClusterNav **v1.38 (versionCode 39)**. Lộ trình: `docs/specs/launcher-foundation.html` + `docs/PROJECT-BACKLOG.md`. Phần README bên dưới kế thừa từ ClusterNav, sẽ viết lại dần cho launcher.
>
> **(EN) THIS IS `byd-launcher` — a FORK of ClusterNav 2.0 (`byd-cluster-2`) being grown into a BYD DiLink car LAUNCHER.** The launcher is named **Kachi** (repo `github.com/dangkhoi/byd-kachi`; the app label now reads "Kachi"). Goal: KEEP every ClusterNav feature (consolidated into a **Settings** area) + a **HOME** screen + many bundled apps/utilities (Dudu-style: window/door/light/wiper/AC controls, gauge widgets, an app grid…). `applicationId` is changed to **`com.byd.launcher`** so it installs **SIDE BY SIDE**, leaving the stable ClusterNav 2.0 (`com.byd.clusternav2`) on the car untouched. Forked from ClusterNav **v1.38 (versionCode 39)**. Roadmap: `docs/specs/launcher-foundation.html` + `docs/PROJECT-BACKLOG.md`. The README below is inherited from ClusterNav and will be rewritten for the launcher over time.

> Song ngữ: các mục hướng đến người dùng viết tiếng Việt trước, English sau. Changelog theo phiên bản giữ nguyên tiếng Anh, có một dòng dẫn tiếng Việt.
> Bilingual: user-facing sections are Vietnamese first, then English. Per-version changelog entries stay in English, with a one-line Vietnamese intro.

> [!CAUTION]
> **(VI) KACHI LAUNCHER — bản hiện tại: `1.40` (versionCode 41, 2026-09-13)** — `com.byd.launcher`, MỘT icon "Kachi" duy nhất (LAUNCHER = KachiHomeActivity), mọi cấu hình ClusterNav đã gộp vào Kachi Settings (10 nhóm); màn ClusterNav cũ chỉ còn là "màn nâng cao" mở từ Hệ thống › Nâng cao. Dòng dưới là trạng thái nền tảng ClusterNav 2.0 kế thừa.
> **(EN) KACHI LAUNCHER — current: `1.40` (versionCode 41, 2026-09-13)** — `com.byd.launcher`, a single "Kachi" icon (LAUNCHER = KachiHomeActivity); every ClusterNav setting now lives in Kachi Settings (10 groups); the old ClusterNav screen is an "advanced screen" opened from System › Advanced. The lines below describe the inherited ClusterNav 2.0 baseline.
>
> **(VI) TRẠNG THÁI HIỆN TẠI: `1.38` (versionCode 39) — app ĐỘC LẬP "Cluster Nav 2.0" (`com.byd.clusternav2`), TÁCH HOÀN TOÀN khỏi app cũ ClusterNav (`com.byd.clusternav`): khác package + khác khoá ký → cài SONG SONG trên xe, không đụng/ghi đè app cũ. Nền tảng mới `byd-cluster-2` (fork từ codebase 1.30, đã khôi phục tín hiệu Waze/VietMap); bản tự-cập-nhật OTA để thử nghiệm.** Các bộ test core/app/car-integration + offcar-planner build **xanh off-car** và release APK build sạch, đã ký; mọi bề mặt test/ghi-thiết-bị (bộ dò T10) chỉ nằm trong build type `vehicleTest` — release APK **không có bề mặt test nào được export/tiếp cận được** (xác minh bằng `aapt2` trên bản build này). Từ `1.11`, chủ sở hữu đăng mỗi `apk/ClusterNav-<ver>-release.apk` lên `main` để app **tự cập nhật qua mạng (OTA)** xuống xe để thử — không cần ADB/laptop. **Đây là kênh thử nghiệm trên xe của riêng chủ sở hữu, KHÔNG phải bản phát hành công khai được hỗ trợ, và tách biệt với quy trình ứng viên exact-source `collectAuthorizedApk` / Stage-11 (vốn là một cổng riêng).** Đây là một thử nghiệm sở thích, không cam kết an toàn lái xe, tương thích, khả năng hoàn tác hay mức độ sẵn sàng sản xuất — cài đặt tự chịu rủi ro. Ứng viên vehicle-test `1.04` vẫn bị **blocklist theo SHA-256** trong on-car guard (nó từng export bề mặt T10 `TEST_ADAS_*` / `TEST_SPEED_LIMIT`) và **không còn được giữ trong `apk/`**; bản release hiện tại không export bề mặt nào như vậy.
>
> **(EN) CURRENT STATUS: `1.38` (versionCode 39) — STANDALONE app "Cluster Nav 2.0" (`com.byd.clusternav2`), FULLY SEPARATE from the legacy ClusterNav app (`com.byd.clusternav`): different package + different signing key → installs SIDE BY SIDE on the car, never overwrites the old app. New `byd-cluster-2` baseline (fork of the 1.30 codebase with Waze/VietMap signals revived); OTA self-test build.** The core/app/car-integration + offcar-planner suites **build green off-car** and the release APK builds cleanly and is signed, with all test/instrument-write surfaces (the T10 probe harness) confined to the `vehicleTest` build type — the release APK has **no exported/reachable test surface** (verified with `aapt2` on this build). From `1.11` the owner publishes each plain `apk/ClusterNav-<ver>-release.apk` to `main` so the app self-updates **over-the-air (OTA)** onto the car for testing — no ADB/laptop needed. **This is the owner's own iterative on-car test channel, not a supported public release, and is separate from the formal exact-source `collectAuthorizedApk` / Stage-11 candidate process (which remains its own distinct gate).** It is a hobby experiment with no driving-safety, compatibility, reversibility, or production-readiness claim — install at your own risk. The `1.04` vehicle-test candidate stays **blocklisted by SHA-256** in the on-car guard (it exported the T10 `TEST_ADAS_*` / `TEST_SPEED_LIMIT` surface) and is **no longer kept in `apk/`**; the current release exports no such surface.

**(VI)** ClusterNav là một thử nghiệm cá nhân mang tính sở thích của **Đăng Khôi · `dangkhoi`**, để khám phá dẫn đường và chiếu màn hình lên cụm đồng hồ trên phần cứng BYD DiLink. Dự án không liên kết với BYD và không đưa ra cam kết nào về an toàn lái xe, tương thích, khả năng hoàn tác hay mức độ sẵn sàng sản xuất.

**(EN)** ClusterNav is a personal hobby experiment by **Đăng Khôi · `dangkhoi`** for exploring navigation and cluster projection on BYD DiLink hardware. It is not affiliated with BYD and makes no driving-safety, compatibility, reversibility, or production-readiness claim.

## Tính năng · Features

**(VI)** Mục lục tính năng — chi tiết từng mục ở phần **Chi tiết tính năng** bên dưới.

*Dẫn đường & chiếu cụm*
- **Navigation + HUD** — dẫn đường trên cụm (làn zin + "Giữa + ETA") và HUD kính lái; hướng rẽ vòng xuyến + số lối ra; chọn chế độ cụm ON/OFF; chạy chữ tên đường dài
- **Cluster Cast** — chiếu app đang mở lên cụm: full hoặc chia đôi chỉnh tỉ lệ (1:9–9:1); CarPlay & Android Auto full-screen; tự chiếu app khi khởi động; watchdog giữ cụm
- **Biển báo tốc độ trên cụm** — hiện tốc độ/giới hạn + giới hạn sắp tới + chip cảnh báo/camera VietMap; chỉnh cỡ và vị trí
- **Bóng VietMap trên cụm** — hiện bóng VietMap trên cụm, kéo-thả chỉnh vị trí

*Tiện nghi cabin (mới ở 1.32)*
- **Ghế mát / sưởi tự động** — tự áp mức mát/sưởi từng ghế qua HAL điều hoà; sơ đồ ghế chạm để chọn mức
- **Tự lọc bụi mịn PM2.5** — tự bật lọc khí khi bụi vượt ngưỡng; đồng hồ hiển thị mức

*Trợ lý & hệ thống*
- **Nút vật lý → trợ lý giọng nói** — gán nút cứng (học phím, gán nhiều nút) mở Google/Gemini · BYD 小迪 · Kiki · speech; chỉ báo trạng thái phím-thoại + "Kiểm tra / Sửa ngay"; khôi phục OFF→ON
- **Cấp quyền notification trong app** — tự cấp qua dadb, không cần laptop/ADB
- **Tự khởi động nền** — tự bật mọi tính năng nền (phím-thoại, ghế, lọc bụi, dẫn đường) khi nổ máy
- **Tự cập nhật OTA** — tự tải bản `apk/` mới hơn từ repo xuống xe
- **Nâng cao · khắc phục sự cố** — kiểm tra cập nhật, dọn cụm, cứu hộ chiếu, chẩn đoán, xuất log ra sdcard

*Giao diện (mới ở 1.32)*
- **Giao diện "cockpit" Level-2** — design system `Cockpit.*`: hero trạng thái + bảng tính năng 2 cột
- **Song ngữ Việt / English** — chuyển ngôn ngữ ngay trong app (Theo xe / VI / EN)
- **Light mode** — chọn giao diện Sáng / Tối / Theo xe

**(EN)** Feature index — each item is detailed under **Feature details** below.

*Navigation & cluster casting*
- **Navigation + HUD** — navigation on the cluster (stock lane + centre "Giữa + ETA") and the windshield HUD; roundabout exit direction + number; cluster display ON/OFF; long road-name marquee
- **Cluster Cast** — cast the foreground app to the cluster: full or split with an adjustable ratio (1:9–9:1); CarPlay & Android Auto full-screen; auto-cast an app on start; a re-pin watchdog
- **Speed badge on the cluster** — show speed/limit + the upcoming limit + a VietMap alert/camera chip; adjustable size and position
- **VietMap bubble on the cluster** — show the VietMap bubble, drag to reposition

*Cabin comfort (new in 1.32)*
- **Auto seat cooling / heating** — applies a saved per-seat cool/heat level over the AC HAL; tap the seat diagram to cycle
- **PM2.5 auto-filter** — auto-enables purification when cabin PM2.5 crosses the heavy threshold; a ring gauge shows the level

*Assistant & system*
- **Physical button → voice assistant** — map a hardware button (learn a key, map many buttons) to Google/Gemini · BYD 小迪 · Kiki · speech; a binding-status indicator + "Check / Fix now"; OFF→ON recovery
- **In-app notification-access grant** — self-granted over dadb, no laptop/ADB
- **Background auto-start** — auto-starts every background feature (voice key, seats, dust filter, navigation) on engine start
- **OTA self-update** — pulls a newer `apk/` build from the repo onto the car
- **Advanced · troubleshooting** — check for updates, clear the cluster, cast rescue, diagnostics, export logs to sdcard

*Interface (new in 1.32)*
- **Level-2 "cockpit" UI** — a reusable `Cockpit.*` design system: hero status cards + a two-column feature board
- **Bilingual Vietnamese / English** — switch language in-app (By-car / VI / EN)
- **Light mode** — Interface selector: Light / Dark / By-car

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

**(VI)** **Cài lần đầu:** đây là app ĐỘC LẬP (`com.byd.clusternav2`) cài SONG SONG với app cũ — **không cần gỡ** gì cả; tải `apk/ClusterNav-1.0-release.apk` (nút **Raw**/Download trên GitHub) rồi cài như một app mới. Sau đó cập nhật qua **OTA**: khi Nav+HUD được bật, app tự dò thư mục `apk/` của repo này trên `main`, và nếu có `ClusterNav-<ver>-release.apk` mới hơn thì tự cài qua dadb loopback trên xe (`-r`, **cùng khoá ký 2.0**) — không cần ADB/laptop. Để build cùng bản release từ nguồn: `./gradlew :app:assembleRelease`.

**(EN)** **First install:** this is a STANDALONE app (`com.byd.clusternav2`) that installs side by side with the old app — **no uninstall needed**; download `apk/ClusterNav-1.0-release.apk` (GitHub **Raw**/Download) and install it as a new app. After that it updates via **OTA**: with Nav+HUD enabled, the app polls this repo's `apk/` folder on `main` and, if a newer `ClusterNav-<ver>-release.apk` exists, installs it over the on-device dadb loopback (`-r`, **same 2.0 signing key**) — no ADB/laptop. To build the same release from source: `./gradlew :app:assembleRelease`.

**(VI)** Changelog theo phiên bản dưới đây giữ nguyên tiếng Anh (mô tả kỹ thuật từng bản sửa).

**Current version: 1.38 (versionCode 39) — "Cluster Nav 2.0" (`com.byd.clusternav2`), a standalone app independent of the legacy `com.byd.clusternav` (its own package + its own signing key, installs side by side).** `byd-cluster-2` re-baselines the 1.30 ClusterNav codebase (Waze/VietMap signals revived — see `docs/specs/waze-vietmap-signal-revival.html`) as a fresh **1.0** for a new iteration; the app OTA self-updates from **this** repo's `apk/ClusterNav-<ver>-release.apk` on `main`. The per-version notes below are kept as lineage history.

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

**(VI)**

*Dẫn đường & chiếu cụm*
- **Navigation + HUD** — một nguồn dẫn đường với đầu ra cluster-lane (làn zin) và cluster-centre ("Giữa + ETA") độc lập, cùng HUD kính lái; hướng rẽ **vòng xuyến + số lối ra**; chọn **chế độ cụm** ON/OFF; **chạy chữ** tên đường dài. Master switch **mặc định TẮT**; bật lên sẽ cấp quyền notification access trong app (qua dadb) và kết nối.
- **Cluster Cast** — projection-first: mở app → cụm sẵn sàng ngay; chạm nút nổi để chiếu app đang mở lên cụm, chạm lại để trả về. Full hoặc **chia đôi chỉnh tỉ lệ (1:9–9:1)**; **CarPlay / Android Auto** luôn full-screen; **tự chiếu** một app khi khởi động; watchdog giữ cụm khi app bị kéo ra.
- **Biển báo tốc độ trên cụm** — hiện tốc độ/giới hạn hiện tại + **giới hạn sắp tới** + **chip cảnh báo/camera VietMap**; chỉnh cỡ và kéo-thả vị trí.
- **Bóng VietMap trên cụm** — hiện bóng VietMap trên cụm, kéo-thả chỉnh vị trí; auto-start VietMap khi bật (1.32 sửa: luôn mở activity rồi đưa về nền để bóng hiện được).

*Tiện nghi cabin (mới ở 1.32)*
- **Ghế mát / sưởi tự động** — tự áp mức mát/sưởi đã lưu cho từng ghế (Seal 2 ghế / Han 4 ghế) qua HAL điều hoà BYDAuto, ~5 giây sau khi mở app / nổ máy; sơ đồ ghế top-down chạm để đổi mức. Mát và sưởi loại trừ nhau (theo HAL).
- **Tự lọc bụi mịn PM2.5** — khi mức PM2.5 trong cabin vượt ngưỡng nặng, tự bật lọc khí (reflection vào service điều hoà); đồng hồ vòng hiển thị mức hiện tại.

*Trợ lý & hệ thống*
- **Nút vật lý → trợ lý giọng nói** *(tuỳ chọn, mặc định TẮT)* — gán một nút cứng + cử chỉ (nhấn / nhấn-giữ) để mở Google/Gemini, BYD 小迪, Kiki hoặc speech recognizer, mà không đổi chức năng gốc của nút; **học phím mới** trên xe, **gán nhiều nút cho nhiều app**; **chỉ báo trạng thái phím-thoại** kèm nút "Kiểm tra / Sửa ngay"; khôi phục OFF→ON khi mất kết nối sau reboot.
- **Cấp quyền notification access trong app** — không cần laptop/ADB, không cần màn hình system-settings: app tự cấp listener qua dadb uid-shell, màn hình cài đặt chỉ là phương án dự phòng.
- **Tự khởi động nền** — tự bật mọi tính năng nền (phím-thoại, ghế, lọc bụi, dẫn đường) khi nổ máy, không cần mở app thủ công.
- **Tự cập nhật OTA** — khi Nav+HUD bật, app tự dò thư mục `apk/` trên nhánh `main`, thấy `ClusterNav-<ver>-release.apk` mới hơn thì tự cài qua dadb loopback (cùng khoá ký).
- **Nâng cao · khắc phục sự cố** — kiểm tra cập nhật thủ công, dọn sạch cụm, cứu hộ chiếu (deep rescue), chẩn đoán, và xuất log ra `/sdcard` để gỡ lỗi.

*Giao diện (mới ở 1.32)*
- **Giao diện "cockpit" Level-2** — design system `Cockpit.*` dùng lại được: hero 3 thẻ trạng thái (ô rẽ + đồng hồ km/h **thật** + xem trước chia đôi cụm) + bảng tính năng 2 cột grouped-row, kèm custom view sơ đồ ghế / đồng hồ PM2.5 / segmented.
- **Song ngữ Việt / English** — chuyển ngôn ngữ ngay trong app (**Ngôn ngữ**: Theo xe / VI / EN); dịch lúc chạy nên không đụng `strings.xml`.
- **Light mode** — bảng màu ngày/đêm đầy đủ, chọn qua **Giao diện** (Theo xe / Sáng / Tối); mọi custom view đổi màu theo.

**(EN)**

*Navigation & cluster casting*
- **Navigation + HUD** — one navigation source with independent cluster-lane (stock lane) and cluster-centre ("Giữa + ETA") outputs, plus the windshield HUD; roundabout **exit direction + exit number**; a **cluster display** ON/OFF selector; a long road-name **marquee**. Master switch **defaults OFF**; turning it on grants notification access in-app (over dadb) and connects.
- **Cluster Cast** — projection-first: open app → cluster ready instantly; tap the floating button to cast the foreground app, tap again to return. Full or **split with an adjustable ratio (1:9–9:1)**; **CarPlay / Android Auto** always full-screen; **auto-cast** an app on start; a re-pin watchdog when an app is pulled off the cluster.
- **Speed badge on the cluster** — show the current speed/limit + the **upcoming limit** + a **VietMap alert/camera chip**; adjustable size and drag-to-position.
- **VietMap bubble on the cluster** — show the VietMap bubble on the cluster, drag to reposition; VietMap auto-starts when enabled (1.32 fix: always open the activity then return to background so the bubble appears).

*Cabin comfort (new in 1.32)*
- **Auto seat cooling / heating** — applies a saved per-seat cool/heat level (Seal 2 seats / Han 4 seats) over the BYDAuto AC HAL ~5 s after the app opens / engine start; a top-down seat diagram lets you tap a seat to cycle. Cool and heat are mutually exclusive (matches the HAL).
- **PM2.5 auto-filter** — when cabin PM2.5 crosses the heavy threshold, purification is auto-enabled (reflection into the AC service); a ring gauge shows the current level.

*Assistant & system*
- **Physical button → voice assistant** *(optional, default OFF)* — map a hardware button + gesture (press / long-press) to launch Google/Gemini, BYD 小迪, Kiki or a speech recognizer, without changing the button's native function; **learn a new key** on the car, **map many buttons to many apps**; a **voice-key binding-status indicator** with a "Check / Fix now" action; OFF→ON recovery when the binding drops after a reboot.
- **In-app notification-access grant** — no laptop/ADB, no system-settings screen: the app self-grants the listener over the dadb uid-shell, with the settings screen only as a fallback.
- **Background auto-start** — auto-starts every background feature (voice key, seats, dust filter, navigation) on engine start, no need to open the app manually.
- **OTA self-update** — with Nav+HUD on, the app polls the repo's `apk/` on `main` and installs a newer `ClusterNav-<ver>-release.apk` over the dadb loopback (same signing key).
- **Advanced · troubleshooting** — manual update check, clear the cluster, cast deep-rescue, diagnostics, and export logs to `/sdcard` for debugging.

*Interface (new in 1.32)*
- **Level-2 "cockpit" UI** — a reusable `Cockpit.*` design system: three hero status cards (turn tile + a real km/h speed dial + a cast split-preview) + a two-column feature board of grouped rows, with custom seat-diagram / PM2.5-gauge / segmented views.
- **Bilingual Vietnamese / English** — switch language in-app (**Language**: By-car / VI / EN); translated at runtime so `strings.xml` is untouched.
- **Light mode** — a full day/night palette selected via **Interface** (By-car / Light / Dark); every custom view adapts.

> ⚠️ **(VI)** Đây là một thử nghiệm sở thích. Không cam kết an toàn lái xe, tương thích, khả năng hoàn tác hay sẵn sàng sản xuất. Cài đặt tự chịu rủi ro. Không liên kết với BYD.
>
> ⚠️ **(EN)** This is a hobby experiment. No driving-safety, compatibility, reversibility, or production-readiness claim. Install at your own risk. Not affiliated with BYD.

## Documentation · Tài liệu

**(VI)** Bộ tài liệu canonical (song ngữ khi hướng đến người dùng):

**(EN)** The canonical documentation set (bilingual where user-facing):

- [Project closeout (1.30)](docs/CLOSEOUT-2026-08-16.md) — final evaluation, the six 1.30 fixes, and honest known limitations (VI + EN).
- [Two-track final plan](docs/specs/clusternav-two-track-final-plan.html) — derived orchestration and evidence gates.
- [Cluster Cast re-baseline](docs/specs/cluster-cast-rebaseline.html) — canonical Cast contracts.
- [Navigation/UX re-baseline](docs/specs/clusternav-uxui-rebaseline.html) — two-card target UX and Navigation contracts.
- [Dead Reckon revalidation](docs/specs/dead-reckon-revalidation.html) — REMOVE decision and deferred review debt.
- [User guide (Hướng dẫn sử dụng)](docs/HUONG-DAN.md) — current 1.30 usage: enable Nav+HUD, in-app notification grant, cluster display mode (ON/OFF), roundabout exit direction, physical-button voice trigger (OFF→ON recovery). VI + EN.
- [1.13 spec — notification-grant · docs refresh · voice-key](docs/specs/notif-grant-docs-voicekey-1.13.html) — this cycle's consolidated spec (requirements → design → tasks → verification).
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
