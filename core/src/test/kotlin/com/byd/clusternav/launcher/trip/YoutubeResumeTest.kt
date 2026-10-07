package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.voice.VoiceAppTargets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.94 · R3 — YouTube phát tiếp ([YoutubeResume], spec `kachi-294-plan.html` §4.3). Khoá: chỉ lưu khi ĐANG PHÁT + có tiêu đề;
 * vị trí ngoại suy theo `PlaybackState` (kẹp, đồng hồ lùi); bài cũ / gần hết ⇒ không phát tiếp; so tiêu đề (NFC, hoa/thường,
 * khoảng trắng); mã hoá không mang dấu ngăn lên đĩa; đích lấy từ DỮ LIỆU kiểu nhạc, không tên gói.
 */
class YoutubeResumeTest {

    private val yt = VoiceAppTargets.byKey(VoiceAppTargets.YOUTUBE)!!
    private val ytPkg = yt.packages.first()
    private val wall = 1_800_000_000_000L
    private val elapsed = 500_000L

    private fun live(
        title: String? = "Lạc Trôi | Official MV",
        playing: Boolean = true,
        pos: Long = 60_000,
        updated: Long = elapsed - 10_000,
        speed: Float = 1f,
        dur: Long = 240_000,
        pkg: String = ytPkg,
    ) = YoutubeResume.Live(pkg, title, "Sơn Tùng M-TP", playing, pos, updated, speed, dur)

    private fun saved(pos: Long = 98_871, dur: Long = 240_000, at: Long = wall, title: String = "Lạc Trôi | Official MV") =
        YoutubeResume.Saved(VoiceAppTargets.YOUTUBE, title, "Sơn Tùng M-TP", pos, dur, at)

    /** Soát 2.94 R3 · P2: quảng cáo (thời lượng ngắn) không bao giờ thành "bài cuối"; live (0) và bài ≥ 60 s vẫn lưu. */
    @Test
    fun `thoi luong ngan nhu quang cao thi khong luu`() {
        assertEquals(null, YoutubeResume.sample(yt, live(dur = 15_000, pos = 3_000), wall, elapsed), "quảng cáo 15 s")
        assertEquals(null, YoutubeResume.sample(yt, live(dur = 59_999, pos = 3_000), wall, elapsed))
        assertTrue(YoutubeResume.sample(yt, live(dur = 60_000, pos = 3_000), wall, elapsed) != null, "đúng 60 s ⇒ lưu")
        assertTrue(YoutubeResume.sample(yt, live(dur = 0, pos = 3_000), wall, elapsed) != null, "live ⇒ lưu")
    }

