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

    @Test
    fun `o ngoai tran va goi rong bi bo qua, drop tra o ve lop luu`() {
        assertTrue(SlotOverlay.EMPTY.place(WorkspaceState.SLOT_CAP, a).isEmpty)
        assertTrue(SlotOverlay.EMPTY.place(0, " ").isEmpty)
        val o = SlotOverlay.EMPTY.place(1, b)
        assertTrue(o.holds(1))
        assertTrue(o.drop(1).isEmpty)
    }
}
