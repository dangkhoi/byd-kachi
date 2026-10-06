package com.byd.clusternav.launcher.testbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 2.91 VOICE-APP-NAMES · A7 — phân tích ba lệnh cầu `teach` · `teach_text` · `teach_clear` (spec §4.11). */
class TestBridgeTeachCommandsTest {

    private fun parse(vararg kv: Pair<String, Any?>) = TestBridgeCommands.parse(mapOf(*kv), emptySet())

    @Test
    fun `ba lenh co trong danh sach va doi dung extra`() {
        assertTrue(TestBridgeTeachCommands.NAMES.all { it in TestBridgeCommands.NAMES })
        assertEquals(TestBridgeParse.Err("missing_extra:pkg"), parse("cmd" to "teach"))
        assertEquals(TestBridgeParse.Err("missing_extra:text"), parse("cmd" to "teach_text", "pkg" to "com.example.a"))
        val ok = parse("cmd" to "teach", "pkg" to "com.example.a", "path" to "/sdcard/x.wav", "op" to "SAVE") as TestBridgeParse.Ok
        assertEquals("save", ok.cmd.op)
        assertEquals("/sdcard/x.wav", ok.cmd.path)
        assertTrue(parse("cmd" to "teach_clear") is TestBridgeParse.Ok, "teach_clear không bắt buộc pkg")
    }

    @Test
    fun `op la bi tu choi khong doan`() {
        assertEquals(TestBridgeParse.Err("bad_op:xoa"), parse("cmd" to "teach_text", "pkg" to "com.example.a", "text" to "abcd", "op" to "xoa"))
    }
}
