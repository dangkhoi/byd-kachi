package com.byd.clusternav

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WIRING contract for the Level-2 cockpit UI (task `ui-visual-upgrade-l2`, ref
 * `docs/specs/ui-visual-upgrade-l2.html`): the HERO live-status strip, the SEAT DIAGRAM custom view (which
 * REPLACES the old per-seat radio rows) and the PM2.5 GAUGE. Runtime behaviour needs Android
 * (Activity/Canvas/HAL), so — like [VoiceKeyStatusWiringContractTest] and [LayoutVariantIdParityTest] — this
 * locks the boundary by reading source text across both ends.
 *
 * It is the regression net for a BEHAVIOUR-CHANGING refactor (CLAUDE.md §10 / P5.3): the seat radios were
 * swapped for a chữ-ký Canvas view, and the feature (per-seat level persistence + apply) MUST stay intact.
 *
 * Locks:
 *  • SEAT — the radio ids (`seat0_group`…`seat3_l2`, `seatN_label`, `seat_comfort_grid`, `seat_rear_row`) are
 *    GONE from both layout variants and `seat_diagram` is present in both; `setupSeatComfortControls` seeds the
 *    diagram from [SeatComfortApplier.detectSeatCount] + [Prefs.seatComfortMode] + [Prefs.seatComfortLevel] and,
 *    on `onSeatLevelChanged`, persists [Prefs.setSeatComfortLevel] + calls [SeatComfortApplier.applySeat] (v1.34:
 *    a per-seat write that sends OFF too — the bulk applyNow skips OFF; SAME persistence the radios had); the mode
 *    control (seg_seat_mode SegmentedControlView, replacing the cool/heat RadioGroup) persists
 *    [Prefs.setSeatComfortMode] + [SeatComfortApplier.applyNow] (bulk re-apply of all non-OFF seats).
 *  • PM2.5 — `pm25_gauge` present in both variants; `refreshPm25Level` reads via [Pm25FilterApplier.readLevel]
 *    on a background thread and updates BOTH `txt_pm25_level` and `pm25_gauge.setLevel`.
 *  • HERO — the six `hero_*` ids present in both variants; `updateHeroStrip` is called from `refresh()` and is
 *    READ-ONLY from existing accessors (nav status text, `SimpleCastRuntime` prefs `castEnabled`,
 *    [NavAccessibilitySource.connected] + [Prefs.voiceKeyEnabled]) — it writes NO prefs / dispatches nothing.
 *  • KEEP — the retained seat ids (mode group + switch + title + hint) survive in both variants.
 *
 * ── LÀM-ĐỎ (P5.3): xoá `SeatComfortApplier.applySeat` khỏi callback ghế (hoặc `applyNow` khỏi callback đổi chế
 * độ), hoặc bỏ `Prefs.setSeatComfortLevel`, hoặc trả lại `seat0_group`, hoặc bỏ `pm25_gauge.setLevel`, hoặc
 * thêm ghi-pref vào `updateHeroStrip` ⇒ test ĐỎ.
 */
class L2CockpitUiWiringContractTest {

    private val mainActivity: String by lazy {
        KotlinSource.stripComments(SourceRoots.text("src/main/java/com/byd/clusternav/MainActivity.kt"))
    }
    private val layoutNarrow: String by lazy { SourceRoots.text("src/main/res/layout/activity_main.xml") }
    private val layoutWide: String by lazy { SourceRoots.text("src/main/res/layout-w960dp/activity_main.xml") }
    private val variants get() = listOf("narrow" to layoutNarrow, "wide" to layoutWide)

