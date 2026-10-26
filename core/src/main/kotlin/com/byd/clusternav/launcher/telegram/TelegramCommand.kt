package com.byd.clusternav.launcher.telegram

/**
 * Lệnh rút ra từ MỘT update Telegram — spec `docs/specs/kachi-telegram-remote.html` R6. Thuần Kotlin (`:core`,
 * cấm `android.*`) ⇒ kiểm off-car. Lớp `:app` ([com.byd...TelegramBridgeService]) thi hành; lớp này chỉ MÔ TẢ.
 *
 * Lệnh TIẾNG ANH (owner 26/10, rules G4 — identifier máy đọc là English): tên NƠI trong [Nav.placeOrQuery] là
 * data người dùng ⇒ để tiếng Việt thoải mái.
 */
sealed class TelegramCommand {

    /**
     * Dẫn đường. [placeOrQuery] = tên nơi trong sổ địa chỉ HOẶC chữ địa chỉ/toạ độ trực tiếp; [coords] từ
     * share-location 📍 (null nếu lệnh text). [apps] = app dẫn (rỗng ⇒ lớp `:app` dùng mặc định hồ sơ).
     */
    data class Nav(val placeOrQuery: String, val coords: String? = null, val apps: List<String> = emptyList()) : TelegramCommand()

    /** Mở nhạc từ link. Lớp `:app`: đang chạy ⇒ mở ngay (R8), chưa lên ⇒ đặt pending nhạc. */
    data class Music(val url: String) : TelegramCommand()

    /** Điều khiển xe (R9). [controlId] = id `ControlRegistry` đã xác thực; [value] theo nút (bật/tắt/mức). */
    data class Control(val controlId: String, val value: Int?) : TelegramCommand()

    /** `/go` trống ⇒ bot render nút chọn từ sổ địa chỉ (R7). */
    object ListPlaces : TelegramCommand()

    /** `/cancel` ⇒ xoá điểm đến chờ. */
    object Cancel : TelegramCommand()

    /** `/help` ⇒ bot liệt kê lệnh + nút. */
    object Help : TelegramCommand()

    /** Không hiểu ⇒ bot báo + gợi ý `/help`. [raw] đã khử để log an toàn. */
    data class Unknown(val raw: String) : TelegramCommand()
}

/**
 * Một update đã phân tích: lệnh + con trỏ [updateId] (chống đọc trùng) + [chatId] (kiểm quyền) + [callbackId]
 * (để `answerCallbackQuery`). `null` khi update không mang gì dùng được.
 */
data class TelegramUpdate(
    val updateId: Long,
    val chatId: Long?,
    val command: TelegramCommand,
    val callbackId: String? = null,
)
