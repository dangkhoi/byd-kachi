package com.byd.clusternav.modules.clustercast.nowplaying

import com.byd.clusternav.launcher.camera.ClusterBandSpec
import com.byd.clusternav.modules.clustercast.simplified.AppType
import com.byd.clusternav.modules.clustercast.simplified.CastBounds
import com.byd.clusternav.modules.clustercast.simplified.DisplayConfig
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.modules.clustercast.simplified.SlotState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.12 · CAST-NOW-PLAYING — luật hiện/ẩn, chữ, chỗ đặt, màu của dải tên bài trên mặt chiếu cụm. Tên bài ở đây là chữ giả.
 */
class CastNowPlayingStripTest {

    private fun song(t: String? = "Bai A", a: String? = "Ca si B", playing: Boolean = true) = CastNowPlayingMedia(t, a, playing)

    @Test fun `khong chieu thi khong hien gi, du nhac dang phat`() {
        val p = CastNowPlayingPlan()
        val s = p.onMedia(song(), 0)
        assertNull(s.content); assertFalse(s.changed)
    }

    @Test fun `dang chieu va dang phat - hien mot lan, su kien trung khong doi gi`() {
        val p = CastNowPlayingPlan()
        p.onActive(true, 0)
        val first = p.onMedia(song(), 1)
        assertEquals(CastNowPlayingContent("Bai A — Ca si B", playing = true), first.content)
        assertTrue(first.changed)
        repeat(5) { assertFalse(p.onMedia(song(), 2L + it).changed, "sự kiện trùng ⇒ 0 thao tác cửa sổ") }
        assertTrue(p.onMedia(song(t = "Bai C"), 10).changed, "đổi bài ⇒ vẽ lại")
    }

    @Test fun `het chieu - an ngay`() {
        val p = CastNowPlayingPlan()
        p.onActive(true, 0); p.onMedia(song(), 0)
        val off = p.onActive(false, 5)
        assertNull(off.content); assertTrue(off.changed)
    }

    @Test fun `tam dung - doi dau, hen dung mot luot, het han thi an`() {
        val p = CastNowPlayingPlan(autoHideMs = 1_000)
        p.onActive(true, 0); p.onMedia(song(), 0)
        val pause = p.onMedia(song(playing = false), 100)
        assertEquals(CastNowPlayingContent("Bai A — Ca si B", playing = false), pause.content)
        assertEquals(1_000L, pause.checkInMs)
        assertNull(p.onMedia(song(playing = false), 500).checkInMs, "dừng lặp lại không dời mốc")
        assertTrue(p.onPauseCheck(600).content != null, "chưa đủ hạn")
        val hide = p.onPauseCheck(1_100)
        assertNull(hide.content); assertTrue(hide.changed)
        assertNull(p.onMedia(song(playing = false), 1_200).content, "vẫn dừng ⇒ vẫn ẩn")
        assertEquals(true, p.onMedia(song(), 1_300).content?.playing, "phát lại ⇒ hiện lại")
    }

    @Test fun `doi bai khi dang dung - mo moc moi`() {
        val p = CastNowPlayingPlan(autoHideMs = 1_000)
        p.onActive(true, 0)
        p.onMedia(song(playing = false), 0)
        assertEquals(1_000L, p.onMedia(song(t = "Bai D", playing = false), 900).checkInMs)
        assertTrue(p.onPauseCheck(1_100).content != null, "mốc tính từ bài mới")
    }

    @Test fun `het phien hoac khong ten - an`() {
        val p = CastNowPlayingPlan()
        p.onActive(true, 0); p.onMedia(song(), 0)
        assertNull(p.onMedia(null, 1).content)
        p.onMedia(song(), 2)
        assertNull(p.onMedia(song(t = "  "), 3).content)
    }

    @Test fun `chu - gon khoang trang, bo nghe si trung, KHONG cat (marquee chay het)`() {
        assertEquals("A — B", CastNowPlayingText.line(" A\n", "\tB "))
        assertEquals("A", CastNowPlayingText.line("A", ""))
        assertEquals("Same", CastNowPlayingText.line("Same", "same"))
        assertNull(CastNowPlayingText.line(null, "B"))
        // Owner 17/10: "đừng cắt, cho chạy marquee all" — tên dài (video YouTube) phải tới view NGUYÊN VẸN, không dấu "…".
        val long = "Ten video rat dai ".repeat(20).trim()
        val emoji = "\uD83C\uDFB5".repeat(50)
        val p = CastNowPlayingPlan()
        p.onActive(true, 0)
        val shown = p.onMedia(CastNowPlayingMedia(long, emoji, playing = true), 1).content!!
        assertEquals("♪ $long — $emoji", CastNowPlayingText.display(shown))
        assertFalse("…" in CastNowPlayingText.display(shown))
        assertEquals("‖ x", CastNowPlayingText.display(CastNowPlayingContent("x", playing = false)))
    }

