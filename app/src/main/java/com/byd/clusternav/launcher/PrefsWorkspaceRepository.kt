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

    /** Hồ sơ trống (mọi ô Empty) → bố cục mặc định 3 widget (khớp `initialState()` cũ của KachiHomeActivity). */
    private fun defaultIfEmpty(ws: WorkspaceState): WorkspaceState =
        if (ws.slots.all { it is SlotContent.Empty }) DEFAULT_WORKSPACE else ws

    companion object {
        private val DEFAULT_WORKSPACE = WorkspaceState(
            LayoutPreset.THREE,
            listOf(
                SlotContent.Widget("w_board"),
                SlotContent.Widget("w_energy"),
                SlotContent.Widget("w_pm25"),
                SlotContent.Empty,
            ),
        )
    }
}
