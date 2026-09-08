package com.byd.clusternav.body

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Device-free unit tests for [Bodywork] (pure :core model). Locks the RE window/hatch constants + method names
 * from `docs/diagnostics/bodywork-window-trunk-RE-2026-09-06.md` and the pure boolean→state mappings.
 *
 * Red-green: e.g. swapping HATCH_OPEN/HATCH_CLOSE, or making windowState(true) return 0, turns the relevant
 * assertion RED — this table is the single source of truth BodyworkControl encodes onto the HAL.
 */
class BodyworkTest {

    @Test fun `window indices match BODYWORK_CMD_WINDOW mapping`() {
        assertEquals(1, Bodywork.WINDOW_LF)
        assertEquals(2, Bodywork.WINDOW_RF)
        assertEquals(3, Bodywork.WINDOW_LR)
        assertEquals(4, Bodywork.WINDOW_RR)
        assertEquals(listOf(1, 2, 3, 4), Bodywork.ALL_WINDOWS)
    }

    @Test fun `window state constants are binary close-0 open-1`() {
        assertEquals(0, Bodywork.WINDOW_CLOSE)
        assertEquals(1, Bodywork.WINDOW_OPEN)
        assertEquals(Bodywork.WINDOW_OPEN, Bodywork.windowState(open = true))
        assertEquals(Bodywork.WINDOW_CLOSE, Bodywork.windowState(open = false))
    }

    @Test fun `hatch status OPEN-1 CLOSE-2 matches setHetchDoorStatus`() {
        // CLOSE_HETCH_DOOR = 2 is proven; OPEN = 1 is the extrapolated candidate (RE doc).
        assertEquals(1, Bodywork.HATCH_OPEN)
        assertEquals(2, Bodywork.HATCH_CLOSE)
        assertEquals(Bodywork.HATCH_OPEN, Bodywork.hatchStatus(open = true))
        assertEquals(Bodywork.HATCH_CLOSE, Bodywork.hatchStatus(open = false))
    }

    @Test fun `isWindow accepts 1 to 4 and rejects out of range`() {
        assertTrue(Bodywork.isWindow(1))
        assertTrue(Bodywork.isWindow(4))
        assertFalse(Bodywork.isWindow(0))
        assertFalse(Bodywork.isWindow(5))
        assertFalse(Bodywork.isWindow(-1))
    }

    @Test fun `method names match the BYDAutoBodyworkDevice API (Hetch typo preserved)`() {
        assertEquals("setBodyWindowCtrlState", Bodywork.METHOD_SET_WINDOW)
        assertEquals("setAllWindowState", Bodywork.METHOD_SET_ALL_WINDOWS)
        assertEquals("setHetchDoorStatus", Bodywork.METHOD_SET_HATCH)   // OEM API typo "Hetch" — must match
        assertEquals("getWindowState", Bodywork.METHOD_GET_WINDOW_STATE)
        assertEquals("getWindowOpenPercent", Bodywork.METHOD_GET_WINDOW_PERCENT)
        assertEquals("getWindowPermitState", Bodywork.METHOD_GET_WINDOW_PERMIT)
    }

    @Test fun `windowLabelKey is bilingual key per window and null when invalid`() {
        assertEquals("window_lf", Bodywork.windowLabelKey(Bodywork.WINDOW_LF))
        assertEquals("window_rf", Bodywork.windowLabelKey(Bodywork.WINDOW_RF))
        assertEquals("window_lr", Bodywork.windowLabelKey(Bodywork.WINDOW_LR))
        assertEquals("window_rr", Bodywork.windowLabelKey(Bodywork.WINDOW_RR))
        assertNull(Bodywork.windowLabelKey(0))
        assertNull(Bodywork.windowLabelKey(9))
    }
}
