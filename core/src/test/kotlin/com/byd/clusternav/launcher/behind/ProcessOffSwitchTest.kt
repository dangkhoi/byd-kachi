package com.byd.clusternav.launcher.behind

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Soát vòng 2 [P3] — BEHIND-HOME bị TẮT bởi một chuỗi KHÁC (chuyến lên xe / lối tắt *Chạy ngầm* gặp `ANCHOR_IN_FRONT`) thì
 * đầu ô phải được báo để bỏ nút *chạy nền* — bản cũ là một `var` trần, không ai được báo ⇒ nút cũ chạm vào im lặng.
 */
class ProcessOffSwitchTest {

    @Test
    fun `tat mot lan bao nguoi nghe dung mot lan, giu ly do dau`() {
        val sw = ProcessOffSwitch()
        var calls = 0
        sw.listen { calls++ }
        assertNull(sw.reason)
        assertTrue(sw.off("anchor-in-front"))
        assertEquals(1, calls, "người nghe được báo khi vừa tắt")
        assertFalse(sw.off("anchor-ran"), "đã tắt ⇒ không tắt lại")
        assertEquals(1, calls, "không báo lại")
        assertEquals("anchor-in-front", sw.reason, "giữ lý do ĐẦU (phép đo đầu tiên)")
    }

    @Test
    fun `go nguoi nghe thi khong con duoc bao - view da thao khong bi giu`() {
        val sw = ProcessOffSwitch()
        var calls = 0
        val l: () -> Unit = { calls++ }
        sw.listen(l)
        sw.listen(l)                       // gắn lại (onAttachedToWindow lần hai) không thành hai lượt báo
        sw.unlisten(l)
        sw.off("x")
        assertEquals(0, calls)
    }

    @Test
    fun `nhieu nguoi nghe deu duoc bao`() {
        val sw = ProcessOffSwitch()
        val seen = mutableListOf<String>()
        sw.listen { seen += "a" }
        sw.listen { seen += "b" }
        sw.off("x")
        assertEquals(setOf("a", "b"), seen.toSet())
        assertEquals(2, seen.size)
    }
}
