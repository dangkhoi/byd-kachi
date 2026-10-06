package com.byd.clusternav.launcher

import kotlin.math.ceil
import kotlin.math.floor

/**
 * ═══ R-SI1 → 2.92 — LƯỚI LỐI TẮT theo khung THẬT của widget `w_apps` (thuần, `:core`, đơn vị px) ═══════════════════
 *
 * Spec `docs/specs/kachi-292-shortcut-widget.html` (thay §4.4 của `kachi-287-look-and-keys.html`). Owner 03/10: *"nhiều
 * thì bé lại, to thì giãn ra cho cân đối trong widget là đẹp, đồng size, khoảng cách đều nhau"*; owner 06/10 (ảnh xe —
 * ô dọc hẹp, một cột 8 icon, lề hai bên trống lớn): *"icon hơi bé so với thanh, margin 2 bên nhiều quá phí, nên làm
 * margin nhỏ lại … không nên giới hạn 8 app"*.
 *
 * ## Vì sao đổi (2.92) [ĐO mã + máy ảo]
 * R-SI1 (2.87) giữ khe tối thiểu = `0,3 × cỡ icon` giữa hai icon VÀ tới mép. Một cột 8 icon ⇒ 9 khe = 2,7 × icon theo
 * chiều cao ⇒ icon bị chiều cao chặn ở `H / 10,7`, bề ngang thừa thành lề: khung 258×956 px ⇒ icon 89 px, lề 84,5 px
 * mỗi bên — đúng dáng ảnh owner (máy ảo 262×956 + 7 app: 101 px · lề 80,5 px).
 *
 * ## Phép khớp ([fit]) — hai chế độ
 *  1. **KHỚP** (mọi icon vừa khung): [GridFit.fit] với hộp 1×1, khe CỐ ĐỊNH `gapPx` (tầng vẽ: 8 dp = nhịp khối lối tắt
 *     trên thanh nút, khe 52 − icon 44) giữa hai icon VÀ tới mép, sàn = `minIconPx`. Thử mọi số cột `c`, hàng
 *     `r = ⌈n/c⌉`: `s(c) = ⌊min((W − (c+1)g)/c, (H − (r+1)g)/r)⌋`; chọn `s` lớn nhất (CHƯA kẹp trần; hoà ⇒ ít ô trống
 *     rồi ít hàng — giữ thứ tự R-SI1), kẹp `[minIconPx, maxIconPx]`, phần dư chia ĐỀU mỗi trục (khe giữa = khe tới mép;
 *     trục bị chặn ⇒ đúng `g`). Hàng cuối thiếu căn giữa. Ca ảnh 258×956, 8 app ⇒ 2 cột × 4 hàng, icon 111 px, lề 12 px.
 *     **Lấp bề ngang** (OQ2, điều phối 06/10 — owner: *"margin 2 bên nhiều quá phí"*): khung ĐỨNG (`H ≥ W`) ⇒ trong
 *     các cách xếp có icon (đã kẹp) ≥ 90 % icon lớn nhất ([FILL_WIDTH]), lấy cách có lề hai bên NHỎ nhất (lệch ≤ 1 px làm
 *     tròn là hoà ⇒ icon to hơn thắng — không đổi 10 % icon lấy phần lẻ px của lề). Ô 262×956 với
 *     7 app: một cột 122 px lề 70 px ⇒ hai cột 113 px lề 12 px, hàng cuối một icon căn giữa ([GridFit.Spec.fillWidth]).
 *  2. **CUỘN** (không cách xếp nào giữ icon ≥ sàn): trục cuộn = trục DÀI của khung (`H ≥ W` ⇒ dọc). Ngang trục `C`:
 *     số dòng `k = max(1, ⌊(C − g)/(sàn + g)⌋)`, icon `s = min(sàn, C)` (ngang trục hẹp hơn sàn ⇒ icon = ngang trục,
 *     không cắt), bước dọc trục ≥ `max(s, sàn) + g` ⇒ ô chạm dọc trục vẫn ≥ sàn + g; khe dọc trục nới để mép khung cắt
 *     đúng GIỮA icon kế ([peekGap] — lộ nửa icon = dấu "còn nữa"). Phần dư ngang trục chia đều. Cuộn NGANG xếp theo CỘT
 *     (icon 1, 2… ở cột đầu) ⇒ mấy app đầu luôn ở đầu dải; cuộn dọc xếp theo hàng như lưới khớp.
 *
 * Tính chất (bài `ShortcutGridFitTest`, vét cạn độc lập):
 *  - chế độ CUỘN ⇒ nội dung dài HƠN khung (chứng minh: ứng viên `c = k` cột ⇒ `s ≥ sàn` nếu nội dung vừa — trái giả thiết;
 *    ca suy biến ngang trục < sàn + 2g mà nội dung vẫn vừa ⇒ trả KHỚP với một dòng);
 *  - ĐƠN ĐIỆU: thêm app thì icon không to ra (cuộn: icon = sàn ≤ mọi icon chế độ khớp; khớp: từ 2.92 OQ2 lấp bề ngang
 *    có thể chọn icon dưới cỡ lớn nhất nên không còn là hệ quả hiển nhiên của "`s(c)` không tăng theo `n`" — khoá bằng
 *    vét cạn `ShortcutGridScrollTest.don dieu` + `ShortcutGridFitTest`);
 *  - ô chạm (icon + khe, tầng vẽ nới nửa khe mỗi bên) ≥ sàn + `g` = 48 dp ở chế độ khớp.
 *
 * Số dp KHÔNG sống ở đây (`SpacingScaleContractTest.core khong giu so dp`): tầng vẽ đổi `KachiBars.SHORTCUT_GRID_*` ra
 * px rồi mới gọi.
 */
