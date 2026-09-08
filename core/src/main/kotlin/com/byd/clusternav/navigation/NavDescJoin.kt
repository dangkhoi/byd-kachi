package com.byd.clusternav.navigation

/**
 * Pure join/dedupe of an accessibility subtree's content descriptions into the single `text` telemetry column.
 *
 * GMaps carries its guidance in `event.text`, but VietMap / Waze leave `event.text` EMPTY and render the whole
 * nav (turn dist+road, current speed+limit, ETA+dist+dest) on each view's `contentDescription` instead — e.g.
 * "Sau đó (122m)\n50m Trần Trọng Kim", "0\nkm/h\n60", "18:21\n197m\nNhà". When the :app accessibility service
 * ([com.byd.clusternav.modules.navaccess.NavAccessibilityService]) finds `event.text` empty it walks the source
 * subtree, collects those content descriptions, and joins them here into one CSV cell.
 *
 * Kept pure (no Android) so the flatten/dedupe/join is unit-tested off-car; the [android.view.accessibility]
 * node-tree walk that produces the raw strings stays Android-bound in :app. Diagnostics only — the result is a
 * telemetry `text` value and never feeds the interpolator/refine path.
 *
 * Rules: flatten any newline run inside a description to a single space (so one description stays one CSV cell),
 * drop blanks, keep the FIRST occurrence of duplicates in order, and join the distinct entries with " | ".
 */
object NavDescJoin {
    private const val SEPARATOR = " | "

    // A run of newline characters (plus any adjacent whitespace) collapses to a single space, so a multi-line
    // contentDescription like "0\nkm/h\n60" becomes "0 km/h 60".
    private val NEWLINES = Regex("\\s*[\\r\\n]+\\s*")

    /**
     * Flatten newlines within each description, drop blank entries, keep the first occurrence of duplicates in
     * order, and join the distinct entries with " | ". Empty / all-blank input → "".
     */
    fun join(descriptions: List<String>): String =
        descriptions.asSequence()
            .map { it.replace(NEWLINES, " ").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(SEPARATOR)
}
