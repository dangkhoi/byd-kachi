package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.testbridge.TestBridgeCommands
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ CAM-ROT-2 (khung đúng tỉ lệ) + CLOSE-14 (đường kết xuất) — bài canh DÂY NỐI của `:app` ═══════════════════
 *
 * Toán đã test bằng số ở `:core` (`CameraOverlayFrameTest`, `CameraSignalPolicyTest`). Bài này canh **đường dây**:
 * `WindowManager`, `TextureView`, `SurfaceView`, `SharedPreferences` đều không chạy off-car, nên mỗi mắt xích dưới
 * đây là thứ gỡ đi thì **build vẫn xanh và không bài `:core` nào đỏ** (CLAUDE.md §8) — đúng cái bẫy mà
 * `CastShell.evictVd` đã trả giá: viết xong, compile sạch, chưa từng được gọi.
 */
class CameraFrameAndRenderWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val overlay by lazy { app("launcher/camera/CameraOverlayView.kt") }
    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }
    private val avm by lazy { app("launcher/camera/AvmCamera.kt") }
    private val settings by lazy { app("launcher/SettingsSectionsCar.kt") }
    private val prefs by lazy { app("PrefsAutomation.kt") }
    private val prefsSet by lazy { app("launcher/testbridge/TestBridgePrefsSet.kt") }

    // ══ (A) CỬA SỔ lấy cỡ từ `:core`, căn giữa vùng, và dựng lại khi đo được cỡ nguồn ═══════════════════════

    /**
     * Cỡ cửa sổ **chỉ** đến từ [CameraOverlayFrame.fit]; `:app` không được tự tính tỉ lệ (một bản sao thứ hai của
     * công thức sẽ lệch đúng vào lần ai đó đổi crop). Và cửa sổ phải căn GIỮA vùng cho phép — nếu không, khung nhỏ
     * hơn ô vuông cũ sẽ dính mép và owner thấy overlay "nhảy chỗ" so với 2.72.
     */
    @Test fun `cua so lay co tu core va can giua vung`() {
        assertTrue("CameraOverlayFrame.fit(" in overlay, "cỡ cửa sổ phải do `:core` tính (có test bằng số)")
        val lp = SourceRoots.body(overlay, "private fun layoutParams(")
        assertTrue("f.w, f.h," in lp, "WindowManager.LayoutParams phải nhận ĐÚNG cỡ khung đã tính")
        assertTrue("box.x0 + ((box.areaW - f.w) / 2)" in lp, "x = lề + (vùng − khung)/2 ⇒ căn giữa vùng")
        assertTrue("box.y0 + ((box.areaH - f.h) / 2)" in lp, "y = lề trên + (vùng − khung)/2")
        // Vùng cho phép vẫn là ô vuông cũ (trần) — không được nới thêm chỗ khi đổi tỉ lệ.
        assertTrue("SQUARE_RATIO = 0.50f" in overlay, "trần vùng giữ nguyên 50% chiều cao màn (2.72)")
    }

    /** Đo được cỡ nguồn ⇒ **dựng lại** cửa sổ: thiếu `updateViewLayout` thì khung đúng tỉ lệ chỉ đúng trên giấy. */
    @Test fun `onStreamMeasured dung lai cua so`() {
        val body = SourceRoots.body(overlay, "fun onStreamMeasured(")
        assertTrue("updateViewLayout(" in body, "phải đổi cỡ cửa sổ THẬT, không chỉ ghi lại con số")
        assertTrue("if (sw == st.streamW && sh == st.streamH && rotationEffective == st.rotationEffective) return" in body,
            "không có gì đổi ⇒ không dựng lại (mỗi lượt updateViewLayout là một lượt vẽ lại cửa sổ)")
        assertTrue("if (streamW > 0) streamW else st.streamW" in body,
            "HAL trả 0 (ROM không hỗ trợ) ⇒ GIỮ gợi ý đang dùng, không về 0")
        assertTrue("c.post {" in body, "gọi từ luồng khác ⇒ về main trước khi chạm WindowManager")
    }

    /** Cỡ ảnh nguồn phải ĐO, không hardcode trong `:app` (xe khác ghép 4-in-1 cỡ khác — CLAUDE.md §7). */
    @Test fun `co anh nguon khong hardcode trong app`() {
        listOf(overlay, controller, avm).forEach { src ->
            listOf("5120", "1280", "960", "720").forEach {
                assertTrue(it !in src, "cỡ ảnh $it bị gõ cứng trong `:app` — gợi ý nằm ở `CamView.hintW/hintH`, số thật do HAL đo")
            }
        }
        assertTrue("view.hintW" in controller && "view.hintH" in controller, "gợi ý phải lấy từ `:core` CamView")
        assertTrue("avm.previewSize()" in controller, "số THẬT phải đo qua AVMCamera")
    }

    // ══ (B) HAI đường kết xuất — mặc định KHÔNG đổi, và cái mất được nói THẲNG ══════════════════════════════

    /** `TextureView` vẫn là đường mặc định và vẫn crop/xoay bằng ma trận; `SurfaceView` là nhánh phụ. */
    @Test fun `hai duong ket xuat, TextureView van mac dinh`() {
        assertTrue("render: String = CameraSignalPolicy.RENDER_TEXTURE" in overlay, "mặc định của tầng vẽ = đường đang chạy")
        assertTrue("CameraSignalPolicy.rotatesByMatrix(render)" in overlay, "chọn nhánh theo `:core`, không so chuỗi tay")
        assertTrue("setZOrderMediaOverlay(true)" in overlay, "SurfaceView phải ghép CÙNG cửa sổ (không setZOrderOnTop)")
        assertTrue("setZOrderOnTop" !in overlay, "setZOrderOnTop bỏ luôn cơ hội được bo góc — xem KDoc lớp")
        assertTrue("CameraOverlayFrame.stretch(" in overlay, "SurfaceView cắt vùng gương bằng cỡ + lề âm (`:core` tính)")
        assertTrue("\"TV\"" !in overlay && "\"SV\"" !in overlay, "mã kết xuất phải lấy từ hằng `:core`")
    }

    /** Controller đọc pref kết xuất và báo lên tầng vẽ xoay có THẬT SỰ được áp hay không (CLAUDE.md §2). */
    @Test fun `controller truyen duong ket xuat va ket qua xoay that`() {
        assertTrue("Prefs.cameraRender(appCtx)" in controller, "đường kết xuất đọc từ pref mỗi lượt dựng overlay")
        assertTrue("render = render," in controller, "mã phải đi vào overlay.show(render = …)")
        assertTrue("avm.setDisplayOrientation(surface, rot)" in controller,
            "SurfaceView không có setTransform ⇒ phải THỬ đường HAL, không im lặng bỏ góc owner đã chọn")
        assertTrue("rotationEffective = rot == 0 || byMatrix || byHal" in controller,
            "cửa sổ chỉ lấy tỉ lệ ĐÃ XOAY khi có ai thật sự xoay — nhận ≠ có tác dụng")
        assertTrue("kết xuất=\$render rot=\$rot\")" in controller, "log một dòng phải nói cả đường kết xuất và góc")
    }

    /** Hai hàm reflection mới dùng đúng TÊN đã RE được trong lớp framework — gõ sai là no-op im lặng trên xe. */
    @Test fun `AvmCamera goi dung ten ham cua framework`() {
        listOf("getPreviewWidth", "getPreviewHeight", "setDisplayOrientation").forEach {
            assertTrue("\"$it\"" in avm, "thiếu lời gọi $it (RE: DiLinkAVMCamera bọc thẳng hàm này)")
        }
        val size = SourceRoots.body(avm, "fun previewSize(")
        assertTrue("if (w <= 0 || h <= 0)" in size, "HAL trả 0 ⇒ coi như CHƯA BIẾT, không trả cỡ 0 cho tầng vẽ")
    }

    // ══ (C) Cài đặt · prefs · cầu kiểm thử ═════════════════════════════════════════════════════════════════

    /** Một hàng chip, ĐÚNG hai mã = hằng `:core`, và nhãn của `SurfaceView` nói rõ giới hạn xoay. */
    @Test fun `cai dat co hang chip ket xuat lay ma tu core`() {
        val body = SourceRoots.body(settings, "private fun cameraSignal(")
        listOf("RENDER_TEXTURE", "RENDER_SURFACE").forEach {
            assertTrue("CameraSignalPolicy.$it to " in body, "chip $it phải lấy mã từ hằng `:core`")
        }
        assertEquals(2, CameraSignalPolicy.RENDERS.size, "mỗi đường `:core` phải có ĐÚNG một chip")
        assertTrue("bridge.cameraRender()" in body && "bridge.setCameraRender(v)" in body, "hàng chip nối qua cầu, không ghi Prefs thẳng")
        assertTrue("R.string.kachi_camera_render_sub" in body && "R.string.kachi_camera_render_row" in body)
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        listOf(vi, en).forEach {
            assertTrue("kachi_camera_render_surface" in it, "chip SurfaceView phải có chữ ở CẢ hai ngôn ngữ")
        }
        assertTrue("HAL" in vi.substringAfter("kachi_camera_render_surface").take(120),
            "nhãn SurfaceView phải nói THẲNG là xoay đi qua HAL (có thể không xoay) — không im lặng bỏ góc")
    }

    /** Pref `camera_render`: đọc lạ ⇒ mặc định `:core`; đảo được qua `prefs_set` (đo hai đường trên xe đang chạy). */
    @Test fun `pref camera_render mac dinh core va vao danh sach trang`() {
        val body = SourceRoots.body(prefs, "fun Prefs.cameraRender(")
        assertTrue("CameraSignalPolicy.defaultRender()" in body, "mặc định lấy từ `:core`, không chép chuỗi")
        assertTrue("CameraSignalPolicy.isRender(raw)" in body, "giá trị lạ trên đĩa ⇒ rơi về mặc định")
        assertTrue("\"camera_render\"" in prefs)
        assertTrue("camera_render" in TestBridgeCommands.WRITABLE_PREFS_KEYS, "đo hai đường trên xe cần prefs_set camera_render")
        assertTrue("\"camera_render\" ->" in prefsSet, "prefs_set phải có nhánh ghi")
        assertTrue("CameraSignalPolicy.isRender(it)" in prefsSet, "prefs_set chỉ nhận mã hợp lệ")
        assertTrue("\"camera_render\" -> Prefs.cameraRender(app)" in prefsSet, "read_back phải đọc lại từ nơi lưu bền")
    }

    // ══ (D) ĐƯỜNG KHUNG HÌNH — không việc nặng mỗi khung (CLOSE-14) ═════════════════════════════════════════

    /**
     * 15 khung/giây đi qua `onSurfaceTextureUpdated`: nó phải TRỐNG (không log, không cấp phát, không shell), và
     * ma trận chỉ dựng ở hai callback đổi cỡ.
     */
    @Test fun `duong khung hinh khong log khong cap phat khong shell`() {
        assertTrue(
            Regex("""override fun onSurfaceTextureUpdated\([^)]*\)\s*\{\s*\}""").containsMatchIn(overlay),
            "onSurfaceTextureUpdated phải TRỐNG — mỗi khung đi qua đó",
        )
        assertTrue(
            Regex("""override fun surfaceChanged\([^)]*\)\s*\{\s*\}""").containsMatchIn(overlay),
            "surfaceChanged của SurfaceView cũng không được làm gì mỗi lượt",
        )
        assertTrue("isOpaque = true" in overlay, "TextureView đục ⇒ khỏi blend alpha của chính nó (nền bo góc ở view CHA)")
        // Không shell / không dumpsys trong tầng vẽ overlay: đường khung hình tuyệt đối không được chạm shell.
        listOf("Runtime.getRuntime", "dumpsys", "ProcessBuilder", "KachiShell").forEach {
            assertTrue(it !in overlay, "$it không được có mặt trong tầng vẽ overlay camera")
        }
        assertEquals(
            1,
            Regex("""android\.graphics\.Matrix\(\)""").findAll(overlay).count(),
            "chỉ MỘT chỗ dựng Matrix (trong applyTransform, chạy ở callback đổi cỡ) — không phải mỗi khung",
        )
    }
}
