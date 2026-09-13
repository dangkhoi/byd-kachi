package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * BẢNG CẢM BIẾN ĐỖ 8 VÙNG (G1 · T3) — hình xe nhìn từ trên, **4 vùng trước + 4 vùng sau** đúng vị trí quanh xe.
 * Spec `docs/specs/kachi-capability-groups.html` §4.3 (bộ vẽ `BOARD`).
 *
 * Đây là ca mà [WidgetShape.BOARD] đã ghi sẵn trong KDoc từ đầu (*"4 lốp, 8 zone radar"*) nhưng **chưa ai dựng**:
 * trước T3, `radar_zones` hiện ra bằng ô số chung, tức một dòng chữ `"0 0 1 2 3 1 0 0"` — đúng dữ liệu nhưng người
 * lái không đọc được vùng nào đang sát vật.
 *
 * ## U9 (2026-09-13) — hình xe nay là CHÍNH path của bộ icon v2
 * Thân + hai vạch kính lấy từ [CarFrames] thay cho `drawRoundRect` + `drawLine` tự vẽ ⇒ bảng này, bảng lốp,
 * bảng ADAS và icon 24dp cùng một chiếc xe. Bảng radar **không** vẽ bánh: ngữ pháp của bộ icon là *vùng tô = bộ
 * phận đang được nói tới*, mà ở đây thứ đang được nói tới là tám vùng cảm biến quanh xe, không phải bánh.
 *
 * ## Quy ước vẽ — bám nguyên [TyreBoardView] (tiền lệ đúng của W4)
 *  • Mọi `Paint` cấp phát MỘT LẦN ở field, màu phân giải MỘT LẦN — KHÔNG cấp phát/parse trong [onDraw].
 *  • Cỡ chữ/nét tính theo cạnh nhỏ nhất ⇒ bất biến với cỡ ô.
 *  • **Ô vẽ KHÔNG tự quyết định gì**: mức vùng và sắc thái đến từ [GroupBoard] (thuần, test off-car), chuỗi chân
 *    bảng do chỗ gọi dựng. Cùng lý do như bảng lốp: ô vẽ tự đặt ngưỡng là cách chắc chắn để có ngưỡng thứ hai.
 */
class RadarBoardView(context: Context) : View(context) {

    /** Mức từng vùng, luôn [GroupBoard.RADAR_ZONE_COUNT] phần tử; `null` = chưa đọc ⇒ vẽ mờ. */
    private var levels: List<Int?> = emptyList()

