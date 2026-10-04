package com.byd.clusternav.launcher

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ═══ L5 WIDGET-FIT-ALL — MỘT phép khớp lưới cho MỌI nội dung trong khung widget (thuần, `:core`, đơn vị px) ═════════
 *
 * Owner 03/10 (2.86, ảnh): *"Các picker, datum add vào widget cũng không co giãn phù hợp, gây bug UI"* — khung dẹt
 * chứa lưới 2×3 nút kính, nhãn bị cắt nửa dưới, có ô mất hẳn nhãn. Mục tiêu owner nói cho lưới lối tắt và áp cho mọi
 * nội dung widget: *"nhiều thì bé lại, to thì giãn ra cho cân đối trong widget là đẹp, đồng size, khoảng cách đều
 * nhau"*.
 *
 * ## Nguyên nhân [ĐO mã 2.86 + AOSP android-10.0.0_r47]
 * `WidgetViews.buildGrid` chia hàng theo SỐ MỤC (6 ⇒ 3+3) không nhìn tỉ lệ khung, mỗi ô dựng ở cỡ CỐ ĐỊNH
 * (`TileSize.DOCK`: icon 20dp + nhãn 11.5sp giữ chỗ 2 dòng ≈ 61dp) trong khi khung một hàng lưới chỉ cho ≈ 37dp mỗi ô.
 * Nhãn `WRAP` được đo `AT_MOST` phần còn lại, TextView tự kẹp `min(desired, size)` (`TextView.java:9404-9405`) và vẽ
 * từ đỉnh ⇒ mất nửa dưới, không `…`. Khung to thì ngược lại: nội dung không bao giờ to ra.
 *
 * ## Phép khớp ([fit])
 * Đầu vào: số mục `n`, khung `W×H`, và các **dạng vẽ** ([Shape]) theo thứ tự ưu tiên — mỗi dạng là một HỘP TỰ NHIÊN
 * đo ở thang 1 (tầng vẽ đo thật, không gõ số) + sàn đọc được [Shape.minScale]. Với MỌI cách chia `c` cột × `r` hàng
 * ([RowSplit]) và MỌI dạng, hệ số co `k` lớn nhất để hộp `k·w0 × k·h0` vừa ô ([Placement]). Chọn theo thứ tự TỪ ĐIỂN
 * (xác định, không ngẫu nhiên) — [better]:
 *  1. **tầng**: dạng CÓ NHÃN đọc được (`k ≥ minScale` — nhãn chính, nhãn DỰ PHÒNG [Shape.reserve], nhãn NGẮN
 *     [Shape.short]) › dạng LÙI ([Shape.fallback], chỉ icon) đọc được › không dạng nào đọc được;
 *  2. **đích chạm**: ô ≥ [Spec.minCellPx] cả hai chiều (có mục bấm được ⇒ 48dp) đứng trước; KHÔNG ứng viên nào đạt
 *     thì ô có cạnh NGẮN dài hơn (gần đích chạm hơn — ít cột/nhiều hàng hơn khi khung dẹt) đứng trước, so theo bậc
 *     `minCellPx / `[TOUCH_STEPS] (QA 04/10: khung 301×123 ra 6×1 ô 36px = 24dp trong khi 3×2 cho ô 43px);
 *  2a. **loại nhãn** (chỉ trong tầng có nhãn đọc được, SAU đích chạm): nhãn chính › dự phòng › ngắn. J1 (QA2 04/10,
 *     quyết định điều phối): ô ≥ 48dp với nhãn NGẮN đứng trước ô 43px với nhãn đầy — trên xe đang chạy, đích chạm
 *     quan trọng hơn chữ đầy đủ; cùng đạt chạm (hoặc cùng không) thì nhãn đầy thắng như trước;
 *  2c. **giá trị TRỌN** (chỉ tầng KHÔNG đọc được, QA3 04/10 — xét NGAY sau "đạt chạm hay không", TRƯỚC "gần đích chạm"
 *     và 2b; tầng khác nhau thì 2a/2c không bao giờ cùng xét): ứng viên mà ở `k = minScale` hộp "chỉ giá trị"
 *     ([Shape.wholeWidthPx] — chú thích nhường, giá trị ở sàn 10sp, [FitValues]) vừa ô đứng trước. Giá trị (số/giờ/số đo)
 *     không bao giờ bị cắt khi còn bố cục khác giữ được nó: khung 2×1 có dock, ô ngang 84×99 không đủ cho `06:57` ngay cả
 *     ở sàn ⇒ xếp DỌC (số trên chú thích) thay vì `…` mọi giá trị. Dạng không khai hộp này ⇒ không phân biệt (thứ tự cũ);
 *  2b. **vừa CAO ở sàn**: ứng viên mà ở `k = minScale` hộp vẫn vừa chiều cao ô đứng trước. Chỉ phân biệt được ở tầng
 *     KHÔNG đọc được (tầng đọc được thì mọi hộp đã vừa): nội dung giữ sàn và tràn — tràn NGANG thì nhãn `…`, tràn DỌC
 *     thì mất nửa dòng dưới, đúng bệnh ảnh 03/10 ⇒ tràn ngang ít hại hơn;
 *  3. **cỡ đã kẹp trần** `min(k, maxScale)` lớn hơn — *"to thì giãn ra"*, *"nhiều thì bé lại"*;
 *  4. **dạng ưu tiên** (thứ tự trong danh sách — dọc trước ngang);
 *  5. **cỡ CHƯA kẹp** lớn hơn — khung lớn hơn trần thì vẫn chọn bố cục ăn khớp hình khung nhất (cùng lẽ
 *     [ShortcutGridFit]: không để bố cục lật sang hình khác chỉ vì mọi ứng viên đều chạm trần);
 *  6. ít ô trống hơn → 7. ô vuông hơn (`|ln(rộng/cao)|`) → 8. ít hàng hơn.
 *
 * Sau khi chọn: `k` kẹp vào `[minScale, maxScale]` — **không bao giờ dưới sàn** (đổi dạng/bớt mục mới là cách đúng,
 * không phải bóp chữ). Phần dư chia ĐỀU thành khe ([Grid]): mọi ô cùng cỡ, khe giữa = khe tới mép (± 1px làm tròn),
 * hàng thiếu căn giữa với cùng khe.
 *
 * [ShortcutGridFit] (R-SI1, icon vuông, khe tỉ lệ cỡ icon) là MỘT cấu hình của phép này ([Placement.EVEN_GAPS] +
 * [RowSplit.FULL_FIRST] + `quantum = 1`) — một bộ giải, hai chỗ dùng.
 *
 * Số dp KHÔNG sống ở đây (`SpacingScaleContractTest.core khong giu so dp`): tầng vẽ đổi dp/sp ra px rồi mới gọi.
 */
