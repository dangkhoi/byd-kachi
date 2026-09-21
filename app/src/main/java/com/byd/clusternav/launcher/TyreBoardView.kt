package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * BẢNG ÁP SUẤT LỐP 4 BÁNH — **WP3-v5**: hình xe nay là **ẢNH bitmap** ([CarImageLayer]) + **ô giá trị đặt cạnh
 * đúng bánh** (neo [CarLayout]) + chấm màu cho bánh non/căng/lệch, thay hình vector cũ (owner bỏ vector car).
 *
 * ## Ranh giới giữ nguyên
 * Ô vẽ **không có ngưỡng nào**: non/căng/lệch + dòng kết luận đến từ [TyreBoard] (`:core`, test off-car); chuỗi số
 * + đơn vị + kết luận do chỗ gọi ([WidgetViews.tyreBoard]) format qua lớp đơn vị. Ở đây chỉ đặt số cạnh bánh và
 * tô chấm theo trạng thái. WP1/WP3-v5: 0 viền · 0 blur · 0 shadow.
 *
 * ## Bố cục
 * Ảnh xe ở CỘT GIỮA; bốn ô giá trị áp sát **chính cái bánh** nó nói về (hai cột trái/phải × hai hàng trước/sau) —
 * chính khoảng cách gần đó nói "ô này là bánh đó". Vị trí ô lấy từ neo bánh trên **khung ảnh đã vẽ** (letterbox),
 * nên đổi ảnh khác tỉ lệ thì ô vẫn bám.
 */
class TyreBoardView(context: Context) : View(context) {

    private var readings: List<TyreReading> = emptyList()
    private var values: List<String?> = emptyList()
    private var unitLabel: String = ""
    private var temps: List<String?> = emptyList()
    private var verdict: String = ""

    private val car = CarImageLayer(context) { invalidate() }

