package com.byd.clusternav

import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.AutostartGate
import com.byd.clusternav.launcher.DefaultHome
import com.byd.clusternav.launcher.FreeformLaunch
import com.byd.clusternav.launcher.HomeActivityCmd
import com.byd.clusternav.launcher.LauncherBootPlan
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.system.FreeformSeedStore

/**
 * Auto-start orchestration for the Kachi launcher (B6) — makes the workspace come up ready on car boot by doing
 * the SURFACE-INDEPENDENT setup, idempotently, off the main thread. Driven by [KachiAutostartService] (a
 * short-lived foreground service started from [RebindReceiver] on BOOT_COMPLETED / MY_PACKAGE_REPLACED).
 *
 * ── Why this is NOT a headless mounter (the VD-surface constraint) ────────────────────────────────────────────
 * Each slot app is hosted in a VirtualDisplay BACKED BY the Activity's slot SurfaceView
 * ([com.byd.clusternav.launcher.VdAppHost]). A headless service has NO surfaces, so it CANNOT create those VDs
 * or mount apps. Kachi IS the HOME app, and [KachiHomeActivity] + [com.byd.clusternav.launcher.HomeViewModel]
 * already RESTORE the persisted workspace on init → per-slot mounting happens IN the Activity when it renders the
 * restored state. So this object does ONLY the setup a service can do:
 *   1. seed the freeform boot flags via the SINGLE sanctioned writer ([FreeformSeedStore.forLauncher] →
 *      [com.byd.clusternav.system.FreeformSeedPolicy.ensureSeed]) — respects the terminal FF_USER_REMOVED marker;
 *   2. S5 — reassert Kachi as the current HOME activity (`cmd package set-home-activity`, only if not already)
 *      ONLY when the user opted into "keep home on boot" ([WorkspacePrefs.keepHomeOnBoot], default OFF); the
 *      primary way to become HOME is the Settings button (`ClusterNavBridge.setDefaultHome`);
 *   3. compute + log the cast-coordination boot plan ([LauncherBootPlan]) — surface-independent (the actual mount
 *      is the Activity's job, and the slot-seed path skips cast-owned apps too);
 *   4. ensure the HOME Activity is up (`am start` the HOME component) so it restores + mounts the saved slots —
 *      covers the MY_PACKAGE_REPLACED case where the installer kills us and does NOT relaunch.
 *
 * All shell writes route through the launcher ownership seam ([com.byd.clusternav.system.WindowCommandDispatcher.launcherSeam],
 * owned by [AppContainer]); none carry `--display`, so they never target the cluster and are ALLOWed by the gate.
 *
 * ── Idempotent + degrade-safe ─────────────────────────────────────────────────────────────────────────────────
 * Single in-flight run + cooldown via [AutostartGate] (the reusable generalization of the proven
 * [VietMapAutostart] guard): a burst of boot triggers runs the setup at most once. Every step is wrapped so a
 * failure (no dadb loopback on the emulator, a rejected write on a locked trim) NEVER crashes boot and is simply
 * retried on the next trigger. Gated by [WorkspacePrefs.launcherAutostart] (default ON) so the user can opt out.
 */
object KachiAutostart {
    private const val TAG = "KachiAutostart"

    /** Minimum spacing between two runs — mirrors [VietMapAutostart.COOLDOWN_MS]. */
    const val COOLDOWN_MS = 30_000L

    private val gate = AutostartGate(COOLDOWN_MS)

    /** Claim a run (in-flight + cooldown). `internal` so the gate can be driven end-to-end off-car. */
    internal fun tryBeginRun(nowMs: Long = System.currentTimeMillis()): Boolean = gate.tryBegin(nowMs)

    /** Release the claimed run (call in `finally`). */
    internal fun finishRun() = gate.finish()

    /** Test-only: reset the process-global gate between tests. */
    internal fun resetGateForTest() = gate.reset()


