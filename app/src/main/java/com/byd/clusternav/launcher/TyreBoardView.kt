package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout

/**
 * BẢNG ÁP SUẤT LỐP 4 BÁNH — **hình xe TÁCH LAYER** (owner 2026-09-25).
 *
 * FrameLayout xếp 2 lớp cùng cỡ:
 *  • **NỀN** [CarImageView] — chỉ vẽ hình xe; `invalidate` DUY NHẤT khi ảnh vừa nạp xong, KHÔNG theo nhịp số.
 *  • **ĐÈ** [CellsView] (trong suốt) — 4 ô giá trị + số + chấm; `invalidate` khi số đổi. Số đổi ⇒ chỉ lớp này
 *    vẽ lại, HÌNH XE ĐỨNG YÊN (hết nháy). Lớp đè tính `content` (letterbox) từ [CarImageView.contentRect] để
 *    ô bám đúng bánh — hai lớp cùng công thức bố cục ([carDstIn]).
 *
 * ## 2.74 · R2 — ba lỗi hình học đã sửa (hình học thuần nằm ở [CellTextLayout], kiểm off-car)
 *  • thẻ đục KHÔNG còn đè lên thân xe: mép trong kẹp theo khung ảnh THẬT ([CellTextLayout.cardSpanX]);
 *  • khối 2 dòng canh giữa ô bằng SỐ ĐO PHÔNG ([CellTextLayout.twoLineTopBaseline]) — hết hằng `0.10`/`0.60`;
 *  • con số nằm đúng trục ô, dòng phụ cùng trục, cả hai co cho vừa thẻ ([CellTextLayout.lineStartX]/`fitScale`).
 *
 * Ranh giới vẫn giữ: ô vẽ 0 ngưỡng (non/căng/lệch + số + đơn vị do [TyreBoard]/chỗ gọi), 0 viền/blur/shadow.
 */
class TyreBoardView(context: Context) : FrameLayout(context) {

    private val carView = CarImageView(context).also { it.layoutRect = ::carDstIn }
    private val cells = CellsView(context)

