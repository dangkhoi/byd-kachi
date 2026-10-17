package com.byd.clusternav.modules.hal

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * 3.1.12 · HUD-ROADNAME-ALT-PATH — dây nối mà JVM không dựng được (HAL, PackageManager, broadcast). Khoá CLAUDE.md §8 (hàm mới
 * phải CÓ chỗ gọi) và §4 (phạm vi lệnh): đường B chỉ đi qua MỘT nơi gửi, gói bị allow-list kiểm ngay trước broadcast, chỉ
 * extra `normal`, chỉ receiver ClusterDebug; mũi tên/cự ly/status không đổi theo chế độ; hết dẫn ⇒ gửi tên xoá.
 */
class HudRoadNameWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$rel")
    private val hal by lazy { code("modules/hal/BydHal.kt") }
    private val tx by lazy { code("modules/hal/HudRoadNameTransport.kt") }

    @Test fun `writeNavFrame doc ke hoach roi chia duong ten`() {
        val w = SourceRoots.body(hal, "fun writeNavFrame(")
        assertTrue("val roadPlan = if (keepAlive) HudRoadNameRoute.Plan.DEFAULT else HudRoadNameTransport.plan(ctx)" in w, w)
        assertTrue("BydHalContentPush.pushPathName(instr, road, keepAlive, rc, roadPlan)" in w)
        assertTrue("if (roadPlan.hal && road.isNotBlank() && BydHalContentPush.pathNameGate.shouldSend(HudPathNameGate.Channel.SDK" in w)
        assertTrue("HudPathName.fit(road, roadPlan.halMaxBytes)" in w, "SDK sendNextPathName theo cùng trần của chế độ")
        assertTrue("if (roadPlan.clusterDebug) HudRoadNameTransport.pushClusterDebug(ctx, road, keepAlive, rc)" in w)
        assertTrue("HudRoadNameTransport.noteSent(ctx, roadPlan, road, rc.substring(roadRcAt))" in w, "một dòng nhật ký mỗi lần tên đổi")
        // Mũi tên + cự ly + status KHÔNG bị gate theo chế độ tên đường.
        listOf(
            "if (!keepAlive) w(\"INSTRUMENT_SEND_NAVI_STATUS_SET\", 2)",
            "w(\"INSTRUMENT_GUIDE_INFO_SIMPLE_SET\", icon)",
            "w(\"INSTRUMENT_FRONT_CROSSING_DISTANCE_SET\", segMeters)",
        ).forEach { assertTrue(it in w, it) }
        assertEquals(1, Regex("roadPlan\\.clusterDebug").findAll(w).count())
        // Thứ tự: B đi SAU khối HAL (đường mới xuống cuối — CLAUDE.md §6), trước icon/cự ly.
        val raw = w.indexOf("pushPathName(instr, road, keepAlive, rc, roadPlan)")
        val cd = w.indexOf("HudRoadNameTransport.pushClusterDebug(")
        val icon = w.indexOf("w(\"INSTRUMENT_GUIDE_INFO_SIMPLE_SET\", icon)")
        assertTrue(raw in 0 until cd && cd < icon)
    }

    @Test fun `pushPathName mac dinh la duong A va B thi khong ghi HAL`() {
        val push = code("modules/hal/BydHalContentPush.kt")
        assertTrue("plan: HudRoadNameRoute.Plan = HudRoadNameRoute.Plan.DEFAULT" in push)
        val body = SourceRoots.body(push, "fun pushPathName(")
        val gate = body.indexOf("if (!plan.hal) return")
        assertTrue(gate in 0 until body.indexOf("HudPathName.encode(road, plan.halMaxBytes)"), body)
        assertTrue(gate < body.indexOf("cachedSetBytes("), "B: cổng đứng trước mọi lượt ghi HAL")
    }

    @Test fun `het dan gui ten xoa truoc khi xa cong`() {
        val c = SourceRoots.body(hal, "fun clearNavFrame(ctx: Context): String")
        val clear = c.indexOf("HudRoadNameTransport.clearIfSent(ctx, rc)")
        val reset = c.indexOf("BydHalContentPush.pathNameGate.reset()")
        assertTrue(clear in 0 until reset, c)
        val body = SourceRoots.body(tx, "fun clearIfSent(ctx: Context, rc: StringBuilder)")
        assertTrue("sentSomething(HudPathNameGate.Channel.CLUSTER_DEBUG)" in body && "ClusterDebugRoadName.CLEAR_NAME" in body, body)
    }

    // Khoá §4: một nơi gửi, gói kiểm allow-list NGAY trước broadcast, receiver tường minh, chỉ extra `normal`.
    @Test fun `noi gui duy nhat co allow-list va pham vi tuong minh`() {
        val send = SourceRoots.body(tx, "private fun send(ctx: Context, road: String): String")
        val allow = send.indexOf("if (!ClusterDebugRoadName.allowed(bytes)) return \"deny\"")
        val bc = send.indexOf("sendBroadcast(intent)")
        assertTrue(allow in 0 until bc, send)
        assertTrue("availability(ctx, fresh = true)" in send, "đọc PackageManager MỚI lúc gửi")
        assertTrue("ComponentName(ClusterDebugRoadName.PACKAGE, ClusterDebugRoadName.RECEIVER)" in send)
        assertTrue(".putExtra(ClusterDebugRoadName.EXTRA, ClusterDebugRoadName.hex(bytes))" in send)
        listOf("wholeFrame", "\"am ", "\"wm ", "service call", "--display", "sendOrderedBroadcast", "FLAG_INCLUDE_STOPPED").forEach {
            assertFalse(it in tx, "không: $it")
        }
        val senders = Files.walk(SourceRoots.path("src/main/java/com/byd/clusternav")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .filter { "BroadcastReceiverCAN" in it.toFile().readText() || "ClusterDebugRoadName.RECEIVER" in it.toFile().readText() }
                .map { it.fileName.toString() }.toList()
        }
        assertEquals(listOf("HudRoadNameTransport.kt"), senders, "chỉ một tệp chạm receiver ClusterDebug")
    }

    // Không bao giờ ghi CHỮ của tên đường vào nhật ký mới.
    @Test fun `nhat ky khong co ten duong`() {
        Regex("journal\\(ctx, \"([^\"]*)\"\\)").findAll(tx).forEach { m ->
            assertFalse("\$road" in m.value || "\${road" in m.value, m.value)
        }
        val note = SourceRoots.body(tx, "fun noteSent(")
        assertTrue("units=\$units" in note && "mode=\${plan.mode.code}" in note, note)
    }

    @Test fun `cai dat va cau kiem thu di cung mot duong`() {
        assertTrue("hudRoad.build(body)" in code("launcher/SettingsSectionsNav.kt"))
        val rows = code("launcher/SettingsHudRoadNameRows.kt")
        assertTrue("bridge().setHudRoadNameMode(mode)" in rows && "bridge().hudRoadNameAvailability()" in rows)
        val br = code("launcher/ClusterNavBridgeHudRoadName.kt")
        assertTrue("HudRoadNameTransport.setMode(app, mode, source = \"settings\")" in br)
        assertTrue("HudRoadNameTransport.setMode(ctx, mode, source = \"testbridge\")" in br)
        val tb = code("launcher/testbridge/TestBridgeHudRoad.kt")
        assertTrue("HudRoadNameControl.setMode(app, mode)" in tb && "cmd.autoConfirm" in tb && "AUTO-CONFIRM" in tb)
        assertTrue("TestBridgeHudRoadCommands.HUDROAD -> TestBridgeHudRoad.run(app, cmd, reply)" in code("launcher/testbridge/TestBridgeNoHome.kt"))
        val set = SourceRoots.body(tx, "fun setMode(ctx: Context, mode: HudRoadNameMode, source: String): Availability?")
        assertTrue(set.indexOf("availability(ctx, fresh = true)") in 0 until set.indexOf("Prefs.setHudRoadNameMode(ctx, mode)"), "B/C kiểm khả dụng TRƯỚC khi lưu")
    }

    @Test fun `manifest khai queries cho ClusterDebug`() {
        assertTrue("<package android:name=\"com.byd.clusterdebug\" />" in SourceRoots.text("src/main/AndroidManifest.xml"))
    }
}
