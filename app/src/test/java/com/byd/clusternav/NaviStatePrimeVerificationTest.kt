package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TASK 6 (closeout 1.28) verification pin: naviState prime is VERIFIED NO-GAP. Per frame the delivery lambda
 * emits the AUTONAVI lane broadcast (naviState=1) via emitLane() BEFORE enqueuing the HAL frame via owner.push().
 * emitLane's first emission is synchronous (AmapEmissionArbiter.sourceFrame → sink → sendBroadcast; only the
 * 400ms heartbeat uses the main Handler), while owner.push only enqueues to the async single-thread worker — so
 * the broadcast happens-before the HAL write is submitted, at boot too. naviState=1 latches (held until STOP) and
 * is heartbeat-re-asserted, so any cross-process OEM transient self-corrects within a beat. No prime broadcast is
 * invented (no-assumptions: OEM broadcast semantics are unmeasured).
 */
class NaviStatePrimeVerificationTest {

    private val navRepo by lazy { SourceRoots.text("src/main/java/com/byd/clusternav/NavRepository.kt") }

    @Test
    fun `delivery lambda emits the lane broadcast before enqueuing the HAL frame`() {
        val emitIdx = navRepo.indexOf("ClusterBroadcaster.emitLane(context, frame.toNavState()")
        val pushIdx = navRepo.indexOf("owner.push(")
        assertTrue(emitIdx >= 0, "delivery lambda must emit the lane broadcast (naviState=1)")
        assertTrue(pushIdx >= 0, "delivery lambda must push the HAL frame")
        assertTrue(emitIdx < pushIdx, "broadcast (naviState=1) must be emitted before the HAL push, per frame")
    }

    @Test
    fun `the NO-GAP verification is documented (no invented prime broadcast)`() {
        assertTrue(navRepo.contains("VERIFIED NO-GAP"), "TASK 6 finding must be documented in the delivery lambda")
    }
}
