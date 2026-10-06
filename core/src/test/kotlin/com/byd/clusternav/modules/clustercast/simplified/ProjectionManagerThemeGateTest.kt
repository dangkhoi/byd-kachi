package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Review 2.89 Pass 1 · safety-1 + B1a — [ProjectionManager.open] theo lời cổng ([ThemeVerdict]) và kế hoạch kiểu cụm
 * ([ClusterStylePlan]).
 *
 * Lỗi khoá ở đây: bản đầu 2.89 coi MỌI lời từ chối là "bỏ opcode, gửi tiếp 16 → 35" (`if (theme && !gate.admit(op)) continue`).
 * Lần mở đầu sau nổ máy (chưa có màn ảo fission, cụm ở theme GỐC — Seal theme2 10.25") mà cổng đọc hỏng ⇒ 16 → 35 trong theme
 * gốc = trạng thái [ĐO xe 05/10] "m/h lạc góc, mất số km/h" (`oncar-2026-10-05-slot-cluster.md` §4). Trước 2.89 bước 30 hỏng
 * cũng làm `open()` trả `false`. B1a khoá thêm: lần mở đầu Seal + Bo tròn ĐÚNG TỪNG BYTE `30 →2s→ 16 →2s→ 35 →1s` (CLAUDE.md
 * §6), opcode theme không bao giờ đi ra ngoài cổng, sổ `pending` trước lệnh.
 */
class ProjectionManagerThemeGateTest {

    private class Gate(
        private val answer: ThemeVerdict,
        private val entry: ThemeLedger.Entry? = null,
        private val clock: ThemeLedger.Now = ThemeLedger.Now.UNKNOWN,
        private val calls: MutableList<String>? = null,
        /** Pass 2 · cluster-r1-5 — `false` = sổ không ghi được `pending`. */
        private val ledgerWrites: Boolean = true,
    ) : ThemeGate {
        val asked = mutableListOf<Int>()
        val sent = mutableListOf<Int>()
        val sending = mutableListOf<Int>()
        override fun admit(op: Int): ThemeVerdict { asked += op; return answer }
        override fun sent(op: Int) { sent += op; calls?.add("sent:$op") }
        override fun sending(op: Int): Boolean { sending += op; calls?.add("sending:$op"); return ledgerWrites }
        override fun ledger(): ThemeLedger.Entry? = entry
        override fun now(): ThemeLedger.Now = clock
    }

    private class Shell(private val calls: MutableList<String> = mutableListOf()) : SimpleCastShell {
        val log: List<String> get() = calls.filter { it.startsWith("service call") }
        override fun execute(command: String): ShellResult { calls += command; return ShellResult(0, "", "") }
    }

    private fun ops(cmds: List<String>) = cmds.map { it.substringAfter("i32 1000 i32 ").substringBefore(" ").toInt() }

    @Test
    fun `B1a hoi quy - Seal Bo tron lan mo dau sau no may - DUNG TUNG BYTE 30 2s 16 2s 35 1s`() {
        val trace = mutableListOf<String>()
        val sh = Shell(trace)
        val gate = Gate(ThemeVerdict.SEND, calls = trace)
        val pm = ProjectionManager(sh, sleepMs = { trace += "sleep:$it" }, recipe = ProjectionRecipe.SEAL_DL3)
        assertTrue(pm.open(-1, gate, CastStyle.CURVED))
        assertEquals(
            listOf(
                "sending:30",
                "service call AutoContainer 2 i32 1000 i32 30 s16 \"\"",
                "sent:30",
                "sleep:2000",
                "service call AutoContainer 2 i32 1000 i32 16 s16 \"\"",
                "sleep:2000",
                "service call AutoContainer 2 i32 1000 i32 35 s16 \"\"",
                "sleep:1000",
            ),
            trace,
        )
        assertEquals(listOf(30), gate.asked)
        assertEquals(BelievedStyle.CURVED, pm.lastPlan?.believed)
    }

    @Test
    fun `SEND - du 30 16 35, bao sent(30)`() {
        val sh = Shell(); val gate = Gate(ThemeVerdict.SEND); val pm = ProjectionManager(sh, sleepMs = {})
        assertTrue(pm.open(-1, gate))
        assertEquals(listOf(30, 16, 35), ops(sh.log))
        assertEquals(listOf(30), gate.sent)
        assertEquals(listOf(30), gate.sending)
        assertNull(pm.abortedOn)
    }

    @Test
    fun `ABORT, so trong - dung, KHONG 16 35, khong mo`() {
        val sh = Shell(); val pm = ProjectionManager(sh, sleepMs = {})
        assertFalse(pm.open(2, Gate(ThemeVerdict.ABORT)))
        assertEquals(emptyList<String>(), sh.log)
        assertEquals(30, pm.abortedOn)
        assertFalse(pm.isOpen)
        assertTrue(pm.lastPlan!!.abort)
    }

    @Test
    fun `cong tu choi (SKIP_KNOWN) - nhat ky lenh DUNG 16 35 day du s16, khong sending-sent`() {
        val sh = Shell(); val gate = Gate(ThemeVerdict.SKIP_KNOWN); val pm = ProjectionManager(sh, sleepMs = {})
        assertTrue(pm.open(2, gate))
        assertEquals(
            listOf("service call AutoContainer 2 i32 1000 i32 16 s16 \"\"", "service call AutoContainer 2 i32 1000 i32 35 s16 \"\""),
            sh.log,
        )
        assertEquals(emptyList<Int>(), gate.sent, "không gửi thì không đánh dấu đã gửi")
        assertEquals(emptyList<Int>(), gate.sending, "không gửi thì không ghi pending")
    }

    @Test
    fun `SKIP_KNOWN khi CHUA co man ao cum (preOpenId lt 1) - coi nhu ABORT, so trong thi DUNG`() {
        val sh = Shell(); val pm = ProjectionManager(sh, sleepMs = {})
        assertFalse(pm.open(-1, Gate(ThemeVerdict.SKIP_KNOWN)))
        assertEquals(emptyList<String>(), sh.log)
        assertEquals(30, pm.abortedOn)
    }

    @Test
    fun `ABORT nhung so CUNG TIEN TRINH ghi 30 ok - cum da Bo tron, 16 35 di tiep (16-35 khong bao gio sap)`() {
        val now = ThemeLedger.Now(elapsedMs = 200_000, boot = 3, processStartMs = 100_000)
        val e = ThemeLedger.Entry(30, ThemeLedger.State.OK, 190_000, 3)
        val sh = Shell(); val pm = ProjectionManager(sh, sleepMs = {})
        assertTrue(pm.open(-1, Gate(ThemeVerdict.ABORT, e, now)))
        assertEquals(listOf(16, 35), ops(sh.log))
        assertEquals(BelievedStyle.CURVED, pm.lastPlan?.believed)
    }

    @Test
    fun `ABORT va so do tien trinh TRUOC ghi - khong chung minh duoc - DUNG`() {
        val now = ThemeLedger.Now(elapsedMs = 200_000, boot = 3, processStartMs = 195_000)
        val e = ThemeLedger.Entry(30, ThemeLedger.State.OK, 190_000, 3)
        val sh = Shell(); val pm = ProjectionManager(sh, sleepMs = {})
        assertFalse(pm.open(-1, Gate(ThemeVerdict.ABORT, e, now)))
        assertEquals(emptyList<String>(), sh.log)
    }

    /**
     * 2.90 · R2 — sổ `31;ok` (của bất kỳ tiến trình nào) KHÔNG còn là lý do bỏ hỏi cổng: [ĐO xe 06/10] theme giữ qua nổ máy, sổ không
     * nói được cụm đang ở kiểu nào. Lượt trùng trong cùng tiến trình do cổng đỡ (SAME_THEME). Thử ĐỎ: trả lại luật `nativeAlready`.
     */
    @Test
    fun `290 - Chu nhat tren xe goc chu nhat, so ghi 31 ok - VAN hoi cong`() {
        val recipe = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT)
        val e = ThemeLedger.Entry(31, ThemeLedger.State.OK, 5, 1)
        val sh = Shell(); val gate = Gate(ThemeVerdict.SEND, e); val pm = ProjectionManager(sh, sleepMs = {}, recipe = recipe)
        assertTrue(pm.open(-1, gate, CastStyle.RECT))
        assertEquals(listOf(31, 16, 35), ops(sh.log))
        assertEquals(listOf(31), gate.asked)
        assertEquals(BelievedStyle.RECT, pm.lastPlan?.believed)
    }

    @Test
    fun `Chu nhat tren xe goc chu nhat, so trong - xin 31 qua cong`() {
        val recipe = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT)
        val sh = Shell(); val gate = Gate(ThemeVerdict.SEND); val pm = ProjectionManager(sh, sleepMs = {}, recipe = recipe)
        assertTrue(pm.open(-1, gate, CastStyle.RECT))
        assertEquals(listOf(31, 16, 35), ops(sh.log))
        assertEquals(listOf(31), gate.asked)
    }

    @Test
    fun `Chu nhat tren xe CHUA biet kieu goc - RECT an, ve Bo tron (30)`() {
        val sh = Shell(); val gate = Gate(ThemeVerdict.SEND); val pm = ProjectionManager(sh, sleepMs = {})
        assertTrue(pm.open(-1, gate, CastStyle.RECT))
        assertEquals(listOf(30, 16, 35), ops(sh.log))
    }

    @Test
    fun `cong thuc dung tay con sot opcode theme trong castSeq - KHONG bao gio gui ngoai cong`() {
        val handMade = ProjectionRecipe("AutoContainer", listOf(31, 16, 35), listOf(18, 0), emptyMap(), null)
        val sh = Shell(); val gate = Gate(ThemeVerdict.SEND); val pm = ProjectionManager(sh, sleepMs = {}, recipe = handMade)
        assertTrue(pm.open(-1, gate))
        assertEquals(listOf(16, 35), ops(sh.log))
        assertEquals(emptyList<Int>(), gate.asked)
    }

    @Test
    fun `luot tat - opcode theme bi boc khoi chuoi tat (D6), chuoi tra dong ho van di`() {
        val recipe = ProjectionRecipe.of("AutoContainer", listOf(30, 16, 35), listOf(18, 30, 0), emptyMap(), null)
        assertEquals(listOf(18, 0), recipe.teardownSeq)
        val sh = Shell(); val pm = ProjectionManager(sh, sleepMs = {}, recipe = recipe)
        pm.resetState(true)
        assertTrue(pm.close(2, Gate(ThemeVerdict.ABORT)))
        assertEquals(listOf(18, 0), ops(sh.log))
        assertFalse(pm.isOpen)
    }

    /**
     * Review 2.89 Pass 2 · cluster-r1-5 — sổ không ghi được `pending` ⇒ KHÔNG gửi theme (CLAUDE.md §5: dấu trước, đổi sau). Sổ
     * trống ⇒ không chứng minh được kiểu cụm ⇒ DỪNG (0 lệnh). Thử ĐỎ: bỏ nhánh `!gate.sending(themeOp)` (bản cũ chỉ log).
     */
    @Test
    fun `so khong ghi duoc pending - KHONG gui theme, so trong thi DUNG luot mo`() {
        val sh = Shell(); val gate = Gate(ThemeVerdict.SEND, ledgerWrites = false); val pm = ProjectionManager(sh, sleepMs = {})
        assertFalse(pm.open(-1, gate))
        assertEquals(emptyList<String>(), sh.log, "không opcode nào: ${sh.log}")
        assertEquals(listOf(30), gate.sending)
        assertEquals(emptyList<Int>(), gate.sent)
        assertEquals(30, pm.abortedOn)
        assertTrue(pm.lastPlan!!.abort && pm.lastPlan!!.why.contains("không ghi được"), pm.lastPlan!!.why)
    }

    @Test
    fun `so khong ghi duoc pending nhung so CUNG TIEN TRINH chung minh cum da Bo tron - bo 30, 16 35 di tiep`() {
        val now = ThemeLedger.Now(elapsedMs = 200_000, boot = 3, processStartMs = 100_000)
        val e = ThemeLedger.Entry(30, ThemeLedger.State.OK, 150_000, 3)   // > 15 s trước ⇒ cổng cho gửi
        val sh = Shell(); val pm = ProjectionManager(sh, sleepMs = {})
        assertTrue(pm.open(-1, Gate(ThemeVerdict.SEND, e, now, ledgerWrites = false)))
        assertEquals(listOf(16, 35), ops(sh.log))
        assertEquals(BelievedStyle.CURVED, pm.lastPlan?.believed)
    }

    @Test
    fun `luot tat - opcode theme sot trong cong thuc dung tay, so khong ghi duoc - bo opcode do`() {
        val handMade = ProjectionRecipe("AutoContainer", listOf(16, 35), listOf(18, 30, 0), mapOf(CastStyle.CURVED to 30), null)
        val sh = Shell(); val pm = ProjectionManager(sh, sleepMs = {}, recipe = handMade)
        pm.resetState(true)
        assertTrue(pm.close(2, Gate(ThemeVerdict.SEND, ledgerWrites = false)))
        assertEquals(listOf(18, 0), ops(sh.log))
    }

    @Test
    fun `refreshRecipe - chi khi CHUA mo (chuoi tat phai khop chuoi da mo)`() {
        val pm = ProjectionManager(Shell(), sleepMs = {})
        val rect = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT)
        assertTrue(pm.refreshRecipe(rect))
        pm.resetState(true)
        assertFalse(pm.refreshRecipe(ProjectionRecipe.SEAL_DL3))
        assertEquals(rect, pm.recipe)
    }
}
