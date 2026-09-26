package com.byd.clusternav.launcher.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View

/**
 * ═══ LỚP VIDEO trong cửa sổ overlay — ba đường, một vai: *"cái gì hiện khung, và dựng nó thế nào"* ══════════════
 *
 * Tách khỏi [CameraOverlayView] ở 2.74 (R8-B): tệp đó lo **cửa sổ** (vùng cho phép, tỉ lệ khung, góc màn, bo góc,
 * hình TRÒN, nhãn) còn tệp này lo **lớp con vẽ video**. Hai vai đã khác nhau từ 2.72; đường GL làm nó rõ hẳn vì nó
 * thêm một luồng vẽ + một vòng đời riêng — và làm tệp kia vượt trần 500 dòng của CLAUDE.md §4.1 nếu nhét thêm vào.
 *
 * ## Ba đường — [CameraSignalPolicy.RENDERS]
 * | Mã | Lớp | Cắt vùng + xoay bằng gì | Bo góc / hình TRÒN | `getBitmap` |
 * |---|---|---|---|---|
 * | `TV` **mặc định** | `TextureView` | `setTransform` ([CameraOverlayTransform]) | ăn thật | khung **GỐC** (ma trận không vào ảnh) |
 * | `SV` | `SurfaceView` | cỡ + lề ÂM ([CameraOverlayFrame.stretch]) + HAL xoay | [ĐOÁN] không ăn | **không có** |
 * | `GL` | `TextureView` + [CameraGlRenderer] | **shader** (`uSrcRect`/`uRotation`) | ăn thật | khung **ĐÃ NẮN** |
 *
 * ## ⚠ Đường GL: `setTransform` PHẢI là ma trận đơn vị
 * Shader đã cắt vùng và xoay. Gọi thêm `setTransform` là làm **hai lần** — và lượt thứ hai còn cắt trên một khung
 * *đã* cắt, nên kết quả không phải "xoay 180" mà là một mảnh vụn của dải. Vì thế [create] **không gọi**
 * [applyTransform] ở nhánh GL, và bài canh `setTransform khong chay o duong GL` ghim điều đó bằng cách đọc thân hàm.
 *
 * ## Đường khung hình không có việc nặng (CLOSE-14)
 * `onSurfaceTextureUpdated` (đường `TV`) và `surfaceChanged` (đường `SV`) để **TRỐNG**: mỗi khung 15 fps đi qua đó.
 * Đường `GL` không dùng `onSurfaceTextureUpdated` chút nào — nó nghe `SurfaceTexture.OnFrameAvailableListener` của
 * **`SurfaceTexture` VÀO** (một `BufferQueue` khác hẳn), trên luồng vẽ riêng.
 */
