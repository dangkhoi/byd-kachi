package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * BẢNG ÁP SUẤT LỐP 4 BÁNH (W4) — hình xe nhìn từ trên, **từng bánh một số riêng** ở 4 góc.
 * Spec `docs/specs/kachi-unified-capability-tile.html` §4.4 (R6–R8).
 *
 * ## Nó thay thế gì (và vì sao không chỉ là "cho đẹp")
 * Bản cũ vẽ 4 ô chữ xếp 2 hàng với **ngưỡng cứng viết tại chỗ** (`t[i] < 2.2`). Đó là ngưỡng **THỨ BA** trong dự án,
 * lệch với ngưỡng ở [TyreBoard] ⇒ cùng một bánh có thể "non" ở chỗ này mà "bình thường" ở chỗ kia. Ô này **không tự
 * quyết định gì**: mọi phán xét lấy từ [TyreBoard.readings] (thuần, đã test off-car), nên chỉ còn MỘT nơi định nghĩa
 * thế nào là non/căng/lệch.
 *
 * ## Quy ước vẽ (theo đúng lối [RingView] / sơ đồ ghế đã có)
 *  • Mọi `Paint` cấp phát MỘT LẦN ở field — KHÔNG cấp phát trong [onDraw].
 *  • Cỡ chữ/nét tính theo cạnh nhỏ nhất ⇒ bất biến với cỡ ô (ô 1/4 màn hay ô full đều đúng tỉ lệ).
 *  • Màu đọc từ [KachiTheme] (không hard-code hex tại chỗ) ⇒ đổi bảng màu là đổi theo.
 *  • Context7 (`/websites/developer_android`) đã xác nhận `onDraw(Canvas)` + `invalidate()` **hiện hành**; các API
 *    bộ-đệm-vẽ (`setWillNotCacheDrawing`, `setChildrenDrawnWithCacheEnabled`) và `Paint.setElegantTextHeight` đã lỗi
 *    thời ⇒ **không dùng**.
 */
class TyreBoardView(context: Context) : View(context) {

    /** 4 bánh theo thứ tự [TyreCorner]; rỗng = chưa có dữ liệu (vẽ 4 dấu gạch ngang). */
    private var readings: List<TyreReading> = emptyList()

    /** Chuỗi số đã format sẵn theo đơn vị người dùng chọn (song song [readings]); `null` = "—". */
    private var values: List<String?> = emptyList()

    /** Nhãn đơn vị đang dùng (vd "bar" / "psi") — hiện MỘT lần ở giữa, không lặp 4 lần cho gọn. */
    private var unitLabel: String = ""

