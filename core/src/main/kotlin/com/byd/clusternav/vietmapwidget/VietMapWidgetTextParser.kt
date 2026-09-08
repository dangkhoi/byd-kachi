package com.byd.clusternav.vietmapwidget

import kotlin.math.roundToInt

object VietMapWidgetViewNames {
    const val CURRENT_SPEED = "osw_current_speed_tv"
    const val SPEED_LIMIT = "speed_limit_widget_text_view"
    const val ALERT_CURRENT_SPEED = "current_speed_textview"
    const val FIRST_ALERT_IMAGE = "warning_alert_image"
    const val SECOND_ALERT_IMAGE = "second_warning_alert_image"

    // VietMap 3.3.2: the per-alert value/distance text lives in `place_holder_textView` /
    // `second_place_holder_textView`. The old `warning_speed_limit_widget_text_view` /
    // `warning_speed_distance_text_view` (+ their `second_…` siblings) DO NOT EXIST in 3.3.2 — proven by
    // 2851 on-car view-dumps that never contained them. A place-holder value of "--" means no active alert
    // value (the parser's sentinel set treats it as null).
    const val PLACE_HOLDER = "place_holder_textView"
    const val SECOND_PLACE_HOLDER = "second_place_holder_textView"

    // VMAlertWidgetProvider (the FULL alert widget, VietMap 3.3.4) — the upcoming/enforced speed-limit CHANGE
    // ahead + the distance to it. These views live ONLY on the full-alert widget, NOT on the sticky provider
    // (`VMOnlyStickyAlertWidgetProvider`) the ALERTS slot binds — proven by on-car dumps where the sticky
    // slot only ever exposed `warning_alert_image` + `place_holder_textView`='--'. Captured via the ALERT_FULL
    // slot so the existing sticky cấm-dừng/đỗ icon capture is untouched.
    const val WARNING_SPEED_LIMIT = "warning_speed_limit_widget_text_view"
    const val WARNING_SPEED_DISTANCE = "warning_speed_distance_text_view"
    const val SECOND_WARNING_SPEED_LIMIT = "second_warning_speed_limit_widget_text_view"
    const val SECOND_WARNING_SPEED_DISTANCE = "second_warning_speed_distance_text_view"

    val speedRequired = setOf(CURRENT_SPEED, SPEED_LIMIT)
    val alertsRequired = setOf(
        ALERT_CURRENT_SPEED,
        SPEED_LIMIT,
        PLACE_HOLDER,
        FIRST_ALERT_IMAGE,
        SECOND_PLACE_HOLDER,
        SECOND_ALERT_IMAGE,
    )

    // The STABLE anchor for the full-alert widget = the first upcoming limit + distance pair. The `second_…`
    // siblings are OPTIONAL (only a second queued limit populates them), so they are NOT part of the anchor.
    val alertFullRequired = setOf(WARNING_SPEED_LIMIT, WARNING_SPEED_DISTANCE)
}

object VietMapWidgetTextParser {
    const val FRESH_FOR_MS = 5_000L
    const val UNAVAILABLE_AFTER_MS = 30_000L

    private val integer = Regex("^[0-9]{1,3}$")
    private val distance = Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*(m|km)$", RegexOption.IGNORE_CASE)
    private val sentinels = setOf("", "--", "!", "-")

    fun parseCurrentSpeed(text: CharSequence?): Int? = parseInteger(text, 0..300)

    fun parseSpeedLimit(text: CharSequence?): Int? = parseInteger(text, 1..300)

    fun parseDistance(text: CharSequence?): VietMapParsedDistance? {
        val normalized = normalize(text)
        if (normalized.lowercase() in sentinels) return null
        val match = distance.matchEntire(normalized)
        val meters = match?.let {
            val value = it.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let null
            val multiplier = if (it.groupValues[2].equals("km", ignoreCase = true)) 1_000.0 else 1.0
            (value * multiplier).takeIf { candidate -> candidate.isFinite() && candidate in 0.0..1_000_000.0 }
                ?.roundToInt()
        }
        return VietMapParsedDistance(normalized, meters)
    }

    fun supportsSpeedShape(availableNames: Set<String>): Boolean =
        availableNames.containsAll(VietMapWidgetViewNames.speedRequired)

    fun supportsAlertsShape(availableNames: Set<String>): Boolean =
        availableNames.containsAll(VietMapWidgetViewNames.alertsRequired)

    fun supportsAlertFullShape(availableNames: Set<String>): Boolean =
        availableNames.containsAll(VietMapWidgetViewNames.alertFullRequired)

