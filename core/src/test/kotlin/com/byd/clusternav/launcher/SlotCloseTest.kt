package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotCloseRun.Outcome
import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L6 · (c) *tắt* app trong ô — chọn stack ([SlotClosePlan]) + một lượt thi hành ([SlotCloseRun]) bằng shell GHI ÂM trên
 * fixture NGUYÊN VĂN `am stack list` (CLAUDE.md §10):
 *  - `…-2026-10-03-sc-maps-in-slot` — Google Maps trong màn ảo 231 của ô (stack 572, `standard`), máy ảo A10;
 *  - `…-2026-10-03-sc-maps-force-stopped` — cùng máy sau khi Maps rời màn ảo (đo bằng `am force-stop`, FIX286 R-SC §9).
 *    Dùng làm bản đọc LẠI "app không còn ở màn ảo" — hình dạng sau `am stack remove` trên màn ảo ô CHƯA đo [CHƯA BIẾT];
 *    cơ chế lệnh [ĐO nguồn] ở KDoc [SlotClosePlan];
 *  - `…-2026-10-02-behind-06-after-3` — hai app trong CÙNG màn ảo 44, bản dump thiếu dòng cấu hình (loại stack rỗng);
 *  - `…-api34-cluster-…` — định dạng `RootTask id=` (A12 / DL5).
 *
 * Mỗi ca khoá một cách làm hỏng xe: gỡ nhầm display (cụm / display 0), gỡ nhầm app khác cùng màn ảo, gỡ stack loại lạ
 * (A12 không tự chặn), coi bản đọc rỗng là "đã đóng", và `am force-stop` cả gói thay vì đúng task.
 */
class SlotCloseTest {

    private val maps = "com.google.android.apps.maps"
    private val self = "com.byd.launcher"

