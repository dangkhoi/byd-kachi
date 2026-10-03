package com.byd.clusternav.launcher

import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.GridFit.Form
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ═══ L5 WIDGET-FIT-ALL — bộ ÁP thang + dạng cho MỘT ô widget (tổng quát, không biết ô nào là ô nào) ═══════════════
 *
 * Ô widget được dựng bởi nhiều bộ dựng (nút xe, gói lệnh, ô đọc nén, đồng hồ, tốc độ, bảng, nhạc, datum…) và mọi bộ
 * dựng đều dùng cỡ CỐ ĐỊNH (dp/sp theo vùng). [FitGridLayout] quyết một hệ số `k` + một [Form] chung cho cả lưới;
 * lớp này THI HÀNH quyết định đó trên cây view đã dựng — **không dựng lại view nào** (tháo/gắn ô giữa cú chạm là mất
 * cú bấm, bài học SOÁT P1-1 / C5) và không cần một dòng mã riêng cho từng loại ô:
 *  - chụp GIÁ TRỊ GỐC (thang 1) của mọi view một lần: lề trong, `LayoutParams` cỡ cố định + lề ngoài, cỡ tối thiểu,
 *    cỡ chữ (hoặc dải autosize), số dòng, đệm/khung drawable kèm chữ, hướng của khối chính;
 *  - [apply] đặt lại mọi số đó = gốc × `k` — vì MỌI kích thước trong ô cùng nhân `k`, hộp tự nhiên của ô cũng nhân
 *    đúng `k` (tuyến tính), nên phép khớp ở `:core` tính được trên số đo ở thang 1;
 *  - đổi DẠNG: [Form.HORIZONTAL] lật khối chính ([main]) dọc → ngang (lề "đệm dọc" của con xoay sang ngang, con dùng
 *    `weight` đổi trục, con `MATCH_PARENT` chia hàng — [FitRules.lp], soát vòng 1 P1); [Form.ICON_ONLY] ẩn NHÃN và
 *    chép chữ nhãn vào mô tả trợ năng của ô bấm (TalkBack + `uiautomator` vẫn đọc được); số dòng nhãn đặt CHUNG cho cả lưới (`minLines = maxLines = lines`) để icon các ô
 *    cùng một trục (luật KIỂM TOÁN UX mục 6 của [reserveTwoLines], nay áp cho mọi ô cùng lưới).
 *
 * **Nhãn** = `TextView` có `maxLines` 2..3 tường minh (nhãn ô nút/gói lệnh/lối tắt). Chữ số/giá trị (`maxLines = 1`)
 * và chữ tự do (mặc định `Int.MAX_VALUE`) không phải nhãn: không bị ẩn, không đổi số dòng.
 *
 * Chỉ ghi khi giá trị THẬT SỰ đổi: mỗi setter của `View`/`TextView` gọi `requestLayout` (vd `setMaxLines` —
 * `TextView.java:5336-5342` r47 — gọi kể cả khi số không đổi), và một lượt đo không được tự gây lượt đo mới vô ích.
 */
internal class FitScale(private val root: View) {

    private class TextBase(tv: TextView) {
        val px = tv.textSize
        val auto = tv.autoSizeTextType != TextView.AUTO_SIZE_TEXT_TYPE_NONE
        val autoMin = tv.autoSizeMinTextSize
        val autoMax = tv.autoSizeMaxTextSize
        val autoStep = tv.autoSizeStepGranularity
        val minWidth = tv.minWidth          // px, −1 khi đặt theo em
        val minHeight = tv.minHeight        // px, −1 khi đặt theo dòng (nhãn)
        val maxLines = tv.maxLines
        val minLines = tv.minLines
        val drawPad = tv.compoundDrawablePadding
        val drawBounds: List<Rect?> = tv.compoundDrawablesRelative.map { d -> d?.bounds?.let { Rect(it) } }
    }

