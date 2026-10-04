package com.byd.clusternav.launcher

import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import kotlin.math.abs

/**
 * ═══ QA3 (2.87, 04/10) — cặp GIÁ TRỊ + CHÚ THÍCH của một ô widget: giá trị ưu tiên, chú thích nhường ═══════════════════════
 *
 * Tầng vẽ của [FitValues] (`:core` — luật thuần, `FitValuesTest`). Bệnh và luật: KDoc [FitValues]. Ở đây chỉ NHẬN DIỆN cặp và
 * ĐO chữ thật bằng `Paint` của nó; [FitScale] áp kết quả qua `LayoutParams` ([lp]) — không dựng view, không đụng `visibility`
 * của bộ dựng (bộ dựng đặt `GONE`/`VISIBLE` cho chú thích mỗi nhịp — `WidgetTelemetry.MiniCard.set`; chú thích "nhường" là
 * LayoutParams 0×0, nên nhịp sau không bật nó lại và không gây lượt đo mỗi nhịp).
 *
 * Nhận diện theo HÌNH cây view, không theo mã widget (CLAUDE.md §7): các con TRỰC TIẾP của khối chính có bề rộng
 * `MATCH_PARENT` (thứ dạng NGANG chia hàng — [FitRules.lp]) mà là chữ MỘT dòng có `…`, không tự do, không autosize; ≥ 2 chữ
 * như thế và đúng MỘT chữ to nhất (cỡ gốc lớn hơn hẳn mọi chữ kia), không phải chữ tên ⇒ đó là GIÁ TRỊ, các chữ kia là CHÚ
 * THÍCH. Ô nén `MiniCard` (số 17sp + dòng phụ 10.5sp) khớp; nhãn nút (2 dòng), tên bài/nghệ sĩ (tự do) không.
 */
