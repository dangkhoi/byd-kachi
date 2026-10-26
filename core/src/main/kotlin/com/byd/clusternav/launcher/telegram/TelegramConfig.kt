package com.byd.clusternav.launcher.telegram

/**
 * Cấu hình bridge Telegram (token + chat-id + công tắc) — spec `kachi-telegram-remote.html` R10. Thuần Kotlin
 * (`:core`, cấm `android.*`) ⇒ kiểm off-car. Lưu bền ở `:app`; lớp này giữ MÔ HÌNH + luật xác thực.
 *
 * ## Bảo mật — vì sao [isAuthorized]
 * Bot Telegram là PUBLIC: ai biết tên bot cũng nhắn được. Chỉ chủ xe mới được điều khiển ⇒ mọi update phải đến
 * từ đúng [allowedChatId]; update từ chat lạ bị BỎ (không trả lời — đỡ lộ bot đang sống). Token là secret ⇒
 * không log, không commit (R-nf, security scan).
 *
 * @property botToken token @BotFather (secret).
 * @property allowedChatId chat-id duy nhất được nhận lệnh; 0 = chưa cấu hình ⇒ [isAuthorized] luôn false.
 * @property enabled công tắc bridge (bật mới polling).
 */
data class TelegramConfig(
    val botToken: String = "",
    val allowedChatId: Long = 0,
    val enabled: Boolean = false,
) {
    /** Đã đủ cấu hình để polling chưa (bật + có token + có chat-id). */
    val ready: Boolean get() = enabled && botToken.isNotBlank() && allowedChatId != 0L

    /** Update này có từ chủ xe không. 0 (chưa cấu hình) / khác ⇒ false (fail-closed). */
    fun isAuthorized(chatId: Long?): Boolean = allowedChatId != 0L && chatId == allowedChatId
}
