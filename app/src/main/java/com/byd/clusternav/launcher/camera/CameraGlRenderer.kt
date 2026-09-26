package com.byd.clusternav.launcher.camera

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ LUỒNG VẼ của đường kết xuất GL — `AVMCamera → OES → `[CameraDewarpShader]` → cửa sổ` ══════════════════════
 *
 * R8-B (2.74) · `docs/diagnostics/offcar-2026-09-26/camera-dewarp-gl.md`. Một thực thể = **một lượt overlay**: dựng
 * ở lúc `TextureView` có `SurfaceTexture`, dỡ ở lúc overlay đóng. Không dùng lại, không chia sẻ.
 *
 * ## Ai làm gì, trên luồng nào (đọc kỹ — sai luồng ở đây là khung đứng im mà log sạch)
 * | Luồng | Việc |
 * |---|---|
 * | **main** | [start]/[stop] gọi từ đây (callback của `TextureView`); [start] trả về rồi mới giao `Surface` cho HAL, nên `AVMCamera.open` vẫn nằm trên main **y như đường 2.73** |
 * | **`kachi-camgl`** (luồng này) | TẤT CẢ lời gọi EGL/GL: dựng ngữ cảnh, biên dịch program, `updateTexImage`, vẽ, `eglSwapBuffers`, dỡ |
 * | **luồng gọi `onFrameAvailable`** | chỉ **đặt cờ + post** — nhưng thực ra nó CŨNG là `kachi-camgl` vì ta truyền `Handler` của luồng này cho `setOnFrameAvailableListener` (xem dưới) |
 *
 * `EGLContext` là *current theo luồng*: một lời gọi GL từ luồng khác **không ném gì cả**, nó chỉ không làm gì. Đó là
 * lý do mọi thứ đi qua đúng một [Handler].
 *
 * [ĐO] AOSP `android-10.0.0_r47` `graphics/java/android/graphics/SurfaceTexture.java:186-189`:
 * *"If no handler is specified, then the callback may be called on an arbitrary thread, so it is not safe to call
 * updateTexImage without first binding the OpenGL ES context to the thread invoking the callback."* ⇒ Kachi **luôn**
 * truyền `Handler` của luồng vẽ, không dùng bản một-tham-số.
 *
 * ## Ba đòn chống giật (RE `electro-camera-RE-2026-09-26.md` §4.3, đòn 1 và 3)
 *  1. **Vẽ chỉ khi có khung mới** — không `Choreographer`, không vòng lặp. Không xi-nhan ⇒ 0 % GPU.
 *  2. **Bỏ khung khi lượt vẽ trước chưa xong** ([pending] + [busySkip]) — Electro gọi nó là `busySkip` (@0x4878a),
 *     kinex bỏ khung nếu chưa đủ 16 ms (`p005b1/RunnableC0171e.java:104`). Kachi tới 2.73 **không có** cơ chế nào
 *     tương đương (RE §5 K8), và đây là chỗ đầu tiên nó có.
 *  3. **Luồng riêng, có tên** — không bao giờ tranh luồng UI của launcher. Đúng thứ K8/K15 đòi.
 *
 * ## Đường khung hình KHÔNG có việc nặng (CLOSE-14, bài canh `duong khung hinh GL khong cap phat`)
 * [drawFrame] không cấp phát **một byte nào**: quad và ma trận là trường dựng sẵn, vị trí uniform lấy lúc liên kết
 * program, không `String.format`, không `dumpsys`, không shell. Đúng hai chỗ có chữ trong cả đường ấy và cả hai đều
 * có cổng đếm: ma trận khung ĐẦU TIÊN (RE §7 Q17, một lần cho cả phiên) và [busySkip] mỗi [LOG_EVERY] khung ở mức
 * **DEBUG** (≈ 1 dòng/7 giây ở 15 fps — con số owner cần để biết GPU có kịp không).
 */
