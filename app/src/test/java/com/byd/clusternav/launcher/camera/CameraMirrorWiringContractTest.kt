package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.testbridge.TestBridgeWritableKeys
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.76 L7 — LẬT GƯƠNG từng bên: bài canh DÂY NỐI của `:app` (phép toán ở `:core`, [CameraMirrorTest]) ═══════════
 *
 * Research §6.2. Những mắt xích mà gỡ đi thì build vẫn xanh và không bài `:core` nào đỏ (CLAUDE.md §8): pref theo
 * bên → controller đọc MỘT lần mỗi phiên → đi vào **cả hai** đường (GL qua `flipH`, TV qua `matrix(…, mirror)`) →
 * `prefs_set` ghi + `read_back` → ô tích đảo lại được ở Cài đặt, chữ VI + EN.
 */
class CameraMirrorWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }
    private val overlay by lazy { app("launcher/camera/CameraOverlayView.kt") }
    private val layer by lazy { app("launcher/camera/CameraVideoLayer.kt") }
    private val prefsGl by lazy { app("PrefsCameraDewarp.kt") }
    private val prefsAuto by lazy { app("PrefsAutomation.kt") }
    private val prefsSet by lazy { app("launcher/testbridge/TestBridgePrefsSet.kt") }
    private val bridge by lazy { app("launcher/ClusterNavBridgeAutomation.kt") }
    private val settings by lazy { app("launcher/SettingsSectionsCamera.kt") }

    /** Pref theo BÊN, mặc định TẮT (tay gương HAL [CHƯA BIẾT] — không đoán), device-scope như `camera_rot_*`. */
    @Test fun `pref theo ben, mac dinh tat`() {
        assertTrue("private fun cameraMirrorKey(left: Boolean) = if (left) \"camera_mirror_left\" else \"camera_mirror_right\"" in prefsAuto)
        assertTrue("fun Prefs.cameraMirror(ctx: Context, left: Boolean): Boolean = autoPrefs(ctx).getBoolean(cameraMirrorKey(left), false)" in prefsAuto,
            "mặc định false: CAM-M1 chưa đo thì không được lật sẵn")
        assertTrue("fun Prefs.setCameraMirror(ctx: Context, left: Boolean, v: Boolean)" in prefsAuto)
    }

    /** Controller đọc pref đúng bên, MỘT lần mỗi phiên, rồi đưa vào CẢ uniforms (GL) lẫn `overlay.show` (TV) + nhật ký. */
    @Test fun `controller doc pref dung ben va dua vao ca hai duong`() {
        val body = SourceRoots.body(controller, "private fun openSession(")
        assertTrue("val mirror = Prefs.cameraMirror(appCtx, left = turn == Turn.LEFT)" in body, "đọc theo BÊN đang xi-nhan")
        assertEquals(2, Regex("""\bmirror = mirror,""").findAll(body).count(), "đi vào Prefs.cameraGlUniforms(…) VÀ overlay.show(…)")
        val gl = body.substring(body.indexOf("Prefs.cameraGlUniforms("), body.indexOf("} else {"))
        assertTrue("mirror = mirror," in gl, "đường GL: vào bộ uniform")
        val show = body.substring(body.indexOf("overlay.show("))
        assertTrue("mirror = mirror," in show, "đường TV: vào overlay.show")
        assertTrue("lật=\$mirror" in body, "dòng nhật ký phiên phải nói lật hay không")
    }

    /** GL: `mirror` → `flipH` của `CameraGlUniforms.of` (lật bằng `uSrcRect.z < 0`, không uniform mới); tham số KHÔNG có mặc định. */
    @Test fun `duong GL - mirror thanh flipH, khong co mac dinh`() {
        val sig = prefsGl.substring(prefsGl.indexOf("fun Prefs.cameraGlUniforms("), prefsGl.indexOf("): CameraGlUniforms"))
        assertTrue(Regex("""\n\s*mirror: Boolean,\n""").containsMatchIn(sig), "`mirror` là tham số bắt buộc, cùng lẽ `left`")
        val body = SourceRoots.body(prefsGl, "fun Prefs.cameraGlUniforms(")
        assertTrue("flipH = mirror," in body, "vào `flipH` — cơ chế đã có test ở `:core` (srcRect w < 0)")
    }

    /** TV: `show(mirror)` → `CameraVideoLayer.create(mirror)` → `applyTransform(…, mirror)` → `matrix(…, mirror)` ở CẢ ba chỗ áp ma trận. */
    @Test fun `duong TV - mirror di tu show qua lop video toi ma tran core`() {
        assertTrue("mirror: Boolean = false" in overlay, "show() mặc định KHÔNG lật = hành vi trước L7")
        assertTrue("mirror = mirror," in SourceRoots.body(overlay, "fun show("), "vào CameraVideoLayer.create")
        // ⚠ [SourceRoots.body] bỏ qua DANH SÁCH THAM SỐ (nó nhảy qua dấu `=` của tham số mặc định), nên một giá trị
        // mặc định chỉ khẳng định được trên văn bản cả tệp; chuỗi này xuất hiện đúng một lần ở đó (`glVideo` nhận
        // `mirror: Boolean` KHÔNG mặc định — nó là đường trong, bắt buộc truyền).
        assertTrue("mirror: Boolean = false" in layer, "create() mặc định không lật = hành vi trước L7")
        assertTrue("textureVideo(ctx, crop, rotationDeg, mirror, onSurfaceReady)" in layer)
        assertTrue("glVideo(ctx, gl, crop, rotationDeg, mirror, streamW, streamH, synthOn, synthFile, onSurfaceReady)" in layer,
            "đường RƠI của GL (TextureView 2.73) cũng phải lật — nếu không, GL hỏng là ảnh đổi tay")
        assertEquals(4, Regex("""applyTransform\(this@apply, w2, h2, crop, rotationDeg, mirror\)""").findAll(layer).count(),
            "bốn chỗ áp ma trận (TV available/size-changed + GL rơi available/size-changed) đều mang mirror")
        assertTrue("CameraOverlayTransform.matrix(vw, vh, crop, rotationDeg, mirror)" in SourceRoots.body(layer, "private fun applyTransform("))
        assertFalse("scaleX" in layer || "scaleX" in overlay, "KHÔNG lật bằng View.scaleX — lật sau xoay khác lật nguồn ở ±90 (CameraMirrorTest)")
    }

    /** `prefs_set` ghi được hai khoá (bool) và `read_back` đọc lại từ đĩa — đo trên xe giữa hai lượt xi-nhan. */
    @Test fun `prefs_set ghi va read_back hai khoa lat guong`() {
        listOf("camera_mirror_left", "camera_mirror_right").forEach { k ->
            assertTrue(k in TestBridgeWritableKeys.ALL, "$k phải nằm trong danh sách trắng")
            assertTrue(k in CameraSettingsIa.USER_KEYS, "$k là khoá NGƯỜI LÁI (có ô tích đảo lại được)")
        }
        assertTrue("\"camera_mirror_left\" -> bool(raw)?.let { Prefs.setCameraMirror(app, left = true, v = it); it.toString() }" in prefsSet)
        assertTrue("\"camera_mirror_right\" -> bool(raw)?.let { Prefs.setCameraMirror(app, left = false, v = it); it.toString() }" in prefsSet)
        val rb = SourceRoots.body(prefsSet, "private fun readBack(")
        assertTrue("\"camera_mirror_left\" -> Prefs.cameraMirror(app, left = true).toString()" in rb, "read_back không được rỗng")
        assertTrue("\"camera_mirror_right\" -> Prefs.cameraMirror(app, left = false).toString()" in rb)
    }

    /** Cài đặt: hai ô tích ở tầng NGƯỜI LÁI qua cầu, ngay sau hai hàng xoay; chữ ở CẢ hai ngôn ngữ, không mồ côi. */
    @Test fun `hai o tich o tang nguoi lai, sau hang xoay, chu VI va EN`() {
        assertTrue("fun ClusterNavBridge.cameraMirrorLeft(): Boolean = Prefs.cameraMirror(app, left = true)" in bridge)
        assertTrue("fun ClusterNavBridge.cameraMirrorRight(): Boolean = Prefs.cameraMirror(app, left = false)" in bridge)
        assertTrue("fun ClusterNavBridge.setCameraMirror(left: Boolean, v: Boolean) = Prefs.setCameraMirror(app, left, v)" in bridge)
        val user = SourceRoots.body(settings, "private fun cameraUser(")
        assertTrue("bridge.setCameraMirror(left = true, v = on)" in user && "bridge.setCameraMirror(left = false, v = on)" in user)
        assertTrue(user.indexOf("bridge.cameraRotRight()") < user.indexOf("bridge.cameraMirrorLeft()"), "đường mới xuống SAU hàng xoay (CLAUDE.md §6)")
        assertTrue(user.indexOf("bridge.cameraMirrorRight()") < user.indexOf("bridge.cameraShape()"), "…và trước hàng hình khung (thứ tự = USER_KEYS)")
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        listOf("kachi_camera_mirror_sub", "kachi_camera_mirror_row_left", "kachi_camera_mirror_row_right", "kachi_camera_mirror_row_sub").forEach { k ->
            assertTrue("\"$k\"" in vi, "thiếu VI $k"); assertTrue("\"$k\"" in en, "thiếu EN $k")
            assertTrue("R.string.$k" in settings, "chữ $k không được dùng ⇒ mồ côi")
        }
    }
}
