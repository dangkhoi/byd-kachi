package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * THANH MỨC mảnh cho đại lượng CÓ TRẦN (3.01 R22): pin %, SOH %, mức xăng %, độ mở kính %.
 *
 * Luật chọn là **chung, theo dữ liệu** — không theo mã widget: đơn vị là `%` và số đọc được nằm trong 0..100 ⇒ có
 * thanh ([fractionOf]). Số chưa đọc được (`—`) ⇒ chỉ còn rãnh (trạng thái trống lặng, không giả một mức 0).
 *
 * Vẽ: hai hình bo tô ĐẶC (rãnh + phần đầy) — không nét (WP1 zero-border), không bóng, không animation. `Paint`/`RectF`
 * cấp phát MỘT lần ở dựng view; [onDraw] không cấp phát. Đổi mức = một `invalidate`, chỉ khi phân số đổi thật.
 */
class KachiMeter(context: Context) : View(context) {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val box = RectF()
    private var fraction: Float? = null

    init { setColors(c(KachiTheme.ACCENT_INK)) }

    /**
     * Mực phần đầy (thường là [KachiWidgetType.accentOf] của lĩnh vực). Rãnh = mực [KachiTheme.INK] ở [TRACK_ALPHA] — KHÔNG dùng
     * vai `TRACK`: [ĐO] bảng SÁNG `TRACK` (228,232,241) trùng gần tuyệt đối mặt thẻ (227,231,240) ⇒ rãnh tàng hình, thanh rỗng
     * (`—`) biến mất. Mực-theo-alpha tự đúng chiều ở cả hai bảng (tối: vệt sáng · sáng: vệt sẫm).
     */
    fun setColors(accent: Int) {
        track.color = ColorMath.withAlpha(c(KachiTheme.INK), TRACK_ALPHA); fill.color = accent; invalidate()
    }

    /** [f] 0..1, `null` = chưa đọc được (chỉ vẽ rãnh). Không đổi ⇒ không vẽ lại. */
    fun set(f: Float?) {
        val v = f?.coerceIn(0f, 1f)
        if (v == fraction) return
        fraction = v; invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            KachiTheme.dpi(context, Sp.BAR_THIN),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat(); val r = h / 2f
        box.set(paddingLeft.toFloat(), 0f, (width - paddingRight).toFloat(), h)
        canvas.drawRoundRect(box, r, r, track)
        val f = fraction ?: return
        if (f <= 0f) return
        box.right = box.left + maxOf(h, box.width() * f)
        canvas.drawRoundRect(box, r, r, fill)
    }

    companion object {
        /** Độ đục rãnh (`0x29` ≈ 16 %) — thấy rõ rãnh trống mà không tranh với phần đầy (đo ở `widget-visual-301`). */
        private const val TRACK_ALPHA = 0x29

        /** Phân số 0..1 của một số có đơn vị `%`; mọi thứ khác (đơn vị khác, chữ, ngoài 0..100) ⇒ `null`. */
        fun fractionOf(number: String, unit: String): Float? {
            if (unit.trim() != "%") return null
            val v = number.trim().replace(',', '.').toFloatOrNull() ?: return null
            return if (v in 0f..100f) v / 100f else null
        }

        /** Đại lượng này CÓ thanh không (đơn vị `%`) — quyết định lúc dựng, kể cả khi số chưa đọc được. */
        fun bounded(unit: String): Boolean = unit.trim() == "%"
    }
}
