package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.simplified.CastEnableDeferral
import com.byd.clusternav.modules.clustercast.simplified.CastGeometryGuard

/**
 * ═══ V-CLUSTER (owner 2026-09-30: *"Phần cụm lưu hết thành profile nhé"*) — bảng phân loại CỤM · CHIẾU · CAMERA ═════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §11.4.1. Tách khỏi [ProfileScope] vì trần 500 dòng (CLAUDE.md
 * §4.1) — [ProfileScope] đọc các bảng ở đây, **không** chép lại.
 *
 * ## Hai nguyên tắc (spec §11.4.1)
 *  • **PROFILE** = sở thích của người lái về thứ hiện trên cụm/màn (chiếu hay không, nút nổi, DPI/khung từng app,
 *    camera lên đâu).
 *  • **DEVICE** = sự thật phần cứng của xe · trạng thái đã áp vào hệ thống · dấu mốc · dữ liệu đời cũ không ai đọc.
 *
 * ## Quyết định này THAY hai chỗ cũ (không xoá lịch sử — xem KDoc tại chỗ ở [ProfileScope.DEVICE_KEYS])
 *  • S4-OQ2 (`cast_enabled` theo xe) là chốt của Pass 1 review, không phải của owner. Lý do kỹ thuật của nó VẪN ĐÚNG
 *    và thành ràng buộc thiết kế: lượt đổi hồ sơ không bao giờ ghi khoá sống ([CastEnableDeferral]).
 *  • Dòng R4 *"khung/DPI/hình học khi chiếu theo xe"* là tóm tắt của agent. Lý do cũ ở `DEVICE_KEY_PREFIXES`
 *    (*"`wm density` đã đo cho màn cụm của chính xe này"*) **sai**: DPI và khung do người lái chọn bằng chip và thanh
 *    −/+; `config_size_` là hằng `1920x720` hoặc suy từ khung người lái chọn (refute B1).
 *
 * ⚠ Đối tượng này KHÔNG được đọc [ProfileScope]/[SettingsCatalog] lúc khởi tạo: [ProfileScope] đọc nó trong thân
 * `object` của mình, nên đọc ngược là vòng khởi tạo (giá trị `null` giữa chừng — bài học `TopStripConfig.BUILT_IN`).
 */
object ProfileScopeCluster {

    const val SIMPLE_CAST_FILE = "simple_cast_prefs"
    const val CLUSTERNAV_FILE = "clusternav_prefs"

    /** Tệp của `CastAppCatalog` (`:app`) — nay chỉ còn `bubbleX/bubbleY` có đường đọc sống. */
    const val CAST_CATALOG_FILE = "cast-v2-app-catalog"

    /** Dấu chạy-một-lần của lượt rót cấu hình cụm xuống mọi hồ sơ (tệp `kachi_workspace`). */
    const val MIGRATED_KEY = "migrated_cluster_profile_v1"

    /** Họ `cast_geometry`: bốn tiền tố của MỘT bản ghi hình học — cả bốn cùng đi (spec K4, refute B2). */
    val CAST_GEOMETRY = SnapshotFamily(
        id = "cast_geometry",
        file = SIMPLE_CAST_FILE,
        prefixes = CastGeometryGuard.KEY_PREFIXES,
        owns = CastGeometryGuard::isFamilyKey,
        valueOk = CastGeometryGuard::isValidValue,
    )

    val FAMILIES: List<SnapshotFamily> = listOf(CAST_GEOMETRY)

    /** Họ của một tệp prefs. */
    fun familiesOf(file: String): List<SnapshotFamily> = FAMILIES.filter { it.file == file }

    /** Tiền tố khoá dựng động theo HỒ SƠ → lý do; [ProfileScope.PROFILE_KEY_PREFIXES] cộng bảng này. */
    val FAMILY_PREFIXES: Map<String, String> = CAST_GEOMETRY.prefixes.associateWith {
        "V-CLUSTER — DPI/khung/kích/overscan từng app khi chiếu, do người lái chọn (chip DPI, thanh −/+). Một bản ghi " +
            "bốn khoá đi cùng nhau; ảnh chụp theo họ có mốc có mặt (ProfileScopeCluster.CAST_GEOMETRY)"
    }

