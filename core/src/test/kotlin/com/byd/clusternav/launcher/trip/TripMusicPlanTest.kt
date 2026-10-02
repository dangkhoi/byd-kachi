package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.voice.VoiceAppTargets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá NHẠC LÊN XE (spec shortcuts-autostart R3 · C7): mã hoá `trip_music`, cổng "không đè" (null ⇒ bỏ), bóc link →
 * `video_id` → URL chuẩn dựng lại từ khuôn của bảng (chống chèn), và bảng "phát gì".
 *
 * Bằng chứng đo cho lựa chọn "phiên nhạc thay ý-định VIEW": `docs/diagnostics/behind-home-emulator-2026-10-01/trip/`.
 */
class TripMusicPlanTest {

    private val ytm = VoiceAppTargets.byKey(VoiceAppTargets.YT_MUSIC)!!
    private val yt = VoiceAppTargets.byKey(VoiceAppTargets.YOUTUBE)!!
    private val pkg = "com.google.android.apps.youtube.music"

    @Test
    fun `ma hoa trip_music - khu hoi tieng Viet, chu nguoi dung khong mang dau ngan nao len dia`() {
        val m = TripMusic(TripMusicMode.YT_MUSIC, "Sơn Tùng | M-TP;\tChạy ngay đi\n")
        val raw = TripMusicCodec.encode(m)
        assertTrue(raw.startsWith("ytmusic|"), raw)
        assertTrue(raw.none { it == '\t' || it == '\n' || it == ';' } && raw.count { it == '|' } == 1, raw)
        assertEquals(TripMusic(TripMusicMode.YT_MUSIC, "Sơn Tùng | M-TP; Chạy ngay đi"), TripMusicCodec.decode(raw))
        assertEquals("car", TripMusicCodec.encode(TripMusic(TripMusicMode.CAR)))
        assertEquals(TripMusic.OFF, TripMusicCodec.decode(null))
        assertEquals(TripMusic.OFF, TripMusicCodec.decode("spotify|abc"), "kiểu lạ ⇒ Tắt (không đoán)")
        assertEquals(TripMusicCodec.QUERY_MAX, TripMusicCodec.clean("a".repeat(500)).length)
    }

    @Test
    fun `cong khong de - null la CHUA BIET thi bo, dang phat hay isMusicActive thi bo`() {
        val idle = listOf(TripMusicPlan.Session("com.spotify.music", playing = false))
        assertEquals(TripMusicPlan.Gate.OFF, TripMusicPlan.gate(TripMusicMode.OFF, pkg, idle, false))
        assertEquals(TripMusicPlan.Gate.OFF, TripMusicPlan.gate(TripMusicMode.CAR, pkg, idle, false), "Theo player của xe ⇒ 0 lệnh")
        assertEquals(TripMusicPlan.Gate.NOT_INSTALLED, TripMusicPlan.gate(TripMusicMode.YT_MUSIC, null, idle, false))
        assertEquals(TripMusicPlan.Gate.UNKNOWN_MEDIA, TripMusicPlan.gate(TripMusicMode.YT_MUSIC, pkg, null, false))
        assertEquals(TripMusicPlan.Gate.OTHER_PLAYING, TripMusicPlan.gate(TripMusicMode.YT_MUSIC, pkg, idle, musicActive = true))
        assertEquals(
            TripMusicPlan.Gate.OTHER_PLAYING,
            TripMusicPlan.gate(TripMusicMode.YT_MUSIC, pkg, listOf(TripMusicPlan.Session("com.byd.radio", playing = true)), false),
        )
        assertEquals(TripMusicPlan.Gate.GO, TripMusicPlan.gate(TripMusicMode.YT_MUSIC, pkg, idle, false))
        assertEquals(TripMusicPlan.Gate.GO, TripMusicPlan.gate(TripMusicMode.YT_MUSIC, pkg, emptyList(), false))
    }

    /**
     * Khoá lỗi E2E (6) [ĐO máy ảo 02/10 `c6b-music-slot`]: YT Music nằm trong ô 3 ⇒ lượt dựng ô force-stop rồi mở lại nó ⇒
     * phiên mới do CHÍNH Kachi tạo bị coi là "có trước" ⇒ `resume-existing`, bỏ link "Phát gì". App trong ô không bao giờ là
     * phiên có trước; app ngoài ô có phiên ⇒ đúng là sống qua lần tắt máy ⇒ tiếp tục nó.
     */
    @Test
    fun `phien co truoc - app trong o khong bao gio tinh, app ngoai o co phien thi tinh`() {
        val mine = listOf(TripMusicPlan.Session(pkg, playing = false))
        assertFalse(TripMusicPlan.preexisting(pkg, mine, inSlot = true), "ô vừa force-stop + mở lại app ⇒ phiên của Kachi")
        assertTrue(TripMusicPlan.preexisting(pkg, mine, inSlot = false))
        assertFalse(TripMusicPlan.preexisting(pkg, listOf(TripMusicPlan.Session("x.y", false)), inSlot = false))
        assertFalse(TripMusicPlan.preexisting(pkg, null, inSlot = false), "không đọc được ⇒ không coi là có")
    }

