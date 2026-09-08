package com.byd.clusternav

import com.byd.clusternav.navigation.HudKeepAlivePolicy
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TASK 2 (closeout 1.28) content-only keep-alive contract. writeNavFrame is Android/HAL-coupled (needs the real
 * BYDAuto instrument device — off-car it returns early with "InstrumentDevice null"), so the keep-alive skip list
 * is pinned by source inspection: the keep-alive branch must RE-ASSERT CONTENT (guidance icon/dist/road + oversea)
 * but SKIP the latched session writes (SEND_NAVI_STATUS, SET_NAVI_SCREEN_STATUS) and the 3 SDK calls
 * (sendSimpleGuidanceInfo/sendNextPathName/sendRestRouteInfo). Status is a latched session flag; re-writing it
 * ~4×/s is churn.
 */
class NavHalContentKeepAliveTest {

    private val hal by lazy { SourceRoots.text("src/main/java/com/byd/clusternav/modules/hal/BydHal.kt") }
    private val owner by lazy { SourceRoots.text("src/main/java/com/byd/clusternav/NavigationHudOwner.kt") }

    @Test
    fun `writeNavFrame takes a keepAlive flag defaulting to false`() {
        assertTrue(hal.contains("keepAlive: Boolean = false"), "writeNavFrame must accept keepAlive (default false)")
    }

    @Test
    fun `keep-alive skips the latched status and screen-mode writes`() {
        assertTrue(
            hal.contains("if (!keepAlive) w(\"INSTRUMENT_SEND_NAVI_STATUS_SET\", 2)"),
            "SEND_NAVI_STATUS must be skipped on keep-alive (latched session flag)",
        )
        assertTrue(
            hal.contains("if (!keepAlive && writeSurface) featureId(\"SET_NAVI_SCREEN_STATUS_SET\")"),
            "SET_NAVI_SCREEN_STATUS skipped on keep-alive AND gated by writeSurface (2026-08-24 tách nội dung/bề mặt — chỉ dựng bề mặt cụm khi Cast OFF)",
        )
    }

    @Test
    fun `keep-alive skips all three SDK invokes`() {
        // All three sends are wrapped in a single `if (!keepAlive) { ... }` block (the only guard using a brace).
        val guardIdx = hal.indexOf("if (!keepAlive) {")
        assertTrue(guardIdx >= 0, "the 3 SDK calls must be wrapped in an if (!keepAlive) { } block")
        val afterGuard = hal.substring(guardIdx)
        listOf("sendSimpleGuidanceInfo", "sendNextPathName", "sendRestRouteInfo").forEach { m ->
            assertTrue(afterGuard.contains(m), "SDK method $m must live inside the keep-alive skip block")
        }
    }

    @Test
    fun `keep-alive still re-asserts content (guidance icon, dist, oversea)`() {
        // Content writes are NOT guarded by keepAlive → they run on every tick.
        assertTrue(hal.contains("w(\"INSTRUMENT_GUIDE_INFO_SIMPLE_SET\", icon)"))
        assertTrue(hal.contains("w(\"INSTRUMENT_FRONT_CROSSING_DISTANCE_SET\", segMeters)"))
        assertTrue(hal.contains("GUIDE_OVERSEA="))
        assertTrue(hal.contains("DIST_OVERSEA="))
    }

    @Test
    fun `owner passes keepAlive equal to not-realPush into writeNavFrame`() {
        assertTrue(owner.contains("keepAlive = !realPush"), "owner must pass keepAlive = !realPush to writeNavFrame")
    }

    @Test
    fun `keep-alive interval default is 250 ms (R6)`() {
        // The pure policy lives in :core; the owner schedules its tick against this default.
        assertEquals(250L, HudKeepAlivePolicy().intervalMs())
    }
}
