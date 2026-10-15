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

    /**
     * 3.1.6 · R32 — cốp giữ theo HẠN CỦA DÒNG (`ControlDef.confirmMs` = 10 s; [ĐO logcat xe 14/10] đóng ~5,8 s mới đọc 0): số đọc cũ
     * "mở" lúc 6 s KHÔNG đè đích "đóng"; số đọc "đóng" ⇒ thả sớm; hết hạn ⇒ số đọc thắng (ca đóng/mở bằng nút vật lý); `drop` (cổng
     * chặn) ⇒ thả ngay. Đỏ khi: hạn cố định 3 s (ô nháy về "mở" giữa lúc đang đóng) · bỏ thả sớm · bỏ `confirmMs` của dòng cốp.
     */
    @Test
    fun `R32 cop giu theo han cua dong, tha som khi khop, het han so doc thang`() {
        val h = LampHold(3_000)
        assertEquals(ControlRegistry.TRUNK_CONFIRM_MS, h.holdMsOf("trunk"))
        assertEquals(3_000, h.holdMsOf("drl"), "dòng không khai confirmMs ⇒ hạn của bộ")
        h.put("trunk", false, 0)
        assertEquals(false, h.held("trunk", true, 6_000), "6 s: cốp đang hạ, xe vẫn đọc MỞ ⇒ giữ đích ĐÓNG")
        assertEquals(0, h.shown("trunk", 1, 6_000), "ô 2 mức hiện đích (0), không hiện số đọc cũ (1)")
        assertNull(h.held("trunk", false, 6_500), "xe đọc ĐÓNG ⇒ thả sớm")
        h.put("trunk", true, 10_000)
        assertNull(h.held("trunk", false, 20_000), "hết 10 s mà xe vẫn ĐÓNG ⇒ số đọc thắng")
        assertEquals(0, h.shown("trunk", 0, 20_000))
        assertNull(h.shown("trunk", null, 20_000), "không đích, không số đọc ⇒ null (chỗ gọi giữ hình cũ)")
        h.put("trunk", true, 30_000); h.drop("trunk")
        assertNull(h.held("trunk", false, 30_001), "cổng tốc độ chặn ⇒ drop ⇒ không còn đích giữ")
    }
}
