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

    /** Một kết quả: [id] + [title] + [channel] (`null` = không thấy trong khối của CHÍNH kết quả đó). */
    data class Hit(val id: String, val title: String?, val channel: String? = null)

    /**
     * Một lượt tìm cho phát tiếp (2.97 · R2) — tách **mạng** (thử lại được) khỏi trang không có gì / lỗi khác.
     * [ĐO máy ảo 08/10, `p297/yt/h1-nonet`] mất mạng lúc lên xe ⇒ `UnknownHostException` sau 19 ms ⇒ 2.96 bỏ luôn (`resume:search-fail`)
     * ⇒ YouTube mở trang chủ, không phát gì — đúng triệu chứng owner báo 08/10.
     */
    sealed interface Search {
        /** Trang về được; [hits] có thể rỗng (không kết quả / markup đổi). */
        data class Found(val hits: List<Hit>) : Search
        /** Không tới được YouTube (không mạng, DNS, quá hạn) — thử lại được. */
        object Offline : Search
        /** Tới được nhưng hỏng (HTTP ≠ 200, lỗi khác) — không thử lại. */
        object Failed : Search
    }

    /**
     * ═══ 2.97 · R2 — [limit] KẾT QUẢ ĐẦU (id + tiêu đề + kênh) từ một DÒNG CHẢY HTML ══════════════════════════════════════
     *
     * Thay `firstVideo` của 2.94 (chỉ xem `"videoId"` THÔ đầu tiên của trang): bên phát tiếp chọn trong cả [limit] kết quả bài có
     * tiêu đề khớp (`YoutubeResume.choose`) — không còn phụ thuộc bài của mình phải đứng HẠNG 1 (video mới đăng, thứ hạng đổi), và
     * không bao giờ coi một `videoId` ngoài kết quả (quảng cáo / kệ) là kết quả.
     *
     * Neo vào `"videoRenderer":{"videoId":"…"` — [ĐO trang thật 08/10, 5 truy vấn] mỗi kết quả là một khối ≈ 14 K ký tự mở bằng
     * đúng chuỗi đó; tiêu đề ở ≈ +420, kênh (`ownerText`) ở ≈ +3,3 K; khối đầu ở ≈ 770 K ký tự. Ngoài neo: không đọc.
     * RAM: trước neo đầu chỉ giữ cửa sổ [OVERLAP_CHARS]; từ neo đầu giữ tối đa [spanChars] ký tự.
     * Markup đổi (không còn neo) ⇒ danh sách rỗng ⇒ bên gọi không phát gì (an toàn, như tiêu đề không khớp).
     */
    fun topVideos(
        reader: Reader,
        maxChars: Int,
        limit: Int = TOP_LIMIT,
        chunkChars: Int = CHUNK_CHARS,
        spanChars: Int = TOP_SPAN_CHARS,
    ): List<Hit> {
        if (maxChars <= 0 || chunkChars <= 0 || limit <= 0 || spanChars <= 0) return emptyList()
        val buf = CharArray(chunkChars)
        val window = StringBuilder(chunkChars + OVERLAP_CHARS)
        var anchored = false
        var read = 0
        while (read < maxChars) {
            val n = reader.read(buf, 0, minOf(chunkChars, maxChars - read))
            if (n < 0) break
            read += n
            window.append(buf, 0, n)
            if (!anchored) {
                val m = RENDERER.find(window)
                if (m == null) {
                    if (window.length > OVERLAP_CHARS) window.delete(0, window.length - OVERLAP_CHARS)
                    continue
                }
                window.delete(0, m.range.first)
                anchored = true
            }
            // Đủ [limit] khối TRỌN (có neo của khối kế) hoặc chạm trần ⇒ thôi đọc.
            if (window.length >= spanChars || RENDERER.findAll(window).take(limit + 1).count() > limit) break
        }
        if (!anchored) return emptyList()
        val text: CharSequence = if (window.length > spanChars) window.subSequence(0, spanChars) else window
        val marks = RENDERER.findAll(text).take(limit + 1).toList()
        return marks.take(limit).mapIndexed { i, m ->
            val seg = text.subSequence(m.range.first, marks.getOrNull(i + 1)?.range?.first ?: text.length)
            Hit(m.groupValues[1], field(TITLE, seg), field(OWNER, seg))
        }
    }

    private fun field(re: Regex, seg: CharSequence): String? =
        re.find(seg)?.groupValues?.get(1)?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() }

    /** Giải thoát chuỗi JSON; chuỗi hỏng (`\u` cụt / thoát lạ) ⇒ `null`. */
    internal fun unescape(s: String): String? {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i++]
            if (c != '\\') { out.append(c); continue }
            if (i >= s.length) return null
            when (val e = s[i++]) {
                '"', '\\', '/' -> out.append(e)
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'u' -> {
                    if (i + 4 > s.length) return null
                    out.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: return null)
                    i += 4
                }
                else -> return null
            }
        }
        return out.toString()
    }

    private fun scan(html: CharSequence): String? =
        VIDEO_ID.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.length == 11 }

    /** Số kết quả đầu bên phát tiếp xét (2.97 · R2). [ĐOÁN] 5: bài của mình đứng hạng 1 ở 5/5 lượt đo 08/10; 5 cho biên khi thứ hạng xê dịch. */
    const val TOP_LIMIT = 5

    /** Trần ký tự giữ lại từ neo đầu: 5 khối × ≈ 14 K ([ĐO] 13,8–13,9 K) + neo khối thứ sáu + biên. ≈ 240 KB RAM. */
    const val TOP_SPAN_CHARS = 120_000

    /** Một lượt đọc. 16 K ký tự — cùng cỡ đệm mà tầng `:app` vẫn dùng. */
    const val CHUNK_CHARS = 16_384

    /** Đuôi giữ lại giữa hai khối: phải **lớn hơn** một khớp (`videoId` 23 ký tự · neo [RENDERER] 40 ký tự). 64 cho dư mà vẫn không đáng kể. */
    const val OVERLAP_CHARS = 64

    private val VIDEO_ID = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")

    /** Neo một KẾT QUẢ tìm kiếm (không bắt `videoId` của quảng cáo / kệ / điều hướng). */
    private val RENDERER = Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"")

    /** Kênh của kết quả: `"ownerText":{"runs":[{"text":"…"`. */
    private val OWNER = Regex("\"ownerText\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.){0,200})\"")

    /** Tiêu đề renderer: `"title":{"runs":[{"text":"…"` hoặc `"title":{"simpleText":"…"`; thân chuỗi JSON có trần 500 ký tự. */
    private val TITLE = Regex("\"title\":\\{(?:\"runs\":\\[\\{\"text\"|\"simpleText\"):\"((?:[^\"\\\\]|\\\\.){0,500})\"")
}
