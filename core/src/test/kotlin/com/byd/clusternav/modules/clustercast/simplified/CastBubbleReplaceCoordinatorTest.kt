package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.5 · R31 — the coordinator side of the bubble's one-tap REPLACE (docs/specs/kachi-301-cast-steps-dialog-ia.html R31).
 *
 * KHOÁ: thay app trên cụm KHÔNG được để app cũ mồ côi trên VD cụm — app cũ phải được TRẢ về màn chính (`--display 0`)
 * TRƯỚC lệnh đặt app mới (`am start … --display 1`), trong CÙNG một thao tác trên executor nối tiếp. Bỏ `returnApp` ở nhánh
 * thay một nửa (SimpleCastCoordinatorIntents.kt, khối R31) ⇒ bài slot đỏ; bỏ cờ `replace` khỏi `validateCastSlot` ⇒ bài slot
 * đỏ (SLOT_OCCUPIED).
 */
class CastBubbleReplaceCoordinatorTest : SimpleCastCoordinatorHarness() {

    private fun returnIndex(pkg: String) = shell.history.indexOfFirst { it.contains("--display 0") && it.contains(pkg) }
    private fun castIndex(pkg: String) = shell.history.indexOfFirst { it.contains("am start") && it.contains("--display 1") && it.contains(pkg) }

    @Test
    fun `full replace returns the old app before casting the new one`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastFull("com.maps.a", AppType.NORMAL))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingFull)?.targetPkg == "com.maps.a" }
        shell.history.clear()

        coordinator.dispatch(SimpleCastIntent.CastFull("com.nav.b", AppType.NORMAL))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingFull)?.targetPkg == "com.nav.b" }

        val back = returnIndex("com.maps.a")
        val pin = castIndex("com.nav.b")
        assertTrue(back >= 0, "old app must be returned to display 0; history=${shell.history}")
        assertTrue(pin >= 0, "new app must be cast to the cluster; history=${shell.history}")
        assertTrue(back < pin, "return BEFORE pin (no orphan on the VD); history=${shell.history}")
    }

    @Test
    fun `slot replace returns that half before casting the new app and keeps the other half`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.maps.a", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.maps.a" }
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.music.c", ClusterSlotSide.RIGHT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.right?.pkg == "com.music.c" }
        shell.history.clear()

        coordinator.dispatch(SimpleCastIntent.CastSlot("com.nav.b", ClusterSlotSide.LEFT, replace = true))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.nav.b" }

        val s = coordinator.state as SimpleCastState.CastingSplit
        assertEquals("com.music.c", s.right?.pkg, "the other half is untouched")
        val back = returnIndex("com.maps.a")
        val pin = castIndex("com.nav.b")
        assertTrue(back >= 0, "old half must be returned; history=${shell.history}")
        assertTrue(pin >= 0, "new app must be cast; history=${shell.history}")
        assertTrue(back < pin, "return BEFORE pin; history=${shell.history}")
        assertTrue(returnIndex("com.music.c") < 0, "the other half is never returned; history=${shell.history}")
    }

    @Test
    fun `slot replace never moves an app that is already on the cluster`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.maps.a", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.maps.a" }
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.music.c", ClusterSlotSide.RIGHT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.right?.pkg == "com.music.c" }
        val mark = shell.history.size

        // The other half's app, and the same app, both stay SLOT_OCCUPIED even with replace = true.
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.music.c", ClusterSlotSide.LEFT, replace = true))
        awaitState<SimpleCastState.Error>()
        assertTrue((coordinator.state as SimpleCastState.Error).message.contains(CastRejectReason.SLOT_OCCUPIED.name))
        val newCommands = shell.history.drop(mark)
        assertTrue(newCommands.none { it.contains("--display 0") }, "reject issues no return; commands=$newCommands")
    }

    /**
     * Pass 5 (senior review 3.1.5, `CAST-REPLACE-FAIL-STATE`): the replace FAILS after the old half was already returned ⇒ the
     * other half is still on the cluster VD, so the state must recover to "split with only that half" — NOT Idle (Idle = the
     * surviving half is orphaned: a later Stop returns nothing). Revert `survivor` in `handleCastSlot` ⇒ red (state Idle).
     */
    @Test
    fun `slot replace that fails after returning keeps the surviving half in state - no orphan`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.maps.a", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left?.pkg == "com.maps.a" }
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.music.c", ClusterSlotSide.RIGHT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.right?.pkg == "com.music.c" }
        shell.history.clear()
        // The new app does not LAND (R1 am start + R2 move-task both miss on `am stack list`) ⇒ `castToCluster` = null. (Not
        // `failCommands`: the fake records history before failing, so a "failed" am start still shows up as landed.)
        shell.blockAppOnDisplay1 = true

        coordinator.dispatch(SimpleCastIntent.CastSlot("com.nav.b", ClusterSlotSide.LEFT, replace = true))
        awaitState<SimpleCastState.Error>()
        assertTrue(returnIndex("com.maps.a") >= 0, "old half was returned before the failed pin; history=${shell.history}")
        assertTrue(returnIndex("com.music.c") < 0, "the other half is never returned; history=${shell.history}")
        // ERROR_RECOVERY_MS = 3 s ⇒ the state must come back as the surviving split, not Idle.
        awaitTrue(timeoutMs = 6000) { coordinator.state !is SimpleCastState.Error }
        val s = coordinator.state
        assertTrue(s is SimpleCastState.CastingSplit, "after the error the surviving half must stay in state (got $s)")
        s as SimpleCastState.CastingSplit
        assertEquals(null, s.left?.pkg, "the replaced half is empty (its app went back to display 0)")
        assertEquals("com.music.c", s.right?.pkg, "the other half is still tracked ⇒ Stop can still return it")
    }

    @Test
    fun `validator lifts SLOT_OCCUPIED only for an opt-in replace by a new app`() {
        val split = SimpleCastState.CastingSplit(
            left = SlotState("com.maps.a", DisplayConfig.NORMAL_DEFAULT),
            right = SlotState("com.music.c", DisplayConfig.NORMAL_DEFAULT),
        )
        fun v(pkg: String, replace: Boolean) =
            CastSlotValidator.validateCastSlot(pkg, ClusterSlotSide.LEFT, split, projectionOpen = true, replace = replace)
        assertEquals(CastRejectReason.SLOT_OCCUPIED, v("com.nav.b", replace = false), "default keeps the autostart race guard")
        assertEquals(null, v("com.nav.b", replace = true))
        assertEquals(CastRejectReason.SLOT_OCCUPIED, v("com.maps.a", replace = true), "same app")
        assertEquals(CastRejectReason.SLOT_OCCUPIED, v("com.music.c", replace = true), "other half's app")
    }
}