    /**
     * Nhiệt độ TỪNG BÁNH đã format sẵn kèm đơn vị (song song [readings]); `null` = chưa đọc ⇒ chỉ hiện nhãn vị trí.
     *
     * [ĐO] máy ảo 2026-09-10: bản đầu tự ghép `"${'$'}{rd.tempC}°C"` ngay trong ô vẽ ⇒ người dùng chọn °F mà bảng vẫn
     * ghi °C. Đó CHÍNH LÀ loại lỗi gói này đi dọn (mỗi bề mặt tự quyết đơn vị). Nay chuỗi do chỗ gọi format qua
     * lớp đơn vị, ô vẽ KHÔNG biết gì về đơn vị.
     */
    private var temps: List<String?> = emptyList()

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // MUT2 (không phải LINE ~9% trắng): [ĐO] đọc ảnh máy ảo cho thấy viền LINE chỉ chênh nền 21/255 ⇒ hình xe
        // gần như tan vào nền, người xem chỉ thấy 4 con số rời rạc.
        style = Paint.Style.STROKE; color = Color.parseColor(KachiTheme.MUT2)
    }
    private val tyrePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true; color = Color.parseColor(KachiTheme.INK)
    }
    private val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor(KachiTheme.MUT2)
    }
    private val midP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor(KachiTheme.MUT)
    }
    private val body = RectF()

    // Màu PHÂN GIẢI MỘT LẦN. `Color.parseColor` cắt chuỗi + parse số mỗi lần gọi; ô widget bị dựng lại theo nhịp
    // trạng thái xe nên [onDraw] chạy đều đặn ⇒ để trong onDraw là rác bộ nhớ đúng chỗ KDoc trên hứa là không có.
    private val colInk = Color.parseColor(KachiTheme.INK)
    private val colMut = Color.parseColor(KachiTheme.MUT)
    private val colMut2 = Color.parseColor(KachiTheme.MUT2)
    private val colRed = Color.parseColor(KachiTheme.RED)
    private val colAmber = Color.parseColor(KachiTheme.AMBER)

    /**
     * Đặt dữ liệu. [values] và [temps] **song song** với [readings] (chuỗi đã đổi đơn vị + format ở chỗ gọi — ô vẽ
     * KHÔNG tự đổi đơn vị để chỉ có một nơi làm việc đó).
     *
     * Độ dài được CHUẨN HOÁ về đúng `readings.size` ngay ở đây: nếu chỗ gọi đưa danh sách lệch độ dài thì mọi bánh
     * thiếu hiện "—" một cách nhất quán, thay vì lệch chỉ số làm số của bánh này nhảy sang bánh khác.
     */
    fun set(readings: List<TyreReading>, values: List<String?>, unitLabel: String, temps: List<String?>) {
        this.readings = readings
        this.values = List(readings.size) { values.getOrNull(it) }
        this.unitLabel = unitLabel
        this.temps = List(readings.size) { temps.getOrNull(it) }
        invalidate()
    }

    /** Màu theo trạng thái: non/căng = đỏ (nguy), lệch = hổ phách (để ý), bình thường = mực, chưa biết = mờ. */
    private fun colorFor(s: TyreStatus): Int = when (s) {
        TyreStatus.LOW, TyreStatus.HIGH -> colRed
        TyreStatus.UNEVEN -> colAmber
        TyreStatus.OK -> colInk
        TyreStatus.UNKNOWN -> colMut2
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val m = minOf(w, h)

        outline.strokeWidth = m * 0.018f
        bigP.textSize = m * 0.155f
        subP.textSize = m * 0.075f
        midP.textSize = m * 0.072f

        // [ĐO] máy ảo 2026-09-10 (đọc ảnh): bản đầu vẽ thân xe 224×262 px = tỉ lệ 0.85 ⇒ gần VUÔNG, không đọc ra là
        // xe; viền lại chỉ chênh nền ~21/255 nên gần như tan biến. Sửa: thân THUÔN DỌC (hẹp hơn, cao hơn) + viền
        // sáng hơn hẳn. Toàn bộ khối dịch LÊN vì bản đầu chừa 80px phía trên mà chỉ 19px dưới chữ đơn vị.
        val bodyW = w * 0.22f
        val bodyH = h * 0.70f
        val cy = h * 0.46f
        body.set((w - bodyW) / 2f, cy - bodyH / 2f, (w + bodyW) / 2f, cy + bodyH / 2f)
        val r = bodyW * 0.34f
        canvas.drawRoundRect(body, r, r, outline)
        // Vạch ngang = kính lái, để người xem biết đâu là đầu xe (nếu không thì bảng 4 số bị lộn trước/sau).
        canvas.drawLine(body.left + bodyW * 0.14f, body.top + bodyH * 0.22f,
            body.right - bodyW * 0.14f, body.top + bodyH * 0.22f, outline)

        val leftX = w * 0.22f
        val rightX = w * 0.78f
        val topY = cy - bodyH * 0.30f
        val botY = cy + bodyH * 0.30f

        // Chưa có dữ liệu (off-car) ⇒ vẫn vẽ đủ 4 chỗ với "—" để bố cục không nhảy khi số về.
        if (readings.size < 4) {
            // Dùng CHÍNH toạ độ ở trên (không hardcode lại) ⇒ hai nhánh không thể lệch nhau khi chỉnh bố cục.
            listOf(leftX to topY, rightX to topY, leftX to botY, rightX to botY).forEach { (x, y) ->
                bigP.color = colorFor(TyreStatus.UNKNOWN)
                canvas.drawText(TelemetryView.PLACEHOLDER, x, y, bigP)
            }
            midP.color = colMut
            canvas.drawText(unitLabel.ifEmpty { "áp suất lốp" }, w / 2f, h * 0.93f, midP)
            return
        }

        readings.forEachIndexed { i, rd ->
            val x = if (rd.corner == TyreCorner.FRONT_LEFT || rd.corner == TyreCorner.REAR_LEFT) leftX else rightX
            val y = if (rd.corner == TyreCorner.FRONT_LEFT || rd.corner == TyreCorner.FRONT_RIGHT) topY else botY
            val col = colorFor(rd.status)

            // Vệt bánh xe: gợi hình, và là chỗ mang màu cảnh báo (số + vệt cùng màu ⇒ thấy ngay bánh nào).
            tyrePaint.color = col
            val tw = m * 0.035f
            val th = m * 0.115f
            // Chồng NHẸ lên mép thân: bản đầu để rời hẳn nên 4 vệt trông như trang trí độc lập, không ra "bánh xe".
            val edgeX = if (x < w / 2f) body.left - tw * 0.55f else body.right - tw * 0.45f
            canvas.drawRoundRect(edgeX, y - th * 0.75f, edgeX + tw, y + th * 0.25f, tw / 2f, tw / 2f, tyrePaint)

            bigP.color = col
            canvas.drawText(values.getOrNull(i) ?: TelemetryView.PLACEHOLDER, x, y, bigP)

            // Dòng phụ: nhiệt độ nếu đọc được, không thì nhãn vị trí bánh (luôn có thông tin, không để trống).
            // MỘT cảnh báo = MỘT màu: bản đầu cho số màu đỏ mà dòng phụ màu hổ phách ⇒ cùng một bánh có hai màu
            // cảnh báo, người xem không biết đang báo cái gì. Nay dòng phụ dùng CHÍNH màu của số.
            subP.color = if (rd.status.alert) col else colMut2
            val t = temps.getOrNull(i)
            val sub = if (t != null) "${rd.corner.shortLabel} · $t" else rd.corner.shortLabel
            canvas.drawText(sub, x, y + m * 0.105f, subP)
        }

        midP.color = colMut
        canvas.drawText(unitLabel, w / 2f, h * 0.93f, midP)
    }
}
