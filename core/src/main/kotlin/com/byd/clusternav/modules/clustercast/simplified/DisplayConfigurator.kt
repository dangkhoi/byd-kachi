package com.byd.clusternav.modules.clustercast.simplified

/**
 * Configures the cluster display's wm size and overscan per app-type.
 *
 * Tracks current applied config to avoid redundant shell commands.
 * Field-proven values measured on vehicle 2026-08-02.
 */
class DisplayConfigurator(
    private val shell: SimpleCastShell,
    private val log: (String) -> Unit = { println("[DisplayConfigurator] $it") },
) {
    @Volatile
    var currentConfig: DisplayConfig? = null
        private set

    /**
     * Applies display config for the given app type.
     * Skips if the same config is already applied.
     *
     * @return true on success, false on shell failure.
     */
    fun apply(displayId: Int, config: DisplayConfig): Boolean {
        if (config == currentConfig) return true
        // V-CLUSTER · VC-R4 — CHỐT CUỐI ngay trước nội suy: ba chuỗi dưới đây đi NGUYÊN VĂN vào shell, và từ V-CLUSTER
        // chúng có thể đến từ một tệp hồ sơ người khác gửi. Lượt đọc đã kiểm (lớp 3), nhưng chốt ở TẦNG THI HÀNH mới là
        // chốt không đường vòng (CLAUDE.md §5): cấu hình không sạch ⇒ từ chối cả lượt, 0 lệnh.
        if (!CastGeometryGuard.isShellSafe(config)) {
            log("TỪ CHỐI cấu hình không sạch trước shell: size='${config.wmSize.take(24)}' — 0 lệnh wm")
            return false
        }

        // Set wm size
        val sizeResult = shell.execute("wm size ${config.wmSize} -d $displayId")
        if (!sizeResult.success) return false

        // Set overscan
        val overscanResult = shell.execute("wm overscan ${config.overscan} -d $displayId")
        if (!overscanResult.success) return false

        // Set density (reset or specific value)
        if (config.density != "reset") {
            val densityResult = shell.execute("wm density ${config.density} -d $displayId")
            if (!densityResult.success) return false
        } else {
            val densityResult = shell.execute("wm density reset -d $displayId")
            if (!densityResult.success) return false
        }

        currentConfig = config
        return true
    }

    /**
     * Resets all display settings to default.
     */
    fun reset(displayId: Int): Boolean {
        val r1 = shell.execute("wm size reset -d $displayId")
        val r2 = shell.execute("wm overscan reset -d $displayId")
        val r3 = shell.execute("wm density reset -d $displayId")
        currentConfig = null
        return r1.success && r2.success && r3.success
    }

    /**
     * Resolves the effective DisplayConfig for a package:
     * - CP/AA → fixed config from constants
     * - Normal → user-saved config or default
     */
    fun resolveConfig(pkg: String, appType: AppType, prefs: SimpleCastPrefs): DisplayConfig {
        return when (appType) {
            AppType.CARPLAY -> DisplayConfig.CARPLAY
            AppType.ANDROID_AUTO -> DisplayConfig.ANDROID_AUTO
            AppType.NORMAL -> prefs.displayConfigFor(pkg) ?: DisplayConfig.NORMAL_DEFAULT
        }
    }
}