    /**
     * BLOCKING (runs on [KachiAutostartService]'s background thread — the FGS keeps the process alive while the
     * dadb round-trips complete). No-op if the user disabled launcher auto-start. Anti-loop (in-flight + cooldown)
     * is inside this call; the whole body is degrade-safe.
     */
    fun runBoot(ctx: Context) {
        val app = ctx.applicationContext
        if (!WorkspacePrefs(app).launcherAutostart()) {
            Log.i(TAG, "launcher auto-start disabled by pref — skip")
            return
        }
        if (!tryBeginRun()) {
            Log.i(TAG, "skip (a run is in-flight or within cooldown ${COOLDOWN_MS}ms) — anti-loop for boot/OTA/relaunch bursts")
            return
        }
        try {
            runCatching {
                val container = AppContainer.get(app)
                val seam = container.windowDispatcher.launcherSeam()
                val comp = DefaultHome.component(app)

                // (1) Seed the freeform boot flags via the ONE sanctioned writer (respects FF_USER_REMOVED).
                val seeded = FreeformSeedStore.forLauncher(app) { Log.i(TAG, it) }.ensureSeed()
                Log.i(TAG, "freeform seed ensured (wrote=$seeded — false = user removed / already handled by marker)")

                // (2) S5 — Reassert Kachi as the HOME activity ONLY if the user opted into "keep home on boot"
                //     (default OFF). Setting the WHOLE CAR's default HOME on every boot is a system-state change and
                //     must be explicit-scope + user-consented (CLAUDE.md §4) — the primary path is the Settings
                //     button. This boot reassert is a best-effort convenience for ROMs that reset HOME after reboot
                //     ([SUY] — chưa đo, chờ P7). `am start` in (4) still brings Kachi UP regardless; that only STARTS
                //     the launcher, it does not make it the default HOME.
                if (WorkspacePrefs(app).keepHomeOnBoot()) {
                    ensureHomeActivity(seam, comp)
                } else {
                    Log.i(TAG, "keep-home-on-boot OFF (default) — not reasserting default HOME on boot")
                }

                // (3) Cast-coordination decision (surface-independent): what the launcher owns vs what cast owns.
                logBootPlan(container)

                // (4) Ensure the HOME Activity is up so it restores + mounts the saved slots (Activity does the VD mounting).
                //     Covers MY_PACKAGE_REPLACED (installer kills us, does not relaunch). No --display ⇒ gate ALLOWs.
                seam("am start -n $comp")
                Log.i(TAG, "requested HOME up ($comp) — Activity restores + mounts saved slots")
            }.onFailure { Log.w(TAG, "kachi auto-start failed (degrade-safe, retried next trigger): ${it.message}") }
        } finally {
            finishRun()
        }
    }

    /**
     * `cmd package set-home-activity` the launcher — ONLY if it is not already the resolved HOME (idempotent).
     * Reads the current HOME via `resolve-activity` (parsed by [FreeformLaunch.parseComponent]); if it already
     * equals [comp], skip. Degrade-safe: on the emulator (no dadb loopback) the seam returns "" ⇒ parse is null ⇒
     * we attempt the set (also a no-op via the dead seam) — never throws.
     */
    private fun ensureHomeActivity(seam: (String) -> String, comp: String) {
        // S5 — cùng chuỗi lệnh với đường Cài đặt (`HomeActivityCmd`) để hai đường không lệch một byte (DRY §4.1).
        val current = runCatching { FreeformLaunch.parseComponent(seam(HomeActivityCmd.RESOLVE)) }.getOrNull()
        if (current == comp) {
            Log.i(TAG, "Kachi already the HOME activity ($comp) — skip set-home")
            return
        }
        seam(HomeActivityCmd.set(comp))
        Log.i(TAG, "set Kachi as HOME activity (was ${current ?: "unresolved"})")
    }

    /**
     * Compute + log the cast-coordination boot plan for the active profile: from the persisted workspace slots,
     * which apps the launcher owns (mount) vs which cluster-cast owns/will-cast onto the cluster (skip). The
     * launcher must NOT fight cast — `castOwns(pkg)` = `!AppLocationRegistry.isCastable(pkg)` (already on display 1).
     */
    private fun logBootPlan(container: AppContainer) {
        val slots = runCatching { container.workspaceRepository.load().slots }.getOrDefault(emptyList())
        val registry = container.windowDispatcher.locations
        val plan = LauncherBootPlan.plan(slots) { pkg -> !registry.isCastable(pkg) }
        Log.i(TAG, "boot plan (active profile): mount=${plan.mount.map { it.pkg }} skipToCast=${plan.skippedToCast.map { it.pkg }}")
    }
}
