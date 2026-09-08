package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.modules.clustercast.model.CastPolicy
import com.byd.clusternav.modules.clustercast.model.TargetClass
import com.byd.clusternav.modules.clustercast.model.TargetEvidence
import com.byd.clusternav.testsupport.SourceRoots

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `CastAppCatalog.setFavorite` / `setProtected` need a real Android `Context` (SharedPreferences), and
 * this project has no Robolectric — so, exactly like `CastLifecycleTest` and `CastAppManagerWiringTest`,
 * this reads the real source instead of instantiating the class. What this locks down:
 *
 * (a) `setFavorite`'s body is prefs-only — it can never grow a call into any shell/dispatch path.
 * (b) `setProtected`'s reject branch (`current != NORMAL && current != KEEP_SESSION -> return false`)
 *     still exists and still runs before any prefs write.
 * (c)/(d)/(e) — the parts that are *executed*, not grepped, against the real `CastPolicy.classify` — prove
 *     the actual reach of that guard for a projection-hinted package (CarPlay/Android Auto, matched via
 *     `PROJECTION_HINTS`).
 *
 * The strong, always-true claim: `classify` can never return `NORMAL` while `projectionComponent = true` (see
 * the `when` in `CastPolicy.kt` -- `NORMAL` is only the final `else`, reachable only once
 * `projectionComponent` has been false), so `setProtected` can never fully strip CarPlay/Android Auto down to
 * force-stoppable, regardless of branch ordering.
 *
 * The narrower claim -- GAP FOUND while writing this test, now FIXED (2026-07-28, castpolicy-branch-order
 * task): `classify` used to return `KEEP_SESSION` for a projection-hinted package when
 * `connectedPhoneSession = false` (confirmed absent) and `userProtected = true` already (reachable via
 * legacy migration, which writes the "protected" prefs key directly, bypassing `classify` entirely) --
 * because the `userProtected` branch was checked BEFORE the unconditional
 * `projectionComponent -> UNKNOWN_PROTECTED` fallback. `setProtected`'s guard treats `KEEP_SESSION` as
 * "settable", so a `setProtected(pkg, false)` call was NOT rejected in that one state. `CastPolicy.classify`
 * now checks the unconditional `projectionComponent -> UNKNOWN_PROTECTED` fallback BEFORE `userProtected`,
 * so this state now correctly resolves to `UNKNOWN_PROTECTED` and `setProtected` rejects it. See the
 * regression test below, and `CastPolicyTest.a confirmed-absent projection session fails closed even when
 * user-protected` at the policy level.
 */
class CastFavoriteProtectToggleTest {

    private val catalogSource = SourceRoots.text("src/main/java/com/byd/clusternav/cast/platform/CastAppCatalog.kt")

    /** Extracts one top-level function's `{ ... }` body by brace-matching from its exact signature. */
    private fun functionBody(signature: String): String {
        val start = catalogSource.indexOf(signature)
        require(start >= 0) { "signature not found (source drifted, re-check this test): $signature" }
        val braceStart = catalogSource.indexOf('{', start)
        var depth = 0
        var i = braceStart
        while (i < catalogSource.length) {
            when (catalogSource[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return catalogSource.substring(braceStart, i + 1)
                }
            }
            i++
        }
        error("unbalanced braces reading body of: $signature")
    }

    @Test
    fun `setFavorite body only ever mutates prefs, never a shell or cast dispatch path`() {
        val body = functionBody("fun setFavorite(packageName: String, enabled: Boolean) {")

        // Everything the body is currently made of.
        listOf("PACKAGE.matches", "favorites()", "toMutableSet", "prefs.edit()", "commit")
            .forEach { assertTrue(body.contains(it), "expected favorite toggle to still use: $it") }

        // Nothing that could reach the vehicle -- any of these appearing means a shell command could
        // now be emitted by a favorite toggle, which must never happen.
        listOf(
            "CastFacade", "runManualIntent", "ShellCommandEncoder", "CommandKind", "Transport", "dadb",
            "shell(", "CastCoordinator", "coordinator", "dispatch", "executeCast", "queueLatestTarget",
            "CastPlacementCommands", "DeviceShell",
        ).forEach { assertFalse(body.contains(it), "favorite toggle must never reference: $it") }
    }

