package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceIntent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VOICE-WRITE-LANE · câu ghép đi qua [VoiceDispatcher] THẬT với HAL giả ghi THỨ TỰ + MỐC GIỜ ═══════════════
 *
 * Spec `docs/specs/kachi-276-closing.html` R5 (AC): *"tăng gió rồi tắt điều hoà"* ⇒ `[ac_auto OFF, level+1, (nhịp
 * 400 ms), ac OFF]`, không lệnh nào rơi; đường đơn mệnh đề giữ nguyên byte (bài cũ `VoiceRelativeStepTest` ·
 * `VoiceDispatcherSafetyTest` không đổi).
 *
 * ## Bệnh nó khoá — review Pass 1 của 2.74, [P2] để lại có chủ ý, owner duyệt đóng ở 2.76
 * Tới 2.75 `runFrom` là một vòng `while` trên luồng gọi. Vế 1 (*"tăng gió"* lúc đang AUTO) là hai lệnh + nhịp 400 ms
 * trên luồng nền và **trả về ngay**; vế 2 (*"tắt điều hoà"*) ghi thẳng ⇒ thứ tự HAL thật là `[auto OFF (vế 1), auto
 * OFF (vế 2), …400 ms…, fan 2]` — lệnh của vế 2 rơi vào **giữa nhịp chờ** của vế 1, đúng cái mà `DEFAULT_GAP_MS`
 * tồn tại để tránh (*"bắn liên tiếp thì lệnh sau rơi"* [ĐO]). Bài 1 (giữ lambda nền) là bài đỏ trên 2.75: ở đó
 * `fired` đã có `toggle:ac_auto:false` của vế 2 **trước khi** ai chạm vào làn nền.
 */
class VoiceWriteLaneDispatchTest {

    /** Cổng xe giả — ghi lệnh + mốc giờ (ns) để đo được cả thứ tự lẫn khoảng cách. */
    private class Port(private val reads: Map<String, Int?> = emptyMap()) : CarControlPort {
        val fired = ArrayList<String>()
        val at = ArrayList<Long>()
        private val latch = CountDownLatch(3)
        private fun rec(s: String): Boolean { synchronized(this) { fired += s; at += System.nanoTime() }; latch.countDown(); return true }
        override fun toggle(id: String, on: Boolean) = rec("toggle:$id:$on")
        override fun step(id: String, value: Int) = rec("step:$id:$value")
        override fun cover(id: String, open: Boolean) = rec("cover:$id:$open")
        override fun select(id: String, index: Int) = rec("select:$id:$index")
        override fun press(id: String) = rec("press:$id")
        override fun readState(id: String): Int? = reads[id]
        override fun readStep(id: String): Int? = null
        fun awaitThree(ms: Long) = latch.await(ms, TimeUnit.MILLISECONDS)
    }

    private class Rig(
        reads: Map<String, Int?> = emptyMap(),
        controls: Map<String, Int> = emptyMap(),
        speedKmh: Int? = null,
        /** `true` ⇒ việc nền được GIỮ trong [lane]; `false` ⇒ chạy thẳng; `null` ⇒ luồng thật (đo mốc giờ). */
        private val holdBackground: Boolean? = false,
        /** Mã đang bật của `voice_confirm_ids` (mặc định RỖNG = không hỏi gì — `VoiceRiskTable.of`). */
        confirmIds: Set<String> = emptySet(),
        /** `true` ⇒ hộp hỏi lại **mở rồi ngồi đó** (không ai bấm), đúng ca người lái còn đang đọc câu hỏi. */
        holdConfirm: Boolean = false,
    ) {
        val port = Port(reads)
        val said = ArrayList<String>()
        val lane = ArrayList<() -> Unit>()
        val state = HomeUiState(
            profiles = listOf("Mặc định"),
            carStatus = CarStatus(controls = controls, drivetrain = CarStatus.Drivetrain(speedKmh = speedKmh)),
        )
        val dispatcher = VoiceDispatcher(
            control = { port },
            state = { state },
            media = { error("bài này không chạm tới nhạc") },
            appsByLabel = { emptyMap() },
            openApp = { false },
            openAppList = {},
            openSettings = {},
            onSwitchProfile = {},
            onListen = {},
            confirm = { _, _, n -> if (!holdConfirm) n() },
            confirmIds = { confirmIds },
            say = { synchronized(said) { said += it } },
            assignAppToSlot = { _, _ -> error("bài này không gắn app vào ô") },
            sendToApp = { error("bài này không giao việc cho app đích") },
            geocode = { error("bài này không tra toạ độ") },
            mediaPackage = { null },
            background = { block ->
                when (holdBackground) {
                    true -> lane += block
                    false -> block()
                    null -> Thread(block, "test-bg").apply { isDaemon = true }.start()
                }
            },
        )
    }

