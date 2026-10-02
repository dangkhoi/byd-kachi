package com.byd.clusternav.launcher.behind

import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T-M1 (spec shortcuts-autostart §5.0) — `am stack list` NGUYÊN BẢN (có dòng `configuration`) chụp trên máy ảo A10
 * 02/10 trong một lượt THẬT của Kachi: giọng nói "mở đồng hồ vào ô 1" (cầu kiểm thử `say`) khi ô 1 = VietMap.
 * Bằng chứng: `docs/diagnostics/behind-home-emulator-2026-10-01/impl/e1/` (`s00` · `s11`–`s14`) và `impl/e6/after-kill.txt`.
 *
 *  - `tm1-before-swap` — VietMap (task 2258) một mình trên màn ảo 83.
 *  - `tm1-b-on-top` — Đồng hồ (2259, stack 121) đã ở đỉnh màn ảo, VietMap (stack 120) nằm dưới (O1).
 *  - `tm1-anchor` — stack giữ chỗ 122 của CHÍNH Kachi (`BehindAnchorActivity`) nằm ĐÁY display 0, `standard`
 *    `fullscreen`, ẩn; `onCreate` không chạy (events: không có `am_on_create_called` của nó).
 *  - `tm1-moved` — sau `am stack move-task 2258 122 true`: VietMap trong S trên giữ chỗ.
 *  - `tm1-after` — sau `finishAndRemoveTask` của giữ chỗ: S chỉ còn VietMap; HOME vẫn đỉnh.
 *  - `tm1-killed-surfaced` — sau `kill -9` Kachi (kiểu BYD): VietMap nổi lên `visible=true`, stack home rỗng.
 */
class BehindHomePlanTm1Test {

    private fun fx(name: String) = StackParse.parse(
        javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-2026-10-02-tm1-$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture am-stack-list-emulator-2026-10-02-tm1-$name.txt"),
    )

    private val vm = "vn.vietmap.live"
    private val clock = "com.google.android.deskclock"
    private val self = "com.byd.launcher"
    private val anchor = "com.byd.launcher/com.byd.clusternav.launcher.behind.BehindAnchorActivity"
    private val home = "com.byd.launcher/com.byd.clusternav.launcher.KachiHome"

    @Test
    fun `chuoi that - B len dinh, giu cho Kachi o day, move-task, go giu cho`() {
        assertEquals(BehindHomePlan.Evict.Go(2258), BehindHomePlan.checkEvict(fx("b-on-top"), 83, vm, clock, self, false))
        assertEquals(BehindHomePlan.Anchor.Ok(122), BehindHomePlan.pickAnchor(fx("b-on-top"), fx("anchor"), anchor))
        val top0 = BehindHomePlan.topStackId(fx("anchor"), 0)
        assertEquals(0, top0, "giữ chỗ không lên đỉnh — HOME (stack 0) vẫn đầu display 0")
        assertTrue(BehindHomePlan.homeOnTop(fx("anchor"), emptyList()), "đọc được loại `home` bằng chữ")
        assertEquals(BehindHomePlan.Moved.OK, BehindHomePlan.verifyMoved(fx("moved"), 2258, 122, top0))
        assertEquals(BehindHomePlan.Moved.OK, BehindHomePlan.verifyMoved(fx("after"), 2258, 122, top0))
        assertEquals(listOf(122), BehindHomePlan.anchorStacks(fx("moved"), anchor))
        assertTrue(BehindHomePlan.anchorStacks(fx("after"), anchor).isEmpty(), "giữ chỗ đã gỡ — không mồ côi")
    }

    @Test
    fun `truoc khi B len dinh thi khong duoc day (A con o dinh man ao)`() {
        assertEquals(BehindHomePlan.Evict.Stop(BehindHomePlan.Why.A_ON_TOP),
            BehindHomePlan.checkEvict(fx("before-swap"), 83, vm, clock, self, false))
    }

    @Test
    fun `R1-8 - sau khi go giu cho, VietMap la app sau man nha theo nghia chat`() {
        val e = fx("after")
        assertEquals(2258, BehindHomePlan.behindTaskOf(e, vm)?.taskId)
        assertNull(BehindHomePlan.behindTaskOf(fx("moved"), vm), "S còn giữ chỗ ⇒ chưa phải hình dạng chặt")
        assertNull(BehindHomePlan.behindTaskOf(e, clock), "Đồng hồ ở màn ảo, không phải sau màn nhà")
        assertFalse(BehindHomePlan.homeOnTop(fx("killed-surfaced"), listOf(home)))
    }

