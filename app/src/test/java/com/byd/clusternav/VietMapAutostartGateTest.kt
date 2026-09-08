package com.byd.clusternav

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Off-car unit test for the [VietMapAutostart] re-entrancy + cooldown gate (B2, on-car 2026-09-06).
 *
 * The bug: with the VietMap bubble ON, every `MainActivity.onCreate` (open / theme-or-language recreate / boot
 * auto-open) ran "launch VietMap → sleep 1.5s → relaunch ClusterNav" with NO dedup → a foreground flash loop.
 * This locks the PURE gate that stops it: one run in-flight at a time, plus a cooldown between runs so a burst
 * of onCreate/recreate/boot triggers only launches once.
 *
 * Pure (no Android): drives [VietMapAutostart.outsideCooldown] + `tryBeginRun`/`finishRun` with explicit
 * clocks. Red-green: dropping the in-flight CAS makes the "second claim while in-flight" test RED; dropping the
 * cooldown check makes the "claim within window after finish" test RED.
 */
class VietMapAutostartGateTest {

    @BeforeEach fun setUp() { VietMapAutostart.resetGateForTest() }
    @AfterEach fun tearDown() { VietMapAutostart.resetGateForTest() }

    private val cd = VietMapAutostart.COOLDOWN_MS

    @Test fun `outsideCooldown is true on the first ever run regardless of clock`() {
        assertTrue(VietMapAutostart.outsideCooldown(nowMs = 0L, lastRunAtMs = 0L))
        assertTrue(VietMapAutostart.outsideCooldown(nowMs = 123_456L, lastRunAtMs = 0L))
    }

    @Test fun `outsideCooldown is false inside the window and true at-or-after the boundary`() {
        val last = 10_000L
        assertFalse(VietMapAutostart.outsideCooldown(last + 1, last), "just after a run")
        assertFalse(VietMapAutostart.outsideCooldown(last + cd - 1, last), "1ms before boundary")
        assertTrue(VietMapAutostart.outsideCooldown(last + cd, last), "exactly at boundary (>=)")
        assertTrue(VietMapAutostart.outsideCooldown(last + cd + 1, last), "after boundary")
    }

    @Test fun `first claim succeeds and a re-entrant claim while in-flight is refused`() {
        assertTrue(VietMapAutostart.tryBeginRun(nowMs = 1_000L), "first claim proceeds")
        assertFalse(VietMapAutostart.tryBeginRun(nowMs = 1_000L), "re-entrant claim while in-flight is refused")
        VietMapAutostart.finishRun()
    }

    @Test fun `after finish a claim within cooldown is refused but outside it succeeds`() {
        assertTrue(VietMapAutostart.tryBeginRun(nowMs = 1_000L))
        VietMapAutostart.finishRun()
        assertFalse(VietMapAutostart.tryBeginRun(nowMs = 1_000L + cd - 1), "within cooldown after finish → refused")
        assertTrue(VietMapAutostart.tryBeginRun(nowMs = 1_000L + cd), "outside cooldown → allowed")
        VietMapAutostart.finishRun()
    }

    @Test fun `finishRun releases the slot so the next allowed run can claim`() {
        assertTrue(VietMapAutostart.tryBeginRun(nowMs = 1_000L))
        VietMapAutostart.finishRun()
        assertTrue(VietMapAutostart.tryBeginRun(nowMs = 1_000L + cd * 10), "far outside cooldown + not in-flight")
        VietMapAutostart.finishRun()
    }

    // ── hasActivityRecord (FIX v1.33→v1.34, bubble): skip relaunch when VietMap already has an activity ──
    // record in the stack (bubble already initialised) instead of always relaunching (flash loop on-car).

    @Test fun `hasActivityRecord is false on blank dumpsys grep (process-only, no activity)`() {
        assertFalse(VietMapAutostart.hasActivityRecord("", VietMapAutostart.PKG))
        assertFalse(VietMapAutostart.hasActivityRecord("   \n  \n", VietMapAutostart.PKG))
    }

    @Test fun `hasActivityRecord is true for an ActivityRecord component reference`() {
        val dump = "    * Hist  #0: ActivityRecord{9a1b u0 vn.vietmap.live/.MainActivity t42}"
        assertTrue(VietMapAutostart.hasActivityRecord(dump, VietMapAutostart.PKG))
    }

