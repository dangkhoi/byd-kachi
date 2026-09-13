package com.byd.clusternav.speedbadge

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WIRING contract for the 2026-08-18 badge-overlay LIFECYCLE fix + the badge on/off toggle (owner note
 * `_oncar-notes/2026-08-18-drive.md` "HƯỚNG FIX").
 *
 * The overlay runtime needs Android (WindowManager / DisplayManager / Handler / Looper) and this project has
 * no Robolectric, so — exactly like [com.byd.clusternav.SpeedSignSourceLifecycleTest] and
 * [com.byd.clusternav.launcher.ClusterNavBridgeWiringContractTest] — the fix is pinned by reading the source
 * across the whole boundary: overlay (idempotent init + retry + DisplayListener + teardown + enabled gate) →
 * Prefs default → owner toggle handler → the bridge + the Nav settings group (the old ClusterNav screen and its
 * two layout variants were removed on 2026-09-13). On-car visual checks live in the note.
 */
class SpeedBadgeLifecycleContractTest {

    private val overlay = SourceRoots.text("src/main/java/com/byd/clusternav/speedbadge/SpeedBadgeOverlay.kt")
    private val prefs = SourceRoots.text("src/main/java/com/byd/clusternav/Prefs.kt")
    private val owner = SourceRoots.text("src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt")
    /** Công tắc badge nay ở nhóm *Dẫn đường* của Kachi Settings — màn cũ gỡ 2026-09-13 (S3 · R1/R3). */
    private val bridge = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt")
    private val section = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/SettingsSectionsNav.kt")

    // ── overlay: no permanent degrade, idempotent + retryable init ───────────
    @Test
    fun `overlay drops the permanent degrade kill`() {
        assertFalse(overlay.contains("degraded"), "the one-way permanent degrade flag must be gone")
    }

    @Test
    fun `initOverlay is idempotent and retried when the display was not ready`() {
        // Idempotent: bail out fast once initialized.
        assertTrue(overlay.contains("if (clusterWm != null) return"), "initOverlay no-ops once initialized")
        // Retry: doShow re-runs initOverlay when the WM is still null (display 1 was not ready at construct).
        assertTrue(overlay.contains("if (clusterWm == null) initOverlay()"), "doShow retries init when uninitialized")
        // Display-absent path stays uninitialized (returns) instead of a permanent kill.
        val init = functionBody(overlay, "private fun initOverlay()")
        assertTrue(init.contains("if (display == null)") && init.contains("return"), "display-absent stays uninitialized")
        assertFalse(init.contains("= true"), "initOverlay sets no permanent state flag")
    }

    // ── overlay: DisplayListener attach/teardown ─────────────────────────────
    @Test
    fun `overlay registers a DisplayListener that re-inits on add and tears down on remove`() {
        assertTrue(overlay.contains("DisplayManager.DisplayListener"), "a DisplayListener is declared")
        assertTrue(overlay.contains("registerDisplayListener(displayListener, handler)"), "listener registered on the main handler")
        assertTrue(overlay.contains("unregisterDisplayListener(displayListener)"), "listener unregistered on close")
        val added = functionBody(overlay, "override fun onDisplayAdded(displayId: Int)")
        assertTrue(added.contains("if (displayId != CLUSTER_DISPLAY_ID) return"), "add gated on display 1")
        assertTrue(added.contains("initOverlay()"), "onDisplayAdded re-initializes")
        assertTrue(added.contains("lastSpeedKph?.let { doShow("), "onDisplayAdded re-shows the pending value")
        val removed = functionBody(overlay, "override fun onDisplayRemoved(displayId: Int)")
        assertTrue(removed.contains("if (displayId != CLUSTER_DISPLAY_ID) return"), "remove gated on display 1")
        assertTrue(removed.contains("teardown()"), "onDisplayRemoved tears down")
    }

    @Test
    fun `teardown detaches and drops the display WM and view so re-attach is clean`() {
        val teardown = functionBody(overlay, "private fun teardown()")
        assertTrue(teardown.contains("removeView"), "teardown detaches the view")
        assertTrue(teardown.contains("attached = false"), "teardown clears attached")
        assertTrue(teardown.contains("clusterWm = null") && teardown.contains("badgeView = null"), "teardown drops display WM + view")
    }

    @Test
    fun `all window ops post to the main handler and are degrade-safe`() {
        assertTrue(overlay.contains("Handler(Looper.getMainLooper())"), "single main handler")
        assertTrue(overlay.contains("handler.post { doShow"), "show posts to main handler")
        assertTrue(overlay.contains("runCatching { clusterWm?.addView"), "addView is degrade-safe")
        assertTrue(overlay.contains("BadgeLayout.clampCenter("), "absolute-centre positioning kept")
    }

    // ── enabled gate: overlay reads Prefs, owner + controller drive it ───────
    @Test
    fun `overlay show gates on Prefs badgeEnabled and detaches when disabled`() {
        val doShow = functionBody(overlay, "private fun doShow(speedKph: Int, signType: SpeedSignType?)")
        assertTrue(doShow.contains("if (!Prefs.badgeEnabled(appContext))"), "doShow honors the enabled gate")
        assertTrue(doShow.indexOf("if (!Prefs.badgeEnabled(appContext))") < doShow.indexOf("addView").coerceAtLeast(0) ||
            doShow.contains("teardown()"), "disabled path detaches / never attaches")
        assertTrue(doShow.contains("lastSpeedKph = speedKph"), "remembers the last value for re-show/retry")
    }

    @Test
    fun `prefs declares badgeEnabled defaulting to OFF`() {
        assertTrue(
            prefs.contains("getBoolean(K_BADGE_ENABLED, false)"),
            "badgeEnabled default is false (OFF) — owner 2026-08-28: VietMap speed badge off by default",
        )
        assertTrue(prefs.contains("fun setBadgeEnabled(ctx: Context, v: Boolean)"), "setter persists the flag")
    }

    @Test
    fun `owner exposes onBadgeEnabledChanged that re-evaluates the shared overlay`() {
        assertTrue(owner.contains("fun onBadgeEnabledChanged()"), "owner exposes the toggle handler")
        assertTrue(owner.contains("badgeOverlay.applyEnabled()"), "handler re-evaluates the ONE shared overlay")
    }

    // ── bề mặt người dùng: ô tick ở Kachi Settings đi qua cầu ────────────────
    @Test
    fun `the badge switch reaches Prefs and the overlay through the bridge`() {
        // Tới 2026-09-13 bài này đọc `BadgePlacementController` + hai biến thể `activity_main.xml`. Cả ba đã gỡ
        // cùng màn cũ (S3), nên công tắc chỉ còn một đường: section Dẫn đường → cầu → Prefs + overlay.
        assertTrue(section.contains("bridge.setBadgeEnabled("), "the Nav settings group carries the switch")
        assertTrue(bridge.contains("Prefs.setBadgeEnabled(app, on)"), "the toggle persists the flag")
        assertTrue(bridge.contains("onBadgeEnabledChanged()"), "the toggle refreshes the shared overlay")
    }

    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "missing $signature" }
        var depth = 0
        var opened = false
        for (index in start until source.length) {
            when (source[index]) {
                '{' -> { depth++; opened = true }
                '}' -> if (opened && --depth == 0) return source.substring(start, index + 1)
            }
        }
        error("unterminated $signature")
    }
}
