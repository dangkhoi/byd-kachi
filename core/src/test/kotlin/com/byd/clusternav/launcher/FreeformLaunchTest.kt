package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FreeformLaunchTest {

    @Test
    fun `launch command uses freeform windowing mode 5 on the main display`() {
        val c = FreeformLaunch.launchCmd("com.foo/.Main")
        assertTrue(c.contains("--windowingMode 5"), c)
        assertTrue(c.contains("--display 0"), c)
        assertTrue(c.contains("-n 'com.foo/.Main'"), c)
        assertTrue(c.contains("category.LAUNCHER"), c)
    }

    @Test
    fun `resize command emits left top right bottom from the slot rect`() {
        val r = SlotRect(0, 10, 20, 300, 400)
        assertEquals("am task resize 7 10 20 300 400", FreeformLaunch.resizeCmd(7, r))
    }

    @Test
    fun `parse task id finds the package task and misses unknown packages`() {
        val out = "  taskId=42: com.foo/.Main bounds=[0,0][100,100]\n  taskId=9: com.bar/.X"
        assertEquals(42, FreeformLaunch.parseTaskId(out, "com.foo"))
        assertNull(FreeformLaunch.parseTaskId(out, "com.baz"))
    }

    @Test
    fun `parse task id on display picks the task on the requested display only`() {
        val out = """
            Stack id=1 bounds=[0,0][1920,720] displayId=1 userId=0
              taskId=88: com.foo/.Main bounds=[0,0][1920,720]
            Stack id=2 bounds=[0,0][1920,720] displayId=0 userId=0
              taskId=42: com.foo/.Main bounds=[0,0][1920,720]
        """.trimIndent()
        // Same package has a task on BOTH displays — must pick the one on the asked display.
        assertEquals(42, FreeformLaunch.parseTaskIdOnDisplay(out, "com.foo", 0))
        assertEquals(88, FreeformLaunch.parseTaskIdOnDisplay(out, "com.foo", 1))
        assertNull(FreeformLaunch.parseTaskIdOnDisplay(out, "com.foo", 2))
        assertNull(FreeformLaunch.parseTaskIdOnDisplay(out, "com.other", 0))
    }

    @Test
    fun `parse component takes the last slashed token`() {
        val out = "Resolving Intent...\ncom.foo/.MainActivity"
        assertEquals("com.foo/.MainActivity", FreeformLaunch.parseComponent(out))
        assertNull(FreeformLaunch.parseComponent("no component here"))
    }

    @Test
    fun `fullscreen return uses windowing mode 1 and the single-top flag`() {
        val c = FreeformLaunch.fullscreenCmd("com.foo/.Main")
        assertTrue(c.contains("--windowingMode 1"), c)
        assertTrue(c.contains("0x20000000"), c)
    }

    @Test
    fun `freeform flags set both global settings`() {
        assertTrue(FreeformLaunch.freeformFlagCmds.any { it.contains("enable_freeform_support") })
        assertTrue(FreeformLaunch.freeformFlagCmds.any { it.contains("force_resizable_activities") })
    }
}
