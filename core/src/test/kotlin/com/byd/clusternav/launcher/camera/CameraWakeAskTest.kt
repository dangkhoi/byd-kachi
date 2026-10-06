package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.camera.CameraDemand.Op
import com.byd.clusternav.launcher.camera.CameraDemand.Outcome
import com.byd.clusternav.launcher.voice.VoiceCameraTurn
import com.byd.clusternav.launcher.voice.VoiceIntent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2C · CAM-WAKE-COLD-MAIN — cầu `:wake` → chính gửi lại MỘT lần khi chính vừa khởi động (spec `kachi-293-wave2c.html` R6) ═══
 *
 * Đồng hồ + bộ hẹn giờ GIẢ (không `Thread.sleep`): mọi mốc thời gian là số, mọi kết quả đếm đúng số lần. Khoá:
 *  • receiver chưa đăng ký (mã khởi đầu về ngay) ⇒ gửi lại đúng MỘT lần sau [CameraWakeAsk.RETRY_DELAY_MS], trong hạn tổng;
 *  • lượt gửi lại cũng không ai trả lời ⇒ [Outcome.UNREACHABLE]; tiến trình chính tắt trong lúc chờ ⇒ [CameraDemand.withoutMain];
 *  • hạn tổng KHÔNG nới (2 s — chỗ cho đường Camera 360 trước lưới an toàn lượt nói); kết quả đúng MỘT lần, về muộn ⇒ bỏ;
 *  • *"tắt camera"* đi tiếp qua `VoiceCameraTurn` ĐÚNG luật Camera 360 của nó: không có gì để tắt ⇒ chạy lại thành nút Camera
 *    360; vẫn không tới được ⇒ *"✗ … chưa liên lạc được màn hình chính"*, KHÔNG rơi về Camera 360.
 */
class CameraWakeAskTest {

    private val total = 2_000L

    /** Bộ hẹn giờ + đồng hồ giả: [advance] chạy các việc tới hạn theo thứ tự thời gian (việc mới hẹn trong lúc chạy cũng được chạy). */
    private class Clock {
        var now = 0L
        private val jobs = ArrayList<Pair<Long, () -> Unit>>()
        fun schedule(ms: Long, block: () -> Unit) { jobs += (now + ms) to block }
        fun advance(ms: Long) {
            val end = now + ms
            while (true) {
                val next = jobs.filter { it.first <= end }.minByOrNull { it.first } ?: break
                jobs.remove(next)
                now = next.first
                next.second()
            }
            now = end
        }
    }

    /** Một kịch bản: [replies] trả lời từng lượt gửi theo thứ tự (mã · `null` = gửi hỏng · bỏ trống = không bao giờ trả lời). */
    private class Run(val op: Op, private val replies: List<Int?>, private val replyAfterMs: Long = 10L, var alive: Boolean = true) {
        val clock = Clock()
        val results = ArrayList<Pair<Outcome, Long>>()
        val logs = ArrayList<String>()
        var sends = 0
        val pending = ArrayList<(Int?) -> Unit>()

        fun start(onResult: (Outcome) -> Unit = { results += it to clock.now }) = CameraWakeAsk.run(
            op = op,
            totalMs = 2_000L,
            now = { clock.now },
            schedule = { ms, block -> clock.schedule(ms, block) },
            mainAlive = { alive },
            send = { reply ->
                val n = sends++
                if (n < replies.size) clock.schedule(replyAfterMs) { reply(replies[n]) } else pending += reply
            },
            log = { logs += it },
            onResult = onResult,
        )
    }

    @Test fun `receiver tra loi ngay o luot dau - mot luot gui, ket qua dung`() {
        val r = Run(Op.Toggle(CameraWhich.REAR), listOf(Outcome.OPENED.code)).apply { start() }
        r.clock.advance(5_000)
        assertEquals(listOf(Outcome.OPENED to 10L), r.results)
        assertEquals(1, r.sends, "đã trả lời ⇒ không gửi lại")
    }

