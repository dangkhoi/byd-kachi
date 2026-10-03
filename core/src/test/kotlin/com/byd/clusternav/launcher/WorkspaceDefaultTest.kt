package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * FIX286 · ES5 — bố cục toàn ô trống GIỮ trống (owner 03/10); mặc định 3 widget chỉ cho hồ sơ chưa từng lưu.
 *
 * Khoá lỗi hiện trường: bản ≤ 2.85 nạp lại 3 widget ở mọi lượt `load()` khi mọi ô trống ⇒ bố cục "vẽ khung, chưa
 * gán app" mất sau mỗi lần nổ máy.
 */
class WorkspaceDefaultTest {

    private val allEmpty = WorkspaceState.of(LayoutPreset.QUAD)

    @Test
    fun `chua tung luu thi nap bo cuc mac dinh`() {
        assertEquals(WorkspaceState.DEFAULT, WorkspaceDefault.resolve(WorkspaceState(), everSaved = false))
        assertEquals(WorkspaceState.DEFAULT, WorkspaceDefault.resolve(allEmpty, everSaved = false))
    }

    @Test
    fun `da luu ma toan o trong thi giu nguyen trong`() {
        val out = WorkspaceDefault.resolve(allEmpty, everSaved = true)
        assertSame(allEmpty, out)
        assertEquals(true, out.slots.all { it is SlotContent.Empty })
    }

    @Test
    fun `da luu co noi dung thi giu nguyen`() {
        val ws = WorkspaceState.of(LayoutPreset.TWO_ROW, SlotContent.Empty, SlotContent.App("a.b"))
        assertSame(ws, WorkspaceDefault.resolve(ws, everSaved = true))
        // Không thể xảy ra từ đĩa (không khoá ⇒ mọi ô trống), nhưng nếu có thì KHÔNG được đè nội dung.
        assertSame(ws, WorkspaceDefault.resolve(ws, everSaved = false))
    }
}
