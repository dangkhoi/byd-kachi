package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

/**
 * ═══ 2.74 · UX7 — HÀNG "MỘT TRỤC": con SỐ ở trục ô, ĐƠN VỊ treo bên phải nó ════════════════════════════════════
 *
 * Bệnh [ĐO]: mọi ô đọc của launcher xếp `số + đơn vị` bằng `LinearLayout(HORIZONTAL) + gravity = CENTER`, tức canh
 * giữa **CẢ CỤM**. Nhãn/dòng phụ ở trên-dưới thì canh giữa đúng trục ô, nên bản thân CON SỐ — thứ mắt đọc — lệch
 * trái đúng `(khe + rộng đơn vị)/2`: ô *Tốc độ* lệch **29,1 px**, số chính của thẻ nhóm **17,9 px**, ô đọc chung
 * **11,4 px**, ô đọc thanh nút `DOCK`/`GROUP` **8,9 px** (density 1,5 — bảng §3.4 của
 * `docs/diagnostics/offcar-2026-09-26/ux-ux7-composite-widgets.md`, nguồn DUY NHẤT của mấy con số này; công thức là
 * [ĐO], phần px là [SUY] theo mô hình advance của phông). Đây
 * chính là lỗi (2) mà bảng lốp đã sửa ở UX2 — [CellTextLayout.lineStartX] — chỉ khác là ở đây chữ do `TextView`
 * vẽ chứ không do `Canvas`, nên phép canh phải nằm trong một `ViewGroup`.
 *
 * ## Giao kèo
 *  • **con 0** = NEO (số): tâm của nó nằm trên trục dọc của hàng;
 *  • **con 1** (tuỳ chọn, `GONE` được) = đuôi (đơn vị): treo ngay bên phải neo, cách [gapPx];
 *  • hết chỗ bên phải ⇒ **cả cụm nhích trái** (không bao giờ cắt mất đơn vị) — đúng luật `lineStartX`;
 *  • đo ĐƠN VỊ trước, số nhận phần còn lại ⇒ số dài thì số bị `ellipsize`, đơn vị vẫn đủ chỗ (cùng luật
 *    [TyreBoardView] dùng khi co chữ trong thẻ);
 *  • canh giữa theo chiều DỌC cho cả hai con ⇒ giống hệt `gravity = CENTER` của bản `LinearLayout` cũ, nên lề trên
 *    của đơn vị (`setPadding(0, Sp.M, 0, 0)` ở chỗ gọi) vẫn cho đúng nhịp baseline như trước.
 *
 * KHÔNG có phép canh thứ hai ở đây: hình học đi qua [CellTextLayout] (thuần, kiểm off-car). Đó là điều
 * `CompositeWidgetLayoutContractTest` canh — thêm một `centerX − lineWidth/2` nào ở `:app` là đỏ.
 */
class AxisRow(context: Context) : ViewGroup(context) {

    /** Khe giữa neo và đuôi (px). `0` khi chỗ gọi đã để khoảng trắng ngay trong chuỗi đơn vị (`" km/h"`). */
    var gapPx: Int = 0
        set(value) {
            if (field != value) { field = value; requestLayout() }
        }

    private fun shown(index: Int): View? = getChildAt(index)?.takeIf { it.visibility != GONE }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val anchor = shown(0)
        val trail = shown(1)
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val inner = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        // Chưa biết bề rộng (UNSPECIFIED) ⇒ cho chỗ rộng thoải mái; `/ 4` để cộng thêm không tràn Int.
        val room = if (mode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE / 4 else inner
        val hSpec = { lp: Int -> getChildMeasureSpec(heightMeasureSpec, paddingTop + paddingBottom, lp) }

        var trailW = 0
        var trailH = 0
        if (trail != null) {
            trail.measure(
                getChildMeasureSpec(MeasureSpec.makeMeasureSpec(room, MeasureSpec.AT_MOST), 0, trail.layoutParams.width),
                hSpec(trail.layoutParams.height),
            )
            trailW = trail.measuredWidth
            trailH = trail.measuredHeight
        }
        val gap = if (trailW > 0) gapPx else 0
        var anchorW = 0
        var anchorH = 0
        if (anchor != null) {
            val left = (room - trailW - gap).coerceAtLeast(0)
            anchor.measure(
                getChildMeasureSpec(MeasureSpec.makeMeasureSpec(left, MeasureSpec.AT_MOST), 0, anchor.layoutParams.width),
                hSpec(anchor.layoutParams.height),
            )
            anchorW = anchor.measuredWidth
            anchorH = anchor.measuredHeight
        }

        val want = anchorW + gap + trailW + paddingLeft + paddingRight
        setMeasuredDimension(
            resolveSize(want, widthMeasureSpec),
            resolveSize(maxOf(anchorH, trailH) + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val anchor = shown(0) ?: return
        val trail = shown(1)
        val left = paddingLeft.toFloat()
        val right = (r - l - paddingRight).toFloat()
        val trailW = trail?.measuredWidth ?: 0
        val gap = if (trailW > 0) gapPx else 0
        val x = CellTextLayout.lineStartX(
            centerX = (left + right) / 2f,
            anchorWidth = anchor.measuredWidth.toFloat(),
            lineWidth = (anchor.measuredWidth + gap + trailW).toFloat(),
            left = left,
            right = right,
            inset = 0f,
        ).roundToInt()
        val boxTop = paddingTop
        val boxH = (b - t) - paddingTop - paddingBottom
        place(anchor, x, boxTop, boxH)
        if (trail != null && trailW > 0) place(trail, x + anchor.measuredWidth + gap, boxTop, boxH)
    }

    private fun place(v: View, x: Int, boxTop: Int, boxH: Int) {
        val top = boxTop + ((boxH - v.measuredHeight) / 2).coerceAtLeast(0)
        v.layout(x, top, x + v.measuredWidth, top + v.measuredHeight)
    }
}
