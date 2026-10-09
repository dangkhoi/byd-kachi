package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.trip.TripGate

/**
 * ═══ S4 · R3/R4 — PHẠM VI của một khoá lưu bền: **theo HỒ SƠ** hay **theo XE** ═════════════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car. Spec `docs/specs/kachi-profiles-are-everything.html` R3–R5.
 *
 * ## Bệnh nó chữa
 * Owner: *"profile cover bố cục, các cấu hình tất cả mọi thứ"*. Câu đó chỉ **kiểm được** khi có một danh sách đầy đủ
 * để đối chiếu — trước S4 thì phạm vi khoá nằm rải ba chỗ và **không chỗ nào biết chỗ kia**:
 *  • `WorkspacePrefs.PROFILE_SUFFIXES` (:app) biết 8 hậu tố theo hồ sơ;
 *  • phần còn lại "chung cả máy" chỉ được ghi trong **văn xuôi** của một KDoc;
 *  • toàn bộ khoá ClusterNav ([SettingsCatalog.CLUSTERNAV_KEYS]) **chưa ai xếp loại** — chúng chỉ có "ở tệp nào".
 *
 * Văn xuôi không chặn được ai: thêm một mục cài đặt mới thì nó **im lặng** thành khoá chung cả máy (không ai chép
 * khi đổi hồ sơ), và không có bài test nào phản đối. Đây đúng họ lỗi mà [SettingsCatalog] sinh ra để chữa cho câu
 * *"khoá này thuộc nhóm nào"* — lớp này làm điều tương tự cho câu *"khoá này thuộc AI"*.
 *
 * ⇒ [scopeOf] phải trả lời được cho **mọi** khoá lưu bền đã khai trong mã, và trả [Scope.UNKNOWN] khi chưa ai xếp
 * loại. `ProfileScopeTest` biến [Scope.UNKNOWN] thành **đỏ**: thêm một mục mới mà quên xếp loại thì đỏ tại chỗ khai,
 * không đợi người dùng đổi hồ sơ trên xe rồi mới thấy một nửa cấu hình không đi theo.
 *
 * ## Ba mức, không phải hai
 * Spec R3 nói *"đúng một trong HAI danh sách"*, nhưng đo thật thì có mức thứ ba: khoá **tạm** (`voicekey_learn` là
 * cờ bật-một-lần rồi dịch vụ tự tắt; `scenes`/`boot_scene` là dữ liệu **đời cũ** chỉ còn sống tới lượt chuyển đổi
 * của [ScenesMigration]). Gộp chúng vào "theo xe" sẽ sai nghĩa (chúng không phải cấu hình của xe), gộp vào "theo hồ
 * sơ" còn tệ hơn: chụp–áp sẽ **chép cờ học phím sang hồ sơ khác** ⇒ đổi hồ sơ là máy vào chế độ học phím.
 */
object ProfileScope {

    /** Phạm vi của một khoá. [UNKNOWN] = **chưa ai xếp loại** ⇒ bài canh đỏ (xem KDoc lớp). */
    enum class Scope { PROFILE, DEVICE, TRANSIENT, UNKNOWN }

    /**
     * Chỗ nối của khoá ảnh chụp cấu hình ClusterNav: `"<tên hồ sơ>$SNAPSHOT_INFIX<tên tệp prefs>"`.
     *
     * ⚠ Phải chứa `__` **và** một chữ phân biệt: `WorkspacePrefs` đã dùng `"<hồ sơ>__<hậu tố>"`, nên nếu ảnh chụp chỉ
     * là `"<hồ sơ>__clusternav_prefs"` thì nó **không phân biệt được** với một hậu tố cấu hình thật tên
     * `clusternav_prefs` mai sau. Một ký tự nhập nhằng ở tiền tố khoá là cách mất dữ liệu im lặng (bài học
     * `SlotCodec` chọn `|` trùng dấu ngăn trường của sổ cảnh).
     */
    const val SNAPSHOT_INFIX = "__cn__"

