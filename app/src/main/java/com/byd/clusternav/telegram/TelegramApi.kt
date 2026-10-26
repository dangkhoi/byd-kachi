package com.byd.clusternav.telegram

import android.util.Log
import com.byd.clusternav.launcher.telegram.TelegramCommand
import com.byd.clusternav.launcher.telegram.TelegramUpdate
import com.byd.clusternav.launcher.telegram.TelegramUpdateParser
import com.byd.clusternav.net.HttpConn
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Bọc 4 endpoint Telegram Bot API qua [HttpConn] (HTTPS GET) — spec `kachi-telegram-remote.html` T8. `:app`:
 * chạm mạng + bóc JSON (org.json). Phần QUYẾT ĐỊNH nghĩa lệnh ở `:core` [TelegramUpdateParser].
 *
 * ## ⚠ Token KHÔNG BAO GIỜ lọt log (senior review P0-1)
 * URL nhúng token (`.../bot<token>/...`). Trên Android `HttpURLConnection.getInputStream()` ném
 * `FileNotFoundException(url)` ở MỌI mã ≥ 400, mà message CHÍNH LÀ url kèm token — token sai ⇒ 401 mỗi vòng poll
 * ⇒ nếu log throwable thì token ghi vào logcat ~1 lần/phút. Nên [call] đọc `responseCode` TRƯỚC (không ném),
 * dùng `errorStream` cho 4xx, và **không bao giờ** log `url`/throwable — chỉ log mã HTTP + tên lớp ngoại lệ.
 *
 * ## Degrade-safe (R-nf2) + đóng kết nối (P2-13)
 * Mọi lời gọi trả rỗng/false khi lỗi; `disconnect()` trong `finally` (HttpConn không tự đóng).
 */
class TelegramApi(private val botToken: String) {

