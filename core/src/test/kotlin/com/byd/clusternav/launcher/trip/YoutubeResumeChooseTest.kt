package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.YoutubeSearchParse
import com.byd.clusternav.launcher.voice.YoutubeSearchParse.Hit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.97 · R2 — phát tiếp YouTube chọn bài trong N kết quả đầu + thử lại khi mất mạng (spec `kachi-297-plan.html` R2).
 *
 * Ca owner 08/10 (2.96): đang nghe *"MƯA CHIỀU - Bản Phối Mới AI Ngọt Ngào Đến NỔI DA GÀ"* (kênh NHẠC TRỊNH TÂN THỜI) ở 2:58, tắt
 * xe mở lại ⇒ YouTube trang chủ, không phát gì. [ĐO máy ảo 08/10] mạng tốt ⇒ phát tiếp đúng bài (`resume:found`); mất mạng lúc lên
 * xe ⇒ `resume:search-fail:open-only` = đúng triệu chứng. Bộ test này khoá cả hai nhánh sửa: chọn theo tiêu đề trong N kết quả
 * (không phụ thuộc hạng 1) và nhịp thử lại.
 */
class YoutubeResumeChooseTest {

    /** Đúng chuỗi phiên YouTube 21.35 báo + Kachi đã lưu trên máy ảo 08/10 (`p297/yt`, `kachi_yt_resume`). */
    private val title = "MƯA CHIỀU - Bản Phối Mới AI Ngọt Ngào Đến NỔI DA GÀ"
    private val channel = "NHẠC TRỊNH TÂN THỜI"
    private val saved = YoutubeResume.Saved(VoiceAppTargets.YOUTUBE, title, channel, 165_369, 324_000, 1_000_000L)
    private val plan = YoutubeResume.resumePlan(saved, VoiceAppTargets.YOUTUBE, 1_000_000L)!!

    private val real by lazy {
        requireNotNull(javaClass.getResourceAsStream("/voice/yt-search-mua-chieu-tan-thoi-2026-10-08.txt")) { "thiếu fixture" }
            .bufferedReader(Charsets.UTF_8).readText()
    }

    @Test fun `plan mang kenh da luu, truy van = tieu de + kenh`() {
        assertEquals(channel, plan.channel)
        assertEquals("$title $channel", plan.query)
        assertEquals(165_369, plan.seekMs)
    }

    @Test fun `trang that ca owner - chon dung video cua kenh`() {
        val hits = YoutubeSearchParse.topVideos(real.reader(), maxChars = 1_800_000)
        assertEquals("_RPAFLQWaME", YoutubeResume.choose(hits, plan)?.id)
    }

    @Test fun `bai cua minh khong o hang 1 - van chon dung, khong phat bai hang 1`() {
        val hits = listOf(
            Hit("AAAAAAAAAAA", "Mưa Chiều - St: Anh Bằng | ROCK BALLAD", "MP Melody"),
            Hit("BBBBBBBBBBB", "MƯA CHIỀU - Bản Phối Mới Rất Đặc Biệt", "QUÁN NỬA ĐÊM"),
            Hit("_RPAFLQWaME", title, channel),
        )
        assertEquals("_RPAFLQWaME", YoutubeResume.choose(hits, plan)?.id)
    }

    @Test fun `cung tieu de nhieu kenh - uu tien kenh da luu, khong co thi hang cao nhat`() {
        val reup = Hit("RRRRRRRRRRR", title, "Kênh đăng lại")
        val mine = Hit("_RPAFLQWaME", title, channel)
        assertEquals("_RPAFLQWaME", YoutubeResume.choose(listOf(reup, mine), plan)?.id)
        assertEquals("RRRRRRRRRRR", YoutubeResume.choose(listOf(reup), plan)?.id, "luật 2.94: tiêu đề khớp là đủ")
        assertEquals("RRRRRRRRRRR", YoutubeResume.choose(listOf(reup, mine), plan.copy(channel = ""))?.id, "không kênh ⇒ hạng cao nhất")
        assertEquals("_RPAFLQWaME", YoutubeResume.choose(listOf(Hit("_RPAFLQWaME", title, null)), plan)?.id, "kênh không đọc được vẫn nhận")
    }

    @Test fun `khong bai nao khop tieu de - khong phat gi`() {
        val hits = listOf(Hit("AAAAAAAAAAA", "MƯA CHIỀU - Bản Phối Mới AI Nghe Xong Không Thể Ngừng Khóc", channel), Hit("BBBBBBBBBBB", null, channel))
        assertNull(YoutubeResume.choose(hits, plan), "cùng kênh nhưng khác tiêu đề ⇒ không phát")
        assertNull(YoutubeResume.choose(emptyList(), plan))
    }

    @Test fun `so khop tieu de bo qua hoa thuong va NFD`() {
        val nfd = java.text.Normalizer.normalize(title.lowercase(), java.text.Normalizer.Form.NFD)
        assertEquals("X0000000000", YoutubeResume.choose(listOf(Hit("X0000000000", nfd, channel)), plan)?.id)
    }

    @Test fun `nhip thu lai - luy thua 2 tu 2 s, tran 10 s, jitter nua tren`() {
        assertEquals(1_000L, YoutubeResume.searchRetryDelayMs(0, 0.0))
        assertEquals(2_000L, YoutubeResume.searchRetryDelayMs(0, 1.0))
        assertEquals(4_000L, YoutubeResume.searchRetryDelayMs(1, 1.0))
        assertEquals(8_000L, YoutubeResume.searchRetryDelayMs(2, 1.0))
        assertEquals(10_000L, YoutubeResume.searchRetryDelayMs(3, 1.0), "trần")
        assertEquals(10_000L, YoutubeResume.searchRetryDelayMs(99, 1.0), "attempt lớn không tràn")
        assertEquals(1_000L, YoutubeResume.searchRetryDelayMs(-5, Double.NaN), "đầu vào hỏng ⇒ nhịp nhỏ nhất, không ném")
        for (a in 0..6) for (j in listOf(0.0, 0.3, 0.99)) {
            val d = YoutubeResume.searchRetryDelayMs(a, j)
            assertTrue(d in 1_000L..10_000L, "a=$a j=$j d=$d")
        }
        // Tổng nhịp tối thiểu trong ngân sách phải cho ≥ 5 lượt thử lại (mạng xe lên trong ~1 phút vẫn bắt được).
        var t = 0L; var n = 0
        while (true) { t += YoutubeResume.searchRetryDelayMs(n, 1.0); if (t >= YoutubeResume.SEARCH_RETRY_BUDGET_MS) break; n++ }
        assertTrue(n >= 5, "chỉ $n lượt trong ngân sách")
    }
}