    /**
     * Khoá của **phía launcher** đã theo hồ sơ từ trước S4 (đúng [WorkspacePrefs] `PROFILE_SUFFIXES` cũ, trừ
     * `scenes`/`boot_scene` đã bỏ theo R1).
     */
    val LAUNCHER_LAYOUT_SUFFIXES: List<String> =
        listOf(
            "preset", "dock_edge", "dock_enabled", "dock_visible", "top_strip", "grid_layout",
            // V3 · R14 (owner 2026-09-16) — *"cho cái toggle hiện text label"*. Theo hồ sơ như `top_strip` ngay
            // cạnh: nó là một lựa chọn về **bố cục thanh trên**, và S4 đã chốt bố cục đi theo hồ sơ.
            "top_strip_labels",
            // [SOÁT Opus 2026-09-27] Mốc *"di trú chip UX5b đã chạy"* (`WorkspacePrefs.K_STRIP_MIGRATED`). Theo HỒ SƠ
            // như chính `top_strip`: mỗi hồ sơ có danh sách chip riêng nên mỗi hồ sơ phải được di trú riêng một lần.
            // Thiếu dòng này thì xoá hồ sơ **không** dọn mốc ⇒ tên hồ sơ đặt lại sẽ bỏ qua lượt di trú của mình.
            "top_strip_migrated_ux5b",
            // UX-OVERHAUL · WP4 (2026-09-20) — THỨ TỰ các vật trên thanh trên ([HeaderLayout]). Cùng họ với
            // `top_strip`/`dock_enabled` ngay cạnh: cả ba trả lời *"thanh này bày gì, ở đâu"*. Thứ tự các nút của
            // thanh nút KHÔNG cần khoá mới — nó LÀ thứ tự của `dock_enabled` ([DockConfig.moveEnabled]).
            "header_order",
            // F1 (owner 01/10, spec shortcuts-autostart R1.1) — LỐI TẮT ỨNG DỤNG: app nào hiện trên khối thanh nút +
            // widget `w_apps`, mỗi app một kiểu mở. Cùng họ `dock_enabled`: *"thanh này bày gì"*, đi theo hồ sơ (S4).
            "app_shortcuts",
            // 2.87 · R-AH3 (owner 03/10) — nút ⇄ của khung tự ẩn hay luôn hiện: một lựa chọn về KHUNG như `top_strip_labels`.
            // ⚠ KHÔNG đặt tên `slot_…`: `slot_` là HỌ khoá nội dung ô ([PROFILE_KEY_PREFIXES], [SettingsCatalog.SLOT_KEY_PREFIX]).
            "swap_button_autohide",
            // 2.89 · B3 DOCK-SCALE (owner 05/10) — cỡ thanh nút theo %: một lựa chọn về THANH như `dock_edge` (`BarScale`).
            "dock_scale",
        )

    /**
     * S4 · R3(a) — khoá TRƯỚC ĐÂY chung cả máy, nay **theo hồ sơ**.
     *
     * Owner: *"mỗi người lái khác nhau hoặc tình huống khác nhau thì switch profile là OK"*. Chủ đề/đơn vị/hình
     * nền/ngôn ngữ/tự-mở đều là *lựa chọn của một người*, nên để chung cả máy nghĩa là hồ sơ chỉ cover được một nửa.
     *
     * `saved_places` (sổ địa chỉ, spec `kachi-voice-addresses.html` R1) vào đây cùng lẽ ấy, và còn rõ hơn: *"nhà"*
     * của người này không phải *"nhà"* của người kia. Nó là **một chuỗi** cho cả sổ (không phải họ khoá
     * `place_0..n`) nên không cần thêm tiền tố nào vào [PROFILE_KEY_PREFIXES] — xem §4.2 của spec.
     *
     * ⚠ `lang` nằm ở **tệp khác** (`clusternav_lang`, dùng chung cho cả APK) nhưng vẫn là hậu tố theo hồ sơ ở đây:
     * bản theo hồ sơ là **nguồn sự thật**, còn khoá chung kia là chỗ mà `attachBaseContext` của ClusterNav đọc ⇒ đổi
     * hồ sơ thì ghi cả hai (cùng khuôn `theme_mode`/`theme_choice` đã có từ IA v2). Vì vậy `lang` **không** được đồng
     * thời nằm trong ảnh chụp ClusterNav — xem [LAUNCHER_OWNED_CLUSTERNAV_KEYS].
     */
    val LAUNCHER_PERSONAL_SUFFIXES: List<String> =
        listOf(
            "theme_mode", "unit_prefs", "wallpaper_prefs", "launcher_autostart", "lang", "saved_places",
            // VISUAL-REFRESH P1b · R8 (owner 2026-09-16 *"có cho người ta chọn màu không nhỉ?"*) — màu nhấn + tông
            // thẻ (+ chỗ để sẵn màu sơn P3) là *lựa chọn của một người* y như `theme_mode` ngay cạnh ⇒ theo hồ sơ
            // (AC8.4). Khoá MỚI hoàn toàn: không có bản chung-cả-máy để lùi về, đọc thẳng `key()` như `saved_places`.
            "color_choice",
            // F2/F3 (owner 01/10, spec shortcuts-autostart R2.1/R3.1) — app mở khi nổ máy + nhạc lên xe: lựa chọn của MỘT
            // người lái như `launcher_autostart` ngay cạnh (S4 "mọi cấu hình theo hồ sơ"). Khoá mới, không có bản chung.
            "ignition_apps", "ignition_music",
            "voice_app_names",   // 2.91 VOICE-APP-NAMES R5 — tên app tự dạy: chữ mô hình in ra cho giọng của MỘT người
        )

    /**
     * Khoá ClusterNav mà **phía launcher đã sở hữu** dưới một hậu tố riêng ⇒ KHÔNG đi qua ảnh chụp → lý do.
     *
     * Có mặt trong cả hai đường là đúng định nghĩa **bẫy hai-bản-sao** mà dự án đã trả giá bốn lần (`customLayout` ·
     * `unitPrefs` ×4 · `wallpaper` · `themeMode`): hai chỗ cùng nhớ một lựa chọn thì sớm muộn chúng lệch nhau, và
     * lượt ghi sau cùng thắng một cách ngẫu nhiên.
     */
    val LAUNCHER_OWNED_CLUSTERNAV_KEYS: Map<String, String> = mapOf(
        "lang" to
            "ngôn ngữ đã là hậu tố theo hồ sơ của launcher (`<hồ sơ>__lang`); tệp `clusternav_lang` chỉ là bản " +
                "PHÁT ra cho `attachBaseContext` đọc, không phải chỗ nhớ thứ hai",
    )

