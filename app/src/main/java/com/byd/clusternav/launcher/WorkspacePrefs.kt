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

    // ── Đơn vị hiển thị (chung mọi hồ sơ) — RW0/R11 ──
    // CHUNG chứ không theo hồ sơ: đơn vị là thói quen của người ĐỌC (cùng cách với theme). Chuỗi rỗng/rác ⇒ mặc định.
    fun unitPrefs(): UnitPrefs = UnitPrefs.decode(sp.getString(K_UNITS, null))

    fun setUnitPrefs(prefs: UnitPrefs) { sp.edit().putString(K_UNITS, prefs.encode()).apply() }

    /**
     * U4 — hình nền + trình chiếu. CHUNG mọi hồ sơ: hình nền là thứ nhìn thấy cả màn, không phải thuộc tính của một
     * hồ sơ (cùng lối với giao diện sáng/tối và đơn vị).
     */
    /** Bố cục tự vẽ của hồ sơ đang dùng. Rỗng = chưa vẽ ⇒ dùng bố cục sẵn. */
    /**
     * Bố cục tự vẽ của hồ sơ đang dùng. Rỗng = chưa vẽ ⇒ dùng bố cục sẵn.
     *
     * [SOÁT P2-3] **Lọc ngay ở cửa vào**: chuỗi lưu là dạng người đọc được (để cứu bằng tay), nên nó có thể bị sửa
     * thành số vô lý. Khung nằm ngoài lưới mà lọt vào trình vẽ thì **kéo một cái là sập** (phép kẹp có trần nhỏ hơn
     * sàn), và việc đếm ô trống chạy hàng tỉ nhịp làm treo giao diện. Màn hình thì đã có lưới an toàn (lùi về bố cục
     * sẵn), nhưng trình vẽ là nơi người dùng vào để **sửa** nên phải chặn ở đây.
     */
    fun gridLayout(): GridLayout {
        val raw = WorkspaceGrid.decode(sp.getString(key(K_GRID), null))
        val sane = raw.frames.filter {
            it.cols in WorkspaceGrid.MIN_COLS..WorkspaceGrid.COLS &&
                it.rows in WorkspaceGrid.MIN_ROWS..WorkspaceGrid.ROWS &&
                it.col in 0 until WorkspaceGrid.COLS && it.row in 0 until WorkspaceGrid.ROWS &&
                it.colEnd <= WorkspaceGrid.COLS && it.rowEnd <= WorkspaceGrid.ROWS
        }
        return if (sane.size == raw.frames.size) raw else GridLayout(sane)
    }

    fun setGridLayout(layout: GridLayout?) {
        sp.edit().apply {
            if (layout == null || layout.frames.isEmpty()) remove(key(K_GRID))
            else putString(key(K_GRID), WorkspaceGrid.encode(layout))
        }.apply()
    }

    fun wallpaperPrefs(): WallpaperPrefs = WallpaperPrefs.decode(sp.getString(K_WALL, null))

    fun setWallpaperPrefs(prefs: WallpaperPrefs) { sp.edit().putString(K_WALL, prefs.encode()).apply() }

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
        private const val K_UNITS = "unit_prefs"
        private const val K_WALL = "wallpaper_prefs"

    /**
     * P9 — bố cục tự vẽ, lưu THEO HỒ SƠ (mỗi tài xế có bố cục riêng, giống thanh nút). Chuỗi tự đọc được
     * (`0,0,7,4;7,0,5,6`) để cứu bằng tay được nếu cần.
     */
    private const val K_GRID = "grid_layout"
    }
}
