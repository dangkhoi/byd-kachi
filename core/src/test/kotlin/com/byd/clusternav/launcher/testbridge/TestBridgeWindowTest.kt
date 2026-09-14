package com.byd.clusternav.launcher.testbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Cửa sổ 60 phút của cầu kiểm thử — ba tính chất: (1) vừa bật là mở, hết 60 phút là đóng; (2) **tắt máy là hết**
 * (danh tính lần nổ máy = `boot_id`); (3) giá trị hỏng/bị sửa tay ⇒ đóng, không ném.
 *
 * ## [ĐO] xe DiLink3 2026-09-14 — vì sao bỏ giờ tường
 * Bản đầu dùng `wall − uptime` làm mốc nổ máy với dung sai 5 s. Trên xe, giờ tường bị chỉnh (GPS/mạng) sau khi owner
 * bật ⇒ mốc lệch >5 s ⇒ cầu tự coi là "khác lần nổ máy" và trả `test_mode_off` dù công tắc đang bật. Nay chỉ dùng
 * `boot_id` + `elapsedRealtime`, cả hai không phụ thuộc giờ tường.
 */
class TestBridgeWindowTest {

    private val bootA = "f17130fb-b4ea-4fc7-9c93-3f7e26992d9c"
    private val bootB = "0b2c9a1e-1111-4222-8333-444455556666"
    private val up = 120_000L // máy đã chạy 2 phút

    private fun on(): String = TestBridgeWindow.encode(bootA, up)

    @Test
    fun `vua bat thi dang mo va con du 60 phut`() {
        val v = on()
        assertTrue(TestBridgeWindow.isOn(v, bootA, up))
        assertEquals(60, TestBridgeWindow.remainingMinutes(v, bootA, up))
    }

    @Test
    fun `het 60 phut thi dong`() {
        val v = on()
        val later = up + TestBridgeWindow.WINDOW_MS
        assertFalse(TestBridgeWindow.isOn(v, bootA, later))
        assertEquals(0, TestBridgeWindow.remainingMinutes(v, bootA, later))
    }

    @Test
    fun `con 1 phut van la DANG MO - lam tron LEN`() {
        val v = on()
        val t = up + TestBridgeWindow.WINDOW_MS - 30_000L
        assertTrue(TestBridgeWindow.isOn(v, bootA, t))
        assertEquals(1, TestBridgeWindow.remainingMinutes(v, bootA, t))
    }

    /** Tính chất 2 — tắt máy là hết: boot_id đổi ⇒ đóng dù "hạn" theo uptime của lần trước vẫn còn. */
    @Test
    fun `khoi dong lai may thi dong, du han van con`() {
        val v = on()
        assertFalse(TestBridgeWindow.isOn(v, bootB, 30_000L), "giá trị của lần nổ máy TRƯỚC không được mở cửa cho lần này")
    }

    /** [ĐO] xe 14/09: giờ tường đổi KHÔNG được làm cửa sổ đóng — hàm không nhận giờ tường nữa, uptime tiếp tục ⇒ vẫn mở. */
    @Test
    fun `gio tuong doi bao nhieu cung khong anh huong - chi uptime va boot_id`() {
        val v = on()
        assertTrue(TestBridgeWindow.isOn(v, bootA, up + 10 * 60_000L))
        assertTrue(TestBridgeWindow.isOn(v, " $bootA\n", up + 1))
    }

    /** Tính chất 3 — hạn xa hơn cả cửa sổ ⇒ giá trị bị sửa tay ⇒ đóng, không kéo dài. */
    @Test
    fun `han vuot cua so thi dong chu khong keo dai`() {
        val forged = "$bootA:${up + 5 * TestBridgeWindow.WINDOW_MS}"
        assertFalse(TestBridgeWindow.isOn(forged, bootA, up))
    }

    @Test
    fun `gia tri hong hoac vang deu la DONG, khong nem`() {
        listOf(null, "", "   ", "abc", ":", "1:", ":2", "x:y", "1", "$bootA:", ":$up").forEach { raw ->
            assertFalse(TestBridgeWindow.isOn(raw, bootA, up), "giá trị '$raw' phải là ĐÓNG")
        }
        assertFalse(TestBridgeWindow.isOn(on(), "", up), "không đọc được boot_id ⇒ đóng (an toàn về phía tắt)")
    }
}
