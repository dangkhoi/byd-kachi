package com.byd.clusternav.launcher.voice

/**
 * ═══ Bóc `videoId` BÀI ĐẦU từ HTML trang tìm kiếm YouTube — thuần, cấm `android.*` ═════════════════════════════
 *
 * ## Vì sao có tệp này (mục tiêu owner 2026-09-18: *"search ra kết quả và mở PLAY bài hát luôn"*)
 * [ĐO nguồn Kiki đã decompile — `jadx-kiki`]: Kiki tự-phát nhạc **KHÔNG** bằng intent `MEDIA_PLAY_FROM_SEARCH`
 * (nó không có action đó ở đâu cả), mà bằng **SERVER**: backend Kiki tải HTML `youtube.com/results?search_query=`,
 * bóc `video_id` bài đầu, trả về JSON có `yt_watch_url` (`C6338g.java:73`), rồi app mở
 * `https://www.youtube.com/watch?v=<id>` — mở **URL watch** thì YouTube **tự phát** đúng video ấy.
 *
 * Kachi KHÔNG có server, nên làm ĐÚNG việc đó nhưng **on-device**: tải HTML, bóc id ở đây (thuần ⇒ test off-car),
 * tầng `:app` ([YoutubeResolver]) lo phần mạng. Hỏng (mạng/không khớp) ⇒ chỗ gọi **lùi** về `MEDIA_PLAY_FROM_SEARCH`
 * (không regression). Đây là scrape ⇒ **mong manh** theo markup YouTube; giữ regex hẹp + có đường lùi.
 */
object YoutubeSearchParse {

    /**
     * `videoId` **đầu tiên** trong [html], hoặc `null` khi không thấy.
     *
     * Trang kết quả nhúng `ytInitialData` với nhiều `"videoId":"<11 ký tự>"`; bài đầu (kết quả top) gần như luôn
     * là khớp đầu tiên — cùng thứ server Kiki chọn. `videoId` YouTube là **đúng 11 ký tự** `[A-Za-z0-9_-]`, nên
     * regex hẹp này không bắt nhầm chuỗi khác. (Có thể trúng một video quảng cáo/shorts hiếm khi nó đứng trước —
     * chấp nhận: sai thì vẫn phát một video liên quan, còn hơn dừng ở tìm kiếm; và đường lùi vẫn còn.)
     */
    fun firstVideoId(html: String): String? =
        VIDEO_ID.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.length == 11 }

    private val VIDEO_ID = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")
}
