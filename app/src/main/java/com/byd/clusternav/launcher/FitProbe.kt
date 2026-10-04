package com.byd.clusternav.launcher

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.GridFit.Form
import com.byd.clusternav.launcher.KachiTheme.dpi
import kotlin.math.max
import com.byd.clusternav.launcher.KachiBars as Bars
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ L5 WIDGET-FIT-ALL — ĐO hộp tự nhiên của một ô ở thang 1, cho từng dạng vẽ ═══════════════════════════════════
 *
 * Hộp tự nhiên KHÔNG gõ số dp: đo bằng chính `measure()` của ô (cùng bài học `ReadGrid.contentPx`) ⇒ đúng cho mọi
 * ngôn ngữ (dòng Thái/CJK cao hơn — số đo, không phải hằng 16 %/23 %), mọi phông hệ thống của BYD (chưa biết), mọi
 * `fontScale`. Với mỗi dạng ([OPTIONS]):
 *  1. áp dạng ở thang 1 ([FitScale.apply]);
 *  2. đo `UNSPECIFIED` ⇒ `w₀ × h₀` (nhãn một dòng nếu vừa, giữ chỗ đủ số dòng của dạng);
 *  3. tìm bề rộng NHỎ NHẤT mà ô vẫn cao ≤ `h₀`, KHÔNG chữ nào bị cắt/`…` và KHÔNG con nào tràn khung cha
 *     ([clipped]) — nhãn 2 dòng được xuống dòng mà không tốn bề cao (chỗ đã giữ), hàng chia `weight` đều (bảng tổng
 *     hợp) tự lộ ra ô cần rộng hơn tổng tự nhiên, con cỡ cố định (nút nhạc 48dp, thanh tiến trình) đặt sàn bề rộng.
 *     Tìm nhị phân, sai số [PRECISION_PX]. Không có bề rộng nào thoả ⇒ dạng đó KHÔNG DÙNG ĐƯỢC cho ô này
 *     ([Need.usable] — soát vòng 1 P1: bản trước lùi về `w₀` và để một dạng không bao giờ vẽ trọn thành ứng viên).
 *
 * Kết quả cache theo từng ô ở [FitGridLayout] — chỉ đo lại khi ô mới vào lưới, hoặc khi chữ của ô ĐỔI ([signature])
 * theo nhịp do [FitRules.reprobe] quyết. Nhịp trạng thái xe 1 Hz không chạy phép đo này. Dạng PHỤ (dự phòng · nhãn
 * ngắn · chỉ-icon — [Option.secondary]) đo LƯỜI qua [more]: chỉ khi dạng chính không cho lưới đọc được + đạt chạm.
 */
internal object FitProbe {

    /**
     * Một dạng của lưới: hướng + số dòng nhãn giữ chỗ (0 = không đổi số dòng). [reserve] = dạng có nhãn DỰ PHÒNG
     * ([GridFit.Shape.reserve]); [short] = ô hiện NHÃN NGẮN ([GridFit.Shape.short], J1 — [FitScale.named]).
     */
    data class Option(val form: Form, val lines: Int, val reserve: Boolean = false, val short: Boolean = false) {
        /**
         * Dạng PHỤ — đo LƯỜI (soát vòng 4, P3: dạng dự phòng thêm một lượt đo dò cho MỌI ô có nhãn, +25 % lượt khớp lúc
         * mở máy): chỉ khi các dạng chính không cho lưới vừa đọc được VỪA đạt chạm ([more]). Khi chúng cho được, dạng
         * phụ không bao giờ thắng ([GridFit] bước 1–2a) ⇒ không đo nó là KHÔNG đổi kết quả.
         */
        val secondary: Boolean get() = reserve || short || form == Form.ICON_ONLY
    }

