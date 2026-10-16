package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Khoá hồi quy R29-RESOLVE-STORM ([ĐO xe 15/10] `mặt` ~700/phút, `đổi-họ` 7–22; [ĐO máy ảo 15/10] kẻ kích `tô-chưa-đo` ~180/phút:
 * nhịp đổ số tô lại nhãn nhiệt lốp `GONE` trên khung 3D — `docs/diagnostics/oncar-perf-2026-10-15-evening.md` §D/§F).
 *
 * Mô phỏng ĐÚNG luồng của `AdaptiveInk.inkUnsafe` (cổng [InkSolveGate] → [InkCoalescer] → lượt chọn đi CẢ cây mặt gốc: phần tử
 * đo được thì có nền + vào `seen`; phần tử chưa đo được thì không — như `groundOf`). Bỏ nhánh [InkSolveGate.Ink.DEFER] (hành vi
 * 3.1.8: thiếu nền ⇒ chọn) ⇒ ca 1 đỏ 60 ≠ 0; bỏ gộp ⇒ ca 5 đỏ.
 */
class InkSolveGateTest {

    private class El(var measurable: Boolean) { var grounded = false }

    /** Một mặt gốc với các phần tử; [turn] = kết thúc một lượt vòng lặp chính (chạy hẹn của bộ gộp). */
    private class Root {
        val els = ArrayList<El>()
        val seen = HashSet<El>()
        var chosen = false
        var solves = 0
        private val q = ArrayList<Runnable>()
        val gate = InkCoalescer<Root>({ q += it }) { it.solve() }
        fun solve() {
            solves++; chosen = true
            for (e in els) if (e.measurable) { e.grounded = true; seen += e }
        }
        fun ink(e: El) {
            when (InkSolveGate.onInk(e.grounded, e.measurable, stale = false, chosen = chosen, seen = e in seen)) {
                InkSolveGate.Ink.PAINT, InkSolveGate.Ink.DEFER -> Unit
                InkSolveGate.Ink.SOLVE -> if (gate.request(this)) solve()
            }
        }
        /** `OnLayoutChangeListener` của mặt: lượt sự kiện chọn lại khi có phần tử mới đo được (nền đổi / `seen` đổi). */
        fun layout() { if (els.any { it.measurable && !it.grounded }) solve() }
        fun turn() { val r = q.toList(); q.clear(); r.forEach { it.run() } }
    }

    private fun settled(vararg els: El) = Root().apply { this.els += els; solve(); solves = 0 }

    @Test
    fun `1 - 60 loi to tren phan tu GONE - 0 luot chon`() {
        val gone = El(measurable = false)
        val r = settled(El(true), gone)
        repeat(60) { r.ink(gone); r.turn() }
        assertEquals(0, r.solves, "phần tử chưa đo được không được kéo lượt chọn họ (bão R29)")
    }

    @Test
    fun `2 - cung vai cung nen - 0 luot chon`() {
        val e = El(true)
        val r = settled(e)
        repeat(60) { r.ink(e); r.turn() }
        assertEquals(0, r.solves)
    }

    @Test
    fun `3 - phan tu co co qua layout - dung 1 luot, sau do 0`() {
        val gone = El(measurable = false)
        val r = settled(El(true), gone)
        repeat(10) { r.ink(gone); r.turn() }
        gone.measurable = true; r.layout()
        repeat(60) { r.ink(gone); r.turn() }
        assertEquals(1, r.solves)
    }

    @Test
    fun `4 - 8 phan tu moi trong mot nhip - toi da 1 luot`() {
        val r = settled(El(true))
        val fresh = List(8) { El(true) }.also { r.els += it }
        fresh.forEach(r::ink); r.turn()
        assertEquals(1, r.solves, "lượt đầu đi cả cây ⇒ phần tử sau đã có trong seen")
    }

    @Test
    fun `5 - bo gop - cung mat goc nhieu loi trong mot luot = 1 ngay + 1 hen`() {
        var solved = 0
        val q = ArrayList<Runnable>()
        val g = InkCoalescer<String>({ q += it }) { solved++ }
        assertEquals(true, g.request("root"))
        repeat(7) { assertEquals(false, g.request("root")) }
        assertEquals(1, q.size, "một hẹn duy nhất mỗi lượt")
        q.toList().also { q.clear() }.forEach { it.run() }
        assertEquals(1, solved)
        q.toList().also { q.clear() }.forEach { it.run() }   // lượt kế không ai xin ⇒ không chạy gì, không hẹn tiếp
        assertEquals(1, solved); assertEquals(0, q.size)
        assertEquals(true, g.request("root"), "lượt mới ⇒ lời đầu chạy ngay")
    }

    @Test
    fun `6 - ke kich`() {
        assertEquals(ContrastLedger.Trigger.INK_STALE, InkSolveGate.why(stale = true, chosen = false))
        assertEquals(ContrastLedger.Trigger.INK_FIRST, InkSolveGate.why(stale = false, chosen = false))
        assertEquals(ContrastLedger.Trigger.INK_NEW, InkSolveGate.why(stale = false, chosen = true))
        assertEquals(InkSolveGate.Ink.SOLVE, InkSolveGate.onInk(grounded = true, measurable = true, stale = true, chosen = true, seen = true))
        assertEquals(InkSolveGate.Ink.DEFER, InkSolveGate.onInk(grounded = true, measurable = false, stale = true, chosen = true, seen = true))
    }
}
