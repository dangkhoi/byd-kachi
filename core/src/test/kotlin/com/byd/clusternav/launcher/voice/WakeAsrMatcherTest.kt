package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * "Hey Kachi" KHÔNG-train ([WakeAsrMatcher]): nổ đúng câu gọi, KHÔNG nổ câu thường/dài. Off-car (chuỗi giả — đây
 * là text mà model NGHE tiếng Việt phát ra, không phải mic).
 */
class WakeAsrMatcherTest {

    @Test fun `no cac bien the goi kachi`() {
        listOf(
            "kachi", "ka chi", "ca chi", "ga chi", "co chi", "kachi ơi", "ok kachi",
            "hey kachi", "hi ca chi", "kachi oi", "ok ca chi",
        ).forEach { assertTrue(WakeAsrMatcher.isWake(it), "phải nổ: \"$it\"") }
    }

    @Test fun `KHONG no cau thuong hoac dai`() {
        listOf(
            "hôm nay trời đẹp quá", "bật đèn đọc", "mở kính lái", "cho tôi nghe nhạc",
            "", "ừ", "cái gì đó tôi không biết nói dài dòng lắm luôn", "chào buổi sáng",
        ).forEach { assertFalse(WakeAsrMatcher.isWake(it), "KHÔNG được nổ: \"$it\"") }
    }

    /** Câu dài (>4 từ) chứa "kachi" giữa cũng KHÔNG nổ — người đang nói chuyện khác, không phải gọi. */
    @Test fun `kachi giua cau dai khong no`() {
        assertFalse(WakeAsrMatcher.isWake("tôi vừa mua con kachi màu đỏ hôm qua"))
    }
}
