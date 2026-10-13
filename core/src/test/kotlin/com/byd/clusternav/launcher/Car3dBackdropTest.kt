package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 3.01 R25 — ảnh nền xe 3D: lưu theo hồ sơ trong `color_choice` (trường `bg=`), tên tệp an toàn, phép căn thuần. */
class Car3dBackdropTest {

    @Test
    fun `luu trong color_choice theo nhan, chuoi cu khong doi tung byte, ban cu bo qua truong`() {
        val c = ColorChoice(carPaint = "grey", carBackdrop = Car3dBackdrop("seal-08.jpg", 1.5, 0.2, -0.1))
        val s = c.encode()
        assertEquals("KACHI_BLUE;NEUTRAL;car=grey;bg=seal-08.jpg@1.500,0.200,-0.100", s)
        assertEquals(c, ColorChoice.decode(s))
        assertEquals("KACHI_BLUE;NEUTRAL;car=grey", ColorChoice(carPaint = "grey").encode(), "không ảnh ⇒ chuỗi như trước")
        assertNull(ColorChoice.decode("KACHI_BLUE;NEUTRAL;car=grey").carBackdrop)
    }

    @Test
    fun `ten tep la duong dan hoac an thi bo - khong bao gio ra ngoai thu muc anh`() {
        listOf("../x.jpg", "a/b.jpg", ".hidden.jpg", "..", "x..jpg", "", "a b.jpg").forEach { assertFalse(Car3dBackdrop.validName(it), it) }
        assertTrue(Car3dBackdrop.validName("Seal_08-x.JPG"))
        assertNull(ColorChoice.decode("KACHI_BLUE;NEUTRAL;bg=../../etc/p@1,0,0").carBackdrop)
        assertEquals(Car3dBackdrop("a.jpg", Car3dBackdrop.MAX_ZOOM, 1.0, -1.0), Car3dBackdrop.decode("bg=a.jpg@99,5,-7"), "kẹp phép căn")
    }

    @Test
    fun `can anh - phu kin khung, dich theo nua khung, phong co tran`() {
        val b = Car3dBackdrop("a.jpg")
        val (s, tx, ty) = b.place(1000, 500, 800, 800)
        assertEquals(1.6f, s, 1e-4f); assertEquals(-400f, tx, 1e-3f); assertEquals(0f, ty, 1e-3f)
        val moved = b.pan(80f, -40f, 800, 800)
        assertEquals(0.2, moved.dx, 1e-9); assertEquals(-0.1, moved.dy, 1e-9)
        assertEquals(Car3dBackdrop.MAX_ZOOM, b.zoomBy(10f).zoom); assertEquals(1.0, b.zoomBy(0.1f).zoom)
    }
}
