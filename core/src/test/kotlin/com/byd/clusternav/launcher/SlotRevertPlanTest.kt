package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotRevertPlan.Event
import com.byd.clusternav.launcher.SlotRevertPlan.Next
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L6 · luật hoàn ô ([SlotRevertPlan]) — ĐỦ bảng 5 nội dung LƯU × 4 nội dung HIỆN × 4 sự kiện × 3 dạng gói = 240 ô, cộng
 * các ca owner 03/10 dựng lại trên đúng hai lớp của [HomeUiState] (LƯU · lớp tạm).
 *
 * Mỗi ca owner khoá một hướng sai có thật:
 *  - (a) app LƯU của ô chết mà ô còn icon + "chạm để mở lại" (2.86, ảnh owner) ⇒ phải trong suốt;
 *  - (b) widget lốp + lối tắt đặt Maps tạm + tắt Maps ⇒ "đen thui 1 mảng" (2.86) ⇒ widget lốp phải về;
 *  - không bao giờ ghi lớp LƯU (owner 01/10: đặt lúc chạy là tạm) — khởi động lại là ô về hồ sơ;
 *  - id widget bên thứ ba không thành rác khi ô widget bị tắt tạm (xoá rồi là mất vĩnh viễn).
 *
 * L8 (owner 03/10, mở khoá D-L6-1) — ĐỔI GHIM có lý do: *chạy nền* nay làm được ở MỌI ô app (lớp che của Kachi trong màn ảo
 * ô, `BehindHomeSequence.evictCovered`) và `APP_BACKGROUND` chỉ báo SAU KHI app đã rời màn ảo ⇒ ô đi đúng đường của app vừa
 * đóng. Bảng cũ (`Keep` khi LƯU = chính app / widget / trống; `ShowSaved(swapInPlace = true)` khi có app LƯU khác) là luật
 * của đường đổi-tại-chỗ L6 — đường đó không còn sinh từ bảng này (một đường chung, CLAUDE.md §7).
 */
class SlotRevertPlanTest {

    private val maps = "com.google.android.apps.maps"
    private val yt = "com.google.android.youtube"
    private val tyres = SlotContent.Widget("w_tyres")
    private val clock = SlotContent.AppWidget(651, "com.google.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider")

    private fun ws(vararg s: SlotContent) =
        WorkspaceState(LayoutPreset.QUAD, List(WorkspaceState.SLOT_CAP) { s.getOrElse(it) { SlotContent.Empty } })

    /** Bảng mong đợi viết TAY theo lời owner + luật điều phối (không suy từ hàm đang thử). */
    private fun expected(saved: SlotContent, shown: SlotContent, event: Event, pkg: String?): Next {
        if (event == Event.WIDGET_CLOSED) {
            return if (shown is SlotContent.Widget || shown is SlotContent.AppWidget) Next.Clear else Next.Keep
        }
        if (shown !is SlotContent.App) return Next.Keep
        if (pkg != null && pkg != shown.pkg) return Next.Keep
        return when {
            saved == shown -> Next.Clear                     // (a) app LƯU của ô (chết / tắt / ra nền) ⇒ trong suốt
            else -> Next.ShowSaved                           // app LƯU khác mở lại · widget về · LƯU trống = trong suốt
        }
    }

    @Test
    fun `du bang 240 o`() {
        val saveds = listOf(SlotContent.Empty, tyres, clock, SlotContent.App(maps), SlotContent.App(yt))
        val showns = listOf(SlotContent.Empty, tyres, clock, SlotContent.App(maps))
        val pkgs = listOf(null, maps, "com.other")
        var cells = 0
        for (saved in saveds) for (shown in showns) for (event in Event.values()) for (pkg in pkgs) {
            assertEquals(expected(saved, shown, event, pkg), SlotRevertPlan.next(saved, shown, event, pkg), "$saved · $shown · $event · $pkg")
            cells++
        }
        assertEquals(5 * 4 * 4 * 3, cells, "thêm sự kiện / loại ô ⇒ sửa bảng tay ở trên")
    }

