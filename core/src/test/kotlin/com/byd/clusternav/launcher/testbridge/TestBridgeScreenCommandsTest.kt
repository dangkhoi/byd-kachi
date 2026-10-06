package com.byd.clusternav.launcher.testbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 wave 2B · DIAG-SCREENS-UNREACHABLE — lệnh cầu `diag_screen` (spec `kachi-293-misc.html` §7 OQ3): danh sách TRẮNG
 * hai tên màn chặn ở TẦNG PHÂN TÍCH (tên lạ không bao giờ thành một lệnh chạy được), không phải lệnh "bật" gì.
 */
class TestBridgeScreenCommandsTest {

    private fun parse(vararg kv: Pair<String, Any?>) = TestBridgeCommands.parse(mapOf(*kv), emptySet())

    @Test fun `lenh co trong bang, doi ten man, chi hai ten`() {
        assertTrue(TestBridgeScreenCommands.NAMES.all { it in TestBridgeCommands.NAMES })
        assertEquals(TestBridgeCommands.NAMES.size, TestBridgeCommands.NAMES.toSet().size, "mã lệnh không trùng")
        assertEquals(listOf("diag", "vietmap"), TestBridgeScreenCommands.TARGETS)
        assertEquals(TestBridgeParse.Err("missing_extra:name"), parse("cmd" to "diag_screen"))
        val ok = parse("cmd" to "diag_screen", "name" to " VietMap ") as TestBridgeParse.Ok
        assertEquals("vietmap", TestBridgeScreenCommands.targetOf(ok.cmd.arg))
        assertTrue(parse("cmd" to "diag_screen", "name" to "diag") is TestBridgeParse.Ok)
    }

    @Test fun `ten man la bi chan o tang phan tich`() {
        listOf("settings", "../diag", "DiagActivity", "cast").forEach {
            assertEquals(TestBridgeParse.Err("bad_screen:$it"), parse("cmd" to "diag_screen", "name" to it), it)
            assertNull(TestBridgeScreenCommands.targetOf(it))
        }
        // Lệnh khác không bị phép kiểm này chạm (kể cả lệnh `diag` cũ — chụp ClusterDiag, cùng chữ khác lệnh).
        assertTrue(parse("cmd" to "diag") is TestBridgeParse.Ok)
        assertTrue(parse("cmd" to "camera", "name" to "left") is TestBridgeParse.Ok)
    }

    @Test fun `khong phai lenh bat che do kiem thu`() {
        val switchLike = setOf("enable", "on", "unlock", "grant", "allow")
        assertFalse(TestBridgeScreenCommands.NAMES.any { it in switchLike })
    }
}
