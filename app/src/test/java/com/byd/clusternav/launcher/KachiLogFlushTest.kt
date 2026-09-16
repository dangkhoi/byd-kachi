package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá bài học H3 (PERF 2026-09-16): gom lượt ghi thẻ **mà không đánh mất dòng quan trọng nhất**.
 *
 * Bản 1.66 gọi `flush()` sau MỖI dòng ⇒ ≈79 KB/phút trên xe đi kèm hàng nghìn lượt `write(2)`. Bỏ hẳn `flush`
 * thì rẻ nhất — và vô dụng: cả lý do tệp log tồn tại là *"có ngữ cảnh khi app CHẾT"*, mà lúc chết thì phần nằm
 * trong bộ đệm là phần biến mất. Hai bài dưới canh đúng hai vế đó.
 */
class KachiLogFlushTest {

    /** Đúng dạng `logcat -v time` mà [KachiLog.startCapture] đọc. */
    private fun line(sev: Char) = "09-16 09:01:53.708 $sev/Tag( 1234): thông điệp"

    @Test
    fun `dong canh bao va loi xa NGAY`() {
        listOf('W', 'E', 'F', 'A').forEach { sev ->
            assertTrue(KachiLog.mustFlushNow(line(sev), sinceLastFlushMs = 0), "mức $sev phải xả ngay")
        }
    }

    @Test
    fun `dong thuong khong xa moi dong`() {
        listOf('D', 'I', 'V').forEach { sev ->
            assertFalse(KachiLog.mustFlushNow(line(sev), sinceLastFlushMs = 0), "mức $sev không cần xả ngay")
        }
    }

    @Test
    fun `dong thuong van xa theo thoi gian`() {
        assertFalse(KachiLog.mustFlushNow(line('D'), KachiLog.FLUSH_EVERY_MS - 1))
        assertTrue(KachiLog.mustFlushNow(line('D'), KachiLog.FLUSH_EVERY_MS))
    }

    @Test
    fun `dong ngan hong dinh dang khong lam no`() {
        // logcat có thể cắt dòng / chèn dòng `--------- beginning of main`; không được ném, và cứ để nhịp thời
        // gian lo — một dòng lạ không đáng làm chết cả luồng ghi log.
        assertFalse(KachiLog.mustFlushNow("", 0))
        assertFalse(KachiLog.mustFlushNow("--------- beginning of main", 0))
        assertTrue(KachiLog.mustFlushNow("--------- beginning of main", KachiLog.FLUSH_EVERY_MS))
    }
}
