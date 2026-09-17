package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** K8 (1.70): cùng nội dung chỉ gửi lại sau ≥ 15 s; nội dung đổi thì gửi ngay. */
class ResendGateTest {
    @Test
    fun `cung noi dung thi doi du 15 s, doi noi dung thi gui ngay`() {
        val g = ResendGate(15_000L)
        assertTrue(g.shouldSend(0L, 1 to 2), "lần đầu luôn gửi")
        assertFalse(g.shouldSend(2_000L, 1 to 2), "2 s sau, cùng toạ độ ⇒ không")
        assertFalse(g.shouldSend(14_999L, 1 to 2))
        assertTrue(g.shouldSend(15_000L, 1 to 2), "đủ 15 s ⇒ gửi lại (bong bóng có thể đã dựng lại)")
        assertTrue(g.shouldSend(15_100L, 3 to 4), "toạ độ đổi ⇒ gửi ngay")
        assertFalse(g.shouldSend(15_200L, 3 to 4))
    }
}