    private class Base(val v: View) {
        val pad = intArrayOf(v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom)
        val lpW = v.layoutParams?.width ?: 0
        val lpH = v.layoutParams?.height ?: 0
        val weight = (v.layoutParams as? LinearLayout.LayoutParams)?.weight ?: 0f
        val margins = (v.layoutParams as? ViewGroup.MarginLayoutParams)
            ?.let { intArrayOf(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin) }
        val relative = (v.layoutParams as? ViewGroup.MarginLayoutParams)?.isMarginRelative == true
        val relStart = (v.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart ?: 0
        val relEnd = (v.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd ?: 0
        val minW = v.minimumWidth
        val minH = v.minimumHeight
        val text = (v as? TextView)?.let { TextBase(it) }
        val isLabel = text != null && text.maxLines in 2..3
        val free = text != null && text.maxLines == 1 && (v as TextView).ellipsize != null
        val visibility = v.visibility
    }

    private val bases = ArrayList<Base>()

    /** Khối chính: `LinearLayout` DỌC đầu tiên (duyệt theo bề rộng) có ≥ 2 con đang hiện — thứ [Form.HORIZONTAL] lật. */
    val main: LinearLayout?
    private val mainOrientation: Int

    /** Nhãn của ô (xem KDoc lớp) — thứ [Form.ICON_ONLY] ẩn và số dòng chung áp vào. */
    val labels: List<TextView>

    /** Ô có icon (một `ImageView` đang hiện, có hình) — không có icon thì không được ẩn nhãn (ô sẽ trống trơn). */
    val hasIcon: Boolean

    /** Ô có thứ bấm được ⇒ lưới phải giữ đích chạm 48dp. */
    val clickable: Boolean

    /** View nhận mô tả trợ năng khi nhãn bị ẩn: ô bấm gần nhất bọc nhãn đầu, không có thì gốc. */
    private val descHost: View
    private val descBase: CharSequence?

    var scale = 1.0
        private set
    var form = Form.VERTICAL
        private set
    var lines = 0
        private set

    init {
        collect(root)
        main = bfsMain(root)
        mainOrientation = main?.orientation ?: LinearLayout.VERTICAL
        labels = bases.filter { it.isLabel }.map { it.v as TextView }
        hasIcon = bases.any { it.v is ImageView && it.visibility == View.VISIBLE && it.v.drawable != null }
        clickable = bases.any { it.v.isClickable }
        descHost = labels.firstOrNull()?.let { clickableAncestor(it) } ?: root
        descBase = descHost.contentDescription
    }

    private fun collect(v: View) {
        bases += Base(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i))
    }

    private fun bfsMain(r: View): LinearLayout? {
        val queue = ArrayDeque<View>().apply { add(r) }
        while (queue.isNotEmpty()) {
            val v = queue.removeFirst()
            if (v is LinearLayout && v.orientation == LinearLayout.VERTICAL &&
                (0 until v.childCount).count { v.getChildAt(it).visibility != View.GONE } >= 2
            ) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) queue.add(v.getChildAt(i))
        }
        return null
    }

    private fun clickableAncestor(v: View): View? {
        var cur: View? = v
        while (cur != null) {
            if (cur.isClickable) return cur
            if (cur === root) return null
            cur = cur.parent as? View
        }
        return null
    }

    /**
     * Áp hệ số [k] + dạng [f] + số dòng nhãn [n] (0 = như bộ dựng). Trả `true` nếu có ít nhất một giá trị đổi
     * (tức đã có `requestLayout`). Gọi lại với cùng bộ ba là no-op.
     */
    fun apply(k: Double, f: Form, n: Int): Boolean {
        if (k == scale && f == form && n == lines) return false
        scale = k; form = f; lines = n
        var changed = false
        val dropLabels = f == Form.ICON_ONLY && hasIcon && labels.isNotEmpty()
        for (b in bases) {
            val rot = f == Form.HORIZONTAL && b.v.parent === main
            changed = padding(b, k, rot) or changed
            changed = params(b, k, rot) or changed
            if (b.text == null) {
                if (b.minW > 0 && b.v.minimumWidth != sc(b.minW, k)) { b.v.minimumWidth = sc(b.minW, k); changed = true }
                if (b.minH > 0 && b.v.minimumHeight != sc(b.minH, k)) { b.v.minimumHeight = sc(b.minH, k); changed = true }
            } else {
                changed = text(b.v as TextView, b, b.text, k, if (b.isLabel) n else 0, dropLabels) or changed
            }
        }
        main?.let {
            val o = if (f == Form.HORIZONTAL) LinearLayout.HORIZONTAL else mainOrientation
            if (it.orientation != o) { it.orientation = o; changed = true }
        }
        val desc = if (dropLabels) labels.joinToString(" ") { it.text } else descBase
        if (descHost.contentDescription?.toString() != desc?.toString()) descHost.contentDescription = desc
        return changed
    }

    private fun sc(v: Int, k: Double): Int = (v * k).roundToInt()

