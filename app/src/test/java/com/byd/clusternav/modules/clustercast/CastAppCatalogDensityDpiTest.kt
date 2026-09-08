package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.testsupport.SourceRoots

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `CastAppCatalog.clusterDensityDpi` / `setClusterDensityDpi` need a real Android `Context`
 * (SharedPreferences), and this project has no Robolectric — so, exactly like
 * `CastFavoriteProtectToggleTest`, this reads the real source instead of instantiating the class.
 *
 * This is the cast-time cluster-density store (`CastAppCatalog.setClusterDensityDpi` /
 * `clusterDensityDpi`). The old cast-v2 shell-encoding cross-check (`CastPlacementCommands.kt`'s
 * `FIT_CLUSTER_COMPOSITE` guard) was removed with the v2 stack; this now locks the storage-level gate
 * that still lives in the kept `CastAppCatalog`. What this locks down:
 *
 * (a) `setClusterDensityDpi` rejects an out-of-range value via `require(...)` BEFORE any prefs write —
 *     an invalid DPI is never even durably stored, so it can never later be read by `clusterDensityDpi`.
 * (b) The stored range is exactly `80..640`.
 * (c) `clusterDensityDpi`'s getter re-validates with the same `80..640` `takeIf`, so even a value that
 *     reached the store some other way (direct prefs edit, migration, corruption) is filtered before it
 *     is ever returned to a caller.
 */
class CastAppCatalogDensityDpiTest {

    private val catalogSource = SourceRoots.text("src/main/java/com/byd/clusternav/cast/platform/CastAppCatalog.kt")

    /** Extracts one top-level function's `{ ... }` body by brace-matching from its exact signature. */
    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "signature not found (source drifted, re-check this test): $signature" }
        val braceStart = source.indexOf('{', start)
        var depth = 0
        var i = braceStart
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(braceStart, i + 1)
                }
            }
            i++
        }
        error("unbalanced braces reading body of: $signature")
    }

    @Test
    fun `setClusterDensityDpi rejects out-of-range value via require before any prefs write`() {
        val body = functionBody(catalogSource, "fun setClusterDensityDpi(packageName: String, densityDpi: Int?) {")

        val requireIndex = body.indexOf("require(densityDpi == null || densityDpi in 80..640)")
        assertTrue(requireIndex >= 0, "range guard text changed -- re-verify this test's claims still hold")

        val editIndex = body.indexOf("prefs.edit()")
        assertTrue(editIndex >= 0, "expected setClusterDensityDpi to still write through prefs.edit()")
        assertTrue(requireIndex < editIndex, "the range guard must run strictly before the prefs write")

        val commitIndex = body.indexOf("editor.commit()")
        assertTrue(commitIndex > editIndex, "expected the edit to be committed after the guarded write")
    }

    @Test
    fun `stored and read-back density ranges use the same 80 to 640 bounds`() {
        val setterBody = functionBody(catalogSource, "fun setClusterDensityDpi(packageName: String, densityDpi: Int?) {")

        // clusterDensityDpi is expression-bodied (`fun clusterDensityDpi(...): Int? = prefs.getInt(...)`),
        // so there is no `{ }` to brace-match -- grab the signature line plus the next line instead.
        val getterSignature = "fun clusterDensityDpi(packageName: String): Int? ="
        val getterStart = catalogSource.indexOf(getterSignature)
        require(getterStart >= 0) { "clusterDensityDpi signature not found (source drifted, re-check this test)" }
        val getterLineEnd = catalogSource.indexOf('\n', catalogSource.indexOf('\n', getterStart) + 1)
        val getterBody = catalogSource.substring(getterStart, getterLineEnd)

        assertTrue(setterBody.contains("80..640"), "setter's stored range must be 80..640, found: $setterBody")
        assertTrue(getterBody.contains("80..640"), "getter's defensive filter must also be 80..640, found: $getterBody")
    }

    @Test
    fun `density literal appears as a range in both the setter and the getter`() {
        // Guards against a silent drift where only one of the two storage sites gets bumped (e.g. someone
        // widens the setter to 60..640 for a new vehicle profile but forgets the getter's defensive
        // filter, leaving a value that is stored fine but silently dropped -- not rejected -- on read).
        val ranges = listOf(
            "prefs.getInt(\"dpi:\$packageName\", 0).takeIf { it in 80..640 }",
            "require(densityDpi == null || densityDpi in 80..640)",
        )
        ranges.forEach {
            assertTrue(catalogSource.contains(it), "expected CastAppCatalog to still contain: $it")
        }
    }
}
