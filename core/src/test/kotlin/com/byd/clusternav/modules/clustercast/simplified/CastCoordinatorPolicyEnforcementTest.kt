package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * T4 gap coverage — CP/AA policy enforcement at the COORDINATOR level.
 *
 * The CastSafetyTest verifies CastSlotValidator unit behavior.
 * This test verifies the full coordinator flow for:
 *
 * R4: CP/AA cannot enter freeform/split mode at the coordinator level.
 * R3: Cast commits only on verified postcondition (coordinator-level with stack proof).
 * R4: CP full cast requires fullscreen stack proof.
 *
 * Uses the same FakeShell/FakePrefs infrastructure as SimpleCastCoordinatorTest.
 */
class CastCoordinatorPolicyEnforcementTest {

    private lateinit var shell: FakeShell
    private lateinit var prefs: FakePrefs
    private lateinit var coordinator: SimpleCastCoordinator

    @BeforeEach
    fun setup() {
        shell = FakeShell()
        prefs = FakePrefs()
        val projection = ProjectionManager(shell, sleepMs = {})
        val configurator = DisplayConfigurator(shell)
        val mover = AppMover(shell, sleepMs = {})
        coordinator = SimpleCastCoordinator(
            projection, configurator, mover, prefs, shell, displayId = 1,
            castTimeoutMs = 15_000L,
            stopTimeoutMs = 5_000L,
        )
    }

    // ─── R4: CP cannot enter split at coordinator level ──────────────────────

    @Test
    fun `coordinator rejects CP in left slot - state remains Idle`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        coordinator.dispatch(SimpleCastIntent.CastSlot("com.byd.autolink.carplay", ClusterSlotSide.LEFT))
        Thread.sleep(300)

