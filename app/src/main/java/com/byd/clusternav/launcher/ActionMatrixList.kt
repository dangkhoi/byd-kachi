package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * 3.01 R22 — DANH SÁCH GỌN của bảng vật × việc: dạng lùi CHỈ khi ô dưới ngưỡng hình xe ([ActionMatrixCompact]).
 *
 * Một hàng một vật: `nhãn · số · [việc 1] [việc 2]`, nút canh phải nên mọi cột nút thẳng hàng dọc; hàng cả-xe và
 * hàng chân (nút lẻ) ở cuối. Hình dạng do [GroupActionMatrix.plan] (`:core`, test off-car) quyết theo số đo `Paint` thật:
 * số cạnh nhãn → số dưới nhãn → bớt cột từ phải → kẹp nhãn; ô rộng-mà-thấp chia khối cạnh nhau; thiếu cao thì bỏ hàng
 * từ cuối (chân giữ). Nhãn nút KHÔNG xuống dòng; không có ô trống lẻ (hàng chân canh giữa).
 */
@SuppressLint("ViewConstructor")
internal class ActionMatrixList(context: Context, private val mx: ActionMatrix, private val kit: MatrixKit) : ViewGroup(context) {

    private class Row(val labelView: TextView, val value: TextView?, val tiles: List<View?>)

    private val rows: List<Row> = mx.rows.map { r ->
        Row(
            kit.caption(r.title),
            if (mx.hasValues) kit.value(r.cell) else null,
            r.slots.map { a -> a?.let { kit.tile(it, it.verb, icon = false) } },
        )
    }
    private val footer: List<View> = mx.footer.map { kit.tile(it, null, icon = false) }

    private val gap = kit.px(Sp.S)
    private val blockGap = kit.px(Sp.L)
    private var plan: GroupActionMatrix.Plan? = null
    private var buttonPx = 0
    private var footerNeed = IntArray(0)

    init {
        rows.forEach { r -> addView(r.labelView); r.value?.let { addView(it) }; r.tiles.forEach { t -> t?.let { addView(it) } } }
        footer.forEach { addView(it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) null else MeasureSpec.getSize(heightMeasureSpec)
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        rows.forEach { r -> r.labelView.measure(free, free); r.value?.measure(free, free) }
        val touch = kit.px(Sp.TOUCH)
        buttonPx = rows.flatMap { it.tiles }.filterNotNull().maxOfOrNull { tileNeedPx(it, touch) } ?: touch
        footerNeed = IntArray(footer.size) { tileNeedPx(footer[it], touch) }
        val m = GroupActionMatrix.Metrics(
            labelPx = rows.maxOf { it.labelView.measuredWidth },
            valuePx = rows.maxOf { it.value?.measuredWidth ?: 0 },
            labelLine = rows.maxOf { it.labelView.measuredHeight },
            valueLine = rows.maxOf { it.value?.measuredHeight ?: 0 },
            buttonPx = buttonPx,
            footerPx = if (footer.isEmpty()) 0 else footerNeed.sum() + gap * (footer.size - 1),
            touchPx = kit.rowPx, gap = gap, blockGap = blockGap,
        )
        val p = GroupActionMatrix.plan(rows.size, mx.verbs.size, footer.isNotEmpty(), w, h, m)
        plan = p
        val zero = MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY)
        val tileH = MeasureSpec.makeMeasureSpec(kit.px(Sp.TOUCH), MeasureSpec.EXACTLY)
        val btn = MeasureSpec.makeMeasureSpec(buttonPx, MeasureSpec.EXACTLY)
        val blockW = blockWidth(w, p)
        val leftW = (blockW - p.cols * (buttonPx + gap)).coerceAtLeast(0)
        rows.forEachIndexed { i, r ->
            val shown = i < p.rowsShown
            val valueW = if (r.value == null) 0 else if (p.stacked) leftW else minOf(r.value.measuredWidth.coerceAtLeast(m.valuePx), leftW)
            val labelW = if (p.stacked || r.value == null) leftW else (leftW - gap - m.valuePx).coerceAtLeast(0)
            r.value?.gravity = (if (p.stacked) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            r.labelView.measure(if (shown) MeasureSpec.makeMeasureSpec(labelW, MeasureSpec.EXACTLY) else zero, free)
            r.value?.measure(if (shown) MeasureSpec.makeMeasureSpec(valueW, MeasureSpec.EXACTLY) else zero, free)
            r.tiles.forEachIndexed { c, t -> t?.measure(if (shown && c < p.cols) btn else zero, if (shown && c < p.cols) tileH else zero) }
        }
        val fw = footerNeed.maxOrNull() ?: 0
        footer.forEach { it.measure(MeasureSpec.makeMeasureSpec(fw, MeasureSpec.EXACTLY), tileH) }
        setMeasuredDimension(w, p.perBlock * p.rowPx)
    }

    private fun blockWidth(w: Int, p: GroupActionMatrix.Plan): Int = (w - (p.blocks - 1) * blockGap) / p.blocks

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val p = plan ?: return
        val blockW = blockWidth(r - l, p)
        var slot = 0
        fun origin(s: Int): Pair<Int, Int> = (s / p.perBlock) * (blockW + blockGap) to (s % p.perBlock) * p.rowPx
        val tilePad = (p.rowPx - kit.px(Sp.TOUCH)) / 2
        rows.forEachIndexed { i, row ->
            if (i >= p.rowsShown) { hide(row.labelView); row.value?.let(::hide); row.tiles.forEach { it?.let(::hide) }; return@forEachIndexed }
            val (x0, y0) = origin(slot++)
            val right = x0 + blockW
            // Nút canh PHẢI ⇒ cột c ở cùng một x trên mọi hàng (cột thẳng dọc kể cả khi một vật thiếu một việc).
            row.tiles.forEachIndexed { c, tile ->
                if (tile == null) return@forEachIndexed
                if (c >= p.cols) { hide(tile); return@forEachIndexed }
                val x = right - (p.cols - c) * (buttonPx + gap) + gap
                tile.layout(x, y0 + tilePad, x + tile.measuredWidth, y0 + tilePad + tile.measuredHeight)
            }
            val lw = row.labelView.measuredWidth; val lh = row.labelView.measuredHeight
            val v = row.value
            if (v == null || !p.stacked) {
                val ly = y0 + (p.rowPx - lh) / 2
                row.labelView.layout(x0, ly, x0 + lw, ly + lh)
                v?.let {
                    val vx = right - p.cols * (buttonPx + gap) - it.measuredWidth
                    val vy = y0 + (p.rowPx - it.measuredHeight) / 2
                    it.layout(vx, vy, vx + it.measuredWidth, vy + it.measuredHeight)
                }
            } else {
                val top = y0 + (p.rowPx - lh - v.measuredHeight) / 2
                row.labelView.layout(x0, top, x0 + lw, top + lh)
                v.layout(x0, top + lh, x0 + v.measuredWidth, top + lh + v.measuredHeight)
            }
        }
        if (footer.isEmpty()) return
        // Chân bảng: các nút lẻ CANH GIỮA khối (không bám trái để lại một khoảng trống trông như thiếu nút).
        val (x0, y0) = origin(slot)
        val total = footer.sumOf { it.measuredWidth } + gap * (footer.size - 1)
        var x = x0 + (blockW - total) / 2
        footer.forEach { f -> f.layout(x, y0 + tilePad, x + f.measuredWidth, y0 + tilePad + f.measuredHeight); x += f.measuredWidth + gap }
    }

    private fun hide(v: View) = v.layout(0, 0, 0, 0)
}