object ShortcutGridFit {

    /**
     * Dung sai LẤP BỀ NGANG (OQ2, quyết định điều phối 06/10): khung đứng đổi tối đa 10 % cỡ icon lấy lề hai bên nhỏ
     * nhất. Một hằng, một chỗ khai ([GridFit.Spec.fillWidth]).
     */
    const val FILL_WIDTH = 0.10

    /** Trục cuộn của lưới: [NONE] = mọi icon vừa khung. */
    enum class Scroll { NONE, VERTICAL, HORIZONTAL }

    /**
     * Kết quả khớp cho [count] icon trong khung [widthPx] × [heightPx]: [cols] cột × [rows] hàng, icon vuông [iconPx],
     * khe [gapXPx]/[gapYPx] (khe giữa = khe tới mép trên từng trục). [scroll] ≠ NONE ⇒ nội dung [contentWidthPx] ×
     * [contentHeightPx] dài hơn khung theo trục cuộn; [left]/[top] tính theo toạ độ NỘI DUNG (tầng vẽ trừ vị trí cuộn).
     * Cuộn NGANG xếp theo cột: [rows] = số dòng ngang trục, [cols] = số cột dọc trục.
     */
    data class Fit(
        val count: Int,
        val cols: Int,
        val rows: Int,
        val iconPx: Int,
        val gapXPx: Float,
        val gapYPx: Float,
        val widthPx: Int,
        val heightPx: Int,
        val scroll: Scroll = Scroll.NONE,
        val contentWidthPx: Int = widthPx,
        val contentHeightPx: Int = heightPx,
    ) {
        /** Quãng cuộn tối đa (px) dọc trục cuộn; 0 khi [Scroll.NONE]. */
        val maxScrollPx: Int
            get() = when (scroll) {
                Scroll.NONE -> 0
                Scroll.VERTICAL -> contentHeightPx - heightPx
                Scroll.HORIZONTAL -> contentWidthPx - widthPx
            }.coerceAtLeast(0)

        /** Số icon ở hàng [row] (cuộn ngang: ở CỘT [row]) — mọi hàng đủ, riêng hàng cuối có thể thiếu. */
        fun inRow(row: Int): Int {
            val per = if (scroll == Scroll.HORIZONTAL) rows else cols
            val lines = if (scroll == Scroll.HORIZONTAL) cols else rows
            return if (row < lines - 1) per else count - per * (lines - 1)
        }

        /** Mép trái (px, toạ độ nội dung) của icon thứ [i]; hàng/cột cuối thiếu căn giữa với cùng khe. */
        fun left(i: Int): Int = if (scroll == Scroll.HORIZONTAL) grid().top(i) else grid().left(i)

        /** Mép trên (px, toạ độ nội dung) của icon thứ [i]. */
        fun top(i: Int): Int = if (scroll == Scroll.HORIZONTAL) grid().left(i) else grid().top(i)

        /**
         * Hình học dùng chung với lưới widget ([GridFit.Grid]) — một công thức đặt ô. Cuộn ngang = lưới dọc của khung
         * CHUYỂN VỊ (rộng ↔ cao, khe X ↔ Y) rồi đổi toạ độ ở [left]/[top] ⇒ xếp theo cột, cột cuối thiếu căn giữa.
         */
        private fun grid(): GridFit.Grid = if (scroll == Scroll.HORIZONTAL) {
            GridFit.Grid(
                GridFit.rowCounts(count, rows, GridFit.RowSplit.FULL_FIRST), iconPx, iconPx, gapYPx, gapXPx,
                contentHeightPx, contentWidthPx,
            )
        } else {
            GridFit.Grid(
                GridFit.rowCounts(count, cols, GridFit.RowSplit.FULL_FIRST), iconPx, iconPx, gapXPx, gapYPx,
                contentWidthPx, contentHeightPx,
            )
        }
    }

