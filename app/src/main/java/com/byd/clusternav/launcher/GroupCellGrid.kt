package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.text.Layout
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.ceil

// ⚠ 3.01 R20 (spec kachi-301 §2.5 · nhóm E). Tách tệp mới vì `GroupTileViews.kt` đã ở trần 500 dòng. Đây là hai LƯỚI của ô
// nhóm (ô con ĐỌC + hàng NÚT) — cùng vai "bộ phận" như [ReadGrid]/[CellBinder] ở `GroupTileParts.kt`. Tệp này PHẢI có trong
// `GroupTileWiringContractTest.tileFiles` (mọi `assertFalse` của bài canh tầng vẽ nhóm phủ nó).

/** Một vùng của ô nhóm NHƯỜNG được chiều cao khi ô chật — xem [GroupTileView.onMeasure]. */
internal interface TrimArea {
    /** Bỏ phần đã nhường — gọi ở ĐẦU mỗi lượt đo của ô nhóm (không thì co dần một chiều). */
    fun resetTrim()

    /** Nhường thêm tối đa [px]; `false` = đã ở sàn (chỗ nội dung thật cần), chỗ gọi hỏi vùng kế tiếp. */
    fun trimBy(px: Int): Boolean

    /**
     * Phần vùng này VƯỢT chỗ cha đưa ở lượt đo vừa rồi (px, 0 = vừa). ⚠ Bắt buộc: thân ô là chuỗi `WRAP` (`FrameLayout` →
     * thẻ CARD `LinearLayout`) nên cha `resolveSize` KẸP con tràn về đúng chỗ có — tổng chiều cao các con của ô nhóm KHÔNG
     * lộ phần tràn. [ĐO] *Khí hậu* khung 2×6: lưới cần 630 px trong 603 px, tổng con = đúng hộp ⇒ không ai nhường, hàng
     * cuối (`PM2.5`) bị cắt còn 19 px.
     */
    val overflowPx: Int
}

/**
 * LƯỚI Ô CON ĐỌC của ô nhóm — số cột, dạng (ngang nhãn·số / dọc nhãn trên số dưới), số dòng nhãn và số mục hiện do
 * [GroupCellFit.plan] (`:core`) chọn theo BỀ RỘNG đo được và chỗ chữ THẬT cần (đo bằng `Paint` của chính các `TextView`).
 * Thay `ReadGrid` + hàng `LinearLayout` chia cột cố định ([ĐO] *Chuyến đi* 267 px: nhãn còn 38 px, vỡ `Th/ời …`).
 *
 * Giữ nguyên giao kèo cũ của lưới đọc: cao tối đa [capRowPx] × số hàng (không phình — kiểm toán mục 3), co theo chỗ còn,
 * và NHƯỜNG qua [trimBy] tới sàn = chỗ MỘT mục thật cần (đo, không hằng dp). Khác: nhường quá chỗ hiện mọi mục thì BỎ mục
 * cuối (ưu tiên = thứ tự khai) thay vì cắt chữ.
 *
 * Chi phí (R21): phép đo chữ chỉ chạy khi [signature] đổi (nhãn đổi / giá trị đổi ĐỊNH DẠNG — chữ số đổi không tính nhờ
 * [FitValues.headroom]); nhịp 1 Hz chỉ dựng chuỗi dấu để so. Không luồng, không nhịp, không listener.
 */
