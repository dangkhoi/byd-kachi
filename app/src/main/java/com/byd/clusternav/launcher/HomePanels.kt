package com.byd.clusternav.launcher

import android.app.Activity
import android.widget.FrameLayout
import com.byd.clusternav.Prefs
import com.byd.clusternav.comfort.RecircApplier

/**
 * HAI BẢNG PHỦ TOÀN MÀN của HOME: **màn Cài đặt** ([SettingsPanel] — S1, gộp bảng "Tuỳ biến" cũ) và **bảng vẽ bố
 * cục** (P9). Tách khỏi [KachiHomeActivity] cùng lý do với [WallpaperController]: Activity vượt **trần 500 dòng** của
 * dự án, còn hai bảng này là một mảng liền mạch (mở/đóng lớp phủ trên `rootFrame`, cùng vòng đời).
 *
 * Nhận **cổng vào bằng lambda** như [DrawerController] / [LauncherWindows] — không tự biết Activity đang giữ gì.
 * Mọi thay đổi bền đi qua ViewModel (một chiều): lớp này KHÔNG ghi bền, chỉ gọi các intent được truyền vào. Ngoại lệ
 * duy nhất là ô tick lấy gió trong — khoá đó thuộc `Prefs` của ClusterNav, không thuộc `WorkspacePrefs`, nên nó không
 * có intent ViewModel để đi qua.
 *
 * @param onApplyLayout ghi bố cục tự vẽ vào nguồn sự thật (`null` = quay về bố cục sẵn).
 * @param onPreset chọn bố cục sẵn — **cùng** đường với 5 nút ở thanh trên (§4.5: nhiều bề mặt, một đường).
 * @param onWallpaper / [onUnitPrefs] intent lưu + áp lại tương ứng.
 * @param onAddProfile mở hộp thoại tạo hồ sơ — dùng LẠI `ProfileBar.addDialog()`, không dựng hộp thoại thứ hai.
 * @param shellUsable có kênh shell hay không — để bảng quyền nói đúng bức tranh.
 * @param goImmersive khôi phục chế độ toàn màn sau khi lớp phủ đóng (bàn phím/dialog làm mất cờ).
 */
class HomePanels(
    private val activity: Activity,
    private val rootFrame: FrameLayout,
    private val state: () -> HomeUiState,
    private val onToggleDock: (String, Boolean) -> Unit,
    private val onApplyLayout: (GridLayout?) -> Unit,
    private val onPreset: (LayoutPreset) -> Unit,
    private val onDockEdge: (DockEdge) -> Unit,
    private val onTopStrip: (String, Boolean) -> Unit,
    private val onWallpaper: (WallpaperPrefs) -> Unit,
    private val onUnitPrefs: (UnitPrefs) -> Unit,
    private val onAutostart: (Boolean) -> Unit,
    private val onSwitchProfile: (String) -> Unit,
    private val onAddProfile: () -> Unit,
    private val onDeleteProfile: (String) -> Unit,
    private val onOpenClusterNav: () -> Unit,
    private val shellUsable: () -> Boolean,
    private val goImmersive: () -> Unit,
) {
    private var settingsPanel: SettingsPanel? = null
    private var layoutPanel: LayoutEditorPanel? = null

    /** Có lớp phủ nào đang mở — để Back đóng đúng lớp trên cùng. */
    fun layoutOpen(): Boolean = layoutPanel != null
    fun settingsOpen(): Boolean = settingsPanel != null

    // ── Bảng vẽ bố cục (P9) ─────────────────────────────────────────────────────────────────────

    fun openLayoutEditor() {
        if (layoutPanel != null) return
        val panel = LayoutEditorPanel(
            activity,
            initial = state().customLayout ?: GridLayout(emptyList()),
            fallbackPreset = state().preset,
            onSave = { l -> onApplyLayout(l) },
            onClear = { onApplyLayout(null) },
            onClose = { closeLayoutEditor() },
        )
        layoutPanel = panel
        rootFrame.addView(
            panel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        goImmersive()
    }

    fun closeLayoutEditor() {
        layoutPanel?.let { rootFrame.removeView(it) }
        layoutPanel = null
        goImmersive()
    }

    // ── Màn Cài đặt (S1) ────────────────────────────────────────────────────────────────────────

    fun openSettings() {
        if (settingsPanel != null) return
        val deps = SettingsDeps(
            state = state,
            // P8: đọc MỚI mỗi lượt dựng trang — quyền có thể vừa được tự cấp xong ở nhịp khởi động.
            permissions = { PermissionPreflight.check(activity, shellUsable = shellUsable()) },
            // U4: nói CHỖ bỏ ảnh vào — người dùng không có cách nào tự đoán, và màn chọn tệp của hệ thống bị khoá trên xe.
            wallpaperFolderHint = WallpaperStore.folderHint(activity),
            recircOnStart = { Prefs.recircOnStartEnabled(activity) },
            // W3: ô tick tự lấy gió trong. Bật ⇒ áp NGAY (không chờ lần nổ máy sau); tắt ⇒ CHỈ đặt lại cờ, KHÔNG
            // tắt chế độ đang bật trên xe (người dùng có thể đang muốn dùng, chỉ là không muốn tự bật nữa).
            onRecircOnStart = { on ->
                Prefs.setRecircOnStartEnabled(activity, on)
                if (on) RecircApplier.applyNowAsync(activity)
            },
            onPreset = { p -> onPreset(p) },
            // P9: đường mở bảng vẽ bố cục (đóng màn Cài đặt trước — hai lớp phủ chồng nhau thì Back mất nghĩa).
            onOpenLayoutEditor = { closeSettings(); openLayoutEditor() },
            onWallpaper = { p -> onWallpaper(p) },
            onTopStrip = { id, on -> onTopStrip(id, on) },
            onToggleDock = { id, on -> onToggleDock(id, on) },   // state+persist → collector: dock.setConfig
            onDockEdge = { e -> onDockEdge(e) },
            // R11: đổi đơn vị ⇒ lưu bền + áp lại NGAY cho cả thanh nút và ô giữa màn (không cần mở lại app).
            onUnitPrefs = { prefs -> onUnitPrefs(prefs) },
            onAutostart = { on -> onAutostart(on) },
            onSwitchProfile = { name -> onSwitchProfile(name) },
            onAddProfile = onAddProfile,
            onDeleteProfile = { name -> onDeleteProfile(name) },
            onOpenClusterNav = onOpenClusterNav,
        )
        val panel = SettingsPanel(activity, deps) { closeSettings() }
        settingsPanel = panel
        rootFrame.addView(
            panel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }

    fun closeSettings() {
        settingsPanel?.let { rootFrame.removeView(it) }
        settingsPanel = null
    }

    /**
     * Trang đã dựng của màn Cài đặt đang cũ ⇒ bỏ hết và dựng lại trang đang xem.
     *
     * Gọi từ `render` khi danh sách/hồ sơ đang dùng đổi: đổi hồ sơ nạp lại **toàn bộ** (bố cục · thanh nút · chip ·
     * hình nền · đơn vị) nên mọi trang đều cũ. Đi theo đường một chiều (state đổi → render → gọi vào đây) thay vì để
     * bảng tự đi thu thay đổi.
     */
    fun invalidateSettings() = settingsPanel?.invalidateAll()

    /** Đóng mọi lớp phủ — gọi lúc huỷ màn (lớp phủ giữ view là giữ activity). */
    fun closeAll() {
        closeSettings()
        closeLayoutEditor()
    }
}
