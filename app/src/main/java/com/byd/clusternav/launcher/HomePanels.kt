package com.byd.clusternav.launcher

import android.app.Activity
import android.widget.FrameLayout

/**
 * HAI BẢNG PHỦ TOÀN MÀN của HOME: **màn Cài đặt** ([SettingsPanel] — S1, gộp bảng "Tuỳ biến" cũ) và **bảng vẽ bố
 * cục** (P9). Tách khỏi [KachiHomeActivity] cùng lý do với [WallpaperController]: Activity vượt **trần 500 dòng** của
 * dự án, còn hai bảng này là một mảng liền mạch (mở/đóng lớp phủ trên `rootFrame`, cùng vòng đời).
 *
 * Nhận **cổng vào bằng lambda** như [DrawerController] / [LauncherWindows] — không tự biết Activity đang giữ gì.
 * Mọi thay đổi bền của phía launcher đi qua ViewModel (một chiều): lớp này KHÔNG ghi bền, chỉ gọi các intent được
 * truyền vào. Phía ClusterNav đi qua [ClusterNavBridge] — lớp này chỉ **chuyển tiếp** cầu đó xuống [SettingsDeps],
 * không gọi một hàm nào của nó.
 *
 * @param onApplyLayout ghi bố cục tự vẽ vào nguồn sự thật (`null` = quay về bố cục sẵn).
 * @param onPreset chọn bố cục sẵn. S4 · R7 gỡ 5 nút bố cục khỏi thanh trên, nên đây là bề mặt DUY NHẤT của nó —
 *   intent thì giữ nguyên (`KachiHomeActivity.selectPreset`), chỉ bớt một chỗ gọi.
 * @param onWallpaper / [onUnitPrefs] intent lưu + áp lại tương ứng.
 * @param onDuplicateProfile S4 · R8 — tạo hồ sơ mới là **bản sao của hồ sơ đang dùng** (nhận tên mới). Hộp thoại
 *   hỏi tên do màn Cài đặt dựng bằng `SettingsDialogs.askName`, không phải ở đây.
 * @param bootProfile / [onBootProfile] S4 · R6 — hồ sơ áp lúc nổ máy (`null` = hồ sơ dùng gần nhất, đúng giao kèo
 *   `WorkspaceRepository.bootProfile`).
 * @param shellUsable có kênh shell hay không — để bảng quyền nói đúng bức tranh.
 * @param goImmersive khôi phục chế độ toàn màn sau khi lớp phủ đóng (bàn phím/dialog làm mất cờ).
 */