    @Test
    fun `setProtected still rejects via NORMAL or KEEP_SESSION guard before any prefs write`() {
        val body = functionBody("fun setProtected(packageName: String, enabled: Boolean): Boolean {")
        val guardText = "if (current != TargetClass.NORMAL && current != TargetClass.KEEP_SESSION)"
        assertTrue(body.contains(guardText), "guard condition text changed -- re-verify this test's claims still hold")

        val guardIndex = body.indexOf(guardText)
        val rejectIndex = body.indexOf("return false", guardIndex)
        val prefsWriteIndex = body.indexOf("prefs.edit()")
        assertTrue(rejectIndex in guardIndex..prefsWriteIndex, "the reject branch must run strictly before the prefs write")
    }

    @Test
    fun `a projection-hinted target can never classify NORMAL, so setProtected can never fully unprotect it`() {
        // This is the one claim that holds regardless of CastPolicy.classify's internal branch order:
        // TargetClass.NORMAL is reachable only through the final `else` in its `when`, which requires
        // `evidence.projectionComponent` to be false at the unconditional `projectionComponent -> ...`
        // branch above it. So for every (session, userProtected) combination, a projection-hinted package
        // (CarPlay/Android Auto, matched via PROJECTION_HINTS) can never classify NORMAL -- setProtected's
        // guard (`current != NORMAL && current != KEEP_SESSION -> reject`) can therefore never be satisfied
        // by "fully unprotected", closing off the worst-case downgrade (force-stoppable). Whether the
        // narrower KEEP_SESSION-vs-UNKNOWN_PROTECTED distinction also holds is a separate, weaker claim --
        // see the two tests below, one of which currently does NOT hold.
        listOf(true, false, null).forEach { session ->
            listOf(true, false).forEach { userProtected ->
                val evidence = TargetEvidence(true, session, userProtected)
                assertFalse(
                    CastPolicy.classify(evidence) == TargetClass.NORMAL,
                    "projection-hinted evidence (session=$session, userProtected=$userProtected) classified NORMAL " +
                        "-- setProtected's guard would then accept this target as fully unprotected",
                )
            }
        }
    }

    @Test
    fun `given tonight's CastPolicy change, the realistic host reading is exactly PROJECTION_SINK`() {
        // Mirrors CastAppCatalog.evidence(pkg, ...) for the actual CarPlay/Android Auto host packages:
        // PROJECTION_HINTS name-matches them permanently (projectionComponent = true), and per CastPolicy.kt's
        // own documentation, ProjectionSessionEvidenceParser can never read the host's OWN session state, so
        // connectedPhoneSession is always null in production -- never a confirmed false. userProtected cannot
        // change the outcome here either, because branch 2 (projectionComponent && session != false)
        // short-circuits before userProtected is ever consulted.
        listOf(true, null).forEach { session ->
            listOf(true, false).forEach { userProtected ->
                val evidence = TargetEvidence(true, session, userProtected)
                assertEquals(
                    TargetClass.PROJECTION_SINK,
                    CastPolicy.classify(evidence),
                    "session=$session, userProtected=$userProtected",
                )
            }
        }
    }

    @Test
    fun `FIXED -- a confirmed-false session with a pre-existing protected flag now classifies UNKNOWN_PROTECTED, not KEEP_SESSION`() {
        // Locks the fix for the gap found while writing this test (verified by running CastPolicy.classify
        // itself, not by inspection): connectedPhoneSession = false (confirmed absent) together with
        // userProtected = true -- reachable via legacy migration, which writes the "protected" prefs key
        // directly from V1's `keepSession` set, bypassing classify() entirely, so a CarPlay/Android Auto
        // package can arrive here with userProtected = true without ever having passed setProtected's own
        // guard -- used to classify KEEP_SESSION, because CastPolicy.kt's `when` checked
        // `evidence.userProtected` BEFORE the unconditional `evidence.projectionComponent -> UNKNOWN_PROTECTED`
        // fallback beneath it.
        //
        // CastPolicy.classify now checks the unconditional `projectionComponent -> UNKNOWN_PROTECTED`
        // fallback BEFORE `userProtected`, so this state resolves to UNKNOWN_PROTECTED and setProtected's
        // guard (`current != NORMAL && current != KEEP_SESSION -> reject`) correctly rejects a
        // `setProtected(pkg, false)` call in this state instead of silently accepting it.
        val evidence = TargetEvidence(projectionComponent = true, connectedPhoneSession = false, userProtected = true)
        assertEquals(TargetClass.UNKNOWN_PROTECTED, CastPolicy.classify(evidence))
    }
}
