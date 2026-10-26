package com.byd.clusternav.launcher.telegram

import com.byd.clusternav.launcher.SavedPlaces

/**
 * Chữ / tin trả lời từ Telegram → [TelegramCommand]. Thuần Kotlin (`:core`) ⇒ kiểm off-car; bóc JSON là việc của `:app`.
 *
 * ## Lệnh (owner 26/10, 3.1.21 — chỉ dẫn đường + nhạc)
 * ```
 * <link Google Maps> [gmaps] [vietmap]     dán link chia sẻ ⇒ dẫn (không cần gõ /go)
 * <link YouTube | YT Music>                dán link ⇒ mở đúng app, phát đè bài đang phát
 * /play <tên bài> [youtube | ytmusic]      tìm rồi phát (không ghi app ⇒ App nhạc mặc định)
 * /play <link>                             như dán link
 * /go <nơi | địa chỉ | lat, lng | link> [gmaps] [vietmap] [waze]
 * /go · /places                            bảng nơi đã lưu + ➕ New
 * /cancel · /help · /start · /menu
 * ```
 * App ở CUỐI lệnh, viết `gmaps vietmap`, `gmaps+vietmap` hay `+ gmaps + vietmap` đều được. Một từ không phải tên app ⇒
 * dừng tách: nó thuộc tên nơi / tên bài (không đoán, không nuốt chữ của người dùng).
 */
object TelegramUpdateParser {

    /** Tên app dẫn đường ở cuối `/go`. */
    val NAV_APPS: Map<String, String> = mapOf("gmaps" to "gmaps", "googlemaps" to "gmaps", "vietmap" to "vietmap", "waze" to "waze")

    /** Tên app nhạc ở cuối `/play` → mã `VoiceAppTargets` (`youtube` / `ytmusic`). */
    val MUSIC_APPS: Map<String, String> = mapOf(
        "youtube" to "youtube", "yt" to "youtube", "ytmusic" to "ytmusic", "ytm" to "ytmusic", "youtubemusic" to "ytmusic",
    )

    /**
     * @param replyToText chữ của tin mà tin này TRẢ LỜI (ForceReply) — lời nhắc của bot ⇒ đổi thành `/play` · `/go`.
     */
    fun parseText(raw: String, replyToText: String? = null): TelegramCommand {
        val text = raw.trim()
        if (text.isEmpty()) return TelegramCommand.Unknown("")
        TelegramReplyKeyboard.answerOf(text, replyToText)?.let { return parseText(it) }
        TelegramReplyKeyboard.commandOf(text)?.let { return it }
        if (!text.startsWith("/")) return parsePlain(text)
        val sp = text.indexOfFirst { it.isWhitespace() }
        val verb = (if (sp < 0) text.substring(1) else text.substring(1, sp)).substringBefore('@').lowercase()
        val rest = if (sp < 0) "" else text.substring(sp + 1).trim()
        return when (verb) {
            "go", "nav" -> if (rest.isEmpty()) TelegramCommand.ListPlaces else parseGo(rest)
            "places" -> TelegramCommand.ListPlaces
            "play", "music" -> if (rest.isEmpty()) TelegramCommand.AskSong else parsePlay(rest)
            "cancel" -> TelegramCommand.Cancel
            "menu", "start" -> TelegramCommand.Menu
            "help" -> TelegramCommand.Help
            else -> TelegramCommand.Unknown(SavedPlaces.sanitize(text))
        }
    }

    /**
     * Tin không có `/`: chỉ nhận LINK (Google Maps ⇒ dẫn, YouTube / YT Music ⇒ nhạc). Chữ trơn khác ⇒ [TelegramCommand.Unknown]
     * — một câu nhắn lạc tay ("ok thanks") không được thành điểm đến.
     */
    private fun parsePlain(text: String): TelegramCommand {
        MapsShareLink.find(text)?.let { link ->
            val after = text.substringAfter(link)
            return TelegramCommand.Nav(link, apps = trailingApps(after.split(WS).filter { it.isNotBlank() }, NAV_APPS).second)
        }
        MusicLink.find(text)?.let { return TelegramCommand.Music(it, isQuery = false) }
        return TelegramCommand.Unknown(SavedPlaces.sanitize(text))
    }

    private fun parseGo(rest: String): TelegramCommand {
        MapsShareLink.find(rest)?.let { link ->
            return TelegramCommand.Nav(link, apps = trailingApps(rest.substringAfter(link).split(WS).filter { it.isNotBlank() }, NAV_APPS).second)
        }
        val (place, apps) = trailingApps(rest.split(WS).filter { it.isNotBlank() }, NAV_APPS)
        if (place.isEmpty()) return TelegramCommand.ListPlaces
        return TelegramCommand.Nav(SavedPlaces.sanitize(place), apps = apps)
    }

    private fun parsePlay(rest: String): TelegramCommand {
        MusicLink.find(rest)?.let { return TelegramCommand.Music(it, isQuery = false) }
        val (song, apps) = trailingApps(rest.split(WS).filter { it.isNotBlank() }, MUSIC_APPS)
        if (song.isEmpty()) return TelegramCommand.AskSong
        return TelegramCommand.Music(SavedPlaces.sanitize(song), isQuery = true, app = apps.lastOrNull())
    }

    /**
     * Tách các tên app ở CUỐI dãy từ: mỗi từ là `+`, hoặc các tên trong [table] nối bằng `+` (có thể dính `+` đầu). Gặp một
     * từ khác ⇒ dừng. Trả (phần đầu ghép lại, danh sách mã app theo thứ tự gõ, không trùng).
     */
    private fun trailingApps(words: List<String>, table: Map<String, String>): Pair<String, List<String>> {
        var end = words.size
        val found = ArrayList<String>()
        while (end > 0) {
            val w = words[end - 1]
            val parts = w.split('+').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) { end--; continue }                 // "+" đứng riêng
            if (!parts.all { it in table }) break
            found.addAll(0, parts.map { table.getValue(it) })
            end--
        }
        val head = words.subList(0, end).joinToString(" ").trim().trimEnd('+').trim()
        return head to found.distinct()
    }

    /** Toạ độ từ share-location 📍 → dẫn đường bằng app mặc định. */
    fun parseLocation(lat: Double, lng: Double): TelegramCommand =
        TelegramCommand.Nav(placeOrQuery = "Shared location", coords = "$lat, $lng")

    private val WS = Regex("\\s+")
}

/** Link nhạc YouTube / YT Music. Thuần Kotlin. */
object MusicLink {
    private val URL_RE = Regex("""https?://\S+""")
    private val YT = Regex("""^https?://(www\.|m\.)?(youtube\.com|youtu\.be)(/|$)""", RegexOption.IGNORE_CASE)
    private val YTM = Regex("""^https?://music\.youtube\.com(/|$)""", RegexOption.IGNORE_CASE)

    /** Link YouTube / YT Music đầu tiên trong [text]. */
    fun find(text: String): String? = URL_RE.findAll(text).map { it.value.trimEnd(')', '.', ',', '>') }
        .firstOrNull { YT.containsMatchIn(it) || YTM.containsMatchIn(it) }

    /** Mã app (`ytmusic` / `youtube`) theo host; `null` = không phải link nhạc. */
    fun appOf(url: String): String? = when {
        YTM.containsMatchIn(url) -> "ytmusic"
        YT.containsMatchIn(url) -> "youtube"
        else -> null
    }
}
