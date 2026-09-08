package com.byd.clusternav.navigation

/**
 * HERO arrow direction — the coarse LEFT / RIGHT / STRAIGHT / UTURN bucket the HERO status strip
 * (`docs/specs/ui-visual-upgrade-l2.html`, `hero_nav_icon`) needs to pick a glanceable turn glyph.
 *
 * This is a PURE, Android-free classification of the neutral [Maneuver] so it can be unit-tested off-device
 * (no `R`, no `Canvas`). The `:app` side maps each bucket to a concrete `R.drawable` arrow that already
 * exists in `res/drawable` (ic_turn_left / ic_turn_right / ic_turn_straight); [UTURN] reuses the left glyph
 * (RHT/VN convention — there is no dedicated u-turn drawable, matching [Maneuver.toHudIcon] which folds
 * UTURN onto the left-hand code 9).
 *
 * The HERO is a read-only summary, not the authoritative cluster/HUD encoder — so slight-left/sharp-left,
 * ramp/fork/keep-left, and left roundabout exits all collapse onto the plain LEFT arrow (and mirror for
 * right); non-directional maneuvers (continue, destination, tunnel, toll, waypoint…) read as STRAIGHT.
 */
enum class HeroArrow { LEFT, RIGHT, STRAIGHT, UTURN }

/**
 * Classify a neutral [Maneuver] into its coarse [HeroArrow] direction for the HERO turn glyph. Exhaustive
 * (no `else`) so any future [Maneuver] member must be classified here at compile time.
 */
fun Maneuver.toHeroArrow(): HeroArrow = when (this) {
    Maneuver.TURN_LEFT, Maneuver.SLIGHT_LEFT, Maneuver.SHARP_LEFT,
    Maneuver.RAMP_LEFT, Maneuver.FORK_LEFT, Maneuver.KEEP_LEFT,
    Maneuver.ROUNDABOUT_LEFT, Maneuver.ROUNDABOUT_LEFT_CW -> HeroArrow.LEFT

    Maneuver.TURN_RIGHT, Maneuver.SLIGHT_RIGHT, Maneuver.SHARP_RIGHT,
    Maneuver.RAMP_RIGHT, Maneuver.FORK_RIGHT, Maneuver.KEEP_RIGHT,
    Maneuver.ROUNDABOUT_RIGHT, Maneuver.ROUNDABOUT_RIGHT_CW -> HeroArrow.RIGHT

    Maneuver.UTURN, Maneuver.UTURN_RIGHT,
    Maneuver.ROUNDABOUT_UTURN, Maneuver.ROUNDABOUT_UTURN_CW -> HeroArrow.UTURN

    Maneuver.STRAIGHT, Maneuver.CONTINUE, Maneuver.MERGE, Maneuver.ROUNDABOUT, Maneuver.ROUNDABOUT_EXIT,
    Maneuver.DESTINATION, Maneuver.TUNNEL, Maneuver.SERVICE_AREA, Maneuver.TOLL, Maneuver.WAYPOINT,
    Maneuver.ROUNDABOUT_STRAIGHT, Maneuver.ROUNDABOUT_STRAIGHT_CW -> HeroArrow.STRAIGHT
}