    /** Khoá sống → khoá chờ: lượt áp hồ sơ chỉ ghi khoá chờ ([CastEnableDeferral]). */
    val DEFERRED: Map<String, String> = mapOf(CastEnableDeferral.LIVE_KEY to CastEnableDeferral.PENDING_KEY)

    /** Tệp prefs ClusterNav NGOÀI danh mục Cài đặt mà ảnh chụp ghi vào → lý do. */
    val EXTRA_FILES: Map<String, String> = mapOf(
        CAST_CATALOG_FILE to
            "prefs của CastAppCatalog — chỉ `bubbleX`/`bubbleY` (chỗ đặt nút nổi chiếu) theo hồ sơ; các khoá V2 còn lại " +
                "không có đường đọc sống nên theo xe. Không có mục Cài đặt nào (vị trí đặt bằng kéo-thả nút)",
    )

    /** Sáu khoá camera là SỞ THÍCH người lái → lý do. Cùng tệp `clusternav_prefs` (`autoPrefs`). */
    val CAMERA_PROFILE_KEYS: Map<String, String> = mapOf(
        "camera_signal_enabled" to "có muốn camera bật theo xi-nhan không — lựa chọn hiển thị, không điều khiển phần cứng",
        "camera_on_cluster" to "hình camera hiện trên cụm hay màn chính",
        "camera_pos_left" to "góc hiện hình bên trái — theo cách ngồi (vành lái/cột A che, PrefsAutomation)",
        "camera_pos_right" to "góc hiện hình bên phải — cùng lý do camera_pos_left",
        "camera_shape" to "khung RECT/ROUND/CLUSTER là cách TRÌNH BÀY, có chip ở tầng người lái",
        "camera_dewarp_amount" to "'có nắn hay không' (100/0) — thứ duy nhất người lái còn quyết về nắn; vắng ⇒ mặc định đời xe",
    )

    private const val R_MOUNT = "chiều ghép/tay gương của cam trong ảnh 4-in-1 — sự thật LẮP ĐẶT của chiếc xe này"
    private const val R_VIEW = "cam nào là trái/phải/360, cắt dải nào — cặp đúng khác nhau theo ĐỜI XE"
    private const val R_RENDER = "đường kết xuất theo ROM/GPU của đầu máy"
    private const val R_MIRROR_ZONE = "hiệu chỉnh vùng gương trong ảnh HAL — owner dò trên xe, không phải sở thích"
    private const val R_OPTICS = "tham số QUANG HỌC của ống kính — hai người lái cùng xe nhìn cùng một ống kính"

    /**
     * 23 khoá camera theo XE → lý do. Hợp với [CAMERA_PROFILE_KEYS] phải bằng ĐÚNG `CameraSettingsIa.USER_KEYS ∪
     * NO_UI_KEYS` (29) — `ProfileScopeClusterTest` đỏ khi `CameraSettingsIa` thêm khoá mà quên xếp loại ở đây.
     */
    val CAMERA_DEVICE_KEYS: Map<String, String> = buildMap {
        listOf("camera_rot_left", "camera_rot_right", "camera_mirror_left", "camera_mirror_right")
            .forEach { put(it, R_MOUNT) }
        listOf(
            "camera_view_left", "camera_view_right", "camera_pano_left", "camera_pano_right",
            "camera_cam_left", "camera_cam_right",
        ).forEach { put(it, R_VIEW) }
        listOf("camera_render", "camera_gl_texmatrix").forEach { put(it, R_RENDER) }
        listOf("camera_span", "camera_strip_left", "camera_strip_right", "camera_circle_scale")
            .forEach { put(it, R_MIRROR_ZONE) }
        listOf(
            "camera_dewarp_cx", "camera_dewarp_cy", "camera_dewarp_k", "camera_dewarp_focal", "camera_dewarp_scale",
            "camera_dewarp_pan_x", "camera_dewarp_pan_y",
        ).forEach { put(it, R_OPTICS) }
    }

