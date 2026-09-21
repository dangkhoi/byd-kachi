package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
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
    /** P1b · §4.10 mục (3) — hai dải che ở đỉnh/đáy màn khi có ảnh; shader dựng ở [onSizeChanged], không mỗi khung. */
    private val bandTop = Paint()
    private val bandBottom = Paint()
    private var bandH = 0f
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
        // UX-OVERHAUL WP1 · R1.3 — glass THẬT: làm mờ NỀN này để thẻ trong mờ frost lên trên. Không làm gì ở API < 31
        // hoặc khi công tắc TẮT (lùi về glass giả). Đặt ở đây để đổi ảnh nền thì hiệu ứng cập nhật theo.
        KachiGlassMode.applyBackdrop(this)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Áp lúc gắn vào cửa sổ (lần dựng màn đầu tiên) — công tắc glass thật áp ở lượt dựng màn kế tiếp.
        KachiGlassMode.applyBackdrop(this)
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
        // P1b: phép phủ/vừa khung dùng CHUNG với `WallArtBuilder` (ảnh mờ dưới thẻ kính phải khớp đúng ảnh này).
        WallFit.map(bw, bh, w, h, fit, src, dst)
        canvas.drawBitmap(p, src, dst, photoPaint)

        // Làm tối ảnh: CẦN THIẾT, không phải trang trí — ảnh sáng làm chữ/ô của launcher nằm trên nó khó đọc.
        //
        // ⚠ [SOÁT P3-3] Chú thích cũ ghi lý do là *"chữ và ô của launcher là màu SÁNG"* — **U5 đã bác câu đó** (nay có
        // bảng màu SÁNG, mực là màu đậm). Giữ tiền đề sai thì lần sửa sau sẽ suy luận từ nó.
        //
        // Màu lấy từ [KachiTheme.WALL_SCRIM] chứ không viết cứng `Color.argb(…, 0, 0, 0)`: hằng màu viết bằng chữ vẫn
        // là màu viết cứng, chỉ là bài canh "0 hex" không thấy — [ĐO] chèn `Color.rgb(255,255,255)` vào tầng vẽ mà cả
        // 3191 bài vẫn XANH. Vai này CỐ Ý mang cùng một mã ở hai bảng (làm tối = làm tối, đúng như nhãn nói); lý do
        // đầy đủ + phần còn tồn ở KDoc [KachiPalette.wallScrim]. **Độ trong suốt** thì vẫn do người dùng đặt.
        if (dimPercent > 0) {
            dimPaint.color = (Color.parseColor(KachiTheme.WALL_SCRIM) and 0x00ffffff) or
                ((dimPercent * 255 / 100).coerceIn(0, 255) shl 24)
            canvas.drawRect(0f, 0f, w, h, dimPaint)
        }
        drawBands(canvas, w, h)
    }

    /**
     * P1b · §4.10 mục (3) — hai dải che (màu nền thanh trên → trong suốt) ở đỉnh và đáy màn, **chỉ khi có ảnh**:
     * thanh trạng thái trên và thanh nút xe luôn có nền đọc được, kể cả khi ảnh có vùng trắng đúng chỗ đó. Vai màu
     * [KachiTheme.BAR_TOP] đã là "nền của thanh trên" ở cả hai bảng nên dải này theo chủ đề mà không mở vai mới.
     */
    private fun drawBands(canvas: Canvas, w: Float, h: Float) {
        if (bandH <= 0f) return
        canvas.drawRect(0f, 0f, w, bandH, bandTop)
        canvas.drawRect(0f, h - bandH, w, h, bandBottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) { bandH = 0f; return }
        bandH = h * BAND_FRACTION
        val bar = Color.parseColor(KachiTheme.BAR_TOP)
        bandTop.shader = LinearGradient(0f, 0f, 0f, bandH, bar, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        bandBottom.shader = LinearGradient(0f, h - bandH, 0f, h.toFloat(), Color.TRANSPARENT, bar, Shader.TileMode.CLAMP)
    }

    private companion object {
        /** Dải che cao 14 % màn (≈ 150 px trên 1080): phủ thanh trên + thanh nút, tan hết trước hàng ô đầu tiên. */
        const val BAND_FRACTION = 0.14f
    }
}
