package com.byd.clusternav.launcher

import android.app.Activity
import android.widget.FrameLayout
import android.widget.Toast
import com.byd.clusternav.R

/**
 * ═══ NỐI DÂY của composition-root — phần KHÔNG cần biết gì về nội tại [KachiHomeActivity] ══════════════════════
 *
 * Ba thứ ở đây, cùng một lý do: **trần 500 dòng** (CLAUDE.md §4.1). [ĐO] `KachiHomeActivity` đang đúng 505 dòng khi
 * T4 phải thêm cầu [ClusterNavBridge] vào nó; cắt đúng khối "dựng [HomePanels] từ ViewModel" là cắt đúng khớp —
 * khối đó chỉ **chuyển tiếp** `viewModel.<intent>` xuống bảng, không đọc một field riêng nào của Activity. Không
 * đổi một hành vi nào so với bản nằm trong Activity.
 *
 * ⚠ Đây KHÔNG phải "tệp tiện ích": nó là **chỗ dịch giữa hai từ vựng** — mã [BridgeMsg] của nhánh ClusterNav và
 * tài nguyên `kachi_bridge_*` của launcher. Chỗ dịch đó phải có đúng một bản (xem KDoc [BridgeMsg]).
 */

/**
 * Dựng cầu ClusterNav cho một Activity.
 *
 * ## Ba quyết định của hợp đồng, và vì sao
 *  - **`applicationContext`**: cầu sống lâu hơn màn hình (callback của `selfGrant`/`seatCount` về sau vài giây).
 *    Nó tự ép lại `app.applicationContext` ở constructor, nhưng truyền đúng ngay từ đây thì không ai phải đọc
 *    KDoc mới biết.
 *  - **[Toast.LENGTH_LONG]**: mọi [BridgeMsg] đều là câu *"vừa xảy ra chuyện gì / phải làm gì tiếp"* (cấp quyền
 *    hỏng, phải bấm Allow USB debugging trên xe…). Câu hướng dẫn dài mà hiện 2 s thì người lái không đọc kịp.
 *  - **`activityProvider`**: chỉ [ClusterNavBridge.checkUpdate] cần Activity (`UpdateFlow.start` dựng dialog +
 *    `startActivity` cài APK). Trả `null` khi màn đang đóng ⇒ cầu tự báo [BridgeMsg.UPDATE_NEEDS_SCREEN] thay vì
 *    ném `WindowManager$BadTokenException` — [ĐO] đúng ca "bấm Kiểm tra cập nhật rồi xoay/đóng màn".
 */
internal fun Activity.clusterNavBridge(): ClusterNavBridge = ClusterNavBridge(
    app = applicationContext,
    toast = { msg -> Toast.makeText(this, getString(bridgeMsgRes(msg)), Toast.LENGTH_LONG).show() },
    ui = { r -> runOnUiThread(r) },
    activityProvider = { if (isFinishing || isDestroyed) null else this },
)

/**
 * [BridgeMsg] → khoá tài nguyên.
 *
 * ## Vì sao `when` TƯỜNG MINH, không `valueOf`/tra bảng theo tên
 * Cách "thông minh" là `resources.getIdentifier("kachi_bridge_" + msg.name.lowercase())` — và nó hỏng **im lặng**:
 * thiếu một chuỗi thì `getIdentifier` trả `0`, `getString(0)` ném `NotFoundException` **lúc chạy**, trên xe, đúng
 * lúc đang cấp quyền hỏng. `when` vét cạn trên enum thì thiếu một nhánh là **không biên dịch được** — mà đây chính
 * là ca *"thêm một BridgeMsg rồi quên viết chuỗi"*, tức ca bài canh phải bắt.
 *
 * Câu VI/EN chép **nguyên văn** từ KDoc của từng giá trị [BridgeMsg] (chúng là câu của màn ClusterNav cũ). Hai màn
 * phải nói cùng một lời — lệch một câu là người dùng tưởng hai tính năng khác nhau.
 */
