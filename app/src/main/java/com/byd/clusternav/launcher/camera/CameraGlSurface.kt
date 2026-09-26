package com.byd.clusternav.launcher.camera

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import android.view.Surface

/**
 * ═══ NGỮ CẢNH `EGL14` + CỬA RA của đường kết xuất GL — một luồng, một ngữ cảnh, không chia sẻ ══════════════════
 *
 * R8-B (2.74) · `docs/diagnostics/offcar-2026-09-26/camera-dewarp-gl.md` §Kiến trúc. Lớp này **chỉ** lo ngữ cảnh:
 * dựng `EGLDisplay`/`EGLConfig`/`EGLContext` (ES **2.0**) + một **EGL window surface** trên `SurfaceTexture` mà
 * `TextureView` cấp, rồi đổi khung bằng `eglSwapBuffers`. Không biết shader, không biết camera — [CameraGlRenderer]
 * mới biết.
 *
 * ## Vì sao `EGL14` tự lái, không `GLSurfaceView`
 * Cùng lựa chọn Electro đã làm ([ĐO] RE `electro-camera-RE-2026-09-26.md` §4.1: `GLSurfaceView`/`TextureView`/
 * `Choreographer`/`setEGLContextClientVersion` = **0 hit** trong `classes.dex` và cả 4 `.so`; tầng EGL của nó là
 * Grafika `EglCore` với `eglCreateContext(..., {12440, 2, 12344})` = `EGL_CONTEXT_CLIENT_VERSION 2`). Ba lý do cụ thể
 * cho Kachi:
 *  1. **Cửa ra phải là `TextureView`** — không đổi được, vì cả cửa sổ 2.73 (bo góc, hình TRÒN, `getBitmap` của
 *     `camera_frame`) dựa vào việc lớp video nằm TRONG cây view. `GLSurfaceView` là một `SurfaceView` ⇒ mất cả ba.
 *  2. **Nhịp vẽ do KHUNG quyết định, không do `Choreographer`** — HAL đẩy 15 fps, `GLSurfaceView` với
 *     `RENDERMODE_CONTINUOUSLY` sẽ vẽ 60 fps (bốn lượt GPU cho một khung mới), còn `RENDERMODE_WHEN_DIRTY` +
 *     `requestRender` thì vẫn đi qua `Choreographer` nên không bỏ khung được (RE §4.3 đòn 1: `busySkip`).
 *  3. **Không chia sẻ ngữ cảnh** (`EGL_NO_CONTEXT`) — không có ai để chia sẻ với, và một ngữ cảnh chia sẻ là một
 *     ràng buộc thứ tự huỷ mà không ai cần ở đây.
 *
 * ## Luồng — mọi hàm của lớp này PHẢI gọi trên ĐÚNG một luồng
 * `EGLContext` là **current theo luồng**. Gọi [swap] từ luồng khác luồng đã [create] thì `eglSwapBuffers` trả
 * `false` với `EGL_BAD_SURFACE` và **không có ngoại lệ nào** — tức khung đứng im mà log sạch. [CameraGlRenderer] giữ
 * lời hứa đó bằng cách chỉ chạm lớp này từ `Handler` của luồng vẽ của nó.
 *
 * ## Hỏng thì trả `false`, không ném
 * Đây là launcher của một chiếc xe đang lăn bánh: một GPU từ chối cấu hình EGL không được phép là một cú crash. Mọi
 * đường hỏng ⇒ [create] trả `false`, [release] dọn phần đã dựng, và chỗ gọi rơi về đường `TextureView` 2.73.
 */
internal class CameraGlSurface {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    /**
     * Ngữ cảnh GL. Tên `eglCtx` **không phải** `context`: bài `LauncherI18nContractTest` quét bề mặt chữ bằng
     * `text\s*=`, và một phép so `context ==` khớp phải nó ⇒ mọi chuỗi trên dòng ấy bị báo oan là chữ trên màn.
     * Đổi tên rẻ hơn một mục loại trừ (mục loại trừ thì rữa; một cái tên thì không).
     */
    private var eglCtx: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    /** `Surface` bọc `SurfaceTexture` của `TextureView` — giữ để [release] nhả đúng thứ mình đã tạo. */
    private var window: Surface? = null

    /** Ngữ cảnh đang sống và đang current hay không — chỉ ĐỌC, cho chỗ gọi kiểm trước khi vẽ. */
    val ready: Boolean get() = eglSurface != EGL14.EGL_NO_SURFACE && eglCtx != EGL14.EGL_NO_CONTEXT