    /** Lề trong × k. Con của khối chính khi lật ngang: lề "chỉ dọc" (trái = phải = 0, không nền) xoay thành ngang. */
    private fun padding(b: Base, k: Double, rot: Boolean): Boolean {
        var (l, t, r, bt) = b.pad.let { listOf(it[0], it[1], it[2], it[3]) }
        if (rot && l == 0 && r == 0 && b.v.background == null) { l = t; r = bt; t = 0; bt = 0 }
        val v = b.v
        val want = intArrayOf(sc(l, k), sc(t, k), sc(r, k), sc(bt, k))
        if (v.paddingLeft == want[0] && v.paddingTop == want[1] && v.paddingRight == want[2] && v.paddingBottom == want[3]) return false
        v.setPadding(want[0], want[1], want[2], want[3])
        return true
    }

    /**
     * `LayoutParams`: bề rộng/cao/`weight` đích do [FitRules.lp] quyết (`:core`, test thuần — cỡ cố định × k, con
     * `weight` đổi trục khi lật, con `MATCH_PARENT` CHIA hàng ngang thay vì nuốt hết hàng); lề ngoài × k (lề "chỉ
     * dọc" xoay khi lật). Icon cỡ cố định đổi cỡ ⇒ [KachiIcons.refit] chọn lại biến thể + tint theo cỡ ĐÃ KHỚP.
     */
    private fun params(b: Base, k: Double, rot: Boolean): Boolean {
        val lp = b.v.layoutParams ?: return false
        val t = FitRules.lp(FitRules.Lp(b.lpW, b.lpH, b.weight), k, rot)
        var changed = false
        if (lp.width != t.width) { lp.width = t.width; changed = true }
        if (lp.height != t.height) { lp.height = t.height; changed = true }
        if (lp is LinearLayout.LayoutParams && lp.weight != t.weight) { lp.weight = t.weight; changed = true }
        if (changed && b.v is ImageView && t.width > 0 && t.height > 0) KachiIcons.refit(b.v, minOf(t.width, t.height))
        val m = b.margins
        if (m != null && lp is ViewGroup.MarginLayoutParams) {
            var (l, t, r, bt) = listOf(m[0], m[1], m[2], m[3])
            if (rot && !b.relative && l == 0 && r == 0) { l = t; r = bt; t = 0; bt = 0 }
            val want = intArrayOf(sc(l, k), sc(t, k), sc(r, k), sc(bt, k))
            if (lp.leftMargin != want[0] || lp.topMargin != want[1] || lp.rightMargin != want[2] || lp.bottomMargin != want[3]) {
                lp.setMargins(want[0], want[1], want[2], want[3]); changed = true
            }
            if (b.relative && (lp.marginStart != sc(b.relStart, k) || lp.marginEnd != sc(b.relEnd, k))) {
                lp.marginStart = sc(b.relStart, k); lp.marginEnd = sc(b.relEnd, k); changed = true
            }
        }
        if (changed) b.v.layoutParams = lp
        return changed
    }

