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

    // ── reconcile() — quality-review 2026-09-15 R1/R2: cửa sổ suy ra từ state, có bước EVICT tường minh ──

    @Test fun `reconcile evicts app whose slot state no longer holds it`() {
        // Trước: maps ở ô 0 (đang hiện). Sau: state chuyển maps sang ô 1 (dedup ⇒ ô 0 trống).
        // placedOnLauncher = {maps} (cửa sổ maps đang hiện). Desired giờ chỉ có maps@ô1.
        val after = listOf(SlotContent.Empty, SlotContent.App(maps), SlotContent.Empty, SlotContent.Empty)
        val r = LauncherBootPlan.reconcile(after, placedOnLauncher = setOf(maps)) { false }
        assertEquals(listOf(LauncherBootPlan.SlotApp(1, maps)), r.mount, "maps phải có mặt ở ô 1")
        assertTrue(r.evict.isEmpty(), "maps vẫn ở màn launcher (ô 1) ⇒ KHÔNG evict")
    }

    @Test fun `reconcile evicts an app removed from all slots`() {
        // maps đang hiện nhưng state không còn ô nào giữ maps ⇒ phải GỠ cửa sổ (không chờ death-poll 10-15s).
        val after = listOf(SlotContent.App(vietmap), SlotContent.Empty, SlotContent.Empty, SlotContent.Empty)
        val r = LauncherBootPlan.reconcile(after, placedOnLauncher = setOf(maps, vietmap)) { false }
        assertEquals(listOf(maps), r.evict, "maps không còn ô nào ⇒ evict")
        assertEquals(listOf(LauncherBootPlan.SlotApp(0, vietmap)), r.mount)
    }

    @Test fun `reconcile does not evict a cast-owned app off the launcher`() {
        // maps đang được cụm giữ (castOwns=true) ⇒ không mount, và KHÔNG có trong placedOnLauncher ⇒ không evict.
        val after = listOf(SlotContent.App(maps), SlotContent.Empty, SlotContent.Empty, SlotContent.Empty)
        val r = LauncherBootPlan.reconcile(after, placedOnLauncher = setOf(vietmap)) { it == maps }
        assertTrue(r.mount.isEmpty(), "cast giữ maps ⇒ launcher không giành")
        assertEquals(listOf(vietmap), r.evict, "vietmap không còn ô ⇒ evict; maps không bị đụng")
    }
}
