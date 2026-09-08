package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.simplified.BubbleGesturePlanner.BubbleMenuAction
import com.byd.clusternav.modules.clustercast.simplified.BubbleGesturePlanner.BubbleTapOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure decision contract for the single-icon bubble (R5 / #7). Verifies the parts of the
 * tap/long-press behaviour that do NOT need Android, so the Android layer only has to WIRE them:
 *
 *  1. TAP toggles full: Idle → CAST_FULL, any casting state → RETURN, transient → PREPARING.
 *  2. LONG-PRESS submenu = exactly Trái / Phải / Cấu hình, in that order.
 *  3. Submenu Trái/Phải map to the LEFT/RIGHT slot; Cấu hình maps to no slot (opens the app).
 */
class BubbleGesturePlannerTest {

    // ─── 1. TAP = toggle full ────────────────────────────────────────────────

    @Test
    fun `tap on an idle cluster casts the foreground full`() {
        assertEquals(BubbleTapOutcome.CAST_FULL, BubbleGesturePlanner.tapOutcome(SimpleCastState.Idle))
    }

    @Test
    fun `tap while casting full returns to the gauges`() {
        val full = SimpleCastState.CastingFull("com.test", AppType.NORMAL, DisplayConfig.NORMAL_DEFAULT)
        assertEquals(BubbleTapOutcome.RETURN, BubbleGesturePlanner.tapOutcome(full))
    }

    @Test
    fun `tap while split returns to the gauges (slot-less stop returns both halves)`() {
        val split = SimpleCastState.CastingSplit(
            left = SlotState("com.left", DisplayConfig.NORMAL_DEFAULT),
            right = SlotState("com.right", DisplayConfig.NORMAL_DEFAULT),
        )
        assertEquals(BubbleTapOutcome.RETURN, BubbleGesturePlanner.tapOutcome(split))
        // A single occupied half is still a casting state → still RETURN.
        val leftOnly = SimpleCastState.CastingSplit(
            left = SlotState("com.left", DisplayConfig.NORMAL_DEFAULT), right = null,
        )
        assertEquals(BubbleTapOutcome.RETURN, BubbleGesturePlanner.tapOutcome(leftOnly))
    }

    @Test
    fun `tap during a transient state is not actionable`() {
        listOf(
            SimpleCastState.Off,
            SimpleCastState.Opening,
            SimpleCastState.Stopping,
            SimpleCastState.Closing,
            SimpleCastState.Error("boom"),
        ).forEach { state ->
            assertEquals(
                BubbleTapOutcome.PREPARING,
                BubbleGesturePlanner.tapOutcome(state),
                "$state must be PREPARING (icon still shows a toast, never a dead no-op)",
            )
        }
    }

    @Test
    fun `every cast state maps to exactly one outcome (icon is never a dead no-op)`() {
        // The single icon must always do SOMETHING on tap. Enumerate the stable + transient states.
        val states = listOf(
            SimpleCastState.Off,
            SimpleCastState.Opening,
            SimpleCastState.Idle,
            SimpleCastState.CastingFull("com.a", AppType.NORMAL, DisplayConfig.NORMAL_DEFAULT),
            SimpleCastState.CastingSplit(SlotState("com.a", DisplayConfig.NORMAL_DEFAULT), null),
            SimpleCastState.Stopping,
            SimpleCastState.Error("x"),
            SimpleCastState.Closing,
        )
        states.forEach { assertTrue(BubbleGesturePlanner.tapOutcome(it) in BubbleTapOutcome.entries) }
    }

    // ─── 2. LONG-PRESS submenu contents + order ──────────────────────────────

    @Test
    fun `submenu is exactly Trai, Phai, Cau hinh in order`() {
        val items = BubbleGesturePlanner.submenuItems()
        assertEquals(3, items.size)
        assertEquals(listOf("Trái", "Phải", "Cấu hình"), items.map { it.label })
        assertEquals(
            listOf(BubbleMenuAction.CAST_LEFT, BubbleMenuAction.CAST_RIGHT, BubbleMenuAction.OPEN_CONFIG),
            items.map { it.action },
        )
    }

    @Test
    fun `every submenu row has a non-blank label`() {
        BubbleGesturePlanner.submenuItems().forEach { assertTrue(it.label.isNotBlank()) }
    }

    // ─── 3. Submenu → slot mapping ───────────────────────────────────────────

    @Test
    fun `Trai casts LEFT and Phai casts RIGHT`() {
        assertEquals(ClusterSlotSide.LEFT, BubbleGesturePlanner.slotFor(BubbleMenuAction.CAST_LEFT))
        assertEquals(ClusterSlotSide.RIGHT, BubbleGesturePlanner.slotFor(BubbleMenuAction.CAST_RIGHT))
    }

    @Test
    fun `Cau hinh maps to no slot — it opens the app instead of casting`() {
        assertNull(BubbleGesturePlanner.slotFor(BubbleMenuAction.OPEN_CONFIG))
    }

    // ─── 4. Submenu slot TOGGLE: return-if-occupied, else cast (owner 2026-08-12) ──

    @Test
    fun `slot outcome RETURNs an occupied side and CASTs an empty side`() {
        val leftOnly = SimpleCastState.CastingSplit(
            left = SlotState("com.left", DisplayConfig.NORMAL_DEFAULT), right = null,
        )
        // Left is cast → pressing Trái returns it; right is empty → pressing Phải casts into it.
        assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.RETURN, BubbleGesturePlanner.slotOutcome(leftOnly, ClusterSlotSide.LEFT))
        assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.CAST, BubbleGesturePlanner.slotOutcome(leftOnly, ClusterSlotSide.RIGHT))

        val both = SimpleCastState.CastingSplit(
            left = SlotState("com.left", DisplayConfig.NORMAL_DEFAULT),
            right = SlotState("com.right", DisplayConfig.NORMAL_DEFAULT),
        )
        assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.RETURN, BubbleGesturePlanner.slotOutcome(both, ClusterSlotSide.LEFT))
        assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.RETURN, BubbleGesturePlanner.slotOutcome(both, ClusterSlotSide.RIGHT))
    }

    @Test
    fun `slot outcome CASTs on any non-split state (idle, full, transient) for both sides`() {
        listOf(
            SimpleCastState.Idle,
            SimpleCastState.CastingFull("com.a", AppType.NORMAL, DisplayConfig.NORMAL_DEFAULT),
            SimpleCastState.Off,
            SimpleCastState.Opening,
            SimpleCastState.Stopping,
            SimpleCastState.Error("x"),
        ).forEach { state ->
            assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.CAST, BubbleGesturePlanner.slotOutcome(state, ClusterSlotSide.LEFT), "$state LEFT")
            assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.CAST, BubbleGesturePlanner.slotOutcome(state, ClusterSlotSide.RIGHT), "$state RIGHT")
        }
    }
}
