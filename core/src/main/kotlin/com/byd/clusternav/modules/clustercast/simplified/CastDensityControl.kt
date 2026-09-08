package com.byd.clusternav.modules.clustercast.simplified

/**
 * Density control for the cluster display. Extracted from SimpleCastCoordinator
 * to keep coordinator under 500 LOC.
 *
 * R6: Persists density ONLY after successful shell application.
 * If `wm density` fails, prior saved value is preserved (no corruption).
 */
internal object CastDensityControl {

    /**
     * Set or reset cluster display density. Saves per-app ONLY if shell succeeds.
     * @param dpi density value, or null to reset.
     * @param activePkg the currently casting full-mode package (for per-app save), or null.
     */
    fun set(shell: SimpleCastShell, prefs: SimpleCastPrefs, displayId: Int, dpi: Int?, activePkg: String?) {
        val success = applyDensity(shell, displayId, dpi)
        if (success && activePkg != null) {
            saveForPkg(prefs, activePkg, dpi)
        }
    }

    /**
     * Split-mode density (R4 / owner bug #5). `wm density` is display-global on Android 10, so it
     * is applied once; on success the value is persisted under the **per-ratio profile key**
     * ([CastProfile.of] with the current leftPercent) of EVERY occupied slot — the SAME key
     * [bounds][DisplayConfig.bounds] use — so re-casting either app at this ratio restores its DPI.
     *
     * This closes the gap behind "DPI not saved after adjusting per ratio": the split DPI control
     * previously routed through [set] with a null active package (state is CastingSplit, not
     * CastingFull), so nothing was ever written. No-op unless [state] is [SimpleCastState.CastingSplit].
     * Persists ONLY on shell success.
     */
    fun setForSplit(
        shell: SimpleCastShell,
        prefs: SimpleCastPrefs,
        displayId: Int,
        dpi: Int?,
        state: SimpleCastState,
    ) {
        val split = state as? SimpleCastState.CastingSplit ?: return
        if (!applyDensity(shell, displayId, dpi)) return
        val leftPercent = prefs.splitRatioLeftPercent()
        split.left?.let { saveForProfile(prefs, it.pkg, CastProfile.of(ClusterSlotSide.LEFT, leftPercent), dpi) }
        split.right?.let { saveForProfile(prefs, it.pkg, CastProfile.of(ClusterSlotSide.RIGHT, leftPercent), dpi) }
    }

    private fun applyDensity(shell: SimpleCastShell, displayId: Int, dpi: Int?): Boolean {
        val result = if (dpi != null && dpi in 80..640) {
            shell.execute("wm density $dpi -d $displayId")
        } else {
            shell.execute("wm density reset -d $displayId")
        }
        return result.success
    }

    private fun saveForPkg(prefs: SimpleCastPrefs, pkg: String, dpi: Int?) {
        // Seed bounds-less: a DPI-only change must never STAMP a size onto an app that was never
        // resized (NORMAL_DEFAULT.bounds is full-cluster). Otherwise the next cast would resize it.
        val existing = prefs.displayConfigFor(pkg) ?: DisplayConfig.NORMAL_DEFAULT.copy(bounds = null)
        prefs.saveDisplayConfig(pkg, existing.copy(density = dpi?.toString() ?: "reset"))
    }

    /** Persist [dpi] under the exact ([pkg], [profile]) geometry key — same key bounds use (R4/#5). */
    private fun saveForProfile(prefs: SimpleCastPrefs, pkg: String, profile: CastProfile, dpi: Int?) {
        // Seed bounds-less (see [saveForPkg]): a split slot whose DPI is changed before it is resized
        // must NOT inherit NORMAL_DEFAULT's full-cluster bounds, or applySavedProfile would blow the
        // slot up to the whole display on re-cast and destroy the split layout.
        val existing = prefs.displayConfigFor(pkg, profile) ?: DisplayConfig.NORMAL_DEFAULT.copy(bounds = null)
        prefs.saveDisplayConfig(pkg, profile, existing.copy(density = dpi?.toString() ?: "reset"))
    }
}
