package com.byd.clusternav.launcher

import android.content.Context
import android.view.ViewGroup
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiBars as Bars

/**
 * ═══ R-SI1 — khung đặt icon của lưới lối tắt `w_apps` theo [ShortcutGridFit] ═════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-287-look-and-keys.html` §4.4. Thay cho các `LinearLayout` lồng có ô cố định (64/52 dp) của
 * 2.85–2.86: cỡ icon + số cột + khe nay khớp theo khung THẬT của widget (ô to hay ô nén), mọi icon cùng cỡ, khe đều
 * theo mỗi trục, hàng cuối thiếu căn giữa — toàn bộ hình học ở `:core` (test thuần), lớp này chỉ ĐẶT.
 *
 * Mỗi con (icon app của [ShortcutIconsView]) được đo ĐÚNG `icon + 2 × nửa khe` mỗi trục và nhận lề trong = nửa khe:
 * hình vẽ đúng `iconPx` ở đúng chỗ phép khớp chỉ ra, còn vùng CHẠM phủ tới giữa khe (đích chạm rộng nhất có thể mà
 * hai icon không giành nhau) — cùng lẽ ô cũ 64 dp quanh icon 52 dp.
 *
 * Khớp lại CHỈ khi (rộng, cao, số icon) đổi — đo trong [onMeasure] (không phải `onSizeChanged`) để icon được đo đúng
 * cỡ ngay trong lượt đo đầu, không cần một lượt bố trí thứ hai. Cỡ icon đổi ⇒ báo [onIconPx] SAU lượt bố trí
 * (`post`): đổi drawable giữa lượt đo/đặt là `requestLayout` lồng.
 */
internal class ShortcutGridLayout(context: Context, private val onIconPx: (Int) -> Unit) : ViewGroup(context) {

    private var fit: ShortcutGridFit.Fit? = null
    private var reportedIconPx = -1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Lưới LẤP khung được cho (khe chia phần dư). Chỉ khi cha không bó (UNSPECIFIED — không có ở ô widget) mới tự
        // chọn cỡ: một hàng icon cỡ tối thiểu.
        val minIcon = dpi(context, Bars.SHORTCUT_GRID_MIN_ICON)
        val natural = (minIcon * (1 + ShortcutGridFit.GAP_RATIO)).toInt()
        val outerW = side(widthMeasureSpec, childCount * natural + natural / 2 + paddingLeft + paddingRight)
        val outerH = side(heightMeasureSpec, natural + natural / 2 + paddingTop + paddingBottom)
        setMeasuredDimension(outerW, outerH)
        val f = refit(
            (outerW - paddingLeft - paddingRight).coerceAtLeast(0),
            (outerH - paddingTop - paddingBottom).coerceAtLeast(0),
            minIcon,
        )
        val padX = (f.gapXPx / 2).toInt()
        val padY = (f.gapYPx / 2).toInt()
        val ws = MeasureSpec.makeMeasureSpec(f.iconPx + 2 * padX, MeasureSpec.EXACTLY)
        val hs = MeasureSpec.makeMeasureSpec(f.iconPx + 2 * padY, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(ws, hs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val f = fit ?: return
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val x = paddingLeft + f.left(i) - c.paddingLeft
            val y = paddingTop + f.top(i) - c.paddingTop
            c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
        }
    }

    private fun side(spec: Int, natural: Int): Int =
        if (MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED) natural else MeasureSpec.getSize(spec)

    /** Phép khớp cho khung [w] × [h] với số con hiện tại — giữ nguyên kết quả cũ khi cả ba không đổi. */
    private fun refit(w: Int, h: Int, minIcon: Int): ShortcutGridFit.Fit {
        fit?.let { if (it.widthPx == w && it.heightPx == h && it.count == childCount) return it }
        val f = ShortcutGridFit.fit(
            childCount, w, h, ShortcutGridFit.GAP_RATIO, minIcon, dpi(context, Bars.SHORTCUT_GRID_MAX_ICON),
        )
        fit = f
        val padX = (f.gapXPx / 2).toInt()
        val padY = (f.gapYPx / 2).toInt()
        for (i in 0 until childCount) getChildAt(i).setPadding(padX, padY, padX, padY)
        if (f.iconPx != reportedIconPx) {
            reportedIconPx = f.iconPx
            post { onIconPx(f.iconPx) }
        }
        return f
    }
}
