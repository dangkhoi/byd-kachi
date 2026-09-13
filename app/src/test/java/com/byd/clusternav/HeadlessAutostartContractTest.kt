package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WIRING contract for 1.21 Item 1 — HEADLESS auto-start (owner 2026-08-15, docs/diagnostics/plan-1.21.md).
 *
 * On boot the app must do its setup WITHOUT foregrounding a screen on the main display (bonus: dodges the
 * dudu size-compat letterbox). The runtime needs Android (BroadcastReceiver, Service, SharedPreferences,
 * View), so this locks the wiring by reading the source across the whole boundary:
 *   Prefs (default-ON toggle) → RebindReceiver (gates launchHome → BootSetupService, both boot entries) →
 *   BootSetupService (startForeground-first, enabled-gated grant + cluster-lane re-assert, always stops) →
 *   manifest (exported=false specialUse) → Kachi Settings › Hệ thống (the user toggle).
 *
 * ADDITIVE: auto-cast (FloatingBubbleService via castBootWork) is untouched by all of the above.
 *
 * ⚠ 2026-09-13 (S3): the old ClusterNav screen — which carried a second copy of the boot setup for the
 * user-opens-app case — was removed (`docs/specs/kachi-remove-legacy-screen.html`). Everything it asserted on
 * every open now has to come from one of the TWO boot branches, which is what
 * [`ca hai nhanh boot deu ep ba khoa khong co nut`] locks.
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
    /** Công tắc + chuỗi setup "mở app" nay ở Kachi Settings › Hệ thống và cầu — màn cũ gỡ 2026-09-13 (S3). */
    private val section by lazy { read(app("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt")) }
    private val navBridge by lazy { read(app("src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt")) }
    private val manifest by lazy { read(app("src/main/AndroidManifest.xml")) }

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

    /**
     * ⚠ S3 (soát 2026-09-13) — ba khoá `HIDDEN_KEYS` không có nút (`hud` ép false · `interpolate`/`acc_booster`
     * ép true) trước đây được màn ClusterNav cũ ghi đè **mỗi lần mở app**. Màn đó đã gỡ, nên nơi ghi duy nhất
     * còn lại là lúc nổ máy — mà lúc nổ máy có **HAI** nhánh: bật *"Tự khởi động nền"* → [BootSetupService],
     * tắt → `launchHome`. Chỉ đặt ở một nhánh thì máy dùng nhánh kia đóng băng giá trị cũ vĩnh viễn: với
     * `interpolate=false` (bản 2026-07-13 từng ép TẮT) là cụm mất phần bù cự ly theo tốc độ, đúng triệu chứng
     * *"cụm trễ khi tới ngã rẽ"* — một hồi quy câm, không báo lỗi gì.
     */
    @Test
    fun `ca hai nhanh boot deu ep ba khoa khong co nut`() {
        val forced = functionBody(bootSetup, "fun forcedPrefs(ctx: Context)")
        assertTrue(forced.contains("Prefs.setHud(ctx, false)"), "hud ép FALSE — không có nút bật, output chưa có thật")
        assertTrue(forced.contains("Prefs.setInterpolate(ctx, true)"), "interpolate ép TRUE — di cư máy từng bị ép tắt")
        assertTrue(forced.contains("Prefs.setAccBooster(ctx, true)"), "acc_booster ép TRUE — bộ đọc màn GMaps")
        // Nhánh HEADLESS (toggle BẬT).
        assertTrue(
            bootSetup.contains("forcedPrefs(applicationContext)"),
            "nhánh headless phải gọi forcedPrefs trong chuỗi setup nền",
        )
        // Nhánh KHÔNG headless (toggle TẮT) — đây là nhánh KHÔNG chạy BootSetupService.
        assertTrue(
            functionBody(receiver, "private fun launchHome(context: Context)")
                .contains("BootSetupService.forcedPrefs("),
            "tắt \"Tự khởi động nền\" thì không có dịch vụ nào chạy ⇒ launchHome phải tự ép ba khoá đó",
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

    // ── Bề mặt người dùng: ô tick ở Kachi Settings › Hệ thống ───────────────
    //
    // Tới 2026-09-13 hai bài dưới đọc `MainActivity` + hai biến thể `activity_main.xml` (ô tick
    // `cb_headless_autostart`). Màn đó đã gỡ (S3 · R1) ⇒ ô tick chỉ còn ở nhóm *Hệ thống* của Kachi Settings,
    // dựng bằng mã và ghi qua cầu.
    @Test
    fun `the system settings group wires the headless autostart switch through the bridge`() {
        assertTrue(section.contains("deps.bridge.headlessAutostart()"), "reads the current pref for the tick")
        assertTrue(section.contains("deps.bridge.setHeadlessAutostart("), "persists the flag on toggle")
        assertTrue(navBridge.contains("Prefs.setHeadlessAutostart(app, on)"), "the bridge writes the real key")
    }

    @Test
    fun `the app-open setup survived the screen removal`() {
        // Relocating to BootSetupService must NOT lose the setup that used to run when the user opened the app:
        // self-grant + cluster-lane re-assert now live on the Nav master switch in the bridge (same order).
        assertTrue(navBridge.contains("NavConnect.grantAccessibility("), "the nav switch still self-grants accessibility")
        assertTrue(navBridge.contains("NavigationOutputTarget.CLUSTER_LANE"), "the nav switch still re-asserts cluster-lane")
    }
}
