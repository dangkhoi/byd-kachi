package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.CarControlPort
import com.byd.clusternav.launcher.CarStatus
import com.byd.clusternav.launcher.ControlLastSent
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.VoiceControlDispatch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * ═══ 3.01 SAFETY × `:wake` — cổng cốp fail-safe phải hỏi đọc TƯƠI trước, không quyết bằng `CarStatus()` rỗng ═══════════
 *
 * Trong `:wake` `state()` là ảnh chụp `grammar().homeState()` ⇒ `carStatus` = `CarStatus()` RỖNG (xem
 * [VoiceWakeFakeStateContractTest]). Luật mới (owner 13/10, `docs/_handoff/kachi-301-stage-7-SAFETY-done.md`): không chứng
 * minh được xe đứng ⇒ CHẶN mở cốp. Nếu cổng đọc ảnh chụp trước, *"Hey Kachi, mở cốp"* lúc ĐỖ sẽ LUÔN bị chặn.
 * Bài chạy [VoiceControlDispatch.run] THẬT với `state = { HomeUiState() }` (đúng hình `:wake`) và `freshCar` giả.
 */
class VoiceWakeTrunkMotionTest {

    private class Port : CarControlPort {
        val fired = ArrayList<String>()
        override fun toggle(id: String, on: Boolean): Boolean { fired += "toggle:$id:$on"; return true }
        override fun step(id: String, value: Int): Boolean { fired += "step:$id:$value"; return true }
        override fun cover(id: String, open: Boolean): Boolean { fired += "cover:$id:$open"; return true }
        override fun select(id: String, index: Int): Boolean { fired += "select:$id:$index"; return true }
        override fun press(id: String): Boolean { fired += "press:$id"; return true }
        override fun readState(id: String): Int? = null
        override fun readStep(id: String): Int? = null
        override fun wiredOnThisCar(id: String): Boolean = true
        override fun partAbsentOnThisCar(id: String): Boolean = false
    }

    /** Một lượt *"mở cốp"* trong hình `:wake`; [fresh] = kết quả đọc tươi theo datum (`null` = đọc hụt). */
    private fun openTrunk(fresh: (String) -> CarStatus?): Pair<List<String>, List<String>> {
        val port = Port()
        val said = ArrayList<String>()
        VoiceControlDispatch(
            control = { port },
            state = { HomeUiState() },   // `:wake`: ảnh chụp — carStatus RỖNG
            say = { said += it },
            freshCar = fresh,
            onUi = { it() },
            background = { it() },
            lang = { Lang.VI },
        ).run(VoiceIntent.Control("trunk", 1)) {}
        return port.fired to said
    }

    private fun car(kmh: Int?, gear: String?) = CarStatus(drivetrain = CarStatus.Drivetrain(speedKmh = kmh, gear = gear))

    private val stopToOpen = VoiceReply.notWhileMoving(VoiceIntent.Control("trunk", 1), Lang.VI)

    private fun freshProcessMemory() =
        ControlRegistry.ALL.forEach { ControlLastSent.shared.record(it.id, ControlLastSent.startIndex(it.id)) }

    @BeforeEach fun before() = freshProcessMemory()

    @AfterEach fun after() = freshProcessMemory()

    @Test
    fun `wake - anh chup rong + toc do tuoi 0 thi MO`() {
        val (fired, _) = openTrunk { id -> if (id == "speed") car(0, null) else null }
        assertEquals(listOf("cover:trunk:true"), fired, "đỗ, đọc tươi được 0 km/h ⇒ mở — ảnh chụp rỗng không được thắng")
    }

    @Test
    fun `wake - doc toc do hut + so P tuoi thi MO`() {
        val (fired, _) = openTrunk { id -> if (id == "gear") car(null, "P") else null }
        assertEquals(listOf("cover:trunk:true"), fired, "tốc độ hụt, số P đọc tươi ⇒ mở")
    }

    @Test
    fun `wake - doc toc do hut + khong doc duoc so thi CHAN`() {
        val (fired, said) = openTrunk { null }
        assertEquals(emptyList<String>(), fired, "không chứng minh được xe đứng ⇒ không lệnh nào đi (fail-safe)")
        assertEquals(listOf(stopToOpen), said, "câu từ chối = câu cổng chạm")
    }

    @Test
    fun `wake - toc do tuoi duong thi CHAN`() {
        val (fired, said) = openTrunk { id -> if (id == "speed") car(30, "D") else null }
        assertEquals(emptyList<String>(), fired)
        assertEquals(listOf(stopToOpen), said)
    }
}
