package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Vòng đo (gauge) — track mờ + cung màu theo %, chữ lớn + nhãn ở giữa. Khớp ring trong prototype. */
class RingView(context: Context) : View(context) {
    private var pct = 0f
    private var big = ""
    private var small = ""

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#3A3F45")   // track xám THẤY ĐƯỢC (prototype), không tàng hình
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor(KachiTheme.GREEN)
    }
    private val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(KachiTheme.INK); textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val smallP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(KachiTheme.MUT); textAlign = Paint.Align.CENTER
    }

    fun set(pct: Float, color: String, big: String, small: String) {
        this.pct = pct.coerceIn(0f, 100f); this.arc.color = Color.parseColor(color)
        this.big = big; this.small = small; invalidate()
    }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val m = minOf(w, h)
        val sw = m * 0.062f                      // stroke MỎNG (~6% như prototype, trước 10% quá dày)
        track.strokeWidth = sw; arc.strokeWidth = sw
        val d = m * 0.76f                        // đường kính nhỏ hơn → có khoảng thở, không đội header
        val cx = w / 2f; val cy = h / 2f
        rect.set(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2)
        canvas.drawArc(rect, 0f, 360f, false, track)
        canvas.drawArc(rect, -90f, pct * 3.6f, false, arc)
        bigP.textSize = d * 0.26f
        smallP.textSize = d * 0.12f
        canvas.drawText(big, cx, cy + bigP.textSize * 0.36f, bigP)
        canvas.drawText(small, cx, cy + bigP.textSize * 0.9f + smallP.textSize, smallP)
    }
}
