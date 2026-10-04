package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.GridFit.Form
import com.byd.clusternav.launcher.GridFit.Shape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min

/**
 * L5 WIDGET-FIT-ALL — phép khớp lưới chung [GridFit] cho mọi nội dung widget.
 *
 * Owner 03/10 (2.86, ảnh): khung dẹt một hàng lưới chứa 6 nút (4 kính + "Đóng/Mở hết kính") xếp **2×3**, nhãn bị cắt
 * nửa dưới, ô mất hẳn nhãn. Mục tiêu: *"nhiều thì bé lại, to thì giãn ra cho cân đối trong widget là đẹp, đồng size,
 * khoảng cách đều nhau"*.
 *
 * Bài khoá bằng TÍNH CHẤT trên lưới tham số tất định + một ORACLE ĐỘC LẬP: cỡ `k` của mỗi ứng viên tìm bằng QUÉT từng
 * bậc (không gọi công thức đóng của [GridFit]) rồi xếp hạng theo đúng thứ tự từ điển đã khai ở KDoc lớp.
 *
 * Hộp tự nhiên trong bài là [SUY] — ô `TileSize.DOCK` ở mật độ 1,5 (icon 20dp = 30px, đệm 4dp, nhãn 11.5sp ≈ 23px/dòng
 * Roboto): dọc-2-dòng ≈ 82×94, dọc-1-dòng ≈ 142×71, ngang ≈ 178×42, chỉ-icon 42×42. Ở :app hộp được ĐO thật bằng
 * `measure()` nên đúng cho mọi phông/ngôn ngữ; hệ số dòng 1,16 (Thái) / 1,23 (CJK) dùng làm fixture.
 */
class GridFitTest {

    private val q = 1.0 / 32
    private val floorK = 10.0 / 11.5        // sàn 10sp trên nhãn 11.5sp

    /**
     * Hộp fixture. [more] (soát vòng 4, P3: bài quét chưa bao giờ có dạng dự phòng) thêm dạng nhãn DỰ PHÒNG (ngang 2
     * dòng) và ba dạng nhãn NGẮN (J1 — hộp hẹp hơn nhãn đầy, cùng chiều cao) để oracle độc lập phủ cả ba loại nhãn của
     * tầng đọc được (KDoc [GridFit], bước 1–2a) trên mọi n/khung/đích chạm.
     */
    private fun tiles(line: Double = 1.0, icon: Boolean = true, more: Boolean = false): List<Shape> = listOfNotNull(
        Shape(Form.VERTICAL, 82.0, 48.0 + 46.0 * line, floorK, lines = 2),
        Shape(Form.VERTICAL, 142.0, 48.0 + 23.0 * line, floorK, lines = 1),
        Shape(Form.HORIZONTAL, 178.0, 12.0 + maxOf(30.0, 23.0 * line), floorK, lines = 1),
        if (more) Shape(Form.HORIZONTAL, 128.0, 12.0 + maxOf(30.0, 46.0 * line), floorK, lines = 2, reserve = true) else null,
        if (more) Shape(Form.VERTICAL, 64.0, 48.0 + 46.0 * line, floorK, lines = 2, short = true) else null,
        if (more) Shape(Form.VERTICAL, 96.0, 48.0 + 23.0 * line, floorK, lines = 1, short = true) else null,
        if (more) Shape(Form.HORIZONTAL, 132.0, 12.0 + maxOf(30.0, 23.0 * line), floorK, lines = 1, short = true) else null,
        if (icon) Shape(Form.ICON_ONLY, 42.0, 42.0, 0.8) else null,
    )

    /** Lưới widget ở 240dpi: khe 8dp = 12px, chừa 2px, trần 2,0, đích chạm 48dp = 72px. */
    private fun gridSpec(minCell: Int = 72) = GridFit.Spec(
        gapPx = 12, slackPx = 2, maxScale = 2.0, minCellPx = minCell, quantum = q,
        rowSplit = GridFit.RowSplit.BALANCED, placement = GridFit.Placement.FILL,
    )

    // ── oracle độc lập ──────────────────────────────────────────────────────────────────────────────────────

