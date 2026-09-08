package com.byd.clusternav.vietmapwidget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for per-provider independence: bad alerts freshness does NOT invalidate fresh speed.
 * Tests the VietMapWidgetTextParser.freshness() and parseSnapshot() contracts for T3 remediation.
 */
class VietMapWidgetProviderIndependenceTest {

    @Test
    fun `fresh speed is not invalidated by stale alerts`() {
        // Speed updated 2s ago (fresh), alerts updated 10s ago (stale)
        val now = 20_000L
        val speedUpdatedAt = 18_000L // 2s ago → FRESH
        val alertsUpdatedAt = 10_000L // 10s ago → STALE

        val (speedFreshness, speedReason) = VietMapWidgetTextParser.freshness(speedUpdatedAt, now, null)
        val (alertsFreshness, alertsReason) = VietMapWidgetTextParser.freshness(alertsUpdatedAt, now, null)

        assertEquals(VietMapWidgetFreshness.FRESH, speedFreshness)
        assertNull(speedReason)
        assertEquals(VietMapWidgetFreshness.STALE, alertsFreshness)
        assertNull(alertsReason)
    }

    @Test
    fun `fresh alerts not invalidated by unavailable speed`() {
        val now = 50_000L
        val speedUpdatedAt = 10_000L // 40s ago → UNAVAILABLE (>30s)
        val alertsUpdatedAt = 48_000L // 2s ago → FRESH

        val (speedFreshness, _) = VietMapWidgetTextParser.freshness(speedUpdatedAt, now, null)
        val (alertsFreshness, _) = VietMapWidgetTextParser.freshness(alertsUpdatedAt, now, null)

        assertEquals(VietMapWidgetFreshness.UNAVAILABLE, speedFreshness)
        assertEquals(VietMapWidgetFreshness.FRESH, alertsFreshness)
    }

    @Test
    fun `per-provider unavailable reason is independent`() {
        // Speed has PROVIDER_MISSING reason, alerts has no reason
        val (speedFresh, speedReason) = VietMapWidgetTextParser.freshness(
            5_000L, 7_000L, VietMapWidgetUnavailableReason.PROVIDER_MISSING
        )
        val (alertsFresh, alertsReason) = VietMapWidgetTextParser.freshness(
            5_000L, 7_000L, null
        )

        assertEquals(VietMapWidgetFreshness.UNAVAILABLE, speedFresh)
        assertEquals(VietMapWidgetUnavailableReason.PROVIDER_MISSING, speedReason)
        assertEquals(VietMapWidgetFreshness.FRESH, alertsFresh)
        assertNull(alertsReason)
    }

    @Test
    fun `per-provider snapshot model stores independent generation`() {
        val speedSnap = VietMapProviderSnapshot(
            slot = VietMapWidgetSlot.SPEED_LIMIT,
            values = VietMapWidgetRawValues(currentSpeedText = "60", speedLimitText = "80"),
            updatedAtElapsedMs = 10_000L,
            freshness = VietMapWidgetFreshness.FRESH,
            reason = null,
            generation = 5L,
        )
        val alertsSnap = VietMapProviderSnapshot(
            slot = VietMapWidgetSlot.ALERTS,
            values = VietMapWidgetRawValues(firstAlertDistanceText = "200 m"),
            updatedAtElapsedMs = 8_000L,
            freshness = VietMapWidgetFreshness.STALE,
            reason = null,
            generation = 3L, // different generation — providers are independent
        )

        assertEquals(VietMapWidgetSlot.SPEED_LIMIT, speedSnap.slot)
        assertEquals(5L, speedSnap.generation)
        assertEquals(VietMapWidgetSlot.ALERTS, alertsSnap.slot)
        assertEquals(3L, alertsSnap.generation)
        // Speed is fresh even though alerts is stale — they are independent
        assertEquals(VietMapWidgetFreshness.FRESH, speedSnap.freshness)
        assertEquals(VietMapWidgetFreshness.STALE, alertsSnap.freshness)
    }

    @Test
    fun `parseSnapshot produces valid data when speed is fresh`() {
        val raw = VietMapWidgetRawValues(
            currentSpeedText = "55",
            speedLimitText = "60",
        )
        val snapshot = VietMapWidgetTextParser.parseSnapshot(
            raw = raw,
            providerVersion = "3.4.0",
            updatedAtElapsedMs = 10_000L,
            nowElapsedMs = 12_000L,
            unavailableReason = null,
        )
        assertEquals(VietMapWidgetFreshness.FRESH, snapshot.freshness)
        assertEquals(55, snapshot.currentSpeedKph)
        assertEquals(60, snapshot.speedLimitKph)
    }

    @Test
    fun `stale snapshot does not expose driving values`() {
        val raw = VietMapWidgetRawValues(
            currentSpeedText = "90",
            speedLimitText = "50",
        )
        val snapshot = VietMapWidgetTextParser.parseSnapshot(
            raw = raw,
            providerVersion = "3.4.0",
            updatedAtElapsedMs = 1_000L,
            nowElapsedMs = 8_000L, // 7s old → stale
            unavailableReason = null,
        )
        assertEquals(VietMapWidgetFreshness.STALE, snapshot.freshness)
        assertNull(snapshot.currentSpeedKph)
        assertNull(snapshot.speedLimitKph)
    }
}
