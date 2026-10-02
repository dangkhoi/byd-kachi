package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A2 (spec shortcuts-autostart §4.4.5) — danh sách `swap` của [WorkspaceRenderPlanner]: chỉ lượt ĐẶT TẠM App(A) →
 * App(B) mới được đổi tại chỗ (giữ màn ảo); mọi thứ khác đi đúng đường dựng lại hôm nay (R1.7, CLAUDE.md §6). Tham số
 * mặc định rỗng ⇒ bài tương đương 1000+ tổ hợp (`WorkspaceRenderPlanTest`) không đổi.
 */
class WorkspaceRenderPlanSwapTest {

    private fun ws(vararg s: SlotContent) =
        WorkspaceState(LayoutPreset.QUAD, List(WorkspaceState.SLOT_CAP) { s.getOrElse(it) { SlotContent.Empty } })

    private val a = SlotContent.App("vn.vietmap.live")
    private val b = SlotContent.App("com.google.android.deskclock")
    private val w = SlotContent.Widget("w_media")

    private fun decide(old: WorkspaceState, new: WorkspaceState, swap: Set<Int>, embed: Boolean = false) =
        WorkspaceRenderPlanner.decide(old, new, builtSlotCount = 4, statusChanged = false, embedChanged = embed, slotCount = 4, swap = swap)

    @Test
    fun `dat tam App A thanh App B tren o co moc moi - doi tai cho, khong dung lai`() {
        assertEquals(WorkspaceRenderPlan.PerSlot(emptyList(), listOf(0)), decide(ws(a), ws(b), setOf(0)))
    }

    @Test
    fun `khong co moc (duong LUU) thi van dung lai nhu hom nay`() {
        assertEquals(WorkspaceRenderPlan.PerSlot(listOf(0)), decide(ws(a), ws(b), emptySet()))
    }

    @Test
    fun `co moc nhung khong phai App sang App khac goi thi dung lai`() {
        assertEquals(WorkspaceRenderPlan.PerSlot(listOf(0)), decide(ws(w), ws(b), setOf(0)), "widget → app: không có màn ảo để giữ")
        assertEquals(WorkspaceRenderPlan.PerSlot(listOf(0)), decide(ws(a), ws(SlotContent.Empty), setOf(0)))
        assertEquals(WorkspaceRenderPlan.PerSlot(emptyList()), decide(ws(a), ws(a), setOf(0)), "cùng gói ⇒ không làm gì")
    }

    @Test
    fun `kenh nhung vua doi thi o App phai dung lai de gan bo chieu, khong doi tai cho`() {
        assertEquals(WorkspaceRenderPlan.PerSlot(listOf(0)), decide(ws(a), ws(b), setOf(0), embed = true))
    }

    @Test
    fun `doi bo cuc thi dung lai tat ca, moc khong cuu duoc`() {
        val old = ws(a)
        val new = ws(b).withPreset(LayoutPreset.TWO_COL)
        assertEquals(
            WorkspaceRenderPlan.RebuildAll,
            WorkspaceRenderPlanner.decide(old, new, 4, false, false, slotCount = 2, swap = setOf(0)),
        )
    }

    @Test
    fun `ung vien la o co moc MOI o luot nay - moc mat (doi ho so) khong tinh`() {
        assertEquals(setOf(1), WorkspaceRenderPlanner.swapCandidates(mapOf(0 to 3L), mapOf(0 to 3L, 1 to 4L)))
        assertEquals(setOf(0), WorkspaceRenderPlanner.swapCandidates(mapOf(0 to 3L), mapOf(0 to 5L)))
        assertEquals(emptySet<Int>(), WorkspaceRenderPlanner.swapCandidates(mapOf(0 to 3L), emptyMap()))
    }
}
