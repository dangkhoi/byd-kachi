package com.byd.clusternav.launcher.testbridge

import com.byd.clusternav.modules.navaccess.A11yBindJournal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.83 · lệnh `a11ylog` — ĐỌC nhật ký gắn Hỗ trợ trên bản PHÁT HÀNH ═══════════════════════════════════════
 *
 * Khoá bài học [ĐO xe 29/09]: trên bản phát hành `filesDir/diag/a11y-bind.log` không đọc được bằng đường nào (màn
 * Chẩn đoán gỡ nút 21/09 + `exported=false` ⇒ `am start` bị từ chối; không debuggable ⇒ không `run-as`). Lệnh cầu
 * kiểm thử là đường đọc — bài này canh phần THUẦN của nó: lệnh có trong bảng, chỉ đọc, và số dòng được kẹp ở ĐÚNG
 * một chỗ. Tách tệp khỏi `TestBridgeCommandTest` vì tệp đó đã chạm trần 500 dòng (CLAUDE.md §4.1).
 */
class TestBridgeA11yLogCommandTest {

    private val files = setOf("clusternav_prefs")

    private fun parse(vararg extras: Pair<String, Any?>): TestBridgeCommand {
        val r = TestBridgeCommands.parse(
            mapOf(TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.A11YLOG, *extras),
            files,
        )
        assertTrue(r is TestBridgeParse.Ok, "a11ylog phải phân tích được, nhận: $r")
        return (r as TestBridgeParse.Ok).cmd
    }

    @Test
    fun `a11ylog la lenh that, khong doi doi so, khong mang co auto_confirm`() {
        assertTrue(TestBridgeCommands.A11YLOG in TestBridgeCommands.NAMES, "chưa khai trong SPECS ⇒ unknown_cmd")
        val spec = TestBridgeCommands.SPECS.first { it.name == TestBridgeCommands.A11YLOG }
        assertTrue(spec.required.isEmpty(), "lệnh ĐỌC chạy ngay sau khi tắt máy không được đòi đối số: ${spec.required}")
        assertEquals(listOf(TestBridgeCommands.EXTRA_SLOT), spec.optional, "chỉ `--ei n` (số dòng) là tuỳ chọn")
        // Chỉ đọc, không chạm xe, nhật ký riêng tư theo thiết kế ⇒ KHÔNG có cổng xác nhận. Mọc cờ này ra là dấu hiệu
        // ai đó đã gắn một đường GHI vào lệnh đọc.
        assertFalse(TestBridgeCommands.EXTRA_AUTO_CONFIRM in spec.optional)
    }

    @Test
    fun `vang n thi lay so dong mac dinh`() {
        assertEquals(TestBridgeCommands.A11YLOG_DEFAULT_LINES, parse().tail)
    }

    @Test
    fun `n hop le giu nguyen, n qua tran bi kep ve tran tep`() {
        assertEquals(10, parse(TestBridgeCommands.EXTRA_SLOT to 10).tail)
        assertEquals(1, parse(TestBridgeCommands.EXTRA_SLOT to 1).tail)
        assertEquals(
            A11yBindJournal.MAX_LINES, parse(TestBridgeCommands.EXTRA_SLOT to 10_000).tail,
            "tệp không bao giờ dài hơn MAX_LINES — xin hơn là vô nghĩa, không phải lỗi",
        )
        assertEquals(A11yBindJournal.MAX_LINES, parse(TestBridgeCommands.EXTRA_SLOT to A11yBindJournal.MAX_LINES).tail)
    }

    /** `--ei n 0`/âm và `--es n 50` (gõ nhầm cờ ⇒ chuỗi) đều về mặc định — lệnh ĐỌC không được trả 0 dòng câm. */
    @Test
    fun `n khong hop le ve mac dinh, khong bao gio ra 0 dong`() {
        listOf<Any?>(0, -1, -500, "50", null).forEach { n ->
            assertEquals(
                TestBridgeCommands.A11YLOG_DEFAULT_LINES, parse(TestBridgeCommands.EXTRA_SLOT to n).tail,
                "n=$n phải về mặc định",
            )
        }
    }

    /** `tail` là trường RIÊNG của `a11ylog`: lệnh khác mang `--ei n` (số Ô) không được nhận số dòng. */
    @Test
    fun `tail chi co gia tri o lenh a11ylog`() {
        val r = TestBridgeCommands.parse(
            mapOf(TestBridgeCommands.EXTRA_CMD to TestBridgeCommands.SLOT_CLEAR, TestBridgeCommands.EXTRA_SLOT to 3),
            files,
        )
        val cmd = (r as TestBridgeParse.Ok).cmd
        assertEquals(3, cmd.slot)
        assertEquals(0, cmd.tail)
    }

    @Test
    fun `mac dinh nam trong dai kep`() {
        assertTrue(TestBridgeCommands.A11YLOG_DEFAULT_LINES in 1..A11yBindJournal.MAX_LINES)
        assertEquals(TestBridgeCommands.A11YLOG_DEFAULT_LINES, TestBridgeCommands.a11yLogTail(0))
    }
}
