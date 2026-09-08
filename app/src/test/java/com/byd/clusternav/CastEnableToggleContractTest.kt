package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WIRING contract for the master "Bật Cluster Cast" switch (owner request 2026-08-11).
 *
 * The runtime behavior needs Android (Service lifecycle, Activity, SharedPreferences), so — like
 * [FloatingBubbleFirstLaunchContractTest] — this locks the wiring by reading the source across the
 * whole boundary: pref interface (:core) → pref impl (app) → service gate → activity guard →
 * controller wiring → the switch class → both layouts. On-car visual checks live in the spec.
 */
class CastEnableToggleContractTest {

    // ── source helpers ───────────────────────────────────────────────────────
    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
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

    private val prefsInterface by lazy {
        read(core("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastModels.kt"))
    }
    private val prefsImpl by lazy {
        read(app("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt"))
    }
    private val service by lazy {
        read(app("src/main/java/com/byd/clusternav/modules/clustercast/FloatingBubbleService.kt"))
    }
    private val mainActivity by lazy { read(app("src/main/java/com/byd/clusternav/MainActivity.kt")) }
    private val controller by lazy {
        read(app("src/main/java/com/byd/clusternav/modules/clustercast/MainActivityCastController.kt"))
    }
    private val switchClass by lazy {
        read(app("src/main/java/com/byd/clusternav/modules/clustercast/CastEnableSwitch.kt"))
    }
    private val layoutNarrow by lazy { read(app("src/main/res/layout/activity_main.xml")) }
    private val layoutWide by lazy { read(app("src/main/res/layout-w960dp/activity_main.xml")) }

    // ── pref contract: interface + default-true impl ─────────────────────────
    @Test
    fun `prefs interface declares the master enable`() {
        assertTrue(prefsInterface.contains("fun castEnabled(): Boolean"), "interface declares castEnabled")
        assertTrue(prefsInterface.contains("fun setCastEnabled(enabled: Boolean)"), "interface declares setCastEnabled")
    }

    @Test
    fun `shared prefs defaults cast enabled to false`() {
        // Default FALSE (owner 2026-08-11) = nav-only: a fresh install keeps the cluster native so
        // navigation shows immediately; Cast is opt-in.
        assertTrue(prefsImpl.contains("getBoolean(\"cast_enabled\", false)"), "castEnabled default is false")
        assertTrue(prefsImpl.contains("putBoolean(\"cast_enabled\""), "setCastEnabled persists the flag")
    }

    // ── service gate: both entries stand down when disabled, AFTER startForeground ──
    @Test
    fun `service gates both lifecycle entries on castEnabled after going foreground`() {
        val onCreate = functionBody(service, "override fun onCreate()")
        val onStart = functionBody(service, "override fun onStartCommand")
        // Gate present in both entries.
        assertTrue(onCreate.contains("if (!castEnabledNow()) { stopSelf()"), "onCreate stands down when disabled")
        assertTrue(onStart.contains("if (!castEnabledNow()) { stopSelf(startId)"), "onStartCommand stands down when disabled")
        // Foreground-service contract preserved: startForeground BEFORE the enable gate BEFORE overlay.
        assertTrue(
            onCreate.indexOf("startForegroundOnce()") < onCreate.indexOf("castEnabledNow()") &&
                onCreate.indexOf("castEnabledNow()") < onCreate.indexOf("requestOverlayIfMissing()"),
            "onCreate order: startForeground → castEnabled gate → overlay gate",
        )
        assertTrue(
            onStart.indexOf("startForegroundOnce()") < onStart.indexOf("castEnabledNow()") &&
                onStart.indexOf("castEnabledNow()") < onStart.indexOf("requestOverlayIfMissing()"),
            "onStartCommand order: startForeground → castEnabled gate → overlay gate",
        )
    }

    @Test
    fun `service reads the master enable fresh and fails safe to off`() {
        val helper = functionBody(service, "private fun castEnabledNow()")
        assertTrue(helper.contains("prefs.castEnabled()"), "reads the persisted flag")
        assertTrue(helper.contains("getOrDefault(false)"), "a read failure never silently STARTS casting (opt-in)")
    }

    // ── activity guard: no service start when disabled ───────────────────────
    @Test
    fun `main activity starts the bubble service only when cast enabled`() {
        assertTrue(mainActivity.contains(".castEnabled()"), "reads the master enable")
        assertTrue(
            mainActivity.indexOf(".castEnabled()") < mainActivity.indexOf("startForegroundService(Intent(this, com.byd.clusternav.modules.clustercast.FloatingBubbleService"),
            "the castEnabled guard precedes the startForegroundService call",
        )
    }

