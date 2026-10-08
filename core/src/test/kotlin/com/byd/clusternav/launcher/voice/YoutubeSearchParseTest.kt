package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bóc `video_id` bài đầu từ HTML tìm kiếm YouTube ([YoutubeSearchParse]) — thuần, off-car.
 *
 * Mục tiêu owner 2026-09-18: *"search ra kết quả và mở PLAY luôn"* → giải id → mở `watch?v=<id>` (tự phát, cơ chế Kiki).
 */
class YoutubeSearchParseTest {

    @Test fun `lay videoId dau tien trong ytInitialData`() {
        val html = """...{"itemSectionRenderer":{"contents":[{"videoRenderer":""" +
            """{"videoId":"dQw4w9WgXcQ","thumbnail":{...}},{"videoRenderer":{"videoId":"9bZkp7q19f0"..."""
        assertEquals("dQw4w9WgXcQ", YoutubeSearchParse.firstVideoId(html), "phải là id BÀI ĐẦU (kết quả top)")
    }

    @Test fun `khong co videoId thi tra null`() {
        assertNull(YoutubeSearchParse.firstVideoId("<html>không có kết quả nào</html>"))
        assertNull(YoutubeSearchParse.firstVideoId(""))
    }

    @Test fun `chi nhan dung 11 ky tu`() {
        // 10 ký tự (thiếu) ⇒ regex {11} không khớp; chuỗi khác không phải videoId cũng không lọt.
        assertNull(YoutubeSearchParse.firstVideoId("""{"videoId":"tooShort10"}"""))
        assertEquals("abcDEF12_-x", YoutubeSearchParse.firstVideoId("""{"videoId":"abcDEF12_-x"}"""))
    }

    // ══ [ĐO 2026-09-18] Quét theo DÒNG CHẢY — id nằm ở ~765 K ký tự, không nằm ở đầu trang ═══════════════

    private fun page(idAtChar: Int, id: String = "dQw4w9WgXcQ"): String =
        "x".repeat(idAtChar) + """{"videoId":"$id"}""" + "y".repeat(5_000)

    /**
     * Ca THẬT của trang YouTube: khớp đầu ở ~765 K ký tự, tức **ngoài** trần 600 K mà bản 1.75 đặt. Bài này là
     * phép chứng minh rằng bản vá UA một mình không đủ — bảng số đo ở KDoc `YoutubeSearchParse.firstVideoId`.
     */
    @Test fun `bat duoc id nam sau 765 nghin ky tu nhu trang that`() {
        val html = page(765_000)
        assertEquals("dQw4w9WgXcQ", YoutubeSearchParse.firstVideoId(html.reader(), maxChars = 1_800_000))
        // Và trần cũ 600 K thì KHÔNG bắt được — giữ ca này để không ai hạ trần về đó nữa mà tưởng vô hại.
        assertNull(
            YoutubeSearchParse.firstVideoId(html.reader(), maxChars = 600_000),
            "trần 600 K cắt trước khi tới id ⇒ lùi về search-play, đúng lỗi [ĐO] trên xe",
        )
    }

    /**
     * ⚠ Bẫy im lặng của phép quét theo khối: một khớp dài 23 ký tự có thể nằm **vắt qua** ranh giới hai khối.
     * Quét từng khối rời mà không giữ đuôi thì mất đúng khớp đó, và chỉ mất ở một vài cỡ khối nhất định.
     * Đặt id vào **mọi** vị trí quanh ranh giới để không còn cỡ nào lọt.
     */
    @Test fun `khop vat qua ranh gioi hai khoi doc van bat duoc`() {
        val chunk = 64
        for (off in (chunk - 30)..(chunk + 5)) {
            assertEquals(
                "dQw4w9WgXcQ",
                YoutubeSearchParse.firstVideoId(page(off).reader(), maxChars = 100_000, chunkChars = chunk),
                "id đặt ở ký tự $off (ranh giới khối $chunk) phải vẫn bắt được",
            )
        }
    }

    /** Vẫn là khớp ĐẦU theo thứ tự tài liệu, kể cả khi hai khớp nằm ở hai khối khác nhau. */
    @Test fun `van tra khop dau tien theo thu tu tai lieu`() {
        val html = "a".repeat(200) + """{"videoId":"AAAAAAAAAAA"}""" +
            "b".repeat(500) + """{"videoId":"BBBBBBBBBBB"}"""
        assertEquals(
            "AAAAAAAAAAA",
            YoutubeSearchParse.firstVideoId(html.reader(), maxChars = 100_000, chunkChars = 64),
        )
    }

