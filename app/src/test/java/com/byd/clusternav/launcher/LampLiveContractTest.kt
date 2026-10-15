package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 TURN → LAMPLIVE — dây đèn THẬT → bảng Đèn / xe 3D. Khoá quyết định nguồn/vòng đời (handoff `kachi-301-stage-7-LAMPLIVE-done.md`):
 *  1. MỘT nguồn: chỉ `HalSignalClient` (main) và `DummyCarReceiver` (vehicleTest) ghi [LampLive]; MỘT client 19322, dựng ở
 *     MỘT chỗ (`HalSignalHub`).
 *  2. MỘT chủ vòng đời helper: `HalHelperLauncher.ensure` chỉ được nối vào `HalSignalHub` (factory ở `AppContainer`); camera và
 *     bảng Đèn chỉ XIN qua `want(Source…)`. Owner 13/10 *"nối luôn đi, đâu cần camera xi nhan bật mới sáng"* — đảo quyết định
 *     §2 của handoff TURN (trước: bảng KHÔNG được khởi helper). Camera vẫn xin đúng sau cổng pref.
 *  3. Vòng đời: hộp chỉ được THU trong `collectHome`, bên trong `repeatOnLifecycle(STARTED)` — HOME dừng ⇒ không ai thu.
 *  4. Số giả đi đúng dây thật: `DummyCarReceiver` phát vào hộp, `fill` không còn đặt `leftTurn/rightTurn`.
 *
 * Thử ĐỎ [ĐO 13/10]: bỏ `.combine(LampLive.shared.state` ở `KachiHomeWiring` ⇒ bài 3 ĐỎ.
 */
class LampLiveContractTest {

    private fun kotlinUnder(dir: String): List<java.nio.file.Path> {
        if (!SourceRoots.exists(dir)) return emptyList()
        return Files.walk(SourceRoots.path(dir)).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
    }

    private fun writers(dir: String): List<String> = kotlinUnder(dir)
        .filter { Regex("""\.(publish|clear)\(""").containsMatchIn(Files.readString(it)) && Files.readString(it).contains("LampLive") }
        .filter { val c = Files.readString(it); c.contains("live.publish(") || c.contains("LampLive.shared.publish(") }
        .map { it.fileName.toString() }.sorted()

    @Test
    fun `mot nguon - chi client socket va so gia ghi hop`() {
        assertEquals(listOf("HalSignalClient.kt"), writers("src/main"))
        assertEquals(listOf("DummyCarReceiver.kt"), writers("src/vehicleTest"))
        val sockets = kotlinUnder("src/main").filter { Files.readString(it).contains("HalHelperLauncher.PORT") }
            .map { it.fileName.toString() }.sorted()
        // HalHelperLauncher tự dò cổng bằng `PORT` (không tiền tố) — chỉ là phép dò sống 400 ms, không nghe dòng.
        assertEquals(listOf("HalSignalClient.kt"), sockets, "không client 19322 thứ hai")
        val builders = kotlinUnder("src/main").filter { it.fileName.toString() != "HalSignalClient.kt" }
            .filter { Regex("""\bHalSignalClient\(""").containsMatchIn(SourceRoots.codeOf(rel(it))) }.map { it.fileName.toString() }
        assertEquals(listOf("HalSignalHub.kt"), builders, "client chỉ được dựng trong hub")
        assertEquals(1, Regex("""\bHalSignalClient\(""").findAll(SourceRoots.codeOf(HUB)).count(), "MỘT client mỗi hub")
    }

