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
 *
 * 2.87 · R-FL2: Đảo / Kế tiếp quyết bằng XE khi nút có readKey và đọc ra số hợp lệ, không thì bằng LỆNH CUỐI Kachi đã
 * gửi ([ControlLastSent]); mỗi bài dùng bảng RIÊNG (`mem`) — không chạm bảng dùng chung của JVM kiểm.
 */
class KeyCtlPlanTest {

    private fun def(id: String) = ControlRegistry.byId(id)!!
    private fun t(spec: String) = KeyCtlTargets.decode(spec)!!

    /** Bảng lệnh cuối RIÊNG cho mỗi bài (không chạm `ControlLastSent.shared` của cả JVM kiểm). */
    private val mem = ControlLastSent()

    private fun plan(spec: String, count: Int = 1, state: Int? = null, memory: ControlLastSent = mem): Outcome {
        var reads = 0
        val d = def(t(spec).controlId)
        val o = KeyCtlPlan.of(d, t(spec), count, memory) { reads++; state }
        val needsRead = (t(spec).action == KeyCtlAction.FLIP || t(spec).action == KeyCtlAction.NEXT) && d.readKey.isNotBlank()
        assertEquals(if (needsRead) 1 else 0, reads, "$spec: chỉ Đảo/Kế tiếp của nút CÓ readKey được tốn một lượt đọc HAL")
        return o
    }
    private fun run(id: String, value: Int? = null, relative: Int = 0, basis: KeyCtlPlan.Basis = KeyCtlPlan.Basis.DIRECT) =
        Outcome.Run(VoiceIntent.Control(id, value, relative), basis)
    private fun byCar(id: String, value: Int) = run(id, value, basis = KeyCtlPlan.Basis.CAR)
    private fun byMem(id: String, value: Int) = run(id, value, basis = KeyCtlPlan.Basis.MEMORY)

    /** Thi hành giả: ghi bảng như `VoiceControlDispatch.finish(ok)` — CHỈ khi lệnh "thành công". */
    private fun exec(o: Outcome, ok: Boolean = true, memory: ControlLastSent = mem) {
        val i = (o as Outcome.Run).intent
        if (ok) memory.record(i.id, i.value!!)
    }

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

    // ══ 2.87 · R-FL2 · Đảo / Kế tiếp: XE nếu đọc được, không thì LỆNH CUỐI ═════════════════════════════════════

    /** Nút CÓ readKey + đọc ra số ⇒ quyết bằng xe, y như 2.86 (bảng lệnh cuối nói gì cũng mặc). */
    @Test
    fun `dao doc duoc xe thi quyet bang xe`() {
        mem.record("win_lf", 1)   // bảng nói "đang mở" — xe nói đóng ⇒ xe thắng
        assertEquals(byCar("win_lf", 1), plan("ctl:win_lf:flip", state = 0), "kính đóng (0 %) ⇒ mở")
        assertEquals(byCar("win_lf", 0), plan("ctl:win_lf:flip", state = 37), "kính hé 37 % ⇒ đóng (đọc % như VoiceReadback)")
        assertEquals(byCar("recirc", 0), plan("ctl:recirc:flip", state = 1))
    }

    /** Có readKey mà đọc hỏng (máy ảo · HAL ném · sentinel) ⇒ lệnh cuối, KHÔNG còn "không bắn" như 2.86. */
    @Test
    fun `dao doc hong thi lui ve lenh cuoi`() {
        assertEquals(byMem("win_lf", 1), plan("ctl:win_lf:flip", state = null), "chưa gửi gì ⇒ coi là đóng ⇒ MỞ")
        mem.record("win_lf", 1)
        assertEquals(byMem("win_lf", 0), plan("ctl:win_lf:flip", state = null), "vừa gửi MỞ ⇒ ĐÓNG")
    }

    /** Nút KHÔNG readKey ⇒ không tốn lượt đọc nào (bài `plan` đếm), quyết bằng lệnh cuối; mặc định tắt/đóng. */
    @Test
    fun `khong readKey thi quyet bang lenh cuoi, mac dinh tat dong`() {
        assertEquals(byMem("trunk", 1), plan("ctl:trunk:flip"), "tiến trình vừa bật (BYD giết Kachi mỗi lần tắt máy) ⇒ cốp đóng ⇒ MỞ")
        assertEquals(byMem("readl", 1), plan("ctl:readl:flip"))
        assertEquals(byMem("sunshade", 1), plan("ctl:sunshade:flip"))
        mem.record("sunshade", 2)
        assertEquals(byMem("sunshade", 0), plan("ctl:sunshade:flip"), "rèm đang Nửa (mức 2 > 0 = đang mở) ⇒ ĐÓNG")
        // Ngoại lệ duy nhất: TOGGLE khai onByDefault (lọc bụi) — ô vẽ "bật" từ đầu ⇒ phím Đảo phải TẮT, không lệch ô.
        assertEquals(byMem("pm25", 0), plan("ctl:pm25:flip"))
    }

