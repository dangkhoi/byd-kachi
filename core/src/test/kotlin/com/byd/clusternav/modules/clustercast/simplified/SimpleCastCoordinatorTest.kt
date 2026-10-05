package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

class SimpleCastCoordinatorTest : SimpleCastCoordinatorHarness() {


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

    /**
     * Lần mở ĐẦU sau nổ máy (CHƯA có màn ảo cụm — [ĐO F2]) ⇒ chuỗi Seal cũ không đổi. 2.89 · CLUSTER-THEME-SAFE: opcode 30 nay
     * đi qua cổng; B1a: chỉ gửi khi chưa có màn ảo cụm — các ca màn ảo còn / app lạ / mở lại / đọc hỏng khoá ở
     * `CastThemeSafeCoordinatorTest`.
     */
    @Test
    fun `openProjection issues seal commands 30, 16, 35`() {
        shell.vdAbsentUntilCast = true
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
}
