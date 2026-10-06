package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * 2.92 (spec `docs/specs/kachi-292-shortcut-widget.html` R3) — widget `w_apps` với NHIỀU app: owner 06/10 *"không nên
 * giới hạn 8 app … bao nhiêu kệ người ta"*. Khi không cách xếp nào giữ mọi icon ≥ sàn (40 dp — ô chạm 48 dp) trong khung,
 * lưới CUỘN theo trục dài của khung; không icon nào bị cắt/chồng (2.91 kẹp sàn rồi để khối TRÀN khung, tầng vẽ cắt).
 *
 * Bài tính lại kỳ vọng bằng phép đếm độc lập (không gọi [ShortcutGridFit]) — px ở mật độ 1,5: `g` 12 · sàn 60 · trần 180.
 */
class ShortcutGridScrollTest {

    private val g = 12
    private val lo = 60
    private val hi = 180

    private fun fit(n: Int, w: Int, h: Int) = ShortcutGridFit.fit(n, w, h, g, lo, hi)

    /** Có cách xếp KHỚP với icon ≥ sàn không (vét cạn độc lập, như [ShortcutGridFitTest]). */
    private fun fitsAtFloor(n: Int, w: Int, h: Int): Boolean = (1..n).any { c ->
        val r = (n + c - 1) / c
        c * lo + (c + 1) * g <= w && r * lo + (r + 1) * g <= h
    }

    private fun check(n: Int, w: Int, h: Int) {
        if (fitsAtFloor(n, w, h)) return
        val f = fit(n, w, h)
        val tag = "n=$n khung ${w}x$h → $f"
        val vertical = h >= w
        val cross = if (vertical) w else h
        var k = 1
        while ((k + 1) * lo + (k + 2) * g <= cross) k++
        val s = minOf(lo, cross)
        assertEquals(s, f.iconPx, "icon = sàn (ngang trục hẹp hơn ⇒ = ngang trục) — $tag")
        val lines = if (vertical) f.cols else f.rows       // số dòng NGANG trục
        if (f.scroll != ShortcutGridFit.Scroll.NONE) {
            assertEquals(if (vertical) ShortcutGridFit.Scroll.VERTICAL else ShortcutGridFit.Scroll.HORIZONTAL, f.scroll, "cuộn theo trục DÀI — $tag")
            assertEquals(k, lines, "số dòng ngang trục = nhiều nhất vừa ở sàn — $tag")
            val length = if (vertical) f.contentHeightPx else f.contentWidthPx
            assertTrue(length > (if (vertical) h else w), "chế độ cuộn mà nội dung vừa khung — $tag")
            assertEquals(length - (if (vertical) h else w), f.maxScrollPx, tag)
            assertEquals(if (vertical) w else h, if (vertical) f.contentWidthPx else f.contentHeightPx, "ngang trục = khung — $tag")
        } else {
            assertEquals(1, lines, "suy biến không cuộn chỉ có ở MỘT dòng — $tag")
        }
        val cw = f.contentWidthPx
        val ch = f.contentHeightPx
        val rects = (0 until n).map { i -> intArrayOf(f.left(i), f.top(i), f.left(i) + s, f.top(i) + s) }
        rects.forEach { r -> assertTrue(r[0] >= 0 && r[1] >= 0 && r[2] <= cw && r[3] <= ch, "icon tràn nội dung ${r.toList()} — $tag") }
        for (a in rects.indices) for (b in a + 1 until rects.size) {
            val x = rects[a]; val y = rects[b]
            assertTrue(!(x[0] < y[2] && y[0] < x[2] && x[1] < y[3] && y[1] < x[3]), "icon $a và $b chồng nhau — $tag")
        }
        // Bước dọc trục (giữa hai dòng liên tiếp) ≥ sàn + g ⇒ ô chạm dọc trục ≥ 48 dp kể cả khi icon hẹp hơn sàn.
        val along = rects.map { if (vertical) it[1] else it[0] }.distinct().sorted()
        along.zipWithNext { a, b -> assertTrue(b - a >= lo + g - 1, "bước dọc trục ${b - a} < sàn + g — $tag") }
        // Lộ NỬA icon kế ở mép khung (vị trí cuộn 0) khi khung chứa được ít nhất một icon trọn + nửa icon.
        if (f.scroll != ShortcutGridFit.Scroll.NONE) {
            val frame = if (vertical) h else w
            val minGap = maxOf(s, lo) + g - s
            if (frame >= minGap + s + minGap + s / 2) {
                val cut = along.first { it + s > frame }
                assertTrue(cut < frame && abs((frame - cut) - s / 2f) <= 1f, "mép khung phải cắt GIỮA icon kế (bắt đầu $cut) — $tag")
            }
        }
        // Hai app đầu luôn ở đầu dải: dòng ngang trục ĐẦU tiên (cuộn ngang xếp theo cột).
        if (n >= 2 && lines >= 2) {
            val a0 = if (vertical) f.top(0) else f.left(0)
            val a1 = if (vertical) f.top(1) else f.left(1)
            assertEquals(a0, a1, "app 1 và 2 phải cùng dòng đầu dọc trục — $tag")
        }
    }

