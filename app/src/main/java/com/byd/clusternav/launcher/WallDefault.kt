package com.byd.clusternav.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * ═══ NỀN MẶC ĐỊNH của Kachi — dựng bằng mã, đi qua ĐÚNG đường ảnh nền + kính (3.01 R22) ═══════════════════════════
 *
 * Brief Kim §4 (13/10): ~60–70 % cảm giác "elegant" là LỚP NỀN, mà Kachi chỉ có ảnh nền khi người dùng tự chép ảnh vào
 * thư mục ⇒ đa số thấy nền xanh phẳng và thẻ đục đứng trên đó như biểu mẫu. Tệp này cho một nền **của chính mình**:
 * phòng trưng bày trừu tượng (chuyển sắc dọc graphite → xanh đêm, đèn hắt trần, vầng sáng sàn), bản Tối + Sáng.
 *
 * ## Vì sao dựng thành BITMAP chứ không vẽ vector trong [WallView]
 * Thẻ kính ([WallWindowDrawable]) vẽ đúng vùng ảnh nền dưới nó qua `BitmapShader` ⇒ nó CẦN một bitmap. Dựng một lần ra
 * bitmap rồi đưa vào [WallpaperController] như một ảnh nền ⇒ kính · lớp che theo độ chói ([GlassVeil]) · dải che đỉnh/
 * đáy đều áp **y như ảnh người dùng**, không mở đường vẽ thứ hai.
 *
 * ## Chi phí
 * Cỡ = màn ÷ [WallArtBuilder.SCALE] (1920×1080 ⇒ 480×270, ~0,5 MB mỗi bitmap; hai bitmap: ảnh hiện + ảnh "mờ" cho kính).
 * Nền chỉ có chuyển sắc mượt nên phóng lên với lọc song tuyến không mất gì, và KHÔNG cần làm mờ. Dựng ở thread nền, một
 * lần mỗi lần đổi Sáng/Tối; 0 vẽ thêm mỗi khung (WallView vẽ một bitmap thay cho hai lớp RadialGradient như trước).
 */
internal object WallDefault {

    /** Nền mặc định cho bảng [night] ở cỡ màn [screenW]×[screenH]: (ảnh hiện, [WallArt] cho kính). `null` ⇒ lỗi cấp phát. */
    fun build(night: Boolean, screenW: Int, screenH: Int): Pair<Bitmap, WallArt>? {
        if (screenW <= 0 || screenH <= 0) return null
        val w = maxOf(1, screenW / WallArtBuilder.SCALE); val h = maxOf(1, screenH / WallArtBuilder.SCALE)
        val shown = runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        paint(Canvas(shown), w.toFloat(), h.toFloat(), if (night) KachiPaletteSeeds.BACKDROP_DARK else KachiPaletteSeeds.BACKDROP_LIGHT)
        val blurred = runCatching { shown.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            ?: return null.also { shown.recycle() }
        val px = IntArray(w * h)
        blurred.getPixels(px, 0, w, 0, 0, w, h)
        val art = WallArt(
            blurred, WallArtBuilder.SCALE, WallArtBuilder.COLS, WallArtBuilder.ROWS,
            WallArtBuilder.luminanceGrid(px, w, h), DominantColors.of(px, 3), screenW, screenH,
        )
        return shown to art
    }

    /**
     * Ba lớp, tô ĐẶC (không nét — WP1), có dither để chuyển sắc tối không thành bậc:
     *  1. chuyển sắc dọc [Backdrop.top] → [Backdrop.mid] (55 %) → [Backdrop.bottom];
     *  2. đèn hắt trần — elip rộng lệch phải ở trên mép màn, [Backdrop.cove] 34 % → trong suốt;
     *  3. vầng sáng sàn — elip dẹt ở đáy, [Backdrop.floor] 30 % → trong suốt (gợi bệ trưng bày, chỗ xe/nội dung đứng).
     * Vùng giữa-trái (nơi thẻ widget nằm nhiều nhất) giữ phẳng, ít chi tiết ⇒ lớp che kính đo độ chói ổn định.
     */
    private fun paint(canvas: Canvas, w: Float, h: Float, b: Backdrop) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
        p.shader = LinearGradient(
            0f, 0f, 0f, h, intArrayOf(col(b.top), col(b.mid), col(b.bottom)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = ellipse(w * 0.64f, -h * 0.10f, w * 0.62f, 0.42f, ColorMath.withAlpha(col(b.cove), COVE_ALPHA))
        canvas.drawRect(0f, 0f, w, h, p)
        p.shader = ellipse(w * 0.58f, h * 1.02f, w * 0.55f, 0.22f, ColorMath.withAlpha(col(b.floor), FLOOR_ALPHA))
        canvas.drawRect(0f, 0f, w, h, p)
    }

    /** Vầng sáng hình ELIP: RadialGradient tròn bán kính [r] ép dọc theo [squash] quanh tâm ([cx], [cy]). */
    private fun ellipse(cx: Float, cy: Float, r: Float, squash: Float, color: Int): Shader =
        RadialGradient(cx, cy, r, color, Color.TRANSPARENT, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(1f, squash, cx, cy) })
        }

    private fun col(hex: String): Int = Color.parseColor(hex)

    /** Độ đục đỉnh của đèn hắt trần / vầng sáng sàn (0..255) — đủ thấy "không khí", không đủ thành một vệt sáng. */
    private const val COVE_ALPHA = 0x8c
    private const val FLOOR_ALPHA = 0x80
}
