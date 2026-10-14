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

    // ─── 5. R31 (3.1.5, owner 14/10 "ok nút chiếu") — one-tap REPLACE ───────────────────────────────────────────────────
    // KHOÁ [ĐO xe 14/10]: GMaps trên cụm + Waze ở tiền cảnh ⇒ lần chạm đầu CHỈ trả GMaps, phải chạm lần hai mới chiếu Waze.
    // Gỡ R31 (tapOutcome bỏ qua foreground) ⇒ các bài REPLACE dưới đây đỏ.

    private val gmapsFull = SimpleCastState.CastingFull("com.maps.a", AppType.NORMAL, DisplayConfig.NORMAL_DEFAULT)

    @Test
    fun `R31 tap idle casts the foreground (unchanged)`() {
        assertEquals(BubbleTapOutcome.CAST_FULL, BubbleGesturePlanner.tapOutcome(SimpleCastState.Idle, "com.nav.b"))
        assertEquals(BubbleTapOutcome.CAST_FULL, BubbleGesturePlanner.tapOutcome(SimpleCastState.Idle, null))
    }

    @Test
    fun `R31 tap casting A with a different castable foreground B replaces in one tap`() {
        assertEquals(BubbleTapOutcome.REPLACE_FULL, BubbleGesturePlanner.tapOutcome(gmapsFull, "com.nav.b"))
    }

    @Test
    fun `R31 tap casting A with the same app A in front returns`() {
        assertEquals(BubbleTapOutcome.RETURN, BubbleGesturePlanner.tapOutcome(gmapsFull, "com.maps.a"))
    }

    @Test
    fun `R31 tap casting with home or nothing castable in front returns`() {
        // The dispatcher's launcher guard maps Kachi/home/launchers to null before the planner sees them.
        assertEquals(BubbleTapOutcome.RETURN, BubbleGesturePlanner.tapOutcome(gmapsFull, null))
    }

    @Test
    fun `R31 tap during a split still returns everything even with a new foreground`() {
        // CastFull from a split is ignored by the coordinator ⇒ a "replace" here would be a silent no-op.
        val split = SimpleCastState.CastingSplit(SlotState("com.left", DisplayConfig.NORMAL_DEFAULT), null)
        assertEquals(BubbleTapOutcome.RETURN, BubbleGesturePlanner.tapOutcome(split, "com.nav.b"))
    }

    @Test
    fun `R31 transient states stay PREPARING whatever is in front`() {
        listOf(SimpleCastState.Opening, SimpleCastState.Stopping, SimpleCastState.Off).forEach {
            assertEquals(BubbleTapOutcome.PREPARING, BubbleGesturePlanner.tapOutcome(it, "com.nav.b"), "$it")
        }
    }

    @Test
    fun `R31 the foreground is probed only for idle and full casts`() {
        assertTrue(BubbleGesturePlanner.tapNeedsForeground(SimpleCastState.Idle))
        assertTrue(BubbleGesturePlanner.tapNeedsForeground(gmapsFull))
        assertEquals(false, BubbleGesturePlanner.tapNeedsForeground(
            SimpleCastState.CastingSplit(SlotState("com.left", DisplayConfig.NORMAL_DEFAULT), null)))
        assertEquals(false, BubbleGesturePlanner.tapNeedsForeground(SimpleCastState.Opening))
    }

    private val leftOnlySplit = SimpleCastState.CastingSplit(
        left = SlotState("com.maps.a", DisplayConfig.NORMAL_DEFAULT), right = null,
    )
    private val bothSplit = SimpleCastState.CastingSplit(
        left = SlotState("com.maps.a", DisplayConfig.NORMAL_DEFAULT),
        right = SlotState("com.music.c", DisplayConfig.NORMAL_DEFAULT),
    )

    @Test
    fun `R31 slot occupied by A with a different castable foreground B replaces that half`() {
        assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.REPLACE,
            BubbleGesturePlanner.slotOutcome(leftOnlySplit, ClusterSlotSide.LEFT, "com.nav.b"))
        assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.REPLACE,
            BubbleGesturePlanner.slotOutcome(bothSplit, ClusterSlotSide.RIGHT, "com.nav.b"))
    }

    @Test
    fun `R31 slot occupied with the same app, home or the other half's app in front returns that half`() {
        assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.RETURN,
            BubbleGesturePlanner.slotOutcome(leftOnlySplit, ClusterSlotSide.LEFT, "com.maps.a"))
        assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.RETURN,
            BubbleGesturePlanner.slotOutcome(leftOnlySplit, ClusterSlotSide.LEFT, null))
        // The other half's app is already on the cluster — never move it across halves.
        assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.RETURN,
            BubbleGesturePlanner.slotOutcome(bothSplit, ClusterSlotSide.LEFT, "com.music.c"))
    }

    @Test
    fun `R31 a free slot still casts and decides without the foreground`() {
        assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.CAST,
            BubbleGesturePlanner.slotOutcome(leftOnlySplit, ClusterSlotSide.RIGHT, "com.nav.b"))
        assertEquals(BubbleGesturePlanner.BubbleSlotOutcome.CAST,
            BubbleGesturePlanner.slotOutcome(SimpleCastState.Idle, ClusterSlotSide.LEFT, "com.nav.b"))
        assertEquals(false, BubbleGesturePlanner.slotNeedsForegroundToDecide(leftOnlySplit, ClusterSlotSide.RIGHT))
        assertTrue(BubbleGesturePlanner.slotNeedsForegroundToDecide(leftOnlySplit, ClusterSlotSide.LEFT))
    }
}