    // ĐỔI GHIM (QA 04/10, làn H1): thứ tự từ điển có thêm (a) tầng nhãn DỰ PHÒNG ([Shape.reserve]) giữa nhãn chính và
    // chỉ-icon, (b) "gần đích chạm" — không ứng viên nào đạt 48dp thì cạnh ngắn của ô dài hơn (theo bậc 1/16 đích chạm)
    // đứng trước cỡ chữ (khung 301×123 từng ra 6×1 ô 36px = 24dp), (c) tầng KHÔNG đọc được: hộp ở sàn còn vừa CAO ô
    // đứng trước (tràn dọc = mất nửa dòng, bệnh ảnh 03/10). Oracle viết lại độc lập ba luật ấy.
    // ĐỔI GHIM (J1, QA2 04/10 — quyết định điều phối): ô ≥ 48dp với nhãn NGẮN đứng trước ô < 48dp với nhãn đầy. Tầng
    // nay là {có nhãn đọc được (chính · dự phòng · ngắn)} › {chỉ-icon đọc được} › {không đọc được}; trong tầng có nhãn,
    // loại nhãn (chính › dự phòng › ngắn) xét SAU đích chạm. Bản H1 xếp dự phòng thành tầng riêng (tầng 1) TRƯỚC đích chạm.
    private data class O(
        val cols: Int, val rows: Int, val counts: List<Int>, val si: Int, val s: Shape, val k: Double,
        val legible: Boolean, val touch: Boolean, val empty: Int, val aspect: Double, val near: Int, val tall: Boolean,
    ) {
        val tier get() = if (!legible) 2 else if (s.fallback) 1 else 0
        val kind get() = if (s.short) 2 else if (s.reserve) 1 else 0
    }

    private fun oracle(n: Int, w: Int, h: Int, shapes: List<Shape>, sp: GridFit.Spec): O {
        val all = mutableListOf<O>()
        for (r in 1..n) {
            val cols = (n + r - 1) / r
            val counts = List(r) { n / r + if (it < n % r) 1 else 0 }
            if (counts.any { it == 0 }) continue
            val cw = (w - (cols + 1) * sp.gapPx) / cols
            val ch = (h - (r + 1) * sp.gapPx) / r
            shapes.forEachIndexed { si, s ->
                var k = 0.0
                while ((k + q) * s.widthPx + sp.slackPx <= cw + 1e-9 && (k + q) * s.heightPx + sp.slackPx <= ch + 1e-9) k += q
                val pw = (w - (cols + 1) * sp.gapPx).toDouble() / cols
                val ph = (h - (r + 1) * sp.gapPx).toDouble() / r
                // Dung sai 1e-6 px như bộ giải (pw là phân số — w/3 không biểu diễn đúng ở cơ số 2).
                val near = if (sp.minCellPx <= 0) 0 else floor((maxOf(0.0, min(pw, ph)) + 1e-6) * 16 / sp.minCellPx).toInt()
                all += O(
                    cols, r, counts, si, s, k, k + 1e-9 >= s.minScale,
                    sp.minCellPx <= 0 || (pw >= sp.minCellPx && ph >= sp.minCellPx), cols * r - n,
                    if (pw > 0 && ph > 0) abs(ln(pw / ph)) else Double.MAX_VALUE,
                    near, s.minScale * s.heightPx + sp.slackPx <= ch + 1e-9,
                )
            }
        }
        // Thứ tự từ điển của KDoc lớp, viết lại độc lập; số thực so với dung sai (k là bội 1/32 nên so đúng, tỉ lệ ô
        // là ln của phân số nên hai bố cục đối xứng có thể lệch một ulp — coi là hoà như bộ giải).
        fun cmp(x: Double, y: Double, tol: Double) = if (abs(x - y) <= tol) 0 else x.compareTo(y)
        return all.sortedWith { a, b ->
            when {
                a.tier != b.tier -> a.tier.compareTo(b.tier)
                a.touch != b.touch -> if (a.touch) -1 else 1
                a.tier == 0 && a.kind != b.kind -> a.kind.compareTo(b.kind)
                !a.touch && a.near != b.near -> b.near.compareTo(a.near)
                a.tall != b.tall -> if (a.tall) -1 else 1
                cmp(min(a.k, sp.maxScale), min(b.k, sp.maxScale), 1e-6) != 0 -> -cmp(min(a.k, sp.maxScale), min(b.k, sp.maxScale), 1e-6)
                a.si != b.si -> a.si.compareTo(b.si)
                cmp(a.k, b.k, 1e-6) != 0 -> -cmp(a.k, b.k, 1e-6)
                a.empty != b.empty -> a.empty.compareTo(b.empty)
                cmp(a.aspect, b.aspect, 1e-9) != 0 -> cmp(a.aspect, b.aspect, 1e-9)
                else -> a.rows.compareTo(b.rows)
            }
        }.first()
    }

