package com.byd.clusternav.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [WindowCommandDispatcher] — cổng ownership của nhánh launcher: launcher→cụm (id dò live) BỊ CHẶN + KHÔNG dispatch;
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
    fun `launcher targeting the detected cluster display is REJECTED and never dispatched`() {
        val t = RecordingTransport()
        val d = WindowCommandDispatcher(runCommand = t.run)
        d.setCastDisplay(1)   // cụm thật là display 1 trên đời xe này (dò live)
        val r = d.dispatch(launcherLaunch(1), DisplayOwner.LAUNCHER)
        assertTrue(r is DispatchResult.Rejected, "launcher → cluster must be rejected")
        assertTrue((r as DispatchResult.Rejected).reason.contains("cross-boundary"), r.reason)
        assertTrue(t.calls.isEmpty(), "a rejected command must NOT reach the transport")
    }

    @Test
    fun `launcher targeting display 1 before any cast detection is still REJECTED (unknown = deny)`() {
        val t = RecordingTransport()
        val d = WindowCommandDispatcher(runCommand = t.run)
        assertTrue(d.dispatch(launcherLaunch(1), DisplayOwner.LAUNCHER) is DispatchResult.Rejected)
        assertTrue(t.calls.isEmpty())
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
        val d = WindowCommandDispatcher(runCommand = t.run)
        d.setCastDisplay(2)
        val seam = d.launcherSeam()
        assertEquals("", seam("am start --display 2 --windowingMode 5 -n 'x/.Y'"))
        assertEquals("", seam("am start --display 1 --windowingMode 5 -n 'x/.Y'"), "display lạ (chưa ai sở hữu) cũng chặn")
        assertTrue(t.calls.isEmpty(), "a cluster-targeting launcher command must be structurally blocked")
    }

    // ─────────────────── B4 · DISPLAY-OWNER-DYNAMIC — hồi quy dựng từ log thật ───────────────────

    /**
     * Hai dòng NGUYÊN VĂN từ xe 15/09 (`docs/diagnostics/perf-oncar-2026-09-26/kachi-logs/usage-1789473430976.log`, cùng
     * phiên với fixture `CastDisplayFixtures2026_09_15`). [ĐO máy ảo 05/10] lặp lại đúng dòng REJECT ×3 ⇒ VietMap không vào ô 1.
     */
    private val carVdLine = "09-15 18:57:13.265 I/KachiVd ( 5343): tạo màn ảo kachi-slot-0-1789473433259 — ô 0 · display 1 · chủ ws@196847688 · đang sống 1"
    private val carRejectLine = "09-15 18:57:14.742 I/Kachi/WinDispatch( 5343): REJECT LAUNCHER Raw @display=1: cross-boundary: LAUNCHER nhắm display 1 thuộc CAST (mutation=Raw)"

    @Test
    fun `regression 15-09 car and 05-10 emulator - slot VD that got display 1 after cold boot opens its app, no REJECT`() {
        val vdId = Regex("""· display (\d+) ·""").find(carVdLine)!!.groupValues[1].toInt()
        assertEquals(1, vdId, "fixture: ô 0 nhận display 1")
        assertTrue(carRejectLine.contains("REJECT LAUNCHER Raw @display=$vdId"), "fixture: đúng dòng lỗi đã đo")

        val t = RecordingTransport()
        val logs = mutableListOf<String>()
        val d = WindowCommandDispatcher(runCommand = t.run, log = { logs += it })
        d.registerLauncherVirtualDisplay(vdId)   // VdAppHost: đăng ký TRƯỚC maybeLaunch (bản cũ: require(id > 1) ném, bị nuốt)
        d.setCastDisplay(2)                      // cụm thật dò được sau khi mở chiếu (fixture 15/09)
        val cmd = com.byd.clusternav.launcher.FreeformLaunch.launchOnDisplayCmd("vn.vietmap.example/.Main", vdId, windowingMode = 1)

        assertEquals("OUT:$cmd", d.launcherSeam()(cmd), "lệnh mở app vào ô display 1 phải chạy")
        assertTrue(logs.none { it.startsWith("REJECT LAUNCHER Raw @display=$vdId") }, "không còn dòng REJECT của 15/09 · 05/10: $logs")
        assertEquals(listOf(cmd to MutationPriority.NORMAL), t.calls)
        assertEquals("", d.launcherSeam()("am start --display 2 --windowingMode 1 -n 'x/.Y'"), "cụm thật (2) vẫn bị chặn")
    }

    @Test
    fun `dispatcher location registry reads the live cast id, not a constant`() {
        val d = WindowCommandDispatcher(runCommand = RecordingTransport().run)
        d.registerLauncherVirtualDisplay(1)
        d.place("com.inslot", 1, null)
        assertTrue(d.locations.isCastable("com.inslot"), "app trong ô display 1 không phải 'đã trên cụm'")
        d.setCastDisplay(2)
        d.place("com.oncast", 2, null)
        assertTrue(!d.locations.isCastable("com.oncast"), "app trên cụm dò được ⇒ đã chiếu")
        d.setCastDisplay(null)
        assertTrue(d.locations.isCastable("com.oncast"), "chiếu đóng ⇒ không còn cụm")
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
