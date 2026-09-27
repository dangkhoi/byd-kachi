package com.byd.clusternav.launcher

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import com.byd.clusternav.UpdateFlow

/**
 * ═══ Cầu → Cài đặt: quyền hệ thống (chỉ đọc) + nhóm *Hệ thống* + "áp ngay" lấy gió trong ═══
 *
 * Tách THUẦN khỏi `ClusterNavBridge.kt` (536 dòng → trần 500, L6-debt 2026-09-27): thân hàm giữ nguyên byte, chỉ đổi thành
 * hàm mở rộng của CHÍNH lớp [ClusterNavBridge] cùng package (giữ bề mặt phẳng `bridge.checkUpdate(...)`, cùng khuôn
 * `ClusterNavBridgeCast.kt` / `ClusterNavBridgeKeys.kt`). Mọi chỉ dẫn `MainActivity.kt:<dòng>` là vết lịch sử — xem KDoc lớp.
 */

/** Lặp lại `MainActivity.kt:781–785` (đọc thẳng secure setting — mọi app đọc được, không cần dadb). */
fun ClusterNavBridge.notificationAccessGranted(): Boolean = hasSecureComponent(
    "enabled_notification_listeners",
    ComponentName(app, com.byd.clusternav.NavNotificationListener::class.java),
)

/** Lặp lại `MainActivity.kt:792–796`. */
fun ClusterNavBridge.accessibilityBoosterGranted(): Boolean = hasSecureComponent(
    "enabled_accessibility_services",
    ComponentName(app, com.byd.clusternav.modules.navaccess.NavAccessibilityService::class.java),
)

/**
 * Service Hỗ trợ đã **BOUND THẬT** chưa — đọc từ [android.view.accessibility.AccessibilityManager]
 * (`getEnabledAccessibilityServiceList`, API chính thức phản ánh service ĐANG CHẠY, không cần dadb).
 *
 * ⚠⚠ GỐC BUG "báo OK mà chả OK / reset mới hết" (owner 2026-09-23, chung cả ClusterNav v1/v2/launcher):
 * bản cũ đọc `NavAccessibilitySource.connected` — cờ IN-PROCESS set ở `onServiceConnected`/`onUnbind`. Khi xe
 * NGỦ ĐÔNG / CPU pressure, hệ UNBIND service **mà KHÔNG gọi `onUnbind`** (Android không đảm bảo onUnbind chạy
 * khi đóng băng/kill ngầm) ⇒ cờ KẸT `true` dù service đã chết ⇒ (a) status báo ACTIVE dối, (b) watchdog gate
 * `!connected`=false nên KHÔNG heal ⇒ chỉ reset process (owner reset) mới về false. Nay đọc SỰ THẬT từ hệ.
 */
fun ClusterNavBridge.accessibilityBound(): Boolean = com.byd.clusternav.NavConnect.isAccessibilityBound(app)

internal fun ClusterNavBridge.hasSecureComponent(setting: String, expected: ComponentName): Boolean {
    val flat = Settings.Secure.getString(app.contentResolver, setting) ?: return false
    return flat.split(':').any { ComponentName.unflattenFromString(it.trim()) == expected }
}


/**
 * "Kiểm tra cập nhật" — lặp lại `MainActivity.kt:284–287`.
 *
 * ⚠ **KHÔNG lặp được từ ngoài Activity**: [UpdateFlow.start] nhận `Activity` (nó dựng
 * `AlertDialog` xác nhận rồi gọi `startActivity` cài APK) và [ĐO] không có đường tĩnh nào bên dưới
 * để gọi thay. Bridge vì thế nhận `activityProvider` ở constructor; không có Activity ⇒ báo thẳng
 * cho người dùng thay vì im lặng.
 */
fun ClusterNavBridge.checkUpdate(onText: (String) -> Unit) {
    val activity = activityProvider()
    if (activity == null) {
        toast(BridgeMsg.UPDATE_NEEDS_SCREEN)
        return
    }
    UpdateFlow.start(activity) { text, _ -> ui(Runnable { onText(text) }) }
}

// ⚠ `openLegacyScreen()` đã XOÁ 2026-09-13 cùng màn ClusterNav cũ (S3 · R1). Mục catalog
// `system_advanced_screen` và nút "Màn nâng cao" ở nhóm *Hệ thống* biến mất theo — mọi cấu hình của màn đó
// nay ở nhóm *Dẫn đường* / *Chiếu cụm* / *Phím vô-lăng* / *Tiện nghi xe*, ghi cùng khoá qua chính cầu này.

/** "Dữ liệu VietMap" — lặp lại `MainActivity.kt:281–283`. */
fun ClusterNavBridge.openVietMapData() = launch(com.byd.clusternav.vietmapwidget.VietMapWidgetDiagActivity::class.java)

/** "Chẩn đoán" — lặp lại `MainActivityCastController.kt:69`. */
fun ClusterNavBridge.openDiagnostics() = launch(com.byd.clusternav.modules.clustercast.DiagActivity::class.java)

internal fun ClusterNavBridge.launch(target: Class<*>) {
    runCatching {
        app.startActivity(Intent(app, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { toast(BridgeMsg.SCREEN_OPEN_FAILED) }
}

// ── Lấy gió trong: đường "áp ngay" (HomePanels cũ: bật ⇒ áp NGAY, không chờ lần nổ máy sau) ─────────
/** Áp chế độ lấy gió trong NGAY (bất đồng bộ) — lặp lại `HomePanels.onRecircOnStart` trước IA v2. */
fun ClusterNavBridge.applyRecircNow() = com.byd.clusternav.comfort.RecircApplier.applyNowAsync(app)