    @Test
    fun `nhieu app x moi khung - cuon dung truc, khong cat khong chong`() {
        val boxes = listOf(262 to 956, 301 to 804, 1872 to 123, 301 to 123, 277 to 252, 929 to 395, 136 to 43, 40 to 30, 84 to 99, 500 to 500)
        for ((w, h) in boxes) for (n in 1..60) check(n, w, h)
    }

    @Test
    fun `don dieu - them app thi icon khong to ra`() {
        val boxes = listOf(262 to 956, 258 to 956, 301 to 804, 1872 to 123, 301 to 123, 277 to 252, 929 to 395, 600 to 300)
        for ((w, h) in boxes) {
            val sizes = (1..80).map { fit(it, w, h).iconPx }
            sizes.zipWithNext { a, b -> assertTrue(b <= a, "khung ${w}x$h: thêm app mà icon to ra $sizes") }
            assertTrue(sizes.all { it >= minOf(lo, minOf(w, h)) }, "icon dưới sàn ở khung ${w}x$h: $sizes")
        }
    }

    /**
     * 2.92 OQ2 (lấp bề ngang) chọn được icon DƯỚI cỡ lớn nhất ⇒ đơn điệu không còn là hệ quả hiển nhiên của phép khớp.
     * Quét dày khung đứng + nằm (651 khung × n 1–40): thêm app không bao giờ làm icon to ra. (Đọc nghĩa đen "trục ngang
     * = chiều cao" ở khung nằm vi phạm ở 213/1 734 khung của mô hình — lý do luật chỉ áp khung đứng, spec §4.3a.)
     */
    @Test
    fun `don dieu - quet day khung dung va nam`() {
        for (w in 40..1900 step 62) for (h in 40..1000 step 48) {
            var prev = Int.MAX_VALUE
            for (n in 1..40) {
                val s = fit(n, w, h).iconPx
                assertTrue(s <= prev, "khung ${w}x$h: $n app ⇒ icon $s > ${n - 1} app ⇒ $prev")
                prev = s
            }
        }
    }

    /**
     * Senior review 2.92 Pass 3 — đơn điệu tới TRẦN KỸ THUẬT ([AppShortcutCodec.MAX] = 256; hai bài trên dừng ở 40/80 app),
     * gồm khung đứng to nơi luật hoà lề ≤ 1 px có tác dụng (842×920). [ĐO bài dò vét cạn 06/10] 2 031 khung đứng + khung nằm
     * × n 1–256: 0 vi phạm.
     */
    @Test
    fun `don dieu - toi tran ky thuat 256 app`() {
        val frames = (200..1000 step 100).flatMap { w -> (w..1000 step 100).map { h -> w to h } } + listOf(842 to 920, 1000 to 600)
        for ((w, h) in frames) {
            var prev = Int.MAX_VALUE
            for (n in 1..AppShortcutCodec.MAX) {
                val s = fit(n, w, h).iconPx
                assertTrue(s <= prev, "khung ${w}x$h: $n app ⇒ icon $s > ${n - 1} app ⇒ $prev")
                prev = s
            }
        }
    }