    /**
     * Dựng ngữ cảnh + cửa ra trên [output], rồi `eglMakeCurrent` **trên luồng gọi**.
     *
     * [ĐO] AOSP `android-10.0.0_r47` `opengl/java/android/opengl/EGL14.java:246-266`:
     * `eglCreateWindowSurface(dpy, config, Object win, …)` nhận `Surface` (nhánh `win instanceof Surface` →
     * `_eglCreateWindowSurface`) **và** nhận thẳng `SurfaceTexture` (→ `_eglCreateWindowSurfaceTexture`). Kachi bọc
     * `Surface(output)` chứ không truyền `SurfaceTexture` trần: `Surface` là thứ **nhả được tường minh**
     * (`Surface.release()`), còn `SurfaceTexture` ấy thuộc `TextureView` — nó sẽ bị nền tảng huỷ ở
     * `onSurfaceTextureDestroyed` và ta không được giữ quyền sống của nó.
     *
     * @return `false` nếu bất kỳ bước EGL nào từ chối (chỗ gọi rơi về đường 2.73).
     */
    fun create(output: SurfaceTexture): Boolean {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return fail("eglGetDisplay")
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return fail("eglInitialize")
        val config = chooseConfig() ?: return fail("eglChooseConfig")
        eglCtx = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, CONTEXT_ATTRIBS, 0)
        if (eglCtx == EGL14.EGL_NO_CONTEXT) return fail("eglCreateContext")
        val win = Surface(output)
        window = win
        eglSurface = EGL14.eglCreateWindowSurface(display, config, win, SURFACE_ATTRIBS, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) return fail("eglCreateWindowSurface")
        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglCtx)) return fail("eglMakeCurrent")
        probe()
        return true
    }

    /**
     * Đẩy khung vừa vẽ ra cửa sổ. `false` = EGL từ chối (surface đã bị huỷ dưới chân ta) ⇒ chỗ gọi dừng vẽ.
     *
     * KHÔNG ghi nhật ký ở đây: hàm này nằm trong **đường khung hình** (15 lần/giây). Một dòng log mỗi khung là đúng
     * thứ CLOSE-14 đã cắt đi, và bài canh `duong khung hinh khong log` quét chính điều này.
     */
    fun swap(): Boolean = EGL14.eglSwapBuffers(display, eglSurface)

    /**
     * Dỡ ngược thứ tự đã dựng. Gọi trên **cùng luồng** đã [create]; idempotent.
     *
     * Thứ tự có chủ ý và không đổi được: `makeCurrent(NO_SURFACE)` **trước** `destroySurface` (huỷ một surface đang
     * current là hành vi không định nghĩa của EGL), `eglReleaseThread` sau khi huỷ ngữ cảnh (nhả phần trạng thái EGL
     * gắn với luồng — thiếu nó thì luồng vẽ chết mà EGL còn giữ tham chiếu), `Surface.release()` **sau cùng của phần
     * EGL** vì chính `eglSurface` đang trỏ vào nó.
     */
    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (eglCtx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglCtx)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        eglSurface = EGL14.EGL_NO_SURFACE
        eglCtx = EGL14.EGL_NO_CONTEXT
        display = EGL14.EGL_NO_DISPLAY
        runCatching { window?.release() }
        window = null
    }

    /** Cấu hình EGL đầu tiên GPU nhận, hoặc `null`. */
    private fun chooseConfig(): EGLConfig? {
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, CONFIG_ATTRIBS, 0, configs, 0, 1, count, 0)) return null
        if (count[0] < 1) return null
        return configs[0]
    }

    /**
     * Đọc **một lần** hai con số mà RE §7 **Q13** đòi trước khi bật tầng này trên xe, rồi ghi vào [CameraGlInfo].
     *
     * `GL_MAX_TEXTURE_SIZE` là câu hỏi sống-chết của cả phương án: texture nguồn rộng **5120** (ảnh 4-in-1). GPU nào
     * trả `< 5120` thì `updateTexImage` hỏng **im lặng** và khung ra đen — [ĐO] AOSP `SurfaceTexture.java:232-246`
     * nói đúng điều đó: *"The width and height parameters must be no greater than the minimum of
     * GL_MAX_VIEWPORT_DIMS and GL_MAX_TEXTURE_SIZE … An error due to invalid dimensions might not be reported until
     * updateTexImage() is called."* Electro **không kiểm bao giờ** (RE §4.2: `glGetIntegerv`/`glGetString` = 0 hit) —
     * Kachi kiểm, vì Kachi phải chạy trên nhiều đời DiLink chứ không phải một chiếc.
     */
    private fun probe() {
        val v = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, v, 0)
        val dims = IntArray(2)
        GLES20.glGetIntegerv(GLES20.GL_MAX_VIEWPORT_DIMS, dims, 0)
        CameraGlInfo.record(
            maxTexture = v[0],
            maxViewportW = dims[0],
            maxViewportH = dims[1],
            renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty(),
            vendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty(),
        )
    }

    /** Một dòng nói bước nào từ chối + mã `eglGetError`, rồi dọn phần đã dựng. Lượt dựng là 1 lần/xi-nhan. */
    private fun fail(step: String): Boolean {
        Log.w(PanoramaHal.TAG, "GL $step từ chối, eglGetError=0x${Integer.toHexString(EGL14.eglGetError())}")
        release()
        return false
    }

    private companion object {
        /**
         * Cấu hình cửa ra: RGB**A** 8888 + ES2.
         *
         * `EGL_ALPHA_SIZE 8` dù `TextureView` đang `isOpaque = true`: `SurfaceTexture` của `TextureView` là một
         * `BufferQueue` RGBA_8888, và xin một config **không** có alpha trên đó là đường `EGL_BAD_MATCH` trên một số
         * driver. Alpha ta ghi luôn là `1.0` (shader trả `vec4(…, 1.0)` ở nhánh đen và `texture2D` OES trả alpha 1),
         * nên có kênh alpha không làm phát sinh một lượt blend nào.
         *
         * **Không** xin `EGL_DEPTH_SIZE`/`EGL_STENCIL_SIZE`: một quad hai tam giác không cần depth test, và một depth
         * buffer cỡ cửa sổ là bộ nhớ GPU cấp cho việc không ai dùng.
         */
        private val CONFIG_ATTRIBS = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE,
        )

        /** ES **2.0** — đúng mức mà [CameraDewarpShader] viết cho (`samplerExternalOES`, không có `#version 300`). */
        private val CONTEXT_ATTRIBS = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)

        private val SURFACE_ATTRIBS = intArrayOf(EGL14.EGL_NONE)
    }
}