    @Test
    fun `mot chu vong doi helper - camera va bang Den chi xin qua hub`() {
        val callers = kotlinUnder("src/main").filter { SourceRoots.codeOf(rel(it)).contains("HalHelperLauncher.ensure(") }
            .map { it.fileName.toString() }
        assertEquals(listOf("AppContainer.kt"), callers, "ensure chỉ được NỐI vào hub (factory), không ai gọi thẳng")
        val app = SourceRoots.codeOf(APP)
        assertTrue(app.contains("HalSignalHub(ensure = { HalHelperLauncher.ensure(app) })"))
        // Bảng Đèn: nhu cầu H1 của màn chính → Watch → hub (một đường); cổng màn từ CÙNG lượt hỏi màn của vòng poll.
        // 3.1.6 HAL-EVENTS: cùng lượt công bố nhu cầu báo thêm `carEvents` (kế hoạch sự kiện) — vẫn MỘT đường từ nhu cầu màn.
        assertTrue(app.contains("CarDataDemand.Holder { lampWatch.demand(it); carEvents.screen(it, carDemand.controls()) }"))
        assertTrue(app.contains("halSignal?.want(com.byd.clusternav.launcher.HalSignalDemand.Source.LAMP_WIDGET, on)"))
        assertTrue(app.contains("awake = { screenAwake().also { lampWatch.screen(it); carEvents.lit(it) } }"))
        // Camera: vẫn sau cổng pref, chỉ XIN/THÔI.
        val ctrl = SourceRoots.codeOf(CTRL)
        assertTrue(ctrl.contains("if (Prefs.cameraSignalEnabled(appCtx)) ensureSignal() else release()"))
        assertTrue(SourceRoots.body(ctrl, "private fun ensureSignal(").contains("hub.want(HalSignalDemand.Source.CAMERA, true)"))
        assertTrue(SourceRoots.body(ctrl, "fun release(").contains("hub.want(HalSignalDemand.Source.CAMERA, false)"))
        assertFalse(SourceRoots.codeOf(WIRING).contains("ensureSignal"), "HOME không tự khởi socket")
        // Hub là chỗ DUY NHẤT gọi start/stop client.
        val starts = kotlinUnder("src/main").filter { Regex("""\b(client|signal)\.(start|stop)\b""").containsMatchIn(SourceRoots.codeOf(rel(it))) }
            .map { it.fileName.toString() }
        assertEquals(listOf("HalSignalHub.kt"), starts)
    }

    @Test
    fun `hop chi duoc thu khi HOME STARTED`() {
        val w = SourceRoots.codeOf(WIRING)
        val body = SourceRoots.body(w, "internal fun collectHome(")
        val at = body.indexOf(".combine(LampLive.shared.state, LampLive::overlay)")
        assertTrue(at > 0, "trạng thái socket phải đè vào dòng trạng thái xe của HOME")
        val lastStarted = body.lastIndexOf("repeatOnLifecycle(Lifecycle.State.STARTED)", at)
        assertTrue(lastStarted in 0 until at, "chỉ thu bên trong repeatOnLifecycle(STARTED)")
        assertTrue(body.indexOf("viewModel.setCarStatus(CarStatusTap.apply(it))", at) > at)
        val refs = kotlinUnder("src/main").filter { SourceRoots.codeOf(rel(it)).contains("LampLive.shared.state") }
        assertEquals(listOf("KachiHomeWiring.kt"), refs.map { it.fileName.toString() }, "không chỗ thu thứ hai")
    }

    @Test
    fun `so gia di dung day that`() {
        val d = SourceRoots.codeOf("src/vehicleTest/java/com/byd/clusternav/DummyCarReceiver.kt")
        assertTrue(d.contains("LampLive.shared.publish(turnL, turnR)"))
        assertTrue(d.contains("LampLive.shared.clear()"))
        assertFalse(d.contains("leftTurn ="), "fill không đặt xi-nhan — đi qua hộp socket")
    }

    private fun rel(p: java.nio.file.Path): String {
        val s = p.toString().replace('\\', '/')
        return s.substring(s.indexOf("src/"))
    }

    private companion object {
        const val CTRL = "src/main/java/com/byd/clusternav/launcher/camera/CameraSignalController.kt"
        const val HUB = "src/main/java/com/byd/clusternav/launcher/camera/HalSignalHub.kt"
        const val APP = "src/main/java/com/byd/clusternav/AppContainer.kt"
        const val WIRING = "src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt"
    }
}
