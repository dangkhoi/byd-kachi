package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * STAGE 0 SAFETY NET · STAGE 3 TIGHTENED — GUARD B: makes the "single writer of persistent global windowing
 * state" invariant EXPLICIT and GREPPABLE.
 *
 * These four states OUTLIVE the process — they survive reboot / reinstall / data-clear, which is exactly why
 * an uncoordinated write from two places is dangerous (the documented cluster-BRICK vector):
 *   • `settings put|delete global enable_freeform_support`    — ActivityTaskManagerService reads at BOOT
 *   • `settings put|delete global force_resizable_activities` — ActivityTaskManagerService reads at BOOT
 *   • `wm size <WxH> -d <display>`                            — persisted to /data/system/display_settings.xml
 *   • `wm density <dpi> -d <display>`                         — persisted to /data/system/display_settings.xml
 *
 * ── STAGE 3 OUTCOME ───────────────────────────────────────────────────────────────────────────────────────────
 * The SINGLE SANCTIONED WRITER is now [com.byd.clusternav.system.FreeformSeedPolicy] (pure :core policy + :app
 * [com.byd.clusternav.system.FreeformSeedStore] executor). It owns the byte-identical command strings + the
 * commit-before-mutate / FF_USER_REMOVED-terminal marker discipline.
 *
 *  • **LAUNCHER side**: enforced to have NO direct writer at all — every launcher file must route through
 *    FreeformSeedPolicy (see [launcher writes persistent window-state ONLY via FreeformSeedPolicy]). The former
 *    `FreeformLaunch.freeformFlagCmds` constant was removed (moved into the policy).
 *  • **CAST side (freeform SEED)**: `CastShell.ensureFreeformSeed`/`unseedFreeform` were CONSOLIDATED — they now
 *    delegate to FreeformSeedPolicy, so `CastShell.kt` is no longer a freeform-flag writer.
 *  • **CAST side (DEFERRED, documented)**: the proven cluster-GEOMETRY writers (`wm size`/`wm density`) and the
 *    live `SimpleCastCoordinator` marker-less seed (`CastGeometryController.ensureFreeformFlags`) are NOT
 *    consolidated this session — they touch proven cast logic that CANNOT be E2E-verified with the emulator dadb
 *    loopback down, and consolidating the live seed would CHANGE its behaviour (add marker discipline). They stay
 *    pinned below as an explicit allow-list, each with a `// TODO(on-car): consolidate into FreeformSeedPolicy
 *    after cluster E2E verify`. This guard PINS reality: any NEW writer, or a later consolidation removing one,
 *    trips the test and forces the pin + handoff to update in lock-step.
 *
 * Detection is over comment-stripped source (a KDoc mention of a command must not count as a writer), and only
 * VALUE writes count for `wm size`/`wm density` — the `reset` and bare `-d` query forms are reads, not writers.
 */
class PersistentWindowStateWriterGuardTest {

    /** A persistent-global mutation (put or delete). The dynamic `settings put global $key` form is caught via
     *  [freeformFlagName] co-occurring in the same file. */
    private val settingsMutateGlobal = Regex("""settings\s+(put|delete)\s+global""")
    private val freeformFlagName = Regex("""enable_freeform_support|force_resizable_activities""")

    /** VALUE write only: `wm size <token> -d` — NOT `reset`, NOT the bare `-d` query, NOT prose in a log string
     *  (a real write always carries a size/density token AND the `-d <display>` flag). */
    private val wmSizeWrite = Regex("""wm\s+size\s+(?!reset\b)(?!-d\b)\S+\s+-d""")
    private val wmDensityWrite = Regex("""wm\s+density\s+(?!reset\b)(?!-d\b)\S+\s+-d""")

    /** True iff [src] (comment-stripped) writes a freeform boot flag. */
    private fun writesFreeformFlag(src: String) =
        settingsMutateGlobal.containsMatchIn(src) && freeformFlagName.containsMatchIn(src)

    /** True iff [src] (comment-stripped) writes ANY of the four persistent window states. */
    private fun writesAnyPersistentState(src: String) =
        writesFreeformFlag(src) || wmSizeWrite.containsMatchIn(src) || wmDensityWrite.containsMatchIn(src)

    @Test
    fun `freeform-flag writers are exactly the sanctioned policy plus documented deferred cast sites`() {
        val writers = writerFilesWhere { writesFreeformFlag(it) }
        assertEquals(
            setOf(
                // ✅ SANCTIONED single writer — :core system. SEED_CMDS / UNSEED_CMDS.
                "FreeformSeedPolicy.kt",
                // ⏳ DEFERRED — :core cast, LIVE SimpleCastCoordinator seed. Marker-LESS by design; routing it
                //    through the policy would ADD marker discipline = behaviour change, unverifiable this session.
                //    TODO(on-car): consolidate into FreeformSeedPolicy after cluster E2E verify.
                "CastGeometryController.kt",
                // ⏳ DEFERRED — :core carexec, T10 probe/operator catalog TEMPLATE (not a runtime writer).
                //    TODO(on-car): fold restore.globals template onto FreeformSeedPolicy after cluster E2E verify.
                "CarExecClusterProjectionCatalog.kt",
            ),
            writers,
            "freeform-flag writer set changed. Discovered=$writers. " +
                "The launcher (FreeformLaunch) + CastShell were consolidated into FreeformSeedPolicy; " +
                "update the pin + Stage-3 handoff if a writer was added/removed.",
        )
    }