internal class CameraVideoLayer private constructor(
    /** Lớp con đã dựng — thứ [CameraOverlayView] `addView` vào cửa sổ. */
    val view: View,
    /** Luồng vẽ GL, `null` ở hai đường kia. */
    private val renderer: CameraGlRenderer?,
    /** Producer giả của `camera_synth`, `null` khi không bật. */
    private var synth: CameraSynthFeeder?,
) {

    /** `frames=N busySkip=M` của luồng vẽ, rỗng khi không phải đường GL. Chỉ ĐỌC — cho lời đáp `camera_frame`. */
    fun glStats(): String = renderer?.stats().orEmpty()

    /** Cỡ ảnh tổng hợp đang bơm, rỗng khi không bật `camera_synth`. Chỉ ĐỌC. */
    fun synthSize(): String = synth?.size.orEmpty()

    /** Chụp một khung **THÔ** qua FBO — chỉ đường GL làm được, xem KDoc [CameraGlRenderer.grabRaw]. */
    fun grabRaw(w: Int, h: Int): IntArray? = renderer?.grabRaw(w, h)

    /**
     * Dỡ lớp. Thứ tự: **producer trước** (ảnh tổng hợp), **consumer sau** (luồng vẽ + EGL) — xem KDoc
     * [CameraGlRenderer.stop] về vì sao ngược lại là huỷ một `BufferQueue` mà đầu kia còn giữ.
     *
     * Không chạm HAL: `AVMCamera.stopPreview`/`close`/`rmPreviewSurface` đã chạy **trước** ở
     * `CameraSignalController.stop()`, và thứ tự đó là đường đã chạy hiện trường ⇒ CLAUDE.md §6 cấm đảo.
     */
    fun release() {
        runCatching { synth?.stop() }
        synth = null
        runCatching { renderer?.stop() }
    }

    companion object {

        /**
         * Dựng lớp video cho [render].
         *
         * @param gl bộ uniform của đường GL (`null` ⇒ đường GL rơi về `TextureView` thường, có ghi nhật ký).
         * @param synthOn bơm ảnh tổng hợp thay HAL (`camera_synth`) — **chỉ** đường GL, và chỉ khi luồng vẽ lên được.
         * @param onSurfaceReady nhận `Surface` để giao cho `AVMCamera.open`. Gọi trên **main thread** ở cả ba đường
         *   (đường GL chờ luồng vẽ dựng xong rồi mới gọi) ⇒ trình tự mở camera y hệt 2.73.
         */
        fun create(
            ctx: Context,
            render: String,
            crop: FloatArray?,
            rotationDeg: Int,
            gl: CameraGlUniforms?,
            streamW: Int,
            streamH: Int,
            synthOn: Boolean,
            onSurfaceReady: (Surface) -> Unit,
        ): CameraVideoLayer {
            if (!CameraSignalPolicy.usesTextureView(render)) {
                return CameraVideoLayer(surfaceVideo(ctx, onSurfaceReady), null, null)
            }
            if (!CameraSignalPolicy.rotatesInShader(render) || gl == null) {
                if (gl == null && CameraSignalPolicy.rotatesInShader(render)) {
                    Log.w(PanoramaHal.TAG, "GL thiếu bộ uniform ⇒ chạy như TextureView thường (không nắn)")
                }
                return CameraVideoLayer(textureVideo(ctx, crop, rotationDeg, onSurfaceReady), null, null)
            }
            return glVideo(ctx, gl, crop, rotationDeg, streamW, streamH, synthOn, onSurfaceReady)
        }

        /**
         * Đường MẶC ĐỊNH: `TextureView` (cắt vùng + xoay bằng ma trận, bo góc ăn thật).
         *
         * `AVMCamera.addPreviewSurface` nhận `Surface` dựng từ `SurfaceTexture` của nó. `onSurfaceTextureUpdated` để
         * TRỐNG — xem ⚠ ở KDoc lớp về đường khung hình.
         */
        private fun textureVideo(
            ctx: Context,
            crop: FloatArray?,
            rotationDeg: Int,
            onSurfaceReady: (Surface) -> Unit,
        ): View {
            return TextureView(ctx).apply {
                isOpaque = true
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w2: Int, h2: Int) {
                        applyTransform(this@apply, w2, h2, crop, rotationDeg)
                        runCatching { onSurfaceReady(Surface(st)) }
                            .onFailure { Log.w(PanoramaHal.TAG, "onSurfaceReady: ${it.message}") }
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w2: Int, h2: Int) {
                        applyTransform(this@apply, w2, h2, crop, rotationDeg)
                    }
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        }

        /**
         * Đường PHỤ (đo L2): `SurfaceView` layer riêng.
         *
         * Không `setTransform` ⇒ cắt vùng làm bằng cỡ + lề âm của chính view này ([CameraOverlayFrame.stretch]), xoay
         * thì chỗ gọi thử nhờ HAL. `setZOrderMediaOverlay(true)`: nằm trên nền bo góc của cửa sổ mà KHÔNG nhảy lên
         * trên toàn bộ cửa sổ (khác `setZOrderOnTop`) ⇒ nhãn *Camera trái/phải* vẫn đọc được.
         */
        private fun surfaceVideo(ctx: Context, onSurfaceReady: (Surface) -> Unit): View {
            return SurfaceView(ctx).apply {
                setZOrderMediaOverlay(true)
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) {
                        runCatching { onSurfaceReady(h.surface) }
                            .onFailure { Log.w(PanoramaHal.TAG, "onSurfaceReady: ${it.message}") }
                    }
                    override fun surfaceChanged(h: SurfaceHolder, format: Int, w2: Int, h2: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) {}
                })
            }
        }

        /**
         * Đường GL: `TextureView` làm **cửa RA** của một luồng vẽ EGL, và `SurfaceTexture` **VÀO** mới là thứ giao
         * cho HAL.
         *
         * Hai `SurfaceTexture` khác nhau, đừng lẫn:
         *  • của `TextureView` (**ra**) — [CameraGlRenderer] dựng `EGLSurface` trên nó;
         *  • do [CameraGlRenderer] tạo (**vào**) — gắn texture OES, `Surface` của nó là thứ `AVMCamera` đổ khung vào.
         *
         * `setTransform` **không** được gọi ở đây (⚠ KDoc lớp) — **trừ** đường RƠI dưới đây. `onSurfaceTextureDestroyed`
         * trả `true` **sau khi** đã dỡ xong luồng vẽ: nền tảng nhả `SurfaceTexture` ngay sau lời trả về ấy, và
         * `eglSurface` của ta còn trỏ vào nó ⇒ trả `true` trước khi dỡ là một `SIGSEGV` trong driver, không phải
         * một dòng log.
         *
         * ## ⚠ ĐƯỜNG RƠI — luồng vẽ không lên được thì cửa sổ vẫn phải CÓ HÌNH
         * [SOÁT Opus 2026-09-27] [CameraGlSurface.create] và [CameraGlRenderer.start] đều hứa bằng KDoc rằng *"chỗ gọi
         * rơi về đường `TextureView` 2.73"* / *"không có màn đen câm"* — nhưng **chỗ gọi chưa từng làm việc đó**: nó
         * ghi một dòng rồi `return`, tức không ai giao `Surface` cho `AVMCamera` và tài xế bật xi-nhan nhìn thấy một
         * ô ĐEN. Đúng họ lỗi CLAUDE.md §8 (`CastShell.evictVd` viết xong mà chưa từng được gọi), và ở đây nó rơi vào
         * lúc tệ nhất: đang chuyển làn.
         *
         * Đường rơi = **đúng** đường `TV` của 2.73 (ma trận crop+xoay + `Surface` của chính `SurfaceTexture` này), nên
         * nó không phải một hành vi mới mà là *"mặc định 2.73"* — hợp R-nf1 của spec. Nó chạy ở **ba** ca: GPU từ chối
         * cấu hình EGL, shader không biên dịch được, và lượt dựng THỨ HAI trên cùng thực thể
         * ([CameraGlRenderer.everStarted]).
         *
         * Không có cắt-xoay HAI lần ở đây: cờ [fellBack] chỉ bật khi `renderer.start()` trả `null`, tức khi **không
         * có** shader nào đang chạy. `CameraGlWiringContractTest.setTransform khong chay o duong GL` ghim đúng vế đó.
         */
        private fun glVideo(
            ctx: Context,
            gl: CameraGlUniforms,
            crop: FloatArray?,
            rotationDeg: Int,
            streamW: Int,
            streamH: Int,
            synthOn: Boolean,
            onSurfaceReady: (Surface) -> Unit,
        ): CameraVideoLayer {
            val renderer = CameraGlRenderer(gl, streamW, streamH)
            val holder = arrayOfNulls<CameraVideoLayer>(1)
            // Lượt này đã rơi về `TextureView` thường chưa (⚠ KDoc trên). Một mảng chứ không phải `var` bắt được:
            // cả hai callback là thành viên của một `object` lồng trong hàm này, nên chúng chia nhau đúng ô này.
            val fellBack = booleanArrayOf(false)
            val tv = TextureView(ctx).apply {
                isOpaque = true
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w2: Int, h2: Int) {
                        val input = renderer.start(st, w2, h2)
                        if (input == null) {
                            fellBack[0] = true
                            Log.w(PanoramaHal.TAG, "GL không dựng được ⇒ RƠI về TextureView 2.73 (xem dòng eglGetError)")
                            applyTransform(this@apply, w2, h2, crop, rotationDeg)
                            runCatching { onSurfaceReady(Surface(st)) }
                                .onFailure { Log.w(PanoramaHal.TAG, "onSurfaceReady: ${it.message}") }
                            return
                        }
                        if (synthOn) holder[0]?.startSynth(input)
                        runCatching { onSurfaceReady(input) }
                            .onFailure { Log.w(PanoramaHal.TAG, "onSurfaceReady: ${it.message}") }
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w2: Int, h2: Int) {
                        // Đã rơi ⇒ cư xử y đường `TV` (áp lại ma trận theo cỡ mới — `onStreamMeasured` đổi cỡ ô video
                        // sau khi HAL trả cỡ ảnh thật). Chưa rơi ⇒ shader lo cắt-xoay, chỉ cần đổi viewport.
                        if (fellBack[0]) applyTransform(this@apply, w2, h2, crop, rotationDeg) else renderer.resize(w2, h2)
                    }
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        holder[0]?.release()
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
            return CameraVideoLayer(tv, renderer, null).also { holder[0] = it }
        }

        /**
         * Ma trận **cắt vùng + xoay** cho `TextureView` của đường `TV`. Phép toán nằm ở `:core`
         * [CameraOverlayTransform] (thuần, có test bằng số); hàm này chỉ dịch 9 số ấy sang
         * [android.graphics.Matrix] và giao cho `setTransform`.
         *
         * `null` trả về từ [CameraOverlayTransform.matrix] ⇒ **không đụng** `setTransform` (y hành vi trước R7).
         * `setValues` (API 1) nhận đúng bố cục row-major mà `:core` dựng — `CameraRotationWiringContractTest` ghim
         * bốn hằng chỉ số của SDK.
         *
         * ⚠ Chỉ chạy ở hai callback ĐỔI CỠ (available / size-changed), **không** mỗi khung ⇒ `Matrix` cấp phát ở đây
         * là vài lần một lượt xi-nhan, không phải 15 lần/giây (CLOSE-14).
         */
        private fun applyTransform(tv: TextureView, vw: Int, vh: Int, crop: FloatArray?, rotationDeg: Int) {
            val values = CameraOverlayTransform.matrix(vw, vh, crop, rotationDeg) ?: return
            tv.setTransform(android.graphics.Matrix().apply { setValues(values) })
        }
    }

    /** Bơm ảnh tổng hợp vào [input] — gọi một lần, ngay sau khi luồng vẽ lên (xem [glVideo]). */
    private fun startSynth(input: Surface) {
        if (synth != null) return
        synth = CameraSynthFeeder(input).also { it.start() }
    }
}
