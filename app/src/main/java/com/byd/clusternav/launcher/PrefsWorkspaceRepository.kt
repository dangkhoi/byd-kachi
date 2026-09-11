package com.byd.clusternav.launcher

import android.content.Context

/**
 * Bản thật của [WorkspaceRepository] cho :app — bọc [WorkspacePrefs] (SharedPreferences, khoá theo hồ sơ tài xế).
 * Chạm [Context] nên KHÔNG phải "file thuần" (LayeringRules không tính vào `pureFilesStillInApp`).
 *
 * Áp quy tắc bố cục mặc định (3 widget) khi hồ sơ trống — chuyển logic `initialState()` cũ từ Activity vào tầng dữ liệu,
 * để [HomeViewModel] chỉ cần `load()`. KHÔNG tự ghi bền lúc load (giống cũ: mặc định chỉ hiện, được ghi khi user chạm).
 */
class PrefsWorkspaceRepository(context: Context) : WorkspaceRepository {

    private val prefs = WorkspacePrefs(context.applicationContext)

    override fun load(): HomeUiState = HomeUiState(
        workspace = defaultIfEmpty(prefs.load()),
        dock = prefs.loadDock(),
        activeProfile = prefs.activeProfile(),
        profiles = prefs.profiles(),
        themeMode = prefs.themeMode(),
        embedded = false,
        // [SOÁT P1-1 kiến trúc] Ba nhóm này nằm ở KHOÁ RIÊNG (không đi qua `persist`) nhưng vẫn phải có mặt trong
        // state ngay từ lượt nạp. Nạp ở đây thì ca **đổi hồ sơ** tự đúng: `switchProfile` gọi lại `load()` nên bố cục
        // tự vẽ của hồ sơ mới được nạp cùng lúc với mọi thứ khác — trước đây phải nhớ nạp lại bằng tay ở tầng UI
        // (và đã từng quên, làm hồ sơ B hiện bố cục của A rồi bấm Lưu là ghi đè mất bố cục của B).
        customLayout = prefs.gridLayout().takeIf { it.frames.isNotEmpty() },
        unitPrefs = prefs.unitPrefs(),
        wallpaper = prefs.wallpaperPrefs(),
    )

    override fun persist(state: HomeUiState) {
        prefs.setActiveProfile(state.activeProfile)
        prefs.save(state.workspace)
        prefs.saveDock(state.dock)
        prefs.setThemeMode(state.themeMode)
    }

    override fun switchProfile(name: String): HomeUiState {
        prefs.setActiveProfile(name)
        return load()
    }

    override fun addProfile(name: String): HomeUiState {
        prefs.addProfile(name)
        return load()
    }

    override fun deleteProfile(name: String): HomeUiState {
        prefs.deleteProfile(name)
        return load()
    }

    override fun recentApps(): List<String> = prefs.recentApps()

    override fun touchRecentApp(pkg: String) = prefs.touchRecentApp(pkg)

    override fun unitPrefs(): UnitPrefs = prefs.unitPrefs()

    // Tên tham số KHÔNG đặt là `prefs`: field `prefs` (WorkspacePrefs) sẽ bị che, phải viết `this.prefs` mới đúng —
    // đọc dễ tưởng gọi đệ quy.
    override fun setUnitPrefs(units: UnitPrefs) = prefs.setUnitPrefs(units)

    override fun gridLayout(): GridLayout = prefs.gridLayout()

    override fun setGridLayout(layout: GridLayout?) = prefs.setGridLayout(layout)

    override fun wallpaperPrefs(): WallpaperPrefs = prefs.wallpaperPrefs()

    override fun setWallpaperPrefs(wall: WallpaperPrefs) = prefs.setWallpaperPrefs(wall)

    /** Hồ sơ trống (mọi ô Empty) → bố cục mặc định 3 widget (khớp `initialState()` cũ của KachiHomeActivity). */
    private fun defaultIfEmpty(ws: WorkspaceState): WorkspaceState =
        if (ws.slots.all { it is SlotContent.Empty }) DEFAULT_WORKSPACE else ws

    companion object {
        // Bố cục mặc định nay ở :core (WorkspaceState.DEFAULT) để KIỂM ĐƯỢC off-car. Trước đây nó là danh sách
        // CỨNG 4 phần tử ở đây, nên khi nới trần ô 4 → 6 nó ném lỗi NGAY LÚC NẠP LỚP ⇒ launcher sập ở lần chạy đầu
        // (lúc chưa có cấu hình để nạp). Không test nào bắt được vì lớp này cần Android.
        private val DEFAULT_WORKSPACE = WorkspaceState.DEFAULT
    }
}
