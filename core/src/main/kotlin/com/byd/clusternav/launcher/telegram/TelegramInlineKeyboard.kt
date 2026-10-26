package com.byd.clusternav.launcher.telegram

/**
 * Văn bản `/help` của bot — spec `kachi-telegram-remote.html` R7 + `kachi-telegram-buttons.html` R12. Thuần Kotlin.
 *
 * ⚠ 3.1.16 · senior review 26/10 (P3 mã chết): `placesKeyboard` (callback `navi:`) và `ackText` đã GỠ — bảng nút nay do
 * [TelegramMenu] dựng (callback [TelegramCallback] có nonce), nên mọi callback dạng cũ là mã không ai sinh ra nữa.
 */
object TelegramInlineKeyboard {

    val HELP: String = buildString {
        appendLine("Kachi remote — commands (or just send /menu and tap):")
        appendLine("/menu                  — control panel with buttons")
        appendLine("/home · /office        — navigate to pinned place 1 / 2")
        appendLine("/go <place>            — navigate (default app)")
        appendLine("/go <place> vietmap    — pick app: gmaps/vietmap/waze (or gmaps+vietmap)")
        appendLine("/go <lat>, <lng>       — navigate to coordinates")
        appendLine("/places                — saved places, 📌 to pin")
        appendLine("/play <link>           — play (YouTube / YT Music by URL)")
        appendLine("/play <song name>      — search on default music app")
        appendLine("/ac on · /defrost on · /fan 3 · /temp 22 — car controls")
        appendLine("/trunk open · /windows_all close — only when parked (gear P)")
        append("/status · /cancel · /help")
    }
}
