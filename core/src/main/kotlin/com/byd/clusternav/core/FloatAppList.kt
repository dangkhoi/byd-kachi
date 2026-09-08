package com.byd.clusternav.core

/**
 * Pure CSV merge for the BYD IVI global float/overlay allow-list (`settings global byd_float_app_list`).
 *
 * The BYD IVI keeps a comma-separated list of packages permitted to draw a floating / overlay window; a
 * package absent from it is refused with the *"Hệ thống IVI không hỗ trợ hoạt động này"* toast. Two
 * on-device callers append to that list over the dadb uid-shell and must NOT clobber packages the OEM (or
 * another app) already granted:
 *  • [com.byd.clusternav.modules.voicekey.AssistantLauncher] — Google + Gemini + self, for the assistant.
 *  • [com.byd.clusternav.VietMapAutostart] — the modded VietMap, for its cluster bubble.
 *
 * The split/trim/filter-null/append/distinct/join is the same in both, so it lives here once — pure (no
 * Android) and unit-tested off-car, per the :core isolation boundary ([CoreBoundary]).
 */
object FloatAppList {

    /**
     * Merge [add] into the existing comma-separated [current] list, preserving OEM / other entries.
     *
     * Reproduces the proven AssistantLauncher recipe EXACTLY: split [current] on commas, trim each entry,
     * drop blanks and the literal `"null"` (what `settings get` prints for an unset key), then append [add]
     * verbatim, de-duplicate keeping the FIRST occurrence, and re-join with commas. Order = existing-first
     * then added. [add] is appended as-is (callers pass clean package literals); de-dup makes a package that
     * is already present a no-op, so re-running is idempotent.
     */
    fun merge(current: String, add: List<String>): String =
        (current.split(',').map { it.trim() }.filter { it.isNotEmpty() && it != "null" } + add)
            .distinct()
            .joinToString(",")
}
