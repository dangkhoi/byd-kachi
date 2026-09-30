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
    // X2 — provider (KHÔNG phải Int cố định): id display cụm được dò động ở coordinator và có thể đổi trong
    // phiên, nên geometry phải đọc giá trị SỐNG mỗi lần dùng thay vì chụp lúc dựng.
    private val displayIdProvider: () -> Int,
    private val log: (String) -> Unit = { println("[CastGeometry] $it") },
) {
    private val displayId: Int get() = displayIdProvider()

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
     *
     * @return true khi một trong hai tầng áp được (V-CLUSTER · VC-R6: chỗ gọi cập nhật bản ghim của phiên CHỈ khi true).
     */
    fun resizeFull(pkg: String, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val taskId = findTaskIdForPkg(pkg) ?: return false
        val result = shell.execute("am task resize $taskId $left $top $right $bottom")
        if (result.success) {
            persistBounds(pkg, CastProfile.FULL, left, top, right, bottom)
            return true
        }
        // Fallback: change logical display size, keeping height to avoid letterbox.
        val (physW, physH) = queryDisplaySize(preferOverride = false) ?: (1920 to 720)
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
            return true
        }
        log("resizeActiveTarget FAILED — both task resize and wm size failed")
        return false
    }

    /**
     * Resize a split slot's [pkg] via `am task resize` and persist to [profile] on success (R5/R6).
     *
     * No `wm size` fallback: split geometry needs per-task bounds (freeform), and `wm size`
     * is display-global — it cannot place two apps in two halves. If the resize is rejected,
     * freeform is not alive (needs a one-time power-cycle) and nothing is persisted.
     *
     * @return true khi `am task resize` thành công (chỗ gọi cập nhật bản ghim của phiên CHỈ khi true).
     */
    fun resizeSlot(pkg: String, profile: CastProfile, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val taskId = findTaskIdForPkg(pkg) ?: return false
        val result = shell.execute("am task resize $taskId $left $top $right $bottom")
        if (result.success) {
            persistBounds(pkg, profile, left, top, right, bottom)
            return true
        }
        log("resizeActiveSlot FAILED — am task resize rejected for $pkg ($profile); freeform likely not alive")
        return false
    }

    /**
     * Áp bản ghi hình học [pinned] ĐÃ GHIM của phiên cho [pkg] — sau một lượt đặt đã xác minh (R6) và ở mọi lượt repin.
     *
     * V-CLUSTER · VC-R6 (thay `applySavedProfile`, hàm cũ đọc lại prefs mỗi lần gọi): hàm này **không đọc prefs**. Prefs là
     * hồ sơ ĐANG DÙNG — đổi hồ sơ giữa phiên rồi repin mà đọc lại prefs là DPI/khung của hồ sơ mới tự nổ lên cụm, không
     * ai bấm (refute B6). Bản ghim được đọc MỘT lần lúc phiên bắt đầu (`CastSessionPin.kt`).
     *
     * Ba chốt trước khi chạm shell, theo thứ tự:
     *  1. `pinned == null` ⇒ không có gì để áp (giữ nguyên no-op của hàm cũ khi chưa lưu gì);
     *  2. **chốt cuối** [CastGeometryGuard.isShellSafe] (VC-R4) — bản ghi đi theo hồ sơ ⇒ theo tệp xuất/nhập: không sạch ⇒ 0 lệnh;
     *  3. khung được KẸP vào khung logic ĐO ĐƯỢC của VD (`wm size -d`, ưu tiên *Override* — khung `am task resize` dùng),
     *     đo hụt ⇒ 1920×720. Một khung lưu cho cụm khác kích thước (tệp nhập từ xe khác) không đẩy task ra ngoài màn.
     *
     * Bounds are per-task (safe per-app). Density is display-global on Android 10 — "last edit wins" for the display.
     */
    fun applyPinned(pkg: String, pinned: DisplayConfig?) {
        if (pinned == null) return
        if (!CastGeometryGuard.isShellSafe(pinned)) {
            log("applyPinned: TỪ CHỐI cấu hình không sạch của $pkg — 0 lệnh")
            return
        }
        val bounds = pinned.bounds
        if (bounds != null) {
            val taskId = findTaskIdForPkg(pkg)
            if (taskId != null) {
                val (w, h) = queryDisplaySize(preferOverride = true) ?: (1920 to 720)
                val b = CastGeometryGuard.clampBounds(bounds, 0, w, h)
                shell.execute("am task resize $taskId ${b.left} ${b.top} ${b.right} ${b.bottom}")
            }
        }
        if (pinned.density != CastGeometryGuard.DENSITY_RESET) {
            shell.execute("wm density ${pinned.density} -d $displayId")
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

    /**
     * Kích VD cụm từ `wm size -d <displayId>` (CHỈ ĐỌC). [preferOverride] = false ⇒ *Physical* (biên kẹp của đường lùi
     * `wm size`, hành vi cũ); true ⇒ *Override* nếu có — khung logic đang hiệu lực mà `am task resize` dùng
     * ([applyPinned]). Phép parse ở [CastGeometryGuard.parseDisplaySize]. `null` = đo hụt.
     */
    private fun queryDisplaySize(preferOverride: Boolean): Pair<Int, Int>? {
        val result = shell.execute("wm size -d $displayId")
        if (!result.success) return null
        return CastGeometryGuard.parseDisplaySize(result.stdout, preferOverride)
    }
}
