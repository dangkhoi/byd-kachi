package com.byd.clusternav.launcher

/**
 * DỮ LIỆU của [SettingsCatalog.ENTRIES] — tách khỏi [SettingsCatalog] vì trần 500 dòng (CLAUDE.md §4.1), không vì
 * lý do kiến trúc: đây vẫn là **một** danh mục, chỉ là thân dữ liệu của nó nằm ở tệp riêng với các phép kiểm.
 *
 * ## Vì sao mỗi mục của ClusterNav có một dòng chú thích chỉ tới điều khiển ở màn cũ
 * IA v2 gộp màn ClusterNav vào Kachi bằng cách **dựng lại điều khiển, ghi cùng khoá** — không trích hàm khỏi
 * `MainActivity.kt` (tệp đó bị ~10 bài wiring test ghim source, còn `activity_main.xml` thì bị hash-seal T11). Nên
 * trong một thời gian **hai màn cùng sửa một khoá**. Dòng `// <R.id ở màn cũ> · <API ghi>` cạnh mỗi mục là sợi dây
 * duy nhất nối hai bề mặt đó: người dựng section mới (T4) biết phải bắt chước điều khiển nào, và người gỡ màn cũ
 * (OQ1) biết mỗi id XML sắp xoá đã có ai thay chưa. Không có nó thì "đã gộp đủ chưa" lại thành câu hỏi đếm bằng mắt.
 */
internal object SettingsCatalogEntries {

    /** MỌI mục cài đặt, theo thứ tự nhóm rồi thứ tự hiện ra trong nhóm. */
    val ALL: List<SettingsEntry> = LAUNCHER() + CLUSTER_NAV()

    // ── Phía LAUNCHER: khoá nằm trong `kachi_workspace` (trừ `lang`) ─────────────────────────────

