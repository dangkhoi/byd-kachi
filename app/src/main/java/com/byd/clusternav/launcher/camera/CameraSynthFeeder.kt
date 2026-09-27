package com.byd.clusternav.launcher.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface

/**
 * ═══ PRODUCER GIẢ — bơm ảnh fisheye TỔNG HỢP vào đúng `Surface` mà HAL lẽ ra bơm vào ════════════════════════════
 *
 * R8-B (2.74), lệnh cầu kiểm thử `camera_synth` ([com.byd.clusternav.launcher.testbridge.TestBridgeCommands.CAMERA_SYNTH]).
 *
 * ## Vì sao nó tồn tại (CLAUDE.md §14 · §8)
 * Máy ảo **không có** `android.hardware.AVMCamera` ⇒ không một khung nào đi qua đường GL được ⇒ cả tầng nắn (EGL,
 * shader, 10 uniform, `eglSwapBuffers`, teardown) sẽ lên xe mà **chưa từng vẽ một pixel**. Đó đúng là *"compile xanh
 * không có nghĩa là code chạy"*. Lớp này thay **đúng một mắt** của chuỗi — cái producer — và để **tất cả** phần còn
 * lại là đường thật: cùng `Surface`, cùng `SurfaceTexture`, cùng texture OES, cùng program, cùng cửa ra.
 *
 * `Surface.lockCanvas` là đường hợp pháp cho việc này, không phải một mẹo: [ĐO] AOSP `android-10.0.0_r47`
 * `graphics/java/android/graphics/SurfaceTexture.java:232-237` — `setDefaultBufferSize` có mặt *"to set the image size
 * when producing images with Canvas (via Surface.lockCanvas), or OpenGL ES"*.
 *
 * ## 2.75 — bơm được một PNG **THẬT** (`camera_synth --es name file:<tên>`)
 * Chính vì đoạn ⚠ dưới đây, từ 2.75 lớp này nhận thêm một đường tệp: đẩy một khung `5120×960` **chụp từ xe** vào
 * `getExternalFilesDir(null)` rồi bơm nó qua **đúng** đường GL ấy ⇒ lượt kiểm chạy trên ống kính THẬT, không chỉ
 * trên ảnh do chính mô hình vẽ ra. Tệp hỏng/thiếu ⇒ **rơi về ảnh sinh** và ghi một dòng `logcat`: một lượt đo
 * không được biến mất im lặng chỉ vì gõ sai tên tệp.
 *
 * Ảnh tệp đi qua đúng phép `drawBitmap(src, dst)` của ảnh sinh — nhưng ⚠ ở đây `dst` là **trọn buffer**, nên nếu tỉ
 * lệ tệp khác tỉ lệ buffer thì đây là một lượt **căng đầy (anamorphic)**, không phải phóng đều. Đó là cái ĐÚNG cho
 * hai ca đang dùng: khung `5120×960` chụp từ xe ⇒ trùng khít (hệ số 1); khung một camera `1280×960` ⇒ kéo ngang
 * ×[CameraPanoCrop.STRIPS], **đúng** thứ HAL làm ở [CameraSignalPolicy.SOURCE_CHANNEL]. Lập luận *"co giãn đẳng
 * hướng giao hoán với phép nắn"* dưới đây chỉ áp cho ảnh SINH (nơi [capped] giữ tỉ lệ), không cho ảnh tệp.
 *
 * ## ⚠ Ảnh này KHÔNG nói gì về ống kính thật
 * Nó được sinh bằng **chính mô hình đang kiểm** ([CameraDewarpTestPattern] dùng [CameraDewarp.idealEquidistantSource]),
 * nên nó chứng minh *cài đặt* đúng (GLSL khớp Kotlin, uniform vào đúng chỗ, đường thẳng thành thẳng) và **không** nói
 * `K`/`F` thật của camera BYD là bao nhiêu (`camera-dewarp-math.md` §6 mục 5). Vì thế đây là lệnh của **cầu kiểm
 * thử**, không phải một chip trong Cài đặt: nó không được phép trở thành một thứ owner bật trên xe rồi tưởng là camera.
 *
 * ## Cỡ buffer lấy từ CHÍNH canvas; cỡ ẢNH SINH thì kẹp theo HEAP
 * Lượt `lockCanvas` đầu tiên nói cỡ buffer THẬT (do `setDefaultBufferSize` của [CameraGlRenderer] đặt, đã kẹp theo
 * `GL_MAX_TEXTURE_SIZE` — RE §7 Q13). Nhưng **sinh ảnh đúng cỡ ấy thì hết bộ nhớ**: buffer của ảnh 4-in-1 là
 * `5120×960` ⇒ `IntArray` 19,6 MB **cộng** `Bitmap` 19,6 MB sống cùng lúc, trong khi Kachi không khai `largeHeap` và
 * `dalvik.vm.heapgrowthlimit` của máy ảo này [ĐO] là **48 MB**. Nên ảnh sinh ở cỡ nhỏ hơn ([budgetPixels]) rồi
 * `drawBitmap(src, dst)` **phóng ĐỀU** lên cỡ buffer.
 *
 * Phóng đều **không** làm nhiễu phép kiểm hình học: cả phép nắn, `uSrcRect` và `uCenter` đều làm việc trong toạ độ
 * **chuẩn hoá**, nên một phép co giãn đẳng hướng của toàn ảnh giao hoán với chúng — đường thẳng vẫn thẳng, vòng ảnh
 * vẫn tròn, tỉ lệ `16:3` giữ nguyên (cỡ chọn luôn là bội của [CameraPanoCrop.STRIPS] để bốn dải chia đúng). Cái mất
 * duy nhất là **độ nét** của nét vẽ, tức một lượt lọc thêm — nói ra trong `logcat` bằng cả hai con số.
 */
