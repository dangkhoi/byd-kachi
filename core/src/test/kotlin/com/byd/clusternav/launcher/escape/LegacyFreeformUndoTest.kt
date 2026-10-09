package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.escape.LegacyFreeformUndo.Marker
import com.byd.clusternav.launcher.escape.LegacyFreeformUndo.Step
import com.byd.clusternav.modules.clustercast.StackParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R18 — đường TRẢ cho máy đã chạy bản R7 (CLAUDE.md §5). Bản đọc NGUYÊN VĂN máy ảo 09/10 lúc R7 đang quản Waze dạng
 * freeform (`am-stack-list-emulator-2026-10-09-r7-waze-freeform-top` · `…-r7-freeform-under-home`), dấu NGUYÊN VĂN định dạng R7
 * (`pkg|ô|task|l,t,r,b`). Khoá: chỉ task freeform của gói có dấu ở display 0 được trả (mã 89 mode 1 toTop 0); gói đã gỡ ⇒ xoá dấu;
 * chưa có task ⇒ giữ dấu, 0 lệnh; dấu lạ bỏ; đọc hỏng không quyết gì.
 */
class LegacyFreeformUndoTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture core/src/test/resources/diagnostics/$name.txt")

    private val r7Raw = "com.waze|1|301|19,89,1901,985;vn.vietmap.live|0|288|19,89,941,985"

    @Test
    fun `dau R7 nguyen van doc duoc, muc la bo`() {
        val m = LegacyFreeformUndo.decode("$r7Raw;;rm -rf /|0|1|0,0,1,1;com.waze|2|999|0,0,1,1")
        assertEquals(listOf("com.waze", "vn.vietmap.live"), m.map { it.pkg })
        assertEquals(emptyList<Marker>(), LegacyFreeformUndo.decode(null))
        assertEquals(r7Raw, LegacyFreeformUndo.encode(LegacyFreeformUndo.decode(r7Raw)), "dấu giữ lại ghi nguyên văn")
    }

    @Test
    fun `Waze freeform tren va duoi man nha deu duoc tra, VietMap chua co task thi giu dau`() {
        for (f in listOf("am-stack-list-emulator-2026-10-09-r7-waze-freeform-top", "am-stack-list-emulator-2026-10-09-r7-freeform-under-home")) {
            val steps = LegacyFreeformUndo.plan(LegacyFreeformUndo.decode(r7Raw), StackParse.parse(fixture(f))) { true }
            assertEquals(listOf(301), (steps[0] as Step.Undo).tasks, f)
            assertTrue(steps[1] is Step.Keep, "$f: VietMap không có task freeform ⇒ giữ dấu, 0 lệnh")
        }
    }

    @Test
    fun `goi da go thi xoa dau, doc hong thi khong quyet`() {
        val list = StackParse.parse(fixture("am-stack-list-emulator-2026-10-09-r7-waze-freeform-top"))
        val steps = LegacyFreeformUndo.plan(LegacyFreeformUndo.decode(r7Raw), list) { it == "com.waze" }
        assertTrue(steps[1] is Step.Forget)
        assertTrue(LegacyFreeformUndo.plan(LegacyFreeformUndo.decode(r7Raw), emptyList()) { false }.all { it is Step.Keep })
    }

    @Test
    fun `chi task freeform o display 0 cua dung goi - YouTube trong o va man nha khong cham`() {
        val list = StackParse.parse(fixture("am-stack-list-emulator-2026-10-09-r7-waze-freeform-top"))
        val steps = LegacyFreeformUndo.plan(LegacyFreeformUndo.decode("com.google.android.youtube|0|294|0,0,1,1;com.byd.launcher|0|292|0,0,1,1"), list) { true }
        assertTrue(steps.all { it is Step.Keep }, "YouTube ở màn ảo (fullscreen), Kachi home ⇒ 0 lệnh")
    }

    @Test
    fun `lenh tra la ma 89 mode 1 toTop 0 va doc ket qua void`() {
        val c = TaskBinderCodes.ANDROID_10_R47
        assertEquals("service call activity_task 89 i32 301 i32 1 i32 0", c.modeCmd(301, TaskBinderCodes.MODE_FULLSCREEN, toTop = false))
        assertTrue(TaskBinderCodes.parseOk(fixture("parcel-emulator-2026-10-09-r7-mode-void")))
        assertFalse(TaskBinderCodes.parseOk("Result: Parcel(ffffffb5 0000003c 00740041 '....<...A.t.')"), "ngoại lệ ⇒ không OK")
        assertFalse(TaskBinderCodes.parseOk(""))
        assertTrue(c.usableOn(29)); assertFalse(c.usableOn(31))
    }
}
