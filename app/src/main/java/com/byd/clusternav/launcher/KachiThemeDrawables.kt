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
 * Thẻ bo góc — **MỘT tô đặc, KHÔNG viền**.
 *
 * ## ⚠⚠ WP1 · R1.1 — vì sao tham số `stroke` đã bị XOÁ, không phải chỉ đổi mặc định
 * Bản trước để `stroke: String = CLEAR` (không viền theo *mặc định*, viền là *opt-in*). [ĐO] sau đó vẫn còn **ba**
 * chỗ gọi truyền viền vào (`KachiTopStrip` · `VoiceOverlay` · ô con nhóm WARN/ALERT) — tức hai đường kẻ dễ thấy
 * nhất màn chính sống sót qua cả một lượt "bỏ viền". Owner 2026-09-20 sau khi xem ảnh: *"gỡ hết, tất các button,
 * widget gì gỡ hết, KHÔNG còn viền ở BẤT CỨ ĐÂU hết."*
 *
 * Một tham số opt-in chỉ là *lời nhắc* đừng dùng; **xoá tham số** là một **cổng biên dịch**. Cùng lối mà P-bug2
 * đã đóng (`WorkspaceView.applyEmbedSeam` đổi 4 field sang `private` ⇒ dựng lại lỗi cũ thì KHÔNG BIÊN DỊCH ĐƯỢC
 * — mạnh hơn một bài canh đỏ). Thêm lại `stroke` ở đây là làm vỡ 3 chỗ gọi + bài canh
 * [com.byd.clusternav.launcher.ZeroBorderContractTest], không phải lặng lẽ mọc lại.
 *
 * Phân tách từ nay **chỉ bằng MÀU FILL + khoảng cách**. Bề mặt nào trước đây chỉ thấy được NHỜ viền thì đã được
 * đổi sang một fill có tương phản ĐO ĐƯỢC (xem `docs/_handoff/ux-wp1-zero-borders.md` §3), không để tàng hình.
 *
 * `radius` nhận **dp dạng `Int`** (T5) chứ không phải `Float` như trước: bán kính giờ đi qua họ hằng
 * `KachiSpace.RADIUS_*`, và để `Float` thì mọi chỗ gọi phải viết `.toFloat()` — tức là mời số trần quay lại.
 */
fun KachiTheme.card(
    ctx: Context,
    radius: Int = KachiSpace.RADIUS_XL,
    fill: String = CARD_FILL,
): GradientDrawable =
    GradientDrawable().apply {
        cornerRadius = KachiSpace.dpf(ctx, radius)
        setColor(c(fill))
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
 * Tile DOCK BẬT — gradient accent **BÁN TRONG SUỐT**, khớp prototype `.dtile.on` (accent 50% → accent2 40%).
 *
 * ## WP1 · R1.1 — viền accent đã GỠ, trạng thái BẬT đọc bằng FILL
 * Prototype có thêm `viền accent 55%`. [ĐO] bỏ viền thì trạng thái vẫn đọc ra rõ **chỉ bằng nền**: ô BẬT so với ô
 * TẮT (nền `KachiTheme.surface`) chênh **1.98×** (bảng tối, đầu chuyển sắc) / **1.59×** (bảng sáng); so với chính
 * thanh nút chênh **2.19×** / **1.57×**. Ngưỡng nhìn-ra-được của một mảng lớn là ~1.15× ⇒ dư sức.
 *
 * ⚠ Vai màu [KachiTheme.TILE_ON_LINE] **giữ lại** trong bảng màu (nó còn đi qua `KachiPaletteDerive.rc` khi người
 * dùng đổi màu nhấn) nhưng **không còn chỗ vẽ nào** — nó là dữ liệu màu, không phải một cái viền đang hiện.
 */
fun KachiTheme.gradientSoft(ctx: Context, radius: Int): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(TILE_ON_FROM), c(TILE_ON_TO))).apply {
        cornerRadius = KachiSpace.dpf(ctx, radius)
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

/**
 * Viên thuốc (pill) bo tròn hết cỡ — **KHÔNG viền** (WP1 · R1.1, xem [card] về vì sao tham số `stroke` bị xoá
 * chứ không chỉ đổi mặc định).
 */
fun KachiTheme.pill(ctx: Context, fill: String = CARD_FILL): GradientDrawable =
    GradientDrawable().apply {
        cornerRadius = KachiSpace.dpf(ctx, KachiSpace.RADIUS_PILL)
        setColor(c(fill))
    }