internal class CameraSynthFeeder(private val surface: Surface, private val file: String = "") {

    private val thread = HandlerThread(THREAD)
    private var handler: Handler? = null

    @Volatile
    private var running = false

    private var bitmap: Bitmap? = null
    private var builtW = 0
    private var builtH = 0

    /** Cỡ khung đã sinh, `"—"` khi chưa có lượt nào — cho lời đáp cầu kiểm thử. */
    @Volatile
    var size: String = "—"
        private set

    @Volatile
    var frames = 0L
        private set

    private val tick = Runnable { pump() }

    /** Vùng nguồn của [Bitmap] — một thực thể, đổi nội dung khi sinh lại (không cấp phát mỗi khung). */
    private val src = Rect()

    /** Vùng đích — cùng lẽ [src]: một thực thể, không `Rect()` mới mỗi khung. */
    private val dst = Rect()

    /** `filterBitmap = false`: ảnh sinh và buffer cùng cỡ ⇒ không có phép lọc nào để làm; bật là đắt vô ích. */
    private val paint = Paint().apply { isFilterBitmap = false }

    fun start() {
        if (running) return
        running = true
        thread.start()
        handler = Handler(thread.looper).also { it.post(tick) }
        Log.i(PanoramaHal.TAG, "GL ảnh tổng hợp: bắt đầu bơm ~${1000 / PERIOD_MS} fps")
    }

    /**
     * Dừng bơm và nhả ảnh. **Chặn** lượt dựng ảnh đang chạy bằng `quitSafely` + `join` ngắn: một `Bitmap` 20 MB bị
     * `recycle` dưới chân một `drawBitmap` đang chạy là `IllegalStateException` trong một luồng không ai bắt.
     */
    fun stop() {
        if (!running) return
        running = false
        handler?.removeCallbacks(tick)
        handler = null
        runCatching { thread.quitSafely() }
        runCatching { thread.join(JOIN_MS) }
        runCatching { bitmap?.recycle() }
        bitmap = null
        Log.i(PanoramaHal.TAG, "GL ảnh tổng hợp: dừng, đã bơm $frames khung")
    }

    private fun pump() {
        if (!running) return
        runCatching { draw() }.onFailure { Log.w(PanoramaHal.TAG, "GL bơm ảnh hỏng: ${it.javaClass.simpleName} ${it.message}") }
        handler?.postDelayed(tick, PERIOD_MS)
    }

