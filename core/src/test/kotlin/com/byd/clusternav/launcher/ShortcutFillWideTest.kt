package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 `SHORTCUT-FILL-WIDE` (spec `docs/specs/kachi-293-widget.html` R-W5 — luật generic do bên làm chọn, kế hoạch 2.93 §4):
 * luật lấp bề ngang (2.92 OQ2) áp khung ĐỨNG; khung NẰM giữ thứ tự R1 (icon lớn nhất, hoà ⇒ ít ô trống rồi ít hàng).
 *
 * Vì sao giữ — ĐO bằng mô hình độc lập (bài dưới tự viết lại ba luật, không gọi [GridFit]), lưới khung nằm 60–1 960 ×
 * 60–1 100 px (bước 37/23) × n 1–40 [ĐO mô hình 06/10, script ngoài repo — cùng số]:
 *  - luật ĐỐI XỨNG (khung nằm lấp lề TRỤC NGẮN = trên/dưới): 800 ca icon TO RA khi thêm app (334 khung), vd 578×543: 3 app
 *    165 px → 4 app 180 px — phá tính chất đơn điệu đã khoá (`ShortcutGridScrollTest`). Gốc: thứ tự R1 xếp theo HÀNG
 *    (FULL_FIRST) ⇒ ứng viên "nhiều hàng" không có ở khung nằm như "nhiều cột" ở khung đứng;
 *  - luật NGHĨA ĐEN ("lề hai bên" mọi khung): đơn điệu giữ, nhưng đổi 44 % ca khung nằm và sinh 12 671 bố cục hàng cuối lẻ
 *    loi mới (vd 1558×668, 9 app: 5+4 → 8+1); ba biến thể chặn hàng lẻ loi đều lại phá đơn điệu (≥ 879 ca) hoặc đổi ca
 *    khung đứng đã chốt OQ2.
 * ⇒ Luật generic = ĐO hình khung: lấp bề ngang chỉ khi trục DÀI là trục đứng (đúng ca owner phàn nàn: cột hẹp, lề hai bên
 * to). Owner muốn khác thì đổi ở [GridFit] một chỗ + bài này đỏ.
 */
class ShortcutFillWideTest {

    private val g = 12; private val lo = 60; private val hi = 180

    private data class C(val c: Int, val r: Int, val s: Int, val cap: Int, val empty: Int)

    private fun cands(n: Int, w: Int, h: Int): List<C> = (1..n).map { c ->
        val r = (n + c - 1) / c
        var s = 0
        while (c * (s + 1) + (c + 1) * g <= w && r * (s + 1) + (r + 1) * g <= h) s++
        C(c, r, s, minOf(s, hi), c * r - n)
    }

    private val r1 = compareBy<C>({ -it.cap }, { -it.s }, { it.empty }, { it.r })

    /** Icon của một luật trên khung nằm: "R1" · "SYM" (lấp lề trên/dưới) · "LIT" (lấp lề hai bên); `lo` = chế độ cuộn. */
    private fun icon(rule: String, n: Int, w: Int, h: Int): Pair<Int, C?> {
        val legible = cands(n, w, h).filter { it.s >= lo }
        if (legible.isEmpty()) return lo to null
        val best = legible.minWith(r1)
        if (rule == "R1") return best.cap to best
        val pool = legible.filter { it.cap >= 0.9 * best.cap - 1e-6 }
        val margin: (C) -> Double = if (rule == "SYM") { x -> (h - x.r * x.cap) / (x.r + 1.0) } else { x -> (w - x.c * x.cap) / (x.c + 1.0) }
        val least = pool.minOf(margin)
        val pick = pool.filter { margin(it) <= least + 1.0 + 1e-6 }.minWith(r1)
        return pick.cap to pick
    }

    private val landscape = (60 until 1960 step 37).flatMap { w -> (60 until 1100 step 23).filter { it < w }.map { w to it } }

    @Test
    fun `khung nam - ShortcutGridFit dung thu tu R1, khong lap`() {
        var checked = 0
        for ((w, h) in landscape) for (n in 1..40) {
            val (cap, best) = icon("R1", n, w, h)
            val f = ShortcutGridFit.fit(n, w, h, g, lo, hi)
            if (best == null) continue
            assertEquals(best.c, f.cols, "n=$n ${w}x$h")
            assertEquals(cap.coerceIn(lo, hi), f.iconPx, "n=$n ${w}x$h")
            checked++
        }
        assertTrue(checked > 20_000, "đủ ca để bài có nghĩa ($checked)")
    }

    @Test
    fun `ly do - luat doi xung pha don dieu, luat nghia den sinh hang le loi`() {
        // Đối xứng: icon TO RA khi thêm app (ca cụ thể + đếm trên lưới).
        assertEquals(165, icon("SYM", 3, 578, 543).first)
        assertEquals(180, icon("SYM", 4, 578, 543).first)
        var viol = 0
        for ((w, h) in landscape) { var prev = Int.MAX_VALUE; for (n in 1..40) { val s = icon("SYM", n, w, h).first; if (s > prev) viol++; prev = s } }
        assertTrue(viol >= 100, "luật đối xứng phải phá đơn điệu ở nhiều ca (đo được $viol)")
        // Nghĩa đen: 1558×668 với 9 app ⇒ 8 + 1 (R1: 5 + 4).
        val lit = icon("LIT", 9, 1558, 668).second!!
        assertEquals(listOf(8, 2), listOf(lit.c, lit.r))
        assertEquals(1, 9 - lit.c, "hàng cuối một icon lẻ loi")
        assertEquals(5, icon("R1", 9, 1558, 668).second!!.c)
        val now = ShortcutGridFit.fit(9, 1558, 668, g, lo, hi)
        assertEquals(listOf(5, 2), listOf(now.cols, now.rows), "Kachi giữ R1 ở khung nằm")
    }
}
