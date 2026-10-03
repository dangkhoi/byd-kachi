package com.byd.clusternav.launcher.testbridge

import com.byd.clusternav.launcher.voice.WakeSessionJournal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FIX286 · VK6 — lệnh `wakelog`: ĐỌC nhật ký phiên nghe của `:wake` (`usage-*.log` không có dòng nào của `:wake`).
 * Cùng khuôn `ctllog`: lệnh có trong bảng, không cờ xác nhận, số dòng kẹp ở ĐÚNG một chỗ theo trần của chính tệp đó.
 */
class TestBridgeWakeLogCommandTest {

    private fun parse(vararg extras: Pair<String, Any?>): TestBridgeCommand {
        val r = TestBridgeCommands.parse(
            mapOf(TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.WAKELOG, *extras),
            setOf("clusternav_prefs"),
        )
        assertTrue(r is TestBridgeParse.Ok, "wakelog phải phân tích được, nhận: $r")
        return (r as TestBridgeParse.Ok).cmd
    }

    @Test
    fun `wakelog la lenh that, khong doi doi so, khong mang co auto_confirm`() {
        assertTrue(TestBridgeCommands.WAKELOG in TestBridgeCommands.NAMES, "chưa khai trong SPECS ⇒ unknown_cmd")
        val spec = TestBridgeCommands.SPECS.first { it.name == TestBridgeCommands.WAKELOG }
        assertTrue(spec.required.isEmpty(), "lệnh đọc không đòi đối số")
        assertTrue(TestBridgeCommands.EXTRA_AUTO_CONFIRM !in spec.optional, "không chạm xe ⇒ không cổng xác nhận")
    }

    @Test
    fun `so dong mac dinh va kep theo tran cua wake-sessions_log`() {
        assertEquals(TestBridgeCommands.A11YLOG_DEFAULT_LINES, parse().tail)
        assertEquals(3, parse(TestBridgeCommands.EXTRA_SLOT to 3).tail)
        assertEquals(WakeSessionJournal.MAX_LINES, parse(TestBridgeCommands.EXTRA_SLOT to 100_000).tail)
        listOf<Any?>(0, -1, "9", null).forEach { n ->
            assertEquals(TestBridgeCommands.A11YLOG_DEFAULT_LINES, parse(TestBridgeCommands.EXTRA_SLOT to n).tail, "n=$n")
        }
    }
}
