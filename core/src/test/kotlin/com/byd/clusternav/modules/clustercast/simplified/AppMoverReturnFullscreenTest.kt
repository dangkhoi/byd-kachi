package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R6 (#1, docs/specs/cast-nav-ux-release-v104.html): returning a NORMAL app from the cluster must
 * reset its windowing-mode to FULLSCREEN so it never lingers as a skewed freeform WINDOW on the
 * main display (owner 2026-08: "cast VietMap back and forth → stuck as a window, even 'return to
 * main' stays windowed").
 *
 * These pin the field-proven return recipe and the self-heal re-issue on the shell boundary
 * (AppMover talks only through [SimpleCastShell], so a recording fake proves the exact commands).
 */
class AppMoverReturnFullscreenTest {

    /** Records every command; scripts `am stack list` + `wm size` so the freeform-window probe can run. */
    private class RecordingShell(
        private val stackList: String = "",
        private val displaySize: String = "Physical size: 1920x720",
    ) : SimpleCastShell {
        val history = mutableListOf<String>()
        override fun execute(command: String): ShellResult {
            history.add(command)
            return when {
                command.startsWith("wm size -d") -> ShellResult(0, displaySize, "")
                command == "am stack list" -> ShellResult(0, stackList, "")
                else -> ShellResult(0, "", "")
            }
        }
    }

    @Test
    fun `fullscreenReturnCommand is the proven fullscreen recipe`() {
        val cmd = AppMover.fullscreenReturnCommand("vn.vietmap.live/.MainActivity")
        // windowing-mode fullscreen (1) on the HOME display, LAUNCHER intent + SINGLE_TOP so the
        // EXISTING task is reparented to a fullscreen stack instead of spawning a duplicate.
        assertTrue(cmd.contains("--display 0"), cmd)
        assertTrue(cmd.contains("--windowingMode 1"), cmd)
        assertTrue(cmd.contains("-f 0x20000000"), cmd) // FLAG_ACTIVITY_SINGLE_TOP
        assertTrue(cmd.contains("-a android.intent.action.MAIN"), cmd)
        assertTrue(cmd.contains("-c android.intent.category.LAUNCHER"), cmd)
        assertTrue(cmd.contains("-n 'vn.vietmap.live/.MainActivity'"), cmd)
    }

    @Test
    fun `NORMAL return issues the fullscreen reset for the target`() {
        val shell = RecordingShell(stackList = "") // empty → freeform probe finds nothing → no retry
        val mover = AppMover(shell, sleepMs = {})
        val ok = mover.returnToMain(
            pkg = "vn.vietmap.live", activity = "vn.vietmap.live/.MainActivity", appType = AppType.NORMAL,
        )
        assertTrue(ok)
        val resets = shell.history.filter { it == AppMover.fullscreenReturnCommand("vn.vietmap.live/.MainActivity") }
        assertEquals(1, resets.size, "exactly one fullscreen reset when nothing is stuck; history=${shell.history}")
    }

    @Test
    fun `NORMAL return re-issues once when a freeform window still lingers on display 0`() {
        // A freeform/floating window for the app on display 0 (stack bounds smaller than 1920x720).
        val stuck = buildString {
            appendLine("Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0")
            appendLine("  taskId=1: com.android.launcher3/.Launcher visible=true")
            appendLine("Stack id=5 bounds=[120,90][900,630] displayId=0 userId=0")
            appendLine("  taskId=42: vn.vietmap.live/.MainActivity visible=true")
        }
        val shell = RecordingShell(stackList = stuck)
        val mover = AppMover(shell, sleepMs = {})
        mover.returnToMain(
            pkg = "vn.vietmap.live", activity = "vn.vietmap.live/.MainActivity", appType = AppType.NORMAL,
        )
        val resets = shell.history.count { it == AppMover.fullscreenReturnCommand("vn.vietmap.live/.MainActivity") }
        assertEquals(2, resets, "freeform window still on display 0 → reset re-issued once; history=${shell.history}")
    }

    @Test
    fun `NORMAL return does not re-issue when app is already fullscreen on display 0`() {
        val fullscreen = buildString {
            appendLine("Stack id=5 bounds=[0,0][1920,720] displayId=0 userId=0")
            appendLine("  taskId=42: vn.vietmap.live/.MainActivity visible=true")
        }
        val shell = RecordingShell(stackList = fullscreen)
        val mover = AppMover(shell, sleepMs = {})
        mover.returnToMain(
            pkg = "vn.vietmap.live", activity = "vn.vietmap.live/.MainActivity", appType = AppType.NORMAL,
        )
        val resets = shell.history.count { it == AppMover.fullscreenReturnCommand("vn.vietmap.live/.MainActivity") }
        assertEquals(1, resets, "already fullscreen → no re-issue; history=${shell.history}")
    }

    @Test
    fun `CP return path is untouched (move-task, not the freeform reset)`() {
        // CP/AA must never use the freeform fullscreen recipe — they return via `am stack move-task`.
        val stackList = buildString {
            appendLine("Stack id=2 bounds=[0,0][1422,800] displayId=1 userId=0")
            appendLine("  taskId=10: com.byd.autolink.carplay/.MainActivity visible=true")
            appendLine("Stack id=3 bounds=[0,0][1920,720] displayId=0 userId=0")
            appendLine("  taskId=1: com.android.settings/.Settings visible=true")
        }
        val shell = RecordingShell(stackList = stackList)
        val mover = AppMover(shell, sleepMs = {})
        mover.returnToMain(
            pkg = "com.byd.autolink.carplay", activity = null, appType = AppType.CARPLAY, taskId = 10,
        )
        assertTrue(shell.history.any { it.startsWith("am stack move-task 10") }, shell.history.toString())
        assertFalse(
            shell.history.any { it.contains("--windowingMode 1") },
            "CP/AA return must not use the NORMAL fullscreen recipe; history=${shell.history}",
        )
    }
}
