package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.testbridge.TestBridgeCommands
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R7 · XOAY VIDEO CAMERA — bài canh DÂY NỐI của `:app` (spec `camera-turn-signal-hal-socket.html`) ═══════════
 *
 * Owner 2026-09-26: *"cái xinhan bật cam mình cắt video ok, nhưng nó bị ngang, cần dọc video lại… bên trái là
 * rotation 90 độ xoay qua trái, bên phải thì rotation 90 độ xoay sang phải, nếu đc thì thêm option rotation trong
 * setting"*. Luật (bảng độ) đã test THUẦN ở `:core` `CameraSignalPolicyTest`; bài này chỉ canh **đường dây** ba tầng
 * `:core` → controller → overlay → Cài đặt, vì `Matrix`/`TextureView`/`WindowManager` không chạy off-car.
 *
 * Mỗi assert là một mắt xích mà gỡ đi thì build vẫn xanh (CLAUDE.md §8): `postRotate` mất ⇒ video vẫn ngang nhưng
 * không test nào của `:core` đỏ, vì `:core` không biết ma trận.
 */
class CameraRotationWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val overlay by lazy { app("launcher/camera/CameraOverlayView.kt") }
    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }
    private val settings by lazy { app("launcher/SettingsSectionsCar.kt") }
    private val prefs by lazy { app("PrefsAutomation.kt") }
    private val prefsSet by lazy { app("launcher/testbridge/TestBridgePrefsSet.kt") }

    /**
     * (a) Overlay: KHÔNG tự nhân ma trận nữa — lấy 9 số từ `:core` [CameraOverlayTransform] rồi `setValues` +
     * `setTransform`, và áp ở CẢ HAI callback của TextureView.
     *
     * Review Pass 1 (2026-09-26) dời phép toán sang `:core` vì `android.graphics.Matrix` là stub trên JVM ⇒ công
     * thức crop/xoay trước đó **không có bài test nào**; nay `CameraOverlayTransformTest` kiểm bằng số. Bài này chỉ
     * còn canh đúng chỗ nối.
     */
    @Test
    fun `overlay lay ma tran tu core va ap o ca hai callback`() {
        val body = SourceRoots.body(overlay, "private fun applyTransform(")
        assertTrue(
            "CameraOverlayTransform.matrix(vw, vh, crop, rotationDeg)" in body,
            "ma trận phải do `:core` dựng (một nguồn sự thật, có test bằng số) — không nhân tay trong `:app`",
        )
        assertTrue("?: return" in body, "`null` từ `:core` ⇒ KHÔNG đụng setTransform (y hành vi trước R7)")
        assertTrue("setValues(values)" in body && "tv.setTransform(" in body, "9 số phải vào Matrix rồi vào TextureView")
        // Cả hai callback: lượt đầu (Available) và lượt đổi cỡ (SizeChanged) — thiếu một là xoay mất khi view đổi cỡ.
        assertEquals(
            2,
            Regex("""override fun onSurfaceTexture(?:Available|SizeChanged)\([^)]*\)\s*\{?\s*applyTransform\(this@apply, w2, h2, crop, rotationDeg\)""")
                .findAll(overlay).count(),
            "applyTransform(…, rotationDeg) phải gọi ở CẢ onSurfaceTextureAvailable và onSurfaceTextureSizeChanged",
        )
        assertTrue("rotationDeg: Int = 0" in overlay, "show() nhận rotationDeg, mặc định 0 = hành vi trước R7")
    }

    /**
     * (a2) Bố cục 9 phần tử mà `:core` dựng phải đúng quy ước `android.graphics.Matrix` — **đọc thẳng hằng của SDK**.
     *
     * `:core` là Kotlin thuần nên không thể tự khẳng định điều này; nếu quy ước row-major của `setValues` khác đi thì
     * ma trận đúng-về-toán vẫn vẽ sai trên xe, và không bài nào bắt được. Đây là chỗ duy nhất kẹp hai bên lại.
     */
    @Test
    fun `bo cuc 9 phan tu khop hang Matrix cua SDK`() {
        assertEquals(0, android.graphics.Matrix.MSCALE_X, "m[0] phải là MSCALE_X")
        assertEquals(1, android.graphics.Matrix.MSKEW_X, "m[1] phải là MSKEW_X")
        assertEquals(2, android.graphics.Matrix.MTRANS_X, "m[2] phải là MTRANS_X (dịch NGANG)")
        assertEquals(3, android.graphics.Matrix.MSKEW_Y)
        assertEquals(4, android.graphics.Matrix.MSCALE_Y)
        assertEquals(5, android.graphics.Matrix.MTRANS_Y, "m[5] phải là MTRANS_Y (dịch DỌC)")
        assertEquals(8, android.graphics.Matrix.MPERSP_2)
        // Và `:core` thật sự dựng theo bố cục đó: crop-only 360×360 ⇒ [10,0,-900, 0,1,0, 0,0,1].
        val m = requireNotNull(CameraOverlayTransform.matrix(360, 360, floatArrayOf(0.25f, 0f, 0.35f, 1f), 0))
        assertEquals(10f, m[android.graphics.Matrix.MSCALE_X], 1e-3f)
        assertEquals(-900f, m[android.graphics.Matrix.MTRANS_X], 1e-2f)
        assertEquals(0f, m[android.graphics.Matrix.MTRANS_Y], 1e-3f)
    }

    /** (b) Controller: số độ đến từ `:core` (`rotationDegrees(Prefs.cameraRotation(…, left), left)`) và đi vào `overlay.show`. */
    @Test
    fun `controller truyen rotationDegrees theo BEN tu pref vao overlay`() {
        assertTrue(
            "CameraSignalPolicy.rotationDegrees(Prefs.cameraRotation(appCtx, left = turn == Turn.LEFT), left = turn == Turn.LEFT)" in controller,
            "controller phải đọc pref của ĐÚNG BÊN xi-nhan (2.71: camera_rot_left/right) rồi tính độ ở `:core` cho bên đó",
        )
        assertTrue("Prefs.cameraRotation(appCtx)" !in controller, "khoá đơn cũ (một chế độ cho cả hai bên) không còn được đọc")
        assertTrue("rotationDeg = rot," in controller, "số độ phải đi vào overlay.show(rotationDeg = …)")
        assertTrue("rot=\$rot\")" in controller, "log 1 dòng của controller phải có rot=")
    }

    /**
     * (c) Cài đặt: HAI hàng chip (trái / phải — owner trên xe 2026-09-26: *"2 line setting độc lập cho camera trái và
     * phải"*), mỗi hàng ĐÚNG 4 mã = hằng `:core` (không chép chuỗi), nối bridge getter riêng bên + setter có `left =`.
     */
    @Test
    fun `cai dat co HAI hang chip, moi hang 4 ma dung hang core`() {
        val body = SourceRoots.body(settings, "private fun cameraSignal(")
        val codes = listOf("ROTATE_NONE", "ROTATE_LEFT", "ROTATE_RIGHT", "ROTATE_180")
        codes.forEach { assertTrue("CameraSignalPolicy.$it to " in body, "chip $it phải lấy mã từ hằng `:core`") }
        assertEquals(codes.size, CameraSignalPolicy.ROTATIONS.size, "mỗi góc `:core` phải có ĐÚNG một chip trong danh sách dùng chung")
        // Hai mã CŨ không còn là chip: chúng chỉ sống trong migrate.
        listOf("ROTATE_BY_SIDE to", "ROTATE_BY_SIDE_INV to").forEach { assertTrue(it !in body, "$it là mã cũ, không được thành chip") }
        // Không chép chuỗi: mã "SIDE"/"L90"/"R90"/"180" không được xuất hiện trần trong tệp Cài đặt.
        listOf("\"SIDE\"", "\"SIDEINV\"", "\"L90\"", "\"R90\"", "\"180\"").forEach {
            assertTrue(it !in settings, "mã $it bị chép trần vào Cài đặt — dùng hằng CameraSignalPolicy")
        }
        // Hai hàng, hai getter riêng bên, một setter có tham số bên — y khuôn camera_pos_left/right ngay trên.
        assertTrue("R.string.kachi_camera_rot_row_left), rotations, bridge.cameraRotLeft()" in body, "hàng TRÁI: tiêu đề + 4 chip + getter trái")
        assertTrue("R.string.kachi_camera_rot_row_right), rotations, bridge.cameraRotRight()" in body, "hàng PHẢI: tiêu đề + 4 chip + getter phải")
        assertTrue("bridge.setCameraRotation(left = true, v = v)" in body && "bridge.setCameraRotation(left = false, v = v)" in body, "setter phải nói rõ bên")
        assertTrue("R.string.kachi_camera_rot_sub" in body)
        assertTrue("kachi_camera_rot_title" !in settings && "kachi_camera_rot_side" !in settings, "tài nguyên của hàng đơn cũ phải gỡ (i18n mồ côi)")
    }

    /**
     * Prefs: hai khoá `camera_rot_left`/`camera_rot_right`; đọc lạ ⇒ mặc định `:core` của BÊN; khoá đơn cũ
     * `camera_rotation` được migrate MỘT lần (qua `CameraSignalPolicy.migrateRotation`, đã test 6×2 ở `:core`) rồi xoá;
     * cả hai khoá mới đảo được qua prefs_set, khoá cũ thì không.
     */
    @Test
    fun `pref camera_rot theo ben, migrate khoa don cu, vao danh sach trang`() {
        val body = SourceRoots.body(prefs, "fun Prefs.cameraRotation(")
        assertTrue("CameraSignalPolicy.defaultRotation(left)" in body, "mặc định phải lấy từ `:core` THEO BÊN, không chép chuỗi")
        assertTrue("CameraSignalPolicy.isRotation(raw)" in body, "giá trị lạ trên đĩa phải rơi về mặc định")
        assertTrue("migrateLegacyCameraRotation(p)" in body, "lượt đọc phải chạy migrate trước — nếu không, xe nâng cấp mất lựa chọn đã chốt")
        val mig = SourceRoots.body(prefs, "private fun migrateLegacyCameraRotation(")
        assertTrue("CameraSignalPolicy.migrateRotation(old, side)" in mig, "bảng đổi mã cũ→mới nằm ở `:core`, không viết lại trong :app")
        assertTrue("if (!p.contains(cameraRotKey(side)))" in mig, "bên đã có khoá mới thì KHÔNG ghi đè — lựa chọn owner thắng dữ liệu di cư")
        assertTrue(".remove(K_CAMERA_ROTATION_LEGACY)" in mig, "khoá cũ phải xoá sau migrate — còn đó là còn migrate lại đè lên lựa chọn mới")
        assertTrue("\"camera_rot_left\"" in prefs && "\"camera_rot_right\"" in prefs && "\"camera_rotation\"" in prefs)
        listOf("camera_rot_left", "camera_rot_right").forEach {
            assertTrue(it in TestBridgeCommands.WRITABLE_PREFS_KEYS, "chốt chiều xoay trên xe cần prefs_set $it")
            assertTrue("\"$it\" ->" in prefsSet, "prefs_set phải có nhánh $it (ghi + read_back)")
        }
        assertEquals(2, Regex("""\"camera_rot_(?:left|right)\" -> Prefs\.cameraRotation\(app, left = (?:true|false)\)""").findAll(prefsSet).count(), "read_back cho cả hai bên")
        assertTrue("camera_rotation" !in TestBridgeCommands.WRITABLE_PREFS_KEYS && "\"camera_rotation\" ->" !in prefsSet, "khoá đơn cũ không nhận ghi nữa")
        assertTrue("CameraSignalPolicy.isRotation(it)" in prefsSet, "prefs_set chỉ nhận mã hợp lệ")
    }
}
