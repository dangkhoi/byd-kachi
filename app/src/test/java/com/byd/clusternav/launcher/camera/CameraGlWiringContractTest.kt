package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.testbridge.TestBridgeCommands
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R8-B · đường kết xuất GL — bài canh DÂY NỐI của `:app` ═══════════════════════════════════════════════════
 *
 * Toán + phép hợp uniform đã test bằng số ở `:core` (`CameraGlUniformsTest` 15 ca, `CameraDewarpPrefsTest` 9 ca).
 * Bài này canh những thứ **không** test được off-car — `EGL14`, `GLES20`, `SurfaceTexture`, `HandlerThread`,
 * `SharedPreferences` đều không chạy trên JVM — nên mỗi mắt xích dưới đây là thứ gỡ đi thì **build vẫn xanh và không
 * bài `:core` nào đỏ** (CLAUDE.md §8, bài học `CastShell.evictVd` viết xong mà chưa từng được gọi).
 *
 * Bốn nhóm, theo mức thiệt hại nếu mất:
 *  (A) **mặc định KHÔNG đổi** — mất là đổi hành vi của xe đang chạy hiện trường (CLAUDE.md §6);
 *  (B) **đường khung hình sạch** — mất là giật thêm đúng lúc đang điều tra giật (CLOSE-14);
 *  (C) **thứ tự dỡ + luồng** — mất là `SIGSEGV` trong driver GPU, không phải một dòng log;
 *  (D) **bề mặt owner/đo** — mất là một tính năng có code mà không ai chạm tới được.
 */
class CameraGlWiringContractTest {