    @Test
    fun `40 app o doc hep - cuon doc 3 cot icon san`() {
        val f = fit(40, 262, 956)
        assertEquals(ShortcutGridFit.Scroll.VERTICAL, f.scroll)
        assertEquals(listOf(3, 14, 60), listOf(f.cols, f.rows, f.iconPx))
        // Khe dọc nới để mép dưới cắt giữa hàng thứ 13: j = 12 hàng trọn ⇒ khe (956 − 12,5 × 60) / 13 = 15,85.
        assertEquals((956 - 12.5f * 60) / 13, f.gapYPx, 1e-3f)
        assertEquals(kotlin.math.ceil(14 * 60 + 15 * f.gapYPx.toDouble()).toInt(), f.contentHeightPx)
        assertEquals(f.contentHeightPx - 956, f.maxScrollPx)
    }

    @Test
    fun `30 app dai rong thap - cuon ngang mot hang`() {
        val f = fit(30, 1872, 123)
        assertEquals(ShortcutGridFit.Scroll.HORIZONTAL, f.scroll)
        assertEquals(listOf(30, 1, 60), listOf(f.cols, f.rows, f.iconPx))
        assertEquals((1872 - 25.5f * 60) / 26, f.gapXPx, 1e-3f)     // 25 icon trọn + nửa icon thứ 26
        assertEquals(kotlin.math.ceil(30 * 60 + 31 * f.gapXPx.toDouble()).toInt(), f.contentWidthPx)
        assertEquals((123 - 60) / 2f, f.gapYPx, 1e-3f)
    }

    @Test
    fun `o nho 2x1 voi 8 app - cuon ngang thay vi icon 42 px o cham 37 dp cua 2_91`() {
        // [ĐO máy ảo 06/10] 2.89/2.91: khung 301×123, 8 app ⇒ 4×2, icon 42 px, ô con 68×54 px (54 px = 36 dp < 48 dp).
        val f = fit(8, 301, 123)
        assertEquals(ShortcutGridFit.Scroll.HORIZONTAL, f.scroll)
        assertEquals(60, f.iconPx)
        // [ĐO máy ảo 06/10, bản đầu 2.92] khe sàn ⇒ 4 icon trọn, icon thứ 5 khuất ĐÚNG sau mép — không thấy là cuộn được.
        // Nay 3 icon trọn + NỬA icon thứ 4: khe (301 − 3,5 × 60) / 4 = 22,75.
        assertEquals(22.75f, f.gapXPx, 1e-3f)
        assertEquals(kotlin.math.ceil(8 * 60 + 9 * 22.75).toInt(), f.contentWidthPx)
        // 4 app vẫn KHỚP (icon 60 px vừa đúng sàn) — ranh giới khớp/cuộn liền mạch, cùng cỡ icon.
        val four = fit(4, 301, 123)
        assertEquals(ShortcutGridFit.Scroll.NONE, four.scroll)
        assertEquals(60, four.iconPx)
    }

    @Test
    fun `khung rat nho - icon bang ngang truc, buoc van du o cham`() {
        val f = fit(4, 40, 30)
        assertEquals(ShortcutGridFit.Scroll.HORIZONTAL, f.scroll)
        assertEquals(30, f.iconPx)
        assertEquals(4 * 30 + 5 * (lo + g - 30), f.contentWidthPx)
        assertEquals(0f, f.gapYPx, 1e-3f)
    }

    @Test
    fun `suy bien mot dong ma noi dung vua - khong cuon, chia deu`() {
        val f = fit(2, 300, 30)
        assertEquals(ShortcutGridFit.Scroll.NONE, f.scroll)
        assertEquals(listOf(2, 1, 30), listOf(f.cols, f.rows, f.iconPx))
        assertEquals((300 - 60) / 3f, f.gapXPx, 1e-3f)
        assertEquals(300 to 30, f.contentWidthPx to f.contentHeightPx)
    }
}
