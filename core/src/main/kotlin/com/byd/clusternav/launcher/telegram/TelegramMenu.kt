package com.byd.clusternav.launcher.telegram

import com.byd.clusternav.launcher.SavedPlace

/**
 * JSON `reply_markup` (inline keyboard) của bot. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * 3.1.21 (owner 26/10, chỉ dẫn đường + nhạc): còn MỘT bảng — các nơi đã lưu (bấm = dẫn bằng app mặc định) + ➕ New
 * (bot hỏi điểm đến). Không bảng điều hoà / ghế / trạng thái xe.
 *
 * Bất biến: `callback_data` qua [TelegramCallback.encode] (≤ 64 byte, mang nonce bảng); tên nơi (data người dùng) chỉ ở
 * NHÃN, callback dùng CHỈ SỐ trong sổ.
 */
object TelegramMenu {

    private const val PER_PAGE = 8

    private fun btn(text: String, cb: TelegramCallback): String =
        """{"text":"${esc(text)}","callback_data":"${esc(cb.encode())}"}"""

    private fun row(buttons: List<String>): String = "[" + buttons.joinToString(",") + "]"

    /** Bảng nơi đã lưu: 2 nơi / hàng, [PER_PAGE] nơi / trang, ➕ New luôn có, ⬇️ More khi còn trang sau. */
    fun places(nonce: Int, places: List<SavedPlace>, page: Int = 0): String {
        val start = (page * PER_PAGE).coerceAtLeast(0)
        val slice = places.drop(start).take(PER_PAGE)
        val rows = slice.chunked(2).mapIndexed { r, pair ->
            row(pair.mapIndexed { c, p -> btn(p.name, TelegramCallback(nonce, "nav", "go", placeId(start + r * 2 + c, p))) })
        }.toMutableList()
        val foot = mutableListOf(btn("➕ New destination", TelegramCallback(nonce, "nav", "new")))
        val left = places.size - (start + slice.size)
        if (left > 0) foot += btn("⬇️ More ($left)", TelegramCallback(nonce, "nav", "page", (page + 1).toString()))
        rows += row(foot)
        return """{"inline_keyboard":[${rows.joinToString(",")}]}"""
    }

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
