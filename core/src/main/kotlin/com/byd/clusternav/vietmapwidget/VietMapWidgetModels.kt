package com.byd.clusternav.vietmapwidget

/**
 * Widget slot identity for VietMap provider widgets.
 * Each slot represents one widget provider and has independent lifecycle.
 *
 * The [preferenceKey] is used for persistence. Component mapping to Android's
 * ComponentName is handled in the app module via the `component` extension property
 * in VietMapWidgetSlotExt.kt.
 */
enum class VietMapWidgetSlot(val preferenceKey: String, val displayName: String) {
    SPEED_LIMIT("widget_id_speed_limit", "Tốc độ"),
    ALERTS("widget_id_alerts", "Cảnh báo"),

    // Full-alert widget (vn.vietmap.live.homewidget.VMAlertWidgetProvider). SEPARATE from the sticky ALERTS
    // slot: it carries the UPCOMING/ENFORCED speed-limit-ahead (`warning_speed_limit_widget_text_view`) + the
    // distance to it (`warning_speed_distance_text_view`), views that DO NOT exist on the sticky provider.
    // Its own independent freshness/generation means it can never drag down the speed or sticky-alert slots.
    ALERT_FULL("widget_id_alert_full", "Cảnh báo đầy đủ"),
}

/** Provider-neutral values exposed by the acquisition POC. */
data class VietMapWidgetSnapshot(
    val currentSpeedKph: Int?,
    val speedLimitKph: Int?,
    val alerts: List<VietMapRoadAlert>,
    val providerVersion: String?,
    val updatedAtElapsedMs: Long?,
    val freshness: VietMapWidgetFreshness,
    val reason: VietMapWidgetUnavailableReason?,
    /** Per-slot freshness: speed provider independent of alerts provider. */
    val speedFreshness: VietMapWidgetFreshness = freshness,
    val alertsFreshness: VietMapWidgetFreshness = freshness,
    val speedUpdatedAtElapsedMs: Long? = updatedAtElapsedMs,
    val alertsUpdatedAtElapsedMs: Long? = updatedAtElapsedMs,
    // ── VMAlertWidgetProvider (ALERT_FULL slot): upcoming/enforced speed-limit change ahead ──
    // ADDITIVE — all default to "no upcoming limit" so existing consumers/constructors are unaffected.
    /** The enforced speed-limit VietMap shows for the camera/zone AHEAD (km/h), or null when idle. */
    val upcomingLimitKph: Int? = null,
    /** Distance to that upcoming limit in metres (derived from the widget text), or null. */
    val upcomingDistanceMeters: Int? = null,
    /** Raw distance-to-upcoming-limit text as shown (e.g. "300 m" / "1,2 km"), or null. */
    val upcomingDistanceText: String? = null,
    /** Second upcoming limit (VietMap can queue two), km/h, or null. */
    val secondUpcomingLimitKph: Int? = null,
    val secondUpcomingDistanceMeters: Int? = null,
    val secondUpcomingDistanceText: String? = null,
    /** Freshness of the ALERT_FULL provider, independent of speed/alerts. */
    val alertFullFreshness: VietMapWidgetFreshness = VietMapWidgetFreshness.UNAVAILABLE,
    val alertFullUpdatedAtElapsedMs: Long? = null,
)

/**
 * Typed per-provider snapshot: each provider (speed, alerts) maintains its own
 * freshness/reason/generation independently. Bad data in one provider never
 * invalidates the other.
 */
data class VietMapProviderSnapshot<T>(
    val slot: VietMapWidgetSlot,
    val values: T?,
    val updatedAtElapsedMs: Long?,
    val freshness: VietMapWidgetFreshness,
    val reason: VietMapWidgetUnavailableReason?,
    val generation: Long,
)

data class VietMapRoadAlert(
    val speedLimitKph: Int?,
    val distanceText: String?,
    val distanceMeters: Int?,
    val imageVisible: Boolean,
    val imageHash: String?,
)

data class VietMapParsedDistance(
    val text: String,
    val meters: Int?,
)

/** Raw text and image metadata read from the applied RemoteViews hierarchy. */
data class VietMapWidgetRawValues(
    val currentSpeedText: String? = null,
    val speedLimitText: String? = null,
    val firstAlertSpeedLimitText: String? = null,
    val firstAlertDistanceText: String? = null,
    val firstAlertImageVisible: Boolean = false,
    val firstAlertImageHash: String? = null,
    val secondAlertSpeedLimitText: String? = null,
    val secondAlertDistanceText: String? = null,
    val secondAlertImageVisible: Boolean = false,
    val secondAlertImageHash: String? = null,
    // ── VMAlertWidgetProvider (ALERT_FULL): upcoming/enforced speed-limit-ahead + distance-to-it ──
    val upcomingSpeedLimitText: String? = null,
    val upcomingDistanceText: String? = null,
    val secondUpcomingSpeedLimitText: String? = null,
    val secondUpcomingDistanceText: String? = null,
)

/**
 * One provider's independent state, fed into [VietMapWidgetTextParser.composeSnapshot].
 * The [freshness]/[reason] are pre-computed by the caller (the bridge owns the clock + bind state);
 * the composer only decides how to project them into the public [VietMapWidgetSnapshot].
 */
data class VietMapProviderState(
    val raw: VietMapWidgetRawValues?,
    val freshness: VietMapWidgetFreshness,
    val reason: VietMapWidgetUnavailableReason?,
    val updatedAtElapsedMs: Long?,
)

/** Output of composition: the public snapshot plus the combined raw used only by verbose logging. */
data class VietMapComposedSnapshot(
    val snapshot: VietMapWidgetSnapshot,
    val combinedRaw: VietMapWidgetRawValues,
)

/** Parsed upcoming/enforced speed-limit-ahead pair (from the VMAlertWidgetProvider full-alert widget). */
data class VietMapUpcomingSpeedLimit(
    val limitKph: Int?,
    val distanceMeters: Int?,
    val distanceText: String?,
)

enum class VietMapWidgetFreshness {
    FRESH,
    STALE,
    UNAVAILABLE,
}

enum class VietMapWidgetUnavailableReason {
    NOT_BOUND,
    PROVIDER_MISSING,
    UNSUPPORTED_SHAPE,
    NO_UPDATE,
    HOST_ERROR,
    BIND_UI_UNAVAILABLE,
    BIND_DENIED,
}

/** States for the retryable speed-sign clear state machine. */
enum class SpeedSignClearState {
    /** Speed limit is actively displayed. */
    ACTIVE,
    /** Clear has been issued, awaiting acknowledgment. */
    CLEARING,
    /** Clear acknowledged — terminal success. */
    CLEARED,
    /** Clear failed, retry scheduled with backoff. */
    RETRY_PENDING,
}

/** Trigger events that initiate a speed-sign clear. */
enum class SpeedSignClearTrigger {
    MASTER_OFF,
    STALE_THRESHOLD,
    PROVIDER_DISCONNECT,
    SERVICE_DESTROY,
    PROCESS_BOOTSTRAP,
}