internal class FitValueRow private constructor(
    private val main: LinearLayout,
    val value: TextView,
    private val captions: List<TextView>,
) {

    /** Bề rộng TĨNH của giá trị khi hàng lật NGANG (px, gồm lề chữ); −1 = không đặt (khối dọc). */
    var valueW = -1
        private set

    /** Chú thích đang nhường (ẩn bằng LayoutParams 0×0 — xem KDoc lớp). */
    var yielded = false
        private set

    /** Soát QA4 — khe giá trị/chú thích đang giữ TRONG [valueW] (px; 0 = không giữ — [FitValues.gapPx]). */
    private var gap = 0

    init {
        // Soát vòng 6 (P3): chú thích NHƯỜNG là view 0×0 ⇒ `getGlobalVisibleRect` false (r47 `View.java:17114-17125`) ⇒
        // `isVisibleToUser` false (`:9652-9682`) ⇒ TalkBack/`uiautomator` bỏ nút ấy, ô chỉ còn đọc `100` / `—`. Đơn vị + TÊN datum
        // chuyển lên nút GIÁ TRỊ, tính LÚC HỎI (luôn theo chữ đang hiện, không ghi mỗi nhịp). Bộ dựng có delegate riêng ⇒ không đè;
        // delegate [Spoken] của một bộ áp CŨ (view được bọc lại) ⇒ thay, để nó đọc trạng thái nhường của hàng đang sống.
        if (value.accessibilityDelegate.let { it == null || it is Spoken }) value.accessibilityDelegate = Spoken()
    }

    private inner class Spoken : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            if (!yielded) return
            val (names, units) = captions.filter { it.visibility == View.VISIBLE }.partition { it.getTag(R.id.kachi_fit_full_text) != null }
            fun say(tv: TextView): CharSequence = tv.getTag(R.id.kachi_fit_full_text) as? CharSequence ?: tv.contentDescription ?: tv.text
            info.contentDescription = FitValues.spoken(info.contentDescription ?: value.text, units.map(::say), names.map(::say))
        }
    }

    fun owns(v: View): Boolean = v === value || captions.any { it === v }

    /** Chú thích đang nhường thì không tính là đang hiện (phép kiểm cắt chữ, chữ tên, sàn `k`). */
    fun hides(v: View): Boolean = yielded && captions.any { it === v }

    /** `LayoutParams` đích của con [v] của hàng, sau [FitRules.lp] ([t]). */
    fun lp(v: View, t: FitRules.Lp, rot: Boolean): FitRules.Lp = when {
        hides(v) -> FitRules.Lp(0, 0, 0f)
        rot && v === value && valueW >= 0 -> FitRules.Lp(valueW, t.height, 0f)
        else -> t
    }

    /**
     * Phân chia gốc khi áp dạng (đo dò + lượt áp, chưa có số đo của ô): hàng ngang ⇒ giá trị đúng nhu cầu ở cỡ đang có + khe
     * ([FitValues.gapPx], soát QA4 — bề rộng nhỏ nhất đo dò được phải đủ cho khe, không thì lưới "đọc được" lại `…` chú thích),
     * chú thích chia phần còn lại (`weight`) ⇒ bề rộng nhỏ nhất đo dò được = icon + giá trị + khe + chú thích.
     */
    fun probe(rot: Boolean) {
        gap = if (rot) reserve(captions.filter { it.visibility == View.VISIBLE }) else 0
        valueW = if (rot) needAt(value.textSize) + gap else -1
        yielded = false
    }

    /** Khe phải giữ cho các chú thích đang hiện [shown] (chú thích có chữ — [FitValues.share] cũng chỉ giữ khe khi đó). */
    private fun reserve(shown: List<TextView>): Int = shown.filter { it.text.isNotEmpty() }.maxOfOrNull { FitValues.gapPx(it.textSize) } ?: 0

    /**
     * Đo hộp "CHỈ GIÁ TRỊ" ([GridFit.Shape.wholeWidthPx], đo dò ở thang 1): giá trị ở cỡ [valuePx], chú thích nhường; [relayout]
     * áp lại `LayoutParams` của hàng, [measure] đo ô rồi trả số đo; trả lại trạng thái phân chia gốc ([probe]) sau đó.
     */
    fun <T> whole(valuePx: Float, rot: Boolean, relayout: () -> Unit, measure: () -> T): T {
        val was = value.textSize
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX, valuePx)
        valueW = if (rot) needAt(valuePx) else -1
        yielded = true
        relayout()
        val out = measure()
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX, was)
        probe(rot)
        relayout()
        return out
    }

    /** Bề rộng phải dành cho giá trị ở cỡ [px]: chữ hiện tại đã chừa chữ số + lề chữ ([FitValues.needPx]). */
    fun needAt(px: Float): Int = contentNeed(value, px) + value.compoundPaddingLeft + value.compoundPaddingRight

    /**
     * Áp luật giá trị trên số đo của lượt vừa rồi. [rot] = hàng đang lật NGANG, [fitPx] = cỡ lưới của giá trị, [tick] = đổ
     * tại chỗ (hàng ngang: chỉ chia lại khi giá trị SẼ bị cắt — [FitValues.wouldClip]). Trả `true` nếu chỗ/cỡ/nhường đổi ⇒
     * chỗ gọi áp lại LayoutParams của hàng.
     */
    fun fit(rot: Boolean, fitPx: Float, legible: Boolean, floorPx: Float, tick: Boolean): Boolean {
        if (main.measuredWidth <= 0 || main.measuredHeight <= 0) return false
        val shown = captions.filter { it.visibility == View.VISIBLE }
        if (rot) {
            // Soát QA4: khe ([gap]) nằm trong [valueW] — chữ dài ra ăn vào khe cũng chia lại (không dán sát chú thích).
            if (tick && !FitValues.wouldClip(valueW - gap, needAt(value.textSize))) return false
            val flex = flex()
            val px = if (legible) fitPx else FitValues.valuePx(fitPx, floorPx, flex, ::needAt)
            val r = reserve(shown)
            val share = FitValues.share(
                flex, needAt(px), shown.sumOf { contentNeed(it, it.textSize) + gaps(it) },
                shown.maxOfOrNull { FitValues.captionMinPx(it.textSize) } ?: 0, r,
            )
            gap = if (share.captions) r else 0
            return set(px, share.valueW, yield = shown.isNotEmpty() && !share.captions)
        }
        // Khối DỌC: cỡ giá trị đã co theo bề rộng của chính nó (FitScale.fitValues); chú thích nhường khi cả khối cao hơn ô.
        gap = 0
        val yield = !legible && shown.isNotEmpty() && FitValues.stackYields(stackHeight(), main.measuredHeight)
        return set(value.textSize, -1, yield)
    }

    private fun set(px: Float, width: Int, yield: Boolean): Boolean {
        var changed = false
        if (abs(value.textSize - px) > 0.01f) { value.setTextSize(TypedValue.COMPLEX_UNIT_PX, px); changed = true }
        if (valueW != width || yielded != yield) { valueW = width; yielded = yield; changed = true }
        return changed
    }

    /** Phần hàng co giãn theo lượt đo vừa rồi: hàng trừ lề trong, mọi con KHÔNG thuộc cặp (icon, dấu "chưa kiểm") và lề ngoài. */
    private fun flex(): Int {
        var used = main.paddingLeft + main.paddingRight
        for (i in 0 until main.childCount) {
            val c = main.getChildAt(i)
            if (c.visibility == View.GONE) continue
            if (!owns(c)) used += c.measuredWidth + horizontalMargins(c)
            else if (c === value) used += horizontalMargins(c)
        }
        return main.measuredWidth - used
    }

    /** Lề chữ + lề ngoài ngang của một chú thích (phần nó chiếm ngoài chữ). */
    private fun gaps(tv: TextView): Int = tv.compoundPaddingLeft + tv.compoundPaddingRight + horizontalMargins(tv)

    private fun horizontalMargins(v: View): Int =
        (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { it.leftMargin + it.rightMargin } ?: 0

    /**
     * Chiều cao khối dọc nếu MỌI chú thích đang hiện (chú thích đang nhường cao 0 ⇒ tính theo một dòng của nó — cùng công thức
     * `BoringLayout`: `bottom − top` khi `includeFontPadding`, không thì `descent − ascent`).
     */
    private fun stackHeight(): Int {
        var h = main.paddingTop + main.paddingBottom
        for (i in 0 until main.childCount) {
            val c = main.getChildAt(i)
            if (c.visibility == View.GONE) continue
            h += (c.layoutParams as? ViewGroup.MarginLayoutParams)?.let { it.topMargin + it.bottomMargin } ?: 0
            h += if (owns(c)) lineHeight(c as TextView) else c.measuredHeight
        }
        return h
    }

    private fun lineHeight(tv: TextView): Int {
        val fm = tv.paint.fontMetricsInt
        val body = if (tv.includeFontPadding) fm.bottom - fm.top else fm.descent - fm.ascent
        return body + tv.compoundPaddingTop + tv.compoundPaddingBottom
    }

    companion object {
        /** Bút đo nháp (luồng chính) — đo ở cỡ thử mà không đổi cỡ chữ đang hiện, không cấp phát mỗi lần đo. */
        private val scratch = TextPaint()

        /** Bề rộng phần CHỮ phải dành cho [tv] ở cỡ [px]: chữ hiện tại đã chừa chữ số, đo bằng `Paint` của nó. */
        fun contentNeed(tv: TextView, px: Float): Int {
            if (tv.text.isEmpty()) return 0
            scratch.set(tv.paint)
            scratch.textSize = px
            val widest = FitValues.widestDigit { scratch.measureText(it.toString()) }
            return FitValues.needPx(scratch.measureText(FitValues.headroom(tv.text, widest)))
        }

        /** Hàm đo cho [FitValues.valuePx] của một chữ giá trị bất kỳ (bề rộng phần chữ). */
        fun needOf(tv: TextView): (Float) -> Int = { px -> contentNeed(tv, px) }

        /**
         * Cặp giá trị/chú thích của khối chính [main] (KDoc lớp), `null` = không có. [row] = các con trực tiếp của [main] mà bộ
         * áp đã lọc (bề rộng gốc `MATCH_PARENT`, chữ một dòng có `…`, không tự do, không autosize); [basePx] = cỡ chữ GỐC (thang
         * 1). Giá trị = chữ to hơn HẲN mọi chữ kia (hoà ⇒ không có cặp — thứ tự cũ) và KHÔNG phải chữ tên ([name]); chú thích
         * có thể là chữ tên (tên datum của ô đọc — nhường như mọi chú thích, [FitNames] vẫn chọn bản hiện của nó).
         */
        fun of(main: LinearLayout?, row: List<TextView>, basePx: (TextView) -> Float, name: (TextView) -> Boolean): FitValueRow? {
            if (main == null || row.size < 2) return null
            val big = row.maxByOrNull(basePx) ?: return null
            if (name(big) || row.count { basePx(it) >= basePx(big) - 0.01f } != 1) return null
            return FitValueRow(main, big, row.filter { it !== big })
        }
    }
}
