package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.screencapture.CaptureBoundsHeuristic.Candidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Pure off-car lock for the arrow/camera a11y-node picker (node identification itself = VERIFY-ON-CAR). */
class CaptureBoundsHeuristicTest {

    private val region = CropRect(0, 0, 1920, 720)

    @Test
    fun `arrow keyword beats generic panel`() {
        val arrow = CropRect(30, 220, 200, 300)
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.ARROW,
            listOf(
                Candidate("android.view.View", "Guidance panel", CropRect(0, 0, 800, 400)),
                Candidate("android.widget.ImageView", "Turn right", arrow),
            ),
            region,
        )
        assertEquals(arrow, picked)
    }

    @Test
    fun `camera keyword picked for VietMap target`() {
        val cam = CropRect(1500, 100, 1620, 220)
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.CAMERA,
            listOf(
                Candidate("android.widget.TextView", "80", CropRect(1000, 100, 1100, 160)),
                Candidate("android.widget.ImageView", "Speed camera ahead", cam),
            ),
            region,
        )
        assertEquals(cam, picked)
    }

    @Test
    fun `too-small node rejected`() {
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.ARROW,
            listOf(Candidate("android.widget.ImageView", "turn", CropRect(0, 0, 4, 4))),
            region,
        )
        assertNull(picked)
    }

    @Test
    fun `full-screen panel rejected as too big`() {
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.ARROW,
            listOf(Candidate("android.view.View", "turn arrow", CropRect(0, 0, 1920, 720))),
            region,
        )
        assertNull(picked)
    }

    @Test
    fun `image fallback when no keyword`() {
        val icon = CropRect(40, 40, 120, 120)
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.ARROW,
            listOf(
                Candidate("android.widget.TextView", "500 m", CropRect(200, 40, 360, 90)),
                Candidate("android.widget.ImageView", "", icon),
            ),
            region,
        )
        assertEquals(icon, picked)
    }

    @Test
    fun `candidate outside region excluded`() {
        val rightRegion = CropRect(960, 0, 1920, 720)
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.ARROW,
            listOf(Candidate("android.widget.ImageView", "turn left", CropRect(30, 220, 200, 300))),
            rightRegion,
        )
        assertNull(picked)
    }

    @Test
    fun `empty candidates to null`() {
        assertNull(CaptureBoundsHeuristic.pick(CaptureTarget.ARROW, emptyList(), region))
    }

    // ── B3.5 regression: emulator Waze+VietMap @960x720 picked tiny junk instead of the arrow banner ────────

    /**
     * The reported bug: an 18×19 traffic-sign icon AND a 62×62 car-marker fragment (both plausible a11y nodes,
     * the sign even carrying a "turn" keyword) were preferred over the ~180×80 Waze maneuver banner because the
     * old scorer took the SMALLEST keyword/image match. The banner must now win.
     */
    @Test
    fun `B3-5 picks the arrow banner over 18x19 and 62x62 junk`() {
        val emulator = CropRect(0, 0, 960, 720)
        val banner = CropRect(20, 40, 200, 120)           // ~180×80 top-left Waze banner
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.ARROW,
            listOf(
                Candidate("android.widget.ImageView", "turn", CropRect(20, 20, 38, 39)),   // 18×19 traffic sign
                Candidate("android.widget.ImageView", "", CropRect(400, 300, 462, 362)),   // 62×62 car-marker frag
                Candidate("android.widget.ImageView", "Turn right", banner),
            ),
            emulator,
        )
        assertEquals(banner, picked)
    }

    /** A 62×62 car-marker fragment alone (no valid target) must be rejected by the min-area gate → null. */
    @Test
    fun `B3-5 lone 62x62 fragment rejected by min area`() {
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.ARROW,
            listOf(Candidate("android.widget.ImageView", "", CropRect(400, 300, 462, 362))),
            CropRect(0, 0, 960, 720),
        )
        assertNull(picked)
    }

    /** No more "smallest wins": between two valid same-tier image icons, the larger plausible one is chosen. */
    @Test
    fun `B3-5 prefers larger plausible icon not the smallest`() {
        val small = CropRect(20, 40, 100, 120)            // 80×80 (valid, but smaller)
        val banner = CropRect(20, 40, 200, 120)           // 180×80 (closer to ideal icon/banner size)
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.ARROW,
            listOf(
                Candidate("android.widget.ImageView", "", small),
                Candidate("android.widget.ImageView", "", banner),
            ),
            region,
        )
        assertEquals(banner, picked)
    }

    /** VietMap CAMERA target: a tiny speed-limit glyph must not beat the real camera icon. */
    @Test
    fun `B3-5 camera icon beats tiny junk glyph`() {
        val camIcon = CropRect(40, 120, 180, 260)         // 140×140 camera icon
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.CAMERA,
            listOf(
                Candidate("android.widget.ImageView", "camera", CropRect(10, 10, 30, 30)),  // 20×20 junk (rejected)
                Candidate("android.widget.ImageView", "Speed camera", camIcon),
            ),
            CropRect(0, 0, 960, 720),
        )
        assertEquals(camIcon, picked)
    }

    /** A long marquee road-name bar (extreme aspect) must not out-score a normal arrow banner. */
    @Test
    fun `B3-5 marquee sliver loses to arrow banner`() {
        val banner = CropRect(20, 40, 200, 120)           // 180×80 aspect 2.25
        val marquee = CropRect(0, 200, 900, 245)          // 900×45 aspect 20 (road-name marquee)
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.ARROW,
            listOf(
                Candidate("android.widget.ImageView", "turn", marquee),
                Candidate("android.widget.ImageView", "turn", banner),
            ),
            region,
        )
        assertEquals(banner, picked)
    }

    /** Expected-zone affinity: among equal-size, equal-tier nodes, the top-left one wins for ARROW. */
    @Test
    fun `B3-5 arrow zone prefers top-left among equals`() {
        val topLeft = CropRect(0, 0, 100, 100)
        val bottomRight = CropRect(1820, 620, 1920, 720)
        val picked = CaptureBoundsHeuristic.pick(
            CaptureTarget.ARROW,
            listOf(
                Candidate("android.widget.ImageView", "", bottomRight),
                Candidate("android.widget.ImageView", "", topLeft),
            ),
            region,
        )
        assertEquals(topLeft, picked)
    }
}
