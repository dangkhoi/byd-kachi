package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * 2.92 (spec `docs/specs/kachi-292-shortcut-widget.html` R1) — lưới icon widget `w_apps`, chế độ KHỚP (mọi icon vừa khung).
 *
 * Owner 06/10 (ảnh xe): *"icon hơi bé so với thanh, margin 2 bên nhiều quá phí"*. R-SI1 (2.87) giữ khe = 0,3 × icon ⇒ một
 * cột 8 icon trong ô dọc hẹp bị chiều cao chặn, bề ngang thừa thành lề. Nay khe CỐ ĐỊNH `g` (8 dp) giữa hai icon và tới
 * mép. Bài khoá từng vế bằng TÍNH CHẤT trên lưới n × nhiều tỉ lệ khung, cỡ lớn nhất tính lại bằng VÉT CẠN (không gọi hàm
 * của [ShortcutGridFit]) để bài không tự xác nhận chính phép chia của nó:
 *  - không tràn khung; icon to NHẤT có thể (thêm 1 px là tràn, không số cột nào cho icon to hơn);
 *  - mọi icon cùng cỡ; khe đều theo mỗi trục (khe giữa = khe tới mép, ± 1 px), khe ≥ `g`; hàng cuối thiếu căn giữa;
 *  - kẹp [sàn, trần]; ô chạm (icon + khe) ≥ sàn + g = 48 dp.
 * Đơn vị: px ở mật độ 1,5 như đầu xe — `g` = 8 dp = 12 px, sàn 40 dp = 60 px, trần 120 dp = 180 px. Chế độ CUỘN (không
 * cách xếp nào giữ icon ≥ sàn) ở [ShortcutGridScrollTest].
 */
class ShortcutGridFitTest {

    private val g = 12
    private val minPx = 60
    private val maxPx = 180

    private fun fit(n: Int, w: Int, h: Int) = ShortcutGridFit.fit(n, w, h, g, minPx, maxPx)

    /** Khung thử: vuông · 2:1 · 1:2 · 3:1 · 1:3 · lẻ · ô máy ảo đo 06/10 · rất to. */
    private val boxes = listOf(
        400 to 400, 600 to 300, 300 to 600, 900 to 300, 300 to 900, 333 to 217, 1001 to 97,
        4000 to 4000, 5000 to 2500, 640 to 360, 360 to 240, 262 to 956, 258 to 956, 301 to 804, 1872 to 123, 929 to 395,
        277 to 252, 1558 to 668,
    )

    // ── vét cạn độc lập ──────────────────────────────────────────────────────────────────────────────────────

    private fun fitsAt(c: Int, r: Int, s: Int, w: Int, h: Int) = c * s + (c + 1) * g <= w && r * s + (r + 1) * g <= h

    private fun bruteLargest(c: Int, r: Int, w: Int, h: Int): Int {
        var s = 0
        while (fitsAt(c, r, s + 1, w, h)) s++
        return s
    }

    private data class Cand(val c: Int, val r: Int, val s: Int, val empty: Int)

    private fun candidates(n: Int, w: Int, h: Int): List<Cand> = (1..n).map { c ->
        val r = (n + c - 1) / c
        Cand(c, r, bruteLargest(c, r, w, h), c * r - n)
    }

    private fun capOf(c: Cand) = minOf(c.s, maxPx)

    /** Lề hai bên của cách xếp [c] ở cỡ đã kẹp (khe ngang chia đều). */
    private fun side(c: Cand, w: Int) = (w - c.c * capOf(c)) / (c.c + 1.0)

    /**
     * Kỳ vọng, tính độc lập với [GridFit]: thứ tự R-SI1 rồi luật lấp bề ngang (khung đứng, dung sai 10 %): lề nhỏ nhất,
     * lề lệch ≤ 1 px (phần lẻ làm tròn icon về px nguyên) là HOÀ ⇒ thứ tự R-SI1 (senior review 2.92 Pass 3).
     */
    private fun expected(all: List<Cand>, w: Int, h: Int): Cand {
        val legible = all.filter { it.s >= minPx }
        val order = compareBy<Cand>({ -capOf(it) }, { -it.s }, { it.empty }, { it.r })
        val best = legible.minWith(order)
        if (h < w) return best
        val pool = legible.filter { capOf(it) * 10 >= capOf(best) * 9 }
        val least = pool.minOf { side(it, w) }
        return pool.filter { side(it, w) <= least + 1.0 + 1e-6 }.minWith(order)
    }

