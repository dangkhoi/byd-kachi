package com.byd.clusternav.launcher.telegram

import com.byd.clusternav.launcher.SavedPlace

/**
 * JSON `reply_markup` (inline keyboard) của bot. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * 3.1.21 (owner 26/10, chỉ dẫn đường + nhạc): bảng nơi đã lưu + ➕ New (bot hỏi điểm đến). 3.1.22 (owner 27/10): bấm
 * một nơi ⇒ [appPicker] chọn Google Maps / VietMap / cả hai + ghim nơi thường dùng lên đầu. Không bảng xe nào.
 *
 * Bất biến: `callback_data` qua [TelegramCallback.encode] (≤ 64 byte, mang nonce bảng); tên nơi (data người dùng) chỉ ở
 * NHÃN, callback dùng CHỈ SỐ trong sổ.
 */
object TelegramMenu {

    private const val PER_PAGE = 8

    private fun btn(text: String, cb: TelegramCallback): String =
        """{"text":"${esc(text)}","callback_data":"${esc(cb.encode())}"}"""

    private fun row(buttons: List<String>): String = "[" + buttons.joinToString(",") + "]"

    /** Số nơi ghim tối đa (owner 27/10: "pin 2-3 cái thường dùng lên đầu"). */
    const val MAX_PINS = 3

    /**
     * Thứ tự hiện: nơi đã ghim (theo thứ tự ghim) lên đầu, rồi các nơi còn lại theo sổ. Trả cặp (chỉ số TRONG SỔ, nơi) —
     * callback luôn mang chỉ số trong sổ ([placeId]), nên đổi thứ tự hiện không làm nút trỏ nhầm.
     */
    fun ordered(places: List<SavedPlace>, pinned: List<String>): List<Pair<Int, SavedPlace>> {
        val indexed = places.mapIndexed { i, p -> i to p }
        val top = pinned.take(MAX_PINS).mapNotNull { name -> indexed.firstOrNull { it.second.name == name } }.distinct()
        return top + indexed.filter { it !in top }
    }

    /** Bảng nơi đã lưu: nơi ghim (📌) lên đầu, 2 nơi / hàng, [PER_PAGE] nơi / trang, ➕ New luôn có, ⬇️ More khi còn trang. */
    fun places(nonce: Int, places: List<SavedPlace>, page: Int = 0, pinned: List<String> = emptyList()): String {
        val all = ordered(places, pinned)
        val pins = pinned.take(MAX_PINS).toSet()
        val start = (page * PER_PAGE).coerceAtLeast(0)
        val slice = all.drop(start).take(PER_PAGE)
        val rows = slice.chunked(2).map { pair ->
            row(pair.map { (i, p) ->
                btn((if (p.name in pins) "📌 " else "") + p.name, TelegramCallback(nonce, "nav", "go", placeId(i, p)))
            })
        }.toMutableList()
        val foot = mutableListOf(btn("➕ New destination", TelegramCallback(nonce, "nav", "new")))
        val left = all.size - (start + slice.size)
        if (left > 0) foot += btn("⬇️ More ($left)", TelegramCallback(nonce, "nav", "page", (page + 1).toString()))
        rows += row(foot)
        return """{"inline_keyboard":[${rows.joinToString(",")}]}"""
    }

    /** Mã app của nút chọn app → danh sách mã `VoiceAppTargets` (cùng mã của `/go … gmaps vietmap`). */
    val APP_CHOICES: Map<String, List<String>> = linkedMapOf(
        "g" to listOf("gmaps"),
        "v" to listOf("vietmap"),
        "gv" to listOf("gmaps", "vietmap"),
    )

    /**
     * Bảng chọn app cho MỘT nơi (owner 27/10: "chọn được dẫn app nào"): Google Maps · VietMap · cả hai, ⭐ = app mặc định;
     * 📌 ghim/bỏ ghim; ⬅️ về danh sách. [id] = [placeId] của nơi đó.
     */
    fun appPicker(nonce: Int, id: String, defaultApp: String, isPinned: Boolean, canPin: Boolean): String {
        fun star(key: String) = if (key == defaultApp) " ⭐" else ""
        val apps = row(listOf(
            btn("🗺 Google Maps" + star("gmaps"), TelegramCallback(nonce, "nav", "app", "$id:g")),
            btn("🧭 VietMap" + star("vietmap"), TelegramCallback(nonce, "nav", "app", "$id:v")),
        ))
        val both = row(listOf(btn("🗺+🧭 Both", TelegramCallback(nonce, "nav", "app", "$id:gv"))))
        val foot = mutableListOf<String>()
        if (isPinned) foot += btn("📍 Unpin", TelegramCallback(nonce, "nav", "pin", id))
        else if (canPin) foot += btn("📌 Pin to top", TelegramCallback(nonce, "nav", "pin", id))
        foot += btn("⬅️ Back", TelegramCallback(nonce, "nav", "page", "0"))
        return """{"inline_keyboard":[$apps,$both,${row(foot)}]}"""
    }

    /** Tách id của nút `app` (`<placeId>:<g|v|gv>`) → (placeId, danh sách app); `null` khi hỏng. */
    fun appChoice(id: String): Pair<String, List<String>>? {
        val place = id.substringBeforeLast(':', "")
        val apps = APP_CHOICES[id.substringAfterLast(':')] ?: return null
        return place.takeIf { it.isNotEmpty() }?.let { it to apps }
    }

    /** Bật/tắt ghim [name] (giữ thứ tự, trần [MAX_PINS] — đầy thì không thêm). */
    fun togglePin(pinned: List<String>, name: String): List<String> =
        if (name in pinned) pinned - name else if (pinned.size >= MAX_PINS) pinned else pinned + name

    /**
     * Id nút một nơi: `<chỉ số>.<dấu tên>` (dấu = 16 bit thấp của `name.hashCode()`, hex). Review 26/10 P2: sổ địa chỉ sửa
     * sau khi bảng đã gửi thì chỉ số trỏ sang nơi KHÁC ⇒ [placeIndex] đòi dấu khớp, không khớp ⇒ `null` (không dẫn nhầm).
     */
    fun placeId(index: Int, p: SavedPlace): String = "$index." + (p.name.hashCode() and 0xffff).toString(16)

    /** Chỉ số trong [places] cho một id của [placeId]; `null` khi hỏng hoặc nơi ở chỉ số đó đã đổi tên. */
    fun placeIndex(id: String, places: List<SavedPlace>): Int? {
        val i = id.substringBefore('.').toIntOrNull() ?: return null
        val p = places.getOrNull(i) ?: return null
        return i.takeIf { placeId(i, p) == id }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}
