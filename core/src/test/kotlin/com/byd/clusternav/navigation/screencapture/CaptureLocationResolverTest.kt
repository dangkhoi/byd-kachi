package com.byd.clusternav.navigation.screencapture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pure off-car lock for the `am stack list` → [AppLocation] resolver (drives selectCase end-to-end). */
class CaptureLocationResolverTest {

    private val geom = DisplayGeometry(1920, 720)

    private fun stack(pkg: String, display: Int, l: Int, t: Int, r: Int, b: Int, visible: Boolean, withFullHome: Boolean = true): String {
        val home = if (withFullHome)
            "Stack id=0 bounds=[0,0][1920,720] displayId=$display userId=0\n" +
                "  taskId=1: com.android.launcher3/.Launcher bounds=[0,0][1920,720] visible=false\n"
        else ""
        return home +
            "Stack id=7 bounds=[$l,$t][$r,$b] displayId=$display userId=0\n" +
            "  taskId=42: $pkg/.MainActivity bounds=[$l,$t][$r,$b] visible=$visible topActivity=$pkg/.MainActivity\n"
    }

    @Test
    fun `fullscreen main to FULL_MAIN`() {
        val loc = CaptureLocationResolver.resolve(
            stack("com.google.android.apps.maps", 0, 0, 0, 1920, 720, visible = true),
            "com.google.android.apps.maps", navFresh = true, foregroundHint = false,
        )
        assertEquals(0, loc.displayId)
        assertTrue(loc.isFullscreen)
        assertNull(loc.slotSide)
        assertTrue(loc.foreground)
        assertEquals(CaptureCase.FULL_MAIN, CaptureRouter.selectCase(loc, geom))
    }

    @Test
    fun `split left half to HALF_MAIN_SPLIT LEFT ~50pct`() {
        val loc = CaptureLocationResolver.resolve(
            stack("com.waze", 0, 0, 0, 960, 720, visible = true),
            "com.waze", navFresh = true, foregroundHint = false,
        )
        assertFalse(loc.isFullscreen)
        assertEquals(CaptureSlotSide.LEFT, loc.slotSide)
        assertEquals(50, loc.leftPercent)
        assertEquals(CaptureCase.HALF_MAIN_SPLIT, CaptureRouter.selectCase(loc, geom))
    }

    @Test
    fun `split right half to HALF_MAIN_SPLIT RIGHT ~50pct`() {
        val loc = CaptureLocationResolver.resolve(
            stack("vn.vietmap.live", 0, 960, 0, 1920, 720, visible = true),
            "vn.vietmap.live", navFresh = true, foregroundHint = false,
        )
        assertFalse(loc.isFullscreen)
        assertEquals(CaptureSlotSide.RIGHT, loc.slotSide)
        assertEquals(50, loc.leftPercent)
        assertEquals(CaptureCase.HALF_MAIN_SPLIT, CaptureRouter.selectCase(loc, geom))
    }

    @Test
    fun `foreground on cluster display to CLUSTER_CAST`() {
        val loc = CaptureLocationResolver.resolve(
            stack("com.waze", 1, 0, 0, 1920, 720, visible = true, withFullHome = false),
            "com.waze", navFresh = true, foregroundHint = false,
        )
        assertEquals(1, loc.displayId)
        assertEquals(CaptureCase.CLUSTER_CAST, CaptureRouter.selectCase(loc, geom))
    }

    @Test
    fun `no task plus foreground hint false to NOT_ACTIVE (case 4)`() {
        val loc = CaptureLocationResolver.resolve(
            "Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0\n" +
                "  taskId=1: com.android.launcher3/.Launcher bounds=[0,0][1920,720] visible=true\n",
            "com.waze", navFresh = true, foregroundHint = false,
        )
        assertFalse(loc.foreground)
        assertEquals(CaptureCase.NOT_ACTIVE, CaptureRouter.selectCase(loc, geom))
    }

    @Test
    fun `no task but a11y foreground hint to FULL_MAIN default (image-only path)`() {
        val loc = CaptureLocationResolver.resolve(
            "Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0\n",
            "com.waze", navFresh = true, foregroundHint = true,
        )
        assertTrue(loc.foreground)
        assertEquals(CaptureCase.FULL_MAIN, CaptureRouter.selectCase(loc, geom))
    }

    @Test
    fun `navFresh flows through to route null when gate closed`() {
        val loc = CaptureLocationResolver.resolve(
            stack("com.waze", 0, 0, 0, 1920, 720, visible = true),
            "com.waze", navFresh = false, foregroundHint = true,
        )
        assertFalse(loc.navFresh)
        assertNull(CaptureRouter.route(loc, geom))
    }
}