object GridFit {

    /** Cách đặt icon với nhãn trong một ô. */
    enum class Form {
        /** Icon trên, nhãn dưới — dạng gốc của mọi bộ dựng. */
        VERTICAL,

        /** Icon cạnh nhãn — cho ô thấp mà rộng (khung một hàng lưới). */
        HORIZONTAL,

        /** Chỉ icon; nhãn chuyển vào mô tả trợ năng. Dạng LÙI cuối cùng — xem [Shape.fallback]. */
        ICON_ONLY,
    }

    /** Cách chia `n` mục vào `r` hàng. */
    enum class RowSplit {
        /**
         * Chia ĐỀU, hàng dài nhất và ngắn nhất chênh ≤ 1 (7 mục / 3 hàng ⇒ 3·2·2) — luật `GroupTileView.rowsOf`, hàng
         * cuối không trơ trọi. Dùng cho lưới widget.
         */
        BALANCED,

        /** Xếp đủ hàng, hàng cuối nhận phần lẻ (7 / 3 cột ⇒ 3·3·1) — luật R-SI1 của lưới lối tắt, giữ nguyên. */
        FULL_FIRST,
    }

    /** Ô to cỡ nào so với nội dung. */
    enum class Placement {
        /** Ô LẤP khung (khe tối thiểu [Spec.gapPx]); nội dung co `k` nằm giữa ô. Ô widget có nền ⇒ dùng cái này. */
        FILL,

