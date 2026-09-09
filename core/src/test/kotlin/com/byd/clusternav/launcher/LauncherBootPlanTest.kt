package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Off-device unit test for [LauncherBootPlan] — the PURE cast-coordination decision (B6): which per-slot apps
 * the launcher mounts vs skips because cluster-cast owns/will-cast them onto the cluster.
 */
class LauncherBootPlanTest {

    private val maps = "com.google.android.apps.maps"
    private val vietmap = "vn.vietmap.live"
    private val spotify = "com.spotify.music"

    @Test fun `no app slots yields an empty plan`() {
        val plan = LauncherBootPlan.plan(
            listOf(SlotContent.Empty, SlotContent.Widget("w_energy"), SlotContent.Empty, SlotContent.Empty),
        ) { false }
        assertTrue(plan.mount.isEmpty())
        assertTrue(plan.skippedToCast.isEmpty())
        assertFalse(plan.hasApps)
    }

    @Test fun `all apps mount when cast owns none`() {
        val plan = LauncherBootPlan.plan(
            listOf(SlotContent.App(maps), SlotContent.App(spotify), SlotContent.Empty, SlotContent.Empty),
        ) { false }
        assertEquals(
            listOf(LauncherBootPlan.SlotApp(0, maps), LauncherBootPlan.SlotApp(1, spotify)),
            plan.mount,
        )
        assertTrue(plan.skippedToCast.isEmpty())
        assertTrue(plan.hasApps)
    }

    @Test fun `a cast-owned app is skipped, others still mount, slot indices preserved`() {
        val slots = listOf(
            SlotContent.App(maps),       // slot 0 — cast owns → skip
            SlotContent.Widget("w_pm25"),// slot 1 — ignored
            SlotContent.App(spotify),    // slot 2 — mount
            SlotContent.App(vietmap),    // slot 3 — cast owns → skip
        )
        val castOwned = setOf(maps, vietmap)
        val plan = LauncherBootPlan.plan(slots) { it in castOwned }

        assertEquals(listOf(LauncherBootPlan.SlotApp(2, spotify)), plan.mount, "only the non-cast app mounts, at its real slot")
        assertEquals(
            listOf(LauncherBootPlan.SlotApp(0, maps), LauncherBootPlan.SlotApp(3, vietmap)),
            plan.skippedToCast,
            "cast-owned apps are recorded as skipped, at their real slots",
        )
    }

    @Test fun `all apps skipped when cast owns all of them`() {
        val plan = LauncherBootPlan.plan(
            listOf(SlotContent.App(maps), SlotContent.App(vietmap), SlotContent.Empty, SlotContent.Empty),
        ) { true }
        assertTrue(plan.mount.isEmpty(), "cast owns everything → launcher mounts nothing (does not fight cast)")
        assertEquals(
            listOf(LauncherBootPlan.SlotApp(0, maps), LauncherBootPlan.SlotApp(1, vietmap)),
            plan.skippedToCast,
        )
    }

    @Test fun `predicate is consulted per package`() {
        val asked = mutableListOf<String>()
        LauncherBootPlan.plan(listOf(SlotContent.App(maps), SlotContent.App(spotify), SlotContent.Empty, SlotContent.Empty)) {
            asked.add(it); it == maps
        }
        assertEquals(listOf(maps, spotify), asked, "castOwns queried once per app slot, in slot order")
    }
}
