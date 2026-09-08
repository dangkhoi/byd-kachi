package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WIRING contract for 1.21 Item 1 — HEADLESS auto-start (owner 2026-08-15, docs/diagnostics/plan-1.21.md).
 *
 * On boot the app must do its setup WITHOUT foregrounding MainActivity on the main display (bonus: dodges
 * the dudu size-compat letterbox). The runtime needs Android (BroadcastReceiver, Service, SharedPreferences,
 * View), so — like [CastEnableToggleContractTest] and [NavCastUiWiringContractTest] — this locks the wiring
 * by reading the source across the whole boundary:
 *   Prefs (default-ON toggle) → RebindReceiver (gates launchHome → BootSetupService, both boot entries) →
 *   BootSetupService (startForeground-first, enabled-gated grant + cluster-lane re-assert, always stops) →
 *   manifest (exported=false specialUse) → MainActivity + both layouts (the user toggle).
 *
 * ADDITIVE: the MainActivity.onCreate boot-setup (grant + cluster-lane) is UNCHANGED — it stays for the
 * user-opens-app case. Auto-cast (FloatingBubbleService via castBootWork) is untouched by all of the above.
 */
class HeadlessAutostartContractTest {

    // ── source helpers (mirror NavCastUiWiringContractTest) ──────────────────
    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative)
        else current.resolve("app").resolve(relative)
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

    private val prefs by lazy { read(app("src/main/java/com/byd/clusternav/Prefs.kt")) }
    private val receiver by lazy { read(app("src/main/java/com/byd/clusternav/RebindReceiver.kt")) }
    private val bootSetup by lazy { read(app("src/main/java/com/byd/clusternav/BootSetupService.kt")) }
    private val mainActivity by lazy { read(app("src/main/java/com/byd/clusternav/MainActivity.kt")) }
    private val manifest by lazy { read(app("src/main/AndroidManifest.xml")) }
    private val layoutNarrow by lazy { read(app("src/main/res/layout/activity_main.xml")) }
    private val layoutWide by lazy { read(app("src/main/res/layout-w960dp/activity_main.xml")) }

    // ── Prefs: default-ON toggle ─────────────────────────────────────────────
    @Test
    fun `prefs declares headless autostart defaulting to true`() {
        assertTrue(prefs.contains("fun headlessAutostart(ctx: Context): Boolean"), "getter declared")
        assertTrue(
            prefs.contains("getBoolean(\"headless_autostart\", true)"),
            "headlessAutostart defaults ON (true) — boot goes headless unless the user opts out",
        )
        assertTrue(
            prefs.contains("fun setHeadlessAutostart(ctx: Context, v: Boolean)") &&
                prefs.contains("putBoolean(\"headless_autostart\""),
            "setter persists the flag",
        )
    }

    // ── RebindReceiver: gate launchHome → BootSetupService on both boot entries ──
    @Test
    fun `boot completed gates launchHome on the toggle and starts BootSetupService`() {
        val body = functionBody(receiver, "override fun onReceive")
        val boot = body.substring(
            body.indexOf("Intent.ACTION_BOOT_COMPLETED"),
            body.indexOf("Intent.ACTION_LOCKED_BOOT_COMPLETED"),
        )
        assertTrue(boot.contains("Prefs.headlessAutostart(context)"), "BOOT_COMPLETED reads the toggle")
        assertTrue(boot.contains("startBootSetup(context)"), "BOOT_COMPLETED starts the headless setup when ON")
        assertTrue(boot.contains("launchHome(context)"), "BOOT_COMPLETED falls back to launchHome when OFF")
        // Untouched behaviour that must remain.
        assertTrue(boot.contains("scheduleWatchdog(context)"), "watchdog still scheduled")
        assertTrue(boot.contains("castBootWork(context"), "auto-cast (castBootWork) still runs — untouched")
    }

    @Test
    fun `package replaced gates launchHome on the toggle and starts BootSetupService`() {
        val body = functionBody(receiver, "override fun onReceive")
        val replaced = body.substring(body.indexOf("Intent.ACTION_MY_PACKAGE_REPLACED"))
        assertTrue(replaced.contains("Prefs.headlessAutostart(context)"), "MY_PACKAGE_REPLACED reads the toggle")
        assertTrue(replaced.contains("startBootSetup(context)"), "MY_PACKAGE_REPLACED starts the headless setup when ON")
        assertTrue(replaced.contains("launchHome(context)"), "MY_PACKAGE_REPLACED falls back to launchHome when OFF")
        assertTrue(replaced.contains("castBootWork(context"), "auto-cast (castBootWork) still runs — untouched")
    }

    @Test
    fun `startBootSetup helper launches the headless service as a foreground service`() {
        val helper = functionBody(receiver, "private fun startBootSetup")
        assertTrue(helper.contains("startForegroundService("), "started as a foreground service (dadb grant > receiver budget)")
        assertTrue(helper.contains("BootSetupService::class.java"), "starts BootSetupService")
        assertTrue(helper.contains("runCatching"), "best-effort — never throws out of the receiver")
    }

    // ── BootSetupService: startForeground-first, gated setup, always stops ──
    @Test
    fun `boot setup service goes foreground first then does the gated setup off the main thread`() {
        val onStart = functionBody(bootSetup, "override fun onStartCommand")
        assertTrue(onStart.contains("startForegroundOnce()"), "startForeground gate present")
        assertTrue(
            onStart.indexOf("startForegroundOnce()") < onStart.indexOf("Thread("),
            "startForeground happens BEFORE the background work (5 s startForegroundService budget)",
        )
        assertTrue(onStart.contains("Prefs.enabled(applicationContext)"), "setup gated on Nav+HUD being enabled")
        assertTrue(onStart.contains("NavConnect.grantAccessibility(applicationContext)"), "relocated accessibility grant + force-bind")
        assertTrue(onStart.contains("NavigationOutputTarget.CLUSTER_LANE"), "re-asserts the cluster-lane output")
        assertTrue(onStart.contains("runCatching"), "wrapped so it never crashes the process")
    }

    @Test
    fun `boot setup grant only escalates when the accessibility service is not already bound`() {
        val onStart = functionBody(bootSetup, "override fun onStartCommand")
        assertTrue(
            onStart.contains("NavAccessibilitySource.connected"),
            "grant is gated on the bound flag (already-bound is a no-op — no flicker)",
        )
    }

    @Test
    fun `boot setup service always stops foreground and self`() {
        assertTrue(bootSetup.contains("startForeground("), "calls startForeground")
        val finish = functionBody(bootSetup, "private fun finish")
        assertTrue(finish.contains("stopForeground("), "stopForeground on finish")
        assertTrue(finish.contains("stopSelf("), "stopSelf on finish")
        // finish(startId) sits OUTSIDE the runCatching in onStartCommand, so it ALWAYS runs.
        val onStart = functionBody(bootSetup, "override fun onStartCommand")
        assertTrue(onStart.contains("finish(startId)"), "onStartCommand always calls finish")
    }

    @Test
    fun `boot setup does not touch the auto-cast track`() {
        // Auto-cast is already headless (FloatingBubbleService is the sole autostart driver via castBootWork).
        assertTrue(!bootSetup.contains("FloatingBubbleService"), "must not start the bubble/cast service")
        assertTrue(!bootSetup.contains("openProjection"), "must not drive projection")
        assertTrue(!bootSetup.contains("castEnabled"), "must not read/steer the cast master")
    }

    // ── manifest: exported=false specialUse ─────────────────────────────────
    @Test
    fun `manifest declares BootSetupService as private special-use foreground service`() {
        val decl = Regex("""<service\s+android:name="\.BootSetupService"[\s\S]*?</service>""")
            .find(manifest)?.value ?: error("BootSetupService declaration missing")
        assertTrue(decl.contains("android:exported=\"false\""), "BootSetupService must be exported=false")
        assertTrue(decl.contains("android:foregroundServiceType=\"specialUse\""), "declared as a specialUse FGS")
        assertTrue(
            decl.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"),
            "carries the special-use subtype property (mirrors FloatingBubbleService)",
        )
    }

    // ── MainActivity + layouts: the user toggle ─────────────────────────────
    @Test
    fun `main activity wires the headless autostart checkbox`() {
        assertTrue(mainActivity.contains("R.id.cb_headless_autostart"), "checkbox bound in onCreate")
        assertTrue(mainActivity.contains("Prefs.headlessAutostart(this)"), "reads the current pref for isChecked")
        assertTrue(mainActivity.contains("Prefs.setHeadlessAutostart(this,"), "persists the flag on toggle")
    }

    @Test
    fun `both layouts carry the headless autostart checkbox`() {
        for ((name, xml) in listOf("narrow" to layoutNarrow, "wide" to layoutWide)) {
            assertTrue(xml.contains("@+id/cb_headless_autostart"), "$name: headless-autostart checkbox present")
        }
    }

    @Test
    fun `main activity boot setup stays additive`() {
        // Relocating to BootSetupService must NOT remove the user-opens-app setup in MainActivity.onCreate.
        assertTrue(mainActivity.contains("NavConnect.grantAccessibility("), "onCreate path still self-grants accessibility")
        assertTrue(mainActivity.contains("NavigationOutputTarget.CLUSTER_LANE"), "onCreate path still re-asserts cluster-lane")
    }
}