    @Test fun `dong chay khong co id hoac tran vo nghia thi tra null, khong nem`() {
        assertNull(YoutubeSearchParse.firstVideoId("không có gì".reader(), maxChars = 100_000))
        assertNull(YoutubeSearchParse.firstVideoId("".reader(), maxChars = 100_000))
        assertNull(YoutubeSearchParse.firstVideoId(page(10).reader(), maxChars = 0))
        assertNull(YoutubeSearchParse.firstVideoId(page(10).reader(), maxChars = 100, chunkChars = 0))
    }

    /** Đuôi giữ lại phải LỚN HƠN một khớp (`"videoId":"` + 11 + `"` = 23) — nếu không, bài trên chỉ đỏ ngẫu nhiên. */
    @Test fun `dau giu lai du dai cho mot khop`() {
        assertTrue(
            YoutubeSearchParse.OVERLAP_CHARS > """{"videoId":"dQw4w9WgXcQ"}""".length,
            "đuôi ${YoutubeSearchParse.OVERLAP_CHARS} ký tự phải dài hơn một khớp",
        )
    }

    // ══ 2.97 · R2 — N KẾT QUẢ ĐẦU (id + tiêu đề + kênh) cho phát tiếp YouTube ═════════════════════════════════════════

    private fun renderer(id: String, title: String, channel: String? = null) =
        """{"videoRenderer":{"videoId":"$id","thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/vi/$id/hq.jpg"}]},""" +
            """"title":{"runs":[{"text":"$title"}],"accessibility":{}},"lengthText":{"simpleText":"5:24"}""" +
            (channel?.let { ""","ownerText":{"runs":[{"text":"$it"}]}""" } ?: "") + "}}"

    /**
     * Trang THẬT (cắt gọn: 6 khối kết quả + neo khối 7, mã theo dõi / link xem trước đã xoá) của truy vấn phát tiếp
     * *"<tiêu đề> <kênh>"* cho đúng ca owner báo 08/10 — `curl` từ máy chủ, UA máy tính như `VoiceYoutubeResolver`.
     */
    private val real by lazy {
        requireNotNull(javaClass.getResourceAsStream("/voice/yt-search-mua-chieu-tan-thoi-2026-10-08.txt")) { "thiếu fixture" }
            .bufferedReader(Charsets.UTF_8).readText()
    }

    @Test fun `topVideos - trang that ca owner 08-10, dung thu tu, tieu de + kenh`() {
        val hits = YoutubeSearchParse.topVideos(real.reader(), maxChars = 1_800_000)
        assertEquals(5, hits.size)
        assertEquals(
            YoutubeSearchParse.Hit("_RPAFLQWaME", "MƯA CHIỀU - Bản Phối Mới AI Ngọt Ngào Đến NỔI DA GÀ", "NHẠC TRỊNH TÂN THỜI"),
            hits[0],
        )
        assertEquals(listOf("_RPAFLQWaME", "DnC4QFdqkmQ", "j-Lcs0ipyp0", "Day4r4euNAc", "qyiQ-pqI5GU"), hits.map { it.id })
        assertEquals(listOf("NHẠC TRỊNH TÂN THỜI", "MP Melody", "QUÁN NỬA ĐÊM", "Bolero Tân Thời", "NHẠC TRỊNH TÂN THỜI"), hits.map { it.channel })
    }

    /** Khối đầu ở ≈ 770 K ký tự trên trang thật — phải tới được trong trần của resolver, và cửa sổ trước neo không phình. */
    @Test fun `topVideos - neo nam sau 770 nghin ky tu nhu trang that`() {
        val html = "x".repeat(770_000) + real
        assertEquals("_RPAFLQWaME", YoutubeSearchParse.topVideos(html.reader(), maxChars = 1_800_000).firstOrNull()?.id)
        assertTrue(YoutubeSearchParse.topVideos(html.reader(), maxChars = 600_000).isEmpty(), "trần nhỏ hơn vị trí neo ⇒ rỗng")
    }

