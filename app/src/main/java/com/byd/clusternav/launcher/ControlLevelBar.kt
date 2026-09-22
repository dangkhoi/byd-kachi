package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ WP2 · R2.2 — DẢI VẠCH MỨC của ô điều khiển nhiều mức ════════════════════════════════════════════════════
 *
 * Owner 2026-09-20: *"nhiều mức (ghế mát/sưởi 2 mức) → 2 vạch nhỏ: mức 1 sáng 1 vạch, mức 2 sáng 2 vạch, OFF mờ
 * cả hai"*. Thay cho dòng chữ *"Tắt / Mức 1 / Mức 2"* mà ô SELECT hiện ra trước WP2.
 *
 * **Bao nhiêu vạch và sáng mấy vạch là câu hỏi của `:core`** ([ControlVisuals.tickCount] / [ControlVisual.lit]) —
 * tệp này chỉ biết vẽ. Cùng ranh giới `ChipTone`/`GroupTone` của dự án.
 *
 * ## ⚠ Vì sao [light] chỉ đổi **độ mờ**, không đổi màu/nền
 * Ô điều khiển làm mới theo nhịp trạng thái xe (1 Hz trên xe). Dựng lại `GradientDrawable` mỗi nhịp là một lượt
 * cấp phát/giây cho mỗi vạch, và `Drawable` dùng chung giữa nhiều `View` thì chia nhau một `ConstantState` (bẫy đã
 * ghi ở KDoc [KachiTheme.surface]). Nên nền của từng vạch dựng **một lần** ở [build]; [light] chỉ đặt `alpha` —
 * không cấp phát, không đụng `ConstantState`.
 *
 * ## Vì sao màu vạch là [KachiTheme.INK_ON_ACCENT]
 * Vạch sáng chỉ xuất hiện khi ô **đang bật**, mà nền ô lúc đó là `gradientSoft` (nhấn bán trong suốt) và
 * icon/nhãn của ô dùng đúng vai này — [ĐO] `ThemePaletteContractTest` đã chứng minh vai đó đủ tương phản trên nền
 * `tileOn*` ở **cả hai** bảng màu (tối `#e7ecff`, sáng `#14224d`). Dùng một vai khác là mở một cặp màu thứ hai mà
 * chưa ai đo. Khi ô tắt thì mọi vạch nằm ở [TICK_OFF_ALPHA] nên chúng đọc ra một dấu gạch mờ trên nền trung tính.
 */
internal object ControlLevelBar {

    /** Dựng dải [count] vạch (chưa sáng vạch nào — chỗ gọi gọi [light] ngay sau đó). */
    fun build(ctx: Context, count: Int): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, dpi(ctx, Sp.XS), 0, 0)
        repeat(count) { i ->
            val tick = View(ctx).apply {
                background = GradientDrawable().apply {
                    // RADIUS_PILL = "bo hết cỡ": GradientDrawable kẹp về nửa cạnh ngắn ⇒ vạch cao BAR_THIN thành
                    // một gạch đầu tròn mà không phải tính nửa chiều cao ở đây.
                    cornerRadius = dpi(ctx, Sp.RADIUS_PILL).toFloat()
                    setColor(c(KachiTheme.INK_ON_ACCENT))
                }
            }
            addView(
                tick,
                // Owner 2026-09-22: mức hiện bằng CHẤM TRÒN (đồng nhất [DatumIconView] ở widget/picker) thay vì vạch —
                // tick VUÔNG + bo hết cỡ = hình tròn. Cạnh = [Sp.DOT] cho một chấm nhỏ gọn, đọc được.
                LinearLayout.LayoutParams(dpi(ctx, Sp.DOT), dpi(ctx, Sp.DOT)).also {
                    if (i > 0) it.marginStart = dpi(ctx, Sp.XS)
                },
            )
        }
    }

    /** Sáng [lit] vạch đầu của [bar], làm mờ phần còn lại. An toàn khi [lit] ngoài phạm vi. */
    fun light(bar: LinearLayout, lit: Int) {
        for (i in 0 until bar.childCount) {
            bar.getChildAt(i).alpha = if (i < lit) 1f else TICK_OFF_ALPHA
        }
    }

    /**
     * Độ mờ của vạch CHƯA sáng — cùng con số 0.72 mà [KachiIcons] dùng cho ô chưa chọn? **Không**: ở đây phải mờ
     * hơn hẳn, vì vạch sáng và vạch mờ nằm **cạnh nhau** trong cùng một dải, nên khác biệt phải đọc được từ xa
     * (0.72 trên nền accent gần như không phân biệt được). 0.28 cho chênh lệch ~3.6× về độ đục.
     */
    private const val TICK_OFF_ALPHA = 0.28f
}