    /**
     * Trong nhóm [SettingsGroup.HOME] thứ tự đi từ **cả bộ** ra **từng phần**, rồi từ **khung** ra **nội dung**:
     * chọn bố cục trước thì các lựa chọn sau mới có nghĩa.
     */
    private fun LAUNCHER(): List<SettingsEntry> = listOf(
        // ── Màn hình chính ──
        // ⚠⚠ S4 · R1 — BA MỤC "CẢNH" ĐÃ XOÁ khỏi đây: `home_scenes` (khoá `scenes`), `home_scene_boot` (khoá
        // `boot_scene`) và `home_scene_save` (nút lưu, không khoá). Owner 2026-09-14: *"có cảnh rồi có hồ sơ nữa hơi
        // khó hiểu"* — và backlog P7 đã ghi hai dòng đó tả **cùng một** khái niệm. Chức năng lên cấp chứ không mất:
        // mỗi cảnh cũ thành một hồ sơ cùng tên (R2, `ScenesMigration` ở `:core`), còn "cảnh lúc nổ máy" thành
        // "profiles_boot" dưới nhóm Hồ sơ tài xế (R6). Hai khoá `scenes`/`boot_scene` bị gỡ khỏi `WorkspacePrefs`
        // cùng lượt, nên `SettingsCoverageContractTest` không còn khoá nào mồ côi.
        SettingsEntry("home_preset", SettingsGroup.HOME, "Bố cục sẵn", "preset", "Preset layout"),
        SettingsEntry("home_grid", SettingsGroup.HOME, "Bố cục tự vẽ", "grid_layout", "Custom layout"),
        // Không lưu gì: đây là NÚT mở bảng vẽ. Bố cục vẽ ra thì lưu ở "home_grid" phía trên — một khoá, một chủ.
        SettingsEntry("home_grid_editor", SettingsGroup.HOME, "Vẽ bố cục riêng…", labelEn = "Draw your own layout…"),
        SettingsEntry(
            "home_wallpaper", SettingsGroup.HOME, "Hình nền & trình chiếu",
            "wallpaper_prefs", "Wallpaper & slideshow",
        ),

        // ── Thanh trạng thái & thanh nút (IA v2 · R-UI a — tách khỏi Màn hình chính) ──
        // ⚠ Mã mục đổi `home_*` → `bars_*` cùng lúc với nhóm. Kiểm trước khi đổi: [ĐO] grep `home_top_strip` /
        // `home_dock_edge` / `home_dock_items` trên cả `:app` và `:core` — **0 chỗ tra theo mã mục** (chỉ
        // `home_scene_save` bị `SceneWiringContractTest` tra), nên đổi mã ở đây không phá dây nối nào. KHOÁ lưu bền
        // thì giữ nguyên (`top_strip`/`dock_edge`/`dock_enabled`) — đổi khoá là mất cấu hình của người đang dùng.
        SettingsEntry("bars_top_strip", SettingsGroup.BARS, "Chip thanh trạng thái", "top_strip", "Status-bar chips"),
        // V3 · R14 (owner 2026-09-16) — *"chỉ hiện icon và chỉ số thôi, text nhiều chật chỗ"*. Đứng NGAY dưới
        // mục chọn chip: nó nói về chính những chip vừa chọn, và chỗ duy nhất đọc được kết quả là thanh trên.
        SettingsEntry(
            "bars_top_strip_labels", SettingsGroup.BARS, "Hiện nhãn trên thanh trên",
            "top_strip_labels", "Show chip labels",
        ),
        // Viền TRƯỚC danh sách nút: thứ tự khai ở đây LÀ thứ tự hiện ra, và mục "nút trên thanh" là lưới 123 ô. Khai
        // ngược lại thì muốn đổi viền phải cuộn qua hết lưới — thứ tự danh mục phải là thứ tự dùng được, không chỉ
        // là thứ tự nghe hợp lý khi đọc danh sách.
        // S1b — ẩn/hiện thanh nút. TRƯỚC danh sách nút + viền vì "có hiện không" là câu hỏi đầu tiên; ẩn rồi thì
        // viền/nút bên dưới không còn tác dụng ngay, nhưng vẫn để lộ ra để đặt sẵn cho lần hiện lại.
        SettingsEntry("bars_dock_visible", SettingsGroup.BARS, "Hiện thanh nút xe", "dock_visible", "Show the car bar"),
        SettingsEntry("bars_dock_edge", SettingsGroup.BARS, "Viền đặt thanh nút", "dock_edge", "Button bar edge"),
        SettingsEntry("bars_dock_items", SettingsGroup.BARS, "Nút trên thanh nút xe", "dock_enabled", "Buttons on the car bar"),

        // ── Hiển thị & đơn vị ──
        SettingsEntry("display_units", SettingsGroup.DISPLAY, "Đơn vị hiển thị", "unit_prefs", "Display units"),
        // [ĐO] kiểm kê S1 §2: khoá này lưu bền, có enum + có đường ghi, nhưng TRƯỚC S1 không có nút nào chạm tới.
        // IA v2 · R3 — cùng một chip nay ghi THÊM `theme_choice` (tệp `clusternav_theme`) để màn nâng cao theo cùng
        // lựa chọn; khoá thứ hai đó khai ở [SettingsCatalog.CLUSTERNAV_COMPANION_KEYS], không mở mục riêng.
        SettingsEntry("display_theme", SettingsGroup.DISPLAY, "Giao diện sáng/tối", "theme_mode", "Light / dark theme"),
        // VISUAL-REFRESH P1b · R8 — màu nhấn (8 ô + theo ảnh nền) và tông thẻ, theo hồ sơ; mã hoá ở `ColorChoice`.
        SettingsEntry("display_color", SettingsGroup.DISPLAY, "Màu sắc", "color_choice", "Colours"),
        // U5·T3 — NGÔN NGỮ. ⚠ Khoá `lang` KHÔNG nằm trong tệp `kachi_workspace` mà trong tệp lưu ngôn ngữ đã có của
        // ClusterNav (`clusternav_lang`, `com.byd.clusternav.Lang`) — cố ý, để một APK chỉ có MỘT công tắc ngôn ngữ
        // thay vì hai cái lệch nhau; lập luận đầy đủ ở KDoc `WorkspacePrefs.langMode`.
        SettingsEntry("display_lang", SettingsGroup.DISPLAY, "Ngôn ngữ", "lang", "Language"),

        // ── Hồ sơ tài xế ──
        // S4 · R3: từ đây một hồ sơ giữ **tất cả** lựa chọn của người dùng (xem `ProfileScope`), nên nhóm này không
        // còn là "một danh sách tên" mà là chỗ đổi cả bộ cấu hình. Thứ tự khai = thứ tự hiện ra: danh sách (việc
        // hằng ngày) → hồ sơ lúc nổ máy (đặt một lần) → thêm hồ sơ (hiếm hơn nữa).
        SettingsEntry("profiles_list", SettingsGroup.PROFILES, "Danh sách hồ sơ", "profiles", "Profile list"),
        SettingsEntry("profiles_active", SettingsGroup.PROFILES, "Hồ sơ đang dùng", "active_profile", "Active profile"),
        // S4 · R6 — thay cho "home_scene_boot". ⚠ Khoá `boot_profile` là khoá **theo XE**, không mang tiền tố hồ sơ
        // (R4): nó trả lời *"máy lên bằng hồ sơ nào"*, nên cất nó bên trong một hồ sơ là vòng tròn. Rỗng = "hồ sơ
        // dùng gần nhất" (mặc định), tức giữ nguyên hành vi trước S4.
        SettingsEntry(
            "profiles_boot", SettingsGroup.PROFILES, "Hồ sơ lúc nổ máy", "boot_profile", "Profile on engine start",
        ),
        // Không lưu gì: đây là NÚT tạo. Danh sách hồ sơ thì nằm ở "profiles_list" phía trên — một khoá, một chủ
        // (cùng lối với "home_grid_editor" và "home_grid"). ⚠ Nhãn nói rõ **bản sao**: từ R3 một hồ sơ trắng nghĩa
        // là mất sạch mọi thứ người dùng đã chỉnh, nên "thêm" ở đây luôn là nhân bản hồ sơ đang dùng (R8).
        SettingsEntry(
            "profiles_add", SettingsGroup.PROFILES, "Thêm hồ sơ (bản sao)", labelEn = "Add profile (a copy)",
        ),

        // ── Sổ địa chỉ (spec `kachi-voice-addresses.html`) — nằm trong nhóm DẪN ĐƯỜNG ──
        // ⚠ Khoá ở phía LAUNCHER (`kachi_workspace`, theo hồ sơ) dù mục hiện trong nhóm [SettingsGroup.NAV] — đó là
        // lý do hai dòng này khai ở đây chứ không ở khối CLUSTER_NAV bên dưới (khối đó toàn khoá của `Prefs`).
        // Nhóm chọn theo **thứ người dùng đang nghĩ tới** (KDoc [SettingsGroup]), không theo tệp lưu: người ta vào
        // *Dẫn đường* để sửa địa chỉ nhà, không vào *Hồ sơ tài xế* — dù sổ đi theo hồ sơ.
        SettingsEntry("places_list", SettingsGroup.NAV, "Sổ địa chỉ", "saved_places", "Address book"),
        // Không lưu gì: đây là NÚT thêm/sửa (một khoá, một chủ — cùng lối "home_grid_editor" / "profiles_add").
        SettingsEntry("places_add", SettingsGroup.NAV, "Thêm địa chỉ…", labelEn = "Add an address…"),
    )

