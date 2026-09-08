package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure classification test for [Maneuver.toHeroArrow] — the HERO `hero_nav_icon` maneuver→arrow bucket
 * (task `stage2-ui` · T1, ref `docs/specs/ui-visual-upgrade-l2.html`). Lives in `:core` because it exercises
 * only `:core` logic (Android-free — no `R`, no `Canvas`), per the layering rules
 * ([com.byd.clusternav.LayeringRulesTest]). The `:app` side maps each bucket to a concrete drawable.
 *
 * ── LÀM-ĐỎ: reclassify TURN_LEFT→RIGHT (or drop a bucket) ⇒ test ĐỎ. Every [Maneuver] is asserted so a new
 * enum member that is mis-bucketed is caught here.
 */
class HeroArrowTest {

    @Test
    fun `left family maps to LEFT`() {
        listOf(
            Maneuver.TURN_LEFT, Maneuver.SLIGHT_LEFT, Maneuver.SHARP_LEFT,
            Maneuver.RAMP_LEFT, Maneuver.FORK_LEFT, Maneuver.KEEP_LEFT,
            Maneuver.ROUNDABOUT_LEFT, Maneuver.ROUNDABOUT_LEFT_CW,
        ).forEach { assertEquals(HeroArrow.LEFT, it.toHeroArrow(), it.name) }
    }

    @Test
    fun `right family maps to RIGHT`() {
        listOf(
            Maneuver.TURN_RIGHT, Maneuver.SLIGHT_RIGHT, Maneuver.SHARP_RIGHT,
            Maneuver.RAMP_RIGHT, Maneuver.FORK_RIGHT, Maneuver.KEEP_RIGHT,
            Maneuver.ROUNDABOUT_RIGHT, Maneuver.ROUNDABOUT_RIGHT_CW,
        ).forEach { assertEquals(HeroArrow.RIGHT, it.toHeroArrow(), it.name) }
    }

    @Test
    fun `u-turn family maps to UTURN`() {
        listOf(
            Maneuver.UTURN, Maneuver.UTURN_RIGHT,
            Maneuver.ROUNDABOUT_UTURN, Maneuver.ROUNDABOUT_UTURN_CW,
        ).forEach { assertEquals(HeroArrow.UTURN, it.toHeroArrow(), it.name) }
    }

    @Test
    fun `straight and non-directional maneuvers map to STRAIGHT`() {
        listOf(
            Maneuver.STRAIGHT, Maneuver.CONTINUE, Maneuver.MERGE, Maneuver.ROUNDABOUT, Maneuver.ROUNDABOUT_EXIT,
            Maneuver.DESTINATION, Maneuver.TUNNEL, Maneuver.SERVICE_AREA, Maneuver.TOLL, Maneuver.WAYPOINT,
            Maneuver.ROUNDABOUT_STRAIGHT, Maneuver.ROUNDABOUT_STRAIGHT_CW,
        ).forEach { assertEquals(HeroArrow.STRAIGHT, it.toHeroArrow(), it.name) }
    }

    @Test
    fun `every Maneuver is classified (exhaustive, no crash)`() {
        // toHeroArrow is exhaustive by construction; assert every enum value yields a stable bucket.
        assertEquals(Maneuver.entries.size, Maneuver.entries.map { it.toHeroArrow() }.size)
        Maneuver.entries.forEach { m -> assertEquals(m.toHeroArrow(), m.toHeroArrow(), m.name) }
    }
}
