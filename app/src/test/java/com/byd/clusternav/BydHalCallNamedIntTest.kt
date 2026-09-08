package com.byd.clusternav

import com.byd.clusternav.modules.hal.BydHal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Off-car unit test for [BydHal.callNamedInt] — the reused reflection helper for named-method HAL writes
 * (windows/trunk via [com.byd.clusternav.body.BodyworkControl], the on-car HAL probe, future seat-fix).
 *
 * There is no real BYDAuto HAL off-car, so reflection is driven against a local fake that mimics the ROM
 * method shapes (int×N setters returning an rc). Locks: arity dispatch (0/1/2 int), rc string formatting, and
 * the degrade-safe (never-throw) path when a method is absent or the HAL throws.
 *
 * Red-green: making callNamedInt rethrow instead of returning [BydHal.root] turns the last two tests RED.
 */
class BydHalCallNamedIntTest {

    /** Fake device mirroring BYDAutoBodyworkDevice method shapes (public so reflection can invoke it). */
    class FakeBodyworkDevice {
        var lastCall: String = ""
        fun setBodyWindowCtrlState(window: Int, state: Int): Int { lastCall = "win($window,$state)"; return 0 }
        fun setHetchDoorStatus(status: Int): Int { lastCall = "hatch($status)"; return 7 }
        fun noArgs(): Int = 42
        fun boom(x: Int): Int = throw IllegalStateException("HAL blocked $x")
    }

    @Test fun `two-int method dispatches by arity and formats rc`() {
        val dev = FakeBodyworkDevice()
        assertEquals("rc=0", BydHal.callNamedInt(dev, "setBodyWindowCtrlState", 1, 1))
        assertEquals("win(1,1)", dev.lastCall)
    }

    @Test fun `one-int method returns its rc`() {
        val dev = FakeBodyworkDevice()
        assertEquals("rc=7", BydHal.callNamedInt(dev, "setHetchDoorStatus", 2))
        assertEquals("hatch(2)", dev.lastCall)
    }

    @Test fun `zero-int method is callable`() {
        assertEquals("rc=42", BydHal.callNamedInt(FakeBodyworkDevice(), "noArgs"))
    }

    @Test fun `absent method degrades to error string, never throws`() {
        val rc = BydHal.callNamedInt(FakeBodyworkDevice(), "doesNotExist", 1)
        assertTrue(rc.contains("NoSuchMethodException"), "expected degrade-safe root() string, got: $rc")
    }

    @Test fun `throwing method is caught and root-summarized`() {
        val rc = BydHal.callNamedInt(FakeBodyworkDevice(), "boom", 5)
        assertTrue(rc.contains("IllegalStateException"), "expected root() unwrap, got: $rc")
        assertTrue(rc.contains("HAL blocked 5"), "expected root cause message, got: $rc")
    }
}