    @Test fun `hasActivityRecord is true for realActivity and Task lines`() {
        assertTrue(VietMapAutostart.hasActivityRecord("      realActivity=vn.vietmap.live/.ui.MapActivity", VietMapAutostart.PKG))
        // A recents Task line references the package via A=uid:pkg (no slash) — still an activity record/task.
        assertTrue(
            VietMapAutostart.hasActivityRecord("  * Task{1f2e #123 type=standard A=10123:vn.vietmap.live U=0}", VietMapAutostart.PKG),
        )
    }

    @Test fun `hasActivityRecord ignores a bare unrelated mention with no record marker`() {
        // A stray line mentioning the package name but not an activity/task record must NOT count.
        assertFalse(VietMapAutostart.hasActivityRecord("note: vn.vietmap.live installed", VietMapAutostart.PKG))
    }

    @Test fun `hasActivityRecord uses PKG by default`() {
        assertTrue(VietMapAutostart.hasActivityRecord("ActivityRecord{x u0 ${VietMapAutostart.PKG}/.Main t1}"))
        assertFalse(VietMapAutostart.hasActivityRecord(""))
    }

    // ── isResumedActivity (B3, on-car 2026-09-07): poll chờ VietMap thật sự VÀO MAP (resumed) trước khi hạ ──
    // nền, thay Thread.sleep(1500) cứng. Foreground guard + vòng poll cùng dùng hàm PURE này.

    @Test fun `isResumedActivity is false on blank input`() {
        assertFalse(VietMapAutostart.isResumedActivity("", VietMapAutostart.PKG))
        assertFalse(VietMapAutostart.isResumedActivity("   \n \n ", VietMapAutostart.PKG))
    }

    @Test fun `isResumedActivity is true for mResumedActivity of the package`() {
        val dump = "  mResumedActivity: ActivityRecord{7f3a u0 vn.vietmap.live/.MainActivity t88}"
        assertTrue(VietMapAutostart.isResumedActivity(dump, VietMapAutostart.PKG))
    }

    @Test fun `isResumedActivity is true for topResumedActivity of the package`() {
        val dump = "    topResumedActivity=ActivityRecord{1a2b u0 vn.vietmap.live/.ui.MapActivity t12}"
        assertTrue(VietMapAutostart.isResumedActivity(dump, VietMapAutostart.PKG))
    }

    @Test fun `isResumedActivity is false when a different app is resumed`() {
        val dump = "  mResumedActivity: ActivityRecord{9c8d u0 com.byd.clusternav2/.MainActivity t3}"
        assertFalse(VietMapAutostart.isResumedActivity(dump, VietMapAutostart.PKG))
    }

    @Test fun `isResumedActivity requires a resumed line — a plain activity mention does not count`() {
        // A Hist/ActivityRecord line that references VietMap but is NOT a *ResumedActivity line (VietMap only
        // in the back stack, another app resumed) must NOT count as foreground.
        val dump = "    * Hist #1: ActivityRecord{5e6f u0 vn.vietmap.live/.MainActivity t88}"
        assertFalse(VietMapAutostart.isResumedActivity(dump, VietMapAutostart.PKG))
    }

    @Test fun `isResumedActivity needs the package component, not a bare mention`() {
        // "ResumedActivity: null" or a resumed line for another package must be false even if the string
        // happens to mention the package elsewhere without a component slash.
        assertFalse(VietMapAutostart.isResumedActivity("  mResumedActivity: null", VietMapAutostart.PKG))
        assertFalse(VietMapAutostart.isResumedActivity("  note vn.vietmap.live is installed", VietMapAutostart.PKG))
    }

    @Test fun `isResumedActivity uses PKG by default`() {
        assertTrue(VietMapAutostart.isResumedActivity("mResumedActivity: ActivityRecord{x u0 ${VietMapAutostart.PKG}/.Main t1}"))
        assertFalse(VietMapAutostart.isResumedActivity("mResumedActivity: ActivityRecord{x u0 other.pkg/.Main t1}"))
    }

    @Test fun `poll constants are sane (settle within timeout, positive interval)`() {
        assertTrue(VietMapAutostart.POLL_INTERVAL_MS > 0L)
        assertTrue(VietMapAutostart.SETTLE_MS > 0L)
        assertTrue(VietMapAutostart.POLL_TIMEOUT_MS > VietMapAutostart.SETTLE_MS, "timeout phải đủ chỗ cho ít nhất một lần settle")
    }
}
