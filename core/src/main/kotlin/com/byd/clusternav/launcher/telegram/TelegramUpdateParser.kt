package com.byd.clusternav.launcher.telegram

import com.byd.clusternav.launcher.SavedPlaces

/**
 * Phân tích LỆNH từ chữ / callback Telegram → [TelegramCommand] — spec `kachi-telegram-remote.html` R6/R7. Thuần
 * Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car. Việc bóc JSON update thô là của `:app` ([TelegramApi]); lớp
 * này nhận trường ĐÃ TRÍCH (text / callback-data / toạ độ share-location) — phần quyết định nghĩa, nơi bài kiểm
 * chạm tới.
 *
 * ## Lệnh (tiếng Anh — owner 26/10)
 * ```
 * /go <nơi hoặc địa chỉ>      dẫn tới nơi trong sổ địa chỉ / chữ / toạ độ
 * /go                         (trống) ⇒ ListPlaces (bot render nút)
 * /play <link>                mở nhạc
 * /cancel                     xoá điểm đến chờ
 * /help                       liệt kê lệnh
 * <control> on|off|<số>       điều khiển xe: /ac on · /defrost · /fan 3 · /temp 22
 * ```
 * Callback-data từ nút: `nav:<tên nơi>` hoặc `nav:<tên nơi>:<apps>`.
 */
object TelegramUpdateParser {

    /** Cụm app dẫn đường nối sau tên nơi trong callback (giống `ScheduledNavRule.navApp`). */
    private const val APP_SEP = '+'

    /**
     * Chữ người dùng gõ → lệnh.
     *
     * @param isControlId trả `true` nếu chuỗi là id control hợp lệ trong `ControlRegistry` — tiêm từ `:app` để
     *   `:core` không phải mang bộ đăng ký điều khiển vào mình (cùng lối `VoiceWiring` tiêm bảng app).
     */
    fun parseText(raw: String, isControlId: (String) -> Boolean = { false }): TelegramCommand {
        val text = raw.trim()
        if (text.isEmpty()) return TelegramCommand.Unknown("")
        // Tách lệnh: "/go Office" → verb="go", rest="Office". KHÔNG có "/" ⇒ Unknown (P1-7: một tin nhắn lạc
        // tay như "ok thanks" KHÔNG được biến thành điểm đến chờ + chặn lịch cả chuyến). Muốn dẫn thì phải /go.
        if (!text.startsWith("/")) return TelegramCommand.Unknown(SavedPlaces.sanitize(text))
        val sp = text.indexOf(' ')
        val verb = (if (sp < 0) text.substring(1) else text.substring(1, sp)).lowercase()
        val rest = if (sp < 0) "" else text.substring(sp + 1).trim()
        return when (verb) {
            "go", "nav" -> if (rest.isEmpty()) TelegramCommand.ListPlaces else TelegramCommand.Nav(SavedPlaces.sanitize(rest))
            "play", "music" -> if (rest.isEmpty()) TelegramCommand.Unknown(text) else TelegramCommand.Music(rest.trim())
            "cancel", "stop" -> TelegramCommand.Cancel
            "help", "start" -> TelegramCommand.Help
            else -> {
                // Điều khiển xe: verb = id control (hoặc bí danh). "on"/"off"/số là tham số.
                val id = CONTROL_ALIASES[verb] ?: verb
                if (isControlId(id)) {
                    TelegramCommand.Control(id, parseControlValue(rest))
                } else {
                    TelegramCommand.Unknown(SavedPlaces.sanitize(text))
                }
            }
        }
    }

    /** `on`→1 · `off`→0 · số→số · rỗng→null (toggle). Chỉ tiếng Anh (spec chốt lệnh English — P3). */
    private fun parseControlValue(rest: String): Int? = when (val r = rest.trim().lowercase()) {
        "", "toggle" -> null
        "on", "open" -> 1
        "off", "close" -> 0
        else -> r.toIntOrNull()
    }

    /** Bí danh lệnh → id `ControlRegistry` (chỉ vài cái tên khác id; còn lại gõ thẳng id). */
    private val CONTROL_ALIASES: Map<String, String> = mapOf(
        "ac" to "ac_auto",
        "fan" to "fan",
        "temp" to "temp",
        "defrost" to "defrost",
        "seat" to "seatc",
        "readlight" to "readl",
        "filter" to "pm25",
        "recirc" to "recirc",
        "camera" to "cam",
    )

    /**
     * Toạ độ từ share-location 📍 → [TelegramCommand.Nav] với [coords]. Tên nơi = "📍 vị trí đã chia sẻ" (hiển
     * thị), apps rỗng ⇒ `:app` dùng mặc định hồ sơ.
     */
    fun parseLocation(lat: Double, lng: Double): TelegramCommand =
        TelegramCommand.Nav(placeOrQuery = "Shared location", coords = "$lat, $lng")

    /** Callback-data từ nút inline. `navi:<index>` (từ [TelegramInlineKeyboard], P2-17) hoặc `nav:<tên>[:<apps>]`. */
    fun parseCallback(data: String): TelegramCommand {
        val parts = data.split(':')
        return when (parts.getOrNull(0)) {
            // navi:<index> — `:app` giải index → mục trong sổ (placeOrQuery = "#<index>").
            "navi" -> parts.getOrNull(1)?.trim()?.toIntOrNull()
                ?.let { TelegramCommand.Nav("#$it") } ?: TelegramCommand.Unknown(data)
            "nav" -> {
                val name = parts.getOrNull(1)?.let { SavedPlaces.sanitize(it) }.orEmpty()
                if (name.isEmpty()) return TelegramCommand.Unknown(data)
                val apps = parts.getOrNull(2)?.split(APP_SEP)?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: emptyList()
                TelegramCommand.Nav(name, apps = apps)
            }
            "cancel" -> TelegramCommand.Cancel
            else -> TelegramCommand.Unknown(SavedPlaces.sanitize(data))
        }
    }
}