    /**
     * Khoá theo HỒ SƠ không có mục Cài đặt → tệp prefs. [ProfileScope.CLUSTERNAV_KEYS] cộng bảng này (không nhét vào
     * `SettingsCatalogClusterNav.KEYS`: bảng đó đòi mỗi khoá có mục chủ, mà màn camera/nút nổi không có mục — spec K7).
     */
    val PROFILE_EXTRA_KEYS: Map<String, String> =
        CAMERA_PROFILE_KEYS.keys.associateWith { CLUSTERNAV_FILE } +
            mapOf("bubbleX" to CAST_CATALOG_FILE, "bubbleY" to CAST_CATALOG_FILE)

    /** Khoá đổi phạm vi XE → HỒ SƠ ở V-CLUSTER, theo tệp — đầu vào của lượt rót một lần ([ProfileScopeMigration]). */
    val MOVED_TO_PROFILE: Map<String, List<String>> = mapOf(
        SIMPLE_CAST_FILE to listOf(CastEnableDeferral.LIVE_KEY, "cast_bubble_visible"),
        CLUSTERNAV_FILE to CAMERA_PROFILE_KEYS.keys.toList(),
        CAST_CATALOG_FILE to listOf("bubbleX", "bubbleY"),
    )

    /** Khoá theo XE của phụ lục này → lý do; [ProfileScope.DEVICE_KEYS] cộng bảng này. */
    val DEVICE_KEYS: Map<String, String> = buildMap {
        put(CastEnableDeferral.PENDING_KEY, "dấu 'máy chưa khớp hồ sơ' — theo hồ sơ thì đổi hồ sơ lại đẻ ra bản chờ giả")
        put(CastEnableDeferral.COMMIT_MARK_KEY, "FIX286 — mốc bền lượt chốt bản chờ lúc khởi động của CHÍNH máy này (chẩn đoán)")
        put(MIGRATED_KEY, "dấu chạy-một-lần phải rộng hơn thứ nó bảo vệ (cùng lẽ migrated_nav_schedule_v1)")
        put("vm_float_whitelist_applied", "đã ghi VietMap vào byd_float_app_list — trạng thái đã áp vào HỆ THỐNG")
        put("profileOverride", "hồ sơ ĐỜI XE (kích cụm, tên service, chuỗi lệnh) — sự thật phần cứng, đã có parse chặt riêng")
        put("camera_rotation", "khoá đời 2.67, chỉ đọc một lần để di trú sang camera_rot_*")
        listOf("badge_corner", "badge_dx", "badge_dy", "bubble_auto")
            .forEach { put(it, "đời cũ — chỉ đọc một lần để di trú, hoặc là mã chết") }
        listOf("autoCast", "castable", "keepSession")
            .forEach { put(it, "tệp `clustercast` đời V1 — không còn ai ghi, chỉ còn lượt di trú một lần đọc") }
        put("migrationVersion", "dấu di trú một lần của CastAppCatalog")
        listOf("bubbleEnabled", "favorites", "protected", "legacyDefaultCandidate", "rectStyle")
            .forEach { put(it, "CastAppCatalog đời V2 — [ĐO] không có đường đọc sống: giữ nguyên trên máy, không chép") }
        putAll(CAMERA_DEVICE_KEYS)
    }

    /** Tiền tố dựng động theo XE → lý do (CastAppCatalog đời V2: `scale-dpi:<gói>`, `scale-l:<gói>`, `dpi:<gói>`). */
    val DEVICE_KEY_PREFIXES: Map<String, String> = mapOf(
        "scale-" to "khung/DPI từng app của V2 (CastAppCatalog.scaleOf) — [ĐO] không có đường đọc sống, không chép",
        "dpi:" to "DPI cụm từng app của V2 (CastAppCatalog.clusterDensityDpi) — [ĐO] không có đường đọc sống, không chép",
    )