    private fun up(id: String, steps: Int = 1) = VoiceIntent.Control(id, null, relative = steps)

    // ══ 1 · AC của R5 — thứ tự, tất định (giữ lambda nền lại) ═══════════════════════════════════════════

    @Test
    fun `tang gio roi tat dieu hoa - ve 2 KHONG duoc ghi khi ve 1 con trong nhip cho`() {
        // Xe đang AUTO (ước chung nút: 1 = bật), gió đang thổi mức 1.
        val r = Rig(reads = mapOf("fan" to 1), controls = mapOf("ac_auto" to 1), holdBackground = true)
        val intents = r.dispatcher.preview("tăng gió rồi tắt điều hoà")
        assertEquals(2, intents.size, "phải tách được hai vế: $intents")
        r.dispatcher.execute(intents)
        assertEquals(emptyList<String>(), r.port.fired, "vế 1 còn trên làn nền ⇒ CHƯA lệnh nào được ghi, kể cả của vế 2 (2.75 ghi `toggle:ac_auto:false` của vế 2 ở đây)")
        assertEquals(1, r.lane.size, "đúng một việc nền: chuỗi rời-AUTO của vế 1")
        assertTrue(r.said.isEmpty(), "chưa ghi xong thì chưa nói")

        r.lane.single().invoke()
        assertEquals(
            listOf("toggle:ac_auto:false", "step:fan:2", "toggle:ac_auto:false"), r.port.fired,
            "[auto OFF, mức +1, (nhịp), AC OFF] — đúng thứ tự nói, không lệnh nào rơi",
        )
        assertEquals(2, r.said.size, "mỗi vế một câu trả lời, đúng thứ tự: ${r.said}")
    }

    // ══ 2 · AC của R5 — MỐC GIỜ trên luồng thật: lệnh của vế 2 đến SAU nhịp 400 ms ════════════════════════

    @Test
    fun `tren luong that lenh cua ve 2 den sau nhip 400 ms cua ve 1`() {
        val r = Rig(reads = mapOf("fan" to 1), controls = mapOf("ac_auto" to 1), holdBackground = null)
        r.dispatcher.submit("tăng gió rồi tắt điều hoà")
        assertTrue(r.port.awaitThree(5_000), "ba lệnh phải tới trong 5 s: ${r.port.fired}")
        assertEquals(listOf("toggle:ac_auto:false", "step:fan:2", "toggle:ac_auto:false"), r.port.fired)
        val gapMs = (r.port.at[1] - r.port.at[0]) / 1_000_000
        val afterMs = (r.port.at[2] - r.port.at[1]) / 1_000_000
        assertTrue(gapMs >= ActionMacros.DEFAULT_GAP_MS - 50, "nhịp giữa auto OFF và mức phải ≈ 400 ms, thấy $gapMs ms")
        assertTrue(afterMs >= 0, "lệnh vế 2 phải đến SAU lệnh mức của vế 1 (thấy $afterMs ms)")
    }

    // ══ 3 · Mọi lối ra sớm của một nút vẫn báo XONG ⇒ vế sau không bị nuốt ═════════════════════════════════

    @Test
    fun `nut la, dang AUTO khong co nac thap hon, chan vi xe dang chay - ve sau van chay`() {
        val la = Rig()
        la.dispatcher.execute(listOf(VoiceIntent.Control("khong-co-nut-nay", 1), VoiceIntent.Control("readl", 1)))
        assertEquals(listOf("toggle:readl:true"), la.port.fired, "nút lạ ⇒ nói hỏng rồi đi tiếp")

        val auto = Rig(reads = mapOf("fan" to 1), controls = mapOf("ac_auto" to 1))
        auto.dispatcher.execute(listOf(up("fan", -1), VoiceIntent.Control("readl", 1)))
        assertEquals(listOf("toggle:readl:true"), auto.port.fired, "đang AUTO mà giảm ⇒ không bắn gì, nhưng vế sau vẫn chạy")

        val moving = Rig(speedKmh = 30)
        moving.dispatcher.execute(listOf(VoiceIntent.Control("trunk", 1), VoiceIntent.Control("readl", 1)))
        assertEquals(listOf("toggle:readl:true"), moving.port.fired, "cốp bị chặn khi xe chạy ⇒ vế sau vẫn chạy")
        assertTrue(moving.said.first().isNotBlank())
    }

    // ══ 4 · Gói lệnh chạy nền cũng giữ vế sau lại tới khi gói xong ═══════════════════════════════════════

