package com.byd.clusternav.launcher

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
        listOf("preset", "dock_edge", "dock_enabled", "dock_visible", "top_strip", "grid_layout")

    /**
     * S4 · R3(a) — khoá TRƯỚC ĐÂY chung cả máy, nay **theo hồ sơ**.
     *
     * Owner: *"mỗi người lái khác nhau hoặc tình huống khác nhau thì switch profile là OK"*. Chủ đề/đơn vị/hình
     * nền/ngôn ngữ/tự-mở đều là *lựa chọn của một người*, nên để chung cả máy nghĩa là hồ sơ chỉ cover được một nửa.
     *
     * ⚠ `lang` nằm ở **tệp khác** (`clusternav_lang`, dùng chung cho cả APK) nhưng vẫn là hậu tố theo hồ sơ ở đây:
     * bản theo hồ sơ là **nguồn sự thật**, còn khoá chung kia là chỗ mà `attachBaseContext` của ClusterNav đọc ⇒ đổi
     * hồ sơ thì ghi cả hai (cùng khuôn `theme_mode`/`theme_choice` đã có từ IA v2). Vì vậy `lang` **không** được đồng
     * thời nằm trong ảnh chụp ClusterNav — xem [LAUNCHER_OWNED_CLUSTERNAV_KEYS].
     */
    val LAUNCHER_PERSONAL_SUFFIXES: List<String> =
        listOf("theme_mode", "unit_prefs", "wallpaper_prefs", "launcher_autostart", "lang")

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
            "keep_home_on_boot",
            "S5 — 'giữ Kachi làm màn hình chính khi nổ máy'. Màn hình chính là thuộc tính của **cả xe** (một " +
                "`cmd package set-home-activity` cho user 0), không phải lựa chọn của một tài xế: chép nó theo hồ " +
                "sơ thì đổi hồ sơ lại đi đặt/không-đặt HOME của cả máy. Cùng họ `boot_profile`/`cast_enabled` — " +
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
        put("doze_whitelist_applied", "trạng thái máy: đã xin miễn doze cho tiến trình chưa — thuộc máy, không thuộc người")
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
            "cast_enabled",
            "S4 · OQ2 chốt ở Pass 1 review (2026-09-14) — công tắc CHÍNH của phiên chiếu lên cụm. Nó theo XE vì " +
                "**không có đường áp an toàn** lúc đang lăn bánh: [ĐO] mọi cổng đọc nó đều LIVE " +
                "(`ClusterNavLaneWidget.kt:110` nhịp thông báo · `NavRepository.kt:215` mỗi khung · " +
                "`FloatingBubbleService.kt:170` mỗi lượt start), nên chỉ đổi giá trị mà không mở/đóng projection là " +
                "để hai bên cùng tưởng mình sở hữu mặt cụm: bật→tắt ⇒ `decide()` chuyển GATED_CAST → ASSERT và op " +
                "39 ghi đè lên cụm mà phiên chiếu vẫn chạy (đúng thứ KDoc `ClusterNavLaneWidget` cấm: *Cast ON " +
                "still wins*); tắt→bật ⇒ HUD thôi ghi mặt cụm mà không có phiên chiếu nào thay chỗ ⇒ cụm trống. " +
                "Còn áp THẬT (`setCastEnabled`) thì làm cụm trước mặt người lái tối đi/sáng lên vì một cú chạm chip " +
                "hồ sơ. Mở lại theo hồ sơ được khi có đường áp gác theo 'phiên chiếu không chạy' — backlog S4-OQ2",
        )
        put("enable_freeform_support", "cờ boot của HỆ THỐNG (`Settings.Global`) — thuộc máy")
        put("force_resizable_activities", "cờ boot của HỆ THỐNG (`Settings.Global`), gieo CẶP với khoá trên")
        put("enabled_accessibility_services", "danh sách trợ năng DÙNG CHUNG với mọi app khác (`Settings.Secure`)")
        put("accessibility_enabled", "cờ trợ năng toàn hệ thống (`Settings.Secure`) — máy tự đổi sau lưng")
    }

    /**
     * Tiền tố khoá **dựng động** theo xe → lý do. `SimpleCastRuntime` ghi `"config_size_$key"` với `key` = gói + hồ
     * sơ chiếu, nên bốn tên đầy đủ không tồn tại nguyên văn trong mã (cùng ca [SettingsCatalog.SLOT_KEY_PREFIX]).
     *
     * Đây là **hình học khi chiếu** mà R4 chỉ đích danh: khung/DPI đo theo *màn cụm của chiếc xe này*. Chép sang xe
     * khác — hoặc sang hồ sơ rồi áp ngược lại — là áp một khung sai lên phần cứng thật.
     */
    val DEVICE_KEY_PREFIXES: Map<String, String> = mapOf(
        "config_size_" to "`wm size` đã đo cho màn cụm của chính xe này",
        "config_overscan_" to "`wm overscan` đã đo cho màn cụm của chính xe này",
        "config_density_" to "`wm density` đã đo cho màn cụm của chính xe này",
        "config_bounds_" to "khung cửa sổ đã đo cho màn cụm của chính xe này",
    )

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
        SettingsCatalog.CLUSTERNAV_KEYS
            .filterKeys { key ->
                key !in DEVICE_KEYS && key !in TRANSIENT_KEYS && key !in LAUNCHER_OWNED_CLUSTERNAV_KEYS
            }
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
    }
}
