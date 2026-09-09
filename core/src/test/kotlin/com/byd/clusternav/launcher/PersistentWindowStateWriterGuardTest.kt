package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * STAGE 0 SAFETY NET — GUARD B: makes the "single writer of persistent global windowing state" invariant
 * EXPLICIT and GREPPABLE.
 *
 * These four states OUTLIVE the process — they survive reboot / reinstall / data-clear, which is exactly why
 * an uncoordinated write from two places is dangerous:
 *   • `settings put|delete global enable_freeform_support`    — ActivityTaskManagerService reads at BOOT
 *   • `settings put|delete global force_resizable_activities` — ActivityTaskManagerService reads at BOOT
 *   • `wm size <WxH> -d <display>`                            — persisted to /data/system/display_settings.xml
 *   • `wm density <dpi> -d <display>`                         — persisted to /data/system/display_settings.xml
 *
 * The refactor GOAL (Stage 3) is ONE owner for each (a FreeformSeedPolicy / a single display-config owner).
 * TODAY there are MANY writers. This guard does NOT fake a single-writer pass — it PINS the known current set
 * of writer files so reality is documented, and ANY new writer (or a Stage-3 consolidation removing one) trips
 * the test and forces the pin (and the handoff) to be updated in lock-step.
 *
 * Detection is over comment-stripped source (a KDoc mention of a command must not count as a writer), and only
 * VALUE writes count for `wm size`/`wm density` — the `reset` and bare `-d` query forms are reads, not writers.
 *
 * // TODO(B3): tighten EACH set below to EXACTLY one writer via FreeformSeedPolicy / a single display-config owner.
 */
class PersistentWindowStateWriterGuardTest {

    /** A persistent-global mutation (put or delete). The dynamic `settings put global $key` form is caught via
     *  [freeformFlagName] co-occurring in the same file (e.g. CastShell's FREEFORM_KEYS list). */
    private val settingsMutateGlobal = Regex("""settings\s+(put|delete)\s+global""")
    private val freeformFlagName = Regex("""enable_freeform_support|force_resizable_activities""")

    /** VALUE write only: `wm size <token> -d` — NOT `reset`, NOT the bare `-d` query, NOT prose in a log string
     *  (a real write always carries a size/density token AND the `-d <display>` flag). */
    private val wmSizeWrite = Regex("""wm\s+size\s+(?!reset\b)(?!-d\b)\S+\s+-d""")
    private val wmDensityWrite = Regex("""wm\s+density\s+(?!reset\b)(?!-d\b)\S+\s+-d""")

    @Test
    fun `freeform-flag writers are the known current set (TODO B3 tighten to one)`() {
        val writers = writerFilesWhere { settingsMutateGlobal.containsMatchIn(it) && freeformFlagName.containsMatchIn(it) }
        assertEquals(
            setOf(
                // :core launcher — freeformFlagCmds constant (defined; Stage 1 will wire the launcher seed through it).
                "FreeformLaunch.kt",
                // :core cast — CastGeometryController.ensureFreeformFlags().
                "CastGeometryController.kt",
                // :app cast — CastShell.ensureFreeformSeed()/unseedFreeform() (dynamic FREEFORM_KEYS form).
                "CastShell.kt",
                // :core carexec — probe/operator catalog command template (T10 harness, not the runtime path).
                "CarExecClusterProjectionCatalog.kt",
            ),
            writers,
            "freeform-flag writer set changed. Discovered=$writers. Update the pin + Stage-1/Stage-3 handoff.",
        )
    }

    @Test
    fun `wm-size writers are the known current set (TODO B3 tighten to one)`() {
        val writers = writerFilesWhere { wmSizeWrite.containsMatchIn(it) }
        assertEquals(
            setOf(
                "CastGeometryController.kt", // :core cast — resizeFull() wm-size fallback.
                "DisplayConfigurator.kt",    // :core cast — apply() per-app-type wm size.
                "CastShell.kt",              // :app cast — forceDisplaySize().
            ),
            writers,
            "wm-size writer set changed. Discovered=$writers. Update the pin + Stage-3 handoff.",
        )
    }

    @Test
    fun `wm-density writers are the known current set (TODO B3 tighten to one)`() {
        val writers = writerFilesWhere { wmDensityWrite.containsMatchIn(it) }
        assertEquals(
            setOf(
                "CastGeometryController.kt",           // :core cast — applySavedProfile() density.
                "DisplayConfigurator.kt",              // :core cast — apply() density.
                "CastDensityControl.kt",               // :core cast — set()/setForSplit() density.
                "CastShell.kt",                        // :app cast — forceDisplaySize() density.
                "ClusterCast.kt",                      // :app cast — applyScaleLive density.
                "CarExecClusterProjectionCatalog.kt",  // :core carexec — probe/operator catalog template.
            ),
            writers,
            "wm-density writer set changed. Discovered=$writers. Update the pin + Stage-3 handoff.",
        )
    }

    /** Files across :core + :app + :car-integration whose comment-stripped source [match]es a write pattern. */
    private fun writerFilesWhere(match: (String) -> Boolean): Set<String> =
        SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
            }
        }.filter { match(KotlinSource.stripComments(it.toFile().readText())) }
            .map { it.fileName.toString() }
            .toSet()
}
