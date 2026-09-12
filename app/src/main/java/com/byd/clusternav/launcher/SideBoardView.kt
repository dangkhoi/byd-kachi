package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiSpace as Sp

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
 * ## ⚠⚠ [KIỂM TOÁN 2026-09-12 mục 2] CỠ CHỮ CÓ SÀN — SỐ HÀNG THÌ CO
 * Bản đầu xếp mỗi hàng thành HAI dòng (giá trị trên, nhãn dưới) và chia bề cao cho **4 hàng mỗi bên** bất kể ô cao
 * bao nhiêu. [ĐO] ở khung 4/12 màn (`m = 231px`): nhãn ra `231 × 0.058 = 13.4px` ⇒ **nét cao 13px**, dưới chuẩn G1
 * (15–16px). Tức bảng tự bóp chữ để nhồi cho đủ hàng, mà người đang lái thì không đọc được — đúng thứ kiểm toán gọi
 * là *"nhét 10 nhãn bằng chữ nhỏ"*.
 *
 * Ba việc đổi, theo đúng thứ tự ưu tiên *"đọc được trước, đủ hàng sau"*:
 *  1. **Một hàng = MỘT dòng** (`nhãn · số` xếp ngang, cùng lối `stripCell` khi không có icon) ⇒ một hàng chỉ cần
 *     [KachiSpace.BOARD_ROW_MIN] thay vì gấp đôi.
 *  2. **Cỡ chữ = `max(tỉ lệ, sàn)`** ([KachiSpace.BOARD_LABEL_MIN] / [KachiSpace.BOARD_VALUE_MIN]) — tỉ lệ vẫn giữ
 *     cho ô to (chữ lớn theo ô), sàn chặn ca ô nhỏ.
 *  3. **Số hàng do chỗ quyết định**: `:app` đếm được mấy hàng ở cỡ chữ sàn rồi hỏi `:core`
 *     ([GroupBoard.sidePlan]) *"vậy hiện cái gì"* — cảnh báo trước, phần còn lại **đếm** ở dòng chân, và nếu không
 *     có cảnh báo nào thì MỘT câu nói thẳng trạng thái.
 *
 * ## Quy ước vẽ — bám nguyên hai bảng BOARD đã có
 *  • Mọi `Paint` cấp phát MỘT LẦN ở field, màu phân giải MỘT LẦN — KHÔNG cấp phát/parse trong [onDraw].
 *  • Mọi cỡ tính theo cạnh nhỏ nhất / bề cao ⇒ bất biến với cỡ ô, và **không bao giờ tràn** ra ngoài khung (đó là
 *    lý do bảng này là Canvas chứ không phải lưới View: số hàng đổi thì hàng tự co).
 *  • Ô vẽ **không tự quyết định gì**: nội dung, sắc thái, **phía** và **hiện-cái-gì-khi-hẹp** đều do `:core` quyết.
 */
internal class SideBoardView(context: Context) : View(context) {

