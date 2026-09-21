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
 * **ô tròn** cỡ chạm ([KachiSpace.TOUCH]) tô đúng màu sẽ được áp; dưới hàng là tên lựa chọn đang chọn (để người mù
 * màu đọc được đang chọn gì).
 *
 * ## ⚠⚠ WP1 · R1.1 (2026-09-20) — GỠ CẢ BA VIỀN, và hai ca phải xử đặc biệt
 * Owner: *"KHÔNG còn viền ở BẤT CỨ ĐÂU hết."* Hàng này từng có **ba** `setStroke` (ô đang chọn: vành [INK] dày · ô
 * *theo ảnh nền* chưa có màu: gạch đứt · mọi ô còn lại: vành [LINE_STRONG]). Gỡ cả ba, nhưng [ĐO] phép quét tương
 * phản từng ô màu trên nền hàng cho thấy **hai** ô sẽ tàng hình nếu chỉ gỡ mà không thay:
 *
 * | ca | trước | sau | cách xử |
 * |---|---|---|---|
 * | sơn **BLACK** (bảng TỐI) | điểm giữa `#2e343e` trên nền hàng `#222941` = **1.15×** (tàng hình) | **1.85×** | ô sơn nay tô **CHUYỂN SẮC hai đầu THẬT** của màu sơn thay cho một điểm giữa — đầu sáng `#4a5361` tự cho tương phản, và nó **đúng hơn**: thân xe vốn là chuyển sắc đó |
 * | *theo ảnh nền* **chưa có ảnh** | [KachiTheme.CHIP_OFF] = `#222941`, **trùng đúng byte** với nền hàng = **1.00×** | **1.22×** tối · **1.34×** sáng | đổi nền ô sang [KachiTheme.FIELD_SUNKEN] (ô lõm — "chưa có gì ở đây"), giữ dấu `?` |
 *
 * Mọi ô còn lại **đo được ≥ 1.66×** trên nền hàng ở cả hai bảng (thấp nhất: sơn PEARL bảng sáng 1.66×; các màu nhấn
 * ≥ 3.7×) ⇒ bỏ vành [LINE_STRONG] không làm ô nào biến mất. Ô **đang chọn** nhận diện bằng **dấu ✓** (mực đã chọn
 * theo tương phản đo được) + tên ở caption — hai dấu hiệu, không cần vành thứ ba.
 */

/**
 * Một ô màu: [code] lưu bền · [color] ARGB để tô (`null` = chưa có màu để xem trước) · [title] tên đã dịch.
 *
 * [color2] = đầu thứ hai của chuyển sắc, chỉ dùng cho **màu sơn xe** (thân xe là chuyển sắc DỌC hai đầu này — xem
 * `KachiPaletteSeeds.CAR_PAINTS`). `null` ⇒ ô tô ĐẶC một màu. Đây là lối thoát cho ca *sơn BLACK tàng hình* ở KDoc
 * trên: hiện đúng hai đầu thật thì vừa fix tương phản vừa nói thật hơn về màu sẽ được áp.
 */
internal class Swatch(val code: String, val color: Int?, val title: String, val color2: Int? = null)

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
            // Chưa có màu để xem trước ⇒ ô LÕM (không bịa màu, không tàng hình) — xem bảng hai ca ở KDoc tệp.
            val fill = sw.color ?: c(KachiTheme.FIELD_SUNKEN)
            // Dấu ✓ phải đọc được trên chính ô đó: chọn mực sáng/đậm theo tương phản ĐO ĐƯỢC, không theo chủ đề.
            tv.setTextColor(if (ColorMath.ratio(c(KachiTheme.ON_ACCENT), fill) >= ColorMath.ratio(c(KachiTheme.BG), fill)) c(KachiTheme.ON_ACCENT) else c(KachiTheme.BG))
            // WP1 · R1.1 — 0 viền. Ô sơn xe tô chuyển sắc DỌC hai đầu thật; mọi ô khác tô đặc.
            val second = sw.color2
            tv.background = if (sw.color != null && second != null) {
                GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(sw.color, second))
                    .apply { shape = GradientDrawable.OVAL }
            } else {
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill) }
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
