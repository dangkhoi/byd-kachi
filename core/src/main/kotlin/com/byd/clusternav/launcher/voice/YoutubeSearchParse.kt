package com.byd.clusternav.launcher.voice

import java.io.Reader

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
    fun firstVideoId(html: String): String? = scan(html)

    /**
     * ═══ Bóc id BÀI ĐẦU từ một **DÒNG CHẢY** HTML — id nằm ở giữa trang, không nằm ở đầu ═════════════════
     *
     * @param reader dòng chảy HTML (chỗ gọi sở hữu + đóng nó).
     * @param maxChars trần **số ký tự ĐỌC** — chặn thời gian/băng thông, không phải chặn bộ nhớ.
     * @param chunkChars cỡ một lượt đọc.
     *
     * ## ⚠ [ĐO 2026-09-18] Vì sao không còn nạp cả trang rồi mới bóc
     * KDoc bản đầu của [firstVideoId] đoán *"id bài đầu nằm sớm trong `ytInitialData` — không cần đọc cả trang"*,
     * và tầng `:app` dựng giả định đó thành một trần **600 000 ký tự**. Đo trang THẬT (UA máy tính, 4 truy vấn
     * khác nhau) thì giả định ấy **sai**:
     *
     * | truy vấn | cỡ trang (ký tự) | `"videoId"` đầu ở ký tự |
     * |---|---|---|
     * | `diem xua` | 1 288 025 | **763 319** |
     * | `son tung mtp` | 1 525 895 | **787 261** |
     * | `hay trao cho anh` | 1 283 353 | **769 370** |
     * | `noi nay co anh` | 1 296 627 | **765 226** |
     *
     * ⇒ 4/4 nằm **ngoài** trần 600 K ⇒ resolver trả `null` **kể cả khi UA đã đúng**, tức bản vá UA một mình vẫn
     * không làm *"phát luôn"* chạy. Trần phải vượt ~790 K, mà một `StringBuilder` 1,3 M ký tự là ~2,6 MB (chưa kể
     * lượt nhân đôi khi nó giãn) — **đúng loại áp lực RAM** đang là gốc của lỗi Piper `SEGV_MAPERR` (doc
     * `oncar-piper-crash-binding-2026-09-18.md`). Nên: quét **theo dòng chảy**, dừng ở khớp ĐẦU TIÊN, và chỉ giữ
     * một cửa sổ nhỏ trong RAM.
     *
     * ## Cửa sổ giữ lại [OVERLAP_CHARS] ký tự — vì sao phải có
     * Một khớp dài 23 ký tự (`"videoId":"` + 11 + `"`) có thể nằm **vắt qua** ranh giới hai khối đọc. Bỏ phần đuôi
     * đi là mất đúng khớp đó, im lặng, và lỗi ấy chỉ hiện ra ở một cỡ khối nhất định — loại lỗi không ai tìm lại
     * được. Giữ 64 ký tự (> 23) ở đầu cửa sổ kế tiếp thì mọi khớp đều nguyên vẹn.
     *
     * Thứ tự trả về **không đổi**: vẫn là khớp đầu tiên theo thứ tự tài liệu (xem KDoc [firstVideoId]).
     */
    fun firstVideoId(reader: Reader, maxChars: Int, chunkChars: Int = CHUNK_CHARS): String? {
        if (maxChars <= 0 || chunkChars <= 0) return null
        val buf = CharArray(chunkChars)
        val window = StringBuilder(chunkChars + OVERLAP_CHARS)
        var read = 0
        while (read < maxChars) {
            val n = reader.read(buf, 0, minOf(chunkChars, maxChars - read))
            if (n < 0) break
            read += n
            window.append(buf, 0, n)
            // `find` nhận CharSequence ⇒ quét THẲNG trên cửa sổ, không `toString()` một bản sao mỗi khối.
            scan(window)?.let { return it }
            if (window.length > OVERLAP_CHARS) window.delete(0, window.length - OVERLAP_CHARS)
        }
        return null
    }

    private fun scan(html: CharSequence): String? =
        VIDEO_ID.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.length == 11 }

    /** Một lượt đọc. 16 K ký tự — cùng cỡ đệm mà tầng `:app` vẫn dùng. */
    const val CHUNK_CHARS = 16_384

    /** Đuôi giữ lại giữa hai khối: phải **lớn hơn** một khớp (23 ký tự). 64 cho dư mà vẫn không đáng kể. */
    const val OVERLAP_CHARS = 64

    private val VIDEO_ID = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")
}
