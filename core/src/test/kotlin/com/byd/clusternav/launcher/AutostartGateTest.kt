package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Off-device unit test for [AutostartGate] — the reusable single-in-flight + cooldown guard B6's launcher
 * auto-start uses (generalizes the proven [com.byd.clusternav.VietMapAutostart] guard).
 *
 * Red-green: dropping the in-flight CAS makes the "re-entrant claim while in-flight" test RED; dropping the
 * cooldown check makes the "claim within window after finish" test RED.
 */
class AutostartGateTest {

    private val cd = 30_000L

    @Test fun `outsideCooldown is true on the first ever run regardless of clock`() {
        assertTrue(AutostartGate.outsideCooldown(nowMs = 0L, lastRunAtMs = 0L, cooldownMs = cd))
        assertTrue(AutostartGate.outsideCooldown(nowMs = 999_999L, lastRunAtMs = 0L, cooldownMs = cd))
    }

    @Test fun `outsideCooldown is false inside the window and true at-or-after the boundary`() {
        val last = 10_000L
        assertFalse(AutostartGate.outsideCooldown(last + 1, last, cd), "just after a run")
        assertFalse(AutostartGate.outsideCooldown(last + cd - 1, last, cd), "1ms before boundary")
        assertTrue(AutostartGate.outsideCooldown(last + cd, last, cd), "exactly at boundary (>=)")
        assertTrue(AutostartGate.outsideCooldown(last + cd + 1, last, cd), "after boundary")
    }

    @Test fun `first claim succeeds and a re-entrant claim while in-flight is refused`() {
        val gate = AutostartGate(cd)
        assertTrue(gate.tryBegin(nowMs = 1_000L), "first claim proceeds")
        assertFalse(gate.tryBegin(nowMs = 1_000L), "re-entrant claim while in-flight is refused")
        gate.finish()
    }

    @Test fun `after finish a claim within cooldown is refused but outside it succeeds`() {
        val gate = AutostartGate(cd)
        assertTrue(gate.tryBegin(nowMs = 1_000L))
        gate.finish()
        assertFalse(gate.tryBegin(nowMs = 1_000L + cd - 1), "within cooldown after finish → refused")
        assertTrue(gate.tryBegin(nowMs = 1_000L + cd), "outside cooldown → allowed")
        gate.finish()
    }

    @Test fun `finish releases the slot so the next allowed run can claim`() {
        val gate = AutostartGate(cd)
        assertTrue(gate.tryBegin(nowMs = 1_000L))
        gate.finish()
        assertTrue(gate.tryBegin(nowMs = 1_000L + cd * 10), "far outside cooldown + not in-flight")
        gate.finish()
    }

    @Test fun `reset clears the latch and clock`() {
        val gate = AutostartGate(cd)
        assertTrue(gate.tryBegin(nowMs = 5_000L))
        // still in-flight + inside cooldown → refused
        assertFalse(gate.tryBegin(nowMs = 5_000L))
        gate.reset()
        // reset clears BOTH the in-flight latch and the last-run clock → first-ever semantics again
        assertTrue(gate.tryBegin(nowMs = 5_001L), "after reset a claim proceeds even inside the old cooldown")
        gate.finish()
    }

    @Test fun `two independent gates do not share state`() {
        val a = AutostartGate(cd)
        val b = AutostartGate(cd)
        assertTrue(a.tryBegin(nowMs = 1_000L), "gate A claims")
        assertTrue(b.tryBegin(nowMs = 1_000L), "gate B is independent — still claims while A is in-flight")
        a.finish(); b.finish()
    }
}
