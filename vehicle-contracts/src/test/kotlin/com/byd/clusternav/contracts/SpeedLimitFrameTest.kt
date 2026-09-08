package com.byd.clusternav.contracts

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedLimitFrameTest {
    @Test
    fun `active frame is typed fresh and bounded`() {
        val frame = SpeedLimitFrame.active(
            value = 80,
            signType = SpeedSignType.REGULATORY,
            limitType = SpeedLimitType.ABSOLUTE,
            unit = SpeedUnit.KPH,
            source = SpeedLimitSource.VIETMAP,
            sequence = 7,
            observedAtMonotonicMs = 1_000,
            validUntilMonotonicMs = 6_000,
        )

        assertTrue(frame.freshness.isFreshAt(5_999))
        assertFalse(frame.freshness.isFreshAt(6_000))
        assertFalse(frame.freshness.isFreshAt(6_001))
        assertThrows(IllegalArgumentException::class.java) { frame.copy(value = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            frame.copy(source = SpeedLimitSource.NONE)
        }
    }

    @Test
    fun `clear frame requires null value typed reason and no retained sign metadata`() {
        val clear = SpeedLimitFrame.clear(
            unit = SpeedUnit.KPH,
            source = SpeedLimitSource.VIETMAP,
            sequence = 8,
            observedAtMonotonicMs = 6_000,
            reason = SpeedLimitClearReason.TTL_EXPIRED,
            state = FreshnessState.STALE,
        )

        assertTrue(clear.value == null && clear.clearReason == SpeedLimitClearReason.TTL_EXPIRED)
        assertThrows(IllegalArgumentException::class.java) {
            clear.copy(signType = SpeedSignType.UNKNOWN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            clear.copy(clearReason = null)
        }
    }

    @Test
    fun `successor requires increasing sequence and non-regressing monotonic observation`() {
        val first = SpeedLimitFrame.active(
            50, null, null, SpeedUnit.KPH, SpeedLimitSource.VIETMAP, 10, 100, 5_100,
        )
        val next = SpeedLimitFrame.clear(
            SpeedUnit.KPH, SpeedLimitSource.VIETMAP, 11, 5_100,
            SpeedLimitClearReason.TTL_EXPIRED, FreshnessState.STALE,
        )

        assertTrue(next.isStrictSuccessorOf(first))
        assertFalse(next.copy(sequence = 10).isStrictSuccessorOf(first))
        assertFalse(next.copy(freshness = next.freshness.copy(observedAtMonotonicMs = 99, validUntilMonotonicMs = 99))
            .isStrictSuccessorOf(first))
    }
}
