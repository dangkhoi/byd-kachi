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
 *  • **ĐÈ** [CellsView] (trong suốt) — 4 cụm số; `invalidate` khi số đổi. Số đổi ⇒ chỉ lớp này
 *    vẽ lại, HÌNH XE ĐỨNG YÊN (hết nháy). Lớp đè tính `content` (letterbox) từ [CarImageView.contentRect] để
 *    ô bám đúng bánh — hai lớp cùng công thức bố cục ([carDstIn]).
 *
 * ## 2.74 · R2 — ba lỗi hình học đã sửa (hình học thuần nằm ở [CellTextLayout], kiểm off-car)
 *  • thẻ đục KHÔNG còn đè lên thân xe: mép trong kẹp theo khung ảnh THẬT ([CellTextLayout.cardSpanX]);
 *  • khối 2 dòng canh giữa ô bằng SỐ ĐO PHÔNG ([CellTextLayout.twoLineTopBaseline]) — hết hằng `0.10`/`0.60`;
 *  • cụm `số + đơn vị` cân giữa trục ô (owner 13/10), dòng phụ cùng trục, cả hai co cho vừa thẻ
 *    ([CellTextLayout.lineStartX]/`fitScale`).
 *
 * Ranh giới vẫn giữ: ô vẽ 0 ngưỡng (màu + lý do + số + đơn vị do [TyreBoard]/chỗ gọi), 0 viền/blur/shadow.
 *
 * ## 3.01 R22·R — hết "hộp trong hộp" (owner 13/10: *"xấu và thô"*; brief Kim §2.2)
 * Bốn thẻ nền đục quanh xe đã GỠ (cùng chấm cảnh báo trên bánh): áp suất là số nét LIGHT đứng thẳng trên thẻ ô, ở cột
 * ngoài thân xe ngang hàng bánh, canh mép về phía xe (cột trái canh phải, cột phải canh trái) ⇒ hai cột thẳng hàng. Owner
 * 13/10 *"giảm text"*: CHỈ con số — không "bar" lặp (đơn vị suy ra được), không "TT/TP/ST/SP" (vị trí nói bánh nào); dòng
 * phụ nhỏ lùi màu = nhiệt (+ lý do khi xe báo). Cảnh báo CHỈ tô màu chữ (hổ phách/đỏ). Hình xe = ảnh như cũ.
 *
 * ## 2.88 — màu theo LỜI PHÁN CỦA XE ([TyreReading.severity], luật M của [TyreJudge]), không còn số ngưỡng nào
 * Đỏ/vàng = xe báo; số thường = xe nói bình thường; xám mờ = chưa phán được (xe chưa trả mã nào — thay đổi thấy được:
 * trước 2.88 bánh có số mà chưa ai phán vẫn in đậm như "bình thường").
 */
