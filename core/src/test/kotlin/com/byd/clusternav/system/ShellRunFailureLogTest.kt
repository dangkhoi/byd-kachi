package com.byd.clusternav.system

import java.net.SocketTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Hardening 2026-09-25 · audit F1 [P1] — `ShellTransport.run` hỏng cả hai lượt thì KHÔNG được im: một dòng W có
 * lệnh rút gọn + lớp lỗi, tiết chế theo lớp lỗi để dadb chết không thành bão log trên thẻ. (Wiring vào `run()`
 * canh ở `:app` — `ShellTransportFailureLogWiringTest`.)
 */
class ShellRunFailureLogTest {

    @BeforeEach fun reset() = ShellRunFailureLog.resetForTest()

    @Test fun `lan dau ghi, cung lop loi trong 60 s thi im, lop khac van ghi`() {
        val t0 = 1_000L
        val first = ShellRunFailureLog.line("am start -n a/.B", SocketTimeoutException("Read timed out"), t0)
        assertNotNull(first)
        assertTrue(first!!.contains("SocketTimeoutException: Read timed out"), first)
        assertTrue(first.contains("cmd=am start -n a/.B"), first)

        assertNull(ShellRunFailureLog.line("pidof x", SocketTimeoutException("again"), t0 + 59_999), "cùng lớp trong cửa sổ ⇒ im")
        assertNotNull(ShellRunFailureLog.line("pidof x", IllegalStateException("closed"), t0 + 10), "lớp khác = bệnh khác ⇒ ghi")
        assertNotNull(ShellRunFailureLog.line("pidof x", SocketTimeoutException("again"), t0 + ShellRunFailureLog.THROTTLE_MS), "hết cửa sổ ⇒ ghi lại làm mốc")
    }

    @Test fun `lenh dai bi cat o 80 ky tu, dong ho lui thi coi nhu het cua so`() {
        val long = "dumpsys " + "x".repeat(300)
        val line = ShellRunFailureLog.line(long, RuntimeException("boom"), 5_000L)!!
        assertEquals(ShellRunFailureLog.CMD_MAX, line.substringAfter("cmd=").length)
        assertNotNull(ShellRunFailureLog.line(long, RuntimeException("boom"), 4_000L), "nowMs lùi ⇒ không khoá vĩnh viễn")
    }
}
