package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * BẢNG *CỬA & KHOANG* — **WP3-v5**: hình xe nay là **ẢNH bitmap** ([CarImageLayer]) + **chấm màu** tại vị trí từng
 * bộ phận đang mở ([CarLayout]), thay hình vector cũ (owner bỏ vector car — chê xấu/tốn token).
 *
 * ## Ranh giới giữ nguyên (vẫn đúng luật `GroupTileWiringContractTest`)
 * Ô vẽ **không phán xét**: bộ phận nào đang mở / sắc thái / câu kết luận đều do [GroupBoard.doorPlan] (`:core`,
 * kiểm off-car) quyết; ở đây không có một mã datum (`door_*`) hay ngưỡng nào. Chỗ nối là enum [CarPart].
 *
 * ## Vì sao số (%) nằm ở CHÂN BẢNG, không vẽ trên ảnh
 * Chữ trên ẢNH (màu bất kỳ do người dùng thay) khó đọc. Dòng kết luận [DoorBoardPlan.footer] (do `:core` dựng) đã
 * kể tên + giá trị các khoang đang mở (*"2 cửa mở · Cốp · 40 %"*), nên overlay trên ảnh chỉ cần **chấm vị trí**
 * (câu hỏi *"khoang NÀO đang mở"* là câu về không gian) — số để cho footer. Hết cửa xoè động (ảnh tĩnh).
 *
 * WP1/WP3-v5: 0 viền · 0 blur · 0 shadow. Mọi `Paint` cấp phát MỘT LẦN; màu phân giải một lần.
 */
internal class DoorBoardView(context: Context) : View(context) {

    private var plan: DoorBoardPlan? = null
    private val car = CarImageLayer(context) { invalidate() }

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val footerP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor(KachiTheme.MUT)
    }

    private val carDst = RectF()
    private val content = RectF()

    private val colMut = Color.parseColor(KachiTheme.MUT)
    private val colAccent = Color.parseColor(KachiTheme.ACCENT)
    private val colAmber = Color.parseColor(KachiTheme.AMBER)
    private val colRed = Color.parseColor(KachiTheme.RED)

    private val labelFloorPx = Sp.dpf(context, Sp.BOARD_LABEL_MIN)

    fun set(model: GroupBoardModel) {
        plan = GroupBoard.doorPlan(model)
        invalidate()
    }

    /** Màu theo sắc thái — cùng thang với các bảng khác (đỏ nguy · hổ phách để ý · nhấn đang-mở · mờ nghỉ). */
    private fun tint(tone: GroupTone): Int = when (tone) {
        GroupTone.ALERT -> colRed
        GroupTone.WARN -> colAmber
        GroupTone.ACTIVE -> colAccent
        GroupTone.NEUTRAL -> colMut
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = plan ?: return
        if (w <= 0f || h <= 0f || p.parts.isEmpty()) return
        val min = minOf(w, h)

        // Ảnh xe chiếm phần trên; chân bảng chừa cho dòng kết luận.
        val pad = w * PAD_RATIO
        carDst.set(pad, h * TOP_INSET, w - pad, h * FOOTER_TOP)
        car.ensure(width, (h * FOOTER_TOP).toInt())
        car.draw(canvas, carDst)

        // Chấm màu tại từng bộ phận ĐANG MỞ, theo neo chuẩn hoá trên đúng khung ảnh đã vẽ.
        car.contentRect(carDst, content)
        val r = minOf(content.width(), content.height()) * DOT_RATIO
        for (i in p.parts.indices) {
            val s = p.parts[i]
            if (!s.open) continue
            val a = CarLayout.part(s.part)
            dot.color = tint(s.tone)
            canvas.drawCircle(content.left + a.x * content.width(), content.top + a.y * content.height(), r, dot)
        }

        // Dòng kết luận (do :core dựng) — thu cho vừa, canh giữa chân bảng.
        footerP.textSize = maxOf(min * FOOTER_RATIO, labelFloorPx)
        footerP.color = colMut
        val room = w - pad * 2f
        val need = footerP.measureText(p.footer)
        if (need > room && need > 0f) footerP.textSize = maxOf(footerP.textSize * room / need, labelFloorPx)
        canvas.drawText(clip(p.footer, room), w / 2f, h * FOOTER_BASELINE, footerP)
    }

    private fun clip(s: String, room: Float): String {
        if (room <= 0f) return ""
        if (footerP.measureText(s) <= room) return s
        var n = s.length
        while (n > 0 && footerP.measureText(s.take(n) + ELLIPSIS) > room) n--
        return if (n <= 0) "" else s.take(n) + ELLIPSIS
    }

    fun release() = car.release()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        car.release()
    }

    private companion object {
        const val ELLIPSIS = "…"
        const val PAD_RATIO = 0.04f
        const val TOP_INSET = 0.03f
        const val FOOTER_TOP = 0.86f
        const val FOOTER_BASELINE = 0.97f
        const val FOOTER_RATIO = 0.056f
        /** Bán kính chấm theo cạnh nhỏ của khung xe. */
        const val DOT_RATIO = 0.065f
    }
}
