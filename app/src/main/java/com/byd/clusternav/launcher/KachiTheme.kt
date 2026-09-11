package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import com.byd.clusternav.R

/** Bảng màu + helper drawable khớp prototype kachi-workspace.html (dark automotive). */
object KachiTheme {
    const val BG = "#0a0d13"
    const val INK = "#eaf0f8"
    const val MUT = "#93a0b4"
    const val MUT2 = "#8b95a7"
    const val LINE = "#17ffffff"     // ~9% trắng
    const val CARD = "#141922"   // thẻ nền
    const val CARD2 = "#1a1e28"
    const val ACCENT = "#4c7dff"
    const val ACCENT2 = "#7b5cff"
    const val CYAN = "#29d3ee"
    const val GREEN = "#34d399"
    const val AMBER = "#fbbf24"
    const val RED = "#fb7185"

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
        fill: String = "#14ffffff",
        stroke: String = LINE,
    ): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = KachiSpace.dpf(ctx, radius)
            setColor(c(fill))
            setStroke(dpi(ctx, KachiSpace.HAIRLINE), c(stroke))
        }

    /** Nền gradient accent (nút chính / tile bật). */
    fun gradient(ctx: Context, radius: Int, from: String = ACCENT, to: String = ACCENT2): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(from), c(to))).apply {
            cornerRadius = KachiSpace.dpf(ctx, radius)
        }

    /**
     * Tile DOCK BẬT — gradient accent BÁN TRONG SUỐT + viền accent, khớp prototype `.dtile.on`
     * (accent 36% → accent2 32%, viền accent 55%). KHÁC gradient đặc [gradient] (dùng cho pill/preset chọn).
     */
    fun gradientSoft(ctx: Context, radius: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c("#5C4C7DFF"), c("#527B5CFF"))).apply {
            cornerRadius = KachiSpace.dpf(ctx, radius); setStroke(dpi(ctx, KachiSpace.HAIRLINE), c("#8C4C7DFF"))
        }

    /** Fade tối từ trên xuống cho thanh tiêu đề ô (slot-head) — khớp prototype `linear-gradient(180deg, rgba(0,0,0,.55), transparent)`. */
    fun topFade(ctx: Context, radius: Int = KachiSpace.RADIUS_L): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c("#8C000000"), c("#00000000"))).apply {
            val r = KachiSpace.dpf(ctx, radius)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }

    /** Viên thuốc (pill) bo tròn hết cỡ. */
    fun pill(ctx: Context, fill: String = "#14ffffff", stroke: String = LINE): GradientDrawable =
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