    @Test fun `goc - chuoi luu ben on dinh, rac ve mac dinh`() {
        assertEquals(CastNowPlayingCorner.BOTTOM_LEFT, CastNowPlayingCorner.parse("bl"))
        assertEquals(CastNowPlayingDefaults.CORNER, CastNowPlayingCorner.parse("zz"))
        assertEquals(CastNowPlayingDefaults.CORNER, CastNowPlayingCorner.parse(null))
        assertEquals(listOf("tl", "tr", "bl", "br"), CastNowPlayingCorner.values().map { it.key })
    }

    @Test fun `khung - giao khung chieu voi dai nhin thay cua doi cum`() {
        val band = ClusterBandSpec.SEAL_DL3                                    // 140,132..1780,560 @1920×720
        assertEquals(CastBounds(140, 132, 1780, 560), CastNowPlayingPlace.frame(1920, 720, null, band))
        assertEquals(CastBounds(960, 132, 1780, 560), CastNowPlayingPlace.frame(1920, 720, CastBounds(960, 0, 1920, 720), band))
        assertEquals(CastBounds(0, 0, 1920, 720), CastNowPlayingPlace.frame(1920, 720, null, null), "đời cụm chưa đo ⇒ trọn display")
        assertEquals(CastBounds(0, 0, 100, 720), CastNowPlayingPlace.frame(1920, 720, CastBounds(0, 0, 100, 720), band), "không giao ⇒ khung chiếu")
        assertEquals(CastBounds(105, 146, 1335, 622), CastNowPlayingPlace.frame(1440, 800, null, band), "wm size khác ⇒ dải kéo theo tỉ lệ")
    }

    @Test fun `neo - goc phai duoi tinh tu mep phai duoi`() {
        val f = CastBounds(140, 132, 1780, 560)
        val tr = CastNowPlayingPlace.anchor(CastNowPlayingCorner.TOP_RIGHT, f, 1920, 720)
        val m = (720 * CastNowPlayingPlace.MARGIN_FRAC).toInt()
        assertTrue(tr.right && !tr.bottom)
        assertEquals(1920 - 1780 + m, tr.x); assertEquals(132 + m, tr.y)
        val bl = CastNowPlayingPlace.anchor(CastNowPlayingCorner.BOTTOM_LEFT, f, 1920, 720)
        assertEquals(140 + m, bl.x); assertEquals(720 - 560 + m, bl.y)
        assertEquals(1640 - 2 * m, bl.maxWidth)
        assertEquals(30, bl.textPx)
    }

    @Test fun `khung chieu theo trang thai phien`() {
        val b = CastBounds(10, 20, 900, 700)
        val full = SimpleCastState.CastingFull("p", AppType.NORMAL, DisplayConfig("1920x720", "0,0,0,0", bounds = b))
        assertEquals(b, CastNowPlayingPlace.castFrame(full))
        val pinned = full.copy(pinned = DisplayConfig("1920x720", "0,0,0,0", bounds = CastBounds(0, 0, 50, 50)))
        assertEquals(CastBounds(0, 0, 50, 50), CastNowPlayingPlace.castFrame(pinned), "bản ghim thắng")
        assertNull(CastNowPlayingPlace.castFrame(SimpleCastState.CastingFull("cp", AppType.CARPLAY, DisplayConfig.CARPLAY)))
        val split = SimpleCastState.CastingSplit(
            SlotState("l", DisplayConfig("1920x720", "0,0,0,0", bounds = CastBounds(0, 0, 960, 720))),
            SlotState("r", DisplayConfig("1920x720", "0,0,0,0", bounds = CastBounds(960, 10, 1920, 700))),
        )
        assertEquals(CastBounds(0, 0, 1920, 720), CastNowPlayingPlace.castFrame(split))
        assertNull(CastNowPlayingPlace.castFrame(SimpleCastState.Idle))
    }

    @Test fun `mau - tuong phan chu nen it nhat 7 lan ca hai che do`() {
        listOf(true, false).forEach { night ->
            val c = CastNowPlayingStyle.colors(night)
            assertTrue(contrast(c.text, c.background) >= 7.0, "night=$night: ${contrast(c.text, c.background)}")
        }
        assertTrue(lum(CastNowPlayingStyle.NIGHT.background) < lum(CastNowPlayingStyle.DAY.background), "đêm nền tối, ngày nền sáng")
    }

    @Test fun `mac dinh TAT - owner 17-10 truoc OTA`() = assertFalse(CastNowPlayingDefaults.ENABLED)

    private fun lum(argb: Int): Double {
        fun ch(v: Int): Double = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(argb shr 16 and 0xFF) + 0.7152 * ch(argb shr 8 and 0xFF) + 0.0722 * ch(argb and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
