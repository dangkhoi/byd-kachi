package com.byd.clusternav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** So sánh version — thuần, test off-device. Khoá logic quyết định "có bản mới". */
class UpdateCheckerTest {
    @Test fun `moi hon`() {
        assertTrue(UpdateChecker.cmp("0.57", "0.56") > 0)
        assertTrue(UpdateChecker.cmp("0.56", "0.9") > 0, "0.56 > 0.9 vì 56 > 9 (không phải so chuỗi)")
        assertTrue(UpdateChecker.cmp("1.0", "0.99") > 0)
        assertTrue(UpdateChecker.cmp("0.56.1", "0.56") > 0)
    }
    @Test fun `bang hoac cu hon`() {
        assertEquals(0, UpdateChecker.cmp("0.56", "0.56"))
        assertEquals(0, UpdateChecker.cmp("0.56", "0.56.0"))
        assertTrue(UpdateChecker.cmp("0.55", "0.56") < 0)
    }
}
