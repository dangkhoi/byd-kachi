package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.simplified.BubbleGesturePlanner
import com.byd.clusternav.modules.clustercast.simplified.BubbleSubmenuAnchor
import com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide

/**
 * Compact long-press menu shown BESIDE the single-icon bubble (R5 / #7). Rows come from
 * [BubbleGesturePlanner.submenuItems] — Trái / Phải / Cấu hình — each a leading SVG glyph + label,
 * divided by hairline built-in dividers, on a small semi-transparent rounded card. The card WRAPS to
 * the text (≈60% of the old fixed width) and rows are compact (~[ROW_HEIGHT_DP]dp). A full-screen
 * transparent scrim backs it so an outside tap dismisses; placement is [BubbleSubmenuAnchor.offset]
 * (beside the bubble, clamped on-screen). One overlay at a time; [dismiss] is idempotent.
 */
internal class BubbleSubmenuOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private var root: View? = null

    fun isShowing(): Boolean = root != null

    /**
     * Show the menu anchored beside the bubble at ([anchorX],[anchorY]) sized [anchorW]×[anchorH]
     * (the bubble window rect). [onAction] receives the chosen action; the row dismisses first.
     */
    fun show(
        anchorX: Int,
        anchorY: Int,
        anchorW: Int,
        anchorH: Int,
        onAction: (BubbleGesturePlanner.BubbleMenuAction) -> Unit,
    ) {
        if (root != null) return

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true // consume taps so they don't fall through to the scrim (dismiss)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(CARD_CORNER_DP).toFloat()
                setColor(CARD_BG)
            }
            elevation = dp(CARD_ELEVATION_DP).toFloat()
            val v = dp(CARD_VPAD_DP)
            setPadding(0, v, 0, v)
            // Hairline dividers BETWEEN rows — drawn at the wrapped content width (no MATCH_PARENT stretch).
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerPadding = dp(ROW_PADDING_H_DP)
            dividerDrawable = GradientDrawable().apply {
                setSize(0, dp(1))
                setColor(SEPARATOR)
            }
        }
        BubbleGesturePlanner.submenuItems().forEach { item ->
            card.addView(rowView(item) { dismiss(); onAction(item.action) })
        }

        // WRAP to content (just fits the text) then place BESIDE the bubble (not centred).
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        card.measure(spec, spec)
        val dm = context.resources.displayMetrics
        val (leftMargin, topMargin) = BubbleSubmenuAnchor.offset(
            dm.widthPixels, dm.heightPixels,
            anchorX, anchorY, anchorW, anchorH,
            card.measuredWidth, card.measuredHeight,
            dp(GAP_DP), dp(EDGE_MARGIN_DP),
        )

        val scrim = FrameLayout(context).apply {
            setOnClickListener { dismiss() }
            addView(
                card,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    this.leftMargin = leftMargin
                    this.topMargin = topMargin
                },
            )
        }

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        root = scrim
        runCatching { windowManager.addView(scrim, layout) }
    }

    /** Remove the overlay if present. Idempotent. */
    fun dismiss() {
        val view = root ?: return
        root = null
        runCatching { windowManager.removeView(view) }
    }

    private fun rowView(item: BubbleGesturePlanner.BubbleSubmenuItem, onTap: () -> Unit): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            minimumHeight = dp(ROW_HEIGHT_DP) // compact rows (~80% of the 48dp automotive size, owner request)
            val h = dp(ROW_PADDING_H_DP)
            setPadding(h, 0, h, 0)
            isClickable = true
            isFocusable = true
            contentDescription = item.label
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onTap()
            }
        }
        row.addView(
            ImageView(context).apply {
                setImageResource(iconFor(item.action))
                layoutParams = LinearLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP)).apply {
                    marginEnd = dp(ICON_GAP_DP)
                }
            },
        )
        row.addView(
            TextView(context).apply {
                text = item.label
                setTextColor(TEXT)
                textSize = ROW_TEXT_SP
                maxLines = 1
            },
        )
        return row
    }

    /** Leading glyph per row: Trái ◀ / Phải ▶ / Cấu hình ⚙ (mapped via the pure slot mapping). */
    private fun iconFor(action: BubbleGesturePlanner.BubbleMenuAction): Int =
        when (BubbleGesturePlanner.slotFor(action)) {
            ClusterSlotSide.LEFT -> R.drawable.ic_menu_left
            ClusterSlotSide.RIGHT -> R.drawable.ic_menu_right
            null -> R.drawable.ic_menu_config
        }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()

    companion object {
        internal const val ROW_TEXT_SP = 15f
        internal const val ROW_PADDING_H_DP = 14
        internal const val ROW_HEIGHT_DP = 40
        internal const val ICON_DP = 20
        internal const val ICON_GAP_DP = 10
        internal const val CARD_CORNER_DP = 14
        internal const val CARD_VPAD_DP = 2
        internal const val CARD_ELEVATION_DP = 6
        internal const val GAP_DP = 10
        internal const val EDGE_MARGIN_DP = 8

        /** Semi-transparent light card (~80% white) — lighter/simpler than the old opaque plate. */
        internal val CARD_BG = 0xCCFFFFFF.toInt()

        /** Hairline row divider (~12% black). */
        internal const val SEPARATOR = 0x1F000000

        /** Row label colour (near-black) — legible on the light card. */
        internal val TEXT = 0xFF1D1D1F.toInt()
    }
}
