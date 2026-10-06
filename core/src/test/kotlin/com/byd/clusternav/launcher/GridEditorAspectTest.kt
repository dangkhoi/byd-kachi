package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 `GRID-EDITOR-ASPECT` — [ĐO mã] bảng vẽ bố cục co theo tỉ lệ CẢ MÀN (`displayMetrics`) trong khi các ô thật nằm trong
 * vùng màn TRỪ thanh trên + thanh nút ⇒ hình vẽ khác vùng ô thật. Nay hộp vẽ giữ tỉ lệ vùng ô đã đo ([GridEditorLogic.drawBox]).
 */
class GridEditorAspectTest {

    @Test
    fun `hop ve giu ti le vung o that, can giua, khong tran view`() {
        // Màn 1920×1080 · thanh trên ~ 72 px · thanh nút dưới 150 % ⇒ vùng ô ≈ 1872×846 (tỉ lệ 2,21 ≠ màn 1,78).
        val b = GridEditorLogic.drawBox(1600, 700, 1872, 846, 8f, 1920f / 1080f)
        assertEquals(1872.0 / 846.0, (b.w / b.h).toDouble(), 1e-3, "tỉ lệ vùng ô thật")
        assertEquals(684f, b.h, 1e-3f, "chặn bởi chiều cao view − 2 lề")
        assertEquals((1600 - b.w) / 2f, b.x, 1e-3f); assertEquals(8f, b.y, 1e-3f)
        // Thanh nút TRÁI ⇒ vùng ô hẹp/cao hơn màn.
        val left = GridEditorLogic.drawBox(1600, 700, 1700, 1008, 8f, 1920f / 1080f)
        assertEquals(1700.0 / 1008.0, (left.w / left.h).toDouble(), 1e-3)
        for ((vw, vh) in listOf(1600 to 700, 800 to 1200, 300 to 200)) for ((aw, ah) in listOf(1872 to 846, 1700 to 1008, 1920 to 1080)) {
            val x = GridEditorLogic.drawBox(vw, vh, aw, ah, 8f, 16f / 9f)
            assertTrue(x.x >= 8f - 1e-3 && x.y >= 8f - 1e-3 && x.x + x.w <= vw - 8f + 1e-3 && x.y + x.h <= vh - 8f + 1e-3, "${vw}x$vh ${aw}x$ah")
        }
    }

    @Test
    fun `vung chua do thi lui ve ti le man nhu truoc`() {
        val b = GridEditorLogic.drawBox(1600, 900, 0, 0, 8f, 1920f / 1080f)
        assertEquals(1920.0 / 1080.0, (b.w / b.h).toDouble(), 1e-3)
        val z = GridEditorLogic.drawBox(1600, 900, 0, 0, 8f, 0f)
        assertEquals(16.0 / 9.0, (z.w / z.h).toDouble(), 1e-3, "không có cả tỉ lệ màn ⇒ 16:9")
    }
}