    /** Ca KHỚP (vét cạn cho icon ≥ sàn) — kiểm mọi tính chất; trả `false` nếu ca thuộc chế độ cuộn (bỏ qua). */
    private fun check(n: Int, w: Int, h: Int): Boolean {
        val all = candidates(n, w, h)
        val bestRaw = all.maxOf { it.s }
        if (bestRaw < minPx) return false
        val f = fit(n, w, h)
        val tag = "n=$n khung ${w}x$h → $f"
        assertEquals(ShortcutGridFit.Scroll.NONE, f.scroll, "vừa ở sàn ⇒ không cuộn — $tag")
        assertEquals(n, f.count, tag)
        assertEquals((n + f.cols - 1) / f.cols, f.rows, "số hàng = ⌈n/c⌉ — $tag")
        assertEquals(w to h, f.contentWidthPx to f.contentHeightPx, "không cuộn ⇒ nội dung = khung — $tag")

        // Chọn số cột (vét cạn độc lập): cỡ CHƯA kẹp lớn nhất; hoà ⇒ ít ô trống; rồi ít hàng. Rồi (OQ2) khung ĐỨNG: trong
        // các cách có icon đã kẹp ≥ 90 % icon lớn nhất, lề hai bên nhỏ nhất thắng; hoà lề ⇒ thứ tự trước.
        val expect = expected(all, w, h)
        val chosen = all.first { it.c == f.cols }
        assertEquals(expect.c, f.cols, "số cột ≠ vét cạn (mong ${expect.c}) — $tag")
        assertTrue(capOf(chosen) * 10 >= capOf(all.maxBy { it.s }) * 9, "icon dưới 90 % icon lớn nhất — $tag")

        assertEquals(chosen.s.coerceIn(minPx, maxPx), f.iconPx, "kẹp [sàn, trần] — $tag")
        if (chosen.s < maxPx) assertTrue(!fitsAt(f.cols, f.rows, f.iconPx + 1, w, h), "thêm 1 px vẫn vừa ⇒ chưa to nhất — $tag")

        val s = f.iconPx
        assertEquals((w - f.cols * s) / (f.cols + 1f), f.gapXPx, 1e-3f, "khe ngang = phần dư chia đều — $tag")
        assertEquals((h - f.rows * s) / (f.rows + 1f), f.gapYPx, 1e-3f, "khe dọc = phần dư chia đều — $tag")
        assertTrue(f.gapXPx >= g - 1e-3 && f.gapYPx >= g - 1e-3, "khe dưới g — $tag")
        // R1.2 — ô chạm = icon + khe (nửa khe mỗi bên) ≥ sàn + g trên cả hai trục.
        assertTrue(s + f.gapXPx >= minPx + g - 1e-3 && s + f.gapYPx >= minPx + g - 1e-3, "ô chạm < sàn + g — $tag")

        val rects = (0 until n).map { i -> intArrayOf(f.left(i), f.top(i), f.left(i) + s, f.top(i) + s) }
        rects.forEach { r -> assertTrue(r[0] >= 0 && r[1] >= 0 && r[2] <= w && r[3] <= h, "icon tràn khung ${r.toList()} — $tag") }
        for (a in rects.indices) for (b in a + 1 until rects.size) {
            val x = rects[a]; val y = rects[b]
            assertTrue(!(x[0] < y[2] && y[0] < x[2] && x[1] < y[3] && y[1] < x[3]), "icon $a và $b chồng nhau — $tag")
        }
        checkEvenGaps(f, tag)
        return true
    }