        /** Ô = đúng hộp nội dung đã co; phần dư chia đều thành khe (icon lối tắt không có nền ô). */
        EVEN_GAPS,
    }

    /**
     * Một dạng vẽ của nội dung: hộp tự nhiên [widthPx]×[heightPx] ở thang 1, sàn [minScale] (chữ/icon/đích chạm nhỏ
     * nhất còn dùng được), số dòng nhãn [lines] dạng này giữ chỗ (0 = không có nhãn). [fallback] = chỉ được chọn khi
     * KHÔNG dạng có nhãn nào đọc được (chỉ icon ⇒ người lái mất chữ, bấm nhầm kính khi icon giống nhau).
     * [reserve] = dạng CÓ NHÃN dự phòng (icon cạnh nhãn 2 dòng — QA 04/10, nhãn Mã Lai dài): đứng sau dạng có nhãn
     * CHÍNH khi cùng đích chạm, luôn trước chỉ-icon. [short] = cùng dạng nhưng ô hiện NHÃN NGẮN (J1, QA2 04/10 — nhãn
     * ngắn đã dịch đủ 5 tiếng của thanh nút): đứng sau nhãn chính + dự phòng khi cùng đích chạm (KDoc lớp, bước 2a).
     * [wholeWidthPx]×[wholeHeightPx] = hộp "CHỈ GIÁ TRỊ" (QA3 — chú thích nhường, giá trị ở cỡ mà `k = minScale` đưa về sàn
     * 10sp), cùng thang 1 với hộp chính; `null` = ô không có cặp giá trị/chú thích (KDoc lớp, bước 2c).
     */
    data class Shape(
        val form: Form,
        val widthPx: Double,
        val heightPx: Double,
        val minScale: Double = 0.0,
        val lines: Int = 0,
        val fallback: Boolean = form == Form.ICON_ONLY,
        val reserve: Boolean = false,
        val short: Boolean = false,
        val wholeWidthPx: Double? = null,
        val wholeHeightPx: Double? = null,
    )

    /**
     * Tham số của một lần khớp.
     *
     * @property gapPx khe TỐI THIỂU giữa các ô và tới mép.
     * @property gapRatio khe cộng thêm tỉ lệ theo cạnh hộp đã co (chỉ [Placement.EVEN_GAPS] — R-SI1 dùng 0,3).
     * @property slackPx chừa trong mỗi ô ([Placement.FILL]) cho sai số làm tròn px/hinting chữ.
     * @property maxScale trần `k` — khung khổng lồ không làm chữ/icon khổng lồ.
     * @property minCellPx đích chạm: ô nhỏ hơn số này ở một chiều ⇒ ứng viên tụt hạng (0 = không xét).
     * @property quantum bước của `k` (0 = liên tục). Lưới lối tắt: 1 (px nguyên). Lưới widget: bậc nhỏ để đổi khung
     *   một chút không áp lại cỡ chữ (không giật bố cục).
     */
    data class Spec(
        val gapPx: Int = 0,
        val gapRatio: Double = 0.0,
        val slackPx: Int = 0,
        val maxScale: Double = Double.POSITIVE_INFINITY,
        val minCellPx: Int = 0,
        val quantum: Double = 0.0,
        val rowSplit: RowSplit = RowSplit.BALANCED,
        val placement: Placement = Placement.FILL,
    )

