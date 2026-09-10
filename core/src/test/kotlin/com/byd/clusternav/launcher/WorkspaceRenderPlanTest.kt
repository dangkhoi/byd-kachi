package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá luật dựng-lại-ô của workspace (gói 1).
 *
 * Hai nhiệm vụ:
 *  1. **Tương đương hành vi cũ** — mọi ca đã chạy trước gói 1 phải ra QUYẾT ĐỊNH Y HỆT luật inline cũ trong
 *     `WorkspaceView.render()` (khoá thành quả cập-nhật-tăng-dần của P-bug1: thêm app ô khác KHÔNG dựng lại ô đang
 *     chạy). Bản tham chiếu `legacyDecide` dưới đây chép ĐÚNG luật cũ và được đối chiếu vét cạn.
 *  2. **Ca P-bug2** — cùng nội dung nhưng năng lực nhúng vừa đổi ⇒ ô App PHẢI dựng lại (để gắn bộ chiếu).
 */
class WorkspaceRenderPlanTest {

    private val app = SlotContent.App("com.a")
    private val app2 = SlotContent.App("com.b")
    private val w1 = SlotContent.Widget(listOf("w_energy"))
    private val w2 = SlotContent.Widget(listOf("w_energy", "w_pm25"))
    private val empty = SlotContent.Empty

    private fun state(preset: LayoutPreset, vararg slots: SlotContent): WorkspaceState =
        WorkspaceState(preset, (slots.toList() + List(WorkspaceState.SLOT_CAP) { empty }).take(WorkspaceState.SLOT_CAP))

    private fun rebuilt(plan: WorkspaceRenderPlan): List<Int> = when (plan) {
        WorkspaceRenderPlan.RebuildAll -> ALL
        is WorkspaceRenderPlan.PerSlot -> plan.rebuild
    }

    // ── 1. Tương đương hành vi CŨ ────────────────────────────────────────────────────────────────

    /** Bản chép NGUYÊN luật inline cũ (trước gói 1) — không có khái niệm "năng lực nhúng đổi". */
    private fun legacyDecide(
        old: WorkspaceState,
        new: WorkspaceState,
        builtSlotCount: Int,
        statusChanged: Boolean,
    ): List<Int> {
        if (old.preset != new.preset || builtSlotCount != new.preset.slotCount) return ALL
        val out = ArrayList<Int>()
        for (i in 0 until new.preset.slotCount) {
            val oc = old.slots.getOrElse(i) { SlotContent.Empty }
            val nc = new.slots.getOrElse(i) { SlotContent.Empty }
            val same = when {
                oc is SlotContent.App && nc is SlotContent.App -> oc.pkg == nc.pkg
                oc is SlotContent.Widget && nc is SlotContent.Widget -> oc.ids == nc.ids
                oc is SlotContent.Empty && nc is SlotContent.Empty -> true
                else -> false
            }
            if (!same || (statusChanged && nc is SlotContent.Widget)) out.add(i)
        }
        return out
    }

    @Test
    fun `quyet dinh khop LUAT CU tren moi to hop noi dung x preset x statusChanged`() {
        val contents = listOf(empty, app, app2, w1, w2)
        var checked = 0
        for (preset in LayoutPreset.values()) {
            for (a in contents) for (b in contents) for (c in contents) {
                val old = state(preset, a, b, c)
                for (x in contents) for (y in contents) {
                    val new = state(preset, x, y, c)
                    for (statusChanged in listOf(false, true)) {
                        val expected = legacyDecide(old, new, preset.slotCount, statusChanged)
                        val actual = rebuilt(
                            WorkspaceRenderPlanner.decide(old, new, preset.slotCount, statusChanged, embedChanged = false),
                        )
                        assertEquals(expected, actual, "preset=$preset old=[$a,$b] new=[$x,$y] status=$statusChanged")
                        checked++
                    }
                }
            }
        }
        assertTrue(checked > 1000, "phải duyệt đủ tổ hợp, đếm được $checked")
    }

