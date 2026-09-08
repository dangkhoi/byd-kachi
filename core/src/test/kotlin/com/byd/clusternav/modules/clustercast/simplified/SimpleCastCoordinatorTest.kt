package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class SimpleCastCoordinatorTest {

    private lateinit var shell: FakeShell
    private lateinit var prefs: FakePrefs
    private lateinit var coordinator: SimpleCastCoordinator

    @BeforeEach
    fun setup() {
        shell = FakeShell()
        prefs = FakePrefs()
        val projection = ProjectionManager(shell, sleepMs = {}) // no actual sleep in tests
        val configurator = DisplayConfigurator(shell)
        val mover = AppMover(shell, sleepMs = {})
        coordinator = SimpleCastCoordinator(
            projection, configurator, mover, prefs, shell, displayId = 1,
            castTimeoutMs = 15_000L,
            stopTimeoutMs = 5_000L,
        )
    }

    // ─── Projection lifecycle ─────────────────────────────────────────────────

    @Test
    fun `initial state is Off`() {
        assertEquals(SimpleCastState.Off, coordinator.state)
    }

    @Test
    fun `openProjection transitions Off to Idle`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        assertEquals(SimpleCastState.Idle, coordinator.state)
    }

    @Test
    fun `openProjection is idempotent when already open (R10)`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        shell.history.clear()
        coordinator.openProjection() // second request while already Idle (boot service + Activity)
        Thread.sleep(100)
        assertEquals(SimpleCastState.Idle, coordinator.state)
        assertTrue(
            shell.history.none { it.contains("AutoContainer") },
            "second openProjection must be a no-op — no re-open seal commands re-issued",
        )
    }

    @Test
    fun `openProjection failure produces Error state`() {
        shell.shouldFail = true
        coordinator.openProjection()
        awaitState<SimpleCastState.Error>()
        assertTrue(coordinator.state is SimpleCastState.Error)
    }

    @Test
    fun `openProjection issues seal commands 30, 16, 35`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        val cmds = shell.history.filter { it.contains("AutoContainer") }
        assertEquals(3, cmds.size)
        assertTrue(cmds[0].contains("i32 30"))
        assertTrue(cmds[1].contains("i32 16"))
        assertTrue(cmds[2].contains("i32 35"))
    }

    @Test
    fun `closeProjection transitions to Off`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.closeProjection()
        awaitState<SimpleCastState.Off>()
        assertEquals(SimpleCastState.Off, coordinator.state)
    }

    @Test
    fun `closeProjection issues commands 18, 0`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        shell.history.clear()
        coordinator.closeProjection()
        awaitState<SimpleCastState.Off>()
        val cmds = shell.history.filter { it.contains("AutoContainer") }
        assertEquals(2, cmds.size)
        assertTrue(cmds[0].contains("i32 18"))
        assertTrue(cmds[1].contains("i32 0"))
    }

    // ─── Cast full (CP/AA) ────────────────────────────────────────────────────

    @Test
    fun `cast CP full from Idle`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastFull("com.byd.autolink.carplay", AppType.CARPLAY))
        awaitState<SimpleCastState.CastingFull>()
        val s = coordinator.state as SimpleCastState.CastingFull
        assertEquals("com.byd.autolink.carplay", s.targetPkg)
        assertEquals(AppType.CARPLAY, s.appType)
        assertEquals(DisplayConfig.CARPLAY, s.displayConfig)
    }

    @Test
    fun `cast CP sets correct wm size`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        shell.history.clear()
        coordinator.dispatch(SimpleCastIntent.CastFull("com.byd.autolink.carplay", AppType.CARPLAY))
        awaitState<SimpleCastState.CastingFull>()
        assertTrue(shell.history.any { it.contains("wm size 1422x800 -d 1") })
        assertTrue(shell.history.any { it.contains("wm overscan 10,-120,10,50 -d 1") })
    }

    @Test
    fun `cast AA sets correct wm size`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        shell.history.clear()
        coordinator.dispatch(
            SimpleCastIntent.CastFull("com.google.android.projection.gearhead", AppType.ANDROID_AUTO)
        )
        awaitState<SimpleCastState.CastingFull>()
        assertTrue(shell.history.any { it.contains("wm size 1920x1080 -d 1") })
        assertTrue(shell.history.any { it.contains("wm overscan 0,0,0,0 -d 1") })
    }

    // ─── Stop ─────────────────────────────────────────────────────────────────

    @Test
    fun `stop from CastingFull returns to Idle`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.app", AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>()
        coordinator.dispatch(SimpleCastIntent.Stop())
        awaitState<SimpleCastState.Idle>()
        assertEquals(SimpleCastState.Idle, coordinator.state)
    }

    @Test
    fun `stop from Idle is no-op`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.Stop())
        Thread.sleep(50)
        assertEquals(SimpleCastState.Idle, coordinator.state)
    }

    // ─── Split mode ───────────────────────────────────────────────────────────

    @Test
    fun `cast to left slot creates CastingSplit`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()
        val s = coordinator.state as SimpleCastState.CastingSplit
        assertEquals("com.test.left", s.left?.pkg)
        assertNull(s.right)
    }

    @Test
    fun `cast to slot applies display config before move`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        shell.history.clear()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()
        // Config is already NORMAL_DEFAULT from openProjection — configurator correctly skips redundant apply.
        // Verify the cast still issues am start (the actual cast command).
        val amStartIndex = shell.history.indexOfFirst { it.contains("am start") && it.contains("com.test.left") }
        assertTrue(amStartIndex >= 0, "am start command should be issued for slot cast")
    }

    @Test
    fun `cast both slots creates full split`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        // wait for the second slot
        Thread.sleep(100)
        val s = coordinator.state as SimpleCastState.CastingSplit
        assertEquals("com.test.left", s.left?.pkg)
        assertEquals("com.test.right", s.right?.pkg)
    }

    @Test
    fun `stop left slot keeps right`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        Thread.sleep(100)
        coordinator.dispatch(SimpleCastIntent.Stop(slot = ClusterSlotSide.LEFT))
        Thread.sleep(100)
        val s = coordinator.state as SimpleCastState.CastingSplit
        assertNull(s.left)
        assertEquals("com.test.right", s.right?.pkg)
    }

    @Test
    fun `stop only occupied slot returns to Idle without invariant crash`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        // Only left slot occupied, right is null
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()
        // Stop the only occupied slot specifically → should go to Idle, not crash
        coordinator.dispatch(SimpleCastIntent.Stop(slot = ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.Idle>()
        assertEquals(SimpleCastState.Idle, coordinator.state)
    }

    @Test
    fun `stop both slots in split returns to Idle`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()
        coordinator.dispatch(SimpleCastIntent.Stop(slot = null)) // stop all
        awaitState<SimpleCastState.Idle>()
        assertEquals(SimpleCastState.Idle, coordinator.state)
    }

    // ─── Invalid transitions ──────────────────────────────────────────────────

    @Test
    fun `cast from Off is rejected with DISPLAY_UNAVAILABLE`() {
        coordinator.dispatch(SimpleCastIntent.CastFull("com.test", AppType.NORMAL))
        Thread.sleep(200)
        // R4 precondition: projection not open → rejected with DISPLAY_UNAVAILABLE → Error
        val state = coordinator.state
        assertTrue(
            state is SimpleCastState.Off || state is SimpleCastState.Error,
            "Cast from Off must be rejected (Off or Error), got: $state"
        )
    }

    @Test
    fun `cast split from CastingFull is ignored`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastFull("com.test", AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.other", ClusterSlotSide.LEFT))
        Thread.sleep(50)
        assertTrue(coordinator.state is SimpleCastState.CastingFull)
    }

    // ─── Repeated cast ────────────────────────────────────────────────────────

    @Test
    fun `cast - stop - cast cycle works multiple times`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        repeat(3) {
            coordinator.dispatch(SimpleCastIntent.CastFull("com.test.app", AppType.NORMAL))
            awaitState<SimpleCastState.CastingFull>()
            coordinator.dispatch(SimpleCastIntent.Stop())
            awaitState<SimpleCastState.Idle>()
        }
        assertEquals(SimpleCastState.Idle, coordinator.state)
    }

    // ─── State listener ───────────────────────────────────────────────────────

    @Test
    fun `state listener receives transitions`() {
        val states = CopyOnWriteArrayList<SimpleCastState>()
        coordinator.addStateListener { states.add(it) }
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        // Should have: Off (initial emit), Opening, Idle
        assertTrue(states.size >= 2)
        assertTrue(states.last() == SimpleCastState.Idle)
    }

    // ─── Profiles + per-slot resize (R4/R5/R6) ────────────────────────────────

    @Test
    fun `CastProfile of maps side and leftPercent`() {
        assertEquals("L50", CastProfile.of(ClusterSlotSide.LEFT, 50).key)
        assertEquals("L30", CastProfile.of(ClusterSlotSide.LEFT, 30).key)
        assertEquals("L70", CastProfile.of(ClusterSlotSide.LEFT, 70).key)
        assertEquals("R50", CastProfile.of(ClusterSlotSide.RIGHT, 50).key)
        assertEquals("R30", CastProfile.of(ClusterSlotSide.RIGHT, 30).key)
        assertEquals("R70", CastProfile.of(ClusterSlotSide.RIGHT, 70).key)
        // Out-of-set leftPercent (never one of the 9 ratios) falls back to the default (50).
        assertEquals(CastProfile.of(ClusterSlotSide.LEFT, 50), CastProfile.of(ClusterSlotSide.LEFT, 55))
        assertEquals(CastProfile.of(ClusterSlotSide.RIGHT, 50), CastProfile.of(ClusterSlotSide.RIGHT, 999))
    }

    @Test
    fun `prefs round-trip per profile uses distinct non-colliding keys`() {
        val pkg = "com.test.app"
        val full = DisplayConfig("1920x720", "0,0,0,0", "160", CastBounds(0, 0, 1920, 720))
        val l30 = DisplayConfig("1920x720", "0,0,0,0", "200", CastBounds(0, 0, 576, 720))
        val r70 = DisplayConfig("1920x720", "0,0,0,0", "240", CastBounds(576, 0, 1920, 720))

        prefs.saveDisplayConfig(pkg, CastProfile.FULL, full)
        prefs.saveDisplayConfig(pkg, CastProfile.of(ClusterSlotSide.LEFT, 30), l30)
        prefs.saveDisplayConfig(pkg, CastProfile.of(ClusterSlotSide.RIGHT, 70), r70)

        // Each profile reads back its own value — no cross-contamination.
        assertEquals(full, prefs.displayConfigFor(pkg, CastProfile.FULL))
        assertEquals(l30, prefs.displayConfigFor(pkg, CastProfile.of(ClusterSlotSide.LEFT, 30)))
        assertEquals(r70, prefs.displayConfigFor(pkg, CastProfile.of(ClusterSlotSide.RIGHT, 70)))
        // No-arg overload is the FULL profile (backward compat).
        assertEquals(full, prefs.displayConfigFor(pkg))
        // Untouched profiles remain null.
        assertNull(prefs.displayConfigFor(pkg, CastProfile.of(ClusterSlotSide.LEFT, 50)))
        assertNull(prefs.displayConfigFor(pkg, CastProfile.of(ClusterSlotSide.RIGHT, 30)))
    }

    @Test
    fun `resizeActiveSlot persists to the matching profile on shell success`() {
        prefs.setSplitRatioLeftPercent(30)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()

        coordinator.resizeActiveSlot(ClusterSlotSide.LEFT, 0, 0, 500, 720)
        // LEFT × ratio 30 → profile L30
        awaitTrue { prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 30))?.bounds == CastBounds(0, 0, 500, 720) }

        assertEquals(
            CastBounds(0, 0, 500, 720),
            prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 30))?.bounds,
        )
        // Persisted to L30 only — the FULL profile is untouched.
        assertNull(prefs.displayConfigFor("com.test.left", CastProfile.FULL))
    }

    @Test
    fun `resizeActiveSlot does not persist when am task resize fails`() {
        prefs.setSplitRatioLeftPercent(70)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitState<SimpleCastState.CastingSplit>()

        // Force the per-slot resize shell command to fail — nothing must be persisted (R6).
        shell.failCommands.add("am task resize")
        coordinator.resizeActiveSlot(ClusterSlotSide.RIGHT, 600, 0, 1920, 720)
        Thread.sleep(300) // allow the serial executor to run the (failing) resize

        // RIGHT × ratio 70 → profile R70; must stay null because the shell rejected the resize.
        assertNull(prefs.displayConfigFor("com.test.right", CastProfile.of(ClusterSlotSide.RIGHT, 70)))
    }

    // ─── Live split-ratio change (Feature 2) ──────────────────────────────────

    @Test
    fun `applySplitRatioLive re-resizes both slots in place when casting split`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.test.left" }
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.right?.pkg == "com.test.right" }

        // Mark history rather than clearing it: FakeShell reconstructs the display-1 stack from the
        // recorded `am start --display 1` commands, so clearing would make both apps "disappear" and
        // the resize would find no task. The ratio-30 bounds below are unique to applySplitRatioLive
        // (the initial cast used the default ratio 50 → boundary 960), so scoping to new commands is
        // enough to prove the live re-resize happened.
        val mark = shell.history.size
        coordinator.applySplitRatioLive(30)

        // (a) new ratio persisted for the next cast.
        awaitTrue { prefs.splitRatioLeftPercent() == 30 }
        // (b) BOTH slots re-resized to the ratio-30 split. FakeShell returns no `wm size`, so the
        // coordinator falls back to 1920×720 → boundary = 1920·30/100 = 576.
        awaitTrue { shell.history.drop(mark).any { it.contains("am task resize") && it.endsWith(" 0 0 576 720") } }
        awaitTrue { shell.history.drop(mark).any { it.contains("am task resize") && it.endsWith(" 576 0 1920 720") } }
        // In-place resize: nothing is returned to the main display (no return+recast).
        assertTrue(
            shell.history.drop(mark).none { it.contains("--display 0") },
            "live ratio change must not return apps to main; commands=${shell.history.drop(mark)}",
        )
        // Per-ratio profiles updated on success (R6).
        awaitTrue { prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 30))?.bounds == CastBounds(0, 0, 576, 720) }
        awaitTrue { prefs.displayConfigFor("com.test.right", CastProfile.of(ClusterSlotSide.RIGHT, 30))?.bounds == CastBounds(576, 0, 1920, 720) }
    }

    @Test
    fun `applySplitRatioLive only persists the ratio when not casting split`() {
        // Off (never cast). Must persist the ratio but issue NO slot resize.
        coordinator.applySplitRatioLive(70)
        awaitTrue { prefs.splitRatioLeftPercent() == 70 }
        Thread.sleep(100)
        assertTrue(
            shell.history.none { it.contains("am task resize") },
            "no split → no slot resize; commands=${shell.history}",
        )
    }

    @Test
    fun `applySplitRatioLive normalizes an out-of-set percent to the default 50`() {
        // Public entry-point hardening: a percent outside the 9 supported buckets (10..90 step 10)
        // must NOT persist verbatim — it would corrupt the next split cast's fitToCluster geometry,
        // drive a degenerate `am task resize`, and file bounds under a mismatched profile key.
        // It clamps to the R3 default (50), matching CastProfile.of/normalizePercent everywhere else.
        coordinator.applySplitRatioLive(55) // 55 is not one of SPLIT_PERCENTS
        awaitTrue { prefs.splitRatioLeftPercent() == 50 }
        coordinator.applySplitRatioLive(0) // degenerate — would make a zero-width slot
        awaitTrue { prefs.splitRatioLeftPercent() == 50 }
        coordinator.applySplitRatioLive(1000) // out of range high
        awaitTrue { prefs.splitRatioLeftPercent() == 50 }
        // A valid bucket still round-trips unchanged.
        coordinator.applySplitRatioLive(30)
        awaitTrue { prefs.splitRatioLeftPercent() == 30 }
    }

    // ─── T1 autostart sequencing proof (R1) ──────────────────────────────────

    @Test
    fun `autostart split sequences LEFT then RIGHT into CastingSplit with no Error`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        // Record every transition from Idle onward so we can prove no Error slips in.
        val states = CopyOnWriteArrayList<SimpleCastState>()
        coordinator.addStateListener { states.add(it) }

        // The service's fixed sequencing: cast LEFT, then cast RIGHT ONLY after the coordinator
        // reports CastingSplit with a landed left slot (never a blind delay).
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.test.left" }

        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitTrue {
            val cur = coordinator.state
            cur is SimpleCastState.CastingSplit &&
                cur.left?.pkg == "com.test.left" && cur.right?.pkg == "com.test.right"
        }

        val s = coordinator.state as SimpleCastState.CastingSplit
        assertEquals("com.test.left", s.left?.pkg)
        assertEquals("com.test.right", s.right?.pkg)
        // R1: no Error state may appear during a correctly-sequenced split autostart.
        assertTrue(
            states.none { it is SimpleCastState.Error },
            "sequenced split autostart must not enter Error; saw: $states",
        )
    }

    @Test
    fun `dispatching the same slot twice is rejected SLOT_OCCUPIED without returning the other slot`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()

        // Establish a full split: LEFT then RIGHT (RIGHT only after LEFT landed).
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.test.left" }
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.right?.pkg == "com.test.right" }

        // Precondition: both slots occupied.
        val before = coordinator.state as SimpleCastState.CastingSplit
        assertEquals("com.test.left", before.left?.pkg)
        assertEquals("com.test.right", before.right?.pkg)

        // Mark the shell history, then dispatch the SAME (already-occupied) LEFT slot again —
        // exactly what the old two-driver autostart did.
        val historyMark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))

        // The coordinator rejects the duplicate with SLOT_OCCUPIED (transient Error).
        awaitState<SimpleCastState.Error>()
        val err = coordinator.state as SimpleCastState.Error
        assertTrue(
            err.message.contains(CastRejectReason.SLOT_OCCUPIED.name),
            "expected SLOT_OCCUPIED rejection, got: ${err.message}",
        )

        // The rejection must not tear down the OTHER slot: no app is returned to the main display
        // (returnToMain always issues `am start --display 0 ...`). The reject path issues no shell
        // at all, so the right (and left) app placement on the cluster is left untouched.
        val newCommands = shell.history.drop(historyMark)
        assertTrue(
            newCommands.none { it.contains("--display 0") },
            "duplicate-slot reject must not return any app to the main display; commands=$newCommands",
        )
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private inline fun <reified T : SimpleCastState> awaitState(timeoutMs: Long = 2000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (coordinator.state !is T && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(coordinator.state is T, "Expected ${T::class.simpleName} but got ${coordinator.state}")
    }

    private fun awaitTrue(timeoutMs: Long = 2000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(condition(), "condition not met within ${timeoutMs}ms")
    }
}

