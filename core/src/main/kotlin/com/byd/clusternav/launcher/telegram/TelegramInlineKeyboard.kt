package com.byd.clusternav.launcher.telegram

import com.byd.clusternav.launcher.SavedPlace

/**
 * Dựng `reply_markup` (inline keyboard) + câu phản hồi cho bot — spec `kachi-telegram-remote.html` R7. Thuần
 * Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car. Trả CHUỖI JSON để `:app` nhét vào tham số `sendMessage`.
 *
 * ## Vì sao tự ghép JSON thay vì org.json
 * `:core` cấm `android.*` và không kéo thêm thư viện JSON cho một payload cố định hình dạng. Dữ liệu vào là tên
 * nơi (đã khử ký tự cấu trúc qua [SavedPlaces.sanitize] khi lưu) + mã app (chữ+số) ⇒ chỉ cần thoát `"` và `\`
 * cho an toàn JSON. [jsonEscape] làm đúng việc đó.
 */
object TelegramInlineKeyboard {

    /**
     * Nút mỗi nơi → callback `navi:<index>`. Dùng CHỈ SỐ, không tên (P2-17: Telegram cap `callback_data` 64 byte;
     * tên tiếng Việt có dấu dễ vượt ⇒ cả bàn nút 400). `:app` giải index → mục trong sổ (cùng danh sách [places]).
     * Rỗng sổ ⇒ chuỗi rỗng.
     */
    fun placesKeyboard(places: List<SavedPlace>): String {
        if (places.isEmpty()) return ""
        val rows = places.mapIndexed { i, p ->
            """[{"text":"${jsonEscape(p.name)}","callback_data":"navi:$i"}]"""
        }.joinToString(",")
        return """{"inline_keyboard":[$rows]}"""
    }

    /** Câu bot trả sau khi nhận lệnh (ngắn, tiếng Anh + emoji trạng thái). */
    fun ackText(cmd: TelegramCommand): String = when (cmd) {
        is TelegramCommand.Nav -> "🧭 Navigating to “${cmd.placeOrQuery}”"
        is TelegramCommand.Music -> "🎵 Opening music"
        is TelegramCommand.Control -> "🚗 ${cmd.controlId} ${cmd.value ?: "toggle"}"
        TelegramCommand.Cancel -> "✖️ Pending destination cleared"
        TelegramCommand.ListPlaces -> "📍 Pick a destination:"
        TelegramCommand.Help -> HELP
        is TelegramCommand.Unknown -> "🤔 Didn’t get that. Try /help"
    }

    val HELP: String = buildString {
        appendLine("Kachi remote — commands:")
        appendLine("/go <place>            — navigate (default app)")
        appendLine("/go <place> vietmap    — pick app: gmaps/vietmap/waze (or gmaps+vietmap)")
        appendLine("/go                    — pick from saved places")
        appendLine("/play <link>           — play (YouTube / YT Music by URL)")
        appendLine("/play <song name>      — search on default music app")
        appendLine("/ac on · /defrost · /fan 3 · /temp 22 — car controls")
        append("/cancel — clear pending · /help")
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}
