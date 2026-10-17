package com.byd.clusternav.navigation

import com.byd.clusternav.launcher.testbridge.TestBridgeCommands
import com.byd.clusternav.launcher.testbridge.TestBridgeHudRoadCommands
import com.byd.clusternav.launcher.testbridge.TestBridgeParse
import com.byd.clusternav.navigation.HudPathNameGate.Channel
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.12 · HUD-ROADNAME-ALT-PATH — khoá cách bốn chế độ chia đường cho tên đường, và rằng chế độ A = ĐÚNG đường 3.1.11.
 */
class HudRoadNameRouteTest {

    // Khoá: mặc định A, và kế hoạch A trùng hằng Plan.DEFAULT mà keep-alive / nơi gọi không Context dùng.
    @Test fun `mac dinh A va A la duong 3_1_11`() {
        assertEquals(HudRoadNameMode.A, HudRoadNameMode.DEFAULT)
        val a = HudRoadNameRoute.plan(HudRoadNameMode.A, clusterDebugAvailable = true)
        assertEquals(HudRoadNameRoute.Plan.DEFAULT, a)
        assertEquals(HudPathName.MAX_BYTES, a.halMaxBytes)
        assertFalse(a.clusterDebug)
        // Payload HAL ở A giữ nguyên byte so với encode cũ (một tham số, trần 255).
        val road = "Nguyễn Văn Linh " + "x".repeat(200)
        assertArrayEquals(HudPathName.encode(road), HudPathName.encode(road, a.halMaxBytes))
        assertEquals(HudPathName.MAX_UNITS, HudPathName.fit(road, a.halMaxBytes).length)
    }

    @Test fun `bon che do chia duong`() {
        val ok = { m: HudRoadNameMode -> HudRoadNameRoute.plan(m, clusterDebugAvailable = true) }
        assertEquals(96, ok(HudRoadNameMode.A48).halMaxBytes)
        assertFalse(ok(HudRoadNameMode.A48).clusterDebug)
        assertEquals(48, HudPathName.fit("y".repeat(100), ok(HudRoadNameMode.A48).halMaxBytes).length)
        assertFalse(ok(HudRoadNameMode.B).hal, "B: không ghi tên qua HAL")
        assertTrue(ok(HudRoadNameMode.B).clusterDebug)
        assertTrue(ok(HudRoadNameMode.C).hal && ok(HudRoadNameMode.C).clusterDebug)
        assertEquals(HudPathName.MAX_BYTES, ok(HudRoadNameMode.C).halMaxBytes)
        HudRoadNameMode.values().forEach { assertFalse(ok(it).degraded) }
    }

    // Khoá degrade-to-resolvable: B/C mà xe không có ClusterDebug ⇒ lùi về A, có cờ degraded (UI nói rõ), không mất tên.
    @Test fun `B C khong kha dung lui ve A co co`() {
        for (m in listOf(HudRoadNameMode.B, HudRoadNameMode.C)) {
            val p = HudRoadNameRoute.plan(m, clusterDebugAvailable = false)
            assertTrue(p.degraded)
            assertFalse(p.clusterDebug)
            assertEquals(HudPathName.MAX_BYTES, p.halMaxBytes)
            assertEquals(m, p.mode)
        }
        assertFalse(HudRoadNameRoute.plan(HudRoadNameMode.A48, clusterDebugAvailable = false).degraded)
    }

    @Test fun `ma luu`() {
        assertEquals(listOf("a", "a48", "b", "c"), HudRoadNameMode.values().map { it.code })
        assertEquals(HudRoadNameMode.A48, HudRoadNameMode.of(" A48 "))
        assertNull(HudRoadNameMode.of("d"))
        assertNull(HudRoadNameMode.of(null))
        assertTrue(HudRoadNameKeys.MODE_KEY in HudRoadNameKeys.DEVICE_KEYS)
    }

    // Khoá nhịp: kênh B dùng CHUNG cổng R9 — keep-alive không gửi, tên không đổi không gửi, hết dẫn ⇒ biết có gì để xoá.
    @Test fun `kenh CLUSTER_DEBUG theo nhip R9`() {
        val g = HudPathNameGate()
        assertFalse(g.sentSomething(Channel.CLUSTER_DEBUG))
        assertFalse(g.shouldSend(Channel.CLUSTER_DEBUG, "Láng", keepAlive = true))
        assertTrue(g.shouldSend(Channel.CLUSTER_DEBUG, "Láng", keepAlive = false))
        g.onResult(Channel.CLUSTER_DEBUG, "Láng", "0")
        assertFalse(g.shouldSend(Channel.CLUSTER_DEBUG, "Láng", keepAlive = false))
        assertTrue(g.sentSomething(Channel.CLUSTER_DEBUG))
        assertFalse(g.sentSomething(Channel.DOMESTIC), "kênh riêng")
        g.onResult(Channel.CLUSTER_DEBUG, "Kim Mã", "unavail:NOT_INSTALLED")
        assertTrue(g.shouldSend(Channel.CLUSTER_DEBUG, "Kim Mã", keepAlive = false), "gửi hỏng ⇒ lần sau thử lại")
        g.reset()
        assertFalse(g.sentSomething(Channel.CLUSTER_DEBUG))
    }

    @Test fun `lenh hudroad`() {
        fun parse(vararg kv: Pair<String, Any?>) = TestBridgeCommands.parse(mapOf("cmd" to "hudroad", *kv), emptySet())
        val st = parse() as TestBridgeParse.Ok
        assertNull(TestBridgeHudRoadCommands.modeOf(st.cmd.op))
        val b = parse("op" to "B", "auto_confirm" to true) as TestBridgeParse.Ok
        assertEquals(HudRoadNameMode.B, TestBridgeHudRoadCommands.modeOf(b.cmd.op))
        assertTrue(TestBridgeHudRoadCommands.needsConfirm(b.cmd.op))
        assertTrue(b.cmd.autoConfirm)
        assertFalse(TestBridgeHudRoadCommands.needsConfirm("a48"))
        assertTrue(TestBridgeHudRoadCommands.needsConfirm("c"))
        assertTrue(parse("op" to "x") is TestBridgeParse.Err)
        assertTrue("hudroad" in TestBridgeCommands.NAMES)
    }
}
