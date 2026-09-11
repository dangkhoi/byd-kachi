package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * BẢNG CẢNH BÁO HAI BÊN XE (kiểm toán UX mục 4c) — hình xe nhìn từ trên, các cảnh báo **bên trái** xếp thành cột
 * bên trái thân xe, **bên phải** xếp bên phải; mục không thuộc bên nào xuống dòng chân bảng.
 *
 * ## Vì sao nó tồn tại — [ĐO] ảnh máy ảo 2026-09-12
 * Nhóm *An toàn · ADAS* có 10 thành viên, trong đó **8/10 mang CÙNG một icon sóng radar** (`bsd_*`, `lca_*`,
 * `rcta_*`, `dow_*` đều tra ra `ic-radar`). Trên dải STRIP, tám ô con vì thế trông **y hệt nhau** và icon không giúp
 * phân biệt gì; nhãn thì bị cắt thành *"Điểm mù trư…"* / *"Chuyển làn tr…"* nên cũng không phân biệt được. Icon ở đó
 * không mang thông tin — nó chỉ chiếm chỗ của thứ mang thông tin.
 *
 * Thay vì tám hình giống nhau, **vị trí** trả lời đúng câu người lái hỏi (*"có gì bên cạnh tôi không, bên nào?"*).
 * Đây là cùng khuôn với [RadarBoardView] / [TyreBoardView]: thân xe ở giữa, dữ liệu áp sát đúng phía.
 *
 * ## Quy ước vẽ — bám nguyên hai bảng BOARD đã có
 *  • Mọi `Paint` cấp phát MỘT LẦN ở field, màu phân giải MỘT LẦN — KHÔNG cấp phát/parse trong [onDraw].
 *  • Mọi cỡ tính theo cạnh nhỏ nhất / bề cao ⇒ bất biến với cỡ ô, và **không bao giờ tràn** ra ngoài khung (đó là
 *    lý do bảng này là Canvas chứ không phải lưới View: số hàng đổi thì hàng tự co).
 *  • Ô vẽ **không tự quyết định gì**: nội dung, sắc thái và **phía** đều do `:core` ([GroupBoard]) quyết định.
 */
internal class SideBoardView(context: Context) : View(context) {

    private var left: List<GroupCell> = emptyList()
    private var right: List<GroupCell> = emptyList()
    private var footer: String = ""

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor(KachiTheme.MUT2)
    }
    private val cellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor(KachiTheme.CARD2)
    }
    private val cellStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val valueP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor(KachiTheme.MUT)
    }
    private val midP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor(KachiTheme.MUT)
    }
    private val body = RectF()
    private val cell = RectF()

    private val colInk = Color.parseColor(KachiTheme.INK)
    private val colMut = Color.parseColor(KachiTheme.MUT)
    private val colMut2 = Color.parseColor(KachiTheme.MUT2)
    private val colAmber = Color.parseColor(KachiTheme.AMBER)
    private val colRed = Color.parseColor(KachiTheme.RED)

    /** Đặt dữ liệu. [footer] là chuỗi đã dựng ở chỗ gọi (các mục không thuộc bên nào). */
    fun set(left: List<GroupCell>, right: List<GroupCell>, footer: String) {
        this.left = left
        this.right = right
        this.footer = footer
        invalidate()
    }

    private fun tint(tone: GroupTone): Int = when (tone) {
        GroupTone.ALERT -> colRed
        GroupTone.WARN -> colAmber
        GroupTone.ACTIVE, GroupTone.NEUTRAL -> colInk
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val m = minOf(w, h)
        val rows = maxOf(left.size, right.size)
        if (rows == 0) return

        outline.strokeWidth = m * 0.018f
        cellStroke.strokeWidth = m * 0.012f
        valueP.textSize = m * 0.075f
        labelP.textSize = m * 0.058f
        midP.textSize = m * 0.062f

        // Thân xe chiếm dải giữa; hai cột dữ liệu áp sát nó. Thân CAO gần hết khung vì các hàng nằm dọc theo nó —
        // hàng đầu là cảnh báo phía trước, hàng cuối phía sau, đúng thứ tự khai của nhóm.
        val hasFooter = footer.isNotEmpty()
        val bottom = if (hasFooter) h * 0.88f else h
        val bodyW = w * 0.16f
        val bodyH = (bottom - h * 0.04f) * 0.94f
        val cy = h * 0.04f + (bottom - h * 0.04f) / 2f
        body.set((w - bodyW) / 2f, cy - bodyH / 2f, (w + bodyW) / 2f, cy + bodyH / 2f)
        val r = bodyW * 0.34f
        canvas.drawRoundRect(body, r, r, outline)
        canvas.drawLine(
            body.left + bodyW * 0.16f, body.top + bodyH * 0.16f,
            body.right - bodyW * 0.16f, body.top + bodyH * 0.16f, outline,
        )

        val gap = m * 0.03f
        val pad = w * 0.02f
        val rowH = bodyH / rows
        val cellH = rowH * 0.82f
        drawColumn(canvas, left, rows, pad, body.left - gap, cellH, rowH)
        drawColumn(canvas, right, rows, body.right + gap, w - pad, cellH, rowH)

        if (hasFooter) {
            midP.color = colMut
            canvas.drawText(footer, w / 2f, h * 0.97f, midP)
        }
    }

    /** Một cột dữ liệu: [items] xếp từ trên xuống, mỗi hàng cao [rowH], ô cao [cellH] canh giữa hàng. */
    private fun drawColumn(
        canvas: Canvas,
        items: List<GroupCell>,
        rows: Int,
        x0: Float,
        x1: Float,
        cellH: Float,
        rowH: Float,
    ) {
        items.forEachIndexed { i, c ->
            val cyRow = body.top + rowH * (i + 0.5f)
            cell.set(x0, cyRow - cellH / 2f, x1, cyRow + cellH / 2f)
            val radius = cellH * 0.22f
            canvas.drawRoundRect(cell, radius, radius, cellFill)
            cellStroke.color = if (c.tone == GroupTone.NEUTRAL) colMut2 else tint(c.tone)
            canvas.drawRoundRect(cell, radius, radius, cellStroke)
            // GIÁ TRỊ trên, NHÃN dưới và nhỏ hơn — cùng thứ bậc với ô con của dải (kiểm toán mục 3: giá trị là thứ
            // to nhất). Nhãn KHÔNG bị làm mờ dù chưa đọc được số (kiểm toán mục 2).
            valueP.color = tint(c.tone)
            valueP.alpha = if (c.available) OPAQUE else DIM
            canvas.drawText(c.value, cell.centerX(), cyRow - labelP.textSize * 0.15f, valueP)
            labelP.color = colMut
            canvas.drawText(c.label, cell.centerX(), cyRow + valueP.textSize * 0.85f, labelP)
        }
        // Hàng thiếu ở một bên: KHÔNG vẽ gì (vòng lặp trên tự dừng). Cố ý **không** vẽ ô rỗng cho chỗ thiếu — ô rỗng
        // ở đây đọc thành *"bên này an toàn"*, mà thật ra là *"bên này không có cảm biến loại đó"*; hai nghĩa khác
        // nhau và không được để người lái lẫn. ([rows] chỉ dùng để chia chiều cao hàng cho hai cột thẳng nhau.)
    }

    private companion object {
        /** `Paint.alpha` theo thang 0..255 (khác `View.alpha` 0..1f) — trị số mờ của GIÁ TRỊ chưa đọc được. */
        const val DIM = 128
        const val OPAQUE = 255
    }
}
