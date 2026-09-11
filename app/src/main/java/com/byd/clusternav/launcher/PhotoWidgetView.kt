package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

/**
 * WIDGET TRÌNH CHIẾU ẢNH (U4 phần b) — một khung ảnh đặt vào ô, tự đổi ảnh theo chu kỳ.
 *
 * Chạy **độc lập với hình nền**: người dùng có thể muốn một khung ảnh trong ô mà **không** đổi nền màn hình. Cùng
 * đọc thư mục ảnh, nhưng có nhịp và chỉ số riêng.
 *
 * ## ⚠ Vì sao nhịp phải TỰ DỌN
 * Ô có thể bị `removeView` bất cứ lúc nào (đổi bố cục · đổi đơn vị · đổi hồ sơ · dựng lại ô). Nếu nhịp vẫn chạy sau
 * khi ô đã bị tháo thì nó **giữ tham chiếu view + nạp ảnh mãi** — rò rỉ, và tốn bộ nhớ đúng lúc launcher cần nhất.
 * Nên nhịp gắn vào [onAttachedToWindow] / [onDetachedFromWindow]: View bị tháo ⇒ nhịp dừng, ảnh được nhả.
 *
 * Đây là lý do widget này tự giữ nhịp thay vì nhờ nhịp chung của thanh trên: nhịp chung không biết ô nào còn sống.
 */
