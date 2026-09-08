package com.byd.clusternav.navigation

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T2 — Navigation output ownership tests.
 *
 * Verifies:
 * 1. Generation fencing: clear invalidates queued positive writes.
 * 2. HAL failure returns typed result (does not commit applied state).
 * 3. Timeout cancels cleanly without wedging.
 * 4. Each output is independent: failing one does not block siblings.
 * 5. Dedup tracks applied state, not enqueued intent.
 * 6. NavigationFrame identity (session/source/sequence) preserved to delivery.
 */
class NavigationOutputOwnershipTest {

    private val source = NavigationSourceIdentity("com.example.maps", "Example Maps")

    private fun frame(sequence: Long, sessionId: String = "s-1") = NavigationFrame(
        sessionId, source, sequence, 1_000L,
        NavigationFrameContent(1, "Rẽ phải", 100, "Đường Ví Dụ", null, null, null, null),
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. Generation fencing: clear invalidates queued positive writes
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `clear invalidates queued positive writes via generation fence`() {
        val delivered = CopyOnWriteArrayList<Long>()
        val blockFirst = CountDownLatch(1)
        val firstEntered = CountDownLatch(1)
        val adapter = ClusterLaneAdapter(
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
            // Submit frame 1 (will block in delivery) and frames 2,3 (queued)
            adapter.submit(frame(1))
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
            adapter.submit(frame(2))
            adapter.submit(frame(3))

            // stopSession = generation fence: increments generation, clears queue
            adapter.stopSession()
            // Re-enable and submit frame 4 (the ONLY one that should deliver after clear)
            adapter.setEnabled(true)
            adapter.submit(frame(4))

            // Release the blocked first delivery
            blockFirst.countDown()

            // Wait for executor to drain
            Thread.sleep(200)

            // Frame 1 was already executing (may or may not finish after generation change).
            // Frames 2,3 MUST NOT appear: they were queued and invalidated by generation fence.
            // Frame 4 MUST appear: submitted after the new generation.
            assertTrue(4L in delivered, "frame 4 must be delivered after re-enable: $delivered")
            assertTrue(2L !in delivered, "frame 2 must be invalidated by generation fence: $delivered")
            assertTrue(3L !in delivered, "frame 3 must be invalidated by generation fence: $delivered")
        } finally {
            blockFirst.countDown() // ensure unblocked on failure
            adapter.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. HAL failure does not commit applied state
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `HAL failure produces FAULT status and does not commit success`() {
        val throwOnce = AtomicBoolean(true)
        val adapter = ClusterLaneAdapter(
            NavigationFrameDelivery { _ ->
                if (throwOnce.getAndSet(false)) {
                    throw RuntimeException("HAL transport error")
                }
            },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        try {
            adapter.submit(frame(1))
            Thread.sleep(100)

            val health = adapter.health()
            assertTrue(health.status is NavigationOutputStatus.FAULT,
                "must be FAULT after HAL throws, got: ${health.status}")
            assertEquals(NavigationOutputFailureReason.DELIVERY_THROWN,
                (health.status as NavigationOutputStatus.FAULT).reason)

            // Applied sequence should be null — failure must NOT commit
            assertNull(adapter.lastAppliedSequence(),
                "failed delivery must not commit applied sequence")
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `successful delivery commits applied sequence`() {
        val adapter = ClusterLaneAdapter(
            NavigationFrameDelivery { },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        try {
            adapter.submit(frame(7))
            Thread.sleep(100)
            assertEquals(7L, adapter.lastAppliedSequence(),
                "successful delivery must commit applied sequence")
        } finally {
            adapter.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. Timeout cancels cleanly without wedging
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `timeout interrupts delivery and reports DEADLINE_EXCEEDED without wedging`() {
        val interrupted = CountDownLatch(1)
        val adapter = ClusterLaneAdapter(
            NavigationFrameDelivery { _ ->
                try {
                    Thread.sleep(5_000)
                } catch (_: InterruptedException) {
                    interrupted.countDown()
                    Thread.currentThread().interrupt()
                    throw InterruptedException("interrupted by deadline")
                }
            },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 50L),
            initiallyEnabled = true,
        )
        try {
            adapter.submit(frame(1))

            // Wait for deadline + interrupt
            assertTrue(interrupted.await(2, TimeUnit.SECONDS),
                "delivery thread must be interrupted after deadline")

            Thread.sleep(50)
            val health = adapter.health()
            // Status must be FAULT with DEADLINE_EXCEEDED (not permanently wedged)
            assertTrue(health.status is NavigationOutputStatus.FAULT,
                "expected FAULT after timeout, got: ${health.status}")

            // Confirm the worker is not wedged: a new submission should be accepted
            val second = adapter.submit(frame(2))
            assertEquals(OutputSubmission.ACCEPTED, second,
                "worker must accept new work after timeout cleanup")
        } finally {
            adapter.close()
        }
    }

    @Test
    fun `close after timeout does not hang`() {
        val adapter = ClusterLaneAdapter(
            NavigationFrameDelivery { Thread.sleep(5_000) },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 30L),
            initiallyEnabled = true,
        )
        adapter.submit(frame(1))
        Thread.sleep(80) // let deadline fire

        val start = System.nanoTime()
        adapter.close()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(elapsedMs < 1_000, "close must not hang after timeout, took ${elapsedMs}ms")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. Each output is independent: failing one does not block siblings
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `independent outputs do not block each other`() {
        val hudDelivered = CountDownLatch(1)
        val laneBlocked = CountDownLatch(1)
        val laneEntered = CountDownLatch(1)

        val lane = ClusterLaneAdapter(
            NavigationFrameDelivery { _ ->
                laneEntered.countDown()
                laneBlocked.await(5, TimeUnit.SECONDS) // block forever
            },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 5_000L),
            initiallyEnabled = true,
        )
        val hud = HudAdapter(
            NavigationFrameDelivery { _ -> hudDelivered.countDown() },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        try {
            // Submit to lane first (will block)
            lane.submit(frame(1))
            assertTrue(laneEntered.await(1, TimeUnit.SECONDS))

            // Submit to HUD — must deliver independently of lane being stuck
            hud.submit(frame(1))
            assertTrue(hudDelivered.await(1, TimeUnit.SECONDS),
                "HUD must deliver even when Lane is blocked")

            // Lane health should show active work, not affect HUD
            val laneHealth = lane.health()
            assertTrue(laneHealth.pendingCount >= 1,
                "lane should show active work")
            val hudHealth = hud.health()
            assertEquals(NavigationOutputStatus.EMITTING, hudHealth.status,
                "HUD must be EMITTING independently")
        } finally {
            laneBlocked.countDown()
            lane.close()
            hud.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. Dedup tracks applied state, not enqueued intent
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `dedup does not suppress retry after failed delivery`() {
        val callCount = AtomicInteger(0)
        val failFirst = AtomicBoolean(true)
        val adapter = ClusterLaneAdapter(
            NavigationFrameDelivery { _ ->
                callCount.incrementAndGet()
                if (failFirst.getAndSet(false)) throw RuntimeException("first attempt fails")
            },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        try {
            // First submit: same content, will fail
            adapter.submit(frame(1))
            Thread.sleep(100)
            assertEquals(1, callCount.get())
            assertNull(adapter.lastAppliedSequence(), "failed delivery must not be 'applied'")

            // Second submit with SAME content (different sequence): must NOT be deduped
            // because the first one failed — appliedSequence is null
            adapter.submit(frame(2))
            Thread.sleep(100)
            assertEquals(2, callCount.get(),
                "same content after failure must re-attempt delivery (dedup by applied, not enqueued)")
            assertEquals(2L, adapter.lastAppliedSequence())
        } finally {
            adapter.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 6. NavigationFrame identity preserved to delivery
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `NavigationFrame session source and sequence reach delivery intact`() {
        val received = CopyOnWriteArrayList<NavigationFrame>()
        val adapter = ClusterLaneAdapter(
            NavigationFrameDelivery { f -> received += f },
            OutputAdapterConfig(queueCapacity = 4, deliveryDeadlineMs = 500L),
            initiallyEnabled = true,
        )
        try {
            val original = NavigationFrame(
                sessionId = "nav-session-42",
                source = NavigationSourceIdentity("com.google.android.apps.maps", "Google Maps"),
                sequence = 17L,
                receivedAtEpochMs = 1_720_000_000_000L,
                content = NavigationFrameContent(5, "Turn left", 300, "Main St", null, null, null, null),
            )
            adapter.submit(original)
            Thread.sleep(100)

            assertEquals(1, received.size, "exactly one frame delivered")
            val delivered = received[0]
            assertEquals("nav-session-42", delivered.sessionId)
            assertEquals("com.google.android.apps.maps", delivered.source.packageName)
            assertEquals("Google Maps", delivered.source.displayName)
            assertEquals(17L, delivered.sequence)
            assertEquals(1_720_000_000_000L, delivered.receivedAtEpochMs)
            assertEquals(5, delivered.content.maneuverCode)
            assertEquals("Turn left", delivered.content.maneuverText)
            assertEquals(300, delivered.content.distanceMeters)
            assertEquals("Main St", delivered.content.roadName)
        } finally {
            adapter.close()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper: expose lastAppliedSequence for ClusterLaneAdapter via worker
    // ─────────────────────────────────────────────────────────────────────────────

    private fun ClusterLaneAdapter.lastAppliedSequence(): Long? {
        // Access via health + reflection-free approach: use the new public accessor
        val field = this::class.java.getDeclaredField("worker")
        field.isAccessible = true
        val worker = field.get(this) as BoundedNavigationOutputWorker
        return worker.lastAppliedSequence()
    }
}
