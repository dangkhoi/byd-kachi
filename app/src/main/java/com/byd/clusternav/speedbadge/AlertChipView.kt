package com.byd.clusternav.speedbadge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Canvas-drawn "road alert / speed-camera" chip (B3.20) for the cluster overlay — VietMap's sticky ALERTS-slot
 * alert: an optional speed-camera glyph + an optional enforced-limit mini-badge + a distance countdown, on a
 * dark rounded pill. SONG SONG với badge tốc-độ + nav GMaps; đây là "data VN trên cụm".
 *
 * Purely presentational: the [RoadAlertChip] decision (:core) decides WHAT to show; this only draws. Layout is
 * horizontal: [camera glyph?] [limit circle?] [distance text?]. Any element with no data is omitted, and the
 * chip measures its own content width via [contentWidthPx] so the overlay window can size to it.
 */
class AlertChipView(context: Context) : View(context) {

    /** Enforced/posted limit the alert carries; ≤ 0 ⇒ no limit circle. */
    var limitKph: Int = 0
        set(v) { field = v; invalidate() }

    /** Distance countdown text ("300 m" / "1,2 km"); blank ⇒ omitted. */
    var distanceText: String = ""
        set(v) { field = v; invalidate() }

    /** Draw the speed-camera glyph on the left. */
    var hasIcon: Boolean = true
        set(v) { field = v; invalidate() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG; style = Paint.Style.FILL }
    private val camBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val camLens = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT; style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; style = Paint.Style.STROKE }
    private val circleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val distPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT
    }

    /** Pixel width the current content needs at height [h] — the overlay uses it to size the window. */
    fun contentWidthPx(h: Int): Int {
        val pad = h * PAD_FRAC
        val glyph = if (hasIcon) h * GLYPH_FRAC + pad else 0f
        val circle = if (limitKph > 0) h * CIRCLE_FRAC + pad else 0f
        distPaint.textSize = h * TEXT_FRAC
        val text = if (distanceText.isNotEmpty()) distPaint.measureText(distanceText) + pad else 0f
        return (pad + glyph + circle + text + pad).toInt().coerceAtLeast(h)
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        if (h <= 0f) return
        val pad = h * PAD_FRAC
        // Dark rounded pill background.
        val r = h * 0.28f
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), h), r, r, bgPaint)

        var x = pad
        val cy = h / 2f
        // Speed-camera glyph: camera body (rounded rect) + lens (accent circle) + viewfinder bump.
        if (hasIcon) {
            val g = h * GLYPH_FRAC
            val top = cy - g / 2f
            val body = RectF(x, top + g * 0.18f, x + g, top + g)
            canvas.drawRoundRect(body, g * 0.14f, g * 0.14f, camBody)
            canvas.drawRect(x + g * 0.30f, top, x + g * 0.60f, top + g * 0.22f, camBody)   // viewfinder bump
            canvas.drawCircle(x + g / 2f, top + g * 0.60f, g * 0.24f, camLens)             // lens
            x += g + pad
        }
        // Enforced-limit mini speed-limit sign (white circle + red ring + black number).
        if (limitKph > 0) {
            val d = h * CIRCLE_FRAC
            val cxc = x + d / 2f
            val rad = d / 2f
            val rw = rad * 0.20f
            canvas.drawCircle(cxc, cy, rad - rw / 2f, circleBg)
            ringPaint.strokeWidth = rw
            canvas.drawCircle(cxc, cy, rad - rw / 2f, ringPaint)
            val t = limitKph.toString()
            numPaint.textSize = rad * (if (t.length >= 3) 0.9f else 1.1f)
            val fm = numPaint.fontMetrics
            canvas.drawText(t, cxc, cy - (fm.ascent + fm.descent) / 2f, numPaint)
            x += d + pad
        }
        // Distance countdown text.
        if (distanceText.isNotEmpty()) {
            distPaint.textSize = h * TEXT_FRAC
            distPaint.setShadowLayer(h * 0.06f, 0f, 0f, Color.BLACK)
            val fm = distPaint.fontMetrics
            canvas.drawText(distanceText, x, cy - (fm.ascent + fm.descent) / 2f, distPaint)
        }
    }

    private companion object {
        private val BG = 0xCC202020.toInt()       // dark translucent pill
        private val ACCENT = 0xFFE53935.toInt()   // camera lens accent (red)
        private const val PAD_FRAC = 0.14f
        private const val GLYPH_FRAC = 0.62f
        private const val CIRCLE_FRAC = 0.86f
        private const val TEXT_FRAC = 0.52f
    }
}
