package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WIRING contract for the three UI features shipped with the nav-display / split-ratio work
 * (owner 2026-08-12). Their runtime behaviour needs Android (Activity/View/SharedPreferences), so —
 * like [CastEnableToggleContractTest], [FloatingBubbleFirstLaunchContractTest] and
 * [NotificationAccessFlowContractTest] — this locks the wiring by reading the source across the whole
 * boundary. Unit tests cover the pure logic ([com.byd.clusternav.modules.clustercast.ClusterNavLaneWidget.shouldAssert]
 * and [com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator.applySplitRatioLive]);
 * this proves those pieces are actually BOUND so a user can reach them. On-car visual checks live in
 * the spec.
 *
 * Covers:
 *  • Item 1 — nav-on-cluster is op39-only (owner 2026-08-12): both layouts dropped the mode buttons +
 *    the self-test button (keeping the op39 status line), MainActivity no longer binds the selector,
 *    Prefs no longer declares a nav-mode, the NAV track drives the op-39 gate (nav-only, Cast off), and
 *    the speed self-compensation (interpolation + screen-read refine) is enabled by default (point 1B).
 *  • Item 2 — 9 visual split-ratio buttons replacing the old spinner: both layouts, controller
 *    bind/teardown, the removed spinner population, and the live coordinator path the buttons call.
 *  • Item 4 — bubble immediate-show on onResume, gated on castEnabled && canDrawOverlays.
 *  • Renderer LOC guard — MainActivityCastController stays a thin renderer (< 501 lines).
 */
class NavCastUiWiringContractTest {

