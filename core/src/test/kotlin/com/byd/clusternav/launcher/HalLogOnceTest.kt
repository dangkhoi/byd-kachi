package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Hardening 2026-09-25 · audit F5 [P2] — lỗi HAL không còn nuốt im: MỘT dòng W cho mỗi `fqn#method`, log lại được
 * sau khi device resolve lại, trần 200 khoá. (Seam `onError` của `BydHal` + wiring gateway canh ở `:app` —
 * `BydHalGatewayLogOnceTest`.)
 */
class HalLogOnceTest {

    @BeforeEach fun reset() = HalLogOnce.resetForTest()

    @Test fun `moi khoa dung mot lan, device song lai thi duoc log lai`() {
        assertTrue(HalLogOnce.first("a.B#getSpeed"))
        assertFalse(HalLogOnce.first("a.B#getSpeed"), "1 400 lượt đọc/phút không được thành 1 400 dòng")
        assertTrue(HalLogOnce.first("a.B#getRpm"), "method khác = khoá khác")
        assertTrue(HalLogOnce.first("a.C#getSpeed"), "device khác = khoá khác")
        HalLogOnce.forgetDevice("a.B")
        assertTrue(HalLogOnce.first("a.B#getSpeed"), "sau khi resolve lại device ⇒ lỗi mới được log lại")
        assertFalse(HalLogOnce.first("a.C#getSpeed"), "device khác không bị quên oan")
    }

    @Test fun `tran 200 khoa — day thi bo khoa cu nhat, khoa moi van lo`() {
        repeat(HalLogOnce.CAP) { assertTrue(HalLogOnce.first("d#m$it")) }
        assertEquals(HalLogOnce.CAP, HalLogOnce.sizeForTest())
        assertTrue(HalLogOnce.first("d#new"), "khoá mới vẫn được log")
        assertEquals(HalLogOnce.CAP, HalLogOnce.sizeForTest(), "không phình quá trần")
        assertTrue(HalLogOnce.first("d#m0"), "khoá cũ nhất đã bị bỏ ⇒ log lại được (chấp nhận, đổi lấy trần bộ nhớ)")
        // Chèn lại m0 lại đẩy m1 ra (LRU theo thứ tự chèn) — m2 vẫn trong sổ, phải im.
        assertFalse(HalLogOnce.first("d#m2"), "khoá còn trong sổ vẫn im")
        assertEquals(HalLogOnce.CAP, HalLogOnce.sizeForTest())
    }
}
