package com.byd.clusternav.vietmapwidget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for VietMapWidgetClearStateMachine pure logic — backoff, constants, enums.
 * Integration tests requiring Handler/Looper belong in androidTest.
 */
class VietMapWidgetClearStateMachineTest {

    @Test
    fun `backoff calculation is exponential with cap`() {
        assertEquals(200L, VietMapWidgetClearStateMachine.backoffMs(1))
        assertEquals(400L, VietMapWidgetClearStateMachine.backoffMs(2))
        assertEquals(800L, VietMapWidgetClearStateMachine.backoffMs(3))
        assertEquals(1600L, VietMapWidgetClearStateMachine.backoffMs(4))
        assertEquals(3200L, VietMapWidgetClearStateMachine.backoffMs(5))
        assertEquals(5000L, VietMapWidgetClearStateMachine.backoffMs(6))
        assertEquals(5000L, VietMapWidgetClearStateMachine.backoffMs(10))
    }

    @Test
    fun `max attempts is 5`() {
        assertEquals(5, VietMapWidgetClearStateMachine.MAX_ATTEMPTS)
    }

    @Test
    fun `clear trigger enum covers all expected triggers`() {
        val triggers = SpeedSignClearTrigger.entries
        assertEquals(5, triggers.size)
        assertTrue(triggers.contains(SpeedSignClearTrigger.MASTER_OFF))
        assertTrue(triggers.contains(SpeedSignClearTrigger.STALE_THRESHOLD))
        assertTrue(triggers.contains(SpeedSignClearTrigger.PROVIDER_DISCONNECT))
        assertTrue(triggers.contains(SpeedSignClearTrigger.SERVICE_DESTROY))
        assertTrue(triggers.contains(SpeedSignClearTrigger.PROCESS_BOOTSTRAP))
    }

    @Test
    fun `clear state enum covers all expected states`() {
        val states = SpeedSignClearState.entries
        assertEquals(4, states.size)
        assertTrue(states.contains(SpeedSignClearState.ACTIVE))
        assertTrue(states.contains(SpeedSignClearState.CLEARING))
        assertTrue(states.contains(SpeedSignClearState.CLEARED))
        assertTrue(states.contains(SpeedSignClearState.RETRY_PENDING))
    }
}