    /** Dòng chân bảng (âm lượng cảm biến) — chuỗi đã format ở chỗ gọi. */
    private var footer: String = ""

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor(KachiTheme.MUT2)
        // Đầu/khớp nét TRÒN — khung xe nay là path của bộ icon v2, vốn khai `strokeLineCap/Join="round"`.
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /**
     * Viền cho vùng **chưa đọc được**. Paint RIÊNG, không sửa màu của [outline] rồi trả lại: đổi trạng thái một Paint
     * dùng chung giữa hai việc là cách sinh lỗi "vẽ đúng ở lượt đầu, sai ở lượt sau" mà không ai lần ra.
     */
    private val dimOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor(KachiTheme.DIM)
    }
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val digitP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val midP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor(KachiTheme.MUT)
    }
    private val body = RectF()
    private val bar = RectF()

    // ── U9 · hình xe dùng chung ([CarFrames]) — cấp phát MỘT LẦN, đúng luật "không cấp phát trong onDraw" ──
    private val carMatrix = Matrix()

    /** Bản chép để biến hình — KHÔNG `transform` thẳng vào path dùng chung của [CarFrames]. */
    private val carPath = Path()
    private val srcBox = RectF()

    /** SÀN nét khung xe (R1: không mảnh hơn 1.6dp tương đương) — lấy bậc "nét" của thang, xem [TyreBoardView]. */
    private val strokeFloorPx = Sp.dpf(context, Sp.STROKE)

    private val colInk = Color.parseColor(KachiTheme.INK)
    private val colMut = Color.parseColor(KachiTheme.MUT)

    /**
     * Màu dấu gạch của vùng **chưa đọc được** — phân giải MỘT LẦN như mọi màu khác ở đây.
     *
     * Dùng `MUT2` (không dùng [colDim]): [colDim] là màu VIỀN của ô rỗng, nên vẽ chữ cùng màu viền thì dấu gạch
     * biến mất. `MUT2` cũng đúng màu mà [TyreBoardView] dùng cho bánh `UNKNOWN` ⇒ hai bảng `BOARD` nói *"chưa đọc
     * được"* bằng cùng một sắc độ.
     */
    private val colMut2 = Color.parseColor(KachiTheme.MUT2)
    private val colDim = Color.parseColor(KachiTheme.DIM)
    private val colRed = Color.parseColor(KachiTheme.RED)
    private val colAmber = Color.parseColor(KachiTheme.AMBER)

    /**
     * Đặt dữ liệu. [levels] được CHUẨN HOÁ về đúng [GroupBoard.RADAR_ZONE_COUNT] ngay ở đây, nên chỗ gọi đưa danh
     * sách lệch độ dài thì các vùng thiếu hiện mờ một cách nhất quán thay vì lệch chỉ số làm mức của vùng này nhảy
     * sang vùng khác (đúng lối chuẩn hoá của [TyreBoardView.set]).
     */
    fun set(levels: List<Int?>, footer: String) {
        this.levels = List(GroupBoard.RADAR_ZONE_COUNT) { levels.getOrNull(it) }
        this.footer = footer
        invalidate()
    }

    /** Màu vùng theo sắc thái do `:core` quyết định — ô vẽ chỉ dịch sắc thái sang màu. */
    private fun colorFor(level: Int?): Int = when (GroupBoard.radarTone(level)) {
        GroupTone.ALERT -> colRed
        GroupTone.WARN -> colAmber
        GroupTone.ACTIVE, GroupTone.NEUTRAL -> if (level == null) colDim else colInk
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val m = minOf(w, h)

        outline.strokeWidth = maxOf(m * 0.018f, strokeFloorPx)
        dimOutline.strokeWidth = outline.strokeWidth
        digitP.textSize = m * 0.085f
        midP.textSize = m * 0.072f

        // Thân xe của [CarFrames], y hệt bảng lốp — để hai bảng BOARD nhìn ra là cùng một cái xe. Khung dành cho
        // xe hẹp hơn bảng lốp vì ở đây còn phải chừa chỗ cho 4 thanh vùng phía trên và 4 thanh phía dưới.
        val bodyW = w * 0.20f
        val bodyH = h * 0.34f
        val cy = h * 0.47f
        body.set((w - bodyW) / 2f, cy - bodyH / 2f, (w + bodyW) / 2f, cy + bodyH / 2f)
        // Chừa nửa nét mỗi phía: `Path` là ĐƯỜNG TÂM nét, không phải mép mực.
        body.inset(outline.strokeWidth / 2f, outline.strokeWidth / 2f)
        CarFrames.frameBounds(srcBox)
        CarFrames.fit(srcBox, body, carMatrix)
        carPath.set(CarFrames.topFrame)
        carPath.transform(carMatrix)
        canvas.drawPath(carPath, outline)

        // 4 vùng TRƯỚC xếp trên nắp máy, 4 vùng SAU dưới cốp — đúng thứ tự HAL trả về.
        // Số vùng mỗi hàng SUY RA từ [GroupBoard.RADAR_ZONE_COUNT], không viết cứng: `GridSeamGuardTest` cấm khoảng
        // viết cứng trong `launcher/` (lỗi P9 thật: `for (i in 0..3)` còn sót sau khi nới trần ô 4 → 6), và ở đây suy
        // ra cũng ĐÚNG HƠN — đổi số vùng ở `:core` là bảng tự chia lại hàng.
        drawRow(canvas, w, h, from = 0, top = h * 0.13f)
        drawRow(canvas, w, h, from = ZONES_PER_ROW, top = h * 0.70f)

        midP.color = colMut
        canvas.drawText(footer, w / 2f, h * 0.95f, midP)
    }

    /** Một hàng [ZONES_PER_ROW] thanh vùng, bắt đầu từ chỉ số [from], mép trên tại [top]. */
    private fun drawRow(canvas: Canvas, w: Float, h: Float, from: Int, top: Float) {
        val span = w * 0.72f
        val left0 = (w - span) / 2f
        val gap = span * 0.06f
        val barW = (span - gap * (ZONES_PER_ROW - 1)) / ZONES_PER_ROW
        val barH = h * 0.11f
        val radius = barH * 0.35f
        for (i in 0 until ZONES_PER_ROW) {
            val level = levels.getOrNull(from + i)
            val x = left0 + i * (barW + gap)
            bar.set(x, top, x + barW, top + barH)
            zonePaint.color = colorFor(level)
            // Vùng chưa đọc vẽ RỖNG (chỉ viền) thay vì tô mờ: tô mờ trông giống "mức 0 = an toàn", mà chưa đọc được
            // KHÔNG phải an toàn — đó là hai nghĩa khác nhau và không được để người lái lẫn.
            //
            // ⚠ [SOÁT G1 · b2] Viền rỗng MỘT MÌNH là chưa đủ: [ĐO] off-car bảng vẽ ra **8 ô trống hoàn toàn**, không
            // một ký tự nào — người xem không phân biệt được *"chưa đọc được"* với *"ô vẽ bị hỏng / tính năng chưa
            // làm"*. Luật của dự án là **off-car ra dấu gạch, không bịa số**, và [TyreBoardView] đã làm đúng thế
            // (nhánh `readings.size < 4` vẽ 4 dấu `TelemetryView.PLACEHOLDER`). Hai bảng `BOARD` nói hai kiểu về
            // cùng một trạng thái là bất nhất mà chính gói này đi dọn ⇒ vẽ dấu gạch vào giữa ô rỗng.
            if (level == null) {
                canvas.drawRoundRect(bar, radius, radius, dimOutline)
                digitP.color = colMut2
                canvas.drawText(
                    TelemetryView.PLACEHOLDER, bar.centerX(),
                    bar.centerY() + digitP.textSize * 0.36f, digitP,
                )
            } else {
                canvas.drawRoundRect(bar, radius, radius, zonePaint)
                digitP.color = if (GroupBoard.radarTone(level) == GroupTone.NEUTRAL) colMut else colInk
                // Chữ số nằm GIỮA thanh: dịch xuống nửa chiều cao chữ để tâm quang học đúng giữa (baseline của
                // Canvas nằm ở chân chữ, không phải giữa).
                canvas.drawText(level.toString(), bar.centerX(), bar.centerY() + digitP.textSize * 0.36f, digitP)
            }
        }
    }

    private companion object {
        /**
         * Vùng mỗi hàng = nửa số vùng: cảm biến đỗ chia **trước / sau**, không phải trái/phải.
         *
         * Suy ra từ [GroupBoard.RADAR_ZONE_COUNT] thay vì viết `4`: một chỗ khai số vùng, và bảng tự chia lại hàng
         * nếu trim xe nào có số vùng khác.
         */
        const val ZONES_PER_ROW = GroupBoard.RADAR_ZONE_COUNT / 2
    }
}
