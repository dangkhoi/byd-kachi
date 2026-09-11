package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * P9 bước 3 — bộ quyết-định-dựng-lại phải biết số ô **THỰC TẾ**, không phải số ô của bố cục sẵn.
 *
 * ## Rủi ro mà nhóm test này khoá
 * Bố cục tự vẽ có thể có số khung **khác** bố cục sẵn. Nếu bộ quyết định vẫn so số view đang dựng với
 * `preset.slotCount` thì với bố cục 6 khung (bố cục sẵn 3 ô) nó thấy `6 != 3` ⇒ trả **RebuildAll** ở **MỌI** lần
 * render. Trên xe trạng thái đổi **2 nhịp/giây** ⇒ ô bị tháo/gắn 2 lần mỗi giây ⇒ **app đang chiếu trong ô bị
 * nhả/gắn liên tục** và **mất cú bấm** của người dùng. Đây đúng là loại lỗi P-bug1/R3 đã tốn nhiều phiên để sửa.
 */
class WorkspaceRenderPlanSlotCountTest {

    private val empty = SlotContent.Empty
    private fun st(preset: LayoutPreset, vararg slots: SlotContent) =
        WorkspaceState(preset, (slots.toList() + List(WorkspaceState.SLOT_CAP) { empty }).take(WorkspaceState.SLOT_CAP))

    // ── Chính cái rủi ro ────────────────────────────────────────────────────────────────────────

    @Test
    fun `bo cuc tu ve nhieu o hon bo cuc san KHONG duoc dung lai lien tuc`() {
        val s = st(LayoutPreset.THREE, SlotContent.App("com.a"), SlotContent.App("com.b"), empty, empty)
        // 6 view ô đang dựng (bố cục tự vẽ 6 khung), bố cục sẵn chỉ 3 ô.
        val plan = WorkspaceRenderPlanner.decide(s, s, builtSlotCount = 6, statusChanged = true, slotCount = 6)
        assertTrue(plan is WorkspaceRenderPlan.PerSlot,
            "số ô ĐÚNG với số view đang dựng ⇒ không được dựng lại tất cả")
        assertEquals(emptyList<Int>(), (plan as WorkspaceRenderPlan.PerSlot).rebuild,
            "không có gì đổi (ô App không quan tâm trạng thái xe) ⇒ không dựng lại ô nào")
    }

    @Test
    fun `hanh vi CU la sai voi bo cuc tu ve - chung minh rui ro co that`() {
        val s = st(LayoutPreset.THREE, SlotContent.App("com.a"))
        // Gọi KHÔNG truyền số ô thực tế = hành vi cũ: so 6 view với 3 ô của bố cục sẵn.
        val old = WorkspaceRenderPlanner.decide(s, s, builtSlotCount = 6, statusChanged = false)
        assertEquals(WorkspaceRenderPlan.RebuildAll, old,
            "đây chính là rủi ro: hành vi cũ dựng lại TẤT CẢ vì tưởng số ô lệch")
        // Truyền số ô thực tế thì hết.
        val fixed = WorkspaceRenderPlanner.decide(s, s, builtSlotCount = 6, statusChanged = false, slotCount = 6)
        assertNotEquals(WorkspaceRenderPlan.RebuildAll, fixed)
    }

    @Test
    fun `van dung lai tat ca khi so view THAT SU lech so o`() {
        // Đây là ca ĐÚNG phải dựng lại: vừa đổi bố cục, số view chưa khớp số ô mới.
        val s = st(LayoutPreset.QUAD)
        assertEquals(
            WorkspaceRenderPlan.RebuildAll,
            WorkspaceRenderPlanner.decide(s, s, builtSlotCount = 4, statusChanged = false, slotCount = 6),
        )
    }

