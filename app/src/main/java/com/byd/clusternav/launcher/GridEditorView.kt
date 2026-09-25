package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * TRÌNH VẼ KHUNG (P9 bước 2) — người dùng tự vẽ bố cục trên lưới 12×6 thay vì chỉ chọn trong các bố cục sẵn.
 *
 * ## Cách dùng
 *  - **Kéo giữa khung** = di chuyển · **kéo góc dưới-phải** = đổi cỡ · **chạm** = chọn khung.
 *  - Toạ độ **luôn bám ô lưới** (không có vị trí nửa ô) ⇒ cái vẽ ra chính là cái nhận được, không có sai số.
 *
 * ## Hai quyết định về trải nghiệm
 *  1. **Cho phép đè nhau rồi tô ĐỎ**, không chặn tay người dùng lúc đang kéo. Chặn giữa lúc kéo làm khung "dính"
 *     vào nhau khó hiểu; tô đỏ thì thấy ngay sai ở đâu, và nút Lưu bị chặn kèm lý do.
 *  2. **Vẽ theo đúng tỉ lệ màn hình** (khung chứa được co về tỉ lệ màn) ⇒ hình vẽ ở đây giống hình thật ở màn chính.
 *     Nếu vẽ vào khung vuông thì bố cục nhìn cân ở trình vẽ mà ra màn hình lại dẹt.
 */
class GridEditorView(context: Context) : View(context) {

