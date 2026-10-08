package com.byd.clusternav.launcher.trip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.97 · R2d — khoá lỗi [ĐO máy ảo QA 08/10, ca D]: phát tiếp hoãn gửi VIEW lúc Maps che màn nhà ⇒ YouTube chưa phát ⇒ hết chờ ⇒
 * về màn nhà thì phát từ giây 0 + bộ lưu ghi đè điểm phát tiếp. Nay: phiên đích phát ĐÚNG bài ⇒ tua; bài khác (người lái chọn) /
 * quá hạn ⇒ bỏ; chưa phát ⇒ chờ. Thử ĐỎ: cho `decide` trả WAIT khi tiêu đề khớp, hoặc SEEK khi tiêu đề khác.
 */
class YoutubeLateSeekTest {

    private val yt = "com.google.android.youtube"
    private val title = "MƯA CHIỀU - Bản Phối Mới AI Ngọt Ngào Đến NỔI DA GÀ"
    private val p = YoutubeLateSeek.Pending(yt, title, 178_000L, untilElapsedMs = 1_000_000L)

    private fun live(pkg: String = yt, t: String? = title, playing: Boolean = true) =
        YoutubeResume.Live(pkg, t, "NHẠC TRỊNH TÂN THỜI", playing, 1_900L, 0L, 1f, 282_000L)

    @Test
    fun `phien dich phat dung bai thi tua`() {
        assertEquals(YoutubeLateSeek.Action.SEEK, YoutubeLateSeek.decide(p, listOf(live()), 10_000L))
    }

    @Test
    fun `chua co phien hoac chua phat thi cho`() {
        assertEquals(YoutubeLateSeek.Action.WAIT, YoutubeLateSeek.decide(p, null, 10_000L))
        assertEquals(YoutubeLateSeek.Action.WAIT, YoutubeLateSeek.decide(p, emptyList(), 10_000L))
        assertEquals(YoutubeLateSeek.Action.WAIT, YoutubeLateSeek.decide(p, listOf(live(playing = false)), 10_000L))
        assertEquals(YoutubeLateSeek.Action.WAIT, YoutubeLateSeek.decide(p, listOf(live(pkg = "com.spotify.music", t = "Khác")), 10_000L),
            "app KHÁC phát không phải việc của lượt tua này")
    }

    /**
     * Soát Pass 4 [P1]: bài KHÁC lúc đầu có thể là quảng cáo đầu video ⇒ OTHER (khoan dung như đường thường 45 s), chỉ CANCEL khi bài
     * khác kéo dài quá [YoutubeLateSeek.OTHER_GRACE_MS]. Thử ĐỎ: trả CANCEL ngay khi thấy bài khác.
     */
    @Test
    fun `bai khac - khoan dung qua quang cao roi moi bo`() {
        val other = listOf(live(t = "Despacito"))
        assertEquals(YoutubeLateSeek.Action.OTHER, YoutubeLateSeek.decide(p, other, 10_000L), "lần đầu thấy bài khác")
        val seen = p.copy(otherSinceMs = 10_000L)
        assertEquals(YoutubeLateSeek.Action.OTHER, YoutubeLateSeek.decide(seen, other, 10_000L + YoutubeLateSeek.OTHER_GRACE_MS - 1))
        assertEquals(YoutubeLateSeek.Action.CANCEL, YoutubeLateSeek.decide(seen, other, 10_000L + YoutubeLateSeek.OTHER_GRACE_MS))
        assertEquals(YoutubeLateSeek.Action.SEEK, YoutubeLateSeek.decide(seen, listOf(live()), 10_000L + YoutubeLateSeek.OTHER_GRACE_MS),
            "quảng cáo xong, đúng bài lên ⇒ vẫn tua")
        assertEquals(YoutubeLateSeek.Action.WAIT, YoutubeLateSeek.decide(seen, listOf(live(playing = false)), 20_000L), "dừng giữa hạn ⇒ chờ")
    }

    @Test
    fun `qua han thi bo, ke ca dang phat dung bai`() {
        assertEquals(YoutubeLateSeek.Action.CANCEL, YoutubeLateSeek.decide(p, listOf(live()), 1_000_001L))
        assertEquals(YoutubeLateSeek.Action.CANCEL, YoutubeLateSeek.decide(p, null, 1_000_001L))
    }

    @Test
    fun `chi hen khi co vi tri va tieu de`() {
        assertTrue(YoutubeLateSeek.worth(title, 178_000L))
        assertFalse(YoutubeLateSeek.worth(title, 0L))
        assertFalse(YoutubeLateSeek.worth(" ", 178_000L))
        assertFalse(YoutubeLateSeek.worth(null, 178_000L))
    }
}
