package com.byd.clusternav.launcher.testbridge

import com.byd.clusternav.launcher.CtlWriteJournal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 · SR6 — lệnh `ctllog`: ĐỌC nhật ký mỗi lệnh ghi xe trên bản PHÁT HÀNH ═══════════════════════════════
 *
 * Cùng lẽ `a11ylog` (2.83): bản phát hành không mở được màn Chẩn đoán, không `run-as` ⇒ cầu kiểm thử là đường đọc
 * `ctl-writes.log` duy nhất ngoài `usage-*.log`. Bài canh phần THUẦN: lệnh có trong bảng, không cờ xác nhận (không
 * chạm xe), số dòng kẹp ở ĐÚNG một chỗ theo trần của chính tệp đó.
 */
class TestBridgeCtlLogCommandTest {

    private fun parse(vararg extras: Pair<String, Any?>): TestBridgeCommand {
        val r = TestBridgeCommands.parse(
            mapOf(TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.CTLLOG, *extras),
            setOf("clusternav_prefs"),
        )
        assertTrue(r is TestBridgeParse.Ok, "ctllog phải phân tích được, nhận: $r")
        return (r as TestBridgeParse.Ok).cmd
    }

    @Test
    fun `ctllog la lenh that, khong doi doi so, khong mang co auto_confirm`() {
        assertTrue(TestBridgeCommands.CTLLOG in TestBridgeCommands.NAMES, "chưa khai trong SPECS ⇒ unknown_cmd")
        val spec = TestBridgeCommands.SPECS.first { it.name == TestBridgeCommands.CTLLOG }
        assertTrue(spec.required.isEmpty(), "lệnh đọc không đòi đối số")
        assertTrue(TestBridgeCommands.EXTRA_AUTO_CONFIRM !in spec.optional, "không chạm xe ⇒ không cổng xác nhận")
    }

    @Test
    fun `so dong mac dinh va kep theo tran cua ctl-writes_log`() {
        assertEquals(TestBridgeCommands.A11YLOG_DEFAULT_LINES, parse().tail)
        assertEquals(7, parse(TestBridgeCommands.EXTRA_SLOT to 7).tail)
        assertEquals(CtlWriteJournal.MAX_LINES, parse(TestBridgeCommands.EXTRA_SLOT to 100_000).tail)
        listOf<Any?>(0, -3, "9", null).forEach { n ->
            assertEquals(TestBridgeCommands.A11YLOG_DEFAULT_LINES, parse(TestBridgeCommands.EXTRA_SLOT to n).tail, "n=$n")
        }
    }
}
