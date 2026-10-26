package com.byd.clusternav.launcher.telegram

import com.byd.clusternav.launcher.SavedPlace

/**
 * Dựng JSON `reply_markup` (inline keyboard) cho MỌI bảng của menu Telegram — spec `kachi-telegram-buttons.html`
 * §4.2–4.6. Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car. Trả CHUỖI JSON để `:app` nhét vào tham số
 * `sendMessage`/`editMessageReplyMarkup`.
 *
 * ## Bất biến thiết kế (QD)
 * - Mọi `callback_data` qua [TelegramCallback.encode] ⇒ ≤64 byte, mang [nonce] bảng (QD-15/16).
 * - Nút là LỆNH TUYỆT ĐỐI: id mang giá trị đích (`24` cho 24°, `cool1` cho ghế), không "toggle" (QD-05).
 * - Nhãn: emoji + từ tiếng Anh (QD-19, OQ3). ≤4 nút/hàng.
 * - Tên địa điểm (data người dùng) chỉ ở NHÃN, callback dùng CHỈ SỐ (QD-15).
 */
object TelegramMenu {

    private fun btn(text: String, cb: TelegramCallback): String =
        """{"text":"${esc(text)}","callback_data":"${esc(cb.encode())}"}"""

    private fun row(vararg buttons: String): String = "[" + buttons.joinToString(",") + "]"

    private fun markup(vararg rows: String): String = """{"inline_keyboard":[${rows.joinToString(",")}]}"""

    /** Menu chính: hàng nhanh (2 địa điểm ghim + macro Mát) + 4 nhóm + Khác + Làm mới (QD-01/04). */
    fun main(nonce: Int, pinned: List<SavedPlace>): String {
        val quick = mutableListOf<String>()
        pinned.take(2).forEachIndexed { i, p ->
            quick += btn("📌 ${p.name}", TelegramCallback(nonce, "nav", "pinned", i.toString()))
        }
        quick += btn("❄️ Cool 24°", TelegramCallback(nonce, "ac", "macro", "cool24"))
        return markup(
            row(*quick.toTypedArray()),
            row(btn("🧭 Navigate", TelegramCallback(nonce, "nav", "open")),
                btn("❄️ AC", TelegramCallback(nonce, "ac", "open"))),
            row(btn("🎵 Music", TelegramCallback(nonce, "music", "open")),
                btn("🪑 Seat", TelegramCallback(nonce, "seat", "open"))),
            row(btn("🌫 More", TelegramCallback(nonce, "misc", "open")),
                btn("🔄 Refresh", TelegramCallback(nonce, "menu", "refresh"))),
        )
    }

    /** Bảng Điều hoà (§4.3): AUTO · preset nhiệt + −/+ (nút giữa chết) · quạt preset + −/+ · sấy kính (PM2.5 → Khác, QD-22). */
    fun ac(nonce: Int, autoOn: Boolean?, tempC: Int?, fan: Int?, defrostOn: Boolean?): String = markup(
        row(btn(if (autoOn == true) "AUTO: ON ✓" else "AUTO: ON", TelegramCallback(nonce, "ac", "auto", "on")),
            btn(if (autoOn == false) "AUTO: OFF ✓" else "AUTO: OFF", TelegramCallback(nonce, "ac", "auto", "off"))),
        row(*TEMP_PRESETS.map { t ->
            btn(if (tempC == t) "$t° ✓" else "$t°", TelegramCallback(nonce, "ac", "temp", t.toString()))
        }.toTypedArray()),
        row(btn("− 1°", TelegramCallback(nonce, "ac", "tempdn")),
            btn("${tempC ?: "—"}°", TelegramCallback(nonce, "ac", "noop")),   // nút chết: hiện giá trị + đệm chống bấm lệch
            btn("+ 1°", TelegramCallback(nonce, "ac", "tempup"))),
        row(*FAN_PRESETS.map { (label, lv) ->
            btn(if (fan == lv) "$label ✓" else label, TelegramCallback(nonce, "ac", "fan", lv.toString()))
        }.toTypedArray()),
        row(btn("− fan", TelegramCallback(nonce, "ac", "fandn")),            // P2-8: −/+ quạt cho mức lẻ
            btn("fan ${fan ?: "—"}", TelegramCallback(nonce, "ac", "noop")),
            btn("+ fan", TelegramCallback(nonce, "ac", "fanup"))),
        row(btn(mark("🌬 Defrost On", defrostOn == true), TelegramCallback(nonce, "ac", "defrost", "on")),
            btn(mark("🌬 Off", defrostOn == false), TelegramCallback(nonce, "ac", "defrost", "off"))),
        row(btn("‹ Menu", TelegramCallback(nonce, "menu", "main"))),
    )

