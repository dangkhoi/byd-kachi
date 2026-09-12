package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.byd.clusternav.R

/**
 * Bảng màu + helper drawable của launcher. **Tra theo chủ đề đang chọn** (T1) — mã hex nằm ở [KachiPalette].
 *
 * ## ⚠ Trước T1 đây là 13 `const val` — và đó là lý do nút gạt chủ đề từng bị BỎ
 * `const val` là hằng **biên dịch**: chỗ gọi được nhúng thẳng chuỗi vào bytecode, nên đổi chủ đề lúc chạy không
 * thể có tác dụng. Phiên S1 đo đúng điều đó rồi kết luận nút gạt sẽ là **nút chết** và cố ý không làm
 * (`kachi-settings-screen.html` §4.5). T1 đổi 13 hằng thành **thuộc tính có getter** ⇒ tên gọi giữ nguyên (21 tệp
 * không phải sửa cách gọi) nhưng giá trị nay đọc lại mỗi lần vẽ.
 *
 * ## Một nơi ghi duy nhất
 * [applyTheme] là **chỗ duy nhất** đổi [palette]. Nó KHÔNG phải bản sao thứ hai của trạng thái: nguồn sự thật vẫn
 * là `HomeUiState.themeMode`, còn đây là **hình chiếu lúc vẽ** của nguồn đó. Bài canh
 * [com.byd.clusternav.launcher.ThemePaletteContractTest] đếm số chỗ gọi [applyTheme] trong `app/src/main` và đỏ nếu
 * có chỗ thứ hai — chính cái bẫy "hai bản sao cùng khoá" mà dự án đã sập vào ba lần.
 */
object KachiTheme {

    /** Bảng màu đang dùng. Mặc định TỐI để mọi đường vẽ trước lượt [applyTheme] đầu tiên vẫn ra bảng cũ. */
    var palette: KachiPalette = KachiPalette.DARK
        private set

    /**
     * Chọn bảng màu cho [mode] tại giờ [hour] (0..23, chỉ dùng khi mode = AUTO).
     *
     * Trả về `true` nếu bảng **ĐỔI** — chỗ gọi dùng giá trị đó để quyết định có dựng lại màn hay không. Trả về
     * `false` thay vì dựng lại vô điều kiện vì `applyTheme` được gọi mỗi lượt trạng thái đổi (1 nhịp/giây trên xe),
     * và dựng lại màn mỗi giây thì app đang chiếu trong ô bị nhả/gắn liên tục — đúng họ lỗi P-bug1/R3.
     */
    fun applyTheme(mode: ThemeMode, hour: Int): Boolean {
        val next = if (mode.isNight(hour)) KachiPalette.DARK else KachiPalette.LIGHT
        if (next == palette) return false
        palette = next
        return true
    }

    // ══ VAI MÀU — tên GIỮ NGUYÊN từ bản `const val` để 21 tệp không phải đổi cách gọi ═════════════════════
    val BG: String get() = palette.bg
    val INK: String get() = palette.ink
    val INK2: String get() = palette.ink2
    val MUT: String get() = palette.mut
    val MUT2: String get() = palette.mut2
    val ICON: String get() = palette.icon
    val CARD: String get() = palette.card
    val CARD2: String get() = palette.card2
    val CARD_FILL: String get() = palette.cardFill
    val PANEL: String get() = palette.panel
    val FIELD: String get() = palette.field
    val CELL: String get() = palette.cell
    val TILE: String get() = palette.tile
    val CHIP_OFF: String get() = palette.chipOff
    val DIM: String get() = palette.dim
    val TRACK: String get() = palette.track
    val SLOT: String get() = palette.slot
    val BAR: String get() = palette.bar
    val BAR_TOP: String get() = palette.barTop
    val HEAD_BG: String get() = palette.headBg
    val LINE: String get() = palette.line
    val LINE_STRONG: String get() = palette.lineStrong
    val GRID_LINE: String get() = palette.gridLine
    val EMPTY_FILL: String get() = palette.emptyFill
    val EMPTY_LINE: String get() = palette.emptyLine
    val WASH: String get() = palette.wash
    val OVERLAY: String get() = palette.overlay
    val ACCENT: String get() = palette.accent
    val ACCENT_INK: String get() = palette.accentInk
    val ACCENT2: String get() = palette.accent2
    val GRAD_FROM: String get() = palette.gradFrom
    val GRAD_TO: String get() = palette.gradTo
    val ON_ACCENT: String get() = palette.onAccent
    val INK_ON_ACCENT: String get() = palette.inkOnAccent
    val ACCENT_SOFT: String get() = palette.accentSoft
    val ACCENT_LINE: String get() = palette.accentLine
    val ACCENT_WASH: String get() = palette.accentWash
    val TILE_ON_FROM: String get() = palette.tileOnFrom
    val TILE_ON_TO: String get() = palette.tileOnTo
    val TILE_ON_LINE: String get() = palette.tileOnLine
    val SCRIM_PANEL: String get() = palette.scrimPanel
    val SCRIM_BTN: String get() = palette.scrimBtn
    val SCRIM_BTN2: String get() = palette.scrimBtn2
    val SCRIM_HEAD: String get() = palette.scrimHead
    val GREEN: String get() = palette.green
    val AMBER: String get() = palette.amber
    val RED: String get() = palette.red
    val CYAN: String get() = palette.cyan
    val ORANGE: String get() = palette.orange
    val SLATE: String get() = palette.slate
    val AMBER_SOFT: String get() = palette.amberSoft
    val ART_TO: String get() = palette.artTo
    val GLOW1: String get() = palette.glow1
    val GLOW2: String get() = palette.glow2
    val CLEAR: String get() = palette.clear

