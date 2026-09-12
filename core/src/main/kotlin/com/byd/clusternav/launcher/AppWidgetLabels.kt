package com.byd.clusternav.launcher

/**
 * ═══ [SOÁT P3-5] NHÃN WIDGET BÊN THỨ BA: gỡ TRÙNG TÊN ═════════════════════════════════════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm được off-car, dù dữ liệu đến từ `AppWidgetProviderInfo.loadLabel`.
 *
 * ## Bệnh nó chữa — đo được
 * [ĐO] 2026-09-12 `emulator-5554`, mục "Widget của app khác" trong ngăn kéo: hiện **HAI** mục cùng tên **"Cảnh báo"**
 * (VietMap `VMAlertWidgetProvider` và `VMOnlyStickyAlertWidgetProvider`) và **HAI** mục cùng tên **"Google Play
 * Music"**. Người dùng phải **bấm thử** để biết mục nào là mục nào, và bấm thử ở đây không rẻ: mỗi lần chọn là một
 * `allocateAppWidgetId` + một lần ràng buộc + (có thể) một phiên kênh shell.
 *
 * ## Vì sao cơ chế của RW0 KHÔNG tự phủ ca này
 * Dự án đã gặp đúng bệnh này với **18 nhãn trùng** ở gói 2 và chữa bằng gợi ý loại (`· xem` / `· bấm` / `· thẻ` /
 * `· nhóm` — xem [CapabilityPick.displayLabel]). Nhưng cơ chế đó đọc [CapabilityCatalog], tức chỉ biết những mục
 * **của dự án**. Nhãn ở đây do **hệ thống** trả về từ app khác, không có mục nào trong bất kỳ registry ⇒ nó nằm ngoài
 * tầm của phép đó. Cùng bệnh, hai nguồn dữ liệu ⇒ phải có phép riêng, nhưng **giữ đúng khuôn**: chỉ thêm gợi ý **ở
 * chỗ trùng** (thêm cho mọi mục là nhiễu), và dấu ngăn là `·` như mọi chỗ khác.
 *
 * ## Gợi ý là gì: thứ NGẮN NHẤT còn phân biệt được
 * Xét lần lượt tên lớp gọn → tên lớp → gói → chuỗi đầy đủ, lấy **cái đầu tiên còn phân biệt được trong nhóm trùng**.
 * Hai ca thật đo được ra hai gợi ý khác nhau, và đó là chủ ý:
 *  - hai widget VietMap **cùng gói** ⇒ gói không phân biệt được ⇒ dùng tên lớp gọn (`VMAlert` / `VMOnlyStickyAlert`);
 *  - hai "Google Play Music" **khác gói** ⇒ nếu tên lớp trùng nhau thì rơi xuống gói.
 * Chuỗi đầy đủ luôn là đáy an toàn: `ComponentName` là duy nhất theo định nghĩa, nên vòng chọn **luôn kết thúc**.
 */
object AppWidgetLabels {

    /** Hậu tố quy ước của lớp nhà cung cấp — bỏ đi thì tên còn phần MANG NGHĨA (`VMAlertWidgetProvider` → `VMAlert`). */
    private val NOISE = listOf("AppWidgetProvider", "WidgetProvider", "Provider", "AppWidget", "Widget")

    /**
     * Nhãn để HIỂN THỊ cho từng mục, **cùng thứ tự** với [entries].
     *
     * @param entries `(nhãn hệ thống trả về, chuỗi provider phẳng `"pkg/cls"`)`.
     */
    fun titles(entries: List<Pair<String, String>>): List<String> {
        val byLabel = entries.groupBy { it.first }
        return entries.map { (label, provider) ->
            val group = byLabel.getValue(label)
            if (group.size == 1) label else "$label · ${hint(provider, group.map { it.second })}"
        }
    }

    /**
     * Gợi ý phân biệt [provider] khỏi [siblings] (gồm cả chính nó).
     *
     * ⚠ Trùng chuỗi provider trong cùng nhóm là chuyện **không thể** với dữ liệu thật (`ComponentName` duy nhất), nhưng
     * dữ liệu vào đây đến từ ROM lạ nên vẫn có đáy: hết ứng viên thì trả chuỗi đầy đủ chứ không ném.
     */
    private fun hint(provider: String, siblings: List<String>): String {
        val candidates = listOf(::shortClass, ::className, ::packageName, { p: String -> p })
        candidates.forEach { pick ->
            val mine = pick(provider)
            if (mine.isNotBlank() && siblings.count { pick(it) == mine } == 1) return mine
        }
        return provider
    }

    private fun packageName(provider: String): String = provider.substringBefore('/')

    /** Tên lớp không có gói: `"com.x/com.x.a.WProvider"` → `"WProvider"`; `"com.x/.WProvider"` → `"WProvider"`. */
    private fun className(provider: String): String =
        provider.substringAfter('/', "").trimStart('.').substringAfterLast('.')

    /** Tên lớp đã bỏ hậu tố quy ước. Bỏ hết mà rỗng ⇒ trả về tên lớp (đừng biến gợi ý thành chuỗi trắng). */
    private fun shortClass(provider: String): String {
        val c = className(provider)
        val trimmed = NOISE.firstOrNull { c.endsWith(it) && c.length > it.length }?.let { c.removeSuffix(it) } ?: c
        return trimmed.ifBlank { c }
    }
}
