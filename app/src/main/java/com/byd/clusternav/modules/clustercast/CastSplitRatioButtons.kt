package com.byd.clusternav.modules.clustercast

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.simplified.CastProfile
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import java.util.concurrent.Executors

/**
 * The 9 visual split-ratio buttons (Feature 2) that replace the old `spinner_split_ratio`.
 *
 * One cell per [CastProfile.SPLIT_PERCENTS] value (leftPercent 10…90). The cell IS the control: a
 * single rounded bar split into two colours by the ratio it represents (left share vs right share),
 * with the `left:right` number sitting INSIDE the bar. No outer button frame (owner 2026-08-12: the
 * framed look was ugly) — selection is shown by a thin accent border only.
 *
 * On tap it wires to the LIVE simplified path: [SimpleCastCoordinator.applySplitRatioLive] persists
 * the ratio to the simplified prefs AND, if the cluster is currently split, re-resizes both slots in
 * place (no return+recast). This is the LIVE store the running cast reads — NOT the disconnected
 * legacy v2 `CastSplitRatioStore`.
 *
 * A separate controller file (bound by [MainActivityCastController]) so that file stays under its LOC
 * contract. The initial selection is read off the main thread; the coordinator's own executor
 * performs the actual persist + resize.
 */
internal class CastSplitRatioButtons(
    private val activity: Activity,
    private val coordinator: SimpleCastCoordinator,
) {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "split-ratio-ui") }
    private val cells = LinkedHashMap<Int, FrameLayout>()

    fun bind() {
        val container = activity.findViewById<LinearLayout>(R.id.split_ratio_buttons) ?: return
        container.removeAllViews()
        cells.clear()

        CastProfile.SPLIT_PERCENTS.forEach { pct ->
            val cell = buildCell(pct)
            cells[pct] = cell
            container.addView(cell)
            cell.setOnClickListener {
                highlight(pct) // instant UI feedback
                // applySplitRatioLive is non-blocking (queues on the coordinator's serial executor,
                // which does the off-main persist + in-place resize).
                runCatching { coordinator.applySplitRatioLive(pct) }
            }
        }

        // Initial selection: read the persisted ratio off the main thread, then highlight.
        io.execute {
            val current = runCatching { coordinator.prefs.splitRatioLeftPercent() }
                .getOrDefault(CastProfile.DEFAULT_PERCENT)
            val normalized = CastProfile.normalizePercent(current)
            activity.runOnUiThread { highlight(normalized) }
        }
    }

    /** Release the background executor (call from the host's onDestroy). */
    fun destroy() {
        io.shutdownNow()
    }

    /**
     * One cell = a rounded, ratio-split colour bar with the `l:r` number centred inside it.
     * The bar's two segments are weighted by [pct] / (100-[pct]) so the cell literally shows the
     * split. No frame; corners are clipped round. Selection border is applied in [highlight].
     */
    private fun buildCell(pct: Int): FrameLayout {
        val cell = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                .apply { setMargins(dp(3), 0, dp(3), 0) }
            isClickable = true
            isFocusable = true
            contentDescription = "Tỉ lệ ${pct / 10}:${(100 - pct) / 10}"
            // Rounded clip so the colour bar (child) gets rounded corners; bg transparent supplies the outline.
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.TRANSPARENT)
            }
            clipToOutline = true
        }

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        bar.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct.toFloat())
                setBackgroundColor(SEG_LEFT)
            },
        )
        bar.addView(
            View(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100 - pct).toFloat())
                setBackgroundColor(SEG_RIGHT)
            },
        )

        // Number INSIDE the bar, centred; white + shadow so it reads over either segment colour.
        val label = TextView(activity).apply {
            text = "${pct / 10}:${(100 - pct) / 10}"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
            ).apply { gravity = Gravity.CENTER }
        }

        cell.addView(bar)
        cell.addView(label)
        return cell
    }

    /** Selection = a thin accent border on the chosen cell (drawn as foreground so it never hides the bar). */
    private fun highlight(selectedPct: Int) {
        cells.forEach { (pct, cell) ->
            cell.foreground = if (pct == selectedPct) {
                GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(3), STROKE_SELECTED)
                }
            } else {
                null
            }
        }
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        val SEG_LEFT: Int = Color.parseColor("#3B82F6") // accent — the left app's share
        val SEG_RIGHT: Int = Color.parseColor("#64748B") // slate — the right app's share (dark enough for white text)
        val STROKE_SELECTED: Int = Color.parseColor("#1D4ED8") // selected-cell border
    }
}
