package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Wave 2 (cast-nav-ux-release-v104): R3 (all 9 split ratios + per-ratio geometry) and R4/#5
 * (DPI persists under the SAME per-ratio profile key as bounds and is re-applied on re-cast).
 *
 * Pure-JVM (:core) — FakeShell/FakePrefs from CastCoordinatorTestFakes.kt. No Android import.
 */
class CastProfileDensityTest {

    private lateinit var shell: FakeShell
    private lateinit var prefs: FakePrefs
    private lateinit var coordinator: SimpleCastCoordinator

    @BeforeEach
    fun setup() {
        shell = FakeShell()
        prefs = FakePrefs()
        coordinator = SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}),
            DisplayConfigurator(shell),
            AppMover(shell, sleepMs = {}),
            prefs, shell, displayId = 1,
        )
    }

    // ─── R3: all 9 split ratios ───────────────────────────────────────────────

    @Test
    fun `SPLIT_PERCENTS is the full 9-ratio step-10 set (R3)`() {
        assertEquals(listOf(10, 20, 30, 40, 50, 60, 70, 80, 90), CastProfile.SPLIT_PERCENTS)
        assertEquals(9, CastProfile.SPLIT_PERCENTS.size)
    }

    @Test
    fun `9 percents map to 9 distinct profiles per side, 19 profiles total (R3)`() {
        val leftKeys = CastProfile.SPLIT_PERCENTS.map { CastProfile.of(ClusterSlotSide.LEFT, it).key }
        val rightKeys = CastProfile.SPLIT_PERCENTS.map { CastProfile.of(ClusterSlotSide.RIGHT, it).key }

        assertEquals(
            listOf("L10", "L20", "L30", "L40", "L50", "L60", "L70", "L80", "L90"),
            leftKeys,
        )
        assertEquals(
            listOf("R10", "R20", "R30", "R40", "R50", "R60", "R70", "R80", "R90"),
            rightKeys,
        )
        // 9 (left) + 9 (right) + FULL = 19 distinct profile keys per app.
        val all = (leftKeys + rightKeys + CastProfile.FULL.key).toSet()
        assertEquals(19, all.size)
        assertEquals("FULL", CastProfile.FULL.key)
        assertTrue(CastProfile.FULL.isFull)
    }

    @Test
    fun `profile key round-trips through fromKey, including predecessor keys (R3 backward compat)`() {
        for (pct in CastProfile.SPLIT_PERCENTS) {
            for (side in listOf(ClusterSlotSide.LEFT, ClusterSlotSide.RIGHT)) {
                val p = CastProfile.of(side, pct)
                assertEquals(p, CastProfile.fromKey(p.key), "round-trip failed for ${p.key}")
            }
        }
        assertEquals(CastProfile.FULL, CastProfile.fromKey("FULL"))
        // Keys saved by the predecessor ({50,30,70}) still parse to the same profile.
        assertEquals(CastProfile.of(ClusterSlotSide.LEFT, 30), CastProfile.fromKey("L30"))
        assertEquals(CastProfile.of(ClusterSlotSide.RIGHT, 70), CastProfile.fromKey("R70"))
        // Malformed tokens → null (never crash on a corrupt pref).
        assertNull(CastProfile.fromKey("X10"))
        assertNull(CastProfile.fromKey("L"))
        assertNull(CastProfile.fromKey("Lxx"))
    }

    @Test
    fun `unknown or legacy percent falls back to default 50 (R3)`() {
        assertEquals(50, CastProfile.normalizePercent(55))
        assertEquals(50, CastProfile.normalizePercent(0))
        assertEquals(50, CastProfile.normalizePercent(100))
        assertEquals(50, CastProfile.normalizePercent(-1))
        assertEquals(50, CastProfile.of(ClusterSlotSide.LEFT, 55).percent)
        // A legacy stored key: "L60" (60 is a valid ratio now) stays 60; "L55" (never valid) → 50.
        assertEquals(60, CastProfile.fromKey("L60")?.percent)
        assertEquals(50, CastProfile.fromKey("L55")?.percent)
        // Valid percents are preserved unchanged.
        CastProfile.SPLIT_PERCENTS.forEach { assertEquals(it, CastProfile.normalizePercent(it)) }
    }

    // ─── R3 + R4: prefs round-trip for bounds AND density across ≥3 profiles ──

    @Test
    fun `prefs round-trips bounds AND density across distinct profiles with no cross-contamination (R3 R4)`() {
        val pkg = "com.test.app"
        val full = DisplayConfig("1920x720", "0,0,0,0", "160", CastBounds(0, 0, 1920, 720))
        val l20 = DisplayConfig("1920x720", "0,0,0,0", "200", CastBounds(0, 0, 384, 720))
        val r80 = DisplayConfig("1920x720", "0,0,0,0", "280", CastBounds(384, 0, 1920, 720))

        prefs.saveDisplayConfig(pkg, CastProfile.FULL, full)
        prefs.saveDisplayConfig(pkg, CastProfile.of(ClusterSlotSide.LEFT, 20), l20)
        prefs.saveDisplayConfig(pkg, CastProfile.of(ClusterSlotSide.RIGHT, 80), r80)

        // FULL: both bounds and density round-trip.
        assertEquals(full, prefs.displayConfigFor(pkg, CastProfile.FULL))
        assertEquals("160", prefs.displayConfigFor(pkg, CastProfile.FULL)?.density)
        assertEquals(CastBounds(0, 0, 1920, 720), prefs.displayConfigFor(pkg, CastProfile.FULL)?.bounds)

        // L20.
        val readL20 = prefs.displayConfigFor(pkg, CastProfile.of(ClusterSlotSide.LEFT, 20))
        assertEquals(l20, readL20)
        assertEquals("200", readL20?.density)
        assertEquals(CastBounds(0, 0, 384, 720), readL20?.bounds)

        // R80.
        val readR80 = prefs.displayConfigFor(pkg, CastProfile.of(ClusterSlotSide.RIGHT, 80))
        assertEquals(r80, readR80)
        assertEquals("280", readR80?.density)
        assertEquals(CastBounds(384, 0, 1920, 720), readR80?.bounds)

        // A never-written profile stays null (isolation), and no-arg == FULL.
        assertNull(prefs.displayConfigFor(pkg, CastProfile.of(ClusterSlotSide.LEFT, 90)))
        assertEquals(full, prefs.displayConfigFor(pkg))
    }

    // ─── R4 / owner bug #5: split DPI persists per ratio and is re-applied ─────

    @Test
    fun `split DPI persists under the per-ratio profile of each occupied slot (R4 #5)`() {
        prefs.setSplitRatioLeftPercent(20)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.test.left" }
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.right?.pkg == "com.test.right" }

        coordinator.setDensitySplit(200)

        // Both occupied slots persist DPI under their per-ratio (leftPercent=20) profile — the SAME
        // key bounds use. Before the fix, split DPI routed through setDensity with a null active pkg
        // (state is CastingSplit, not CastingFull), so nothing was written — the owner-reported gap.
        awaitTrue { prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 20))?.density == "200" }
        assertEquals(
            "200",
            prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 20))?.density,
        )
        assertEquals(
            "200",
            prefs.displayConfigFor("com.test.right", CastProfile.of(ClusterSlotSide.RIGHT, 20))?.density,
        )
        // The FULL profile is NOT where split DPI lands (the old path wrote FULL/nothing).
        assertNull(prefs.displayConfigFor("com.test.left", CastProfile.FULL))
    }

    @Test
    fun `re-cast applies the DPI saved for that ratio profile on restore (R4 #5)`() {
        prefs.setSplitRatioLeftPercent(20)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.test.left" }

        coordinator.setDensitySplit(200)
        awaitTrue { prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 20))?.density == "200" }

        // Tear the split down, then re-cast the same app at the same ratio.
        coordinator.dispatch(SimpleCastIntent.Stop())
        awaitState<SimpleCastState.Idle>()
        shell.history.clear()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.test.left" }

        // The restore path (applySavedProfile) must re-issue the saved density for this ratio.
        awaitTrue { shell.history.any { it == "wm density 200 -d 1" } }
        assertTrue(
            shell.history.any { it == "wm density 200 -d 1" },
            "re-cast must apply saved DPI 200 for ratio 20; history=${shell.history}",
        )
    }

    @Test
    fun `setDensitySplit is a no-op outside split state (R4 #5)`() {
        // In Idle (no split), nothing should be persisted for any profile.
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.setDensitySplit(240)
        Thread.sleep(150) // let the serial executor drain
        assertNull(prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 50)))
        assertNull(prefs.displayConfigFor("com.test.left", CastProfile.FULL))
    }

    // ─── #1/#2 owner bugs (2026-08-12): default DPI 240, no-stamp-bounds, adopt-branch restore ──

    @Test
    fun `NORMAL default cluster DPI is 240 not native 360 (owner default)`() {
        assertEquals("240", DisplayConfig.NORMAL_DEFAULT.density)
    }

    @Test
    fun `split DPI-only change does not stamp full-cluster bounds onto an un-resized slot (#1 guard)`() {
        prefs.setSplitRatioLeftPercent(20)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.test.left" }

        coordinator.setDensitySplit(200)
        awaitTrue { prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 20))?.density == "200" }

        val cfg = prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 20))
        assertEquals("200", cfg?.density)
        assertNull(
            cfg?.bounds,
            "a DPI-only change must NOT stamp full-cluster bounds — that would resize the split slot to full on re-cast",
        )
    }

    @Test
    fun `re-casting an app already on the cluster restores its saved FULL size + DPI (#1 adopt-branch)`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        // First cast lands the app on the cluster (am start --display 1 → present on display 1).
        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.app", AppType.NORMAL))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingFull)?.targetPkg == "com.test.app" }
        // Return it — it is still present on display 1 (the adopt path). Save a resized FULL profile.
        coordinator.dispatch(SimpleCastIntent.Stop())
        awaitState<SimpleCastState.Idle>()
        prefs.saveDisplayConfig(
            "com.test.app", CastProfile.FULL,
            DisplayConfig("1920x720", "0,0,0,0", "200", CastBounds(100, 50, 900, 600)),
        )
        // Re-cast: app already on display 1 → adopt branch. Must restore saved size + DPI (before the
        // fix this branch used NORMAL_DEFAULT and returned BEFORE applySavedProfile → the size was lost).
        coordinator.dispatch(SimpleCastIntent.CastFull("com.test.app", AppType.NORMAL))
        awaitTrue {
            (coordinator.state as? SimpleCastState.CastingFull)?.displayConfig?.bounds == CastBounds(100, 50, 900, 600)
        }
        val cfg = (coordinator.state as SimpleCastState.CastingFull).displayConfig
        assertEquals(CastBounds(100, 50, 900, 600), cfg.bounds)
        assertEquals("200", cfg.density)
        // The saved bounds are actually re-applied on the cluster (only source of this exact resize).
        // applySavedProfile runs on the executor right AFTER setState, so await the command landing.
        awaitTrue { shell.history.any { it.startsWith("am task resize") && it.endsWith("100 50 900 600") } }
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
