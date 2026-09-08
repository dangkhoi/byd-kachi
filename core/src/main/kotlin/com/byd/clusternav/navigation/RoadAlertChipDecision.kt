package com.byd.clusternav.navigation

import com.byd.clusternav.vietmapwidget.VietMapRoadAlert

/**
 * Immutable decision for the "road alert / speed-camera" chip (B3.20) — a chip drawn near the speed badge on
 * the cluster showing VietMap's sticky ALERTS-slot road alert (speed camera / hazard ahead + its enforced
 * limit + distance). SONG SONG với nav GMaps + badge tốc độ; đây là "data VN trên cụm" còn thiếu.
 *
 * @property show           whether the chip should be visible.
 * @property limitKph        enforced/posted limit the alert carries (0 = none → chip omits the limit circle).
 * @property distanceMeters  distance to the alert in metres (0 = unknown → renderer omits the countdown).
 * @property distanceText    VietMap's raw distance text ("300 m" / "1,2 km") preferred for the countdown.
 * @property hasIcon         VietMap showed the alert type icon (camera/hazard). true ⇒ draw the warning glyph.
 */
data class RoadAlertChip(
    val show: Boolean,
    val limitKph: Int,
    val distanceMeters: Int,
    val distanceText: String?,
    val hasIcon: Boolean,
) {
    companion object {
        /** Canonical "nothing to show" value. */
        val HIDDEN = RoadAlertChip(show = false, limitKph = 0, distanceMeters = 0, distanceText = null, hasIcon = false)
    }
}

/**
 * Decides the road-alert chip from VietMap's sticky ALERTS-slot [VietMapRoadAlert] list, mirroring VietMap
 * (no own distance threshold — show what VietMap shows). Pure — no Android — unit-tested off-car.
 *
 * Rules (degrade-safe — thiếu dữ kiện thì IM LẶNG, tuyệt đối không đoán):
 *  - not [fresh] (ALERTS provider stale/unavailable) ⇒ hidden.
 *  - pick the NEAREST alert that hasn't been passed (distance null = unknown-but-present is allowed; a
 *    KNOWN distance ≤ 0 means "already reached" ⇒ that alert is dropped, like the upcoming badge).
 *  - an alert must carry CONTENT to show: a positive speed limit OR a visible type icon. A blank alert
 *    (no limit, no icon) ⇒ nothing meaningful ⇒ hidden.
 */
object RoadAlertChipDecision {

    fun decide(alerts: List<VietMapRoadAlert>, fresh: Boolean): RoadAlertChip {
        if (!fresh) return RoadAlertChip.HIDDEN
        val candidate = alerts
            .filter { it.distanceMeters == null || it.distanceMeters > 0 }   // drop already-passed (known ≤ 0)
            .filter { (it.speedLimitKph != null && it.speedLimitKph > 0) || it.imageVisible }  // must have content
            .minByOrNull { it.distanceMeters ?: Int.MAX_VALUE }              // nearest (unknown distance = last)
            ?: return RoadAlertChip.HIDDEN
        return RoadAlertChip(
            show = true,
            limitKph = candidate.speedLimitKph?.takeIf { it > 0 } ?: 0,
            distanceMeters = candidate.distanceMeters?.takeIf { it > 0 } ?: 0,
            distanceText = candidate.distanceText,
            hasIcon = candidate.imageVisible,
        )
    }
}
