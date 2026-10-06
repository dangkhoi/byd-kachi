package com.byd.clusternav.launcher.camera

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.77 — IA MỘT tầng: bài canh DÂY NỐI của `:app` (hợp đồng thuần ở `CameraSettingsIaTest`) ══════════════════════
 *
 * `LinearLayout` không chạy off-car nên ở đây canh **nguồn**: mỗi khoá của [CameraSettingsIa.USER_KEYS] có đúng một
 * hàng trong `cameraUser`, và **không có hàng nào khác** — tức là bài này đỏ khi ai đó thêm một hàng camera vào màn
 * người lái mà không đổi hợp đồng `:core` (đúng bẫy CLAUDE.md §8: dựng hàng mà không ai đếm).
 *
 * 2.76 có thêm khối gập *"Nâng cao (kỹ thuật)"* (16 hàng đo) và một hàng *Nguồn*. Owner gỡ cả hai trên xe 27/09 —
 * *"bỏ hết phần nâng cao đi, bỏ luôn nguồn"*; bài này giờ canh **sự VẮNG MẶT** của chúng: 15 khoá kia còn ghi được
 * qua `prefs_set` nhưng **không được có một hàng nào** (nếu không thì việc gỡ đã bị hoàn lại mà không ai thấy).
 */
class CameraSettingsIaWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val settings by lazy { app("launcher/SettingsSectionsCamera.kt") }
    // 2.93 · CAMERA-PER-CAM-CONFIG — bộ chỉnh *Từng camera* + bảng nhãn chip dùng chung.
    private val perCam by lazy { app("launcher/SettingsSectionsCameraPerCam.kt") }
    private val labels by lazy { app("launcher/CameraSettingsLabels.kt") }
    private val car by lazy { app("launcher/SettingsSectionsCar.kt") }

    /** Khoá người lái → mảnh getter qua cầu phải có trong `cameraUser`. */
    private val userRows = mapOf(
        "camera_signal_enabled" to "bridge.cameraSignal()",
        "camera_on_cluster" to "bridge.cameraOnCluster()",
        // 2.93: sáu hàng góc/xoay/lật trái-phải (2.35 · 2.71 · 2.76 L7) RỜI khối này sang bộ chỉnh *Từng camera* — xem
        // [perCamRows]; cùng sáu khoá, hàng theo camera.
        "camera_shape" to "bridge.cameraShape()",
        // 2.92 · CAMERA-FULL-VIEW — ô tích Nắn hình ⇒ hàng chip Kiểu hình + thanh kéo Thu phóng.
        "camera_projection" to "bridge.cameraProjection()",
        "camera_zoom" to "bridge.cameraZoom()",
        // +2 (2026-09-28) — khối *Nếu camera không hiện* (CAM-SL6-RIGHT). Owner trên SL6 không mở được cam
        // phải; mỗi góc nhìn mang cả lệnh xuất hình lẫn camera id nên đây là núm dò đúng cho người lái.
        "camera_view_left" to "bridge.cameraViewLeft()",
        "camera_view_right" to "bridge.cameraViewRight()",
        "camera_pano_left" to "bridge.cameraPanoLeft()",
        "camera_pano_right" to "bridge.cameraPanoRight()",
    )

    /**
     * 2.93 — LOẠI khoá của bộ chỉnh *Từng camera* → mảnh getter theo camera (`w` = camera đang chọn ở hàng chip đầu). Một
     * bộ hàng cho cả bốn camera (owner 27/09 *"nhiều option quá rối"* ⇒ không nhân bốn số hàng).
     */
    private val perCamRows = mapOf(
        CameraCamConfig::cornerKey to "bridge.cameraCorner(w)",
        CameraCamConfig::placeKey to "bridge.cameraPlace(w)",
        CameraCamConfig::sizeKey to "bridge.cameraSize(w)",
        CameraCamConfig::shapeKey to "bridge.cameraShapeChoice(w)",
        CameraCamConfig::projectionKey to "bridge.cameraProjectionChoice(w)",
        CameraCamConfig::rotationKey to "bridge.cameraRotationOf(w)",
        CameraCamConfig::mirrorKey to "bridge.cameraMirrorOf(w)",
    )

    /** Khoá KHÔNG còn UI → mảnh getter của hàng đã gỡ. Mảnh nào xuất hiện lại trong tệp Cài đặt là hàng đã sống lại. */
    private val noUiGetters = mapOf(
        "camera_render" to "bridge.cameraRender()",
        "camera_span" to "bridge.cameraSpan()",
        "camera_strip_left" to "bridge.cameraStripLeft()",
        "camera_strip_right" to "bridge.cameraStripRight()",
        "camera_cam_left" to "bridge.cameraCamLeft()",
        "camera_cam_right" to "bridge.cameraCamRight()",
        "camera_gl_texmatrix" to "bridge.cameraGlTexMatrix()",
        "camera_dewarp_cx" to "bridge.cameraDewarpCx()",
        "camera_dewarp_cy" to "bridge.cameraDewarpCy()",
        "camera_dewarp_k" to "bridge.cameraDewarpK()",
        "camera_dewarp_focal" to "bridge.cameraDewarpFocal()",
        "camera_dewarp_scale" to "bridge.cameraDewarpScale()",
        "camera_dewarp_pan_x" to "bridge.cameraDewarpPanX()",
        "camera_dewarp_pan_y" to "bridge.cameraDewarpPanY()",
        // 2.92 — ô tích Nắn hình đã gỡ: khoá amount còn ghi/đọc qua prefs_set nhưng KHÔNG còn hàng nào.
        "camera_dewarp_amount" to "bridge.cameraDewarpAmount()",
    )

    @Test fun `man nguoi lai co dung cac hang cua USER_KEYS`() {
        val user = SourceRoots.body(settings, "private fun cameraUser(")
        assertEquals(CameraSettingsIa.USER_KEYS.toSet(), userRows.keys, "bảng canh phải khớp hợp đồng `:core`")
        userRows.forEach { (key, needle) -> assertTrue(needle in user, "khoá người lái $key không có hàng: thiếu `$needle`") }
        // 2.93: PER_CAMERA_KEYS = đúng bảy loại khoá × bốn camera, mỗi LOẠI có một hàng ở bộ chỉnh *Từng camera*, và bộ
        // chỉnh được dựng TỪ khối người lái (một lượt, không cổng).
        assertTrue("SettingsCameraPerCamSection(context, rows, deps).build(body)" in user, "bộ chỉnh Từng camera phải được dựng")
        assertEquals(
            CameraSettingsIa.PER_CAMERA_KEYS.toSet(),
            CameraWhich.ALL.flatMap { w -> perCamRows.keys.map { it(w) } }.toSet(),
            "bảng canh Từng camera phải khớp hợp đồng `:core`",
        )
        val ed = SourceRoots.body(perCam, "private fun rebuild(")
        perCamRows.values.forEach { needle -> assertTrue(needle in ed, "bộ chỉnh Từng camera thiếu hàng `$needle`") }
        assertTrue("CameraWhich.ALL.map" in SourceRoots.body(perCam, "    fun build("), "hàng chip chọn camera SINH từ `:core` (đủ bốn)")
        // Sáu hàng theo bên cũ không được sống lại ở khối chung (một khoá, một hàng).
        listOf("bridge.cameraPosLeft()", "bridge.cameraPosRight()", "bridge.cameraRotLeft()", "bridge.cameraRotRight()",
            "bridge.cameraMirrorLeft()", "bridge.cameraMirrorRight()").forEach {
            assertFalse(it in settings, "`$it` đã dời vào bộ chỉnh Từng camera — hai hàng cho một khoá là hai nơi để lệch")
        }
        // 2.92: "nhìn kiểu nào" = MỘT hàng chip sinh từ `:core` + MỘT thanh kéo; không núm −/+ nào.
        assertTrue("CameraViewMode.MODES.map" in user, "chip kiểu hình sinh từ `:core`, không gõ tay danh sách")
        assertTrue("rows.sliderRow(" in user && "CameraViewMode.ZOOM_POSITIONS" in user, "thanh kéo thu phóng, miền ở `:core`")
        assertFalse("knob(" in settings, "không một núm −/+ nào còn trong tệp Cài đặt camera")
    }

    /**
     * ═══ Khối *Nâng cao (kỹ thuật)* và hàng *Nguồn* phải VẮNG — canh bằng sự vắng mặt ═══════════════════════════
     *
     * Owner trên xe 27/09: *"không biết chỉnh đâu, nên chốt theo cái nào best là được, bỏ hết phần nâng cao đi"*.
     * Mỗi mảnh dưới đây là một mảnh của khối đã gỡ; nó xuất hiện lại = 16 hàng đo quay về màn người lái.
     */
    @Test fun `khong con tang ky thuat, khong con hang Nguon`() {
        listOf(
            "cameraAdvanced(", "cameraTech(", "cameraDewarp(", "cameraSource(",
            "TestBridgeStore.isOn(context)", "rows.disclosureRow(", "rows.stepperRow(",
            "CameraSettingsIa.TECH_KEYS", "R.string.kachi_camera_advanced_title", "R.string.kachi_camera_source_row",
            "R.string.kachi_camera_channel_fallback_note", "R.string.kachi_camera_hal_row",
            "bridge.cameraSource()", "bridge.cameraHalMode()", "bridge.cameraChannelSupported()",
        ).forEach { assertFalse(it in settings, "`$it` đã gỡ ở 2.77 — thấy lại là tầng kỹ thuật/hàng Nguồn đã sống lại") }
        noUiGetters.forEach { (key, needle) ->
            assertFalse(needle in settings, "khoá $key không được có hàng nào (`$needle`) — nó chỉ còn đường prefs_set")
        }
        // `build()` chỉ còn MỘT lượt, không cổng nào.
        val build = SourceRoots.body(settings, "    fun build(")
        assertTrue("cameraUser(body)" in build)
        assertEquals(
            listOf("cameraUser(body)"),
            build.lines().map { it.trim() }.filter { it.isNotEmpty() && it !in setOf("{", "}") },   // đã qua codeOf: 0 dòng chú thích
            "build() chỉ còn đúng một lượt dựng — không cổng, không khối thứ hai",
        )
        // Tệp `ClusterNavBridgeCamera.kt` (hai câu hỏi chỉ-đọc của hàng Nguồn) đã xoá cùng hàng ấy.
        assertFalse(SourceRoots.exists("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeCamera.kt"),
            "tệp cầu của hàng Nguồn phải xoá — để lại là một cửa không ai gọi (CLAUDE.md §8)")
    }

    /** `SettingsSectionsCar` không còn hàng camera nào — chỉ uỷ quyền; và chữ còn dùng có ở CẢ hai ngôn ngữ. */
    @Test fun `SettingsSectionsCar chi uy quyen, chu moi co o ca hai ngon ngu`() {
        assertTrue("SettingsCameraSection(context, rows, deps).build(body)" in car)
        listOf("bridge.cameraRender()", "bridge.cameraSpan()", "knob(", "CameraDewarpPrefs").forEach {
            assertFalse(it in car, "`$it` còn trong SettingsSectionsCar — camera phải sang tệp riêng trọn vẹn")
        }
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        // 2.93: nhãn chip hình/kiểu dời vào bảng chung `CameraSettingsLabels`; chữ của bộ chỉnh Từng camera ở tệp riêng.
        val used = settings + perCam + labels
        listOf(
            "kachi_camera_shape_cluster", "kachi_camera_projection_sub", "kachi_camera_projection_row",
            "kachi_camera_projection_straight", "kachi_camera_projection_wide", "kachi_camera_projection_fisheye",
            "kachi_camera_projection_note", "kachi_camera_zoom_row", "kachi_camera_zoom_desc",
            // 2.93 · Từng camera + nút Xem thử.
            "kachi_camera_percam_sub", "kachi_camera_percam_note", "kachi_camera_percam_row",
            "kachi_camera_preview_on", "kachi_camera_preview_off", "kachi_camera_corner_row",
            "kachi_camera_place_hint", "kachi_camera_place_reset", "kachi_camera_size_row", "kachi_camera_size_desc",
            "kachi_camera_follow", "kachi_camera_shape_own_row", "kachi_camera_projection_own_row",
            "kachi_camera_rot_own_row", "kachi_camera_mirror_own_title",
            "kachi_cam_chip_rear", "kachi_cam_chip_left", "kachi_cam_chip_right", "kachi_cam_chip_front",
        ).forEach { k ->
            assertTrue("\"$k\"" in vi, "thiếu VI $k"); assertTrue("\"$k\"" in en, "thiếu EN $k")
            assertTrue("R.string.$k" in used, "chữ $k không được dùng ⇒ mồ côi")
        }
        // 2.93: chữ của sáu hàng theo bên (góc/xoay/lật trái-phải) phải XOÁ — hàng của chúng đã gộp vào bộ chỉnh.
        listOf(
            "kachi_camera_pos_sub", "kachi_camera_pos_left", "kachi_camera_pos_right",
            "kachi_camera_rot_sub", "kachi_camera_rot_row_left", "kachi_camera_rot_row_right",
            "kachi_camera_mirror_sub", "kachi_camera_mirror_row_left", "kachi_camera_mirror_row_right",
        ).forEach { k ->
            assertFalse("\"$k\"" in vi, "chữ VI $k mồ côi — hàng của nó đã gộp vào bộ chỉnh Từng camera (2.93)")
            assertFalse("\"$k\"" in en, "chữ EN $k mồ côi — hàng của nó đã gộp vào bộ chỉnh Từng camera (2.93)")
        }
        // Chữ của khối gập + hàng Nguồn + 16 hàng đo phải XOÁ khỏi cả hai tệp chữ (i18n mồ côi — CLAUDE.md §4.1).
        listOf(
            "kachi_camera_advanced_title", "kachi_camera_advanced_count", "kachi_camera_advanced_note",
            "kachi_camera_channel_fallback_note", "kachi_camera_source_sub", "kachi_camera_source_row",
            "kachi_camera_source_pano", "kachi_camera_source_channel",
            "kachi_camera_render_sub", "kachi_camera_render_row", "kachi_camera_render_texture",
            "kachi_camera_render_surface", "kachi_camera_render_gl",
            "kachi_camera_span_sub", "kachi_camera_span_row", "kachi_camera_span_narrow", "kachi_camera_span_strip",
            "kachi_camera_strip_sub", "kachi_camera_strip_left", "kachi_camera_strip_right",
            "kachi_camera_hal_sub", "kachi_camera_hal_row", "kachi_camera_hal_auto",
            "kachi_camera_pick_sub", "kachi_camera_pick_left", "kachi_camera_pick_right",
            "kachi_camera_gl_texmatrix_title", "kachi_camera_gl_texmatrix_sub",
            "kachi_camera_dewarp_sub", "kachi_camera_dewarp_note",
            "kachi_camera_dewarp_cx", "kachi_camera_dewarp_cy", "kachi_camera_dewarp_k", "kachi_camera_dewarp_focal",
            "kachi_camera_dewarp_scale", "kachi_camera_dewarp_amount", "kachi_camera_dewarp_pan_x",
            "kachi_camera_dewarp_pan_y",
            // 2.92 — ba chữ của ô tích *Nắn hình* (hàng chip Kiểu hình thay).
            "kachi_camera_dewarp_on_sub_header", "kachi_camera_dewarp_on_title", "kachi_camera_dewarp_on_sub",
        ).forEach { k ->
            assertFalse("\"$k\"" in vi, "chữ VI $k mồ côi — hàng của nó đã gỡ ở 2.77")
            assertFalse("\"$k\"" in en, "chữ EN $k mồ côi — hàng của nó đã gỡ ở 2.77")
        }
        // Nhãn "(mặc định)" đã bỏ khỏi chip: mặc định nay theo hồ sơ xe, một nhãn cố định sẽ sai cho xe khác.
        listOf("kachi_camera_shape_rect").forEach { k ->
            val line = vi.lines().first { "\"$k\"" in it }
            assertFalse("mặc định" in line, "$k còn nhãn '(mặc định)' — mặc định nay theo hồ sơ xe")
        }
    }

    /**
     * ═══ 2.83 — VẠCH CHUẨN khoảng cách (2.82) phải VẮNG ở mọi tầng — canh bằng sự vắng mặt ════════════════════════
     *
     * Owner 29/09 sau buổi xe: *"dẹp vạch đi"*. Tính năng đi qua sáu tầng (pref → cầu → Cài đặt → `prefs_set` →
     * bộ điều khiển → lớp vẽ), và các làn 2.83 khác đều dựng từ 2.82 — tức từ cây CÒN vạch. Một lượt gộp sơ ý ở bất kỳ
     * tầng nào là vạch sống lại một nửa: hàng Cài đặt còn mà không ai vẽ, hay lớp vẽ còn mà không ai bật. Nên bài này
     * quét **toàn cây `main` của mọi module** (mã đã bỏ chú thích — chú thích lịch sử được phép nhắc tên), không chỉ
     * vài tệp đã biết.
     *
     * Muốn đưa vạch trở lại thì đó là một QUYẾT ĐỊNH của owner: sửa bài này cùng lúc, kèm câu của owner.
     */
    @Test fun `vach chuan khoang cach da go o moi tang`() {
        val tokens = listOf("CameraGuide", "cameraGuide", "camera_guide_", "kachi_camera_guide_")
        val files = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }
        assertTrue(files.size > 100, "quét được ${files.size} tệp — cây nguồn không tìm thấy thì bài này xanh giả")
        val hits = files.flatMap { p ->
            val code = SourceRoots.codeOf(p.toString())
            tokens.filter { it in code }.map { "${p.fileName}: $it" }
        }
        assertEquals(emptyList<String>(), hits, "mã của vạch chuẩn còn sót — tính năng đã gỡ hẳn ở 2.83")
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        assertFalse("kachi_camera_guide_" in vi, "chữ VI của vạch chuẩn mồ côi — hàng đã gỡ ở 2.83")
        assertFalse("kachi_camera_guide_" in en, "chữ EN của vạch chuẩn mồ côi — hàng đã gỡ ở 2.83")
        listOf(
            "src/main/java/com/byd/clusternav/launcher/camera/CameraGuide.kt",
            "src/main/java/com/byd/clusternav/launcher/camera/CameraGuideLineView.kt",
        ).forEach { assertFalse(SourceRoots.exists(it), "$it phải xoá — để lại là mã không ai gọi (CLAUDE.md §8)") }
        // Tệp tách của 2.82 ở LẠI: lý do tách là trần 500 dòng (CLAUDE.md §4.1), không phải vạch — gộp lại là vỡ trần.
        assertTrue(SourceRoots.exists("src/main/java/com/byd/clusternav/launcher/camera/CameraOverlayLayout.kt"))
    }
}