    @Test fun `chinh dang khoi dong - luot dau khong ai tra loi, gui lai MOT lan thi gap receiver`() {
        val r = Run(Op.CloseAll, listOf(Outcome.UNREACHABLE.code, Outcome.NOTHING_TO_CLOSE.code)).apply { start() }
        r.clock.advance(5_000)
        assertEquals(2, r.sends, "đúng một lượt gửi lại")
        assertEquals(1, r.results.size, "kết quả đúng MỘT lần")
        val (o, at) = r.results.single()
        assertEquals(Outcome.NOTHING_TO_CLOSE, o)
        assertEquals(10L + CameraWakeAsk.RETRY_DELAY_MS + 10L, at, "lượt gửi lại đi sau đúng RETRY_DELAY_MS kể từ lượt đầu về")
        assertTrue(at <= total, "vẫn trong hạn TỔNG cũ — không nới")
        assertTrue(r.logs.any { "gửi lại sau" in it }, "buổi xe phải đọc được lượt gửi lại trong logcat")
    }

    @Test fun `gui lai cung khong ai tra loi - khong toi duoc, khong gui lan ba`() {
        val r = Run(Op.CloseAll, listOf(Outcome.UNREACHABLE.code, Outcome.UNREACHABLE.code)).apply { start() }
        r.clock.advance(10_000)
        assertEquals(2, r.sends, "gửi lại TỐI ĐA một lần")
        assertEquals(listOf(Outcome.UNREACHABLE), r.results.map { it.first })
    }

    @Test fun `chinh tat trong luc cho - luat withoutMain, khong gui lai`() {
        listOf(Op.CloseAll to Outcome.NOTHING_TO_CLOSE, Op.Toggle(CameraWhich.LEFT) to Outcome.UNREACHABLE).forEach { (op, want) ->
            val r = Run(op, listOf(Outcome.UNREACHABLE.code))
            r.start()
            r.alive = false
            r.clock.advance(5_000)
            assertEquals(1, r.sends, "$op: chính đã tắt ⇒ không gửi vào khoảng không")
            assertEquals(listOf(want), r.results.map { it.first }, "$op: cùng luật của lượt hỏi khi chính không sống")
        }
    }

    @Test fun `het han tong - khong toi duoc dung luc, ket qua ve muon bi bo`() {
        val r = Run(Op.Toggle(CameraWhich.FRONT), emptyList()).apply { start() }
        r.clock.advance(total)
        assertEquals(listOf(Outcome.UNREACHABLE to total), r.results)
        r.pending.single()(Outcome.OPENED.code)   // receiver trả lời sau hạn
        assertEquals(1, r.results.size, "kết quả đúng MỘT lần — câu đã nói không rút lại được")
        assertTrue(r.logs.any { "MUỘN" in it })
        assertEquals(1, r.sends, "hết hạn ⇒ không gửi lại (lượt đầu có thể còn đang bay)")
    }

    @Test fun `gui hong - khong toi duoc ngay, khong gui lai`() {
        val r = Run(Op.CloseAll, listOf(null)).apply { start() }
        r.clock.advance(5_000)
        assertEquals(listOf(Outcome.UNREACHABLE to 10L), r.results)
        assertEquals(1, r.sends)
    }

    @Test fun `luot dau ve qua muon thi khong con cho gui lai`() {
        val late = total - CameraWakeAsk.RETRY_DELAY_MS - CameraWakeAsk.MIN_RETRY_WINDOW_MS + 1
        val r = Run(Op.CloseAll, listOf(Outcome.UNREACHABLE.code, Outcome.CLOSED.code), replyAfterMs = late).apply { start() }
        r.clock.advance(5_000)
        assertEquals(1, r.sends, "không đủ hạn cho lượt gửi lại ⇒ báo luôn")
        assertEquals(listOf(Outcome.UNREACHABLE to late), r.results)
    }

    /** Hằng phải chừa đủ chỗ: lượt gửi lại đi được khi lượt đầu về nhanh, và luôn nằm TRONG hạn tổng. */
    @Test fun `hang so - gui lai nam tron trong han tong cu`() {
        assertTrue(CameraWakeAsk.RETRY_DELAY_MS + CameraWakeAsk.MIN_RETRY_WINDOW_MS < total, "lượt gửi lại phải vừa trong 2 s")
        assertTrue(CameraWakeAsk.MIN_RETRY_WINDOW_MS >= 100L, "cửa sổ lượt gửi lại ≥ vài lần đường một chiều đã đo (52 ms máy ảo)")
    }

