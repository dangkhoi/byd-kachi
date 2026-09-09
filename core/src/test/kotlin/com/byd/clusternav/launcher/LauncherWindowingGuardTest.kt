package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * STAGE 0 SAFETY NET — GUARD A: the LAUNCHER must never target a display >= 1.
 *
 * Display 1 is the driver's CLUSTER. Only Cluster Cast (`com.byd.clusternav.modules.clustercast`) is allowed
 * to place windows there. A launcher (home / app-slot) command that leaked `--display 1` (or higher) would
 * fling a home-slot app onto the cluster — exactly the class of bug the two-track split forbids.
 *
 * Two layers:
 *  (1) PURE — every command the pure builders ([FreeformLaunch], [ShellAppLauncher]) emit for the launcher
 *      path targets display 0 only.
 *  (2) SOURCE-SCAN — no launcher source file under `com/byd/clusternav/launcher/` (cast excluded) contains a
 *      literal `--display <1-9>`. Comment-stripped, so a doc mention can never trip it.
 *
 * It also PINS the inline `am start --display ...` templates of the two Android launcher hosts ([VdAppHost],
 * [SlotAppHost]) which build them inline (unreachable from a pure JVM test). Those two target a SELF-CREATED
 * [android.hardware.display.VirtualDisplay] (a private secondary display used only to render the app inside a
 * slot — NOT the cluster), so they are safe today, but the strings are documented here and flagged for
 * Stage 1 to route through [FreeformLaunch].
 */
class LauncherWindowingGuardTest {

    /** A launcher command targeting the cluster or any secondary display by LITERAL id (0 is the only allowed). */
    private val displayGe1 = Regex("""--display\s+[1-9]""")

    // ─────────────────────────────────── (1) PURE builder outputs ───────────────────────────────────

    @Test
    fun `FreeformLaunch defaults to and only ever names the main display 0`() {
        assertEquals(0, FreeformLaunch.MAIN_DISPLAY)
        assertTrue(FreeformLaunch.launchCmd("com.foo/.Main").contains("--display 0"))
        assertTrue(FreeformLaunch.fullscreenCmd("com.foo/.Main").contains("--display 0"))
        assertFalse(displayGe1.containsMatchIn(FreeformLaunch.launchCmd("com.foo/.Main")))
        assertFalse(displayGe1.containsMatchIn(FreeformLaunch.fullscreenCmd("com.foo/.Main")))
    }

    @Test
    fun `ShellAppLauncher never emits a command targeting display greater than or equal to 1`() {
        val calls = mutableListOf<String>()
        val stack = "Stack id=2 bounds=[0,0][1920,720] displayId=0 userId=0\n  taskId=42: com.foo/.Main"
        val sh: (String) -> String = { c ->
            calls += c
            when {
                c.startsWith("cmd package resolve-activity") -> "priority=0\ncom.foo/.Main"
                c == "am stack list" -> stack
                else -> ""
            }
        }
        val launcher = ShellAppLauncher(sh, sleep = {})
        val bounds = SlotRect(index = 0, left = 0, top = 90, right = 1920, bottom = 630)
        launcher.openInSlot("com.foo", bounds)
        launcher.moveToSlot("com.foo", bounds)
        launcher.closeSlot("com.foo")
        val leaks = calls.filter { displayGe1.containsMatchIn(it) }
        assertTrue(leaks.isEmpty(), "launcher adapter leaked a cluster/secondary-display command: $leaks")
    }

    // ────────────────────────────────────── (2) SOURCE-SCAN ─────────────────────────────────────────

    @Test
    fun `no launcher source file targets a display greater than or equal to 1`() {
        val launcherFiles = launcherSourceFiles()
        assertTrue(launcherFiles.isNotEmpty(), "could not locate launcher source files to scan (root resolution)")
        val offenders = launcherFiles
            .filter { displayGe1.containsMatchIn(KotlinSource.stripComments(it.toFile().readText())) }
            .map { it.fileName.toString() }
            .sorted()
        assertEquals(
            emptyList<String>(),
            offenders,
            "launcher (non-cast) source must never target display >= 1 — the cluster is cast-only. Offenders: $offenders",
        )
    }

    // ── inline golden-string DOC for the Android hosts (pinned; flagged for Stage 1) ─────────────────
    // TODO(Stage1): VdAppHost / SlotAppHost build these inline. Route them through FreeformLaunch (with the
    //  VirtualDisplay id) so they are byte-locked by the pure builder instead of by this source pin. They target
    //  a SELF-CREATED VirtualDisplay (private secondary display for in-slot rendering), NOT the cluster.

    @Test
    fun `VdAppHost inline am-start template is pinned for Stage 1`() {
        val src = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/VdAppHost.kt")
        assertTrue(
            src.contains("am start --display \$displayId --windowingMode 1"),
            "VdAppHost am-start head changed — update this golden pin and the Stage-1 handoff",
        )
        assertTrue(
            src.contains(" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n '\$comp'"),
            "VdAppHost launch-intent tail changed",
        )
        assertTrue(src.contains("am force-stop \$p"), "VdAppHost force-stop template changed")
        assertTrue(
            src.contains("cmd package resolve-activity --brief -a android.intent.action.MAIN" +
                " -c android.intent.category.LAUNCHER \$pkg"),
            "VdAppHost resolve-activity template changed",
        )
    }

    @Test
    fun `SlotAppHost inline am-start template is pinned for Stage 1`() {
        val src = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/SlotAppHost.kt")
        assertTrue(
            src.contains("am start --display \$vd --windowingMode 1 -n '\$comp'"),
            "SlotAppHost am-start template changed — update this golden pin and the Stage-1 handoff",
        )
    }

    /** All launcher (non-cast) Kotlin sources across :core and :app. */
    private fun launcherSourceFiles(): List<Path> =
        SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { paths ->
                paths.filter { p ->
                    val s = p.toString().replace('\\', '/')
                    Files.isRegularFile(p) && s.endsWith(".kt") &&
                        s.contains("/com/byd/clusternav/launcher/") &&
                        !s.contains("/modules/clustercast/")
                }.toList()
            }
        }
}