    /**
     * Hình học đã chốt: số mục mỗi hàng [rowCounts], ô [cellW]×[cellH] (mọi ô cùng cỡ), khe [gapX]/[gapY] (khe giữa
     * = khe tới mép trên mỗi trục). Hàng thiếu căn giữa với CÙNG khe; cả khối căn giữa theo chiều dọc.
     */
    data class Grid(
        val rowCounts: List<Int>,
        val cellW: Int,
        val cellH: Int,
        val gapX: Float,
        val gapY: Float,
        val widthPx: Int,
        val heightPx: Int,
    ) {
        val rows: Int get() = rowCounts.size
        val cols: Int get() = rowCounts.maxOrNull() ?: 0

        /** Hàng của mục thứ [i] (đếm dồn [rowCounts]). */
        fun rowOf(i: Int): Int {
            var acc = 0
            rowCounts.forEachIndexed { r, k -> acc += k; if (i < acc) return r }
            return (rows - 1).coerceAtLeast(0)
        }

        private fun firstOf(row: Int): Int = rowCounts.take(row).sum()

        /** Mép trái (px, so với khung) của mục [i] — hàng căn giữa với khe [gapX]. */
        fun left(i: Int): Int {
            val row = rowOf(i)
            val k = rowCounts[row]
            val rowW = k * cellW + (k - 1) * gapX
            return ((widthPx - rowW) / 2f + (i - firstOf(row)) * (cellW + gapX)).roundToInt()
        }

        /** Mép trên (px, so với khung) của mục [i] — cả khối căn giữa theo chiều dọc với khe [gapY]. */
        fun top(i: Int): Int {
            val blockH = rows * cellH + (rows - 1) * gapY
            return ((heightPx - blockH) / 2f + rowOf(i) * (cellH + gapY)).roundToInt()
        }
    }

    /**
     * Kết quả: hình học [grid], dạng đã chọn [shape] (`null` khi `n = 0`), hệ số [scale] (đã kẹp sàn/trần) và
     * [rawScale] (chưa kẹp trần, đã lượng tử). [legible] `false` ⇒ ngay cả dạng tốt nhất cũng dưới sàn: nội dung giữ
     * cỡ sàn và tràn ô — tầng vẽ ghi nhật ký, trình chọn nên báo [capacity]. [touchOk] `false` ⇒ ô < [Spec.minCellPx].
     */
    data class Fit(
        val count: Int,
        val grid: Grid,
        val shape: Shape?,
        val scale: Double,
        val rawScale: Double,
        val legible: Boolean,
        val touchOk: Boolean,
    ) {
        val cols: Int get() = grid.cols
        val rows: Int get() = grid.rows
        val cellW: Int get() = grid.cellW
        val cellH: Int get() = grid.cellH
        fun left(i: Int): Int = grid.left(i)
        fun top(i: Int): Int = grid.top(i)
    }

    /** Sai số khi so tích số thực với khung nguyên (0,3 hay 1/32·w không biểu diễn đúng ở cơ số 2). */
    private const val EPS = 1e-6

    /**
     * Số bậc chia đích chạm khi so "gần đích chạm hơn" (KDoc lớp, bước 2): bậc = `minCellPx / 16` (48dp ⇒ 3dp). Chênh
     * dưới một bậc không phải khác biệt về chạm ⇒ để cỡ chữ quyết. [ĐỀ XUẤT, owner chốt].
     */
    const val TOUCH_STEPS = 16

    /** Hộp trung tính khi KHÔNG mục nào co giãn (mọi ô tự vẽ theo khung): chọn bố cục cho ô to + vuông nhất. */
    private val NEUTRAL = Shape(Form.VERTICAL, 1.0, 1.0)

    /** Số mục mỗi hàng cho [n] mục ở [cols] cột theo [split] (`BALANCED` lấy `r = ⌈n/cols⌉` rồi chia đều). */
    fun rowCounts(n: Int, cols: Int, split: RowSplit): List<Int> {
        if (n <= 0 || cols <= 0) return emptyList()
        val r = (n + cols - 1) / cols
        return when (split) {
            RowSplit.FULL_FIRST -> List(r) { if (it < r - 1) cols else n - cols * (r - 1) }
            RowSplit.BALANCED -> { val base = n / r; val extra = n % r; List(r) { base + if (it < extra) 1 else 0 } }
        }
    }

