package com.byd.clusternav.modules.voicekey

import com.byd.clusternav.launcher.CarControlPort
import com.byd.clusternav.launcher.CarStatus
import com.byd.clusternav.launcher.ControlLastSent
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.ControlTileLogic
import com.byd.clusternav.launcher.ControlTileState
import com.byd.clusternav.launcher.HomeUiState
import com.byd.clusternav.launcher.KeyCtlTargets
import com.byd.clusternav.launcher.KeyCtlThrottle
import com.byd.clusternav.launcher.VoiceControlDispatch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
        // 2.87 · R-FL2 (thay khẳng định 2.86 "đọc không được ⇒ không bắn"): owner 03/10 chấp nhận Đảo theo LỆNH CUỐI khi
        // không đọc được xe (spec §4.3) ⇒ bảng chưa có gì = đóng ⇒ MỞ, và bảng ghi lại sau khi ghi thành công.
        sent.record("win_lf", 0)
        Rig(reads = mapOf("win_lf" to 60)).also { it.press("ctl:win_lf:flip"); assertEquals(0, sent.index("win_lf"), "đọc được ⇒ xe thắng bảng") }
        Rig().also {
            it.press("ctl:win_lf:flip")
            assertEquals(listOf("toggle:win_lf:true"), it.port.fired, "đọc không được ⇒ lệnh cuối (đóng) ⇒ MỞ")
            assertEquals(1, sent.index("win_lf"), "ghi thành công ⇒ bảng nhớ MỞ")
            it.press("ctl:win_lf:flip")
            assertEquals(listOf("toggle:win_lf:true", "toggle:win_lf:false"), it.port.fired, "bấm lại ⇒ ĐÓNG")
        }
    }

    // ── 2.87 · R-FL1/R-FL2 — MỘT phím Đảo cho cốp (owner 03/10 "phải 2 nút à?"), dùng CHUNG bảng với ô trên màn ────

    /** Bảng THẬT của tiến trình (`ControlLastSent.shared`) — mỗi bài bắt đầu và kết thúc ở trạng thái "vừa bật". */
    private val sent = ControlLastSent.shared

    private fun freshProcessMemory() = ControlRegistry.ALL.forEach { sent.record(it.id, ControlLastSent.startIndex(it.id)) }

    @BeforeEach
    fun before() = freshProcessMemory()

    @AfterEach
    fun after() = freshProcessMemory()

    @Test
    fun `phim dao cot khong readKey mo roi dong, chi ghi bang khi thanh cong`() {
        val r = Rig(speedKmh = 0)
        r.press("ctl:trunk:flip")
        assertEquals(listOf("cover:trunk:true"), r.port.fired, "tiến trình vừa bật ⇒ coi cốp đóng ⇒ MỞ")
        assertEquals(1, sent.index("trunk"))
        r.press("ctl:trunk:flip")
        assertEquals(listOf("cover:trunk:true", "cover:trunk:false"), r.port.fired, "Đảo lần hai ⇒ ĐÓNG — về như cũ")
        assertEquals(0, sent.index("trunk"))
        // Xe từ chối (ghi trả false) ⇒ bảng KHÔNG đổi ⇒ lần Đảo kế vẫn MỞ, không "nhảy" sang ĐÓNG một cốp chưa từng mở.
        r.port.accept = false
        r.press("ctl:trunk:flip")
        assertEquals("cover:trunk:true", r.port.fired.last())
        assertEquals(0, sent.index("trunk"), "chỉ ghi lệnh cuối khi lệnh THÀNH CÔNG")
    }

    /** Hợp đồng ô ↔ phím: ô mở cốp (đúng phép ghi của ô — `ControlTileState.setSel`) ⇒ phím Đảo ĐÓNG ⇒ cú chạm kế MỞ. */
    @Test
    fun `o va phim dung chung mot bang lenh cuoi`() {
        val tile = ControlTileState.shared
        tile.setSel("trunk", 1)                                  // cú chạm ô cốp: 0 → 1 (lạc quan; ghi thành công giữ nguyên)
        Rig(speedKmh = 30).also {
            it.press("ctl:trunk:flip")
            assertEquals(listOf("cover:trunk:false"), it.port.fired, "ô đã mở ⇒ phím Đảo ĐÓNG (đóng lúc chạy luôn được)")
        }
        assertEquals(0, tile.sel("trunk"), "ô đọc CÙNG bảng ⇒ thấy cốp đã đóng")
        assertEquals(1, ControlTileLogic.nextSelectIndex(tile.sel("trunk"), 2), "cú chạm ô kế tiếp ⇒ MỞ, không lặp ĐÓNG")
        // TOGGLE không readKey (đèn đọc): ô bật ⇒ phím Đảo tắt ⇒ ô thấy tắt.
        tile.setOn("readl", true)
        Rig().also { it.press("ctl:readl:flip"); assertEquals(listOf("toggle:readl:false"), it.port.fired) }
        assertFalse(tile.isOn("readl"))
        // Lọc bụi (onByDefault): ô vẽ "bật" từ đầu ⇒ phím Đảo đầu tiên phải TẮT.
        assertTrue(tile.isOn("pm25"))
        Rig().also { it.press("ctl:pm25:flip"); assertEquals(listOf("toggle:pm25:false"), it.port.fired) }
        // Giọng nói *"đóng rèm"* (COVER) nay cũng ghi bảng — trước 2.87 nhánh COVER của `finish` bỏ qua.
        tile.setSel("sunshade", 1)
        Rig().also { it.press("ctl:sunshade:close") }
        assertEquals(0, tile.sel("sunshade"))
    }

    /**
     * ═══ 2.87 · SOÁT vòng 1 · P1 (thay khẳng định cũ *"Đảo ra MỞ cốp lúc chạy ⇒ bị chặn, bảng giữ 'đóng'"*) ══════════
     * Đúng ca gãy: cốp mở bằng chìa/công tắc/app BYD ⇒ bảng vẫn "đóng" (0) ⇒ bản trước giải Đảo ra MỞ, cổng tốc độ chặn,
     * bảng không đổi ⇒ MỌI lần bấm lúc chạy lặp lại y hệt — phím Đảo không bao giờ ĐÓNG được cốp tới khi xe dừng. Nay
     * Đảo giải từ TRÍ NHỚ mà cổng sẽ chặn ⇒ hướng luôn được phép: ĐÓNG (`cover:trunk:false`). Cổng KHÔNG bị vòng qua:
     * lệnh MỞ thẳng (`ctl:trunk:open`) lúc chạy vẫn bị từ chối (bài đầu tệp), và không có lệnh MỞ nào được bắn.
     */
    @Test
    fun `dao cop tu tri nho luc xe chay thi DONG, khong ket o MO bi chan`() {
        val r = Rig(speedKmh = 30)
        assertEquals(0, sent.index("trunk"), "trí nhớ CŨ: tiến trình nói 'đóng' (cốp thật đang mở bằng chìa)")
        r.press("ctl:trunk:flip")
        assertEquals(listOf("cover:trunk:false"), r.port.fired, "Đảo lúc chạy ⇒ ĐÓNG — hướng duy nhất cổng cho phép")
        assertFalse(r.said.single().contains("chỉ mở được khi xe đang dừng"), "không còn lời từ chối MỞ: ${r.said}")
        assertEquals(0, sent.index("trunk"), "đóng thành công ⇒ bảng ghi 'đóng'")
        r.press("ctl:trunk:flip")
        assertEquals(listOf("cover:trunk:false", "cover:trunk:false"), r.port.fired, "vẫn chạy ⇒ vẫn ĐÓNG, không bao giờ MỞ")
        // Xe từ chối lệnh đóng ⇒ bảng không đổi (chỉ ghi khi thành công).
        sent.record("trunk", 1)
        r.port.accept = false
        r.press("ctl:trunk:flip")
        assertEquals("cover:trunk:false", r.port.fired.last())
        assertEquals(1, sent.index("trunk"), "lệnh hỏng ⇒ bảng giữ nguyên")
        // Xe dừng ⇒ Đảo theo trí nhớ như thường.
        sent.record("trunk", 0)
        Rig(speedKmh = 0).also { it.press("ctl:trunk:flip"); assertEquals(listOf("cover:trunk:true"), it.port.fired) }
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
