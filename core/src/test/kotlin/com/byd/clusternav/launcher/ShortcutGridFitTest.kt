package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.floor

/**
 * R-SI1 (spec `docs/specs/kachi-287-look-and-keys.html` §4.4) — lưới icon của widget `w_apps` tự co giãn theo khung.
 *
 * Owner 03/10: *"nhiều thì bé lại, to thì giãn ra cho cân đối trong widget là đẹp, đồng size, khoảng cách đều nhau"*.
 * Bài này khoá từng vế bằng TÍNH CHẤT trên lưới n = 1…8 × nhiều tỉ lệ khung, với cỡ lớn nhất tính lại bằng VÉT CẠN
 * (không gọi hàm của [ShortcutGridFit]) để bài không tự xác nhận chính phép chia của nó:
 *  - không tràn khung; icon to NHẤT có thể (thêm 1 px là tràn, không số cột nào cho icon to hơn);
 *  - mọi icon cùng cỡ; khe đều theo mỗi trục (khe giữa = khe tới mép, ± 1 px làm tròn); hàng cuối thiếu căn giữa;
 *  - kẹp 28/120 dp được tôn trọng (ở đây đổi sẵn ra px ở mật độ 1,5 như đầu xe: 42/180 px).
 */
class ShortcutGridFitTest {

    private val ratio = ShortcutGridFit.GAP_RATIO
    private val minPx = 42      // 28 dp × 1,5
    private val maxPx = 180     // 120 dp × 1,5

    /** Khung thử: vuông · 2:1 · 1:2 · 3:1 · 1:3 · lẻ · rất nhỏ · rất to. */
    private val boxes = listOf(
        400 to 400, 600 to 300, 300 to 600, 900 to 300, 300 to 900, 333 to 217, 1001 to 97,
        40 to 30, 10 to 10, 4000 to 4000, 5000 to 2500, 640 to 360, 360 to 240,
    )

    // ── vét cạn độc lập ──────────────────────────────────────────────────────────────────────────────────────

    private fun fitsAt(c: Int, r: Int, s: Int, w: Int, h: Int) =
        c * s + (c + 1) * ratio * s <= w + 1e-6 && r * s + (r + 1) * ratio * s <= h + 1e-6

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

    private fun check(n: Int, w: Int, h: Int) {
        val f = ShortcutGridFit.fit(n, w, h, ratio, minPx, maxPx)
        val tag = "n=$n khung ${w}x$h → $f"
        assertEquals(n, f.count, tag)
        assertEquals((n + f.cols - 1) / f.cols, f.rows, "số hàng = ⌈n/c⌉ — $tag")
        assertTrue(f.cols in 1..n, tag)

        // Chọn số cột: cỡ CHƯA kẹp lớn nhất; hoà ⇒ ít ô trống; rồi ít hàng.
        val all = candidates(n, w, h)
        val bestRaw = all.maxOf { it.s }
        val chosen = all.first { it.c == f.cols }
        assertEquals(bestRaw, chosen.s, "có số cột khác cho icon to hơn — $tag")
        val ties = all.filter { it.s == bestRaw }
        val pref = ties.minWith(compareBy<Cand>({ it.empty }, { it.r }))
        assertEquals(pref.c, f.cols, "hoà cỡ ⇒ ít ô trống rồi ít hàng — $tag")

        // Kẹp.
        assertEquals(bestRaw.coerceIn(minPx, maxPx), f.iconPx, "kẹp [min, max] — $tag")
        assertTrue(f.iconPx in minPx..maxPx, tag)
        if (bestRaw in minPx until maxPx) {
            assertTrue(!fitsAt(f.cols, f.rows, f.iconPx + 1, w, h), "thêm 1 px vẫn vừa ⇒ chưa to nhất — $tag")
        }

        val s = f.iconPx
        val rects = (0 until n).map { i -> intArrayOf(f.left(i), f.top(i), f.left(i) + s, f.top(i) + s) }
        val fitsX = f.cols * s <= w
        val fitsY = f.rows * s <= h
        // Khe = phần dư chia đều mỗi trục, ≥ 0 (= 0 chỉ khi ngay cả khe 0 cũng không vừa).
        assertEquals(if (fitsX) (w - f.cols * s) / (f.cols + 1f) else 0f, f.gapXPx, 1e-3f, "khe ngang — $tag")
        assertEquals(if (fitsY) (h - f.rows * s) / (f.rows + 1f) else 0f, f.gapYPx, 1e-3f, "khe dọc — $tag")
        if (bestRaw >= minPx) {
            // Icon chưa chạm sàn ⇒ không tràn + khe ≥ mức sàn trên cả hai trục.
            assertTrue(fitsX && fitsY, "icon tràn khung — $tag")
            assertTrue(f.gapXPx >= ratio * s - 1e-3 && f.gapYPx >= ratio * s - 1e-3, "khe dưới mức sàn — $tag")
        } else {
            // Khung nhỏ hơn mức sàn: icon giữ cỡ tối thiểu ⇒ trục bó có khe DƯỚI mức sàn (có thể 0 và tràn đều hai bên).
            assertTrue(minOf(f.gapXPx, f.gapYPx) < ratio * s, "kẹp sàn mà khe vẫn đủ ⇒ lẽ ra icon to hơn — $tag")
        }
        if (fitsX && fitsY) rects.forEach { r ->
            assertTrue(r[0] >= 0 && r[1] >= 0 && r[2] <= w && r[3] <= h, "icon tràn khung ${r.toList()} — $tag")
        }

        // Không chồng nhau.
        for (a in rects.indices) for (b in a + 1 until rects.size) {
            val x = rects[a]; val y = rects[b]
            val overlap = x[0] < y[2] && y[0] < x[2] && x[1] < y[3] && y[1] < x[3]
            assertTrue(!overlap, "icon $a và $b chồng nhau — $tag")
        }

        if (fitsX && fitsY) checkEvenGaps(f, tag)
    }

