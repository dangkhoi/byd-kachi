package com.byd.clusternav.speedbadge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import com.byd.clusternav.contracts.SpeedSignType

/**
 * Canvas-drawn speed-limit badge (Vietnamese/EU regulatory style).
 * White circle, thick red ring, bold black number centered.
 * Designed for 120dp cluster overlay; scales to any measured size.
 *
 * B2 (owner 2026-08-19): a [muted] variant draws a GRAY ring + GRAY number instead of red/black — used ONLY by
 * the "upcoming speed-limit" badge so the driver never confuses the upcoming limit with the CURRENT one. The
 * current-limit badge keeps [muted] = false → unchanged red/black regulatory style.
 */
class SpeedBadgeView(context: Context) : View(context) {

    var speedValue: Int = 0
        set(v) { field = v; invalidate() }

    var signType: SpeedSignType? = SpeedSignType.REGULATORY
        set(v) { field = v; invalidate() }

    /**
     * B2: MUTED style — GRAY ring + GRAY number (upcoming badge). Default false = red/black regulatory style
     * (current-limit badge, UNCHANGED).
     */
    var muted: Boolean = false
        set(v) { field = v; invalidate() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        if (speedValue <= 0) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy)
        val ringWidth = radius * 0.15f

        // White filled circle
        canvas.drawCircle(cx, cy, radius - ringWidth / 2f, bgPaint)

        // Ring: regulatory RED for the current-limit badge; muted GRAY for the upcoming badge (B2).
        ringPaint.color = if (muted) MUTED_GRAY else Color.RED
        ringPaint.strokeWidth = ringWidth
        canvas.drawCircle(cx, cy, radius - ringWidth / 2f, ringPaint)

        // Speed number: BLACK for the current-limit badge; muted GRAY for the upcoming badge (B2).
        textPaint.color = if (muted) MUTED_GRAY else Color.BLACK
        val text = speedValue.toString()
        textPaint.textSize = radius * (if (text.length >= 3) 0.7f else 0.9f)
        val fm = textPaint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, cx, textY, textPaint)
    }

    private companion object {
        /** B2: muted gray (~#888888) for the upcoming badge's ring + number. */
        private const val MUTED_GRAY = 0xFF888888.toInt()
    }
}
