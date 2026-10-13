package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
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
 */
@SuppressLint("ViewConstructor")
internal class Car3dBackdropView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
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
        set(v) { field = v; reload(); invalidate() }

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

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); bitmap = null; loadedKey = null; loading = null }

    /** Giải mã MỘT lần cho (tệp, cỡ khung); tệp vắng / hỏng ⇒ không ảnh (nền thẻ kính như cũ). */
    private fun reload() {
        val b = backdrop
        if (b == null) { bitmap = null; loadedKey = null; return }
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
            main.post {
                if (loading != key) return@post
                loading = null; loadedKey = key; bitmap = bmp
                Log.i(TAG, "nền ${b.file}: ${bmp?.let { "${it.width}x${it.height}" } ?: "không đọc được"} · ${(System.nanoTime() - t0) / 1_000_000} ms")
                invalidate()
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

    private companion object {
        const val TAG = "Car3d"
        /** Giải mã theo cỡ khung (≈ 5 MB ARGB cho ô lớn) — phóng thì mờ dần, đổi lấy bộ nhớ (memory perf-every-fix). */
        const val MAX_ZOOM_DECODE = 1
        const val SHADOW_CORE = 0xB4000000.toInt()
        const val SHADOW_MID = 0x78000000
    }
}
