package com.byd.clusternav.launcher

/**
 * BẢN ĐỒ KHOÁ của phía ClusterNav (IA v2 · §4.3) — *"khoá này nằm ở tệp prefs nào"* và *"khoá nào cố ý không lên
 * UI"*. Thuần Kotlin, không `android.*` ⇒ kiểm off-car.
 *
 * ## Bệnh nó chữa — và vì sao nó KHÔNG trùng với [SettingsCatalog.PREFS_FILES]
 * [SettingsCatalog.PREFS_FILES] trả lời câu *"phía launcher có mở tệp prefs thứ ba nào không"*, và bộ quét của nó
 * (`SettingsCoverageContractTest`) **cố ý không đọc** `Prefs.kt` của ClusterNav — kéo hàng chục khoá của tính năng
 * cũ vào sẽ biến bài R2 thành bài kiểm ClusterNav. Khi Settings gộp cả ClusterNav vào thì đúng những khoá bị bỏ ra
 * đó lại thành nội dung chính của bốn nhóm mới ⇒ phải có lưới canh riêng cho chúng, nếu không mỗi mục mới là một
 * chuỗi viết tay không ai đối chiếu với mã.
 *
 * Lưới đó là [CLUSTERNAV_KEYS]: mỗi khoá nói nó ở **tệp nào**, và `ClusterNavKeysContractTest` (`:app`) đòi chuỗi
 * đúng-nguyên-văn ấy có mặt trong **tệp nguồn khai nó**. Gõ sai một ký tự ⇒ đỏ, thay vì lặng lẽ ghi vào một khoá
 * không ai đọc (bẫy đã ăn một lần: spec ghi `recirc_on_start`, mã thật là `recirc_on_start_enabled`).
 */
internal object SettingsCatalogClusterNav {

    /**
     * Tiền tố khoá MỨC GHẾ (`seat_level_0`..`seat_level_3`).
     *
     * ⚠ `Prefs.seatComfortLevel` ghi `"seat_level_$seatIndex"` — **khoá dựng động trong một biểu thức nội suy**, nên
     * chuỗi `"seat_level_0"` KHÔNG tồn tại nguyên văn ở đâu trong mã. Cùng tình huống với
     * [SettingsCatalog.SLOT_KEY_PREFIX]: bài canh phải chấp nhận dạng **chưa nội suy**, không thì nó đỏ oan; và phải
     * chấp nhận theo **tiền tố khai tường minh** chứ không theo "bắt đầu bằng seat", không thì nó tha quá tay.
     */
    const val SEAT_LEVEL_KEY_PREFIX = "seat_level_"

    /** Tiền tố khoá dựng động → lý do. Bài canh nguyên-văn tra bảng này trước khi kết luận "khoá không tồn tại". */
    val DYNAMIC_KEY_PREFIXES: Map<String, String> = mapOf(
        SEAT_LEVEL_KEY_PREFIX to
            "Prefs.seatComfortLevel/setSeatComfortLevel ghi `\"seat_level_\$seatIndex\"` cho 4 ghế trong một hàm " +
                "chung — bốn tên đầy đủ không tồn tại nguyên văn trong mã",
    )

    /**
     * Tệp SharedPreferences mà **phía ClusterNav** đang ghi → nơi khai tên tệp đó.
     *
     * Bốn tệp, mỗi tệp một lý do lịch sử khác nhau — cố ý KHÔNG gộp lại: gộp nghĩa là viết lại đường đọc của runtime
     * đang chạy trên xe (`NavNotificationListener`, `FloatingBubbleService`, `attachBaseContext`), tức đổi hành vi
     * để cho gọn bảng. IA v2 gộp **giao diện**, không gộp chỗ lưu.
     */
    val PREFS_FILES: Map<String, String> = mapOf(
        "clusternav_prefs" to "tệp CHÍNH của ClusterNav (`Prefs.FILE`); `VmOverlayPosition` mở CÙNG tệp này",
        "simple_cast_prefs" to "prefs của Cluster Cast bản rút gọn (`SimpleCastRuntime`), runtime là FloatingBubbleService",
        "clusternav_theme" to "chỉ chứa `theme_choice`; `ThemeMode` cố ý để riêng vì nó phải đọc được ở attachBaseContext",
        "clusternav_lang" to "chỉ chứa `lang`; chỗ lưu ngôn ngữ DÙNG CHUNG cho cả APK (`Lang`)",
    )