    private fun app(relative: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$relative")

    private val overlay by lazy { app("launcher/camera/CameraOverlayView.kt") }
    private val layer by lazy { app("launcher/camera/CameraVideoLayer.kt") }
    private val renderer by lazy { app("launcher/camera/CameraGlRenderer.kt") }
    /** 2.92 — vị trí + gán uniform tách khỏi renderer (trần 500 dòng); vẫn là đường khung hình. */
    private val bindings by lazy { app("launcher/camera/CameraGlBindings.kt") }
    private val eglSurface by lazy { app("launcher/camera/CameraGlSurface.kt") }
    private val glProgram by lazy { app("launcher/camera/CameraGlProgram.kt") }
    private val synth by lazy { app("launcher/camera/CameraSynthFeeder.kt") }
    private val controller by lazy { app("launcher/camera/CameraSignalController.kt") }
    private val prefs by lazy { app("PrefsCameraDewarp.kt") }
    private val prefsSet by lazy { app("launcher/testbridge/TestBridgePrefsSet.kt") }
    private val settings by lazy { app("launcher/SettingsSectionsCamera.kt") }
    private val frameCmd by lazy { app("launcher/testbridge/TestBridgeCameraFrame.kt") }
    private val bridge by lazy { app("launcher/testbridge/KachiTestBridge.kt") }

    // ══ (A) MẶC ĐỊNH KHÔNG ĐỔI ═════════════════════════════════════════════════════════════════════════════

    /**
     * Đường GL **chỉ** dựng khi pref = `GL`, và tầng vẽ vẫn mặc định `TextureView`.
     *
     * Đây là bài **mutation-check** của cả R8-B: đổi `defaultRender()` sang `GL` ở `:core` phải làm bài
     * `CameraSignalPolicyTest.ket xuat mac dinh la TextureView` đỏ, còn đổi nhánh ở đây (vd bỏ `rotatesInShader` để
     * "cho GL chạy luôn") phải làm bài này đỏ. Hai lưới ở hai tầng vì hai cách phá khác nhau.
     */
    @Test fun `duong GL chi dung khi pref la GL, mac dinh khong doi`() {
        assertTrue("render: String = CameraSignalPolicy.RENDER_TEXTURE" in overlay, "mặc định tầng vẽ = đường 2.73")
        val create = SourceRoots.body(layer, "fun create(")
        assertTrue("CameraSignalPolicy.usesTextureView(render)" in create, "chọn lớp view theo `:core`, không so chuỗi")
        assertTrue("CameraSignalPolicy.rotatesInShader(render)" in create, "chỉ mã GL mới vào nhánh glVideo")
        assertTrue("glVideo(" in create, "nhánh GL phải có call site — nếu không thì cả tầng chưa từng chạy")
        // `gl == null` (chưa dựng được bộ uniform) ⇒ rơi về TextureView thường, KHÔNG dựng ngữ cảnh EGL nào.
        assertTrue("gl == null" in create, "thiếu bộ uniform ⇒ rơi về đường 2.73, không vẽ một khung đen câm")

        // Controller chỉ đọc bảy khoá nắn khi đường là GL — xe không chạm Cài đặt thì không một lượt đọc đĩa nào.
        // 2.76 · R3: lượt dựng phiên nằm ở `openSession` (dùng chung cho xi-nhan và cho phép lùi CHANNEL → PANO).
        val tick = SourceRoots.body(controller, "private fun openSession(")
        assertTrue("CameraSignalPolicy.rotatesInShader(render)" in tick, "bộ uniform chỉ dựng ở đường GL")
        assertTrue("Prefs.cameraGlUniforms(" in tick, "đọc bảy khoá qua MỘT cửa (`PrefsCameraDewarp`)")
        assertTrue("gl = gl," in tick, "bộ uniform phải đi vào overlay.show(gl = …)")
    }

    /**
     * `setTransform` **không** được gọi khi shader đang sống — áp cả hai là cắt+xoay hai lần.
     *
     * [SOÁT Opus 2026-09-27] Bài này trước đây cấm `applyTransform` **tuyệt đối** trong `glVideo`, và cái lệnh cấm ấy
     * là lý do đường RƠI (luồng vẽ không lên được) chưa từng được nối: nó *"đúng"* nhưng nó khoá luôn cả việc cửa sổ
     * còn có hình. Nay bất biến được nói đúng hơn: `applyTransform` chỉ được gọi ở nhánh **đã rơi** (`fellBack`), tức
     * đúng lúc KHÔNG có shader nào chạy. Vế *"đường GL sống thì không"* vẫn được ghim — bằng thứ tự, không bằng lệnh cấm.
     */
    @Test fun `setTransform khong chay o duong GL`() {
        val glBody = SourceRoots.body(layer, "private fun glVideo(")
        assertFalse("setTransform" in glBody, "setTransform trên đường GL phải là ma trận đơn vị, tức KHÔNG gọi")
        // Hai lời gọi, cả hai thuộc đường RƠI: một ở `available` (sau `input == null`), một ở `size-changed` (sau cờ).
        assertEquals(
            2, Regex("""applyTransform\(""").findAll(glBody).count(),
            "chỉ HAI lời gọi, và cả hai phải nằm sau cổng `fellBack` — thêm cái thứ ba là cắt+xoay hai lần",
        )
        assertTrue("fellBack[0] = true" in glBody, "phải có cờ ĐÃ RƠI, nếu không size-changed không biết mình là ai")
        assertTrue("if (fellBack[0]) applyTransform(" in glBody, "size-changed: đã rơi thì áp lại ma trận theo cỡ mới")
        // Trên đường GL SỐNG thì không: lời gọi ở `available` phải đứng SAU cổng `input == null`.
        val avail = glBody.substring(glBody.indexOf("onSurfaceTextureAvailable"))
        assertTrue(
            avail.indexOf("if (input == null)") in 0 until avail.indexOf("applyTransform("),
            "applyTransform ở `available` phải nằm TRONG nhánh `input == null` (luồng vẽ không lên được)",
        )
        // Và đường rơi phải giao ĐÚNG `Surface` của `SurfaceTexture` này cho HAL — nếu không thì vẫn là ô đen.
        assertTrue("onSurfaceReady(Surface(st))" in glBody, "rơi về TV mà không giao Surface ⇒ vẫn không có khung")
        // Nhánh TV thì ngược lại: phải gọi, ở CẢ hai callback đổi cỡ.
        val tvBody = SourceRoots.body(layer, "private fun textureVideo(")
        assertEquals(2, Regex("""applyTransform\(""").findAll(tvBody).count(), "TV: available + size-changed")
        // Và đường GL vẫn là `TextureView` ⇒ `videoLp` phải hỏi `usesTextureView`, không `rotatesByMatrix`.
        val lp = SourceRoots.body(overlay, "private fun videoLp(")
        assertTrue("CameraSignalPolicy.usesTextureView(st.render)" in lp, "GL lấp kín cửa sổ như TV, không phóng-kéo-lệch")
        assertFalse("rotatesByMatrix" in lp, "dùng rotatesByMatrix ở đây đẩy GL vào nhánh SurfaceView ⇒ cắt hai lần")
    }

    /** Hình TRÒN (R8-A) phải ăn trên đường GL: outline oval nằm ở view CHA, và GL vẫn là `TextureView`. */
    @Test fun `hinh TRON van an tren duong GL`() {
        val show = SourceRoots.body(overlay, "    fun show(")
        assertTrue("roundOutline(radius, round)" in show, "outline oval đặt trên view CHA ⇒ cắt mọi lớp con HWUI")
        assertTrue("GradientDrawable.OVAL" in show, "nền cũng phải là oval, nếu không bốn góc lộ màu đục")
        // Không có một nhánh nào loại GL khỏi lượt bo — nếu có, chip "Tròn" sẽ im lặng vẽ hình vuông.
        assertFalse("RENDER_GL" in show, "lượt bo/nền không được rẽ nhánh theo đường kết xuất")
        assertTrue(CameraSignalPolicy.usesTextureView(CameraSignalPolicy.RENDER_GL), "tiền đề: GL là TextureView")
    }

    // ══ (B) ĐƯỜNG KHUNG HÌNH SẠCH ══════════════════════════════════════════════════════════════════════════

    /**
     * [CameraGlRenderer.drawFrame] + `paint` không cấp phát, không shell, và **chỉ** ghi nhật ký qua hai cổng đếm.
     *
     * 15 khung/giây đi qua đây. Một `FloatArray(16)` mỗi khung là 960 lượt cấp phát/phút cho GC của launcher — đúng
     * vùng CLOSE-14 đang điều tra. Một `Log.i` mỗi khung thì tệ hơn: nó tự làm hỏng phép đo của chính mình.
     */
    @Test fun `duong khung hinh GL khong cap phat khong log khong shell`() {
        val draw = SourceRoots.body(renderer, "private fun drawFrame(")
        val paint = SourceRoots.body(renderer, "private fun paint(")
        val bind = SourceRoots.body(bindings, "fun bind(")
        assertTrue("bindings.bind(active, texMatrix)" in paint, "paint gán uniform qua MỘT cửa (CameraGlBindings.bind)")
        listOf("draw" to draw, "paint" to paint, "bind" to bind).forEach { (name, body) ->
            listOf("FloatArray(", "IntArray(", "ByteArray(", "ByteBuffer.allocate", "arrayOf(", "mutableListOf").forEach {
                assertFalse(it in body, "$name cấp phát `$it` mỗi khung")
            }
            listOf("Log.i(", "Log.w(", "Log.e(", ".format(", "dumpsys", "Runtime.getRuntime", "ProcessBuilder").forEach {
                assertFalse(it in body, "$name có `$it` trong đường khung hình")
            }
        }
        // Hai cổng đếm — và cả hai phải có mặt, nếu không thì "không log" đạt được bằng cách bỏ luôn số liệu.
        assertTrue("if (frames == 0L" in draw, "ma trận khung ĐẦU chỉ ghi một lần (RE §7 Q17)")
        assertTrue("frames % LOG_EVERY == 0L" in draw, "busySkip ghi mỗi LOG_EVERY khung")
        assertTrue("Log.d(" in draw, "số khung bỏ phải ghi được — ở mức DEBUG")
        assertTrue("busySkip != loggedSkip" in draw, "không đổi thì không ghi (đừng in cùng một con số mãi)")
        // Ma trận + quad dựng SẴN.
        assertTrue("private val texMatrix = FloatArray(16)" in renderer, "ma trận là trường, không phải biến cục bộ")
        assertTrue("val posBuf: FloatBuffer = direct(POS)" in renderer, "quad dựng một lần cho cả tiến trình")
    }

    /** Bỏ khung khi lượt vẽ trước chưa xong (RE §4.3 đòn 1) — cơ chế Kachi tới 2.73 **không có** (RE §5 K8). */
    @Test fun `bo khung khi luot ve truoc chua xong`() {
        val body = SourceRoots.body(renderer, "private fun onFrameAvailable(")
        assertTrue("pending.compareAndSet(false, true)" in body, "một phép NGUYÊN TỬ, không `if (busy) … busy = true`")
        assertTrue("busySkip++" in body, "phải ĐẾM khung bỏ — không đếm thì không ai biết GPU có kịp không")
        assertTrue("handler?.post(drawTask)" in body, "vẽ trên luồng riêng, không trên luồng gọi callback")
        assertTrue("pending.set(false)" in SourceRoots.body(renderer, "private fun drawFrame("), "phải nhả cờ")
        assertTrue("finally" in SourceRoots.body(renderer, "private fun drawFrame("), "nhả cờ cả khi vẽ NÉM")
        assertTrue("private val drawTask = Runnable" in renderer, "một Runnable duy nhất ⇒ post không cấp phát")
    }

    /**
     * `setOnFrameAvailableListener` phải truyền **`Handler` của luồng vẽ**.
     *
     * [ĐO] AOSP `android-10.0.0_r47` `graphics/java/android/graphics/SurfaceTexture.java:186-189`: bản một-tham-số
     * gọi callback trên *"an arbitrary thread"*, và `updateTexImage` từ luồng không giữ `EGLContext` **không làm gì**
     * mà cũng không ném ⇒ khung đứng im với log sạch, đúng loại lỗi không ai truy được.
     */
    @Test fun `listener khung moi di qua Handler cua luong ve`() {
        assertTrue(
            "st.setOnFrameAvailableListener({ onFrameAvailable() }, handler)" in renderer,
            "phải dùng bản HAI tham số với handler của luồng vẽ (AOSP SurfaceTexture.java:186-189)",
        )
        assertTrue("HandlerThread(THREAD)" in renderer && "const val THREAD = \"kachi-camgl\"" in renderer,
            "luồng vẽ có TÊN ⇒ đọc được trong `dumpsys gfxinfo`/traces (RE §4.3 đòn 3)")
    }

    /**
     * ═══ [P0] Một lượt vẽ NÉM **không được** giết launcher, và một lượt dựng thứ hai không được ném ════════════
     *
     * [SOÁT Opus 2026-09-27] Hai lỗ cùng một họ — *"ngoại lệ thoát ra khỏi một `Runnable`/callback"*:
     *  (a) `drawFrame` có `try … finally` mà **không `catch`** ⇒ `SurfaceTexture.updateTexImage()` ném
     *      `RuntimeException` ([ĐO] AOSP `SurfaceTexture.java:371-376`) khi producer chết hoặc ngữ cảnh GL bị thu
     *      hồi ⇒ ngoại lệ đi tới `uncaughtExceptionHandler` của `HandlerThread` ⇒ **tiến trình chết**;
     *  (b) `start()` gọi lần hai trên cùng thực thể (destroy → available lại trong CÙNG một `show()`) ⇒
     *      `thread.start()` ném `IllegalThreadStateException` trên **luồng main**.
     *
     * Cả hai là *"launcher của một chiếc xe đang lăn bánh biến mất"*, tức mức thiệt hại cao nhất trong tệp này —
     * nên chúng có bài canh riêng, không gộp vào bài *"đường khung hình sạch"* ở trên.
     */
    @Test fun `mot luot ve nem khong giet launcher, luot dung thu hai bi tu choi`() {
        val draw = SourceRoots.body(renderer, "private fun drawFrame(")
        assertTrue("catch (t: Throwable)" in draw, "phải bắt `Throwable` (driver GL ném cả `Error`), không chỉ Exception")
        assertTrue("onDrawFailed(t)" in draw, "thân `catch` chỉ gọi bộ xử lý — đường khung hình vẫn không được log")
        assertTrue("if (broken) return" in draw, "đã hỏng thì thôi vẽ, không thử lại 15 lần/giây")
        val failed = SourceRoots.body(renderer, "private fun onDrawFailed(")
        assertTrue("broken = true" in failed, "phải chốt cờ, nếu không mỗi khung lại ghi một dòng")
        assertTrue("Log.w(" in failed, "phải ghi LÝ DO — một khung đứng im mà log sạch là lỗi không ai truy được")
        assertTrue("@Volatile" in renderer && "private var broken = false" in renderer, "cờ đọc từ luồng khác")

        val start = SourceRoots.body(renderer, "    fun start(")
        assertTrue(
            "everStarted.compareAndSet(false, true)" in start,
            "lượt dựng phải là MỘT lần cho cả đời thực thể — `alive` không đủ vì `stop()` đặt nó về false",
        )
        assertTrue(
            start.indexOf("everStarted.compareAndSet") < start.indexOf("thread.start()"),
            "cổng một-lần phải đứng TRƯỚC `thread.start()`, nếu không IllegalThreadStateException vẫn ném",
        )
    }

    // ══ (C) THỨ TỰ DỠ + LUỒNG ══════════════════════════════════════════════════════════════════════════════

    /**
     * Thứ tự dỡ: **HAL trước** (không đụng ở đây — `controller.stop()` giữ nguyên thứ tự 2.73), rồi producer, rồi
     * `SurfaceTexture` vào, rồi texture+program, rồi ngữ cảnh EGL. Và lượt dỡ phải **CHẶN**.
     */
    @Test fun `thu tu do dung va chan cho luong ve don xong`() {
        // Đường HAL của 2.73 KHÔNG đổi: `avm.close()` vẫn đứng trước `overlay.hide()`. Từ 2.75 chuỗi ấy nằm ở
        // `closeSession()` (lượt đổi bên dùng chung — xem `do phien cu TRUOC khi mo phien moi`), `stop()` gọi lại nó.
        val stop = SourceRoots.body(controller, "private fun closeSession(")
        val iAvm = stop.indexOf("avm.close()")
        val iHal = stop.indexOf("hal.close()")
        val iHide = stop.indexOf("overlay.hide()")
        assertTrue(iAvm in 0..iHal && iHal < iHide, "thứ tự avm.close → hal.close → overlay.hide (đường 2.73)")

        // `hide()` dỡ LỚP trước khi gỡ view (gỡ view = nền tảng huỷ SurfaceTexture mà EGLSurface còn trỏ vào).
        val hide = SourceRoots.body(overlay, "    fun hide(")
        assertTrue(hide.indexOf("layer?.release()") < hide.indexOf("wm?.removeView"), "dỡ lớp video TRƯỚC removeView")

        // `CameraVideoLayer.release`: producer trước, consumer sau.
        val release = SourceRoots.body(layer, "    fun release(")
        assertTrue(release.indexOf("synth?.stop()") < release.indexOf("renderer?.stop()"), "producer trước consumer")

        // `CameraGlRenderer.stop` phải CHẶN (latch + trần thời gian), không `post` rồi trả về.
        val rstop = SourceRoots.body(renderer, "    fun stop(")
        assertTrue("CountDownLatch(1)" in rstop && "await2(TEARDOWN_MS)" in rstop, "phải chờ luồng vẽ dọn xong")
        assertTrue("alive.compareAndSet(true, false)" in rstop, "idempotent bằng một phép nguyên tử")
        assertTrue("quitSafely()" in rstop)

        // `onSurfaceTextureDestroyed` của nhánh GL phải dỡ TRƯỚC khi trả `true`.
        val glBody = SourceRoots.body(layer, "private fun glVideo(")
        val destroyed = glBody.substring(glBody.indexOf("onSurfaceTextureDestroyed"))
        assertTrue(destroyed.indexOf("release()") < destroyed.indexOf("return true"), "dỡ rồi mới nhả SurfaceTexture")
    }

    /** `teardown` dỡ ĐỦ sáu thứ, đúng chiều ngược lượt dựng — bỏ sót một là một đối tượng GPU rò mỗi lượt xi-nhan. */
    @Test fun `teardown do du sau thu`() {
        val body = SourceRoots.body(renderer, "private fun teardown(")
        val order = listOf(
            "setOnFrameAvailableListener(null)", "inputSurface?.release()", "input?.release()",
            "glDeleteTextures", "glDeleteProgram", "egl.release()",
        )
        var at = -1
        order.forEach {
            val i = body.indexOf(it)
            assertTrue(i > at, "`$it` thiếu hoặc sai thứ tự trong teardown")
            at = i
        }
        // EGL tự dỡ ngược: makeCurrent(NO_SURFACE) → destroySurface → destroyContext → releaseThread → terminate.
        val er = SourceRoots.body(eglSurface, "    fun release(")
        val eglOrder = listOf("eglMakeCurrent", "eglDestroySurface", "eglDestroyContext", "eglReleaseThread", "eglTerminate")
        var eat = -1
        eglOrder.forEach {
            val i = er.indexOf(it)
            assertTrue(i > eat, "`$it` thiếu hoặc sai thứ tự — huỷ một surface đang current là hành vi không định nghĩa")
            eat = i
        }
    }

    /** Cấu hình EGL: RGBA8888 + ES **2.0** + window bit, và KHÔNG xin depth/stencil (một quad không cần). */
    @Test fun `cau hinh EGL dung ES2 va khong xin depth stencil`() {
        listOf(
            "EGL14.EGL_RED_SIZE, 8", "EGL14.EGL_GREEN_SIZE, 8", "EGL14.EGL_BLUE_SIZE, 8", "EGL14.EGL_ALPHA_SIZE, 8",
            "EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT", "EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT",
        ).forEach { assertTrue(it in eglSurface, "thiếu thuộc tính cấu hình `$it`") }
        assertTrue("EGL14.EGL_CONTEXT_CLIENT_VERSION, 2" in eglSurface, "shader viết cho ES 2.0 (samplerExternalOES)")
        listOf("EGL_DEPTH_SIZE", "EGL_STENCIL_SIZE").forEach {
            assertFalse(it in eglSurface, "$it là bộ nhớ GPU cấp cho việc không ai dùng")
        }
        assertTrue("EGL14.EGL_NO_CONTEXT," in eglSurface, "không chia sẻ ngữ cảnh — không có ai để chia sẻ với")
        // Trần texture phải ĐO, không đoán (RE §7 Q13) — Electro không kiểm bao giờ (RE §4.2).
        assertTrue("GLES20.GL_MAX_TEXTURE_SIZE" in eglSurface && "GLES20.GL_MAX_VIEWPORT_DIMS" in eglSurface)
        assertTrue("GLES20.GL_RENDERER" in eglSurface)
    }

    /** Nguồn GLSL **chỉ** đến từ `:core` — không một dòng shader nào viết ở `:app` (điều kiện để `:core` ghim công thức). */
    @Test fun `khong co dong shader nao viet o app`() {
        assertTrue("CameraDewarpShader.program()" in glProgram, "cặp shader lấy từ `:core`")
        listOf(renderer, glProgram, eglSurface, layer, bindings).forEach { src ->
            listOf("gl_FragColor", "samplerExternalOES", "varying ", "precision highp", "void main()").forEach {
                assertFalse(it in src, "GLSL `$it` viết ở `:app` ⇒ hai bản công thức sẽ trôi khỏi nhau")
            }
        }
        // Mười uniform gán theo TÊN, và lượt kiểm cuối đọc danh sách từ `:core`.
        val locate = SourceRoots.body(bindings, "fun locate(")
        CameraDewarpShader.UNIFORMS.forEach {
            assertTrue("\"$it\"" in locate, "uniform `$it` chưa được lấy vị trí ⇒ nhận 0 và khung sai IM LẶNG")
        }
        assertTrue("CameraDewarpShader.UNIFORMS.filter" in locate, "lượt kiểm cuối phải đọc danh sách từ `:core`")
        // `transpose = false` — ma trận của SurfaceTexture đã là column-major (AOSP SurfaceTexture.java:308-309).
        assertTrue("glUniformMatrix4fv(uTexMatrix, 1, false, texMatrix, 0)" in bindings, "KHÔNG chuyển vị")
        assertTrue("bindings.locate(program)" in SourceRoots.body(renderer, "private fun setup("), "locate phải có call site")
    }

    /** Cỡ ảnh nguồn không hardcode ở `:app` — và `setDefaultBufferSize` phải kẹp theo trần texture đã đo (Q13). */
    @Test fun `co anh nguon khong hardcode, co xin phai kep theo tran texture`() {
        listOf(renderer, eglSurface, glProgram, layer, synth).forEach { src ->
            listOf("5120", "1280", "960", "720").forEach {
                assertTrue(it !in src, "cỡ ảnh $it gõ cứng trong `:app` — gợi ý ở `CamView.hintW/hintH`")
            }
        }
        val input = SourceRoots.body(renderer, "private fun createInput(")
        assertTrue("setDefaultBufferSize(" in input)
        assertTrue("CameraGlInfo.cap(" in input, "phải kẹp theo GL_MAX_TEXTURE_SIZE đã đo, không xin 5120 rồi chờ đen")
        // Ảnh tổng hợp lấy cỡ từ CHÍNH canvas ⇒ không có phép co giãn nào làm nhiễu phép kiểm hình học.
        assertTrue("canvas.width" in synth && "canvas.height" in synth, "cỡ ảnh sinh = cỡ buffer thật")
        assertTrue("CameraDewarpTestPattern.pano(" in synth, "ảnh sinh lấy từ `:core`, không vẽ tay ở `:app`")
    }

    // ══ (D) BỀ MẶT owner + bề mặt ĐO ═══════════════════════════════════════════════════════════════════════

    /** Chín khoá nắn: mặc định từ `:core`, đọc lạ ⇒ mặc định, và **cả chín** vào danh sách trắng + có `read_back`. */
    @Test fun `bay khoa nan lay mien tu core va vao danh sach trang`() {
        val keys = listOf(
            "camera_dewarp_amount", "camera_dewarp_focal", "camera_dewarp_k",
            "camera_dewarp_scale", "camera_dewarp_cx", "camera_dewarp_cy",
            // 2.75 — dịch CỬA SỔ (xe 27/09). Cùng khuôn sáu khoá trên: miền ở `:core`, ghi qua cầu, đọc lại từ đĩa.
            "camera_dewarp_pan_x", "camera_dewarp_pan_y",
            "camera_gl_texmatrix",
        )
        keys.forEach {
            assertTrue("\"$it\"" in prefs, "khoá $it chưa khai ở PrefsCameraDewarp")
            assertTrue(it in TestBridgeCommands.WRITABLE_PREFS_KEYS, "khoá $it chưa vào danh sách trắng")
            assertTrue("\"$it\" ->" in prefsSet, "prefs_set thiếu nhánh ghi cho $it")
            assertTrue("\"$it\" -> Prefs." in prefsSet, "read_back phải đọc LẠI $it từ nơi lưu bền")
        }
        // Miền hợp lệ lấy từ `:core`, không gõ số ở `:app` (hai bản sao sẽ lệch khi ai đó nới dải).
        listOf("CameraDewarpPrefs.isAmountPct(", "CameraDewarpPrefs.isPct(", "CameraDewarpPrefs.isCenterPct(",
            "CameraDewarpPrefs.isPanPct(").forEach {
            assertTrue(it in prefsSet, "prefs_set phải kiểm miền qua `:core` ($it)")
        }
        // 2.76 · R2: tám núm lấy mặc định từ HỒ SƠ XE (khoá vắng ⇒ `CameraDefaults.of(ctx).x`); các hằng `*_DEFAULT`
        // của `:core` là giá trị của hồ sơ TRUNG TÍNH (`CameraProfileDefaults.NEUTRAL`) — một chỗ, không hai.
        listOf("CameraDefaults.of(ctx).amountPct", "CameraDefaults.of(ctx).focalPct", "CameraDefaults.of(ctx).kPct",
            "CameraDefaults.of(ctx).scalePct", "CameraDefaults.of(ctx).centerXPct", "CameraDefaults.of(ctx).centerYPct",
            "CameraDefaults.of(ctx).panXPct", "CameraDefaults.of(ctx).panYPct", "CameraDewarpPrefs.TEX_MATRIX_DEFAULT").forEach {
            assertTrue(it in prefs, "mặc định phải lấy từ hồ sơ xe / `:core` ($it)")
        }
        val neutral = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/camera/CameraProfileDefaults.kt")
        listOf("CameraDewarpPrefs.AMOUNT_DEFAULT", "CameraDewarpPrefs.PCT_DEFAULT", "CameraDewarpPrefs.CENTER_DEFAULT",
            "CameraDewarpPrefs.PAN_DEFAULT").forEach {
            assertTrue(it in neutral, "hồ sơ trung tính phải lấy $it — không chép số")
        }
        // Đúng MỘT accessor cho tệp prefs (không mở cửa thứ hai vào cùng chỗ lưu).
        assertFalse("getSharedPreferences" in prefs, "phải dùng lại `autoPrefs` của PrefsAutomation")
    }

    /**
     * ═══ 2.77 — tám núm nắn + công tắc `uTexMatrix` KHÔNG còn hàng nào trong Cài đặt ═══════════════════════════
     *
     * Owner trên xe 27/09: *"bỏ hết phần nâng cao đi"*. Bộ `F 55 · K 100 · S 130 · tâm 0,0 · dịch 0,0` owner đã duyệt
     * bằng mắt nay là mặc định hồ sơ Seal, và người lái chỉ còn **một ô tích** *Nắn hình* (`camera_dewarp_amount`).
     * Bài này canh **sự vắng mặt**: chín khoá còn ghi/đọc qua `prefs_set` (đường chẩn đoán CLAUDE.md §15 bước 2),
     * nhưng không một hàng nào trên màn — và chữ của chúng đã xoá khỏi CẢ hai tệp chữ (i18n mồ côi).
     */
    @Test fun `tam num nan khong con hang nao trong Cai dat`() {
        val gone = listOf(
            "kachi_camera_dewarp_cx", "kachi_camera_dewarp_cy", "kachi_camera_dewarp_k",
            "kachi_camera_dewarp_focal", "kachi_camera_dewarp_scale", "kachi_camera_dewarp_amount",
            "kachi_camera_dewarp_pan_x", "kachi_camera_dewarp_pan_y",
            "kachi_camera_dewarp_sub", "kachi_camera_dewarp_note",
            "kachi_camera_gl_texmatrix_title", "kachi_camera_gl_texmatrix_sub", "kachi_camera_render_gl",
        )
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        gone.forEach {
            assertFalse(it in settings, "hàng `$it` đã gỡ ở 2.77 — thấy lại là khối nắn đã sống lại")
            assertFalse("\"$it\"" in vi, "chữ VI $it mồ côi")
            assertFalse("\"$it\"" in en, "chữ EN $it mồ côi")
        }
        listOf("private fun cameraDewarp(", "private fun knob(", "rows.stepperRow(", "CameraDewarpPrefs.PAN_STEP",
            "CameraDewarpPrefs.PCT_STEP", "CameraDewarpPrefs.CENTER_STEP", "bridge.cameraGlTexMatrix()")
            .forEach { assertFalse(it in settings, "`$it` đã gỡ ở 2.77") }
        // 2.92 · CAMERA-FULL-VIEW: ô tích *Nắn hình* nhường chỗ hàng chip *Kiểu hình* + thanh *Thu phóng* (hàng mới + chữ
        // canh ở `CameraSettingsIaWiringContractTest`); ở đây chỉ canh ô tích KHÔNG sống lại.
        val user = SourceRoots.body(settings, "private fun cameraUser(")
        assertFalse("CameraDewarpPrefs.AMOUNT_MAX" in user, "ô tích Nắn hình đã gỡ ở 2.92 — thấy lại là hai hàng chồng nghĩa")
        assertTrue("bridge.setCameraProjection(v)" in user && "bridge.setCameraZoom(" in user, "chip kiểu hình + thanh thu phóng")
        // Chín khoá vẫn ghi được qua cầu kiểm thử — gỡ UI KHÔNG được biến thành gỡ đường chẩn đoán.
        listOf("camera_dewarp_cx", "camera_dewarp_cy", "camera_dewarp_k", "camera_dewarp_focal", "camera_dewarp_scale",
            "camera_dewarp_pan_x", "camera_dewarp_pan_y", "camera_gl_texmatrix").forEach {
            assertTrue(it in TestBridgeCommands.WRITABLE_PREFS_KEYS && "\"$it\" ->" in prefsSet, "$it phải còn ghi được để dò trên xe")
        }
    }

    /**
     * `camera_frame` phải nói **ảnh này là khung gì** và có đường chụp khung **THÔ**.
     *
     * Không có `content` thì một PNG đã nắn trông y như một PNG thô, và mọi phép đo bán kính/tâm vòng ảnh sẽ chạy trên
     * ảnh đã bị biến đổi bởi chính bộ tham số đang muốn chốt — một vòng tự xác nhận (CLAUDE.md §2).
     */
    @Test fun `camera_frame noi content va co duong chup THO`() {
        assertTrue("\"content\" to content" in frameCmd, "lời đáp phải nói khung này đã nắn hay chưa")
        assertTrue("ARG_RAW" in frameCmd && "\"raw\"" in frameCmd, "cờ `--es name raw`")
        assertTrue("hooks.cameraFrameRaw(" in frameCmd, "đường chụp thô phải có call site")
        assertTrue("hooks.cameraFrame(0, 0)" in frameCmd, "ngữ cảnh đọc cùng nhịp, KHÔNG cấp phát bitmap nào")
        listOf("\"gl\" to shot.glInfo", "\"gl_stats\" to shot.glStats").forEach {
            assertTrue(it in frameCmd, "lời đáp phải mang $it (RE §7 Q13 + nhịp bỏ khung)")
        }
        // Controller ĐO đường đang treo, không tra pref.
        val grab = SourceRoots.body(controller, "    fun grabFrame(")
        assertTrue("CameraSignalPolicy.rotatesInShader(overlay.renderPath())" in grab, "ĐO tầng vẽ, không tra pref")
        assertTrue("CONTENT_DEWARPED" in grab && "CONTENT_RAW" in grab)
        assertTrue("overlay.grabRawFrame(" in SourceRoots.body(controller, "    fun grabRawFrame("))
        // `glReadPixels` trả hàng DƯỚI trước ⇒ phải lật, nếu không PNG ngược mà mắt không nhận ra.
        assertTrue("argbFlipped(" in glProgram && "h - 1 - y" in glProgram, "phải lật hàng của glReadPixels")
    }

    /** `camera_synth` là một lệnh THẬT: có trong bảng `:core`, có nhánh dispatch, có móc, có đường vào producer. */
    @Test fun `camera_synth la lenh that va noi day den producer`() {
        assertTrue(TestBridgeCommands.CAMERA_SYNTH in TestBridgeCommands.NAMES, "chưa khai trong SPECS ⇒ unknown_cmd")
        assertTrue("TestBridgeCommands.CAMERA_SYNTH -> TestBridgeSynth.run(" in bridge, "thiếu nhánh dispatch")
        val hooks = app("launcher/testbridge/TestBridgeHooks.kt")
        assertTrue("cameraSynth = { on, file ->" in hooks, "móc phải nối tới controller thật, KÈM tên tệp (2.75)")
        assertTrue("cameraFrameRaw = { w, h ->" in hooks)
        assertTrue("hooks.cameraSynth(" in app("launcher/testbridge/TestBridgeSynth.kt"))
        // Bơm ảnh tổng hợp ⇒ KHÔNG mở HAL (hai producer trên một BufferQueue = ảnh chắp vá).
        val tick = SourceRoots.body(controller, "private fun openSession(")
        assertTrue("if (!synth) avm.open(" in tick, "đang bơm ảnh tổng hợp thì không được mở HAL")
        assertTrue("synthOn = synth," in tick)
        assertTrue("startSynth(input, synthFile)" in layer, "producer gắn vào ĐÚNG Surface mà HAL lẽ ra dùng")
        // 2.75 — đường "khung THẬT từ xe": tên tệp phải được LỌC ở biên (CLAUDE.md §4.1 đầu vào → đường tệp) và
        // thư mục gốc phải do tầng có Context ghép, không để tầng dưới tự nối chuỗi.
        val synthCmd = app("launcher/testbridge/TestBridgeSynth.kt")
        assertTrue("fun safeName(" in synthCmd && "java.io.File(raw.trim()).name" in synthCmd,
            "tên tệp phải bỏ mọi thành phần thư mục")
        assertTrue("name == \"..\"" in synthCmd && "name.startsWith(\".\")" in synthCmd,
            "phải từ chối `..` và tên bắt đầu bằng dấu chấm")
        assertTrue("getExternalFilesDir(null)" in SourceRoots.body(controller, "    fun setSynth("),
            "thư mục gốc ghép ở controller (nơi có Context), không ở tầng vẽ")
        // Cờ chỉ trong RAM — một cờ lưu bền sống qua nổ máy sẽ cho owner một ảnh vẽ sẵn thay camera gương.
        assertFalse("camera_synth" in prefs, "cờ synth KHÔNG được là pref lưu bền")
        assertTrue("private var synth = false" in controller && "@Volatile" in controller)
    }

    /**
     * ═══ [P0 · xe 27/09] Đổi BÊN phải DỠ phiên cũ, và dỡ **HAL trước, cửa sổ sau** ══════════════════════════════
     *
     * Bệnh đã có thật: 2.74 rẽ LEFT → RIGHT mà không gọi một lời dỡ nào cho phiên LEFT ⇒ `overlay.show` huỷ
     * `SurfaceTexture` của LEFT trong khi HAL vẫn giữ `Surface` ấy ⇒ `E/BufferQueueProducer … BufferQueue has been
     * abandoned` **16 dòng/giây, không bao giờ dứt** ([ĐO] 55 004 dòng trong một buổi, bắt đầu đúng 87 ms sau lượt
     * rẽ). Bài này ghim cả ba điều kiện để nó không quay lại:
     *
     *  1. (2.93: một cửa `show(next)` cho MỌI lượt đổi camera, kể cả theo yêu cầu) `closeSession()` khi đổi camera ≠ null;
     *  2. lượt đổi camera **không** chạm máy trạng thái (`hold.reset()` ⇒ overlay sẽ chớp giữa chuyến);
     *  3. trong `closeSession()`, `avm.close()` đứng **TRƯỚC** `overlay.hide()` — đảo lại là dựng lại đúng con bọ.
     */
    @Test fun `do phien cu TRUOC khi mo phien moi`() {
        val show = SourceRoots.body(controller, "private fun show(")
        assertTrue(
            "if (prev != null && next != null) closeSession(keepPano = true)" in show,
            "đổi camera phải dỡ phiên cũ — nếu không, HAL giữ một BufferQueue đã bị bỏ và dequeue mãi",
        )
        assertTrue(show.indexOf("closeSession(keepPano = true)") < show.indexOf("openSession(next)"), "dỡ TRƯỚC rồi mới dựng")
        assertTrue("openSession(" !in SourceRoots.body(controller, "private fun tickMain("), "xi-nhan đi qua cửa show() chung")
        assertFalse("hold." in show || "if (next == prev) return" !in show, "show(): không đụng HOLD; cùng camera ⇒ không dựng lại")
        val close = SourceRoots.body(controller, "private fun closeSession(")
        val iAvm = close.indexOf("avm.close()")
        val iHal = close.indexOf("hal.close()")
        val iHide = close.indexOf("overlay.hide()")
        assertTrue(iAvm in 1..<iHide, "avm.close() phải đứng TRƯỚC overlay.hide() (HAL trước, cửa sổ sau)")
        assertTrue(iHal in 1..<iHide, "hal.close() phải đứng TRƯỚC overlay.hide()")
        // Đổi bên KHÔNG được tắt thiết bị panorama: một vòng WORK_OFF → WORK_ON giữa hai lượt rẽ là hành vi chưa
        // ai đo trên xe, mà con bọ nằm ở AVMCamera chứ không ở thiết bị panorama.
        assertTrue("if (!keepPano) hal.close()" in close, "đổi bên chỉ dỡ AVMCamera, giữ panorama đang bật")
        assertTrue("closeSession()" in show, "show(null) dùng lại closeSession() — chuỗi dỡ chỉ một bản")
        assertEquals(1, Regex("""avm\.close\(\)""").findAll(controller).count(), "chuỗi dỡ chỉ được có MỘT bản")
        assertFalse("hold.reset()" in close, "closeSession() không được đụng HOLD — đó là việc của dropBlinker()")
        assertTrue("hold.reset()" in SourceRoots.body(controller, "private fun dropBlinker("), "hạ xi-nhan mới xoá nền HOLD")

        // `rmPreviewSurface` (móc đo) phải nằm GIỮA stopPreview và close: gọi sau close thì HAL từ chối
        // ([ĐO] `rc=false` bốn lượt trên xe 27/09) ⇒ lời gọi ấy không đo được gì.
        val avmClose = SourceRoots.body(app("launcher/camera/AvmCamera.kt"), "    fun close(")
        val iStop = avmClose.indexOf("stopPreview")
        val iRm = avmClose.indexOf("rmPreviewSurface")
        val iHalClose = avmClose.indexOf("getDeclaredMethod(\"close\")")
        assertTrue(iStop in 0..<iRm, "rmPreviewSurface phải sau stopPreview")
        assertTrue(iRm in 0..<iHalClose, "rmPreviewSurface phải TRƯỚC close (2.74 gọi sau ⇒ rc=false)")

        // [CAM-B4] Trần nhịp vẽ: `updateTexImage` LUÔN chạy (không nhận khung ⇒ producer nghẽn), chỉ bỏ phần ĐẮT.
        val draw = SourceRoots.body(renderer, "private fun drawFrame(")
        val iUpd = draw.indexOf("st.updateTexImage()")
        val iCap = draw.indexOf("MIN_PAINT_GAP_MS")
        val iPaint = draw.indexOf("paint(viewW, viewH)")
        assertTrue(iUpd in 0..<iCap, "trần nhịp phải đứng SAU updateTexImage — bỏ khung là mời producer nghẽn")
        assertTrue(iCap in 0..<iPaint, "và TRƯỚC paint/swap — đó mới là phần đắt")
        assertTrue("CameraSignalPolicy.renderMinGapMs()" in renderer, "con số trần lấy từ `:core`, không gõ ở `:app`")
        assertTrue("fpsSkip" in renderer && "fpsCap=" in renderer, "phải đếm + in ra để chốt nhịp thật trên xe")

        // Mã trả về của `setPanoOperation(OFF)` phải được giải mã trong nhật ký — `-2147482645` đọc bằng mắt là vô
        // nghĩa, `0x800003EB` thì nói ngay là bit lỗi + mã 1003.
        val pano = app("launcher/camera/PanoramaHal.kt")
        assertTrue("decodeOp(op)" in pano && "0x%08X" in pano, "mã HAL phải ghi kèm hex + cờ lỗi")
    }
}
