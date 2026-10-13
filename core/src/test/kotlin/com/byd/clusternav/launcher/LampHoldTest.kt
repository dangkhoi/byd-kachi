package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** POLISH2 — đèn vừa chạm giữ đích tới khi số đọc xác nhận hoặc ~3 s (khoá lỗi "đèn ban ngày không tắt được" trên máy ảo). */
class LampHoldTest {

    @Test
    fun `so doc cu khong de duoc dich vua gui, xac nhan thi tha`() {
        val h = LampHold(3_000)
        h.put("drl", false, 1_000)
        assertEquals(false, h.held("drl", true, 1_500), "số đọc cũ (còn SÁNG) không đè đích TẮT")
        assertNull(h.held("drl", false, 1_600), "xe báo TẮT ⇒ thả")
        assertNull(h.held("drl", true, 1_700), "đã thả ⇒ số đọc thắng")
    }

    @Test
    fun `het han thi so doc thang, khong so doc van giu trong han`() {
        val h = LampHold(3_000)
        h.put("headl", true, 0)
        assertEquals(true, h.held("headl", null, 2_999))
        assertEquals(1, h.remaining(2_999))
        assertNull(h.held("headl", false, 3_000), "hết 3 s ⇒ xe không làm theo thì màn nói thật")
        assertEquals(0, h.remaining(3_000))
    }
}