    // ── Phía CLUSTERNAV: khoá nằm trong `clusternav_prefs` / `simple_cast_prefs` ─────────────────

    /**
     * Mục dựng lại từ màn ClusterNav (IA v2 · §4.3). Mỗi dòng chú thích nói **điều khiển nào ở màn cũ** làm đúng
     * việc đó, để T4 dựng lại mà không phải đọc lại cả `MainActivity.kt` 1386 dòng.
     */
    private fun CLUSTER_NAV(): List<SettingsEntry> = listOf(
        // ── Dẫn đường & cụm đồng hồ ──
        // switch_enabled · Prefs.setEnabled + setLane(true) + NavConnect.selfGrant/ensureConnected (MainActivity.kt:99–126)
        SettingsEntry("nav_enabled", SettingsGroup.NAV, "Dẫn đường lên cụm", "enabled", "Navigation on the cluster"),
        // seg_cluster_mode · Prefs.setNavClusterScreenMode — chỉ OFF(0)/FULL(3), xem [ClusterNavSettingsModel]
        SettingsEntry(
            "nav_cluster_mode", SettingsGroup.NAV, "Chế độ hiện trên cụm",
            "nav_cluster_screen_mode", "Cluster display mode",
        ),
        // cb_marquee · Prefs.setMarquee
        SettingsEntry("nav_marquee", SettingsGroup.NAV, "Chạy chữ tên đường", "marquee", "Scroll long street names"),
        // App dẫn đường MẶC ĐỊNH (owner 2026-09-18): nói "dẫn đường" không nêu app ⇒ dùng cái này.
        SettingsEntry(
            "nav_default_app", SettingsGroup.NAV, "App dẫn đường mặc định",
            "voice_nav_default_app", "Default navigation app",
        ),
        // btn_reconnect_nav · NavConnect.ensureConnected — VIỆC LÀM, không lưu gì
        SettingsEntry(
            "nav_reconnect", SettingsGroup.NAV, "Kết nối lại nguồn dẫn đường",
            labelEn = "Reconnect the navigation source",
        ),
        // switch_badge_enabled · Prefs.setBadgeEnabled (BadgePlacementController.kt:44–112)
        SettingsEntry("badge_enabled", SettingsGroup.NAV, "Biển báo tốc độ", "badge_enabled", "Speed limit badge"),
        // switch_upcoming_badge · Prefs.setShowUpcomingBadge
        SettingsEntry("badge_upcoming", SettingsGroup.NAV, "Giới hạn sắp tới", "show_upcoming_badge", "Upcoming limit"),
        // switch_alert_chip · Prefs.setShowAlertChip
        SettingsEntry("badge_alert_chip", SettingsGroup.NAV, "Chip cảnh báo camera", "show_alert_chip", "Camera alert chip"),
        // seek_badge_size · Prefs.setBadgeSizeDp, kẹp bằng BadgeLayout.clampSizeDp (60..240)
        SettingsEntry("badge_size", SettingsGroup.NAV, "Cỡ biển báo", "badge_size_dp", "Badge size"),
        // badge_placement_container (kéo-thả) · Prefs.setBadgeCenter ghi CẶP x/y — `badge_center_y` đi kèm, xem
        // [SettingsCatalog.CLUSTERNAV_COMPANION_KEYS]
        SettingsEntry("badge_center", SettingsGroup.NAV, "Vị trí biển báo", "badge_center_x", "Badge position"),
        // switch_vm_bubble_enabled · Prefs.setVmBubbleEnabled
        SettingsEntry("vm_bubble_enabled", SettingsGroup.NAV, "Bong bóng VietMap", "vm_bubble_enabled", "VietMap bubble"),
        // vm_bubble_placement_container · VmOverlayPosition.set ghi CẶP x/y rồi broadcast sang bản mod
        SettingsEntry("vm_bubble_pos", SettingsGroup.NAV, "Vị trí bong bóng", "vm_bubble_x", "Bubble position"),

        // ── Chiếu màn lên cụm ──
        // switch_cast_enabled · SimpleCastRuntime.coordinator(app).prefs.setCastEnabled
        SettingsEntry("cast_enabled", SettingsGroup.CAST, "Bật chiếu màn", "cast_enabled", "Enable casting"),
        // split_ratio_buttons (9 nút 1:9…9:1) · prefs.setSplitRatioLeftPercent + applySplitRatioLive
        SettingsEntry("cast_split", SettingsGroup.CAST, "Tỉ lệ chia đôi", "split_ratio_left_pct", "Split ratio"),
        // cb_autostart · prefs.setAutoStartEnabled — loại trừ nhau với tự-chiếu chia đôi (CastAutostart.kt:32–61)
        SettingsEntry(
            "cast_autostart", SettingsGroup.CAST, "Tự chiếu khi nổ máy",
            "autostart_enabled", "Autostart on engine start",
        ),
        // spinner_autostart_app · prefs.setAutoStartPackage
        SettingsEntry(
            "cast_autostart_pkg", SettingsGroup.CAST, "App tự chiếu toàn màn",
            "autostart_package", "Full-screen autostart app",
        ),
        // cb_autostart_split · prefs.setAutoStartSplitEnabled (CastAutostart.kt:47–56)
        SettingsEntry(
            "cast_autostart_split", SettingsGroup.CAST, "Tự chiếu chia đôi",
            "autostart_split_enabled", "Autostart split view",
        ),
        // spinner_autostart_left · prefs.setAutoStartLeftPackage
        SettingsEntry("cast_autostart_left", SettingsGroup.CAST, "App bên trái", "autostart_left_package", "Left-hand app"),
        // spinner_autostart_right · prefs.setAutoStartRightPackage
        SettingsEntry("cast_autostart_right", SettingsGroup.CAST, "App bên phải", "autostart_right_package", "Right-hand app"),
        // cast_zone_full / cast_zone_left / cast_zone_right / cast_stop · openProjection/dispatch(Stop) — VIỆC LÀM
        SettingsEntry(
            "cast_actions", SettingsGroup.CAST, "Chiếu ngay: toàn màn, trái, phải, dừng",
            labelEn = "Cast now: full, left, right, stop",
        ),
        // cast_recovery_actions (cast_clear_cluster · cast_deep_rescue) · trả cụm về đồng hồ — VIỆC LÀM
        SettingsEntry("cast_rescue", SettingsGroup.CAST, "Cứu hộ cụm", labelEn = "Cluster rescue"),

        // ── Phím vô-lăng ──
        // switch_voicekey_enabled · Prefs.setVoiceKeyEnabled + NavConnect.grantAccessibility(reset=true)
        SettingsEntry("keys_enabled", SettingsGroup.KEYS, "Nhận nút vật lý", "voicekey_enabled", "Listen to physical buttons"),
        // list_voicekey_bindings + btn_binding_remove · Prefs.add/removeVoiceKeyBinding (JSON [{k,t}])
        SettingsEntry("keys_bindings", SettingsGroup.KEYS, "Danh sách gán nút", "voicekey_bindings", "Button bindings"),
        // spinner_voicekey_button + btn_voicekey_add · Prefs.add/removeVoiceKeyCustomButton (MainActivity.kt:819)
        SettingsEntry(
            "keys_custom_buttons", SettingsGroup.KEYS, "Nút tự học thêm",
            "voicekey_custom_buttons", "Self-learned buttons",
        ),
        // btn_voicekey_learn · Prefs.setVoiceKeyLearn(true) rồi chờ VoiceKeyLearnBus (MainActivity.kt:919)
        SettingsEntry("keys_learn", SettingsGroup.KEYS, "Học phím mới", "voicekey_learn", "Learn a new key"),
        // btn_voicekey_recheck + txt_voicekey_status · refreshVoiceKeyStatus (MainActivity.kt:998–1016) — VIỆC LÀM
        SettingsEntry("keys_check", SettingsGroup.KEYS, "Kiểm tra và sửa ngay", labelEn = "Check and fix now"),

        // ── Tiện nghi xe ──
        // ⚠ Tên khoá THẬT là "recirc_on_start_enabled" (Prefs.K_RECIRC_ON_START), KHÁC tên "recirc_on_start" mà spec
        // S1 §2 ghi. Lấy theo mã nguồn, vì bài test phủ khoá đối chiếu với mã chứ không với spec.
        SettingsEntry(
            "car_recirc_on_start", SettingsGroup.CAR, "Tự lấy gió trong khi nổ máy", "recirc_on_start_enabled",
            "Recirculation on engine start",
        ),
        // switch_seat_comfort_enabled · Prefs.setSeatComfortEnabled + SeatComfortApplier.applyNow
        SettingsEntry(
            "car_seat_enabled", SettingsGroup.CAR, "Ghế mát/sưởi tự động",
            "seat_comfort_enabled", "Automatic seat cooling / heating",
        ),
        // seg_seat_mode · Prefs.setSeatComfortMode (0=COOL, 1=HEAT — khớp SeatComfort.SeatMode.ordinal)
        SettingsEntry(
            "car_seat_mode", SettingsGroup.CAR, "Chế độ ghế: mát hay sưởi",
            "seat_comfort_mode", "Seat mode: cool or heat",
        ),
        // seat_diagram · Prefs.setSeatComfortLevel(i) — ghi `seat_level_0`..`seat_level_3` trong MỘT vòng lặp, xem
        // [SettingsCatalog.SEAT_LEVEL_KEY_PREFIX]
        SettingsEntry("car_seat_levels", SettingsGroup.CAR, "Mức từng ghế", "seat_level_0", "Level per seat"),
        // switch_pm25_filter · Prefs.setPm25FilterEnabled + Pm25FilterApplier.enable/disable
        SettingsEntry("car_pm25", SettingsGroup.CAR, "Tự lọc bụi mịn", "pm25_filter_enabled", "Automatic air purifier"),
        // btn_pm25_clean_now + pm25_gauge · Pm25FilterApplier.cleanNow/readLevel — VIỆC LÀM
        SettingsEntry("car_pm25_clean", SettingsGroup.CAR, "Lọc ngay một lượt", labelEn = "Purify now"),

        // ── Hệ thống & quyền ──
        // Không lưu gì: hàng quyền chỉ ĐỌC trạng thái thật rồi tự xin lại (xem [LauncherRequirements]).
        SettingsEntry("system_permissions", SettingsGroup.SYSTEM, "Quyền còn thiếu", labelEn = "Missing permissions"),
        // [ĐO] kiểm kê S1 §2: khoá thứ hai không có đường tới trước S1 — chỉ được đọc/ghi trong mã.
        SettingsEntry(
            "system_autostart", SettingsGroup.SYSTEM, "Tự mở Kachi khi nổ máy",
            "launcher_autostart", "Auto-start Kachi on engine start",
        ),
        // cb_headless_autostart · Prefs.setHeadlessAutostart. ⚠ KHÁC `launcher_autostart` ngay trên: cái kia mở
        // **màn hình** Kachi, cái này chạy **dịch vụ** dẫn đường/cụm nền. Hai nghĩa khác nhau ⇒ hai công tắc, nhưng
        // phải đứng cạnh nhau với câu chữ nói đúng việc (IA v2 · R3), không thì trông như một cái bị lặp.
        SettingsEntry(
            "system_headless_autostart", SettingsGroup.SYSTEM, "Chạy dịch vụ nền khi nổ máy",
            "headless_autostart", "Run background service on engine start",
        ),
        // ── Giọng nói: ĐỌC phản hồi (spec `kachi-voice-feedback.html` R4 · T9) ──
        // Ba mục đứng cạnh hàng *Nhận dạng giọng nói* trong nhóm Hệ thống (§Nâng cao) vì chúng là hai nửa của
        // MỘT việc: cái tai (mô hình nghe) và cái miệng (gói đọc + hai công tắc). DEBT-CAT-2: bề mặt đã vẽ thì
        // phải có mục danh mục, nếu không rail nói một đằng mà trang có một nẻo.
        SettingsEntry(
            "voice_speak_replies", SettingsGroup.SYSTEM, "Đọc phản hồi bằng giọng",
            "voice_speak_replies", "Speak replies out loud",
        ),
        SettingsEntry(
            "voice_prefer_offline", SettingsGroup.SYSTEM, "Ưu tiên giọng offline",
            "voice_prefer_offline", "Prefer the offline voice",
        ),
        // 1.70 (voice-clone T7/T8) — chọn giọng phản hồi Piper (mặc định) hay "Giọng Kachi bé" (clip clone).
        SettingsEntry(
            "voice_feedback_voice", SettingsGroup.SYSTEM, "Giọng phản hồi",
            "voice_feedback_voice", "Feedback voice",
        ),
        // Không lưu khoá: đây là NÚT tải/gỡ gói giọng (cùng lối `profiles_add` / `system_default_home`). Gói nằm
        // trên đĩa của chính xe này, trạng thái đọc từ đĩa (`VoiceModelStore.isReady`) — không có pref nào để nhớ.
        SettingsEntry("voice_tts_pack", SettingsGroup.SYSTEM, "Giọng đọc offline", labelEn = "Offline voice pack"),
        // ── V3 · "nhanh + tự nhiên" (spec `kachi-voice-fast-natural.html`) ──
        // R7 — mục liệt kê MỌI việc có thể hỏi lại, mỗi việc một ô tích; mặc định KHÔNG tích cái nào (owner
        // 2026-09-16: *"cái nào nguy hiểm lái xe mới hỏi, chứ mở cửa hỏi làm gì"*).
        SettingsEntry(
            "voice_confirm_ids", SettingsGroup.SYSTEM, "Hỏi xác nhận trước khi chạy",
            "voice_confirm_ids", "Ask before running",
        ),
        // OQ4 — đứng NGAY dưới mục trên, vì nó chỉ có nghĩa khi có ít nhất một việc được tích: nó quyết định câu
        // hỏi ấy có được ĐỌC LÊN hay chỉ hiện chữ.
        SettingsEntry(
            "voice_ask_aloud", SettingsGroup.SYSTEM, "Đọc to câu hỏi xác nhận",
            "voice_ask_aloud", "Read confirmation questions aloud",
        ),
        // R1 — nguồn micro. Ở nhóm Hệ thống cạnh hàng *Nhận dạng giọng nói*: nó là một tính chất của PHẦN CỨNG
        // xe này, không phải một sở thích; và nó tồn tại để đo được từng nguồn trên đường mà không build lại.
        SettingsEntry(
            "voice_mic_source", SettingsGroup.SYSTEM, "Nguồn micro",
            "voice_mic_source", "Microphone source",
        ),
        // ── H2/H6 (1.69) — nhật ký lượt nói + đổi mô hình nghe ──
        // R-H2 — ô tích GIỮ NHẬT KÝ, mặc định BẬT (owner cần dữ liệu thật trên đường; off-car chỉ có 25 tệp TTS
        // macOS, mà CLAUDE.md §2 đã ghi số đo trên tập ấy không nói gì về cabin thật). Tiếng nằm trong `filesDir`,
        // không ra mạng, vòng đệm 30 mục / 30 MB — ba tính chất đo được từ mã, xem `VoiceUtteranceLog`.
        SettingsEntry(
            "voice_keep_log", SettingsGroup.SYSTEM, "Giữ nhật ký lượt nói",
            "voice_keep_log", "Keep a log of what you say",
        ),
        // Không lưu khoá: đây là NÚT nén `voice-log/` ra `Download/` (cùng lối `voice_tts_pack`). Người dùng cắm
        // USB chép hoặc gửi Zalo — đường DUY NHẤT tiếng rời khỏi xe, và nó luôn do một cú bấm của họ.
        SettingsEntry("voice_log_export", SettingsGroup.SYSTEM, "Xuất nhật ký voice", labelEn = "Export the voice log"),
        // Không lưu khoá: hai NÚT của H6 (chuyển sang mô hình nhẹ · gỡ bản nặng). Lựa chọn mô hình lưu ở tệp prefs
        // RIÊNG của `VoiceModelStore` (`kachi_voice`), không phải `clusternav_prefs` — nên ở đây chỉ có mục UI.
        SettingsEntry("voice_model_light", SettingsGroup.SYSTEM, "Mô hình nghe nhẹ (int8)", labelEn = "Light recognition model"),
        // ── Màn hình chính (S5) ──
        // btn_set_home · ClusterNavBridge.setDefaultHome — VIỆC LÀM (không lưu khoá): ROM BYD KHÔNG hiện hộp chọn
        // HOME khi bấm nút Home, nên đây là đường đặt được duy nhất. Nút gọi `cmd package set-home-activity` qua
        // dadb uid-shell ([ĐO] DiLink3.0 2026-09-14 ⇒ Success). Trạng thái ("đang là"/"chưa — hệ thống dùng <gói>")
        // ĐỌC qua PackageQueries, không shell.
        SettingsEntry("system_default_home", SettingsGroup.SYSTEM, "Màn hình chính", labelEn = "Home screen"),
        // cb_keep_home_on_boot · ClusterNavBridge.setKeepHomeOnBoot. Nổ máy thì đặt lại HOME một lần nếu ROM reset
        // (mặc định TẮT — [SUY] chưa đo ROM có reset không, chờ P7 trên xe). Khoá `keep_home_on_boot` là **theo XE**
        // ([ProfileScope.DEVICE_KEYS]): màn hình chính là thuộc tính của cả xe, không phải của một tài xế.
        SettingsEntry(
            "system_keep_home_on_boot", SettingsGroup.SYSTEM, "Giữ Kachi làm màn hình chính khi nổ máy",
            "keep_home_on_boot", "Keep Kachi as home screen on engine start",
        ),
        // btn_check_update · VIỆC LÀM
        SettingsEntry("system_update", SettingsGroup.SYSTEM, "Kiểm tra cập nhật", labelEn = "Check for updates"),
        // btn_nav_stop · dừng mọi output dẫn đường — VIỆC LÀM
        SettingsEntry("system_nav_stop", SettingsGroup.SYSTEM, "Dừng toàn bộ dẫn đường", labelEn = "Stop all navigation"),
        // ⚠ `system_advanced_screen` (mở màn ClusterNav cũ) đã XOÁ 2026-09-13 — màn đó bị gỡ hẳn
        // (docs/specs/kachi-remove-legacy-screen.html R1, đóng OQ1 của IA v2). Không có mục thay thế: mọi cấu
        // hình của nó đã nằm ở các nhóm nav/cast/keys/car từ IA v2 và ghi đúng cùng khoá.
        // btn_vietmap_widget_diag · kiểm dữ liệu/widget VietMap — VIỆC LÀM
        SettingsEntry("system_vietmap_data", SettingsGroup.SYSTEM, "Dữ liệu VietMap", labelEn = "VietMap data"),
        // cast_diagnostics · ClusterDiag/DiagActivity — VIỆC LÀM
        SettingsEntry("system_diagnostics", SettingsGroup.SYSTEM, "Chẩn đoán và nhật ký", labelEn = "Diagnostics and logs"),
        // T-BRIDGE · `KachiTestBridge` — công tắc mở **cầu kiểm thử qua adb** (docs/specs/kachi-test-bridge.html).
        // ⚠ Khoá `test_bridge_until` là TRANSIENT, không phải một sở thích: nó tự hết hạn sau 60 phút và chết theo
        // lần nổ máy (xem `TestBridgeWindow`). Đứng ở "Nâng cao" cạnh hai màn chẩn đoán vì cùng loại — một chỗ ĐO,
        // không phải một bề mặt cấu hình.
        SettingsEntry(
            "system_test_bridge", SettingsGroup.SYSTEM, "Chế độ kiểm thử qua adb",
            "test_bridge_until", "ADB test mode",
        ),

        // ── Giới thiệu ──
        SettingsEntry("about_version", SettingsGroup.ABOUT, "Phiên bản và giấy phép", labelEn = "Version and licence"),
        // maybeShowDisclaimer (MainActivity.kt:620) — hộp thoại một-lần, gác bằng `disclaimer_shown`. Ở đây là bản
        // ĐỌC LẠI bất cứ lúc nào; khoá `disclaimer_shown` vẫn thuộc màn cũ (trạng thái "đã hiện chưa", không phải
        // một lựa chọn) nên mục này KHÔNG nhận khoá.
        SettingsEntry("about_disclaimer", SettingsGroup.ABOUT, "Miễn trừ trách nhiệm", labelEn = "Disclaimer"),
    )
}