    /** Owner 03/10: *"Cái nút picker trên widget thì nhấn cái đóng, nhấn cái mở đc mà?"* — ô mở thì phím Đảo ĐÓNG. */
    @Test
    fun `cot mo bang o roi bam phim dao thi dong`() {
        mem.record("trunk", 1)   // đúng thứ `ControlTileState.setSel` của ô cốp ghi sau cú chạm MỞ (bài :app khoá dây thật)
        assertEquals(byMem("trunk", 0), plan("ctl:trunk:flip"))
    }

    /** Đảo hai lần (mỗi lần thành công) ⇒ về đúng chỗ cũ — cho MỌI nút đảo được của registry, sinh tự động. */
    @Test
    fun `dao hai lan ve nhu cu voi moi nut dao duoc`() {
        ControlRegistry.ALL.filter { it.kind == ControlKind.TOGGLE || it.kind == ControlKind.COVER }.forEach { d ->
            val m = ControlLastSent()
            val start = m.index(d.id)
            val f = KeyCtlTarget(d.id, KeyCtlAction.FLIP)
            val first = KeyCtlPlan.of(d, f, memory = m) { null }
            exec(first, memory = m)
            assertEquals(if (start > 0) 0 else 1, m.index(d.id), "${d.id}: Đảo lần một phải đổi chiều")
            exec(KeyCtlPlan.of(d, f, memory = m) { null }, memory = m)
            assertEquals(start, m.index(d.id), "${d.id}: Đảo hai lần phải về như cũ")
        }
    }

    /** Lệnh hỏng / bị cổng an toàn chặn ⇒ bảng KHÔNG đổi ⇒ lần Đảo kế vẫn ra cùng hành động (không "nhảy" chiều). */
    @Test
    fun `lenh hong khong doi bang nen dao lan sau ra cung hanh dong`() {
        val first = plan("ctl:trunk:flip")
        assertEquals(byMem("trunk", 1), first, "đã giải ra MỞ trước khi thi hành ⇒ cổng tốc độ của MỞ cốp áp được")
        exec(first, ok = false)
        assertEquals(byMem("trunk", 1), plan("ctl:trunk:flip"))
    }

    @Test
    fun `ke tiep vong theo so lua chon, so la thi lui ve lenh cuoi`() {
        assertEquals(byCar("seatc", 1), plan("ctl:seatc:next", state = 0))
        assertEquals(byCar("seatc", 0), plan("ctl:seatc:next", state = 2), "mức cuối ⇒ vòng về Tắt")
        assertEquals(byCar("seatc", 2), plan("ctl:seatc:next", count = 2, state = 0))
        assertEquals(byMem("seatc", 1), plan("ctl:seatc:next", state = null), "đọc hỏng ⇒ lệnh cuối (mặc định 0)")
        mem.record("seatc", 2)
        assertEquals(byMem("seatc", 0), plan("ctl:seatc:next", state = 7), "mã mức ngoài thang = không đọc được ⇒ lệnh cuối")
    }

    /** SELECT không readKey (dựng tay — registry hôm nay mọi SELECT đều đọc được) ⇒ Kế tiếp vòng theo lệnh cuối. */
    @Test
    fun `ke tiep cua nut khong readKey vong theo lenh cuoi, mot lua chon thi khong hop le`() {
        val d = ControlDef("zz_sel", "Chọn", "ic", ControlKind.SELECT, args = listOf("A", "B", "C"))
        val next = KeyCtlTarget("zz_sel", KeyCtlAction.NEXT)
        var reads = 0
        assertEquals(byMem("zz_sel", 1), KeyCtlPlan.of(d, next, memory = mem) { reads++; 2 })
        assertEquals(0, reads, "không readKey ⇒ không hỏi HAL")
        mem.record("zz_sel", 2)
        assertEquals(byMem("zz_sel", 0), KeyCtlPlan.of(d, next, memory = mem) { null })
        assertEquals(byMem("zz_sel", 1), KeyCtlPlan.of(d, next, count = 2, memory = mem) { null })
        val one = ControlDef("zz_one", "Một", "ic", ControlKind.SELECT, args = listOf("A"))
        assertEquals(Outcome.Invalid, KeyCtlPlan.of(one, KeyCtlTarget("zz_one", KeyCtlAction.NEXT), memory = mem) { null })
    }

    @Test
    fun `cau bao ma khong con hop le noi ma dich`() {
        assertTrue("ctl:x:y" in KeyCtlPlan.invalidReply("ctl:x:y", Lang.VI))
        assertTrue("ctl:x:y" in KeyCtlPlan.invalidReply("ctl:x:y", Lang.EN))
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
