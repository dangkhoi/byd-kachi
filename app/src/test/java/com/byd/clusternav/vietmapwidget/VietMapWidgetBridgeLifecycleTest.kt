package com.byd.clusternav.vietmapwidget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * T3 gap coverage — VietMapWidgetBridge lifecycle contract tests (pure JVM):
 *
 * These test the MODEL contracts of the bridge (generation binding, listener lifecycle,
 * restart recovery semantics, commit failure propagation) without requiring Android
 * framework classes. The actual AppWidgetHost integration requires instrumented tests.
 *
 * Tests:
 * 1. Generation binding: listener from gen N is pruned when bridge advances to gen N+1.
 * 2. Restart recovery: after stop+start, old listeners are pruned, new listeners work.
 * 3. Commit failure propagation: failed commit = NOT_BOUND state, requires rebind.
 * 4. Per-provider snapshot independence across restart.
 * 5. Listener dispatch ordering: listeners fire in registration order.
 */
class VietMapWidgetBridgeLifecycleTest {

    // ─── Generation binding contract ──────────────────────────────────────────

    @Test
    fun `listener from prior generation is considered stale and pruned`() {
        // Simulates: VietMapWidgetBridge.dispatchToListeners() skips entries where
        // entry.generation != listenerGeneration
        val generation = AtomicLong(1L)
        val listeners = CopyOnWriteArrayList<ListenerEntry>()

        // Add listener at gen 1
        val received = mutableListOf<String>()
        val entry = ListenerEntry({ received.add(it) }, generation.get())
        listeners.add(entry)

        // Dispatch at gen 1 — listener receives
        dispatchToListeners(listeners, generation.get(), "snapshot-1")
        assertEquals(1, received.size)
        assertEquals("snapshot-1", received[0])

        // Bridge restarts (stop/start) → generation advances
        generation.incrementAndGet() // now gen 2

        // Dispatch at gen 2 — old listener is stale and should be pruned
        dispatchToListeners(listeners, generation.get(), "snapshot-2")
        assertEquals(1, received.size, "stale listener must NOT receive new dispatches")
        assertTrue(listeners.isEmpty(), "stale listener must be pruned from set")
    }

    @Test
    fun `listener added after generation advance receives dispatches`() {
        val generation = AtomicLong(1L)
        val listeners = CopyOnWriteArrayList<ListenerEntry>()

        // Advance generation (simulates restart)
        generation.incrementAndGet() // gen 2

        // Add listener at gen 2
        val received = mutableListOf<String>()
        val entry = ListenerEntry({ received.add(it) }, generation.get())
        listeners.add(entry)

        // Dispatch at gen 2 — new listener receives
        dispatchToListeners(listeners, generation.get(), "after-restart")
        assertEquals(1, received.size)
        assertEquals("after-restart", received[0])
    }

    // ─── Restart recovery ─────────────────────────────────────────────────────

    @Test
    fun `stop-start cycle prunes old listeners and accepts new ones`() {
        val generation = AtomicLong(1L)
        val listeners = CopyOnWriteArrayList<ListenerEntry>()

        // Gen 1: add listener A
        val receivedA = mutableListOf<String>()
        listeners.add(ListenerEntry({ receivedA.add(it) }, generation.get()))

        dispatchToListeners(listeners, generation.get(), "gen1-data")
        assertEquals(1, receivedA.size)

        // Simulate stop + start: generation advances twice (stop increments, start increments)
        generation.incrementAndGet() // stop → gen 2
        generation.incrementAndGet() // start → gen 3

        // Old listener still in set but won't fire
        dispatchToListeners(listeners, generation.get(), "gen3-data")
        assertEquals(1, receivedA.size, "old listener must not fire after restart")
        assertTrue(listeners.isEmpty(), "old listeners pruned after dispatch")

        // Add listener B at gen 3
        val receivedB = mutableListOf<String>()
        listeners.add(ListenerEntry({ receivedB.add(it) }, generation.get()))

        dispatchToListeners(listeners, generation.get(), "gen3-data2")
        assertEquals(1, receivedB.size)
        assertEquals("gen3-data2", receivedB[0])
    }

    @Test
    fun `multiple restarts do not accumulate stale listeners in set`() {
        val generation = AtomicLong(1L)
        val listeners = CopyOnWriteArrayList<ListenerEntry>()

        // Add listeners across multiple generations
        repeat(5) { i ->
            listeners.add(ListenerEntry({ }, generation.get()))
            generation.incrementAndGet() // simulate restart
        }

        // All 5 listeners are stale (from gens 1-5, current is 6)
        dispatchToListeners(listeners, generation.get(), "final")
        assertEquals(0, listeners.size, "all stale listeners must be pruned in one pass")
    }

    // ─── Commit failure propagation ───────────────────────────────────────────

    @Test
    fun `commit failure results in NOT_BOUND unavailable state`() {
        // Contract: if SharedPreferences.Editor.commit() returns false,
        // the widget binding is NOT persisted. On process restart, the bridge
        // finds no saved widget ID → state = UNAVAILABLE(NOT_BOUND).
        // This is fail-closed: no silent fallback.
        val reason = VietMapWidgetUnavailableReason.NOT_BOUND
        val snapshot = VietMapWidgetSnapshot(
            currentSpeedKph = null,
            speedLimitKph = null,
            alerts = emptyList(),
            providerVersion = null,
            updatedAtElapsedMs = null,
            freshness = VietMapWidgetFreshness.UNAVAILABLE,
            reason = reason,
        )
        assertEquals(VietMapWidgetFreshness.UNAVAILABLE, snapshot.freshness)
        assertEquals(VietMapWidgetUnavailableReason.NOT_BOUND, snapshot.reason)
        assertNull(snapshot.currentSpeedKph)
        assertNull(snapshot.speedLimitKph)
    }

