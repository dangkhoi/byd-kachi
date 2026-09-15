package com.byd.clusternav

import com.byd.clusternav.testsupport.KotlinSource
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Wiring lock for B6 — the Kachi LAUNCHER auto-start. On boot the workspace must come up ready via a
 * surface-INDEPENDENT foreground service ([KachiAutostartService] → [KachiAutostart.runBoot]) that seeds
 * freeform, sets Kachi as HOME, computes the cast-coordination plan, and ensures the HOME activity is up (which
 * restores + mounts the saved workspace) — while the service NEVER creates VirtualDisplays (surface-bound) and
 * HOME ([com.byd.clusternav.launcher.KachiHomeActivity]) stays a trivial renderer.
 *
 * The runtime needs Android (Service, Context, dadb, SharedPreferences) and `:app` has no Robolectric, so — like
 * [VietMapAutostartServiceWiringTest] — this locks the boundary by reading SOURCE. Comments are stripped first
 * ([KotlinSource]) so a mention in a comment cannot satisfy a contract; this is the real producer→consumer
 * boundary (RebindReceiver boot event → service → runBoot → seam), and if someone rewires it the test goes RED.
 */
class KachiAutostartServiceWiringTest {

    private fun app(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve("src"))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }

    private fun readSrc(relative: String) =
        KotlinSource.stripComments(app("src/main/java/com/byd/clusternav/$relative").toFile().readText())

    private val receiver by lazy { readSrc("RebindReceiver.kt") }
    private val service by lazy { readSrc("KachiAutostartService.kt") }
    private val autostart by lazy { readSrc("KachiAutostart.kt") }
    private val boot by lazy { readSrc("BootSetupService.kt") }
    private val home by lazy { readSrc("launcher/KachiHomeActivity.kt") }
    private val windows by lazy { readSrc("launcher/LauncherWindows.kt") }
    private val prefs by lazy { readSrc("launcher/WorkspacePrefs.kt") }
    private val manifest by lazy { app("src/main/AndroidManifest.xml").toFile().readText() }

    // ── RebindReceiver: both boot entries start the launcher auto-start service ─────────────────
    @Test
    fun `boot completed and package replaced both start KachiAutostartService`() {
        val bootRegion = receiver.substring(
            receiver.indexOf("Intent.ACTION_BOOT_COMPLETED"),
            receiver.indexOf("Intent.ACTION_LOCKED_BOOT_COMPLETED"),
        )
        assertTrue(
            bootRegion.contains("KachiAutostartService.startForBoot(context)"),
            "BOOT_COMPLETED must start the launcher auto-start service",
        )
        val replacedRegion = receiver.substring(receiver.indexOf("Intent.ACTION_MY_PACKAGE_REPLACED"))
        assertTrue(
            replacedRegion.contains("KachiAutostartService.startForBoot(context)"),
            "MY_PACKAGE_REPLACED must start the launcher auto-start service (installer does not relaunch)",
        )
    }

    @Test
    fun `launcher auto-start is independent of the ClusterNav headless toggle`() {
        // It must NOT be gated behind Prefs.headlessAutostart — the launcher should come up ready regardless.
        // (startForBoot appears in the receiver OUTSIDE any headlessAutostart branch — assert the call is present
        // in both boot regions, which the previous test already covers; here we assert the pref gate is elsewhere.)
        assertTrue(autostart.contains("WorkspacePrefs(app).launcherAutostart()"), "its own pref gate lives in runBoot")
        assertFalse(
            service.contains("headlessAutostart"),
            "the launcher service must not read the ClusterNav headlessAutostart toggle",
        )
    }

    // ── Service is the SOLE caller of runBoot; boot/main do not call it directly ─────────────────
    @Test
    fun `service is the only place calling runBoot`() {
        assertTrue(service.contains("KachiAutostart.runBoot("), "KachiAutostartService must call KachiAutostart.runBoot on its worker")
        assertFalse(boot.contains("KachiAutostart.runBoot("), "BootSetupService must NOT call runBoot directly")
        // (Trước 2026-09-13 bài này cũng canh `MainActivity` không gọi runBoot; màn đó đã gỡ — S3 · R1 — nên
        // chỉ còn hai chỗ có thể gọi nhầm là receiver boot và dịch vụ boot-setup, cả hai đã canh ở trên.)
        assertFalse(receiver.contains("KachiAutostart.runBoot("), "RebindReceiver goes through the service, not runBoot")
    }

    @Test
    fun `service goes foreground first then runs on a worker and always stops`() {
        assertTrue(service.contains("startForegroundOnce()"), "startForeground gate present")
        assertTrue(
            service.indexOf("startForegroundOnce()") < service.indexOf("Thread("),
            "startForeground happens BEFORE the background work (5 s startForegroundService budget)",
        )
        assertTrue(service.contains("workerActive.compareAndSet(false, true)"), "single worker via CAS latch")
        assertTrue(service.contains("stopForeground(") && service.contains("stopSelf("), "always tears the FGS down")
    }

    // ── runBoot: the surface-independent setup steps ─────────────────────────────────────────────
    @Test
    fun `runBoot seeds freeform via the sanctioned launcher writer`() {
        assertTrue(autostart.contains("FreeformSeedStore.forLauncher(app)"), "freeform seed via the single sanctioned launcher writer")
        assertTrue(autostart.contains(".ensureSeed()"), "calls ensureSeed (respects the terminal FF_USER_REMOVED marker)")
    }

    @Test
    fun `runBoot reasserts HOME idempotently via the shared command builder`() {
        // S5 — chuỗi lệnh chuyển sang HomeActivityCmd (:core) để đường Cài đặt + đường khởi động dùng chung (DRY).
        assertTrue(autostart.contains("HomeActivityCmd.set("), "sets Kachi as HOME via the shared HomeActivityCmd.set builder")
        assertTrue(autostart.contains("HomeActivityCmd.RESOLVE"), "reads current HOME first (idempotent: only set if not already)")
        assertTrue(autostart.contains("FreeformLaunch.parseComponent("), "parses the resolved HOME component before comparing")
    }

    @Test
    fun `runBoot reasserts HOME only when the user opted into keep-home-on-boot`() {
        // S5 — đặt HOME của CẢ XE lúc nổ máy là đổi state hệ thống ⇒ phải gác sau công tắc (mặc định TẮT, CLAUDE.md §4).
        // Đường CHÍNH để thành HOME là nút trong Cài đặt (ClusterNavBridge.setDefaultHome).
        assertTrue(
            autostart.contains("keepHomeOnBoot()"),
            "the boot set-home must be gated behind WorkspacePrefs.keepHomeOnBoot (default OFF)",
        )
        assertTrue(prefs.contains("fun keepHomeOnBoot()"), "the keep-home-on-boot pref lives in WorkspacePrefs (per-vehicle)")
        // 2026-09-15 (HOME-alias, DuDu-style): the ONLY other gate is `homeChosen()` — set solely when the user pressed
        // "Đặt làm màn hình chính" and it succeeded. Re-asserting then RESTORES an expressed choice after an upgrade
        // (the alias ships disabled, so without it Home falls back to launcher3) — still user-consented (CLAUDE.md §4),
        // not a silent system change. Pin both the gate and that the alias is enabled BEFORE set-home.
        assertTrue(autostart.contains("prefs.homeChosen()"), "boot set-home may also run when the user already chose Kachi as home")
        assertTrue(prefs.contains("fun homeChosen()"), "the home-chosen marker lives in WorkspacePrefs next to keepHomeOnBoot")
        assertTrue(
            autostart.indexOf("DefaultHome.enableHomeEntry(") in 0 until autostart.indexOf("ensureHomeActivity(seam, comp)"),
            "the disabled HOME alias must be enabled BEFORE set-home-activity targets it",
        )
    }

    @Test
    fun `runBoot consults the cast-coordination plan`() {
        assertTrue(autostart.contains("LauncherBootPlan.plan("), "consults the pure cast-coordination decision")
        assertTrue(autostart.contains("isCastable("), "castOwns = !AppLocationRegistry.isCastable(pkg) — do not fight cast")
    }

    @Test
    fun `runBoot ensures the HOME activity is up so it restores and mounts slots`() {
        assertTrue(autostart.contains("am start -n "), "launches the HOME component so the Activity restores + mounts saved slots")
        // ⚠ [SOÁT 2026-09-15 · P1] `am start` must target the ALWAYS-ENABLED activity, never the HOME alias: the alias
        // ships `enabled=false`, and `am start -n <disabled component>` fails with "Activity class … does not exist" —
        // so a user who never pressed "Đặt làm màn hình chính" would get NO launcher after boot / MY_PACKAGE_REPLACED.
        assertTrue(
            autostart.contains("am start -n \$launchComp"),
            "am start must use DefaultHome.launchComponent (KachiHomeActivity), not the disabled HOME alias",
        )
        assertTrue(autostart.contains("DefaultHome.launchComponent(app)"), "launch component comes from the single source")
        val defaultHome = readSrc("launcher/DefaultHome.kt")
        assertTrue(defaultHome.contains("fun launchComponent("), "DefaultHome owns BOTH components (home alias vs launch activity)")
        assertTrue(
            defaultHome.contains("KachiHomeActivity::class.java.name}\""),
            "launchComponent points at KachiHomeActivity (MAIN+LAUNCHER, always enabled)",
        )
    }

    @Test
    fun `runBoot is gated and degrade-safe`() {
        assertTrue(autostart.contains("tryBeginRun()"), "anti-loop: claims a run (in-flight + cooldown)")
        assertTrue(autostart.contains("finishRun()"), "releases the run in finally")
        assertTrue(autostart.contains("runCatching"), "degrade-safe — never crashes boot")
    }

    // ── The service must NOT mount slots (VirtualDisplays are surface-bound) ─────────────────────
    @Test
    fun `neither the service nor runBoot creates VirtualDisplays or mounts slots`() {
        for ((name, src) in listOf("service" to service, "autostart" to autostart)) {
            assertFalse(src.contains("VirtualDisplay"), "$name must not create VirtualDisplays (surface-bound → Activity)")
            assertFalse(src.contains("VdAppHost"), "$name must not touch the VD app host")
            assertFalse(src.contains("SlotAppHost"), "$name must not touch the slot app host")
        }
    }

    // ── HOME stays a trivial renderer (no heavy boot init added) ─────────────────────────────────
    @Test
    fun `home activity stays trivial — no boot orchestration added to it`() {
        assertFalse(home.contains("KachiAutostart"), "KachiHomeActivity must not run the boot orchestration")
        assertFalse(home.contains("set-home-activity"), "HOME must not set itself as home")
        assertFalse(home.contains("FreeformSeedStore"), "HOME must not seed freeform")
    }

    // ── Cast coordination is wired into the real slot-seed path ──────────────────────────────────
    @Test
    fun `slot seed path skips cast-owned apps`() {
        val seed = windows.substring(windows.indexOf("fun seedLocations()"))
        assertTrue(seed.contains("LauncherBootPlan.plan("), "seedLocations uses the pure cast-coordination decision")
        assertTrue(seed.contains("isCastable("), "seedLocations skips apps cast already owns on the cluster")
    }

    // ── Pref: default-ON launcher auto-start kill-switch ─────────────────────────────────────────
    @Test
    fun `workspace prefs declares launcher autostart defaulting to true`() {
        assertTrue(prefs.contains("fun launcherAutostart(): Boolean"), "getter declared")
        // S4 · R3(a): the flag is now per-profile, read through `profileBoolean` (which falls back once to the old
        // device-wide key so nobody loses their choice on upgrade). The default itself is unchanged: ON.
        assertTrue(prefs.contains("profileBoolean(K_AUTOSTART, true)"), "defaults ON (true)")
        assertTrue(prefs.contains("fun setLauncherAutostart(on: Boolean)"), "setter present")
    }

    // ── Manifest: private special-use FGS, never exported ────────────────────────────────────────
    @Test
    fun `manifest declares KachiAutostartService as private special-use foreground service`() {
        val decl = Regex("""<service\s+android:name="\.KachiAutostartService"[\s\S]*?</service>""")
            .find(manifest)?.value ?: error("KachiAutostartService declaration missing")
        assertTrue(decl.contains("android:exported=\"false\""), "must be exported=false")
        assertTrue(decl.contains("android:foregroundServiceType=\"specialUse\""), "declared as a specialUse FGS")
        // [ĐO on-car 2026-09-15] `<property>` (API 34) làm Android 10 PackageParser từ chối cài ("Unknown element under
        // <service>: property"). Bất biến mới: KHÔNG có <property> — xem HeadlessAutostartContractTest cùng lý do.
        assertTrue(!decl.contains("<property"), "KHONG duoc co <property> duoi <service> — Android 10 tu choi cai")
    }

    @Test
    fun `manifest does not duplicate boot or foreground-service permissions`() {
        assertTrue(manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"), "boot permission already present (reused)")
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE"), "FGS permission already present (reused)")
        // No new receiver — RebindReceiver already listens for BOOT_COMPLETED.
        assertTrue(
            Regex("""android:name="android\.permission\.RECEIVE_BOOT_COMPLETED"""").findAll(manifest).count() == 1,
            "RECEIVE_BOOT_COMPLETED declared exactly once (no dup)",
        )
    }
}
