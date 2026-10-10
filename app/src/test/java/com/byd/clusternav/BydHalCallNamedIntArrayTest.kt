package com.byd.clusternav

import com.byd.clusternav.modules.hal.BydHal
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Off-car unit test for [BydHal.callNamedIntArray] — the reflection helper for named-method HAL writes that take a
 * single `int[]` (the cầu-kiểm-thử `setarr`/`setisa` ops; real target = `BYDAutoStatisticDevice.sendIsaDataToAdas(int[12])`,
 * RE `re-map-speedlimit-2606-2026-10-10.md` §1.2).
 *
 * No real BYDAuto HAL off-car ⇒ reflection drives a local fake mimicking the `sendIsaDataToAdas(int[])` shape.
 * Locks: int[]-param dispatch (NOT confused with int×N), the array reaches the method intact, rc string
 * formatting, and the degrade-safe (never-throw) path when a method is absent or the HAL throws.
 */
class BydHalCallNamedIntArrayTest {

    /** Fake mirroring BYDAutoStatisticDevice's int[]-array method shape (public so reflection can invoke it). */
    class FakeStatisticDevice {
        var lastArray: IntArray? = null
        fun sendIsaDataToAdas(data: IntArray): Int { lastArray = data; return 0 }
        fun scalar(a: Int, b: Int): Int = a + b
        fun boom(data: IntArray): Int = throw IllegalStateException("HAL blocked len=${data.size}")
    }

    @Test fun `int-array method dispatches and passes the array intact`() {
        val dev = FakeStatisticDevice()
        val arr = intArrayOf(10, 2, 2, 0, 8191, 2, 31, 31, 2, 255, 1, 1)
        assertEquals("rc=0", BydHal.callNamedIntArray(dev, "sendIsaDataToAdas", arr))
        assertArrayEquals(arr, dev.lastArray, "mảng 12 ô phải tới method nguyên vẹn")
    }

    @Test fun `does not match a scalar int-by-int method of the same arity`() {
        // `scalar(int,int)` is NOT `scalar(int[])` — getMethod(name, int[].class) must miss it, degrade-safe.
        val rc = BydHal.callNamedIntArray(FakeStatisticDevice(), "scalar", intArrayOf(1, 2))
        assertTrue(rc.contains("NoSuchMethodException"), "int[] không được khớp method int×N: $rc")
    }

    @Test fun `absent method degrades to error string, never throws`() {
        val rc = BydHal.callNamedIntArray(FakeStatisticDevice(), "doesNotExist", intArrayOf(1))
        assertTrue(rc.contains("NoSuchMethodException"), "expected degrade-safe root() string, got: $rc")
    }

    @Test fun `throwing method is caught and root-summarized`() {
        val rc = BydHal.callNamedIntArray(FakeStatisticDevice(), "boom", intArrayOf(1, 2, 3))
        assertTrue(rc.contains("IllegalStateException"), "expected root() unwrap, got: $rc")
        assertTrue(rc.contains("len=3"), "expected root cause message, got: $rc")
    }
}