    // ── controller: projection gate + switch wiring ──────────────────────────
    @Test
    fun `controller gates projection on start and wires the switch`() {
        assertTrue(
            controller.contains("if (coordinator.prefs.castEnabled()) coordinator.openProjection()"),
            "projection opens on start only when enabled",
        )
        assertTrue(controller.contains("CastEnableSwitch(activity, coordinator).bind("), "switch is wired")
        assertTrue(
            controller.contains("R.id.switch_cast_enabled") &&
                controller.contains("R.id.cast_body") &&
                controller.contains("R.id.txt_cast_disabled_hint"),
            "switch bound to header switch + collapsible body + disabled hint",
        )
    }

    // ── switch class: enable opens, disable stands down WITHOUT reopening ─────
    @Test
    fun `enable opens projection and starts the bubble service`() {
        val enable = functionBody(switchClass, "private fun enable()")
        assertTrue(enable.contains("coordinator.openProjection()"), "enable opens the projection")
        assertTrue(enable.contains("startForegroundService("), "enable starts the bubble service")
    }

    @Test
    fun `disable stops, closes projection, stops service and does NOT reopen`() {
        val disable = functionBody(switchClass, "private fun disable()")
        assertTrue(disable.contains("SimpleCastIntent.Stop()"), "disable returns any cast app")
        assertTrue(disable.contains("coordinator.closeProjection()"), "disable closes the projection (cluster → gauges)")
        assertTrue(disable.contains("stopService("), "disable stops the bubble service")
        assertTrue(!disable.contains("openProjection"), "disable must NOT reopen (leave cluster on native gauges)")
    }

    @Test
    fun `bind persists the flag and toggles body visibility without firing on programmatic set`() {
        val bind = functionBody(switchClass, "fun bind(")
        assertTrue(bind.contains("setOnCheckedChangeListener(null)"), "detaches listener before applying persisted state")
        assertTrue(bind.indexOf("setOnCheckedChangeListener(null)") < bind.indexOf("isChecked = enabled"),
            "listener detached BEFORE isChecked is set (no spurious enable/disable on open)")
        assertTrue(bind.contains("setCastEnabled(isChecked)"), "user toggle persists the flag")
    }

    @Test
    fun `switch does not tear down the coordinator singleton`() {
        // The coordinator owns the shared privileged shell gateway (executeShell) — it must survive Cast-off.
        assertTrue(!switchClass.contains("shutdown()"), "must not shutdown the coordinator (HUD shell gateway)")
    }

    // ── layouts: switch + collapsible body + hint present, update stays reachable ──
    @Test
    fun `both layouts carry the switch, collapsible body, hint and a still-reachable update button`() {
        for ((name, xml) in listOf("narrow" to layoutNarrow, "wide" to layoutWide)) {
            assertTrue(xml.contains("@+id/switch_cast_enabled"), "$name: master switch present")
            assertTrue(xml.contains("@+id/cast_body"), "$name: collapsible cast_body present")
            assertTrue(xml.contains("@+id/txt_cast_disabled_hint"), "$name: disabled hint present")
            assertTrue(xml.contains("@+id/btn_check_update"), "$name: update button still present")
            // Option B (docs/specs/ui-redesign-options.html): the update button moved OUT of the Cast card
            // into its own top-level "Hệ thống" card. The ORIGINAL INTENT — it stays reachable when Cast is
            // OFF — is preserved (strengthened) by asserting it is NOT inside the collapsible cast_body (the
            // block the master switch hides). The old "after cast_recovery_toggle" positional check no longer
            // holds now that recovery lives in the separate "Nâng cao" card.
            val bodyRange = castBodyRange(xml)
            val updateIdx = xml.indexOf("@+id/btn_check_update")
            assertTrue(
                updateIdx !in bodyRange,
                "$name: update button must sit OUTSIDE cast_body so it stays reachable when Cast is OFF",
            )
        }
    }

    /** Range of the `cast_body` `<LinearLayout>` element (matches nested LinearLayouts by depth). */
    private fun castBodyRange(xml: String): IntRange {
        val idIdx = xml.indexOf("@+id/cast_body\"")
        require(idIdx >= 0) { "missing cast_body" }
        val open = xml.lastIndexOf("<LinearLayout", idIdx)
        require(open >= 0) { "cast_body open tag not found" }
        var i = open
        var depth = 0
        while (i < xml.length) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) break
            when {
                xml.startsWith("</LinearLayout", lt) -> {
                    depth--
                    val gt = xml.indexOf('>', lt)
                    if (depth == 0) return open until (gt + 1)
                    i = gt + 1
                }
                xml.startsWith("<LinearLayout", lt) -> { depth++; i = lt + "<LinearLayout".length }
                else -> i = lt + 1
            }
        }
        error("cast_body close tag not found")
    }
}
