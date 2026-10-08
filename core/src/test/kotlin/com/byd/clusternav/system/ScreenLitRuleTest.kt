package com.byd.clusternav.system

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 2.98 · R6-C — một nguồn báo TẮT là tắt; không hỏi được ⇒ SÁNG (đọc như cũ). Thử ĐỎ: đổi `&&` thành `||`. */
class ScreenLitRuleTest {
    @Test
    fun `mot nguon tat la tat, khong biet thi sang`() {
        assertTrue(ScreenLitRule.lit(true, true))
        assertFalse(ScreenLitRule.lit(true, false), "BYD tắt màn mà interactive vẫn true")
        assertFalse(ScreenLitRule.lit(false, true))
        assertTrue(ScreenLitRule.lit(null, null), "fail-open")
        assertTrue(ScreenLitRule.lit(null, true))
        assertFalse(ScreenLitRule.lit(null, false))
    }
}
