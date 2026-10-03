package com.byd.clusternav.launcher.trip

import com.byd.clusternav.launcher.voice.VoiceAppTargets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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

    /**
     * L4 · D3(i) — ĐỔI PIN có lý do: bản R3.5 cũ (`Gate.OTHER_PLAYING` khi nguồn khác đang phát / `isMusicActive`) làm bước
     * nhạc KHÔNG BAO GIỜ chạy trên xe owner: BYD MediaAutoPlay tự phát lại nguồn cuối lúc nổ máy ([ĐO firmware] +
     * `memory_play_back=1` [ĐO xe 29/09]). Chọn YouTube / YT Music là lựa chọn CỤ THỂ của người dùng ⇒ thắng nguồn xe.
     * Còn giữ: Tắt / Theo player của xe ⇒ 0 lệnh; chưa cài; không đọc được phiên (null) ⇒ bỏ; app chọn ĐANG phát ⇒ xong.
     */
    @Test
    fun `cong - chon app cu the thi THANG nguon xe dang phat, Theo player xe van khong lam gi`() {
        val idle = listOf(TripMusicPlan.Session("com.spotify.music", playing = false))
        assertEquals(TripMusicPlan.Gate.OFF, TripMusicPlan.gate(TripMusicMode.OFF, pkg, idle))
        assertEquals(TripMusicPlan.Gate.OFF, TripMusicPlan.gate(TripMusicMode.CAR, pkg, idle), "Theo player của xe ⇒ 0 lệnh")
        assertEquals(TripMusicPlan.Gate.NOT_INSTALLED, TripMusicPlan.gate(TripMusicMode.YT_MUSIC, null, idle))
        assertEquals(TripMusicPlan.Gate.UNKNOWN_MEDIA, TripMusicPlan.gate(TripMusicMode.YT_MUSIC, pkg, null))
        val radio = listOf(TripMusicPlan.Session("com.byd.radio", playing = true))
        assertEquals(TripMusicPlan.Gate.GO, TripMusicPlan.gate(TripMusicMode.YT_MUSIC, pkg, radio), "nguồn xe tự phát lại KHÔNG chặn nữa")
        assertTrue(TripMusicPlan.otherPlaying(pkg, radio, musicActive = true), "nhưng sổ/nhật ký vẫn ghi là đã giành")
        assertEquals(TripMusicPlan.Gate.SELF_PLAYING, TripMusicPlan.gate(TripMusicMode.YT_MUSIC, pkg, listOf(TripMusicPlan.Session(pkg, true))))
        assertEquals(TripMusicPlan.Gate.GO, TripMusicPlan.gate(TripMusicMode.YT_MUSIC, pkg, idle))
        assertEquals(TripMusicPlan.Gate.GO, TripMusicPlan.gate(TripMusicMode.YT_MUSIC, pkg, emptyList()))
        assertFalse(TripMusicPlan.otherPlaying(pkg, listOf(TripMusicPlan.Session(pkg, true)), musicActive = true), "chính app chọn đang phát ≠ nguồn khác")
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

    /** L4 · D3(i) — ĐỔI PIN: nguồn khác vừa phát trong lúc chờ phiên (BYD tự phát lại) không còn dừng lượt — cùng lẽ [gate]. */
    @Test
    fun `kiem lai ngay truoc lenh phat`() {
        assertEquals(TripMusicPlan.Recheck.UNKNOWN_MEDIA, TripMusicPlan.recheck(pkg, null))
        assertEquals(TripMusicPlan.Recheck.CLEAR, TripMusicPlan.recheck(pkg, listOf(TripMusicPlan.Session("x.y", true))))
        assertEquals(TripMusicPlan.Recheck.SELF_PLAYING, TripMusicPlan.recheck(pkg, listOf(TripMusicPlan.Session(pkg, true))))
        assertEquals(TripMusicPlan.Recheck.CLEAR, TripMusicPlan.recheck(pkg, listOf(TripMusicPlan.Session(pkg, false))))
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

    /**
     * L4 · D3(ii) — quyết bằng PHIÊN ĐO ĐƯỢC. ĐỔI PIN có lý do: bản cũ `play(url, hasSession=false)` ⇒ `OpenOnly` là đúng ca
     * owner báo 03/10 (*"YouTube … để link không chạy"*): YouTube nguội trên trang chủ không có phiên ([ĐO máy ảo] e3
     * `open-only (no session)`) ⇒ link không bao giờ tới. Nay có link mà không phiên NHẬN URI ⇒ `View` (activity, K4-VIEW).
     * Phiên [ĐO máy ảo 03/10 e5/e8] YT Music `actions=2600887` có bit `ACTION_PLAY_FROM_URI` ⇒ vẫn `FromUri` (0 lệnh cửa sổ).
     */
    @Test
    fun `phat gi - phien nhan URI thi qua phien, khong phien hay phien khong nhan URI thi VIEW, khong link thi tiep tuc`() {
        val url = "https://music.youtube.com/watch?v=9bZkp7q19f0"
        val uri = TripMusicPlan.Session(pkg, playing = false, acceptsUri = true)
        val noUri = TripMusicPlan.Session(pkg, playing = false, acceptsUri = false)
        assertEquals(TripMusicPlan.Play.FromUri(url), TripMusicPlan.play(url, uri))
        assertEquals(TripMusicPlan.Play.View(url), TripMusicPlan.play(url, noUri), "phiên không nhận URI ⇒ giao bằng activity")
        assertEquals(TripMusicPlan.Play.View(url), TripMusicPlan.play(url, null), "không phiên (YouTube trang chủ) ⇒ giao bằng activity")
        assertEquals(TripMusicPlan.Play.Resume, TripMusicPlan.play(null, uri))
        assertEquals(TripMusicPlan.Play.Resume, TripMusicPlan.play("https://evil/x", uri), "URL không qua rào ⇒ không dùng")
        assertEquals(TripMusicPlan.Play.OpenOnly, TripMusicPlan.play("https://evil/x", null), "URL lạ không bao giờ thành lệnh VIEW")
        assertEquals(TripMusicPlan.Play.OpenOnly, TripMusicPlan.play(null, null))
    }

    /**
     * L4 · D3(ii) — bốn câu CLAUDE.md §4 của lệnh MỚI K4-VIEW, khoá bằng chuỗi: chỉ màn ảo của Kachi (`vd ≥ 1`, không bao giờ
     * display 0), đúng một gói (`-p`), URL chỉ là link xem một video (không `'` nào thoát cặp nháy).
     */
    @Test
    fun `K4-VIEW - pham vi tuong minh, chuoi dung tung byte`() {
        val url = "https://music.youtube.com/watch?v=9bZkp7q19f0"
        assertEquals(
            "am start --display 283 -a android.intent.action.VIEW -d 'https://music.youtube.com/watch?v=9bZkp7q19f0' -p $pkg",
            TripMusicPlan.viewCmd(283, url, pkg),
        )
        assertThrows(IllegalArgumentException::class.java) { TripMusicPlan.viewCmd(0, url, pkg) }
        assertThrows(IllegalArgumentException::class.java) { TripMusicPlan.viewCmd(-1, url, pkg) }
        assertThrows(IllegalArgumentException::class.java) { TripMusicPlan.viewCmd(283, "https://music.youtube.com/watch?v=9bZkp7q19f0'; reboot", pkg) }
        assertThrows(IllegalArgumentException::class.java) { TripMusicPlan.viewCmd(283, "intent://x", pkg) }
        assertThrows(IllegalArgumentException::class.java) { TripMusicPlan.viewCmd(283, url, "a;reboot") }
    }

    /** L4 · D3(iii) — câu gợi ý "cần link" là DỮ LIỆU của kiểu, không phải nhánh lúc chạy (lúc chạy quyết bằng phiên đo). */
    @Test
    fun `kieu khong phat tiep - YouTube can link, YT Music tiep tuc duoc`() {
        assertTrue(TripMusicMode.YT_MUSIC.resumable)
        assertFalse(TripMusicMode.YOUTUBE.resumable)
        assertFalse(TripMusicMode.OFF.resumable || TripMusicMode.CAR.resumable)
    }

    @Test
    fun `bang kieu - chi YouTube va YT Music co viec, ma app lay tu bang du lieu giong noi`() {
        assertEquals(listOf(TripMusicMode.YT_MUSIC, TripMusicMode.YOUTUBE), TripMusicMode.values().filter { it.plays })
        TripMusicMode.values().filter { it.plays }.forEach { assertTrue(VoiceAppTargets.byKey(it.targetKey)?.watch != null, "$it phải có khuôn watch") }
        assertEquals(TripMusicMode.OFF, TripMusicMode.of("??"))
    }
}
