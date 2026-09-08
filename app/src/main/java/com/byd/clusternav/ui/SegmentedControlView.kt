package com.byd.clusternav.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R

/**
 * SEGMENTED CONTROL — a rounded-track pill with N mutually-exclusive text segments (Level-2 cockpit UI, task
 * `ui-visual-upgrade-l2`, ref `docs/specs/ui-visual-upgrade-l2.html` `.seg`). FRAMEWORK-only (no AppCompat /
 * Material): a [LinearLayout] whose track is [R.drawable.segment_track_bg] and whose SELECTED child sits on
 * [R.drawable.segment_selected_bg] (blue→indigo) — or [R.drawable.segment_selected_warm_bg] (pink→amber) when
 * [warm] is true. Selected text is white; unselected text is muted ink (`@color/text_secondary`).
 *
 * Reusable replacement (Stage 2) for the cluster-mode Spinner, the seat cool/heat RadioGroup and the per-seat
 * level radios. Pure UI — no HAL / prefs coupling; the owner wires [onSelected].
 *
 * ── API ───────────────────────────────────────────────────────────────────────────────────────────
 *  • [setOptions] — set the segment labels (rebuilds children; clamps [selectedIndex]).
 *  • [selectedIndex] — the selected segment; setting it programmatically updates visuals but does NOT fire
 *    [onSelected] (only a user tap does), so seeding from prefs can't echo a false event.
 *  • [onSelected] — invoked with the new index when the USER taps a segment.
 *  • [warm] — swaps the selected fill to the warm pink→amber gradient (e.g. the seat heat/cool toggle).
 *
 * Degrade-safe: empty options → no children; taps clamp to range; re-setting the same index is idempotent.
 */
class SegmentedControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).toInt()

    private val selectedInk = context.getColor(R.color.white)          // white on the gradient
    private val idleInk = context.getColor(R.color.text_secondary)     // idle segment ink

    private var options: List<String> = emptyList()
    private val segViews = ArrayList<TextView>()

    /** Invoked with the new index when the USER taps a segment (not on programmatic [selectedIndex]). */
    var onSelected: ((Int) -> Unit)? = null

    /** Selected segment index. Setter re-styles only (never fires [onSelected]); clamps to the option range. */
    var selectedIndex: Int = 0
        set(value) {
            field = if (options.isEmpty()) 0 else value.coerceIn(0, options.size - 1)
            updateStyles()
        }

    /** When true the selected segment uses the warm pink→amber gradient instead of blue→indigo. */
    var warm: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateStyles()
            }
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = context.getDrawable(R.drawable.segment_track_bg)
        // loosen-spacing: horizontal track inset 3dp (mockup `.seg{padding:3px}`); vertical inset raised
        // 2->3dp so the segmented control is not cramped (owner on-car: elements felt too tight after v1.34).
        val ph = dp(3f)
        val pv = dp(3f)
        setPadding(ph, pv, ph, pv)
    }

    /** Replace the segment labels. Rebuilds the child TextViews; keeps [selectedIndex] in range. */
    fun setOptions(labels: List<String>) {
        options = labels.toList()
        removeAllViews()
        segViews.clear()
        options.forEachIndexed { index, label ->
            val tv = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)        // v1.34 (FIX 2): text restored (was 9f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(13f), dp(5f), dp(13f), dp(5f))          // loosen-spacing: horiz 13dp; vert 4->5dp (mockup `.seg span{padding:6px 13px}`, ~90%) so labels breathe
                isClickable = true
                isFocusable = true
                setOnClickListener { select(index, fromUser = true) }
            }
            addView(tv, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            segViews.add(tv)
        }
        if (selectedIndex >= options.size) selectedIndex = 0 else updateStyles()
    }

    private fun select(index: Int, fromUser: Boolean) {
        if (options.isEmpty()) return
        val clamped = index.coerceIn(0, options.size - 1)
        val changed = clamped != selectedIndex
        selectedIndex = clamped                                        // setter re-styles
        if (fromUser && changed) onSelected?.invoke(clamped)
    }

    private fun updateStyles() {
        val selectedBg = if (warm) R.drawable.segment_selected_warm_bg else R.drawable.segment_selected_bg
        segViews.forEachIndexed { index, tv ->
            if (index == selectedIndex) {
                tv.background = context.getDrawable(selectedBg)
                tv.setTextColor(selectedInk)
            } else {
                tv.background = null
                tv.setTextColor(idleInk)
            }
        }
    }
}
