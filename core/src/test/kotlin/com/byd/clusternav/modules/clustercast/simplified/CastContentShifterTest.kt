package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1 · R6 — đường thi hành "áp → đo → giữ/trả" ([CastContentShifter]) trên shell giả mà bản đọc cửa sổ là dump THẬT của máy ảo
 * A10 (chọn theo overscan vừa áp). Thử ĐỎ (đã làm, xem handoff stage-6): cho [CastContentShift.moved] luôn trả `true` ⇒ bài
 * "Maps không dời" đỏ (không trả overscan, kết cục APPLIED); bỏ lượt trả `configurator.applyOverscan(vd, from)` ⇒ đỏ ở lệnh cuối.
 */
class CastContentShifterTest {

    private fun fx(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/diagnostics/emu-overscan-neg-2026-10-13/$name.txt")).bufferedReader().readText()

    /** Shell giả: nhớ overscan TOP của display 1 theo lệnh `wm overscan`, trả dump cửa sổ [windowsAt] tương ứng. */
    private class Scripted(val windowsAt: (Int) -> String?) : SimpleCastShell {
        val history = mutableListOf<String>()
        var top = 0
        override fun execute(command: String): ShellResult {
            history += command
            Regex("""^wm overscan (-?\d+),(-?\d+),(-?\d+),(-?\d+) -d 1$""").find(command)?.let {
                top = it.groupValues[2].toInt()
                return ShellResult(0, "", "")
            }
            return when (command) {
                CastContentShift.DISPLAYS_CMD ->
                    ShellResult(0, "Display: mDisplayId=1\n    mDisplayInfoOverscan=[0,$top][0,0]\n", "")
                CastContentShift.WINDOWS_CMD -> ShellResult(0, windowsAt(top) ?: "", "")
                else -> ShellResult(0, "", "")
            }
        }
        val overscans get() = history.filter { it.startsWith("wm overscan") }
    }

    private fun shifter(shell: Scripted) = CastContentShifter(shell, DisplayConfigurator(shell), {}, {})

    @Test
    fun `Chrome doi theo overscan am - giu, khong tra`() {
        val shell = Scripted { t -> mapOf(0 to "chrome-full-os0", -40 to "chrome-full-os40", -120 to "chrome-full-os120")[t]?.let(::fx) }
        val out = shifter(shell).shift(1, "com.android.chrome", "0,0,0,0", -40)
        assertEquals(CastShiftResult.APPLIED, out.result)
        assertEquals("0,-40,0,0", out.overscan)
        assertEquals(listOf("wm overscan 0,-40,0,0 -d 1"), shell.overscans)
    }

    @Test
    fun `Maps khong doi - tra overscan cu ngay, phan khong nhan`() {
        val shell = Scripted { t -> mapOf(0 to "maps-full-os0", -40 to "maps-full-os40")[t]?.let(::fx) }
        val out = shifter(shell).shift(1, "com.google.android.apps.maps", "0,0,0,0", -40)
        assertEquals(CastShiftResult.INEFFECTIVE, out.result)
        assertEquals(listOf("wm overscan 0,-40,0,0 -d 1", "wm overscan 0,0,0,0 -d 1"), shell.overscans)
        assertEquals(0, shell.top, "cụm phải về đúng giá trị trước khi áp")
    }

    @Test
    fun `khong thay cua so app - khong ap gi`() {
        val shell = Scripted { null }
        val out = shifter(shell).shift(1, "com.waze", "0,0,0,0", -20)
        assertEquals(CastShiftResult.FAILED, out.result)
        assertTrue(shell.overscans.isEmpty(), "${shell.history}")
    }

    @Test
    fun `cum da o dung gia tri - khong lenh nao (su that thang ban ghim)`() {
        val shell = Scripted { fx("chrome-full-os40") }.also { it.top = -40 }
        val out = shifter(shell).shift(1, "com.android.chrome", "0,0,0,0", -40)
        assertEquals(CastShiftResult.UNCHANGED, out.result)
        assertTrue(shell.overscans.isEmpty())
    }

    @Test
    fun `display chua xac minh - tang thi hanh tu choi`() {
        val shell = Scripted { null }
        assertEquals(false, DisplayConfigurator(shell).applyOverscan(0, "0,-20,0,0"))
        assertEquals(false, DisplayConfigurator(shell).applyOverscan(1, "0,-20,0,0; reboot"))
        assertTrue(shell.history.isEmpty())
    }
}
