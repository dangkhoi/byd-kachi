package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.escape.EscapeShade.Edge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R16 — khoá: chỉ CẠNH (không gì → có / có → không gì) mới sinh lệnh; hộp thoại chồng nhau (bộ chọn hồ sơ → hộp thứ hai)
 * hay hộp thoại mở TRONG bảng Cài đặt không sinh lượt Home/đưa-lên thừa; đóng báo lặp không làm âm bộ đếm.
 */
class EscapeShadeTest {

    @Test
    fun `hop thoai tren man nha - mo la OPENED, dong la CLOSED`() {
        val s = EscapeShade()
        assertEquals(Edge.OPENED, s.dialog(true))
        assertTrue(s.open)
        assertEquals(Edge.CLOSED, s.dialog(false))
        assertFalse(s.open)
    }

    @Test
    fun `hai hop thoai chong nhau chi mot canh moi chieu`() {
        val s = EscapeShade()
        assertEquals(Edge.OPENED, s.dialog(true))
        assertNull(s.dialog(true), "hộp thứ hai: app đã ở dưới, không Home lần nữa")
        assertNull(s.dialog(false), "còn một hộp đang mở ⇒ chưa đưa app lên")
        assertEquals(Edge.CLOSED, s.dialog(false))
        assertNull(s.dialog(false), "báo đóng lặp ⇒ không âm, không cạnh")
        assertEquals(Edge.OPENED, s.dialog(true), "bộ đếm không âm ⇒ lần mở sau vẫn là cạnh")
    }

    @Test
    fun `hop thoai trong bang Cai dat khong sinh canh, dong bang moi CLOSED`() {
        val s = EscapeShade()
        assertEquals(Edge.OPENED, s.panels(true))
        assertNull(s.panels(true))
        assertNull(s.dialog(true))
        assertNull(s.dialog(false))
        assertEquals(Edge.CLOSED, s.panels(false))
        assertNull(s.panels(false))
    }

    @Test
    fun `bang dong khi hop thoai con mo thi cho hop thoai dong`() {
        val s = EscapeShade()
        s.panels(true); s.dialog(true)
        assertNull(s.panels(false))
        assertEquals(Edge.CLOSED, s.dialog(false))
    }
}
