package com.byd.clusternav.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.byd.clusternav.R

/**
 * CLUSTER PREVIEW — a mini rounded-rect preview of the instrument cluster (Level-2 cockpit UI, task
 * `ui-visual-upgrade-l2`, ref `docs/specs/ui-visual-upgrade-l2.html` `.mini-cl`). FRAMEWORK-only custom
 * [View] drawn with [Canvas]: a recessed rounded rectangle that is either FULL or SPLIT into a LEFT
 * (blue-tinted) and RIGHT (purple-tinted) region by a ratio, with a thin blue divider and an optional
 * centred label. Pure UI — the owner drives it from the cast state.
 *
 * ── API ───────────────────────────────────────────────────────────────────────────────────────────
 *  • [setSplit] — show a split cluster; `leftFraction` (clamped 0.05..0.95) is the LEFT app's share, `label`
 *    is an optional centred caption (e.g. "GMaps · VietMap (40:60)"). null label → no caption.
 *  • [setFull] — show a single full-cluster app with an optional centred `label`.
 *
 * Degrade-safe: nothing drawn before measure; default footprint so it never collapses in wrap_content.
 */
class ClusterPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val bg = context.getColor(R.color.cluster_face)              // recessed cluster face
    private val border = context.getColor(R.color.hairline_strong)       // face border
    private val leftTint = context.getColor(R.color.cluster_tint_left)   // ~16% blue left half
    private val rightTint = context.getColor(R.color.cluster_tint_right) // ~14% purple right half
    private val divider = context.getColor(R.color.accent_blue)          // split divider
    private val inkLabel = context.getColor(R.color.text_tertiary)       // caption

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    /** LEFT share when split; `null` = full (single app). */
    private var leftFraction: Float? = null
    private var label: String? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = divider
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = inkLabel
    }
    private val clip = Path()
    private val rect = RectF()

    /** Show a split cluster: [leftFraction] is the LEFT app's share (clamped 0.05..0.95); [label] optional. */
    fun setSplit(leftFraction: Float, label: String?) {
        this.leftFraction = leftFraction.coerceIn(0.05f, 0.95f)
        this.label = label
        invalidate()
    }

    /** Show a single full-cluster app with an optional centred [label]. */
    fun setFull(label: String?) {
        this.leftFraction = null
        this.label = label
        invalidate()
    }

    /** Default ~98×38dp when a dimension is UNBOUNDED (wrap_content) so the preview never collapses. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(98f).toInt(), widthMeasureSpec),
            resolveSize(dp(38f).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val stroke = dp(1f)
        val radius = dp(12f)   // v1.34 (FIX 2): corner radius restored to v1.32 (cosmetic)
        rect.set(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f)

        // Clip everything to the rounded cluster face.
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clip)

        // Base face.
        fillPaint.color = bg
        canvas.drawRect(0f, 0f, w, h, fillPaint)

        // Split tints + divider.
        val frac = leftFraction
        if (frac != null) {
            val splitX = w * frac
            fillPaint.color = leftTint
            canvas.drawRect(0f, 0f, splitX, h, fillPaint)
            fillPaint.color = rightTint
            canvas.drawRect(splitX, 0f, w, h, fillPaint)
            dividerPaint.strokeWidth = stroke
            canvas.drawLine(splitX, 0f, splitX, h, dividerPaint)
        }
        canvas.restoreToCount(save)

        // Border on top (not clipped so the stroke is crisp).
        strokePaint.color = border
        strokePaint.strokeWidth = stroke
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        // Optional centred caption.
        val text = label
        if (!text.isNullOrEmpty()) {
            labelPaint.textSize = minOf(h * 0.22f, dp(11f))   // v1.34 (FIX 2): text cap restored to v1.32
            val fm = labelPaint.fontMetrics
            canvas.drawText(text, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, labelPaint)
        }
    }
}
