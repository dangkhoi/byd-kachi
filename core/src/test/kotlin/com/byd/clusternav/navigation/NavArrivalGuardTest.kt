package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R7 (#2, docs/specs/cast-nav-ux-release-v104.html): the pure end-of-route decisions.
 * Reproduces the owner sequence off-car: 500 m approaching → spurious 3.5 km (dropped) → arrival
 * (clears). See [NavArrivalGuard].
 */
class NavArrivalGuardTest {

    // ── Arrival text detection ────────────────────────────────────────────────
    @Test
    fun `detects arrival phrases in EN and VI across fields`() {
        assertTrue(NavArrivalGuard.isArrivalText("Arrive at Big C", "")) // EN "arriv"
        assertTrue(NavArrivalGuard.isArrivalText("", "Bạn đã tới nơi"))
        assertTrue(NavArrivalGuard.isArrivalText(null, null, "Đã đến 123 Nguyễn Huệ")) // bigText field
        assertTrue(NavArrivalGuard.isArrivalText("Đến nơi"))
    }

    @Test
    fun `does not treat a normal turn as arrival`() {
        assertFalse(NavArrivalGuard.isArrivalText("500 m", "Rẽ phải vào Nguyễn Huệ"))
        assertFalse(NavArrivalGuard.isArrivalText("3.5 km", "Đi thẳng"))
        assertFalse(NavArrivalGuard.isArrivalText(null, null, null))
        assertFalse(NavArrivalGuard.isArrivalText("", "", ""))
    }

    // ── Route-remaining arrival ───────────────────────────────────────────────
    @Test
    fun `route-remaining at or below threshold is arrival, above is not`() {
        val g = NavArrivalGuard()
        assertTrue(g.arrivedByRouteRemaining(0))
        assertTrue(g.arrivedByRouteRemaining(25))
        assertFalse(g.arrivedByRouteRemaining(100))   // still approaching
        assertFalse(g.arrivedByRouteRemaining(3500))
        assertFalse(g.arrivedByRouteRemaining(null))  // not reported → not an arrival signal
    }

    // ── Distance-regression guard (the 500 m → 3.5 km bug) ────────────────────
    @Test
    fun `drops a spurious jump-up while approaching the same maneuver`() {
        val g = NavArrivalGuard()
        assertTrue(g.acceptDistance(500, "Big C|Đi thẳng"), "first frame accepted")
        // 500 m → 3.5 km on the SAME maneuver with no reroute → implausible → dropped.
        assertFalse(g.acceptDistance(3500, "Big C|Đi thẳng"), "spurious jump-up dropped")
        // A plausible continuation of the approach is still accepted.
        assertTrue(g.acceptDistance(400, "Big C|Đi thẳng"))
    }

    @Test
    fun `releases a persistent large value after the hysteresis window (real reroute recovers)`() {
        val g = NavArrivalGuard(releaseAfterRejects = 2)
        assertTrue(g.acceptDistance(500, "A|straight"))
        assertFalse(g.acceptDistance(3500, "A|straight")) // reject 1
        assertFalse(g.acceptDistance(3500, "A|straight")) // reject 2
        assertTrue(g.acceptDistance(3500, "A|straight"), "persistent value released after 2 rejects")
    }

    @Test
    fun `a new maneuver legitimately jumps up and is accepted`() {
        val g = NavArrivalGuard()
        assertTrue(g.acceptDistance(150, "Road A|Turn right"))
        // Different maneuver key (next turn) → jump to 2 km is real, must be accepted immediately.
        assertTrue(g.acceptDistance(2000, "Road B|Turn left"))
    }

    @Test
    fun `no guard once past the approach window`() {
        val g = NavArrivalGuard(approachMeters = 800)
        assertTrue(g.acceptDistance(5000, "A|straight")) // far out — establishes last-good 5000
        assertTrue(g.acceptDistance(9000, "A|straight"), "not approaching → large values pass")
    }

    @Test
    fun `negative distance (heading-only or arrival frame) is always accepted`() {
        val g = NavArrivalGuard()
        assertTrue(g.acceptDistance(300, "A|straight"))
        assertTrue(g.acceptDistance(-1, "A|straight"))
    }

    @Test
    fun `reset lets a fresh route start clean`() {
        val g = NavArrivalGuard()
        assertTrue(g.acceptDistance(200, "A|straight"))
        assertFalse(g.acceptDistance(3500, "A|straight")) // would be dropped mid-route
        g.reset()
        // After reset there is no last-good, so the first frame of the new route is accepted even if large.
        assertTrue(g.acceptDistance(3500, "New route|straight"))
    }
}
