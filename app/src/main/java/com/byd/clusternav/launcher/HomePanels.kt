package com.byd.clusternav.launcher

import android.app.Activity
import android.widget.FrameLayout
import com.byd.clusternav.Prefs
import com.byd.clusternav.comfort.RecircApplier

/**
 * HAI BẢNG PHỦ TOÀN MÀN của HOME: **Tuỳ biến** (chọn khả năng cho thanh nút + đơn vị + hình nền + quyền) và **bảng
 * vẽ bố cục** (P9). Tách khỏi [KachiHomeActivity] cùng lý do với [WallpaperController]: Activity vượt **trần 500
 * dòng** của dự án, còn hai bảng này là một mảng liền mạch (mở/đóng lớp phủ trên `rootFrame`, cùng vòng đời).
 *
 * Nhận **cổng vào bằng lambda** như [DrawerController] / [LauncherWindows] — không tự biết Activity đang giữ gì.
 * Mọi thay đổi bền đi qua ViewModel (một chiều): lớp này KHÔNG ghi bền, chỉ gọi các intent được truyền vào.
 *
 * @param onApplyLayout ghi bố cục tự vẽ vào nguồn sự thật (`null` = quay về bố cục sẵn).
 * @param onWallpaper / [onUnitPrefs] intent lưu + áp lại tương ứng.
 * @param shellUsable có kênh shell hay không — để bảng quyền nói đúng bức tranh.
 * @param goImmersive khôi phục chế độ toàn màn sau khi lớp phủ đóng (bàn phím/dialog làm mất cờ).
 */
class HomePanels(
    private val activity: Activity,
    private val rootFrame: FrameLayout,
    private val state: () -> HomeUiState,
    private val onToggleDock: (String, Boolean) -> Unit,
    private val onApplyLayout: (GridLayout?) -> Unit,
    private val onTopStrip: (String, Boolean) -> Unit,
    private val onWallpaper: (WallpaperPrefs) -> Unit,
    private val onUnitPrefs: (UnitPrefs) -> Unit,
    private val shellUsable: () -> Boolean,
    private val goImmersive: () -> Unit,
) {
    private var customizePanel: CustomizePanel? = null
    private var layoutPanel: LayoutEditorPanel? = null

    /** Có lớp phủ nào đang mở — để Back đóng đúng lớp trên cùng. */
    fun layoutOpen(): Boolean = layoutPanel != null
    fun customizeOpen(): Boolean = customizePanel != null

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

    // ── Bảng Tuỳ biến ───────────────────────────────────────────────────────────────────────────

    fun openCustomize() {
        if (customizePanel != null) return
        val panel = CustomizePanel(
            activity,
            enabledIds = state().dock.enabled,
            onToggle = { id, on -> onToggleDock(id, on) },   // state+persist → collector: dock.setConfig
            onClose = { closeCustomize() },
            // W3: ô tick tự lấy gió trong. Bật ⇒ áp NGAY (không chờ lần nổ máy sau); tắt ⇒ CHỈ đặt lại cờ, KHÔNG
            // tắt chế độ đang bật trên xe (người dùng có thể đang muốn dùng, chỉ là không muốn tự bật nữa).
            recircOnStart = Prefs.recircOnStartEnabled(activity),
            onRecircOnStart = { on ->
                Prefs.setRecircOnStartEnabled(activity, on)
                if (on) RecircApplier.applyNowAsync(activity)
            },
            // R11: đổi đơn vị ⇒ lưu bền + áp lại NGAY cho cả thanh nút và ô giữa màn (không cần mở lại app).
            unitPrefs = state().unitPrefs,
            // P8: bảng Tuỳ biến là chỗ xem ĐỦ bức tranh quyền (thông báo chỉ nói mục ảnh hưởng tính năng lõi).
            permissions = PermissionPreflight.check(activity, shellUsable = shellUsable()),
            // U4: nói CHỖ bỏ ảnh vào — người dùng không có cách nào tự đoán, và màn chọn tệp của hệ thống bị khoá trên xe.
            wallpaper = state().wallpaper,
            wallpaperFolderHint = WallpaperStore.folderHint(activity),
            // P9: đường mở bảng vẽ bố cục + nói người dùng đang dùng bố cục nào.
            onOpenLayoutEditor = { closeCustomize(); openLayoutEditor() },
            layoutSummary = state().customLayout?.let {
                "Đang dùng bố cục tự vẽ: ${it.frames.size} khung" +
                    (EffectiveLayout.ignoredReason(it)?.let { r -> " — nhưng bị bỏ qua ($r)" } ?: "")
            } ?: "",
            // RW0 vùng thứ ba: chip thanh trên.
            topStrip = state().topStrip,
            onTopStrip = { id, on -> onTopStrip(id, on) },
            onWallpaper = { p -> onWallpaper(p) },
            onUnitPrefs = { prefs -> onUnitPrefs(prefs) },
        )
        customizePanel = panel
        rootFrame.addView(
            panel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }

    fun closeCustomize() {
        customizePanel?.let { rootFrame.removeView(it) }
        customizePanel = null
    }

    /** Đóng mọi lớp phủ — gọi lúc huỷ màn (lớp phủ giữ view là giữ activity). */
    fun closeAll() {
        closeCustomize()
        closeLayoutEditor()
    }
}
