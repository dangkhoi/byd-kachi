package com.byd.clusternav.telegram

import android.content.Context
import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.telegram.MapsShareLink
import com.byd.clusternav.launcher.telegram.TelegramReplyKeyboard
import com.byd.clusternav.setTelegramKbVersion
import com.byd.clusternav.telegramKbVersion

/**
 * Bàn phím nút dưới ô gõ + lời nhắc gõ chữ (3.1.20, owner 26/10) — tách khỏi [TelegramBridgeService] theo VAI (trần 500
 * dòng). Nghĩa của nút/lời nhắc ở `:core` [TelegramReplyKeyboard]; tệp này chỉ GỬI.
 */

/**
 * Gửi bàn phím nút MỘT lần cho mỗi [TelegramReplyKeyboard.VERSION]. Telegram giữ bàn phím phía máy khách, nên chỉ cần gửi
 * lại khi đổi nút. Gửi hỏng ⇒ không ghi phiên bản ⇒ lượt sau thử lại.
 */
internal fun ensureReplyKeyboard(app: Context, api: TelegramApi, chatId: Long) {
    if (Prefs.telegramKbVersion(app) >= TelegramReplyKeyboard.VERSION) return
    val ok = api.sendMessage(chatId, "⌨️ Quick buttons are now below the text box.", TelegramReplyKeyboard.markup())
    if (ok) Prefs.setTelegramKbVersion(app, TelegramReplyKeyboard.VERSION)
}

/**
 * Link rút gọn `maps.app.goo.gl/…` ⇒ URL đầy đủ, đi TỪNG bước chuyển hướng bằng header `Location` (không tải trang — trang
 * đích không có gì dùng được, [ĐO 26/10]), tối đa 4 bước, mỗi bước hạn đọc 7 s. Link đầy đủ / lỗi mạng ⇒ trả nguyên [url]
 * (bộ đọc lấy được gì thì lấy). Chạy trên luồng polling (nền). Không log url (có thể mang vị trí của chủ xe).
 */
internal fun resolveMapsLink(url: String): String {
    var cur = url
    repeat(4) {
        if (!MapsShareLink.isShort(cur) || !cur.startsWith("https://")) return cur
        val conn = runCatching { com.byd.clusternav.net.HttpConn.open(cur, 7_000) }.getOrNull() ?: return cur
        try {
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            val next = conn.getHeaderField("Location")
            if (code !in 300..399 || next.isNullOrBlank()) return cur
            cur = java.net.URL(java.net.URL(cur), next).toString()
        } catch (t: Throwable) {
            android.util.Log.w("KachiTelegram", "mở link Google Maps lỗi: ${t.javaClass.simpleName}")
            return cur
        } finally { runCatching { conn.disconnect() } }
    }
    return cur
}