    @Test
    fun `a - app LUU cua o chet thi o trong suot, khong icon, lop LUU giu nguyen`() {
        val st = HomeUiState(workspace = ws(SlotContent.App(maps), tyres))
        val next = SlotRevertPlan.next(st.workspace.slots[0], st.effectiveWorkspace.slots[0], Event.APP_DIED, maps)
        assertEquals(Next.Clear, next)
        val after = st.copy(overlay = SlotRevertPlan.overlayAfter(st.overlay, 0, next))
        assertEquals(SlotContent.Empty, after.effectiveWorkspace.slots[0], "ô hiện như khung trống (trong suốt + ⇄)")
        assertSame(st.workspace, after.workspace, "persist() ghi lớp LƯU — không được đổi")
        assertEquals(SlotContent.App(maps), after.copy(overlay = SlotOverlay.EMPTY).effectiveWorkspace.slots[0],
            "khởi động lại / đổi hồ sơ (lớp tạm mất) ⇒ ô về hồ sơ, app mở lại như lúc khởi động")
    }

    @Test
    fun `b - widget lop, lo tat dat Maps tam, tat Maps thi widget lop ve`() {
        val saved = HomeUiState(workspace = ws(tyres))
        val temp = saved.copy(overlay = saved.overlay.place(0, maps))
        assertEquals(SlotContent.App(maps), temp.effectiveWorkspace.slots[0])
        listOf(Event.APP_DIED, Event.APP_CLOSED, Event.APP_BACKGROUND).forEach { ev ->
            val next = SlotRevertPlan.next(temp.workspace.slots[0], temp.effectiveWorkspace.slots[0], ev, maps)
            assertEquals(Next.ShowSaved, next, "$ev")
            val after = temp.copy(overlay = SlotRevertPlan.overlayAfter(temp.overlay, 0, next))
            assertEquals(tyres, after.effectiveWorkspace.slots[0], "$ev ⇒ widget lốp về, không 'đen thui'")
            assertTrue(after.overlay.isEmpty)
        }
    }

    @Test
    fun `b - app LUU khac thi mo lai app LUU, chay nen cung vay sau khi app tam da ra sau man nha`() {
        val saved = HomeUiState(workspace = ws(SlotContent.App(yt)))
        val temp = saved.copy(overlay = saved.overlay.place(0, maps))
        assertEquals(Next.ShowSaved, SlotRevertPlan.next(temp.workspace.slots[0], temp.effectiveWorkspace.slots[0], Event.APP_CLOSED, maps))
        val bg = SlotRevertPlan.next(temp.workspace.slots[0], temp.effectiveWorkspace.slots[0], Event.APP_BACKGROUND, maps)
        assertEquals(Next.ShowSaved, bg, "L8: Maps đã ra sau màn nhà (lớp che) ⇒ ô dựng lại, YouTube mở lại như lúc khởi động")
        assertEquals(SlotContent.App(yt), temp.copy(overlay = SlotRevertPlan.overlayAfter(temp.overlay, 0, bg)).effectiveWorkspace.slots[0])
    }

    /**
     * L8 — ca owner 03/10 phổ biến nhất (D-L6-1 cũ: KHÔNG làm được): ô LƯU Maps, bấm *chạy nền* ⇒ Maps sau màn nhà, ô TRONG
     * SUỐT (*"để UI trong suốt thấy nền background cho đẹp"*); khởi động lại ⇒ Maps về ô (K8 vì Maps mang dấu sau màn nhà).
     */
    @Test
    fun `L8 - o LUU chinh app, chay nen thi o trong suot, khoi dong lai thi app ve o`() {
        val st = HomeUiState(workspace = ws(SlotContent.App(maps), tyres))
        val next = SlotRevertPlan.next(st.workspace.slots[0], st.effectiveWorkspace.slots[0], Event.APP_BACKGROUND, maps)
        assertEquals(Next.Clear, next)
        val after = st.copy(overlay = SlotRevertPlan.overlayAfter(st.overlay, 0, next))
        assertEquals(SlotContent.Empty, after.effectiveWorkspace.slots[0])
        assertSame(st.workspace, after.workspace, "không ghi hồ sơ")
        assertEquals(SlotContent.App(maps), after.copy(overlay = SlotOverlay.EMPTY).effectiveWorkspace.slots[0])
        listOf(SlotContent.Empty to SlotContent.Empty, tyres to tyres).forEach { (savedSlot, back) ->
            val t = HomeUiState(workspace = ws(savedSlot)).let { it.copy(overlay = it.overlay.place(0, maps)) }
            val n = SlotRevertPlan.next(t.workspace.slots[0], t.effectiveWorkspace.slots[0], Event.APP_BACKGROUND, maps)
            assertEquals(Next.ShowSaved, n, "LƯU=$savedSlot")
            assertEquals(back, t.copy(overlay = SlotRevertPlan.overlayAfter(t.overlay, 0, n)).effectiveWorkspace.slots[0])
        }
    }

