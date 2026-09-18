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
}
