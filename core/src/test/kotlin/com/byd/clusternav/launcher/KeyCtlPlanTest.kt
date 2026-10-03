package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.KeyCtlPlan.Outcome
import com.byd.clusternav.launcher.KeyCtlThrottle.Step
import com.byd.clusternav.launcher.voice.VoiceIntent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 · R-KC — lần bấm ⇒ ý định nút (KC3) + chống dồn núm vặn (KC4) ═══════════════════════════════════════
 *
 * Phần thi hành (cổng tốc độ cốp, AUTO, "xe này không có", ghi HAL) KHÔNG ở đây: nó là `VoiceControlDispatch` có sẵn —
 * `KeyCtlSafetyTest` (`:app`) dựng nó thật với cổng xe giả. Ở đây khoá hai bảng thuần.
 */
class KeyCtlPlanTest {

    private fun def(id: String) = ControlRegistry.byId(id)!!
    private fun t(spec: String) = KeyCtlTargets.decode(spec)!!
    private fun plan(spec: String, count: Int = 1, state: Int? = null): Outcome {
        var reads = 0
        val o = KeyCtlPlan.of(def(t(spec).controlId), t(spec), count) { reads++; state }
        val needsRead = t(spec).action == KeyCtlAction.FLIP || t(spec).action == KeyCtlAction.NEXT
        assertEquals(if (needsRead) 1 else 0, reads, "$spec: chỉ Đảo/Kế tiếp được tốn một lượt đọc HAL")
        return o
    }
    private fun run(id: String, value: Int? = null, relative: Int = 0) = Outcome.Run(VoiceIntent.Control(id, value, relative))

    // ══ KC3 · bảng hành động → ý định ══════════════════════════════════════════════════════════════════════

    @Test
    fun `dat thang khong doc gi va ra dung y dinh cua cau noi`() {
        assertEquals(run("win_lf", 1), plan("ctl:win_lf:on"))
        assertEquals(run("win_lf", 0), plan("ctl:win_lf:off"))
        assertEquals(run("trunk", 1), plan("ctl:trunk:open"), "= đúng ý định của câu 'mở cốp' ⇒ đi qua cổng tốc độ")
        assertEquals(run("trunk", 0), plan("ctl:trunk:close"))
        assertEquals(run("sunshade", 2), plan("ctl:sunshade:=2"))
        assertEquals(run("seatc", 1), plan("ctl:seatc:=1"))
        assertEquals(run("windows_close_all", 1), plan("ctl:windows_close_all:press"))
    }

    @Test
    fun `step la tuong doi, so nac = so lan bam da gop`() {
        assertEquals(run("fan", relative = 1), plan("ctl:fan:+1"))
        assertEquals(run("fan", relative = -1), plan("ctl:fan:-1"))
        assertEquals(run("fan", relative = 4), plan("ctl:fan:+1", count = 4), "núm vặn 4 nấc gộp ⇒ +4, không mất nấc")
        assertEquals(run("temp", relative = -3), plan("ctl:temp:-1", count = 3))
    }

    /** §5 — Đảo quyết bằng trạng thái THẬT; đọc không được ⇒ báo, KHÔNG bắn, KHÔNG đoán. */
    @Test
    fun `dao doc trang thai that, khong doc duoc thi khong ban`() {
        assertEquals(run("win_lf", 1), plan("ctl:win_lf:flip", state = 0), "kính đóng (0 %) ⇒ mở")
        assertEquals(run("win_lf", 0), plan("ctl:win_lf:flip", state = 37), "kính hé 37 % ⇒ đóng (đọc % như VoiceReadback)")
        assertEquals(run("recirc", 0), plan("ctl:recirc:flip", state = 1))
        assertEquals(Outcome.Unreadable, plan("ctl:win_lf:flip", state = null), "máy ảo / HAL hỏng ⇒ không bắn")
    }

    @Test
    fun `ke tiep vong theo so lua chon, so la thi khong doan`() {
        assertEquals(run("seatc", 1), plan("ctl:seatc:next", state = 0))
        assertEquals(run("seatc", 0), plan("ctl:seatc:next", state = 2), "mức cuối ⇒ vòng về Tắt")
        assertEquals(run("seatc", 2), plan("ctl:seatc:next", count = 2, state = 0))
        assertEquals(Outcome.Unreadable, plan("ctl:seatc:next", state = null))
        assertEquals(Outcome.Unreadable, plan("ctl:seatc:next", state = 7), "mã mức ngoài thang ⇒ không đoán")
    }

