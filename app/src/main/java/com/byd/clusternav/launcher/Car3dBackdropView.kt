package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File

/**
 * ═══ 3.01 R25 — lớp NỀN sau xe 3D: ảnh người dùng chọn ([Car3dBackdrop]) + BÓNG ĐẤT mềm dưới xe ═══════════════════════════
 *
 * Owner 13/10 duyệt: ảnh nền theo hồ sơ, lấy từ thư mục hình nền SẴN CÓ ([WallpaperStore] — app không chở ảnh nào), có chế độ
 * CĂN (kéo = dịch, chụm hai ngón = phóng), lưu lại. Chi phí (memory perf-every-fix):
 *  • Ảnh giải mã MỘT lần cho (tệp, cỡ khung) trên luồng nền, đã giảm cỡ theo khung ([WallpaperStore.loadScaled]); đổi phép căn
 *    chỉ đổi ma trận vẽ — không giải mã lại.
 *  • View thường, chỉ vẽ khi có thay đổi (ảnh nạp xong, căn, bóng đổi khi xe xoay — chính lúc Filament đang vẽ). Rảnh = 0 khung.
 *  • Bóng: một bầu dục gradient tròn (không ảnh, không blur RenderScript) tại hình chiếu đáy xe — chủ ([Car3dWidgetView]) đưa
 *    chữ nhật sau mỗi khung Filament.
 *  • 3.1.5 R29 T29-6 — NỀN ĐO cho lớp phủ trên xe ([Car3dInk]): lưới hộp ≤ 64 cột ([LumaField]) dựng CÙNG lượt giải mã trên
 *    luồng nền sẵn có (đọc từng hàng, không mảng cả ảnh); [boxUnder] đổi vùng màn → toạ độ ảnh theo đúng phép căn đang vẽ;
 *    [paint] = màu sơn đang áp. Ảnh nạp xong / đổi sơn ⇒ [Car3dInk.changed] (một lượt giải), không gì chạy mỗi khung.
 */
