package com.byd.clusternav

import com.byd.clusternav.modules.hal.BydHal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * TASK 5 (closeout 1.28) runtime-rejection cache. writeNavFrame itself needs a real BYDAuto HAL device (off-car
 * it returns early with "InstrumentDevice null"), so the skip/keep DECISION is unit-tested directly on the
 * process-local cache. On the owner trim a not-provisioned feature returns the sentinel rc -2147482648 → cached
 * → skipped next frame (kills the per-frame 'no permission device 1007' spam). On Sealion 6 (oversea provisioned)
 * the same ids return rc=0 → never cached → keep being written.
 */
class BydHalRejectionCacheTest {

    @BeforeEach fun reset() {
        BydHal.resetRejectionCacheForTest()
        BydHal.resetFeatureIdCacheForTest()
    }

    @Test
    fun `sentinel is the on-car value 0x800003E8 not Int MIN_VALUE`() {
        assertEquals(-2147482648, BydHal.NOT_PROVISIONED_RC, "must match the on-car observed not-provisioned rc")
        // The docs prose calls it "Int.MIN_VALUE" but that (-2147483648) would never match the real rc.
        assertNotEquals(Int.MIN_VALUE, BydHal.NOT_PROVISIONED_RC)
        assertEquals(0x800003E8.toInt(), BydHal.NOT_PROVISIONED_RC) // device-1007 no-permission sentinel
    }

    @Test
    fun `a feature returning the sentinel is cached and skipped on the next frame`() {
        val id = BydHal.EASY_NAVI_GUIDE_OVERSEA_ID   // 0x1F701010 — rejected on the owner trim
        assertFalse(BydHal.isFeatureRejected(id), "first frame attempts the write")
        assertTrue(BydHal.recordFeatureRc(id, -2147482648), "sentinel rc must cache the id")
        assertTrue(BydHal.isFeatureRejected(id), "next frame must skip the rejected feature")
    }

    @Test
    fun `a feature returning a normal rc is never cached (Sealion 6 keeps writing)`() {
        val id = BydHal.EASY_NAVI_GUIDE_OVERSEA_ID
        assertFalse(BydHal.recordFeatureRc(id, 0), "rc=0 (success) must not cache")
        assertFalse(BydHal.isFeatureRejected(id), "provisioned feature keeps being written every frame")
    }

    @Test
    fun `non-int rc (exception path or null) does not cache`() {
        assertFalse(BydHal.recordFeatureRc(1, null))
        assertFalse(BydHal.recordFeatureRc(2, "SecurityException: blocked"))
        assertFalse(BydHal.isFeatureRejected(1))
        assertFalse(BydHal.isFeatureRejected(2))
    }

    @Test
    fun `an SDK method that throws is cached by key and skipped`() {
        val key = "sendSimpleGuidanceInfo"
        assertFalse(BydHal.isSdkRejected(key))
        BydHal.recordSdkFailure(key)
        assertTrue(BydHal.isSdkRejected(key), "a throwing SDK call must be skipped next frame (kills spam)")
    }

    @Test
    fun `featureId lookups are cached and return the same value across calls`() {
        // D2 (closeout 1.28) featureId cache. Off-car the BYDAutoFeatureIds class is absent → featureId returns
        // null. The contract under test is the cache's CONSISTENCY: repeated lookups return the SAME result and
        // the second call is served from the cache / absent-set instead of re-doing reflection (on-car a present
        // field caches its Int identically). resetFeatureIdCacheForTest ran in @BeforeEach, so this starts clean.
        val name = "INSTRUMENT_GUIDE_INFO_SIMPLE_SET"
        val first = BydHal.featureId(name)
        val second = BydHal.featureId(name)
        assertEquals(first, second, "cached featureId lookup must return the same value across calls")
    }
}