    /**
     * Khoá của ClusterNav **có mặt trên UI Kachi** → tệp prefs chứa nó.
     *
     * Gồm cả khoá đi-kèm ([COMPANION_KEYS]) vì câu hỏi ở đây là *"khoá này ở tệp nào"*, không phải *"mục nào sở
     * hữu"* — câu sau đã có [SettingsCatalog.groupOf].
     */
    val KEYS: Map<String, String> = buildMap {
        // ── clusternav_prefs (Prefs.kt, trừ hai khoá bong bóng do VmOverlayPosition.kt khai) ──
        listOf(
            "enabled", "nav_cluster_screen_mode", "marquee",
            "badge_enabled", "show_upcoming_badge", "show_alert_chip", "badge_size_dp",
            "badge_center_x", "badge_center_y",
            "vm_bubble_enabled", "vm_bubble_x", "vm_bubble_y",
            "voicekey_enabled", "voicekey_bindings", "voicekey_custom_buttons", "voicekey_learn",
            "seat_comfort_enabled", "seat_comfort_mode", "seat_level_0",
            "pm25_filter_enabled", "recirc_on_start_enabled", "headless_autostart",
            // V1 pha NÓI · R4 (spec `kachi-voice-feedback.html` T9) — hai công tắc của đường ra TIẾNG. Khoá nằm
            // cùng tệp với `voice_mic_pill` (cũng của `Prefs`), nên "cấu hình giọng nói ở đâu" có một câu trả lời.
            "voice_speak_replies", "voice_prefer_offline",
        ).forEach { put(it, "clusternav_prefs") }
        // ── simple_cast_prefs (SimpleCastRuntime.kt) ──
        listOf(
            "cast_enabled", "split_ratio_left_pct",
            "autostart_enabled", "autostart_package",
            "autostart_split_enabled", "autostart_left_package", "autostart_right_package",
        ).forEach { put(it, "simple_cast_prefs") }
        // ── hai tệp một-khoá ──
        put("theme_choice", "clusternav_theme")
        put("lang", "clusternav_lang")
    }

    /**
     * Khoá ĐI KÈM → mã mục đặt nó. Đây là ca *"một điều khiển ghi hai khoá"*, không phải ca *"khoá không ai nhận"*.
     *
     * Bất biến "một khoá đúng một chủ" của [SettingsCatalog] vẫn nguyên: chủ là **mục**, và mục đó ghi cả cặp trong
     * MỘT lượt. Tách `badge_center_y` ra thành mục riêng thì người dùng đặt được nửa toạ độ — một trạng thái vô
     * nghĩa mà giao diện tự dựng ra (cùng lập luận đã dùng cho cặp cờ boot ở [SettingsCatalog.NOT_SETTINGS]).
     */
    val COMPANION_KEYS: Map<String, String> = mapOf(
        "badge_center_y" to "badge_center",
        "vm_bubble_y" to "vm_bubble_pos",
        // IA v2 · R3 — chip sáng/tối của Kachi ghi CẢ `theme_mode` (nguồn sự thật của launcher) lẫn `theme_choice`
        // (màn nâng cao đọc ở attachBaseContext). Một khái niệm, một công tắc, hai chỗ lưu vì hai màn đọc khác nhau.
        "theme_choice" to "display_theme",
    )

    /**
     * Khoá của ClusterNav **cố ý KHÔNG lên UI**, kèm lý do tại chỗ.
     *
     * Cùng khuôn [SettingsCatalog.NOT_SETTINGS]: không có danh sách này thì không phân biệt được *"quên gom"* với
     * *"cố ý không gom"*, và câu trả lời cho *"sao Settings mới thiếu cái này"* sẽ phải đi tìm lại trong mã.
     *
     * Phạm vi: đúng những khoá mà kiểm kê §4.3 xét qua. Khoá trạng-thái-máy của ClusterNav (`disclaimer_shown`,
     * `vm_float_whitelist_applied`, `nav_verbose_log`, `badge_corner/dx/dy` đời cũ) không thuộc phạm vi đợt này —
     * chúng chưa bao giờ là một dòng cài đặt. `bubble_auto` và họ `mod_*` là mã chết: không khai, không đụng.
     */
    val HIDDEN_KEYS: Map<String, String> = mapOf(
        "interpolate" to
            "ép true — bù cự ly theo tốc độ là hành vi mặc định từ 2026-08-12; màn cũ không có nút, và một nút " +
                "\"tắt phần bù\" chỉ có nghĩa khi đang gỡ lỗi trên xe",
        "acc_booster" to "ép true — bộ đọc màn GMaps tự câm khi bị che, không có nút ở màn cũ",
        "lane" to
            "ép true khi bật dẫn đường (MainActivity.kt:99–126 gọi setLane(true)) — tách ra thành công tắc riêng " +
                "thì bật dẫn đường mà cụm vẫn trống, một trạng thái không ai muốn dựng",
        "source_mode" to
            "không có nút ở màn cũ — màn chỉ HIỆN nguồn đang dùng (txt_nav_source_active); chọn tay nguồn nào là " +
                "việc của bộ trọng tài, không phải của người lái",
        "anim_opt" to "ép true — tối ưu hoạt ảnh cụm, không có nút ở màn cũ",
        "voice_ask_aloud" to
            "OQ4 — đọc to CÂU HỎI xác nhận rồi mới mở micro. Owner chốt 2026-09-16: **không** đọc câu hỏi (chỉ " +
                "đọc phản hồi sau lệnh) ⇒ khoá này mặc định false và CHƯA lên UI. Không gỡ mã: đường ấy đã có " +
                "hợp đồng + bài canh, và đây là một lựa chọn hành vi, không phải một cơ chế sai. Hàng trong Cài " +
                "đặt đi cùng batch 'chọn nút nào phải hỏi lại' (backlog V-CONFIRM-UI) — lúc đó dòng này chuyển " +
                "sang KEYS + một mục danh mục",
        "hud" to
            "ép false — HUD kính lái mới chỉ có vòng đời request/output, KHÔNG có đường ghi nội dung thật; bày nút " +
                "ra là hứa một tính năng chưa tồn tại",
    )
}