    private fun check(n: Int, w: Int, h: Int, shapes: List<Shape>, sp: GridFit.Spec) {
        val f = GridFit.fit(n, w, h, shapes, sp)
        val tag = "n=$n khung ${w}x$h → ${f.cols}x${f.rows} ${f.shape} k=${f.scale} raw=${f.rawScale} legible=${f.legible}"
        val o = oracle(n, w, h, shapes, sp)
        // I5 — đúng ứng viên oracle chọn, đúng cỡ.
        assertEquals(o.cols to o.rows, f.cols to f.rows, "bố cục khác oracle (${o.cols}x${o.rows} ${o.s}) — $tag")
        assertEquals(o.s, f.shape, "dạng khác oracle — $tag")
        assertEquals(o.k, f.rawScale, 1e-9, "cỡ chưa kẹp khác oracle — $tag")
        assertEquals(o.legible, f.legible, tag)
        assertEquals(o.touch, f.touchOk, tag)
        // I3 — phân hàng cân bằng.
        assertEquals(n, f.grid.rowCounts.sum(), tag)
        assertEquals((n + f.cols - 1) / f.cols, f.rows, "r = ⌈n/c⌉ — $tag")
        assertTrue(f.grid.rowCounts.max() - f.grid.rowCounts.min() <= 1, "hàng chênh > 1 — $tag")
        // I4 + I8 — sàn/trần + lượng tử.
        val s = f.shape!!
        assertTrue(f.scale >= s.minScale - 1e-9, "dưới sàn — $tag")
        if (!f.legible) assertEquals(s.minScale, f.scale, 1e-9, "không đọc được ⇒ giữ sàn, KHÔNG bóp chữ — $tag")
        else assertEquals(min(f.rawScale, sp.maxScale), f.scale, 1e-9, tag)
        assertEquals(0.0, (f.rawScale / q) - Math.round(f.rawScale / q), 1e-6, "k không theo bậc lượng tử — $tag")
        // I1 — không tràn khi đọc được.
        if (f.legible) {
            assertTrue(f.scale * s.widthPx + sp.slackPx <= f.cellW + 1e-6, "tràn ngang — $tag cell ${f.cellW}")
            assertTrue(f.scale * s.heightPx + sp.slackPx <= f.cellH + 1e-6, "tràn dọc — $tag cell ${f.cellH}")
        }
        checkGeometry(f, sp, tag)
    }

    /** I2 — ô cùng cỡ, khe đều (± 1px), hàng thiếu căn giữa, không chồng, nằm trong khung. */
    private fun checkGeometry(f: GridFit.Fit, sp: GridFit.Spec, tag: String) {
        val g = f.grid
        if (g.cellW <= 0 || g.cellH <= 0) return
        assertTrue(g.gapX + 1e-3 >= sp.gapPx && g.gapY + 1e-3 >= sp.gapPx, "khe dưới tối thiểu — $tag")
        val rects = (0 until f.count).map { intArrayOf(f.left(it), f.top(it), f.left(it) + g.cellW, f.top(it) + g.cellH) }
        rects.forEach { r -> assertTrue(r[0] >= 0 && r[1] >= 0 && r[2] <= g.widthPx && r[3] <= g.heightPx, "ra ngoài khung ${r.toList()} — $tag") }
        for (a in rects.indices) for (b in a + 1 until rects.size) {
            val x = rects[a]; val y = rects[b]
            assertFalse(x[0] < y[2] && y[0] < x[2] && x[1] < y[3] && y[1] < x[3], "ô $a và $b chồng nhau — $tag")
        }
        var first = 0
        g.rowCounts.forEachIndexed { row, k ->
            val idx = (first until first + k).toList()
            idx.zipWithNext { a, b -> f.left(b) - (f.left(a) + g.cellW) }
                .forEach { gap -> assertTrue(abs(gap - g.gapX) <= 1f, "khe ngang $gap ≠ ${g.gapX} hàng $row — $tag") }
            val l = f.left(idx.first()); val r = g.widthPx - (f.left(idx.last()) + g.cellW)
            assertTrue(abs(l - r) <= 1, "hàng $row không căn giữa ($l vs $r) — $tag")
            if (k == f.cols) assertTrue(abs(l - g.gapX) <= 1f, "hàng đủ: khe mép ≠ khe giữa — $tag")
            idx.forEach { assertEquals(f.top(idx.first()), f.top(it), "lệch hàng — $tag") }
            first += k
        }
        val tops = g.rowCounts.indices.map { row -> f.top(g.rowCounts.take(row).sum()) }
        tops.zipWithNext { a, b -> b - (a + g.cellH) }.forEach { gap -> assertTrue(abs(gap - g.gapY) <= 1f, "khe dọc — $tag") }
        assertTrue(abs(tops.first() - g.gapY) <= 1f, "khe mép trên — $tag")
    }

