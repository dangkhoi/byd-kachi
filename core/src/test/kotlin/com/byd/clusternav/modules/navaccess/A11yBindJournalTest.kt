package com.byd.clusternav.modules.navaccess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá R7 — nhật ký bền. Bài học: vòng đệm log trên xe chỉ giữ ~32 phút ([ĐO 2026-09-28]) nên khoảnh khắc
 * mối nối đứt trong đêm luôn trôi mất; không có nhật ký bền thì câu hỏi "đứt lúc nào" mãi là [CHƯA BIẾT].
 */
class A11yBindJournalTest {

    private val J = A11yBindJournal

    @Test
    fun `hieu hai dong ho = thoi gian ngu sau`() {
        // Xe chạy liên tục 3 giờ: hai đồng hồ đi cùng nhau ⇒ không ngủ.
        assertEquals(0L, J.deepSleepMs(elapsedMs = 10_800_000L, uptimeMs = 10_800_000L), "chạy liên tục")
        // Đứng qua đêm 9 tiếng rồi chạy 1 tiếng: chỉ đồng hồ có-đếm-lúc-ngủ nhảy.
        assertEquals(32_400_000L, J.deepSleepMs(elapsedMs = 36_000_000L, uptimeMs = 3_600_000L), "ngủ 9 giờ")
        assertEquals(0L, J.deepSleepMs(elapsedMs = 5L, uptimeMs = 99L), "đọc nghịch ⇒ kẹp về 0, không âm")
    }

    @Test
    fun `phan biet duoc dung qua dem voi chay duong dai`() {
        val h = 3_600_000L
        // Mười tiếng ĐỨNG: ngủ tích luỹ tăng 10 giờ ⇒ ngòi nổ bật.
        assertTrue(J.wokeFromLongSleep(prevDeepSleepMs = 0L, nowDeepSleepMs = 10 * h, thresholdMs = 2 * h),
            "để xe qua đêm ⇒ đúng lúc chữa rẻ nhất")
        // Mười tiếng CHẠY: ngủ tích luỹ không đổi ⇒ ngòi nổ im, không ai bị khởi động lại giữa đường.
        assertFalse(J.wokeFromLongSleep(prevDeepSleepMs = 10 * h, nowDeepSleepMs = 10 * h, thresholdMs = 2 * h),
            "chạy đường dài KHÔNG được tự khởi động lại giao diện")
        // Chợp mắt ngắn (mở cửa xem đồng hồ) ⇒ dưới ngưỡng, im.
        assertFalse(J.wokeFromLongSleep(0L, 300_000L, 2 * h), "ngủ 5 phút thì kệ")
        assertFalse(J.wokeFromLongSleep(-1L, 10 * h, 2 * h), "lần đo ĐẦU TIÊN chưa có mốc trước ⇒ không kết luận")
    }

    @Test
    fun `chi ghi khi DOI trang thai, cong nhip tim`() {
        assertTrue(J.shouldAppend(null, A11yBindJournal.State.BOUND, 0L, 3_600_000L), "tệp rỗng ⇒ luôn ghi dòng đầu")
        assertFalse(J.shouldAppend(A11yBindJournal.State.BOUND, A11yBindJournal.State.BOUND, 60_000L, 3_600_000L), "không đổi, chưa tới nhịp ⇒ im")
        assertTrue(J.shouldAppend(A11yBindJournal.State.BOUND, A11yBindJournal.State.STUCK, 60_000L, 3_600_000L), "ĐỔI ⇒ ghi, đây mới là thông tin")
        assertTrue(J.shouldAppend(A11yBindJournal.State.BOUND, A11yBindJournal.State.BOUND, 3_600_000L, 3_600_000L), "tới nhịp tim ⇒ ghi để biết còn sống")
    }

    @Test
    fun `dong ghi doc duoc bang mat va grep duoc`() {
        val l = J.line("2026-09-28T10:20:00", elapsedMs = 36_000_000L, uptimeMs = 3_600_000L,
            state = A11yBindJournal.State.STUCK, pid = 12738, note = "watchdog")
        assertEquals("2026-09-28T10:20:00 state=STUCK up=3600s sleep=32400s pid=12738 note=watchdog", l)
        assertEquals(A11yBindJournal.State.STUCK, J.stateOf(l), "đọc ngược lại được trạng thái")
    }

    @Test
    fun `mot dong luon la mot dong`() {
        val l = J.line("2026-09-28T10:20:00", 1000L, 1000L, A11yBindJournal.State.BOUND, 1,
            note = "xuống\ndòng\tvà   nhiều khoảng trắng dài lê thê vượt quá bốn mươi ký tự")
        assertEquals(1, l.lines().size, "note có xuống dòng KHÔNG được làm vỡ nhật ký")
        assertTrue(l.substringAfter("note=").length <= 40, "note bị cắt")
    }

    @Test
    fun `dong la khong lam vo luong`() {
        assertNull(J.stateOf(null), "tệp rỗng")
        assertNull(J.stateOf("rác từ bản cũ"), "dòng lạ ⇒ null, không ném")
        assertNull(J.stateOf("... state=KHONG_CO_THAT ..."), "trạng thái không tồn tại ⇒ null")
    }

    @Test
    fun `tep khong phinh to vo han`() {
        val many = (1..250).map { "dòng $it" }
        val kept = J.trim(many, max = 200)
        assertEquals(200, kept.size)
        assertEquals("dòng 51", kept.first(), "bỏ dòng CŨ nhất")
        assertEquals("dòng 250", kept.last(), "giữ dòng MỚI nhất")
        assertEquals(3, J.trim(listOf("a", "b", "c"), max = 200).size, "chưa đầy thì giữ nguyên")
    }
}
