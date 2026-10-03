package com.byd.clusternav.launcher

/**
 * ═══ R-SI1 — LƯỚI LỐI TẮT TỰ CO GIÃN theo khung THẬT của widget `w_apps` (thuần, `:core`, đơn vị px) ═══════════════
 *
 * Spec `docs/specs/kachi-287-look-and-keys.html` R-SI1 · §4.4. Owner 03/10: *"nhiều thì bé lại, to thì giãn ra cho cân
 * đối trong widget là đẹp, đồng size, khoảng cách đều nhau"*. Trước 2.87 lưới dùng ô CỐ ĐỊNH (64/52 dp) và số cột
 * `min(n, 4)` — không biết khung to hay nhỏ, nên khung to thì icon lọt thỏm, khung nhỏ thì tràn.
 *
 * Phép khớp ([fit]): thử MỌI số cột `c = 1…n`, hàng `r = ⌈n/c⌉`; cỡ icon `s` (px nguyên) lớn nhất sao cho
 * `c·s + (c+1)·g ≤ w` và `r·s + (r+1)·g ≤ h` với khe tối thiểu `g = gapRatio·s` (khe giữa hai icon VÀ tới mép).
 * Chọn `c` cho `s` lớn nhất — so trên cỡ CHƯA kẹp, để khung lớn hơn mức trần không làm bố cục lật sang hình khác;
 * hoà thì ít ô trống hơn, rồi ít hàng hơn. Sau đó kẹp `s` vào `[minIconPx, maxIconPx]` và chia ĐỀU phần dư theo mỗi
 * trục: `gapX = (w − c·s)/(c+1)`, `gapY = (h − r·s)/(r+1)` ⇒ khe giữa icon = khe tới mép, trên từng trục.
 * Hàng cuối thiếu icon thì căn GIỮA với cùng `gapX` ([Fit.left]).
 *
 * Kẹp dưới thắng "khe tối thiểu": khung nhỏ tới mức icon < [minIconPx] thì icon giữ cỡ tối thiểu và khe là phần dư
 * chia đều (dưới mức `gapRatio·s`); không còn dư thì khe = 0 và khối căn giữa, tràn đều hai bên (tầng vẽ cắt theo
 * khung) — một icon không bấm/nhìn được còn tệ hơn một icon bị cắt mép.
 *
 * Số dp KHÔNG sống ở đây (`SpacingScaleContractTest.core khong giu so dp`): tầng vẽ đổi 28/120 dp
 * (`KachiBars.SHORTCUT_GRID_MIN_ICON`/`MAX_ICON`) ra px rồi mới gọi.
 *
 * ## L5 WIDGET-FIT-ALL (2.87) — nay là MỘT cấu hình của [GridFit]
 * Phép khớp ở trên KHÔNG đổi một px: nó chính là [GridFit.fit] với hộp `1×1` (icon vuông, `k` = cạnh icon px),
 * [GridFit.Placement.EVEN_GAPS] (ô = icon, khe = phần dư chia đều), [GridFit.RowSplit.FULL_FIRST] (hàng cuối nhận phần
 * lẻ) và `quantum = 1` (px nguyên + hai vòng sửa ranh giới). `ShortcutGridFitTest` (vét cạn độc lập) giữ nguyên và
 * xanh — đó là bằng chứng tương đương. Lý do gộp: owner nói CÙNG một mục tiêu cho lưới lối tắt và mọi nội dung
 * widget; hai bộ giải là hai chỗ để lệch nhau.
 */
object ShortcutGridFit {

    /**
     * Khe tối thiểu = 0,3 × cỡ icon (khe giữa hai icon và tới mép, trên trục bị bó).
     *
     * Vì sao 0,3 (không phải số nào khác trong khoảng 0,25–0,35):
     *  - [ĐO mã] lưới cũ (ô 64 / icon 52) và khối thanh nút (ô 52 / icon 44) cho khe giữa icon 12/52 ≈ 0,23 và
     *    8/44 ≈ 0,18 — đó là lúc chưa tính khe tới mép; chia đều phần dư thì khe trên trục thoáng luôn ≥ mức này,
     *    nên mức sàn phải nhỉnh hơn để trục bị bó không trông chật hơn bản cũ.
     *  - [SUY] khe ≈ ¼–⅓ cỡ vật là tỉ lệ để mắt vẫn gom các icon thành MỘT nhóm mà từng icon vẫn tách bạch; > 0,35
     *    thì icon bé đi thấy rõ ở 8 app mà khung không thoáng hơn bao nhiêu.
     *  - [SUY] đích chạm: tầng vẽ nới vùng chạm ra nửa khe mỗi bên ⇒ bước lưới ≈ 1,3·s ≥ 48 dp khi s ≥ 37 dp.
     */
    const val GAP_RATIO: Double = 0.3

    /**
     * Kết quả khớp cho [count] icon trong khung [widthPx] × [heightPx]: [cols] cột × [rows] hàng, icon vuông
     * [iconPx], khe đều [gapXPx]/[gapYPx] (≥ 0; = 0 khi kẹp dưới làm khối tràn).
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
    ) {
        /** Số icon ở hàng [row] — mọi hàng đủ [cols], riêng hàng cuối có thể thiếu. */
        fun inRow(row: Int): Int = if (row < rows - 1) cols else count - cols * (rows - 1)

        /**
         * Mép trái (px, so với khung) của icon thứ [i]. Mỗi hàng căn giữa với CÙNG [gapXPx]: hàng đủ thì mép trái =
         * đúng một khe; hàng cuối thiếu thì dồn vào giữa, khe giữa các icon không đổi.
         */
        fun left(i: Int): Int = grid().left(i)

        /** Mép trên (px, so với khung) của icon thứ [i] — cả khối căn giữa theo chiều dọc với [gapYPx]. */
        fun top(i: Int): Int = grid().top(i)

        /** Hình học dùng chung với lưới widget ([GridFit.Grid]) — một công thức đặt ô, không phải hai. */
        private fun grid() = GridFit.Grid(
            GridFit.rowCounts(count, cols, GridFit.RowSplit.FULL_FIRST), iconPx, iconPx, gapXPx, gapYPx, widthPx, heightPx,
        )
    }

    /**
     * Khớp [n] icon vuông vào khung [widthPx] × [heightPx] (KDoc lớp). [n] ≤ 0 ⇒ lưới rỗng (0 cột, 0 hàng) — tầng vẽ
     * tự dựng ô "chưa có lối tắt". Khung ≤ 0 (chưa đo) ⇒ icon = [minIconPx].
     */
    fun fit(n: Int, widthPx: Int, heightPx: Int, gapRatio: Double, minIconPx: Int, maxIconPx: Int): Fit {
        val g = GridFit.fit(
            n, widthPx, heightPx,
            listOf(GridFit.Shape(GridFit.Form.ICON_ONLY, 1.0, 1.0, minIconPx.coerceAtLeast(0).toDouble(), fallback = false)),
            GridFit.Spec(
                gapRatio = gapRatio.coerceAtLeast(0.0), maxScale = maxIconPx.toDouble(), quantum = 1.0,
                rowSplit = GridFit.RowSplit.FULL_FIRST, placement = GridFit.Placement.EVEN_GAPS,
            ),
        )
        return Fit(g.count, g.cols, g.rows, g.cellW, g.grid.gapX, g.grid.gapY, g.grid.widthPx, g.grid.heightPx)
    }
}
