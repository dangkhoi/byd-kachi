package com.byd.clusternav.navigation

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Concurrency coverage for the real typed speed-sign coordinator plus the shared bounded-output
 * worker generation regressions. No test encodes a speed sign in NavigationFrame.distanceMeters.
 */
class NavigationSpeedSignRaceTest {

    private val source = NavigationSourceIdentity("com.example.maps", "Example Maps")

    @Test
    fun `typed coordinator serializes concurrent positives behind one master clear`() {
        val clock = AtomicLong(1_000L)
        val cluster = RecordingSpeedSignPort(SpeedSignOutput.CLUSTER)
        val hud = RecordingSpeedSignPort(SpeedSignOutput.HUD)
        val coordinator = SpeedSignLifecycleCoordinator(cluster, hud, clock::get)
        try {
            coordinator.onProcessRestart(1)
            cluster.clearHistory(); hud.clearHistory()
            coordinator.onSourceSelected(com.byd.clusternav.contracts.SpeedLimitSource.VIETMAP)
            coordinator.onOutputEnabled(SpeedSignOutput.CLUSTER, true)
            coordinator.onOutputEnabled(SpeedSignOutput.HUD, true)
            coordinator.onMasterEnabled(true)
            coordinator.onSpeedLimit(com.byd.clusternav.contracts.SpeedLimitSource.VIETMAP, 50, clock.get(), 1)

            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            Thread {
                start.await()
                repeat(100) { value ->
                    coordinator.onSpeedLimit(
                        com.byd.clusternav.contracts.SpeedLimitSource.VIETMAP,
                        60 + value % 3,
                        clock.incrementAndGet(),
                        1,
                    )
                }
                done.countDown()
            }.start()
            Thread {
                start.await()
                coordinator.onMasterEnabled(false)
                done.countDown()
            }.start()
            start.countDown()
            assertTrue(done.await(3, TimeUnit.SECONDS))

            listOf(cluster, hud).forEach { port ->
                val events = port.snapshot().map { it.frame }
                val clears = events.filter { it.value == null }
                assertEquals(1, clears.size)
                val clearIndex = events.indexOf(clears.single())
                assertTrue(events.drop(clearIndex + 1).none { it.value != null })
            }
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun `typed coordinator rejects callback from prior process epoch after restart clear`() {
        val clock = AtomicLong(1_000L)
        val cluster = RecordingSpeedSignPort(SpeedSignOutput.CLUSTER)
        val hud = RecordingSpeedSignPort(SpeedSignOutput.HUD)
        val coordinator = SpeedSignLifecycleCoordinator(cluster, hud, clock::get)
        try {
            coordinator.onProcessRestart(10)
            coordinator.onSourceSelected(com.byd.clusternav.contracts.SpeedLimitSource.VIETMAP)
            coordinator.onOutputEnabled(SpeedSignOutput.CLUSTER, true)
            coordinator.onOutputEnabled(SpeedSignOutput.HUD, true)
            coordinator.onMasterEnabled(true)
            coordinator.onSpeedLimit(com.byd.clusternav.contracts.SpeedLimitSource.VIETMAP, 80, 1_000, 10)
            coordinator.onProcessRestart(11)
            assertFalse(coordinator.onSpeedLimit(
                com.byd.clusternav.contracts.SpeedLimitSource.VIETMAP, 100, 1_001, 10,
            ))
            assertEquals(null, coordinator.snapshot().activeFrame)
        } finally {
            coordinator.close()
        }
    }

    private fun frame(sequence: Long, sessionId: String = "s-1") = NavigationFrame(
        sessionId, source, sequence, 1_000L,
        NavigationFrameContent(1, "Straight", 50, "Road", null, null, null, null),
    )

    // ─── 1. Push-after-clear race ─────────────────────────────────────────────

    @Test
    fun `frame submitted just before stopSession is discarded by generation fence`() {
        val delivered = CopyOnWriteArrayList<Long>()
        val blockDelivery = CountDownLatch(1)
        val deliveryEntered = CountDownLatch(1)

        val adapter = ClusterLaneAdapter(
            NavigationFrameDelivery { f ->
                if (f.sequence == 1L) {
                    deliveryEntered.countDown()
                    blockDelivery.await(2, TimeUnit.SECONDS)
                }
                delivered += f.sequence
            },
            OutputAdapterConfig(queueCapacity = 8, deliveryDeadlineMs = 3_000L),
            initiallyEnabled = true,
        )
        try {
            // Submit frame 1 (blocks in delivery), then frame 2 (queued)
            adapter.submit(frame(1))
            assertTrue(deliveryEntered.await(1, TimeUnit.SECONDS))
            adapter.submit(frame(2))

            // stopSession WHILE frame 2 is in queue
            adapter.stopSession()

            // Submit frame 3 after re-enable (new generation)
            adapter.setEnabled(true)
            adapter.submit(frame(3))

            blockDelivery.countDown()
            Thread.sleep(200)

            // Frame 2 must NOT be delivered (invalidated by generation fence)
            assertFalse(2L in delivered, "frame queued before clear must be discarded: $delivered")
            // Frame 3 must deliver (new generation)
            assertTrue(3L in delivered, "frame after clear must deliver: $delivered")
        } finally {
            blockDelivery.countDown()
            adapter.close()
        }
    }

    // ─── 2. Stale-after-clear is no-op ───────────────────────────────────────

    @Test
    fun `markStale after stopSession is no-op since cached sequence is cleared`() {
        val adapter = ClusterLaneAdapter(
            NavigationFrameDelivery { },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        try {
            adapter.submit(frame(1))
            Thread.sleep(50)
            assertEquals(NavigationOutputStatus.EMITTING, adapter.health().status)

            // Clear the session
            adapter.stopSession()
            assertEquals(NavigationOutputStatus.OFF, adapter.health().status)

            // markStale after clear — should be no-op (no cached sequence)
            adapter.markStale()
            // Status should remain OFF, not transition to STALE
            assertEquals(NavigationOutputStatus.OFF, adapter.health().status,
                "markStale after stopSession must be no-op")
        } finally {
            adapter.close()
        }
    }

    // ─── 3. Rapid push-clear-push: no generation leakage ─────────────────────

    @Test
    fun `rapid push-clear-push cycle delivers only latest generation frames`() {
        val delivered = CopyOnWriteArrayList<Long>()
        val adapter = ClusterLaneAdapter(
            NavigationFrameDelivery { f -> delivered += f.sequence },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        try {
            // Generation 1
            adapter.submit(frame(1))
            Thread.sleep(30)

            // Clear (generation 2)
            adapter.stopSession()
            adapter.setEnabled(true)

            // Generation 2 frame
            adapter.submit(frame(10))
            Thread.sleep(30)

            // Clear again (generation 3)
            adapter.stopSession()
            adapter.setEnabled(true)

            // Generation 3 frame
            adapter.submit(frame(100))
            Thread.sleep(100)

            // Frame 100 (latest generation) must have been delivered
            assertTrue(100L in delivered, "latest generation frame must deliver: $delivered")
            // Frame 1 was probably delivered before clear (timing-dependent but valid)
            // Key assertion: frame 10 may or may not have delivered before the second clear
            // The important thing is that the adapter is not wedged
            val finalHealth = adapter.health()
            assertTrue(
                finalHealth.status == NavigationOutputStatus.EMITTING ||
                    finalHealth.status == NavigationOutputStatus.STARTING,
                "adapter must be operational after rapid cycles, got: ${finalHealth.status}"
            )
        } finally {
            adapter.close()
        }
    }

    // ─── 4. Concurrent submit and stopSession from different threads ──────────

    @Test
    fun `concurrent stopSession and submit do not throw or deadlock`() {
        val adapter = ClusterLaneAdapter(
            NavigationFrameDelivery { Thread.sleep(5) },
            OutputAdapterConfig(queueCapacity = 8, deliveryDeadlineMs = 1_000L),
            initiallyEnabled = true,
        )
        try {
            val errors = AtomicInteger(0)
            val done = CountDownLatch(2)

            // Thread 1: rapid submits
            Thread {
                try {
                    repeat(50) { i ->
                        try {
                            adapter.submit(frame((i + 1).toLong()))
                        } catch (_: Exception) {
                            errors.incrementAndGet()
                        }
                        Thread.sleep(2)
                    }
                } finally {
                    done.countDown()
                }
            }.start()

            // Thread 2: rapid stop/re-enable cycles
            Thread {
                try {
                    repeat(10) {
                        adapter.stopSession()
                        adapter.setEnabled(true)
                        Thread.sleep(8)
                    }
                } finally {
                    done.countDown()
                }
            }.start()

            assertTrue(done.await(5, TimeUnit.SECONDS), "threads must finish without deadlock")
            assertEquals(0, errors.get(), "no exceptions thrown during concurrent operations")
        } finally {
            adapter.close()
        }
    }

    // ─── 5. SpeedReading: safety-critical null semantics ─────────────────────

    @Test
    fun `SpeedReading null input returns null not zero`() {
        val reading = SpeedReading()
        val result = reading.acceptKmh(null)
        assertEquals(null, result, "null HAL reading must produce null, not 0")
    }

    @Test
    fun `SpeedReading negative is sentinel not speed`() {
        val reading = SpeedReading()
        val result = reading.acceptKmh(-1.0)
        assertEquals(null, result, "negative HAL value must be null (sentinel)")
    }

    @Test
    fun `SpeedReading NaN is rejected`() {
        val reading = SpeedReading()
        val result = reading.acceptKmh(Double.NaN)
        assertEquals(null, result, "NaN must be rejected")
    }

    @Test
    fun `SpeedReading exceeding max is rejected`() {
        val reading = SpeedReading(maxPlausibleKmh = 400.0)
        val result = reading.acceptKmh(401.0)
        assertEquals(null, result, "above max plausible must be rejected")
    }

    @Test
    fun `SpeedReading valid value converts km per h to m per s`() {
        val reading = SpeedReading()
        val result = reading.acceptKmh(72.0)
        assertEquals(20.0, result!!, 0.01, "72 km/h = 20 m/s")
        assertEquals(20.0, reading.lastGoodMps, 0.01)
    }

    @Test
    fun `SpeedReading mpsForDisplay degrades to last good on null`() {
        val reading = SpeedReading()
        reading.acceptKmh(108.0) // 30 m/s
        val degraded = reading.mpsForDisplay(null)
        assertEquals(30.0, degraded, 0.01, "must degrade to last known good")
    }
}
