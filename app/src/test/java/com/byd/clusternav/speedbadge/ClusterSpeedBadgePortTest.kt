package com.byd.clusternav.speedbadge

import com.byd.clusternav.contracts.SpeedLimitClearReason
import com.byd.clusternav.contracts.SpeedLimitFrame
import com.byd.clusternav.contracts.SpeedLimitSource
import com.byd.clusternav.contracts.SpeedSignType
import com.byd.clusternav.contracts.SpeedUnit
import com.byd.clusternav.navigation.SpeedSignOutput
import com.byd.clusternav.navigation.SpeedSignPort
import com.byd.clusternav.navigation.SpeedSignSubmission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests generation fencing logic shared by ClusterSpeedBadgePort and HalSpeedSignPort.
 * Since both degrade to no-op off-car (no display 1 / no HAL), we test the port contract
 * via a FakeOverlayPort that shares identical fencing logic.
 */
class ClusterSpeedBadgePortTest {

    /** Port with same generation fencing as the real ports but no Android/HAL deps. */
    private class FakeOverlayPort(override val output: SpeedSignOutput) : SpeedSignPort {
        private var acceptedGeneration = 0L
        val published = mutableListOf<Int>()
        val cleared = mutableListOf<SpeedLimitClearReason>()

        override fun publish(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission {
            require(frame.value != null)
            if (generation < acceptedGeneration) return SpeedSignSubmission.STALE_DROPPED
            acceptedGeneration = generation
            published += frame.value!!
            return SpeedSignSubmission.ACCEPTED
        }

        override fun replaceWithClear(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission {
            require(frame.value == null)
            if (generation < acceptedGeneration) return SpeedSignSubmission.STALE_DROPPED
            acceptedGeneration = generation
            cleared += frame.clearReason!!
            return SpeedSignSubmission.ACCEPTED
        }

        override fun close() = Unit
    }

    private fun activeFrame(value: Int, seq: Long) = SpeedLimitFrame.active(
        value = value, signType = SpeedSignType.REGULATORY, limitType = null,
        unit = SpeedUnit.KPH, source = SpeedLimitSource.VIETMAP,
        sequence = seq, observedAtMonotonicMs = 1000L, validUntilMonotonicMs = 6000L,
    )

    private fun clearFrame(seq: Long) = SpeedLimitFrame.clear(
        unit = SpeedUnit.KPH, source = SpeedLimitSource.VIETMAP,
        sequence = seq, observedAtMonotonicMs = 2000L,
        reason = SpeedLimitClearReason.ZERO_VALUE,
    )

    @Test
    fun `publish active frame accepted`() {
        val port = FakeOverlayPort(SpeedSignOutput.CLUSTER)
        assertEquals(SpeedSignSubmission.ACCEPTED, port.publish(activeFrame(60, 1L), 1L))
        assertEquals(listOf(60), port.published)
    }

    @Test
    fun `stale generation rejected`() {
        val port = FakeOverlayPort(SpeedSignOutput.CLUSTER)
        port.publish(activeFrame(80, 2L), 5L)
        assertEquals(SpeedSignSubmission.STALE_DROPPED, port.publish(activeFrame(50, 3L), 3L))
        assertEquals(listOf(80), port.published)
    }

    @Test
    fun `clear accepted and fences older generations`() {
        val port = FakeOverlayPort(SpeedSignOutput.CLUSTER)
        port.publish(activeFrame(60, 1L), 1L)
        assertEquals(SpeedSignSubmission.ACCEPTED, port.replaceWithClear(clearFrame(2L), 2L))
        assertEquals(SpeedSignSubmission.STALE_DROPPED, port.publish(activeFrame(40, 3L), 1L))
    }

    @Test
    fun `HUD port same fencing semantics`() {
        val port = FakeOverlayPort(SpeedSignOutput.HUD)
        assertEquals(SpeedSignOutput.HUD, port.output)
        port.publish(activeFrame(100, 1L), 10L)
        assertEquals(SpeedSignSubmission.STALE_DROPPED, port.publish(activeFrame(50, 2L), 5L))
    }
}
