package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for Cast core safety (T4 remediation):
 * - R4: CP/AA rejects freeform/split
 * - R3/R6: Postcondition verification gates state commits
 * - R5: Bounded executor, stop preempts, timeout preserves state
 * - Parser: ambiguous task → reject
 * - Slot occupancy: occupied slot → reject
 * - Failed resize → density NOT persisted
 */
class CastSafetyTest {

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

    // ─── R4: CP/AA rejects freeform/split ─────────────────────────────────────

    @Test
    fun `CP package rejected from CastSlot`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.byd.autolink.carplay", ClusterSlotSide.LEFT))
        // Should stay Idle (rejected) or transition to Error
        Thread.sleep(200)
        val s = coordinator.state
        assertTrue(
            s is SimpleCastState.Idle || s is SimpleCastState.Error,
            "CP in slot should be rejected, got $s"
        )
        // Verify no am start was issued for carplay on split
        assertFalse(
            shell.history.any { it.contains("am start") && it.contains("carplay") && it.contains("--display 1") },
            "No am start should be issued for CP in slot mode"
        )
    }

    @Test
    fun `Android Auto package rejected from CastSlot`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(
            SimpleCastIntent.CastSlot("com.google.android.projection.gearhead", ClusterSlotSide.RIGHT)
        )
        Thread.sleep(200)
        val s = coordinator.state
        assertTrue(
            s is SimpleCastState.Idle || s is SimpleCastState.Error,
            "AA in slot should be rejected, got $s"
        )
    }

    @Test
    fun `CP full cast is allowed (not rejected)`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastFull("com.byd.autolink.carplay", AppType.CARPLAY))
        awaitState<SimpleCastState.CastingFull>()
        val s = coordinator.state as SimpleCastState.CastingFull
        assertEquals("com.byd.autolink.carplay", s.targetPkg)
        assertEquals(AppType.CARPLAY, s.appType)
    }

    // ─── Parser: ambiguous task → reject ──────────────────────────────────────

    @Test
    fun `ambiguous task lookup returns Ambiguous result`() {
        // Simulate two tasks with same package on same display
        val amOutput = """
            Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0
              taskId=100: com.test.app/.ActivityA visible=true
              taskId=101: com.test.app/.ActivityB visible=true
        """.trimIndent()
        val result = CastStackParser.findTaskIdTyped(amOutput, "com.test.app", 1)
        assertTrue(result is CastStackParser.TaskLookupResult.Ambiguous, "Expected Ambiguous, got $result")
        val ambiguous = result as CastStackParser.TaskLookupResult.Ambiguous
        assertEquals(2, ambiguous.matches.size)
    }

    @Test
    fun `single task match returns Found`() {
        val amOutput = """
            Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0
              taskId=100: com.test.app/.MainActivity visible=true
        """.trimIndent()
        val result = CastStackParser.findTaskIdTyped(amOutput, "com.test.app", 1)
        assertTrue(result is CastStackParser.TaskLookupResult.Found, "Expected Found, got $result")
        assertEquals("100", (result as CastStackParser.TaskLookupResult.Found).taskId)
    }

    @Test
    fun `no task match returns NotFound`() {
        val amOutput = """
            Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0
              taskId=100: com.other.app/.MainActivity visible=true
        """.trimIndent()
        val result = CastStackParser.findTaskIdTyped(amOutput, "com.test.app", 1)
        assertTrue(result is CastStackParser.TaskLookupResult.NotFound, "Expected NotFound, got $result")
    }

    @Test
    fun `package with regex metacharacters does not cause crash`() {
        // Package name like "com.test+app" shouldn't be treated as regex
        val amOutput = """
            Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0
              taskId=100: com.normal.app/.MainActivity visible=true
        """.trimIndent()
        // This should not throw — previously unescaped pkg could cause PatternSyntaxException
        val result = CastStackParser.findTaskIdTyped(amOutput, "com.test+app", 1)
        assertTrue(result is CastStackParser.TaskLookupResult.NotFound)
    }

    // ─── Postcondition: timeout → prior state preserved ───────────────────────

    @Test
    fun `failed cast does not commit state`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        // Make the shell fail to show the app on display 1 (postcondition fails)
        shell.blockAppOnDisplay1 = true
        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.app", AppType.NORMAL))
        Thread.sleep(500)

        // State should revert to Idle or Error, NOT CastingFull
        val s = coordinator.state
        assertTrue(
            s is SimpleCastState.Idle || s is SimpleCastState.Error,
            "Failed cast should preserve prior Idle state, got $s"
        )
    }

    // ─── R6: Failed resize → density NOT persisted ────────────────────────────

    @Test
    fun `failed density shell command does not persist preference`() {
        // Shell will fail density commands
        shell.failCommands.add("wm density")
        val result = CastDensityControl.set(shell, prefs, 1, 200, "com.test.app")
        // Check that prefs were NOT updated
        val saved = prefs.displayConfigFor("com.test.app")
        assertTrue(saved == null || saved.density == "reset" || saved.density == null,
            "Density should NOT be persisted on shell failure, got $saved")
    }

    @Test
    fun `successful density shell command persists preference`() {
        CastDensityControl.set(shell, prefs, 1, 200, "com.test.app")
        val saved = prefs.displayConfigFor("com.test.app")
        assertNotNull(saved)
        assertEquals("200", saved!!.density)
    }

    // ─── R5: Stop preempts pending cast ───────────────────────────────────────

    @Test
    fun `stop from CastingFull returns to Idle with bounded executor`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.app", AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>()
        // Now dispatch stop — should preempt any pending and execute immediately
        coordinator.dispatch(SimpleCastIntent.Stop())
        awaitState<SimpleCastState.Idle>()
        assertEquals(SimpleCastState.Idle, coordinator.state)
    }

    // ─── Slot occupancy: occupied slot → reject ───────────────────────────────

    @Test
    fun `cast to occupied slot is rejected`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        // Fill left slot
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()

        // Try to cast ANOTHER app to left slot (already occupied)
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.other", ClusterSlotSide.LEFT))
        Thread.sleep(200)

        // State should still show original left app, not the new one
        val s = coordinator.state
        if (s is SimpleCastState.CastingSplit) {
            assertEquals("com.test.left", s.left?.pkg,
                "Occupied slot should keep original app")
        }
        // OR it might be an error state which is also acceptable
        assertTrue(
            s is SimpleCastState.CastingSplit || s is SimpleCastState.Error,
            "Expected CastingSplit (unchanged) or Error, got $s"
        )
    }

    // ─── CastSlotValidator unit tests ─────────────────────────────────────────

    @Test
    fun `CastSlotValidator rejects CP in slot mode`() {
        val reason = CastSlotValidator.validateCastSlot(
            pkg = "com.byd.autolink.carplay",
            side = ClusterSlotSide.LEFT,
            currentState = SimpleCastState.Idle,
            projectionOpen = true,
        )
        assertEquals(CastRejectReason.PROTECTED_FULLSCREEN_STACK_UNPROVEN, reason)
    }

    @Test
    fun `CastSlotValidator rejects AA in slot mode`() {
        val reason = CastSlotValidator.validateCastSlot(
            pkg = "com.google.android.projection.gearhead",
            side = ClusterSlotSide.RIGHT,
            currentState = SimpleCastState.Idle,
            projectionOpen = true,
        )
        assertEquals(CastRejectReason.PROTECTED_FULLSCREEN_STACK_UNPROVEN, reason)
    }

    @Test
    fun `CastSlotValidator rejects occupied slot`() {
        val state = SimpleCastState.CastingSplit(
            left = SlotState("com.existing", DisplayConfig.NORMAL_DEFAULT),
            right = null,
        )
        val reason = CastSlotValidator.validateCastSlot(
            pkg = "com.new.app",
            side = ClusterSlotSide.LEFT,
            currentState = state,
            projectionOpen = true,
        )
        assertEquals(CastRejectReason.SLOT_OCCUPIED, reason)
    }

    @Test
    fun `CastSlotValidator allows normal app in empty slot`() {
        val reason = CastSlotValidator.validateCastSlot(
            pkg = "com.test.normal",
            side = ClusterSlotSide.LEFT,
            currentState = SimpleCastState.Idle,
            projectionOpen = true,
        )
        assertNull(reason, "Normal app in empty slot should be allowed")
    }

    @Test
    fun `CastSlotValidator rejects blank package`() {
        val reason = CastSlotValidator.validateCastSlot(
            pkg = "",
            side = ClusterSlotSide.LEFT,
            currentState = SimpleCastState.Idle,
            projectionOpen = true,
        )
        assertEquals(CastRejectReason.INVALID_PACKAGE, reason)
    }

    @Test
    fun `CastSlotValidator rejects when projection not open`() {
        val reason = CastSlotValidator.validateCastSlot(
            pkg = "com.test.normal",
            side = ClusterSlotSide.LEFT,
            currentState = SimpleCastState.Idle,
            projectionOpen = false,
        )
        assertEquals(CastRejectReason.DISPLAY_UNAVAILABLE, reason)
    }

    // ─── isAppOnDisplay exact match ───────────────────────────────────────────

    @Test
    fun `isAppOnDisplay does not match prefix`() {
        val amOutput = """
            Stack id=2 bounds=[0,0][1920,720] displayId=1 userId=0
              taskId=100: com.test.application/.MainActivity visible=true
        """.trimIndent()
        // "com.test" should NOT match "com.test.application"
        assertFalse(CastStackParser.isAppOnDisplay(amOutput, "com.test", 1))
        // But "com.test.application" should match exactly
        assertTrue(CastStackParser.isAppOnDisplay(amOutput, "com.test.application", 1))
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