    /** Mọi cách chia hợp lệ: `FULL_FIRST` theo cột `1..n`; `BALANCED` theo hàng `1..n` (cột = `⌈n/r⌉`). */
    private fun layouts(n: Int, split: RowSplit): List<List<Int>> = when (split) {
        RowSplit.FULL_FIRST -> (1..n).map { rowCounts(n, it, split) }
        RowSplit.BALANCED -> (1..n).map { r -> val base = n / r; val extra = n % r; List(r) { base + if (it < extra) 1 else 0 } }
            .filter { it.all { k -> k > 0 } }
    }

    private class Cand(
        val counts: List<Int>, val shapeIndex: Int, val shape: Shape, val q: Double, val capped: Double,
        val legible: Boolean, val touchOk: Boolean, val empty: Int, val aspect: Double, val near: Int, val tall: Boolean,
        val whole: Boolean,
    ) {
        val rows: Int get() = counts.size
        val cols: Int get() = counts.max()
        val tier: Int get() = when {
            !legible -> 2
            shape.fallback -> 1
            else -> 0
        }

        /** Loại nhãn trong tầng có nhãn đọc được: chính 0 · dự phòng 1 · ngắn 2 (KDoc lớp, bước 2a). */
        val kind: Int get() = when {
            shape.short -> 2
            shape.reserve -> 1
            else -> 0
        }
    }

    /** Khớp [n] mục vào khung [widthPx]×[heightPx] (KDoc lớp). `n ≤ 0` ⇒ lưới rỗng; khung ≤ 0 ⇒ `k = 0` ⇒ sàn. */
    fun fit(n: Int, widthPx: Int, heightPx: Int, shapes: List<Shape>, spec: Spec): Fit {
        val w = widthPx.coerceAtLeast(0)
        val h = heightPx.coerceAtLeast(0)
        if (n <= 0) return Fit(0, Grid(emptyList(), 0, 0, 0f, 0f, w, h), null, 0.0, 0.0, legible = true, touchOk = true)
        val pool = shapes.ifEmpty { listOf(NEUTRAL) }
        var best: Cand? = null
        pool.forEachIndexed { si, s ->
            layouts(n, spec.rowSplit).forEach { counts ->
                val c = candidate(n, counts, si, s, w, h, spec)
                if (best == null || better(c, best!!)) best = c
            }
        }
        val b = best!!
        val lo = b.shape.minScale.coerceAtLeast(0.0)
        val scale = b.q.coerceIn(lo, max(spec.maxScale, lo))
        return Fit(n, grid(b, scale, w, h, spec), b.shape, scale, b.q, b.legible, b.touchOk)
    }

    /**
     * SỨC CHỨA: số mục nhiều nhất (≤ [maxN]) mà khung [widthPx]×[heightPx] còn vẽ được bằng một dạng CÓ NHÃN đọc
     * được và đạt đích chạm — để trình chọn/nhật ký nói *"khung này vừa N mục"* trước khi người dùng thêm mục thứ N+1.
     * 0 = không vừa nổi một mục.
     */
    fun capacity(widthPx: Int, heightPx: Int, shapes: List<Shape>, spec: Spec, maxN: Int): Int {
        for (n in maxN downTo 1) {
            val f = fit(n, widthPx, heightPx, shapes, spec)
            if (f.legible && f.shape?.fallback == false && f.touchOk) return n
        }
        return 0
    }

    private fun candidate(n: Int, counts: List<Int>, si: Int, s: Shape, w: Int, h: Int, spec: Spec): Cand {
        val r = counts.size
        val c = counts.max()
        val pitchW = (w - (c + 1) * spec.gapPx).toDouble() / c
        val pitchH = (h - (r + 1) * spec.gapPx).toDouble() / r
        val q = largest(c, r, s, w, h, spec)
        val touch = spec.minCellPx <= 0 || (pitchW + EPS >= spec.minCellPx && pitchH + EPS >= spec.minCellPx)
        val aspect = when (spec.placement) {
            Placement.FILL -> if (pitchW > 0 && pitchH > 0) abs(ln(pitchW / pitchH)) else Double.MAX_VALUE
            Placement.EVEN_GAPS -> if (s.widthPx > 0 && s.heightPx > 0) abs(ln(s.widthPx / s.heightPx)) else 0.0
        }
        val near = nearTouch(min(pitchW, pitchH), spec.minCellPx)
        val tall = tallLimit(r, s, h, spec) + EPS >= s.minScale
        return Cand(
            counts, si, s, q, min(q, spec.maxScale), q + EPS >= s.minScale, touch, c * r - n, aspect, near, tall,
            whole(c, r, s, w, h, spec),
        )
    }