    private fun base() = "https://api.telegram.org/bot$botToken"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** GET đã an-token: trả body (2xx) hoặc `null`; không để url/throwable tới log. */
    private fun call(path: String, query: String, readTimeoutMs: Int): String? {
        val conn = HttpConn.open("${base()}/$path?$query", readTimeoutMs, "application/json")
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            if (code in 200..299) {
                body
            } else {
                Log.w(TAG, "telegram $path HTTP $code")   // KHÔNG in url/token
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "telegram $path lỗi: ${t.javaClass.simpleName}")   // KHÔNG truyền throwable (message = url+token)
            null
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /**
     * Đọc update mới hơn [offset] (long-poll [timeoutSec] giây). Trả list [TelegramUpdate] đã parse; rỗng khi
     * không có / lỗi. Con trỏ: chỗ gọi đặt `offset = max(update_id)+1`.
     */
    fun getUpdates(offset: Long, timeoutSec: Int, isControlId: (String) -> Boolean): List<TelegramUpdate> {
        val body = call(
            "getUpdates",
            "offset=$offset&timeout=$timeoutSec&allowed_updates=%5B%22message%22,%22callback_query%22%5D",
            readTimeoutMs = (timeoutSec + 10) * 1000,
        ) ?: return emptyList()
        return runCatching {
            val root = JSONObject(body)
            if (!root.optBoolean("ok", false)) return@runCatching emptyList()
            val arr = root.optJSONArray("result") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { i -> parseUpdate(arr.getJSONObject(i), isControlId) }
        }.getOrElse {
            Log.w(TAG, "getUpdates parse lỗi: ${it.javaClass.simpleName}")
            emptyList()
        }
    }

    /** Bóc MỘT update JSON → [TelegramUpdate] (nghĩa lệnh do `:core` quyết). Chỉ nhận chat RIÊNG TƯ (P2-11). */
    private fun parseUpdate(u: JSONObject, isControlId: (String) -> Boolean): TelegramUpdate? {
        val updateId = u.optLong("update_id", -1).takeIf { it >= 0 } ?: return null
        // callback_query (nút bấm) — lấy id người bấm (from.id) để kiểm quyền, không chỉ chat.id (P2-11).
        u.optJSONObject("callback_query")?.let { cq ->
            val data = cq.optString("data", "")
            val message = cq.optJSONObject("message")
            val chat = message?.optJSONObject("chat")
            if (chat != null && chat.optString("type", "private") != "private") return null
            val fromId = cq.optJSONObject("from")?.optLong("id")
            val cbId = cq.optString("id", "").takeIf { it.isNotEmpty() }
            val msgId = message?.optLong("message_id")?.takeIf { it > 0 }
            // chatId = from.id (NHẤT QUÁN với message path: authorize theo NGƯỜI bấm). Trong chat riêng tư 1-1
            // from.id == chat.id nên gửi tin/sửa bảng vẫn đúng đích. rawCallback giữ chuỗi gốc cho menu mới.
            return TelegramUpdate(updateId, fromId, TelegramUpdateParser.parseCallback(data), cbId, msgId, rawCallback = data)
        }
        // message
        val msg = u.optJSONObject("message") ?: return TelegramUpdate(updateId, null, TelegramCommand.Unknown(""))
        // Chỉ nhận chat RIÊNG TƯ: bot trong group thì mọi thành viên không được lái xe (P2-11).
        if (msg.optJSONObject("chat")?.optString("type", "private") != "private") return null
        val fromId = msg.optJSONObject("from")?.optLong("id")
        // share-location 📍
        msg.optJSONObject("location")?.let { loc ->
            val lat = loc.optDouble("latitude", Double.NaN)
            val lng = loc.optDouble("longitude", Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) {
                return TelegramUpdate(updateId, fromId, TelegramUpdateParser.parseLocation(lat, lng))
            }
        }
        val text = msg.optString("text", "")
        return TelegramUpdate(updateId, fromId, TelegramUpdateParser.parseText(text, isControlId))
    }

    /** Gửi tin (text + inline keyboard tuỳ chọn). `false` khi lỗi. */
    fun sendMessage(chatId: Long, text: String, replyMarkupJson: String? = null): Boolean {
        var q = "chat_id=$chatId&text=${enc(text)}"
        if (!replyMarkupJson.isNullOrBlank()) q += "&reply_markup=${enc(replyMarkupJson)}"
        return call("sendMessage", q, readTimeoutMs = 10_000) != null
    }

    /** Như [sendMessage] nhưng trả `message_id` của tin vừa gửi (cho một-bảng-sống QD-17). `null` khi lỗi. */
    fun sendMessageReturningId(chatId: Long, text: String, replyMarkupJson: String? = null): Long? {
        var q = "chat_id=$chatId&text=${enc(text)}"
        if (!replyMarkupJson.isNullOrBlank()) q += "&reply_markup=${enc(replyMarkupJson)}"
        val body = call("sendMessage", q, readTimeoutMs = 10_000) ?: return null
        return runCatching { JSONObject(body).optJSONObject("result")?.optLong("message_id")?.takeIf { it > 0 } }.getOrNull()
    }

    /** Đóng vòng xoay "đang xử lý" + toast tuỳ chọn (R-nf4: gọi &lt;1s TRƯỚC khi chờ xe). */
    fun answerCallback(callbackId: String, toast: String? = null): Boolean {
        var q = "callback_query_id=${enc(callbackId)}"
        if (!toast.isNullOrBlank()) q += "&text=${enc(toast)}"
        return call("answerCallbackQuery", q, readTimeoutMs = 10_000) != null
    }

    /** Sửa text + bảng của một tin đã gửi (QD-17 một-bảng-sống — vẽ lại tại chỗ). */
    fun editMessageText(chatId: Long, messageId: Long, text: String, replyMarkupJson: String? = null): Boolean {
        var q = "chat_id=$chatId&message_id=$messageId&text=${enc(text)}"
        if (!replyMarkupJson.isNullOrBlank()) q += "&reply_markup=${enc(replyMarkupJson)}"
        return call("editMessageText", q, readTimeoutMs = 10_000) != null
    }

    /** Gỡ nút của một tin cũ (QD-17: bảng cũ còn text nhưng không bấm được nữa). */
    fun clearMarkup(chatId: Long, messageId: Long): Boolean =
        call("editMessageReplyMarkup", "chat_id=$chatId&message_id=$messageId", readTimeoutMs = 10_000) != null

    /** Xoá hẳn một tin (dùng khi dọn bảng cũ). */
    fun deleteMessage(chatId: Long, messageId: Long): Boolean =
        call("deleteMessage", "chat_id=$chatId&message_id=$messageId", readTimeoutMs = 10_000) != null

    /** Đăng ký gợi ý lệnh (gõ "/" hiện danh sách — QD-21). [commands] = cặp (lệnh không dấu '/', mô tả). */
    fun setMyCommands(commands: List<Pair<String, String>>): Boolean {
        val arr = commands.joinToString(",") { (cmd, desc) ->
            """{"command":"${escJson(cmd)}","description":"${escJson(desc)}"}"""
        }
        return call("setMyCommands", "commands=${enc("[$arr]")}", readTimeoutMs = 10_000) != null
    }

    private fun escJson(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val TAG = "KachiTelegram"
    }
}