    /** `videoId` ngoài khối kết quả (quảng cáo / kệ / điều hướng) đứng trước không bao giờ thành "kết quả 1". */
    @Test fun `topVideos - videoId ngoai khoi ket qua khong duoc tinh`() {
        val html = """{"searchPyvRenderer":{"ads":[{"videoId":"ADADADADADA","title":{"runs":[{"text":"Quảng cáo"}]}}]}},""" +
            renderer("dQw4w9WgXcQ", "Bài A", "Kênh A") + renderer("9bZkp7q19f0", "Bài B")
        assertEquals(
            listOf(YoutubeSearchParse.Hit("dQw4w9WgXcQ", "Bài A", "Kênh A"), YoutubeSearchParse.Hit("9bZkp7q19f0", "Bài B", null)),
            YoutubeSearchParse.topVideos(html.reader(), maxChars = 100_000),
        )
    }

    @Test fun `topVideos - giai thoat JSON, simpleText, gioi han so ket qua`() {
        val html = renderer("dQw4w9WgXcQ", "Lạc Trôi | Official \\u0026 \\\"MV\\\"", "S\\u01a1n T\\u00f9ng") +
            """{"videoRenderer":{"videoId":"9bZkp7q19f0","title":{"simpleText":"Đêm đông"}}}""" +
            renderer("CCCCCCCCCCC", "C")
        val hits = YoutubeSearchParse.topVideos(html.reader(), maxChars = 100_000, limit = 2)
        assertEquals(listOf(YoutubeSearchParse.Hit("dQw4w9WgXcQ", "Lạc Trôi | Official & \"MV\"", "Sơn Tùng"),
            YoutubeSearchParse.Hit("9bZkp7q19f0", "Đêm đông", null)), hits)
    }

    @Test fun `topVideos - khong neo, tran vo nghia thi rong, khong nem`() {
        assertTrue(YoutubeSearchParse.topVideos("""{"videoId":"dQw4w9WgXcQ"}""".reader(), maxChars = 1_000).isEmpty(), "markup đổi ⇒ rỗng")
        assertTrue(YoutubeSearchParse.topVideos("".reader(), maxChars = 1_000).isEmpty())
        assertTrue(YoutubeSearchParse.topVideos(real.reader(), maxChars = 0).isEmpty())
        assertTrue(YoutubeSearchParse.topVideos(real.reader(), maxChars = 1_000_000, limit = 0).isEmpty())
    }

    /** Trần giữ từ neo: khối vượt trần bị cắt ⇒ ít kết quả hơn, không phình RAM. */
    @Test fun `topVideos - tran giu tu neo`() {
        val html = renderer("dQw4w9WgXcQ", "A") + "z".repeat(50_000) + renderer("9bZkp7q19f0", "B")
        assertEquals(listOf("dQw4w9WgXcQ"), YoutubeSearchParse.topVideos(html.reader(), maxChars = 1_000_000, spanChars = 10_000).map { it.id })
        assertEquals(2, YoutubeSearchParse.topVideos(html.reader(), maxChars = 1_000_000, spanChars = 100_000).size)
    }

    /** Bẫy ranh giới khối: neo + tiêu đề vắt qua mọi ranh giới vẫn ra đúng. */
    @Test fun `topVideos - neo vat qua ranh gioi khoi doc`() {
        val chunk = 64
        assertTrue(YoutubeSearchParse.OVERLAP_CHARS > "\"videoRenderer\":{\"videoId\":\"dQw4w9WgXcQ\"".length)
        for (off in 0..(chunk * 3)) {
            val html = "x".repeat(off) + renderer("dQw4w9WgXcQ", "Bài | một", "Kênh")
            assertEquals(
                listOf(YoutubeSearchParse.Hit("dQw4w9WgXcQ", "Bài | một", "Kênh")),
                YoutubeSearchParse.topVideos(html.reader(), maxChars = 100_000, chunkChars = chunk),
                "lệch $off",
            )
        }
    }

    @Test fun `unescape - thoat hong thi null`() {
        assertEquals("a\"b/\\c", YoutubeSearchParse.unescape("a\\\"b\\/\\\\c"))
        assertNull(YoutubeSearchParse.unescape("abc\\"))
        assertNull(YoutubeSearchParse.unescape("\\u12"))
        assertNull(YoutubeSearchParse.unescape("\\x"))
    }
}