    @Test
    fun `dau ben - Kachi chet thi app co dau noi len, HOME o tren thi khong`() {
        val marks = mapOf(2258 to vm)
        assertTrue(BehindMarks.surfaced(fx("killed-surfaced"), marks), "stack rỗng 71/53 ở trên không được che mắt phép đo")
        assertFalse(BehindMarks.surfaced(fx("after"), marks), "HOME đang hiện ở trên ⇒ không làm gì")
        assertFalse(BehindMarks.surfaced(fx("killed-surfaced"), mapOf(2258 to "com.other")), "sai gói ⇒ không phải dấu của ta")
        assertFalse(BehindMarks.surfaced(fx("killed-surfaced"), emptyMap()))
        assertFalse(BehindMarks.surfaced(emptyList(), marks), "đọc hỏng ⇒ không làm gì")
    }

    /**
     * Review lượt 2 [P2] — ba phép "đỉnh display 0" cùng một luật ([BehindHomePlan.topStackId] · [BehindHomePlan.topVisibleStackId]):
     *  (a) stack RỖNG của `KachiHomeActivity` đã chết (71/53, không hiện) nằm trên cùng KHÔNG phải "màn nhà ở đỉnh" khi nhận
     *      cả dạng activity thật (`DefaultHome.shownComponents`) — nếu không, K12 đè lên app người dùng đang thấy;
     *  (b) cửa sổ PIP (luôn trên cùng — A10 r47 `ActivityDisplay.java:302-322`) không che mắt phép "đỉnh": khối PIP NGUYÊN
     *      VĂN (stack 54, GMaps) của fixture xe `camera-under-pip-derived` đặt trên `killed-surfaced` / `after` (DẪN XUẤT).
     */
    @Test
    fun `dinh display 0 - bo stack rong khong hien va bo PIP`() {
        val homes = listOf(home, "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity")
        assertEquals(71, fx("killed-surfaced").first { it.displayId == 0 }.stackId, "đầu danh sách là stack rỗng đã chết")
        assertFalse(BehindHomePlan.homeOnTop(fx("killed-surfaced"), homes), "(a) stack rỗng không hiện ≠ màn nhà ở đỉnh")
        assertTrue(BehindHomePlan.homeOnTop(fx("after"), homes))

        val pip = (javaClass.getResourceAsStream("/diagnostics/am-stack-list-oncar-2026-09-29-camera-under-pip-derived.txt")
            ?.bufferedReader()?.readText() ?: error("thiếu fixture camera-under-pip-derived")).split("\n\n").first { "Stack id=54 " in it }
        fun withPip(name: String) = StackParse.parse(
            pip + "\n\n" + (javaClass.getResourceAsStream("/diagnostics/am-stack-list-emulator-2026-10-02-tm1-$name.txt")
                ?.bufferedReader()?.readText() ?: error("thiếu fixture tm1-$name")),
        )
        assertTrue(withPip("after").first { it.displayId == 0 }.isPinned, "dẫn xuất đúng: PIP đứng đầu display 0")
        assertTrue(BehindMarks.surfaced(withPip("killed-surfaced"), mapOf(2258 to vm)), "(b) app có dấu nổi lên dưới PIP vẫn là che màn nhà")
        assertTrue(BehindHomePlan.homeOnTop(withPip("after"), homes), "(b) PIP trên màn nhà ⇒ màn nhà vẫn ở đỉnh")
        assertEquals(BehindHomePlan.topStackId(fx("after"), 0), BehindHomePlan.topStackId(withPip("after"), 0), "(b) PIP không đổi đỉnh")
    }

    @Test
    fun `dau ben - tia dau task da mat, giu dau task con`() {
        assertEquals(mapOf(2258 to vm), BehindMarks.prune(fx("after"), mapOf(2258 to vm, 1999 to clock)))
        assertEquals(mapOf(2258 to vm), BehindMarks.prune(emptyList(), mapOf(2258 to vm)), "đọc hỏng ⇒ giữ nguyên")
    }

    @Test
    fun `dau ben - ma hoa khu hoi, chuoi la bi bo, tran 16`() {
        val m = (1..20).fold(emptyMap<Int, String>()) { acc, i -> BehindMarks.add(acc, i, "com.app$i") }
        assertEquals(BehindMarks.MAX, m.size)
        assertEquals(5, m.keys.first(), "bỏ dấu cũ nhất")
        assertEquals(m, BehindMarks.decode(BehindMarks.encode(m)))
        assertEquals(mapOf(7 to "a.b"), BehindMarks.decode("7:a.b,x:y,0:c.d,9:bad pkg,:z"))
    }
}