    @Test
    fun `goi lenh chay nen thi ve sau cho goi xong`() {
        val r = Rig(holdBackground = true)
        r.dispatcher.execute(listOf(VoiceIntent.Macro("mac_win_open_all"), VoiceIntent.Control("readl", 1)))
        try {
            assertEquals(emptyList<String>(), r.port.fired, "gói còn trên làn nền ⇒ đèn đọc chưa được bật")
            assertEquals(1, r.lane.size)
            r.lane.single().invoke()
            assertTrue(r.port.fired.size > 1, "gói phải ghi ít nhất một bước: ${r.port.fired}")
            assertEquals("toggle:readl:true", r.port.fired.last(), "vế sau chạy SAU gói, đúng thứ tự nói")
        } finally {
            ControlTileState.shared.endRun("mac_win_open_all")
        }
    }

    // ══ 5 · Đường đơn mệnh đề: y nguyên ═══════════════════════════════════════════════════════════════════

    @Test
    fun `mot ve dong bo van ghi ngay tren luong goi`() {
        val r = Rig(holdBackground = true)
        r.dispatcher.execute(listOf(VoiceIntent.Control("readl", 1)))
        assertEquals(listOf("toggle:readl:true"), r.port.fired)
        assertTrue(r.lane.isEmpty(), "một lệnh ⇒ không có gì xuống nền")
        assertEquals(1, r.said.size)
    }

    // ══ 6 · Mốc "CẢ CÂU ĐÃ GHI XONG" — thứ lượt nói phải chờ trước khi đọc câu trả lời ════════════════════

    /**
     * ═══ [P1 · SOÁT Opus 2026-09-27] `execute` trả về **trước** khi câu ghép ghi xong ⇒ phải có mốc `onSettled` ═══
     *
     * Bệnh nó khoá: `VoiceSession.execute` gom mảng lời đáp ngay sau `d.execute`. Với *"tăng gió rồi tắt điều hoà"*
     * lúc xe đang AUTO, vế 1 xuống luồng nền nên lúc `d.execute` trả về **chưa có một lời `say` nào** ⇒ mảng RỖNG ⇒
     * `speakLines` thoát ngay ⇒ micro nối mở trong vài ms ⇒ hai câu trả lời thật (≈ 400 ms sau) bị cổng `micOpen`
     * bỏ im. Người lái nghe **đúng một tiếng chuông** cho hai thay đổi trên xe — kể cả khi vế 2 hỏng thật.
     *
     * Ba vế được ghim: mốc **chưa** bắn khi còn việc trên làn · bắn **đúng một lần** khi câu xong, lúc ấy mảng lời
     * đáp đã đủ hai câu · và ở nhánh hỏi lại thì bắn NGAY (phần còn lại chờ người lái — y hành vi 2.75).
     */
    @Test
    fun `onSettled chi ban khi lan ghi da can, va ban ngay o nhanh hoi lai`() {
        val r = Rig(reads = mapOf("fan" to 1), controls = mapOf("ac_auto" to 1), holdBackground = true)
        var settled = 0
        val intents = r.dispatcher.preview("tăng gió rồi tắt điều hoà")
        assertEquals(2, intents.size)
        r.dispatcher.execute(intents) { settled++ }
        assertEquals(0, settled, "vế 1 còn trên làn nền ⇒ CHƯA được chốt lượt nói (nếu chốt thì mảng lời đáp rỗng)")
        assertTrue(r.said.isEmpty())
        r.lane.single().invoke()
        assertEquals(1, settled, "cả câu ghi xong ⇒ đúng MỘT mốc")
        assertEquals(2, r.said.size, "…và lúc ấy mảng lời đáp đã đủ hai câu: ${r.said}")

        // Một vế đồng bộ ⇒ mốc bắn NGAY trong lượt gọi (đường đơn mệnh đề của 2.75 không đổi một byte).
        val one = Rig(holdBackground = true)
        var settledOne = 0
        one.dispatcher.execute(listOf(VoiceIntent.Control("readl", 1))) { settledOne++ }
        assertEquals(1, settledOne)

        // Hộp HỎI LẠI còn đang mở (không ai trả lời) ⇒ mốc vẫn phải bắn: phần còn lại của câu chờ NGƯỜI LÁI, và
        // lượt nói phải đọc câu hỏi + đóng theo đường 2.75. Không có vế này thì tấm chữ treo tới lưới an toàn.
        val ask = Rig(holdBackground = true, confirmIds = setOf("control:sunroof"), holdConfirm = true)
        var settledAsk = 0
        ask.dispatcher.execute(listOf(VoiceIntent.Control("sunroof", 1), VoiceIntent.Control("readl", 1))) { settledAsk++ }
        assertEquals(1, settledAsk, "đứng ở hộp hỏi lại cũng là 'chốt được' — chờ người lái, không chờ làn")
        assertEquals(emptyList<String>(), ask.port.fired, "chưa đồng ý ⇒ chưa ghi gì, và vế sau cũng dừng (spec §7 OQ4)")
    }
}
