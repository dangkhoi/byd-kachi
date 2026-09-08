package com.byd.clusternav.navigation

/** Một mẩu chữ đọc được trên màn, kèm toạ độ pixel. Đủ để quyết định, không cần biết Android. */
data class ScreenTextItem(val text: String, val top: Int, val left: Int)

/**
 * Kết quả đọc màn dẫn đường.
 *
 * [turnMeters] dùng −1 cho "không đọc được" thay vì 0, cùng lý do như [SpeedReading]: 0 mét là "đã tới
 * chỗ rẽ", một nghĩa hoàn toàn khác.
 */
data class NavScreenReading(
    val turnMeters: Int = UNKNOWN_METERS,
    val road: String = "",
    val bottomInfo: String = "",
) {
    companion object {
        const val UNKNOWN_METERS = -1
    }
}

/**
 * Heuristic đọc cự ly-tới-rẽ và tên đường từ các mẩu chữ trên màn dẫn đường.
 *
 * Trước 2026-07-27 toàn bộ phần này nằm trong `NavAccessibilityService` ở `:app`, tức **0 bài kiểm** cho
 * đúng đoạn quyết định con số mà tài xế thấy trên cụm. Nó chỉ cần toạ độ và chuỗi — không cần một hành vi
 * nào của Android (khuôn Q1b trong layering-rules), nên phần đi cây `AccessibilityNodeInfo` ở lại `:app`
 * còn phần quyết định về đây.
 *
 * Ba ngưỡng trong này là heuristic thật, và cũng là chỗ dễ sai âm thầm nhất: thẻ rẽ giả định nằm ở nửa
 * trên, thanh đích ở đáy, và tên đường là chuỗi dài nhất còn lại. Có bài kiểm rồi thì mỗi lần chỉnh
 * ngưỡng đều thấy hậu quả.
 */
object NavScreenScan {

    /** Thẻ rẽ nằm ở nửa trên màn. */
    const val TOP_BAND_RATIO = 0.55

    /** Thanh đích/ETA nằm sát đáy. */
    const val BOTTOM_BAND_RATIO = 0.78

    /** Chiều cao mặc định khi không đọc được kích thước màn. */
    const val FALLBACK_SCREEN_HEIGHT = 1080

    /** Cự ly nhận được: trên 50 km thì gần như chắc chắn đọc nhầm thứ khác. */
    val acceptedMeters = 0..50_000

    /** Cự ly: "250 m", "1.2 km", "0,4 km". ft/mi để nhận diện thẻ rẽ vùng imperial; parse vẫn theo mét. */
    private val distance = Regex("""\b\d+([.,]\d+)?\s?(km|m|ft|mi)\b""", RegexOption.IGNORE_CASE)

    /** Thời gian: "12 phút", "1 h", "08:30". Dùng để LOẠI, vì thanh đích cũng chứa số. */
    private val time = Regex("""\d+\s?(phút|min|giờ|h\b|hr)|\b\d{1,2}:\d{2}\b""", RegexOption.IGNORE_CASE)

    fun scan(items: List<ScreenTextItem>, screenHeight: Int): NavScreenReading {
        if (items.isEmpty()) return NavScreenReading()
        val height = if (screenHeight > 0) screenHeight else FALLBACK_SCREEN_HEIGHT
        val topBand = (height * TOP_BAND_RATIO).toInt()
        val bottomBand = height * BOTTOM_BAND_RATIO

        // Cự ly tới rẽ: token cự ly ở nửa trên, CAO NHẤT trên màn. Loại token thời gian vì thanh đích
        // cũng có dạng số.
        val turn = items
            .filter { it.top < topBand && distance.containsMatchIn(it.text) && !time.containsMatchIn(it.text) }
            .minByOrNull { it.top }
        val meters = turn?.let { NavParse.parseMeters(it.text) } ?: NavScreenReading.UNKNOWN_METERS

        // Đường/lệnh kế: chuỗi DÀI nhất ở nửa trên, không phải cự ly hay thời gian.
        val road = items
            .filter {
                it.top < topBand && !distance.containsMatchIn(it.text) &&
                    !time.containsMatchIn(it.text) && it.text.length >= 3
            }
            .maxByOrNull { it.text.length }?.text.orEmpty()

        // Thông tin đáy (giờ tới · còn lại · phút): chỉ để soi, chưa đẩy lên cụm.
        val bottomInfo = items
            .filter {
                it.top > bottomBand &&
                    (distance.containsMatchIn(it.text) || time.containsMatchIn(it.text))
            }
            .sortedBy { it.left }
            .joinToString(" · ") { it.text }

        return NavScreenReading(
            turnMeters = if (meters in acceptedMeters) meters else NavScreenReading.UNKNOWN_METERS,
            road = road,
            bottomInfo = bottomInfo,
        )
    }
}
