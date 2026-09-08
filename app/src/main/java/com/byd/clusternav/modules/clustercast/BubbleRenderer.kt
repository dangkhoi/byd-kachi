package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState

/**
 * Renders the single-icon floating bubble (R5 / #7 — docs/specs/cast-nav-ux-release-v104.html).
 *
 * The bubble is ONE navigation-arrow icon sized like an app icon (~[ICON_SIZE_DP]dp), with **no
 * background / border / fill** — just the vector arrow ([R.drawable.ic_bubble_nav]). Idle/active
 * dimming is the WINDOW alpha owned by [FloatingBubbleService], not a per-view background. The prior
 * three-zone (Trái/Phải/Full) layout, its painting and its zone-hit-test are gone; slot casting now
 * lives in the long-press submenu ([BubbleSubmenuOverlay]).
 *
 * Owns: icon-view creation and a state-driven content description. Gesture disambiguation lives in
 * [BubbleGestureHandler]; action dispatch in [BubbleActionDispatcher].
 */
internal class BubbleRenderer(private val context: Context) {

    /** The single bubble icon, or null before [buildBubble] / after [clearViews]. */
    var iconView: ImageView? = null
        private set

    /**
     * Build the one-icon bubble: a transparent [ImageView] showing only the nav arrow. The view has
     * no background, so the cluster/app content shows through around the glyph. `minimumWidth`/
     * `minimumHeight` force a ≥[ICON_SIZE_DP]dp box (well above the [TOUCH_MIN_DP]dp automotive
     * touch-target floor) even though the vector's intrinsic size is smaller. It is clickable,
     * long-clickable and focusable so the gesture handler and TalkBack both see one actionable
     * target. Window WRAP_CONTENT + this minimum give the bubble its size (see
     * [FloatingBubbleService.showBubble]).
     */
    fun buildBubble(): View {
        val size = dp(ICON_SIZE_DP)
        val pad = dp(ICON_PADDING_DP)
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_bubble_nav)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = null // R5: no background / border / fill — just the arrow
            minimumWidth = size
            minimumHeight = size
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isLongClickable = true
            isFocusable = true
            contentDescription = CONTENT_DESC_IDLE
        }
        iconView = icon
        return icon
    }

    /**
     * Update ONLY the icon's content description to reflect whether a tap will cast or return
     * (no background/tint change — the arrow stays a plain transparent icon, R5). Returns true when
     * a view exists to update. The visual idle/active state is the window alpha, handled elsewhere.
     */
    fun refreshFromState(state: SimpleCastState): Boolean {
        val icon = iconView ?: return false
        icon.contentDescription = contentDescriptionFor(state)
        return true
    }

    fun clearViews() {
        iconView = null
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()

    companion object {
        /** Rendered icon side (dp) — app-icon sized, within the 48–56dp band the spec (#7) asks for. */
        internal const val ICON_SIZE_DP = 52

        /** Automotive minimum touch target (dp) enforced for the icon AND each submenu row (R5/R9). */
        internal const val TOUCH_MIN_DP = 48

        /** Inset so the arrow reads as an ~app-icon glyph rather than edge-to-edge. */
        internal const val ICON_PADDING_DP = 6

        /** Accessibility label when nothing is on the cluster (a tap will cast the foreground). */
        internal const val CONTENT_DESC_IDLE = "ClusterNav cast"

        /** Accessibility label while casting (a tap will return to the cluster gauges). */
        internal const val CONTENT_DESC_CASTING = "ClusterNav cast · chạm để trả về"

        /** Accessibility label during a transient state (not actionable yet). */
        internal const val CONTENT_DESC_BUSY = "ClusterNav cast · đang xử lý"

        /**
         * Content description for a cast state — pure so it is unit-testable without a Context.
         * Idle → [CONTENT_DESC_IDLE]; casting (full or split) → [CONTENT_DESC_CASTING]; any transient
         * state → [CONTENT_DESC_BUSY]. Mirrors [BubbleGesturePlanner.tapOutcome] so the spoken hint
         * always matches what the tap will do.
         */
        fun contentDescriptionFor(state: SimpleCastState): String = when (state) {
            is SimpleCastState.Idle -> CONTENT_DESC_IDLE
            is SimpleCastState.CastingFull, is SimpleCastState.CastingSplit -> CONTENT_DESC_CASTING
            else -> CONTENT_DESC_BUSY
        }
    }
}