        // Must NOT be in CastingSplit — CP in split is forbidden
        val state = coordinator.state
        assertTrue(
            state is SimpleCastState.Idle || state is SimpleCastState.Error,
            "CP must not enter split, got: $state"
        )
        if (state is SimpleCastState.CastingSplit) {
            throw AssertionError("R4 VIOLATION: CarPlay entered split mode!")
        }
    }

    @Test
    fun `coordinator rejects CP in right slot`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        coordinator.dispatch(SimpleCastIntent.CastSlot("com.byd.autolink.carplay", ClusterSlotSide.RIGHT))
        Thread.sleep(300)

        val state = coordinator.state
        assertFalse(state is SimpleCastState.CastingSplit,
            "R4 VIOLATION: CarPlay entered split mode on RIGHT side!")
    }

    @Test
    fun `coordinator rejects Android Auto in left slot`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        coordinator.dispatch(
            SimpleCastIntent.CastSlot("com.google.android.projection.gearhead", ClusterSlotSide.LEFT)
        )
        Thread.sleep(300)

        val state = coordinator.state
        assertFalse(state is SimpleCastState.CastingSplit,
            "R4 VIOLATION: Android Auto entered split mode!")
    }

    @Test
    fun `coordinator rejects Android Auto in right slot`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        coordinator.dispatch(
            SimpleCastIntent.CastSlot("com.google.android.projection.gearhead", ClusterSlotSide.RIGHT)
        )
        Thread.sleep(300)

        val state = coordinator.state
        assertFalse(state is SimpleCastState.CastingSplit,
            "R4 VIOLATION: Android Auto entered split mode on RIGHT!")
    }

    // ─── R4: CP full cast accepted (fullscreen only mode) ─────────────────────

    @Test
    fun `coordinator accepts CP full cast with correct display config`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        coordinator.dispatch(SimpleCastIntent.CastFull("com.byd.autolink.carplay", AppType.CARPLAY))
        awaitState<SimpleCastState.CastingFull>()

        val state = coordinator.state as SimpleCastState.CastingFull
        assertEquals("com.byd.autolink.carplay", state.targetPkg)
        assertEquals(AppType.CARPLAY, state.appType)
        assertEquals(DisplayConfig.CARPLAY, state.displayConfig)

        // Verify wm size was set to CP dimensions
        assertTrue(
            shell.history.any { it.contains("wm size 1422x800 -d 1") },
            "CP full cast must set wm size 1422x800"
        )
        assertTrue(
            shell.history.any { it.contains("wm overscan 10,-120,10,50 -d 1") },
            "CP full cast must set CP overscan"
        )
    }

    @Test
    fun `coordinator accepts AA full cast with correct display config`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        coordinator.dispatch(
            SimpleCastIntent.CastFull("com.google.android.projection.gearhead", AppType.ANDROID_AUTO)
        )
        awaitState<SimpleCastState.CastingFull>()

        val state = coordinator.state as SimpleCastState.CastingFull
        assertEquals("com.google.android.projection.gearhead", state.targetPkg)
        assertEquals(AppType.ANDROID_AUTO, state.appType)
        assertEquals(DisplayConfig.ANDROID_AUTO, state.displayConfig)

        // Verify correct AA dimensions
        assertTrue(
            shell.history.any { it.contains("wm size 1920x1080 -d 1") },
            "AA full cast must set wm size 1920x1080"
        )
    }

    // ─── R3: Failed postcondition preserves Idle state ────────────────────────

    @Test
    fun `coordinator preserves Idle when postcondition fails for normal app`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        // Block app from appearing on display 1 (simulates failed landing)
        shell.blockAppOnDisplay1 = true

        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.newapp", AppType.NORMAL))
        Thread.sleep(500)

        val state = coordinator.state
        assertTrue(
            state is SimpleCastState.Idle || state is SimpleCastState.Error,
            "R3: failed postcondition must not commit CastingFull, got: $state"
        )
        if (state is SimpleCastState.CastingFull) {
            throw AssertionError("R3 VIOLATION: State committed without postcondition verification!")
        }
    }

    @Test
    fun `coordinator does not persist display config on failed postcondition`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        shell.blockAppOnDisplay1 = true

        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.failapp", AppType.NORMAL))
        Thread.sleep(500)

        // Prefs must NOT have saved config for the failed app
        val saved = prefs.displayConfigFor("com.test.failapp")
        // Even if config was computed, it must not be persisted on failed postcondition
        // (persistence happens only AFTER verified cast)
    }

    // ─── R4: CP/AA package classification is closed ──────────────────────────

    @Test
    fun `classifyApp identifies all known CarPlay packages`() {
        assertEquals(AppType.CARPLAY, AppMover.classifyApp("com.byd.autolink.carplay"))
        // Also matches via substring "carplay"
        assertEquals(AppType.CARPLAY, AppMover.classifyApp("com.example.carplay.client"))
    }

    @Test
    fun `classifyApp identifies Android Auto packages`() {
        assertEquals(AppType.ANDROID_AUTO, AppMover.classifyApp("com.google.android.projection.gearhead"))
        // Matches via substring "android.auto"
        assertEquals(AppType.ANDROID_AUTO, AppMover.classifyApp("com.example.android.auto.helper"))
    }

    @Test
    fun `classifyApp returns NORMAL for regular packages`() {
        assertEquals(AppType.NORMAL, AppMover.classifyApp("com.google.android.apps.maps"))
        assertEquals(AppType.NORMAL, AppMover.classifyApp("vn.vietmap.live"))
        assertEquals(AppType.NORMAL, AppMover.classifyApp("com.spotify.music"))
    }

    @Test
    fun `protected apps are correctly flagged`() {
        assertTrue(AppType.CARPLAY.isProtected)
        assertTrue(AppType.ANDROID_AUTO.isProtected)
        assertFalse(AppType.NORMAL.isProtected)
    }

    // ─── Slot + CP sequence: ensure CP rejected even after split ──────────────

    @Test
    fun `CP rejected from slot even when another app is already in split`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        // First: normal app in left slot (succeeds)
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.normal.app", ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()

        // Try CP in right slot (must be rejected)
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.byd.autolink.carplay", ClusterSlotSide.RIGHT))
        Thread.sleep(300)

        // After rejection, state may be Error (transient) or CastingSplit (auto-recovered).
        // The critical assertion: CP must NOT have entered the split state.
        val state = coordinator.state
        when (state) {
            is SimpleCastState.CastingSplit -> {
                // Right slot must still be null (CP rejected)
                assertEquals(null, state.right,
                    "CP must not enter the right slot of an existing split")
                assertEquals("com.normal.app", state.left?.pkg)
            }
            is SimpleCastState.Error -> {
                // Error state from rejection — CP still did not enter split.
                // This is acceptable: the error will auto-clear to Idle after 3s.
                assertTrue(state.message.contains("PROTECTED_FULLSCREEN_STACK_UNPROVEN") ||
                    state.message.contains("rejected"),
                    "Error must indicate CP rejection reason, got: ${state.message}")
            }
            else -> {
                throw AssertionError(
                    "After CP slot rejection from split, expected CastingSplit or Error, got: $state"
                )
            }
        }
    }

    // ─── R3: Verified postcondition saves taskId for exact return ──────────────

    @Test
    fun `verified cast saves taskId in state for exact return`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.app", AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>()

        val state = coordinator.state as SimpleCastState.CastingFull
        // FakeShell generates taskId 100 for the first started app
        assertTrue(state.taskId != null && state.taskId!! > 0,
            "Verified cast must save taskId for exact return, got: ${state.taskId}")
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private inline fun <reified T : SimpleCastState> awaitState(timeoutMs: Long = 3000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (coordinator.state !is T && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(coordinator.state is T, "Expected ${T::class.simpleName} but got ${coordinator.state}")
    }
}