    /**
     * S4 · R4 — khoá **theo XE**, kèm lý do tại chỗ (bắt buộc: danh sách không lý do là chỗ làm im bài test).
     *
     * Luật chung: đây là **phần cứng, hệ thống, hoặc chính bộ máy hồ sơ**. Chép chúng theo hồ sơ thì hoặc vô nghĩa
     * (danh sách hồ sơ nằm trong hồ sơ?), hoặc **mất dữ liệu**: xoá một hồ sơ mà mất luôn lịch sử mở app của cả xe.
     */
    val DEVICE_KEYS: Map<String, String> = buildMap {
        put(
            "profiles",
            "danh sách hồ sơ của cả xe — để nó theo hồ sơ là đệ quy: phải đọc hồ sơ đang dùng mới biết có hồ sơ nào",
        )
        put("active_profile", "hồ sơ đang dùng — chính con trỏ, không thể nằm trong thứ nó trỏ tới")
        put(
            "boot_profile",
            "S4 · R6 — hồ sơ lúc nổ máy. Cùng lý do [active_profile]: nó CHỌN hồ sơ nên phải đọc được trước khi " +
                "biết hồ sơ nào; `null` = dùng hồ sơ gần nhất",
        )
        put(
            "migrated_scenes_v1",
            "S4 · R2 — dấu 'đã chuyển cảnh sang hồ sơ', chạy MỘT lần cho cả máy. Theo hồ sơ thì mỗi hồ sơ mới lại " +
                "chạy lại một lượt chuyển đổi trên dữ liệu đã chuyển rồi",
        )
        put(
            "migrated_nav_schedule_v1",
            "2026-09-28 — dấu 'đã chuyển lịch tự dẫn đường sang hồ sơ', chạy MỘT lần cho cả máy. Cùng lẽ với " +
                "`migrated_scenes_v1`: chính phép di trú RÓT giá trị đang sống xuống MỌI hồ sơ, nên nếu cái dấu " +
                "cũng đi theo hồ sơ thì hồ sơ nào chưa mang dấu sẽ rót lại một lượt nữa — lần này đè lên lịch mà " +
                "người dùng đã kịp sửa. Dấu của một phép chạy-một-lần phải ở phạm vi rộng hơn thứ nó bảo vệ",
        )
        put(
            "keep_home_on_boot",
            "S5 — 'giữ Kachi làm màn hình chính khi nổ máy'. Màn hình chính là thuộc tính của **cả xe** (một " +
                "`cmd package set-home-activity` cho user 0), không phải lựa chọn của một tài xế: chép nó theo hồ " +
                "sơ thì đổi hồ sơ lại đi đặt/không-đặt HOME của cả máy. Cùng họ `boot_profile`/`home_chosen` — " +
                "quyết định mức máy, không mức người",
        )
        put(
            "home_chosen",
            "2026-09-15 HOME-alias — marker 'đã bấm Đặt làm màn hình chính thành công'. Cùng lý do với " +
                "`keep_home_on_boot`: HOME là của **cả xe**, KachiAutostart đọc marker để bật lại alias + set-home sau " +
                "nâng cấp; theo hồ sơ thì đổi hồ sơ lại quên/nhớ HOME của cả máy. Là NOT_SETTINGS nhưng vẫn phải có phạm vi",
        )
        put(
            "recent_apps",
            "lịch sử mở app của cả xe (đã khai ở [SettingsCatalog.NOT_SETTINGS] là trạng thái dùng, không phải " +
                "cấu hình) — xoá một hồ sơ mà mất lịch sử của cả xe là lỗi tệ hơn lỗi đang vá",
        )
        put(
            "last_display_id",
            "R4 — số hiệu màn cụm ĐO ĐƯỢC của chính chiếc xe này (`SimpleCastRuntime`), không phải lựa chọn",
        )
        put(
            "doze_whitelist_applied",
            "cờ một-lần đời cũ (miễn doze VietMap) — 2.89 B2 không còn đọc/ghi (quyết bằng sự thật `AppPrereqs`); " +
                "còn trên máy, thuộc máy, không chép",
        )
        put(
            "ui_glass_real",
            "UX-OVERHAUL WP1 · R1.3 — 'Kính thật (làm mờ nền)'. Theo XE: glass-thật là RenderEffect blur, một tính " +
                "chất của PHẦN CỨNG (GPU) + ROM (API ≥ 31) của chính chiếc xe này, không phải sở thích đi theo " +
                "người lái. [ĐO] xe DiLink là API 29 nên nó luôn lùi về glass-giả ở đó. Cùng họ keep_home_on_boot/" +
                "voice_wake_enabled — quyết định mức máy, không mức người",
        )
        put(
            "captest_results",
            "nhật ký 'kiểm tra từng nút' (`CapTestStore`, tệp `kachi_captest`) — kết quả OK/Không OK khi soát trên " +
                "xe NÀY. Theo XE, không theo người: chép hồ sơ sang xe khác không mang theo kết quả soát phần cứng. " +
                "Cùng họ `last_display_id`/`sherpa_model_id` — trạng thái đo mức máy",
        )
        put(
            "sherpa_model_id",
            "mã mô hình ASR đã TẢI VỀ máy NÀY (`VoiceModelStore`, tệp `kachi_voice`) — theo XE, không theo người: " +
                "tệp mô hình 78 MB nằm trên đĩa của chính xe này, chép hồ sơ sang xe khác thì mô hình có thể chưa tải " +
                "ở đó. Cùng họ `last_display_id`/OTA — trạng thái mức máy. Cũng khai ở [SettingsCatalog.NOT_SETTINGS]",
        )
        put(
            "freeform_state",
            "dấu mốc gieo cờ cửa sổ tự do, dùng CHUNG với đường chiếu-cụm (lý do đầy đủ ở " +
                "[SettingsCatalog.NOT_SETTINGS])",
        )
        put(
            "kachi_shell_approval",
            "READY-AT-HOME — dấu 'xe này đã duyệt khoá adb này' (`ShellApprovalStore`). Theo XE: duyệt nằm trong " +
                "`adb_keys` của CHIẾC XE này; chép sang hồ sơ/xe khác là nối sớm bằng khoá chưa được nhận ở đó. Cũng khai " +
                "ở [SettingsCatalog.NOT_SETTINGS]",
        )
        put(
            "kachi_floating_opened",
            "PROFILE-SWITCH-SLOTS R-B4 — dấu 'Kachi đã mở gói này thành cửa sổ nổi' ([FloatingWindowLedger]). Theo XE: " +
                "cửa sổ nằm trên màn của CHIẾC XE này; theo hồ sơ thì đổi hồ sơ — đúng lúc cần dọn — là mất dấu. Cũng " +
                "khai ở [SettingsCatalog.NOT_SETTINGS]",
        )
        put("kachi_slot_escape", "2.98 R7 — dấu app thoát ô Kachi đổi sang freeform (`EscapeMarkers`); theo XE vì task nằm trên xe này")
        put(
            "kachi_behind_marks",
            "BEHIND-HOME — dấu 'task này do Kachi đẩy ra sau màn nhà' (`BehindMarks`). Theo XE: task nằm trên màn của " +
                "CHIẾC XE này và sống qua lần BYD giết Kachi; theo hồ sơ thì đổi hồ sơ là mất dấu. Cũng khai ở " +
                "[SettingsCatalog.NOT_SETTINGS]",
        )
        // ⚠ 2026-09-30 — `cast_enabled` ĐÃ RỜI danh sách này, chuyển sang theo HỒ SƠ. Owner: *"Phần cụm lưu hết thành
        // profile nhé"* — THAY chốt S4-OQ2 của Pass 1 review (2026-09-14; không phải quyết định của owner). Lý do kỹ
        // thuật của chốt cũ VẪN ĐÚNG ([ĐO] mọi cổng đọc đều LIVE qua `SimpleCastPrefs.castEnabled()` ⇒ ghi thẳng khoá
        // sống lúc đổi hồ sơ là cụm HAI CHỦ) và nay là ràng buộc thiết kế: lượt đổi hồ sơ không bao giờ ghi khoá sống,
        // giá trị hồ sơ đợi ở `cast_enabled_pending` (theo XE) tới lần khởi động tiến trình kế — `CastEnableDeferral`.
        put(
            "voice_speak_replies",
            "V1 pha NÓI · R4 — công tắc 'Đọc phản hồi bằng giọng'. Theo XE vì thứ quyết định nó có nghĩa hay " +
                "không là **máy này có giọng gì**: engine của hệ thống có gói `vi-VN` chưa, gói Piper offline đã " +
                "lắp chưa (`VoiceSpeakerSelector` đo lại ở MỖI câu). Chép nó theo hồ sơ thì đổi hồ sơ xong loa im " +
                "mà không ai hiểu vì sao — cùng họ `voice_mic_pill`/`headless_autostart`: lựa chọn mức máy",
        )
        put(
            "voice_ask_aloud",
            "V1 pha NÓI · OQ4 — 'đọc câu hỏi xác nhận rồi mới mở micro'. Cùng lý do [voice_speak_replies]: nó chỉ " +
                "có nghĩa khi MÁY NÀY có giọng, và nó gác một lượt mở micro — tức một tính chất của cái xe, không " +
                "phải một sở thích đi theo người lái. Owner chốt 2026-09-16: mặc định TẮT, chưa lên UI",
        )
        put(
            "voice_nav_default_app",
            "App dẫn đường MẶC ĐỊNH (owner 2026-09-18) — 'nói dẫn đường không nêu app thì dùng cái này'. Theo XE: " +
                "app nào đang cài / owner ưa dùng là tính chất của cái xe, không đi theo người lái; cùng họ " +
                "voice_speak_replies/voice_mic_pill",
        )
        put(
            "voice_wake_enabled",
            "\"Hey Kachi\" wake-word (W-WAKE, owner 2026-09-18) — nghe câu gọi rảnh tay. Theo XE: nghe-nền là " +
                "tính chất phần cứng/ROM của chiếc xe (mic + tải CPU), không đi theo người lái. Mặc định TẮT.",
        )
        put(
            "voice_wake_phrase",
            "Câu gọi preset của \"Hey Kachi\" (VoiceWakePhrase id). Theo XE cùng voice_wake_enabled.",
        )
        put(
            "voice_mic_source",
            "V3 · R1 — nguồn micro thử TRƯỚC. Theo XE vì nó là một tính chất của **phần cứng và ROM của chính " +
                "chiếc xe này** ([ĐO] nguồn 6 gần câm trên DiLink3.0), không phải sở thích của người lái; chép " +
                "nó theo hồ sơ thì đổi hồ sơ là micro đổi độ nhạy mà không ai hiểu vì sao",
        )
        put(
            "voice_confirm_ids",
            "V3 · R7 — danh sách việc phải hỏi lại trước khi chạy. Theo XE: đây là một quyết định AN TOÀN về " +
                "chính chiếc xe (owner chốt mặc định RỖNG 2026-09-16; 2.86 thêm mở-cửa-sổ-trời), và một hồ sơ chép sang xe khác không được " +
                "mang theo lựa chọn 'không hỏi gì cả'. Cùng họ `keep_home_on_boot` — quyết định mức máy, không mức người",
        )
        put(
            "voice_confirm_default_v286",
            "FIX286 · SR5 — mốc *\"lựa chọn hỏi-xác-nhận đã được lưu khi màn Cài đặt bày mặc định 2.86 (mở cửa sổ " +
                "trời)\"*. Đi CÙNG `voice_confirm_ids` (cùng hàm ghi, cùng lượt `edit()`) nên phải cùng phạm vi: " +
                "tách ra thì đổi hồ sơ mang tập theo mà bỏ mốc lại (hoặc ngược lại) ⇒ nóc bị cộng/bớt khỏi tập hỏi " +
                "mà không ai bấm gì",
        )
        put(
            "voice_follow_up_ms",
            "V3 · R9 — quãng giữ micro cho câu tiếp. Cùng lý do [voice_mic_source]: nó là một hằng ĐO trên cabin " +
                "này (ồn nền, khoảng cách mic), không phải một sở thích đi theo người lái",
        )
        put(
            "voice_keep_log",
            "H2 (1.69) — giữ nhật ký lượt nói. Theo XE: thứ nó ghi là TIẾNG trong cabin của chính chiếc xe này, " +
                "và nó chiếm chỗ trên đĩa của chính máy này (vòng đệm 30 mục / 30 MB). Một hồ sơ chép sang xe " +
                "khác không được mang theo quyết định 'ghi lại giọng người ngồi trong xe đó'",
        )
        put(
            "voice_endpoint_silence_ms",
            "V3 · R2 — im bao lâu thì chốt câu. Cùng lý do [voice_mic_source]: một hằng ĐO trên cabin này (mức " +
                "ồn nền, khoảng cách mic), không phải sở thích đi theo người lái",
        )
        put(
            "voice_endpoint_min_speech_ms",
            "V3 · R2 — tối thiểu tiếng cộng dồn trước khi được phép chốt. Cùng lý do khoá trên",
        )
        put(
            "voice_endpoint_floor_cap",
            "[P0-2] trần mức nền của bộ ngắt câu. Rõ ràng là thuộc tính của PHẦN CỨNG + cabin xe này: nó tồn " +
                "tại vì micro và tiếng bíp của chính máy này làm nhiễm cửa sổ đo nền ([ĐO xe 2026-09-16])",
        )
        put(
            "voice_beam",
            "V2 — bề rộng chùm giải mã. Theo XE vì nó là phép đổi chác với CPU của chính đầu máy này ([ĐO] " +
                "Qualcomm TRINKET 8 lõi, một câu 8 s mất 2,35 s ở beam 4), không phải sở thích của người lái",
        )
        put(
            "voice_hotword_score",
            "V2 — điểm biasing hotwords. Cùng họ [voice_beam]/[voice_mic_source]: đo bằng tai trên cabin thật, " +
                "và kết quả phụ thuộc micro + mức ồn của xe này chứ không phụ thuộc ai đang lái",
        )
        put(
            "voice_prefer_offline",
            "V1 pha NÓI · R4 — 'Ưu tiên giọng offline'. Cùng lý do [voice_speak_replies], và còn rõ hơn: nó chỉ " +
                "có tác dụng khi **gói 61 MB đã nằm trên đĩa của chính xe này**, mà đĩa thì không đi theo hồ sơ",
        )
        put(
            "rain_defrost_enabled",
            "AUTOMATION #1 (1.85, spec kachi-automation R1/R5) — 'Tự sấy kính khi mưa'. Theo XE: nó đọc một CẢM " +
                "BIẾN của chiếc xe này ([ĐO] SETTING_FRONT_RAIN_WIPER_SPEED) và ghi hai nút sấy của chính nó, " +
                "tức một quyết định về phần cứng chứ không phải sở thích đi theo người lái. Chép nó theo hồ sơ thì " +
                "đổi hồ sơ giữa cơn mưa là sấy tự tắt/bật mà không ai hiểu vì sao — cùng họ keep_home_on_boot/" +
                "voice_wake_enabled: quyết định mức máy, không mức người",
        )
        // ⚠ 2026-09-28 — `nav_automation_rules` và `nav_automation_fired` ĐÃ RỜI danh sách này, chuyển sang theo
        // HỒ SƠ. Owner quyết: *"lịch theo profile luôn nhé, ví dụ tôi chuyển sang profile Trip Đà Lạt, thì chắc
        // chắn sẽ cần địa chỉ khác, lịch trình khác với việc đi làm hàng ngày chứ?"* — lý lẽ ấy mạnh hơn lý lẽ cũ
        // ("việc của CHIẾC XE"), và nó xoá luôn cái nghịch lý mà chính mục cũ đã tự ghi: luật theo xe mà
        // `saved_places` theo hồ sơ ⇒ một luật trỏ tới địa chỉ không có trong hồ sơ đang dùng thì BỎ LƯỢT lặng lẽ.
        // Nay cả ba thứ (luật · dấu đã-dẫn · sổ địa chỉ) cùng một phạm vi nên không còn lệch nhau được.
        // Xem [CLUSTERNAV_PROFILE_STATE_KEYS] cho khoá dấu-đã-dẫn (không phải cài đặt nên không nằm ở danh mục).
        put(
            "rain_defrost_front",
            "V7 (owner 2026-09-25), độc lập từ kachi-automation V8 (2026-09-30) — hàng *'Mưa thì tự bật sấy kính trước'*. Theo XE **cùng phạm vi với** " +
                "`rain_defrost_enabled` mà nó phụ thuộc: để hai khoá của CÙNG một tính năng ở hai phạm vi khác " +
                "nhau là mời chúng lệch nhau (công tắc theo xe mà lựa chọn kính theo hồ sơ ⇒ đổi hồ sơ giữa cơn " +
                "mưa là sấy đổi bên mà không ai hiểu vì sao). Nó cũng là một quyết định về PHẦN CỨNG của chiếc xe " +
                "này (cái nào ăn điện, cái nào cần), không phải sở thích đi theo người lái",
        )
        put(
            "rain_defrost_rear",
            "V7/V8 — hàng *'Mưa thì tự bật sấy kính sau + gương'*. Cùng lý do [rain_defrost_front]: cùng tính năng thì cùng phạm vi",
        )
        put(
            "auto_update_enabled",
            "V8 (owner 2026-09-25) — *'Tự động cập nhật'*. Theo XE: nó tải một APK về **đĩa của chính máy này** rồi " +
                "cài đè lên bản đang chạy ở đây — một việc mức MÁY, không phải lựa chọn hiển thị của một tài xế. " +
                "Chép nó theo hồ sơ thì đổi hồ sơ có thể khởi động một lượt tải ~40 MB giữa chuyến, hoặc lặng lẽ " +
                "tắt việc cập nhật của cả xe. Cùng họ `sherpa_model_id`/`doze_whitelist_applied` — trạng thái/" +
                "quyết định mức máy",
        )
        // ⚠ 2026-09-30 — `cast_bubble_visible` ĐÃ RỜI danh sách này (V-CLUSTER, cùng quyết định với `cast_enabled`).
        // Lý do cũ chỉ là *"cùng phạm vi với cast_enabled"*; [ĐO code] khoá này chỉ ẩn/hiện cửa sổ nổi trên màn chính
        // (vòng `syncBubbleWindow` 2 s), không dựng lại dịch vụ, không chạm projection ⇒ áp NGAY khi đổi hồ sơ là an toàn.
        put("enable_freeform_support", "cờ boot của HỆ THỐNG (`Settings.Global`) — thuộc máy")
        put("force_resizable_activities", "cờ boot của HỆ THỐNG (`Settings.Global`), gieo CẶP với khoá trên")
        put("enabled_accessibility_services", "danh sách trợ năng DÙNG CHUNG với mọi app khác (`Settings.Secure`)")
        put("accessibility_enabled", "cờ trợ năng toàn hệ thống (`Settings.Secure`) — máy tự đổi sau lưng")
        // V-CLUSTER — khoá cụm/chiếu/camera theo XE (23 khoá hiệu chỉnh camera, dấu mốc, đời cũ): bảng + lý do ở
        // [ProfileScopeCluster.DEVICE_KEYS] (tách vì trần 500 dòng), không chép lại ở đây.
        putAll(ProfileScopeCluster.DEVICE_KEYS)
        putAll(TripGate.DEVICE_KEYS)   // F2/F3 — sổ chuyến lên xe + mốc khởi động (theo XE); lý do ở chỗ chủ
    }

