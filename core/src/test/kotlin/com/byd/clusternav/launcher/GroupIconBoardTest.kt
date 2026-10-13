package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R22·B — BẢNG ICON ([GroupIconBoard]) dựng từ nhóm THẬT. Khoá: nút và ô đọc của cùng một đèn là MỘT ô (không còn
 * hàng nút chữ lặp dưới lưới đọc); mọi thành viên có đúng một chỗ; dạng do nhóm KHAI, không đoán theo mã.
 */
class GroupIconBoardTest {

    private fun model(g: CapabilityGroup) = GroupBoard.of(g, CarStatus())

    @Test
    fun `Den - nut gop vao o doc cung hinh, moi thanh vien mot cho`() {
        val tiles = GroupIconBoard.of(model(CapabilityGroups.LIGHTS))!!
        val g = CapabilityGroups.LIGHTS
        assertEquals(g.reads, tiles.mapNotNull { it.cell?.id }, "mọi ô đọc, đúng thứ tự khai")
        assertEquals(g.writes.sorted(), tiles.mapNotNull { it.action?.id }.sorted(), "mọi nút, không mất không lặp")
        assertEquals("headl", tiles.single { it.cell?.id == "light_high_beam" }.action?.id, "đèn pha: ô đọc + nút cùng hình")
        assertEquals("drl", tiles.single { it.cell?.id == "light_drl" }.action?.id)
        assertNull(tiles.single { it.action?.id == "readl" }.cell, "đèn đọc không có ô đọc ⇒ ô riêng")
        assertEquals(g.reads.size + 1, tiles.size)
    }

    @Test
    fun `dang do nhom khai - nhom khac khong doi`() {
        assertEquals(listOf("g_lights"), CapabilityGroups.ALL.filter { GroupIconBoard.of(model(it)) != null }.map { it.id })
    }

    @Test
    fun `luoi chon o vuong nhat ma van du dich cham`() {
        // Ô 2×3 của owner (vùng thân ~267×312 px @1,5×), 10 đèn, đích chạm 72 px.
        val (cols, rows) = GroupIconBoard.grid(10, 267, 312, 72)!!
        assertTrue(267 / cols >= 72 && 312 / rows >= 72, "$cols×$rows")
        assertEquals(3 to 4, cols to rows)
        val wide = GroupIconBoard.grid(10, 870, 170, 72)!!
        assertEquals(10, wide.first * wide.second, "6×2: lưới kín, không chỗ trống (${wide})")
        assertTrue(870 / wide.first >= 72 && 170 / wide.second >= 72)
        assertNull(GroupIconBoard.grid(10, 140, 140, 72), "không chia được ⇒ khung ngoài lùi ô tóm tắt")
        assertNotNull(GroupIconBoard.grid(1, 72, 72, 72))
    }
}