    /** Khe đều theo mỗi trục: mép trái, giữa các icon, mép phải lệch nhau ≤ 1 px (làm tròn); hàng cuối căn giữa. */
    private fun checkEvenGaps(f: ShortcutGridFit.Fit, tag: String) {
        val s = f.iconPx
        for (row in 0 until f.rows) {
            val first = row * f.cols
            val idx = (first until first + f.inRow(row)).toList()
            idx.zipWithNext { a, b -> f.left(b) - (f.left(a) + s) }
                .forEach { gap -> assertTrue(abs(gap - f.gapXPx) <= 1f, "khe ngang $gap ≠ ${f.gapXPx} hàng $row — $tag") }
            val leftEdge = f.left(idx.first())
            val rightEdge = f.widthPx - (f.left(idx.last()) + s)
            assertTrue(abs(leftEdge - rightEdge) <= 1, "hàng $row không căn giữa: trái $leftEdge phải $rightEdge — $tag")
            if (f.inRow(row) == f.cols) assertTrue(abs(leftEdge - f.gapXPx) <= 1f, "hàng đủ: khe mép = khe giữa — $tag")
        }
        val tops = (0 until f.rows).map { f.top(it * f.cols) }
        tops.zipWithNext { a, b -> b - (a + s) }.forEach { gap -> assertTrue(abs(gap - f.gapYPx) <= 1f, "khe dọc — $tag") }
        assertTrue(abs(tops.first() - f.gapYPx) <= 1f, "khe mép trên ≠ khe giữa — $tag")
        assertTrue(abs(f.heightPx - (tops.last() + s) - f.gapYPx) <= 1f, "khe mép dưới ≠ khe giữa — $tag")
        (0 until f.count).forEach { i -> assertEquals(tops[i / f.cols], f.top(i), "icon $i lệch hàng — $tag") }
    }

