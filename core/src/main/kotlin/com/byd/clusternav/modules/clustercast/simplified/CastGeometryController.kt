package com.byd.clusternav.modules.clustercast.simplified

/**
 * Owns per-app freeform geometry: task lookup, resize (full + per-slot), profile
 * persistence and restore (R4/R5/R6), plus the read-only cluster-display stack queries
 * those decisions depend on (task-on-display, fullscreen-stack availability, freeform probe).
 *
 * Extracted from [SimpleCastCoordinator] on 2026-08-05 to keep that file ≤ 500 LOC
 * and to centralize the single hard rule: **persist geometry ONLY after a shell
 * `am task resize` (or `wm size` fallback) reports success.**
 *
 * Pure JVM — depends only on [SimpleCastShell] and [SimpleCastPrefs] (LayeringRulesTest Q1).
 */
internal class CastGeometryController(
    private val shell: SimpleCastShell,
    private val prefs: SimpleCastPrefs,
    private val displayId: Int,
    private val log: (String) -> Unit = { println("[CastGeometry] $it") },
) {

    /** Find the taskId for [pkg], preferring the cluster display. Null if not found. */
    fun findTaskIdForPkg(pkg: String): String? {
        val result = shell.execute("am stack list")
        if (!result.success) return null
        return CastStackParser.findTaskId(result.stdout, pkg, prefs.lastDisplayId() ?: displayId)
    }

    /**
     * Resize the full-screen [pkg]. Tier-1 `am task resize`; tier-2 `wm size` fallback
     * (V0.36 approach) when freeform resize is rejected.
     *
     * R6: persists to the [CastProfile.FULL] profile ONLY on shell success.
     */
    fun resizeFull(pkg: String, left: Int, top: Int, right: Int, bottom: Int) {
        val taskId = findTaskIdForPkg(pkg) ?: return
        val result = shell.execute("am task resize $taskId $left $top $right $bottom")
        if (result.success) {
            persistBounds(pkg, CastProfile.FULL, left, top, right, bottom)
            return
        }
        // Fallback: change logical display size, keeping height to avoid letterbox.
        val (physW, physH) = queryDisplayPhysicalSize() ?: (1920 to 720)
        val scaleW = (right - left).coerceIn(320, physW)
        val scaleH = (bottom - top).coerceIn(240, physH)
        val sizeResult = shell.execute("wm size ${scaleW}x${scaleH} -d $displayId")
        if (sizeResult.success) {
            log("resizeActiveTarget FALLBACK wm size ${scaleW}x${scaleH} OK")
            val existing = prefs.displayConfigFor(pkg, CastProfile.FULL) ?: DisplayConfig.NORMAL_DEFAULT
            prefs.saveDisplayConfig(
                pkg,
                CastProfile.FULL,
                existing.copy(wmSize = "${scaleW}x${scaleH}", bounds = CastBounds(left, top, right, bottom)),
            )
        } else {
            log("resizeActiveTarget FAILED — both task resize and wm size failed")
        }
    }

    /**
     * Resize a split slot's [pkg] via `am task resize` and persist to [profile] on success (R5/R6).
     *
     * No `wm size` fallback: split geometry needs per-task bounds (freeform), and `wm size`
     * is display-global — it cannot place two apps in two halves. If the resize is rejected,
     * freeform is not alive (needs a one-time power-cycle) and nothing is persisted.
     */
    fun resizeSlot(pkg: String, profile: CastProfile, left: Int, top: Int, right: Int, bottom: Int) {
        val taskId = findTaskIdForPkg(pkg) ?: return
        val result = shell.execute("am task resize $taskId $left $top $right $bottom")
        if (result.success) {
            persistBounds(pkg, profile, left, top, right, bottom)
        } else {
            log("resizeActiveSlot FAILED — am task resize rejected for $pkg ($profile); freeform likely not alive")
        }
    }

    /**
     * Apply saved bounds (+ density) for [pkg] under [profile] AFTER a verified landing (R6).
     *
     * Bounds are per-task (safe per-app). Density is display-global on Android 10 —
     * applying it here is "last edit wins" for the whole cluster display.
     * No-op when no profile is saved (caller then keeps the ratio/type default).
     */
    fun applySavedProfile(pkg: String, profile: CastProfile) {
        val savedConfig = prefs.displayConfigFor(pkg, profile) ?: return
        val bounds = savedConfig.bounds
        if (bounds != null) {
            val taskId = findTaskIdForPkg(pkg)
            if (taskId != null) {
                shell.execute("am task resize $taskId ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}")
            }
        }
        if (savedConfig.density != "reset") {
            shell.execute("wm density ${savedConfig.density} -d $displayId")
        }
    }

    /**
     * Set freeform boot flags. Read only at boot by ATMS.retrieveSettings (no ContentObserver),
     * so they activate after a physical power-cycle. Idempotent — safe to run on every open.
     */
    fun ensureFreeformFlags() {
        shell.execute("settings put global enable_freeform_support 1")
        shell.execute("settings put global force_resizable_activities 1")
    }

    /**
     * Probe whether freeform is alive by attempting a harmless `am task resize` on a cluster task.
     * Success (or no task) ⇒ freeform may be alive; rejection ⇒ not alive until next power-cycle.
     */
    fun isFreeformAlive(): Boolean {
        val stackResult = shell.execute("am stack list")
        if (!stackResult.success) return false
        val taskId = CastStackParser.parseTasks(stackResult.stdout)
            .firstOrNull { it.displayId == displayId }?.taskId ?: return false
        return shell.execute("am task resize $taskId 0 0 1920 720").success
    }

    /** True if [pkg] has a visible task on [targetDisplayId]. */
    fun isAppOnDisplay(pkg: String, targetDisplayId: Int): Boolean {
        val result = shell.execute("am stack list")
        if (!result.success) return false
        return CastStackParser.isAppOnDisplay(result.stdout, pkg, targetDisplayId)
    }

    /**
     * R4/R7: Verify the cluster display has (or can have) a fullscreen stack — not freeform-only.
     * Guards CP/AA (protected) casts away from a freeform-only display (surfaceflinger crash path).
     * Returns true when unsure (let the mover try) — false only when a freeform-only stack is proven.
     */
    fun verifyFullscreenStackAvailable(): Boolean {
        val stackCheck = shell.execute("am stack list")
        if (!stackCheck.success) return true // cannot verify → let mover try
        val hasStack = Regex("""Stack id=\d+.*displayId=$displayId""").containsMatchIn(stackCheck.stdout)
        if (!hasStack) return true // no stack yet → mover will create fullscreen
        val freeformOnly = stackCheck.stdout.lines().any { line ->
            line.contains("displayId=$displayId") && line.contains("windowingMode=5")
        } && !stackCheck.stdout.lines().any { line ->
            line.contains("displayId=$displayId") && line.contains("windowingMode=1")
        }
        if (freeformOnly) { log("CP/AA REJECTED: only freeform stack on display $displayId"); return false }
        return true
    }

    private fun persistBounds(pkg: String, profile: CastProfile, left: Int, top: Int, right: Int, bottom: Int) {
        val existing = prefs.displayConfigFor(pkg, profile) ?: DisplayConfig.NORMAL_DEFAULT
        prefs.saveDisplayConfig(pkg, profile, existing.copy(bounds = CastBounds(left, top, right, bottom)))
    }

    /** Query physical display size from `wm size -d <displayId>`. Returns WxH or null. */
    private fun queryDisplayPhysicalSize(): Pair<Int, Int>? {
        val result = shell.execute("wm size -d $displayId")
        if (!result.success) return null
        val match = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(result.stdout) ?: return null
        val w = match.groupValues[1].toIntOrNull() ?: return null
        val h = match.groupValues[2].toIntOrNull() ?: return null
        return w to h
    }
}
