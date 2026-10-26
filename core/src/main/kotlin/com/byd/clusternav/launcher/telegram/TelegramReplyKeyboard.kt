package com.byd.clusternav.launcher.telegram

/**
 * Bàn phím nút CỐ ĐỊNH dưới ô gõ của Telegram (`ReplyKeyboardMarkup`) + hai lời nhắc gõ chữ (`ForceReply`) — owner
 * 26/10: *"nhét hết function vào dưới đó, chỉ cần nhấn là được … nhấn song thì auto /play [chờ gõ tên bài]"*.
 * Thuần Kotlin (`:core`) ⇒ test bằng giá trị.
 *
 * ## Cách Telegram chạy hai thứ này
 * - Nút bàn phím gửi đúng CHỮ trên nút như một tin nhắn thường ⇒ [boardOf] dịch nhãn → bảng nút inline cần mở.
 * - `ForceReply` làm app Telegram tự mở bàn phím ở chế độ TRẢ LỜI tin nhắc; tin người dùng gõ mang `reply_to_message`
 *   = tin nhắc ⇒ [answerOf] nhận ra lời nhắc theo CHỮ của nó (không cần lưu trạng thái — sống qua lúc BYD giết app).
 *
 * Nhãn nút là hằng ở đây — đổi nhãn ở một chỗ, phía dịch đọc cùng hằng.
 */
object TelegramReplyKeyboard {

    const val GO = "🧭 Go"
    const val MUSIC = "🎵 Music"
    const val AC = "❄️ AC"
    const val SEAT = "🪑 Seat"
    const val MORE = "🌫 More"
    const val STATUS = "📊 Status"

    /** Lời nhắc gõ tên bài — câu trả lời thành `/play <chữ>`. */
    const val SONG_PROMPT = "🎵 Type a song name or paste a link:"

    /** Lời nhắc gõ điểm đến — câu trả lời thành `/go <chữ>`. */
    const val DEST_PROMPT = "🧭 Type a destination (saved name, address, or lat, lng):"

    /**
     * Phiên bản bàn phím. Tăng khi đổi nút ⇒ `:app` gửi lại bàn phím một lần cho chat đã có bàn phím cũ (Telegram giữ bàn
     * phím phía máy khách tới khi bot gửi bàn phím khác).
     */
    const val VERSION = 1

    /** Nhãn nút → domain bảng inline cần mở (`nav`·`music`·`ac`·`seat`·`misc`·`menu`). `null` = không phải nút bàn phím. */
    fun boardOf(text: String): String? = when (text.trim()) {
        GO -> "nav"
        MUSIC -> "music"
        AC -> "ac"
        SEAT -> "seat"
        MORE -> "misc"
        STATUS -> "menu"
        else -> null
    }

    /**
     * Tin trả lời một lời nhắc → chuỗi lệnh tương đương (`/play …` / `/go …`) để đi đúng bộ phân tích lệnh gõ. `null` khi
     * [replyToText] không phải lời nhắc của bot, chữ rỗng, hoặc người dùng gõ hẳn một lệnh `/…` (lệnh đó thắng).
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

    /** JSON `reply_markup` của bàn phím: 3 hàng × 2 nút, gọn (`resize_keyboard`), luôn hiện (`is_persistent`). */
    fun markup(): String {
        fun b(t: String) = """{"text":"$t"}"""
        val rows = listOf(listOf(GO, MUSIC), listOf(AC, SEAT), listOf(MORE, STATUS))
            .joinToString(",") { r -> "[" + r.joinToString(",") { b(it) } + "]" }
        return """{"keyboard":[$rows],"resize_keyboard":true,"is_persistent":true,"input_field_placeholder":"Tap a button or type /help"}"""
    }

    /** JSON `ForceReply` kèm gợi ý trong ô gõ ([placeholder] ≤ 64 ký tự — giới hạn của Telegram). */
    fun forceReply(placeholder: String): String =
        """{"force_reply":true,"input_field_placeholder":"${placeholder.take(64).replace("\"", "'")}"}"""
}
