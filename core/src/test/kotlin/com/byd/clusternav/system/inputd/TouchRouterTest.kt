package com.byd.clusternav.system.inputd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TouchRouter] — the fallback command MUST be byte-identical to what `VdAppHost` emitted before B4
 * (`input -d <display> tap <x> <y>`), so touch behaviour is unchanged whenever the daemon is off. This golden
 * lock is the tripwire guarding "no touch regression". Also pins the fallback decision.
 */
class TouchRouterTest {

    @Test
    fun `fallback tap command is byte-exact (matches pre-B4 VdAppHost)`() {
        // BYTE-for-BYTE what VdAppHost used to run: sh("input -d $displayId tap $x $y").
        assertEquals("input -d 7 tap 640 360", TouchRouter.fallbackTapCmd(7, 640, 360))
        assertEquals("input -d 0 tap 0 0", TouchRouter.fallbackTapCmd(0, 0, 0))
        assertEquals("input -d 3 tap 1919 719", TouchRouter.fallbackTapCmd(3, 1919, 719))
    }

    @Test
    fun `shouldFallback is true exactly when the daemon did not take the event`() {
        assertTrue(TouchRouter.shouldFallback(daemonRouted = false), "daemon down → must fall back")
        assertFalse(TouchRouter.shouldFallback(daemonRouted = true), "daemon took it → must NOT fall back")
    }
}
