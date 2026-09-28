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

    /**
     * Ba hàm dựng lớp video (`textureVideo`/`surfaceVideo`/`glVideo`) + `applyTransform` **đã sang tệp riêng** ở 2.74
     * (R8-B): `CameraOverlayView` lo *cửa sổ*, `CameraVideoLayer` lo *cái gì vẽ khung*. Các assert của bài này đi theo
     * đúng vai — không gộp hai tệp thành một chuỗi, vì thế thì một hàm nằm sai tệp cũng vẫn xanh.
     */
    private val layer by lazy { app("launcher/camera/CameraVideoLayer.kt") }
    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }
    private val avm by lazy { app("launcher/camera/AvmCamera.kt") }
    private val settings by lazy { app("launcher/SettingsSectionsCamera.kt") }
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
        listOf(overlay, layer, controller, avm).forEach { src ->
            listOf("5120", "1280", "960", "720").forEach {
                assertTrue(it !in src, "cỡ ảnh $it bị gõ cứng trong `:app` — gợi ý nằm ở `CamView.hintW/hintH`, số thật do HAL đo")
            }
        }
        // 2026-09-28: cỡ nguồn nay do `:core` giải một lần (ảnh ghép ⇒ 5120×960; còn lại ⇒ gợi ý của góc).
        assertTrue(
            "CameraPanoCrop.streamW(view, panoStrip)" in controller &&
                "CameraPanoCrop.streamH(view, panoStrip)" in controller,
            "cỡ nguồn phải lấy từ `:core`, không gõ số trong app",
        )
        // Cả BA chỗ dùng cùng một biến. Trước đây ba chỗ tự đọc `view.hintW` riêng ⇒ sửa một chỗ quên hai chỗ
        // là hình vừa bị kéo bẹp vừa cong lệch (lượt phản biện 2026-09-28).
        assertEquals(
            0, Regex("view\\.hint[WH]").findAll(controller).count(),
            "không chỗ nào được đọc thẳng `view.hintW/hintH` nữa — phải đi qua biến đã giải",
        )
        assertEquals(
            2, Regex("streamW = streamW").findAll(controller).count(),
            "đúng HAI chỗ truyền cỡ nguồn (uniform GL + overlay) và cả hai dùng biến chung",
        )
        assertTrue("avm.previewSize()" in controller, "số THẬT phải đo qua AVMCamera")
    }

    // ══ (B) HAI đường kết xuất — mặc định KHÔNG đổi, và cái mất được nói THẲNG ══════════════════════════════

    /** `TextureView` vẫn là đường mặc định và vẫn crop/xoay bằng ma trận; `SurfaceView` là nhánh phụ. */
    @Test fun `hai duong ket xuat, TextureView van mac dinh`() {
        assertTrue("render: String = CameraSignalPolicy.RENDER_TEXTURE" in overlay, "mặc định của tầng vẽ = đường đang chạy")
        assertTrue("CameraSignalPolicy.rotatesByMatrix(render)" in overlay, "chọn nhánh theo `:core`, không so chuỗi tay")
        assertTrue("setZOrderMediaOverlay(true)" in layer, "SurfaceView phải ghép CÙNG cửa sổ (không setZOrderOnTop)")
        listOf(overlay, layer).forEach {
            assertTrue("setZOrderOnTop" !in it, "setZOrderOnTop bỏ luôn cơ hội được bo góc — xem KDoc lớp")
            assertTrue("\"TV\"" !in it && "\"SV\"" !in it && "\"GL\"" !in it, "mã kết xuất phải lấy từ hằng `:core`")
        }
        assertTrue("CameraOverlayFrame.stretch(" in overlay, "SurfaceView cắt vùng gương bằng cỡ + lề âm (`:core` tính)")
    }

    /** Controller đọc pref kết xuất và báo lên tầng vẽ xoay có THẬT SỰ được áp hay không (CLAUDE.md §2). */
    @Test fun `controller truyen duong ket xuat va ket qua xoay that`() {
        assertTrue("Prefs.cameraRender(appCtx)" in controller, "đường kết xuất đọc từ pref mỗi lượt dựng overlay")
        assertTrue("render = render," in controller, "mã phải đi vào overlay.show(render = …)")
        assertTrue("avm.setDisplayOrientation(surface, rot)" in controller,
            "SurfaceView không có setTransform ⇒ phải THỬ đường HAL, không im lặng bỏ góc owner đã chọn")
        assertTrue("rotationEffective = CameraSignalPolicy.rotationEffective(render, rot, byHal)" in controller,
            "cửa sổ chỉ lấy tỉ lệ ĐÃ XOAY khi có ai thật sự xoay — nhận ≠ có tác dụng; phép hợp ba nhánh ở `:core`")
        assertTrue("kết xuất=\$render rot=\$rot lật=\$mirror\")" in controller, "log một dòng phải nói cả đường kết xuất và góc")
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

    /**
     * 2.77 — hàng chip *Kết xuất camera* **không còn**; ba đường vẽ vẫn còn ở `:core` và pref vẫn ghi được.
     *
     * Owner trên xe 27/09 bỏ cả khối *Nâng cao (kỹ thuật)*; mặc định đường vẽ nay theo hồ sơ xe (Seal = `GL`,
     * [ĐO CAM-B6 27/09]: khung giật 11,15 % → 0,81 % với trần 15 fps ⇒ GL là lựa chọn đã chốt, không còn gì để dò
     * bằng một chip). Bài này canh **sự vắng mặt của hàng** + **sự CÒN LẠI của đường prefs_set**.
     */
    @Test fun `hang chip ket xuat da go, ba duong van con o core va van ghi duoc`() {
        assertEquals(3, CameraSignalPolicy.RENDERS.size, "ba đường vẽ vẫn ở `:core` — chỉ hàng chip bị gỡ")
        listOf("bridge.cameraRender()", "bridge.setCameraRender(v)", "R.string.kachi_camera_render_row",
            "R.string.kachi_camera_render_sub", "RENDER_TEXTURE", "RENDER_SURFACE", "RENDER_GL").forEach {
            assertTrue(it !in settings, "`$it` đã gỡ khỏi Cài đặt ở 2.77")
        }
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        listOf("kachi_camera_render_sub", "kachi_camera_render_row", "kachi_camera_render_texture",
            "kachi_camera_render_surface", "kachi_camera_render_gl").forEach {
            assertTrue("\"$it\"" !in vi && "\"$it\"" !in en, "chữ $it mồ côi — hàng của nó đã gỡ")
        }
        // Đường chẩn đoán KHÔNG được mất cùng UI (CLAUDE.md §15 bước 2/3).
        assertTrue("camera_render" in TestBridgeCommands.WRITABLE_PREFS_KEYS, "`camera_render` phải còn ghi được qua prefs_set")
    }

    /** Pref `camera_render`: đọc lạ ⇒ mặc định `:core`; đảo được qua `prefs_set` (đo hai đường trên xe đang chạy). */
    @Test fun `pref camera_render mac dinh core va vao danh sach trang`() {
        val body = SourceRoots.body(prefs, "fun Prefs.cameraRender(")
        // 2.76 · R2: mặc định theo HỒ SƠ XE (Seal GL / chưa đo TV = `CameraSignalPolicy.defaultRender()` ở NEUTRAL).
        assertTrue("CameraDefaults.of(ctx).render" in body, "mặc định lấy từ hồ sơ xe, không chép chuỗi")
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
        // BA callback `onSurfaceTextureUpdated` (TV · GL) và `surfaceChanged` (SV) — tất cả phải TRỐNG.
        assertEquals(
            2,
            Regex("""override fun onSurfaceTextureUpdated\([^)]*\)\s*\{\s*\}""").findAll(layer).count(),
            "cả hai đường TextureView (TV và GL) phải có onSurfaceTextureUpdated TRỐNG — mỗi khung đi qua đó",
        )
        assertTrue(
            Regex("""override fun surfaceChanged\([^)]*\)\s*\{\s*\}""").containsMatchIn(layer),
            "surfaceChanged của SurfaceView cũng không được làm gì mỗi lượt",
        )
        assertTrue("isOpaque = true" in layer, "TextureView đục ⇒ khỏi blend alpha của chính nó (nền bo góc ở view CHA)")
        // Không shell / không dumpsys trong tầng vẽ overlay: đường khung hình tuyệt đối không được chạm shell.
        listOf("Runtime.getRuntime", "dumpsys", "ProcessBuilder", "KachiShell").forEach {
            listOf(overlay, layer).forEach { src -> assertTrue(it !in src, "$it không được có mặt trong tầng vẽ overlay camera") }
        }
        assertEquals(
            1,
            Regex("""android\.graphics\.Matrix\(\)""").findAll(layer).count(),
            "chỉ MỘT chỗ dựng Matrix (trong applyTransform, chạy ở callback đổi cỡ) — không phải mỗi khung",
        )
        assertTrue(
            "android.graphics.Matrix" !in overlay,
            "tầng cửa sổ không còn dựng ma trận nào (đã sang CameraVideoLayer) — `rotatesByMatrix` thì vẫn được gọi",
        )
    }
}