    // ── tính chất trên toàn lưới ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `n 1 den 30 x moi ti le khung - khong tran, to nhat, khe deu, kep dung, o cham du`() {
        var checked = 0
        for (n in 1..30) for ((w, h) in boxes) if (check(n, w, h)) checked++
        assertTrue(checked > 300, "phải có đủ ca KHỚP để bài có nghĩa (được $checked)")
    }

    @Test
    fun `quet day khung nho den to - tinh chat giu o moi co`() {
        // Bước lẻ để rơi vào cả các ranh giới làm tròn.
        var checked = 0
        for (n in listOf(1, 2, 3, 5, 7, 8, 9, 12, 20)) for (w in 30..1900 step 97) for (h in 30..1000 step 61) {
            if (check(n, w, h)) checked++
        }
        assertTrue(checked > 500, "được $checked ca KHỚP")
    }

    // ── ca ảnh owner 06/10 (khung đo máy ảo cùng mật độ màn xe) ──────────────────────────────────────────────

    /**
     * CA ẢNH: ô dọc hẹp 258×956 (thanh nút TRÁI; khung ≤ 258 px là nơi phép 2.91 ra MỘT cột với 8 app — vét cạn ở
     * spec §6), 8 app. 2.91: một cột icon 89 px, lề 84,5 px mỗi bên. 2.92: hai cột icon 111 px, lề 12 px.
     */
    @Test
    fun `ca anh - o doc hep 8 app, icon to len, le hai ben con 12 px`() {
        val f = fit(8, 258, 956)
        assertEquals(2 to 4, f.cols to f.rows)
        assertEquals(111, f.iconPx, "2.91: 89 px")
        assertEquals(12f, f.gapXPx, 1e-3f, "lề hai bên — 2.91: 84,5 px")
        assertEquals(ShortcutGridFit.Scroll.NONE, f.scroll)
    }

    /**
     * Máy ảo 06/10: thanh nút TRÁI 150 %, lưới `0,0,2,6` ⇒ khung 262×956 [ĐO uiautomator]. 2.89/2.91 với 7 app: MỘT cột
     * icon 101 px, lề 80,5 px (đúng dáng ảnh owner). Bản 2.92 đầu (icon to nhất): vẫn một cột 122 px, lề 70 px. OQ2 (điều
     * phối 06/10, *"side margins too big"*): hai cột 113 px (≥ 90 % của 122), lề 12 px, hàng cuối một icon căn giữa.
     */
    @Test
    fun `ca may ao - o 262x956, 7 8 9 app deu hai cot le 12`() {
        val seven = fit(7, 262, 956)
        assertEquals(2 to 4, seven.cols to seven.rows)
        assertEquals(113, seven.iconPx, "2.91: 101 px · 2.92 trước OQ2: 122 px một cột")
        assertEquals(12f, seven.gapXPx, 1e-3f, "lề hai bên — 2.91: 80,5 px · trước OQ2: 70 px")
        assertEquals(1, seven.inRow(3), "hàng cuối một icon")
        assertTrue(abs(seven.left(6) + seven.iconPx / 2 - 131) <= 1, "hàng cuối căn giữa: $seven")
        val eight = fit(8, 262, 956)
        assertEquals(listOf(2, 4, 113), listOf(eight.cols, eight.rows, eight.iconPx), "2.91: 90 px")
        val nine = fit(9, 262, 956)
        assertEquals(listOf(2, 5, 113), listOf(nine.cols, nine.rows, nine.iconPx))
        listOf(seven, eight, nine).forEach { assertEquals(12f, it.gapXPx, 1e-3f) }
    }

    /** OQ2 cả khi icon đã chạm trần: ô đứng 400×956, 4 app — 2.91/2.92 trước OQ2 một cột (180 px, lề 110 px). */
    @Test
    fun `lap be ngang ca khi icon cham tran - 4 app o dung 400x956`() {
        val f = fit(4, 400, 956)
        assertEquals(listOf(2, 2, maxPx), listOf(f.cols, f.rows, f.iconPx))
        assertEquals((400 - 2 * maxPx) / 3f, f.gapXPx, 1e-3f, "lề 13,3 px thay vì 110 px")
        // Ô hẹp 301×804 (thanh nút DƯỚI), 5 app: một cột 146 px lề 77,5 ⇒ hai cột 132 px lề 12,3.
        val five = fit(5, 301, 804)
        assertEquals(listOf(2, 3, 132), listOf(five.cols, five.rows, five.iconPx))
    }

    /**
     * Ranh 10 %: chỉ đổi khi icon nhỏ đi KHÔNG quá 10 %. 5 app ô 262×956: một cột 176 px; hai cột 113 px (−36 %) ⇒ giữ một
     * cột. 6 app: 145 px; hai cột 113 px (−22 %) ⇒ giữ một cột.
     */
    @Test
    fun `lap be ngang chi doi khi icon nho di khong qua 10 phan tram`() {
        assertEquals(listOf(1, 5, 176), fit(5, 262, 956).let { listOf(it.cols, it.rows, it.iconPx) })
        assertEquals(listOf(1, 6, 145), fit(6, 262, 956).let { listOf(it.cols, it.rows, it.iconPx) })
        // Đúng ranh: icon lớn nhất M ⇒ hai cột ở đúng 0,9·M còn được đổi, ở 0,9·M − 1 thì không (khung dựng ở [ranh]).
        val (eq, below) = ranh()
        assertEquals(2, fit(eq.first, eq.second.first, eq.second.second).cols, "đúng 90 % ⇒ đổi: $eq")
        assertEquals(1, fit(below.first, below.second.first, below.second.second).cols, "dưới 90 % ⇒ giữ: $below")
    }

    /**
     * Tìm hai khung đứng mà MỘT cột cho icon lớn nhất M, HAI cột cho lề hai bên nhỏ hơn với icon (a) đúng 0,9·M, (b)
     * 0,9·M − 1; mọi số cột khác nhỏ hơn hai cột. Vét cạn độc lập ([bruteLargest]) — bài không dựa vào hàm đang thử.
     */
    private fun ranh(): Pair<Pair<Int, Pair<Int, Int>>, Pair<Int, Pair<Int, Int>>> {
        var eq: Pair<Int, Pair<Int, Int>>? = null
        var below: Pair<Int, Pair<Int, Int>>? = null
        for (n in 3..9) for (w in 150..420) for (h in w..1200 step 7) {
            val one = minOf(bruteLargest(1, n, w, h), maxPx)
            val two = minOf(bruteLargest(2, (n + 1) / 2, w, h), maxPx)
            if (one < minPx || two < minPx || two >= one) continue
            val isEq = eq == null && two * 10 == one * 9
            val isBelow = below == null && (two + 1) * 10 == one * 9
            if (!isEq && !isBelow) continue
            if ((w - 2 * two) / 3.0 >= (w - one) / 2.0 - 1.0) continue   // lề lệch ≤ 1 px là hoà (expected)
            if ((3..n).any { c -> minOf(bruteLargest(c, (n + c - 1) / c, w, h), maxPx) >= two }) continue
            if (isEq) eq = n to (w to h) else below = n to (w to h)
            if (eq != null && below != null) return eq to below
        }
        error("không dựng được khung ranh: eq=$eq below=$below")
    }

    /**
     * Khung NẰM giữ thứ tự cũ (OQ2 chỉ áp khung đứng): đọc "trục ngang" thành chiều cao ở khung nằm làm ô 4 (929×395) với
     * 4 app thành 2×2 lề hai bên 190 px (nay 4×1 lề 41,8) và làm icon TO RA khi thêm app (vét cạn 06/10, spec §4.3a).
     */
    @Test
    fun `khung nam giu nguyen - dai rong va o 4`() {
        assertEquals(listOf(8, 1, 99), fit(8, 1872, 123).let { listOf(it.cols, it.rows, it.iconPx) })
        assertEquals(listOf(4, 1, 99), fit(4, 1872, 123).let { listOf(it.cols, it.rows, it.iconPx) })
        assertEquals(listOf(4, 2, 179), fit(8, 929, 395).let { listOf(it.cols, it.rows, it.iconPx) })
        val four = fit(4, 929, 395)
        assertEquals(listOf(4, 1, maxPx), listOf(four.cols, four.rows, four.iconPx))
        assertEquals((929 - 4 * maxPx) / 5f, four.gapXPx, 1e-3f, "lề hai bên 41,8 px — không phải 190")
        assertEquals(listOf(3, 1, maxPx), fit(3, 929, 395).let { listOf(it.cols, it.rows, it.iconPx) })
    }

    /**
     * Senior review 2.92 Pass 3 [P3] — lề lệch DƯỚI 1 px là phần lẻ làm tròn icon về px nguyên, không phải "lề nhỏ hơn":
     * ô đứng 842×920 với 101 app: 10 cột × 11 hàng icon 70 px lề 12,91 px; 11 cột icon 63 px (đúng 90 %) lề 12,42 px. Bản
     * đầu OQ2 so lề tới 1e-6 ⇒ chọn 11 cột — icon nhỏ đi 10 % để đổi 0,49 px lề [ĐO bài dò vét cạn: 11 859 ca kiểu này ở
     * khung tới 1300 px × n tới 256; 0 ca ở khung ≤ 960 px rộng với ≤ 60 app]. Nay hoà ⇒ icon to hơn thắng.
     */
    @Test
    fun `le lech duoi 1 px la hoa - khong doi 10 phan tram icon lay phan le cua le`() {
        val f = fit(101, 842, 920)
        assertEquals(listOf(10, 11, 70), listOf(f.cols, f.rows, f.iconPx), "bản đầu OQ2: 11 cột 63 px")
        assertEquals(ShortcutGridFit.Scroll.NONE, f.scroll)
        assertEquals((842 - 10 * 70) / 11f, f.gapXPx, 1e-3f)
    }

    /** Các khung khác đo trên máy ảo 06/10 (cùng bố cục với ảnh trước/sau ở `docs/diagnostics/shortcut-widget-2026-10-06/`). */
    @Test
    fun `khung may ao khac - icon to hon 2_91`() {
        // (khung, n) → (cột, hàng, icon 2.92, icon 2.91)
        val cases = listOf(
            Triple(301 to 804, 8, listOf(2, 4, 132, 103)),     // ô hẹp 2×6, thanh nút DƯỚI
            Triple(1872 to 123, 8, listOf(8, 1, 99, 76)),      // dải rộng 12×1
            Triple(929 to 395, 8, listOf(4, 2, 179, 136)),     // bố cục 4 ô
            Triple(277 to 252, 8, listOf(3, 3, 68, 60)),       // ô nén trong ô 3 widget
        )
        cases.forEach { (box, n, want) ->
            val f = fit(n, box.first, box.second)
            assertEquals(listOf(want[0], want[1], want[2]), listOf(f.cols, f.rows, f.iconPx), "khung $box")
            assertTrue(f.iconPx > want[3], "khung $box: 2.92 phải to hơn 2.91 (${want[3]} px)")
        }
    }

    @Test
    fun `nhieu app van khop khi icon con tren san - 12, 20, 30 o 262x956`() {
        val twelve = fit(12, 262, 956)
        assertEquals(listOf(2, 6, 113), listOf(twelve.cols, twelve.rows, twelve.iconPx))
        val twenty = fit(20, 262, 956)
        assertEquals(listOf(2, 10, 82), listOf(twenty.cols, twenty.rows, twenty.iconPx))
        val thirty = fit(30, 262, 956)
        assertEquals(listOf(3, 10, 71), listOf(thirty.cols, thirty.rows, thirty.iconPx))
        listOf(twelve, twenty, thirty).forEach { assertEquals(ShortcutGridFit.Scroll.NONE, it.scroll) }
    }

    // ── ca cụ thể giữ từ R-SI1 ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nhieu thi be lai, it thi to ra - cung mot khung`() {
        val sizes = (1..8).map { fit(it, 600, 300).iconPx }
        sizes.zipWithNext { a, b -> assertTrue(b <= a, "thêm app mà icon to ra: $sizes") }
        assertTrue(sizes.first() > sizes.last(), "1 app phải to hơn 8 app: $sizes")
    }

    @Test
    fun `8 app khung 2-1 la hai hang bon`() {
        val f = fit(8, 600, 300)
        assertEquals(4 to 2, f.cols to f.rows)
    }

    @Test
    fun `3 app khung vuong la 2 cong 1, hang cuoi can giua cung khe`() {
        val f = fit(3, 400, 400)
        assertEquals(2 to 2, f.cols to f.rows)
        assertEquals(1, f.inRow(1))
        assertTrue(abs(f.left(2) + f.iconPx / 2 - 200) <= 1, "hàng cuối không căn giữa: $f")
    }

    @Test
    fun `5 app khung vuong - hoa co thi it hang hon, 3 cong 2`() {
        val f = fit(5, 400, 400)
        assertEquals(3 to 2, f.cols to f.rows)
        assertEquals(listOf(3, 2), (0 until f.rows).map { f.inRow(it) })
    }

    @Test
    fun `1 app khung rat to - kep tran, nam giua`() {
        val f = fit(1, 4000, 2000)
        assertEquals(maxPx, f.iconPx)
        assertEquals((4000 - maxPx) / 2f, f.gapXPx)
        assertEquals((2000 - maxPx) / 2f, f.gapYPx)
        assertEquals(1910, f.left(0))
        assertEquals(910, f.top(0))
    }

    @Test
    fun `mot cot bi be ngang chan - icon = khung tru hai khe`() {
        // Dải dọc rất hẹp: bề ngang chặn icon ⇒ icon = khung − 2g (owner: "icon ≈ thanh, lề nhỏ").
        val f = fit(3, 150, 900)
        assertEquals(1 to 3, f.cols to f.rows)
        assertEquals(150 - 2 * g, f.iconPx)
        assertEquals(g.toFloat(), f.gapXPx, 1e-3f)
    }

    @Test
    fun `khong co app hoac khung chua do - khong no`() {
        val empty = fit(0, 400, 400)
        assertEquals(0 to 0, empty.cols to empty.rows)
        val unmeasured = fit(3, 0, 0)
        assertEquals(minPx, unmeasured.iconPx)
        assertEquals(0f, unmeasured.gapXPx)
        assertEquals(ShortcutGridFit.Scroll.NONE, unmeasured.scroll)
    }
}
