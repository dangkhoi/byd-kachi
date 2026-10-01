package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Khoá MỘT LƯỢT DỌN (PROFILE-SWITCH-SLOTS R-B3/R-B4/R-B6) bằng kênh shell GHI ÂM — chuỗi lệnh từng byte, đúng thứ tự,
 * trả lời bằng dump `am stack list` NGUYÊN VĂN (xem KDoc [FloatingOrphanPlanTest]).
 *
 * Cặp `t0a-opened` → `t0a-after-remove` là trước/sau CỦA CÙNG MỘT lệnh `am stack remove 26` đo trên máy ảo
 * (`docs/diagnostics/profile-switch-slots-emulator-2026-10-01.md`) — nên ca vàng dưới đây không bịa trạng thái sau.
 *
 * Bất biến chung cho mọi ca: không `am force-stop`, không `--windowingMode 1` (fullscreenCmd), không `am task …`.
 */
class FloatingOrphanSweepTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-2026-10-01-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture am-stack-list-emulator-2026-10-01-$name.txt")

    private class Mem(var value: String? = null) : FloatingWindowLedger.Store {
        override fun read(): String? = value
        override fun write(value: String): Boolean { this.value = value; return true }
    }

    /** Shell ghi âm: mỗi lần `am stack list` trả bản kế tiếp trong [reads] (hết thì lặp bản cuối). */
    private class Rec(private val reads: List<String>, private val onRemove: (String) -> String = { "" }) : (String) -> String {
        val calls = ArrayList<String>()
        private var i = 0
        override fun invoke(cmd: String): String {
            calls += cmd
            return when {
                cmd == FloatingOrphanPlan.LIST_CMD -> reads[minOf(i++, reads.lastIndex)]
                else -> onRemove(cmd)
            }
        }
    }

    private val self = "com.byd.launcher"
    private val yt = "com.google.android.youtube"
    private val vm = "vn.vietmap.live"

    /** Giấc ngủ ghi âm của vòng đọc lại ([FloatingOrphanSweep.settle]) — test không ngủ thật. */
    private val naps = ArrayList<Long>()
    private fun sweep(m: Mem) = FloatingOrphanSweep(FloatingWindowLedger(m), self, sleep = { naps += it })

    private fun assertNoForbidden(calls: List<String>) = calls.forEach {
        assertTrue(!it.contains("force-stop") && !it.contains("--windowingMode") && !it.startsWith("am task"), "lệnh cấm: $it")
    }

    @Test
    fun `vang - doc, go dung stack 26, doc lai, xoa dau`() {
        val m = Mem("$yt"); val sh = Rec(listOf(fixture("t0a-opened"), fixture("t0a-after-remove")))
        val r = sweep(m).run(sh, held = setOf(vm), reason = "shell-up")
        assertEquals(listOf("am stack list", "am stack remove 26", "am stack list"), sh.calls)
        assertEquals(FloatingOrphanSweep.Outcome.DONE, r.outcome)
        assertEquals(listOf(26), r.removed)
        assertEquals(setOf(yt), r.forgotten)
        assertEquals("", m.value, "dấu phải rỗng sau khi cửa sổ đã đóng")
        assertTrue(r.line().contains("shell-up") && r.line().contains("[26]"), r.line())
        assertEquals(emptyList<Long>(), naps, "bản đọc lại đầu đã thấy stack đi ⇒ không ngủ")
        assertEquals(1, r.settleReads)
    }

    /**
     * Khoá lỗi E2E 01/10 ca 3b [ĐO máy ảo]: dòng `KachiFloat` lúc 20:14:17.607 ghi `gỡ=[]/[40] còn-nổi=[vn.vietmap.live]
     * xoá-dấu=[]` dù VietMap kết thúc ngay sau đó — bản đọc lại chạy TRƯỚC khi task rời stack (AOSP r47
     * `TaskRecord.removeTaskActivitiesLocked` chỉ yêu cầu activity kết thúc, KDoc `settle`). Dẫn xuất từ cặp t0a:
     * lần đọc lại đầu trả LẠI `t0a-opened` (stack 26 còn), lần sau trả `t0a-after-remove`.
     */
    @Test
    fun `doc lai ngay sau lenh go con thay stack thi cho roi doc tiep - khong ghi sai, khong giu dau thua`() {
        val m = Mem("$yt")
        val sh = Rec(listOf(fixture("t0a-opened"), fixture("t0a-opened"), fixture("t0a-after-remove")))
        val r = sweep(m).run(sh, held = setOf(vm), reason = "shell-up")
        assertEquals(listOf("am stack list", "am stack remove 26", "am stack list", "am stack list"), sh.calls)
        assertEquals(listOf(FloatingOrphanSweep.SETTLE_STEP_MS), naps)
        assertEquals(listOf(26), r.removed, r.line())
        assertEquals(emptySet<String>(), r.stillFloating)
        assertEquals("", m.value, "cửa sổ đã đóng thật ⇒ dấu phải rỗng ngay lượt này")
        assertTrue(r.line().contains("đọc-lại=2"), r.line())
        assertNoForbidden(sh.calls)
    }

    @Test
    fun `noshell-A-back - chi go 3, lenh go khong an thi doc lai toi tran roi giu dau de luot sau lam tiep`() {
        val back = fixture("noshell-A-back")
        val m = Mem("$vm,$yt"); val sh = Rec(listOf(back))   // mọi lần đọc lại: stack 3 VẪN còn
        val r = sweep(m).run(sh, held = setOf(vm), reason = "evict")
        val reads = FloatingOrphanSweep.SETTLE_READS
        assertEquals(listOf("am stack list", "am stack remove 3") + List(reads) { "am stack list" }, sh.calls)
        assertEquals(List(reads - 1) { FloatingOrphanSweep.SETTLE_STEP_MS }, naps, "có trần — không chặn luồng nền mãi")
        assertEquals(listOf(3), r.sent); assertEquals(emptyList<Int>(), r.removed)
        assertEquals(listOf(vm, yt), FloatingWindowLedger(m).opened(), "chưa đóng được ⇒ không được xoá dấu")
        assertNoForbidden(sh.calls)
    }

    @Test
    fun `bi ngat luc cho doc lai thi khong nem, giu co ngat, giu dau theo ban doc gan nhat`() {
        val m = Mem("$yt")
        val sh = Rec(listOf(fixture("t0a-opened")))
        val sweep = FloatingOrphanSweep(FloatingWindowLedger(m), self, sleep = { throw InterruptedException("shutdownNow") })
        try {
            val r = sweep.run(sh, held = emptySet(), reason = "evict")
            assertEquals(FloatingOrphanSweep.Outcome.DONE, r.outcome)
            assertEquals(listOf("am stack list", "am stack remove 26", "am stack list"), sh.calls)
            assertEquals(yt, m.value, "bản đọc gần nhất còn thấy YouTube nổi ⇒ giữ dấu")
            assertTrue(Thread.currentThread().isInterrupted, "phải trả lại cờ ngắt cho executor đang tắt")
        } finally {
            Thread.interrupted()   // dọn cờ cho các test sau chạy cùng luồng
        }
    }

    @Test
    fun `dau rong thi 0 lenh shell`() {
        val sh = Rec(listOf(fixture("noshell-A-back")))
        val r = FloatingOrphanSweep(FloatingWindowLedger(Mem()), self).run(sh, emptySet(), "evict")
        assertEquals(emptyList<String>(), sh.calls)
        assertEquals(FloatingOrphanSweep.Outcome.EMPTY_LEDGER, r.outcome)
        assertTrue(r.line().contains("dấu rỗng"))
    }

    @Test
    fun `khong co gi de go thi chi doc mot lan va xoa dau goi da het noi`() {
        // embedded-initial: VietMap ở màn ảo display 2 — không có cửa sổ nổi nào; YouTube không còn ở đâu cả.
        val m = Mem("$yt,$vm"); val sh = Rec(listOf(fixture("embedded-initial")))
        val r = FloatingOrphanSweep(FloatingWindowLedger(m), self).run(sh, held = setOf(vm), reason = "shell-up")
        assertEquals(listOf("am stack list"), sh.calls)
        assertEquals(setOf(yt), r.forgotten)
        assertEquals(listOf(vm), FloatingWindowLedger(m).opened(), "gói còn giữ ở lại trong dấu")
    }

    @Test
    fun `doc hong - nem loi hoac rong - thi khong go va giu dau`() {
        val throwing: (String) -> String = { throw IOException("dadb đứt") }
        val m = Mem("$yt")
        val r1 = FloatingOrphanSweep(FloatingWindowLedger(m), self).run(throwing, emptySet(), "evict")
        assertEquals(FloatingOrphanSweep.Outcome.READ_FAILED, r1.outcome)
        assertTrue(r1.line().contains("IOException"), r1.line())
        val sh = Rec(listOf(""))
        val r2 = FloatingOrphanSweep(FloatingWindowLedger(m), self).run(sh, emptySet(), "evict")
        assertEquals(FloatingOrphanSweep.Outcome.READ_FAILED, r2.outcome)
        assertEquals(listOf("am stack list"), sh.calls)
        assertEquals(yt, m.value)
    }

    @Test
    fun `doc lan hai hong thi giu dau`() {
        val m = Mem("$yt"); val sh = Rec(listOf(fixture("t0a-opened"), ""))
        val r = sweep(m).run(sh, emptySet(), "evict")
        assertEquals(FloatingOrphanSweep.Outcome.READ2_FAILED, r.outcome)
        assertEquals(listOf(26), r.sent)
        assertEquals(yt, m.value)
    }

    @Test
    fun `lenh go nem loi thi khong sap, ghi loi, giu dau`() {
        val m = Mem("$yt")
        val sh = Rec(listOf(fixture("t0a-opened"))) { throw IllegalStateException("kênh đóng") }
        val r = FloatingOrphanSweep(FloatingWindowLedger(m), self).run(sh, emptySet(), "evict")
        assertEquals(FloatingOrphanSweep.Outcome.DONE, r.outcome)
        assertEquals(emptyList<Int>(), r.sent)
        assertTrue(r.line().contains("lỗi"), r.line())
        assertEquals(yt, m.value, "YouTube còn nổi ⇒ dấu còn")
    }

    // ── Guard ở TẦNG THI HÀNH ────────────────────────────────────────────────────────────────────

    @Test
    fun `guard thi hanh tu choi stack home, man ao, goi ngoai dau, id la - khong mot lenh nao`() {
        val e = StackParse.parse(fixture("t0a-opened"))
        val sweep = FloatingOrphanSweep(FloatingWindowLedger(Mem()), self)
        val sh = Rec(listOf(""))
        val all = listOf(self, yt, vm)
        assertEquals(FloatingOrphanSweep.Exec.REFUSED, sweep.removeStack(sh, 0, e, all, emptySet()), "home")
        assertEquals(FloatingOrphanSweep.Exec.REFUSED, sweep.removeStack(sh, 24, e, all, emptySet()), "màn ảo 15")
        assertEquals(FloatingOrphanSweep.Exec.REFUSED, sweep.removeStack(sh, 26, e, listOf(vm), emptySet()), "ngoài dấu")
        assertEquals(FloatingOrphanSweep.Exec.REFUSED, sweep.removeStack(sh, 26, e, all, setOf(yt)), "còn giữ")
        assertEquals(FloatingOrphanSweep.Exec.REFUSED, sweep.removeStack(sh, 999, e, all, emptySet()), "id lạ")
        assertEquals(emptyList<String>(), sh.calls)
        assertEquals(FloatingOrphanSweep.Exec.SENT, sweep.removeStack(sh, 26, e, all, emptySet()))
        assertEquals(listOf("am stack remove 26"), sh.calls)
    }
}