    private var model: GroupBoardModel? = null
    private var footer: String = ""

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor(KachiTheme.MUT2)
    }
    private val cellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor(KachiTheme.CARD2)
    }
    private val cellStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val valueP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT; isFakeBoldText = true
    }
    private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; color = Color.parseColor(KachiTheme.MUT)
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

    /** SÀN cỡ chữ quy ra pixel — tính một lần (density không đổi trong đời một View). */
    private val labelFloorPx = Sp.dpf(context, Sp.BOARD_LABEL_MIN)
    private val valueFloorPx = Sp.dpf(context, Sp.BOARD_VALUE_MIN)
    private val rowFloorPx = Sp.dp(context, Sp.BOARD_ROW_MIN)

    /** SÀN khe nhãn ↔ thân xe — cùng lý do tính một lần như ba sàn trên (xem [onDraw]). */
    private val gapFloorPx = Sp.dpf(context, Sp.S)

    /**
     * Đặt dữ liệu.
     *
     * Nhận **cả model** (không phải hai danh sách đã cắt sẵn) vì phép chọn *"hiện cái gì khi hẹp"* cần biết mọi ô con
     * và chỉ chạy được khi đã biết bề cao ⇒ nó nằm trong [onDraw] chứ không nằm ở chỗ gọi. [footer] vẫn do chỗ gọi
     * dựng (nó là các ô con KHÔNG thuộc bên nào — cùng quy ước với chân bảng radar).
     */
    fun set(model: GroupBoardModel, footer: String) {
        this.model = model
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
        val m = model ?: return
        if (w <= 0f || h <= 0f) return
        val min = minOf(w, h)

        outline.strokeWidth = min * OUTLINE_RATIO
        cellStroke.strokeWidth = min * STROKE_RATIO
        // SÀN đứng trước tỉ lệ: ô to thì tỉ lệ thắng (chữ lớn theo ô), ô nhỏ thì sàn thắng (chữ vẫn đọc được).
        valueP.textSize = maxOf(min * VALUE_RATIO, valueFloorPx)
        labelP.textSize = maxOf(min * LABEL_RATIO, labelFloorPx)
        midP.textSize = maxOf(min * MID_RATIO, labelFloorPx)

        // Thân xe chiếm dải giữa; hai cột dữ liệu áp sát nó. Thân CAO gần hết khung vì các hàng nằm dọc theo nó —
        // hàng đầu là cảnh báo phía trước, hàng cuối phía sau, đúng thứ tự khai của nhóm.
        val bottom = if (footer.isNotEmpty()) h * FOOTER_TOP else h
        val bodyW = w * BODY_W_RATIO
        val top = h * BODY_INSET
        val bodyH = (bottom - top) * BODY_H_RATIO
        val cy = top + (bottom - top) / 2f
        body.set((w - bodyW) / 2f, cy - bodyH / 2f, (w + bodyW) / 2f, cy + bodyH / 2f)

        // Mấy hàng còn vẽ được **ở cỡ chữ sàn** — rồi để `:core` chọn hiện cái gì trong số đó.
        val plan = GroupBoard.sidePlan(m, (bodyH / rowFloorPx).toInt())
        val rows = maxOf(plan.left.size, plan.right.size)
        // ⚠ [SOÁT UI 2026-09-12] Khoảng cách nhãn ↔ thân xe có SÀN: `min * GAP_RATIO` một mình cho ~5-7px khi ô ADAS
        // hẹp ([ĐO] ô ~190px ⇒ 190×0.03≈5.7px) làm chữ DÍNH sát nét xe. Sàn `Sp.S` giữ khe đọc được ở mọi cỡ ô;
        // ô to thì tỉ lệ vẫn thắng. Sàn lấy từ field (xem [gapFloorPx]) — `onDraw` không tra `displayMetrics`.
        val gap = maxOf(min * GAP_RATIO, gapFloorPx)
        val pad = w * PAD_RATIO
        if (rows > 0) {
            // ⚠ Hình xe chỉ vẽ KHI có dữ liệu đặt quanh nó. [ĐO] ảnh máy ảo: ở ô thấp (kế hoạch chỉ còn một câu), vẽ
            // hình xe làm đường viền **cắt ngang giữa câu** — chữ đè lên nét xe, đọc không ra. Hình xe ở bảng này có
            // đúng một việc: nói *"bên trái / bên phải của xe"*; không có gì để đặt hai bên thì nó là hình trang trí
            // đang tranh chỗ với thứ mang thông tin.
            drawBody(canvas, bodyW, bodyH)
            val rowH = bodyH / rows
            val cellH = rowH * CELL_H_RATIO
            drawColumn(canvas, plan.left, pad, body.left - gap, cellH, rowH)
            drawColumn(canvas, plan.right, body.right + gap, w - pad, cellH, rowH)
        }
        // Không vẽ được hàng nào ⇒ MỘT câu giữa bảng. Câu do `:core` dựng và nó phân biệt "đã đọc, sạch" với "chưa
        // đọc được" — bỏ trống chỗ này là để người lái tự đoán, đúng cái "kênh im lặng" dự án đã vá ba lần.
        plan.summary?.let {
            midP.color = colMut
            canvas.drawText(it, w / 2f, cy + midP.textSize * TEXT_MID_LIFT, midP)
        }
        val foot = if (plan.hidden > 0 && plan.summary == null) hiddenNote(plan.hidden) else footer
        if (foot.isNotEmpty()) {
            midP.color = colMut
            canvas.drawText(foot, w / 2f, h * FOOTER_BASELINE, midP)
        }
    }

    /** Hình xe nhìn từ trên (thân + vạch kính lái) — chỉ vẽ khi có dữ liệu đặt hai bên (xem [onDraw]). */
    private fun drawBody(canvas: Canvas, bodyW: Float, bodyH: Float) {
        val r = bodyW * CORNER_RATIO
        canvas.drawRoundRect(body, r, r, outline)
        canvas.drawLine(
            body.left + bodyW * WINDSCREEN_INSET, body.top + bodyH * WINDSCREEN_INSET,
            body.right - bodyW * WINDSCREEN_INSET, body.top + bodyH * WINDSCREEN_INSET, outline,
        )
    }

    /**
     * Dòng chân khi có ô con bị ẩn — **nói ra con số**, không im lặng bỏ bớt.
     *
     * Chữ đi qua tài nguyên (`R.string`) vì đây là câu của tầng `:app`; phần đếm thì lấy từ [SideBoardPlan.hidden] chứ
     * không tự đếm lại.
     */
    private fun hiddenNote(hidden: Int): String {
        val more = resources.getQuantityString(R.plurals.kachi_board_hidden_n, hidden, hidden)
        return if (footer.isEmpty()) more else "$footer   $more"
    }

    /**
     * Một cột dữ liệu: [items] xếp từ trên xuống, mỗi hàng cao [rowH], ô cao [cellH] canh giữa hàng.
     *
     * Trong ô: **nhãn bên trái, số bên phải** — một dòng. Xếp ngang (thay vì số trên / nhãn dưới) là điều làm cỡ chữ
     * sàn vừa được vào ô: một hàng chỉ cần một dòng chữ. Cùng lối `stripCell` khi không có icon (*"thà cắt nhãn còn
     * hơn mất con số"* — nên số vẽ TRƯỚC và nhãn bị kẹp theo chỗ còn lại).
     */
    private fun drawColumn(
        canvas: Canvas,
        items: List<GroupCell>,
        x0: Float,
        x1: Float,
        cellH: Float,
        rowH: Float,
    ) {
        items.forEachIndexed { i, c ->
            val cyRow = body.top + rowH * (i + 0.5f)
            cell.set(x0, cyRow - cellH / 2f, x1, cyRow + cellH / 2f)
            val radius = cellH * CELL_CORNER_RATIO
            canvas.drawRoundRect(cell, radius, radius, cellFill)
            cellStroke.color = if (c.tone == GroupTone.NEUTRAL) colMut2 else tint(c.tone)
            canvas.drawRoundRect(cell, radius, radius, cellStroke)
            val inset = cellH * CELL_PAD_RATIO
            val baseline = cyRow + valueP.textSize * TEXT_MID_LIFT
            // GIÁ TRỊ vẽ trước và canh PHẢI ⇒ nó luôn có chỗ; nhãn canh trái và bị kẹp theo phần còn lại.
            valueP.color = tint(c.tone)
            valueP.alpha = if (c.available) OPAQUE else DIM
            canvas.drawText(c.value, cell.right - inset, baseline, valueP)
            labelP.color = colMut
            val room = (cell.width() - inset * 2) - valueP.measureText(c.value) - inset
            canvas.drawText(clip(c.label, room), cell.left + inset, baseline, labelP)
        }
        // Hàng thiếu ở một bên: KHÔNG vẽ gì (vòng lặp trên tự dừng). Cố ý **không** vẽ ô rỗng cho chỗ thiếu — ô rỗng
        // ở đây đọc thành *"bên này an toàn"*, mà thật ra là *"bên này không có cảm biến loại đó"*; hai nghĩa khác
        // nhau và không được để người lái lẫn.
    }

    /**
     * Cắt [s] cho vừa [room] pixel, có dấu `…`.
     *
     * `Canvas.drawText` **không tự cắt** — chữ dài sẽ vẽ tràn ra ngoài ô (và ở đây là tràn lên thân xe / cột bên
     * kia). Vẽ tràn còn tệ hơn cắt: nó trông như một lỗi vẽ chứ không như "còn chữ nữa".
     */
    private fun clip(s: String, room: Float): String {
        if (room <= 0f) return ""
        if (labelP.measureText(s) <= room) return s
        var n = s.length
        while (n > 0 && labelP.measureText(s.take(n) + ELLIPSIS) > room) n--
        return if (n <= 0) "" else s.take(n) + ELLIPSIS
    }

    private companion object {
        /** `Paint.alpha` theo thang 0..255 (khác `View.alpha` 0..1f) — trị số mờ của GIÁ TRỊ chưa đọc được. */
        const val DIM = 128
        const val OPAQUE = 255

        /** Dấu cắt chữ — ký hiệu, không phải chữ để dịch. */
        const val ELLIPSIS = "…"

        // ── Tỉ lệ hình học (bám nguyên bản trước; tách tên để đọc ra vai trò) ──
        const val OUTLINE_RATIO = 0.018f
        const val STROKE_RATIO = 0.012f
        const val VALUE_RATIO = 0.075f
        const val LABEL_RATIO = 0.058f
        const val MID_RATIO = 0.062f
        const val FOOTER_TOP = 0.88f
        const val FOOTER_BASELINE = 0.97f
        const val BODY_W_RATIO = 0.16f
        const val BODY_H_RATIO = 0.94f
        const val BODY_INSET = 0.04f
        const val CORNER_RATIO = 0.34f
        const val WINDSCREEN_INSET = 0.16f
        const val GAP_RATIO = 0.03f
        const val PAD_RATIO = 0.02f
        const val CELL_H_RATIO = 0.82f
        const val CELL_CORNER_RATIO = 0.22f

        /** Lề trong ô (theo bề cao ô ⇒ tự đúng khi ô co). */
        const val CELL_PAD_RATIO = 0.18f

        /** Nâng đường chân chữ lên để chữ canh GIỮA ô theo mắt (nửa chiều cao nét, trừ phần đuôi chữ). */
        const val TEXT_MID_LIFT = 0.35f
    }
}
