package com.byd.clusternav.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [WindowCommandDispatcher] — cổng ownership của nhánh launcher: launcher→cụm(1) BỊ CHẶN + KHÔNG dispatch;
 * launcher→display0 / VD-đã-đăng-ký ĐƯỢC dispatch + cập nhật [AppLocationRegistry]; [WindowCommandDispatcher
 * .launcherSeam] suy `--display N` và chặn rò display ≥ 1. Thuần JVM (transport = lambda ghi lại lời gọi).
 */
class WindowCommandDispatcherTest {

    /** Ghi lại từng (lệnh, priority) đến transport; trả "OUT:<cmd>" để phân biệt output. */
    private class RecordingTransport {
        val calls = mutableListOf<Pair<String, MutationPriority>>()
        val run: (String, MutationPriority) -> String = { cmd, priority -> calls += cmd to priority; "OUT:$cmd" }
    }

    private fun launcherLaunch(displayId: Int) =
        WindowMutation.LaunchOnDisplay("com.foo/.Main", displayId, windowingMode = 1)

    // ─────────────────── ownership guard ───────────────────

    @Test
    fun `launcher targeting the cluster display 1 is REJECTED and never dispatched`() {
        val t = RecordingTransport()
        val d = WindowCommandDispatcher(runCommand = t.run)
        val r = d.dispatch(launcherLaunch(1), DisplayOwner.LAUNCHER)
        assertTrue(r is DispatchResult.Rejected, "launcher → cluster must be rejected")
        assertTrue(t.calls.isEmpty(), "a rejected command must NOT reach the transport")
    }

    @Test
    fun `launcher targeting main display 0 is dispatched with the mutation priority and updates the registry`() {
        val t = RecordingTransport()
        val d = WindowCommandDispatcher(runCommand = t.run)
        val m = WindowMutation.LaunchOnDisplay("com.foo/.Main", displayId = 0, windowingMode = 5)
        val r = d.dispatch(m, DisplayOwner.LAUNCHER)
        assertTrue(r is DispatchResult.Dispatched)
        assertEquals("OUT:${m.render()}", (r as DispatchResult.Dispatched).output)
        assertEquals(listOf(m.render() to MutationPriority.NORMAL), t.calls)
        assertEquals(0, d.locations.locationOf("com.foo")?.displayId, "placed app tracked on display 0")
    }

    @Test
    fun `launcher targeting its registered virtual display is ALLOWED`() {
        val t = RecordingTransport()
        val d = WindowCommandDispatcher(runCommand = t.run)
        d.registerLauncherVirtualDisplay(7)
        assertTrue(d.dispatch(launcherLaunch(7), DisplayOwner.LAUNCHER) is DispatchResult.Dispatched)
        assertEquals(1, t.calls.size)
        // and once unregistered, the same op is rejected again (fail-safe deny for unowned displays)
        d.unregisterLauncherVirtualDisplay(7)
        assertTrue(d.dispatch(launcherLaunch(7), DisplayOwner.LAUNCHER) is DispatchResult.Rejected)
        assertEquals(1, t.calls.size, "the unregistered-VD op must not reach the transport")
    }

    @Test
    fun `Fullscreen and ForceStop remove the app from the location registry`() {
        val t = RecordingTransport()
        val d = WindowCommandDispatcher(runCommand = t.run)
        d.place("com.foo", 0, 2)
        d.dispatch(WindowMutation.Fullscreen("com.foo/.Main", displayId = 0), DisplayOwner.LAUNCHER)
        assertNull(d.locations.locationOf("com.foo"), "fullscreen-return removes the slot placement")
        d.place("com.bar", 0, 1)
        d.dispatch(WindowMutation.ForceStop("com.bar"), DisplayOwner.LAUNCHER)
        assertNull(d.locations.locationOf("com.bar"), "force-stop removes the placement")
    }

    // ─────────────────── launcherSeam (raw command → typed guard) ───────────────────

    @Test
    fun `launcherSeam runs display-0 and no-display commands unchanged`() {
        val t = RecordingTransport()
        val seam = WindowCommandDispatcher(runCommand = t.run).launcherSeam()
        assertEquals("OUT:am stack list", seam("am stack list")) // NO_DISPLAY → allow
        val launch = "am start --display 0 --windowingMode 5 -n 'x/.Y'"
        assertEquals("OUT:$launch", seam(launch)) // display 0 → allow
        assertEquals(listOf("am stack list" to MutationPriority.NORMAL, launch to MutationPriority.NORMAL), t.calls)
    }

    @Test
    fun `launcherSeam blocks a command that leaks the cluster display and does not run it`() {
        val t = RecordingTransport()
        val seam = WindowCommandDispatcher(runCommand = t.run).launcherSeam()
        assertEquals("", seam("am start --display 1 --windowingMode 5 -n 'x/.Y'"))
        assertTrue(t.calls.isEmpty(), "a cluster-targeting launcher command must be structurally blocked")
    }

    @Test
    fun `launcherSeam allows a registered VD but blocks an unregistered secondary display`() {
        val t = RecordingTransport()
        val d = WindowCommandDispatcher(runCommand = t.run)
        val seam = d.launcherSeam()
        val cmd = "am start --display 9 --windowingMode 1 -n 'x/.Y'"
        assertEquals("", seam(cmd), "display 9 not owned → blocked")
        assertTrue(t.calls.isEmpty())
        d.registerLauncherVirtualDisplay(9)
        assertEquals("OUT:$cmd", seam(cmd), "display 9 now a launcher VD → runs")
        assertEquals(1, t.calls.size)
    }
}
