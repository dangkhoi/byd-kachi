package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FIX286 R-HUD — khoá bộ đọc khối `Live notification listeners` trên ba bản dump NGUYÊN VĂN từ máy ảo A10
 * (`emulator-5554`, 03/10, lệnh `dumpsys notification p com.byd.launcher`):
 *  • `live` — bình thường, Kachi đang gắn;
 *  • `approved-not-live` — ĐÚNG HÌNH của ca hiện trường H1: còn trong danh sách duyệt + "All … enabled" mà KHÔNG có ở
 *    Live (dựng bằng `pm disable` component — chỉ để có hình dump, đã `pm enable` lại ngay);
 *  • `disallowed-mirror-granted` — ca giả lập E2E: NMS đã gỡ duyệt, bản gương Settings ghi lại "đã cấp".
 * Khối trợ lý (`Live notification assistants (1):`) đứng ngay sau — không được lẫn vào.
 */
class NlsLiveDumpTest {

    private val comp = "com.byd.launcher/com.byd.clusternav.NavNotificationListener"

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/dumpsys-notification-emulator-2026-10-03-$name.txt")
            ?.bufferedReader()?.readText() ?: error("thiếu fixture dumpsys-notification-emulator-2026-10-03-$name.txt")

    @Test
    fun `dump nguyen van luc dang gan - LIVE`() {
        val d = fixture("live")
        assertEquals(NlsLiveDump.Verdict.LIVE, NlsLiveDump.verdict(d, comp))
        val b = NlsLiveDump.parse(d)!!
        assertEquals(setOf(comp), b.live, "lọc theo gói ⇒ Live chỉ còn component của Kachi")
        assertEquals(setOf(comp), b.enabled)
        assertTrue(b.snoozed.isEmpty())
    }

    @Test
    fun `hinh ca hien truong H1 - da duyet ma KHONG Live thi NOT_LIVE`() {
        val d = fixture("approved-not-live")
        val b = NlsLiveDump.parse(d)!!
        assertTrue(comp in b.enabled, "ca H1: vẫn nằm trong 'All … enabled for current profiles'")
        assertTrue(b.live.isEmpty(), "…mà khối Live rỗng")
        assertEquals(NlsLiveDump.Verdict.NOT_LIVE, NlsLiveDump.verdict(d, comp), "quyết theo Live, KHÔNG theo enabled")
    }

    @Test
    fun `ca gia lap E2E - NMS go duyet thi NOT_LIVE`() {
        val d = fixture("disallowed-mirror-granted")
        assertEquals(NlsLiveDump.Verdict.NOT_LIVE, NlsLiveDump.verdict(d, comp))
        assertTrue(NlsLiveDump.parse(d)!!.enabled.isEmpty())
    }

    @Test
    fun `khoi tro ly KHONG lan vao khoi bo nghe`() {
        // Ghép hai khối như dump KHÔNG lọc: trợ lý có Live riêng chứa một component — không được tính là bộ nghe.
        val d = """
            |  Notification listeners:
            |    Live notification listeners (1):
            |    Snoozed notification listeners (0):
            |    mListenerHints: 0
            |
            |  Notification assistant services:
            |    Live notification assistants (1):
            |      ComponentInfo{$comp} (user 0): x SYSTEM
        """.trimMargin()
        assertEquals(NlsLiveDump.Verdict.NOT_LIVE, NlsLiveDump.verdict(d, comp))
    }

    @Test
    fun `khong thay khoi Live thi UNREADABLE - khong doan`() {
        assertEquals(NlsLiveDump.Verdict.UNREADABLE, NlsLiveDump.verdict("", comp))
        assertEquals(NlsLiveDump.Verdict.UNREADABLE, NlsLiveDump.verdict(null, comp))
        assertEquals(NlsLiveDump.Verdict.UNREADABLE, NlsLiveDump.verdict("Can't find service: notification", comp))
        assertNull(NlsLiveDump.parse("Permission Denial: can't dump NotificationManager"))
    }

    @Test
    fun `snoozed in dang ngan duoc chuan hoa`() {
        val d = """
            |    Live notification listeners (0):
            |    Snoozed notification listeners (1):
            |      com.byd.launcher/com.byd.clusternav.NavNotificationListener
            |      com.x.y/.Short
            |    mListenerHints: 0
        """.trimMargin()
        val b = NlsLiveDump.parse(d)!!
        assertEquals(setOf(comp, "com.x.y/com.x.y.Short"), b.snoozed)
        assertEquals(NlsLiveDump.Verdict.NOT_LIVE, NlsLiveDump.verdict(d, comp))
    }

    @Test
    fun `lenh doc chi loc dung goi cua minh`() {
        assertEquals("dumpsys notification p com.byd.launcher", NlsLiveDump.command("com.byd.launcher"))
        assertThrows(IllegalArgumentException::class.java) { NlsLiveDump.command("com.byd.launcher; reboot") }
        assertThrows(IllegalArgumentException::class.java) { NlsLiveDump.command("") }
    }
}
