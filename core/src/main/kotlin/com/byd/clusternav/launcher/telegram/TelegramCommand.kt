package com.byd.clusternav.launcher.telegram

/**
 * Lệnh rút ra từ MỘT update Telegram. Thuần Kotlin (`:core`) ⇒ kiểm off-car; `:app` (`TelegramBridgeService`) thi hành.
 *
 * ## Phạm vi (owner 26/10, 3.1.21): CHỈ dẫn đường + nhạc
 * *"không đọc ghi gì vào xe hết, an toàn data privacy, chỉ làm basic"* ⇒ không lệnh điều khiển xe, không gửi trạng thái
 * xe về Telegram. Lệnh tiếng Anh; tên nơi / tên bài là data người dùng (tiếng Việt thoải mái).
 */
sealed class TelegramCommand {

    /**
     * Dẫn đường. [placeOrQuery] = tên nơi trong sổ địa chỉ, chữ địa chỉ, toạ độ, hoặc link chia sẻ Google Maps; [coords] từ
     * share-location 📍. [apps] = app dẫn (`gmaps`/`vietmap`/`waze`), rỗng ⇒ app mặc định của hồ sơ.
     */
    data class Nav(val placeOrQuery: String, val coords: String? = null, val apps: List<String> = emptyList()) : TelegramCommand()

    /**
     * Nhạc. [isQuery] = false ⇒ [text] là LINK http(s) (app chọn theo host: YouTube / YT Music). true ⇒ TÊN BÀI, tìm trên
     * [app] (`youtube`/`ytmusic`), `null` ⇒ App nhạc mặc định.
     */
    data class Music(val text: String, val isQuery: Boolean = false, val app: String? = null) : TelegramCommand()

    /** `/go` trống · `/places` · nút 🧭 Go ⇒ bảng nơi đã lưu + ➕ New. */
    object ListPlaces : TelegramCommand()

    /** Nút 🎵 Music ⇒ bot hỏi tên bài (ForceReply). */
    object AskSong : TelegramCommand()

    /** `/cancel` ⇒ xoá điểm đến chờ. */
    object Cancel : TelegramCommand()

    /** `/help` · nút ❓ Help ⇒ danh sách lệnh. */
    object Help : TelegramCommand()

    /** `/start` · `/menu` ⇒ gửi lại bàn phím nút + hướng dẫn ngắn. */
    object Menu : TelegramCommand()

    /** Không hiểu ⇒ bot gợi ý `/help`. [raw] đã khử để log an toàn. */
    data class Unknown(val raw: String) : TelegramCommand()
}

/**
 * Một update đã phân tích: lệnh + con trỏ [updateId] + [chatId] (kiểm quyền) + [callbackId] (để `answerCallbackQuery`) +
 * [messageId] (tin chứa bảng nút, để sửa tại chỗ) + [rawCallback] (`callback_data` gốc).
 */
data class TelegramUpdate(
    val updateId: Long,
    val chatId: Long?,
    val command: TelegramCommand,
    val callbackId: String? = null,
    val messageId: Long? = null,
    val rawCallback: String? = null,
)
