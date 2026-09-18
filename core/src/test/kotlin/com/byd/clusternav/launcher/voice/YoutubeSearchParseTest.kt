package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