    /**
     * Tiền tố khoá **dựng động** theo xe → lý do. Nay chỉ còn hai họ đời V2 của `CastAppCatalog` (`scale-*:<gói>`,
     * `dpi:<gói>`), bảng ở [ProfileScopeCluster.DEVICE_KEY_PREFIXES].
     *
     * ⚠ 2026-09-30 (V-CLUSTER) — bốn tiền tố `config_size_/overscan_/density_/bounds_` ĐÃ RỜI bảng này sang theo HỒ SƠ
     * ([ProfileScopeCluster.CAST_GEOMETRY]). Lý do cũ (*"`wm density` đã ĐO cho màn cụm của chính xe này"*) sai: DPI và
     * khung do người lái chọn bằng chip và thanh −/+, còn `config_size_` là hằng `1920x720` hoặc suy từ khung người lái
     * chọn. Cả bốn là MỘT bản ghi nên cùng đi (spec §11.2 K4).
     */
    val DEVICE_KEY_PREFIXES: Map<String, String> = ProfileScopeCluster.DEVICE_KEY_PREFIXES

    /**
     * Khoá **tạm / đời cũ** → lý do. Không theo hồ sơ **và** không theo xe: chúng không phải một lựa chọn để nhớ.
     */
    val TRANSIENT_KEYS: Map<String, String> = mapOf(
        "voicekey_learn" to
            "cờ BẬT-MỘT-LẦN: `MainActivity` đặt true rồi dịch vụ tự tắt sau khi học xong một phím. Chép nó theo hồ " +
                "sơ ⇒ đổi hồ sơ là máy vào chế độ học phím mà không ai bấm gì",
        "scenes" to
            "S4 · R1 — dữ liệu CẢNH đời cũ. Chỉ còn sống tới lượt chuyển đổi một lần ([ScenesMigration]); sau đó " +
                "khoá này bị xoá khỏi đĩa cùng khái niệm 'cảnh'",
        "boot_scene" to "S4 · R1 — con trỏ cảnh lúc nổ máy đời cũ; [ScenesMigration] đổi nó thành `boot_profile`",
        "test_bridge_until" to
            "T-BRIDGE — cửa sổ 60 phút của chế độ kiểm thử qua adb (`TestBridgeWindow`). Cùng họ `voicekey_learn`: " +
                "một cờ BẬT-MỘT-LẦN rồi tự tắt, không phải lựa chọn để nhớ. Theo hồ sơ thì đổi hồ sơ là mở lại " +
                "một cửa điều khiển mà không ai bấm gì; theo xe thì sai nghĩa (nó không phải cấu hình của xe) và " +
                "còn mời người sau bỏ luôn phép hết hạn",
    )