    /**
     * Hộp "chỉ giá trị" ([Shape.wholeWidthPx]) vừa ô ở `k = minScale` — đúng `k` mà tầng không đọc được áp (KDoc [fit]: kẹp
     * sàn) — KDoc lớp, bước 2c. Dạng không khai hộp ⇒ `false` cho mọi ứng viên (không đổi thứ tự). Chỉ ô [Placement.FILL].
     */
    private fun whole(c: Int, r: Int, s: Shape, w: Int, h: Int, spec: Spec): Boolean {
        val ww = s.wholeWidthPx ?: return false
        val wh = s.wholeHeightPx ?: return false
        if (spec.placement != Placement.FILL) return false
        val cw = floor((w - (c + 1) * spec.gapPx).toDouble() / c) - spec.slackPx
        val ch = floor((h - (r + 1) * spec.gapPx).toDouble() / r) - spec.slackPx
        return s.minScale * ww <= cw + EPS && s.minScale * wh <= ch + EPS
    }

    /**
     * `k` lớn nhất theo RIÊNG chiều cao (cùng công thức [rawLimit], trục dọc) — cho phép xét "ở sàn còn vừa cao không"
     * (KDoc lớp, bước 2b). Chỉ ô [Placement.FILL] (ô widget có nhãn) — lưới lối tắt chỉ có icon vuông, không có dòng
     * chữ nào để mất ⇒ +∞ (thứ tự R-SI1 giữ nguyên, `ShortcutGridFitTest`).
     */
    private fun tallLimit(r: Int, s: Shape, h: Int, spec: Spec): Double = when (spec.placement) {
        Placement.FILL -> axis(floor((h - (r + 1) * spec.gapPx).toDouble() / r) - spec.slackPx, s.heightPx)
        Placement.EVEN_GAPS -> Double.POSITIVE_INFINITY
    }

    /**
     * Bậc "gần đích chạm" của ô có cạnh ngắn [sidePx] (KDoc lớp, bước 2) — chỉ dùng khi ô CHƯA đạt [minCellPx]. Không
     * xét chạm (`minCellPx ≤ 0`) ⇒ 0 cho mọi ô (không đổi thứ tự).
     */
    fun nearTouch(sidePx: Double, minCellPx: Int): Int =
        if (minCellPx <= 0) 0 else floor((sidePx.coerceAtLeast(0.0) + EPS) * TOUCH_STEPS / minCellPx).toInt()

    /**
     * `k` lớn nhất (đã lượng tử [Spec.quantum]) để [c]×[r] hộp của [s] vừa khung. Phép chia cho ra đáp số; hai vòng
     * sửa chỉ chạy khi số thực lệch đúng tại ranh giới (≤ 1 bước) — cùng cách R-SI1 đã ghim bằng vét cạn.
     */
    private fun largest(c: Int, r: Int, s: Shape, w: Int, h: Int, spec: Spec): Double {
        val fits = fitsAt(c, r, s, w, h, spec)
        val raw = rawLimit(c, r, s, w, h, spec).coerceAtLeast(0.0)
        if (raw == Double.POSITIVE_INFINITY) return spec.maxScale
        val q = spec.quantum
        if (q <= 0.0) return raw
        var k = floor(raw / q) * q
        while (k > 0 && !fits(k)) k -= q
        while (fits(k + q)) k += q
        return k.coerceAtLeast(0.0)
    }

