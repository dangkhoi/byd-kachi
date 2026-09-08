package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit test for [UpcomingBadgeDecision] — the pure show/hide + value decision for the "upcoming speed-limit
 * ahead" cluster badge (spec `upcoming-speed-limit-badge`, OQ2 = mirror VietMap, no own distance threshold).
 */
class UpcomingBadgeDecisionTest {

    @Test fun `null limit hides`() {
        val d = UpcomingBadgeDecision.decide(limitKph = null, distanceMeters = 300, fresh = true)
        assertFalse(d.show)
    }

    @Test fun `stale hides even with a valid limit`() {
        val d = UpcomingBadgeDecision.decide(limitKph = 60, distanceMeters = 300, fresh = false)
        assertFalse(d.show)
    }

    @Test fun `fresh valid limit with distance shows and carries the values`() {
        val d = UpcomingBadgeDecision.decide(limitKph = 60, distanceMeters = 300, fresh = true)
        assertTrue(d.show)
        assertEquals(60, d.limitKph)
        assertEquals(300, d.distanceMeters)
    }

    @Test fun `distance zero hides (already reached)`() {
        val d = UpcomingBadgeDecision.decide(limitKph = 60, distanceMeters = 0, fresh = true)
        assertFalse(d.show)
    }

    @Test fun `negative distance hides`() {
        val d = UpcomingBadgeDecision.decide(limitKph = 60, distanceMeters = -5, fresh = true)
        assertFalse(d.show)
    }

    @Test fun `zero limit hides`() {
        val d = UpcomingBadgeDecision.decide(limitKph = 0, distanceMeters = 300, fresh = true)
        assertFalse(d.show)
    }

    @Test fun `negative limit hides`() {
        val d = UpcomingBadgeDecision.decide(limitKph = -30, distanceMeters = 300, fresh = true)
        assertFalse(d.show)
    }

    @Test fun `fresh valid limit with null distance shows with distance zero (mirror VietMap)`() {
        val d = UpcomingBadgeDecision.decide(limitKph = 80, distanceMeters = null, fresh = true)
        assertTrue(d.show)
        assertEquals(80, d.limitKph)
        assertEquals(0, d.distanceMeters)
    }

    @Test fun `HIDDEN constant is not shown`() {
        assertFalse(UpcomingBadge.HIDDEN.show)
    }
}