@SuppressLint("ViewConstructor")
internal class GroupCellGrid(
    context: Context,
    private val capRowPx: Int,
    private val maxCols: Int,
    private val icons: Boolean,
    private val margin: Int,
    /** Khe nhãn–số dạng ngang (px, từ thang [KachiSpace]). */
    private val sideGap: Int,
) : ViewGroup(context), TrimArea {

    /** Một ô con: [root] chứa ([icon]) · [labelView] · [value] theo đúng thứ tự đó. */
    class Cell(val root: LinearLayout, val icon: ImageView?, val labelView: TextView, val value: TextView) {
        internal var option: GroupCellFit.Option? = null
        internal var columns: GroupCellFit.Columns? = null
    }

    private val cells = ArrayList<Cell>()
    private var override: Int? = null
    private var lastPx = 0
    private var floorPx = 0
    override var overflowPx = 0
        private set
    private var sig: String? = null
    /** Chữ số rộng nhất của phông giá trị — đo MỘT lần (phông không đổi trong đời ô). */
    private var widest: Char? = null
    private var needs: List<GroupCellFit.Need> = emptyList()
    private var metrics: GroupCellFit.Metrics? = null
    private var plan: GroupCellFit.Plan? = null
    private var rowPx = IntArray(0)
    private var slotW = 0

    /** Gói đang hiện (cho QA / bài canh). */
    val shownCount: Int get() = plan?.shown ?: cells.size

    // WrongConstant: lint đòi hằng LineBreaker.* còn ta dùng Layout.BREAK_STRATEGY_SIMPLE — giá trị y hệt (alias
    // hợp lệ của cùng API), lint nhầm. Không đổi giá trị.
    @SuppressLint("WrongConstant")
    fun add(cell: Cell) {
        // Ngắt THAM LAM ở dấu cách, không gạch nối — đúng giả định của [GroupCellFit.twoLineWidth] (mặc định của TextView
        // là HIGH_QUALITY + gạch nối: có thể ngắt giữa từ để cân dòng).
        cell.labelView.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        cell.labelView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        cells += cell
        addView(cell.root, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    override fun resetTrim() { override = null }

    override fun trimBy(px: Int): Boolean {
        if (px <= 0 || lastPx <= floorPx) return false
        override = maxOf(lastPx - px, floorPx)
        return true
    }

    /** Dấu chữ: nhãn + giá trị đã thay chữ số bằng chữ số rộng nhất — đổi ⇒ đo lại nhu cầu. */
    private fun signature(widest: Char): String = buildString {
        cells.forEach { append(it.labelView.text).append('\u0001').append(FitValues.headroom(it.value.text, widest)).append('\u0002') }
    }

    /**
     * Gọi SAU khi đổ chữ tại chỗ (nhịp xe): cột số/nhãn có bề rộng TĨNH nên `setText` không tự xin đo lại ⇒ so dấu chữ
     * ([signature] — chỉ dựng chuỗi, KHÔNG đo `Paint`) và xin một lượt đo khi định dạng đổi (`—` → `45678 km`). Chữ số đổi
     * (`1:25` → `1:26`) không đổi dấu ⇒ không đo. [ĐO] thiếu bước này: số giả hiện `13.8 V` thành `…` (cột số còn bề rộng `—`).
     */
    fun recheck() {
        val w = widest ?: return    // chưa đo lần nào ⇒ lượt đo đầu sẽ tự lo
        if (signature(w) != sig) requestLayout()
    }

    private fun refreshNeeds() {
        if (cells.isEmpty()) return
        val vp = cells[0].value.paint
        val widest = this.widest ?: FitValues.widestDigit { vp.measureText(it.toString()) }.also { this.widest = it }
        val s = signature(widest)
        if (s == sig && metrics != null) return
        sig = s
        needs = cells.map { c ->
            val lp = c.labelView.paint
            val text = c.labelView.text.toString()
            val words = text.split(' ').filter { it.isNotEmpty() }.map { ceil(lp.measureText(it).toDouble()).toInt() }
            GroupCellFit.Need(
                label1 = FitValues.needPx(lp.measureText(text)),
                label2 = GroupCellFit.twoLineWidth(words, ceil(lp.measureText(" ").toDouble()).toInt()) + 1,
                value = FitValues.needPx(c.value.paint.measureText(FitValues.headroom(c.value.text, widest))),
            )
        }
        val c0 = cells[0]
        val icon = c0.icon?.let { (it.layoutParams as? LinearLayout.LayoutParams)?.height ?: 0 } ?: 0
        metrics = GroupCellFit.Metrics(
            labelLine = lineH(c0.labelView.paint, 1), labelLine2 = lineH(c0.labelView.paint, 2),
            valueLine = lineH(c0.value.paint, 1), icon = icon,
            padX = c0.root.paddingLeft, padY = c0.root.paddingTop,
            inner = (c0.icon?.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin ?: c0.root.paddingLeft,
            margin = margin, sideGap = sideGap,
        )
    }

    /**
     * Chiều cao [n] dòng chữ của `TextView` mặc định (`includeFontPadding = true`): dòng đầu/cuối theo `top`/`bottom`, dòng
     * giữa theo `ascent`/`descent` — cách `StaticLayout` cộng (đo từ phông thật, không hằng).
     */
    private fun lineH(p: Paint, n: Int): Int {
        val fm = p.fontMetricsInt
        return (fm.bottom - fm.top) + (n - 1) * (fm.descent - fm.ascent)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        refreshNeeds()
        val m = metrics
        if (m == null || w <= 0) { setMeasuredDimension(w, 0); lastPx = 0; overflowPx = 0; return }
        val p = GroupCellFit.plan(needs, w, override, m, maxCols, icons)
        plan = p
        floorPx = GroupCellFit.plan(needs.take(1), w, null, m, 1, icons).heightPx
        apply(p)
        val rows = p.rowCounts.size
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        var bound = capRowPx * rows
        if (mode != MeasureSpec.UNSPECIFIED) bound = minOf(bound, MeasureSpec.getSize(heightMeasureSpec))
        override?.let { bound = minOf(bound, it) }
        val target = maxOf(p.heightPx, bound)
        overflowPx = if (mode == MeasureSpec.UNSPECIFIED) 0 else (target - MeasureSpec.getSize(heightMeasureSpec)).coerceAtLeast(0)
        slotW = if (p.cols > 0) w / p.cols else w
        rowPx = IntArray(rows) { target / rows + if (it == rows - 1) target % rows else 0 }
        var i = 0
        p.rowCounts.forEachIndexed { r, n ->
            repeat(n) {
                cells[i++].root.measure(
                    MeasureSpec.makeMeasureSpec((slotW - 2 * margin).coerceAtLeast(0), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((rowPx[r] - 2 * margin).coerceAtLeast(0), MeasureSpec.EXACTLY),
                )
            }
        }
        setMeasuredDimension(w, target)
        lastPx = target
    }

    /** Hiện [GroupCellFit.Plan.shown] mục đầu ở dạng của kế hoạch; đổi dạng chỉ khi khác lượt trước (không cấp phát). */
    private fun apply(p: GroupCellFit.Plan) {
        cells.forEachIndexed { i, c ->
            val show = i < p.shown
            c.root.visibility = if (show) View.VISIBLE else View.GONE
            if (show && (c.option != p.option || c.columns != p.columns)) { setForm(c, p.option, p.columns); c.option = p.option; c.columns = p.columns }
        }
    }

    /**
     * Áp dạng. NGANG ⇒ hai cột DÙNG CHUNG cả lưới ([GroupCellFit.Columns]): nhãn canh phải sát số, số canh trái, cả cặp
     * canh giữa ô — mọi hàng một trục số (owner 13/10: *"chỉ số lệch phải, nằm chơi vơi"*). DỌC ⇒ nhãn trên, số dưới, canh
     * giữa. Ghi thẳng vào `LayoutParams` (không `setLayoutParams` — đang trong lượt đo, con được đo ngay sau đó).
     */
    private fun setForm(c: Cell, o: GroupCellFit.Option, cols: GroupCellFit.Columns?) {
        val side = o.form == GroupCellFit.Form.SIDE && cols != null
        c.root.orientation = if (side) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        c.icon?.visibility = if (o.icon) View.VISIBLE else View.GONE
        c.labelView.maxLines = o.lines
        // R22 (brief Kim §2.2): hàng NGANG = nhãn ĐẦU · số CUỐI trên cả bề ngang ô con (bảng hai cột như thẻ trạng thái xe),
        // thay cho cặp nhãn-sát-số canh giữa của W2 — hết hộp nền thì cặp canh giữa trôi lơ lửng, còn hàng trải ngang đọc
        // thành một bảng; mọi số vẫn thẳng một trục (mép phải chung). Bề rộng cột số giữ [GroupCellFit.Columns.valuePx].
        c.labelView.gravity = if (side) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
        c.value.gravity = if (side) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.CENTER
        (c.labelView.layoutParams as? LinearLayout.LayoutParams)?.apply {
            width = if (side) 0 else LayoutParams.MATCH_PARENT; weight = if (side) 1f else 0f
        }
        (c.value.layoutParams as? LinearLayout.LayoutParams)?.apply {
            width = if (side) cols!!.valuePx else LayoutParams.WRAP_CONTENT
            // `leftMargin` cùng lúc: `marginStart` chỉ được quy ra trái/phải ở `resolveLayoutDirection` (lúc `setLayoutParams`),
            // ghi thẳng vào LP thì không — [ĐO] khe 0 px `Trong xe22 °C`. Năm ngôn ngữ của app đều trái→phải.
            marginStart = if (side) sideGap else 0; leftMargin = marginStart
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val p = plan ?: return
        var i = 0
        var y = 0
        p.rowCounts.forEachIndexed { row, n ->
            // Hàng thiếu ô ⇒ CANH GIỮA, cùng bề rộng ô hàng đủ (R22 owner 13/10 "rối": hàng cuối bám trái để lại một ô
            // trống trông như hỏng).
            val lead = (p.cols - n) * slotW / 2
            for (j in 0 until n) {
                val v = cells[i++].root
                val x = lead + j * slotW + margin
                v.layout(x, y + margin, x + v.measuredWidth, y + margin + v.measuredHeight)
            }
            y += rowPx[row]
        }
    }
}

/** Mọi `TextView` đang hiện trong cây [v] (theo thứ tự cây). */
internal fun tileTexts(v: View, out: MutableList<TextView>) {
    if (v.visibility == View.GONE) return
    if (v is TextView) out += v
    if (v is ViewGroup) for (i in 0 until v.childCount) tileTexts(v.getChildAt(i), out)
}

/** Dấu chữ của mọi nút con của [g] — đổi ⇒ phải đo lại nhu cầu bề rộng (nút đổi chữ theo trạng thái). */
internal fun tileSignature(g: ViewGroup): String = buildString {
    for (i in 0 until g.childCount) { val l = ArrayList<TextView>(); tileTexts(g.getChildAt(i), l); l.forEach { append(it.text).append('\u0001') }; append('\u0002') }
}

/**
 * Bề rộng một nút cần: chữ dài nhất trong nút (≤ 2 dòng nếu nút cho 2 dòng, 1 dòng nếu `maxLines = 1`) + mọi lề/đệm từ
 * chữ ra tới mép nút, không dưới [minTouchPx]. Dùng chung cho [GroupActionGrid] và bảng vật × việc (R22).
 */
@SuppressLint("WrongConstant")   // Layout.BREAK_STRATEGY_SIMPLE = alias hợp lệ của LineBreaker.*; lint nhầm (API 29).
internal fun tileNeedPx(tile: View, minTouchPx: Int): Int {
    val l = ArrayList<TextView>(); tileTexts(tile, l)
    var need = maxOf(minTouchPx, tile.minimumWidth)
    l.forEach { tv ->
        tv.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        tv.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        val p = tv.paint
        val text = tv.text.toString()
        val words = text.split(' ').filter { it.isNotEmpty() }.map { ceil(p.measureText(it).toDouble()).toInt() }
        val textW = if (tv.maxLines >= 2) GroupCellFit.twoLineWidth(words, ceil(p.measureText(" ").toDouble()).toInt()) + 1
        else FitValues.needPx(p.measureText(text))
        var chrome = 0
        var v: View? = tv
        while (v != null) {
            chrome += v.paddingLeft + v.paddingRight
            if (v === tile) break
            (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { chrome += it.leftMargin + it.rightMargin }
            v = v.parent as? View
        }
        need = maxOf(need, maxOf(textW, tv.minWidth) + chrome)
    }
    return need
}

/**
 * HÀNG NÚT của ô nhóm — số cột theo bề rộng đo được: mỗi nút phải đủ chỗ cho nhãn của nó ở ≤ 2 dòng ngắt ở dấu cách
 * (đo `Paint` thật) và không dưới đích chạm [minTouchPx] ([GroupCellFit.actionCols]). Thay `LinearLayout` chia cố định 6
 * cột ([ĐO] *Kính* 267 px: nút 32 px, nhãn `Kính lái` thành `Kí/n…`).
 *
 * Nút vẫn dựng MỘT lần (không bao giờ tháo/gắn khi làm mới — SOÁT P1-1); đổi cỡ chỉ đặt lại vị trí. Đo nhu cầu nhãn chỉ khi
 * chữ của nút đổi (dấu [signature]) — nút đổi chữ theo trạng thái thì đo lại một lượt, không theo nhịp.
 */
@SuppressLint("ViewConstructor")
internal class GroupActionGrid(
    context: Context,
    private val maxCols: Int,
    private val margin: Int,
    private val minTouchPx: Int,
) : ViewGroup(context) {

    private var sig: String? = null
    private var minTile = 0
    private var counts: List<Int> = emptyList()
    private var slotW = 0
    private var rowPx = IntArray(0)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val n = childCount
        val s = tileSignature(this)
        if (s != sig) { sig = s; minTile = (0 until n).maxOfOrNull { tileNeedPx(getChildAt(it), minTouchPx) } ?: 0 }
        val cols = GroupCellFit.actionCols(n, w, minTile, margin, maxCols)
        counts = GridFit.rowCounts(n, cols, GridFit.RowSplit.BALANCED)
        slotW = if (cols > 0) w / cols else w
        val cw = MeasureSpec.makeMeasureSpec((slotW - 2 * margin).coerceAtLeast(0), MeasureSpec.EXACTLY)
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        rowPx = IntArray(counts.size)
        var i = 0
        counts.forEachIndexed { r, k ->
            // Mọi nút trong hàng cùng cao (cao nhất hàng) — nút nhãn 1 dòng không lùn hơn nút cạnh nó.
            val from = i
            repeat(k) { getChildAt(i++).measure(cw, free) }
            rowPx[r] = (from until i).maxOf { getChildAt(it).measuredHeight }
            val ch = MeasureSpec.makeMeasureSpec(rowPx[r], MeasureSpec.EXACTLY)
            for (j in from until i) getChildAt(j).measure(cw, ch)
        }
        setMeasuredDimension(w, paddingTop + paddingBottom + rowPx.sum())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var i = 0
        var y = paddingTop
        val cols = counts.maxOrNull() ?: 0
        counts.forEachIndexed { row, k ->
            val lead = (cols - k) * slotW / 2   // R22: hàng ngắn canh giữa (không để ô trống lẻ ở cuối)
            for (j in 0 until k) {
                val v = getChildAt(i++)
                val x = lead + j * slotW + margin
                v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight)
            }
            y += rowPx[row]
        }
    }
}
