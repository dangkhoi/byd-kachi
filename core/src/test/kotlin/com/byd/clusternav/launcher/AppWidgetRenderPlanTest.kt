package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T4 — luật dựng lại ô cho widget Android bên thứ ba.
 *
 * ## Vì sao bộ bài này tồn tại
 * Ô widget bên thứ ba do **nhà cung cấp tự vẽ** (đẩy RemoteViews sang). Nếu tầng vẽ coi nó như thẻ dựng tay thì trên
 * xe (trạng thái đổi **1 nhịp/giây**) `AppWidgetHostView` bị tháo và dựng lại **mỗi giây**: mất RemoteViews vừa nhận,
 * mất cú bấm giữa lúc chạm, và mỗi giây một lượt `createView` liên-tiến-trình.
 *
 * ⚠ Ca đó **KHÔNG quan sát được off-car** (không xe ⇒ `statusChanged` luôn `false`) — đúng cái bẫy đã làm phép đo
 * off-car của dự án kết luận sai về `w_photos`. Nên nó được khoá bằng **test** với `statusChanged = true` truyền tay,
 * chứ không bằng phép đo trên máy ảo.
 */
class AppWidgetRenderPlanTest {

    private fun aw(id: Int, provider: String = "com.x/.W") = SlotContent.AppWidget(id, provider)

    private fun state(vararg c: SlotContent) = WorkspaceState.of(LayoutPreset.TWO_COL, *c)

    private fun rebuilt(plan: WorkspaceRenderPlan): List<Int> = when (plan) {
        is WorkspaceRenderPlan.PerSlot -> plan.rebuild
        WorkspaceRenderPlan.RebuildAll -> error("không mong RebuildAll ở bài này")
    }

    /** ⚠ CHỐT CHÍNH: trạng thái xe đổi ⇒ ô widget bên thứ ba **KHÔNG** được dựng lại. */
    @Test
    fun `trang thai xe doi thi o widget ben thu ba KHONG dung lai`() {
        val s = state(aw(11), SlotContent.Empty)
        val plan = WorkspaceRenderPlanner.decide(s, s, builtSlotCount = 2, statusChanged = true, slotCount = 2)
        assertEquals(emptyList<Int>(), rebuilt(plan), "nhà cung cấp tự đẩy nội dung — dựng lại là tháo view đang nhận RemoteViews")
    }

    /** Đối chứng: thẻ dựng tay ĐỌC dữ liệu xe thì vẫn phải dựng lại (không vá quá tay). */
    @Test
    fun `doi chung - the dung tay doc du lieu xe VAN dung lai`() {
        val s = state(SlotContent.Widget("w_energy"), SlotContent.Empty)
        val plan = WorkspaceRenderPlanner.decide(s, s, builtSlotCount = 2, statusChanged = true, slotCount = 2)
        assertEquals(listOf(0), rebuilt(plan))
    }

    /** Năng lực nhúng đổi chỉ liên quan ô **App** — ô widget bên thứ ba không cần gắn bộ chiếu. */
    @Test
    fun `nang luc nhung doi khong dung lai o widget ben thu ba`() {
        val s = state(aw(11), SlotContent.Empty)
        val plan = WorkspaceRenderPlanner.decide(s, s, 2, statusChanged = false, embedChanged = true, slotCount = 2)
        assertEquals(emptyList<Int>(), rebuilt(plan))
    }

    @Test
    fun `dat widget moi vao o thi o do dung lai`() {
        val old = state(SlotContent.Empty, SlotContent.Empty)
        val new = state(aw(11), SlotContent.Empty)
        val plan = WorkspaceRenderPlanner.decide(old, new, 2, statusChanged = false, slotCount = 2)
        assertEquals(listOf(0), rebuilt(plan))
    }

    /**
     * ⚠ Ràng buộc lại **cùng một id** sang nhà cung cấp khác ⇒ phải dựng lại.
     *
     * Ca có thật: app cũ bị gỡ, người dùng chọn widget khác cho cùng ô. Nếu [WorkspaceRenderPlanner.sameContent] chỉ
     * so `widgetId` thì số id y nguyên ⇒ "ô không đổi" ⇒ ô vẫn hiện widget của app đã gỡ.
     */
    @Test
    fun `cung id nhung provider khac thi PHAI dung lai`() {
        val old = state(aw(11, "com.a/.W"), SlotContent.Empty)
        val new = state(aw(11, "com.b/.W"), SlotContent.Empty)
        assertFalse(WorkspaceRenderPlanner.sameContent(old.slots[0], new.slots[0]))
        assertEquals(listOf(0), rebuilt(WorkspaceRenderPlanner.decide(old, new, 2, statusChanged = false, slotCount = 2)))
    }

    @Test
    fun `cung id cung provider la cung noi dung`() {
        assertTrue(WorkspaceRenderPlanner.sameContent(aw(11, "com.a/.W"), aw(11, "com.a/.W")))
    }

    @Test
    fun `widget ben thu ba khac han the dung tay va o app`() {
        assertFalse(WorkspaceRenderPlanner.sameContent(aw(11), SlotContent.Widget("w_board")))
        assertFalse(WorkspaceRenderPlanner.sameContent(aw(11), SlotContent.App("com.x")))
        assertFalse(WorkspaceRenderPlanner.sameContent(aw(11), SlotContent.Empty))
    }

    /** Thay widget bên thứ ba bằng thẻ dựng tay (và ngược lại) ⇒ dựng lại. */
    @Test
    fun `doi qua lai voi the dung tay thi dung lai`() {
        val a = state(aw(11), SlotContent.Empty)
        val b = state(SlotContent.Widget("w_board"), SlotContent.Empty)
        assertEquals(listOf(0), rebuilt(WorkspaceRenderPlanner.decide(a, b, 2, statusChanged = false, slotCount = 2)))
        assertEquals(listOf(0), rebuilt(WorkspaceRenderPlanner.decide(b, a, 2, statusChanged = false, slotCount = 2)))
    }
}