    // ── tính chất trên lưới tham số ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `luoi tham so - dung oracle, khong tran, khe deu, san va tran dung`() {
        var checked = 0
        for (line in listOf(1.0, 1.16, 1.23, 1.35)) for (icon in listOf(true, false)) for (minCell in listOf(0, 72))
            for (more in listOf(false, true))
                for (n in 1..8) for (w in listOf(180, 301, 458, 615, 929, 1200, 1872)) for (h in listOf(90, 123, 148, 310, 471, 956)) {
                    check(n, w, h, tiles(line, icon, more), gridSpec(minCell)); checked++
                }
        assertEquals(4 * 2 * 2 * 2 * 8 * 7 * 6, checked)
    }

    @Test
    fun `quet day khung - tinh chat giu o moi co`() {
        for (more in listOf(false, true))
            for (n in 1..8) for (w in 150..1900 step 89) for (h in 100..960 step 71) check(n, w, h, tiles(more = more), gridSpec())
    }

    /** Bài quét có thật sự đi qua cả ba loại nhãn (không thì oracle phủ "rỗng nghĩa" — đúng lỗi soát vòng 4 bắt). */
    @Test
    fun `quet day khung - oracle cham ca nhan chinh, du phong, ngan`() {
        val kinds = mutableSetOf<String>()
        for (n in 1..8) for (w in 150..1900 step 89) for (h in 100..960 step 71) {
            val f = GridFit.fit(n, w, h, tiles(more = true), gridSpec())
            if (f.legible) f.shape?.let { kinds += if (it.short) "short" else if (it.reserve) "reserve" else if (it.fallback) "icon" else "primary" }
        }
        assertEquals(setOf("primary", "reserve", "short", "icon"), kinds)
    }

    // ── ca hồi quy dựng từ ảnh 03/10 ────────────────────────────────────────────────────────────────────────

    /**
     * KHOÁ lỗi ảnh owner 03/10: 6 nút trong khung 4×1 lưới (≈ 615×148px ở 1920×1080@240, không thanh dock). Bản 2.86
     * xếp 2×3 dạng dọc ⇒ ô ≈ 56px cao trong khi nút cần ≈ 91px ⇒ nhãn mất nửa dưới. Phép khớp phải ra một hàng 6 ô,
     * nhãn đủ 2 dòng, ô ≥ 48dp, chữ không nhỏ hơn bản cũ.
     */
    @Test
    fun `anh 03-10 - 6 nut khung det khong bao gio 2x3 doc bi cat`() {
        val f = GridFit.fit(6, 615, 148, tiles(), gridSpec())
        assertEquals(6 to 1, f.cols to f.rows, "$f")
        assertEquals(Form.VERTICAL, f.shape!!.form)
        assertEquals(2, f.shape!!.lines, "nhãn giữ chỗ 2 dòng như luật KIỂM TOÁN UX mục 6")
        assertTrue(f.legible && f.touchOk, "$f")
        assertTrue(f.scale >= 1.0, "chữ không nhỏ hơn bản 2.86 — $f")
        // Khung thật chưa đo được [CHƯA BIẾT] ⇒ quét cả dải bề ngang hợp lý của một khung một hàng: không bao giờ ra
        // hai hàng dạng dọc (đúng hình bị cắt), và luôn đọc được + đủ đích chạm.
        for (w in 580..1000) for (h in listOf(119, 123, 148)) {
            val g = GridFit.fit(6, w, h, tiles(), gridSpec())
            assertFalse(g.rows >= 2 && g.shape!!.form == Form.VERTICAL, "khung ${w}x$h ⇒ $g")
            if (h == 148) assertTrue(g.legible && g.touchOk && g.shape!!.fallback.not(), "khung ${w}x$h ⇒ $g")
        }
    }

    @Test
    fun `nhan Thai va CJK cao hon van vua, khong tran`() {
        for (line in listOf(1.16, 1.23)) {
            val f = GridFit.fit(6, 615, 148, tiles(line), gridSpec())
            assertTrue(f.legible, "$line ⇒ $f")
            assertTrue(f.scale * f.shape!!.heightPx + 2 <= f.cellH, "$line ⇒ $f")
        }
    }

    @Test
    fun `QUAD - 1 muc gian toi tran, 2 muc gian toi tran`() {
        // Một mục (ô to BIG ≈ 150×198px): khung QUAD 929×471, không khe — chạm trần 1,5 (to ra, không lọt thỏm).
        val one = GridFit.fit(
            1, 929, 471, listOf(Shape(Form.VERTICAL, 150.0, 198.0, 0.67, lines = 2)),
            GridFit.Spec(slackPx = 2, maxScale = 1.5, quantum = q),
        )
        assertEquals(1.5, one.scale, 1e-9)
        assertEquals(929 to 471, one.cellW to one.cellH, "một mục lấp khung")
        val two = GridFit.fit(2, 929, 471, tiles(), gridSpec())
        assertEquals(2.0, two.scale, 1e-9, "$two")
        assertEquals(2 to 1, two.cols to two.rows, "khung ngang ⇒ hai ô cạnh nhau")
    }

    @Test
    fun `nhieu thi be lai - cung khung, them muc khong to ra`() {
        for ((w, h) in listOf(615 to 148, 929 to 471, 458 to 310, 1872 to 471)) {
            val ks = (1..8).map { GridFit.fit(it, w, h, tiles(icon = false), gridSpec(0)).let { f -> if (f.legible) f.scale else 0.0 } }
            ks.zipWithNext { a, b -> assertTrue(b <= a + 1e-9, "thêm mục mà to ra ở ${w}x$h: $ks") }
        }
    }

    @Test
    fun `to thi gian ra - khung lon hon khong lam chu nho di`() {
        for (n in 1..8) {
            var prev = 0.0
            for (w in 300..1900 step 40) {
                val f = GridFit.fit(n, w, 471, tiles(icon = false), gridSpec(0))
                val k = if (f.legible) f.scale else 0.0
                assertTrue(k + 1e-9 >= prev, "n=$n rộng $w: $k < $prev")
                prev = k
            }
        }
    }

    @Test
    fun `dich cham 48dp - co ung vien dat thi ket qua dat`() {
        // J1: kể cả khi ứng viên đạt chạm chỉ có nhãn NGẮN/dự phòng (`more`) — đích chạm đứng trước loại nhãn.
        for (more in listOf(false, true)) for (n in 1..8) for (w in listOf(301, 458, 615, 929)) for (h in listOf(123, 148, 310)) {
            val f = GridFit.fit(n, w, h, tiles(more = more), gridSpec())
            val o = oracle(n, w, h, tiles(more = more), gridSpec())
            assertEquals(o.touch, f.touchOk)
            if (f.legible && !f.shape!!.fallback && !f.touchOk) {
                // Không có ứng viên NHÃN đọc được nào đạt đích chạm (nếu có, thứ tự từ điển đã chọn nó).
                for (r in 1..n) {
                    val c = (n + r - 1) / r
                    val pw = (w - (c + 1) * 12) / c; val ph = (h - (r + 1) * 12) / r
                    if (pw >= 72 && ph >= 72) tiles(more = more).filterNot { it.fallback }.forEach { s ->
                        val k = floor(min((pw - 2) / s.widthPx, (ph - 2) / s.heightPx) * 32) / 32
                        assertTrue(k < s.minScale, "n=$n ${w}x$h: ${c}x$r ${s.form} đạt chạm + đọc được mà không được chọn")
                    }
                }
            }
        }
    }

    @Test
    fun `chi-icon chi la duong lui - va khong co khi khong duoc phep`() {
        // 8 nút trong khung 2×1 lưới (301×148): không dạng có nhãn nào đọc được ⇒ chỉ-icon (nếu icon phân biệt được).
        val f = GridFit.fit(8, 301, 148, tiles(), gridSpec())
        assertEquals(Form.ICON_ONLY, f.shape!!.form, "$f")
        assertTrue(f.legible)
        val g = GridFit.fit(8, 301, 148, tiles(icon = false), gridSpec())
        assertFalse(g.legible, "không được phép chỉ-icon ⇒ báo KHÔNG đọc được, không bịa")
        assertTrue(g.scale >= floorK - 1e-9, "giữ sàn chữ, không bóp")
        // Có dạng nhãn đọc được ⇒ không bao giờ rơi về chỉ-icon dù icon to hơn.
        for (n in 1..8) for (w in listOf(615, 929, 1872)) for (h in listOf(148, 310, 471)) {
            val r = GridFit.fit(n, w, h, tiles(), gridSpec())
            if (GridFit.fit(n, w, h, tiles(icon = false), gridSpec()).legible) assertFalse(r.shape!!.fallback, "n=$n ${w}x$h ⇒ $r")
        }
    }

    @Test
    fun `xac dinh - cung dau vao cung ket qua`() {
        for (n in 1..8) assertEquals(GridFit.fit(n, 777, 333, tiles(), gridSpec()), GridFit.fit(n, 777, 333, tiles(), gridSpec()))
    }

    @Test
    fun `khong co hop nao - moi o tu ve - chon o to va vuong`() {
        // Hai vòng đo trong khung ngang ⇒ hai ô cạnh nhau (vuông hơn), không chồng dọc.
        val f = GridFit.fit(2, 900, 400, emptyList(), gridSpec(0))
        assertEquals(2 to 1, f.cols to f.rows)
        val g = GridFit.fit(4, 1800, 300, emptyList(), gridSpec(0))
        assertEquals(4 to 1, g.cols to g.rows, "4 vòng trong khung dẹt ⇒ một hàng (2.86 ép 2+2, ô bé)")
    }

    @Test
    fun `suc chua - khop dung voi fit va voi anh 03-10`() {
        for ((w, h) in listOf(615 to 148, 301 to 148, 929 to 471, 458 to 123)) {
            val cap = GridFit.capacity(w, h, tiles(), gridSpec(), 8)
            for (n in 1..8) {
                val f = GridFit.fit(n, w, h, tiles(), gridSpec())
                val ok = f.legible && !f.shape!!.fallback && f.touchOk
                if (n == cap) assertTrue(ok, "capacity=$cap nhưng n=$n không vừa ở ${w}x$h")
                if (n > cap) assertFalse(ok, "capacity=$cap nhưng n=$n vẫn vừa ở ${w}x$h")
            }
        }
        // [SUY] với hộp fixture: khung ảnh 03/10 vừa 7 nút có nhãn (7×1, k = 0,875 ≥ sàn 0,87), 8 thì không.
        assertEquals(7, GridFit.capacity(615, 148, tiles(), gridSpec(), 8))
    }

    @Test
    fun `rowCounts - can bang va du-hang-truoc`() {
        assertEquals(listOf(3, 2, 2), GridFit.rowCounts(7, 3, GridFit.RowSplit.BALANCED))
        assertEquals(listOf(3, 3, 1), GridFit.rowCounts(7, 3, GridFit.RowSplit.FULL_FIRST))
        assertEquals(listOf(5, 4, 4), GridFit.rowCounts(13, 5, GridFit.RowSplit.BALANCED))
        assertEquals(emptyList<Int>(), GridFit.rowCounts(0, 3, GridFit.RowSplit.BALANCED))
    }

    @Test
    fun `khong co muc - khong no`() {
        val f = GridFit.fit(0, 400, 400, tiles(), gridSpec())
        assertEquals(0 to 0, f.cols to f.rows)
        val z = GridFit.fit(3, 0, 0, tiles(), gridSpec())
        assertFalse(z.legible)
        assertEquals(0, z.cellW)
    }
}
