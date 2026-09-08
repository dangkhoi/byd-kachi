package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * J1 keep-alive wiring contract. The HUD/centre keep-alive lives INSIDE NavigationHudOwner and re-asserts the
 * last applied frame periodically (bypassing dedup), WITHOUT coupling the cluster-lane broadcaster to the HAL
 * owner — the two-track boundary the PhysicalHudOwnershipTest also guards.
 */
class HudKeepAliveWiringTest {

    @Test
    fun `keep-alive is wired inside the HUD owner`() {
        val owner = SourceRoots.text("src/main/java/com/byd/clusternav/NavigationHudOwner.kt")
        assertTrue(owner.contains("HudKeepAlivePolicy"), "owner must use the pure keep-alive policy")
        assertTrue(owner.contains("scheduleWithFixedDelay"), "owner must schedule a periodic keep-alive tick")
        assertTrue(owner.contains("resubmitApplied"), "owner must re-assert the last applied frame on tick")
        assertTrue(owner.contains("keepAliveScheduler.shutdownNow"), "owner must shut the scheduler down on close")
    }

    @Test
    fun `cluster-lane broadcaster stays decoupled from the HAL owner and its keep-alive`() {
        val broadcaster = SourceRoots.text("src/main/java/com/byd/clusternav/ClusterBroadcaster.kt")
        assertFalse(broadcaster.contains("NavigationHudOwner"), "broadcaster must not reference the HAL owner")
        assertFalse(broadcaster.contains("HudKeepAlivePolicy"), "keep-alive belongs to the owner, not the lane feeder")
    }
}