    @Test
    fun `cau bao vi en noi ten nut va cach chua`() {
        assertEquals("Không đọc được Kính lái — gán Bật / Tắt riêng", KeyCtlPlan.unreadableReply(def("win_lf"), Lang.VI))
        assertEquals("Can't read Driver window — bind On / Off separately", KeyCtlPlan.unreadableReply(def("win_lf"), Lang.EN))
        assertEquals("Không đọc được Mát ghế lái — gán từng mức", KeyCtlPlan.unreadableReply(def("seatc"), Lang.VI))
        assertTrue(KeyCtlPlan.unreadableReply(def("trunk"), Lang.VI).endsWith("gán Mở / Đóng riêng"))
        assertTrue("ctl:x:y" in KeyCtlPlan.invalidReply("ctl:x:y", Lang.VI))
    }

    // ══ KC4 · chống dồn ═════════════════════════════════════════════════════════════════════════════════════

    private val up = KeyCtlTarget("fan", KeyCtlAction.UP)
    private val down = KeyCtlTarget("fan", KeyCtlAction.DOWN)

    @Test
    fun `lan bam dau ban ngay, trong cua so gop, canh cuoi ban mot lenh`() {
        val th = KeyCtlThrottle(400)
        assertEquals(Step.Fire(up, 1), th.press(up, 1_000))
        assertEquals(Step.Hold(1_400, schedule = true), th.press(up, 1_050))
        assertEquals(Step.Hold(1_400, schedule = false), th.press(up, 1_100), "chỉ hẹn MỘT lần mỗi cửa sổ")
        assertEquals(Step.Hold(1_400, schedule = false), th.press(up, 1_150))
        assertEquals(Step.Fire(up, 3), th.flush("fan", 1_400), "3 nấc gộp thành 1 lệnh +3 — không mất nấc")
        assertNull(th.flush("fan", 1_401), "flush lần hai không còn gì")
        assertEquals(Step.Fire(up, 1), th.press(up, 1_900), "hết cửa sổ ⇒ lại bắn ngay")
    }

    @Test
    fun `cw ccw cung mot nut gop thanh tong co dau, trieu tieu thi khong ban`() {
        val th = KeyCtlThrottle(400)
        th.press(up, 0)
        th.press(down, 10); th.press(down, 20); th.press(down, 30)
        assertEquals(Step.Fire(down, 3), th.flush("fan", 400))
        th.press(up, 900)
        th.press(up, 910); th.press(down, 920)
        assertNull(th.flush("fan", 1_300), "+1 −1 triệt tiêu ⇒ 0 lệnh")
    }

    @Test
    fun `dao va bam bi bo trong cua so, dat thang lay lan cuoi`() {
        val th = KeyCtlThrottle(400)
        val flip = KeyCtlTarget("win_lf", KeyCtlAction.FLIP)
        assertEquals(Step.Fire(flip, 1), th.press(flip, 0))
        assertEquals(Step.Drop, th.press(flip, 120), "nảy phím ⇒ không đảo hai lần")
        val on = KeyCtlTarget("win_rf", KeyCtlAction.ON)
        val off = KeyCtlTarget("win_rf", KeyCtlAction.OFF)
        th.press(on, 0); th.press(off, 50); th.press(on, 60); th.press(off, 70)
        assertEquals(Step.Fire(off, 1), th.flush("win_rf", 400))
        assertTrue(th.press(KeyCtlTarget("trunk", KeyCtlAction.OPEN), 10) is Step.Fire, "nút khác không chung cửa sổ")
    }

    /** Owner *"nút vặn volume … vặn gió"*: 100 sự kiện trong 2 s ⇒ số lệnh có trần, tổng nấc giữ nguyên. */
    @Test
    fun `nui van 100 su kien trong 2 giay khong don hang tram lenh`() {
        val th = KeyCtlThrottle(400)
        var fired = 0
        var steps = 0
        var flushAt: Long? = null
        for (i in 0 until 100) {
            val now = i * 20L
            flushAt?.let { if (now >= it) { th.flush("fan", it)?.let { f -> fired++; steps += f.count }; flushAt = null } }
            when (val s = th.press(up, now)) {
                is Step.Fire -> { fired++; steps += s.count }
                is Step.Hold -> if (s.schedule) flushAt = s.flushAtMs
                Step.Drop -> error("STEP không bao giờ bị bỏ")
            }
        }
        flushAt?.let { th.flush("fan", it)?.let { f -> fired++; steps += f.count } }
        assertEquals(100, steps, "mỗi sự kiện đúng một nấc")
        assertTrue(fired <= 1 + 2_000 / 400 + 1, "≤ 1 + thời gian/cửa sổ lệnh; thấy $fired")
    }
}
