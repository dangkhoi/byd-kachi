package com.byd.clusternav.comfort

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.byd.clusternav.R

/**
 * SEAT DIAGRAM — top-down car outline with tappable seats (Level-2 cockpit UI, task
 * `ui-visual-upgrade-l2`, ref `docs/specs/ui-visual-upgrade-l2.html` `.car`). Replaces the old per-seat radio
 * rows (`seat0_group`…`seat3_l2`) with a single glanceable, chữ-ký component: chạm ghế để đổi mức.
 *
 * FRAMEWORK-only custom View (no AppCompat / Material) drawn with [Canvas]. Pure UI — it holds NO HAL / prefs
 * coupling; the owner ([com.byd.clusternav.MainActivity.setupSeatComfortControls]) seeds it from
 * [Prefs.seatComfortLevel]/[Prefs.seatComfortMode] and, on [onSeatLevelChanged], persists to
 * [Prefs.seatComfortLevel] + calls [SeatComfortApplier.applyNow] — the SAME persistence + apply behaviour the
 * radios had, so the feature is intact.
 *
 * ── Model ─────────────────────────────────────────────────────────────────────────────────────────
 *  • [setSeatCount] — 2 (front only, Seal) or 4 (Han). Off-car default 2. Rear seats are always DRAWN but
 *    dimmed (α .35) and non-tappable when the car has only 2 seats.
 *  • [setMode] — cool (cyan #22d3ee glow) vs heat (amber #fbbf24 glow); mutually exclusive (matches HAL).
 *  • [setLevel]/[getLevel] — per-seat level 0=Tắt / 1=Mức 1 / 2=Mức 2.
 *  • TAP a seat cycles 0→1→2→0 and fires [onSeatLevelChanged].
 *
 * ── Render (mockup `.car`) ──────────────────────────────────────────────────────────────────────────
 * Body = rounded-top capsule (top 26 / bottom 20) with a vertical gradient + line2 stroke; 4 seats at
 * fl/fr/rl/rr, each drawn as a top-down car-seat silhouette — a rounded seat CUSHION (base) + a slightly
 * narrower rounded BACKREST band at the top + two thin side BOLSTERS (raised left/right sides). OFF = #171922
 * fill + line2 outline (the part outlines still make the seat shape read); level ≥ 1 tinted by mode with
 * intensity by level (cushion fill α + brighter raised parts + glow ring + stroke width) and an 'M1'/'M2' +
 * name badge. Disabled (master switch OFF) → whole diagram drawn faint + touches ignored.
 *
 * Degrade-safe: nothing drawn before the view is measured; every value clamps; hidden seats (index ≥
 * [seatCount]) are drawn dim but never hit-tested.
 */
class SeatDiagramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // ── Palette (theme-adaptive: read from @color so LIGHT + DARK both resolve; a theme change calls
    //    recreate() → a fresh instance re-reads these. Dark values equal the former hardcoded hex exactly). ──
    private val coolColor = context.getColor(R.color.accent_cyan)        // cyan
    private val heatColor = context.getColor(R.color.accent_amber)       // amber
    private val bodyStroke = context.getColor(R.color.hairline_strong)   // line2
    private val bodyTop = context.getColor(R.color.seat_body_top)        // body gradient top
    private val bodyBottom = context.getColor(R.color.seat_body_bottom)  // body gradient bottom
    private val offStroke = context.getColor(R.color.hairline_strong)    // line2
    private val offFill = context.getColor(R.color.seat_off_fill)        // seat off fill
    private val labelColor = context.getColor(R.color.text_primary)      // text_primary
    private val offLabelColor = context.getColor(R.color.text_secondary) // off-seat label grey

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var seatCount = 2
    private var coolMode = true
    private val levels = intArrayOf(0, 0, 0, 0)
    private val seatNames = arrayOf("Lái", "Phụ", "Sau T", "Sau P")

    /** Fired when a seat is tapped and its level cycles. `seat` = 0=FL,1=FR,2=RL,3=RR; `level` = new 0/1/2. */
    var onSeatLevelChanged: ((seat: Int, level: Int) -> Unit)? = null

    /** Seat hit-rects in view coords, index-aligned (0=FL,1=FR,2=RL,3=RR). Recomputed each draw. */
    private val seatRects = arrayOfNulls<RectF>(4)
    private var downSeat = -1

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }
    private val bodyPath = Path()
    private val bodyRadii = FloatArray(8)

    fun setSeatCount(count: Int) {
        val c = if (count >= 4) 4 else 2
        if (c != seatCount) { seatCount = c; invalidate() }
    }

    fun setMode(cool: Boolean) {
        if (cool != coolMode) { coolMode = cool; invalidate() }
    }

    fun setLevel(i: Int, level: Int) {
        if (i in 0..3) {
            val v = level.coerceIn(0, 2)
            if (levels[i] != v) { levels[i] = v; invalidate() }
        }
    }

    fun getLevel(i: Int): Int = if (i in 0..3) levels[i] else 0

    /**
     * Default cockpit footprint when a dimension's spec is UNBOUNDED (wrap_content / UNSPECIFIED — e.g. nested
     * in a ScrollView) so the diagram NEVER collapses to 0 px and vanishes. [resolveSize] leaves an EXACTLY
     * spec untouched, so the layout's fixed height renders exactly as before; this only backstops the
     * wrap_content case a plain [View] would otherwise measure at the (0 px) suggested minimum.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(182f).toInt(), widthMeasureSpec),
            resolveSize(dp(105f).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val restore = if (!isEnabled) {
            canvas.saveLayerAlpha(0f, 0f, w, h, 100)   // faint whole diagram when master OFF
        } else {
            -1
        }

        // ── Car body: centred, taller-than-wide (mockup 120×150 ≈ 0.8), rounded-top capsule ──────────
        val pad = dp(8f)
        val carW = minOf(w - 2f * pad, (h - 2f * pad) * 0.80f)
        val carLeft = (w - carW) / 2f
        val carTop = pad
        val carBottom = h - pad
        val carH = carBottom - carTop
        val carRight = carLeft + carW
        val topR = carW * 0.21f   // ~26/120
        val botR = carW * 0.16f   // ~20/120

        val bodyRect = RectF(carLeft, carTop, carRight, carBottom)
        setRadii(bodyRadii, topR, botR)
        bodyPath.reset()
        bodyPath.addRoundRect(bodyRect, bodyRadii, Path.Direction.CW)
        fillPaint.shader = LinearGradient(carLeft, carTop, carLeft, carBottom, bodyTop, bodyBottom, Shader.TileMode.CLAMP)
        canvas.drawPath(bodyPath, fillPaint)
        fillPaint.shader = null
        strokePaint.color = bodyStroke
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawPath(bodyPath, strokePaint)

        // ── Seat geometry (mockup fl/fr top:58, rl/rr bottom:14 of a 120×150 body) ────────────────────
        val seatW = carW * 0.317f
        val seatH = carH * 0.267f
        val leftCx = carLeft + carW * 0.275f
        val rightCx = carRight - carW * 0.275f
        val frontCy = carTop + carH * 0.30f
        val rearCy = carTop + carH * 0.66f

        seatRects[0] = seatRect(leftCx, frontCy, seatW, seatH)
        seatRects[1] = seatRect(rightCx, frontCy, seatW, seatH)
        seatRects[2] = seatRect(leftCx, rearCy, seatW, seatH)
        seatRects[3] = seatRect(rightCx, rearCy, seatW, seatH)

        for (i in 0 until 4) {
            val r = seatRects[i] ?: continue
            drawSeat(canvas, r, levels[i], seatNames[i], dim = i >= seatCount)
        }

        if (restore >= 0) canvas.restoreToCount(restore)
    }

    private fun setRadii(out: FloatArray, top: Float, bottom: Float) {
        out[0] = top; out[1] = top       // top-left
        out[2] = top; out[3] = top       // top-right
        out[4] = bottom; out[5] = bottom // bottom-right
        out[6] = bottom; out[7] = bottom // bottom-left
    }

    private fun seatRect(cx: Float, cy: Float, sw: Float, sh: Float): RectF =
        RectF(cx - sw / 2f, cy - sh / 2f, cx + sw / 2f, cy + sh / 2f)

    /**
     * A top-down car-seat glyph inside the (UNCHANGED) hit-test rect [r]: a rounded seat CUSHION (base), a
     * slightly narrower rounded BACKREST band at the top, and two thin side BOLSTERS (the raised left/right
     * sides). OFF renders in the neutral off palette (the part outlines still make the seat shape read); an
     * active seat tints by mode with the raised parts (backrest + bolsters) brighter than the cushion, plus a
     * glow ring and a thicker stroke at level 2.
     */
    private fun drawSeat(canvas: Canvas, r: RectF, level: Int, name: String, dim: Boolean) {
        val save = if (dim) canvas.saveLayerAlpha(r.left - dp(8f), r.top - dp(8f), r.right + dp(8f), r.bottom + dp(8f), 89) else -1

        // Glyph box — a small breathing inset inside the seat rect so the glow reads and the shape doesn't
        // butt against the neighbouring seat. The hit-test rect [r] itself is left untouched.
        val inset = r.width() * 0.06f
        val gl = r.left + inset
        val gr = r.right - inset
        val gt = r.top + inset
        val gb = r.bottom - inset
        val gw = gr - gl
        val gh = gb - gt

        // Cushion (base): the lower ~72% of the glyph, generously rounded.
        val cushionTop = gt + gh * 0.28f
        val cushionR = gw * 0.22f
        // Backrest: a narrower rounded band across the top ~36%, overlapping the cushion top a touch.
        val backInset = gw * 0.15f
        val backLeft = gl + backInset
        val backRight = gr - backInset
        val backBottom = gt + gh * 0.36f
        val backR = gw * 0.17f
        // Bolsters: two thin rounded pillars hugging the cushion's left/right edges (the raised sides).
        val bolW = gw * 0.16f
        val bolTop = gt + gh * 0.31f
        val bolBottom = gb - gh * 0.05f
        val bolR = bolW * 0.5f

        val on = level >= 1
        val strong = level >= 2
        val tint = if (coolMode) coolColor else heatColor

        // Glow ring behind the whole glyph (active only) — a translucent, slightly larger rounded rect.
        if (on) {
            val grow = if (strong) dp(6f) else dp(3f)
            glowPaint.color = withAlpha(tint, if (strong) 92 else 56)
            canvas.drawRoundRect(gl - grow, gt - grow, gr + grow, gb + grow, cushionR + grow, cushionR + grow, glowPaint)
        }

        val cushionFill = if (on) withAlpha(tint, if (strong) 74 else 46) else offFill
        val raisedFill = if (on) withAlpha(tint, if (strong) 132 else 92) else offFill

        // Fills first (cushion dim, raised parts brighter so bolsters/backrest read as raised).
        fillPaint.color = cushionFill
        canvas.drawRoundRect(gl, cushionTop, gr, gb, cushionR, cushionR, fillPaint)
        fillPaint.color = raisedFill
        canvas.drawRoundRect(gl, bolTop, gl + bolW, bolBottom, bolR, bolR, fillPaint)
        canvas.drawRoundRect(gr - bolW, bolTop, gr, bolBottom, bolR, bolR, fillPaint)
        canvas.drawRoundRect(backLeft, gt, backRight, backBottom, backR, backR, fillPaint)

        // Outlines last so every part edge stays crisp (thicker at level 2).
        strokePaint.color = if (on) tint else offStroke
        strokePaint.strokeWidth = if (strong) dp(2.4f) else dp(1.6f)
        canvas.drawRoundRect(gl, cushionTop, gr, gb, cushionR, cushionR, strokePaint)
        canvas.drawRoundRect(gl, bolTop, gl + bolW, bolBottom, bolR, bolR, strokePaint)
        canvas.drawRoundRect(gr - bolW, bolTop, gr, bolBottom, bolR, bolR, strokePaint)
        canvas.drawRoundRect(backLeft, gt, backRight, backBottom, backR, backR, strokePaint)

        drawSeatLabel(canvas, r, name, level, off = !on)
        if (save >= 0) canvas.restoreToCount(save)
    }

    /** Small centred badge: seat name, plus "·M1"/"·M2" when active. Shrinks to fit the seat width. */
    private fun drawSeatLabel(canvas: Canvas, r: RectF, name: String, level: Int, off: Boolean) {
        val text = if (level >= 1) "$name·M$level" else name
        textPaint.color = if (off) offLabelColor else labelColor
        var size = r.height() * 0.24f
        textPaint.textSize = size
        val maxW = r.width() * 0.86f
        while (size > dp(7f) && textPaint.measureText(text) > maxW) {
            size -= dp(0.5f)
            textPaint.textSize = size
        }
        val fm = textPaint.fontMetrics
        canvas.drawText(text, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downSeat = seatAt(event.x, event.y)
                return downSeat >= 0
            }
            MotionEvent.ACTION_UP -> {
                val up = seatAt(event.x, event.y)
                if (up >= 0 && up == downSeat) {
                    val next = (levels[up] + 1) % 3
                    levels[up] = next
                    invalidate()
                    performClick()
                    onSeatLevelChanged?.invoke(up, next)
                    downSeat = -1
                    return true
                }
                downSeat = -1
            }
            MotionEvent.ACTION_CANCEL -> downSeat = -1
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Visible seat whose rect contains the point, else -1 (hidden seats index ≥ [seatCount] excluded). */
    private fun seatAt(x: Float, y: Float): Int {
        for (i in 0 until seatCount) {
            if (seatRects[i]?.contains(x, y) == true) return i
        }
        return -1
    }
}
