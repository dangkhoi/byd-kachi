package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
 * Ô điều khiển BẬT — **nền TÔNG nhạt**: bề mặt trung tính của ô ([KachiTheme.surface]) + một lớp nhấn mỏng
 * ([KachiTheme.TILE_ON_FROM], ~14–16 % màu nhấn). Icon tô mực nhấn, chữ mực trung tính (xem `ControlTileFactory.tint`).
 *
 * ## 3.01 R22·R — gradient chàm→tím đã GỠ (owner 13/10: *"mấy cái miếng xanh xanh đó xấu và thô"*; brief Kim §2.7)
 * Bản trước (`gradientSoft`) là chuyển sắc nhấn 50 % → tím 40 % (`#4B4FB0→#5A4CC0` đo trên ảnh) ⇒ ô bật như cục kẹo,
 * lệch tông với ô đèn của bảng icon (nền nhấn mềm 15 %). Nay một tô phẳng; "bật" đọc bằng nền tông + icon nhấn — cùng
 * ngôn ngữ ô Đèn. Đổi ở VAI MÀU (`tileOnFrom/To` trong [KachiPalette]) + bộ dựng này, không ở từng ô.
 *
 * Không viền (WP1 · R1.1). Hai lớp tạo khi ô đổi trạng thái (không mỗi khung).
 */
fun KachiTheme.tonalOn(ctx: Context, radius: Int): Drawable = LayerDrawable(
    arrayOf(
        surface(ctx, radius),
        GradientDrawable().apply { cornerRadius = KachiSpace.dpf(ctx, radius); setColor(c(TILE_ON_FROM)) },
    ),
)

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
