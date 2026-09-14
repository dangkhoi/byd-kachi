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
    val WIDGET_BACKING: String get() = palette.widgetBacking
    val WALL_SCRIM: String get() = palette.wallScrim
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
        "ic-readlight" -> R.drawable.ic_readlight
        "ic-leaf" -> R.drawable.ic_leaf
        "ic-seat" -> R.drawable.ic_seat
        "ic-temp" -> R.drawable.ic_temp
        "ic-fan" -> R.drawable.ic_fan
        "ic-defrost" -> R.drawable.ic_defrost
        "ic-cam" -> R.drawable.ic_cam
        "ic-door" -> R.drawable.ic_door
        "ic-hood" -> R.drawable.ic_hood
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
        "ic-drive" -> R.drawable.ic_drive
        // ── [U6 · ĐO ảnh 2026-09-12] 18 icon MỚI: tách theo KHÁI NIỆM trong 3 nhóm dày nhất ───────────
        // Bệnh đo được: nhóm Năng lượng có 9/28 ô cùng glyph tia sét và 6/28 cùng glyph con đường; nhóm Động lực có
        // 6/14 ô cùng đồng hồ tốc; nhóm Khí hậu có 5/12 ô cùng nhiệt kế và 4/12 cùng chiếc lá. Icon trùng ở mật độ
        // đó thì nó không còn giúp phân biệt gì — người dùng phải đọc chữ trong ô 40dp (mà chữ thì bị cắt).
        // Lý do của TỪNG hình ghi trong chính tệp XML (đó là chỗ người sửa icon sẽ đọc).
        "ic-battery-charging" -> R.drawable.ic_battery_charging
        "ic-plug" -> R.drawable.ic_plug
        "ic-charger" -> R.drawable.ic_charger
        "ic-consumption" -> R.drawable.ic_consumption
        "ic-range" -> R.drawable.ic_range
        "ic-cell-temp" -> R.drawable.ic_cell_temp
        "ic-cell-volt" -> R.drawable.ic_cell_volt
        "ic-pedal" -> R.drawable.ic_pedal
        "ic-brake" -> R.drawable.ic_brake
        "ic-slope" -> R.drawable.ic_slope
        "ic-rpm" -> R.drawable.ic_rpm
        "ic-torque" -> R.drawable.ic_torque
        "ic-engine" -> R.drawable.ic_engine
        "ic-mode" -> R.drawable.ic_mode
        "ic-dust" -> R.drawable.ic_dust
        "ic-sensor" -> R.drawable.ic_sensor
        "ic-coolant" -> R.drawable.ic_coolant
        "ic-alert" -> R.drawable.ic_alert
        // 4 tên dưới dùng cho CẢ mục đọc lẫn NÚT cùng khái niệm (mục tiêu sạc · nhiệt ngoài · điều hoà · lọc khí):
        // hai ô cùng một việc thì phải cùng một hình, phần "xem hay bấm" đã nằm ở dòng phụ (U6).
        "ic-target" -> R.drawable.ic_target
        "ic-temp-out" -> R.drawable.ic_temp_out
        "ic-ac" -> R.drawable.ic_ac
        "ic-filter" -> R.drawable.ic_filter
        "ic-drift" -> R.drawable.ic_drift
        // Tên icon dùng lại tệp đã có (trước đây chưa được map nên tra ra 0 = ô trống icon)
        "ic-clock" -> R.drawable.ic_clock_g
        // ── S4 · R12 · hai hành động của CHÍNH launcher ([LauncherActions]) ───────────────────────────
        // KHÔNG vẽ hình mới: cả hai khái niệm đã có tệp đúng nghĩa trong bộ.
        //  • `ic-apps` → `ic_grid` (⊞ bốn ô). Đây KHÔNG phá luật *"⊞ chỉ còn nghĩa bảng tổng hợp"*
        //    (`CapabilityIconMeaningTest`): luật đó nói về **khả năng của XE** — mọi datum/nút/lĩnh vực từng lùi về
        //    ⊞ đã được gỡ. Ở đây ⊞ mang nghĩa gốc của nó trên mọi launcher Android: *lưới ứng dụng*. Hai chỗ dùng
        //    không bao giờ đứng cạnh nhau trong một danh sách: `w_board` có `domain = null` nên không vào bộ chọn
        //    nút, còn khối Launcher chỉ hiện ở chế độ chọn-nút-thanh-xe.
        //  • `ic-settings` → `ic_gear` (bánh răng THẬT — [ĐO] ảnh 2026-09-14: `ic_sys_g` là mặt trời 8 tia, đọc thành "độ sáng"; nét trắng 1.6 như cả bộ). `ic_menu_config.xml` cũng là bánh răng
        //    nhưng GIỮ MÀU xanh thương hiệu (nó vẽ thẳng cho bảng con nút nổi Cast, không qua bước tint) ⇒ dùng nó ở
        //    đây sẽ cho một ô xanh lạc giữa thanh nút.
        "ic-apps" -> R.drawable.ic_grid
        "ic-settings" -> R.drawable.ic_gear
        // ══ U7 · BỘ HÌNH XE THEO VỊ TRÍ (spec docs/specs/kachi-icon-set-v2.html) ═════════════════════
        // Ba KHUNG dùng chung (top · front · rear) + VÙNG TÔ là bộ phận đang được nói tới. Tên tệp mang
        // luôn khung + bộ phận + vị trí (`ic_car_top_door_lf`) nên đọc bảng này là đọc được cả nghĩa.
        // ⚠ Chín dòng đã bị GỠ ở lượt này (`ic-trunk` · `ic-sunroof` · `ic-mirror` · `ic-seatbelt` ·
        // `ic-radar` · `ic-gps` · `ic-adas` · `ic-turn-left` · `ic-turn-right`): năm tệp đầu được hình xe
        // thay 1:1 nên xoá luôn tệp; bốn tên sau chỉ chết TÊN, còn TỆP vẫn sống (ic_adas là icon nhóm
        // ADAS, hai ic_turn_* là mũi tên rẽ của màn dẫn đường) — nên không xoá tệp, chỉ xoá dòng tra.
        "ic-car-top-door-lf" -> R.drawable.ic_car_top_door_lf
        "ic-car-top-door-rf" -> R.drawable.ic_car_top_door_rf
        "ic-car-top-door-lr" -> R.drawable.ic_car_top_door_lr
        "ic-car-top-door-rr" -> R.drawable.ic_car_top_door_rr
        "ic-car-top-door-all" -> R.drawable.ic_car_top_door_all
        "ic-car-top-window-lf" -> R.drawable.ic_car_top_window_lf
        "ic-car-top-window-rf" -> R.drawable.ic_car_top_window_rf
        "ic-car-top-window-lr" -> R.drawable.ic_car_top_window_lr
        "ic-car-top-window-rr" -> R.drawable.ic_car_top_window_rr
        "ic-car-top-window-all" -> R.drawable.ic_car_top_window_all
        "ic-car-top-window-rain" -> R.drawable.ic_car_top_window_rain
        "ic-car-top-tyre-fl" -> R.drawable.ic_car_top_tyre_fl
        "ic-car-top-tyre-fr" -> R.drawable.ic_car_top_tyre_fr
        "ic-car-top-tyre-rl" -> R.drawable.ic_car_top_tyre_rl
        "ic-car-top-tyre-rr" -> R.drawable.ic_car_top_tyre_rr
        "ic-car-top-tyre-temp-fl" -> R.drawable.ic_car_top_tyre_temp_fl
        "ic-car-top-tyre-temp-fr" -> R.drawable.ic_car_top_tyre_temp_fr
        "ic-car-top-tyre-temp-rl" -> R.drawable.ic_car_top_tyre_temp_rl
        "ic-car-top-tyre-temp-rr" -> R.drawable.ic_car_top_tyre_temp_rr
        "ic-car-top-seat-fl" -> R.drawable.ic_car_top_seat_fl
        "ic-car-top-belt-fl" -> R.drawable.ic_car_top_belt_fl
        "ic-car-top-belt-fr" -> R.drawable.ic_car_top_belt_fr
        "ic-car-top-occupant-fl" -> R.drawable.ic_car_top_occupant_fl
        "ic-car-top-occupant-fr" -> R.drawable.ic_car_top_occupant_fr
        "ic-car-top-occupant-rear" -> R.drawable.ic_car_top_occupant_rear
        "ic-car-top-mirror" -> R.drawable.ic_car_top_mirror
        "ic-car-top-trunk" -> R.drawable.ic_car_top_trunk
        "ic-car-top-trunk-pos" -> R.drawable.ic_car_top_trunk_pos
        "ic-car-top-hood" -> R.drawable.ic_car_top_hood
        "ic-car-top-sunroof" -> R.drawable.ic_car_top_sunroof
        "ic-car-top-sunroof-pos" -> R.drawable.ic_car_top_sunroof_pos
        "ic-car-top-sunshade" -> R.drawable.ic_car_top_sunshade
        "ic-car-top-lock" -> R.drawable.ic_car_top_lock
        "ic-car-top-ambient" -> R.drawable.ic_car_top_ambient
        "ic-car-top-ambient-color-front" -> R.drawable.ic_car_top_ambient_color_front
        "ic-car-top-ambient-color-rear" -> R.drawable.ic_car_top_ambient_color_rear
        "ic-car-top-ambient-bright-front" -> R.drawable.ic_car_top_ambient_bright_front
        "ic-car-top-ambient-bright-rear" -> R.drawable.ic_car_top_ambient_bright_rear
        "ic-car-top-ambient-music" -> R.drawable.ic_car_top_ambient_music
        "ic-car-top-bsd-l" -> R.drawable.ic_car_top_bsd_l
        "ic-car-top-bsd-r" -> R.drawable.ic_car_top_bsd_r
        "ic-car-top-lca-l" -> R.drawable.ic_car_top_lca_l
        "ic-car-top-lca-r" -> R.drawable.ic_car_top_lca_r
        "ic-car-top-rcta-l" -> R.drawable.ic_car_top_rcta_l
        "ic-car-top-rcta-r" -> R.drawable.ic_car_top_rcta_r
        "ic-car-top-rcta-all" -> R.drawable.ic_car_top_rcta_all
        "ic-car-top-dow-l" -> R.drawable.ic_car_top_dow_l
        "ic-car-top-dow-r" -> R.drawable.ic_car_top_dow_r
        "ic-car-top-dow-all" -> R.drawable.ic_car_top_dow_all
        "ic-car-top-lane" -> R.drawable.ic_car_top_lane
        "ic-car-top-park-all" -> R.drawable.ic_car_top_park_all
        "ic-car-front-lowbeam" -> R.drawable.ic_car_front_lowbeam
        "ic-car-front-highbeam" -> R.drawable.ic_car_front_highbeam
        "ic-car-front-fog" -> R.drawable.ic_car_front_fog
        "ic-car-front-drl" -> R.drawable.ic_car_front_drl
        "ic-car-front-turn-l" -> R.drawable.ic_car_front_turn_l
        "ic-car-front-turn-r" -> R.drawable.ic_car_front_turn_r
        "ic-car-front-sidelight" -> R.drawable.ic_car_front_sidelight
        "ic-car-front-headlight-mode" -> R.drawable.ic_car_front_headlight_mode
        "ic-car-front-fcw" -> R.drawable.ic_car_front_fcw
        "ic-car-rear-fog" -> R.drawable.ic_car_rear_fog
        "ic-car-rear-defrost" -> R.drawable.ic_car_rear_defrost
        // Bốn thành phần của MỘT toạ độ, nhưng là bốn đại lượng khác nhau ⇒ bốn hình (U7 · OQ1: mục
        // "nằm trên xe" mới vẽ hình xe, đại lượng đo thì giữ glyph trừu tượng — cùng nét, cùng ô).
        "ic-gps-lat" -> R.drawable.ic_gps_lat
        "ic-gps-lon" -> R.drawable.ic_gps_lon
        "ic-gps-alt" -> R.drawable.ic_gps_alt
        "ic-gps-heading" -> R.drawable.ic_gps_heading
        "ic-sign" -> R.drawable.ic_sign
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
