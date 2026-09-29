package com.byd.clusternav.modules.navaccess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.83 · MỌI dòng nhật ký gắn Hỗ trợ cũng ra logcat, nguyên văn ═══════════════════════════════════════════
 *
 * Khoá bài học [ĐO xe 29/09]: trên bản phát hành tệp nhật ký không đọc được, còn `kachi-logs/usage-*.log`
 * (logcat của chính tiến trình, `adb pull` không cần root) thì đọc được. Nhưng usage log hôm đó chỉ có dòng ĐỔI
 * trạng thái, ở một dạng KHÁC dòng tệp (nguyên văn usage-cycle2.log):
 *
 *     09-29 11:34:23.731 I/A11yJournal(29552): a11y BOUND → NOT_BOUND (watchdog)
 *     09-29 11:34:29.238 I/A11yJournal(29552): a11y NOT_BOUND → STUCK (grant-tu-dong)
 *
 * ⇒ không có `up=`/`sleep=` (đúng hai số phân biệt "đứng qua đêm" với "chạy đường dài"), không có nhịp tim, và
 * một lệnh grep `state=STUCK` chạy trên tệp thì câm trên usage log.
 */
class A11yBindJournalLogcatTest {

    private val J = A11yBindJournal

    /** Chuỗi trạng thái + note + giờ + pid lấy từ usage-cycle2.log [ĐO]; hai đồng hồ là số dựng (log cũ không có). */
    private val cycle = listOf(
        Triple(null, A11yBindJournal.State.BOUND, "watchdog"),
        Triple(A11yBindJournal.State.BOUND, A11yBindJournal.State.NOT_BOUND, "watchdog"),
        Triple(A11yBindJournal.State.NOT_BOUND, A11yBindJournal.State.STUCK, "grant-tu-dong"),
        Triple(A11yBindJournal.State.STUCK, A11yBindJournal.State.STUCK, "watchdog"), // nhịp tim: KHÔNG đổi
    )

    @Test
    fun `dong logcat chua NGUYEN VAN dong tep, ke ca nhip tim`() {
        cycle.forEachIndexed { i, (prev, now, note) ->
            val line = J.line("2026-09-29T11:34:2$i", 36_000_000L, 3_600_000L, now, 29552, note)
            val log = J.logcatLine(prev, line)
            assertTrue(log.contains(line), "dòng #$i: usage log phải mang nguyên văn dòng tệp — `$log`")
            assertTrue(log.startsWith(A11yBindJournal.LOGCAT_PREFIX), "tiền tố tách dòng nhật ký khỏi câu lỗi cùng tag")
            assertTrue(log.contains("sleep=32400s"), "hai đồng hồ phải có mặt — log 29/09 thiếu đúng thứ này")
            assertEquals(now, J.stateOf(log), "grep/stateOf trên dòng logcat phải ra đúng trạng thái như trên tệp")
            assertEquals(1, log.lines().size, "một dòng logcat là một dòng")
        }
    }

    @Test
    fun `dong logcat noi ro trang thai truoc, ke ca khi tep rong`() {
        val line = J.line("2026-09-29T11:34:23", 1_000L, 1_000L, A11yBindJournal.State.NOT_BOUND, 29552, "watchdog")
        assertTrue(J.logcatLine(A11yBindJournal.State.BOUND, line).endsWith(" prev=BOUND"), "bước ĐỔI đọc được ngay")
        assertTrue(J.logcatLine(A11yBindJournal.State.NOT_BOUND, line).endsWith(" prev=NOT_BOUND"), "nhịp tim cũng nói rõ")
        assertTrue(J.logcatLine(null, line).endsWith(" prev=-"), "dòng đầu của tệp mới ⇒ `-`, không `null`")
    }
}
