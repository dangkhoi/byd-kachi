package com.byd.clusternav.navigation

/**
 * R7 (#2, docs/specs/cast-nav-ux-release-v104.html): pure decisions for ending a navigation route
 * cleanly on the cluster. Android-free → unit-tested off-car; [com.byd.clusternav.NavNotificationListener]
 * holds one instance, performs the emitted CLEAR/STOP, and drops rejected frames.
 *
 * Two independent guards, both aimed at the same owner-observed failure (2026-08): "was 500 m to the
 * destination with the flag, jumped to 3.5 km still showing the flag; Google Maps had announced
 * arrival, but the cluster stayed stuck at '3.5 km go straight'."
 *
 *  1. **ARRIVAL detection** — a route is over when the notification text says "arrived" (EN/VI)
 *     ([isArrivalText]) OR the route-remaining collapses to ~0 ([arrivedByRouteRemaining]). Either
 *     way the listener must emit STOP so the cluster returns to gauges, instead of leaving the last
 *     turn frame to heart-beat for `ClusterBroadcaster.STALE_MS` (180 s).
 *
 *  2. **Distance-REGRESSION guard** ([acceptDistance]) — while approaching a turn/destination (last
 *     accepted distance small), a sudden much-larger distance for the SAME maneuver (no reroute) is
 *     a stale/glitch frame; it is dropped so it never becomes the displayed/heart-beaten value. A
 *     real maneuver/road change legitimately jumps up and is always allowed; a persistent large
 *     value is released after [releaseAfterRejects] frames so a genuine reroute recovers instead of
 *     freezing the cluster.
 *
 * Instance state ([lastGoodMeters]/[lastKey]/[rejectStreak]) is per navigation session; the listener
 * calls [reset] on STOP/arrival/removal so a new route starts clean.
 */
class NavArrivalGuard(
    /** Only guard the final approach: regression is considered only when last-good ≤ this (m). */
    private val approachMeters: Int = 800,
    /** A jump of more than this above last-good (same maneuver) is implausible while approaching (m). */
    private val regressionJumpMeters: Int = 1500,
    /** Accept a persistent large value after this many consecutive rejects (reroute recovery). */
    private val releaseAfterRejects: Int = 2,
    /** Route-remaining at or below this counts as "arrived" (m). */
    private val arrivedRouteRemainingMeters: Int = 30,
) {
    private var lastGoodMeters: Int = -1
    private var lastKey: String = ""
    private var rejectStreak: Int = 0

    /**
     * Distance-regression guard. Returns `true` to ACCEPT [meters] (feed it to the cluster) or
     * `false` to DROP it as a spurious jump-up while approaching [maneuverKey].
     *
     * [meters] < 0 (no distance — heading-only / arrival frame) is always accepted; it is not this
     * guard's concern. [maneuverKey] identifies the turn (e.g. cleaned road name + maneuver text) so
     * a NEW turn (key change) is never mistaken for a regression.
     */
    fun acceptDistance(meters: Int, maneuverKey: String): Boolean {
        if (meters < 0) return true
        val sameManeuver = maneuverKey == lastKey
        val approaching = lastGoodMeters in 0..approachMeters
        val implausible = sameManeuver && approaching && meters > lastGoodMeters + regressionJumpMeters
        if (implausible && rejectStreak < releaseAfterRejects) {
            rejectStreak++
            return false
        }
        rejectStreak = 0
        lastGoodMeters = meters
        lastKey = maneuverKey
        return true
    }

    /**
     * True when the route-remaining distance has collapsed to ~0 (≤ [arrivedRouteRemainingMeters]),
     * i.e. the destination is reached even if no "arrived" text was posted. `null`/negative means the
     * notification did not report a route-remaining, so this returns false (not an arrival signal).
     */
    fun arrivedByRouteRemaining(routeRemainingMeters: Int?): Boolean =
        routeRemainingMeters != null && routeRemainingMeters in 0..arrivedRouteRemainingMeters

    /** Reset per-session state. Call on STOP / arrival / notification removal / source change. */
    fun reset() {
        lastGoodMeters = -1
        lastKey = ""
        rejectStreak = 0
    }

    companion object {
        /**
         * End-of-route phrases (EN + VI) that Google Maps / VietMap post on arrival. Moved here from
         * NavNotificationListener so both listener call-sites (post + removed) and this guard share
         * one source of truth, unit-testable off-car.
         */
        private val ARRIVAL = Regex("""arriv|đã đến|đến nơi|tới nơi|bạn đã tới|đã tới""", RegexOption.IGNORE_CASE)

        /** True if any provided notification field contains an arrival phrase. */
        fun isArrivalText(vararg fields: String?): Boolean =
            fields.any { !it.isNullOrBlank() && ARRIVAL.containsMatchIn(it) }
    }
}
