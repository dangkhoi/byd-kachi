package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationSessionCoordinatorTest {
    private val source = NavigationSourceIdentity("com.example.maps", "Example Maps")
    private val content = NavigationFrameContent(2, "Turn right", 250, "Example Road", null, null, null, null)

    @Test fun `closed model fields preserve typed nullability and exact enums`() {
        val minimal = NavigationFrameContent(null, null, null, null, null, null, null, null)
        assertNull(minimal.maneuverCode)
        assertNull(minimal.roadName)
        assertThrows(IllegalArgumentException::class.java) {
            NavigationFrameContent(null, "", -1, "", null, null, null, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NavigationSourceState(
                NavigationPermission.MISSING,
                source,
                "session",
                NavigationFreshness.Unknown(NavigationSourceReason.PERMISSION_MISSING),
                null,
                NavigationSourceReason.PERMISSION_MISSING
            )
        }
        val statuses = listOf(
            NavigationOutputStatus.OFF,
            NavigationOutputStatus.STARTING,
            NavigationOutputStatus.EMITTING,
            NavigationOutputStatus.DISPLAY_VERIFIED,
            NavigationOutputStatus.STALE,
            NavigationOutputStatus.FAULT(NavigationOutputFailureReason.DELIVERY_THROWN)
        )
        assertEquals(
            listOf("off", "starting", "emitting", "verified", "stale", "fault"),
            statuses.map(::assertOutputStatusExhaustive)
        )
        val callerActions = linkedSetOf(NavigationAction.START_NAVIGATION)
        val immutableState = NavigationUiState(
            InteractionContext.UNKNOWN,
            NavigationSourceState(
                NavigationPermission.GRANTED, null, null,
                NavigationFreshness.Unknown(NavigationSourceReason.NO_ACTIVE_SESSION), null,
                NavigationSourceReason.NO_ACTIVE_SESSION
            ),
            RecordingPort(NavigationOutputTarget.CLUSTER_LANE).health,
            RecordingPort(NavigationOutputTarget.HUD).health,
            callerActions,
            emptyMap()
        )
        callerActions.clear()
        assertEquals(setOf(NavigationAction.START_NAVIGATION), immutableState.allowedActions)
        assertThrows(UnsupportedOperationException::class.java) {
            (immutableState.allowedActions as MutableSet).clear()
        }
        assertEquals(6, NavigationOutputFailureReason.entries.size)
        assertEquals(3, NavigationPermission.entries.size)
        assertEquals(10, NavigationAction.entries.size)
        assertEquals(8, NavigationSourceReason.entries.size)
        assertEquals(3, NavigationOutputTarget.entries.size)
        assertOutputStatusExhaustive(NavigationOutputStatus.FAULT(NavigationOutputFailureReason.DELIVERY_THROWN))
        assertFreshnessExhaustive(NavigationFreshness.Fresh(0))
    }

    @Test fun `coordinator alone assigns session sequence source and freshness`() {
        var now = 1_000L
        val persistence = MemoryPersistence()
        val lane = RecordingPort(NavigationOutputTarget.CLUSTER_LANE)
        val hud = RecordingPort(NavigationOutputTarget.HUD)
        val coordinator = NavigationSessionCoordinator(
            PersistentNavigationFrameStore(persistence), lane, hud,
            nowEpochMs = { now }, nextSessionId = { "nav-session-1" }, staleAfterMs = 100
        )
        coordinator.setPermission(NavigationPermission.GRANTED)
        assertEquals("nav-session-1", coordinator.startSession(source))
        val first = coordinator.acceptFrame(source, content)
        assertEquals(1, first.sequence)
        assertEquals(first, lane.frames.single())
        assertEquals(first, hud.frames.single())
        assertInstanceOf(NavigationFreshness.Fresh::class.java, coordinator.snapshot().source.freshness)

        now = 1_101L
        coordinator.refreshFreshness()
        val stale = coordinator.snapshot().source.freshness
        assertInstanceOf(NavigationFreshness.Stale::class.java, stale)
        assertEquals(NavigationOutputStatus.STALE, lane.health.status)
        assertEquals(NavigationOutputStatus.STALE, hud.health.status)
        assertThrows(IllegalArgumentException::class.java) {
            coordinator.acceptFrame(NavigationSourceIdentity("other.maps"), content)
        }
    }

    @Test fun `process rehydration restores immutable frame but emits no operation or verification`() {
        val persistence = MemoryPersistence()
        val originalLane = RecordingPort(NavigationOutputTarget.CLUSTER_LANE)
        val originalHud = RecordingPort(NavigationOutputTarget.HUD)
        val original = NavigationSessionCoordinator(
            PersistentNavigationFrameStore(persistence), originalLane, originalHud,
            nowEpochMs = { 2_000L }, nextSessionId = { "persisted-session" }
        )
        original.setPermission(NavigationPermission.GRANTED)
        original.startSession(source)
        val saved = original.acceptFrame(source, content)

        val restoredLane = RecordingPort(NavigationOutputTarget.CLUSTER_LANE)
        val restoredHud = RecordingPort(NavigationOutputTarget.HUD)
        val restored = NavigationSessionCoordinator(
            PersistentNavigationFrameStore(persistence), restoredLane, restoredHud,
            nowEpochMs = { 2_050L }
        )
        restored.setPermission(NavigationPermission.GRANTED)
        assertTrue(restored.rehydrate())
        assertTrue(restoredLane.frames.isEmpty())
        assertTrue(restoredHud.frames.isEmpty())
        val state = restored.snapshot()
        assertEquals("persisted-session", state.source.sessionId)
        assertEquals(saved.receivedAtEpochMs, state.source.lastFrameAtEpochMs)
        assertEquals(
            NavigationFreshness.Unknown(NavigationSourceReason.PROCESS_REHYDRATED_UNVERIFIED),
            state.source.freshness
        )
        assertEquals(NavigationOutputStatus.OFF, state.clusterLane.status)
        assertEquals(NavigationOutputStatus.OFF, state.hud.status)
    }

    @Test fun `per-output toggle changes one adapter while whole-session stop clears both and store`() {
        val persistence = MemoryPersistence()
        val lane = RecordingPort(NavigationOutputTarget.CLUSTER_LANE)
        val hud = RecordingPort(NavigationOutputTarget.HUD)
        val coordinator = NavigationSessionCoordinator(
            PersistentNavigationFrameStore(persistence), lane, hud,
            nowEpochMs = { 3_000L }, nextSessionId = { "toggle-session" }
        )
        coordinator.setPermission(NavigationPermission.GRANTED)
        coordinator.startSession(source)
        coordinator.acceptFrame(source, content)

        coordinator.setOutputEnabled(NavigationOutputTarget.HUD, false)
        assertFalse(hud.health.enabled)
        assertTrue(lane.health.enabled)
        assertEquals(0, lane.stopCalls)
        assertEquals(0, hud.stopCalls)

        coordinator.stopSession()
        assertEquals(1, lane.stopCalls)
        assertEquals(1, hud.stopCalls)
        assertNull(persistence.value)
        assertNull(coordinator.snapshot().source.sessionId)
    }

    @Test fun `submit and fault reporting failures remain isolated from peer`() {
        val persistence = MemoryPersistence()
        val lane = RecordingPort(NavigationOutputTarget.CLUSTER_LANE).apply {
            throwOnSubmit = true
            throwOnRecordFault = true
        }
        val hud = RecordingPort(NavigationOutputTarget.HUD)
        val coordinator = NavigationSessionCoordinator(
            PersistentNavigationFrameStore(persistence), lane, hud,
            nowEpochMs = { 4_000L }, nextSessionId = { "fault-session" }
        )
        coordinator.setPermission(NavigationPermission.GRANTED)
        coordinator.startSession(source)

        val frame = coordinator.acceptFrame(source, content)

        assertEquals(frame, hud.frames.single())
    }

    @Test fun `failed durable clear preserves in-memory session and outputs`() {
        val persistence = MemoryPersistence()
        val lane = RecordingPort(NavigationOutputTarget.CLUSTER_LANE)
        val hud = RecordingPort(NavigationOutputTarget.HUD)
        val coordinator = NavigationSessionCoordinator(
            PersistentNavigationFrameStore(persistence), lane, hud,
            nowEpochMs = { 5_000L }, nextSessionId = { "durable-session" }
        )
        coordinator.setPermission(NavigationPermission.GRANTED)
        coordinator.startSession(source)
        persistence.failClear = true

        assertThrows(IllegalStateException::class.java) { coordinator.stopSession() }
        assertEquals("durable-session", coordinator.snapshot().source.sessionId)
        assertEquals(0, lane.stopCalls)
        assertEquals(0, hud.stopCalls)
        assertTrue(persistence.value != null)
    }

    @Test fun `listener disconnect immediately marks source and outputs stale without clearing durable session`() {
        val persistence = MemoryPersistence()
        val lane = RecordingPort(NavigationOutputTarget.CLUSTER_LANE)
        val hud = RecordingPort(NavigationOutputTarget.HUD)
        val coordinator = NavigationSessionCoordinator(
            PersistentNavigationFrameStore(persistence), lane, hud,
            nowEpochMs = { 6_000L }, nextSessionId = { "disconnect-session" }, staleAfterMs = 10_000,
        )
        coordinator.setPermission(NavigationPermission.GRANTED)
        coordinator.startSession(source)
        coordinator.acceptFrame(source, content)

        coordinator.setPermission(NavigationPermission.UNKNOWN)

        val state = coordinator.snapshot()
        assertEquals(
            NavigationFreshness.Stale(0, NavigationSourceReason.SOURCE_DISCONNECTED),
            state.source.freshness,
        )
        assertEquals(NavigationSourceReason.SOURCE_DISCONNECTED, state.source.reason)
        assertEquals(NavigationOutputStatus.STALE, lane.health.status)
        assertEquals(NavigationOutputStatus.STALE, hud.health.status)
        assertEquals("disconnect-session", persistence.value?.sessionId)
    }

    private fun assertOutputStatusExhaustive(status: NavigationOutputStatus): String = when (status) {
        NavigationOutputStatus.OFF -> "off"
        NavigationOutputStatus.STARTING -> "starting"
        NavigationOutputStatus.EMITTING -> "emitting"
        NavigationOutputStatus.DISPLAY_VERIFIED -> "verified"
        NavigationOutputStatus.STALE -> "stale"
        is NavigationOutputStatus.FAULT -> "fault"
    }

    private fun assertFreshnessExhaustive(freshness: NavigationFreshness): String = when (freshness) {
        is NavigationFreshness.Unknown -> "unknown"
        is NavigationFreshness.Fresh -> "fresh"
        is NavigationFreshness.Stale -> "stale"
    }

    private class MemoryPersistence : NavigationFramePersistence {
        var value: StoredNavigationSession? = null
        var failClear = false
        override fun load() = value
        override fun save(session: StoredNavigationSession) { value = session }
        override fun clear() {
            if (failClear) throw IllegalStateException("simulated clear failure")
            value = null
        }
    }

    private class RecordingPort(override val target: NavigationOutputTarget) : NavigationOutputPort {
        val frames = mutableListOf<NavigationFrame>()
        var stopCalls = 0
        var throwOnSubmit = false
        var throwOnRecordFault = false
        var health = NavigationOutputHealth(target, true, NavigationOutputStatus.OFF, 0, null, null, null, null)

        override fun setEnabled(enabled: Boolean) {
            health = health.copy(enabled = enabled, status = if (enabled) NavigationOutputStatus.STARTING else NavigationOutputStatus.OFF)
        }
        override fun submit(frame: NavigationFrame): OutputSubmission {
            if (throwOnSubmit) throw IllegalStateException("simulated submit failure")
            frames += frame
            health = health.copy(status = NavigationOutputStatus.EMITTING, cachedSequence = frame.sequence)
            return OutputSubmission.ACCEPTED
        }
        override fun markDisplayVerified(sequence: Long, observedAtEpochMs: Long): Boolean {
            if (health.cachedSequence != sequence) return false
            health = health.copy(status = NavigationOutputStatus.DISPLAY_VERIFIED, lastVerifiedAtEpochMs = observedAtEpochMs)
            return true
        }
        override fun markStale() { health = health.copy(status = NavigationOutputStatus.STALE) }
        override fun recordFault(reason: NavigationOutputFailureReason, detail: String?) {
            if (throwOnRecordFault) throw IllegalStateException("simulated fault reporting failure")
            health = health.copy(status = NavigationOutputStatus.FAULT(reason, detail))
        }
        override fun stopSession() {
            stopCalls++
            health = health.copy(status = NavigationOutputStatus.OFF, cachedSequence = null)
        }
        override fun health() = health
        override fun close() = Unit
    }
}
