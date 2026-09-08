package com.byd.clusternav.vietmapwidget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VietMapWidgetTextParserTest {
    @Test
    fun `speed parsers accept exact valid values and reject sentinels and ranges`() {
        assertEquals(0, VietMapWidgetTextParser.parseCurrentSpeed("0"))
        assertEquals(300, VietMapWidgetTextParser.parseCurrentSpeed(" 300 "))
        assertEquals(50, VietMapWidgetTextParser.parseSpeedLimit("50"))

        listOf(null, "", "--", "!", "-", "301", "50 km/h", "abc").forEach {
            assertNull(VietMapWidgetTextParser.parseCurrentSpeed(it), it)
        }
        listOf(null, "", "--", "!", "0", "301", "50.0").forEach {
            assertNull(VietMapWidgetTextParser.parseSpeedLimit(it), it)
        }
    }

    @Test
    fun `distance parser preserves text and only derives metres for known units`() {
        assertEquals(VietMapParsedDistance("100 m", 100), VietMapWidgetTextParser.parseDistance("100  m"))
        assertEquals(VietMapParsedDistance("1,25 km", 1_250), VietMapWidgetTextParser.parseDistance("1,25 km"))
        assertEquals(VietMapParsedDistance("near bridge", null), VietMapWidgetTextParser.parseDistance("near bridge"))
        assertNull(VietMapWidgetTextParser.parseDistance("--"))
        assertNull(VietMapWidgetTextParser.parseDistance("!"))
    }

    @Test
    fun `fresh snapshot parses at most two alerts and hides irrelevant image hashes`() {
        val snapshot = VietMapWidgetTextParser.parseSnapshot(
            raw = VietMapWidgetRawValues(
                currentSpeedText = "42",
                speedLimitText = "50",
                firstAlertSpeedLimitText = "40",
                firstAlertDistanceText = "250m",
                firstAlertImageVisible = false,
                firstAlertImageHash = "must-not-leak",
                secondAlertDistanceText = "1.5 km",
                secondAlertImageVisible = true,
                secondAlertImageHash = "abc123",
            ),
            providerVersion = "3.3.4",
            updatedAtElapsedMs = 10_000L,
            nowElapsedMs = 12_000L,
        )

        assertEquals(VietMapWidgetFreshness.FRESH, snapshot.freshness)
        assertEquals(42, snapshot.currentSpeedKph)
        assertEquals(50, snapshot.speedLimitKph)
        assertEquals(2, snapshot.alerts.size)
        assertEquals(250, snapshot.alerts[0].distanceMeters)
        assertNull(snapshot.alerts[0].imageHash)
        assertEquals(1_500, snapshot.alerts[1].distanceMeters)
        assertEquals("abc123", snapshot.alerts[1].imageHash)
    }

    @Test
    fun `stale and unavailable snapshots never expose old driving values`() {
        val raw = VietMapWidgetRawValues(currentSpeedText = "90", speedLimitText = "50")
        val stale = VietMapWidgetTextParser.parseSnapshot(raw, "3.3.4", 1_000L, 7_000L)
        val unavailable = VietMapWidgetTextParser.parseSnapshot(raw, "3.3.4", 1_000L, 40_000L)
        val missing = VietMapWidgetTextParser.parseSnapshot(
            raw,
            "3.3.4",
            40_000L,
            40_000L,
            VietMapWidgetUnavailableReason.PROVIDER_MISSING,
        )

        assertEquals(VietMapWidgetFreshness.STALE, stale.freshness)
        assertNull(stale.currentSpeedKph)
        assertNull(stale.speedLimitKph)
        assertTrue(stale.alerts.isEmpty())
        assertEquals(VietMapWidgetFreshness.UNAVAILABLE, unavailable.freshness)
        assertEquals(VietMapWidgetUnavailableReason.NO_UPDATE, unavailable.reason)
        assertEquals(VietMapWidgetUnavailableReason.PROVIDER_MISSING, missing.reason)
        assertNull(missing.currentSpeedKph)
    }

    @Test
    fun `alert view names target the place_holder text slots present in VietMap 3_3_2`() {
        assertEquals("place_holder_textView", VietMapWidgetViewNames.PLACE_HOLDER)
        assertEquals("second_place_holder_textView", VietMapWidgetViewNames.SECOND_PLACE_HOLDER)
        // The old warning_speed_* names are gone from the required shape (they never existed on-car).
        assertTrue(VietMapWidgetViewNames.PLACE_HOLDER in VietMapWidgetViewNames.alertsRequired)
        assertTrue(VietMapWidgetViewNames.SECOND_PLACE_HOLDER in VietMapWidgetViewNames.alertsRequired)
        assertFalse(VietMapWidgetViewNames.alertsRequired.any { it.startsWith("warning_speed") })
    }

    @Test
    fun `place_holder value is parsed as the alert value and '--' means no active alert`() {
        // extractAlerts routes place_holder / second_place_holder into the *DistanceText fields.
        val snapshot = VietMapWidgetTextParser.parseSnapshot(
            raw = VietMapWidgetRawValues(
                currentSpeedText = "42",
                speedLimitText = "50",
                // First alert: active value in place_holder + a visible icon.
                firstAlertDistanceText = "250 m",
                firstAlertImageVisible = true,
                firstAlertImageHash = "cam-hash",
                // Second alert: place_holder shows the idle "--" sentinel and no icon → dropped entirely.
                secondAlertDistanceText = "--",
                secondAlertImageVisible = false,
            ),
            providerVersion = "3.3.2",
            updatedAtElapsedMs = 10_000L,
            nowElapsedMs = 12_000L,
        )

        assertEquals(VietMapWidgetFreshness.FRESH, snapshot.freshness)
        // Only the active alert survives; the '--' placeholder with no icon is not an alert.
        assertEquals(1, snapshot.alerts.size)
        assertEquals("250 m", snapshot.alerts[0].distanceText)
        assertEquals(250, snapshot.alerts[0].distanceMeters)
        assertEquals("cam-hash", snapshot.alerts[0].imageHash)
    }

    @Test
    fun `an active icon with a '--' place_holder still reports an alert with null value`() {
        val snapshot = VietMapWidgetTextParser.parseSnapshot(
            raw = VietMapWidgetRawValues(
                currentSpeedText = "42",
                speedLimitText = "50",
                firstAlertDistanceText = "--", // idle value text …
                firstAlertImageVisible = true, // … but the alert icon is showing
                firstAlertImageHash = "police-hash",
            ),
            providerVersion = "3.3.2",
            updatedAtElapsedMs = 10_000L,
            nowElapsedMs = 12_000L,
        )

        assertEquals(1, snapshot.alerts.size)
        assertNull(snapshot.alerts[0].distanceText)
        assertNull(snapshot.alerts[0].distanceMeters)
        assertTrue(snapshot.alerts[0].imageVisible)
        assertEquals("police-hash", snapshot.alerts[0].imageHash)
    }

    @Test
    fun `shape checks require names not decompiled integer resource ids`() {
        assertTrue(VietMapWidgetTextParser.supportsSpeedShape(VietMapWidgetViewNames.speedRequired))
        assertFalse(VietMapWidgetTextParser.supportsSpeedShape(setOf(VietMapWidgetViewNames.CURRENT_SPEED)))
        assertTrue(VietMapWidgetTextParser.supportsAlertsShape(VietMapWidgetViewNames.alertsRequired))
        assertFalse(
            VietMapWidgetTextParser.supportsAlertsShape(
                VietMapWidgetViewNames.alertsRequired - VietMapWidgetViewNames.SECOND_PLACE_HOLDER,
            ),
        )
    }

    // ─── VMAlertWidgetProvider: upcoming/enforced speed-limit-ahead (ALERT_FULL slot) ───

    @Test
    fun `full-alert view names target the VMAlertWidgetProvider warning_speed views`() {
        assertEquals("warning_speed_limit_widget_text_view", VietMapWidgetViewNames.WARNING_SPEED_LIMIT)
        assertEquals("warning_speed_distance_text_view", VietMapWidgetViewNames.WARNING_SPEED_DISTANCE)
        assertEquals(
            "second_warning_speed_limit_widget_text_view",
            VietMapWidgetViewNames.SECOND_WARNING_SPEED_LIMIT,
        )
        // Anchor requires BOTH first-pair views; the second_* siblings are optional.
        assertTrue(VietMapWidgetTextParser.supportsAlertFullShape(VietMapWidgetViewNames.alertFullRequired))
        assertFalse(
            VietMapWidgetTextParser.supportsAlertFullShape(setOf(VietMapWidgetViewNames.WARNING_SPEED_LIMIT)),
        )
    }

    @Test
    fun `parseUpcomingSpeedLimit parses limit int plus distance and collapses sentinels`() {
        val m = VietMapWidgetTextParser.parseUpcomingSpeedLimit("60", "300 m")
        assertEquals(60, m.limitKph)
        assertEquals(300, m.distanceMeters)
        assertEquals("300 m", m.distanceText)

        val km = VietMapWidgetTextParser.parseUpcomingSpeedLimit("80", "1,2 km")
        assertEquals(80, km.limitKph)
        assertEquals(1_200, km.distanceMeters)

        // Idle sentinels collapse to null on both fields.
        val idle = VietMapWidgetTextParser.parseUpcomingSpeedLimit("--", "--")
        assertNull(idle.limitKph)
        assertNull(idle.distanceMeters)
        assertNull(idle.distanceText)

        // Out-of-range limit rejected; unknown-unit distance keeps text but no metres.
        val partial = VietMapWidgetTextParser.parseUpcomingSpeedLimit("400", "gần camera")
        assertNull(partial.limitKph)
        assertEquals("gần camera", partial.distanceText)
        assertNull(partial.distanceMeters)
    }

    @Test
    fun `composeSnapshot maps upcoming fields when full-alert is fresh and keeps combined on speed plus alerts`() {
        val composed = VietMapWidgetTextParser.composeSnapshot(
            speed = VietMapProviderState(
                VietMapWidgetRawValues(currentSpeedText = "42", speedLimitText = "50"),
                VietMapWidgetFreshness.FRESH, null, 10_000L,
            ),
            alerts = VietMapProviderState(
                VietMapWidgetRawValues(firstAlertDistanceText = "250 m", firstAlertImageVisible = true),
                VietMapWidgetFreshness.FRESH, null, 10_000L,
            ),
            alertFull = VietMapProviderState(
                VietMapWidgetRawValues(
                    upcomingSpeedLimitText = "60",
                    upcomingDistanceText = "300 m",
                    secondUpcomingSpeedLimitText = "40",
                    secondUpcomingDistanceText = "1 km",
                ),
                VietMapWidgetFreshness.FRESH, null, 11_000L,
            ),
            providerVersion = "3.3.4",
            nowElapsedMs = 12_000L,
        )
        val s = composed.snapshot
        assertEquals(60, s.upcomingLimitKph)
        assertEquals(300, s.upcomingDistanceMeters)
        assertEquals("300 m", s.upcomingDistanceText)
        assertEquals(40, s.secondUpcomingLimitKph)
        assertEquals(1_000, s.secondUpcomingDistanceMeters)
        assertEquals(VietMapWidgetFreshness.FRESH, s.alertFullFreshness)
        assertEquals(11_000L, s.alertFullUpdatedAtElapsedMs)
        // Existing fields still parse; combined updatedAt stays min(speed, alerts) — ignores full-alert's 11_000.
        assertEquals(42, s.currentSpeedKph)
        assertEquals(50, s.speedLimitKph)
        assertEquals(10_000L, s.updatedAtElapsedMs)
        // The sticky-alert capture is preserved through the combinedRaw handed to logging.
        assertEquals("250 m", composed.combinedRaw.firstAlertDistanceText)
        assertTrue(composed.combinedRaw.firstAlertImageVisible)
    }

    @Test
    fun `composeSnapshot never lets an unavailable full-alert mask the working speed slot`() {
        val composed = VietMapWidgetTextParser.composeSnapshot(
            speed = VietMapProviderState(
                VietMapWidgetRawValues(currentSpeedText = "55", speedLimitText = "60"),
                VietMapWidgetFreshness.FRESH, null, 10_000L,
            ),
            alerts = VietMapProviderState(
                VietMapWidgetRawValues(), VietMapWidgetFreshness.FRESH, null, 10_000L,
            ),
            alertFull = VietMapProviderState(
                raw = null,
                freshness = VietMapWidgetFreshness.UNAVAILABLE,
                reason = VietMapWidgetUnavailableReason.NOT_BOUND,
                updatedAtElapsedMs = null,
            ),
            providerVersion = "3.3.4",
            nowElapsedMs = 12_000L,
        )
        val s = composed.snapshot
        // Combined + speed stay FRESH; full-alert is independently UNAVAILABLE.
        assertEquals(VietMapWidgetFreshness.FRESH, s.freshness)
        assertEquals(VietMapWidgetFreshness.FRESH, s.speedFreshness)
        assertEquals(VietMapWidgetFreshness.UNAVAILABLE, s.alertFullFreshness)
        assertEquals(55, s.currentSpeedKph)
        assertEquals(60, s.speedLimitKph)
        // No upcoming leaked while the full-alert slot is down.
        assertNull(s.upcomingLimitKph)
        assertNull(s.upcomingDistanceText)
    }

    @Test
    fun `composeSnapshot hides upcoming values while full-alert is only stale`() {
        val composed = VietMapWidgetTextParser.composeSnapshot(
            speed = VietMapProviderState(
                VietMapWidgetRawValues(currentSpeedText = "40", speedLimitText = "50"),
                VietMapWidgetFreshness.FRESH, null, 10_000L,
            ),
            alerts = VietMapProviderState(
                VietMapWidgetRawValues(), VietMapWidgetFreshness.FRESH, null, 10_000L,
            ),
            alertFull = VietMapProviderState(
                VietMapWidgetRawValues(upcomingSpeedLimitText = "60", upcomingDistanceText = "200 m"),
                VietMapWidgetFreshness.STALE, null, 5_000L,
            ),
            providerVersion = "3.3.4",
            nowElapsedMs = 12_000L,
        )
        assertNull(composed.snapshot.upcomingLimitKph)
        assertNull(composed.snapshot.upcomingDistanceMeters)
        assertEquals(VietMapWidgetFreshness.STALE, composed.snapshot.alertFullFreshness)
    }
}
