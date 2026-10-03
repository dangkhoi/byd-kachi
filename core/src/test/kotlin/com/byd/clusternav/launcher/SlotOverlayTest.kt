package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * C2 (spec shortcuts-autostart §2.2 · §4.4.4) — lớp ĐẶT TẠM: một app một ô qua CẢ HAI lớp, không chạm lớp LƯU, mất
 * khi đường LƯU sửa ô đó. Đính chính owner 01/10: *"đưa app vào khung trong quá trình chạy là tạm thời, không lưu"*.
 */
class SlotOverlayTest {

    private val a = "vn.vietmap.live"
    private val b = "com.google.android.deskclock"
    private val c = "com.google.android.youtube"

    private fun saved(vararg s: SlotContent) =
        WorkspaceState(LayoutPreset.QUAD, List(WorkspaceState.SLOT_CAP) { s.getOrElse(it) { SlotContent.Empty } })

    @Test
    fun `dat tam de len o, lop luu khong doi`() {
        val ws = saved(SlotContent.App(a), SlotContent.Widget("w_media"))
        val st = HomeUiState(workspace = ws).let { it.copy(overlay = it.overlay.place(0, b)) }
        assertEquals(SlotContent.App(b), st.effectiveWorkspace.slots[0])
        assertEquals(SlotContent.App(a), st.workspace.slots[0], "lớp LƯU (thứ persist() ghi) không đổi")
        assertSame(ws, st.workspace)
    }

    @Test
    fun `mot app mot o qua ca hai lop - app dat tam bien khoi o luu dang giu no`() {
        val ws = saved(SlotContent.App(a), SlotContent.App(b))
        val eff = SlotOverlay.EMPTY.place(0, b).applyTo(ws)
        assertEquals(SlotContent.App(b), eff.slots[0])
        assertEquals(SlotContent.Empty, eff.slots[1], "b không được hiện ở hai ô")
    }

    @Test
    fun `cung goi dat lai o khac thi muc cu bi go`() {
        val o = SlotOverlay.EMPTY.place(0, c).place(2, c)
        assertEquals(mapOf(2 to c), o.entries)
    }

    @Test
    fun `duong LUU go muc tam o bi cham VA muc tam dang giu goi vua luu`() {
        val o = SlotOverlay.EMPTY.place(0, b).place(3, c)
        assertEquals(mapOf(3 to c), o.afterSave(listOf(0)).entries)
        // Chọn c cho ô 1 bằng ngăn kéo trong khi c còn tạm ở ô 3 ⇒ mục tạm của c phải đi, nếu không ô 1 hiện trống.
        val after = o.afterSave(listOf(1), listOf(c))
        assertEquals(mapOf(0 to b), after.entries)
        val ws = saved(SlotContent.App(a), SlotContent.App(c))
        assertEquals(SlotContent.App(c), after.applyTo(ws).slots[1])
    }

    @Test
    fun `khong co muc tam thi effective chinh la lop luu`() {
        val ws = saved(SlotContent.App(a))
        assertSame(ws, HomeUiState(workspace = ws).effectiveWorkspace)
        assertTrue(SlotOverlay.EMPTY.afterSave(listOf(0), listOf(a)).isEmpty)
    }

    /**
     * FIX286 · R-SC1 — lối tắt *Ô n* đặt TẠM đè lên ô widget (owner 03/10 *"đạp widget ra để thay app vào đấy"*). Ba điều
     * phải giữ: (1) id widget bên thứ ba KHÔNG thành rác — `AppWidgetIds.orphaned` đọc lớp LƯU, nên lượt render đặt tạm
     * không gọi `deleteAppWidgetId` (id do nền tảng cấp, xoá rồi là mất vĩnh viễn); (2) widget của Kachi (trình chiếu ảnh)
     * vẫn nằm nguyên trong lớp LƯU; (3) bỏ lớp tạm (khởi động lại = state mới · đổi hồ sơ = `reload`) ⇒ widget hiện lại.
     */
    @Test
    fun `dat tam de len o widget - id widget khong thanh rac, lop luu giu widget, bo lop tam thi widget ve`() {
        val aw = SlotContent.AppWidget(651, "com.google.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider")
        val ws = saved(SlotContent.Widget("w_photos"), aw)
        val before = HomeUiState(workspace = ws)
        val after = before.copy(overlay = before.overlay.place(0, b).place(1, c))
        assertEquals(SlotContent.App(b), after.effectiveWorkspace.slots[0], "ô trình chiếu ảnh hiện app đặt tạm")
        assertEquals(SlotContent.App(c), after.effectiveWorkspace.slots[1], "ô widget bên thứ ba hiện app đặt tạm")
        assertEquals(emptySet<Int>(), AppWidgetIds.orphaned(before, after), "đặt tạm KHÔNG được thu hồi id 651")
        assertEquals(setOf(651), AppWidgetIds.used(after))
        assertEquals(ws, after.workspace, "lớp LƯU (persist ghi) giữ nguyên hai widget")
        val restarted = after.copy(overlay = SlotOverlay.EMPTY)
        assertEquals(ws.slots, restarted.effectiveWorkspace.slots, "bỏ lớp tạm ⇒ cả hai widget về đúng ô")
        assertEquals(emptySet<Int>(), AppWidgetIds.orphaned(after, restarted))
    }

    @Test
    fun `o ngoai tran va goi rong bi bo qua, drop tra o ve lop luu`() {
        assertTrue(SlotOverlay.EMPTY.place(WorkspaceState.SLOT_CAP, a).isEmpty)
        assertTrue(SlotOverlay.EMPTY.place(0, " ").isEmpty)
        val o = SlotOverlay.EMPTY.place(1, b)
        assertTrue(o.holds(1))
        assertTrue(o.drop(1).isEmpty)
    }
}
