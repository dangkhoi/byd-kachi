package com.byd.clusternav.launcher

import android.content.Context
import android.util.TypedValue
import android.view.View
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
 *  3. tìm bề rộng NHỎ NHẤT mà ô vẫn cao ≤ `h₀` và KHÔNG chữ nào bị cắt/`…` ([clipped]) — nhãn 2 dòng được xuống dòng
 *     mà không tốn bề cao (chỗ đã giữ), hàng chia `weight` đều (bảng tổng hợp) tự lộ ra ô cần rộng hơn tổng tự nhiên.
 *     Tìm nhị phân, sai số [PRECISION_PX]. Không có bề rộng nào thoả (phép kiểm cắt chữ báo nhầm) ⇒ dùng `w₀`.
 *
 * Kết quả cache theo từng ô ở [FitGridLayout] — chỉ đo lại khi ô mới vào lưới, hoặc khi chữ của ô ĐỔI và bị cắt
 * ([signature]). Nhịp trạng thái xe 1 Hz không chạy phép đo này.
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

    /** Nhu cầu của một ô: một [GridFit.Shape] cho mỗi [OPTIONS] (cùng chỉ số) + dấu chữ lúc đo. */
    class Need(val shapes: List<GridFit.Shape>, val sig: Int)

    /** Đo [child] (gốc của ô, [fs] = bộ áp của nó) ở mọi dạng. Để ô ở trạng thái của dạng cuối — chỗ gọi áp lại. */
    fun need(child: View, fs: FitScale, floors: Floors): Need {
        val hasLabels = fs.labels.isNotEmpty()
        val shapes = ArrayList<GridFit.Shape>(OPTIONS.size)
        OPTIONS.forEach { opt ->
            // Ô không có nhãn: số dòng vô nghĩa (dọc-2 ≡ dọc-1) và chỉ-icon ≡ dọc ⇒ dùng lại số đo, không đo lại.
            val same = when {
                hasLabels -> null
                opt.form == Form.ICON_ONLY || (opt.form == Form.VERTICAL && opt.lines == 1) -> shapes.firstOrNull()
                else -> null
            }
            shapes += same?.copy(form = opt.form, lines = opt.lines, fallback = opt.form == Form.ICON_ONLY)
                ?: shape(child, fs, opt, floors)
        }
        return Need(shapes, signature(fs))
    }

    private fun shape(child: View, fs: FitScale, opt: Option, floors: Floors): GridFit.Shape {
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
        if (grow == GROW_STEPS && !ok(hi)) hi = w0 else {
            var lo = 0
            while (hi - lo > PRECISION_PX) { val mid = (lo + hi) / 2; if (ok(mid)) hi = mid else lo = mid }
        }
        fs.forceAll()
        child.measure(View.MeasureSpec.makeMeasureSpec(hi, View.MeasureSpec.EXACTLY), un)
        return GridFit.Shape(
            opt.form, hi.toDouble(), child.measuredHeight.toDouble(), minScale(fs, floors), opt.lines,
            fallback = opt.form == Form.ICON_ONLY,
        )
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
     * Có chữ nào của ô bị CẮT ở lần đo vừa rồi không — chính bệnh ảnh 03/10: nhãn bị kẹp `AT_MOST` còn nửa dòng
     * (`TextView.java:9404-9405`), hoặc bị `…`, hoặc dòng bị bỏ (quá `maxLines` không ellipsize), hoặc một từ dài hơn
     * bề rộng. Chữ tự co (autosize — ô giá trị STEP) tự lo, không xét.
     */
    fun clipped(fs: FitScale): Boolean = fs.texts().any { tv -> visible(tv, fs) && !fs.autoSized(tv) && clippedText(tv) }

    private fun clippedText(tv: TextView): Boolean {
        val l = tv.layout ?: return false
        val n = l.lineCount
        if (n == 0) return false
        for (i in 0 until n) if (l.getEllipsisCount(i) > 0) return true
        val max = tv.maxLines
        val shown = if (max in 1 until n) max else n
        if (l.getLineEnd(shown - 1) < l.text.length) return true
        val availW = tv.measuredWidth - tv.compoundPaddingLeft - tv.compoundPaddingRight
        for (i in 0 until shown) if (l.getLineWidth(i) > availW + 1f) return true
        val availH = tv.measuredHeight - tv.compoundPaddingTop - tv.compoundPaddingBottom
        return l.getLineTop(shown) > availH + 1
    }

    /** Dấu nội dung chữ của ô (chữ + hiện/ẩn) — đổi ⇒ hộp tự nhiên có thể đã đổi. */
    fun signature(fs: FitScale): Int =
        fs.texts().fold(17) { h, tv -> 31 * (31 * h + tv.text.toString().hashCode()) + tv.visibility }
}
