package com.byd.clusternav.comfort

import com.byd.clusternav.AppContainer
import com.byd.clusternav.launcher.HalGateway
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.6 · SEAT-AUTO-OFF-REAPPLY — nối dây của bộ bật-lại-ghế (`docs/diagnostics/seat-vent-auto-off-2026-10-14.md` §13).
 *
 * Khoá ba điều CLAUDE.md §8 đòi (compile xanh ≠ code chạy):
 *  1. MỘT đường ghi HAL cho ghế: `BydHal.callNamedInt` chỉ xuất hiện một lần trong `SeatComfortApplier` (`writeSeat`), và bộ canh
 *     bật lại qua đúng đường đó — không tự gọi HAL;
 *  2. lượt áp lúc lên nguồn THẬT SỰ giao ghế cho bộ canh (`SeatAutoOffWatch.start` có call site);
 *  3. MỌI lời ghi ghế của Kachi tắt bộ canh: chạm ghế (`applySeat`), áp lại hàng loạt (`applyNow`), và sáu cửa ghi của
 *     `WakeOnWriteControl` (ô nút · giọng nói · phím) — cửa sau đo bằng đồ thị DI thật (`AppContainer.carControl`).
 */
class SeatAutoOffWiringContractTest {

    private val applier = SourceRoots.codeOf("src/main/java/com/byd/clusternav/comfort/SeatComfortApplier.kt")
    private val watch = SourceRoots.codeOf("src/main/java/com/byd/clusternav/comfort/SeatAutoOffWatch.kt")

    @Test
    fun `mot loi ghi HAL duy nhat cho ghe, bo canh di qua no`() {
        assertEquals(1, Regex("""BydHal\.callNamedInt\(""").findAll(applier).count(), "ghế chỉ có MỘT lời ghi HAL (`writeSeat`)")
        assertEquals(3, Regex("""\bwriteSeat\(dev,""").findAll(applier).count(), "bulk · chạm · bật lại đều đi `writeSeat`")
        assertTrue(SourceRoots.body(applier, "fun reapplySeat(").contains("writeSeat("))
        assertFalse(watch.contains("callNamedInt") || watch.contains("BydHal."), "bộ canh không được tự ghi HAL")
        assertEquals(1, Regex("""SeatComfortApplier\.reapplySeat\(""").findAll(watch).count(), "bộ canh bật lại đúng một chỗ")
        // Soát Pass 6: lệnh ghi tự khởi phát đi sau cổng màn FAIL-CLOSED (không hỏi được màn ⇒ không ghi).
        assertTrue(SourceRoots.body(watch, "private fun reapply(").contains("ScreenLit.readStrict(app)"), "bật lại ghế phải qua ScreenLit.readStrict")
    }

    @Test
    fun `luot ap luc len nguon giao ghe cho bo canh, luot khac thi khong`() {
        assertTrue(SourceRoots.body(applier, "private fun launch(").contains("SeatAutoOffWatch.start("))
        assertTrue(SourceRoots.body(applier, "fun applyOnStart(").contains("poweredAt = powered"))
        assertTrue(SourceRoots.body(applier, "fun applyNow(").contains("poweredAt = null"), "đổi chế độ/hồ sơ không phải lần lên nguồn")
    }

    @Test
    fun `cham ghe va ap lai hang loat tat bo canh`() {
        assertTrue(SourceRoots.body(applier, "fun applySeat(").contains("SeatAutoOffWatch.noteKachiWrite(ctx, seatIndex)"))
        assertTrue(SourceRoots.body(applier, "fun applyNow(").contains("SeatAutoOffWatch.noteKachiWrite(ctx, seatIndex = null)"))
    }

    @Test
    fun `day that cua AppContainer noi WakeOnWriteControl vao bo canh`() {
        val container = SourceRoots.codeOf("src/main/java/com/byd/clusternav/AppContainer.kt")
        assertTrue(container.contains("SeatAutoOffWatch.onControlWrite(app, id)"), "build() phải tiêm móc của bộ canh")
        assertTrue(container.contains("WakeOnWriteControl(CarControlAdapter(halBindingTable), carDataAdapter, onControlWrite)"))
        val wake = SourceRoots.body(SourceRoots.codeOf("src/main/java/com/byd/clusternav/WakeOnWriteControl.kt"), "private fun wake(")
        assertTrue(wake.contains("onWrite(id)"), "móc phải nằm trong `wake` — sáu cửa ghi cùng gọi nó")
    }

    /** Gateway rỗng: mọi lệnh ghi trả `false`, nhưng cửa ghi VẪN chạy `wake` ⇒ móc vẫn phải nhận mã nút. */
    private object NoCar : HalGateway {
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? = null
        override fun namedInt(deviceFqn: String, method: String, args: IntArray): Long? = null
        override fun featureGet(deviceFqn: String, id: Int): String? = null
        override fun featureSet(deviceFqn: String, id: Int, value: Int): Long? = null
        override fun settingGet(key: String): String? = null
        override fun settingSet(key: String, value: Int): Long? = null
        override fun localGet(target: String, method: String, arg: Int?): String? = null
        override fun localSet(target: String, method: String, args: IntArray): Boolean = false
    }

    @Test
    fun `sau cua ghi cua carControl deu bao bo canh`() {
        val seen = ArrayList<String>()
        val control = AppContainer(
            shellTransportInit = { error("không cần") },
            windowDispatcherInit = { error("không cần") },
            workspaceRepositoryInit = { error("không cần") },
            inputDaemonClientInit = { error("không cần") },
            carGatewayInit = { NoCar },
            onControlWrite = { seen += it },
        ).carControl
        control.select("seatc", 1)
        control.select("seath_r", 0)
        control.toggle("pm25", true)
        control.step("temp", 22)
        control.cover("sunroof", true)
        control.coverLevel("sunroof", 50)
        control.press("horn")
        assertEquals(listOf("seatc", "seath_r", "pm25", "temp", "sunroof", "sunroof", "horn"), seen)
        // và móc của prod chỉ để lại dấu cho nút GHẾ, đúng ghế
        assertEquals(listOf(0, 1), seen.take(2).map { SeatControls.seatIndexOf(it) })
        assertTrue(seen.drop(2).all { SeatControls.seatIndexOf(it) == null })
    }
}
