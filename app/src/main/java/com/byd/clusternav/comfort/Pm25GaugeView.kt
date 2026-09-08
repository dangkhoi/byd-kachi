package com.byd.clusternav.comfort

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.byd.clusternav.R

/**
 * PM2.5 GAUGE — a circular arc gauge for the fine-dust level (Level-2 cockpit UI, task
 * `ui-visual-upgrade-l2`, ref `docs/specs/ui-visual-upgrade-l2.html` `.gauge`). FRAMEWORK-only custom View
 * drawn with [Canvas]. Pure UI — no HAL / prefs coupling; the owner ([com.byd.clusternav.MainActivity.refreshPm25Level])
 * reads the level on a background thread via [Pm25FilterApplier.readLevel] and calls [setLevel] on the main
 * thread. [Pm25Filter] (core) stays UNCHANGED — this only maps its 0..6 scale to an arc fraction, colour and
 * the bilingual-neutral Vietnamese label ([Pm25Filter.levelLabelVi]).
 *
 * ── Render (mockup `.gauge`) ────────────────────────────────────────────────────────────────────────
 * A 270° arc (90° gap at the bottom): a #242634 track (stroke 7, round cap) with a coloured value arc grown
 * to `level / SERIOUS` and coloured green (≤ GOOD) → amber (≤ MIDDLE) → red (HEAVY+), round cap. The centre
 * stacks a big value (the Vietnamese level label — the meaningful readout, since the head unit exposes only
 * the 0..6 level, not raw µg/m³) over a small "PM2.5" caption. Degrade-safe: off-car / [Pm25Filter.INVALID]
 * (0) → empty arc + "—".
 */
class Pm25GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val trackColor = context.getColor(R.color.hairline)          // arc track (line)
    private val green = context.getColor(R.color.accent_green)           // ≤ GOOD
    private val amber = context.getColor(R.color.accent_amber)           // ≤ MIDDLE
    private val red = context.getColor(R.color.accent_red)               // HEAVY+
    private val inkPrimary = context.getColor(R.color.text_primary)      // centre value
    private val inkSecondary = context.getColor(R.color.text_secondary)  // "PM2.5" caption

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val startAngle = 135f    // bottom-left
    private val sweepTotal = 270f    // leave a 90° gap at the bottom

    private var level = Pm25Filter.INVALID

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = inkPrimary
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = inkSecondary
    }
    private val arcRect = RectF()

    /** Set the PM2.5 level ([Pm25Filter] 0..6). Out-of-range clamps; [Pm25Filter.INVALID] → empty arc + "—". */
    fun setLevel(levelIndex: Int) {
        val v = levelIndex.coerceIn(Pm25Filter.INVALID, Pm25Filter.SERIOUS)
        if (v != level) { level = v; invalidate() }
    }

    /**
     * Default 72 dp square when a dimension's spec is UNBOUNDED (wrap_content / UNSPECIFIED) so the gauge
     * never collapses to 0 px. [resolveSize] honours an EXACTLY spec (the layout pins 72×72 dp) unchanged;
     * [onDraw] already sizes off `min(w, h)` and centres, so a non-square measured size still renders cleanly.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = dp(50f).toInt()
        setMeasuredDimension(resolveSize(d, widthMeasureSpec), resolveSize(d, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val stroke = dp(7f)
        val inset = stroke / 2f + dp(2f)
        val size = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f
        arcRect.set(cx - size / 2f + inset, cy - size / 2f + inset, cx + size / 2f - inset, cy + size / 2f - inset)

        // Track.
        trackPaint.color = trackColor
        trackPaint.strokeWidth = stroke
        canvas.drawArc(arcRect, startAngle, sweepTotal, false, trackPaint)

        val valid = level in Pm25Filter.EXCELLENT..Pm25Filter.SERIOUS
        if (valid) {
            val fraction = level.toFloat() / Pm25Filter.SERIOUS.toFloat()
            arcPaint.color = colorForLevel(level)
            arcPaint.strokeWidth = stroke
            canvas.drawArc(arcRect, startAngle, sweepTotal * fraction, false, arcPaint)
        }

        // Centre: big value (Vi label — doubles as the readout) over a small "PM2.5" caption. INVALID → "—".
        val label = if (valid) Pm25Filter.levelLabelVi(level) else "—"
        valuePaint.textSize = fitText(label, arcRect.width() * 0.74f, size * 0.28f)
        val fm = valuePaint.fontMetrics
        canvas.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f - size * 0.05f, valuePaint)

        // Sub-label "PM2.5".
        labelPaint.textSize = size * 0.13f
        canvas.drawText("PM2.5", cx, cy + size * 0.28f, labelPaint)
    }

    private fun colorForLevel(l: Int): Int = when {
        l <= Pm25Filter.GOOD -> green          // 1–2 = clean
        l <= Pm25Filter.MIDDLE -> amber         // 3–4 = moderate
        else -> red                             // 5–6 = heavy / serious
    }

    /** Largest size ≤ [max] whose text width fits [maxWidth] (so long labels like "Nghiêm trọng" stay inside). */
    private fun fitText(text: String, maxWidth: Float, max: Float): Float {
        var size = max
        valuePaint.textSize = size
        while (size > dp(8f) && valuePaint.measureText(text) > maxWidth) {
            size -= dp(1f)
            valuePaint.textSize = size
        }
        return size
    }
}