    @Test
    fun `kiem lai ngay truoc lenh phat`() {
        assertEquals(TripMusicPlan.Recheck.UNKNOWN_MEDIA, TripMusicPlan.recheck(pkg, null, false))
        assertEquals(TripMusicPlan.Recheck.OTHER_PLAYING, TripMusicPlan.recheck(pkg, listOf(TripMusicPlan.Session("x.y", true)), false))
        assertEquals(TripMusicPlan.Recheck.SELF_PLAYING, TripMusicPlan.recheck(pkg, listOf(TripMusicPlan.Session(pkg, true)), true))
        assertEquals(TripMusicPlan.Recheck.OTHER_PLAYING, TripMusicPlan.recheck(pkg, listOf(TripMusicPlan.Session(pkg, false)), true))
        assertEquals(TripMusicPlan.Recheck.CLEAR, TripMusicPlan.recheck(pkg, listOf(TripMusicPlan.Session(pkg, false)), false))
    }

    @Test
    fun `boc link YouTube ra video_id - moi dang link bai, khong nhan host la hay danh sach phat`() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=30",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM",
            "https://youtu.be/dQw4w9WgXcQ?si=abc",
            "youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
        ).forEach { assertEquals(TripMusicPlan.Source.Video("dQw4w9WgXcQ"), TripMusicPlan.source(it), it) }
        listOf(
            "https://music.youtube.com/playlist?list=RDCLAK5uy_kmPRjHDECIcuVwnKsx2Ng7fyNgFKWNJFs",   // [ĐO] playFromUri không đổi bài
            "https://evil.example/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXc'; reboot",
            "https://www.youtube.com/watch?v=short",
        ).forEach { assertEquals(TripMusicPlan.Source.BadLink, TripMusicPlan.source(it), it) }
        assertEquals(TripMusicPlan.Source.Keyword("sơn tùng mtp"), TripMusicPlan.source("  sơn tùng   mtp "))
        assertEquals(TripMusicPlan.Source.None, TripMusicPlan.source("   "))
    }

    @Test
    fun `URL dung CHINH khuon watch cua bang giong noi - va chi URL xem mot video qua duoc rao`() {
        assertEquals("https://music.youtube.com/watch?v=9bZkp7q19f0", TripMusicPlan.watchUrl(ytm, "9bZkp7q19f0"))
        assertEquals("https://www.youtube.com/watch?v=9bZkp7q19f0", TripMusicPlan.watchUrl(yt, "9bZkp7q19f0"))
        assertNull(TripMusicPlan.watchUrl(ytm, "x'; reboot"))
        assertTrue(TripMusicPlan.safeWatchUrl("https://music.youtube.com/watch?v=9bZkp7q19f0"))
        listOf(
            "https://music.youtube.com/watch?v=9bZkp7q19f0&x=1",
            "http://music.youtube.com/watch?v=9bZkp7q19f0",
            "https://music.youtube.com.evil/watch?v=9bZkp7q19f0",
            "intent://watch?v=9bZkp7q19f0",
        ).forEach { assertFalse(TripMusicPlan.safeWatchUrl(it), it) }
    }

    @Test
    fun `phat gi - URL hop le khi co phien, khong thi tiep tuc phien, khong co phien thi chi mo app`() {
        val url = "https://music.youtube.com/watch?v=9bZkp7q19f0"
        assertEquals(TripMusicPlan.Play.FromUri(url), TripMusicPlan.play(url, hasSession = true))
        assertEquals(TripMusicPlan.Play.Resume, TripMusicPlan.play(null, hasSession = true))
        assertEquals(TripMusicPlan.Play.Resume, TripMusicPlan.play("https://evil/x", hasSession = true), "URL không qua rào ⇒ không dùng")
        assertEquals(TripMusicPlan.Play.OpenOnly, TripMusicPlan.play(url, hasSession = false))
    }

    @Test
    fun `bang kieu - chi YouTube va YT Music co viec, ma app lay tu bang du lieu giong noi`() {
        assertEquals(listOf(TripMusicMode.YT_MUSIC, TripMusicMode.YOUTUBE), TripMusicMode.values().filter { it.plays })
        TripMusicMode.values().filter { it.plays }.forEach { assertTrue(VoiceAppTargets.byKey(it.targetKey)?.watch != null, "$it phải có khuôn watch") }
        assertEquals(TripMusicMode.OFF, TripMusicMode.of("??"))
    }
}