class PhotoWidgetView(context: Context) : View(context) {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val bg = Paint().apply { color = Color.parseColor(KachiTheme.CARD) }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(KachiTheme.MUT); textAlign = Paint.Align.CENTER
    }
    private val src = Rect()
    private val dst = Rect()

    private var images: List<String> = emptyList()
    private var state = SlideshowState()
    private var intervalSec = Slideshow.DEFAULT_INTERVAL_SEC
    private var bitmap: Bitmap? = null
    private var running = false

    /** Ảnh đang nạp (đường dẫn) — chặn nạp trùng khi nhịp tới trước lúc nạp xong. */
    private var loading: String? = null

    /** Thẻ thế hệ của việc giải mã: lượt cũ về muộn thì bị bỏ + nhả ảnh, không ghi đè lượt mới. */
    private var decodeGen = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            step()
            // Nhịp bằng 1/3 chu kỳ, tối thiểu 5 giây: đủ mịn để đổi đúng hạn mà không thức dậy vô cớ.
            postDelayed(this, tickDelayMs())
        }
    }

    /** Đặt nguồn ảnh + chu kỳ. Gọi lúc dựng ô. */
    fun bind(images: List<String>, intervalSec: Int) {
        this.images = images
        this.intervalSec = intervalSec.coerceAtLeast(1)
        this.state = SlideshowState()
        if (isAttachedToWindow) step(force = true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        step(force = true)
        postDelayed(tick, tickDelayMs())
    }

    /**
     * [SOÁT P2-1] HOME bị app khác che thì ô **không** bị tháo, nên nhịp cũ vẫn chạy: mỗi 15–60 giây một lượt đọc đĩa
     * + giải mã ảnh cho thứ **không ai đang xem**. Lái ba giờ là hàng trăm lượt vô ích. Nhịp của màn chính dừng đúng
     * lúc màn tạm dừng; nhịp của widget nay cũng dừng khi cửa sổ không còn hiện.
     */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            if (!running && isAttachedToWindow) {
                running = true
                postDelayed(tick, tickDelayMs())
            }
        } else {
            running = false
            removeCallbacks(tick)
        }
    }

    private fun tickDelayMs(): Long = (intervalSec * 1000L / 3).coerceAtLeast(5_000L)

    override fun onDetachedFromWindow() {
        // Dừng nhịp + nhả ảnh NGAY khi ô bị tháo — không để nhịp sống lâu hơn ô.
        running = false
        removeCallbacks(tick)
        decodeGen++            // [SOÁT P2-5] huỷ lượt giải mã đang bay: nó về sau khi ô đã tháo thì phải tự nhả ảnh
        loading = null
        bitmap?.recycle()
        bitmap = null
        super.onDetachedFromWindow()
    }

    /**
     * Một nhịp trình chiếu.
     *
     * ## [SOÁT P2-5/P2-6] Hai điều bản trước làm sai, nay sửa
     *  1. **Giải mã ảnh ở thread NỀN.** Bản trước gọi `loadScaled` ngay trong nhịp (thread chính) ⇒ mỗi 5–20 giây
     *     launcher đứng một nhịp để đọc đĩa + giải mã ảnh nhiều megapixel. Hình nền đã chuyển sang thread nền từ
     *     lượt soát trước; widget thì chưa — nay đi cùng một đường.
     *  2. **Không giải mã ở cỡ 1×1.** `onAttachedToWindow` chạy TRƯỚC lượt đo cây view ⇒ `width == 0` ⇒ cỡ cần = 1 ⇒
     *     ảnh bị giảm tối đa rồi kéo lên phủ ô = **vệt màu loang**. Đây đúng lỗi đã vá cho hình nền (lúc đó lấy cỡ
     *     theo màn hình). Ở đây ô nhỏ hơn màn nên **chờ có cỡ thật**: chưa có cỡ thì chỉ đóng mốc, `onSizeChanged`
     *     sẽ nạp.
     */
    private fun step(force: Boolean = false) {
        if (images.isEmpty()) { invalidate(); return }
        val before = state
        state = Slideshow.next(state, images.size, System.currentTimeMillis(), intervalSec)
        if (!force && state.index == before.index && bitmap != null) return
        val path = Slideshow.pick(images, state.index) ?: return
        val reqW = width
        val reqH = height
        if (reqW <= 0 || reqH <= 0) return          // chưa đo xong ⇒ để onSizeChanged nạp ở cỡ THẬT
        if (loading == path) return                 // đang nạp đúng ảnh này rồi
        val gen = ++decodeGen
        loading = path
        DECODER.execute {
            val next = runCatching { WallpaperStore.loadScaled(path, reqW, reqH) }.getOrNull()
            post {
                if (loading == path) loading = null
                // Lượt cũ về muộn / ô đã bị tháo ⇒ bỏ và NHẢ ảnh, không ghi đè lượt mới.
                if (gen != decodeGen || !isAttachedToWindow) { next?.recycle(); return@post }
                if (next == null) return@post
                val old = bitmap
                bitmap = next
                invalidate()
                // Nhả ảnh CŨ sau khi đã thay — nhả trước thì lần vẽ kế tiếp dùng ảnh đã thu hồi và sập.
                old?.recycle()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Cỡ ô đổi ⇒ nạp lại ở đúng cỡ, không phóng ảnh cũ lên cho nhoè.
        if (w > 0 && h > 0) step(force = true)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawRect(0f, 0f, w, h, bg)

        val b = bitmap
        if (b == null || b.isRecycled) {
            // Chưa có ảnh: NÓI chỗ bỏ ảnh vào chứ không để ô trống — người dùng không có cách nào tự đoán.
            hint.textSize = minOf(w, h) * 0.075f
            canvas.drawText("Chưa có ảnh", w / 2f, h / 2f - hint.textSize * 0.4f, hint)
            hint.textSize = minOf(w, h) * 0.058f
            canvas.drawText("Bỏ ảnh vào thư mục ảnh của Kachi", w / 2f, h / 2f + hint.textSize * 1.4f, hint)
            return
        }

        // Phủ kín + cắt giữa: khung ảnh trong ô nhỏ mà để viền đen thì nhìn như lỗi.
        val bw = b.width; val bh = b.height
        if (bw <= 0 || bh <= 0) return
        val scale = maxOf(w / bw, h / bh)
        val cw = (w / scale).toInt().coerceIn(1, bw)
        val ch = (h / scale).toInt().coerceIn(1, bh)
        src.set((bw - cw) / 2, (bh - ch) / 2, (bw - cw) / 2 + cw, (bh - ch) / 2 + ch)
        dst.set(0, 0, w.toInt(), h.toInt())
        canvas.drawBitmap(b, src, dst, paint)
    }

    companion object {
        /** MỘT thread nền dùng chung cho MỌI widget trình chiếu — xem KDoc ở step(). */
        private val DECODER: java.util.concurrent.ExecutorService =
            java.util.concurrent.Executors.newSingleThreadExecutor { r ->
                Thread(r, "kachi-photo-decode").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
            }
    }
}