    /** Khe đều theo mỗi trục: mép trái, giữa các icon, mép phải lệch nhau ≤ 1 px (làm tròn); hàng cuối căn giữa. */
    private fun checkEvenGaps(f: ShortcutGridFit.Fit, tag: String) {
        val s = f.iconPx
        for (row in 0 until f.rows) {
            val first = row * f.cols
            val idx = (first until first + f.inRow(row)).toList()
            val inner = idx.zipWithNext { a, b -> f.left(b) - (f.left(a) + s) }
            val leftEdge = f.left(idx.first())
            val rightEdge = f.widthPx - (f.left(idx.last()) + s)
            inner.forEach { g -> assertTrue(abs(g - f.gapXPx) <= 1f, "khe ngang $g ≠ ${f.gapXPx} hàng $row — $tag") }
            assertTrue(abs(leftEdge - rightEdge) <= 1, "hàng $row không căn giữa: trái $leftEdge phải $rightEdge — $tag")
            if (f.inRow(row) == f.cols) {
                assertTrue(abs(leftEdge - f.gapXPx) <= 1f, "hàng đủ: khe mép trái phải = khe giữa — $tag")
            }
        }
        val tops = (0 until f.rows).map { f.top(it * f.cols) }
        tops.zipWithNext { a, b -> b - (a + s) }.forEach { g ->
            assertTrue(abs(g - f.gapYPx) <= 1f, "khe dọc $g ≠ ${f.gapYPx} — $tag")
        }
        assertTrue(abs(tops.first() - f.gapYPx) <= 1f, "khe mép trên ≠ khe giữa — $tag")
        assertTrue(abs(f.heightPx - (tops.last() + s) - f.gapYPx) <= 1f, "khe mép dưới ≠ khe giữa — $tag")
        // Cùng hàng ⇒ cùng mép trên (đồng cỡ, thẳng hàng).
        (0 until f.count).forEach { i -> assertEquals(tops[i / f.cols], f.top(i), "icon $i lệch hàng — $tag") }
    }