    /** Bảng Ghế (§4.4): phẳng 4 mức/ghế + Tắt cả hàng, cho lái + phụ (QD-09). */
    fun seat(nonce: Int, driver: String?, passenger: String?): String = markup(
        row(*seatRow(nonce, "d", driver)),
        row(btn("⏹ Driver off", TelegramCallback(nonce, "seat", "d", "off"))),
        row(*seatRow(nonce, "p", passenger)),
        row(btn("⏹ Passenger off", TelegramCallback(nonce, "seat", "p", "off"))),
        row(btn("‹ Menu", TelegramCallback(nonce, "menu", "main"))),
    )

    private fun seatRow(nonce: Int, side: String, cur: String?): Array<String> = arrayOf(
        btn(mark("🧊 Cool 1", cur == "cool1"), TelegramCallback(nonce, "seat", side, "cool1")),
        btn(mark("🧊 Cool 2", cur == "cool2"), TelegramCallback(nonce, "seat", side, "cool2")),
        btn(mark("♨️ Heat 1", cur == "heat1"), TelegramCallback(nonce, "seat", side, "heat1")),
        btn(mark("♨️ Heat 2", cur == "heat2"), TelegramCallback(nonce, "seat", side, "heat2")),
    )

    /** Bảng Nhạc + transport (§4.5, QD-25): ⏮/⏸▶️/⏭ + phát nhanh app mặc định. */
    fun music(nonce: Int, playing: Boolean?): String = markup(
        row(btn("⏮ Prev", TelegramCallback(nonce, "music", "prev")),
            btn(if (playing == true) "⏸ Pause" else "▶️ Play", TelegramCallback(nonce, "music", if (playing == true) "pause" else "resume")),
            btn("⏭ Next", TelegramCallback(nonce, "music", "next"))),
        // 3.1.20: bấm ⇒ bot nhắc gõ tên bài (ForceReply) ⇒ câu trả lời thành /play <tên>.
        row(btn("🔎 Song", TelegramCallback(nonce, "music", "song")),
            btn("‹ Menu", TelegramCallback(nonce, "menu", "main"))),
    )

    /** Bảng Dẫn đường (§4.6): phân trang 6 nơi/trang, 2 nút/hàng, GPS ở status (QD-10/12/13). */
    fun nav(nonce: Int, places: List<SavedPlace>, page: Int): String {
        val perPage = 6
        val start = page * perPage
        val slice = places.drop(start).take(perPage)
        val rows = mutableListOf<String>()
        slice.chunked(2).forEachIndexed { r, pair ->
            rows += "[" + pair.mapIndexed { c, p ->
                btn(p.name, TelegramCallback(nonce, "nav", "go", (start + r * 2 + c).toString()))
            }.joinToString(",") + "]"
        }
        val nextCount = places.size - (start + slice.size)
        // 3.1.20: ➕ New ⇒ bot nhắc gõ điểm đến (ForceReply) ⇒ câu trả lời thành /go <chữ>.
        rows += row(btn("➕ New destination", TelegramCallback(nonce, "nav", "new")))
        val footer = mutableListOf(btn("‹ Menu", TelegramCallback(nonce, "menu", "main")))
        if (nextCount > 0) footer += btn("⬇️ More ($nextCount)", TelegramCallback(nonce, "page", "nav", (page + 1).toString()))
        rows += "[" + footer.joinToString(",") + "]"
        return markup(*rows.toTypedArray())
    }

