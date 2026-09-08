package com.byd.clusternav.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.byd.clusternav.R

/**
 * SPEED DIAL — a small red-ring dial showing the current speed (Level-2 cockpit UI, task
 * `ui-visual-upgrade-l2`, ref `docs/specs/ui-visual-upgrade-l2.html` `.spd`). FRAMEWORK-only custom [View]
 * drawn with [Canvas]: a circle with a 3dp red ring over a faint red fill, a big centred km/h number and a
 * small "km/h" sublabel. Pure UI — the owner calls [setSpeed] from existing speed state.
 *
 * ── API ───────────────────────────────────────────────────────────────────────────────────────────
 *  • [setSpeed] — the speed in km/h; `null` (or unknown, off-car) shows "—". Values clamp to 0..999.
 *
 * Degrade-safe: nothing drawn before measure; default footprint ~60dp so it never collapses in wrap_content.
 */
class SpeedDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val red = context.getColor(R.color.accent_red)              // ring
    private val fill = context.getColor(R.color.speed_dial_fill)        // ~8% red face fill
    private val inkPrimary = context.getColor(R.color.text_primary)     // number
    private val inkSecondary = context.getColor(R.color.text_secondary) // "km/h"

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var speed: Int? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = fill }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = red
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = inkPrimary
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = inkSecondary
    }

    /** Set the current speed in km/h. `null` / unknown → "—". Clamps to a sane 0..999. */
    fun setSpeed(kmh: Int?) {
        val v = kmh?.coerceIn(0, 999)
        if (v != speed) {
            speed = v
            invalidate()
        }
    }

    /** Default ~42dp square when a dimension is UNBOUNDED (wrap_content) so the dial never collapses. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = dp(42f).toInt()
        setMeasuredDimension(resolveSize(d, widthMeasureSpec), resolveSize(d, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val ring = dp(2f)
        val size = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val radius = size / 2f - ring / 2f - dp(1f)
        if (radius <= 0f) return

        ringPaint.strokeWidth = ring
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        // Big number (or "—"), sat slightly above centre; small "km/h" below.
        val label = speed?.toString() ?: "—"
        numPaint.textSize = size * 0.36f
        val numFm = numPaint.fontMetrics
        val numBaseline = cy - (numFm.ascent + numFm.descent) / 2f - size * 0.06f
        canvas.drawText(label, cx, numBaseline, numPaint)

        unitPaint.textSize = size * 0.15f
        canvas.drawText("km/h", cx, cy + size * 0.28f, unitPaint)
    }
}
