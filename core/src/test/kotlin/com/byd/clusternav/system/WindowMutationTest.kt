package com.byd.clusternav.system

import com.byd.clusternav.launcher.FreeformLaunch
import com.byd.clusternav.launcher.SlotRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [WindowMutation] — mỗi biến thể CÓ KIỂU render() ra ĐÚNG chuỗi golden ([FreeformLaunch], byte-locked bởi
 * `LauncherCommandGoldenTest`) và [MutationPriority] có thứ tự rút đúng. Thuần JVM.
 */
class WindowMutationTest {

    // ─────────── render() khớp BYTE chuỗi golden của FreeformLaunch ───────────

    @Test
    fun `LaunchOnDisplay with launcher category renders the golden VdAppHost string`() {
        val m = WindowMutation.LaunchOnDisplay("com.foo/.Main", displayId = 7, windowingMode = 1)
        assertEquals(
            "am start --display 7 --windowingMode 1 -a android.intent.action.MAIN" +
                " -c android.intent.category.LAUNCHER -n 'com.foo/.Main'",
            m.render(),
        )
        assertEquals(7, m.targetDisplayId)
        assertEquals(MutationPriority.NORMAL, m.priority)
    }

    @Test
    fun `LaunchOnDisplay without launcher category renders the golden SlotAppHost string`() {
        val m = WindowMutation.LaunchOnDisplay("com.foo/.Main", 7, 1, withLauncherCategory = false)
        assertEquals("am start --display 7 --windowingMode 1 -n 'com.foo/.Main'", m.render())
    }

    @Test
    fun `ResizeTask renders the golden resize string and carries the caller-supplied display`() {
        val m = WindowMutation.ResizeTask(42, 0, 90, 1920, 630, targetDisplayId = 0)
        assertEquals("am task resize 42 0 90 1920 630", m.render())
        assertEquals(0, m.targetDisplayId)

        val m2 = WindowMutation.ResizeTask(7, 10, 20, 300, 400, targetDisplayId = 5)
        assertEquals("am task resize 7 10 20 300 400", m2.render())
        assertEquals(5, m2.targetDisplayId)
    }

    @Test
    fun `Fullscreen renders the golden fullscreen-return string on the main display`() {
        val m = WindowMutation.Fullscreen("com.foo/.Main")
        assertEquals(
            "am start --display 0 --windowingMode 1 -f 0x20000000" +
                " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n 'com.foo/.Main'",
            m.render(),
        )
        assertEquals(0, m.targetDisplayId)
    }

    @Test
    fun `ForceStop renders am force-stop and targets NO_DISPLAY`() {
        val m = WindowMutation.ForceStop("com.foo")
        assertEquals("am force-stop com.foo", m.render())
        assertEquals(WindowMutation.NO_DISPLAY, m.targetDisplayId)
        assertEquals(-1, WindowMutation.NO_DISPLAY)
    }

    @Test
    fun `Raw returns its command verbatim and carries the supplied display + priority`() {
        val m = WindowMutation.Raw(
            "am stack move-task 5 1 true",
            targetDisplayId = 1,
            priority = MutationPriority.STOP,
        )
        assertEquals("am stack move-task 5 1 true", m.render())
        assertEquals(1, m.targetDisplayId)
        assertEquals(MutationPriority.STOP, m.priority)
    }

    /** render() của biến thể có kiểu PHẢI bằng đúng call FreeformLaunch (tái dùng, không tự bịa chuỗi). */
    @Test
    fun `typed render() equals the FreeformLaunch builder output (reuse, not re-invent)`() {
        assertEquals(
            FreeformLaunch.launchOnDisplayCmd("p/.A", 7, 5, true),
            WindowMutation.LaunchOnDisplay("p/.A", 7, 5, true).render(),
        )
        assertEquals(
            FreeformLaunch.launchOnDisplayCmd("p/.A", 3, 1, false),
            WindowMutation.LaunchOnDisplay("p/.A", 3, 1, withLauncherCategory = false).render(),
        )
        assertEquals(
            FreeformLaunch.fullscreenCmd("p/.A", 0),
            WindowMutation.Fullscreen("p/.A", 0).render(),
        )
        assertEquals(
            FreeformLaunch.resizeCmd(9, SlotRect(0, 10, 20, 300, 400)),
            WindowMutation.ResizeTask(9, 10, 20, 300, 400, targetDisplayId = 0).render(),
        )
        assertEquals(
            FreeformLaunch.forceStopCmd("p"),
            WindowMutation.ForceStop("p").render(),
        )
    }

    // ─────────── ưu tiên (drain order) ───────────

    @Test
    fun `priority drain rank is STOP then RESCUE then NORMAL`() {
        assertTrue(MutationPriority.STOP.drainRank < MutationPriority.RESCUE.drainRank)
        assertTrue(MutationPriority.RESCUE.drainRank < MutationPriority.NORMAL.drainRank)
        assertTrue(MutationPriority.STOP.isHighPriority)
        assertTrue(MutationPriority.RESCUE.isHighPriority)
        assertFalse(MutationPriority.NORMAL.isHighPriority)
    }

    @Test
    fun `DRAIN_ORDER comparator sorts STOP RESCUE NORMAL`() {
        val sorted = listOf(MutationPriority.NORMAL, MutationPriority.STOP, MutationPriority.RESCUE)
            .sortedWith(MutationPriority.DRAIN_ORDER)
        assertEquals(
            listOf(MutationPriority.STOP, MutationPriority.RESCUE, MutationPriority.NORMAL),
            sorted,
        )
    }

    @Test
    fun `sorting mutations by priority drains STOP and RESCUE before NORMAL (stable within a level)`() {
        val mutations = listOf(
            WindowMutation.Raw("normal", 0, MutationPriority.NORMAL),
            WindowMutation.Raw("stop", 1, MutationPriority.STOP),
            WindowMutation.Raw("normal2", 0, MutationPriority.NORMAL),
            WindowMutation.Raw("rescue", 1, MutationPriority.RESCUE),
        )
        val drained = mutations.sortedWith(compareBy { it.priority.drainRank }).map { it.render() }
        assertEquals(listOf("stop", "rescue", "normal", "normal2"), drained)
    }
}