    init {
        addView(carView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(cells, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Nhãn trợ năng của cả bảng (xem [set]) chỉ tới được TalkBack nếu view được đánh dấu QUAN TRỌNG:
        // [ĐO AOSP android-10.0.0_r47 `View.java:12691-12713`] `isImportantForAccessibility()` ở chế độ mặc định
        // (AUTO) chỉ trả `true` khi view bấm/được-focus/có listener/là pane — **không** xét `contentDescription`.
        // Bảng lốp không bấm được nên nếu để AUTO thì nhãn kia chỉ có `uiautomator` (cờ include-not-important) đọc
        // được, còn người dùng TalkBack thì không ⇒ lại là một đường gần-chết.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** Neo bố cục ảnh xe = cột giữa (dùng chung cho cả hai lớp). */
    private fun carDstIn(w: Float, h: Float, out: RectF) {
        out.set(w * CAR_LEFT, h * CAR_TOP, w * CAR_RIGHT, h * CAR_BOTTOM)
    }

    /**
     * Số + dòng phụ cho 4 bánh; [summary] = câu kết luận do `:core` dựng (chỗ gọi đã ghép cả dấu "nhiệt chưa kiểm").
     *
     * [summary] KHÔNG được vẽ lên bảng (owner 2026-09-23 đã bỏ dòng kết luận dưới widget tổng hợp, `84f91e6`) —
     * nó là **nhãn trợ năng** của cả ô: TalkBack/`uiautomator` đọc được "2.1 bar · TT non · nhiệt chưa kiểm" mà
     * mặt bảng vẫn sạch. Trước 2.74 tham số này được truyền vào lớp vẽ rồi **không ai dùng** (đường chết: chuỗi
     * dựng đủ, test ghim chuỗi, mà màn không bao giờ hiện — đúng bệnh CLAUDE.md §8).
     */
    fun set(
        readings: List<TyreReading>,
        values: List<String?>,
        unitLabel: String,
        temps: List<String?>,
        summary: String,
    ) {
        if (contentDescription?.toString() != summary) contentDescription = summary
        cells.set(readings, values, unitLabel, temps)
    }

    fun release() = carView.release()

    /**
     * Lớp NỀN vẽ lại (= ẢNH vừa nạp xong, xem [CarImageView]) ⇒ khung letterbox đổi ⇒ lớp thẻ phải kẹp LẠI theo
     * khung mới, không thì thẻ giữ hình học của khung placeholder (tỉ lệ 0.46 ≠ tỉ lệ ảnh thật) và đè lên xe cho
     * tới nhịp số kế tiếp.
     *
     * [ĐO AOSP android-10.0.0_r47] `View.java:17620` `p.invalidateChild(this, damage)` →
     * `ViewGroup.java:5909-5915` `invalidateChild` chuyển THẲNG sang `onDescendantInvalidated(child, child)` khi
     * đang tăng tốc phần cứng. Không có vòng lặp: lượt `cells` tự vẽ lại vào đây với `child === cells` ⇒ bỏ qua.
     * Không tăng tốc phần cứng ⇒ không được gọi ⇒ rơi về hành vi cũ (kẹp lại ở nhịp số kế), không sập.
     */
    override fun onDescendantInvalidated(child: View, target: View) {
        super.onDescendantInvalidated(child, target)
        if (child === carView) cells.postInvalidateOnAnimation()
    }

    /** Lớp ĐÈ trong suốt: chỉ 4 ô + số + chấm. `invalidate` khi số đổi — không đụng hình xe. */
    private inner class CellsView(context: Context) : View(context) {
        private var readings: List<TyreReading> = emptyList()
        private var values: List<String?> = emptyList()
        private var unitLabel: String = ""
        private var temps: List<String?> = emptyList()

        private val cellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT; isFakeBoldText = true; color = Color.parseColor(KachiTheme.INK)
        }
        private val unitP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT; color = Color.parseColor(KachiTheme.MUT)
        }
        /** Dòng phụ vẽ từ MÉP TRÁI đã tính ([CellTextLayout.lineStartX]) — canh CENTER của Paint không kẹp được thẻ. */
        private val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT; color = Color.parseColor(KachiTheme.MUT)
        }
        private val content = RectF()
        private val cell = RectF()
        /** Hộp MỰC của dòng số — cấp phát MỘT LẦN (onDraw không được cấp phát). */
        private val ink = Rect()

        private val colInk = Color.parseColor(KachiTheme.INK)
        private val colMut = Color.parseColor(KachiTheme.MUT)
        private val colMut2 = Color.parseColor(KachiTheme.MUT2)
        private val colRed = Color.parseColor(KachiTheme.RED)
        private val colAmber = Color.parseColor(KachiTheme.AMBER)

        /** Cỡ chữ GỐC của lượt vẽ (mỗi thẻ co riêng từ đây — thẻ hẹp không được kéo thẻ rộng nhỏ theo). */
        private var baseBig = 0f
        private var baseSub = 0f
        private var floorBig = 0f
        private var floorSub = 0f

        fun set(readings: List<TyreReading>, values: List<String?>, unitLabel: String, temps: List<String?>) {
            val nv = List(readings.size) { values.getOrNull(it) }
            val nt = List(readings.size) { temps.getOrNull(it) }
            val unchanged = this.readings == readings && this.values == nv &&
                this.unitLabel == unitLabel && this.temps == nt
            this.readings = readings; this.values = nv; this.unitLabel = unitLabel
            this.temps = nt
            if (!unchanged) invalidate()   // chỉ lớp SỐ vẽ lại; hình xe (view khác) đứng yên
        }

        private fun colorFor(s: TyreStatus): Int = when (s) {
            TyreStatus.LOW, TyreStatus.HIGH -> colRed
            TyreStatus.UNEVEN -> colAmber
            TyreStatus.OK -> colInk
            TyreStatus.UNKNOWN -> colMut2
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val m = minOf(w, h)
            carView.contentRect(w, h, content)   // khung hình xe THẬT — bám bánh theo lớp nền

            val wheelSpanY = (CarLayout.wheel(TyreCorner.REAR_LEFT).y - CarLayout.wheel(TyreCorner.FRONT_LEFT).y) * content.height()
            val cellH = minOf(h * 0.24f, wheelSpanY - m * 0.035f).coerceAtLeast(m * 0.10f)

            floorBig = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_VALUE_SP, resources.displayMetrics)
            floorSub = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_SUB_SP, resources.displayMetrics)
            baseBig = minOf(m * 0.150f, cellH * 0.58f).coerceAtLeast(floorBig)
            baseSub = minOf(m * 0.042f, cellH * 0.22f).coerceAtLeast(floorSub)