    // ── source helpers (mirror CastEnableToggleContractTest) ─────────────────
    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative)
        else current.resolve("app").resolve(relative)
    }

    private fun core(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve("..").resolve("core").resolve(relative)
        else current.resolve("core").resolve(relative)
    }

    private fun read(path: Path): String = path.toFile().readText()

    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "missing $signature" }
        val after = start + signature.length
        val next = listOf("\n    fun ", "\n    private fun ", "\n    override fun ", "\n    companion object", "\n}")
            .mapNotNull { source.indexOf(it, after).takeIf { i -> i >= 0 } }
            .minOrNull() ?: source.length
        return source.substring(start, next)
    }

    private val mainActivity by lazy { read(app("src/main/java/com/byd/clusternav/MainActivity.kt")) }
    private val controllerPath by lazy { app("src/main/java/com/byd/clusternav/modules/clustercast/MainActivityCastController.kt") }
    private val controller by lazy { read(controllerPath) }
    private val autostart by lazy { read(app("src/main/java/com/byd/clusternav/modules/clustercast/CastAutostart.kt")) }
    private val splitButtons by lazy { read(app("src/main/java/com/byd/clusternav/modules/clustercast/CastSplitRatioButtons.kt")) }
    private val listener by lazy { read(app("src/main/java/com/byd/clusternav/NavNotificationListener.kt")) }
    private val laneWidget by lazy { read(app("src/main/java/com/byd/clusternav/modules/clustercast/ClusterNavLaneWidget.kt")) }
    private val prefs by lazy { read(app("src/main/java/com/byd/clusternav/Prefs.kt")) }
    private val coordinator by lazy { read(core("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinator.kt")) }
    private val layoutNarrow by lazy { read(app("src/main/res/layout/activity_main.xml")) }
    private val layoutWide by lazy { read(app("src/main/res/layout-w960dp/activity_main.xml")) }

    // ── Item 1: nav-on-cluster is op39-only (owner 2026-08-12) + speed self-compensation ON ──
    @Test
    fun `both layouts dropped the mode buttons and the self-test button but keep the op39 status line`() {
        for ((name, xml) in listOf("narrow" to layoutNarrow, "wide" to layoutWide)) {
            assertTrue(!xml.contains("btn_nav_mode_small"), "$name: small/top mode button removed")
            assertTrue(!xml.contains("btn_nav_mode_center"), "$name: centre+ETA mode button removed")
            assertTrue(!xml.contains("btn_nav_cluster_test"), "$name: 'Test cụm ngay' button removed")
            assertTrue(xml.contains("@+id/txt_cluster_op39_status"), "$name: op39 status line kept")
        }
    }

    @Test
    fun `main activity no longer binds the mode selector but keeps the op39 status line`() {
        assertTrue(
            !mainActivity.contains("NavClusterModeButtons"),
            "the deleted mode-selector must not be referenced in MainActivity",
        )
        assertTrue(
            mainActivity.contains("navClusterStatus.bind()"),
            "the op39 status line stays bound",
        )
    }

    @Test
    fun `prefs no longer declares a nav-cluster mode (op39 is the only mode)`() {
        assertTrue(!prefs.contains("NAV_MODE_SMALL_TOP"), "small/top mode constant removed")
        assertTrue(!prefs.contains("NAV_MODE_CENTER_ETA"), "centre-mode constant removed")
        assertTrue(!prefs.contains("navClusterMode"), "navClusterMode getter removed")
        assertTrue(!prefs.contains("setNavClusterMode"), "setNavClusterMode setter removed")
    }

    @Test
    fun `nav track drives the op39 gate in nav-only mode (cast off)`() {
        // The listener (NAV track) tells the widget when nav starts/stops.
        assertTrue(listener.contains("ClusterNavLaneWidget.onNavActive("), "listener asserts the widget on nav active")
        assertTrue(listener.contains("ClusterNavLaneWidget.onNavIdle()"), "listener clears the widget on nav idle")
        // The pure 2-arg gate withholds op39 only while Cast is ON (Cast owns the cluster); there is no
        // display-mode gate anymore. decide() is the single source of truth; shouldAssert() is the
        // ==ASSERT predicate for back-compat callers/tests.
        assertTrue(
            laneWidget.contains("castEnabled -> Decision.GATED_CAST") &&
                laneWidget.contains("decide(navActive, castEnabled) == Decision.ASSERT"),
            "decide() gates on Cast only; shouldAssert() == (decide == ASSERT)",
        )
        assertTrue(!laneWidget.contains("GATED_SMALL"), "no small/top gate remains")
        assertTrue(!laneWidget.contains("navClusterMode"), "gate no longer reads a nav-mode pref")
        assertTrue(!laneWidget.contains("fun selfTest("), "the self-test entry point is removed")
        // op39 is the OEM 'simple navigation' opcode on the AutoContainer 1000-channel.
        assertTrue(laneWidget.contains("OP_SIMPLE_NAV = 39"), "opcode is 39")
        assertTrue(laneWidget.contains("AutoContainer 2 i32 1000 i32"), "asserted via the 1000-channel service call")
        assertTrue(laneWidget.contains("castEnabled()"), "gate reads the persisted cast-enabled flag")
    }

    /**
     * 08-23 vòng 3 ([P1]) — đường notification GMaps dựng bề mặt op-39 (đường ảnh đã GỠ 2026-08-28).
     */
    @Test
    fun `speed self-compensation is enabled by default (point 1B)`() {
        // Sparse GMaps notifications froze the cluster countdown when sending RAW distance; re-enabling
        // the speed-based interpolation + screen-read refine fills the gaps between notifications.
        assertTrue(prefs.contains("getBoolean(\"interpolate\", true)"), "interpolate defaults ON")
        assertTrue(mainActivity.contains("Prefs.setInterpolate(this, true)"), "MainActivity enables interpolation")
        assertTrue(mainActivity.contains("Prefs.setAccBooster(this, true)"), "MainActivity enables the screen-read booster")
    }

    @Test
    fun `nav+HUD self-grants the accessibility booster so screenRead ground-truth is populated`() {
        // 2026-08-14: enabled_accessibility_services lost ClusterNav's service after a reboot and the app
        // had NO path to re-grant it — grantAccessibility was orphaned when the voice-key UI was removed —
        // so two consecutive drives came back with an empty screenRead_m column (interp tuning blocked).
        // Lock the wiring: turning Nav+HUD on, AND opening the app while it is already on, must self-grant
        // the accessibility service over dadb when the grant is missing.
        //
        // 2026-08-14 PM (§8): reboot also leaves the service ENABLED-but-NOT-BOUND (present in the setting,
        // absent from dumpsys "Bound services") → accessibilityBoosterGranted() (setting-only) can't see it.
        // The guard now ALSO escalates on the in-process connected flag being false, so the force-rebind in
        // NavConnect actually fires on boot / Nav+HUD-on. grantAccessibility confirms bound via dumpsys
        // before toggling, so an already-bound service is a no-op (no flicker, no redundant work).
        assertTrue(mainActivity.contains("NavConnect.grantAccessibility("), "MainActivity self-grants the accessibility booster")
        assertTrue(
            mainActivity.contains("private fun accessibilityBoosterGranted()"),
            "a local (no-dadb) grant check exists so we only escalate to dadb when actually missing",
        )
        assertTrue(
            mainActivity.contains(
                "if (!accessibilityBoosterGranted() || " +
                    "!com.byd.clusternav.modules.navaccess.NavAccessibilitySource.connected) " +
                    "NavConnect.grantAccessibility(",
            ),
            "the dadb grant escalates when the setting is missing OR the service is enabled-but-not-bound " +
                "(post-reboot); grantAccessibility verifies dumpsys before toggling so already-bound is a no-op",
        )
    }

    // ── Item 2: 9 split-ratio buttons replace the spinner ────────────────────
    @Test
    fun `both layouts replace the split-ratio spinner with the buttons container`() {
        for ((name, xml) in listOf("narrow" to layoutNarrow, "wide" to layoutWide)) {
            assertTrue(xml.contains("@+id/split_ratio_buttons"), "$name: split-ratio buttons container present")
            assertTrue(!xml.contains("spinner_split_ratio"), "$name: old split-ratio spinner removed")
        }
    }

    @Test
    fun `controller binds and tears down the split-ratio buttons`() {
        assertTrue(controller.contains("CastSplitRatioButtons(activity, coordinator)"), "controller constructs the buttons")
        assertTrue(controller.contains("splitRatioButtons.bind()"), "controller binds the buttons")
        assertTrue(controller.contains("splitRatioButtons.destroy()"), "controller releases the buttons on destroy")
    }

    @Test
    fun `autostart no longer populates a split-ratio spinner`() {
        assertTrue(!autostart.contains("populateSplitRatioSpinner"), "the spinner population is removed from autostart")
        assertTrue(!autostart.contains("spinner_split_ratio"), "no reference to the removed spinner id")
    }

    @Test
    fun `split buttons wire to the live coordinator path`() {
        assertTrue(coordinator.contains("fun applySplitRatioLive("), "coordinator exposes the live split-ratio path")
        assertTrue(
            splitButtons.contains("coordinator.applySplitRatioLive("),
            "buttons call the LIVE simplified path (not the disconnected legacy v2 store)",
        )
    }

    // ── Item 4: bubble immediate-show on resume ──────────────────────────────
    @Test
    fun `onResume shows the bubble gated on castEnabled and overlay permission`() {
        val resume = functionBody(mainActivity, "override fun onResume()")
        assertTrue(resume.contains(".castEnabled()"), "reads the master enable")
        assertTrue(resume.contains("Settings.canDrawOverlays(this)"), "checks the overlay permission")
        assertTrue(resume.contains("startForegroundService("), "starts the bubble service")
        assertTrue(resume.contains("FloatingBubbleService"), "the started service is the bubble")
        assertTrue(
            resume.indexOf(".castEnabled()") < resume.indexOf("startForegroundService("),
            "castEnabled guard precedes the service start",
        )
        assertTrue(
            resume.indexOf("Settings.canDrawOverlays(this)") < resume.indexOf("startForegroundService("),
            "overlay-permission guard precedes the service start",
        )
    }

    // ── Renderer LOC guard (thin renderer/dispatcher, not an orchestrator) ────
    @Test
    fun `cast controller stays a thin renderer under 501 lines`() {
        val lines = controllerPath.toFile().readLines().size
        assertTrue(lines < 501, "MainActivityCastController must stay a thin renderer (< 501 lines); was $lines")
    }
}
