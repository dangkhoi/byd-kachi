package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi

/**
 * Năm bộ dựng drawable **không-chất-liệu** của [KachiTheme] — tách ra khỏi `KachiTheme.kt` ở P1b vì trần 500 dòng
 * (CLAUDE.md §4.1). Là hàm mở rộng trên chính `object KachiTheme` nên **mọi chỗ gọi giữ nguyên** (`KachiTheme.card(…)`)
 * và mọi bài canh đếm chuỗi `KachiTheme.card(`/`KachiTheme.pill(` trong tầng vẽ vẫn đúng.
 *
 * Bề mặt CÓ chất liệu ([KachiTheme.surface]) ở lại tệp gốc: hai bài canh (`SurfaceMaterialContractTest` ·
 * `SurfaceContrastContractTest`) đọc thân hàm đó từ đúng tệp đó.
 */

/**
 * Thẻ kính bo góc + viền mảnh.
 *
 * `radius` nhận **dp dạng `Int`** (T5) chứ không phải `Float` như trước: bán kính giờ đi qua họ hằng
 * `KachiSpace.RADIUS_*`, và để `Float` thì mọi chỗ gọi phải viết `.toFloat()` — tức là mời số trần quay lại.
 */
fun KachiTheme.card(
    ctx: Context,
    radius: Int = KachiSpace.RADIUS_XL,
    fill: String = CARD_FILL,
    stroke: String = LINE,
): GradientDrawable =
    GradientDrawable().apply {
        cornerRadius = KachiSpace.dpf(ctx, radius)
        setColor(c(fill))
        setStroke(dpi(ctx, KachiSpace.HAIRLINE), c(stroke))
    }

/**
 * Nền gradient accent (nút chính / tile bật).
 *
 * ⚠ Mặc định là [KachiTheme.GRAD_FROM]/[KachiTheme.GRAD_TO], **không** phải [KachiTheme.ACCENT]/[KachiTheme.ACCENT2]:
 * chữ [KachiTheme.ON_ACCENT] nằm TRÊN nền này, và [ĐO] trắng trên `#4c7dff` chỉ **3.69:1** (dưới 4.5). `GRAD_FROM` là
 * cùng họ xanh nhưng tối một bậc ⇒ 4.83:1. `ACCENT` vẫn là màu nhận diện cho chấm/viền/lớp tô nhạt — chỗ không có
 * chữ đè lên. (P1b: khi người dùng chọn màu nhấn, cặp này đã qua `ContrastGuard` — xem `KachiPaletteDerive.kt`.)
 */
fun KachiTheme.gradient(ctx: Context, radius: Int, from: String = GRAD_FROM, to: String = GRAD_TO): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(from), c(to))).apply {
        cornerRadius = KachiSpace.dpf(ctx, radius)
    }

/**
 * Tile DOCK BẬT — gradient accent BÁN TRONG SUỐT + viền accent, khớp prototype `.dtile.on`
 * (accent 36% → accent2 32%, viền accent 55%). KHÁC gradient đặc [gradient] (dùng cho pill/preset chọn).
 */
fun KachiTheme.gradientSoft(ctx: Context, radius: Int): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(TILE_ON_FROM), c(TILE_ON_TO))).apply {
        cornerRadius = KachiSpace.dpf(ctx, radius); setStroke(dpi(ctx, KachiSpace.HAIRLINE), c(TILE_ON_LINE))
    }

/**
 * Fade dưới nhãn ô (slot-head) — khớp prototype `linear-gradient(180deg, rgba(0,0,0,.55), transparent)`.
 *
 * ⚠ Bản SÁNG dùng mờ **TRẮNG** chứ không phải mờ đen: mực trên nhãn này là [KachiTheme.INK], và ở bảng sáng `INK`
 * là mực đậm ⇒ mờ đen sẽ làm chữ đậm nằm trên nền đậm.
 */
fun KachiTheme.topFade(ctx: Context, radius: Int = KachiSpace.RADIUS_L): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c(SCRIM_HEAD), c(CLEAR))).apply {
        val r = KachiSpace.dpf(ctx, radius)
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    }

/** Viên thuốc (pill) bo tròn hết cỡ. */
fun KachiTheme.pill(ctx: Context, fill: String = CARD_FILL, stroke: String = LINE): GradientDrawable =
    GradientDrawable().apply {
        cornerRadius = KachiSpace.dpf(ctx, KachiSpace.RADIUS_PILL)
        setColor(c(fill))
        setStroke(dpi(ctx, KachiSpace.HAIRLINE), c(stroke))
    }
