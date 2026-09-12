package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.view.View

/**
 * Nền "wall": tối + 2 vầng sáng (xanh trên-trái, tím dưới-phải) — khớp prototype kachi-workspace.html.
 *
 * **U4**: nay vẽ được **ảnh** làm nền khi người dùng bật. Nền vẽ sẵn (gradient) vẫn là **mặc định và là đường lùi**:
 * chưa bật · không có ảnh · ảnh hỏng ⇒ vẽ đúng như trước, **không đổi một pixel** cho người không dùng tính năng này.
 */
class WallView(context: Context) : View(context) {
    private val base = Paint().apply { color = Color.parseColor(KachiTheme.BG) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint()
    private val src = Rect()
    private val dst = Rect()

    private var photo: Bitmap? = null
    private var fit: ImageFit = ImageFit.FILL
    private var dimPercent: Int = WallpaperPrefs.DEFAULT_DIM_PERCENT

    /**
     * Đặt ảnh nền. `null` ⇒ quay về nền vẽ sẵn.
     *
     * KHÔNG tự giải phóng ảnh cũ ở đây: chỗ gọi ([KachiHomeActivity]) mới biết ảnh đó còn ai dùng không. Giải phóng
     * bừa ở đây thì lần vẽ kế tiếp sẽ dùng ảnh đã bị thu hồi và sập.
     */
    fun setPhoto(bitmap: Bitmap?, fit: ImageFit = ImageFit.FILL, dimPercent: Int = WallpaperPrefs.DEFAULT_DIM_PERCENT) {
        this.photo = bitmap
        this.fit = fit
        this.dimPercent = dimPercent.coerceIn(0, 90)
        invalidate()
    }

    /** Có đang vẽ ảnh không (cho test dây nối / nhật ký). */
    fun hasPhoto(): Boolean = photo?.isRecycled == false

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val p = photo
        if (p != null && !p.isRecycled) {
            drawPhoto(canvas, p, w, h)
            return
        }

        // Đường mặc định / đường lùi — byte-giữ so với bản trước U4.
        canvas.drawRect(0f, 0f, w, h, base)
        val r = maxOf(w, h)
        glow.shader = RadialGradient(w * 0.13f, h * -0.06f, r * 0.42f,
            Color.parseColor(KachiTheme.GLOW1), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, glow)
        glow.shader = RadialGradient(w * 0.94f, h * 1.08f, r * 0.40f,
            Color.parseColor(KachiTheme.GLOW2), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, glow)
    }

    private fun drawPhoto(canvas: Canvas, p: Bitmap, w: Float, h: Float) {
        // Nền tối vẽ TRƯỚC: chế độ "vừa khung" có thể còn viền, và viền phải là nền của launcher chứ không phải đen thô.
        canvas.drawRect(0f, 0f, w, h, base)

        val bw = p.width; val bh = p.height
        if (bw <= 0 || bh <= 0) return
        when (fit) {
            ImageFit.FILL -> {
                // Phủ kín: cắt phần thừa ở giữa ảnh (cắt lệch một bên sẽ mất chủ thể).
                val scale = maxOf(w / bw, h / bh)
                val cw = (w / scale).toInt().coerceAtMost(bw)
                val ch = (h / scale).toInt().coerceAtMost(bh)
                src.set((bw - cw) / 2, (bh - ch) / 2, (bw - cw) / 2 + cw, (bh - ch) / 2 + ch)
                dst.set(0, 0, w.toInt(), h.toInt())
            }
            ImageFit.FIT -> {
                val scale = minOf(w / bw, h / bh)
                val dw = (bw * scale).toInt()
                val dh = (bh * scale).toInt()
                src.set(0, 0, bw, bh)
                val left = ((w.toInt() - dw) / 2); val top = ((h.toInt() - dh) / 2)
                dst.set(left, top, left + dw, top + dh)
            }
        }
        canvas.drawBitmap(p, src, dst, photoPaint)

        // Làm tối: CẦN THIẾT, không phải trang trí — chữ và ô của launcher là màu sáng; ảnh sáng làm chữ không đọc được.
        if (dimPercent > 0) {
            dimPaint.color = Color.argb((dimPercent * 255 / 100).coerceIn(0, 255), 0, 0, 0)
            canvas.drawRect(0f, 0f, w, h, dimPaint)
        }
    }
}