    /**
     * Soát senior wave 2B/2C [P2] — lệnh tới receiver SAU hạn của bên gửi bị BỎ: câu *"✗ … chưa liên lạc được"* đã nói thì lệnh
     * không được có hiệu lực muộn (lệnh MỞ muộn là *"mới nhất"* ⇒ đè được camera xi-nhan bật trong lúc chờ). Đúng hạn vẫn áp;
     * không mang hạn (`≤ 0`) ⇒ không bỏ gì.
     */
    @Test fun `lenh toi receiver sau han cua ben gui thi bo - khong co hieu luc muon`() {
        val deadline = 5_000L + total   // bên gửi: t0 = 5 s (đồng hồ khởi động), hạn tổng 2 s
        assertFalse(CameraWakeAsk.expired(deadline, 5_010L), "đường thường (~52 ms máy ảo) ⇒ áp")
        assertFalse(CameraWakeAsk.expired(deadline, deadline), "ĐÚNG mốc hạn vẫn áp — bên gửi chưa quá hạn")
        assertTrue(CameraWakeAsk.expired(deadline, deadline + 1), "quá hạn 1 ms ⇒ bỏ (câu ✗ đã/đang nói)")
        assertTrue(CameraWakeAsk.expired(deadline, deadline + 8_000L), "luồng chính bận cả chục giây (vừa dựng HOME) ⇒ bỏ")
        assertFalse(CameraWakeAsk.expired(0L, Long.MAX_VALUE), "không mang hạn ⇒ không bỏ gì")
        // Cùng kịch bản với đồng hồ giả: hết hạn tổng ⇒ bên gửi báo UNREACHABLE; receiver tới sau đó thấy lệnh đã quá hạn.
        val r = Run(Op.Toggle(CameraWhich.REAR), emptyList()).apply { start() }
        r.clock.advance(total)
        assertEquals(listOf(Outcome.UNREACHABLE to total), r.results)
        assertTrue(CameraWakeAsk.expired(deadlineMs = 0L + total, nowMs = r.clock.now + 1), "receiver tới muộn ⇒ không áp")
    }

    // ── *"tắt camera"* đi tiếp qua VoiceCameraTurn — luật Camera 360 KHÔNG đổi, chỉ có thêm cơ hội tới được chính ──────────

    private class Turn(val r: Run) {
        val said = ArrayList<String>()
        val reruns = ArrayList<VoiceIntent>()
        var nexts = 0
        fun go() = VoiceCameraTurn.run(
            i = VoiceIntent.Launcher(LauncherActions.CAM_OFF),
            lang = Lang.VI,
            fire = { op, done ->
                assertEquals(r.op, op, "tiền đề: *tắt camera* = tắt mọi camera theo yêu cầu")
                r.start(done)
            },
            onUi = { it() },
            say = { said += it },
            next = { nexts++ },
            rerun = { reruns += it },
        )
    }

    @Test fun `tat camera luc chinh vua khoi dong - gui lai gap chinh, khong gi de tat thi Camera 360`() {
        val t = Turn(Run(Op.CloseAll, listOf(Outcome.UNREACHABLE.code, Outcome.NOTHING_TO_CLOSE.code)))
        t.go(); t.r.clock.advance(5_000)
        assertEquals(listOf(VoiceIntent.Control("cam", 0)), t.reruns, "rơi về nút Camera 360 TẮT như ≤ 2.92 (wave 2B D1)")
        assertEquals(0, t.nexts, "vế thay thế tự đi tiếp — không gọi `next` hai lần")
    }

    @Test fun `tat camera van khong toi duoc sau luot gui lai - noi that, KHONG roi ve Camera 360`() {
        val t = Turn(Run(Op.CloseAll, listOf(Outcome.UNREACHABLE.code, Outcome.UNREACHABLE.code)))
        t.go(); t.r.clock.advance(5_000)
        assertEquals(emptyList<VoiceIntent>(), t.reruns, "không biết camera theo yêu cầu có mở không ⇒ không đoán")
        assertEquals(listOf("✗ Tắt camera — chưa liên lạc được màn hình chính"), t.said)
        assertEquals(1, t.nexts)
    }

    @Test fun `tat camera khi chinh tat trong luc cho - khong gi de tat, Camera 360`() {
        val t = Turn(Run(Op.CloseAll, listOf(Outcome.UNREACHABLE.code)))
        t.go(); t.r.alive = false; t.r.clock.advance(5_000)
        assertEquals(listOf(VoiceIntent.Control("cam", 0)), t.reruns, "cùng luật withoutMain của lượt hỏi đầu (wave 2B)")
    }
}