    private val cell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.parseColor(KachiTheme.GRID_LINE)
    }
    private val frameFill = Paint(Paint.ANTI_ALIAS_FLAG)
    // ⚠ WP1 · R1.1 — `frameLine` (viền khung) đã XOÁ. Giữ một Paint nét không ai dùng là mời cái viền quay lại ở
    // lượt sửa sau; khung nay đọc ra bằng VÙNG TÔ + tay cầm (xem chú thích trong `onDraw`).
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(KachiTheme.INK); textAlign = Paint.Align.CENTER
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(KachiTheme.INK) }
    private val box = RectF()
    /** Dùng lại giữa các khung hình (lint DrawAllocation): chỉ số khung đè nhau + bảng màu khung. */
    private val overlapping = HashSet<Int>()
    private val palette = listOf(KachiTheme.ACCENT, KachiTheme.GREEN, KachiTheme.CYAN, KachiTheme.ACCENT2)

    /**
     * Độ đục vùng tô của khung — **hai con số này gánh luôn phần mà viền từng góp** (WP1 · R1.1).
     *
     * Nâng từ `55`/`90` lên `96`/`150`: khung phải nổi trên lưới ô mà không còn đường kẻ, và chênh giữa hai mức phải
     * đủ để "đang chọn" đọc ra ngay. Vẫn bán trong suốt (không đục) để thấy được lưới bên dưới — lưới là thứ người
     * dùng canh theo khi kéo.
     */
    private companion object {
        const val IDLE_ALPHA = 96
        const val SELECTED_ALPHA = 150
    }

    /** Bố cục đang vẽ. Đặt vào là vẽ lại. */
    var layout: GridLayout = GridLayout(emptyList())
        set(value) { field = value; invalidate() }

    /** Khung đang chọn (−1 = không chọn). */
    var selected: Int = -1
        set(value) { field = value; invalidate() }

    /** Gọi mỗi khi bố cục đổi do người dùng kéo. */
    var onChanged: (GridLayout) -> Unit = {}

    /** Gọi khi người dùng chọn khung khác. */
    var onSelected: (Int) -> Unit = {}

    // Vùng vẽ thật (co về tỉ lệ màn hình, canh giữa).
    private var gx = 0f; private var gy = 0f; private var gw = 0f; private var gh = 0f
    private val cw: Float get() = gw / WorkspaceGrid.COLS
    private val chh: Float get() = gh / WorkspaceGrid.ROWS

    private enum class Mode { NONE, MOVE, RESIZE }
    private var mode = Mode.NONE
    private var dragIndex = -1
    /** Id ngón đang kéo — chống ngón thứ hai làm khung bay ([SOÁT P3]). */
    private var activePointer = -1
    private var grabCol = 0f     // lệch giữa điểm chạm và góc khung, tính bằng ĐƠN VỊ Ô (kéo mới mượt)
    private var grabRow = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Co về tỉ lệ MÀN HÌNH để hình vẽ giống hình thật.
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        val aspect = if (screenH > 0f) screenW / screenH else 16f / 9f
        val pad = 8f
        var bw = w - pad * 2; var bh = bw / aspect
        if (bh > h - pad * 2) { bh = h - pad * 2; bw = bh * aspect }
        gw = bw; gh = bh; gx = (w - bw) / 2f; gy = (h - bh) / 2f
    }

    // ── Chạm ─────────────────────────────────────────────────────────────────────────────────────

    private fun handleSize(): Float = minOf(cw, chh).coerceAtLeast(28f)

    private fun frameRect(f: GridFrame): RectF = RectF(
        gx + f.col * cw, gy + f.row * chh, gx + f.colEnd * cw, gy + f.rowEnd * chh,
    )

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Xét từ khung CUỐI về đầu: khung vẽ sau nằm trên, nên nó phải được bắt trước.
                val hit = layout.frames.indices.reversed().firstOrNull { frameRect(layout.frames[it]).contains(e.x, e.y) }
                if (hit == null) { mode = Mode.NONE; return false }
                dragIndex = hit
                if (selected != hit) { selected = hit; onSelected(hit) }
                val r = frameRect(layout.frames[hit])
                val hs = handleSize()
                mode = if (e.x >= r.right - hs && e.y >= r.bottom - hs) Mode.RESIZE else Mode.MOVE
                grabCol = (e.x - r.left) / cw
                grabRow = (e.y - r.top) / chh
                activePointer = e.getPointerId(0)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.NONE || dragIndex !in layout.frames.indices) return false
                // [SOÁT P3] Chỉ theo NGÓN đã bắt đầu kéo. Không có chốt này thì ngón thứ hai đặt xuống làm toạ độ
                // e.x/e.y nhảy sang ngón khác ⇒ khung "bay" theo ngón mới giữa lúc đang kéo.
                if (e.findPointerIndex(activePointer) < 0) return true
                val f = layout.frames[dragIndex]
                val next = when (mode) {
                    Mode.MOVE -> {
                        val col = floor((e.x - gx) / cw - grabCol + 0.5f).toInt()
                        val row = floor((e.y - gy) / chh - grabRow + 0.5f).toInt()
                        // Phép kẹp nằm ở [GridEditorLogic] (:core, thuần) — chỗ này từng chứa một lỗi SẬP
                        // (`coerceIn` ném khi trần < sàn với bố cục hỏng) mà KHÔNG test nào canh được vì logic
                        // thuần lại nằm trong onTouchEvent. Nay có bảng test off-car khoá.
                        GridEditorLogic.move(f, col, row)
                    }
                    Mode.RESIZE -> {
                        val cols = ((e.x - gx) / cw).roundToInt() - f.col
                        val rows = ((e.y - gy) / chh).roundToInt() - f.row
                        GridEditorLogic.resize(f, cols, rows)
                    }
                    Mode.NONE -> f
                }
                if (next != f) {
                    layout = GridLayout(layout.frames.toMutableList().also { it[dragIndex] = next })
                    onChanged(layout)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = Mode.NONE; dragIndex = -1; activePointer = -1
                return true
            }
        }
        return false
    }

    // ── Vẽ ───────────────────────────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        if (gw <= 0f || gh <= 0f) return

        // Lưới ô — cho người dùng thấy chỗ khung sẽ bám vào.
        for (c in 0..WorkspaceGrid.COLS) canvas.drawLine(gx + c * cw, gy, gx + c * cw, gy + gh, cell)
        for (r in 0..WorkspaceGrid.ROWS) canvas.drawLine(gx, gy + r * chh, gx + gw, gy + r * chh, cell)

        // Khung nào đang đè nhau — tô đỏ để thấy ĐÍCH DANH, không chỉ báo chung "bố cục lỗi".
        val bad = overlapping
        bad.clear()
        for (i in layout.frames.indices) for (j in i + 1 until layout.frames.size) {
            if (layout.frames[i].overlaps(layout.frames[j])) { bad.add(i); bad.add(j) }
        }

        layout.frames.forEachIndexed { i, f ->
            val r = frameRect(f)
            box.set(r.left + 2f, r.top + 2f, r.right - 2f, r.bottom - 2f)
            val base = if (i in bad) KachiTheme.RED else palette[i % palette.size]
            // ⚠ WP1 · R1.1 — viền khung (`frameLine`, 4f khi chọn / 2f khi không) đã GỠ: owner *"KHÔNG còn viền ở
            // BẤT CỨ ĐÂU hết"*. Việc nó làm được chia lại cho hai thứ ĐÃ có sẵn: (a) khung đọc ra bằng **VÙNG TÔ**
            // (alpha nâng 55→96 / 90→150 để bù đúng phần độ đậm mà viền từng góp — khung vẫn nổi trên lưới), (b)
            // "đang chọn" đọc ra bằng **tô đậm hơn + TAY CẦM đổi cỡ** (tay cầm vốn chỉ hiện ở khung đang chọn, tức
            // dấu hiệu đó đã tồn tại và không mơ hồ). Lưới ô ở trên KHÔNG phải viền — nó là cái lưới để bám.
            frameFill.color = Color.parseColor(base); frameFill.alpha = if (i == selected) SELECTED_ALPHA else IDLE_ALPHA
            canvas.drawRoundRect(box, 10f, 10f, frameFill)

            label.textSize = minOf(box.width(), box.height()) * 0.32f
            canvas.drawText("${i + 1}", box.centerX(), box.centerY() + label.textSize * 0.35f, label)

            // Tay cầm đổi cỡ ở góc dưới-phải, chỉ hiện ở khung đang chọn (đỡ rối).
            if (i == selected) {
                val hs = handleSize()
                val cx = box.right - hs * 0.42f; val cy = box.bottom - hs * 0.42f
                canvas.drawCircle(cx, cy, hs * 0.20f, handle)
            }
        }
    }
}