internal class CameraGlRenderer(
    /** Bộ uniform của lượt overlay này — bất biến; đổi tham số ⇒ lượt xi-nhan sau dựng lại (như mọi pref camera). */
    private val uniforms: CameraGlUniforms,
    /** Cỡ ảnh nguồn gợi ý/đo được — nuôi `setDefaultBufferSize`. `<= 0` ⇒ không gọi (xem [createInput]). */
    private val streamW: Int,
    private val streamH: Int,
) {

    private val thread = HandlerThread(THREAD)
    private var handler: Handler? = null
    private val egl = CameraGlSurface()

    private var program = 0
    private var oesTex = 0
    private var aPosition = -1
    private var aTexCoord = -1
    private var uTex = -1
    private var uTexMatrix = -1
    private var uSrcRect = -1
    private var uRotation = -1
    private var uAmount = -1
    private var uFocal = -1
    private var uK = -1
    private var uScale = -1
    private var uAspect = -1
    private var uCenter = -1

    private var input: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    /**
     * Cỡ cửa sổ — ghi từ **main** ([resize]), đọc từ **luồng vẽ** ([drawFrame]) ⇒ `@Volatile` ([SOÁT Opus
     * 2026-09-27]). Thiếu nó thì một lượt đổi cỡ có thể không bao giờ tới luồng vẽ và `glViewport` giữ cỡ cũ: ảnh
     * co/kéo mà không dòng log nào nói gì.
     */
    @Volatile
    private var viewW = 0

    @Volatile
    private var viewH = 0

    /** Ma trận `getTransformMatrix` — dựng MỘT lần, ghi đè mỗi khung (không cấp phát trong đường khung hình). */
    private val texMatrix = FloatArray(16)

    /** Số khung đã vẽ; cũng là cổng của lượt ghi ma trận khung đầu và của [grabRaw]. */
    @Volatile
    private var frames = 0L

    /** Số khung bị bỏ vì lượt vẽ trước chưa xong — đòn 2 ở KDoc lớp. */
    @Volatile
    private var busySkip = 0L

    private var loggedSkip = -1L

    /** Một lượt vẽ đã NÉM ⇒ dừng vẽ hẳn ([onDrawFailed]). `@Volatile` vì [stats] đọc từ luồng cầu kiểm thử. */
    @Volatile
    private var broken = false

    /** `true` = đang có một lượt vẽ xếp hàng hoặc đang chạy ⇒ khung mới bị BỎ, không xếp thêm. */
    private val pending = AtomicBoolean(false)

    private val alive = AtomicBoolean(false)

    /**
     * ═══ [P1 · SOÁT Opus 2026-09-27] Lượt DỰNG là **một lần cho cả đời thực thể** — không bao giờ đặt lại ════════
     *
     * [alive] một mình KHÔNG đủ: [stop] đặt nó về `false`, nên một [start] thứ hai đi qua được `compareAndSet` rồi
     * chạm `thread.start()` trên một `HandlerThread` **đã start và đã quit** ⇒ `IllegalThreadStateException`, ném từ
     * `TextureView.onSurfaceTextureAvailable`, tức trên **luồng main** ⇒ launcher chết.
     *
     * Đường tới đó [SUY] (chưa dựng lại được off-car): `TextureView` mất rồi có lại `SurfaceTexture` **trong cùng
     * một lượt `show()`** (màn tắt/bật, nền tảng dựng lại surface của cửa sổ). `hide()`/`show()` KHÔNG đi đường này.
     * Trả `null` là đúng hợp đồng [start], và chỗ gọi đã có đường rơi về `TextureView` thường ⇒ vẫn **có hình**.
     */
    private val everStarted = AtomicBoolean(false)

    /** Vẽ một khung — một thực thể `Runnable` duy nhất, không cấp phát khi post. */
    private val drawTask = Runnable { drawFrame() }

    /**
     * Dựng ngữ cảnh + program + `SurfaceTexture` vào trên luồng vẽ, rồi trả `Surface` để chỗ gọi giao cho HAL.
     *
     * **Chặn** luồng gọi tới [SETUP_MS] — có chủ ý: chỗ gọi (`TextureView.onSurfaceTextureAvailable`) phải có
     * `Surface` **ngay** để giao cho `AVMCamera.open`, y trình tự đường 2.73. Một lượt dựng EGL + biên dịch hai
     * shader là ~10–30 ms; hết giờ ⇒ trả `null` và chỗ gọi rơi về `TextureView` thường (không có khung nắn, nhưng
     * cũng không có màn đen câm).
     *
     * @return `Surface` để đổ khung vào, hoặc `null` nếu dựng hỏng.
     */
    fun start(output: SurfaceTexture, width: Int, height: Int): Surface? {
        if (!everStarted.compareAndSet(false, true)) {
            Log.w(PanoramaHal.TAG, "GL lượt dựng THỨ HAI trên cùng thực thể ⇒ từ chối (xem KDoc everStarted)")
            return null
        }
        if (!alive.compareAndSet(false, true)) return null
        thread.start()
        val h = Handler(thread.looper)
        handler = h
        viewW = width
        viewH = height
        val done = CountDownLatch(1)
        var ok = false
        h.post {
            ok = runCatching { setup(output) }.getOrElse {
                Log.w(PanoramaHal.TAG, "GL dựng hỏng: ${it.javaClass.simpleName} ${it.message}"); false
            }
            done.countDown()
        }
        if (!done.await2(SETUP_MS)) Log.w(PanoramaHal.TAG, "GL dựng quá $SETUP_MS ms")
        if (!ok) { stop(); return null }
        Log.i(
            PanoramaHal.TAG,
            "GL luồng vẽ lên: cửa sổ ${width}x$height nguồn ${streamW}x$streamH ${uniforms.describe()}" +
                " ${CameraGlInfo.summary()}",
        )
        return inputSurface
    }

    /** Cửa sổ đổi cỡ ⇒ đổi viewport ở lượt vẽ kế tiếp. Không dựng lại gì (EGL surface tự theo cỡ buffer). */
    fun resize(width: Int, height: Int) {
        viewW = width
        viewH = height
    }

    /**
     * Dỡ trọn bộ, **chặn** tới [TEARDOWN_MS]. Idempotent; gọi từ main.
     *
     * ## Thứ tự, và vì sao nó phải CHẶN
     * Chỗ gọi là `CameraOverlayView.hide()` / `onSurfaceTextureDestroyed`. Bước ngay sau nó là nền tảng **huỷ**
     * `SurfaceTexture` của `TextureView` — trong khi `eglSurface` của ta còn trỏ vào đó. Trả về trước khi luồng vẽ
     * dọn xong là mở đúng cửa sổ đua ấy, và hậu quả không phải một dòng log mà là một `SIGSEGV` trong driver GPU.
     *
     * Thứ tự bên trong: **producer trước, consumer sau** — nhả `Surface` vào (HAL đã `stopPreview` từ trước,
     * `CameraSignalController.stop` gọi `avm.close()` **trước** `overlay.hide()`), rồi `SurfaceTexture` vào, rồi
     * texture + program, rồi ngữ cảnh EGL. Ngược thứ tự là huỷ một `BufferQueue` mà đầu kia còn đang giữ.
     */
    fun stop() {
        if (!alive.compareAndSet(true, false)) return
        val h = handler
        val done = CountDownLatch(1)
        if (h == null || !h.post { runCatching { teardown() }; done.countDown() }) teardown()
        else if (!done.await2(TEARDOWN_MS)) Log.w(PanoramaHal.TAG, "GL dỡ quá $TEARDOWN_MS ms")
        handler = null
        runCatching { thread.quitSafely() }
        Log.i(PanoramaHal.TAG, "GL luồng vẽ xuống: ${stats()}")
    }

    /**
     * Chụp MỘT khung **THÔ** (không nắn, nguyên khung, không xoay) cỡ [w]×[h] vào một FBO rồi `glReadPixels`.
     *
     * Đây là đường trả lời câu hỏi mà cả tầng nắn dựa vào: *"vòng ảnh fisheye thật bán kính bao nhiêu, tâm ở đâu"*
     * (`camera-dewarp-math.md` §3.2 D1/D2). Nó **không** đi qua `TextureView.getBitmap` vì trên đường GL thứ
     * `getBitmap` trả về là khung **ĐÃ NẮN** (cửa ra là chính `SurfaceTexture` của `TextureView`) — tức đúng thứ
     * không dùng được để đo tham số nắn.
     *
     * @return ARGB **hàng trên trước** (`out[y*w + x]`), hoặc `null` khi chưa có khung nào / FBO không dựng được.
     */
    fun grabRaw(w: Int, h: Int): IntArray? {
        val hd = handler ?: return null
        if (frames <= 0L) return null
        val cap = CameraGlInfo.cap(w)
        val rw = w.coerceIn(1, cap)
        val rh = h.coerceIn(1, cap)
        var out: IntArray? = null
        val done = CountDownLatch(1)
        if (!hd.post { out = runCatching { readback(rw, rh) }.getOrNull(); done.countDown() }) return null
        if (!done.await2(GRAB_MS)) { Log.w(PanoramaHal.TAG, "GL chụp thô quá $GRAB_MS ms"); return null }
        return out
    }

    /**
     * Số khung đã vẽ / đã bỏ — cho lời đáp `camera_frame`. Chỉ ĐỌC.
     *
     * **ASCII**, cùng luật mã lỗi của cầu kiểm thử: chuỗi này đi vào JSON mà script đọc, và `busySkip` còn là đúng
     * token Electro dùng (RE §4.3 @0x4878a) nên một lượt `grep busySkip` bắt được cả hai bên.
     */
    fun stats(): String =
        if (broken) "frames=$frames busySkip=$busySkip broken=true" else "frames=$frames busySkip=$busySkip"

    // ── Trên luồng vẽ ────────────────────────────────────────────────────────────────────────────

    private fun setup(output: SurfaceTexture): Boolean {
        if (!egl.create(output)) return false
        program = CameraGlProgram.build() ?: return false
        locate()
        oesTex = CameraGlProgram.oesTexture()
        return createInput()
    }

    /**
     * `SurfaceTexture` VÀO, gắn với texture OES, nghe khung trên **luồng này**.
     *
     * `setDefaultBufferSize` chỉ là **gợi ý**: [ĐO] AOSP `SurfaceTexture.java:232-234` — *"The image producer may
     * override the buffer size … Both video and camera based image producers do override the size."* ⇒ với HAL thật
     * nó gần như vô tác dụng, và điều đó **không sao**: nó có mặt cho hai ca khác, cả hai đều thật:
     *  • **ảnh tổng hợp** (`camera_synth` trên máy ảo) đi qua `Surface.lockCanvas`, và ở đó cỡ này là cỡ DUY NHẤT
     *    quyết định khung canvas (cùng dòng AOSP: *"This method may be used to set the image size when producing
     *    images with Canvas (via Surface.lockCanvas)"*);
     *  • **trần texture** — cỡ xin phải ≤ `min(GL_MAX_VIEWPORT_DIMS, GL_MAX_TEXTURE_SIZE)`, nếu không lỗi *"might
     *    not be reported until updateTexImage()"* (`:242-245`). Vì vậy kẹp theo [CameraGlInfo.cap] đã đo ở
     *    [CameraGlSurface.probe] — chứ không xin 5120 rồi chờ một khung đen không ai giải thích được (RE §7 Q13).
     */
    private fun createInput(): Boolean {
        val st = SurfaceTexture(oesTex)
        if (streamW > 0 && streamH > 0) {
            val cap = CameraGlInfo.cap(maxOf(streamW, streamH))
            val w = streamW.coerceAtMost(cap)
            val h = streamH.coerceAtMost(cap)
            if (w != streamW || h != streamH) {
                Log.w(PanoramaHal.TAG, "GL nguồn ${streamW}x$streamH > trần texture $cap ⇒ xin ${w}x$h (RE Q13)")
            }
            st.setDefaultBufferSize(w, h)
        }
        st.setOnFrameAvailableListener({ onFrameAvailable() }, handler)
        input = st
        inputSurface = Surface(st)
        return true
    }

    /**
     * Khung mới tới — **chỉ** xếp một lượt vẽ, hoặc BỎ khung nếu lượt trước chưa xong (đòn 2 ở KDoc lớp).
     *
     * Chạy trên luồng vẽ (ta đã truyền `Handler` của nó), nên "xếp hàng" ở đây nghĩa là *sau* lượt vẽ đang chạy.
     * Không `updateTexImage` ở lượt bị bỏ có chủ ý: `BufferQueue` giữ khung **mới nhất** và `updateTexImage` của lượt
     * sau sẽ lấy chính nó ([ĐO] AOSP `SurfaceTexture.java:35-37`: *"the contents … are updated to contain the most
     * recent image from the image stream. This may cause some frames of the stream to be skipped."*) ⇒ bỏ khung là
     * **trễ ít hơn**, không phải mất hình.
     */
    private fun onFrameAvailable() {
        if (!pending.compareAndSet(false, true)) { busySkip++; return }
        if (handler?.post(drawTask) != true) pending.set(false)
    }

    private fun drawFrame() {
        try {
            if (broken) return
            val st = input ?: return
            if (!egl.ready) return
            st.updateTexImage()
            if (uniforms.texMatrix) st.getTransformMatrix(texMatrix) else IDENTITY.copyInto(texMatrix)
            if (frames == 0L && uniforms.texMatrix) CameraGlInfo.recordTexMatrix(texMatrix)
            paint(viewW, viewH)
            egl.swap()
            frames++
            if (frames % LOG_EVERY == 0L && busySkip != loggedSkip) {
                loggedSkip = busySkip
                Log.d(PanoramaHal.TAG, "GL ${stats()}")
            }
        } catch (t: Throwable) {
            onDrawFailed(t)
        } finally {
            pending.set(false)
        }
    }

    /**
     * ═══ [P0 · SOÁT Opus 2026-09-27] Lượt vẽ NÉM — ghi một dòng rồi **dừng hẳn**, không để ngoại lệ thoát ra ════
     *
     * Trước bản vá, [drawFrame] có `try … finally` mà **không `catch`**: một ngoại lệ thoát khỏi `Runnable` của
     * `Looper` đi thẳng tới `uncaughtExceptionHandler` ⇒ **launcher của xe đang lăn bánh biến mất**. Đường ném là
     * THẬT: [ĐO] AOSP `android-10.0.0_r47` `graphics/java/android/graphics/SurfaceTexture.java:371-376` —
     * `updateTexImage()` ném `RuntimeException` khi `nativeUpdateTexImage` trả lỗi, tức khi producer đã biến mất
     * (helper HAL chết — ca mà 2.72 dựng lưới cho) hoặc ngữ cảnh GL bị driver thu hồi (`EGL_CONTEXT_LOST`).
     *
     * **Dừng** chứ không thử lại: cả hai nguyên nhân không tự lành trong một lượt overlay, nên thử lại 15 lần/giây
     * chỉ đổi một cú crash thành một trận bão log. Khung đứng ở ảnh cuối, nhãn vẫn đó, và lượt xi-nhan SAU dựng lại
     * trọn bộ (mỗi `show()` là một thực thể mới) ⇒ tự phục hồi ở đúng nhịp tự nhiên.
     *
     * Bắt `Throwable` chứ không chỉ `Exception`, cùng khuôn `VoiceSessionListen.runListen` (*"launcher KHÔNG được
     * chết vì một tính năng phụ"*): `UnsatisfiedLinkError` của driver GL thiếu ký hiệu cũng là `Error`.
     */
    private fun onDrawFailed(t: Throwable) {
        if (broken) return
        broken = true
        Log.w(
            PanoramaHal.TAG,
            "GL lượt vẽ NÉM ⇒ dừng vẽ (khung đứng, launcher sống; lượt xi-nhan sau dựng lại):" +
                " ${t.javaClass.simpleName} ${t.message} — ${stats()}",
        )
    }

    /** Một quad toàn khung với trọn bộ uniform. Dùng chung cho cửa sổ và cho FBO của [readback]. */
    private fun paint(w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniform1i(uTex, 0)
        // `transpose = false`: [ĐO] AOSP `SurfaceTexture.java:308-309` — *"The matrix is stored in column-major order
        // so that it may be passed directly to OpenGL ES via … glUniformMatrix4fv"*. Chuyển vị ở đây là xoay/lật
        // khung theo một cách trông "gần đúng" trên ma trận đơn vị và sai hẳn trên ma trận thật của xe.
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)
        val u = active
        val r = u.srcRect
        GLES20.glUniform4f(uSrcRect, r[0], r[1], r[2], r[3])
        GLES20.glUniform1f(uRotation, u.rotationDeg)
        val d = u.dewarp
        GLES20.glUniform1f(uAmount, d.amount)
        GLES20.glUniform1f(uFocal, d.focal)
        GLES20.glUniform1f(uK, d.k)
        GLES20.glUniform1f(uScale, d.scale)
        GLES20.glUniform1f(uAspect, u.aspect)
        GLES20.glUniform2f(uCenter, d.centerX, d.centerY)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, posBuf)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texBuf)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /**
     * Vị trí uniform/attribute, lấy **theo TÊN** ngay sau khi liên kết program.
     *
     * Lượt kiểm cuối đọc danh sách từ `:core` ([CameraDewarpShader.UNIFORMS]) chứ không từ mấy dòng ngay trên: thêm
     * một `uniform` vào GLSL mà quên gán ở đây thì nó nhận `0` và khung ra sai **im lặng** — đúng loại lỗi mà
     * `declaredUniforms()` sinh ra để chặn ở tầng test, và dòng dưới đây chặn nốt ở tầng chạy.
     */
    private fun locate() {
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uSrcRect = GLES20.glGetUniformLocation(program, "uSrcRect")
        uRotation = GLES20.glGetUniformLocation(program, "uRotation")
        uAmount = GLES20.glGetUniformLocation(program, "uAmount")
        uFocal = GLES20.glGetUniformLocation(program, "uFocal")
        uK = GLES20.glGetUniformLocation(program, "uK")
        uScale = GLES20.glGetUniformLocation(program, "uScale")
        uAspect = GLES20.glGetUniformLocation(program, "uAspect")
        uCenter = GLES20.glGetUniformLocation(program, "uCenter")
        val missing = CameraDewarpShader.UNIFORMS.filter { GLES20.glGetUniformLocation(program, it) < 0 }
        if (missing.isNotEmpty()) Log.w(PanoramaHal.TAG, "GL uniform KHÔNG tìm thấy: $missing (khung sẽ sai)")
    }

    /** Vẽ một lượt **thô** vào FBO cỡ [w]×[h] rồi đọc pixel về. Trên luồng vẽ. */
    private fun readback(w: Int, h: Int): IntArray? {
        val fbo = IntArray(1)
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[0], 0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        var out: IntArray? = null
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.w(PanoramaHal.TAG, "GL FBO ${w}x$h không hoàn chỉnh: 0x${Integer.toHexString(status)}")
        } else {
            val saved = raw
            raw = true
            runCatching { paint(w, h) }
            raw = saved
            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            out = CameraGlProgram.argbFlipped(buf, w, h)
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDeleteFramebuffers(1, fbo, 0)
        GLES20.glDeleteTextures(1, tex, 0)
        return out
    }

    /**
     * Lượt vẽ hiện tại là lượt **thô** hay không — [paint] đọc nó để chọn bộ uniform.
     *
     * Một cờ chứ không phải một tham số của [paint] vì [paint] nằm trong đường khung hình: truyền một
     * `CameraGlUniforms` mỗi lượt là một tham số tham chiếu thêm trên stack ở 15 fps — không đắt, nhưng cũng không
     * cần, và một cờ `Boolean` đọc được từ bài canh *"đường khung hình không rẽ nhánh theo dữ liệu ngoài"*.
     */
    private var raw = false

    private val active: CameraGlUniforms get() = if (raw) passthrough else uniforms

    private val passthrough = CameraGlUniforms.passthrough(uniforms.texMatrix)

    private fun teardown() {
        runCatching { input?.setOnFrameAvailableListener(null) }
        runCatching { inputSurface?.release() }
        inputSurface = null
        runCatching { input?.release() }
        input = null
        if (oesTex != 0) GLES20.glDeleteTextures(1, intArrayOf(oesTex), 0)
        oesTex = 0
        if (program != 0) GLES20.glDeleteProgram(program)
        program = 0
        egl.release()
    }

    private fun CountDownLatch.await2(ms: Long): Boolean =
        runCatching { await(ms, TimeUnit.MILLISECONDS) }.getOrDefault(false)

    private companion object {
        const val THREAD = "kachi-camgl"

        /** Trần chờ lượt dựng (EGL + hai shader). Vượt ⇒ rơi về đường `TextureView` 2.73. */
        const val SETUP_MS = 1500L

        /** Trần chờ lượt dỡ — phải đủ cho một `eglSwapBuffers` đang dở (một nhịp vsync ≈ 16 ms) rồi dọn. */
        const val TEARDOWN_MS = 1500L

        /** Trần chờ một lượt chụp thô (FBO 5120×960 + `glReadPixels` ~20 MB). */
        const val GRAB_MS = 5000L

        /** Ghi nhật ký nhịp vẽ mỗi bấy nhiêu khung — 15 fps ⇒ ≈ 1 dòng / 6,7 s, ở mức DEBUG. */
        const val LOG_EVERY = 100L

        val IDENTITY = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )

        /**
         * Quad toàn khung, `TRIANGLE_STRIP` 4 đỉnh — và **quy ước trục v là chỗ dễ sai nhất của cả tệp**.
         *
         * Cặp `(NDC y, v)` được chọn là `(+1, 0)` / `(−1, 1)`, tức **`v = 0` là ĐỈNH cửa sổ**. Không phải tuỳ ý: cả
         * shader ([CameraDewarpShader] bước 1) và [CameraOverlayTransform] xoay theo quy ước **màn hình, trục y
         * hướng XUỐNG** (dương = cùng chiều kim đồng hồ). Lấy `v = 0` ở đáy — như quad mẫu của Grafika — thì cùng một
         * uniform `uRotation` sẽ xoay **ngược chiều** và khung còn bị lật dọc so với đường `TextureView`, mà không
         * có một lời báo lỗi nào. Phép đổi từ đây sang trục `t` của texture nằm ở [CameraGlUniforms.textureT].
         */
        val POS = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

        /** Xem KDoc [POS] — `v = 0` ứng với `NDC y = +1`. */
        val TEX = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)

        val posBuf: FloatBuffer = direct(POS)
        val texBuf: FloatBuffer = direct(TEX)

        private fun direct(v: FloatArray): FloatBuffer = ByteBuffer
            .allocateDirect(v.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(v); position(0) }
    }
}
