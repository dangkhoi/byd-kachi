package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Vòng đo (gauge) — track mờ + cung màu theo %, chữ lớn + nhãn ở giữa. Khớp ring trong prototype.
 *
 * Dùng cho widget *Năng lượng* và *Không khí* ([WidgetTelemetry.ringCard]) và cho mọi mã datum khai
 * [WidgetShape.RING] ([WidgetTelemetry.telemetryRing]).
 *
 * ## 2.74 · UX7 — hai lỗi hình học đã sửa (cùng luật với bảng lốp, hình học thuần ở [CellTextLayout])
 *  • **khối 2 dòng lệch XUỐNG** [ĐO]: bản cũ đặt baseline bằng hằng ma thuật (`cy + big*0.36f` và
 *    `cy + big*0.9f + small`) — hai hằng không mang chiều cao thật của khối, nên tâm khối 2 dòng nằm **thấp hơn**
 *    tâm vòng đúng `0.146 × d` (d = đường kính vòng): số thì gần đúng tâm, còn nhãn thì trôi xuống gần cung dưới.
 *    Nay baseline tính từ SỐ ĐO PHÔNG ([CellTextLayout.twoLineTopBaseline]) và nhịp 2 dòng lấy đúng nhịp của bảng
 *    lốp ([CellTextLayout.SUB_LINE_GAP]) ⇒ bốn ô vẽ Canvas của launcher cùng một nhịp chữ.
 *  • **chữ tràn khỏi vòng** [ĐO]: `drawText` KHÔNG kẹp theo bề rộng, mà giá trị đi vào đây là chuỗi tuỳ ý
 *    ([TelemetryView.display] + đơn vị, ví dụ `"1013"` + `"hPa"`) ⇒ chữ dài vẽ đè lên cung, thậm chí ra ngoài ô.
 *    Nay co cho vừa **đường kính trong** của vòng ([CellTextLayout.fitScale]), sàn cỡ chữ theo thang bảng
 *    ([KachiSpace.BOARD_VALUE_MIN]/[KachiSpace.BOARD_LABEL_MIN] — cùng sàn [DoorBoardView] đang dùng).
 */
class RingView(context: Context) : View(context) {
    private var pct = 0f
    private var big = ""
    private var small = ""

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor(KachiTheme.TRACK)   // track xám THẤY ĐƯỢC (prototype), không tàng hình
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor(KachiTheme.GREEN)
    }
    private val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(KachiTheme.INK); textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val smallP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(KachiTheme.MUT); textAlign = Paint.Align.CENTER
    }

    fun set(pct: Float, color: String, big: String, small: String) {
        val p2 = pct.coerceIn(0f, 100f); val col = Color.parseColor(color)
        // (owner 2026-09-25 giật): chỉ vẽ lại khi ĐỔI — trước invalidate mỗi nhịp dù số y hệt ⇒ nháy.
        val unchanged = this.pct == p2 && this.arc.color == col && this.big == big && this.small == small
        this.pct = p2; this.arc.color = col; this.big = big; this.small = small
        if (!unchanged) invalidate()
    }

    private val rect = RectF()

    /** Hộp MỰC của dòng số — cấp phát MỘT LẦN (onDraw không được cấp phát). */
    private val ink = Rect()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val m = minOf(w, h)
        val sw = m * STROKE_RATIO                // stroke MỎNG (~6% như prototype, trước 10% quá dày)
        track.strokeWidth = sw; arc.strokeWidth = sw
        val d = m * DIAMETER_RATIO               // đường kính nhỏ hơn → có khoảng thở, không đội header
        val cx = w / 2f; val cy = h / 2f
        rect.set(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2)
        canvas.drawArc(rect, 0f, 360f, false, track)
        canvas.drawArc(rect, -90f, pct * 3.6f, false, arc)

        // Chỗ vẽ chữ = đường kính TRONG của vòng, chừa một bề dày cung mỗi bên cho khoảng thở. Suy từ `sw` (đo
        // được) chứ không thêm một hằng lề mới.
        val room = d - sw * 4f
        bigP.textSize = fit(big, bigP, d * BIG_RATIO, room, Sp.dpf(context, Sp.BOARD_VALUE_MIN))
        smallP.textSize = fit(small, smallP, d * SMALL_RATIO, room, Sp.dpf(context, Sp.BOARD_LABEL_MIN))

        // Baseline từ SỐ ĐO PHÔNG (hết hằng 0.36/0.9 — xem KDoc lớp). Đo ink của chữ số MẪU, không của chuỗi đang
        // vẽ: giá trị nhảy `"82%"` → `"—"` (hộp mực mỏng nằm giữa dòng) thì baseline phải ĐỨNG YÊN.
        bigP.getTextBounds(INK_REF, 0, INK_REF.length, ink)
        val hasSub = small.isNotEmpty()
        val lineGap = if (hasSub) smallP.textSize * CellTextLayout.SUB_LINE_GAP else 0f
        val bottomInk = if (hasSub) smallP.descent() else 0f
        val base = CellTextLayout.twoLineTopBaseline(cy, -ink.top.toFloat(), lineGap, bottomInk)
        canvas.drawText(big, cx, base, bigP)
        if (hasSub) canvas.drawText(small, cx, base + lineGap, smallP)
    }

    /**
     * Cỡ chữ đã co cho [text] vừa [room].
     *
     * Sàn [floor] chỉ chặn phần CO: `minOf(nominal, floor)` để ô nhỏ (vòng bé hơn sàn) không bị chữ **to lên** so
     * với tỉ lệ vòng — đó là cách một "sàn đọc được" biến thành lỗi chữ tràn ở chiều ngược lại.
     */
    private fun fit(text: String, paint: Paint, nominal: Float, room: Float, floor: Float): Float {
        paint.textSize = nominal
        if (text.isEmpty()) return nominal
        val scale = CellTextLayout.fitScale(paint.measureText(text), room)
        return if (scale >= 1f) nominal else maxOf(nominal * scale, minOf(nominal, floor))
    }

    private companion object {
        const val STROKE_RATIO = 0.062f
        const val DIAMETER_RATIO = 0.76f
        const val BIG_RATIO = 0.26f
        const val SMALL_RATIO = 0.12f
        /** Chữ MẪU để đo chiều cao mực của dòng số (mọi chữ số cùng cap-height) — cùng lẽ [TyreBoardView]. */
        const val INK_REF = "0"
    }
}