    fun freshness(
        updatedAtElapsedMs: Long?,
        nowElapsedMs: Long,
        unavailableReason: VietMapWidgetUnavailableReason? = null,
    ): Pair<VietMapWidgetFreshness, VietMapWidgetUnavailableReason?> {
        if (unavailableReason != null) return VietMapWidgetFreshness.UNAVAILABLE to unavailableReason
        if (updatedAtElapsedMs == null || updatedAtElapsedMs < 0L) {
            return VietMapWidgetFreshness.UNAVAILABLE to VietMapWidgetUnavailableReason.NO_UPDATE
        }
        val age = (nowElapsedMs - updatedAtElapsedMs).coerceAtLeast(0L)
        return when {
            age <= FRESH_FOR_MS -> VietMapWidgetFreshness.FRESH to null
            age <= UNAVAILABLE_AFTER_MS -> VietMapWidgetFreshness.STALE to null
            else -> VietMapWidgetFreshness.UNAVAILABLE to VietMapWidgetUnavailableReason.NO_UPDATE
        }
    }

    fun parseSnapshot(
        raw: VietMapWidgetRawValues,
        providerVersion: String?,
        updatedAtElapsedMs: Long?,
        nowElapsedMs: Long,
        unavailableReason: VietMapWidgetUnavailableReason? = null,
    ): VietMapWidgetSnapshot {
        val (freshness, reason) = freshness(updatedAtElapsedMs, nowElapsedMs, unavailableReason)
        if (freshness != VietMapWidgetFreshness.FRESH) {
            return VietMapWidgetSnapshot(
                currentSpeedKph = null,
                speedLimitKph = null,
                alerts = emptyList(),
                providerVersion = providerVersion,
                updatedAtElapsedMs = updatedAtElapsedMs,
                freshness = freshness,
                reason = reason,
            )
        }

        val alerts = listOf(
            alert(
                raw.firstAlertSpeedLimitText,
                raw.firstAlertDistanceText,
                raw.firstAlertImageVisible,
                raw.firstAlertImageHash,
            ),
            alert(
                raw.secondAlertSpeedLimitText,
                raw.secondAlertDistanceText,
                raw.secondAlertImageVisible,
                raw.secondAlertImageHash,
            ),
        ).filterNotNull()

        return VietMapWidgetSnapshot(
            currentSpeedKph = parseCurrentSpeed(raw.currentSpeedText),
            speedLimitKph = parseSpeedLimit(raw.speedLimitText),
            alerts = alerts,
            providerVersion = providerVersion,
            updatedAtElapsedMs = updatedAtElapsedMs,
            freshness = freshness,
            reason = null,
        )
    }

    /**
     * Parse the VMAlertWidgetProvider upcoming/enforced speed-limit pair. The limit is an integer km/h
     * (`warning_speed_limit_widget_text_view`); the distance is free text (`warning_speed_distance_text_view`)
     * whose metres are derived only for known units. Sentinels ("--"/"!"/empty) collapse to null on both.
     */
    fun parseUpcomingSpeedLimit(limitText: String?, distanceText: String?): VietMapUpcomingSpeedLimit {
        val distance = parseDistance(distanceText)
        return VietMapUpcomingSpeedLimit(
            limitKph = parseSpeedLimit(limitText),
            distanceMeters = distance?.meters,
            distanceText = distance?.text,
        )
    }