    /**
     * Khớp [n] icon vuông vào khung [widthPx] × [heightPx] (KDoc lớp). [n] ≤ 0 ⇒ lưới rỗng (0 cột, 0 hàng) — tầng vẽ tự
     * dựng ô "chưa có lối tắt". Khung ≤ 0 (chưa đo) ⇒ icon = [minIconPx], không cuộn.
     */
    fun fit(n: Int, widthPx: Int, heightPx: Int, gapPx: Int, minIconPx: Int, maxIconPx: Int): Fit {
        val w = widthPx.coerceAtLeast(0)
        val h = heightPx.coerceAtLeast(0)
        val g = gapPx.coerceAtLeast(0)
        val lo = minIconPx.coerceAtLeast(1)
        val hi = maxIconPx.coerceAtLeast(lo)
        val f = GridFit.fit(
            n, w, h,
            listOf(GridFit.Shape(GridFit.Form.ICON_ONLY, 1.0, 1.0, lo.toDouble(), fallback = false)),
            GridFit.Spec(
                gapPx = g, maxScale = hi.toDouble(), quantum = 1.0,
                rowSplit = GridFit.RowSplit.FULL_FIRST, placement = GridFit.Placement.EVEN_GAPS, fillWidth = FILL_WIDTH,
            ),
        )
        if (n <= 0 || w <= 0 || h <= 0 || f.legible) {
            return Fit(f.count, f.cols, f.rows, f.cellW, f.grid.gapX, f.grid.gapY, f.grid.widthPx, f.grid.heightPx)
        }
        return scrolled(n, w, h, g, lo)
    }

    /** Chế độ CUỘN (KDoc lớp, mục 2): trục dài, icon = sàn (hoặc ngang trục nếu hẹp hơn), dòng ngang trục nhiều nhất. */
    private fun scrolled(n: Int, w: Int, h: Int, g: Int, lo: Int): Fit {
        val vertical = h >= w
        val cross = if (vertical) w else h
        val along = if (vertical) h else w
        val lines = maxOf(1, (cross - g) / (lo + g))
        val s = minOf(lo, cross).coerceAtLeast(1)
        val step = maxOf(s, lo) + g                       // bước dọc trục: ô chạm ≥ sàn + g dù icon hẹp hơn sàn
        val m = (n + lines - 1) / lines                    // số hàng (dọc) / số cột (ngang) dọc trục cuộn
        val gapMin = step - s
        val needed = m * s + (m + 1) * gapMin
        val crossGap = ((cross - lines * s) / (lines + 1f)).coerceAtLeast(0f)
        // Suy biến (ngang trục < sàn + 2g, một dòng): nội dung vẫn vừa ⇒ không cuộn, dọc trục chia đều như chế độ khớp.
        val scroll = if (needed > along) (if (vertical) Scroll.VERTICAL else Scroll.HORIZONTAL) else Scroll.NONE
        val alongGap = if (scroll == Scroll.NONE) (along - m * s) / (m + 1f) else peekGap(along, s, gapMin)
        val length = if (scroll == Scroll.NONE) along else ceil(m * s + (m + 1) * alongGap).toInt()
        return if (vertical) {
            Fit(n, lines, m, s, crossGap, alongGap, w, h, scroll, w, length)
        } else if (scroll == Scroll.NONE) {
            // Một dòng ngang, không cuộn ⇒ xếp theo hàng như lưới khớp (m cột × 1 hàng — `lines` = 1 ở ca suy biến).
            Fit(n, m, lines, s, alongGap, crossGap, w, h)
        } else {
            Fit(n, m, lines, s, alongGap, crossGap, w, h, scroll, length, h)
        }
    }

    /**
     * Khe dọc trục ở chế độ CUỘN để mép khung cắt ĐÚNG GIỮA icon kế (lộ nửa icon = dấu "còn nữa, vuốt đi" — [ĐO máy ảo
     * 06/10] khung 301×123, 8 app, khe sàn ⇒ 4 icon trọn và icon thứ 5 nằm khuất đúng sau mép: trông như một dải đủ 4 app,
     * không ai biết phải vuốt). `j` = số icon trọn còn chỗ cho nửa icon kế ở khe sàn [gapMin]; khe = `(F − (j + ½)·s) /
     * (j + 1)` ≥ [gapMin] (vì `j` lớn nhất thoả bất đẳng thức đó). Khung không đủ một icon trọn + nửa ⇒ giữ khe sàn.
     */
    private fun peekGap(along: Int, s: Int, gapMin: Int): Float {
        val j = floor((along - gapMin - s / 2.0) / (s + gapMin)).toInt()
        return if (j < 1) gapMin.toFloat() else maxOf(gapMin.toFloat(), (along - (j + 0.5f) * s) / (j + 1))
    }
}
