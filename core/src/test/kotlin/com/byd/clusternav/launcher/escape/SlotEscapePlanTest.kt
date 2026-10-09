package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.escape.SlotEscapePlan.Adopt
import com.byd.clusternav.launcher.escape.SlotEscapePlan.Step
import com.byd.clusternav.launcher.escape.SlotEscapePlan.Why
import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R7 (SLOT-ESCAPE-POLICY) — khoá quyết định nhận / đối chiếu / trả bằng bản đọc THẬT.
 *
 * Fixture (`core/src/test/resources/diagnostics/`, [ĐO máy ảo A10 kachi_play 09/10], Waze task 301, khung ô 1 ô
 * `[19,89][1901,985]` = đúng số đo trên xe Seal — evidence `oncar-freeform-waze-2026-10-09.md` §2):
 *  - `…-r7-waze-fullscreen-top` — Waze toàn màn trên đỉnh display 0 (vừa thoát ô), màn nhà Kachi khuất.
 *  - `…-r7-waze-freeform-top` — sau mã 89 mode 5 + `am task resize`: freeform đúng khung, nổi trên màn nhà (home `visible=true`).
 *  - `…-r7-freeform-under-home` — sau Home: freeform nằm dưới màn nhà (`visible=false`).
 *  - `…-r7-released-under-home` — sau mã 89 mode 1 toTop 0: toàn màn, vẫn dưới màn nhà.
 *  - `…-2026-10-02-esc-after-move` — Waze thoát ô nằm DƯỚI VietMap toàn màn (app khác ở đỉnh).
 */
class SlotEscapePlanTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture core/src/test/resources/diagnostics/$name.txt")

    private fun r7(name: String) = StackParse.parse(fixture("am-stack-list-emulator-2026-10-09-r7-$name"))

    private val waze = "com.waze"
    private val slot = PxRect(19, 89, 1901, 985)
    private val homes = listOf("com.byd.launcher/com.byd.clusternav.launcher.KachiHome", "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity")
    private val codes = TaskBinderCodes.ANDROID_10_R47

    @Test
    fun `app vua thoat o nam tren dinh thi nhan va dua len tren`() {
        val d = SlotEscapePlan.checkAdopt(r7("waze-fullscreen-top"), waze, waze, slot, 240, homes)
        assertTrue(d is Adopt.Go, "$d")
        d as Adopt.Go
        assertEquals(301, d.task.taskId)
        assertTrue(d.toTop)
    }

    @Test
    fun `app nam duoi man nha Kachi thi van dua len tren vi man nha dang o dinh`() {
        val d = SlotEscapePlan.checkAdopt(r7("released-under-home"), waze, waze, slot, 240, homes) as Adopt.Go
        assertTrue(d.toTop, "màn nhà Kachi ở đỉnh ⇒ người dùng đang nhìn màn nhà ⇒ đưa vào khung ô phía trên")
    }

    @Test
    fun `app khac dang o dinh thi nhan nhung KHONG dua len tren`() {
        val d = SlotEscapePlan.checkAdopt(StackParse.parse(fixture("am-stack-list-emulator-2026-10-02-esc-after-move")), waze, waze, slot, 240, homes)
        assertTrue(d is Adopt.Go, "$d")
        assertFalse((d as Adopt.Go).toTop, "VietMap toàn màn đang ở trước người lái ⇒ không giành màn hình")
    }

    @Test
    fun `cac tien dieu kien that bai thi dung dung ly do`() {
        val e = r7("waze-fullscreen-top")
        assertEquals(Adopt.Stop(Why.NOT_SHOWN), SlotEscapePlan.checkAdopt(e, waze, "com.google.android.youtube", slot, 240, homes))
        assertEquals(Adopt.Stop(Why.NOT_SHOWN), SlotEscapePlan.checkAdopt(e, waze, null, slot, 240, homes))
        assertEquals(Adopt.Stop(Why.NO_READ), SlotEscapePlan.checkAdopt(emptyList(), waze, waze, slot, 240, homes))
        assertEquals(Adopt.Stop(Why.SMALL_RECT), SlotEscapePlan.checkAdopt(e, waze, waze, PxRect(0, 100, 300, 400), 240, homes))
        assertEquals(Adopt.Stop(Why.NO_TASK), SlotEscapePlan.checkAdopt(e, "com.not.here", "com.not.here", slot, 240, homes))
        assertEquals(Adopt.Stop(Why.BAD_PKG), SlotEscapePlan.checkAdopt(e, "com.x; rm", "com.x; rm", slot, 240, homes))
        // Kachi có 4 task trên display 0 (stack home + 3 stack standard) ⇒ không bao giờ tự nhận chính mình.
        val self = "com.byd.launcher"
        assertEquals(Adopt.Stop(Why.MULTI_TASK), SlotEscapePlan.checkAdopt(e, self, self, slot, 240, homes))
    }

    @Test
    fun `stack khong phai standard thi khong nhan`() {
        // Màn nhà: một task trong stack `home` khi chỉ còn MỘT task — dựng từ dòng thật của fixture.
        val e = r7("waze-fullscreen-top").filter { it.pkg != "com.byd.launcher" || it.taskId == 292 }
        val self = "com.byd.launcher"
        assertEquals(Adopt.Stop(Why.NOT_STANDARD), SlotEscapePlan.checkAdopt(e, self, self, slot, 240, homes))
    }

    @Test
    fun `lenh nhan dung chuoi da do tren xe va may ao`() {
        assertEquals(
            listOf("service call activity_task 89 i32 301 i32 5 i32 1", "am task resize 301 19 89 1901 985"),
            SlotEscapePlan.adoptCmds(codes, 301, toTop = true, rect = slot),
        )
        assertEquals("service call activity_task 89 i32 301 i32 5 i32 0", codes.modeCmd(301, TaskBinderCodes.MODE_FREEFORM, false))
        assertEquals("service call activity_task 59 i32 301", codes.boundsCmd(301))
    }

    @Test
    fun `doc lai khop khung TASK khong phai khung stack`() {
        val out = fixture("am-stack-list-emulator-2026-10-09-r7-waze-freeform-top")
        assertEquals(slot, SlotEscapePlan.taskBounds(out, 301))
        assertTrue(SlotEscapePlan.verify(out, 301, slot))
        assertFalse(SlotEscapePlan.verify(out, 301, PxRect(19, 89, 1900, 985)), "lệch 1 px ⇒ không khớp")
        assertFalse(SlotEscapePlan.verify(fixture("am-stack-list-emulator-2026-10-09-r7-waze-fullscreen-top"), 301, slot), "toàn màn ⇒ chưa nhận")
        assertFalse(SlotEscapePlan.verify(out, 999, slot))
        // Dòng tiêu đề stack freeform vẫn in [0,0][1920,1080] — dùng nó là kết luận sai.
        assertEquals(1920, StackParse.parse(out).first { it.taskId == 301 }.bounds!![2])
    }

    @Test
    fun `dua lai len qua rao camera va intent goc MAIN LAUNCHER`() {
        val cmd = SlotEscapePlan.refrontCmd("com.waze/com.waze.FreeMapAppActivity", "com.byd.avc/", homes)
        assertNotNull(cmd)
        cmd!!
        assertTrue(cmd.contains("am start --display 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.waze/com.waze.FreeMapAppActivity"), cmd)
        assertTrue(cmd.indexOf("com.byd.avc/") < cmd.indexOf("am start --display 0"), "nhánh camera đứng trước: $cmd")
        assertTrue(cmd.contains("\"${homes[0]} \""), "chỉ chạy khi màn nhà Kachi đang hiện: $cmd")
        assertFalse(Regex("--display\\s+[1-9]").containsMatchIn(cmd))
        assertNull(SlotEscapePlan.refrontCmd("com.foo/.Outer\$Inner", null, homes), "\$ trong case là biến ⇒ không lệnh")
        assertNull(SlotEscapePlan.refrontCmd("com.foo/.Main", null, emptyList()))
        assertNotNull(SlotEscapePlan.refrontCmd("com.foo/.Main", null, homes), "đời xe chưa biết dấu camera ⇒ chỉ cổng màn nhà")
    }

    @Test
    fun `tra ve toan man bang ma 89 mode 1 khong bao gio go stack`() {
        val homeFirst = SlotEscapePlan.releaseCmds(codes, 301, homeFirst = true, cameraSig = "com.byd.avc/", homeComps = homes)
        assertEquals(2, homeFirst.size)
        assertTrue(homeFirst[0].contains("am start -a android.intent.action.MAIN -c android.intent.category.HOME"), homeFirst[0])
        assertEquals("service call activity_task 89 i32 301 i32 1 i32 0", homeFirst[1])
        assertEquals(listOf("service call activity_task 89 i32 301 i32 1 i32 0"), SlotEscapePlan.releaseCmds(codes, 301, false, null, homes))
        (homeFirst).forEach { c ->
            assertFalse(c.contains("stack remove") || c.contains("force-stop") || c.contains("removeTask"), "lệnh trả không được giết app: $c")
        }
    }

    private fun m(task: Int = 301, slot: Int = 0) = EscapeMarker(waze, slot, task, this.slot)

    @Test
    fun `doi chieu - o con hien goi va task freeform thi giu`() {
        val top = SlotEscapePlan.reconcile(listOf(m()), r7("waze-freeform-top"), { waze }, { true }).single() as Step.Keep
        assertEquals(m(), top.marker)
        assertTrue(top.onTop)
        assertEquals("com.waze/com.waze.FreeMapAppActivity", top.task.comp)
        val under = SlotEscapePlan.reconcile(listOf(m()), r7("freeform-under-home"), { waze }, { true }).single() as Step.Keep
        assertFalse(under.onTop, "Home đã đẩy app xuống dưới ⇒ bên gọi đưa lại lên khi màn nhà lên trước")
    }

    @Test
    fun `doi chieu - task doi id sau khoi dong lai thi tim lai theo goi`() {
        val k = SlotEscapePlan.reconcile(listOf(m(task = 77)), r7("waze-freeform-top"), { waze }, { true }).single() as Step.Keep
        assertEquals(301, k.marker.task)
    }

    @Test
    fun `doi chieu - o khong con hien goi thi tra, Home truoc chi khi app dang noi tren man nha`() {
        val r1 = SlotEscapePlan.reconcile(listOf(m()), r7("waze-freeform-top"), { "com.google.android.youtube" }, { true }).single()
        assertTrue(r1 is Step.Release && r1.homeFirst, "$r1")
        val r2 = SlotEscapePlan.reconcile(listOf(m()), r7("freeform-under-home"), { null }, { true }).single()
        assertTrue(r2 is Step.Release && !r2.homeFirst, "$r2")
        // Đã toàn màn (vd đầu xe khởi động lại) mà ô không hiện ⇒ vẫn mode 1 (rẻ, idempotent, xoá freeform Android nhớ).
        val r3 = SlotEscapePlan.reconcile(listOf(m()), r7("released-under-home"), { null }, { true }).single()
        assertTrue(r3 is Step.Release && !r3.homeFirst, "$r3")
    }

    @Test
    fun `doi chieu - task mat, go cai, chua quyet duoc`() {
        val noWaze = r7("waze-freeform-top").filter { it.pkg != waze }
        assertEquals(Step.Gone(m()), SlotEscapePlan.reconcile(listOf(m()), noWaze, { waze }, { true }).single())
        assertEquals(Step.Dormant(m()), SlotEscapePlan.reconcile(listOf(m()), noWaze, { null }, { true }).single())
        assertEquals(Step.Forget(m()), SlotEscapePlan.reconcile(listOf(m()), noWaze, { waze }, { false }).single())
        assertEquals(Step.Wait(m()), SlotEscapePlan.reconcile(listOf(m()), r7("released-under-home"), { waze }, { true }).single(),
            "ô hiện gói mà task toàn màn ⇒ để đường ô bình thường lo, không lệnh")
        assertTrue(SlotEscapePlan.reconcile(listOf(m()), emptyList(), { waze }, { true }).isEmpty(), "đọc hỏng ⇒ không quyết")
    }

    @Test
    fun `host o khong mo lai app dang duoc quan`() {
        assertNotNull(SlotEscapePlan.claims(listOf(m()), r7("freeform-under-home"), 0, waze))
        assertNull(SlotEscapePlan.claims(listOf(m()), r7("freeform-under-home"), 1, waze), "dấu của ô khác")
        assertNull(SlotEscapePlan.claims(listOf(m()), r7("released-under-home"), 0, waze), "đã toàn màn ⇒ mở vào ô như thường")
        assertNull(SlotEscapePlan.claims(emptyList(), r7("freeform-under-home"), 0, waze))
    }
}