    /**
     * Kiểu KHAI SẴN của khoá theo hồ sơ mà tầng này biết chắc (đọc từ chỗ `get*` thật). Lượt áp/nhập bỏ giá trị sai
     * kiểu — kể cả khi tệp sống đang VẮNG khoá (ca mà phép so với kiểu sống không bắt được).
     */
    val DECLARED_TYPES: Map<String, PrefType> = buildMap {
        // simple_cast_prefs — SharedPrefsSimpleCastPrefs (`:app`).
        listOf(CastEnableDeferral.LIVE_KEY, "cast_bubble_visible", "autostart_enabled", "autostart_split_enabled")
            .forEach { put(it, PrefType.BOOLEAN) }
        listOf("autostart_package", "autostart_left_package", "autostart_right_package")
            .forEach { put(it, PrefType.STRING) }
        put("split_ratio_left_pct", PrefType.INT)
        // clusternav_prefs — PrefsAutomation / PrefsCameraDewarp.
        listOf("camera_signal_enabled", "camera_on_cluster").forEach { put(it, PrefType.BOOLEAN) }
        listOf("camera_pos_left", "camera_pos_right", "camera_shape").forEach { put(it, PrefType.STRING) }
        put("camera_dewarp_amount", PrefType.INT)
        // cast-v2-app-catalog — CastAppCatalog.bubblePosition (`getInt`).
        listOf("bubbleX", "bubbleY").forEach { put(it, PrefType.INT) }
        // clusternav_prefs — nhóm "lên cụm" đã theo hồ sơ từ S4 (senior review V-CLUSTER Pass 1). Chỗ đọc là DỊCH VỤ đang
        // chạy trên xe: [ĐO code] `Prefs.enabled/marquee` getBoolean · `Prefs.navClusterScreenMode` getInt · `PrefsBadge`
        // getBoolean ×4 + getInt ×3 · `VmOverlayPosition.x/y` getInt. Tệp sống thường VẮNG các khoá này (người lái chưa
        // từng kéo/đổi) ⇒ phép so với kiểu sống không bắt được một chuỗi từ tệp nhập — spec §11.4.7 nêu đích danh
        // `enabled`. Khai kiểu = chặn `ClassCastException` trên đường; giá trị đúng kiểu đi qua y như cũ.
        listOf("enabled", "marquee", "badge_enabled", "show_upcoming_badge", "show_alert_chip", "vm_bubble_enabled")
            .forEach { put(it, PrefType.BOOLEAN) }
        listOf("nav_cluster_screen_mode", "badge_size_dp", "badge_center_x", "badge_center_y", "vm_bubble_x", "vm_bubble_y")
            .forEach { put(it, PrefType.INT) }
        // Khoá theo hồ sơ NGOÀI nhóm cụm (S4) — senior review V-CLUSTER Pass 2: spec §11.8 [P1] nói "MỘT khoá ClusterNav"
        // bất kỳ, không riêng nhóm cụm. [ĐO code] ca đắt nhất: `voicekey_bindings` đọc bằng `getString` trong
        // NavAccessibilityService mỗi lần bấm phím vô-lăng — tệp nhập đặt nó thành Boolean trên xe chưa từng gán phím (tệp
        // sống VẮNG khoá) ⇒ dịch vụ phím nổ. Kiểu lấy từ lượt GHI thật (`put*`) trong mã; bài canh `:app`
        // `ClusterProfileScopeCoverageTest` đối chiếu từng khoá với mã và đòi MỌI khoá của ảnh chụp có kiểu ở bảng này.
        listOf("voicekey_enabled", "seat_comfort_enabled", "pm25_filter_enabled", "recirc_on_start_enabled", "headless_autostart")
            .forEach { put(it, PrefType.BOOLEAN) }
        listOf(
            "voicekey_bindings", "voicekey_custom_buttons", "voice_music_default_app", "nav_automation_rules",
            "nav_automation_fired", "theme_choice",
        ).forEach { put(it, PrefType.STRING) }
        listOf("seat_comfort_mode", "seat_level_0", "seat_level_1", "seat_level_2", "seat_level_3")
            .forEach { put(it, PrefType.INT) }
    }
}
