package com.byd.clusternav.launcher.testbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ T-BRIDGE · BÀI CANH CỬA SỔ THỜI GIAN ════════════════════════════════════════════════════════════════════
 *
 * Đây là **cổng an toàn duy nhất** của một receiver `exported` (spec R2), nên ba tính chất của nó phải được
 * khoá bằng máy, không bằng văn xuôi: tự hết hạn · chết theo lần nổ máy · không tin một mình đồng hồ treo tường.
 *
 * Mọi mốc thời gian ở đây là **tham số**, không phải `System.currentTimeMillis()` — nhờ vậy ca *"đã khởi động
 * lại"* và ca *"vặn đồng hồ"* kiểm được trong một phần nghìn giây, thay vì phải ngồi chờ 60 phút trong xe.
 */
class TestBridgeWindowTest {

    private val boot = 1_700_000_000_000L   // giờ treo tường lúc máy nổ
    private val up = 120_000L               // máy đã chạy 2 phút
    private val now = boot + up

    private fun on(): String = TestBridgeWindow.encode(now, up)

    @Test
    fun `vua bat thi dang mo va con gan du 60 phut`() {
        val v = on()
        assertTrue(TestBridgeWindow.isOn(v, now, up))
        assertEquals(60, TestBridgeWindow.remainingMinutes(v, now, up))
    }

    @Test
    fun `het 60 phut thi dong`() {
        val v = on()
        val later = now + TestBridgeWindow.WINDOW_MS
        assertFalse(TestBridgeWindow.isOn(v, later, up + TestBridgeWindow.WINDOW_MS))
        assertEquals(0, TestBridgeWindow.remainingMinutes(v, later, up + TestBridgeWindow.WINDOW_MS))
    }

    @Test
    fun `con 1 phut van la DANG MO — lam tron LEN`() {
        val v = on()
        val t = now + TestBridgeWindow.WINDOW_MS - 30_000L
        assertTrue(TestBridgeWindow.isOn(v, t, up + TestBridgeWindow.WINDOW_MS - 30_000L))
        assertEquals(1, TestBridgeWindow.remainingMinutes(v, t, up + TestBridgeWindow.WINDOW_MS - 30_000L))
    }

    /**
     * ⚠⚠ Tính chất số 2 — **tắt máy là hết**.
     *
     * Sau khi khởi động lại, `thời gian máy đã chạy` về gần 0 nên *mốc nổ máy* nhảy hẳn. Giá trị cũ còn nguyên
     * trên đĩa và hạn của nó **vẫn còn 58 phút**, nhưng nó thuộc về một lần nổ máy khác ⇒ đóng.
     */
    @Test
    fun `khoi dong lai may thi dong, du han van con`() {
        val v = on()
        val afterReboot = now + 5 * 60_000L        // 5 phút sau (giờ treo tường vẫn chạy tiếp)
        val upAfterReboot = 30_000L                // nhưng máy mới chạy được 30 giây
        assertFalse(
            TestBridgeWindow.isOn(v, afterReboot, upAfterReboot),
            "giá trị của lần nổ máy TRƯỚC không được mở cửa cho lần nổ máy này",
        )
    }

    /** ⚠⚠ Tính chất số 3 — vặn đồng hồ LÙI để kéo dài cửa sổ thì cửa **đóng**, không phải mở thêm. */
    @Test
    fun `van dong ho lui thi dong chu khong keo dai`() {
        val v = on()
        val back = now - 30 * 60_000L
        assertFalse(TestBridgeWindow.isOn(v, back, up), "hạn xa hơn cả cửa sổ ⇒ giá trị không đáng tin")
    }

    @Test
    fun `gia tri hong hoac vang deu la DONG, khong nem`() {
        listOf(null, "", "   ", "abc", ":", "1:", ":2", "x:y", "1", "1:2:3").forEach { raw ->
            assertFalse(TestBridgeWindow.isOn(raw, now, up), "giá trị '$raw' phải là ĐÓNG")
        }
    }

    @Test
    fun `sai so mot vai giay cua moc no may van duoc chap nhan`() {
        val v = on()
        // Hai lời gọi `currentTimeMillis`/`elapsedRealtime` không bao giờ trùng khít; một lượt NTP nhỏ cũng lệch.
        val drift = TestBridgeWindow.BOOT_TOLERANCE_MS - 1
        assertTrue(TestBridgeWindow.isOn(v, now + drift, up))
    }

    @Test
    fun `lech hon sai so cho phep thi coi la may khac`() {
        val v = on()
        val drift = TestBridgeWindow.BOOT_TOLERANCE_MS + 1_000
        assertFalse(TestBridgeWindow.isOn(v, now + drift, up))
    }

    @Test
    fun `cua so dung 60 phut — hang so nay la mot quyet dinh, khong phai so ngau nhien`() {
        assertEquals(60L * 60L * 1000L, TestBridgeWindow.WINDOW_MS)
    }
}
