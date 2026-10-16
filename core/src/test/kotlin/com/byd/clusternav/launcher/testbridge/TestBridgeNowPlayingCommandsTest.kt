package com.byd.clusternav.launcher.testbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 3.1.11 · lệnh `np` — op vắng = status, op lạ bị chặn ở tầng phân tích, auto_confirm đi qua tới tầng thi hành. */
class TestBridgeNowPlayingCommandsTest {

    private fun parse(vararg kv: Pair<String, Any?>) =
        TestBridgeCommands.parse(mapOf(TestBridgeCommands.EXTRA_CMD to "np", *kv), emptySet())

    @Test fun `op vang la status, on off hop le`() {
        val r = parse() as TestBridgeParse.Ok
        assertEquals("status", TestBridgeNowPlayingCommands.opOf(r.cmd.op))
        val on = parse(TestBridgeCommands.EXTRA_OP to "ON", TestBridgeCommands.EXTRA_AUTO_CONFIRM to true) as TestBridgeParse.Ok
        assertEquals("on", TestBridgeNowPlayingCommands.opOf(on.cmd.op))
        assertTrue(on.cmd.autoConfirm)
        assertTrue(parse(TestBridgeCommands.EXTRA_OP to "off") is TestBridgeParse.Ok)
    }

    @Test fun `op la bi chan`() {
        val r = parse(TestBridgeCommands.EXTRA_OP to "write")
        assertEquals(TestBridgeParse.Err(TestBridgeCommands.ERR_BAD_OP + "write"), r)
        assertTrue("np" in TestBridgeCommands.NAMES)
    }
}