/**
 * ═══ HAI CON SỐ mà buổi xe phải mang về (RE §7 Q13 · Q17) — đọc một lần, đọc được từ mọi bề mặt đo ══════════════
 *
 * `object` của cả tiến trình vì ba bề mặt cần cùng một sự thật và chúng ở ba vòng đời khác nhau: dòng `overlay show`
 * của [CameraOverlayView] (main thread, **trước** khi ngữ cảnh GL tồn tại), lời đáp `camera_frame` (luồng nền của
 * cầu kiểm thử), và một dòng `logcat` riêng lúc dựng ngữ cảnh (luồng vẽ).
 *
 * ⚠ Trước lượt dựng ngữ cảnh đầu tiên thì [summary] trả **[NOT_MEASURED]**, không trả một số 0 — *"chưa biết"* và
 * *"GPU nói 0"* là hai câu trả lời khác nhau, và CLAUDE.md §2 cấm trộn chúng. Chuỗi ấy **ASCII** vì nó đi vào lời
 * đáp JSON của `camera_frame` mà script đọc (cùng luật mã lỗi của cầu kiểm thử).
 */
internal object CameraGlInfo {

    /** Chưa dựng ngữ cảnh GL nào ⇒ chưa đo được gì. ASCII — xem ⚠ ở KDoc lớp. */
    const val NOT_MEASURED = "not-measured"

    @Volatile
    var maxTextureSize: Int = 0
        private set

    @Volatile
    var maxViewport: String = ""
        private set

    @Volatile
    var renderer: String = ""
        private set

    /**
     * `SurfaceTexture.getTransformMatrix` của khung ĐẦU TIÊN, dạng chữ — RE §7 **Q17**.
     *
     * [ĐO] AOSP `SurfaceTexture.java:44-47`: ma trận **có thể đổi mỗi lượt `updateTexImage`**, nên về nguyên tắc một
     * lần đọc không đủ. Nhưng câu hỏi của Q17 là *"camera id 1 của ROM này trả ma trận gì"* — một câu hỏi về **kiểu**
     * của luồng (lật dọc? cắt viền?), không phải về một khung. Ghi khung đầu là đủ trả lời, và ghi mỗi khung là đúng
     * thứ đường khung hình không được phép làm.
     */
    @Volatile
    var texMatrix: String = ""
        private set

    fun record(maxTexture: Int, maxViewportW: Int, maxViewportH: Int, renderer: String, vendor: String) {
        maxTextureSize = maxTexture
        maxViewport = "${maxViewportW}x$maxViewportH"
        this.renderer = if (vendor.isEmpty()) renderer else "$vendor / $renderer"
        Log.i(
            PanoramaHal.TAG,
            "GL ngữ cảnh: GL_MAX_TEXTURE_SIZE=$maxTexture GL_MAX_VIEWPORT_DIMS=$maxViewport" +
                " GL_RENDERER='${this.renderer}' (RE Q13)",
        )
    }

    /** Ghi ma trận khung đầu — chỉ lần đầu; lượt sau là no-op để đường khung hình không cấp phát chuỗi. */
    fun recordTexMatrix(m: FloatArray) {
        if (texMatrix.isNotEmpty()) return
        texMatrix = m.joinToString(",") { "%.3f".format(it) }
        Log.i(PanoramaHal.TAG, "GL uTexMatrix khung đầu = [$texMatrix] (RE Q17)")
    }

    /** Cỡ texture lớn nhất còn an toàn cho một cạnh, hoặc [fallback] khi chưa đo được. */
    fun cap(fallback: Int): Int = if (maxTextureSize > 0) maxTextureSize else fallback

    /** Một chuỗi ngắn cho dòng nhật ký + lời đáp cầu kiểm thử. */
    fun summary(): String =
        if (maxTextureSize <= 0) NOT_MEASURED else "maxTex=$maxTextureSize viewport=$maxViewport rend='$renderer'"
}