    /**
     * Khoá ClusterNav đi theo HỒ SƠ nhưng **không phải cài đặt** (trạng thái chạy) → tệp prefs chứa nó.
     *
     * Vì sao cần danh sách RIÊNG thay vì nhét vào danh mục Cài đặt: bảng `SettingsCatalogClusterNav.KEYS` chỉ nhận
     * khoá **có mặt trên UI** (chú thích tại chỗ), mà `nav_automation_fired` là sổ ĐÃ-DẪN, không có hàng nào. Nhưng
     * nó vẫn phải đi theo hồ sơ, và phải đi **CÙNG** `nav_automation_rules`: tách phạm vi hai khoá là mời chúng
     * lệch nhau — luật theo hồ sơ mà dấu đã-dẫn theo xe thì đổi hồ sơ là **dẫn lại lần hai trong cùng khung giờ**.
     *
     * ⚠ Đường SAI mà lượt đọc 2026-09-28 chỉ ra: nhét khoá này vào [LAUNCHER_PERSONAL_SUFFIXES] thì [scopeOf] trả
     * PROFILE và bài canh vẫn XANH, nhưng hậu tố đó được ghép tiền tố vào tệp prefs **của launcher**, trong khi sổ
     * đã-dẫn được đọc từ tệp **ClusterNav** ⇒ đọc RỖNG, im lặng, và dẫn hai lần. Phải đi đường ảnh-chụp này.
     */
    val CLUSTERNAV_PROFILE_STATE_KEYS: Map<String, String> = mapOf(
        "nav_automation_fired" to "clusternav_prefs",
    )