    @Test
    fun `doi preset hoac lech so o dang dung thi dung lai TAT CA`() {
        val a = state(LayoutPreset.THREE, app, w1, w1)
        val b = state(LayoutPreset.QUAD, app, w1, w1)
        assertEquals(WorkspaceRenderPlan.RebuildAll, WorkspaceRenderPlanner.decide(a, b, 3, false))
        // cùng preset nhưng view đang dựng 2 ô trong khi bố cục cần 3 ⇒ vẫn phải dựng lại tất cả
        assertEquals(WorkspaceRenderPlan.RebuildAll, WorkspaceRenderPlanner.decide(a, a, 2, false))
    }

    @Test
    fun `khong doi gi thi KHONG dung lai o nao`() {
        val s = state(LayoutPreset.THREE, app, w1, empty)
        assertEquals(emptyList<Int>(), rebuilt(WorkspaceRenderPlanner.decide(s, s, 3, false)))
    }

    @Test
    fun `trang thai xe doi chi dung lai o WIDGET — o App dang chay KHONG bi dung lai (P-bug1)`() {
        val s = state(LayoutPreset.THREE, app, w1, empty)
        assertEquals(listOf(1), rebuilt(WorkspaceRenderPlanner.decide(s, s, 3, statusChanged = true)))
    }

    // ── 2. Ca P-bug2 ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `P-bug2 — cung noi dung nhung nang luc nhung DOI thi o App phai dung lai`() {
        val s = state(LayoutPreset.THREE, app, w1, empty)
        // Trước gói 1: quyết định là "không dựng lại ô nào" ⇒ bộ chiếu không bao giờ được gắn.
        assertEquals(emptyList<Int>(), rebuilt(WorkspaceRenderPlanner.decide(s, s, 3, false, embedChanged = false)))
        // Sau gói 1: ô App (index 0) phải dựng lại; ô widget/trống KHÔNG bị đụng (không làm mới oan).
        assertEquals(listOf(0), rebuilt(WorkspaceRenderPlanner.decide(s, s, 3, false, embedChanged = true)))
    }

    @Test
    fun `P-bug2 — nhieu o App thi dung lai HET cac o App do`() {
        val s = state(LayoutPreset.QUAD, app, app2, w1, empty)
        assertEquals(listOf(0, 1), rebuilt(WorkspaceRenderPlanner.decide(s, s, 4, false, embedChanged = true)))
    }

    @Test
    fun `P-bug2 — khong co o App nao thi nang luc nhung doi KHONG dung lai gi`() {
        val s = state(LayoutPreset.THREE, w1, w2, empty)
        assertEquals(emptyList<Int>(), rebuilt(WorkspaceRenderPlanner.decide(s, s, 3, false, embedChanged = true)))
    }

    @Test
    fun `P-bug2 — nang luc nhung doi CONG voi trang thai xe doi thi gop ca hai ly do, khong trung lap`() {
        val s = state(LayoutPreset.QUAD, app, w1, app2, w2)
        val out = rebuilt(WorkspaceRenderPlanner.decide(s, s, 4, statusChanged = true, embedChanged = true))
        assertEquals(listOf(0, 1, 2, 3), out)
        assertEquals(out.distinct(), out, "không được có chỉ số trùng")
    }

    @Test
    fun `sameContent — App theo goi, Widget theo danh sach id, Empty bang Empty`() {
        assertTrue(WorkspaceRenderPlanner.sameContent(app, SlotContent.App("com.a")))
        assertFalse(WorkspaceRenderPlanner.sameContent(app, app2))
        assertTrue(WorkspaceRenderPlanner.sameContent(w2, SlotContent.Widget(listOf("w_energy", "w_pm25"))))
        assertFalse(WorkspaceRenderPlanner.sameContent(w1, w2))
        assertTrue(WorkspaceRenderPlanner.sameContent(empty, SlotContent.Empty))
        assertFalse(WorkspaceRenderPlanner.sameContent(empty, app))
    }

    private companion object {
        /** Dấu hiệu "dựng lại tất cả" khi so sánh với bản tham chiếu. */
        val ALL = listOf(-1)
    }
}