    /**
     * Thứ tự ƯU TIÊN (hoà cỡ ⇒ dạng đứng trước thắng): dọc giữ 2 dòng (dạng gốc, luật KIỂM TOÁN UX mục 6) · dọc 1 dòng
     * (ô thấp, nhãn ngắn) · ngang 1 dòng (khung một hàng lưới) · ngang 2 dòng DỰ PHÒNG (QA 04/10: nhãn Mã Lai dài không
     * vừa một dòng cạnh icon, cũng không vừa ô dọc ⇒ trước đây rơi thẳng về chỉ-icon) · ba dạng chính với NHÃN NGẮN (J1)
     * · chỉ-icon (đường lùi, xem [GridFit.Shape.fallback]).
     */
    val OPTIONS: List<Option> = listOf(
        Option(Form.VERTICAL, 2), Option(Form.VERTICAL, 1), Option(Form.HORIZONTAL, 1),
        Option(Form.HORIZONTAL, 2, reserve = true),
        Option(Form.VERTICAL, 2, short = true), Option(Form.VERTICAL, 1, short = true), Option(Form.HORIZONTAL, 1, short = true),
        Option(Form.ICON_ONLY, 0),
    )

    /** Sai số (px) của phép tìm bề rộng nhỏ nhất. */
    private const val PRECISION_PX = 2

    /** Số lần nới bề rộng (× 1,5) khi chính `w₀` còn cắt chữ (hàng chia `weight` đều). */
    private const val GROW_STEPS = 4

