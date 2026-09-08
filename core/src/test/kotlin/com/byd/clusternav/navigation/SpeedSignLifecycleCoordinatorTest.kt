package com.byd.clusternav.navigation

import com.byd.clusternav.contracts.SpeedLimitClearReason
import com.byd.clusternav.contracts.SpeedLimitFrame
import com.byd.clusternav.contracts.SpeedLimitSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedSignLifecycleCoordinatorTest {
    @Test
    fun `WAZE and VIETMAP positive to zero each clear both ports exactly once`() {
        sources().forEach { source ->
            assertGlobalClear(source, SpeedLimitClearReason.ZERO_VALUE) { fixture ->
                assertTrue(fixture.coordinator.onSpeedLimit(source, 0, fixture.clock.now(), fixture.epoch))
            }
        }
    }

    @Test
    fun `TTL is monotonic and expires at exactly 5000ms for both sources`() {
        sources().forEach { source ->
            val fixture = Fixture(source)
            fixture.positive()
            fixture.scheduler.advanceBy(4_999)
            assertEquals(0, fixture.cluster.clears().size)
            assertEquals(0, fixture.hud.clears().size)
            fixture.scheduler.advanceBy(1)
            fixture.assertSingleGlobalClear(SpeedLimitClearReason.TTL_EXPIRED)
            fixture.close()
        }
    }

    @Test
    fun `source switch disconnect stop and master off clear each source once`() {
        sources().forEach { source ->
            // Bỏ ca SOURCE_SWITCHED: chỉ còn một nguồn thật nên "đổi sang nguồn khác" KHÔNG còn xảy ra được
            // (onSourceSelected cùng nguồn thì return sớm). Nhánh đó nay bất khả đạt trong sản phẩm.
            assertGlobalClear(source, SpeedLimitClearReason.PROVIDER_DISCONNECTED) {
                it.coordinator.onProviderDisconnected(source)
                it.coordinator.onProviderDisconnected(source)
            }
            assertGlobalClear(source, SpeedLimitClearReason.SOURCE_STOPPED) {
                it.coordinator.onSourceStopped(source)
                it.coordinator.onSourceStopped(source)
            }
            assertGlobalClear(source, SpeedLimitClearReason.MASTER_DISABLED) {
                it.coordinator.onMasterEnabled(false)
                it.coordinator.onMasterEnabled(false)
            }
        }
    }

    @Test
    fun `individual output OFF clears only that port and never backpressures its peer`() {
        sources().forEach { source ->
            SpeedSignOutput.entries.forEach { disabled ->
                val fixture = Fixture(source)
                fixture.positive()
                fixture.coordinator.onOutputEnabled(disabled, false)
                val disabledPort = fixture.port(disabled)
                val peerPort = fixture.port(if (disabled == SpeedSignOutput.CLUSTER) SpeedSignOutput.HUD else SpeedSignOutput.CLUSTER)
                assertEquals(listOf(SpeedLimitClearReason.OUTPUT_DISABLED), disabledPort.clears().map { it.clearReason })
                assertTrue(peerPort.clears().isEmpty(), "peer output must remain active")
                assertEquals(source, peerPort.positives().single().source)
                fixture.close()
            }
        }
    }

    @Test
    fun `output re-enable preserves the original absolute TTL deadline`() {
        sources().forEach { source ->
            val fixture = Fixture(source)
            fixture.positive()
            fixture.coordinator.onOutputEnabled(SpeedSignOutput.HUD, false)
            fixture.scheduler.advanceBy(2_000)
            fixture.coordinator.onOutputEnabled(SpeedSignOutput.HUD, true)

            fixture.scheduler.advanceBy(2_999)
            assertEquals(80, fixture.coordinator.snapshot().activeFrame?.value)
            assertTrue(fixture.cluster.clears().isEmpty())

            fixture.scheduler.advanceBy(1)
            assertNull(fixture.coordinator.snapshot().activeFrame)
            assertEquals(SpeedLimitClearReason.TTL_EXPIRED, fixture.cluster.clears().single().clearReason)
            assertEquals(SpeedLimitClearReason.TTL_EXPIRED, fixture.hud.clears().last().clearReason)
            fixture.assertNoPositiveAfterClear()
            fixture.close()
        }
    }

    @Test
    fun `process restart force clears both ports and rejects prior epoch callbacks`() {
        sources().forEach { source ->
            val fixture = Fixture(source)
            fixture.positive()
            assertTrue(fixture.coordinator.onProcessRestart(fixture.epoch + 1))
            fixture.assertSingleGlobalClear(SpeedLimitClearReason.PROCESS_RESTARTED)
            assertFalse(fixture.coordinator.onSpeedLimit(source, 90, fixture.clock.now(), fixture.epoch))
            assertNull(fixture.coordinator.snapshot().activeFrame)
            fixture.close()
        }
    }

    @Test
    fun `queue saturation priority clears both independent ports`() {
        sources().forEach { source ->
            val clock = Clock()
            val scheduler = ManualScheduler(clock)
            val cluster = SaturatingPort(SpeedSignOutput.CLUSTER)
            val hud = RecordingSpeedSignPort(SpeedSignOutput.HUD)
            val coordinator = SpeedSignLifecycleCoordinator(cluster, hud, clock::now, scheduler)
            coordinator.onProcessRestart(1)
            cluster.clearHistory()
            hud.clearHistory()
            coordinator.onSourceSelected(source)
            coordinator.onOutputEnabled(SpeedSignOutput.CLUSTER, true)
            coordinator.onOutputEnabled(SpeedSignOutput.HUD, true)
            coordinator.onMasterEnabled(true)

            assertTrue(coordinator.onSpeedLimit(source, 80, clock.now(), 1))
            assertEquals(listOf(SpeedLimitClearReason.QUEUE_SATURATED), cluster.clears().map { it.clearReason })
            assertEquals(listOf(SpeedLimitClearReason.QUEUE_SATURATED), hud.clears().map { it.clearReason })
            assertTrue(hud.snapshot().last().priorityClear)
            assertNull(coordinator.snapshot().activeFrame)
            coordinator.close()
        }
    }

    @Test
    fun `old positive and old TTL cannot follow a lifecycle clear`() {
        sources().forEach { source ->
            val fixture = Fixture(source)
            fixture.positive(50)
            val oldEpoch = fixture.epoch
            fixture.coordinator.onMasterEnabled(false)
            assertFalse(fixture.coordinator.onSpeedLimit(source, 100, fixture.clock.now() - 1, oldEpoch))
            fixture.scheduler.advanceBy(10_000)
            fixture.assertSingleGlobalClear(SpeedLimitClearReason.MASTER_DISABLED)
            fixture.assertNoPositiveAfterClear()
            fixture.close()
        }
    }

    private fun assertGlobalClear(
        source: SpeedLimitSource,
        reason: SpeedLimitClearReason,
        event: (Fixture) -> Unit,
    ) {
        val fixture = Fixture(source)
        fixture.positive()
        event(fixture)
        fixture.assertSingleGlobalClear(reason)
        fixture.assertNoPositiveAfterClear()
        fixture.close()
    }

    // 2026-08-22: chỉ còn MỘT nguồn thật (widget VietMap) — hằng WAZE đã gỡ vì không producer nào sinh ra.
    private fun sources() = listOf(SpeedLimitSource.VIETMAP)

    private class Fixture(val source: SpeedLimitSource) : AutoCloseable {
        val clock = Clock()
        val scheduler = ManualScheduler(clock)
        val cluster = RecordingSpeedSignPort(SpeedSignOutput.CLUSTER)
        val hud = RecordingSpeedSignPort(SpeedSignOutput.HUD)
        val epoch = 10L
        val coordinator = SpeedSignLifecycleCoordinator(cluster, hud, clock::now, scheduler)

        init {
            coordinator.onProcessRestart(epoch)
            cluster.clearHistory()
            hud.clearHistory()
            coordinator.onSourceSelected(source)
            coordinator.onOutputEnabled(SpeedSignOutput.CLUSTER, true)
            coordinator.onOutputEnabled(SpeedSignOutput.HUD, true)
            coordinator.onMasterEnabled(true)
        }

        fun positive(value: Int = 80) {
            assertTrue(coordinator.onSpeedLimit(source, value, clock.now(), epoch))
        }

        fun port(output: SpeedSignOutput) = if (output == SpeedSignOutput.CLUSTER) cluster else hud

        fun assertSingleGlobalClear(reason: SpeedLimitClearReason) {
            listOf(cluster, hud).forEach { port ->
                val clears = port.snapshot().map { it.frame }.filter { it.value == null }
                assertEquals(1, clears.size, "${port.output} clear count")
                assertEquals(reason, clears.single().clearReason)
                assertEquals(source, clears.single().source)
            }
        }

        fun assertNoPositiveAfterClear() {
            listOf(cluster, hud).forEach { port ->
                val events = port.snapshot()
                val clearIndex = events.indexOfLast { it.frame.value == null }
                assertTrue(clearIndex >= 0)
                assertTrue(events.drop(clearIndex + 1).none { it.frame.value != null })
                val clear = events[clearIndex].frame
                events.take(clearIndex).lastOrNull { it.frame.value != null }?.frame?.let { positive ->
                    assertTrue(clear.isStrictSuccessorOf(positive))
                }
            }
        }

        override fun close() = coordinator.close()
    }

    private class SaturatingPort(override val output: SpeedSignOutput) : SpeedSignPort {
        private val events = mutableListOf<RecordedSpeedSignEmission>()
        override fun publish(frame: SpeedLimitFrame, generation: Long) = SpeedSignSubmission.QUEUE_SATURATED
        override fun replaceWithClear(frame: SpeedLimitFrame, generation: Long): SpeedSignSubmission {
            events += RecordedSpeedSignEmission(frame, generation, priorityClear = true)
            return SpeedSignSubmission.ACCEPTED
        }
        fun clears() = events.map { it.frame }.filter { it.value == null }
        fun clearHistory() = events.clear()
        override fun close() = Unit
    }

    private class Clock {
        private var value = 1_000L
        fun now() = value
        fun advance(delta: Long) { value += delta }
    }

    private class ManualScheduler(private val clock: Clock) : SpeedSignScheduler {
        private data class Task(val dueAt: Long, val action: () -> Unit, var cancelled: Boolean = false)
        private val tasks = mutableListOf<Task>()
        override fun schedule(delayMs: Long, action: () -> Unit): SpeedSignScheduledTask {
            val task = Task(clock.now() + delayMs, action)
            tasks += task
            return SpeedSignScheduledTask { task.cancelled = true }
        }
        fun advanceBy(delta: Long) {
            val target = clock.now() + delta
            while (true) {
                val task = tasks.filter { !it.cancelled && it.dueAt <= target }.minByOrNull { it.dueAt }
                    ?: break
                tasks.remove(task)
                clock.advance(task.dueAt - clock.now())
                task.action()
            }
            clock.advance(target - clock.now())
        }
    }

    private fun RecordingSpeedSignPort.clears() = snapshot().map { it.frame }.filter { it.value == null }
    private fun RecordingSpeedSignPort.positives() = snapshot().map { it.frame }.filter { it.value != null }
}