    /** Tin xác nhận sau khi đặt điểm đến (§4.6): nút ➕ mở thêm app + Huỷ (CHỈ ở đây — QD-11/14). */
    fun navConfirm(nonce: Int, placeIndex: Int): String = markup(
        row(btn("➕ VietMap", TelegramCallback(nonce, "nav", "also", "vietmap:$placeIndex")),
            btn("➕ Waze", TelegramCallback(nonce, "nav", "also", "waze:$placeIndex"))),
        row(btn("↩️ Cancel destination", TelegramCallback(nonce, "nav", "cancel")),
            btn("‹ Menu", TelegramCallback(nonce, "menu", "main"))),
    )

    /** Bảng Khác (§4.9 R9, QD-22): đèn đọc + PM2.5 + bộ phận cổng-tốc-độ (cốp/kính/nóc) — nhãn mang trạng thái. */
    fun misc(nonce: Int, readLightOn: Boolean?, pm25On: Boolean?): String = markup(
        // 3.1.20 (owner 26/10: "mở đèn đọc được mà không tắt được"): đèn đọc KHÔNG có đường đọc ⇒ trạng thái luôn "—" ⇒
        // nút đảo bật/tắt cũ chỉ gửi BẬT. Nay mỗi việc hai nút TUYỆT ĐỐI On / Off (QD-05), trạng thái chỉ là nhãn.
        row(btn(mark("💡 Read light On", readLightOn == true), TelegramCallback(nonce, "misc", "readl", "on")),
            btn(mark("💡 Off", readLightOn == false), TelegramCallback(nonce, "misc", "readl", "off"))),
        row(btn(mark("🌫 PM2.5 On", pm25On == true), TelegramCallback(nonce, "misc", "pm25", "on")),
            btn(mark("🌫 Off", pm25On == false), TelegramCallback(nonce, "misc", "pm25", "off"))),
        // Cổng tốc độ: NÚT VẪN HIỆN (QD-20), bấm lúc chạy ⇒ toast "only when stopped". Phải nói rõ open/close (R9).
        row(btn("🧳 Trunk open", TelegramCallback(nonce, "misc", "trunk", "on")),
            btn("🧳 Trunk close", TelegramCallback(nonce, "misc", "trunk", "off"))),
        row(btn("🪟 Windows open", TelegramCallback(nonce, "misc", "windows", "on")),
            btn("🪟 Windows close", TelegramCallback(nonce, "misc", "windows", "off"))),
        row(btn("‹ Menu", TelegramCallback(nonce, "menu", "main"))),
    )

    /** Bảng /places (OQ1): mỗi nơi 2 nút [tên=dẫn] [📌=ghim toggle]. Ghim quyết hàng nhanh menu chính. */
    fun placesManage(nonce: Int, places: List<SavedPlace>, pinned: Set<String>): String {
        if (places.isEmpty()) return markup(row(btn("‹ Menu", TelegramCallback(nonce, "menu", "main"))))
        val rows = places.mapIndexed { i, p ->
            val pin = if (p.name in pinned) "📌 ✓" else "📌"
            row(btn(p.name, TelegramCallback(nonce, "nav", "go", i.toString())),
                btn(pin, TelegramCallback(nonce, "nav", "pin", i.toString())))
        }.toMutableList()
        rows += row(btn("‹ Menu", TelegramCallback(nonce, "menu", "main")))
        return markup(*rows.toTypedArray())
    }

    private fun mark(label: String, on: Boolean) = if (on) "$label ✓" else label

    private val TEMP_PRESETS = listOf(20, 22, 24, 26)
    private val FAN_PRESETS = listOf("Off" to 0, "Low 2" to 2, "Mid 4" to 4, "High 6" to 6)

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}