    /** Slice from a top-level method signature to the next top-level member (mirrors VoiceKeyStatusWiringContractTest). */
    private fun body(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "missing $signature" }
        val after = start + signature.length
        val next = listOf("\n    fun ", "\n    private fun ", "\n    override fun ", "\n    companion object", "\n}")
            .mapNotNull { source.indexOf(it, after).takeIf { i -> i >= 0 } }
            .minOrNull() ?: source.length
        return source.substring(start, next)
    }

    // ── SEAT: radios removed, diagram present, feature intact ─────────────────
    @Test
    fun `the old seat radio ids are gone from both layout variants`() {
        val removed = listOf(
            "seat0_group", "seat0_off", "seat0_l1", "seat0_l2",
            "seat1_group", "seat2_group", "seat3_group", "seat3_l2",
            "seat0_label", "seat3_label", "seat_comfort_grid", "seat_rear_row",
        )
        for ((name, xml) in variants) {
            for (id in removed) {
                assertFalse(xml.contains("@+id/$id\""), "$name: removed seat radio id @+id/$id must be gone")
            }
        }
    }

    @Test
    fun `both variants declare seat_diagram and keep the mode control + switch + title + hint`() {
        val kept = listOf(
            "seat_diagram", "seg_seat_mode",
            "switch_seat_comfort_enabled", "txt_seat_comfort_title", "txt_seat_comfort_hint",
        )
        for ((name, xml) in variants) {
            for (id in kept) assertTrue(xml.contains("@+id/$id\""), "$name: must declare @+id/$id")
            assertTrue(
                xml.contains("com.byd.clusternav.comfort.SeatDiagramView"),
                "$name: seat_diagram must be the SeatDiagramView custom view",
            )
            assertTrue(
                xml.contains("com.byd.clusternav.ui.SegmentedControlView"),
                "$name: the cool/heat mode control is now a SegmentedControlView (seg_seat_mode)",
            )
        }
    }

    @Test
    fun `setupSeatComfortControls seeds the diagram and persists + applies per-seat level (feature intact)`() {
        val b = body(mainActivity, "private fun setupSeatComfortControls()")
        assertTrue(b.contains("R.id.seat_diagram"), "wires the seat_diagram view")
        assertTrue(
            b.contains("setSeatCount(SeatComfortApplier.detectSeatCount(this@MainActivity))"),
            "2/4 seats by model via detectSeatCount → setSeatCount",
        )
        assertTrue(b.contains("setMode("), "seeds cool/heat mode onto the diagram")
        assertTrue(
            b.contains("setLevel(i, Prefs.seatComfortLevel(this@MainActivity, i))"),
            "seeds each seat level from Prefs.seatComfortLevel",
        )
        assertTrue(b.contains("onSeatLevelChanged ="), "installs the seat-tap callback")
        assertTrue(
            b.contains("Prefs.setSeatComfortLevel(this@MainActivity, seat, level)"),
            "on tap: persists the per-seat level to Prefs.seatComfortLevel (SAME persistence as the old radios)",
        )
        assertTrue(
            b.contains("SeatComfortApplier.applySeat(this@MainActivity, seat, level)"),
            "on tap: applies the TAPPED seat to HAL now via applySeat — v1.34 sends OFF too (the bulk applyNow " +
                "skips OFF); no-op when the master switch is off — feature + off-fix intact",
        )
    }

    @Test
    fun `the mode control still persists and applies`() {
        val b = body(mainActivity, "private fun setupSeatComfortControls()")
        assertTrue(b.contains("R.id.seg_seat_mode"), "keeps the cool/heat mode control (SegmentedControlView)")
        assertTrue(b.contains("Prefs.setSeatComfortMode(this@MainActivity, mode)"), "mode persists to Prefs")
        // The mode change also re-tints the diagram and applies now.
        assertTrue(b.contains("diagram?.setMode("), "mode change recolours the diagram")
        // Mode change (cool↔heat) re-applies ALL non-OFF seats via the bulk applyNow (the seat TAP uses applySeat).
        assertTrue(
            b.contains("SeatComfortApplier.applyNow(this@MainActivity)"),
            "on mode change: re-applies all non-OFF seats to HAL via the bulk applyNow",
        )
    }

    @Test
    fun `refreshSeatComfortPanel enables the diagram and mode by the master switch, not the old radio arrays`() {
        val b = body(mainActivity, "private fun refreshSeatComfortPanel()")
        assertTrue(b.contains("R.id.seat_diagram"), "gates the diagram enabled-state by the master switch")
        assertTrue(b.contains("Prefs.seatComfortEnabled(this)"), "reads the master switch pref")
        assertFalse(b.contains("seatGroupIds"), "no dangling reference to the removed radio-id arrays")
    }

    // ── PM2.5 gauge ───────────────────────────────────────────────────────────
    @Test
    fun `both variants declare the pm25 gauge and keep the level text`() {
        for ((name, xml) in variants) {
            assertTrue(xml.contains("@+id/pm25_gauge\""), "$name: pm25_gauge id present")
            assertTrue(xml.contains("com.byd.clusternav.comfort.Pm25GaugeView"), "$name: gauge is the Pm25GaugeView")
            assertTrue(xml.contains("@+id/txt_pm25_level\""), "$name: txt_pm25_level kept")
            assertTrue(xml.contains("@+id/btn_pm25_clean_now\""), "$name: 'Lọc ngay' button present in both variants")
        }
    }

    @Test
    fun `setupPm25FilterControls wires the clean-now button to a manual quick-clean`() {
        val b = body(mainActivity, "private fun setupPm25FilterControls()")
        assertTrue(b.contains("R.id.btn_pm25_clean_now"), "looks up the 'Lọc ngay' button")
        assertTrue(b.contains("Pm25FilterApplier.cleanNow(this@MainActivity)"), "button fires an on-demand quick-clean")
    }

    @Test
    fun `refreshPm25Level updates the gauge from the same background read`() {
        val b = body(mainActivity, "private fun refreshPm25Level()")
        assertTrue(b.contains("Pm25FilterApplier.readLevel(this)"), "reads the level off the main thread")
        assertTrue(b.contains("R.id.pm25_gauge"), "looks up the gauge")
        assertTrue(b.contains("gauge?.setLevel(level)"), "drives the gauge with the read level (INVALID → empty/—)")
        assertTrue(b.contains("R.id.txt_pm25_level"), "keeps the secondary text readout")
    }

    // ── HERO strip: present + read-only ───────────────────────────────────────
    @Test
    fun `both variants declare the six hero ids`() {
        val hero = listOf("hero_nav_icon", "hero_dist", "hero_road", "hero_speed", "hero_cast", "hero_vk")
        for ((name, xml) in variants) {
            for (id in hero) assertTrue(xml.contains("@+id/$id\""), "$name: hero id @+id/$id present")
        }
    }

    @Test
    fun `refresh calls updateHeroStrip and the hero is read-only from existing accessors`() {
        assertTrue(
            body(mainActivity, "private fun refresh()").contains("updateHeroStrip(sourceText)"),
            "refresh() feeds the hero the SAME nav status text as the main status line",
        )
        val b = body(mainActivity, "private fun updateHeroStrip(navStatusText: String)")
        assertTrue(b.contains("R.id.hero_road"), "wires hero_road (nav status)")
        assertTrue(b.contains("R.id.hero_cast"), "wires hero_cast (cast state)")
        assertTrue(b.contains("R.id.hero_vk"), "wires hero_vk (voice-key)")
        assertTrue(b.contains("castEnabled()"), "cast chip reads SimpleCastRuntime prefs castEnabled (read-only)")
        assertTrue(
            b.contains("com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected"),
            "voice-key chip reads the same bound flag as refreshVoiceKeyStatus",
        )
        assertTrue(b.contains("Prefs.voiceKeyEnabled(this)"), "voice-key chip reads the enabled pref")
        // READ-ONLY: the hero must not write prefs or dispatch cast intents.
        assertFalse(b.contains("Prefs.set"), "hero must not WRITE any pref (read-only)")
        assertFalse(b.contains("dispatch("), "hero must not dispatch cast intents (read-only)")
    }

    @Test
    fun `hero_nav_icon and hero_road are wired to NavRepository state and the neutral Maneuver`() {
        val b = body(mainActivity, "private fun updateHeroStrip(navStatusText: String)")
        assertTrue(b.contains("NavRepository.state"), "hero reads the published nav snapshot")
        // T1(b): hero_nav_icon ← maneuver arrow (was a static placeholder before).
        assertTrue(b.contains("R.id.hero_nav_icon"), "wires the hero maneuver arrow image")
        assertTrue(b.contains(".maneuver"), "reads the neutral maneuver from state")
        assertTrue(b.contains("heroArrowRes("), "maps the maneuver to an arrow drawable")
        // T1(a): hero_road ← street/road name, not the source-status string.
        assertTrue(b.contains("R.id.hero_road"), "wires hero_road")
        assertTrue(b.contains(".road"), "hero_road reads the street/road name from state")

        // heroArrowRes routes through the PURE core classifier to EXISTING turn drawables.
        val hr = body(mainActivity, "private fun heroArrowRes(m: Maneuver): Int")
        assertTrue(hr.contains("toHeroArrow()"), "heroArrowRes routes through the pure toHeroArrow classifier")
        assertTrue(hr.contains("R.drawable.ic_turn_"), "maps to existing ic_turn_* arrow drawables")
    }
}
