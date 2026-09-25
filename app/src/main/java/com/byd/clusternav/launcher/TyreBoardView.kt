package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * BẢNG ÁP SUẤT LỐP 4 BÁNH — **hình xe TÁCH LAYER** (owner 2026-09-25).
 *
 * FrameLayout xếp 2 lớp cùng cỡ:
 *  • **NỀN** [CarImageView] — chỉ vẽ hình xe; `invalidate` DUY NHẤT khi ảnh vừa nạp xong, KHÔNG theo nhịp số.
 *  • **ĐÈ** [CellsView] (trong suốt) — 4 ô giá trị + số + chấm; `invalidate` khi số đổi. Số đổi ⇒ chỉ lớp này
 *    vẽ lại, HÌNH XE ĐỨNG YÊN (hết nháy). Lớp đè tính `content` (letterbox) từ [CarImageView.contentRect] để
 *    ô bám đúng bánh — hai lớp cùng công thức bố cục ([carDstIn]).
 *
 * Ranh giới vẫn giữ: ô vẽ 0 ngưỡng (non/căng/lệch + số + đơn vị do [TyreBoard]/chỗ gọi), 0 viền/blur/shadow.
 */
class TyreBoardView(context: Context) : FrameLayout(context) {

    private val carView = CarImageView(context).also { it.layoutRect = ::carDstIn }
    private val cells = CellsView(context)

    init {
        addView(carView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(cells, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Neo bố cục ảnh xe = cột giữa (dùng chung cho cả hai lớp). */
    private fun carDstIn(w: Float, h: Float, out: RectF) {
        out.set(w * CAR_LEFT, h * TOP_INSET, w * CAR_RIGHT, h * FOOTER_TOP)
    }

    fun set(
        readings: List<TyreReading>,
        values: List<String?>,
        unitLabel: String,
        temps: List<String?>,
        verdict: String,
    ) = cells.set(readings, values, unitLabel, temps, verdict)

    fun release() = carView.release()

    /** Lớp ĐÈ trong suốt: chỉ 4 ô + số + chấm. `invalidate` khi số đổi — không đụng hình xe. */
    private inner class CellsView(context: Context) : View(context) {
        private var readings: List<TyreReading> = emptyList()
        private var values: List<String?> = emptyList()
        private var unitLabel: String = ""
        private var temps: List<String?> = emptyList()
        private var verdict: String = ""

        private val cellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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
        private val content = RectF()
        private val cell = RectF()

        private val colInk = Color.parseColor(KachiTheme.INK)
        private val colMut = Color.parseColor(KachiTheme.MUT)
        private val colMut2 = Color.parseColor(KachiTheme.MUT2)
        private val colRed = Color.parseColor(KachiTheme.RED)
        private val colAmber = Color.parseColor(KachiTheme.AMBER)

        fun set(
            readings: List<TyreReading>, values: List<String?>, unitLabel: String,
            temps: List<String?>, verdict: String,
        ) {
            val nv = List(readings.size) { values.getOrNull(it) }
            val nt = List(readings.size) { temps.getOrNull(it) }
            val unchanged = this.readings == readings && this.values == nv &&
                this.unitLabel == unitLabel && this.temps == nt && this.verdict == verdict
            this.readings = readings; this.values = nv; this.unitLabel = unitLabel
            this.temps = nt; this.verdict = verdict
            if (!unchanged) invalidate()   // chỉ lớp SỐ vẽ lại; hình xe (view khác) đứng yên
        }

        private fun colorFor(s: TyreStatus): Int = when (s) {
            TyreStatus.LOW, TyreStatus.HIGH -> colRed
            TyreStatus.UNEVEN -> colAmber
            TyreStatus.OK -> colInk
            TyreStatus.UNKNOWN -> colMut2
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val m = minOf(w, h)
            carView.contentRect(w, h, content)   // khung hình xe THẬT — bám bánh theo lớp nền

            val wheelSpanY = (CarLayout.wheel(TyreCorner.REAR_LEFT).y - CarLayout.wheel(TyreCorner.FRONT_LEFT).y) * content.height()
            val cellH = minOf(h * 0.24f, wheelSpanY - m * 0.035f).coerceAtLeast(m * 0.10f)

            val minBig = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_VALUE_SP, resources.displayMetrics)
            val minSub = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_SUB_SP, resources.displayMetrics)
            bigP.textSize = minOf(m * 0.150f, cellH * 0.58f).coerceAtLeast(minBig)
            unitP.textSize = bigP.textSize * 0.45f
            subP.textSize = minOf(m * 0.042f, cellH * 0.22f).coerceAtLeast(minSub)

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
                val st = rd?.status ?: TyreStatus.UNKNOWN
                if (st.alert || st == TyreStatus.UNEVEN) {
                    dot.color = colorFor(st)
                    canvas.drawCircle(wheelX, wheelY, minOf(content.width(), content.height()) * DOT_RATIO, dot)
                }
            }
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
    }

    private companion object {
        const val SEMANTIC_MIX = 0.20
        const val MIN_VALUE_SP = 13f
        const val MIN_SUB_SP = 8f
        const val CAR_LEFT = 0.30f
        const val CAR_RIGHT = 0.70f
        const val TOP_INSET = 0.04f
        const val FOOTER_TOP = 0.98f   // task 3 (owner 2026-09-25): dùng gần hết chiều cao, bớt dải trống đáy (~66px)
        const val DOT_RATIO = 0.05f
        val PLACEHOLDER_CORNERS: List<TyreCorner> = TyreCorner.values().toList()
    }
}