    // ── Đích theo dữ liệu ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `dich phat tiep lay tu kieu nhac khong tu phat tiep - hom nay dung YouTube`() {
        assertEquals(listOf(VoiceAppTargets.YOUTUBE), YoutubeResume.watchedTargets().map { it.key })
        assertTrue(YoutubeResume.wanted(TripMusic(TripMusicMode.YOUTUBE)))
        assertTrue(YoutubeResume.wanted(TripMusic(TripMusicMode.YOUTUBE, "  \t ")), "chỉ khoảng trắng = trống")
        assertFalse(YoutubeResume.wanted(TripMusic(TripMusicMode.YOUTUBE, "son tung")), "Phát gì có chữ ⇒ như cũ")
        assertFalse(YoutubeResume.wanted(TripMusic(TripMusicMode.YOUTUBE, "https://youtu.be/dQw4w9WgXcQ")))
        assertFalse(YoutubeResume.wanted(TripMusic(TripMusicMode.YT_MUSIC)), "YT Music tự phát tiếp")
        assertFalse(YoutubeResume.wanted(TripMusic.OFF) || YoutubeResume.wanted(TripMusic(TripMusicMode.CAR)))
    }

    @Test
    fun `pick - chi phien cua app dich, uu tien phien dang phat`() {
        val other = live(pkg = "com.spotify.music")
        assertNull(YoutubeResume.pick(listOf(other), YoutubeResume.watchedTargets()))
        val paused = live(playing = false, title = "A")
        val playing = live(title = "B")
        val (t, l) = YoutubeResume.pick(listOf(other, paused, playing), YoutubeResume.watchedTargets())!!
        assertEquals(VoiceAppTargets.YOUTUBE, t.key)
        assertEquals("B", l.title)
    }

    // ── Lấy mẫu ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `sample - dang phat co tieu de thi luu, vi tri ngoai suy theo PlaybackState`() {
        val s = YoutubeResume.sample(yt, live(), wall, elapsed)!!
        assertEquals(70_000, s.positionMs, "60 s + 10 s × 1,0")
        assertEquals(VoiceAppTargets.YOUTUBE, s.target)
        assertEquals("Lạc Trôi | Official MV", s.title)
        assertEquals(wall, s.savedAtMs)
        assertEquals(80_000, YoutubeResume.sample(yt, live(speed = 2f), wall, elapsed)!!.positionMs, "tốc độ 2×")
    }

    @Test
    fun `sample - tam dung, tieu de rong, app khac thi khong luu`() {
        assertNull(YoutubeResume.sample(yt, live(playing = false), wall, elapsed), "tạm dừng không đè bài đang lưu")
        assertNull(YoutubeResume.sample(yt, live(title = null), wall, elapsed))
        assertNull(YoutubeResume.sample(yt, live(title = " \n\t "), wall, elapsed))
        assertNull(YoutubeResume.sample(yt, live(pkg = "com.spotify.music"), wall, elapsed))
    }

    @Test
    fun `sample - dong ho lui, toc do vo nghia, vuot cuoi deu kep`() {
        assertEquals(60_000, YoutubeResume.sample(yt, live(updated = elapsed + 5_000), wall, elapsed)!!.positionMs, "đồng hồ lùi ⇒ không cộng")
        assertEquals(60_000, YoutubeResume.sample(yt, live(speed = Float.NaN), wall, elapsed)!!.positionMs)
        assertEquals(60_000, YoutubeResume.sample(yt, live(speed = -1f), wall, elapsed)!!.positionMs)
        assertEquals(60_000, YoutubeResume.sample(yt, live(updated = 0), wall, elapsed)!!.positionMs, "chưa có mốc cập nhật")
        assertEquals(240_000, YoutubeResume.sample(yt, live(pos = 239_000), wall, elapsed)!!.positionMs, "kẹp ở thời lượng")
        assertEquals(0, YoutubeResume.sample(yt, live(pos = -50_000, updated = elapsed), wall, elapsed)!!.positionMs, "kẹp dưới 0")
        assertEquals(1_010_000, YoutubeResume.sample(yt, live(pos = 1_000_000, dur = 0), wall, elapsed)!!.positionMs, "live: không kẹp trên")
    }

    // ── Lên xe ──────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `resumePlan - bai tuoi thi tim tieu de + kenh va tua toi dung giay`() {
        val p = YoutubeResume.resumePlan(saved(), VoiceAppTargets.YOUTUBE, wall + 60_000)!!
        assertEquals("Lạc Trôi | Official MV Sơn Tùng M-TP", p.query)
        assertEquals("Lạc Trôi | Official MV", p.title)
        assertEquals(98_871, p.seekMs)
    }

    @Test
    fun `resumePlan - khong co, khac dich, qua cu thi khong phat tiep`() {
        assertNull(YoutubeResume.resumePlan(null, VoiceAppTargets.YOUTUBE, wall))
        assertNull(YoutubeResume.resumePlan(saved(), VoiceAppTargets.YT_MUSIC, wall), "bài của app khác")
        assertNull(YoutubeResume.resumePlan(saved(), null, wall))
        assertNotNull(YoutubeResume.resumePlan(saved(), VoiceAppTargets.YOUTUBE, wall + YoutubeResume.MAX_AGE_MS))
        assertNull(YoutubeResume.resumePlan(saved(), VoiceAppTargets.YOUTUBE, wall + YoutubeResume.MAX_AGE_MS + 1), "quá cũ")
    }

    @Test
    fun `resumePlan - dong ho xe lui van nhan trong bien, lui qua xa thi bo`() {
        assertNotNull(YoutubeResume.resumePlan(saved(), VoiceAppTargets.YOUTUBE, wall - 3_600_000), "giờ xe lùi 1 giờ")
        assertNull(YoutubeResume.resumePlan(saved(), VoiceAppTargets.YOUTUBE, wall - YoutubeResume.MAX_AGE_MS - 1))
    }

    @Test
    fun `resumePlan - gan het hoac qua cuoi video thi khong phat tiep`() {
        assertNull(YoutubeResume.resumePlan(saved(pos = 230_000), VoiceAppTargets.YOUTUBE, wall), "còn 10 s < 15 s")
        assertNull(YoutubeResume.resumePlan(saved(pos = 240_000), VoiceAppTargets.YOUTUBE, wall))
        assertNull(YoutubeResume.resumePlan(saved(pos = 999_999), VoiceAppTargets.YOUTUBE, wall))
        assertEquals(225_000, YoutubeResume.resumePlan(saved(pos = 225_000), VoiceAppTargets.YOUTUBE, wall)!!.seekMs, "còn đúng 15 s")
    }

    @Test
    fun `resumePlan - live hoac vua mo dau thi mo khong tua`() {
        assertEquals(0, YoutubeResume.resumePlan(saved(pos = 3_600_000, dur = 0), VoiceAppTargets.YOUTUBE, wall)!!.seekMs, "live")
        assertEquals(0, YoutubeResume.resumePlan(saved(pos = 4_999), VoiceAppTargets.YOUTUBE, wall)!!.seekMs)
        assertEquals(5_000, YoutubeResume.resumePlan(saved(pos = 5_000), VoiceAppTargets.YOUTUBE, wall)!!.seekMs)
    }

    @Test
    fun `matches - NFC, hoa thuong, khoang trang, rong thi sai`() {
        val nfd = java.text.Normalizer.normalize("Lạc Trôi", java.text.Normalizer.Form.NFD)
        assertTrue(YoutubeResume.matches(nfd, "Lạc Trôi"), "NFD từ trang ≡ NFC đã lưu")
        assertTrue(YoutubeResume.matches("  LẠC   trôi\t", "Lạc Trôi"))
        assertFalse(YoutubeResume.matches("Lạc Trôi (Remix)", "Lạc Trôi"), "bài khác — không phát nhầm")
        assertFalse(YoutubeResume.matches("", ""))
        assertFalse(YoutubeResume.matches(null, "Lạc Trôi"))
        assertFalse(YoutubeResume.matches("Lac Troi", "Lạc Trôi"), "bỏ dấu là bài khác")
    }

    @Test
    fun `shouldLog - doi bai thi ghi, cung bai toi da moi 5 phut`() {
        val a = saved(at = wall)
        assertTrue(YoutubeResume.shouldLog(null, a, 0))
        assertTrue(YoutubeResume.shouldLog(a, saved(title = "Khác", at = wall + 60_000), wall))
        assertFalse(YoutubeResume.shouldLog(a, saved(at = wall + 60_000), wall))
        assertTrue(YoutubeResume.shouldLog(a, saved(at = wall + YoutubeResume.LOG_EVERY_MS), wall))
        assertTrue(YoutubeResume.shouldLog(a, saved(at = wall - 1), wall), "đồng hồ lùi ⇒ ghi lại")
    }

    // ── Mã hoá ──────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `codec - khu hoi tieng Viet va dau ngan, tren dia khong tab xuong dong`() {
        val s = saved(title = "Live | Đêm nhạc “Hà Nội” 100% & bạn bè").copy(channel = "Kênh | A\\B")
        val raw = YoutubeResume.encode(s)
        assertEquals(6, raw.count { it == '|' }, raw)
        assertTrue(raw.none { it == '\t' || it == '\n' || it == ';' }, raw)
        assertEquals(s, YoutubeResume.decode(raw))
    }

    @Test
    fun `codec - chuoi hong thi null, khong doan`() {
        assertNull(YoutubeResume.decode(null))
        assertNull(YoutubeResume.decode(""))
        assertNull(YoutubeResume.decode("v2|youtube|1|2|3|a|b"), "phiên bản lạ")
        assertNull(YoutubeResume.decode("v1|youtube|x|2|3|a|b"))
        assertNull(YoutubeResume.decode("v1|youtube|-1|2|3|a|b"))
        assertNull(YoutubeResume.decode("v1|youtube|1|2|3||b"), "tiêu đề rỗng")
        assertNull(YoutubeResume.decode("v1|youtube|1|2|3|%ZZ|b"), "phần trăm hỏng")
        assertNull(YoutubeResume.decode("v1|youtube|1|2|3|a"))
        assertNull(YoutubeResume.decode("v1| |1|2|3|a|b"))
        assertEquals(YoutubeResume.Saved("youtube", "a", "", 1, 2, 3), YoutubeResume.decode("v1|youtube|1|2|3|a|"))
    }

    @Test
    fun `khoa luu theo XE, khai ly do`() {
        assertTrue(YoutubeResume.KEY in TripGate.DEVICE_KEYS)
        assertTrue(TripGate.DEVICE_KEYS.getValue(YoutubeResume.KEY).isNotBlank())
    }
}
