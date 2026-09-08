package com.byd.clusternav.navigation

/**
 * Immutable decision for the "upcoming speed-limit ahead" badge — the smaller badge (+ a countdown label)
 * drawn BELOW the current-limit badge on the cluster (spec `upcoming-speed-limit-badge`).
 *
 * @property show           whether the upcoming badge should be visible at all.
 * @property limitKph        the upcoming enforced speed limit to render (only meaningful when [show]).
 * @property distanceMeters  distance to that limit in metres, for the countdown label (only when [show]);
 *                           0 when VietMap reports a fresh limit but no distance (the renderer then omits it).
 */
data class UpcomingBadge(
    val show: Boolean,
    val limitKph: Int,
    val distanceMeters: Int,
) {
    companion object {
        /** Canonical "nothing to show" value. */
        val HIDDEN = UpcomingBadge(show = false, limitKph = 0, distanceMeters = 0)
    }
}

/**
 * Decides whether to show the upcoming-speed-limit badge, mirroring VietMap 1:1 (OQ2): there is NO own
 * distance threshold — we show exactly when VietMap shows a FRESH upcoming limit, using the distance VietMap
 * reports, and we only hide when the data says "nothing to show" or "already reached".
 *
 * Pure — no Android — so it is unit-tested off-car (spec R7).
 */
object UpcomingBadgeDecision {

    /**
     * @param limitKph        the upcoming limit VietMap reports (km/h), or null when none.
     * @param distanceMeters  distance to it (m) as VietMap reports, or null when unknown.
     * @param fresh           true iff the ALERT_FULL provider freshness is FRESH.
     *
     * Show when the limit is a valid positive value AND the provider is fresh. Hide when:
     *  - not fresh (stale / unavailable — VietMap stopped showing an upcoming limit), or
     *  - the limit is null / <= 0 (nothing upcoming), or
     *  - the distance is KNOWN and <= 0 (already reached → VietMap promotes it to the current limit).
     *
     * When the distance is null but the limit is fresh + valid we still show (mirror VietMap) with distance 0,
     * so the renderer simply omits the countdown label rather than hiding the whole badge.
     */
    fun decide(limitKph: Int?, distanceMeters: Int?, fresh: Boolean): UpcomingBadge {
        if (!fresh) return UpcomingBadge.HIDDEN
        if (limitKph == null || limitKph <= 0) return UpcomingBadge.HIDDEN
        if (distanceMeters != null && distanceMeters <= 0) return UpcomingBadge.HIDDEN
        return UpcomingBadge(show = true, limitKph = limitKph, distanceMeters = distanceMeters ?: 0)
    }
}
