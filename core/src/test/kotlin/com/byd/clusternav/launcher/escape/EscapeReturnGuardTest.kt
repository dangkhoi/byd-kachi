package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.escape.EscapeReturnGuard.ACTIVITY_TYPE_STANDARD
import com.byd.clusternav.launcher.escape.EscapeReturnGuard.After
import com.byd.clusternav.launcher.escape.EscapeReturnGuard.Decision
import com.byd.clusternav.launcher.escape.EscapeReturnGuard.Escape
import com.byd.clusternav.launcher.escape.EscapeReturnGuard.StackFact
import com.byd.clusternav.launcher.escape.EscapeReturnGuard.WINDOWING_MODE_FULLSCREEN
import com.byd.clusternav.launcher.escape.EscapeReturnGuard.Why
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 2.98 · R18 — luật dời stack app thoát ô về màn ảo ô. Ca dương dựng từ log NGUYÊN VĂN của nguyên mẫu trên máy ảo
 * (`escape-return-prototype-log-emulator-2026-10-09.txt`: `Failed to put TaskRecord{… #task A=pkg … StackId=s …} on display vd`
 * — stack s là stack MỚI một task ở display 0, đúng thứ nguyên mẫu đã dời 8/8 app thành công). Mỗi ca âm khoá một câu §4.
 */
class EscapeReturnGuardTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/diagnostics/$name.txt")?.bufferedReader()?.readText()
            ?: error("thiếu fixture core/src/test/resources/diagnostics/$name.txt")

    private val failedRe = Regex("""Failed to put TaskRecord\{\S+ #(\d+) A=(\S+) U=0 StackId=(\d+) sz=\d+\} on display (\d+)""")

    /** (task, gói, stack ở display 0, màn ô) cho mỗi dòng `Failed to put` của log thật. */
    private fun events() = failedRe.findAll(fixture("escape-return-prototype-log-emulator-2026-10-09")).map {
        val (task, pkg, stack, vd) = it.destructured
        listOf(task, pkg, stack, vd)
    }.toList()

    private fun escapedStack(stack: Int, task: Int, display: Int = 0, mode: Int = WINDOWING_MODE_FULLSCREEN, type: Int = ACTIVITY_TYPE_STANDARD) =
        StackFact(stack, display, mode, type, listOf(task))

    private val home = StackFact(0, 0, WINDOWING_MODE_FULLSCREEN, 2, listOf(416))
    private val breaker = EscapeReturnBreaker()
    private val api = EscapeReturnApi.ANDROID_10_R47

    @Test
    fun `log that - 7 lan thoat cua 6 app deu duoc doi ve dung man ao o`() {
        val evs = events()
        assertEquals(7, evs.size, "fixture: Gmail · Messages · Drive · Meet ×2 · Waze · VietMap")
        for ((task, pkg, stack, vd) in evs) {
            val e = Escape(task.toInt(), pkg, vd.toInt())
            val cfg = EscapeReturnConfig(api, mapOf(vd.toInt() to pkg))
            assertNull(EscapeReturnGuard.precheck(e, cfg, breaker, 0L), "$pkg")
            val d = EscapeReturnGuard.decide(e, listOf(home, escapedStack(stack.toInt(), task.toInt())), WINDOWING_MODE_FULLSCREEN)
            assertEquals(Decision.Move(stack.toInt(), vd.toInt()), d, "$pkg")
        }
    }

    @Test
    fun `cau 1 - man khong phai man ao o Kachi bao thi khong cham`() {
        val e = Escape(432, "com.waze", 13)
        assertEquals(Why.NOT_SLOT_VD, EscapeReturnGuard.precheck(e, EscapeReturnConfig(api, mapOf(12 to "com.waze")), breaker, 0L))
        assertEquals(Why.NOT_SLOT_VD, EscapeReturnGuard.precheck(e, EscapeReturnConfig(api, emptyMap()), breaker, 0L))
    }

    @Test
    fun `cau 2 - goi khac goi o (Gmail soan thu NEW_TASK tu Waze) khong cham`() {
        val cfg = EscapeReturnConfig(api, mapOf(13 to "com.waze"))
        assertEquals(Why.NOT_SLOT_PKG, EscapeReturnGuard.precheck(Escape(440, "com.google.android.gm", 13), cfg, breaker, 0L))
        assertEquals(Why.NOT_SLOT_PKG, EscapeReturnGuard.precheck(Escape(440, null, 13), cfg, breaker, 0L))
    }

    @Test
    fun `bang tat (DL5, ngat ben) thi khong cham`() {
        assertEquals(Why.DISABLED, EscapeReturnGuard.precheck(Escape(432, "com.waze", 13), EscapeReturnConfig(null, mapOf(13 to "com.waze")), breaker, 0L))
    }

    @Test
    fun `cau 3 - chi stack standard fullscreen mot task o display 0, man dich fullscreen`() {
        val e = Escape(432, "com.waze", 13)
        fun d(vararg s: StackFact, target: Int = WINDOWING_MODE_FULLSCREEN) = EscapeReturnGuard.decide(e, listOf(home, *s), target)
        assertEquals(Decision.Skip(Why.NO_STACK), d())
        assertEquals(Decision.Skip(Why.NOT_MAIN_DISPLAY), d(escapedStack(38, 432, display = 13)), "đã ở ô / màn khác ⇒ không dời")
        assertEquals(Decision.Skip(Why.NOT_FULLSCREEN), d(escapedStack(38, 432, mode = 5)), "freeform ⇒ đường NPE 08-01")
        assertEquals(Decision.Skip(Why.NOT_FULLSCREEN), d(escapedStack(38, 432, mode = 2)), "pinned")
        assertEquals(Decision.Skip(Why.NOT_STANDARD), d(escapedStack(38, 432, type = 2)), "home")
        assertEquals(Decision.Skip(Why.NOT_STANDARD), d(escapedStack(38, 432, type = 3)), "recents")
        assertEquals(Decision.Skip(Why.MULTI_TASK), d(StackFact(38, 0, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, listOf(432, 77))))
        assertEquals(Decision.Skip(Why.TARGET_NOT_FULLSCREEN), d(escapedStack(38, 432), target = 5), "màn ô freeform (desktop mode) ⇒ đổi chế độ")
        assertEquals(Decision.Skip(Why.TARGET_NOT_FULLSCREEN), d(escapedStack(38, 432), target = 0), "màn không tồn tại (UNDEFINED)")
        assertEquals(Decision.Move(38, 13), d(escapedStack(38, 432)))
    }

    @Test
    fun `cau chi chan truoc moi loi goi he`() {
        val b = EscapeReturnBreaker()
        b.onMechanismError("x")
        assertEquals(Why.TRIPPED_ALL, EscapeReturnGuard.precheck(Escape(432, "com.waze", 13), EscapeReturnConfig(api, mapOf(13 to "com.waze")), b, 0L))
    }

    @Test
    fun `doc lai sau lenh - o, chua toi, mat`() {
        assertEquals(After.IN_SLOT, EscapeReturnGuard.verify(432, 13, listOf(home, escapedStack(38, 432, display = 13))))
        assertEquals(After.ELSEWHERE, EscapeReturnGuard.verify(432, 13, listOf(home, escapedStack(38, 432))))
        assertEquals(After.LOST, EscapeReturnGuard.verify(432, 13, listOf(home)), "hậu quả vụ NPE 08-01: task biến mất")
        assertEquals(After.ELSEWHERE, EscapeReturnGuard.verify(432, 13, emptyList()), "đọc hỏng không phải bằng chứng mất")
    }
}