    private fun fx(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/diagnostics/am-stack-list-$name.txt")) { "thiếu fixture $name" }
            .bufferedReader().readText()

    /** Shell ghi âm: mỗi lần `am stack list` trả bản kế trong [reads] (hết thì lặp bản cuối); lệnh khác trả "". */
    private class Rec(private val reads: List<String>) {
        val calls = ArrayList<String>()
        private var i = 0
        val sh: (String) -> String = { cmd ->
            calls += cmd
            if (cmd == "am stack list") reads[minOf(i++, reads.size - 1)] else ""
        }
    }

    private val noSleep = ArrayList<Long>()
    private fun run(rec: Rec, vd: Int, pkg: String = maps) = SlotCloseRun(self) { noSleep += it }.run(rec.sh, vd, pkg)

    @Test
    fun `ca owner - Maps trong man ao o duoc go dung stack 572, doc lai thay da roi o`() {
        val rec = Rec(listOf(fx("emulator-2026-10-03-sc-maps-in-slot"), fx("emulator-2026-10-03-sc-maps-force-stopped")))
        val r = run(rec, vd = 231)
        assertEquals(Outcome.CLOSED, r.outcome)
        assertTrue(r.slotFree)
        assertEquals(listOf("am stack list", "am stack remove 572", "am stack list"), rec.calls)
        assertFalse(rec.calls.any { "force-stop" in it }, "tắt = đúng task trên màn ảo ô, KHÔNG force-stop cả gói")
    }

    @Test
    fun `chi chon stack tren DUNG man ao cua o`() {
        val e = StackParse.parse(fx("emulator-2026-10-03-sc-maps-in-slot"))
        assertEquals(listOf(572), SlotClosePlan.targets(e, 231, maps, self))
        assertEquals(emptyList<Int>(), SlotClosePlan.targets(e, 232, maps, self), "màn ảo khác ⇒ không gì")
        assertEquals(emptyList<Int>(), SlotClosePlan.targets(e, 0, maps, self), "display 0 (vd < 1) ⇒ không bao giờ")
        assertEquals(emptyList<Int>(), SlotClosePlan.targets(e, 231, "com.google.android.youtube", self), "app khác ⇒ không gì")
    }

    @Test
    fun `app khong con o man ao - 0 lenh go, o tu do`() {
        val rec = Rec(listOf(fx("emulator-2026-10-03-sc-maps-force-stopped")))
        val r = run(rec, vd = 231)
        assertEquals(Outcome.NOT_ON_VD, r.outcome)
        assertTrue(r.slotFree)
        assertEquals(listOf("am stack list"), rec.calls)
    }

    @Test
    fun `hai app cung man ao, ban dump thieu loai stack - KHONG go, khong dung app kia`() {
        val e = StackParse.parse(fx("emulator-2026-10-02-behind-06-after-3"))
        assertTrue(e.any { it.displayId == 44 && it.pkg == "vn.vietmap.live" })
        assertEquals(emptyList<Int>(), SlotClosePlan.targets(e, 44, "vn.vietmap.live", self), "loại stack trống ⇒ không gỡ (A12 không tự chặn)")
        val rec = Rec(listOf(fx("emulator-2026-10-02-behind-06-after-3")))
        val r = run(rec, vd = 44, pkg = "vn.vietmap.live")
        assertEquals(Outcome.REFUSED, r.outcome)
        assertFalse(r.slotFree, "ô KHÔNG được đổi khi chưa gỡ được")
        assertEquals(listOf("am stack list"), rec.calls)
    }

    /** Stack dựng tay theo đúng ba dòng của `am stack list` (A10 `Stack id=` / A12 `RootTask id=`). */
    private fun stack(head: String, id: Int, display: Int, type: String, mode: String, vararg tasks: String) = buildString {
        append("$head id=$id bounds=[0,0][800,480] displayId=$display userId=0\n")
        append(" configuration={1.0 winConfig={ mWindowingMode=$mode mDisplayWindowingMode=fullscreen mActivityType=$type mAlwaysOnTop=undefined}}\n")
        tasks.forEachIndexed { k, comp -> append("  taskId=${id * 10 + k}: $comp bounds=[0,0][800,480] userId=0 visible=true\n") }
    }

    private val home = stack("Stack", 0, 0, "home", "fullscreen", "$self/com.byd.clusternav.launcher.KachiHome")

    @Test
    fun `rao tang thi hanh - loai la, ghim, lan goi khac, chinh Kachi deu bi tu choi`() {
        val e = StackParse.parse(
            home +
                stack("Stack", 5, 7, "undefined", "fullscreen", "$maps/x.A") +
                stack("Stack", 6, 7, "standard", "pinned", "$maps/x.B") +
                stack("Stack", 8, 7, "standard", "fullscreen", "$maps/x.C", "com.other/y.D") +
                stack("Stack", 9, 7, "standard", "fullscreen", "$self/z.E") +
                stack("Stack", 11, 0, "standard", "fullscreen", "$maps/x.F"),
        )
        listOf(5, 6, 8, 11).forEach { assertFalse(SlotClosePlan.admissible(it, e, 7, maps, self), "stack $it") }
        assertFalse(SlotClosePlan.admissible(9, e, 7, self, self), "không bao giờ gỡ chính Kachi")
        assertFalse(SlotClosePlan.admissible(0, e, 0, self, self), "stack home")
        assertFalse(SlotClosePlan.admissible(42, e, 7, maps, self), "id không có trong bản đọc")
        assertEquals(emptyList<Int>(), SlotClosePlan.targets(e, 7, maps, self))
        assertEquals(emptyList<Int>(), SlotClosePlan.targets(e, 7, maps, ""), "không biết chính mình ⇒ không làm gì")
    }

    @Test
    fun `dinh dang A12 RootTask - cung lenh, cung rao`() {
        val inSlot = home.replace("Stack id", "RootTask id") + stack("RootTask", 31, 6, "standard", "fullscreen", "$maps/x.A")
        val gone = home.replace("Stack id", "RootTask id")
        assertEquals(listOf(31), SlotClosePlan.targets(StackParse.parse(inSlot), 6, maps, self))
        val rec = Rec(listOf(inSlot, gone))
        assertEquals(Outcome.CLOSED, run(rec, vd = 6).outcome)
        assertEquals(listOf("am stack list", "am stack remove 31", "am stack list"), rec.calls)
        // Bản A12 thật (cụm 1920×720): mọi app ở display 0 ⇒ không bao giờ là đích của một màn ảo ô.
        assertEquals(emptyList<Int>(), SlotClosePlan.targets(StackParse.parse(fx("api34-cluster-1920x720-d240")), 0, "com.chisadin.wazemod", self))
    }

    @Test
    fun `doc hong - khong lenh go, o khong doi`() {
        listOf("", "Error: unknown command 'stack'").forEach { out ->
            val rec = Rec(listOf(out))
            val r = run(rec, vd = 231)
            assertEquals(Outcome.READ_FAILED, r.outcome)
            assertFalse(r.slotFree)
            assertEquals(listOf("am stack list"), rec.calls)
        }
        val thrower = SlotCloseRun(self) {}.run({ error("dadb đứt") }, 231, maps)
        assertEquals(Outcome.READ_FAILED, thrower.outcome)
    }

    /**
     * A3 · SLOT-CLOSE-SETTLE — ĐỔI GHIM có lý do ([ĐO xe 05/10]: `tắt vn.vietmap.live vd=4 → STILL_THERE gửi=[17] đọc-lại=5`,
     * app chỉ rời ô ~27 s sau): cửa sổ 5 × 250 ms cũ ngắn hơn lệnh gỡ trên xe ⇒ nay đọc lại theo lịch giãn dần ~4 s rồi mới
     * kết luận STILL_THERE.
     */
    @Test
    fun `gui lenh ma doc lai van con - doc lai het lich ~4 s roi moi bao STILL_THERE`() {
        val inSlot = fx("emulator-2026-10-03-sc-maps-in-slot")
        val rec = Rec(listOf(inSlot))
        noSleep.clear()
        val r = run(rec, vd = 231)
        assertEquals(Outcome.STILL_THERE, r.outcome)
        assertFalse(r.slotFree)
        val steps = SlotCloseRun.SETTLE_STEPS_MS
        assertEquals(1 + steps.size, r.reads)
        assertEquals(steps.toList(), noSleep, "nghỉ đúng lịch, không nhịp đều")
        assertEquals(SlotCloseSettle.BUDGET_MS, r.waitedMs)
        assertEquals(listOf("am stack list", "am stack remove 572") + List(1 + steps.size) { "am stack list" }, rec.calls)
        assertTrue("chờ=4000ms" in r.line(), r.line())
    }

    /**
     * A3 — ca xe: lệnh gỡ chậm hơn 5 × 250 ms (cửa sổ cũ) nhưng xong trong lịch mới ⇒ CLOSED (ô áp luật hoàn ô ngay, không
     * câu "chưa tắt được", không chờ nhịp đo ô ~27 s). Lần đọc lại thứ 6 mới thấy app rời ô (đã nghỉ 250+400+640+1000+1000 ms).
     */
    @Test
    fun `lenh go cham hon cua so cu - lich moi doi duoc, ket luan CLOSED`() {
        val inSlot = fx("emulator-2026-10-03-sc-maps-in-slot")
        val gone = fx("emulator-2026-10-03-sc-maps-force-stopped")
        val rec = Rec(listOf(inSlot) + List(5) { inSlot } + gone)
        noSleep.clear()
        val r = run(rec, vd = 231)
        assertEquals(Outcome.CLOSED, r.outcome, r.line())
        assertTrue(r.slotFree)
        assertEquals(6, r.reads, "đọc lại: 5 lần còn thấy (đủ cửa sổ cũ) + lần thứ 6 thấy rời")
        assertEquals(3_290L, r.waitedMs)
        assertTrue(r.reads > FloatingOrphanSweep.SETTLE_READS, "cửa sổ cũ (5 lần đọc) đã kết luận nhầm STILL_THERE ở ca này")
    }

    @Test
    fun `bao DA GUI dung mot lan, TRUOC vong doc lai, chi khi co lenh go`() {
        val events = ArrayList<String>()
        val rec = Rec(listOf(fx("emulator-2026-10-03-sc-maps-in-slot"), fx("emulator-2026-10-03-sc-maps-force-stopped")))
        val sh: (String) -> String = { cmd -> events += cmd; rec.sh(cmd) }
        val r = SlotCloseRun(self) {}.run(sh, 231, maps) { events += "SENT" }
        assertEquals(Outcome.CLOSED, r.outcome)
        assertEquals(listOf("am stack list", "am stack remove 572", "SENT", "am stack list"), events)
        // Không gửi được lệnh nào (đã rời ô · bị rào · đọc hỏng · lệnh ném) ⇒ KHÔNG báo đang tắt (mặt vẽ giữ nguyên).
        var sent = 0
        SlotCloseRun(self) {}.run(Rec(listOf(fx("emulator-2026-10-03-sc-maps-force-stopped"))).sh, 231, maps) { sent++ }
        SlotCloseRun(self) {}.run(Rec(listOf("")).sh, 231, maps) { sent++ }
        SlotCloseRun(self) {}.run(Rec(listOf(fx("emulator-2026-10-02-behind-06-after-3"))).sh, 44, "vn.vietmap.live") { sent++ }
        SlotCloseRun(self) {}.run({ cmd -> if (cmd.startsWith("am stack remove")) error("x") else fx("emulator-2026-10-03-sc-maps-in-slot") }, 231, maps) { sent++ }
        assertEquals(0, sent)
    }

    @Test
    fun `lich doc lai - nhip dau 250 ms, gian dan, tran 1 s, tong dung ngan sach`() {
        val s = SlotCloseSettle.steps()
        assertEquals(listOf(250L, 400L, 640L, 1_000L, 1_000L, 710L), s.toList())
        assertEquals(SlotCloseSettle.BUDGET_MS, s.sum())
        assertEquals(FloatingOrphanSweep.SETTLE_STEP_MS, s.first(), "ca nhanh kết luận như cũ (một nhịp 250 ms)")
        assertTrue(s.all { it <= SlotCloseSettle.CAP_MS })
        assertTrue(s.dropLast(1).zipWithNext().all { (a, b) -> b >= a }, "không giảm (trừ phần dư cuối)")
        assertEquals(listOf(250L, 250L, 250L, 250L), SlotCloseSettle.steps(250, 1.0, 250, 1_000).toList(), "hệ số 1 = nhịp đều")
        assertEquals(listOf(250L), SlotCloseSettle.steps(250, 2.0, 1_000, 400).toList(), "phần dư < nhịp đầu ⇒ bỏ")
        assertEquals(0L, SlotCloseSettle.waited(s, 1))
        assertEquals(650L, SlotCloseSettle.waited(s, 3))
        assertEquals(4_000L, SlotCloseSettle.waited(s, 99), "kẹp trong lịch")
    }

    @Test
    fun `lenh go nem loi - khong sap luong nen, bao STILL_THERE`() {
        val inSlot = fx("emulator-2026-10-03-sc-maps-in-slot")
        val calls = ArrayList<String>()
        val r = SlotCloseRun(self) {}.run({ cmd -> calls += cmd; if (cmd.startsWith("am stack remove")) error("kênh từ chối") else inSlot }, 231, maps)
        assertEquals(Outcome.STILL_THERE, r.outcome)
        assertTrue(r.error!!.contains("572"))
        assertEquals(listOf("am stack list", "am stack remove 572"), calls)
    }

    @Test
    fun `vd duoi 1, goi rong, chinh Kachi - 0 lenh`() {
        listOf(Triple(0, maps, "vd"), Triple(231, "", "gói rỗng"), Triple(231, self, "chính mình")).forEach { (vd, pkg, why) ->
            val rec = Rec(listOf(fx("emulator-2026-10-03-sc-maps-in-slot")))
            assertEquals(Outcome.REFUSED, run(rec, vd, pkg).outcome, why)
            assertEquals(emptyList<String>(), rec.calls, why)
        }
    }

    @Test
    fun `dong nhat ky ghi du gui va so lan doc`() {
        val line = SlotCloseRun.Report(231, maps, Outcome.CLOSED, listOf(572), 1).line()
        assertTrue("572" in line && "CLOSED" in line && "vd=231" in line, line)
    }
}
