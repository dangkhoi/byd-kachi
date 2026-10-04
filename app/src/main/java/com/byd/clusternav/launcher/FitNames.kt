package com.byd.clusternav.launcher

import android.view.View
import android.widget.TextView

/**
 * ═══ J1 (2.87, QA2 04/10) — chọn CHỮ TÊN cho từng ô của một lưới widget, trên bố cục THẬT ════════════════════════════
 *
 * Tầng vẽ của [FitLabels] (`:core` — luật thuần, `FitLabelsTest`). Sau khi [FitGridLayout] đã áp `k` + dạng và đo mọi ô
 * đúng cỡ ô, nếu có chữ tên bị cắt, hai ô cùng hiện một chữ, hoặc lưới đang ở dạng NHÃN NGẮN: với từng ô, thử từng cách
 * hiện nó có ([FitLabels.variants] — nhãn đầy/ngắn × cắt cuối/đầu), đo ô ở đúng cỡ ô, đọc chữ THẤY được từ `Layout`
 * (`getEllipsisStart/Count` — cùng nguồn AOSP vẽ `…`), rồi để [FitLabels.choose] quyết. Không dựng view nào, chỉ đổi chữ
 * + chỗ `…` qua [FitScale.variant] (ghi khi đổi thật).
 *
 * Chi phí: không làm gì khi mọi chữ tên hiện trọn và khác nhau (lưới đọc được — ca thường); ngược lại ≤ 4 lượt đo một ô
 * mỗi lượt khớp (không theo nhịp 1 Hz — chỉ trong [FitGridLayout.refit]).
 */
internal object FitNames {

    /** Một ô của lưới: view gốc + bộ áp của nó. */
    class Cell(val view: View, val fs: FitScale)

    /**
     * Chạy luật chữ tên trên [cells] (đã áp + đo ở cỡ ô [cw]×[ch]); [short] = lưới đang ở dạng nhãn NGẮN (ô nào nhãn đầy
     * vừa thì về nhãn đầy — [FitLabels.base]). Trả `true` nếu đã chạy (cách hiện các ô có thể đã đổi ⇒ chỗ gọi đo lại).
     */
    fun pick(cells: List<Cell>, cw: Int, ch: Int, short: Boolean): Boolean {
        val now = cells.map { shown(it.fs) }
        val texts = now.mapNotNull { it?.text?.takeIf(String::isNotBlank) }
        if (!short && texts.size == texts.toSet().size && now.none { it?.cut == true }) return false
        val tiles = cells.map { c -> options(c, cw, ch) }
        val pick = FitLabels.choose(tiles)
        cells.forEachIndexed { i, c -> pick[i]?.let { c.fs.variant(it) } }
        return true
    }

    /** Chữ thấy được của ô dưới từng cách hiện nó có (để ô ở cách hiện cuối — chỗ gọi áp lại cách đã chọn). */
    private fun options(c: Cell, cw: Int, ch: Int): Map<FitLabels.Variant, FitLabels.Shown>? {
        if (shown(c.fs) == null) return null
        return FitLabels.variants(c.fs.hasShort(), c.fs.oneLine()).associateWith { v ->
            c.fs.variant(v)
            c.fs.forceAll()
            c.view.measure(
                View.MeasureSpec.makeMeasureSpec(cw.coerceAtLeast(0), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ch.coerceAtLeast(0), View.MeasureSpec.EXACTLY),
            )
            shown(c.fs) ?: FitLabels.Shown("", cut = false)
        }
    }

    /** Chữ THẤY được + có bị cắt của mọi chữ tên đang hiện của ô; `null` = ô không có chữ tên nào đang hiện. */
    fun shown(fs: FitScale): FitLabels.Shown? {
        val ns = fs.names().filter { fs.visibleInTile(it) && it.text.isNotEmpty() }
        if (ns.isEmpty()) return null
        return FitLabels.Shown(ns.joinToString("\n") { visible(it) }, ns.any { FitProbe.cut(it) })
    }

    /** Chữ thấy được của [tv] theo bố cục vừa đo: các dòng đang hiện, phần bị `…` thay bằng `…`. */
    private fun visible(tv: TextView): String {
        val l = tv.layout ?: return tv.text.toString()
        val n = l.lineCount
        val shownLines = if (tv.maxLines in 1 until n) tv.maxLines else n
        return FitLabels.visible(
            tv.text,
            (0 until shownLines).map { i -> FitLabels.Line(l.getLineStart(i), l.getLineEnd(i), l.getEllipsisStart(i), l.getEllipsisCount(i)) },
        )
    }
}