    private fun draw() {
        // `lockCanvas(null)` = khoá TRỌN buffer. Một `Rect` con sẽ để phần ngoài mang nội dung khung TRƯỚC, và trên
        // một `BufferQueue` nhiều slot thì "khung trước" là một buffer bất kỳ ⇒ ảnh chắp vá không ai giải thích được.
        val canvas = surface.lockCanvas(null) ?: return
        try {
            val w = canvas.width
            val h = canvas.height
            if (w <= 0 || h <= 0) return
            val bmp = ensure(w, h) ?: return
            canvas.drawBitmap(bmp, src, dst, paint)
        } finally {
            runCatching { surface.unlockCanvasAndPost(canvas) }
        }
        frames++
    }

    /**
     * Ảnh cho buffer cỡ [bufW]×[bufH], sinh lại khi cỡ buffer đổi. `null` = không dựng được (hết bộ nhớ).
     *
     * Cỡ ảnh sinh = [bufW]×[bufH] **kẹp theo [budgetPixels]**, giữ đúng tỉ lệ và luôn là bội của
     * [CameraPanoCrop.STRIPS] — xem KDoc lớp về vì sao phóng đều không làm nhiễu phép kiểm.
     */
    private fun ensure(bufW: Int, bufH: Int): Bitmap? {
        val cached = bitmap
        if (cached != null && !cached.isRecycled && builtW == bufW && builtH == bufH) return cached
        runCatching { cached?.recycle() }
        bitmap = null
        val began = SystemClock.elapsedRealtime()
        fromFile(bufW, bufH)?.let { return it }
        val (w, h) = capped(bufW, bufH)
        val built = runCatching {
            val spec = CameraDewarpTestPattern.Spec(width = w, height = h, strips = CameraPanoCrop.STRIPS)
            val px = CameraDewarpTestPattern.pano(spec)
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }.onFailure {
            Log.w(PanoramaHal.TAG, "GL sinh ảnh ${w}x$h hỏng: ${it.javaClass.simpleName}")
        }.getOrNull() ?: return null
        builtW = bufW
        builtH = bufH
        bitmap = built
        size = if (w == bufW && h == bufH) "${w}x$h" else "${w}x$h→${bufW}x$bufH"
        src.set(0, 0, w, h)
        dst.set(0, 0, bufW, bufH)
        Log.i(
            PanoramaHal.TAG,
            "GL ảnh tổng hợp $size (${CameraPanoCrop.STRIPS} dải) dựng trong" +
                " ${SystemClock.elapsedRealtime() - began} ms, ngân sách ${budgetPixels()} px",
        )
        return built
    }