class TyreBoardView(context: Context) : FrameLayout(context), MinUsefulHeight {

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
     * 2.93 `GROUPBOARD-1ROW` — cao tối thiểu để thẻ bánh còn chữ trên sàn: thẻ cao [CELL_RATIO] × cao bảng phải chứa dòng số
     * ([MIN_VALUE_SP]) + dòng phụ ([MIN_SUB_SP] × nhịp [CellTextLayout.SUB_LINE_GAP]) — tính từ chính sàn chữ của bảng.
     */
    override val minUsefulHeightPx: Int
        get() {
            val dm = resources.displayMetrics
            val big = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_VALUE_SP, dm)
            val sub = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_SUB_SP, dm)
            return kotlin.math.ceil((big + sub * CellTextLayout.SUB_LINE_GAP) / CELL_RATIO).toInt()
        }

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

        /** Số = nét LIGHT (vai số chính của widget, [KachiWidgetType.heroFace]) — không đậm giả (brief Kim §2.4). */
        private val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT; typeface = KachiWidgetType.heroFace; color = Color.parseColor(KachiTheme.INK)
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

        private fun colorFor(s: TyreSeverity): Int = when (s) {
            TyreSeverity.ALERT -> colRed
            TyreSeverity.WARN -> colAmber
            TyreSeverity.OK -> colInk
            // R22·R: số giờ đứng TRÊN mặt ô (hết thẻ card2) ⇒ MUT2 chỉ còn 4,48:1 trên kính bảng sáng [ĐO máy ảo] ⇒ dùng MUT
            // (5,05:1) — vẫn "lùi" so với số đã phán (INK), chỉ không chạm sàn.
            TyreSeverity.NONE -> colMut
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val m = minOf(w, h)
            carView.contentRect(w, h, content)   // khung hình xe THẬT — bám bánh theo lớp nền

            val art = carView.art   // R23: bánh theo ẢNH đang vẽ
            val wheelSpanY = (art.wheel(TyreCorner.REAR_LEFT).y - art.wheel(TyreCorner.FRONT_LEFT).y) * content.height()
            val cellH = minOf(h * CELL_RATIO, wheelSpanY - m * 0.035f).coerceAtLeast(m * 0.10f)

            floorBig = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_VALUE_SP, resources.displayMetrics)
            floorSub = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_SUB_SP, resources.displayMetrics)
            baseBig = minOf(m * 0.150f, cellH * 0.58f).coerceAtLeast(floorBig)
            baseSub = minOf(m * 0.042f, cellH * 0.22f).coerceAtLeast(floorSub)

            val gap = m * 0.03f
            val pad = w * 0.02f
            val rows = if (readings.size < 4) PLACEHOLDER_CORNERS else readings.map { it.corner }
            rows.forEachIndexed { i, corner ->
                val a = art.wheel(corner)
                val wheelX = content.left + a.x * content.width()
                // R22·R (owner 13/10 "hộp trong hộp"): KHÔNG còn thẻ nền — số đứng thẳng trên thẻ ô, cột ngoài thân xe,
                // canh mép về phía xe (trái canh phải · phải canh trái) ⇒ hai cột thẳng hàng, cùng hàng với bánh.
                val span = CellTextLayout.cardSpanX(a.x < 0.5f, w, pad, content.left, content.right, wheelX, gap)
                if (span.usable) {
                    val cy = content.top + a.y * content.height()
                    cell.set(span.start, cy - cellH / 2f, span.end, cy + cellH / 2f)
                    drawCell(canvas, m, a.x < 0.5f, readings.getOrNull(i), values.getOrNull(i), temps.getOrNull(i))
                }
            }
        }

        private fun drawCell(canvas: Canvas, m: Float, toRight: Boolean, rd: TyreReading?, value: String?, temp: String?) {
            val st = rd?.severity ?: TyreSeverity.NONE   // 2.88: MỨC NẶNG (lời phán của xe), không còn trạng thái theo ngưỡng
            // R22·R: cảnh báo chỉ tô MÀU CHỮ (số + lý do, hổ phách/đỏ ngữ nghĩa) — không nền, không chấm.
            val col = colorFor(st)
            val room = cell.width()
            if (room <= 0f) return

            // ── dòng SỐ + ĐƠN VỊ ───────────────────────────────────────────────────────────────────────────
            val numText = value ?: TelemetryView.PLACEHOLDER
            // R22·R (owner 13/10 "giảm text"): CHỈ con số — đơn vị (bar/psi/kPa) suy ra được từ độ lớn, và nằm trong nhãn
            // trợ năng của cả bảng ([set] `summary`). Bốn chữ "bar" lặp là chữ thừa.
            val unit = ""
            bigP.textSize = baseBig
            unitP.textSize = baseBig * UNIT_RATIO
            var gapU = if (unit.isEmpty()) 0f else m * UNIT_GAP_RATIO
            var numW = bigP.measureText(numText)
            var unitW = if (unit.isEmpty()) 0f else unitP.measureText(unit)
            // Cột hẹp (ô 2×3) và đơn vị "psi"/"kPa" dài hơn "bar" ⇒ co cho vừa, không thì cụm số tràn lên thân xe.
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
            // Lý do lấy từ `:core` (mã lạ ⇒ "mã N" đúng con số — kênh 1 của spec 2.88 §4.4: ảnh chụp mang mã thô).
            // R22·R "giảm text": bánh nào = VỊ TRÍ cạnh bánh (bỏ "TT/TP/ST/SP"); lý do chỉ hiện khi xe BÁO (một chữ ngắn).
            val why = if (rd != null && st.alert) rd.reason else null
            var sub = listOfNotNull(why, temp).joinToString(SEP)
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

            // Baseline khối 2 dòng từ SỐ ĐO PHÔNG, đo ở cỡ GỐC (bốn cột cùng một baseline — xem lịch sử SOÁT 2026-09-27).
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
            val groupW = numW + gapU + unitW
            val x = if (toRight) cell.right - groupW else cell.left
            canvas.drawText(numText, x, numBase, bigP)
            if (unit.isNotEmpty()) canvas.drawText(unit, x + numW + gapU, numBase, unitP)

            subP.color = if (st.alert) col else colMut   // lý do cảnh báo đi cùng MỘT màu với số (không màu thứ hai)
            canvas.drawText(sub, if (toRight) cell.right - subW else cell.left, numBase + lineGap, subP)
        }

        /** Cắt đuôi dòng phụ cho vừa [room] (chỉ chạy ở ca đã tới sàn cỡ chữ — không cấp phát ở ca thường). */
        private fun clip(s: String, room: Float): String {
            var n = s.length
            while (n > 0 && subP.measureText(s.take(n) + ELLIPSIS) > room) n--
            return if (n <= 0) "" else s.take(n) + ELLIPSIS
        }
    }

    private companion object {
        /** Cao thẻ bánh theo cao bảng — số của bản trước (0.24), tách hằng cho [minUsefulHeightPx] dùng chung. */
        const val CELL_RATIO = 0.24f
        const val MIN_VALUE_SP = 13f
        const val MIN_SUB_SP = 8f
        const val CAR_LEFT = 0.30f
        const val CAR_RIGHT = 0.70f
        const val CAR_TOP = 0.04f
        /** Mép DƯỚI khung ảnh — task 3 (owner 2026-09-25): dùng gần hết chiều cao, bớt dải trống đáy (~66 px).
         *  Tên cũ `FOOTER_TOP` là di sản dòng kết luận đã gỡ ở `84f91e6`; SỐ giữ nguyên (CLAUDE.md §6). */
        const val CAR_BOTTOM = 0.98f
        const val UNIT_RATIO = 0.45f
        const val UNIT_GAP_RATIO = 0.018f
        // Nhịp 2 dòng (`SUB_GAP_RATIO = 1.35f`) đã dời sang [CellTextLayout.SUB_LINE_GAP] ở UX7: bốn ô vẽ Canvas
        // dùng CHUNG một nhịp chữ, nên nó không được là hằng riêng của một bảng nữa (số giữ nguyên).
        const val SEP = " · "
        const val ELLIPSIS = "…"
        /** Chữ MẪU để đo chiều cao mực của dòng số (mọi chữ số cùng cap-height) — xem [CellsView.drawCell]. */
        const val INK_REF = "0"
        val PLACEHOLDER_CORNERS: List<TyreCorner> = TyreCorner.values().toList()
    }
}
