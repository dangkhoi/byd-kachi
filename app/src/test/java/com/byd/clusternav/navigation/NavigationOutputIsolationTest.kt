package com.byd.clusternav.navigation

import com.byd.clusternav.testsupport.SourceRoots
import com.byd.clusternav.contracts.SpeedLimitClearReason
import com.byd.clusternav.contracts.SpeedLimitSource
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationOutputIsolationTest {
    private val source = NavigationSourceIdentity("com.example.maps")

    @Test fun `lane and HUD preserve peer delivery under bidirectional block throw and saturation`() {
        NavigationOutputTarget.entries.forEach { failingTarget ->
            assertBlockIsolation(failingTarget)
            assertThrowIsolation(failingTarget)
            assertSaturationIsolation(failingTarget)
        }
    }

    @Test fun `display verified requires explicit matching observation and stale remains per output`() {
        val lane = ClusterLaneAdapter(NavigationFrameDelivery { }, OutputAdapterConfig(2, 1_000), initiallyEnabled = true)
        val hud = HudAdapter(NavigationFrameDelivery { }, OutputAdapterConfig(2, 1_000), initiallyEnabled = true)
        try {
            assertEquals(OutputSubmission.ACCEPTED, lane.submit(frame(1)))
            assertEquals(OutputSubmission.ACCEPTED, hud.submit(frame(1)))
            await { lane.health().status == NavigationOutputStatus.EMITTING }
            await { hud.health().status == NavigationOutputStatus.EMITTING }
            assertFalse(lane.markDisplayVerified(2, 20))
            assertTrue(lane.markDisplayVerified(1, 20))
            assertEquals(NavigationOutputStatus.DISPLAY_VERIFIED, lane.health().status)
            assertEquals(NavigationOutputStatus.EMITTING, hud.health().status)
            hud.markStale()
            assertEquals(NavigationOutputStatus.DISPLAY_VERIFIED, lane.health().status)
            assertEquals(NavigationOutputStatus.STALE, hud.health().status)
        } finally {
            lane.close()
            hud.close()
        }
    }

    @Test fun `Navigation Stage 2 sources have no Cast import legacy wiring or adapter mutual call`() {
        val sourceRoot = SourceRoots.path("src/main/java/com/byd/clusternav/navigation")
        val files = listOf(
            "NavigationModels.kt",
            "NavigationFrameStore.kt",
            "NavigationSessionCoordinator.kt",
            "ClusterLaneAdapter.kt",
            "HudAdapter.kt"
        )
        files.forEach { name ->
            val text = sourceRoot.resolve(name).toFile().readText()
            assertFalse(text.contains("modules.clustercast"), "$name imports Cluster Cast")
            assertFalse(text.contains("NavRepository"), "$name wires the legacy repository")
            assertFalse(text.contains("ClusterBroadcaster"), "$name wires the legacy broadcaster")
        }
        val lane = sourceRoot.resolve("ClusterLaneAdapter.kt").toFile().readText()
        val hud = sourceRoot.resolve("HudAdapter.kt").toFile().readText()
        assertFalse(lane.contains("HudAdapter"), "lane must not reference or reset HUD")
        assertFalse(hud.contains("ClusterLaneAdapter"), "HUD must not reference or reset lane")
    }

    @Test fun `speed sign ports are independent from each other and canonical Amap navigation`() {
        var now = 1_000L
        val cluster = RecordingSpeedSignPort(SpeedSignOutput.CLUSTER)
        val hud = RecordingSpeedSignPort(SpeedSignOutput.HUD)
        val coordinator = SpeedSignLifecycleCoordinator(cluster, hud, { now })
        try {
            coordinator.onProcessRestart(1)
            cluster.clearHistory(); hud.clearHistory()
            coordinator.onSourceSelected(SpeedLimitSource.VIETMAP)
            coordinator.onOutputEnabled(SpeedSignOutput.CLUSTER, true)
            coordinator.onOutputEnabled(SpeedSignOutput.HUD, true)
            coordinator.onMasterEnabled(true)
            assertTrue(coordinator.onSpeedLimit(SpeedLimitSource.VIETMAP, 80, now++, 1))
            coordinator.onOutputEnabled(SpeedSignOutput.CLUSTER, false)

            assertEquals(listOf(SpeedLimitClearReason.OUTPUT_DISABLED),
                cluster.snapshot().mapNotNull { it.frame.clearReason })
            assertTrue(hud.snapshot().none { it.frame.clearReason != null })
            assertEquals(80, hud.snapshot().single().frame.value)
        } finally {
            coordinator.close()
        }

        val navigation = SourceRoots.text("src/main/java/com/byd/clusternav/ClusterBroadcaster.kt")
        val speed = SourceRoots.text("src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt")
        assertFalse(navigation.contains("NavigationSpeedSignOwner"))
        assertFalse(navigation.contains("SpeedLimitFrame"))
        assertFalse(speed.contains("ClusterBroadcaster"))
        assertFalse(speed.contains("AmapFrameBuilder"))
    }

    private fun assertBlockIsolation(failingTarget: NavigationOutputTarget) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val peerDelivered = CountDownLatch(1)
        val pair = adapters(
            failingTarget,
            failingDelivery = NavigationFrameDelivery {
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
            },
            peerDelivery = NavigationFrameDelivery { peerDelivered.countDown() },
            config = OutputAdapterConfig(2, 1_500)
        )
        try {
            assertEquals(OutputSubmission.ACCEPTED, pair.failing.submit(frame(1)))
            assertTrue(entered.await(500, TimeUnit.MILLISECONDS), "$failingTarget did not enter blocking delivery")
            assertEquals(OutputSubmission.ACCEPTED, pair.peer.submit(frame(1)))
            assertTrue(peerDelivered.await(500, TimeUnit.MILLISECONDS), "$failingTarget blocked its peer")
        } finally {
            release.countDown()
            pair.close()
        }
    }

    private fun assertThrowIsolation(failingTarget: NavigationOutputTarget) {
        val peerDelivered = CountDownLatch(1)
        val pair = adapters(
            failingTarget,
            failingDelivery = NavigationFrameDelivery { throw IllegalStateException("synthetic failure") },
            peerDelivery = NavigationFrameDelivery { peerDelivered.countDown() },
            config = OutputAdapterConfig(2, 1_000)
        )
        try {
            pair.failing.submit(frame(1))
            pair.peer.submit(frame(1))
            assertTrue(peerDelivered.await(500, TimeUnit.MILLISECONDS), "$failingTarget throw blocked its peer")
            await { pair.failing.health().status is NavigationOutputStatus.FAULT }
            val fault = pair.failing.health().status as NavigationOutputStatus.FAULT
            assertEquals(NavigationOutputFailureReason.DELIVERY_THROWN, fault.reason)
            assertEquals(NavigationOutputStatus.EMITTING, pair.peer.health().status)
        } finally {
            pair.close()
        }
    }

    private fun assertSaturationIsolation(failingTarget: NavigationOutputTarget) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val peerDelivered = CountDownLatch(1)
        val pair = adapters(
            failingTarget,
            failingDelivery = NavigationFrameDelivery {
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
            },
            peerDelivery = NavigationFrameDelivery { peerDelivered.countDown() },
            config = OutputAdapterConfig(1, 1_500)
        )
        try {
            assertEquals(OutputSubmission.ACCEPTED, pair.failing.submit(frame(1)))
            assertTrue(entered.await(500, TimeUnit.MILLISECONDS))
            assertEquals(OutputSubmission.ACCEPTED, pair.failing.submit(frame(2)))
            assertEquals(OutputSubmission.REJECTED_QUEUE_FULL, pair.failing.submit(frame(3)))
            assertEquals(OutputSubmission.ACCEPTED, pair.peer.submit(frame(1)))
            assertTrue(peerDelivered.await(500, TimeUnit.MILLISECONDS), "$failingTarget saturation blocked its peer")
            val fault = pair.failing.health().status as NavigationOutputStatus.FAULT
            assertEquals(NavigationOutputFailureReason.QUEUE_SATURATED, fault.reason)
        } finally {
            release.countDown()
            pair.close()
        }
    }

    private fun adapters(
        failingTarget: NavigationOutputTarget,
        failingDelivery: NavigationFrameDelivery,
        peerDelivery: NavigationFrameDelivery,
        config: OutputAdapterConfig
    ): AdapterPair {
        return if (failingTarget == NavigationOutputTarget.CLUSTER_LANE) {
            AdapterPair(
                ClusterLaneAdapter(failingDelivery, config, initiallyEnabled = true),
                HudAdapter(peerDelivery, config, initiallyEnabled = true)
            )
        } else {
            AdapterPair(
                HudAdapter(failingDelivery, config, initiallyEnabled = true),
                ClusterLaneAdapter(peerDelivery, config, initiallyEnabled = true)
            )
        }
    }

    private fun frame(sequence: Long) = NavigationFrame(
        "session", source, sequence, sequence,
        NavigationFrameContent(1, "Continue", 100, "Example Road", null, null, null, null)
    )

    private fun await(timeoutMs: Long = 1_000, condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!condition() && System.nanoTime() < end) Thread.sleep(5)
        assertTrue(condition(), "condition not reached within ${timeoutMs}ms")
    }

    private data class AdapterPair(
        val failing: NavigationOutputPort,
        val peer: NavigationOutputPort
    ) : AutoCloseable {
        override fun close() {
            failing.close()
            peer.close()
        }
    }
}
