package com.byd.clusternav.modules.clustercast

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.Lang
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.simplified.CastProfile
import com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.modules.clustercast.simplified.SlotState

/**
 * Manages the cast geometry (resize) editor and display-global DPI control.
 *
 * Extracted from MainActivityCastController to keep each file ≤ 400 LOC.
 * Uses dynamic display dimensions from coordinator state instead of hardcoded 1920×720.
 * Replaces two per-package DPI buttons with one display-global DPI control.
 */
internal class CastGeometryEditor(
    private val activity: Activity,
    private val coordinator: SimpleCastCoordinator,
) {
    private val geometryContainer: FrameLayout by lazy {
        activity.findViewById(R.id.cast_geometry_container)
    }

    // Label index into [densityCycle]; re-initialised per editor build from the actual/saved density
    // (see [densityIndexFor]) so "DPI: N" matches reality. Owner 2026-08-12: it used to be pinned to
    // index 0 ("320") even when the applied DPI was the 240 default → confusing label.
    private var densityIndex = DEFAULT_DENSITY_INDEX
    private val densityCycle = intArrayOf(320, 240, 160)

    /** Cycle index for a persisted density string ("240"/"reset"/null); unknown/reset → 240 default. */
    private fun densityIndexFor(density: String?): Int {
        val dpi = density?.toIntOrNull() ?: return DEFAULT_DENSITY_INDEX
        val idx = densityCycle.indexOf(dpi)
        return if (idx >= 0) idx else DEFAULT_DENSITY_INDEX
    }

    /**
     * Show or hide the geometry editor based on current state.
     * During transient states, visibility is unchanged to prevent flicker.
     */
    fun updateVisibility(state: SimpleCastState) {
        when {
            state is SimpleCastState.CastingFull && state.appType.isProtected -> {
                geometryContainer.visibility = View.GONE
            }
            state is SimpleCastState.CastingFull && state.appType.isResizable -> {
                geometryContainer.visibility = View.VISIBLE
                setupResizeControls(state)
            }
            state is SimpleCastState.CastingSplit -> {
                geometryContainer.visibility = View.VISIBLE
                setupSplitResizeControls(state)
            }
            state is SimpleCastState.Idle || state is SimpleCastState.Off || state is SimpleCastState.Error -> {
                geometryContainer.visibility = View.GONE
            }
            state is SimpleCastState.Opening || state is SimpleCastState.Stopping || state is SimpleCastState.Closing -> {
                // Transient — keep current visibility to avoid panel flicker
            }
            else -> {
                geometryContainer.visibility = View.GONE
            }
        }
    }

    /**
     * Resize editor for full-mode normal apps.
     * Display dimensions come from the active display config (not hardcoded).
     */
    private fun setupResizeControls(state: SimpleCastState.CastingFull) {
        val tag = "resize_rect_${state.targetPkg}"
        if (geometryContainer.tag == tag) return
        geometryContainer.tag = tag
        geometryContainer.removeAllViews()

        val dp = activity.resources.displayMetrics.density

        // Read actual display dimensions from active config
        val displayConfig = state.displayConfig
        val wmParts = displayConfig.wmSize.split("x")
        val clusterWidth = wmParts.getOrNull(0)?.toIntOrNull() ?: 1920
        val clusterHeight = wmParts.getOrNull(1)?.toIntOrNull() ?: 720

        val outerLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        }

        val resizeView = CastResizeView(activity, clusterWidth, clusterHeight) { l, t, r, b ->
            coordinator.resizeActiveTarget(l, t, r, b)
        }
        val rectHeight = (140 * dp).toInt()
        resizeView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            rectHeight,
        )
        // Default bounds from display config
        val defaultBounds = displayConfig.bounds
        resizeView.setBounds(
            defaultBounds?.left ?: 0,
            defaultBounds?.top ?: 0,
            defaultBounds?.right ?: clusterWidth,
            defaultBounds?.bottom ?: clusterHeight,
        )

        outerLayout.addView(resizeView)

        // Button row: one display-global DPI control + reset
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }

        densityIndex = densityIndexFor(displayConfig.density)
        val btnDpi = Button(activity).apply {
            text = "DPI: ${densityCycle[densityIndex]}"
            textSize = 13f
            minimumHeight = (48 * dp).toInt()
            setOnClickListener {
                densityIndex = (densityIndex + 1) % densityCycle.size
                text = "DPI: ${densityCycle[densityIndex]}"
                coordinator.setDensity(densityCycle[densityIndex])
            }
        }

        val btnReset = Button(activity).apply {
            text = Lang.t("Đặt lại", "Reset")
            textSize = 13f
            minimumHeight = (48 * dp).toInt()
            setOnClickListener {
                val b = displayConfig.bounds
                resizeView.setBounds(
                    b?.left ?: 0,
                    b?.top ?: 0,
                    b?.right ?: clusterWidth,
                    b?.bottom ?: clusterHeight,
                )
                coordinator.resizeActiveTarget(
                    b?.left ?: 0,
                    b?.top ?: 0,
                    b?.right ?: clusterWidth,
                    b?.bottom ?: clusterHeight,
                )
            }
        }

        btnRow.addView(btnDpi)
        btnRow.addView(btnReset)
        outerLayout.addView(btnRow)
        geometryContainer.addView(outerLayout)
    }

    /**
     * Split-mode editor (R5/R6): one resize view per occupied slot, each constrained to its
     * horizontal band, plus one display-global DPI control. Only NORMAL apps ever reach split
     * (CP/AA cannot split — R7), so every slot here is resizable.
     */
    private fun setupSplitResizeControls(state: SimpleCastState.CastingSplit) {
        val leftPercent = coordinator.prefs.splitRatioLeftPercent()
        val tag = "split_resize_${state.left?.pkg}_${state.right?.pkg}_$leftPercent"
        if (geometryContainer.tag == tag) return
        geometryContainer.tag = tag
        geometryContainer.removeAllViews()

        val dp = activity.resources.displayMetrics.density

        // Split runs on the NORMAL cluster display; read dims from a present slot (fallback 1920×720).
        val refConfig = state.left?.displayConfig ?: state.right?.displayConfig
        val wmParts = refConfig?.wmSize?.split("x")
        val clusterWidth = wmParts?.getOrNull(0)?.toIntOrNull() ?: 1920
        val clusterHeight = wmParts?.getOrNull(1)?.toIntOrNull() ?: 720
        val split = (clusterWidth * leftPercent / 100).coerceIn(1, clusterWidth - 1)

        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        }

        // One editor per occupied slot, each clamped to its half (LEFT=[0,split], RIGHT=[split,W]).
        state.left?.let { slot ->
            outer.addView(buildSlotEditor(ClusterSlotSide.LEFT, slot, 0, split, clusterWidth, clusterHeight, leftPercent, dp))
        }
        state.right?.let { slot ->
            outer.addView(buildSlotEditor(ClusterSlotSide.RIGHT, slot, split, clusterWidth, clusterWidth, clusterHeight, leftPercent, dp))
        }

        // Reflect the saved split DPI (per-ratio profile) on the label; default 240 if none set.
        val savedSplitDensity = state.left?.let {
            coordinator.prefs.displayConfigFor(it.pkg, CastProfile.of(ClusterSlotSide.LEFT, leftPercent))?.density
        } ?: state.right?.let {
            coordinator.prefs.displayConfigFor(it.pkg, CastProfile.of(ClusterSlotSide.RIGHT, leftPercent))?.density
        }
        densityIndex = densityIndexFor(savedSplitDensity)
        outer.addView(buildSplitDpiRow(dp))
        geometryContainer.addView(outer)
    }

    /**
     * Build a labeled resize editor for one split slot, constrained to [bandMinX, bandMaxX].
     * Initial bounds = saved profile bounds (R6 restore) if present, else the ratio-default band
     * rectangle. Drag-end resizes that slot's task via [SimpleCastCoordinator.resizeActiveSlot].
     */
    private fun buildSlotEditor(
        side: ClusterSlotSide,
        slot: SlotState,
        bandMinX: Int,
        bandMaxX: Int,
        clusterWidth: Int,
        clusterHeight: Int,
        leftPercent: Int,
        dp: Float,
    ): View {
        val block = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (6 * dp).toInt())
        }

        block.addView(TextView(activity).apply {
            text = if (side == ClusterSlotSide.LEFT) Lang.t("Trái", "Left") else Lang.t("Phải", "Right")
            textSize = 13f
            setPadding(0, 0, 0, (2 * dp).toInt())
        })

        val resizeView = CastResizeView(activity, clusterWidth, clusterHeight) { l, t, r, b ->
            coordinator.resizeActiveSlot(side, l, t, r, b)
        }
        resizeView.setSlotBand(bandMinX, bandMaxX)

        // R6 restore: saved profile bounds win over the ratio-default band rectangle.
        val saved = coordinator.prefs.displayConfigFor(slot.pkg, CastProfile.of(side, leftPercent))?.bounds
        if (saved != null) {
            resizeView.setBounds(saved.left, saved.top, saved.right, saved.bottom)
        } else {
            resizeView.setBounds(bandMinX, 0, bandMaxX, clusterHeight)
        }

        resizeView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (120 * dp).toInt(),
        )
        block.addView(resizeView)
        return block
    }

    /**
     * Display-global DPI control for split mode. On Android 10 `wm density` is display-global,
     * so this affects BOTH slots (OS limitation, D4) — the label says so.
     */
    private fun buildSplitDpiRow(dp: Float): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, (6 * dp).toInt(), 0, 0)
        }
        row.addView(Button(activity).apply {
            text = "DPI: ${densityCycle[densityIndex]}"
            textSize = 13f
            minimumHeight = (48 * dp).toInt()
            contentDescription = Lang.t("Thay đổi DPI cả cụm", "Change whole-cluster DPI")
            setOnClickListener {
                densityIndex = (densityIndex + 1) % densityCycle.size
                text = "DPI: ${densityCycle[densityIndex]}"
                // R4/#5: split DPI must persist under the per-ratio profile of each occupied slot
                // (setDensitySplit), NOT the FULL-only setDensity (which no-ops in split state).
                coordinator.setDensitySplit(densityCycle[densityIndex])
            }
        })
        row.addView(TextView(activity).apply {
            text = Lang.t("(áp cho cả cụm)", "(whole cluster)")
            textSize = 11f
            setPadding((8 * dp).toInt(), 0, 0, 0)
        })
        return row
    }

    companion object {
        /** densityCycle index of the 240 default (NORMAL_DEFAULT.density). densityCycle[1] == 240. */
        private const val DEFAULT_DENSITY_INDEX = 1
    }
}