    /**
     * S4 · R3(b) — khoá ClusterNav **theo hồ sơ**, gom theo **tệp prefs**: `tệp → khoá`.
     *
     * Sinh bằng mã từ [SettingsCatalog.CLUSTERNAV_KEYS] (nguồn duy nhất, đã có bài canh nguyên-văn ở `:app`) trừ đi
     * ba tập trên. **Cố ý không chép tay lại danh sách**: chép tay thì thêm một khoá ClusterNav ở bản sau sẽ có mặt
     * ở bảng kia mà vắng ở đây ⇒ đổi hồ sơ bỏ sót đúng khoá mới, im lặng.
     *
     * Gom theo tệp vì [PrefSnapshot] chụp **một chuỗi cho một tệp**: mỗi tệp là một `SharedPreferences` riêng, và
     * lượt áp phải ghi đúng tệp mà dịch vụ đang đọc.
     */
    val CLUSTERNAV_KEYS: Map<String, List<String>> =
        (
            SettingsCatalog.CLUSTERNAV_KEYS
                .filterKeys { key ->
                    key !in DEVICE_KEYS && key !in TRANSIENT_KEYS && key !in LAUNCHER_OWNED_CLUSTERNAV_KEYS
                } + CLUSTERNAV_PROFILE_STATE_KEYS +
                // V-CLUSTER — khoá theo hồ sơ KHÔNG có mục Cài đặt (6 khoá camera + vị trí nút nổi chiếu).
                ProfileScopeCluster.PROFILE_EXTRA_KEYS
            )
            .entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, keys) -> keys.sorted() }
            .toSortedMap()

    /** Mọi khoá ClusterNav theo hồ sơ (phẳng) — tiện cho [scopeOf] và cho chỗ gọi chỉ cần hỏi "có thuộc không". */
    val CLUSTERNAV_PROFILE_KEYS: Set<String> = CLUSTERNAV_KEYS.values.flatten().toSet()

    /** Hậu tố ảnh chụp của một tệp prefs ClusterNav. Chỉ có ĐÂY dựng chuỗi đó — không chỗ nào ghép tay. */
    fun snapshotSuffix(prefsFile: String): String = "$SNAPSHOT_INFIX$prefsFile"

    /** Hậu tố ảnh chụp của **mọi** tệp ClusterNav theo hồ sơ, sinh từ [CLUSTERNAV_KEYS]. */
    val SNAPSHOT_SUFFIXES: List<String> = CLUSTERNAV_KEYS.keys.map { snapshotSuffix(it) }

    /**
     * ⚠⚠ **MỌI hậu tố khoá theo-hồ-sơ của launcher, khai ĐÚNG MỘT LẦN** — `WorkspacePrefs.PROFILE_SUFFIXES` (:app)
     * phải đọc thẳng danh sách này, không được viết lại.
     *
     * Đây là danh sách mà `deleteProfile` dùng để dọn sạch và `widgetIdsOtherProfiles` dùng để dò. Hai chỗ đó tự viết
     * lại danh sách thì hoặc khoá mồ côi sống mãi, hoặc id widget của hồ sơ khác bị xoá oan — cả hai đều im lặng
     * ([SOÁT P2-2]).
     *
     * `slot_*` **sinh theo** [WorkspaceState.SLOT_CAP], ảnh chụp **sinh theo** [CLUSTERNAV_KEYS]: trần ô đã đổi một
     * lần (4 → 6) và số tệp ClusterNav còn đổi được — chỗ nào chép tay con số đó thì lần đổi sau bỏ sót im lặng.
     */
    val LAUNCHER_SUFFIXES: List<String> = buildList {
        addAll(LAUNCHER_LAYOUT_SUFFIXES)
        addAll(LAUNCHER_PERSONAL_SUFFIXES)
        addAll((0 until WorkspaceState.SLOT_CAP).map { "${SettingsCatalog.SLOT_KEY_PREFIX}$it" })
        addAll(SNAPSHOT_SUFFIXES)
    }

    /**
     * Phạm vi của [prefKey] — **nguồn duy nhất** trả lời *"khoá này thuộc ai"*.
     *
     * ⚠ Nhận **khoá TRẦN** (`"preset"`, `"badge_size_dp"`), KHÔNG nhận khoá đã ghép tiền tố hồ sơ
     * (`"Mặc định__preset"`): ghép tiền tố là việc của nơi lưu bền (:app), và cho hàm này nhận cả hai dạng sẽ mở
     * đường đoán tên hồ sơ từ chuỗi — mà tên hồ sơ do người dùng đặt, có thể chứa `_`.
     *
     * Thứ tự xét là **hẹp trước, rộng sau**: một khoá tạm hoặc theo-xe không bao giờ được rơi vào nhánh theo-hồ-sơ
     * chỉ vì nó khớp một tiền tố. [Scope.UNKNOWN] = chưa xếp loại ⇒ `ProfileScopeTest` đỏ.
     */
    fun scopeOf(prefKey: String): Scope = when {
        prefKey in TRANSIENT_KEYS -> Scope.TRANSIENT
        prefKey in DEVICE_KEYS -> Scope.DEVICE
        DEVICE_KEY_PREFIXES.keys.any { prefKey.startsWith(it) } -> Scope.DEVICE
        prefKey in LAUNCHER_SUFFIXES -> Scope.PROFILE
        prefKey in CLUSTERNAV_PROFILE_KEYS -> Scope.PROFILE
        prefKey in LAUNCHER_OWNED_CLUSTERNAV_KEYS -> Scope.PROFILE
        PROFILE_KEY_PREFIXES.keys.any { prefKey.startsWith(it) } -> Scope.PROFILE
        else -> Scope.UNKNOWN
    }

    /** Tiện đọc cho tầng gọi: khoá này có đi theo hồ sơ không. */
    fun isProfileScoped(prefKey: String): Boolean = scopeOf(prefKey) == Scope.PROFILE

    /**
     * Khoá **chưa xếp loại** trong [keys] ⇒ *"thêm mục mới mà quên xếp loại"*. Rỗng là đúng.
     *
     * Cùng vai [SettingsCatalog.orphans]: phép kiểm của R3 chạy bằng máy, không bằng mắt.
     */
    fun unclassified(keys: Collection<String>): Set<String> =
        keys.filterTo(mutableSetOf()) { scopeOf(it) == Scope.UNKNOWN }

    /**
     * Tiền tố khoá **dựng động** thuộc về hồ sơ → lý do. Sinh từ hai nguồn đã có, không chép tay:
     *  • `slot_` — nội dung từng ô ([SettingsCatalog.SLOT_KEY_PREFIX]);
     *  • `seat_level_` — mức từng ghế ([SettingsCatalog.CLUSTERNAV_DYNAMIC_KEY_PREFIXES]).
     *
     * ⚠ Khai SAU [scopeOf] không sao (hàm đọc nó lúc **chạy**), nhưng phải khai TRƯỚC bất kỳ `val` nào đọc nó —
     * thân `object` chạy theo thứ tự khai, bài học `TopStripConfig.BUILT_IN` ([ĐO] 27 bài đỏ).
     */
    val PROFILE_KEY_PREFIXES: Map<String, String> = buildMap {
        put(
            SettingsCatalog.SLOT_KEY_PREFIX,
            "nội dung từng ô — `WorkspacePrefs.save` ghi `slot_\$i` trong một vòng lặp tới " +
                "${WorkspaceState.SLOT_CAP} (SLOT_CAP)",
        )
        putAll(SettingsCatalog.CLUSTERNAV_DYNAMIC_KEY_PREFIXES)
        // V-CLUSTER — họ `cast_geometry` (DPI/khung từng app khi chiếu) theo HỒ SƠ.
        putAll(ProfileScopeCluster.FAMILY_PREFIXES)
    }
}
