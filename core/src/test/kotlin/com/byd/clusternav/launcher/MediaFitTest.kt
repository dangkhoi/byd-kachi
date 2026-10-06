package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 `WF-MEDIA-SMALL` — [ĐO máy ảo QA3 04/10] widget nhạc đơn ở khung nhỏ mất nút điều khiển (4×1 có thanh nút: trước/sau
 * rộng 0; 2×1: chỉ còn ảnh bìa; 2×2: ảnh + tiến trình, 0 nút). Số ở mật độ 1,5 như đầu xe: ảnh 80 dp = 120 px, nút 48 dp =
 * 72 px, tên 15 sp ≈ 26 px/dòng, nghệ sĩ 12,5 sp ≈ 22 px, cột chữ tối thiểu 6 em × 22,5 px = 135 px.
 */
class MediaFitTest {

    private val b = MediaFit.Box(
        pad = 12, art = 120, gap = 12, titleH = 26, artistH = 22, textMinW = 135,
        progW = 228, progH = 6, progGap = 13, btn = 72, btnGap = 12, rowGap = 18,
    )
    private val all = MediaFit.Part.values().toSet()

    private fun p(w: Int, h: Int) = MediaFit.plan(w, h, b)

    @Test
    fun `khung mot hang - xep NGANG, giu ba nut, bo anh khi khong du cao`() {
        val wide = p(615, 123)
        assertEquals(MediaFit.Arrange.ROW, wide.arrange)
        assertEquals(all, wide.parts, "4×1: ảnh │ tên + nghệ sĩ + tiến trình │ ba nút")
        assertTrue(wide.k >= 1.0)
        val dock = p(615, 97)
        assertEquals(MediaFit.Arrange.ROW, dock.arrange)
        assertEquals(all - MediaFit.Part.ART, dock.parts, "4×1 có thanh nút: bỏ ẢNH trước, ba nút vẫn 48 dp")
    }

    @Test
    fun `khung nho - bo phan phu theo thu tu, nut truoc phat sau con nguyen`() {
        val twoByOne = p(301, 123)
        assertEquals(setOf(MediaFit.Part.TITLE, MediaFit.Part.PREV_NEXT), twoByOne.parts, "2×1: tên + ba nút (trước: chỉ ảnh)")
        assertEquals(MediaFit.Arrange.STACK, twoByOne.arrange)
        assertEquals(setOf(MediaFit.Part.TITLE, MediaFit.Part.PROGRESS, MediaFit.Part.PREV_NEXT), p(301, 148).parts)
        val twoByTwo = p(301, 260)
        assertEquals(all - MediaFit.Part.ART, twoByTwo.parts, "2×2: đủ trừ ảnh (trước: ảnh + tiến trình, 0 nút)")
        assertEquals(301.0 / 240.0, twoByTwo.k, 1e-9, "giãn tới chạm bề ngang 240 px của hàng nút")
    }

    @Test
    fun `khung to - du noi dung, gian toi tran, dang doc nhu 2_92`() {
        val quad = p(615, 520)
        assertEquals(all, quad.parts)
        assertEquals(MediaFit.Arrange.STACK, quad.arrange)
        assertEquals(MediaFit.MAX_SCALE, quad.k, 1e-9)
    }

    @Test
    fun `khung hep hon ba nut - chi nut phat, qua nho thi bao khong vua`() {
        val one = p(150, 123)
        assertEquals(emptySet<MediaFit.Part>(), one.parts, "chỉ nút PHÁT")
        assertTrue(one.fits && one.k >= 1.0)
        val tiny = p(60, 60)
        assertFalse(tiny.fits)
        assertEquals(1.0, tiny.k)
    }

    @Test
    fun `tinh chat - vua khung, nut khong duoi 48 dp, ba nut con khi chung vua`() {
        for (w in 40..1300 step 13) for (h in 40..900 step 11) {
            val pl = p(w, h)
            if (!pl.fits) { assertTrue(w < b.btn || h < b.btn, "${w}x$h: một nút còn vừa mà báo không vừa"); continue }
            assertTrue(pl.k >= 1.0 - 1e-9 && pl.k <= MediaFit.MAX_SCALE + 1e-9, "${w}x$h k=${pl.k}")
            val (nw, nh) = MediaFit.natural(pl.parts, pl.arrange, b)
            assertTrue(nw * pl.k <= w + 1e-6 && nh * pl.k <= h + 1e-6, "${w}x$h tràn")
            if (w >= 3 * b.btn + 2 * b.btnGap && h >= b.btn) assertTrue(MediaFit.Part.PREV_NEXT in pl.parts, "${w}x$h: ba nút vừa mà bị bỏ")
            // Mức chọn là mức NHIỀU nội dung nhất đạt k ≥ 1.
            val idx = MediaFit.LEVELS.indexOf(pl.parts)
            MediaFit.LEVELS.take(idx).forEach { lv ->
                val k = maxOf(MediaFit.scale(lv, MediaFit.Arrange.STACK, w, h, b), MediaFit.scale(lv, MediaFit.Arrange.ROW, w, h, b))
                assertTrue(k < 1.0, "${w}x$h: mức nhiều hơn $lv cũng vừa (k=$k)")
            }
        }
    }
}