    /** Trần liên tục của `k` theo từng trục (công thức đóng). */
    private fun rawLimit(c: Int, r: Int, s: Shape, w: Int, h: Int, spec: Spec): Double = when (spec.placement) {
        Placement.FILL -> {
            val cw = floor((w - (c + 1) * spec.gapPx).toDouble() / c) - spec.slackPx
            val ch = floor((h - (r + 1) * spec.gapPx).toDouble() / r) - spec.slackPx
            min(axis(cw, s.widthPx), axis(ch, s.heightPx))
        }
        Placement.EVEN_GAPS -> min(
            axis((w - (c + 1) * spec.gapPx).toDouble(), (c + (c + 1) * spec.gapRatio) * s.widthPx),
            axis((h - (r + 1) * spec.gapPx).toDouble(), (r + (r + 1) * spec.gapRatio) * s.heightPx),
        )
    }

    private fun axis(room: Double, per: Double): Double = if (per <= 0.0) Double.POSITIVE_INFINITY else room / per

    private fun fitsAt(c: Int, r: Int, s: Shape, w: Int, h: Int, spec: Spec): (Double) -> Boolean = { k ->
        when (spec.placement) {
            Placement.FILL -> {
                val cw = floor((w - (c + 1) * spec.gapPx).toDouble() / c) - spec.slackPx
                val ch = floor((h - (r + 1) * spec.gapPx).toDouble() / r) - spec.slackPx
                k * s.widthPx <= cw + EPS && k * s.heightPx <= ch + EPS
            }
            Placement.EVEN_GAPS ->
                k * (c + (c + 1) * spec.gapRatio) * s.widthPx <= w - (c + 1) * spec.gapPx + EPS &&
                    k * (r + (r + 1) * spec.gapRatio) * s.heightPx <= h - (r + 1) * spec.gapPx + EPS
        }
    }

    /** `true` nếu [a] xếp TRƯỚC [b] theo thứ tự từ điển ở KDoc lớp. Hoà hẳn ⇒ giữ ứng viên đến trước (xác định). */
    private fun better(a: Cand, b: Cand): Boolean {
        if (a.tier != b.tier) return a.tier < b.tier
        if (a.touchOk != b.touchOk) return a.touchOk
        if (a.tier == 2 && a.whole != b.whole) return a.whole
        if (a.tier == 0 && a.kind != b.kind) return a.kind < b.kind
        if (!a.touchOk && a.near != b.near) return a.near > b.near
        if (a.tall != b.tall) return a.tall
        if (abs(a.capped - b.capped) > EPS) return a.capped > b.capped
        if (a.shapeIndex != b.shapeIndex) return a.shapeIndex < b.shapeIndex
        if (abs(a.q - b.q) > EPS) return a.q > b.q
        if (a.empty != b.empty) return a.empty < b.empty
        if (abs(a.aspect - b.aspect) > 1e-9) return a.aspect < b.aspect
        return a.rows < b.rows
    }

    /** Ô + khe cho ứng viên đã chọn ở cỡ [scale]: ô lấp khung ([Placement.FILL]) hoặc ô = hộp đã co. */
    private fun grid(b: Cand, scale: Double, w: Int, h: Int, spec: Spec): Grid {
        val c = b.cols
        val r = b.rows
        val cellW: Int
        val cellH: Int
        when (spec.placement) {
            Placement.FILL -> {
                cellW = ((w - (c + 1) * spec.gapPx) / c).coerceAtLeast(0)
                cellH = ((h - (r + 1) * spec.gapPx) / r).coerceAtLeast(0)
            }
            Placement.EVEN_GAPS -> {
                cellW = (scale * b.shape.widthPx).roundToInt()
                cellH = (scale * b.shape.heightPx).roundToInt()
            }
        }
        val gapX = ((w - c * cellW) / (c + 1f)).coerceAtLeast(0f)
        val gapY = ((h - r * cellH) / (r + 1f)).coerceAtLeast(0f)
        return Grid(b.counts, cellW, cellH, gapX, gapY, w, h)
    }
}
