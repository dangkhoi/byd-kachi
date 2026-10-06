package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.ceil

/**
 * ═══ 2.93 `WIDGET-CAR-STRIP-LAYOUT` — khung của widget *Trạng thái xe*: hình xe TRÊN hay CẠNH chú thích ═══════════════
 *
 * Quyết định là của `:core` [CarStripFit] (test thuần); lớp này chỉ ĐO chú thích rồi đặt hai con theo kế hoạch. Thay khối
 * dọc `WidgetViews.col` của 2.92 (cùng lề trong [pad] = 8 dp): ở khung đứng/vuông/to kế hoạch luôn là STACK = đúng dáng cũ
 * (hình lấp phần trên, chú thích dưới căn giữa, khe [gap]); khung rộng thấp ⇒ SIDE (hình cao trọn khung, chú thích bên phải,
 * cả cụm căn giữa).
 *
 * Bề ngang chú thích dùng cho quyết định = chữ DÀI NHẤT trong [captions] (mọi chữ widget này có thể hiện) đo bằng chính
 * `Paint` của chú thích ⇒ cửa mở/đóng đổi chữ không lật cách xếp, không `requestLayout` mỗi nhịp (chú thích bề ngang
 * TĨNH trong mỗi cách xếp). Tỉ lệ hình xe = tỉ lệ thân xe nhìn từ trên [CAR_ASPECT] (cùng số với placeholder của
 * `CarImageLayer`) — ảnh xe người dùng thay thì `CarMiniView` vẫn letterbox trong hộp, chỉ ranh quyết định lệch chút.
 *
 * Ô tự lấp khung (chứa `CarMiniView`) ⇒ `FitGridLayout.selfFitting` để nguyên, không co bằng `FitScale` — như 2.92.
 */
@SuppressLint("ViewConstructor")   // chỉ dựng bằng mã (WidgetViews), không bao giờ từ XML
internal class CarStateLayout(
    context: Context,
    private val art: View,
    private val caption: TextView,
    private val pad: Int,
    private val gap: Int,
    private val captions: List<CharSequence>,
) : ViewGroup(context) {

    /** Cách xếp của lượt đo gần nhất — cho bài canh/QA (không ai ghi từ ngoài). */
    var arrange: CarStripFit.Arrange = CarStripFit.Arrange.STACK
        private set

    private var plan: CarStripFit.Plan? = null
    private var capW = 0

    init {
        addView(art, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // ⚠ Bề ngang KHÔNG `WRAP_CONTENT`: chú thích được đổ chữ mỗi nhịp xe (1 Hz) và `TextView` bề ngang WRAP đổi chữ ⇒
        // `requestLayout` mỗi nhịp (`TextView.checkForRelayout`, r47 `TextView.java:9641-9692`) — `addView` trần của
        // `ViewGroup` cho đúng WRAP. Khai MATCH ⇒ nhánh bề ngang tĩnh (như con của khối dọc `col` 2.92); đo thì do [onMeasure].
        addView(caption, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val widest = captions.maxOfOrNull { caption.paint.measureText(it.toString()) } ?: 0f
        capW = ceil(widest.toDouble()).toInt() + caption.compoundPaddingLeft + caption.compoundPaddingRight + 1
        val iw = (w - 2 * pad).coerceAtLeast(0)
        // Soát senior 2.93 Pass 1 [P3]: cao chú thích = cao THẬT của chữ đang hiện ở bề ngang khối dọc. Khung hẹp + chữ dài
        // (ms "Semua 4 pintu tertutup") ⇒ xuống 2 dòng như `col` 2.92; bản đo MỘT dòng rồi ép EXACTLY cắt mất nửa chữ. Khung
        // đủ chỗ cho SIDE thì capW < iw ⇒ mọi chữ một dòng ⇒ cùng số như đo UNSPECIFIED (quyết định SIDE/STACK không đổi).
        val capSpec = if (iw > 0) MeasureSpec.makeMeasureSpec(iw, MeasureSpec.AT_MOST) else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        caption.measure(capSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val capH = caption.measuredHeight
        val p = CarStripFit.plan(w, h, pad, gap, capW, capH, CAR_ASPECT)
        plan = p; arrange = p.arrange
        when (p.arrange) {
            CarStripFit.Arrange.STACK -> {
                art.measure(exactly(iw), exactly(p.artH))
                caption.measure(exactly(iw), exactly(capH))
            }
            CarStripFit.Arrange.SIDE -> {
                art.measure(exactly(p.artW), exactly(p.artH))
                caption.measure(exactly(minOf(capW, (iw - p.artW - gap).coerceAtLeast(0))), exactly(capH))
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val p = plan ?: return
        val w = r - l; val h = b - t
        when (p.arrange) {
            CarStripFit.Arrange.STACK -> {
                art.layout(pad, pad, pad + art.measuredWidth, pad + art.measuredHeight)
                val ct = pad + art.measuredHeight + gap
                caption.layout(pad, ct, pad + caption.measuredWidth, ct + caption.measuredHeight)
            }
            CarStripFit.Arrange.SIDE -> {
                val group = art.measuredWidth + gap + caption.measuredWidth
                val x0 = ((w - group) / 2).coerceAtLeast(pad)
                art.layout(x0, pad, x0 + art.measuredWidth, pad + art.measuredHeight)
                val cx = x0 + art.measuredWidth + gap
                val ct = (h - caption.measuredHeight) / 2
                caption.layout(cx, ct, cx + caption.measuredWidth, ct + caption.measuredHeight)
            }
        }
    }

    private fun exactly(px: Int) = MeasureSpec.makeMeasureSpec(px.coerceAtLeast(0), MeasureSpec.EXACTLY)

    companion object {
        /** Tỉ lệ rộng:cao thân xe nhìn từ trên — cùng số với `CarImageLayer.PLACEHOLDER_ASPECT` (0,46). */
        const val CAR_ASPECT = 0.46
    }
}
