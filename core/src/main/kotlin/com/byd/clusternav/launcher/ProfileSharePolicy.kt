package com.byd.clusternav.launcher

/**
 * ═══ PROFILE-IO-0930 · IO-R3 — khoá nào của một hồ sơ được đi ra ngoài trong bản **CHIA SẺ** ═══════════════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §12.4.2. Thuần Kotlin (`:core`) ⇒ kiểm off-device.
 *
 * ## Bệnh nó chữa
 * Tệp xuất #4 (2026-09-24) chép MỌI khoá theo hồ sơ. Từ khi sổ địa chỉ (09-14) và lịch dẫn đường (09-28) vào hồ sơ,
 * gửi tệp cho người khác là gửi luôn nhà · công ty · giờ đi làm · nhật ký chuyến — mà KDoc cũ vẫn ghi "không xuất khoá
 * nhạy cảm". Văn xuôi không chặn được ai, nên phân loại ở đây là **bảng**, và `ProfileSharePolicyTest` đòi MỌI khoá
 * theo hồ sơ thuộc ĐÚNG MỘT trong hai bảng.
 *
 * ## Danh sách TRẮNG, không danh sách đen
 * Lọc chia sẻ giữ khoá có trong [SHAREABLE] — khoá chưa ai soát thì KHÔNG đi ra ngoài (fail-closed). Thêm một khoá mang
 * toạ độ ở bản sau mà quên xếp loại ⇒ bài canh đỏ, và kể cả khi ai đó tắt bài canh thì khoá đó vẫn bị bỏ khỏi bản
 * chia sẻ thay vì lọt ra. [PRIVATE] vẫn cần: nó là chỗ ghi LÝ DO, và bài canh dùng nó để bắt khoá bị xếp nhầm bảng.
 *
 * ⚠ Khoá ở đây là khoá **trần**: hậu tố launcher (`saved_places`) và khoá trong ảnh chụp ClusterNav (`nav_automation_rules`)
 * — hai không gian tên, nhưng hôm nay không trùng chữ nào (bài canh kiểm).
 */
object ProfileSharePolicy {

    /** Khoá theo hồ sơ mang dữ liệu VỊ TRÍ / RIÊNG TƯ → lý do. Bản chia sẻ không mang giá trị của chúng. */
    val PRIVATE: Map<String, String> = mapOf(
        "saved_places" to
            "sổ địa chỉ (hậu tố launcher): tên nơi người dùng đặt ('Nhà', 'Công ty') + địa chỉ nguyên văn + toạ độ",
        "nav_automation_rules" to
            "lịch dẫn đường (ảnh clusternav_prefs): khung giờ + thứ trong tuần + nơi đến (placeId = tên trong sổ địa " +
                "chỉ) + app — tức lịch đi lại của một người",
        "nav_automation_fired" to
            "sổ đã-dẫn (ảnh clusternav_prefs): ngày nào luật nào đã chạy = nhật ký chuyến đi thật",
    )

    private const val R_LAYOUT = "bố cục/ô/thanh nút/chip — chỉ tên gói app, mã widget, thứ tự; không vị trí"
    private const val R_LOOK = "giao diện/màu/đơn vị/ngôn ngữ/tự mở — lựa chọn hiển thị, không vị trí"
    private const val R_WALLPAPER =
        "chỉ cờ bật · nhịp đổi ảnh · cách vừa · độ tối (WallpaperPrefs.encode) — ẢNH không nằm trong tệp"
    private const val R_CLUSTER =
        "hiển thị lên cụm; toạ độ ở đây là PIXEL MÀN HÌNH (badge/bong bóng), không phải toạ độ địa lý"
    private const val R_KEYS = "phím vô-lăng: mã phím + đích là gói app/mã việc (Prefs.VK_TARGET_*) — không vị trí"
    private const val R_COMFORT = "tiện nghi xe theo người lái (ghế, lọc bụi, lấy gió) — không vị trí"
    private const val R_CAMERA = "sở thích hiển thị camera (ProfileScopeCluster.CAMERA_PROFILE_KEYS) — không vị trí"
    private const val R_CAST = "chiếu cụm: công tắc, gói app tự chiếu, tỉ lệ chia đôi — không vị trí"
    private const val R_APPS = "gói app mặc định / dịch vụ nền lúc nổ máy — không vị trí"