    /**
     * Chữ: cỡ × k (hoặc dải autosize × k — `setTextSize` là no-op khi autosize đang bật, `TextView.java:4271-4275`),
     * cỡ tối thiểu px × k, drawable kèm chữ × k; nhãn: số dòng chung + ẩn ở dạng chỉ-icon.
     */
    private fun text(tv: TextView, b: Base, t: TextBase, k: Double, n: Int, drop: Boolean): Boolean {
        var changed = false
        if (t.auto) {
            val lo = sc(t.autoMin, k).coerceAtLeast(1)
            val hi = sc(t.autoMax, k).coerceAtLeast(lo + 1)
            if (tv.autoSizeMinTextSize != lo || tv.autoSizeMaxTextSize != hi) {
                tv.setAutoSizeTextTypeUniformWithConfiguration(lo, hi, t.autoStep.coerceAtLeast(1), TypedValue.COMPLEX_UNIT_PX)
                changed = true
            }
        } else {
            val px = (t.px * k).toFloat()
            if (abs(tv.textSize - px) > 0.01f) { tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px); changed = true }
        }
        if (t.minWidth > 0 && tv.minWidth != sc(t.minWidth, k)) { tv.minWidth = sc(t.minWidth, k); changed = true }
        if (t.minHeight > 0 && tv.minHeight != sc(t.minHeight, k)) { tv.minHeight = sc(t.minHeight, k); changed = true }
        if (t.drawPad > 0 && tv.compoundDrawablePadding != sc(t.drawPad, k)) {
            tv.compoundDrawablePadding = sc(t.drawPad, k); changed = true
        }
        if (t.drawBounds.any { it != null && !it.isEmpty }) {
            val ds = tv.compoundDrawablesRelative
            var moved = false
            ds.forEachIndexed { i, d ->
                val r0 = t.drawBounds.getOrNull(i) ?: return@forEachIndexed
                if (d == null || r0.isEmpty) return@forEachIndexed
                val w = sc(r0.width(), k).coerceAtLeast(1); val h = sc(r0.height(), k).coerceAtLeast(1)
                if (d.bounds.width() != w || d.bounds.height() != h) { d.setBounds(0, 0, w, h); moved = true }
            }
            // `TextView` giữ cỡ drawable lúc đặt (mDrawables) ⇒ đặt lại cùng bộ drawable để nó đo lại.
            if (moved) { tv.setCompoundDrawablesRelative(ds[0], ds[1], ds[2], ds[3]); changed = true }
        }
        if (b.isLabel) {
            val vis = if (drop) View.GONE else b.visibility
            if (tv.visibility != vis) { tv.visibility = vis; changed = true }
            val max = if (n > 0) n else t.maxLines
            val min = if (n > 0) n else t.minLines.coerceAtLeast(0)
            if (tv.maxLines != max) { tv.maxLines = max; changed = true }
            if (tv.minLines != min) { tv.minLines = min; changed = true }
        }
        return changed
    }

    /** Mọi `TextView` của ô (kể cả nhãn, kể cả đang ẩn) — cho phép kiểm cắt chữ, sàn chữ, dấu nội dung. */
    fun texts(): List<TextView> = bases.mapNotNull { it.v as? TextView }

    /** [v] và mọi cha của nó tới gốc ô đều `VISIBLE` (không dựa `isShown` — lúc đo dò ô có thể chưa gắn cửa sổ). */
    fun visibleInTile(v: View): Boolean {
        var cur: View? = v
        while (cur != null) {
            if (cur.visibility != View.VISIBLE) return false
            if (cur === root) return true
            cur = cur.parent as? View
        }
        return true
    }

    /**
     * Ép MỌI view đang hiện của ô đo lại ở lần `measure` kế tiếp. Không có bước này thì `View.measure` có thể trả số
     * từ bộ đệm đo (`View.java:24519-24552` r47 — khoá theo cặp MeasureSpec) mà KHÔNG chạy `onMeasure`, nên
     * `TextView.getLayout()` còn là bố cục của lần đo trước ở bề rộng khác ⇒ phép kiểm cắt chữ đọc nhầm.
     */
    fun forceAll() {
        bases.forEach { if (it.v.visibility != View.GONE) it.v.forceLayout() }
    }

    /** Cỡ chữ GỐC (px) của [tv] — sàn đọc được tính trên số gốc, không trên số đã nhân. */
    fun basePx(tv: TextView): Float = bases.firstOrNull { it.v === tv }?.text?.px ?: tv.textSize

    /** `true` nếu [tv] là chữ tự co (autosize) — nó tự lo khoảng trống, không đưa vào phép kiểm cắt chữ. */
    fun autoSized(tv: TextView): Boolean = bases.firstOrNull { it.v === tv }?.text?.auto == true

    /**
     * `true` nếu [tv] là chữ TỰ DO một dòng (bộ dựng đặt `maxLines = 1` + `ellipsize`: giá trị, tên bài) — `…` là thiết
     * kế của nó, phép kiểm cắt chữ chỉ đòi nó trọn tới ngân sách [FitRules.FREE_TEXT_EM]. Nhãn (`maxLines` 2..3 GỐC) không
     * phải, kể cả khi dạng 1 dòng đặt nó về `maxLines = 1`.
     */
    fun freeLine(tv: TextView): Boolean = bases.firstOrNull { it.v === tv }?.free == true

    /** Mọi khung con (`ViewGroup`) của ô, kể cả gốc — cho phép kiểm con TRÀN khung cha ([FitProbe.clipped]). */
    fun groups(): List<ViewGroup> = bases.mapNotNull { it.v as? ViewGroup }

    /** Cạnh nhỏ của các `ImageView` cỡ cố định (px gốc) — sàn icon. */
    fun baseIconSides(): List<Int> = bases.filter { it.v is ImageView && it.lpW > 0 && it.lpH > 0 }.map { minOf(it.lpW, it.lpH) }

    /** Cạnh nhỏ của các nút bấm cỡ cố định bên trong ô (px gốc) — sàn đích chạm (nút nhạc 48dp không được co). */
    fun baseTouchSides(): List<Int> =
        bases.filter { it.v !== root && it.v.isClickable && it.lpW > 0 && it.lpH > 0 }.map { minOf(it.lpW, it.lpH) }
}
