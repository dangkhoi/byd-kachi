package com.byd.clusternav.launcher

import com.byd.clusternav.setTelegramKbVersion
import com.byd.clusternav.Prefs
import com.byd.clusternav.automation.AutomationService
import com.byd.clusternav.automation.PendingNavStore
import com.byd.clusternav.launcher.telegram.PendingDestinations
import com.byd.clusternav.setTelegramConfig
import com.byd.clusternav.telegram.TelegramApi
import com.byd.clusternav.telegram.TelegramBridgeService
import com.byd.clusternav.telegramConfig

/**
 * Cầu Cài đặt ↔ TELEGRAM-REMOTE (spec `kachi-telegram-remote.html` R10 + §4.2). Hàm mở rộng của
 * [ClusterNavBridge] (tách vì trần 500 dòng, cùng cách `ClusterNavBridgeKeys`). Mọi lượt ghi config đồng bộ lại
 * FGS bridge ([TelegramBridgeService.sync]); mọi lượt đặt/xoá pending đồng bộ vòng automation ưu tiên.
 */

fun ClusterNavBridge.telegramEnabled(): Boolean = Prefs.telegramConfig(app).enabled
fun ClusterNavBridge.telegramToken(): String = Prefs.telegramConfig(app).botToken
fun ClusterNavBridge.telegramChatId(): String = Prefs.telegramConfig(app).allowedChatId.let { if (it == 0L) "" else it.toString() }

fun ClusterNavBridge.setTelegramEnabled(on: Boolean) {
    Prefs.setTelegramConfig(app, Prefs.telegramConfig(app).copy(enabled = on))
    TelegramBridgeService.sync(app)
}

fun ClusterNavBridge.setTelegramToken(token: String) {
    Prefs.setTelegramConfig(app, Prefs.telegramConfig(app).copy(botToken = token.trim()))
    TelegramBridgeService.sync(app)
}

/** Chat-id: nhận chuỗi số; rỗng/hỏng ⇒ 0 (chưa cấu hình). */
fun ClusterNavBridge.setTelegramChatId(raw: String) {
    val id = raw.trim().toLongOrNull() ?: 0L
    Prefs.setTelegramConfig(app, Prefs.telegramConfig(app).copy(allowedChatId = id))
    TelegramBridgeService.sync(app)
}

/** Kiểm kết nối: gọi `getMe` gián tiếp qua một sendMessage thử tới chat-id (degrade-safe). `null` = chưa đủ config. */
/**
 * Kiểm kết nối — CHẠY NỀN rồi trả kết quả trên main (P1-6: targetSdk 37 bật death-on-network, gọi mạng trên
 * luồng UI ⇒ `NetworkOnMainThreadException` ⇒ luôn báo "fail"; debug chỉ không lộ vì StrictMode `penaltyLog`).
 * `onResult`: `null` = chưa đủ config · `true/false` = gửi được/không.
 */
fun ClusterNavBridge.telegramTestConnection(onResult: (Boolean?) -> Unit) {
    val cfg = Prefs.telegramConfig(app)
    if (!cfg.ready) { onResult(null); return }
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    Thread({
        // 3.1.20: tin thử mang luôn bàn phím nút dưới ô gõ (TelegramReplyKeyboard) ⇒ chủ xe có nút ngay sau khi nối.
        val ok = runCatching {
            TelegramApi(cfg.botToken).sendMessage(cfg.allowedChatId, "✅ Kachi connected — buttons are below the text box",
                com.byd.clusternav.launcher.telegram.TelegramReplyKeyboard.markup())
        }.getOrDefault(false)
        if (ok) Prefs.setTelegramKbVersion(app, com.byd.clusternav.launcher.telegram.TelegramReplyKeyboard.VERSION)
        main.post { onResult(ok) }
    }, "KachiTgTest").apply { isDaemon = true }.start()
}

// ── Điểm đến chờ (nhập tay trên màn xe — T5) ────────────────────────────────────────────────────

/** Tóm tắt điểm đến chờ cho dòng Cài đặt; rỗng = chưa đặt. */
fun ClusterNavBridge.pendingDestinationSummary(): String =
    PendingNavStore.read(app)?.dest?.let { "${it.place.name} · ${it.appsEncoded}" } ?: ""

/**
 * Đặt điểm đến chờ từ một mục sổ địa chỉ + danh sách app; dựng vòng ưu tiên ngay. Trả `false` (không đặt) khi MỌI app chọn
 * cần toạ độ (VietMap — [ĐO RE 23/09]) mà nơi chỉ có chữ: review 26/10 P1 — điểm đến đó không app nào mở được, vòng chờ sẽ
 * thử lại 24 h trong im lặng. Màn Cài đặt chạy trên luồng UI nên không tra toạ độ ở đây; chỗ gọi nói cho người dùng.
 */
fun ClusterNavBridge.setPendingDestination(place: SavedPlace, apps: List<String>): Boolean {
    val usable = if (place.hasCoords) apps
        else apps.filterNot { com.byd.clusternav.launcher.voice.VoiceAppTargets.byKey(it)?.needsCoords == true }
    if (usable.isEmpty()) return false
    val dest = PendingDestinations.of(
        place.name, place.query, usable,
        if (place.hasCoords) "${place.lat}, ${place.lng}" else null,
    ) ?: return false
    // GPS mặc định BẮT BUỘC: chỉ bắt đầu dẫn khi có định vị (GpsAvailability trả true). Chưa cấp quyền /
    // chưa có fix (trong hầm, vừa nổ máy) ⇒ ScheduledNavApplier CHỜ, không dẫn mù — cùng luật với lịch.
    PendingNavStore.write(app, dest, requireGps = true)
    AutomationService.sync(app)
    return true
}

fun ClusterNavBridge.clearPendingDestination() {
    PendingNavStore.consume(app)
}
