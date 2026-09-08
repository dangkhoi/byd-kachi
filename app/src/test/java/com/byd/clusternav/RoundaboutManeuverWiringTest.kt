package com.byd.clusternav

import com.byd.clusternav.navigation.ArrayPixelFrame
import com.byd.clusternav.navigation.Maneuver
import com.byd.clusternav.navigation.ManeuverRegistry
import com.byd.clusternav.navigation.ManeuverSignature
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TASK 1 (closeout 1.28) wiring contract: NavNotificationListener.handle must carry a DIRECTIONAL roundabout
 * Maneuver onto NavState.maneuver so the HUD/centre encoder (NavRepository → frame.content.maneuver.toHudIcon())
 * yields the exit direction (CAN 15/18/20/22 …) instead of the AMAP-int collapse (every roundabout → 11 →
 * ROUNDABOUT generic → HUD 20).
 *
 * handle() is private + Android-coupled (StatusBarNotification/Bitmap), so — like the other boundary tests here
 * — the WIRING is pinned by reading the source, and the classify → NavState → toHudIcon contract is PROVEN with
 * a pure-JVM synthetic frame built from the exact registry signature (Hamming 0) for a roundabout-left glyph.
 */
class RoundaboutManeuverWiringTest {

    private val listenerSrc by lazy {
        SourceRoots.text("src/main/java/com/byd/clusternav/NavNotificationListener.kt")
    }

    @Test
    fun `handle wires a directional maneuver onto NavState via classifyManeuver with AMAP fallback`() {
        // The exact expression the design mandates, so the pure-JVM proof below matches the shipped wiring.
        assertTrue(
            listenerSrc.contains("ManeuverSignature.classifyManeuver(arrow?.asPixelFrame())"),
            "handle() must classify the directional maneuver from the arrow bitmap",
        )
        assertTrue(
            listenerSrc.contains("Maneuver.fromAmapIcon(classifiedIcon)"),
            "non-roundabout / held frames must fall back to the AMAP int (unchanged behaviour)",
        )
        assertTrue(
            listenerSrc.contains(".copy(maneuver = maneuver)"),
            "the directional maneuver must be set on NavState.maneuver (carried into NavRepository.ingest)",
        )
    }

    /**
     * Build a 15×15 frame whose signature is EXACTLY the registry bits for a roundabout-left glyph (each grid
     * cell = 1 pixel; white=ink, black=bg → Hamming 0 → exact registry match). Proves the full TASK 1 contract:
     * arrow → classifyManeuver → ROUNDABOUT_LEFT → NavState.maneuver → toHudIcon() == 15 (CAN roundabout ¾ left).
     */
    @Test
    fun `a roundabout-left arrow ends as NavState maneuver ROUNDABOUT_LEFT with HUD CAN 15`() {
        val frame = registryFrame("maneuver_roundabout_enter_and_exit_ccw_normal_left")

        // Exactly the handle() wiring expression (classifiedIcon is the AMAP collapse any roundabout would carry).
        val classifiedIcon = 11
        val maneuver = ManeuverSignature.classifyManeuver(frame) ?: Maneuver.fromAmapIcon(classifiedIcon)
        assertEquals(Maneuver.ROUNDABOUT_LEFT, maneuver, "roundabout-left glyph must classify directionally")

        val state = NavState().copy(maneuver = maneuver)
        assertEquals(Maneuver.ROUNDABOUT_LEFT, state.maneuver)
        assertEquals(15, state.maneuver!!.toHudIcon(), "HUD CAN for roundabout ¾ left = 15 (OpenBYD table)")
    }

    @Test
    fun `non-roundabout frame falls back to the AMAP int maneuver (unchanged behaviour)`() {
        // classifyManeuver returns null for a null/non-roundabout frame → the fallback preserves prior behaviour.
        val classifiedIcon = 2   // AMAP turn-left
        val maneuver = ManeuverSignature.classifyManeuver(null) ?: Maneuver.fromAmapIcon(classifiedIcon)
        val state = NavState().copy(maneuver = maneuver)
        assertEquals(Maneuver.TURN_LEFT, state.maneuver)
        assertEquals(1, state.maneuver!!.toHudIcon())
    }

    private fun registryFrame(name: String): ArrayPixelFrame {
        val bits = ManeuverRegistry.RAW.first { it.second == name }.first
        val grid = 15
        assertEquals(grid * grid, bits.length, "registry signature must be 15×15 = 225 bits")
        // white (opaque) = ink pixel, black (opaque) = background; 1 pixel/cell → signature == registry bits.
        val px = IntArray(bits.length) { if (bits[it] == '1') 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        return ArrayPixelFrame(grid, grid, px)
    }
}
