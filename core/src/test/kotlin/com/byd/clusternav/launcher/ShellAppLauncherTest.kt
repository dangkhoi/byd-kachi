package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Kiểm bộ adapter freeform ON-CAR [ShellAppLauncher] hoàn toàn off-car: cắm một `sh` giả trả output
 * `am stack list` / resolve / resize tuỳ lệnh, khẳng định openInSlot phát ĐÚNG chuỗi lệnh proven và
 * chọn ĐÚNG task theo display. Đặt cửa sổ thật vẫn verify trên xe — đây khoá phần logic thuần.
 */
class ShellAppLauncherTest {

    private val slot = SlotRect(0, 0, 90, 1920, 630)

    private fun fakeShell(
        stack: String,
        resizeOut: String = "",
        component: String = "com.foo/.Main",
        calls: MutableList<String> = mutableListOf(),
    ): (String) -> String = { cmd ->
        calls += cmd
        when {
            cmd.startsWith("cmd package resolve-activity") -> "priority=0\n$component"
            cmd == "am stack list" -> stack
            cmd.startsWith("am task resize") -> resizeOut
            else -> ""
        }
    }

    @Test
    fun `openInSlot resizes the display-0 task, not a same-package task on the cluster`() {
        val stack = """
            Stack id=1 bounds=[0,0][1920,720] displayId=1 userId=0
              taskId=88: com.foo/.Main bounds=[0,0][1920,720]
            Stack id=2 bounds=[0,0][1920,720] displayId=0 userId=0
              taskId=42: com.foo/.Main bounds=[0,0][1920,720]
        """.trimIndent()
        val calls = mutableListOf<String>()
        val launcher = ShellAppLauncher(fakeShell(stack, calls = calls), sleep = {})

        assertTrue(launcher.openInSlot("com.foo", slot))
        // Task on the MAIN display (42) must be the one resized — never the cluster task (88).
        assertTrue(
            calls.contains("am task resize 42 0 90 1920 630"),
            "expected resize of task 42; resize calls = ${calls.filter { it.startsWith("am task resize") }}",
        )
        assertFalse(calls.any { it.startsWith("am task resize 88") })
    }

    @Test
    fun `openInSlot only queries the stack once`() {
        val stack = "Stack id=2 bounds=[0,0][1920,720] displayId=0 userId=0\n  taskId=42: com.foo/.Main"
        val calls = mutableListOf<String>()
        ShellAppLauncher(fakeShell(stack, calls = calls), sleep = {}).openInSlot("com.foo", slot)
        assertEquals(1, calls.count { it == "am stack list" })
    }

    @Test
    fun `openInSlot returns false when the app never lands`() {
        val stack = "Stack id=2 bounds=[0,0][1920,720] displayId=0 userId=0\n  taskId=42: com.other/.X"
        assertFalse(ShellAppLauncher(fakeShell(stack), sleep = {}).openInSlot("com.foo", slot))
    }

    @Test
    fun `openInSlot returns false when the resize is rejected`() {
        val stack = "Stack id=2 bounds=[0,0][1920,720] displayId=0 userId=0\n  taskId=42: com.foo/.Main"
        val launcher = ShellAppLauncher(fakeShell(stack, resizeOut = "Error: not allowed"), sleep = {})
        assertFalse(launcher.openInSlot("com.foo", slot))
    }

    @Test
    fun `openInSlot returns false when the component cannot be resolved`() {
        val sh: (String) -> String = { cmd -> if (cmd.startsWith("cmd package")) "no component here" else "" }
        assertFalse(ShellAppLauncher(sh, sleep = {}).openInSlot("com.foo", slot))
    }

    @Test
    fun `isFreeformAvailable reads the global flag`() {
        assertTrue(ShellAppLauncher({ "1" }, sleep = {}).isFreeformAvailable())
        assertFalse(ShellAppLauncher({ "0" }, sleep = {}).isFreeformAvailable())
        assertFalse(ShellAppLauncher({ "" }, sleep = {}).isFreeformAvailable())
    }

    @Test
    fun `closeSlot sends the fullscreen return recipe for the resolved component`() {
        val calls = mutableListOf<String>()
        ShellAppLauncher(fakeShell("", calls = calls), sleep = {}).closeSlot("com.foo")
        assertTrue(calls.any { it.contains("--windowingMode 1") && it.contains("0x20000000") && it.contains("com.foo/.Main") })
    }
}
