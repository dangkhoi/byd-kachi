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
    // 2.93: lượt đọc phiên (`CameraSessionSpec`) + cửa theo CAMERA (`PrefsCameraPerCam`) + bộ chỉnh *Từng camera*.
    private val spec by lazy { app("launcher/camera/CameraSessionSpec.kt") }
    private val perCam by lazy { app("PrefsCameraPerCam.kt") }
    private val perCamBridge by lazy { app("launcher/ClusterNavBridgeCameraPerCam.kt") }
    private val perCamSettings by lazy { app("launcher/SettingsSectionsCameraPerCam.kt") }
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
        // 2.93: đọc MỘT lần ở lượt đọc phiên, theo CAMERA đang mở; camera gương đi qua ĐÚNG khoá theo bên của 2.76.
        assertTrue("val mirror = s.mirror" in body, "controller dùng giá trị của lượt đọc phiên (một lần mỗi phiên)")
        val read = SourceRoots.body(spec, "fun read(ctx: Context, which: CameraWhich): CameraSessionSpec?")
        assertTrue("mirror = Prefs.cameraMirrorOf(ctx, which)," in read, "đọc theo CAMERA đang mở")
        assertTrue("if (w.side) cameraMirror(ctx, left = w == CameraWhich.LEFT)" in SourceRoots.body(perCam, "fun Prefs.cameraMirrorOf("),
            "camera gương: đúng khoá `camera_mirror_left/right` của 2.76 (cấu hình đã chỉnh trên xe giữ nguyên)")
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
        // 2.92: phép quyết theo kiểu hình nằm ở `:core` CameraViewPlan.gl — `mirror` đi qua nguyên vẹn rồi vào `flipH`
        // ở CẢ ba nhánh (Nắn thẳng · Thẳng rộng · Gương cầu), cơ chế đã có test ở `:core` (srcRect w < 0).
        assertTrue("mirror = mirror," in body, "cameraGlUniforms phải chuyển `mirror` xuống kế hoạch `:core`")
        val plan = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/camera/CameraViewPlan.kt")
        assertEquals(3, Regex("""flipH = mirror,""").findAll(plan).count(), "ba kiểu hình đều lật bằng flipH")
    }

    /** TV: `show(mirror)` → `CameraVideoLayer.create(mirror)` → `applyTransform(…, mirror)` → `matrix(…, mirror)` ở CẢ ba chỗ áp ma trận. */
    @Test fun `duong TV - mirror di tu show qua lop video toi ma tran core`() {
        assertTrue("mirror: Boolean = false" in overlay, "show() mặc định KHÔNG lật = hành vi trước L7")
        assertTrue("mirror = mirror," in SourceRoots.body(overlay, "fun show("), "vào CameraVideoLayer.create")
        // ⚠ [SourceRoots.body] bỏ qua DANH SÁCH THAM SỐ (nó nhảy qua dấu `=` của tham số mặc định), nên một giá trị
        // mặc định chỉ khẳng định được trên văn bản cả tệp; chuỗi này xuất hiện đúng một lần ở đó (`glVideo` nhận
        // `mirror: Boolean` KHÔNG mặc định — nó là đường trong, bắt buộc truyền).
        assertTrue("mirror: Boolean = false" in layer, "create() mặc định không lật = hành vi trước L7")
        // 2.92: thêm `scale` (vừa khung / thu phóng của ma trận TV, `:core` CameraViewPlan.tvScale) — đi CÙNG mirror.
        assertTrue("textureVideo(ctx, crop, rotationDeg, mirror, scale, onSurfaceReady)" in layer)
        assertTrue("glVideo(ctx, gl, crop, rotationDeg, mirror, scale, streamW, streamH, synthOn, synthFile, onSurfaceReady)" in layer,
            "đường RƠI của GL (TextureView 2.73) cũng phải lật — nếu không, GL hỏng là ảnh đổi tay")
        assertEquals(4, Regex("""applyTransform\(this@apply, w2, h2, crop, rotationDeg, mirror, scale\)""").findAll(layer).count(),
            "bốn chỗ áp ma trận (TV available/size-changed + GL rơi available/size-changed) đều mang mirror (+ scale 2.92)")
        assertTrue("CameraOverlayTransform.matrix(vw, vh, crop, rotationDeg, mirror, scale)" in SourceRoots.body(layer, "private fun applyTransform("))
        assertFalse("scaleX" in layer || "scaleX" in overlay, "KHÔNG lật bằng View.scaleX — lật sau xoay khác lật nguồn ở ±90 (CameraMirrorTest)")
    }

    /** `prefs_set` ghi được hai khoá (bool) và `read_back` đọc lại từ đĩa — đo trên xe giữa hai lượt xi-nhan. */
    @Test fun `prefs_set ghi va read_back hai khoa lat guong`() {
        listOf("camera_mirror_left", "camera_mirror_right").forEach { k ->
            assertTrue(k in TestBridgeWritableKeys.ALL, "$k phải nằm trong danh sách trắng")
            // 2.93: ô tích dời vào bộ chỉnh *Từng camera* (một khoá, một hàng) — vẫn là khoá NGƯỜI LÁI có UI.
            assertTrue(k in CameraSettingsIa.PER_CAMERA_KEYS, "$k là khoá NGƯỜI LÁI (có ô tích đảo lại được)")
        }
        assertTrue("\"camera_mirror_left\" -> bool(raw)?.let { Prefs.setCameraMirror(app, left = true, v = it); it.toString() }" in prefsSet)
        assertTrue("\"camera_mirror_right\" -> bool(raw)?.let { Prefs.setCameraMirror(app, left = false, v = it); it.toString() }" in prefsSet)
        val rb = SourceRoots.body(prefsSet, "private fun readBack(")
        assertTrue("\"camera_mirror_left\" -> Prefs.cameraMirror(app, left = true).toString()" in rb, "read_back không được rỗng")
        assertTrue("\"camera_mirror_right\" -> Prefs.cameraMirror(app, left = false).toString()" in rb)
        // 2.93: hai camera GIỮA ghi/đọc qua nhánh THEO LOẠI (`TestBridgePerCam`), tên khoá ở `:core`.
        listOf(CameraWhich.REAR, CameraWhich.FRONT).forEach { w ->
            val k = CameraCamConfig.mirrorKey(w)
            assertTrue(k in TestBridgeWritableKeys.ALL, "$k phải nằm trong danh sách trắng")
            assertTrue(k in CameraSettingsIa.PER_CAMERA_KEYS, "$k là khoá của bộ chỉnh *Từng camera*")
        }
        val perCamSet = app("launcher/testbridge/TestBridgePerCam.kt")
        assertTrue("Field.MIRROR -> TestBridgePrefsSet.bool(v)?.also { Prefs.setCameraMirrorOf(app, w, it) }?.toString()" in perCamSet)
        assertTrue("Field.MIRROR -> Prefs.cameraMirrorOf(app, w).toString()" in perCamSet, "read_back đọc lại từ nơi lưu bền")
    }

    /**
     * Cài đặt (2.93): MỘT ô tích ở bộ chỉnh *Từng camera* qua cầu, ngay sau hàng xoay của cùng camera; chữ ở CẢ hai ngôn ngữ,
     * không mồ côi. Hai ô trái/phải của 2.76 gộp vào ô này (chọn camera ở hàng chip đầu bộ chỉnh) — cùng hai khoá.
     */
    @Test fun `o tich lat guong o bo chinh tung camera, sau hang xoay, chu VI va EN`() {
        assertTrue("fun ClusterNavBridge.cameraMirrorOf(w: CameraWhich): Boolean = Prefs.cameraMirrorOf(app, w)" in perCamBridge)
        val set = SourceRoots.body(perCamBridge, "fun ClusterNavBridge.setCameraMirrorOf(")
        assertTrue("Prefs.setCameraMirrorOf(app, w, v)" in set && "reapplyIf(w)" in set, "ghi xong áp ngay nếu đúng camera ấy đang hiện")
        val ed = SourceRoots.body(perCamSettings, "private fun rebuild(")
        assertTrue("bridge.cameraMirrorOf(w)" in ed && "bridge.setCameraMirrorOf(w, on)" in ed)
        assertTrue(ed.indexOf("bridge.cameraRotationOf(w)") in 0 until ed.indexOf("bridge.cameraMirrorOf(w)"),
            "ô lật xuống SAU hàng xoay (CLAUDE.md §6 — đường mới xuống cuối)")
        // Hai ô theo bên của 2.76 + cửa cầu theo bên đã gỡ (không còn ai gọi — CLAUDE.md §8).
        listOf("cameraMirrorLeft()", "cameraMirrorRight()", "setCameraMirror(left").forEach {
            assertFalse(it in settings, "hàng theo bên `$it` đã dời vào bộ chỉnh *Từng camera*")
            assertFalse("fun ClusterNavBridge.$it" in bridge, "cửa cầu theo bên `$it` đã gỡ")
        }
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        listOf("kachi_camera_mirror_own_title", "kachi_camera_mirror_row_sub").forEach { k ->
            assertTrue("\"$k\"" in vi, "thiếu VI $k"); assertTrue("\"$k\"" in en, "thiếu EN $k")
            assertTrue("R.string.$k" in perCamSettings, "chữ $k không được dùng ⇒ mồ côi")
        }
    }
}