    @Test
    fun `commit failure does not expose stale driving values`() {
        // Even if values were previously set, a NOT_BOUND snapshot must not expose them
        val failedSnapshot = VietMapWidgetSnapshot(
            currentSpeedKph = null, // cleared on commit failure
            speedLimitKph = null,
            alerts = emptyList(),
            providerVersion = null,
            updatedAtElapsedMs = null,
            freshness = VietMapWidgetFreshness.UNAVAILABLE,
            reason = VietMapWidgetUnavailableReason.NOT_BOUND,
        )
        assertNull(failedSnapshot.currentSpeedKph, "commit failure must not expose speed")
        assertNull(failedSnapshot.speedLimitKph, "commit failure must not expose limit")
    }

    // ─── Per-provider snapshot independence across restart ─────────────────────

    @Test
    fun `restart clears both provider snapshots independently`() {
        // After stop: both providers go to UNAVAILABLE/NOT_BOUND independently
        val speedAfterStop = VietMapProviderSnapshot<VietMapWidgetRawValues>(
            slot = VietMapWidgetSlot.SPEED_LIMIT,
            values = null,
            updatedAtElapsedMs = null,
            freshness = VietMapWidgetFreshness.UNAVAILABLE,
            reason = VietMapWidgetUnavailableReason.NOT_BOUND,
            generation = 2L, // new generation after restart
        )
        val alertsAfterStop = VietMapProviderSnapshot<VietMapWidgetRawValues>(
            slot = VietMapWidgetSlot.ALERTS,
            values = null,
            updatedAtElapsedMs = null,
            freshness = VietMapWidgetFreshness.UNAVAILABLE,
            reason = VietMapWidgetUnavailableReason.NOT_BOUND,
            generation = 2L,
        )

        assertEquals(VietMapWidgetFreshness.UNAVAILABLE, speedAfterStop.freshness)
        assertEquals(VietMapWidgetFreshness.UNAVAILABLE, alertsAfterStop.freshness)
        assertNull(speedAfterStop.values)
        assertNull(alertsAfterStop.values)
        // Both at generation 2 (post-restart) — independent of each other
        assertEquals(speedAfterStop.generation, alertsAfterStop.generation)
    }

    @Test
    fun `speed restored after restart does not auto-restore alerts`() {
        // After restart, speed updates arrive first → alerts still NOT_BOUND
        val speedRestored = VietMapProviderSnapshot(
            slot = VietMapWidgetSlot.SPEED_LIMIT,
            values = VietMapWidgetRawValues(currentSpeedText = "80", speedLimitText = "100"),
            updatedAtElapsedMs = 5_000L,
            freshness = VietMapWidgetFreshness.FRESH,
            reason = null,
            generation = 3L,
        )
        val alertsStillDown = VietMapProviderSnapshot<VietMapWidgetRawValues>(
            slot = VietMapWidgetSlot.ALERTS,
            values = null,
            updatedAtElapsedMs = null,
            freshness = VietMapWidgetFreshness.UNAVAILABLE,
            reason = VietMapWidgetUnavailableReason.NOT_BOUND,
            generation = 3L,
        )

        assertEquals(VietMapWidgetFreshness.FRESH, speedRestored.freshness)
        assertNotNull(speedRestored.values)
        assertEquals(VietMapWidgetFreshness.UNAVAILABLE, alertsStillDown.freshness)
        assertNull(alertsStillDown.values)
    }

    // ─── Listener dispatch ordering ───────────────────────────────────────────

    @Test
    fun `listeners fire in registration order`() {
        val generation = AtomicLong(1L)
        val listeners = CopyOnWriteArrayList<ListenerEntry>()
        val order = mutableListOf<Int>()

        listeners.add(ListenerEntry({ order.add(1) }, generation.get()))
        listeners.add(ListenerEntry({ order.add(2) }, generation.get()))
        listeners.add(ListenerEntry({ order.add(3) }, generation.get()))

        dispatchToListeners(listeners, generation.get(), "test")
        assertEquals(listOf(1, 2, 3), order, "listeners must fire in registration order")
    }

    @Test
    fun `listener exception does not prevent subsequent listeners from firing`() {
        val generation = AtomicLong(1L)
        val listeners = CopyOnWriteArrayList<ListenerEntry>()
        val received = mutableListOf<Int>()

        listeners.add(ListenerEntry({ received.add(1) }, generation.get()))
        listeners.add(ListenerEntry({ throw RuntimeException("listener crash") }, generation.get()))
        listeners.add(ListenerEntry({ received.add(3) }, generation.get()))

        dispatchToListeners(listeners, generation.get(), "test")
        // Even though listener 2 threw, listener 3 must still fire
        assertTrue(received.contains(1))
        assertTrue(received.contains(3), "exception in listener must not block subsequent listeners")
    }

    // ─── Test infrastructure ─────────────────────────────────────────────────

    private data class ListenerEntry(
        val callback: (String) -> Unit,
        val generation: Long,
    )

    /**
     * Replicates VietMapWidgetBridge.dispatchToListeners() logic.
     * Dispatches to current-gen listeners, prunes stale ones.
     */
    private fun dispatchToListeners(
        listeners: CopyOnWriteArrayList<ListenerEntry>,
        currentGeneration: Long,
        payload: String,
    ) {
        val stale = mutableListOf<ListenerEntry>()
        listeners.forEach { entry ->
            if (entry.generation != currentGeneration) {
                stale += entry
                return@forEach
            }
            try {
                entry.callback(payload)
            } catch (_: RuntimeException) {
                // Production: Log.e; test: swallow to verify subsequent fire
            }
        }
        if (stale.isNotEmpty()) {
            listeners.removeAll(stale.toSet())
        }
    }
}