    fun c(s: String): Int = Color.parseColor(s)
    fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density
    fun dpi(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /**
     * Thẻ kính bo góc + viền mảnh.
     *
     * `radius` nhận **dp dạng `Int`** (T5) chứ không phải `Float` như trước: bán kính giờ đi qua họ hằng
     * `KachiSpace.RADIUS_*`, và để `Float` thì mọi chỗ gọi phải viết `.toFloat()` — tức là mời số trần quay lại.
     */
    fun card(
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
     * ⚠ Mặc định là [GRAD_FROM]/[GRAD_TO], **không** phải [ACCENT]/[ACCENT2]: chữ [ON_ACCENT] nằm TRÊN nền này, và
     * [ĐO] trắng trên `#4c7dff` chỉ **3.69:1** (dưới 4.5). [GRAD_FROM] là cùng họ xanh nhưng tối một bậc ⇒ 4.83:1.
     * [ACCENT] vẫn là màu nhận diện cho chấm/viền/lớp tô nhạt — chỗ không có chữ đè lên.
     */
    fun gradient(ctx: Context, radius: Int, from: String = GRAD_FROM, to: String = GRAD_TO): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(from), c(to))).apply {
            cornerRadius = KachiSpace.dpf(ctx, radius)
        }

    /**
     * Tile DOCK BẬT — gradient accent BÁN TRONG SUỐT + viền accent, khớp prototype `.dtile.on`
     * (accent 36% → accent2 32%, viền accent 55%). KHÁC gradient đặc [gradient] (dùng cho pill/preset chọn).
     */
    fun gradientSoft(ctx: Context, radius: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(TILE_ON_FROM), c(TILE_ON_TO))).apply {
            cornerRadius = KachiSpace.dpf(ctx, radius); setStroke(dpi(ctx, KachiSpace.HAIRLINE), c(TILE_ON_LINE))
        }

    /**
     * Fade dưới nhãn ô (slot-head) — khớp prototype `linear-gradient(180deg, rgba(0,0,0,.55), transparent)`.
     *
     * ⚠ Bản SÁNG dùng mờ **TRẮNG** chứ không phải mờ đen: mực trên nhãn này là [INK], và ở bảng sáng [INK] là mực
     * đậm ⇒ mờ đen sẽ làm chữ đậm nằm trên nền đậm.
     */
    fun topFade(ctx: Context, radius: Int = KachiSpace.RADIUS_L): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c(SCRIM_HEAD), c(CLEAR))).apply {
            val r = KachiSpace.dpf(ctx, radius)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }

    /** Viên thuốc (pill) bo tròn hết cỡ. */
    fun pill(ctx: Context, fill: String = CARD_FILL, stroke: String = LINE): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = KachiSpace.dpf(ctx, KachiSpace.RADIUS_PILL)
            setColor(c(fill))
            setStroke(dpi(ctx, KachiSpace.HAIRLINE), c(stroke))
        }

    /** Ánh xạ tên icon (ControlDef.icon / WidgetDef.icon) → vector drawable. 0 = không có. */
    fun iconRes(icon: String): Int = when (icon) {
        "ic-lock" -> R.drawable.ic_lock
        "ic-window" -> R.drawable.ic_window
        "ic-trunk" -> R.drawable.ic_trunk
        "ic-readlight" -> R.drawable.ic_readlight
        "ic-leaf" -> R.drawable.ic_leaf
        "ic-seat" -> R.drawable.ic_seat
        "ic-temp" -> R.drawable.ic_temp
        "ic-fan" -> R.drawable.ic_fan
        "ic-defrost" -> R.drawable.ic_defrost
        "ic-cam" -> R.drawable.ic_cam
        "ic-door" -> R.drawable.ic_door
        "ic-hood" -> R.drawable.ic_hood
        "ic-sunroof" -> R.drawable.ic_sunroof
        "ic-light" -> R.drawable.ic_light
        "ic-recirc" -> R.drawable.ic_recirc
        "ic-volume" -> R.drawable.ic_volume
        "ic-wiper" -> R.drawable.ic_wiper
        "ic-cast" -> R.drawable.ic_cast
        "ic-bolt" -> R.drawable.ic_bolt
        "ic-tire" -> R.drawable.ic_tire
        "ic-sun" -> R.drawable.ic_sun
        "ic-music" -> R.drawable.ic_music
        "ic-prev" -> R.drawable.ic_prev
        "ic-play" -> R.drawable.ic_play
        "ic-next" -> R.drawable.ic_next
        "ic-speed" -> R.drawable.ic_speed
        "ic-grid" -> R.drawable.ic_grid
        "ic-swap" -> R.drawable.ic_swap
        "ic-close" -> R.drawable.ic_close
        // U1: 6 icon MỚI cho khái niệm xuất hiện nhiều mà trước đây không có icon nào gần nghĩa
        "ic-road" -> R.drawable.ic_road
        "ic-battery" -> R.drawable.ic_battery
        "ic-seatbelt" -> R.drawable.ic_seatbelt
        "ic-radar" -> R.drawable.ic_radar
        "ic-gps" -> R.drawable.ic_gps
        "ic-steering" -> R.drawable.ic_steering
        // [SOÁT P3] 3 tên icon TRƯỚC ĐÂY KHÔNG được map ⇒ 13/64 nút lùi về icon NHÓM: 2 nút gương mang hình
        // KÍNH (sai nghĩa), 3 nút chế độ lái và 8 nút hỗ trợ lái mang hình lưới (không gợi nghĩa gì).
        // ── [KIỂM TOÁN UX 2026-09-12 · mục 4] 8 icon vá NGHĨA SAI / NGHĨA TRÙNG ────────────────────────
        // Mỗi tên dưới đây tồn tại vì một hình đang mang SAI nghĩa hoặc mang NHIỀU nghĩa; lý do cụ thể ghi trong
        // chính tệp XML (đó là chỗ người sửa icon sẽ đọc).
        "ic-window-open" -> R.drawable.ic_window_open
        "ic-window-close" -> R.drawable.ic_window_close
        "ic-esp" -> R.drawable.ic_esp
        "ic-car" -> R.drawable.ic_car
        "ic-photo" -> R.drawable.ic_photo
        "ic-fuel" -> R.drawable.ic_fuel
        "ic-motor" -> R.drawable.ic_motor
        "ic-shield" -> R.drawable.ic_shield
        "ic-mirror" -> R.drawable.ic_mirror
        "ic-drive" -> R.drawable.ic_drive
        "ic-adas" -> R.drawable.ic_adas
        // Tên icon dùng lại tệp đã có (trước đây chưa được map nên tra ra 0 = ô trống icon)
        "ic-clock" -> R.drawable.ic_clock_g
        "ic-turn-left" -> R.drawable.ic_turn_left
        "ic-turn-right" -> R.drawable.ic_turn_right
        // ── T2: 12 ICON NHÓM (spec kachi-capability-groups §4.1) ───────────────────────────────────────
        // Đây là ĐẦU `:app` của giao kèo tên icon cho nhóm khả năng: `CapabilityGroups` (T1, `:core`) khai
        // `icon = "ic-group-…"`, bảng này dịch sang `R.drawable`. Tên là HỢP ĐỒNG giữa hai module — đổi một bên mà
        // không đổi bên kia thì icon tra ra 0 (ô trống), nên có [IconStyleContractTest] canh đủ 12 tên tra được.
        "ic-group-tyres" -> R.drawable.ic_group_tyres
        "ic-group-windows" -> R.drawable.ic_group_windows
        "ic-group-doors" -> R.drawable.ic_group_doors
        "ic-group-lights" -> R.drawable.ic_group_lights
        "ic-group-ambient" -> R.drawable.ic_group_ambient
        // ADAS dùng LẠI ic_adas: tệp đó vốn được vẽ đúng cho nhóm này (xe nhìn từ trên + hai vệt quét), thêm tệp
        // thứ hai cùng nghĩa chỉ tạo hai bản sao phải giữ đồng bộ.
        "ic-group-adas" -> R.drawable.ic_adas
        "ic-group-occupants" -> R.drawable.ic_group_occupants
        "ic-group-parking" -> R.drawable.ic_group_parking
        "ic-group-climate" -> R.drawable.ic_group_climate
        "ic-group-energy" -> R.drawable.ic_group_energy
        "ic-group-battery" -> R.drawable.ic_group_battery_health
        "ic-group-trip" -> R.drawable.ic_group_trip
        else -> 0
    }

    /**
     * 12 tên icon NHÓM mà `:core` được phép khai (spec kachi-capability-groups §4.1).
     *
     * Khai ở đây thay vì rải trong test: nó là **danh sách hợp đồng**, và [iconRes] phải tra ra được từng tên.
     * Thứ tự = thứ tự nhóm trong spec.
     */
    val GROUP_ICON_NAMES: List<String> = listOf(
        "ic-group-tyres", "ic-group-windows", "ic-group-doors", "ic-group-lights", "ic-group-ambient",
        "ic-group-adas", "ic-group-occupants", "ic-group-parking", "ic-group-climate", "ic-group-energy",
        "ic-group-battery", "ic-group-trip",
    )
}
