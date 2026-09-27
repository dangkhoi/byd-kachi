package com.byd.clusternav.launcher.camera

import com.byd.clusternav.testsupport.SourceRoots
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
    private val car by lazy { app("launcher/SettingsSectionsCar.kt") }

    /** Khoá người lái → mảnh getter qua cầu phải có trong `cameraUser`. */
    private val userRows = mapOf(
        "camera_signal_enabled" to "bridge.cameraSignal()",
        "camera_on_cluster" to "bridge.cameraOnCluster()",
        "camera_pos_left" to "bridge.cameraPosLeft()",
        "camera_pos_right" to "bridge.cameraPosRight()",
        "camera_rot_left" to "bridge.cameraRotLeft()",
        "camera_rot_right" to "bridge.cameraRotRight()",
        "camera_mirror_left" to "bridge.cameraMirrorLeft()",     // 2.76 L7 — ô tích lật gương từng bên
        "camera_mirror_right" to "bridge.cameraMirrorRight()",
        "camera_shape" to "bridge.cameraShape()",
        "camera_dewarp_amount" to "bridge.cameraDewarpAmount() > CameraDewarpPrefs.AMOUNT_MIN",
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
    )

    @Test fun `man nguoi lai co dung cac hang cua USER_KEYS`() {
        val user = SourceRoots.body(settings, "private fun cameraUser(")
        assertEquals(CameraSettingsIa.USER_KEYS.toSet(), userRows.keys, "bảng canh phải khớp hợp đồng `:core`")
        userRows.forEach { (key, needle) -> assertTrue(needle in user, "khoá người lái $key không có hàng: thiếu `$needle`") }
        // Nắn = MỘT ô tích 100/0, không núm.
        assertTrue("if (on) CameraDewarpPrefs.AMOUNT_MAX else CameraDewarpPrefs.AMOUNT_MIN" in user, "ô tích nắn ghi AMOUNT_MAX/AMOUNT_MIN")
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
            build.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") && it !in setOf("{", "}") },
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
        listOf(
            "kachi_camera_shape_cluster", "kachi_camera_dewarp_on_sub_header", "kachi_camera_dewarp_on_title",
            "kachi_camera_dewarp_on_sub",
        ).forEach { k ->
            assertTrue("\"$k\"" in vi, "thiếu VI $k"); assertTrue("\"$k\"" in en, "thiếu EN $k")
            assertTrue("R.string.$k" in settings, "chữ $k không được dùng ⇒ mồ côi")
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
}
