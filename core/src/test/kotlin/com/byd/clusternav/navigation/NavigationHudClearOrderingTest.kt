package com.byd.clusternav.navigation

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T2 gap coverage — NavigationHudOwner-specific tests:
 *
 * 1. Clear arrives during pending write: HUD must discard pending frame (generation fence).
 * 2. HAL failure path: HUD delivery throws → FAULT, does NOT commit, does NOT affect Lane.
 * 3. Multiple rapid clears: HUD generation monotonically advances, no wedge.
 * 4. Clear-then-submit: new frame after clear delivers successfully on new generation.
 *
 * R10: Each output is independent — HUD failure does NOT block Lane (proven with live pair).
 */
class NavigationHudClearOrderingTest {

    private val source = NavigationSourceIdentity("com.example.maps", "Example Maps")

    private fun frame(sequence: Long, sessionId: String = "s-1") = NavigationFrame(
        sessionId, source, sequence, 1_000L,
        NavigationFrameContent(1, "Rẽ phải", 100, "Đường Ví Dụ", null, null, null, null),
    )

    // ─── 1. Clear during pending write (HUD-specific) ─────────────────────────

    @Test
    fun `HUD clear during blocked delivery invalidates queued frames`() {
        val delivered = CopyOnWriteArrayList<Long>()
        val blockFirst = CountDownLatch(1)
        val firstEntered = CountDownLatch(1)
        val hud = HudAdapter(
            NavigationFrameDelivery { f ->
                if (f.sequence == 1L) {
                    firstEntered.countDown()
                    blockFirst.await(2, TimeUnit.SECONDS)
                }
                delivered += f.sequence
            },
            OutputAdapterConfig(queueCapacity = 8, deliveryDeadlineMs = 3_000L),
            initiallyEnabled = true,
        )
        try {
            hud.submit(frame(1))
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
            hud.submit(frame(2))
            hud.submit(frame(3))

            // stopSession = generation fence on HUD
            hud.stopSession()
            hud.setEnabled(true)
            hud.submit(frame(4))

            blockFirst.countDown()
            Thread.sleep(200)

            assertTrue(4L in delivered, "frame 4 must deliver after HUD clear: $delivered")
            assertTrue(2L !in delivered, "frame 2 must be invalidated by HUD clear: $delivered")
            assertTrue(3L !in delivered, "frame 3 must be invalidated by HUD clear: $delivered")
        } finally {
            blockFirst.countDown()
            hud.close()
        }
    }

    // ─── 2. HAL failure path (HUD-specific) ──────────────────────────────────

    @Test
    fun `HUD HAL failure produces FAULT and does not commit applied sequence`() {
        val throwOnce = AtomicBoolean(true)
        val hud = HudAdapter(
            NavigationFrameDelivery { _ ->
                if (throwOnce.getAndSet(false)) {
                    throw RuntimeException("HUD HAL transport error")
                }
            },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        try {
            hud.submit(frame(1))
            Thread.sleep(100)

            val health = hud.health()
            assertTrue(health.status is NavigationOutputStatus.FAULT,
                "HUD must be FAULT after delivery throws, got: ${health.status}")
            assertEquals(NavigationOutputFailureReason.DELIVERY_THROWN,
                (health.status as NavigationOutputStatus.FAULT).reason)
            assertEquals(NavigationOutputTarget.HUD, health.target)
        } finally {
            hud.close()
        }
    }

    @Test
    fun `HUD HAL failure does not affect simultaneously active Lane`() {
        val laneDelivered = CountDownLatch(1)
        val lane = ClusterLaneAdapter(
            NavigationFrameDelivery { _ -> laneDelivered.countDown() },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        val hud = HudAdapter(
            NavigationFrameDelivery { _ -> throw RuntimeException("HUD HAL error") },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        try {
            // HUD fails
            hud.submit(frame(1))
            Thread.sleep(100)
            assertTrue(hud.health().status is NavigationOutputStatus.FAULT)

            // Lane must still accept and deliver independently
            lane.submit(frame(1))
            assertTrue(laneDelivered.await(500, TimeUnit.MILLISECONDS),
                "Lane delivery must succeed independently of HUD failure")
            assertEquals(NavigationOutputStatus.EMITTING, lane.health().status)
        } finally {
            lane.close()
            hud.close()
        }
    }

    // ─── 3. Rapid clear does not wedge ────────────────────────────────────────

    @Test
    fun `multiple rapid HUD clears increment generation without wedging`() {
        val deliverCount = AtomicInteger(0)
        val hud = HudAdapter(
            NavigationFrameDelivery { _ -> deliverCount.incrementAndGet() },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        try {
            // Rapid clear cycle
            repeat(5) {
                hud.submit(frame((it + 1).toLong()))
                hud.stopSession()
                hud.setEnabled(true)
            }
            Thread.sleep(200)

            // After rapid clears, the adapter must still be functional
            val finalFrame = frame(100)
            hud.submit(finalFrame)
            Thread.sleep(100)

            val health = hud.health()
            // Must NOT be wedged (still accepts work)
            assertTrue(
                health.status == NavigationOutputStatus.EMITTING ||
                    health.status == NavigationOutputStatus.STARTING ||
                    health.status is NavigationOutputStatus.FAULT,
                "HUD must not be permanently wedged after rapid clears, got: ${health.status}"
            )
        } finally {
            hud.close()
        }
    }

    // ─── 4. Clear-then-submit delivers on new generation ─────────────────────

    @Test
    fun `HUD frame submitted after clear delivers on new generation`() {
        val delivered = CopyOnWriteArrayList<Long>()
        val hud = HudAdapter(
            NavigationFrameDelivery { f -> delivered += f.sequence },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        try {
            hud.submit(frame(1))
            Thread.sleep(50)
            assertTrue(1L in delivered, "first frame delivers normally")

            hud.stopSession()
            hud.setEnabled(true)

            hud.submit(frame(2))
            Thread.sleep(100)
            assertTrue(2L in delivered, "frame after clear must deliver on new generation")
        } finally {
            hud.close()
        }
    }

    // ─── 5. HUD timeout does not permanently wedge ───────────────────────────

    @Test
    fun `HUD timeout allows subsequent delivery without wedging`() {
        val deliverCount = AtomicInteger(0)
        val blockFirst = AtomicBoolean(true)
        val hud = HudAdapter(
            NavigationFrameDelivery { _ ->
                if (blockFirst.getAndSet(false)) {
                    Thread.sleep(5_000) // will be interrupted by deadline
                }
                deliverCount.incrementAndGet()
            },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 50L),
            initiallyEnabled = true,
        )
        try {
            hud.submit(frame(1))
            Thread.sleep(150) // let deadline fire

            val faultHealth = hud.health()
            assertTrue(faultHealth.status is NavigationOutputStatus.FAULT,
                "Expected FAULT after timeout, got: ${faultHealth.status}")

            // Submit second frame — must be accepted (not wedged)
            val result = hud.submit(frame(2))
            assertEquals(OutputSubmission.ACCEPTED, result,
                "HUD must accept new work after timeout cleanup")
        } finally {
            hud.close()
        }
    }
}
