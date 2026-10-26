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

    /**
     * Mở nhạc. [isQuery] = false ⇒ [text] là LINK http(s), lớp `:app` mở thẳng `ACTION_VIEW` (hệ tự chọn app theo
     * URL: youtube.com → YouTube, music.youtube.com → YT Music). true ⇒ [text] là TÊN BÀI, lớp `:app` tìm trên
     * App nhạc mặc định (`voice_music_default_app`), chưa đặt ⇒ lùi YouTube. Đang chạy ⇒ mở ngay (R8), chưa lên ⇒ pending.
     */
    data class Music(val text: String, val isQuery: Boolean = false) : TelegramCommand()

    /** Điều khiển xe (R9). [controlId] = id `ControlRegistry` đã xác thực; [value] theo nút (bật/tắt/mức). */
    data class Control(val controlId: String, val value: Int?) : TelegramCommand()

    /** `/go` trống ⇒ bot render nút chọn từ sổ địa chỉ (R7). */
    object ListPlaces : TelegramCommand()

    /** `/cancel` ⇒ xoá điểm đến chờ. */
    object Cancel : TelegramCommand()

    /** `/help` ⇒ bot liệt kê lệnh + nút. */
    object Help : TelegramCommand()

    /** `/menu` · `/start` ⇒ bot mở bảng điều khiển bấm nút (menu chính). */
    object Menu : TelegramCommand()

    /** `/status` ⇒ mở menu + bảng trạng thái sống (giống /menu, nhấn mạnh trạng thái). */
    object Status : TelegramCommand()

    /** Không hiểu ⇒ bot báo + gợi ý `/help`. [raw] đã khử để log an toàn. */
    data class Unknown(val raw: String) : TelegramCommand()
}

/**
 * Một update đã phân tích: lệnh + con trỏ [updateId] (chống đọc trùng) + [chatId] (kiểm quyền) + [callbackId]
 * (để `answerCallbackQuery`) + [messageId] (id tin chứa bảng — để sửa/xoá bảng, QD-17). `null` khi update không
 * mang gì dùng được.
 */
data class TelegramUpdate(
    val updateId: Long,
    val chatId: Long?,
    val command: TelegramCommand,
    val callbackId: String? = null,
    val messageId: Long? = null,
    val rawCallback: String? = null,   // chuỗi callback_data gốc (menu mới parse bằng TelegramCallback ở :app)
    val dateSec: Long? = null,         // `message.date` (giây UTC) của TIN GÕ; null với callback (Telegram không gửi giờ bấm)
)
