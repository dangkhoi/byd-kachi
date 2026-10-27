package com.byd.clusternav.launcher.telegram

/** Văn bản `/help` của bot (3.1.21 — chỉ dẫn đường + nhạc). Thuần Kotlin. */
object TelegramInlineKeyboard {

    val HELP: String = buildString {
        appendLine("Kachi remote — navigation & music only:")
        appendLine("• Paste a Google Maps link — navigate (Google Maps)")
        appendLine("• Link + vietmap — Google Maps and VietMap")
        appendLine("• Paste a YouTube / YT Music link — play it (screen off: plays when you board)")
        appendLine("/go <place>            — saved place or address, default app")
        appendLine("/go <place> gmaps vietmap — pick apps: gmaps · vietmap · waze")
        appendLine("/play <song>           — search & play on default music app")
        appendLine("/play <song> youtube   — or ytmusic")
        appendLine("🧭 Go — tap a place, pick Google Maps / VietMap / Both; 📌 pins up to 3 on top")
        append("/cancel — clear pending destination · /help")
    }
}