            val gap = m * 0.03f
            val pad = w * 0.02f
            val rows = if (readings.size < 4) PLACEHOLDER_CORNERS else readings.map { it.corner }
            rows.forEachIndexed { i, corner ->
                val a = CarLayout.wheel(corner)
                val wheelX = content.left + a.x * content.width()
                val wheelY = content.top + a.y * content.height()
                // Mép TRONG của thẻ kẹp ra ngoài khung ảnh thật ⇒ thẻ đục không bao giờ ăn vào thân xe (R2·b).
                val span = CellTextLayout.cardSpanX(a.x < 0.5f, w, pad, content.left, content.right, wheelX, gap)
                val rd = readings.getOrNull(i)
                if (span.usable) {
                    // OQ7 (2.76): thẻ dịch theo [CarLayout.tyreCardShift] để KHỐI 4 thẻ có tâm = tâm ảnh xe; chấm
                    // cảnh báo (bên dưới) vẫn ở `wheelY` = bánh thật. Hình học thuần ở `:core`, `TyreCardBlockTest`.
                    val cardY = CarLayout.tyreCardCenterY(corner, content.top, content.height())
                    cell.set(span.start, cardY - cellH / 2f, span.end, cardY + cellH / 2f)
                    drawCell(canvas, m, corner, rd, values.getOrNull(i), temps.getOrNull(i))
                }
                val st = rd?.status ?: TyreStatus.UNKNOWN
                if (st.alert || st == TyreStatus.UNEVEN) {
                    // Chấm vẫn ở neo bánh THẬT: chấm nói "bánh nào", thẻ chỉ cần nằm cạnh.
                    dot.color = colorFor(st)
                    canvas.drawCircle(wheelX, wheelY, minOf(content.width(), content.height()) * DOT_RATIO, dot)
                }
            }
        }

        private fun drawCell(canvas: Canvas, m: Float, corner: TyreCorner, rd: TyreReading?, value: String?, temp: String?) {
            val st = rd?.status ?: TyreStatus.UNKNOWN
            val col = colorFor(st)
            val radius = m * 0.035f
            cellFill.color = if (st.alert) ColorMath.mix(Color.parseColor(KachiTheme.CARD2), col, SEMANTIC_MIX)
            else Color.parseColor(KachiTheme.CARD2)
            canvas.drawRoundRect(cell, radius, radius, cellFill)

            val inset = m * TEXT_INSET_RATIO
            val room = cell.width() - inset * 2f
            // Ô hẹp tới mức không còn chỗ cho một chữ nào: vẽ thẻ trơn rồi dừng. Vẽ tiếp = chữ tràn khỏi thẻ, mà
            // ngay bên cạnh thẻ là thân xe ⇒ lại đúng cái lỗi (C) vừa vá.
            if (room <= 0f) return

            // ── dòng SỐ + ĐƠN VỊ ───────────────────────────────────────────────────────────────────────────
            val hasValue = value != null
            val numText = value ?: TelemetryView.PLACEHOLDER
            val unit = if (hasValue) unitLabel else ""
            bigP.textSize = baseBig
            unitP.textSize = baseBig * UNIT_RATIO
            var gapU = if (unit.isEmpty()) 0f else m * UNIT_GAP_RATIO
            var numW = bigP.measureText(numText)
            var unitW = if (unit.isEmpty()) 0f else unitP.measureText(unit)
            // Thẻ hẹp hơn hẳn sau khi kẹp mép (mất 10–20 % bề rộng đang mượn của thân xe) và đơn vị "psi"/"kPa"
            // dài hơn "bar" ⇒ phải co cho vừa, không thì cụm số tràn ra ngoài thẻ (= lại vẽ lên xe).
            val scale = CellTextLayout.fitScale(numW + gapU + unitW, room)
            if (scale < 1f) {
                bigP.textSize = (baseBig * scale).coerceAtLeast(floorBig)
                unitP.textSize = bigP.textSize * UNIT_RATIO
                gapU = if (unit.isEmpty()) 0f else m * UNIT_GAP_RATIO * scale
                numW = bigP.measureText(numText)
                unitW = if (unit.isEmpty()) 0f else unitP.measureText(unit)
            }

            // ── dòng PHỤ (viết tắt bánh · lý do · nhiệt) ────────────────────────────────────────────────────
            subP.textSize = baseSub
            var sub = listOfNotNull(corner.displayShortLabel, st.reason, temp).joinToString(SEP)
            var subW = subP.measureText(sub)
            val subScale = CellTextLayout.fitScale(subW, room)
            if (subScale < 1f) {
                subP.textSize = (baseSub * subScale).coerceAtLeast(floorSub)
                subW = subP.measureText(sub)
                if (subW > room) {   // đã tới sàn cỡ chữ mà vẫn dài ⇒ cắt đuôi, KHÔNG cho tràn lên xe
                    sub = clip(sub, room)
                    subW = subP.measureText(sub)
                }
            }

            // Baseline khối 2 dòng từ SỐ ĐO PHÔNG (hết hằng 0.10/0.60 — xem KDoc CellTextLayout).
            // Đo ink của chữ số MẪU, không của chuỗi đang vẽ: bốn thẻ phải cùng một baseline (bảng mà mỗi ô một
            // baseline thì đọc ra như lỗi), và bánh chưa đọc được ("—", hộp mực mỏng nằm giữa dòng) không được kéo
            // baseline của chính nó lên so với ba bánh bên cạnh.
            // ⚠ [SOÁT Opus 2026-09-27] Đo ở cỡ chữ **GỐC** (`baseBig`/`baseSub`), KHÔNG ở cỡ đã co riêng cho thẻ này.
            // Phép co là quyết định của TỪNG thẻ (bốn chuỗi dài khác nhau), nên lấy cỡ đã co vào baseline là bốn thẻ
            // bốn baseline — đúng thứ ba dòng ngay trên vừa hứa là không được xảy ra. Hôm nay lỗi này **latent**
            // ([ĐO] cả năm bố cục hiện có đều ra `fitScale == 1`, thẻ hẹp nhất 289 px vs cụm số dài nhất ≈ 95 px), và
            // vì `scale == 1` nên bản vá này KHÔNG đổi một pixel nào ở mọi bố cục đang dùng; nó chỉ chặn đường lùi
            // khi có một bố cục hẹp hơn, hoặc đơn vị `psi`/`kPa` (dài hơn `bar`).
            val drawnBig = bigP.textSize
            val drawnSub = subP.textSize
            bigP.textSize = baseBig
            subP.textSize = baseSub
            bigP.getTextBounds(INK_REF, 0, INK_REF.length, ink)
            val lineGap = subP.textSize * CellTextLayout.SUB_LINE_GAP
            val numBase = CellTextLayout.twoLineTopBaseline(cell.centerY(), -ink.top.toFloat(), lineGap, subP.descent())
            bigP.textSize = drawnBig
            subP.textSize = drawnSub

            bigP.color = col
            val x = CellTextLayout.lineStartX(cell.centerX(), numW, numW + gapU + unitW, cell.left, cell.right, inset)
            canvas.drawText(numText, x, numBase, bigP)
            if (unit.isNotEmpty()) canvas.drawText(unit, x + numW + gapU, numBase, unitP)

            subP.color = if (st.alert) col else colMut
            val subX = CellTextLayout.lineStartX(cell.centerX(), subW, subW, cell.left, cell.right, inset)
            canvas.drawText(sub, subX, numBase + lineGap, subP)
        }

        /** Cắt đuôi dòng phụ cho vừa [room] (chỉ chạy ở ca đã tới sàn cỡ chữ — không cấp phát ở ca thường). */
        private fun clip(s: String, room: Float): String {
            var n = s.length
            while (n > 0 && subP.measureText(s.take(n) + ELLIPSIS) > room) n--
            return if (n <= 0) "" else s.take(n) + ELLIPSIS
        }
    }

    private companion object {
        const val SEMANTIC_MIX = 0.20
        const val MIN_VALUE_SP = 13f
        const val MIN_SUB_SP = 8f
        const val CAR_LEFT = 0.30f
        const val CAR_RIGHT = 0.70f
        const val CAR_TOP = 0.04f
        /** Mép DƯỚI khung ảnh — task 3 (owner 2026-09-25): dùng gần hết chiều cao, bớt dải trống đáy (~66 px).
         *  Tên cũ `FOOTER_TOP` là di sản dòng kết luận đã gỡ ở `84f91e6`; SỐ giữ nguyên (CLAUDE.md §6). */
        const val CAR_BOTTOM = 0.98f
        const val DOT_RATIO = 0.05f
        const val UNIT_RATIO = 0.45f
        const val UNIT_GAP_RATIO = 0.018f
        const val TEXT_INSET_RATIO = 0.02f
        // Nhịp 2 dòng (`SUB_GAP_RATIO = 1.35f`) đã dời sang [CellTextLayout.SUB_LINE_GAP] ở UX7: bốn ô vẽ Canvas
        // dùng CHUNG một nhịp chữ, nên nó không được là hằng riêng của một bảng nữa (số giữ nguyên).
        const val SEP = " · "
        const val ELLIPSIS = "…"
        /** Chữ MẪU để đo chiều cao mực của dòng số (mọi chữ số cùng cap-height) — xem [CellsView.drawCell]. */
        const val INK_REF = "0"
        val PLACEHOLDER_CORNERS: List<TyreCorner> = TyreCorner.values().toList()
    }
}
