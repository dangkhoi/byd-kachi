package com.byd.clusternav.launcher

import android.content.Context

/**
 * Lưu/khôi phục [WorkspaceState] + [DockConfig] theo HỒ SƠ TÀI XẾ (profile) + [ThemeMode] (chung), qua SharedPreferences.
 * Mỗi hồ sơ = một bố cục + thanh điều khiển riêng (khoá key theo tên hồ sơ). Off-car test được (thuần prefs).
 */
class WorkspacePrefs(context: Context) {
    private val sp = context.getSharedPreferences("kachi_workspace", Context.MODE_PRIVATE)

    // ── Hồ sơ tài xế ──
    fun profiles(): List<String> =
        sp.getString(K_PROFILES, null)?.split("\n")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?: listOf(DEFAULT_PROFILE)

    fun activeProfile(): String = sp.getString(K_ACTIVE, DEFAULT_PROFILE) ?: DEFAULT_PROFILE

    fun setActiveProfile(name: String) { sp.edit().putString(K_ACTIVE, name).apply() }

    fun addProfile(name: String) {
        val clean = name.trim().replace(Regex("[\\r\\n]"), " "); if (clean.isEmpty()) return
        val list = profiles().toMutableList()
        if (clean !in list) list.add(clean)
        sp.edit().putString(K_PROFILES, list.joinToString("\n")).putString(K_ACTIVE, clean).apply()
    }

    fun deleteProfile(name: String) {
        val list = profiles().toMutableList()
        if (list.size <= 1 || name !in list) return
        list.remove(name)
        val e = sp.edit().putString(K_PROFILES, list.joinToString("\n"))
        if (activeProfile() == name) e.putString(K_ACTIVE, list.first())
        e.apply()
    }

    private fun key(suffix: String) = "${activeProfile()}__$suffix"

    // ── Workspace (theo hồ sơ) ──
    fun load(): WorkspaceState {
        val preset = runCatching { LayoutPreset.valueOf(sp.getString(key("preset"), LayoutPreset.THREE.name)!!) }
            .getOrDefault(LayoutPreset.THREE)
        val slots = (0 until WorkspaceState.SLOT_CAP).map { decode(sp.getString(key("slot_$it"), "") ?: "") }
        return WorkspaceState(preset, slots)
    }

    fun save(state: WorkspaceState) {
        sp.edit().apply {
            putString(key("preset"), state.preset.name)
            state.slots.forEachIndexed { i, c -> putString(key("slot_$i"), encode(c)) }
            apply()
        }
    }

    // ── Dock (theo hồ sơ) ──
    fun loadDock(): DockConfig {
        val edge = runCatching { DockEdge.valueOf(sp.getString(key("dock_edge"), DockEdge.BOTTOM.name)!!) }
            .getOrDefault(DockEdge.BOTTOM)
        val enabled = sp.getString(key("dock_enabled"), null)?.split(",")?.filter { it.isNotBlank() }
            ?: ControlRegistry.defaultEnabledIds()
        return DockConfig(edge, enabled)
    }

    fun saveDock(c: DockConfig) {
        sp.edit()
            .putString(key("dock_edge"), c.edge.name)
            .putString(key("dock_enabled"), c.enabled.joinToString(","))
            .apply()
    }

    // ── Theme (chung mọi hồ sơ) ──
    fun themeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(sp.getString(K_THEME, ThemeMode.NIGHT.name)!!) }.getOrDefault(ThemeMode.NIGHT)

    fun setThemeMode(m: ThemeMode) { sp.edit().putString(K_THEME, m.name).apply() }

    // ── Launcher auto-start (chung mọi hồ sơ) — B6 ──
    // Nổ máy → Kachi tự làm setup KHÔNG cần bung view (seed freeform + đặt HOME + đảm bảo HOME lên để khôi phục ô).
    // Kill-switch của người dùng; MẶC ĐỊNH BẬT (launcher nên tự sẵn sàng). [com.byd.clusternav.KachiAutostart] đọc cờ này.
    fun launcherAutostart(): Boolean = sp.getBoolean(K_AUTOSTART, true)

    fun setLauncherAutostart(on: Boolean) { sp.edit().putBoolean(K_AUTOSTART, on).apply() }

    // ── App mở gần đây (chung mọi hồ sơ) — U3 ──
    // CHUNG chứ không theo hồ sơ: đây là lịch sử dùng máy, không phải bố cục của một tài xế (cùng cách với theme).
    fun recentApps(): List<String> = RecentApps.decode(sp.getString(K_RECENT, null))

    fun touchRecentApp(pkg: String) {
        sp.edit().putString(K_RECENT, RecentApps.encode(RecentApps.touch(recentApps(), pkg))).apply()
    }

    private fun encode(c: SlotContent): String = when (c) {
        SlotContent.Empty -> ""
        is SlotContent.App -> "app:${c.pkg}"
        is SlotContent.Widget -> "widget:${c.ids.joinToString(",")}"
    }

    private fun decode(s: String): SlotContent = when {
        s.startsWith("app:") -> SlotContent.App(s.removePrefix("app:"))
        s.startsWith("widget:") -> s.removePrefix("widget:").split(",").filter { it.isNotBlank() }
            .let { if (it.isEmpty()) SlotContent.Empty else SlotContent.Widget(it) }
        else -> SlotContent.Empty
    }

    companion object {
        const val DEFAULT_PROFILE = "Mặc định"
        private const val K_PROFILES = "profiles"
        private const val K_ACTIVE = "active_profile"
        private const val K_THEME = "theme_mode"
        private const val K_AUTOSTART = "launcher_autostart"
        private const val K_RECENT = "recent_apps"
    }
}