    @Test
    fun `o thu 5 va 6 duoc xet, khong bi bo qua`() {
        val a = st(LayoutPreset.THREE, empty, empty, empty, empty)
        val slots = a.slots.toMutableList()
        // Ô thứ 5 (index 4) chỉ tồn tại khi trần ô > 4.
        if (WorkspaceState.SLOT_CAP > 4) {
            slots[4] = SlotContent.Widget(listOf("w_clock"))
            val b = WorkspaceState(LayoutPreset.THREE, slots)
            val plan = WorkspaceRenderPlanner.decide(a, b, builtSlotCount = 6, statusChanged = false, slotCount = 6)
            assertEquals(listOf(4), (plan as WorkspaceRenderPlan.PerSlot).rebuild,
                "đổi nội dung ô thứ 5 phải được nhận ra — bỏ qua thì ô đó không bao giờ vẽ lại")
        }
    }

    // ── [SOÁT P1-1] Widget tự lo nội dung không được dựng lại theo nhịp trạng thái xe ────────────

    @Test
    fun `widget trinh chieu anh KHONG bi dung lai theo nhip trang thai xe`() {
        // Trên xe trạng thái đổi 1 nhịp/giây. Nếu ô trình chiếu bị dựng lại theo nhịp đó thì trạng thái quay vòng bị
        // ĐẶT LẠI mỗi giây ⇒ ảnh đứng mãi ở một tấm; và mỗi giây một lượt đọc tệp + giải mã ảnh trên thread chính.
        // ⚠ Ca này KHÔNG quan sát được off-car (không xe ⇒ trạng thái luôn rỗng ⇒ "trạng thái đổi" luôn false).
        val s = st(LayoutPreset.ONE, SlotContent.Widget(listOf("w_photos")))
        val plan = WorkspaceRenderPlanner.decide(s, s, builtSlotCount = 1, statusChanged = true, slotCount = 1)
        assertEquals(emptyList<Int>(), (plan as WorkspaceRenderPlan.PerSlot).rebuild,
            "ô trình chiếu ảnh tự lo nội dung, trạng thái xe đổi KHÔNG có gì để làm mới")
    }

    @Test
    fun `widget doc du lieu xe thi VAN dung lai theo nhip`() {
        // Chặn cách vá quá tay: đừng miễn trừ hết mọi widget.
        val s = st(LayoutPreset.ONE, SlotContent.Widget(listOf("w_energy")))
        val plan = WorkspaceRenderPlanner.decide(s, s, builtSlotCount = 1, statusChanged = true, slotCount = 1)
        assertEquals(listOf(0), (plan as WorkspaceRenderPlan.PerSlot).rebuild,
            "ô đọc năng lượng PHẢI dựng lại khi trạng thái xe đổi, không thì số liệu đứng yên")
    }

    @Test
    fun `o tron trinh chieu voi widget doc thi VAN dung lai`() {
        // Ô có thể chứa nhiều widget. Chỉ cần MỘT cái đọc dữ liệu xe là phải làm mới.
        val s = st(LayoutPreset.ONE, SlotContent.Widget(listOf("w_photos", "w_energy")))
        val plan = WorkspaceRenderPlanner.decide(s, s, builtSlotCount = 1, statusChanged = true, slotCount = 1)
        assertEquals(listOf(0), (plan as WorkspaceRenderPlan.PerSlot).rebuild)
    }

    // ── Mặc định phải giữ NGUYÊN hành vi cũ ─────────────────────────────────────────────────────

    @Test
    fun `khong truyen so o thi hanh vi GIU NGUYEN nhu truoc`() {
        // Bộ quyết định này đang bị test tương-đương-hành-vi hơn 1000 tổ hợp khoá. Tham số mới phải có mặc định
        // tái tạo ĐÚNG hành vi cũ, nếu không là xáo trộn đúng chỗ nguy hiểm nhất.
        LayoutPreset.values().forEach { preset ->
            listOf(0, 1, 2, 3, 4, 5, 6).forEach { built ->
                listOf(true, false).forEach { status ->
                    val s = st(preset, SlotContent.Widget(listOf("w_energy")), SlotContent.App("com.x"))
                    assertEquals(
                        WorkspaceRenderPlanner.decide(s, s, built, status, slotCount = preset.slotCount),
                        WorkspaceRenderPlanner.decide(s, s, built, status),
                        "mặc định phải bằng 'số ô của bố cục sẵn' ở $preset built=$built status=$status",
                    )
                }
            }
        }
    }
}
