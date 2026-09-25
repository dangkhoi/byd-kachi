package com.byd.clusternav.comfort

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * F7: 3 lần INVALID liên tiếp ⇒ ×2 mỗi lần tới trần 10 phút; đọc được ⇒ về 45 s (đếm về 0 ở caller).
 * Đỏ→xanh: đổi `< threshold` thành `<= threshold` ⇒ ca `3 → 90 s` ĐỎ; bỏ `coerceAtMost(maxMs)` ⇒ ca `≥6` ĐỎ.
 */
class Pm25PollBackoffTest {
    private val base = 45_000L

    @Test
    fun `duoi nguong giu 45 s, tu lan 3 gian x2 toi tran 10 phut`() {
        assertEquals(45_000L, Pm25PollBackoff.nextIntervalMs(0, base))
        assertEquals(45_000L, Pm25PollBackoff.nextIntervalMs(2, base), "2 lần chưa đủ kết luận")
        assertEquals(90_000L, Pm25PollBackoff.nextIntervalMs(3, base))
        assertEquals(180_000L, Pm25PollBackoff.nextIntervalMs(4, base))
        assertEquals(360_000L, Pm25PollBackoff.nextIntervalMs(5, base))
        assertEquals(600_000L, Pm25PollBackoff.nextIntervalMs(6, base), "720 s bị chặn ở trần 10 phút")
        assertEquals(600_000L, Pm25PollBackoff.nextIntervalMs(60, base), "không tràn số, không vượt trần")
    }

    @Test
    fun `doc duoc mot lan thi caller dem lai tu 0 va ve 45 s ngay`() {
        // Hợp đồng với Pm25FilterApplier: level != INVALID ⇒ consecutiveInvalid = 0 ⇒ base.
        assertEquals(base, Pm25PollBackoff.nextIntervalMs(0, base))
    }
}