    @Test
    fun `o LUU trong ma app dat tam bi tat thi o trong suot`() {
        val st = HomeUiState(workspace = ws())
        val temp = st.copy(overlay = st.overlay.place(2, maps))
        val next = SlotRevertPlan.next(temp.workspace.slots[2], temp.effectiveWorkspace.slots[2], Event.APP_DIED, maps)
        assertEquals(Next.ShowSaved, next)
        assertEquals(SlotContent.Empty, temp.copy(overlay = SlotRevertPlan.overlayAfter(temp.overlay, 2, next)).effectiveWorkspace.slots[2])
    }

    @Test
    fun `tat o widget - trong suot tam, id widget ben thu ba khong thanh rac, khoi dong lai thi widget ve`() {
        val st = HomeUiState(workspace = ws(tyres, clock))
        val n0 = SlotRevertPlan.next(st.workspace.slots[0], st.effectiveWorkspace.slots[0], Event.WIDGET_CLOSED)
        val n1 = SlotRevertPlan.next(st.workspace.slots[1], st.effectiveWorkspace.slots[1], Event.WIDGET_CLOSED)
        assertEquals(Next.Clear, n0); assertEquals(Next.Clear, n1)
        val after = st.copy(overlay = SlotRevertPlan.overlayAfter(SlotRevertPlan.overlayAfter(st.overlay, 0, n0), 1, n1))
        assertEquals(listOf(SlotContent.Empty, SlotContent.Empty), after.effectiveWorkspace.slots.take(2))
        assertEquals(emptySet<Int>(), AppWidgetIds.orphaned(st, after), "tắt tạm KHÔNG được thu hồi id 651")
        assertEquals(setOf(651), AppWidgetIds.used(after))
        assertEquals(st.workspace.slots, after.copy(overlay = SlotOverlay.EMPTY).effectiveWorkspace.slots, "khởi động lại ⇒ cả hai widget về")
    }

    @Test
    fun `su kien cu cua app da roi o thi khong doi gi`() {
        val st = HomeUiState(workspace = ws(tyres)).let { it.copy(overlay = it.overlay.place(0, yt)) }
        assertEquals(Next.Keep, SlotRevertPlan.next(st.workspace.slots[0], st.effectiveWorkspace.slots[0], Event.APP_DIED, maps),
            "nhịp đo của Maps về muộn sau khi ô đã đổi sang YouTube ⇒ không được đổi ô")
        assertEquals(Next.Keep, SlotRevertPlan.next(SlotContent.Empty, SlotContent.Empty, Event.APP_DIED, maps), "lần hai (đã trong suốt) ⇒ không đổi")
    }

    @Test
    fun `o trong suot tam - chon noi dung bang duong LUU hay dat tam thi thoi trong suot`() {
        val o = SlotOverlay.EMPTY.clear(0)
        assertTrue(0 in o.cleared && !o.isEmpty)
        assertTrue(o.afterSave(listOf(0)).isEmpty, "⇄ / ngăn kéo ghi ô 0 ⇒ thôi trong suốt")
        assertEquals(SlotOverlay(mapOf(0 to maps)), o.place(0, maps), "lối tắt Ô 1 / giọng nói đặt app ⇒ app hiện ra")
        assertTrue(o.drop(0).isEmpty)
        assertEquals(SlotOverlay(cleared = setOf(0)), SlotOverlay.EMPTY.place(0, yt).clear(0), "app tạm bị bỏ khi ô trong suốt")
        assertSame(o, o.clear(0), "trong suốt rồi ⇒ không dựng đối tượng mới")
        assertSame(SlotOverlay.EMPTY, SlotOverlay.EMPTY.clear(WorkspaceState.SLOT_CAP), "ô ngoài trần bị bỏ qua")
    }

    @Test
    fun `Keep khong doi lop tam`() {
        val o = SlotOverlay.EMPTY.place(1, maps)
        assertSame(o, SlotRevertPlan.overlayAfter(o, 1, Next.Keep))
    }
}
