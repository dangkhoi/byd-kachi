package com.byd.clusternav.launcher

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ VISUAL-REFRESH P1b · R8 — HÀNG Ô MÀU (swatch) của màn Cài đặt ═══════════════════════════════════════════════
 *
 * Phần mở rộng của [SettingsRows] (tách tệp vì trần 500 dòng, cùng lối `WorkspacePrefsProfile.kt`): **một nơi dựng
 * component** vẫn là `SettingsRows` — hàm này dùng lại `stackLp`/`rowLabel` của nó, không dựng lề riêng.
 *
 * AC8.6: *"không bánh xe màu, không mã hex, không chỉnh từng thành phần (khó dùng trên xe)"* ⇒ mỗi lựa chọn là một
 * **ô tròn** cỡ chạm ([KachiSpace.TOUCH]) tô đúng màu sẽ được áp; ô đang chọn có viền [KachiTheme.INK] dày + dấu ✓;
 * dưới hàng là tên lựa chọn đang chọn (để người mù màu đọc được đang chọn gì). Ô *theo ảnh nền* khi chưa có ảnh vẽ
 * viền gạch đứt và dấu `?` — không bịa màu.
 */

/** Một ô màu: [code] lưu bền · [color] ARGB để tô (`null` = chưa có màu để xem trước) · [title] tên đã dịch. */
internal class Swatch(val code: String, val color: Int?, val title: String)

internal fun SettingsRows.swatchRow(label: String, options: List<Swatch>, current: String, onPick: (String) -> Unit): View {
    val dots = HashMap<String, TextView>()
    var chosen = current
    val caption = TextView(context).apply {
        setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpi(context, Sp.M), 0, 0, 0)
    }
    fun paint() {
        dots.forEach { (code, tv) ->
            val on = code == chosen
            val sw = options.first { it.code == code }
            tv.text = if (on) "✓" else if (sw.color == null) "?" else ""
            val fill = sw.color ?: c(KachiTheme.CHIP_OFF)
            // Dấu ✓ phải đọc được trên chính ô đó: chọn mực sáng/đậm theo tương phản ĐO ĐƯỢC, không theo chủ đề.
            tv.setTextColor(if (ColorMath.ratio(c(KachiTheme.ON_ACCENT), fill) >= ColorMath.ratio(c(KachiTheme.BG), fill)) c(KachiTheme.ON_ACCENT) else c(KachiTheme.BG))
            tv.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fill)
                if (on) setStroke(dpi(context, Sp.XS), c(KachiTheme.INK))
                else if (sw.color == null) setStroke(dpi(context, Sp.HAIRLINE), c(KachiTheme.MUT2), dpi(context, Sp.DASH_ON).toFloat(), dpi(context, Sp.DASH_OFF).toFloat())
                else setStroke(dpi(context, Sp.HAIRLINE), c(KachiTheme.LINE_STRONG))
            }
        }
        caption.text = options.firstOrNull { it.code == chosen }?.title ?: ""
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = stackLp()
        addView(rowLabel(label))
        options.forEach { sw ->
            val dot = TextView(context).apply {
                KachiType.apply(this, KachiType.BODY, bold = true); gravity = Gravity.CENTER
                contentDescription = sw.title
                setOnClickListener { chosen = sw.code; paint(); onPick(sw.code) }
            }
            dots[sw.code] = dot
            // Ô tròn = đích chạm 48dp; khe [KachiSpace.S] giữa các ô — cùng nhịp với chip của `chipRow`.
            addView(dot, LinearLayout.LayoutParams(dpi(context, Sp.TOUCH), dpi(context, Sp.TOUCH)).also { it.marginStart = dpi(context, Sp.S) })
        }
        addView(caption, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        paint()
    }
}
