package com.byd.clusternav.speedbadge

/**
 * PURE placement math for the cluster speed-limit badge overlay.
 *
 * Lives in :core (JVM-only, no Android — enforced by LayeringRulesTest) so the absolute-position clamp and
 * the size clamp are unit-testable off-device.
 *
 * The badge is a `sizePx × sizePx` square positioned by its **CENTRE** in cluster pixel coordinates
 * (`0..clusterW`, `0..clusterH`). The Android overlay uses `gravity = TOP|LEFT` and assigns `x`/`y` to the
 * top-left corner, which is [topLeftFromCenter] of the clamped centre. Storing the centre (not a corner id)
 * lets the driver drag the badge anywhere on the cluster — the old 4-corner model was replaced 2026-08-17
 * (spec: speed-badge-placement-vietmap-logging §4.3, Part B).
 */
object BadgeLayout {

    // ─── Badge size bounds (dp) ──────────────────────────────────────────────────────────────────
    const val SIZE_MIN_DP = 60
    const val SIZE_MAX_DP = 240
    const val SIZE_DEFAULT_DP = 120

    /** Clamp a badge size (dp) into [SIZE_MIN_DP]..[SIZE_MAX_DP]. Applied on both read and write in Prefs. */
    fun clampSizeDp(sizeDp: Int): Int = sizeDp.coerceIn(SIZE_MIN_DP, SIZE_MAX_DP)

    /**
     * Clamp a badge **centre** `(cx, cy)` so the `sizePx × sizePx` square stays fully inside
     * `[0, clusterW] × [0, clusterH]`. Each axis is clamped independently.
     *
     * Defensive: if the badge is larger than the cluster on an axis (should not happen after [clampSizeDp],
     * but a corrupt pref or tiny display could), the badge is centred on that axis rather than throwing.
     *
     * @return the clamped centre `(cx, cy)`.
     */
    fun clampCenter(cx: Int, cy: Int, sizePx: Int, clusterW: Int, clusterH: Int): Pair<Int, Int> {
        val half = sizePx / 2
        return clampAxis(cx, half, clusterW) to clampAxis(cy, half, clusterH)
    }

    /** Keep a centre value so `[center - half, center + half]` stays within `[0, extent]`. */
    private fun clampAxis(center: Int, half: Int, extent: Int): Int {
        val min = half
        val max = extent - half
        return if (min > max) extent / 2 else center.coerceIn(min, max)
    }

    /** Top-left px `(left, top)` of the `sizePx × sizePx` badge whose **centre** is `(cx, cy)`. */
    fun topLeftFromCenter(cx: Int, cy: Int, sizePx: Int): Pair<Int, Int> {
        val half = sizePx / 2
        return (cx - half) to (cy - half)
    }
}
