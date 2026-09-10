package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** W1b: [CarControlAdapter] route theo kind, off-car no-op, sentinel = fail, KHÔNG gate. */
class CarControlAdapterTest {

    @Test fun `toggle on routes named-method and returns true when rc ok`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.toggle("pm25", true))
        assertEquals("setAutoCleanAirState", gw.namedCalls[0].method)
        assertEquals(listOf(1), gw.namedCalls[0].args)
    }

    @Test fun `step routes value`() {
        val gw = FakeHalGateway(featureRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.step("fan", 5))       // fan = feature 501219340
        assertEquals(5, gw.featureSetCalls[0].value)
    }

    @Test fun `cover open derives (window,state)`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.cover("win_lf", true))
        assertEquals(listOf(1, 1), gw.namedCalls[0].args)
    }

    @Test fun `select routes index for feature-id control`() {
        val gw = FakeHalGateway(featureRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.select("drive_mode", 2))   // drive_mode = feature 1272971280
        assertEquals(1272971280, gw.featureSetCalls[0].id)
        assertEquals(2, gw.featureSetCalls[0].value)
    }

    @Test fun `press fires momentary named-method with arg 1`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.press("pm25_clean_now"))   // setQuickCleanAirState(1)
        assertEquals("setQuickCleanAirState", gw.namedCalls[0].method)
        assertEquals(listOf(1), gw.namedCalls[0].args)
    }

    @Test fun `act dispatches by ControlKind`() {
        val gw = FakeHalGateway(namedRc = 0L, featureRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.act("lock", 1))            // TOGGLE (named)
        assertTrue(adapter.act("drive_mode", 3))      // SELECT (feature)
    }

    @Test fun `off-car write is a no-op returning false`() {
        val adapter = CarControlAdapter(HalBindingTable(FakeHalGateway()))   // rc null
        assertFalse(adapter.toggle("pm25", true))
        assertFalse(adapter.cover("win_lf", true))
        assertFalse(adapter.press("pm25_clean_now"))
    }

    @Test fun `sentinel rc is treated as failure`() {
        val adapter = CarControlAdapter(HalBindingTable(FakeHalGateway(namedRc = -2147482648L)))
        assertFalse(adapter.toggle("pm25", true))
    }

    @Test fun `unknown control id returns false`() {
        assertFalse(CarControlAdapter(HalBindingTable(FakeHalGateway(namedRc = 0L))).toggle("khong_co", true))
    }
}