@SuppressLint("ViewConstructor")
internal class Car3dBackdropView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    /** Lưới hộp màu của [bitmap] (cùng lượt giải mã) — `null` khi không ảnh. */
    private var field: LumaField? = null
    private var loadedKey: String? = null
    private var loading: String? = null
    private val matrix = Matrix()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = RectF()
    private var hasShadow = false
    private val main = Handler(Looper.getMainLooper())
    /** Phép căn đang xem (khi căn: bản nháp chưa lưu). */
    var backdrop: Car3dBackdrop? = null
        // Căn/phóng xong ⇒ vùng ảnh dưới lớp phủ đổi ⇒ giải lại MỘT lần (không theo từng cú kéo khi đang căn).
        set(v) { field = v; reload(); invalidate(); if (!Car3dBackdropChoice.aligning && bitmap != null && adaptedFor != v) changed() }
    private var adaptedFor: Car3dBackdrop? = null

    /** Màu sơn đang áp lên xe (ARGB đục; `null` = chưa có xe) — chủ đặt khi đổi sơn ([Car3dWidgetView.applyPaint]). */
    var paint: Int? = null
        set(v) { if (field != v) { field = v; changed() } }

    private fun changed() { adaptedFor = backdrop; (parent as? View)?.let(Car3dInk::changed) }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    /** Chữ nhật bóng (px của khung 3D) — đáy xe chiếu lên màn; `null` = ẩn. Chỉ vẽ lại khi đổi. */
    fun shadow(l: Float, t: Float, r: Float, b: Float) {
        if (hasShadow && shadow.left == l && shadow.top == t && shadow.right == r && shadow.bottom == b) return
        if (!hasShadow) Log.i(TAG, "bóng đất ${l.toInt()},${t.toInt()}–${r.toInt()},${b.toInt()}")
        shadow.set(l, t, r, b); hasShadow = true
        val rad = maxOf(1f, shadow.width() / 2)
        shadowPaint.shader = RadialGradient(0f, 0f, rad, intArrayOf(SHADOW_CORE, SHADOW_MID, 0), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); reload() }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); bitmap = null; field = null; loadedKey = null; loading = null }

    /**
     * R29 — hộp ảnh nền dưới vùng [win] (toạ độ CỬA SỔ) + ảnh có phủ trọn vùng không ([Pair.second]). `null` = không ảnh / vùng
     * nằm ngoài ảnh. Phép đổi = nghịch đúng ma trận [onDraw] vẽ ([Car3dBackdrop.place]); ô lưới chạm một phần tính trọn ô (bảo thủ).
     */
    fun boxUnder(win: Rect): Pair<ToneBox, Boolean>? {
        val f = field ?: return null; val bmp = bitmap ?: return null; val b = backdrop ?: return null
        val loc = IntArray(2).also { getLocationInWindow(it) }
        val (sc, tx, ty) = b.place(bmp.width, bmp.height, width, height)
        val l = (win.left - loc[0] - tx) / sc; val t = (win.top - loc[1] - ty) / sc
        val r = (win.right - loc[0] - tx) / sc; val bt = (win.bottom - loc[1] - ty) / sc
        val box = f.range(kotlin.math.floor(l).toInt(), kotlin.math.floor(t).toInt(), kotlin.math.ceil(r).toInt(), kotlin.math.ceil(bt).toInt()) ?: return null
        return box to (l >= 0 && t >= 0 && r <= bmp.width && bt <= bmp.height)
    }

    /** Vùng [win] (toạ độ cửa sổ) chạm bầu dục bóng đất đang vẽ không. */
    fun shadowHits(win: Rect): Boolean {
        if (!hasShadow) return false
        val loc = IntArray(2).also { getLocationInWindow(it) }
        return win.right > shadow.left + loc[0] && win.left < shadow.right + loc[0] && win.bottom > shadow.top + loc[1] && win.top < shadow.bottom + loc[1]
    }

    /**
     * Lưới hộp của [bmp] đọc từng hàng (bộ nhớ = một hàng) — trên luồng giải mã. [ĐO máy ảo 14/10] đọc ĐỦ điểm 1882×1059: 179–258 ms
     * (gấp 5–10 lần giải mã) ⇒ lấy mẫu mỗi [GRID_SAMPLE] hàng × [GRID_SAMPLE] cột: ô lưới ~29 px vẫn ~200 mẫu (p10/p90 bắt mảng ≥ 10 %,
     * chi tiết 1 px có thể lọt — đúng giới hạn đã nhận ở pha 1). Ảnh nhỏ (ô < 4 px) ⇒ đọc đủ. Hỏng ⇒ `null` + log (không ném trên luồng).
     */
    private fun fieldOf(bmp: Bitmap): LumaField? = runCatching {
        val k = if (bmp.width >= GRID_DENSE_PX && bmp.height >= GRID_DENSE_PX) GRID_SAMPLE else 1
        val fb = LumaField.Builder(bmp.width, bmp.height)
        val row = IntArray(bmp.width)
        for (y in 0 until bmp.height step k) { bmp.getPixels(row, 0, bmp.width, 0, y, bmp.width, 1); fb.addRow(y, row, 0, k) }
        fb.build()
    }.onFailure { Log.w(TAG, "R29 lưới nền 3D hỏng — lớp phủ coi như không ảnh: $it") }.getOrNull()

    /** Giải mã MỘT lần cho (tệp, cỡ khung); tệp vắng / hỏng ⇒ không ảnh (nền thẻ kính như cũ). */
    private fun reload() {
        val b = backdrop
        if (b == null) { val had = bitmap != null; bitmap = null; field = null; loadedKey = null; if (had) changed(); return }
        if (width <= 0 || height <= 0) return
        val key = "${b.file}@${width}x$height"
        if (key == loadedKey || key == loading) return
        loading = key
        val dir = WallpaperStore.folder(context) ?: return
        val f = File(dir, b.file)
        // Chặn đường dẫn ra ngoài thư mục ảnh (tên đã lọc ở `:core`; kiểm thêm tại chỗ dùng — mặc định TỪ CHỐI).
        if (!Car3dBackdrop.validName(b.file) || f.parentFile?.canonicalPath != dir.canonicalPath) { loading = null; return }
        val w = width; val h = height
        Thread({
            val t0 = System.nanoTime()
            val bmp = if (f.isFile) WallpaperStore.loadScaled(f.absolutePath, w * MAX_ZOOM_DECODE, h * MAX_ZOOM_DECODE) else null
            val t1 = System.nanoTime()
            val grid = bmp?.let { fieldOf(it) }
            val t2 = System.nanoTime()   // đo TRÊN luồng nền — bản trước lấy mốc trong `main.post` nên cộng cả thời gian chờ luồng chính
            main.post {
                if (loading != key) return@post
                loading = null; loadedKey = key; bitmap = bmp; field = grid
                Log.i(TAG, "nền ${b.file}: ${bmp?.let { "${it.width}x${it.height}" } ?: "không đọc được"} · ${(t1 - t0) / 1_000_000} ms" +
                    " · R29 lưới ${grid?.let { "${it.cols}×${it.rows}" } ?: "—"} ${(t2 - t1) / 1_000_000} ms")
                invalidate(); changed()
            }
        }, "car3d-bg").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap; val b = backdrop
        if (bmp != null && b != null) {
            val (s, tx, ty) = b.place(bmp.width, bmp.height, width, height)
            matrix.setScale(s, s); matrix.postTranslate(tx, ty)
            canvas.drawBitmap(bmp, matrix, bmpPaint)
        }
        if (hasShadow) {
            // Gradient TRÒN co theo trục dọc ⇒ bầu dục mờ đều tới mép (vẽ bầu dục thẳng với gradient tròn thì mép trên/dưới cứng).
            val rad = maxOf(1f, shadow.width() / 2)
            canvas.save(); canvas.translate(shadow.centerX(), shadow.centerY()); canvas.scale(1f, shadow.height() / (2 * rad))
            canvas.drawCircle(0f, 0f, rad, shadowPaint); canvas.restore()
        }
    }

    // ── Chế độ căn: kéo = dịch, chụm = phóng (chủ chuyển cú chạm sang đây khi [Car3dBackdropChoice.aligning]) ─────────
    private var lastX = 0f; private var lastY = 0f
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { backdrop = backdrop?.zoomBy(d.scaleFactor); return true }
    })

    fun align(ev: MotionEvent) {
        scaler.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = ev.x; lastY = ev.y }
            MotionEvent.ACTION_MOVE -> if (!scaler.isInProgress && ev.pointerCount == 1) {
                backdrop = backdrop?.pan(ev.x - lastX, ev.y - lastY, width, height); lastX = ev.x; lastY = ev.y
            }
            MotionEvent.ACTION_POINTER_UP -> { lastX = ev.x; lastY = ev.y }
        }
    }

    companion object {
        private const val TAG = "Car3d"
        /** Giải mã theo cỡ khung (≈ 5 MB ARGB cho ô lớn) — phóng thì mờ dần, đổi lấy bộ nhớ (memory perf-every-fix). */
        private const val MAX_ZOOM_DECODE = 1
        /** Lõi bóng đất (đen 70 %) — cũng là đầu đậm của lớp bóng trong mô hình nền [Car3dInk]. */
        internal const val SHADOW_CORE = 0xB4000000.toInt()
        private const val SHADOW_MID = 0x78000000
        /** Lưới nền 3D: mẫu mỗi 2 hàng × 2 cột khi ảnh ≥ 256 px mỗi chiều (ô lưới ≥ 4 px ⇒ ô nào cũng có mẫu). */
        private const val GRID_SAMPLE = 2
        private const val GRID_DENSE_PX = 256
    }
}
