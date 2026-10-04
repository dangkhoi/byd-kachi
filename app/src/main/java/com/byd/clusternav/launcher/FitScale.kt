package com.byd.clusternav.launcher

import android.graphics.Rect
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
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
 * J1 (QA2 04/10): **chữ TÊN** ([names]) = nhãn + chữ bộ dựng khai qua [named] (bản đầy + bản ngắn) — [variant] đổi
 * bản hiện / chỗ `…` theo [FitLabels]; **giá trị** (chữ một dòng còn lại) không bao giờ `…` khi còn co được tới sàn
 * ([fitValues] — lưới không đọc được). QA3: cặp GIÁ TRỊ + CHÚ THÍCH ([FitValueRow]) — giá trị ưu tiên, chú thích nhường;
 * icon cả lưới chung một trần ([iconCapPx]); nhãn ngắn ⇒ nhãn đầy làm mô tả của CHÍNH chữ tên ([variant], soát vòng 5).
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
        val ellipsize = tv.ellipsize        // J1: cách hiện "cắt đầu" (FitLabels) trả về đúng giá trị này
        val desc: CharSequence? = tv.contentDescription   // soát 5: mô tả của bộ dựng — chữ tên trả về nó khi hiện bản đầy
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
        val free = text != null &&
            FitRules.freeText(v.getTag(R.id.kachi_fit_free_text) == true, text.maxLines, (v as TextView).ellipsize != null)
        val visibility = v.visibility

        /** `weight` bộ áp đang ghi đè lên con này (phép lật) — `null` = weight là của bộ dựng ([FitRules.weight]). */
        var heldWeight: Float? = null

        /** Icon cỡ cố định: tổng lề (px gốc) (ngang, dọc) giữa nó và mép ô — lề trong mọi khung bọc + lề ngoài; [rotInset] ở dạng NGANG. */
        var inset = 0 to 0
        var rotInset = 0 to 0
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

    /** QA3 — cặp GIÁ TRỊ + CHÚ THÍCH của khối chính ([FitValueRow]; `null` = ô không có cặp). */
    private val row: FitValueRow?

    /**
     * QA3 — trần CHUNG (px cạnh) cho icon cỡ cố định của cả lưới ([FitRules.iconCap], đặt bởi `FitGridLayout` trước [apply]);
     * `Int.MAX_VALUE` = không chặn chung.
     */
    var iconCapPx = Int.MAX_VALUE
    private var appliedCap = Int.MAX_VALUE

    var scale = 1.0
        private set
    var form = Form.VERTICAL
        private set
    var lines = 0
        private set

    /** J1 — cách hiện chữ TÊN đang áp ([variant]): nhãn đầy/ngắn × cắt cuối/đầu ([FitLabels]). */
    var nameShown = FitLabels.Variant.FULL
        private set

    /** Cỡ ô của lượt áp gần nhất ([apply]) — chặn icon theo ô. */
    private var cellW = Int.MAX_VALUE
    private var cellH = Int.MAX_VALUE

    init {
        collect(root)
        main = FitTree.main(root)
        insets()            // sau [main]: lề xoay ở dạng NGANG tuỳ con của khối chính
        mainOrientation = main?.orientation ?: LinearLayout.VERTICAL
        labels = bases.filter { it.isLabel }.map { it.v as TextView }
        hasIcon = bases.any { it.v is ImageView && it.visibility == View.VISIBLE && it.v.drawable != null }
        clickable = bases.any { it.v.isClickable }
        descHost = labels.firstOrNull()?.let { FitTree.clickableAncestor(it, root) } ?: root
        descBase = descHost.contentDescription
        row = FitValueRow.of(
            main,
            bases.filter { b ->
                val t = b.text
                b.v.parent === main && b.lpW == FitRules.MATCH && t != null && !t.auto && !b.free && t.maxLines == 1 && t.ellipsize != null
            }.map { it.v as TextView },
            ::basePx,
        ) { tv -> bases.any { it.v === tv && isName(it) } }
    }

    private fun collect(v: View) {
        bases += Base(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i))
    }

    /** Lề (px gốc) giữa mỗi icon cỡ cố định và mép ô: lề trong mọi cha tới gốc ô + lề ngoài; soát 6: cả dạng NGANG ([Base.rotInset]). */
    private fun insets() {
        val byView = bases.associateBy { it.v }
        bases.filter { it.v is ImageView && it.lpW > 0 && it.lpH > 0 }.forEach { b ->
            val s = IntArray(4)     // ngang, dọc (dạng dọc) · ngang, dọc (dạng NGANG)
            fun add(p: IntArray, rotated: Boolean) {
                FitRules.insetOf(p[0], p[1], p[2], p[3], false).let { s[0] += it.first; s[1] += it.second }
                FitRules.insetOf(p[0], p[1], p[2], p[3], rotated).let { s[2] += it.first; s[3] += it.second }
            }
            b.margins?.let { add(it, b.v.parent === main && !b.relative) }
            var cur = b.v.parent as? View
            while (cur != null) {
                byView[cur]?.pad?.let { p -> add(p, cur.parent === main && cur.background == null) }
                if (cur === root) break
                cur = cur.parent as? View
            }
            b.inset = s[0] to s[1]; b.rotInset = s[2] to s[3]
        }
    }

    /**
     * Áp hệ số [k] + dạng [f] + số dòng nhãn [n] (0 = như bộ dựng) trong ô [cw]×[ch] (px; mặc định = không chặn — đo
     * dò ở thang 1). Trả `true` nếu có ít nhất một giá trị đổi (tức đã có `requestLayout`). Gọi lại cùng bộ là no-op.
     * Icon cỡ cố định không to hơn chỗ của nó trong ô ([iconK], QA 04/10).
     */
    fun apply(k: Double, f: Form, n: Int, cw: Int = Int.MAX_VALUE, ch: Int = Int.MAX_VALUE): Boolean {
        if (k == scale && f == form && n == lines && cw == cellW && ch == cellH && iconCapPx == appliedCap) return false
        scale = k; form = f; lines = n; cellW = cw; cellH = ch; appliedCap = iconCapPx
        var changed = false
        val dropLabels = f == Form.ICON_ONLY && hasIcon && labels.isNotEmpty()
        for (b in bases) {
            val rot = f == Form.HORIZONTAL && b.v.parent === main
            changed = padding(b, k, rot) or changed
            changed = params(b, iconK(b, k), rot) or changed
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
        // QA3: cặp giá trị/chú thích — phân chia gốc (giá trị đúng nhu cầu, chú thích phần còn lại) ở cỡ chữ vừa áp.
        row?.let { r -> r.probe(f == Form.HORIZONTAL); changed = rowParams(f == Form.HORIZONTAL) or changed }
        val desc = desc(dropLabels)
        if (descHost.contentDescription?.toString() != desc?.toString()) descHost.contentDescription = desc
        return changed
    }

    /**
     * Mô tả trợ năng của ô bấm: chỉ-icon ⇒ nhãn (ĐẦY) bị ẩn; còn lại ⇒ mô tả của bộ dựng. Soát vòng 5 (P3): nhãn NGẮN không
     * còn ghi lên ô bấm (mô tả của ô THAY cả cây con ⇒ TalkBack mất chữ chọn/con số) — bản đầy nằm trên CHÍNH chữ tên ([variant]).
     */
    private fun desc(dropLabels: Boolean): CharSequence? = if (dropLabels) labels.joinToString(" ") { fullText(it) } else descBase

    /** Áp lại `LayoutParams` của cặp giá trị/chú thích ([FitValueRow.lp] trong [params]); `true` nếu có ghi. */
    private fun rowParams(rot: Boolean): Boolean {
        val r = row ?: return false
        var changed = false
        for (b in bases) if (r.owns(b.v)) changed = params(b, scale, rot) or changed
        return changed
    }

    /**
     * J1 — áp cách hiện chữ TÊN [v] ([FitLabels.Variant]) lên mọi chữ tên của ô ([names]): nhãn ngắn (chỉ chữ có
     * [named] khai bản ngắn) và `…` ở đầu (chỉ chữ đang MỘT dòng — `StaticLayout.java:1078-1103` r47). Chỉ ghi khi đổi
     * thật; trả `true` nếu có ghi. Áp SAU [apply] (số dòng đã đặt).
     */
    fun variant(v: FitLabels.Variant): Boolean {
        nameShown = v
        var changed = false
        for (b in bases) {
            val tv = b.v as? TextView ?: continue
            if (!isName(b)) continue
            val short = tv.getTag(R.id.kachi_fit_short_text) as? CharSequence
            val use = v.short && short != null
            if (usesShort(tv) != use) tv.setTag(R.id.kachi_fit_use_short, use)
            val want = if (use) short!! else fullText(tv)
            if (tv.text.toString() != want.toString()) { tv.text = want; changed = true }
            val base = b.text?.ellipsize      // của bộ dựng — chỗ `…` chỉ đổi khi bộ dựng đã cho phép `…`
            val e = if (v.start && base != null && tv.maxLines == 1) TextUtils.TruncateAt.START else base
            if (tv.ellipsize != e) { tv.ellipsize = e; changed = true }
            // Soát vòng 5 (P3): bản ngắn ⇒ bản ĐẦY làm mô tả trợ năng của CHÍNH chữ tên (không của ô bấm — KDoc FitLabels.spoken).
            val say = FitLabels.spoken(use, fullText(tv), b.text?.desc)
            if (tv.contentDescription?.toString() != say?.toString()) tv.contentDescription = say
        }
        return changed
    }

    /** Chữ TÊN của ô: nhãn (KDoc lớp) + mọi chữ bộ dựng khai qua [named] (chú thích = tên datum). Xét lại mỗi lần gọi. */
    fun names(): List<TextView> = bases.filter(::isName).map { it.v as TextView }

    private fun isName(b: Base): Boolean = b.v is TextView && (b.isLabel || b.v.getTag(R.id.kachi_fit_full_text) != null)

    /** Ô có ít nhất một chữ tên mang bản NGẮN khác bản đầy ([named]). */
    fun hasShort(): Boolean = names().any { it.getTag(R.id.kachi_fit_short_text) != null }

    /** Mọi chữ tên đang hiện là MỘT dòng — điều kiện của `…` ở đầu. */
    fun oneLine(): Boolean = names().filter { visibleInTile(it) }.all { it.maxLines == 1 }

    /** Bản ĐẦY của chữ [tv] (đã khai qua [named]; không khai ⇒ chính chữ đang hiện — chữ ấy không bao giờ bị đổi). */
    fun fullText(tv: TextView): CharSequence = tv.getTag(R.id.kachi_fit_full_text) as? CharSequence ?: tv.text

    private fun usesShort(tv: TextView): Boolean = tv.getTag(R.id.kachi_fit_use_short) == true

    private fun sc(v: Int, k: Double): Int = (v * k).roundToInt()

    /**
     * Hệ số cho [b]: icon cỡ cố định ⇒ [FitRules.iconScale] chặn theo chỗ còn lại trong ô (ô trừ lề × [k]); mọi view
     * khác ⇒ [k]. Không có ô (đo dò) ⇒ [k].
     *
     * Soát vòng 4 (P3) — NÚT BẤM cỡ cố định (nút nhạc trước/phát/sau: `ImageView` bấm được, LayoutParams 48×48dp) KHÔNG
     * bị chặn theo ô: R-WF4 "nút nhạc 48dp không co dưới 48dp" — chặn nó là bóp đích chạm dưới sàn mà [FitProbe] đã giữ
     * ([FitScale.baseTouchSides]). Ô quá thấp thì nút TRÀN (phép kiểm tràn bắt được, lưới đổi bố cục), không co.
     */
    private fun iconK(b: Base, k: Double): Double {
        if (b.v !is ImageView || b.v.isClickable || b.lpW <= 0 || b.lpH <= 0 || cellW == Int.MAX_VALUE || cellH == Int.MAX_VALUE) return k
        val own = room(b, form, k, cellW, cellH)
        // QA3: trần CHUNG của lưới ([iconCapPx] — FitRules.iconCap) ⇒ icon gần trần cùng cỡ; soát 6: icon to hơn hẳn giữ cỡ của nó.
        return if (iconCapPx == Int.MAX_VALUE) own else minOf(b.lpW, b.lpH).toDouble().let { FitRules.iconShared(own * it, iconCapPx.toDouble()) / it }
    }

    /** Hệ số [FitRules.iconScale] của icon [b] ở dạng [f] trong ô [cw]×[ch] — lề theo DẠNG ([Base.rotInset] khi NGANG, soát vòng 6). */
    private fun room(b: Base, f: Form, k: Double, cw: Int, ch: Int): Double {
        val (x, y) = if (f == Form.HORIZONTAL) b.rotInset else b.inset
        return FitRules.iconScale(k, b.lpW, b.lpH, cw - sc(x, k), ch - sc(y, k))
    }

    /**
     * QA3 — mỗi icon cỡ cố định không bấm được của ô ở lưới `k` = [k], dạng [f] (dạng SẮP áp — chưa phải [form]), ô [cw]×[ch]:
     * (cạnh ở `k`, cạnh sau khi chặn theo chỗ của nó) — đầu vào [FitRules.iconCap] (trần chung của lưới). Không đổi view nào.
     */
    fun iconSides(k: Double, f: Form, cw: Int, ch: Int): List<Pair<Double, Double>> =
        bases.filter { it.v is ImageView && !it.v.isClickable && it.lpW > 0 && it.lpH > 0 }
            .map { b -> minOf(b.lpW, b.lpH).toDouble().let { side -> k * side to room(b, f, k, cw, ch) * side } }

    /**
     * Luật GIÁ TRỊ ([FitValues]) trên số đo của lượt vừa rồi — không lượt đo view nào; trả `true` nếu đổi cỡ chữ / chỗ / chú
     * thích nhường (chữ + `LayoutParams` tự xin lượt đo).
     *  - J1 (QA2): lưới KHÔNG đọc được ⇒ mọi chữ GIÁ TRỊ một dòng có `…` (số, chú thích đơn vị, chữ chọn — không phải chữ tên
     *    [names], không chữ tự do, không autosize) TỰ CO tới cỡ lớn nhất vừa chỗ của nó, không dưới sàn [floorPx];
     *  - QA3: cặp giá trị/chú thích ([FitValueRow]) — hàng NGANG: giá trị nhận đúng nhu cầu (co theo CẢ phần hàng khi lưới
     *    không đọc được), chú thích phần còn lại hoặc nhường; khối DỌC: chú thích nhường khi khối cao hơn ô. [tick] = đổ tại
     *    chỗ (hàng ngang chỉ chia lại khi giá trị sẽ bị cắt).
     */
    fun fitValues(floorPx: Float, legible: Boolean, tick: Boolean = false): Boolean {
        var changed = false
        val rot = form == Form.HORIZONTAL
        val nameSet = names().toSet()
        if (!legible) for (b in bases) {
            val tv = b.v as? TextView ?: continue
            val t = b.text ?: continue
            if (t.auto || b.free || tv in nameSet || tv.maxLines != 1 || tv.ellipsize == null || !visibleInTile(tv)) continue
            if (rot && tv === row?.value) continue      // hàng ngang: giá trị co theo CẢ phần hàng (FitValueRow.fit)
            val avail = tv.measuredWidth - tv.compoundPaddingLeft - tv.compoundPaddingRight
            if (avail <= 0 || tv.textSize <= 0f) continue
            // Soát vòng 6 (P3): nhịp đổ tại chỗ chỉ CO — chữ còn vừa cỡ đang có ⇒ giữ (99 ↔ 100 không nhảy cỡ), lượt khớp kế lớn lại.
            if (tick && FitValues.holds(tv.textSize, avail, FitValueRow.needOf(tv))) continue
            val px = FitValues.valuePx((t.px * scale).toFloat(), floorPx, avail, FitValueRow.needOf(tv))
            if (abs(tv.textSize - px) > 0.01f) { tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px); changed = true }
        }
        val r = row ?: return changed
        val fit = (basePx(r.value) * scale).toFloat()
        if (visibleInTile(r.value) && r.fit(rot, fit, legible, floorPx, tick)) changed = rowParams(rot) or true
        return changed
    }

    /** QA3 — hộp "CHỈ GIÁ TRỊ" ở dạng đang áp ([FitValueRow.whole]); `null` = ô không có cặp giá trị/chú thích. */
    fun <T> whole(valuePx: Float, measure: () -> T): T? =
        row?.whole(valuePx, form == Form.HORIZONTAL, { rowParams(form == Form.HORIZONTAL) }, measure)

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
     * `weight` đổi trục khi lật, con `MATCH_PARENT` CHIA hàng ngang thay vì nuốt hết hàng), weight chỉ ghi khi bộ áp sở
     * hữu nó ([FitRules.weight]); lề ngoài × k (lề "chỉ dọc" xoay khi lật). Icon cỡ cố định đổi cỡ ⇒
     * [KachiIcons.refit] chọn lại biến thể + tint theo cỡ ĐÃ KHỚP.
     */
    private fun params(b: Base, k: Double, rot: Boolean): Boolean {
        val lp = b.v.layoutParams ?: return false
        val t = FitRules.lp(FitRules.Lp(b.lpW, b.lpH, b.weight), k, rot).let { row?.lp(b.v, it, rot) ?: it }
        var changed = false
        if (lp.width != t.width) { lp.width = t.width; changed = true }
        if (lp.height != t.height) { lp.height = t.height; changed = true }
        if (lp is LinearLayout.LayoutParams) {
            // Soát vòng 2 (P3): chỉ ghi weight bộ áp SỞ HỮU (lật ngang) — weight bộ dựng đổi lúc chạy (thanh tiến trình
            // nhạc) không bị kéo về số lúc chụp ở mỗi lượt áp.
            FitRules.weight(b.weight, t.weight, lp.weight, b.heldWeight)?.let { lp.weight = it; changed = true }
            b.heldWeight = t.weight.takeIf { it != b.weight }
        }
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

    /**
     * [v] và mọi cha của nó tới gốc ô đều `VISIBLE` (không dựa `isShown` — lúc đo dò ô có thể chưa gắn cửa sổ). QA3: chú thích
     * đang NHƯỜNG ([FitValueRow.hides]) không tính là đang hiện.
     */
    fun visibleInTile(v: View): Boolean {
        if (row?.hides(v) == true) return false
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
     * `true` nếu [tv] là chữ TỰ DO một dòng mà bộ dựng KHAI ([markFree] — tên bài, nghệ sĩ) — `…` là thiết kế của nó,
     * phép kiểm cắt chữ chỉ đòi nó trọn tới ngân sách [FitRules.FREE_TEXT_EM]. Giá trị/chú thích `maxLines = 1` +
     * `ellipsize` KHÔNG phải (soát vòng 2, P3 — [FitRules.freeText]); nhãn (`maxLines` 2..3 GỐC) cũng không.
     */
    fun freeLine(tv: TextView): Boolean = bases.firstOrNull { it.v === tv }?.free == true

    /** `true` nếu [tv] là NHÃN của ô (KDoc lớp) — hiện/ẩn của nó do bộ áp sở hữu ([FitRules.sigStep]). */
    fun isLabel(tv: TextView): Boolean = bases.firstOrNull { it.v === tv }?.isLabel == true

    /** Mọi khung con (`ViewGroup`) của ô, kể cả gốc — cho phép kiểm con TRÀN khung cha ([FitProbe.clipped]). */
    fun groups(): List<ViewGroup> = bases.mapNotNull { it.v as? ViewGroup }

    /** Cạnh nhỏ của các `ImageView` cỡ cố định (px gốc) — sàn icon. */
    fun baseIconSides(): List<Int> = bases.filter { it.v is ImageView && it.lpW > 0 && it.lpH > 0 }.map { minOf(it.lpW, it.lpH) }

    /** Cạnh nhỏ của các nút bấm cỡ cố định bên trong ô (px gốc) — sàn đích chạm (nút nhạc 48dp không được co). */
    fun baseTouchSides(): List<Int> =
        bases.filter { it.v !== root && it.v.isClickable && it.lpW > 0 && it.lpH > 0 }.map { minOf(it.lpW, it.lpH) }

    companion object {
        /**
         * Bộ dựng KHAI [tv] là chữ tự do (dài vô hạn theo thiết kế: tên bài, nghệ sĩ) ⇒ được `…` sau ngân sách
         * [FitRules.FREE_TEXT_EM] thay vì kéo cỡ cả lưới. Gọi lúc DỰNG, trước khi ô vào lưới khớp (bộ áp chụp một lần).
         */
        fun markFree(tv: TextView): TextView = tv.apply { setTag(R.id.kachi_fit_free_text, true) }

        /**
         * J1 — bộ dựng KHAI chữ TÊN [tv] có hai bản: [full] (nhãn đầy) và [short] (nhãn NGẮN đã dịch — `null`/rỗng/trùng
         * ⇒ không có bản ngắn). Ghi cả hai lên chính `TextView` (phép khớp chụp một lần lúc dựng, chữ đổ lại mỗi nhịp
         * cũng đi qua đây) rồi hiện đúng bản phép khớp đang chọn ([variant] — cờ `kachi_fit_use_short`). Dấu chữ của ô
         * tính trên bản ĐẦY ([fullText]) ⇒ đổi bản hiện không làm ô đo dò lại.
         */
        fun named(tv: TextView, full: CharSequence, short: CharSequence?, name: Boolean = true): TextView = tv.apply {
            // [name] = false: chữ này lượt này KHÔNG phải tên (chú thích đổi sang đơn vị) ⇒ gỡ khai báo, hiện [full].
            val alt = short?.takeIf { name && it.isNotBlank() && it.toString() != full.toString() }
            setTag(R.id.kachi_fit_full_text, if (name) full else null)
            setTag(R.id.kachi_fit_short_text, alt)
            val want = if (alt != null && getTag(R.id.kachi_fit_use_short) == true) alt else full
            if (text.toString() != want.toString()) text = want
        }
    }
}