    /**
     * Nạp PNG của [file] (nếu có) làm ảnh nguồn — đường *"khung THẬT từ xe"* của 2.75.
     *
     * `inSampleSize` chọn theo **cùng [budgetPixels]** với ảnh sinh: một khung `5120×960` giải mã nguyên cỡ là
     * `Bitmap` 19,6 MB trên một heap [ĐO] 48 MB. Giải mã hai lượt (`inJustDecodeBounds` rồi thật) vì cỡ tệp là
     * thứ duy nhất chưa biết trước.
     *
     * `null` ⇒ không có tệp / không giải mã được ⇒ chỗ gọi dùng ảnh sinh (đã ghi `logcat`).
     */
    private fun fromFile(bufW: Int, bufH: Int): Bitmap? {
        if (file.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(PanoramaHal.TAG, "GL ảnh tệp «$file» không đọc được cỡ ⇒ dùng ảnh sinh")
            return null
        }
        var sample = 1
        val budget = budgetPixels()
        while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > budget) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = runCatching { BitmapFactory.decodeFile(file, opts) }
            .onFailure { Log.w(PanoramaHal.TAG, "GL giải mã «$file» hỏng: ${it.javaClass.simpleName}") }
            .getOrNull()
        if (bmp == null) {
            Log.w(PanoramaHal.TAG, "GL ảnh tệp «$file» giải mã ra null ⇒ dùng ảnh sinh")
            return null
        }
        builtW = bufW
        builtH = bufH
        bitmap = bmp
        // ASCII: trường này đi thẳng vào lời đáp JSON của cầu kiểm thử và vào `logcat`, không phải chữ trên màn —
        // `LauncherI18nContractTest` bắt mọi chuỗi có dấu trong tầng vẽ, và ở đây quy tắc ấy đúng.
        size = "png ${bmp.width}x${bmp.height}→${bufW}x$bufH (1/$sample)"
        src.set(0, 0, bmp.width, bmp.height)
        dst.set(0, 0, bufW, bufH)
        Log.i(PanoramaHal.TAG, "GL ảnh TỆP $size từ $file")
        return bmp
    }

    /**
     * Cỡ ảnh sinh: không vượt buffer, không vượt [budgetPixels], **giữ tỉ lệ**, bề ngang là bội của
     * [CameraPanoCrop.STRIPS].
     *
     * Co theo `sqrt` của tỉ số diện tích ⇒ hai trục cùng một hệ số ⇒ tỉ lệ giữ nguyên (cùng phép mà
     * `TestBridgeFrameSize` dùng cho trần diện tích, và cùng lý do: cắt một cạnh sẽ làm méo đúng thứ đang đo).
     */
    private fun capped(bufW: Int, bufH: Int): Pair<Int, Int> {
        val budget = budgetPixels()
        val area = bufW.toLong() * bufH.toLong()
        if (area <= budget) return bufW to bufH
        val scale = Math.sqrt(budget.toDouble() / area.toDouble())
        val strips = CameraPanoCrop.STRIPS
        val w = ((bufW * scale).toInt() / strips * strips).coerceAtLeast(strips)
        val h = (bufH.toLong() * w / bufW).toInt().coerceAtLeast(1)
        return w to h
    }

    /**
     * Trần số điểm ảnh của ảnh sinh, suy từ **heap thật của tiến trình**.
     *
     * `IntArray` + `Bitmap` sống **cùng lúc** ⇒ 8 byte/px; lấy `1/4` số px mà cả heap chứa nổi để còn chỗ cho phần
     * còn lại của launcher (cây view, mô hình giọng nói nếu đang nạp, bitmap hình nền). Không gõ một con số MB cứng:
     * máy ảo ở đây [ĐO] `heapgrowthlimit = 48m` còn đầu xe có thể khác, và một hằng sẽ sai ở đúng máy kia.
     */
    private fun budgetPixels(): Int =
        ((Runtime.getRuntime().maxMemory() / BYTES_PER_PIXEL) / HEAP_SHARE)
            .coerceIn(MIN_PIXELS, MAX_PIXELS).toInt()

    private companion object {
        const val THREAD = "kachi-camsynth"

        /**
         * ~5 fps. Không chọn 15 fps như HAL thật: mục đích là **kiểm hình học**, không kiểm nhịp — và mỗi khung ở đây
         * là một lượt `lockCanvas` + `drawBitmap` 20 MB **trên CPU** (HAL thật không chép gì), tức 15 fps sẽ làm nghẽn
         * chính cái máy ảo đang đo. Nhịp bỏ khung của [CameraGlRenderer] đo được bằng ảnh thật trên xe, không ở đây.
         */
        const val PERIOD_MS = 200L

        const val JOIN_MS = 500L

        /** `IntArray` (4) + `Bitmap` (4) sống cùng lúc. */
        const val BYTES_PER_PIXEL = 8L

        /** Chỉ dùng `1/4` heap cho hai bản ảnh — phần còn lại là của launcher. */
        const val HEAP_SHARE = 4L

        /** Sàn: dưới mức này nét vẽ mảnh hơn một pixel và phép kiểm không đọc được gì. */
        const val MIN_PIXELS = 240_000L

        /** Trần: `5120×960` của ảnh 4-in-1 — không bao giờ sinh lớn hơn buffer thật. */
        const val MAX_PIXELS = 5_000_000L
    }
}