    /** Khoá theo hồ sơ ĐÃ SOÁT, không mang vị trí/riêng tư → lý do. Bản chia sẻ mang nguyên. */
    val SHAREABLE: Map<String, String> = buildMap {
        listOf(
            "preset", "dock_edge", "dock_enabled", "dock_visible", "top_strip", "grid_layout", "top_strip_labels",
            "top_strip_migrated_ux5b", "header_order",
            // F1 R1.1 — chuỗi `pkg|S2,pkg|F,…` (AppShortcutCodec): chỉ tên gói + kiểu mở, không vị trí.
            "app_shortcuts",
        ).forEach { put(it, R_LAYOUT) }
        listOf("theme_mode", "unit_prefs", "launcher_autostart", "lang", "color_choice", "theme_choice")
            .forEach { put(it, R_LOOK) }
        put("wallpaper_prefs", R_WALLPAPER)
        listOf(
            "enabled", "nav_cluster_screen_mode", "marquee", "badge_enabled", "show_upcoming_badge", "show_alert_chip",
            "badge_size_dp", "badge_center_x", "badge_center_y", "vm_bubble_enabled", "vm_bubble_x", "vm_bubble_y",
            "bubbleX", "bubbleY",
        ).forEach { put(it, R_CLUSTER) }
        listOf("voicekey_enabled", "voicekey_bindings", "voicekey_custom_buttons").forEach { put(it, R_KEYS) }
        listOf(
            "seat_comfort_enabled", "seat_comfort_mode", "seat_level_0", "seat_level_1", "seat_level_2", "seat_level_3",
            "pm25_filter_enabled", "recirc_on_start_enabled",
        ).forEach { put(it, R_COMFORT) }
        listOf(
            "camera_signal_enabled", "camera_on_cluster", "camera_pos_left", "camera_pos_right", "camera_shape",
            "camera_dewarp_amount",
        ).forEach { put(it, R_CAMERA) }
        listOf(
            "cast_enabled", "cast_bubble_visible", "split_ratio_left_pct", "autostart_enabled", "autostart_package",
            "autostart_split_enabled", "autostart_left_package", "autostart_right_package",
        ).forEach { put(it, R_CAST) }
        listOf("voice_music_default_app", "headless_autostart").forEach { put(it, R_APPS) }
        // F2/F3 — `pkg|B,pkg|N` (tên gói + kiểu) và `ytmusic|<từ khoá/link mã hoá>`: lựa chọn app/nhạc, không vị trí.
        listOf("ignition_apps", "ignition_music").forEach { put(it, R_APPS) }
    }

    /**
     * Tiền tố khoá dựng động ĐÃ SOÁT → lý do. `slot_` = nội dung từng ô; họ `cast_geometry` = DPI/khung từng GÓI app
     * trên màn cụm (px/dpi). Họ MỚI (một [SnapshotFamily] khác) không tự vào đây ⇒ bài canh đỏ cho tới khi có người soát.
     */
    val SHAREABLE_PREFIXES: Map<String, String> = buildMap {
        put(SettingsCatalog.SLOT_KEY_PREFIX, R_LAYOUT)
        ProfileScopeCluster.CAST_GEOMETRY.prefixes.forEach {
            put(it, "hình học cửa sổ từng gói app khi chiếu (px/dpi) — không vị trí")
        }
    }

    /** Khoá ô do `WorkspacePrefs.save` sinh (`slot_<số>`) — neo hai đầu, không phải mọi chuỗi mở đầu bằng `slot_`. */
    private val SLOT_KEY = Regex("^" + Regex.escape(SettingsCatalog.SLOT_KEY_PREFIX) + "[0-9]+$")

    /**
     * Khoá [key] có THẬT thuộc một họ dựng động đã soát không: vừa mang tiền tố ở [SHAREABLE_PREFIXES], vừa đúng dạng
     * của họ đó (`slot_<số>`, hoặc [SnapshotFamily.owns] — neo hai đầu).
     *
     * Senior review PROFILE-IO-0930 lượt 2 [P3]: bản trước chỉ so TIỀN TỐ ⇒ một khoá mới đặt tên `slot_…`/`config_size_…`
     * mà chưa ai soát vẫn đi ra ngoài — trái lời hứa "danh sách trắng, kể cả khi tắt bài canh" ở KDoc lớp.
     */
    private fun reviewedDynamic(key: String): Boolean =
        SHAREABLE_PREFIXES.keys.any { key.startsWith(it) } &&
            (SLOT_KEY.matches(key) || ProfileScopeCluster.FAMILIES.any { it.owns(key) })

    /** Khoá [key] (trần) có được đi ra ngoài trong bản chia sẻ không. Chưa soát ⇒ `false`. */
    fun shareable(key: String): Boolean = key !in PRIVATE && (key in SHAREABLE || reviewedDynamic(key))
}
