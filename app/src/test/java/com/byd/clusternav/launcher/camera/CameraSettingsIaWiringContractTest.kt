package com.byd.clusternav.launcher.camera

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.76 · R1 — IA hai tầng: bài canh DÂY NỐI của `:app` (hợp đồng thuần ở `CameraSettingsIaTest`) ═══════════════
 *
 * `LinearLayout`/`TestBridgeStore` không chạy off-car nên ở đây canh **nguồn**: mỗi khoá của [CameraSettingsIa.USER_KEYS]
 * có hàng trong `cameraUser`, mỗi khoá kỹ thuật có hàng trong `cameraTech`, và `cameraTech` chỉ được dựng sau cổng
 * `TestBridgeStore.isOn` — gỡ cổng ấy là 16 hàng đo quay lại màn người lái mà không bài `:core` nào đỏ (CLAUDE.md §8).
 */
class CameraSettingsIaWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val settings by lazy { app("launcher/SettingsSectionsCamera.kt") }
    private val car by lazy { app("launcher/SettingsSectionsCar.kt") }
    private val bridgeCam by lazy { app("launcher/ClusterNavBridgeCamera.kt") }

    /** Khoá người lái → mảnh getter qua cầu phải có trong `cameraUser` (hoặc `cameraSource` cho hàng Nguồn). */
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
        "camera_source" to "bridge.cameraSource()",
    )

    /** Khoá kỹ thuật → mảnh getter trong `cameraTech`/`cameraDewarp`. `camera_circle_scale` chỉ có `prefs_set`, không hàng. */
    private val techRows = mapOf(
        "camera_render" to "bridge.cameraRender()",
        "camera_span" to "bridge.cameraSpan()",
        "camera_strip_left" to "bridge.cameraStripLeft()",
        "camera_strip_right" to "bridge.cameraStripRight()",
        "camera_hal_mode" to "bridge.cameraHalMode()",
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

    @Test fun `tang nguoi lai co dung cac hang cua USER_KEYS va khong hang ky thuat nao`() {
        val user = SourceRoots.body(settings, "private fun cameraUser(") + SourceRoots.body(settings, "private fun cameraSource(")
        assertEquals(CameraSettingsIa.USER_KEYS.toSet(), userRows.keys, "bảng canh phải khớp hợp đồng `:core`")
        userRows.forEach { (key, needle) -> assertTrue(needle in user, "khoá người lái $key không có hàng: thiếu `$needle`") }
        techRows.values.forEach { needle -> assertFalse(needle in user, "getter kỹ thuật `$needle` lộ ở tầng người lái") }
        // Nắn = MỘT ô tích 100/0, không núm.
        assertTrue("if (on) CameraDewarpPrefs.AMOUNT_MAX else CameraDewarpPrefs.AMOUNT_MIN" in user, "ô tích nắn ghi AMOUNT_MAX/AMOUNT_MIN")
        assertFalse("knob(" in SourceRoots.body(settings, "private fun cameraUser("), "không một núm −/+ nào ở tầng người lái")
    }

    @Test fun `tang ky thuat co du cac hang cua TECH_KEYS`() {
        val tech = SourceRoots.body(settings, "private fun cameraTech(") + SourceRoots.body(settings, "private fun cameraDewarp(")
        assertEquals(CameraSettingsIa.TECH_KEYS.toSet() - "camera_circle_scale", techRows.keys, "bảng canh phải khớp hợp đồng `:core`")
        techRows.forEach { (key, needle) -> assertTrue(needle in tech, "khoá kỹ thuật $key không có hàng: thiếu `$needle`") }
        assertTrue("cameraDewarp(body)" in SourceRoots.body(settings, "private fun cameraTech("), "tám núm nắn có call site trong tầng kỹ thuật")
        // Số mục trên tiêu đề khối gập = kích thước TECH_KEYS ở `:core`, không gõ số.
        assertTrue("CameraSettingsIa.TECH_KEYS.size" in SourceRoots.body(settings, "private fun cameraAdvanced("))
    }

    /** Cổng: `cameraAdvanced` chỉ chạy sau `TestBridgeStore.isOn(context)`; tắt ⇒ không dựng (không mờ). */
    @Test fun `tang ky thuat dung sau cong che do kiem thu`() {
        val build = SourceRoots.body(settings, "    fun build(")
        assertTrue("if (TestBridgeStore.isOn(context)) cameraAdvanced(body)" in build, "cổng = cùng cổng cầu kiểm thử, không cờ mới")
        assertTrue("cameraUser(body)" in build)
        assertEquals(1, Regex("""cameraAdvanced\(body\)""").findAll(settings).count(), "chỉ MỘT call site, và nó nằm sau cổng")
        // Khối gập dùng component có sẵn (2.74 R3), thân dựng trễ.
        assertTrue("rows.disclosureRow(Disclosure(" in SourceRoots.body(settings, "private fun cameraAdvanced("))
        assertTrue("{ inner -> cameraTech(inner) }" in SourceRoots.body(settings, "private fun cameraAdvanced("))
    }

    /** Hàng Nguồn chỉ khi hồ sơ có bản đồ kênh; dấu "đã lùi" chỉ khi đang chọn CHANNEL — cả hai qua cầu. */
    @Test fun `hang Nguon theo ban do kenh cua ho so va dau da lui`() {
        val src = SourceRoots.body(settings, "private fun cameraSource(")
        assertTrue("if (!bridge.cameraChannelSupported()) return" in src, "xe chưa đo kênh ⇒ hàng vắng")
        assertTrue("bridge.cameraChannelFallback()" in src && "R.string.kachi_camera_channel_fallback_note" in src)
        assertTrue("bridge.cameraSource() == CameraSignalPolicy.SOURCE_CHANNEL" in src, "dấu chỉ có nghĩa khi đang chọn CHANNEL")
        assertTrue("fun ClusterNavBridge.cameraChannelSupported(): Boolean = CameraDefaults.of(app).hasChannelMap" in bridgeCam)
        assertTrue("fun ClusterNavBridge.cameraChannelFallback(): String = Prefs.cameraChannelFallback(app)" in bridgeCam)
    }

    /** `SettingsSectionsCar` không còn hàng camera nào — chỉ uỷ quyền; và tệp mới có chữ ở CẢ hai ngôn ngữ. */
    @Test fun `SettingsSectionsCar chi uy quyen, chu moi co o ca hai ngon ngu`() {
        assertTrue("SettingsCameraSection(context, rows, deps).build(body)" in car)
        listOf("bridge.cameraRender()", "bridge.cameraSpan()", "knob(", "CameraDewarpPrefs").forEach {
            assertFalse(it in car, "`$it` còn trong SettingsSectionsCar — camera phải sang tệp riêng trọn vẹn")
        }
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        listOf(
            "kachi_camera_shape_cluster", "kachi_camera_dewarp_on_sub_header", "kachi_camera_dewarp_on_title",
            "kachi_camera_dewarp_on_sub", "kachi_camera_advanced_title", "kachi_camera_advanced_count",
            "kachi_camera_advanced_note", "kachi_camera_channel_fallback_note",
        ).forEach { k ->
            assertTrue("\"$k\"" in vi, "thiếu VI $k"); assertTrue("\"$k\"" in en, "thiếu EN $k")
            assertTrue("R.string.$k" in settings, "chữ $k không được dùng ⇒ mồ côi")
        }
        // Nhãn "(mặc định)" đã bỏ khỏi chip: mặc định nay theo hồ sơ xe, một nhãn cố định sẽ sai cho xe khác.
        listOf("kachi_camera_render_texture", "kachi_camera_span_narrow", "kachi_camera_shape_rect").forEach { k ->
            val line = vi.lines().first { "\"$k\"" in it }
            assertFalse("mặc định" in line, "$k còn nhãn '(mặc định)' — mặc định nay theo hồ sơ xe")
        }
    }
}
