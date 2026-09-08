package com.byd.clusternav.speedbadge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the badge placement math so the on-car placement UI can't silently drift.
 *
 * The badge is positioned by its CENTRE in cluster px; the overlay clamps that centre on-screen and converts
 * it to a TOP|LEFT `x`/`y`. If clampCenter or topLeftFromCenter regresses, a driver's dragged badge could land
 * partly (or fully) off the cluster — caught here off-car. Default cluster used in tests = 1920×720 (Seal).
 */
class BadgeLayoutTest {

    // ─── Size clamp (unchanged behaviour, still applied on Prefs read+write) ─────────────────────
    @Test
    fun `clampSizeDp clamps below, within, and above the bounds`() {
        assertEquals(60, BadgeLayout.clampSizeDp(10))     // below min → min
        assertEquals(60, BadgeLayout.clampSizeDp(60))     // at min
        assertEquals(120, BadgeLayout.clampSizeDp(120))   // default, within range
        assertEquals(240, BadgeLayout.clampSizeDp(240))   // at max
        assertEquals(240, BadgeLayout.clampSizeDp(1000))  // above max → max
    }

    @Test
    fun `size bounds and default are the documented values`() {
        assertEquals(60, BadgeLayout.SIZE_MIN_DP)
        assertEquals(240, BadgeLayout.SIZE_MAX_DP)
        assertEquals(120, BadgeLayout.SIZE_DEFAULT_DP)
    }

    // ─── clampCenter ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun `clampCenter leaves a centre that already fits untouched`() {
        assertEquals(960 to 360, BadgeLayout.clampCenter(960, 360, 120, 1920, 720))
    }

    @Test
    fun `clampCenter pushes the top-left corner overrun inward`() {
        // centre (0,0), size 120 → smallest valid centre is (half, half) = (60, 60)
        assertEquals(60 to 60, BadgeLayout.clampCenter(0, 0, 120, 1920, 720))
        assertEquals(60 to 60, BadgeLayout.clampCenter(-500, -500, 120, 1920, 720))
    }

    @Test
    fun `clampCenter pushes the bottom-right corner overrun inward`() {
        // centre (W,H), size 120 → largest valid centre is (W-half, H-half) = (1860, 660)
        assertEquals(1860 to 660, BadgeLayout.clampCenter(1920, 720, 120, 1920, 720))
        assertEquals(1860 to 660, BadgeLayout.clampCenter(9999, 9999, 120, 1920, 720))
    }

    @Test
    fun `clampCenter clamps each of the four edges independently`() {
        assertEquals(60 to 360, BadgeLayout.clampCenter(-100, 360, 120, 1920, 720))   // left edge
        assertEquals(1860 to 360, BadgeLayout.clampCenter(5000, 360, 120, 1920, 720)) // right edge
        assertEquals(960 to 60, BadgeLayout.clampCenter(960, -50, 120, 1920, 720))    // top edge
        assertEquals(960 to 660, BadgeLayout.clampCenter(960, 9999, 120, 1920, 720))  // bottom edge
    }

    @Test
    fun `clampCenter centres a badge larger than the cluster instead of throwing`() {
        // size 800 on a 720-tall cluster: y cannot fit → centred at H/2; x still clamps normally.
        assertEquals(400 to 360, BadgeLayout.clampCenter(0, 0, 800, 1920, 720))
    }

    // ─── topLeftFromCenter ─────────────────────────────────────────────────────────────────────
    @Test
    fun `topLeftFromCenter subtracts half the size on each axis`() {
        assertEquals(900 to 300, BadgeLayout.topLeftFromCenter(960, 360, 120))
        assertEquals(0 to 0, BadgeLayout.topLeftFromCenter(60, 60, 120))
    }

    @Test
    fun `clampCenter then topLeftFromCenter always lands fully on-screen`() {
        val size = 120
        for (probe in listOf(-9999 to -9999, 0 to 0, 960 to 360, 5000 to 5000, 1920 to 720)) {
            val (cx, cy) = BadgeLayout.clampCenter(probe.first, probe.second, size, 1920, 720)
            val (left, top) = BadgeLayout.topLeftFromCenter(cx, cy, size)
            assertTrue(left in 0..(1920 - size), "left $left off-screen for probe $probe")
            assertTrue(top in 0..(720 - size), "top $top off-screen for probe $probe")
        }
    }
}