class HomePanels(
    private val activity: Activity,
    private val rootFrame: FrameLayout,
    private val state: () -> HomeUiState,
    /** IA v2 · §4.2 — cầu sang cấu hình/hành động của ClusterNav; chuyển thẳng xuống [SettingsDeps.bridge]. */
    private val bridge: ClusterNavBridge,
    private val openDockPicker: (Set<String>, (Set<String>) -> Unit) -> Unit,
    private val onDockConfig: (DockConfig) -> Unit,
    private val runAction: (String, Int) -> Boolean,
    private val readInfo: (String) -> String?,
    private val onApplyLayout: (GridLayout?) -> Unit,
    private val onPreset: (LayoutPreset) -> Unit,
    private val onDockEdge: (DockEdge) -> Unit,
    private val onTopStrip: (String, Boolean) -> Unit,
    private val onTopStripConfig: (TopStripConfig) -> Unit,
    /** UX-OVERHAUL · WP4 — thứ tự các vật trên thanh trên; xem [SettingsDeps.onHeaderLayout]. */
    private val onHeaderLayout: (HeaderLayout) -> Unit,
    private val onWallpaper: (WallpaperPrefs) -> Unit,
    private val onUnitPrefs: (UnitPrefs) -> Unit,
    /**
     * Sổ địa chỉ của hồ sơ đang dùng (spec `kachi-voice-addresses.html` R1) — nhận **cả danh sách** đã chốt.
     *
     * Không phải cặp `onAdd`/`onDelete`: phép thêm/sửa/xoá là hàm thuần ở `:core` ([SavedPlaces]), nên hai cổng
     * riêng chỉ nhân đôi chỗ để lệch nhau (cùng lập luận [onDockConfig] — xem KDoc [SettingsDeps.onDockConfig]).
     */
    private val onSavedPlaces: (List<SavedPlace>) -> Unit,
    private val onThemeMode: (ThemeMode) -> Unit,
    private val onColorChoice: (ColorChoice) -> Unit,
    private val onLangMode: (LangMode) -> Unit,
    private val onAutostart: (Boolean) -> Unit,
    private val onSwitchProfile: (String) -> Unit,
    /**
     * S4 · R8 — tạo hồ sơ mới là **bản sao** của hồ sơ đang dùng. Nối ở `KachiHomeWiring.homePanels(...)` →
     * `HomeViewModel.duplicateProfile` → `WorkspaceRepository.duplicateProfile`.
     *
     * ⚠ Thân mặc định rỗng **cố ý giữ lại** sau khi đã nối: nó là thứ cho phép một bản dựng thử/một chỗ gọi khác
     * không phải khai cổng này. Nhưng mặc định rỗng cũng đúng hình dạng của một **nút chết** (bấm không lỗi, không
     * đổi gì, không ai đỏ — bài học `CastShell.evictVd`), nên `SettingsScreenWiringContractTest.ba lambda ho so moi
     * phai duoc noi that` canh đúng điều đó ở `KachiHomeWiring`.
     */
    private val onDuplicateProfile: (String) -> Unit = {},
    private val onDeleteProfile: (String) -> Unit,
    /** #4 — chuỗi export hồ sơ đang dùng (ViewModel) + nhập danh sách chuỗi file (trả số vào được) + tên hồ sơ đang dùng. */
    private val onExportProfileData: () -> String? = { null },
    private val onImportProfilesData: (List<String>) -> Int = { 0 },
    private val activeProfileName: () -> String = { "profile" },
    private val onRenameProfile: (String, String) -> Unit = { _, _ -> },
    /** Tóm tắt bố cục của MỘT hồ sơ (theo tên) cho thẻ hồ sơ ở Cài đặt — đọc-để-vẽ, qua ViewModel. */
    private val profileSummary: (String) -> String,
    /**
     * S4 · R6 — hồ sơ áp lúc nổ máy; `null` = *"hồ sơ dùng gần nhất"*. Đọc từ `HomeUiState.bootProfile` (đã nạp
     * trong `load()`), ghi qua `HomeViewModel.setBootProfile`. Mặc định rỗng: xem KDoc [onDuplicateProfile].
     */
    private val bootProfile: () -> String? = { null },
    private val onBootProfile: (String?) -> Unit = {},
    private val shellUsable: () -> Boolean,
    /** F4 — hệ thống đang hỏi *"Cho phép gỡ lỗi USB?"*: hàng quyền phải nói việc NGƯỜI DÙNG làm, không nói
     * "hạn chế môi trường" ([ĐO] xe 2026-09-14 nói sai đúng ca này). Xem [ShellChannelGate]. */
    private val shellAwaiting: () -> Boolean,
    private val goImmersive: () -> Unit,
    /**
     * V1 · R6 — mở NGĂN KÉO ứng dụng. Cùng lambda mà thanh nút đang dùng (`KachiHomeWiring.controlDock`), chuyển
     * thẳng xuống [SettingsDeps.openAppList]; lớp này không tự biết ngăn kéo nằm ở đâu.
     */
    private val openAppList: () -> Unit = {},
    /** V1 · R6 — mở một app theo tên gói (đường `AppOpener.openByIntent`). */
    private val openAppByPackage: (String) -> Boolean = { false },
    /** V1.1 — gắn app vào ô (đường `KachiHomeSlots.assignApp` mà ngăn kéo dùng). */
    private val assignAppToSlot: (Int, String) -> Boolean = { _, _ -> false },
    /** Báo "có lớp phủ nào đang mở" đổi — để nút ⇄ nổi (OverlayHeads) ẩn/hiện theo (không đè lên bảng Cài đặt). */
    private val onPanelsChanged: () -> Unit = {},
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
        onPanelsChanged()
    }

    fun closeLayoutEditor() {
        layoutPanel?.let { rootFrame.removeView(it) }
        layoutPanel = null
        goImmersive()
        onPanelsChanged()
    }

    // ── Màn Cài đặt (S1) ────────────────────────────────────────────────────────────────────────

    /**
     * Mở màn Cài đặt; [group] khác `null` ⇒ mở thẳng nhóm đó (S3 · R1: bong bóng "Cấu hình" → *Chiếu cụm*).
     *
     * Đang mở sẵn thì **chỉ đổi nhóm**, không dựng lại bảng: bảng giữ 10 trang đã dựng và cả chỗ đang cuộn của
     * chúng ([SettingsPanel.pages]) — dựng lại vì một cú bấm từ bong bóng là vứt hết chỗ đó đi.
     */
    fun openSettings(group: SettingsGroup? = null) {
        settingsPanel?.let { open ->
            group?.let { open.show(it) }
            return
        }
        val deps = SettingsDeps(
            state = state,
            // P8: đọc MỚI mỗi lượt dựng trang — quyền có thể vừa được tự cấp xong ở nhịp khởi động.
            permissions = {
                PermissionPreflight.check(activity, shellUsable = shellUsable(), awaitingApproval = shellAwaiting())
            },
            // U4: nói CHỖ bỏ ảnh vào — người dùng không có cách nào tự đoán, và màn chọn tệp của hệ thống bị khoá trên xe.
            wallpaperFolderHint = WallpaperStore.folderHint(activity),
            // IA v2 · N2: một cầu, không bọc lại thành lambda (xem KDoc [SettingsDeps.bridge]).
            bridge = bridge,
            onPreset = { p -> onPreset(p) },
            // P9: đường mở bảng vẽ bố cục (đóng màn Cài đặt trước — hai lớp phủ chồng nhau thì Back mất nghĩa).
            onOpenLayoutEditor = { closeSettings(); openLayoutEditor() },
            onWallpaper = { p -> onWallpaper(p) },
            onTopStrip = { id, on -> onTopStrip(id, on) },
            onTopStripConfig = { cfg -> onTopStripConfig(cfg) },
            onHeaderLayout = { layout -> onHeaderLayout(layout) },
            // T6 · R-UI (m): một bộ chọn, hai lối vào. Bảng Cài đặt gấp tập đã chốt bằng `DockSelection.apply`
            // rồi đẩy xuống qua intent — lớp này không biết phép gấp đó, nó chỉ nối hai đầu dây.
            openDockPicker = { selected, onApply -> openDockPicker(selected, onApply) },
            onDockConfig = { config -> onDockConfig(config) },
            onDockEdge = { e -> onDockEdge(e) },
            runAction = { id, arg -> runAction(id, arg) },
            readInfo = { id -> readInfo(id) },
            // R11: đổi đơn vị ⇒ lưu bền + áp lại NGAY cho cả thanh nút và ô giữa màn (không cần mở lại app).
            onUnitPrefs = { prefs -> onUnitPrefs(prefs) },
            // Sổ địa chỉ: một cổng, nhận cả danh sách đã chốt (xem KDoc [onSavedPlaces]).
            onSavedPlaces = { list -> onSavedPlaces(list) },
            onThemeMode = { m -> onThemeMode(m) },
            onColorChoice = { c -> onColorChoice(c) },
            onLangMode = { m -> onLangMode(m) },
            onAutostart = { on -> onAutostart(on) },
            onSwitchProfile = { name -> onSwitchProfile(name) },
            // S4 · R8 — "thêm hồ sơ" nay là NHÂN BẢN hồ sơ đang dùng; hộp thoại hỏi tên nằm trong màn Cài đặt
            // (`SettingsDialogs.askName`), lớp này chỉ nối hai đầu dây.
            onDuplicateProfile = { name -> onDuplicateProfile(name) },
            onDeleteProfile = { name -> onDeleteProfile(name) },
            // #4 (owner 2026-09-24) — Xuất: lấy chuỗi export từ ViewModel → ghi file (ProfileIoStore); trả đường dẫn.
            //          Nhập: đọc mọi file trong thư mục → đẩy vào ViewModel; trả số hồ sơ vào được.
            onExportProfile = {
                onExportProfileData()?.let { data -> ProfileIoStore.write(activity, activeProfileName(), data) }
            },
            onImportProfiles = { onImportProfilesData(ProfileIoStore.readAll(activity)) },
            profileFolderPath = { ProfileIoStore.folderPath(activity) },
            onRenameProfile = { old, new -> onRenameProfile(old, new) },
            // S4 · R6 — hồ sơ lúc nổ máy (theo XE, không theo hồ sơ — R4).
            bootProfile = bootProfile,
            onBootProfile = { name -> onBootProfile(name) },
            // Owner 2026-09-14 "chưa thấy hồ sơ gắn với bố cục chỗ nào": thẻ hồ sơ nói ra bố cục của TỪNG hồ sơ.
            profileSummary = profileSummary,
            // V1 · R6 — ba cổng cho đường thử lệnh bằng chữ; xem KDoc [SettingsDeps.openAppList].
            openAppList = openAppList,
            openAppByPackage = openAppByPackage,
            assignAppToSlot = assignAppToSlot,
            openSettingsGroup = { g -> openSettings(g) },
            // WP7 — công tắc test-mode là CỔNG của khối đồ đo, nên nó phải dựng lại được trang đang xem. Đi qua
            // `invalidateSettings()` (đường đã có sẵn cho ca đổi hồ sơ) chứ không dựng lại cả bảng: dựng lại bảng
            // là mất luôn 10 trang đã nhớ + chỗ cuộn của chúng.
            refreshSettings = { invalidateSettings() },
        )
        val panel = SettingsPanel(activity, deps) { closeSettings() }
        settingsPanel = panel
        group?.let { panel.show(it) }
        rootFrame.addView(
            panel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        onPanelsChanged()
    }

    fun closeSettings() {
        settingsPanel?.let { rootFrame.removeView(it) }
        settingsPanel = null
        onPanelsChanged()
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
        // Hardening 2026-09-25 (audit F16): đang HỌC phím mà màn `recreate()` (đổi ngôn ngữ) ⇒ `VoiceKeyLearnBus`
        // (singleton) giữ lambda `ui(...)` của Activity cũ tới lần học sau. Bus là của bridge ⇒ trả lại ở đây.
        runCatching { bridge.stopLearn() }
    }
}