    private val cellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor(KachiTheme.CARD2)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; isFakeBoldText = true; color = Color.parseColor(KachiTheme.INK)
    }
    private val unitP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; color = Color.parseColor(KachiTheme.MUT)
    }
    private val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor(KachiTheme.MUT)
    }
    private val midP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor(KachiTheme.MUT)
    }
    private val carDst = RectF()
    private val content = RectF()
    private val cell = RectF()

    private val colInk = Color.parseColor(KachiTheme.INK)
    private val colMut = Color.parseColor(KachiTheme.MUT)
    private val colMut2 = Color.parseColor(KachiTheme.MUT2)
    private val colRed = Color.parseColor(KachiTheme.RED)
    private val colAmber = Color.parseColor(KachiTheme.AMBER)

    private val verdictCapPx =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, VERDICT_CAP_SP, resources.displayMetrics)

    fun set(
        readings: List<TyreReading>,
        values: List<String?>,
        unitLabel: String,
        temps: List<String?>,
        verdict: String,
    ) {
        this.readings = readings
        this.values = List(readings.size) { values.getOrNull(it) }
        this.unitLabel = unitLabel
        this.temps = List(readings.size) { temps.getOrNull(it) }
        this.verdict = verdict
        invalidate()
    }

    private fun colorFor(s: TyreStatus): Int = when (s) {
        TyreStatus.LOW, TyreStatus.HIGH -> colRed
        TyreStatus.UNEVEN -> colAmber
        TyreStatus.OK -> colInk
        TyreStatus.UNKNOWN -> colMut2
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val m = minOf(w, h)

        // Ảnh xe ở CỘT GIỮA (chừa hai bên cho ô giá trị); chân bảng cho dòng kết luận.
        carDst.set(w * CAR_LEFT, h * TOP_INSET, w * CAR_RIGHT, h * FOOTER_TOP)
        car.ensure((w * (CAR_RIGHT - CAR_LEFT)).toInt(), (h * FOOTER_TOP).toInt())
        car.draw(canvas, carDst)
        car.contentRect(carDst, content)

        // Ô giá trị cao bao nhiêu = do khoảng cách hai hàng bánh trên ẢNH quyết (không thì hai ô cùng bên chồng nhau).
        val wheelSpanY = (CarLayout.wheel(TyreCorner.REAR_LEFT).y - CarLayout.wheel(TyreCorner.FRONT_LEFT).y) * content.height()
        val cellH = minOf(h * 0.24f, wheelSpanY - m * 0.035f).coerceAtLeast(m * 0.10f)

        bigP.textSize = minOf(m * 0.150f, cellH * 0.58f)
        unitP.textSize = bigP.textSize * 0.45f
        subP.textSize = minOf(m * 0.042f, cellH * 0.22f)
        midP.textSize = maxOf(minOf(m * 0.072f, verdictCapPx), subP.textSize)

        val gap = m * 0.03f
        val pad = w * 0.02f
        val rows = if (readings.size < 4) PLACEHOLDER_CORNERS else readings.map { it.corner }
        rows.forEachIndexed { i, corner ->
            val a = CarLayout.wheel(corner)
            val wheelX = content.left + a.x * content.width()
            val wheelY = content.top + a.y * content.height()
            if (a.x < 0.5f) cell.set(pad, wheelY - cellH / 2f, wheelX - gap, wheelY + cellH / 2f)
            else cell.set(wheelX + gap, wheelY - cellH / 2f, w - pad, wheelY + cellH / 2f)
            val rd = readings.getOrNull(i)
            drawCell(canvas, m, corner, rd, values.getOrNull(i), temps.getOrNull(i))
            // Chấm màu trên bánh có vấn đề (non/căng = đỏ, lệch = hổ phách); OK/chưa-đọc ⇒ không chấm (số đã nói).
            val st = rd?.status ?: TyreStatus.UNKNOWN
            if (st.alert || st == TyreStatus.UNEVEN) {
                dot.color = colorFor(st)
                canvas.drawCircle(wheelX, wheelY, minOf(content.width(), content.height()) * DOT_RATIO, dot)
            }
        }

        midP.color = colMut
        canvas.drawText(verdict, w / 2f, h * 0.965f, midP)
    }

    private fun drawCell(canvas: Canvas, m: Float, corner: TyreCorner, rd: TyreReading?, value: String?, temp: String?) {
        val st = rd?.status ?: TyreStatus.UNKNOWN
        val col = colorFor(st)
        val radius = m * 0.035f
        cellFill.color = if (st.alert) ColorMath.mix(Color.parseColor(KachiTheme.CARD2), col, SEMANTIC_MIX)
        else Color.parseColor(KachiTheme.CARD2)
        canvas.drawRoundRect(cell, radius, radius, cellFill)

        val hasValue = value != null
        val numText = value ?: TelemetryView.PLACEHOLDER
        bigP.color = col
        val unit = if (hasValue) unitLabel else ""
        val numBase = cell.centerY() + bigP.textSize * 0.10f - subP.textSize * 0.60f
        drawPair(canvas, m, numText, unit, numBase)

        subP.color = if (st.alert) col else colMut
        val why = st.reason
        val sub = listOfNotNull(corner.shortLabel, why, temp).joinToString(" · ")
        canvas.drawText(sub, cell.centerX(), numBase + subP.textSize * 1.35f, subP)
    }

    private fun drawPair(canvas: Canvas, m: Float, number: String, unit: String, baseline: Float) {
        val numW = bigP.measureText(number)
        val gapU = if (unit.isEmpty()) 0f else m * 0.018f
        val unitW = if (unit.isEmpty()) 0f else unitP.measureText(unit)
        var x = cell.centerX() - (numW + gapU + unitW) / 2f
        canvas.drawText(number, x, baseline, bigP)
        if (unit.isEmpty()) return
        x += numW + gapU
        canvas.drawText(unit, x, baseline, unitP)
    }

    fun release() = car.release()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        car.release()
    }

    private companion object {
        const val SEMANTIC_MIX = 0.20
        const val VERDICT_CAP_SP = 16f
        /** Cột giữa dành cho ảnh xe (0.30..0.70 của bề ngang) — hai bên cho ô giá trị. */
        const val CAR_LEFT = 0.30f
        const val CAR_RIGHT = 0.70f
        const val TOP_INSET = 0.02f
        const val FOOTER_TOP = 0.88f
        const val DOT_RATIO = 0.05f
        val PLACEHOLDER_CORNERS: List<TyreCorner> = TyreCorner.values().toList()
    }
}
