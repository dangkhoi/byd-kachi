package com.byd.clusternav.modules.voicekey

import com.byd.clusternav.launcher.CarControlPort
import com.byd.clusternav.launcher.CarStatus
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.KeyCtlTargets
import com.byd.clusternav.launcher.KeyCtlThrottle
import com.byd.clusternav.launcher.VoiceControlDispatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 · R-KC · KC2 — phím gán nút xe đi ĐÚNG đường thi hành của nút, giữ MỌI cổng an toàn (bài chạy thật) ════
 *
 * Dựng [KeyCtlRunner] với [VoiceControlDispatch] THẬT + cổng xe giả: thứ cần khoá là **hành vi** (*"có bắn lệnh
 * không, bắn cửa nào, nói câu nào"*), không đọc ra được từ chuỗi nguồn. Chuỗi phím → đích → [KeyCtlTargets.decode] →
 * [com.byd.clusternav.launcher.KeyCtlPlan] → `VoiceControlDispatch.run` → `actByKind` là đúng chuỗi của bản chạy, chỉ
 * thay đúng hai đầu Android (Toast · AppContainer).
 */
class KeyCtlSafetyTest {

    private class Port(private val reads: Map<String, Int?> = emptyMap()) : CarControlPort {
        val fired = ArrayList<String>()
        var accept = true
        var absentPart = false
        override fun toggle(id: String, on: Boolean): Boolean { fired += "toggle:$id:$on"; return accept }
        override fun step(id: String, value: Int): Boolean { fired += "step:$id:$value"; return accept }
        override fun cover(id: String, open: Boolean): Boolean { fired += "cover:$id:$open"; return accept }
        override fun select(id: String, index: Int): Boolean { fired += "select:$id:$index"; return accept }
        override fun press(id: String): Boolean { fired += "press:$id"; return accept }
        override fun readState(id: String): Int? = reads[id]
        override fun readStep(id: String): Int? = reads[id]
        override fun wiredOnThisCar(id: String): Boolean = !absentPart
        override fun partAbsentOnThisCar(id: String): Boolean = absentPart
    }

    private class Rig(reads: Map<String, Int?> = emptyMap(), speedKmh: Int? = null) {
        val port = Port(reads)
        val said = ArrayList<String>()
        private val car = CarStatus(drivetrain = CarStatus.Drivetrain(speedKmh = speedKmh))
        val runner = KeyCtlRunner(
            controls = VoiceControlDispatch(
                control = { port },
                state = { HomeUiState() },
                say = { said += it },
                freshCar = { id -> if (id == "speed") car else null },
                onUi = { it() },
                background = { it() },
            ),
            port = { port },
            say = { said += it },
        )

        fun press(spec: String, count: Int = 1) =
            runner.run(KeyCtlThrottle.Step.Fire(KeyCtlTargets.decode(spec) ?: error("mã hỏng $spec"), count))
    }

    // ── Cổng tốc độ của cốp: MỞ chỉ khi đứng yên, ĐÓNG luôn được (CtlSafetyPolicy.REQUIRES_STATIONARY) ─────────

    @Test
    fun `mo cop bang phim khi xe dang chay thi tu choi, khong ban lenh nao`() {
        val r = Rig(speedKmh = 30)
        r.press("ctl:trunk:open")
        assertEquals(emptyList<String>(), r.port.fired, "phím bấm nhầm lúc chạy KHÔNG được bung cốp")
        assertTrue(r.said.single().contains("chỉ mở được khi xe đang dừng"), "${r.said}")
    }

    @Test
    fun `dong cop khi xe dang chay van ban, mo cop khi dung thi ban`() {
        Rig(speedKmh = 30).also { it.press("ctl:trunk:close"); assertEquals(listOf("cover:trunk:false"), it.port.fired) }
        Rig(speedKmh = 0).also { it.press("ctl:trunk:open"); assertEquals(listOf("cover:trunk:true"), it.port.fired) }
    }

    // ── Đúng cửa theo kiểu nút (actByKind) ─────────────────────────────────────────────────────────────────

    @Test
    fun `gio cong mot nac doc muc that roi ghi step, mot lenh`() {
        val r = Rig(reads = mapOf("fan" to 2))
        r.press("ctl:fan:+1")
        assertEquals(listOf("step:fan:3"), r.port.fired)
        val gop = Rig(reads = mapOf("fan" to 2))
        gop.press("ctl:fan:+1", count = 4)
        assertEquals(listOf("step:fan:6"), gop.port.fired, "núm vặn 4 nấc gộp ⇒ MỘT lệnh, đủ 4 nấc")
    }

    @Test
    fun `dao kinh lai doc trang thai that`() {
        Rig(reads = mapOf("win_lf" to 0)).also { it.press("ctl:win_lf:flip"); assertEquals(listOf("toggle:win_lf:true"), it.port.fired) }
        Rig(reads = mapOf("win_lf" to 60)).also { it.press("ctl:win_lf:flip"); assertEquals(listOf("toggle:win_lf:false"), it.port.fired) }
        Rig().also {
            it.press("ctl:win_lf:flip")
            assertEquals(emptyList<String>(), it.port.fired, "đọc không được ⇒ KHÔNG đoán bằng cờ RAM")
            assertTrue(it.said.single().startsWith("Không đọc được Kính lái — gán Bật / Tắt riêng"), "${it.said}")
        }
    }

    @Test
    fun `ke tiep ghe mat va bam nut mot phat di dung cua`() {
        Rig(reads = mapOf("seatc" to 2)).also { it.press("ctl:seatc:next"); assertEquals(listOf("select:seatc:0"), it.port.fired) }
        Rig().also { it.press("ctl:windows_close_all:press"); assertEquals(listOf("press:windows_close_all"), it.port.fired) }
        Rig().also { it.press("ctl:sunshade:=2"); assertEquals(listOf("cover:sunshade:true"), it.port.fired.take(1)) }
    }

    // ── "Xe này không có" — cùng câu của giọng nói, không câu chung chung ────────────────────────────────────

    @Test
    fun `xe tu bao khong co bo phan thi noi dung cau do`() {
        val r = Rig()
        r.port.accept = false
        r.port.absentPart = true
        r.press("ctl:sunroof:on")
        assertEquals(listOf("toggle:sunroof:true"), r.port.fired, "cổng có-mặt nằm ở tầng ghi (HalBindingTable), cổng giả trả false")
        assertTrue(r.said.single().startsWith("✗"), "${r.said}")
        assertTrue(!r.said.single().contains("xe không nhận lệnh"), "xe không có bộ phận ≠ xe từ chối: ${r.said}")
    }

    @Test
    fun `ma khong con hop le thi bao, khong ban`() {
        val r = Rig()
        r.runner.run(KeyCtlThrottle.Step.Fire(KeyCtlTargets.parse("ctl:gone_ctl:on")!!, 1))
        assertEquals(emptyList<String>(), r.port.fired)
        assertTrue(r.said.single().contains("không còn hợp lệ"), "${r.said}")
    }
}
