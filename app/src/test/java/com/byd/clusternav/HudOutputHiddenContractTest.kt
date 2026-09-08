package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R1 (#6, docs/specs/cast-nav-ux-release-v104.html): the independent nav→HUD output toggle is
 * hidden from the UI and force-disabled, while the nav→cluster pipeline stays intact.
 *
 * This project has no Robolectric, so — exactly like [SpeedSignSourceLifecycleTest] and
 * [com.byd.clusternav.navigation.NavigationOutputIsolationTest] — the contract is pinned by scanning
 * the real source of [MainActivity] and both `activity_main.xml` layouts.
 */
class HudOutputHiddenContractTest {
    private val main = SourceRoots.text("src/main/java/com/byd/clusternav/MainActivity.kt")
    private val layoutPortrait = SourceRoots.text("src/main/res/layout/activity_main.xml")
    private val layoutWide = SourceRoots.text("src/main/res/layout-w960dp/activity_main.xml")

    @Test
    fun `MainActivity does not wire the HUD checkbox`() {
        assertFalse(main.contains("hudEnabled"), "cb_hud field/wiring must be gone — no HUD toggle")
        assertFalse(
            main.contains("R.id.cb_hud"),
            "MainActivity must not bind cb_hud anymore (view stays in XML as gone)",
        )
        assertFalse(
            main.contains("Prefs.setHud(this, enabled)"),
            "HUD preference must never be written from a toggle — only force-disabled to false",
        )
    }

    @Test
    fun `MainActivity force-disables HUD output exactly once and keeps the enum`() {
        assertTrue(
            main.contains("Prefs.setHud(this, false)"),
            "HUD preference must be forced off so connect() re-reads it disabled",
        )
        assertTrue(
            main.contains("NavRepository.setOutputEnabled(this, NavigationOutputTarget.HUD, false)"),
            "HUD navigation output must be force-disabled",
        )
        assertTrue(
            main.contains("speedSign.onOutputEnabled(SpeedSignOutput.HUD, false)"),
            "HUD speed-sign output must be force-disabled",
        )
        // Enum value must NOT be deleted — the isolation contract depends on it.
        assertTrue(main.contains("NavigationOutputTarget.HUD"), "NavigationOutputTarget.HUD must remain referenced")
    }

    @Test
    fun `cluster-lane output follows the master switch (cb_lane removed)`() {
        // Owner 2026-08-11: the redundant cb_lane checkbox is removed; cluster-lane output now follows
        // the Navigation+HUD master switch (always on when nav is on).
        assertFalse(main.contains("R.id.cb_lane"), "MainActivity must not bind cb_lane anymore")
        assertFalse(main.contains("laneEnabled"), "the lane checkbox field/listener must be gone")
        assertTrue(
            main.contains("NavRepository.setOutputEnabled(this, NavigationOutputTarget.CLUSTER_LANE, true)"),
            "cluster-lane output is enabled with the master switch (nav→cluster follows master)",
        )
    }

    @Test
    fun `both layouts hide the HUD block but keep the ids`() {
        listOf("portrait" to layoutPortrait, "wide" to layoutWide).forEach { (name, xml) ->
            assertTrue(xml.contains("@+id/cb_hud"), "$name: cb_hud id must remain so findViewById is safe")
            assertTrue(xml.contains("@+id/txt_hud_status"), "$name: txt_hud_status id must remain")
            assertTrue(
                elementWithId(xml, "cb_hud").contains("android:visibility=\"gone\""),
                "$name: cb_hud must be visibility=gone",
            )
            assertTrue(
                elementWithId(xml, "txt_hud_status").contains("android:visibility=\"gone\""),
                "$name: txt_hud_status must be visibility=gone",
            )
            // The redundant cluster-lane checkbox is removed entirely (owner 2026-08-11).
            assertFalse(xml.contains("@+id/cb_lane"), "$name: cb_lane must be removed (lane follows master)")
        }
    }

    /** The single XML element (`<... />`) that declares `@+id/<id>`, attribute-order independent. */
    private fun elementWithId(xml: String, id: String): String {
        val idIdx = xml.indexOf("@+id/$id\"")
        require(idIdx >= 0) { "missing @+id/$id" }
        val start = xml.lastIndexOf('<', idIdx)
        val end = xml.indexOf("/>", idIdx)
        require(start in 0 until end) { "unterminated element for @+id/$id" }
        return xml.substring(start, end + 2)
    }
}