    // ── tính chất trên toàn lưới ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `n 1 den 8 x moi ti le khung - khong tran, to nhat, khe deu, kep dung`() {
        var checked = 0
        for (n in 1..8) for ((w, h) in boxes) { check(n, w, h); checked++ }
        assertEquals(8 * boxes.size, checked)
    }

    @Test
    fun `quet day khung nho den to - tinh chat giu o moi co`() {
        // Bước lẻ để rơi vào cả các ranh giới làm tròn (0,3 không đúng ở cơ số 2).
        for (n in 1..8) for (w in 30..1300 step 97) for (h in 30..900 step 61) check(n, w, h)
    }

    // ── ca cụ thể (đọc được bằng mắt) ────────────────────────────────────────────────────────────────────────

    @Test
    fun `nhieu thi be lai, it thi to ra - cung mot khung`() {
        val sizes = (1..8).map { ShortcutGridFit.fit(it, 600, 300, ratio, minPx, maxPx).iconPx }
        sizes.zipWithNext { a, b -> assertTrue(b <= a, "thêm app mà icon to ra: $sizes") }
        assertTrue(sizes.first() > sizes.last(), "1 app phải to hơn 8 app: $sizes")
    }

    @Test
    fun `8 app khung 2-1 la hai hang bon`() {
        val f = ShortcutGridFit.fit(8, 600, 300, ratio, minPx, maxPx)
        assertEquals(4 to 2, f.cols to f.rows)
    }

    @Test
    fun `3 app khung vuong la 2 cong 1, hang cuoi can giua cung khe`() {
        val f = ShortcutGridFit.fit(3, 400, 400, ratio, minPx, maxPx)
        assertEquals(2 to 2, f.cols to f.rows)
        assertEquals(1, f.inRow(1))
        // Icon lẻ ở giữa: tâm trùng tâm khung (± 1 px).
        assertTrue(abs(f.left(2) + f.iconPx / 2 - 200) <= 1, "hàng cuối không căn giữa: $f")
    }

    @Test
    fun `5 app khung vuong - hoa co thi it hang hon, 3 cong 2`() {
        val f = ShortcutGridFit.fit(5, 400, 400, ratio, minPx, maxPx)
        assertEquals(3 to 2, f.cols to f.rows)
        assertEquals(listOf(3, 2), (0 until f.rows).map { f.inRow(it) })
    }

    @Test
    fun `1 app khung rat to - kep tran, nam giua`() {
        val f = ShortcutGridFit.fit(1, 4000, 2000, ratio, minPx, maxPx)
        assertEquals(maxPx, f.iconPx)
        assertEquals((4000 - maxPx) / 2f, f.gapXPx)
        assertEquals((2000 - maxPx) / 2f, f.gapYPx)
        assertEquals(1910, f.left(0))
        assertEquals(910, f.top(0))
    }

    @Test
    fun `khung rat nho - icon giu co toi thieu, khe 0, khoi can giua`() {
        val f = ShortcutGridFit.fit(4, 40, 30, ratio, minPx, maxPx)
        assertEquals(minPx, f.iconPx)
        assertEquals(0f, f.gapXPx)
        assertEquals(0f, f.gapYPx)
        // Khối tràn đều hai bên (tâm khối = tâm khung).
        val l = (0 until 4).minOf { f.left(it) }
        val r = (0 until 4).maxOf { f.left(it) } + f.iconPx
        assertTrue(abs((l + r) - 40) <= 1, "khối tràn không căn giữa: $l..$r")
    }

    @Test
    fun `khong co app hoac khung chua do - khong no`() {
        val empty = ShortcutGridFit.fit(0, 400, 400, ratio, minPx, maxPx)
        assertEquals(0 to 0, empty.cols to empty.rows)
        val unmeasured = ShortcutGridFit.fit(3, 0, 0, ratio, minPx, maxPx)
        assertEquals(minPx, unmeasured.iconPx)
        assertEquals(0f, unmeasured.gapXPx)
    }

    @Test
    fun `ti le khe nam trong khoang can doi da chot`() {
        assertTrue(ShortcutGridFit.GAP_RATIO in 0.25..0.35, "spec §4.4: khe ≈ 0,25–0,35 × icon")
        // Bước lưới ≥ 48 dp từ icon 37 dp (vùng chạm nới nửa khe mỗi bên — KDoc GAP_RATIO).
        assertTrue(floor(37 * (1 + ShortcutGridFit.GAP_RATIO)) >= 48)
    }
}
