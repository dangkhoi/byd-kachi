package com.byd.clusternav.modules.clustercast.simplified

/**
 * Density control for the cluster display. Extracted from SimpleCastCoordinator
 * to keep coordinator under 500 LOC.
 *
 * R6: Persists density ONLY after successful shell application.
 * If `wm density` fails, prior saved value is preserved (no corruption).
 *
 * V-CLUSTER · VC-R6: hai hàm trả **đúng chuỗi DPI đã áp** (`"240"` / `"reset"`) khi shell OK, `null` khi hỏng — chỗ gọi
 * ([SimpleCastCoordinator.setDensity]/[SimpleCastCoordinator.setDensitySplit]) cập nhật trường DPI của bản ghim phiên
 * bằng ĐÚNG giá trị đó (sự thật vừa áp), không đọc lại prefs (prefs là hồ sơ ĐANG DÙNG, có thể đã đổi giữa phiên).
 */
internal object CastDensityControl {

    /**
     * Set or reset cluster display density. Saves per-app ONLY if shell succeeds.
     * @param dpi density value, or null to reset.
     * @param activePkg the currently casting full-mode package (for per-app save), or null.
     * @param style B1b — kiểu khung của PHIÊN: Chữ nhật lưu vào khoá `__RECT`, Bo tròn = khoá FULL cũ.
     * @return chuỗi DPI đã áp khi shell OK (kể cả khi không có [activePkg] để lưu), `null` khi hỏng.
     */
    fun set(
        shell: SimpleCastShell,
        prefs: SimpleCastPrefs,
        displayId: Int,
        dpi: Int?,
        activePkg: String?,
        style: CastStyle = CastStyle.CURVED,
    ): String? {
        val applied = applyDensity(shell, displayId, dpi) ?: return null
        if (activePkg != null) saveForProfile(prefs, activePkg, CastProfile.FULL.inStyle(style), dpi)
        return applied
    }

    /**
     * Split-mode density (R4 / owner bug #5). `wm density` is display-global on Android 10, so it
     * is applied once; on success the value is persisted under the **per-ratio profile key**
     * ([CastProfile.of] with the SESSION's leftPercent) of EVERY occupied slot — the SAME key
     * [bounds][DisplayConfig.bounds] use — so re-casting either app at this ratio restores its DPI.
     *
     * This closes the gap behind "DPI not saved after adjusting per ratio": the split DPI control
     * previously routed through [set] with a null active package (state is CastingSplit, not
     * CastingFull), so nothing was ever written. No-op unless [state] is [SimpleCastState.CastingSplit].
     * Persists ONLY on shell success.
     *
     * V-CLUSTER · sửa refute C2: tỉ lệ lấy từ [SimpleCastState.CastingSplit.leftPercent] (tỉ lệ ĐANG chia trên cụm), không
     * đọc `prefs.splitRatioLeftPercent()` — đổi hồ sơ giữa lúc chia đôi thì prefs mang tỉ lệ của hồ sơ mới, và DPI sẽ bị
     * lưu nhầm vào ô nhớ của một tỉ lệ không có trên cụm.
     */
    fun setForSplit(
        shell: SimpleCastShell,
        prefs: SimpleCastPrefs,
        displayId: Int,
        dpi: Int?,
        state: SimpleCastState,
        style: CastStyle = CastStyle.CURVED,
    ): String? {
        val split = state as? SimpleCastState.CastingSplit ?: return null
        val applied = applyDensity(shell, displayId, dpi) ?: return null
        val leftPercent = split.leftPercent
        split.left?.let { saveForProfile(prefs, it.pkg, CastProfile.of(ClusterSlotSide.LEFT, leftPercent, style), dpi) }
        split.right?.let { saveForProfile(prefs, it.pkg, CastProfile.of(ClusterSlotSide.RIGHT, leftPercent, style), dpi) }
        return applied
    }

    /** Chuỗi DPI đã áp khi shell OK (`"<dpi>"` hoặc `"reset"` — đúng nhánh lệnh đã chạy), `null` khi hỏng. */
    private fun applyDensity(shell: SimpleCastShell, displayId: Int, dpi: Int?): String? {
        val result = if (dpi != null && dpi in CastGeometryGuard.DENSITY_RANGE) {
            shell.execute("wm density $dpi -d $displayId")
        } else {
            shell.execute("wm density reset -d $displayId")
        }
        if (!result.success) return null
        return if (dpi != null && dpi in CastGeometryGuard.DENSITY_RANGE) dpi.toString() else CastGeometryGuard.DENSITY_RESET
    }

    /**
     * Persist [dpi] under the exact ([pkg], [profile]) geometry key — same key bounds use (R4/#5). B1b: FULL đi cùng đường
     * (trước là `saveForPkg` với hai hàm không hồ sơ — cùng khoá FULL từng byte, `SimpleCastPrefs.displayConfigFor(pkg)` =
     * hồ sơ FULL), để khoá `__RECT` của cụm Chữ nhật không phải viết một đường thứ hai.
     */
    private fun saveForProfile(prefs: SimpleCastPrefs, pkg: String, profile: CastProfile, dpi: Int?) {
        // Seed bounds-less: a DPI-only change must never STAMP a size onto an app that was never
        // resized (NORMAL_DEFAULT.bounds is full-cluster). Otherwise the next cast would resize it —
        // and a split slot would blow up to the whole display on re-cast and destroy the split layout.
        val existing = prefs.displayConfigFor(pkg, profile) ?: DisplayConfig.NORMAL_DEFAULT.copy(bounds = null)
        prefs.saveDisplayConfig(pkg, profile, existing.copy(density = dpi?.toString() ?: "reset"))
    }
}
