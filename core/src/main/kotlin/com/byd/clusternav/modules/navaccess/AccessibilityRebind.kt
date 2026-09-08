package com.byd.clusternav.modules.navaccess

/**
 * PURE (no-Android) decision + string logic for FORCE-REBINDING the accessibility service.
 *
 * The bug (measured on-car 2026-08-14, see docs/diagnostics/oncar-handoff-voicekey-2026-08-14.md §8):
 * after a reboot [NavAccessibilityService] is ENABLED (present in `settings get secure
 * enabled_accessibility_services`, `accessibility_enabled=1`) but NOT actually BOUND (absent from the
 * `dumpsys accessibility` "Bound services" section). `onServiceConnected` never runs, so `onKeyEvent`
 * (mic-hold voice key) and the screen-read booster are both dead. Writing the setting only makes it
 * *enabled*; it does not force a *bind*.
 *
 * The proven live fix is to TOGGLE the service OUT then IN, which forces the framework to rebind it:
 *   1. write enabled_accessibility_services WITHOUT ClusterNav (OEM services preserved),
 *   2. brief pause,
 *   3. write it back WITH ClusterNav appended, then `accessibility_enabled 1`.
 *
 * This object holds only the parts that can be decided without a device — which settings-write commands to
 * run, and whether the dump says we are bound — so they are covered by a JVM unit test
 * ([AccessibilityRebindTest]). The dadb I/O, pausing and never-leave-removed recovery live in
 * `com.byd.clusternav.NavConnect.doGrantAccessibility` in `:app` (which owns the Android + dadb transport).
 */
object AccessibilityRebind {
    /** ClusterNav's accessibility-service component, exactly as it appears in enabled_accessibility_services. */
    const val ACC_COMP = "com.byd.clusternav/com.byd.clusternav.modules.navaccess.NavAccessibilityService"

    private const val KEY = "enabled_accessibility_services"

    /**
     * The ordered `settings put secure ...` commands that force a REBIND via a remove -> re-add toggle.
     *
     * Returns an EMPTY list when [boundContainsClusterNav] is already true — if the service is genuinely bound
     * we must do NOTHING (no flicker). Otherwise the returned order is exactly:
     *   - `[0]` remove ClusterNav from the enabled list (every other service, incl. the OEM ones, is preserved),
     *   - `[1]` re-add ClusterNav appended after the preserved services,
     *   - `[2]` `accessibility_enabled 1`.
     *
     * The caller MUST pause between `[0]` and the rest, and MUST guarantee `[1..]` run even on failure so the
     * setting is never left in the removed state (see `NavConnect`).
     *
     * [current] is the raw value of `enabled_accessibility_services` (colon-separated, possibly `"null"`,
     * blank, or with stray/duplicate colons). It is normalised: entries are trimmed, blanks and the literal
     * `null` are dropped, and every ClusterNav entry is removed before exactly one is re-appended — so the
     * output never contains a dangling/leading/trailing/double colon, and OEM services keep their exact
     * original strings and relative order. Values are quoted so an empty remove-list is written as `""`.
     */
    fun accessibilityRebindWrites(current: String?, boundContainsClusterNav: Boolean, component: String = ACC_COMP): List<String> {
        if (boundContainsClusterNav) return emptyList()
        val entries = (current ?: "")
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "null" }
        val without = entries.filter { it != component }
        val readd = without + component
        return listOf(
            "settings put secure $KEY \"${without.joinToString(":")}\"",
            "settings put secure $KEY \"${readd.joinToString(":")}\"",
            "settings put secure accessibility_enabled 1",
        )
    }

    /**
     * Whether ClusterNav appears in the **Bound services** section of `dumpsys accessibility` — i.e. the
     * service is really running, not merely listed under "Enabled services". On BYD DiLink (Android 10) the
     * dump prints `Bound services:{ ... }` (each bound service dumped with its label and/or `ComponentInfo{
     * pkg/cls}`) and, separately, `Enabled services:{ ... }`. After a reboot ClusterNav is only in the latter.
     *
     * We scope the search to the balanced braces immediately following the "Bound services" header (nested
     * `ComponentInfo{...}` braces are handled), so a ClusterNav entry in a LATER section (Enabled) is never
     * mistaken for being bound. Matching is case-insensitive on the distinctive token `clusternav`, which
     * appears whether the dump prints the package (`com.byd.clusternav/...`) or the label (`ClusterNav ...`).
     *
     * Fails SAFE: when the dump is null/blank or the section can't be located, returns `true` (treated as
     * bound) so the caller does NOT toggle — never risk a flicker on an unreadable/unexpected dump. The real
     * on-car dump is readable by the uid=shell dadb session, so the heal path still triggers when needed.
     */
    fun isClusterNavBound(dumpsysAccessibility: String?): Boolean {
        val dump = dumpsysAccessibility
        if (dump.isNullOrBlank()) return true
        val header = dump.indexOf("Bound services", ignoreCase = true, startIndex = 0)
        if (header < 0) return true
        val open = dump.indexOf('{', header)
        if (open < 0) return true
        var depth = 0
        var close = -1
        var i = open
        while (i < dump.length) {
            when (dump[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) { close = i; break }
                }
            }
            i++
        }
        val section = if (close > open) dump.substring(open, close + 1) else dump.substring(open)
        return section.contains("clusternav", ignoreCase = true)
    }
}
