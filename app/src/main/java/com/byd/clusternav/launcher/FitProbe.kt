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
 * theo nhịp do [FitRules.reprobe] quyết. Nhịp trạng thái xe 1 Hz không chạy phép đo này.
 */
internal object FitProbe {

    /** Một dạng của lưới: hướng + số dòng nhãn giữ chỗ (0 = không đổi số dòng). */
    data class Option(val form: Form, val lines: Int)

    /**
     * Thứ tự ƯU TIÊN (hoà cỡ ⇒ dạng đứng trước thắng): dọc giữ 2 dòng (dạng gốc, luật KIỂM TOÁN UX mục 6) · dọc 1 dòng
     * (ô thấp, nhãn ngắn) · ngang 1 dòng (khung một hàng lưới) · chỉ-icon (đường lùi, xem [GridFit.Shape.fallback]).
     */
    val OPTIONS: List<Option> = listOf(
        Option(Form.VERTICAL, 2), Option(Form.VERTICAL, 1), Option(Form.HORIZONTAL, 1), Option(Form.ICON_ONLY, 0),
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
     * Nhu cầu của một ô: một [GridFit.Shape] cho mỗi [OPTIONS] (cùng chỉ số), [usable] = dạng đó có bề rộng nào vẽ
     * trọn ô không (cùng chỉ số), [sig] = dấu chữ lúc đo.
     */
    class Need(val shapes: List<GridFit.Shape>, val usable: List<Boolean>, val sig: Int)

    /** Đo [child] (gốc của ô, [fs] = bộ áp của nó) ở mọi dạng. Để ô ở trạng thái của dạng cuối — chỗ gọi áp lại. */
    fun need(child: View, fs: FitScale, floors: Floors): Need {
        val hasLabels = fs.labels.isNotEmpty()
        val shapes = ArrayList<GridFit.Shape>(OPTIONS.size)
        val usable = ArrayList<Boolean>(OPTIONS.size)
        OPTIONS.forEach { opt ->
            // Ô không có nhãn: số dòng vô nghĩa (dọc-2 ≡ dọc-1) và chỉ-icon ≡ dọc ⇒ dùng lại số đo, không đo lại.
            val same = when {
                hasLabels -> null
                opt.form == Form.ICON_ONLY || (opt.form == Form.VERTICAL && opt.lines == 1) -> shapes.firstOrNull()
                else -> null
            }
            if (same != null) {
                shapes += same.copy(form = opt.form, lines = opt.lines, fallback = opt.form == Form.ICON_ONLY)
                usable += usable.first()
            } else {
                val (s, ok) = shape(child, fs, opt, floors)
                shapes += s; usable += ok
            }
        }
        return Need(shapes, usable, signature(fs))
    }

    /** Hộp tự nhiên của [child] ở dạng [opt] + có bề rộng nào vẽ trọn ô không (KDoc lớp, bước 1–3). */
    private fun shape(child: View, fs: FitScale, opt: Option, floors: Floors): Pair<GridFit.Shape, Boolean> {
        fs.apply(1.0, opt.form, opt.lines)
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
        return GridFit.Shape(
            opt.form, hi.toDouble(), child.measuredHeight.toDouble(), minScale(fs, floors), opt.lines,
            fallback = opt.form == Form.ICON_ONLY,
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
     * ellipsize), hoặc một từ dài hơn bề rộng; con cỡ cố định rộng hơn chỗ ([spills]). Chữ tự co (autosize — ô giá trị
     * STEP) tự lo, không xét; chữ TỰ DO một dòng ([FitScale.freeLine]) chỉ tính khi còn hẹp hơn ngân sách của nó.
     */
    fun clipped(fs: FitScale): Boolean =
        fs.texts().any { tv -> visible(tv, fs) && !fs.autoSized(tv) && clippedText(tv, fs.freeLine(tv)) } ||
            fs.groups().any { g -> visible(g, fs) && spills(g) }

    private fun clippedText(tv: TextView, free: Boolean): Boolean {
        val l = tv.layout ?: return false
        val n = l.lineCount
        if (n == 0) return false
        val availW = tv.measuredWidth - tv.compoundPaddingLeft - tv.compoundPaddingRight
        val dots = (0 until n).any { l.getEllipsisCount(it) > 0 }
        if (dots && (!free || FitRules.freeTextCut(availW.toFloat(), tv.textSize))) return true
        val max = tv.maxLines
        val shown = if (max in 1 until n) max else n
        if (l.getLineEnd(shown - 1) < l.text.length) return true
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

    /** Dấu nội dung chữ của ô (chữ + hiện/ẩn) — đổi ⇒ hộp tự nhiên có thể đã đổi. */
    fun signature(fs: FitScale): Int =
        fs.texts().fold(17) { h, tv -> 31 * (31 * h + tv.text.toString().hashCode()) + tv.visibility }
}