    /** Sàn đọc được (px) — chữ [KachiBars.FIT_TEXT_MIN] sp, icon [KachiSpace.ICON_XS], đích chạm [KachiSpace.TOUCH]. */
    class Floors(val textPx: Float, val iconPx: Int, val touchPx: Int) {
        companion object {
            fun of(ctx: Context) = Floors(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, Bars.FIT_TEXT_MIN.toFloat(), ctx.resources.displayMetrics),
                dpi(ctx, Sp.ICON_XS),
                dpi(ctx, Sp.TOUCH),
            )
        }
    }

    /**
     * Nhu cầu của một ô: một [GridFit.Shape] cho mỗi [OPTIONS] (cùng chỉ số; `null` = dạng PHỤ chưa đo — [more]),
     * [usable] = dạng đó có bề rộng nào vẽ trọn ô không (cùng chỉ số), [sig] = dấu chữ lúc đo — không phụ thuộc dạng
     * ([signature]), nên bằng dấu ở dạng ô đang hiện dù đo dò để ô ở dạng cuối.
     */
    class Need(val shapes: List<GridFit.Shape?>, val usable: List<Boolean>, val sig: Int) {
        /** Còn dạng phụ chưa đo (chỉ-icon chỉ tính khi lưới cho phép — [icon]). */
        fun lacks(icon: Boolean): Boolean = OPTIONS.indices.any { shapes[it] == null && (OPTIONS[it].form != Form.ICON_ONLY || icon) }
    }

    /**
     * Đo [child] (gốc của ô, [fs] = bộ áp của nó) ở mọi dạng CHÍNH ([Option.secondary] = dạng phụ để `null`, đo lười
     * bằng [more]). Để ô ở trạng thái của dạng cuối — chỗ gọi áp lại.
     */
    fun need(child: View, fs: FitScale, floors: Floors): Need =
        fill(child, fs, floors, Need(OPTIONS.map { null }, OPTIONS.map { false }, signature(fs)), secondary = false, icon = false)

    /** Đo nốt các dạng PHỤ còn thiếu của [need] (chỉ-icon chỉ khi [icon]) — dạng đã có giữ nguyên số đo. */
    fun more(child: View, fs: FitScale, floors: Floors, need: Need, icon: Boolean): Need =
        fill(child, fs, floors, need, secondary = true, icon = icon)

    private fun fill(child: View, fs: FitScale, floors: Floors, need: Need, secondary: Boolean, icon: Boolean): Need {
        val shapes = need.shapes.toMutableList()
        val usable = need.usable.toMutableList()
        OPTIONS.forEachIndexed { i, opt ->
            if (shapes[i] != null || opt.secondary != secondary || (opt.form == Form.ICON_ONLY && !icon)) return@forEachIndexed
            val at = twin(i, fs)
            val same = shapes.getOrNull(at)
            if (same != null) {
                shapes[i] = same.copy(
                    form = opt.form, lines = opt.lines, fallback = opt.form == Form.ICON_ONLY, reserve = opt.reserve,
                    short = opt.short,
                )
                usable[i] = usable[at]
            } else {
                val (s, ok) = shape(child, fs, opt, floors)
                shapes[i] = s; usable[i] = ok
            }
        }
        return Need(shapes, usable, need.sig)
    }

    /**
     * Dạng TƯƠNG ĐƯƠNG đã đo của dạng thứ [i] (dùng lại số đo, cùng chỉ số cho cờ dùng được — không đo lại), −1 = phải
     * đo. Ô không nhãn: số dòng vô nghĩa (dọc-2 ≡ dọc-1, ngang-2 ≡ ngang-1), chỉ-icon ≡ dọc, nhãn ngắn ≡ nhãn đầy. Ô có
     * nhãn mà không chữ tên nào có bản ngắn ([FitScale.hasShort]): dạng nhãn ngắn ≡ dạng nhãn đầy cùng hướng/số dòng.
     */
    private fun twin(i: Int, fs: FitScale): Int {
        val opt = OPTIONS[i]
        fun at(short: Boolean, form: Form, lines: Int) =
            OPTIONS.indexOfFirst { !it.reserve && it.short == short && it.form == form && it.lines == lines }
        val lined = fs.labels.isNotEmpty()      // số dòng + ẩn nhãn chỉ có nghĩa với NHÃN (KDoc FitScale)
        val j = when {
            opt.short && !fs.hasShort() -> at(false, opt.form, opt.lines)
            lined -> -1
            opt.form == Form.ICON_ONLY -> 0
            opt.lines == 1 && opt.form == Form.VERTICAL -> at(opt.short, Form.VERTICAL, 2)
            opt.lines == 2 && opt.form == Form.HORIZONTAL -> at(opt.short, Form.HORIZONTAL, 1)
            else -> -1
        }
        return j.takeIf { it != i } ?: -1
    }

    /** Hộp tự nhiên của [child] ở dạng [opt] + có bề rộng nào vẽ trọn ô không (KDoc lớp, bước 1–3). */
    private fun shape(child: View, fs: FitScale, opt: Option, floors: Floors): Pair<GridFit.Shape, Boolean> {
        fs.apply(1.0, opt.form, opt.lines)
        fs.variant(if (opt.short) FitLabels.Variant.SHORT else FitLabels.Variant.FULL)
        val un = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        fs.forceAll()
        child.measure(un, un)
        val w0 = child.measuredWidth.coerceAtLeast(1)
        val h0 = child.measuredHeight
        fun ok(w: Int): Boolean {
            fs.forceAll()
            child.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), un)
            return child.measuredHeight <= h0 && !clipped(fs)
        }
        var hi = w0
        var grow = 0
        while (!ok(hi) && grow < GROW_STEPS) { hi += hi / 2 + 1; grow++ }
        val usable = grow < GROW_STEPS || ok(hi)
        if (!usable) hi = w0 else {
            var lo = 0
            while (hi - lo > PRECISION_PX) { val mid = (lo + hi) / 2; if (ok(mid)) hi = mid else lo = mid }
        }
        fs.forceAll()
        child.measure(View.MeasureSpec.makeMeasureSpec(hi, View.MeasureSpec.EXACTLY), un)
        val h = child.measuredHeight.toDouble()
        val m = minScale(fs, floors)
        // QA3: hộp "CHỈ GIÁ TRỊ" — giá trị ở cỡ mà `k = m` đưa về đúng sàn 10sp, chú thích nhường (GridFit bước 2c). Ô không có
        // cặp giá trị/chú thích ⇒ `null` (không đo thêm).
        val whole = if (m <= 0.0) null else fs.whole((floors.textPx / m).toFloat()) {
            fs.forceAll()
            child.measure(un, un)
            child.measuredWidth.toDouble() to child.measuredHeight.toDouble()
        }
        return GridFit.Shape(
            opt.form, hi.toDouble(), h, m, opt.lines,
            fallback = opt.form == Form.ICON_ONLY, reserve = opt.reserve, short = opt.short,
            wholeWidthPx = whole?.first, wholeHeightPx = whole?.second,
        ) to usable
    }

    /**
     * Sàn `k` của ô ở dạng đang áp: chữ đang hiện không xuống dưới [Floors.textPx] (chữ VI MÔ đã nhỏ hơn sàn từ bộ dựng
     * — dấu *"chưa kiểm"* 9.5sp — không bị sàn này giữ: nó là dấu, không phải nội dung), icon cỡ cố định không dưới
     * [Floors.iconPx], nút bấm cỡ cố định ≥ 48dp (nút nhạc) không co dưới 48dp.
     */
    private fun minScale(fs: FitScale, f: Floors): Double {
        var m = 0.0
        fs.texts().filter { visible(it, fs) }.forEach { tv ->
            val base = fs.basePx(tv)
            if (base >= f.textPx) m = max(m, f.textPx / base.toDouble())
        }
        fs.baseIconSides().forEach { if (it >= f.iconPx) m = max(m, f.iconPx / it.toDouble()) }
        fs.baseTouchSides().forEach { if (it >= f.touchPx) m = max(m, f.touchPx / it.toDouble()) }
        return m
    }

    /** Chữ đang hiện thật (nó và mọi cha tới gốc ô đều VISIBLE). */
    private fun visible(tv: View, fs: FitScale): Boolean = fs.visibleInTile(tv)

    /**
     * Có chữ nào của ô bị CẮT, hoặc con nào TRÀN khung cha, ở lần đo vừa rồi không — chính bệnh ảnh 03/10: nhãn bị kẹp
     * `AT_MOST` còn nửa dòng (`TextView.java:9404-9405`), hoặc bị `…`, hoặc dòng bị bỏ (quá `maxLines` không
     * ellipsize), hoặc một từ dài hơn bề rộng, hoặc một con số bị bẻ đôi qua hai dòng; con cỡ cố định rộng hơn chỗ
     * ([spills]). Chữ tự co (autosize — ô giá trị STEP) tự lo, không xét; chữ TỰ DO một dòng được KHAI
     * ([FitScale.freeLine]) chỉ tính khi còn hẹp hơn ngân sách của nó.
     */
    fun clipped(fs: FitScale): Boolean =
        fs.texts().any { tv -> visible(tv, fs) && !fs.autoSized(tv) && clippedText(tv, fs.freeLine(tv)) } ||
            fs.groups().any { g -> visible(g, fs) && spills(g) }

    /**
     * Trạng thái cắt của ô cho phép xét đo dò lại ([FitRules.Cell.check]): [FitRules.Clip.UNKNOWN] khi một chữ đang
     * hiện CHƯA có bố cục — `TextView` bề rộng `WRAP` vừa đổi chữ đã bỏ bố cục và tự xin lượt đo (KDoc
     * [FitRules.Clip.UNKNOWN]); [clipped] khi đó đọc "không cắt" là đọc nhầm (soát vòng 2, P1).
     */
    fun clip(fs: FitScale): FitRules.Clip = when {
        fs.texts().any { tv -> visible(tv, fs) && !fs.autoSized(tv) && tv.layout == null } -> FitRules.Clip.UNKNOWN
        clipped(fs) -> FitRules.Clip.YES
        else -> FitRules.Clip.NO
    }

    private fun clippedText(tv: TextView, free: Boolean): Boolean {
        val l = tv.layout ?: return false
        val n = l.lineCount
        if (n == 0) return false
        val availW = tv.measuredWidth - tv.compoundPaddingLeft - tv.compoundPaddingRight
        val dots = (0 until n).any { l.getEllipsisCount(it) > 0 }
        if (FitRules.cut(dots, free, availW.toFloat(), tv.textSize)) return true
        val max = tv.maxLines
        val shown = if (max in 1 until n) max else n
        if (l.getLineEnd(shown - 1) < l.text.length) return true
        // Số bị BẺ ĐÔI qua hai dòng (`100` → `10`/`0`, chữ `WRAP` không `maxLines` hẹp hơn chính nó) — không cắt, không
        // tràn, nhưng không đọc được (soát vòng 2, P1; [FitRules.splitsNumber]).
        for (i in 1 until shown) if (FitRules.splitsNumber(l.text, l.getLineStart(i))) return true
        // `getLineMax` (KHÔNG tính khoảng trắng cuối dòng — bộ ngắt dòng cũng không tính nó, `Layout.java:1387-1401`
        // r47), không `getLineWidth`: dòng "Sấy kính " vỡ sau dấu cách không bị báo cắt oan (soát vòng 1, P3).
        for (i in 0 until shown) if (l.getLineMax(i) > availW + 1f) return true
        val availH = tv.measuredHeight - tv.compoundPaddingTop - tv.compoundPaddingBottom
        return l.getLineTop(shown) > availH + 1
    }

    /**
     * Khung [g] để con TRÀN ra ngoài trên trục nào không ([FitRules.spills]): `LinearLayout` cộng dồn theo hướng của
     * nó, trục chéo và mọi khung khác xét từng con. Đọc số đo của lượt vừa rồi, không đo thêm.
     */
    private fun spills(g: ViewGroup): Boolean {
        val kids = (0 until g.childCount).map { g.getChildAt(it) }.filter { it.visibility != View.GONE }
        if (kids.isEmpty()) return false
        val row = (g as? LinearLayout)?.orientation
        fun across(v: View): Int = v.measuredWidth + ((v.layoutParams as? ViewGroup.MarginLayoutParams)
            ?.let { it.marginStart + it.marginEnd } ?: 0)
        fun down(v: View): Int = v.measuredHeight + ((v.layoutParams as? ViewGroup.MarginLayoutParams)
            ?.let { it.topMargin + it.bottomMargin } ?: 0)
        val padX = g.paddingLeft + g.paddingRight
        val padY = g.paddingTop + g.paddingBottom
        return FitRules.spills(g.measuredWidth, padX, kids.map(::across), stacked = row == LinearLayout.HORIZONTAL) ||
            FitRules.spills(g.measuredHeight, padY, kids.map(::down), stacked = row == LinearLayout.VERTICAL)
    }

    /**
     * Dấu nội dung chữ của ô (chữ + hiện/ẩn của chữ KHÔNG phải nhãn) — đổi ⇒ hộp tự nhiên có thể đã đổi. Hiện/ẩn của
     * nhãn do bộ áp sở hữu nên không vào dấu ([FitRules.sigStep], soát vòng 2 P2). J1: chữ tên băm theo bản ĐẦY
     * ([FitScale.fullText]) — bản đang hiện (đầy/ngắn) do bộ áp sở hữu, cùng lẽ hiện/ẩn của nhãn: băm bản đang hiện thì
     * đo dò dạng nhãn ngắn để ô ở bản ngắn ⇒ dấu lúc đo không bao giờ khớp ⇒ đo dò lại mỗi [FitRules.RECHECK_MS].
     */
    fun signature(fs: FitScale): Int =
        fs.texts().fold(17) { h, tv -> FitRules.sigStep(h, fs.fullText(tv).toString(), tv.visibility, fs.isLabel(tv)) }

    /** Chữ [tv] (một chữ TÊN của ô — [FitScale.names]) có bị cắt ở lần đo vừa rồi không — cho [FitNames]. */
    fun cut(tv: TextView): Boolean = clippedText(tv, free = false)
}