internal fun bridgeMsgRes(msg: BridgeMsg): Int = when (msg) {
    BridgeMsg.GRANTING_NOTIFICATION -> R.string.kachi_bridge_granting_notification
    BridgeMsg.NOTIFICATION_GRANTED -> R.string.kachi_bridge_notification_granted
    BridgeMsg.NOTIFICATION_FALLBACK -> R.string.kachi_bridge_notification_fallback
    BridgeMsg.RECONNECTING -> R.string.kachi_bridge_reconnecting
    BridgeMsg.CAST_ON -> R.string.kachi_bridge_cast_on
    BridgeMsg.CAST_OFF -> R.string.kachi_bridge_cast_off
    BridgeMsg.CAST_RETURNING -> R.string.kachi_bridge_cast_returning
    BridgeMsg.CLUSTER_RESET_REOPENING -> R.string.kachi_bridge_cluster_reset
    BridgeMsg.DEEP_RESCUE_RUNNING -> R.string.kachi_bridge_deep_rescue_running
    BridgeMsg.ENABLING_ACCESSIBILITY -> R.string.kachi_bridge_enabling_accessibility
    BridgeMsg.ACCESSIBILITY_ENABLED -> R.string.kachi_bridge_accessibility_enabled
    BridgeMsg.ACCESSIBILITY_FAILED -> R.string.kachi_bridge_accessibility_failed
    BridgeMsg.CHECKING -> R.string.kachi_bridge_checking
    BridgeMsg.VOICE_KEY_READY -> R.string.kachi_bridge_voice_key_ready
    BridgeMsg.BINDING_REMOVED -> R.string.kachi_bridge_binding_removed
    BridgeMsg.BUTTON_SAVED -> R.string.kachi_bridge_button_saved
    BridgeMsg.BUTTON_REMOVED -> R.string.kachi_bridge_button_removed
    BridgeMsg.LEARN_PRESS_BUTTON -> R.string.kachi_bridge_learn_press
    BridgeMsg.SETTING_GEMINI_ASSISTANT -> R.string.kachi_bridge_gemini_setting
    BridgeMsg.GEMINI_ASSISTANT_SET -> R.string.kachi_bridge_gemini_set
    BridgeMsg.GEMINI_ASSISTANT_FAILED -> R.string.kachi_bridge_gemini_failed
    BridgeMsg.CLEANING_AIR -> R.string.kachi_bridge_cleaning_air
    BridgeMsg.UPDATE_NEEDS_SCREEN -> R.string.kachi_bridge_update_needs_screen
    BridgeMsg.SCREEN_OPEN_FAILED -> R.string.kachi_bridge_screen_open_failed
}

/**
 * Dựng [HomePanels] (màn Cài đặt + bảng vẽ bố cục) từ ViewModel và mấy đường không nằm trong ViewModel.
 *
 * Mọi tham số ở đây đều là thứ mà Activity **không thể** tự suy ra: hai đường đổi bố cục đi kèm tác dụng phụ
 * ([onApplyLayout]/[onPreset] phải bỏ bố cục còn lại), hai đường phải áp lại NGAY lên view đang hiện
 * ([onWallpaperChanged]/[onUnitsChanged]), hộp thoại tạo hồ sơ dùng lại của `ProfileBar`, và bộ chọn nút thanh xe
 * nằm ở [DrawerController]. Phần còn lại chỉ là `viewModel.<intent>` nên nó ở đây, không ở Activity.
 */
@Suppress("LongParameterList")
internal fun homePanels(
    activity: Activity,
    rootFrame: FrameLayout,
    viewModel: HomeViewModel,
    bridge: ClusterNavBridge,
    scenes: SceneActions,
    openDockPicker: (Set<String>, (Set<String>) -> Unit) -> Unit,
    onApplyLayout: (GridLayout?) -> Unit,
    onPreset: (LayoutPreset) -> Unit,
    onWallpaperChanged: (WallpaperPrefs) -> Unit,
    onUnitsChanged: (UnitPrefs) -> Unit,
    onAddProfile: () -> Unit,
    shellUsable: () -> Boolean,
    goImmersive: () -> Unit,
    onPanelsChanged: () -> Unit,
): HomePanels = HomePanels(
    activity = activity,
    rootFrame = rootFrame,
    state = { viewModel.uiState.value },
    bridge = bridge,
    openDockPicker = openDockPicker,
    // T6 · R-UI (m): tập người dùng vừa chốt đã được `DockSelection.apply` gấp thành cấu hình ở tầng Cài đặt;
    // ở đây chỉ còn một intent — **không** ghi bền trực tiếp (`GridSeamGuardTest.chi ViewModel duoc ghi ben`).
    onDockConfig = { config -> viewModel.setDockConfig(config) },
    onApplyLayout = onApplyLayout,
    onPreset = onPreset,                         // CÙNG đường với 5 nút bố cục ở thanh trên (§4.5)
    onDockEdge = { e -> viewModel.setDockEdge(e) },
    onTopStrip = { id, on -> viewModel.toggleTopStrip(id, on) },
    onWallpaper = onWallpaperChanged,
    onUnitPrefs = onUnitsChanged,
    onThemeMode = { m -> viewModel.setThemeMode(m) },   // T1 — đọc-để-vẽ ở [ThemeHost]; gương store ở repository
    onLangMode = { m -> viewModel.setLangMode(m) },     // U5·T3 — đọc-để-vẽ ở [LangHost.wrap]
    onAutostart = { on -> viewModel.setAutostart(on) },
    onSwitchProfile = { name -> viewModel.switchProfile(name) },
    onAddProfile = onAddProfile,                 // dùng LẠI hộp thoại có sẵn, không dựng bản thứ hai
    onDeleteProfile = { name -> viewModel.deleteProfile(name) },
    scenes = scenes,                             // P7/P6 — lưu/gọi/nổ-máy/đổi-tên/xoá cảnh
    shellUsable = shellUsable,
    goImmersive = goImmersive,
    onPanelsChanged = onPanelsChanged,
)