    /**
     * Compose the public snapshot from the three INDEPENDENT provider states (speed, sticky alerts,
     * full-alert). Pure — no clock, no Android. The legacy combined [VietMapWidgetSnapshot.freshness] and
     * `updatedAtElapsedMs` stay defined by speed + sticky-alerts ONLY (backward-compat: ALERT_FULL never
     * changes them); ALERT_FULL is projected purely into the additive `upcoming*` fields under its OWN
     * `alertFullFreshness`. Each provider's driving values are exposed only while that provider is FRESH.
     */
    fun composeSnapshot(
        speed: VietMapProviderState,
        alerts: VietMapProviderState,
        alertFull: VietMapProviderState,
        providerVersion: String?,
        nowElapsedMs: Long,
    ): VietMapComposedSnapshot {
        fun freshRaw(state: VietMapProviderState): VietMapWidgetRawValues =
            if (state.freshness == VietMapWidgetFreshness.FRESH) {
                state.raw ?: VietMapWidgetRawValues()
            } else {
                VietMapWidgetRawValues()
            }

        val speedRaw = freshRaw(speed)
        val alertsRaw = freshRaw(alerts)
        val alertFullRaw = freshRaw(alertFull)

        val combinedRaw = speedRaw.copy(
            firstAlertSpeedLimitText = alertsRaw.firstAlertSpeedLimitText,
            firstAlertDistanceText = alertsRaw.firstAlertDistanceText,
            firstAlertImageVisible = alertsRaw.firstAlertImageVisible,
            firstAlertImageHash = alertsRaw.firstAlertImageHash,
            secondAlertSpeedLimitText = alertsRaw.secondAlertSpeedLimitText,
            secondAlertDistanceText = alertsRaw.secondAlertDistanceText,
            secondAlertImageVisible = alertsRaw.secondAlertImageVisible,
            secondAlertImageHash = alertsRaw.secondAlertImageHash,
        )

        val combinedFreshness = worst(speed.freshness, alerts.freshness)
        val combinedReason = speed.reason ?: alerts.reason
        val updatedAt = when {
            speed.updatedAtElapsedMs != null && alerts.updatedAtElapsedMs != null ->
                minOf(speed.updatedAtElapsedMs, alerts.updatedAtElapsedMs)
            else -> speed.updatedAtElapsedMs ?: alerts.updatedAtElapsedMs
        }

        val upcoming = parseUpcomingSpeedLimit(alertFullRaw.upcomingSpeedLimitText, alertFullRaw.upcomingDistanceText)
        val secondUpcoming =
            parseUpcomingSpeedLimit(alertFullRaw.secondUpcomingSpeedLimitText, alertFullRaw.secondUpcomingDistanceText)

        val snapshot = parseSnapshot(combinedRaw, providerVersion, updatedAt, nowElapsedMs, combinedReason).copy(
            freshness = combinedFreshness,
            reason = combinedReason,
            speedFreshness = speed.freshness,
            alertsFreshness = alerts.freshness,
            speedUpdatedAtElapsedMs = speed.updatedAtElapsedMs,
            alertsUpdatedAtElapsedMs = alerts.updatedAtElapsedMs,
            upcomingLimitKph = upcoming.limitKph,
            upcomingDistanceMeters = upcoming.distanceMeters,
            upcomingDistanceText = upcoming.distanceText,
            secondUpcomingLimitKph = secondUpcoming.limitKph,
            secondUpcomingDistanceMeters = secondUpcoming.distanceMeters,
            secondUpcomingDistanceText = secondUpcoming.distanceText,
            alertFullFreshness = alertFull.freshness,
            alertFullUpdatedAtElapsedMs = alertFull.updatedAtElapsedMs,
        )
        return VietMapComposedSnapshot(snapshot, combinedRaw)
    }

    /** Worst-of two freshness values (UNAVAILABLE > STALE > FRESH) — used for the legacy combined field. */
    private fun worst(a: VietMapWidgetFreshness, b: VietMapWidgetFreshness): VietMapWidgetFreshness = when {
        a == VietMapWidgetFreshness.UNAVAILABLE || b == VietMapWidgetFreshness.UNAVAILABLE ->
            VietMapWidgetFreshness.UNAVAILABLE
        a == VietMapWidgetFreshness.STALE || b == VietMapWidgetFreshness.STALE ->
            VietMapWidgetFreshness.STALE
        else -> VietMapWidgetFreshness.FRESH
    }

    private fun alert(
        speedLimitText: String?,
        distanceText: String?,
        imageVisible: Boolean,
        imageHash: String?,
    ): VietMapRoadAlert? {
        val limit = parseSpeedLimit(speedLimitText)
        val parsedDistance = parseDistance(distanceText)
        val hash = imageHash?.takeIf { imageVisible }
        if (limit == null && parsedDistance == null && !imageVisible) return null
        return VietMapRoadAlert(
            speedLimitKph = limit,
            distanceText = parsedDistance?.text,
            distanceMeters = parsedDistance?.meters,
            imageVisible = imageVisible,
            imageHash = hash,
        )
    }

    private fun parseInteger(text: CharSequence?, allowed: IntRange): Int? {
        val normalized = normalize(text)
        if (normalized.lowercase() in sentinels || !integer.matches(normalized)) return null
        return normalized.toIntOrNull()?.takeIf { it in allowed }
    }

    private fun normalize(text: CharSequence?): String = text
        ?.toString()
        .orEmpty()
        .replace('\u00a0', ' ')
        .trim()
        .replace(Regex("\\s+"), " ")
}