    @Test
    fun `wm-size writers are the sanctioned policy plus documented deferred cast sites`() {
        val writers = writerFilesWhere { wmSizeWrite.containsMatchIn(it) }
        assertEquals(
            setOf(
                "FreeformSeedPolicy.kt",      // ✅ SANCTIONED — :core system, sizeCmd() (the launcher/consolidation API).
                "CastGeometryController.kt",  // ⏳ DEFERRED — :core cast, resizeFull() wm-size fallback. TODO(on-car).
                "DisplayConfigurator.kt",     // ⏳ DEFERRED — :core cast, apply() per-app-type wm size. TODO(on-car).
                "CastShell.kt",               // ⏳ DEFERRED — :app cast, forceDisplaySize(). TODO(on-car).
            ),
            writers,
            "wm-size writer set changed. Discovered=$writers. Cluster-geometry writes are DEFERRED (proven, " +
                "unverifiable off-car); update the pin + Stage-3 handoff.",
        )
    }

    @Test
    fun `wm-density writers are the sanctioned policy plus documented deferred cast sites`() {
        val writers = writerFilesWhere { wmDensityWrite.containsMatchIn(it) }
        assertEquals(
            setOf(
                "FreeformSeedPolicy.kt",               // ✅ SANCTIONED — :core system, densityCmd() (the API).
                "CastGeometryController.kt",           // ⏳ DEFERRED — :core cast, applySavedProfile() density. TODO(on-car).
                "DisplayConfigurator.kt",              // ⏳ DEFERRED — :core cast, apply() density. TODO(on-car).
                "CastDensityControl.kt",               // ⏳ DEFERRED — :core cast, set()/setForSplit() density. TODO(on-car).
                "CastShell.kt",                        // ⏳ DEFERRED — :app cast, forceDisplaySize() density. TODO(on-car).
                "ClusterCast.kt",                      // ⏳ DEFERRED — :app cast, setDensityIfNeeded/applyScaleLive. TODO(on-car).
                "CarExecClusterProjectionCatalog.kt",  // ⏳ DEFERRED — :core carexec, probe/operator catalog template. TODO(on-car).
            ),
            writers,
            "wm-density writer set changed. Discovered=$writers. Cluster-geometry writes are DEFERRED (proven, " +
                "unverifiable off-car); update the pin + Stage-3 handoff.",
        )
    }

    /**
     * STAGE 3 TIGHTENING — the LAUNCHER may write these ONLY via [com.byd.clusternav.system.FreeformSeedPolicy]
     * (which lives in the `/system/` package, NOT `/launcher/`). This makes "the launcher has no rogue persistent-
     * state writer" a TRUE, greppable structural invariant: no file under any `/launcher/` package (in :core or
     * :app) may emit a freeform-flag / `wm size` / `wm density` VALUE write. A `settings get` READ (e.g.
     * ShellAppLauncher.isFreeformAvailable) is not a write and does not trip this.
     */
    @Test
    fun `launcher writes persistent window-state ONLY via FreeformSeedPolicy`() {
        val launcherFiles = launcherSourceFiles().map { it.fileName.toString() }.toSet()
        // Self-check: the scan must actually reach the launcher packages (guard against a path-match regression
        // that would let this test pass on an empty scan). FreeformLaunch + ShellAppLauncher live in /launcher/.
        assertTrue(
            launcherFiles.containsAll(setOf("FreeformLaunch.kt", "ShellAppLauncher.kt")),
            "launcher source scan is not reaching /launcher/ packages — found only $launcherFiles",
        )
        val rogue = launcherSourceFiles()
            .filter { writesAnyPersistentState(KotlinSource.stripComments(it.toFile().readText())) }
            .map { it.fileName.toString() }
            .toSet()
        assertEquals(
            emptySet<String>(),
            rogue,
            "A launcher file writes persistent window state DIRECTLY. The launcher MUST route these through " +
                "com.byd.clusternav.system.FreeformSeedPolicy (in /system/, the single sanctioned writer). Rogue=$rogue",
        )
    }

    /** Files across :core + :app + :car-integration whose comment-stripped source [match]es a write pattern. */
    private fun writerFilesWhere(match: (String) -> Boolean): Set<String> =
        allKotlinSources()
            .filter { match(KotlinSource.stripComments(it.toFile().readText())) }
            .map { it.fileName.toString() }
            .toSet()

    /** Source files whose path is inside a `/launcher/` package (:core or :app). */
    private fun launcherSourceFiles(): List<Path> =
        allKotlinSources().filter { it.toString().replace('\\', '/').contains("/launcher/") }

    private fun allKotlinSources(): List<Path> =
        SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.toList()
            }
        }
}
