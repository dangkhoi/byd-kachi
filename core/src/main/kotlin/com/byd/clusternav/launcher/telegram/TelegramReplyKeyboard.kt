package com.byd.clusternav.launcher.telegram

/**
 * Bàn phím nút CỐ ĐỊNH dưới ô gõ (`ReplyKeyboardMarkup`) + hai lời nhắc gõ chữ (`ForceReply`). Thuần Kotlin (`:core`).
 *
 * 3.1.21 (owner 26/10: *"chỉ làm basic … có menu gọn thôi"*): 3 nút — 🧭 Go · 🎵 Music · ❓ Help.
 *  - Nút gửi đúng CHỮ trên nút như một tin thường ⇒ [commandOf] dịch nhãn → lệnh.
 *  - `ForceReply` làm Telegram mở ô gõ ở chế độ TRẢ LỜI tin nhắc; tin gõ vào mang `reply_to_message` = tin nhắc ⇒ [answerOf]
 *    nhận ra lời nhắc theo CHỮ của nó (không lưu trạng thái — sống qua lúc BYD giết app).
 */
object TelegramReplyKeyboard {

    const val GO = "🧭 Go"
    const val MUSIC = "🎵 Music"
    const val HELP = "❓ Help"

    /** Lời nhắc gõ tên bài — câu trả lời thành `/play <chữ>` (kèm được `youtube`/`ytmusic` ở cuối). */
    const val SONG_PROMPT = "🎵 Type a song name (add “youtube” or “ytmusic” to pick the app) or paste a link:"

    /** Lời nhắc gõ điểm đến — câu trả lời thành `/go <chữ>`. */
    const val DEST_PROMPT = "🧭 Type a destination — saved name, address, lat, lng or a Google Maps link (add “vietmap” to also open VietMap):"

    /** Phiên bản bàn phím. Tăng khi đổi nút ⇒ `:app` gửi lại một lần (Telegram giữ bàn phím cũ phía máy khách). */
    const val VERSION = 3

    /** Nhãn nút → lệnh; `null` = không phải nút bàn phím. */
    fun commandOf(text: String): TelegramCommand? = when (text.trim()) {
        GO -> TelegramCommand.ListPlaces
        MUSIC -> TelegramCommand.AskSong
        HELP -> TelegramCommand.Help
        else -> null
    }

    /**
     * Tin trả lời một lời nhắc → chuỗi lệnh (`/play …` / `/go …`). `null` khi [replyToText] không phải lời nhắc của bot, chữ
     * rỗng, hoặc người dùng gõ hẳn một lệnh `/…` (lệnh đó thắng).
     */
    fun answerOf(text: String, replyToText: String?): String? {
        val t = text.trim()
        if (t.isEmpty() || t.startsWith("/")) return null
        return when (replyToText?.trim()) {
            SONG_PROMPT -> "/play $t"
            DEST_PROMPT -> "/go $t"
            else -> null
        }
    }

    /** JSON `reply_markup`: một hàng 3 nút, gọn (`resize_keyboard`), luôn hiện (`is_persistent`). */
    fun markup(): String =
        """{"keyboard":[[{"text":"$GO"},{"text":"$MUSIC"},{"text":"$HELP"}]],"resize_keyboard":true,"is_persistent":true,""" +
            """"input_field_placeholder":"Paste a link or tap a button"}"""

    /** JSON `ForceReply` kèm gợi ý trong ô gõ ([placeholder] ≤ 64 ký tự — giới hạn của Telegram). */
    fun forceReply(placeholder: String): String =
        """{"force_reply":true,"input_field_placeholder":"${placeholder.take(64).replace("\"", "'")}"}"""
}
