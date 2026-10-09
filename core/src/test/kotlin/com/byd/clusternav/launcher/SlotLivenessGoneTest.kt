package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R12 (`SLOT-APP-CRASH-WHITE`) — app sập NGAY lượt mở vào ô (chưa nhịp đo nào thấy nó trong ô) phải đi luật hoàn ô.
 *
 * [ĐO máy ảo 09/10 15:45] Maps mở vào ô 1 rồi sập liên tục (`am crash` lặp): `FATAL EXCEPTION` + `Force finishing activity` ở cả
 * lượt mở lẫn lượt thử lại 2 s ⇒ ô xám (màn ảo trống) — luật 1 "chưa thấy sống thì không kết luận" giữ ô như thế tới khi tình cờ
 * bắt được một nhịp sống (lần đo này 37 s; app sập trước MỌI nhịp ⇒ mãi mãi — đúng ca xe 09/10 13:02 YouTube mod, ô trắng).
 * Fixture `am-stack-list-emulator-2026-10-09-r11-maps-crashed-gone` = bản đọc thật sau loạt sập: không còn task Maps ở display nào.
 */
class SlotLivenessGoneTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture core/src/test/resources/diagnostics/$name.txt")

    private val maps = "com.google.android.apps.maps"

    @Test
    fun `ban doc that sau khi app sap la GONE, khong phai ELSEWHERE`() {
        val out = fixture("am-stack-list-emulator-2026-10-09-r11-maps-crashed-gone")
        assertEquals(SlotPresence.GONE, SlotPresence.of(out, maps, 14))
        assertEquals(SlotPresence.IN_SLOT, SlotPresence.of(out, "com.google.android.youtube", 14), "app khác trong ô vẫn sống")
        assertEquals(SlotPresence.ELSEWHERE, SlotPresence.of(out, "com.waze", 14))
    }

    @Test
    fun `chua tung thay song ma GONE hai nhip doc duoc thi ket luan da dong`() {
        val l = SlotLiveness()
        assertFalse(l.observe(alive = false, gone = true), "một nhịp ⇒ chưa (có thể rơi giữa lúc hệ dời task)")
        assertTrue(l.observe(alive = false, gone = true), "hai nhịp ⇒ app đã sập/đóng ngay lượt mở")
        assertFalse(l.elsewhere, "không phải 'đã rời ô, vẫn mở' ⇒ luật hoàn ô APP_DIED, không câu báo")
        assertFalse(l.missing)
        assertFalse(l.observe(alive = false, gone = true), "chỉ báo MỘT lần")
    }

    @Test
    fun `GONE xen nhip doc hong hoac thay song thi dem lai`() {
        val l = SlotLiveness()
        assertFalse(l.observe(alive = false, gone = true))
        assertFalse(l.observe(alive = false, gone = false), "bản đọc rỗng/lạ ⇒ UNKNOWN ⇒ không phải GONE ⇒ về 0")
        assertFalse(l.observe(alive = false, gone = true))
        assertFalse(l.observe(alive = true), "thấy sống ⇒ luật 2 cũ")
        assertFalse(l.observe(alive = false, gone = true))
        assertTrue(l.observe(alive = false, gone = true), "đã thấy sống ⇒ 2 nhịp hụt như trước")
    }

    @Test
    fun `chua thay song va khong co bang chung duong thi van khong ket luan - luat 1 giu nguyen`() {
        val l = SlotLiveness()
        repeat(20) { assertFalse(l.observe(alive = false)) }
    }

    @Test
    fun `man ao nhan lai tu o 7 giu PARK-2b - mot nhip vang la missing`() {
        val l = SlotLiveness(adopted = true)
        assertTrue(l.observe(alive = false, gone = true))
        assertTrue(l.missing, "PARK-2b: mở lại app, không hoàn ô")
    }
}
