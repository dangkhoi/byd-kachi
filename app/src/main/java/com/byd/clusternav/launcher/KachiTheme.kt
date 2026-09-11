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

    /** Thẻ kính bo góc + viền mảnh. */
    fun card(ctx: Context, radius: Float = 20f, fill: String = "#14ffffff", stroke: String = LINE): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(ctx, radius)
            setColor(c(fill))
            setStroke(dpi(ctx, 1), c(stroke))
        }

    /** Nền gradient accent (nút chính / tile bật). */
    fun gradient(ctx: Context, radius: Float, from: String = ACCENT, to: String = ACCENT2): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c(from), c(to))).apply {
            cornerRadius = dp(ctx, radius)
        }

    /**
     * Tile DOCK BẬT — gradient accent BÁN TRONG SUỐT + viền accent, khớp prototype `.dtile.on`
     * (accent 36% → accent2 32%, viền accent 55%). KHÁC gradient đặc [gradient] (dùng cho pill/preset chọn).
     */
    fun gradientSoft(ctx: Context, radius: Float): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c("#5C4C7DFF"), c("#527B5CFF"))).apply {
            cornerRadius = dp(ctx, radius); setStroke(dpi(ctx, 1), c("#8C4C7DFF"))
        }

    /** Fade tối từ trên xuống cho thanh tiêu đề ô (slot-head) — khớp prototype `linear-gradient(180deg, rgba(0,0,0,.55), transparent)`. */
    fun topFade(ctx: Context, radius: Float = 16f): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c("#8C000000"), c("#00000000"))).apply {
            cornerRadii = floatArrayOf(dp(ctx, radius), dp(ctx, radius), dp(ctx, radius), dp(ctx, radius), 0f, 0f, 0f, 0f)
        }

    /** Viên thuốc (pill) bo tròn hết cỡ. */
    fun pill(ctx: Context, fill: String = "#14ffffff", stroke: String = LINE): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(ctx, 999f)
            setColor(c(fill))
            setStroke(dpi(ctx, 1), c(stroke))
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
        // Tên icon dùng lại tệp đã có (trước đây chưa được map nên tra ra 0 = ô trống icon)
        "ic-clock" -> R.drawable.ic_clock_g
        "ic-turn-left" -> R.drawable.ic_turn_left
        "ic-turn-right" -> R.drawable.ic_turn_right
        else -> 0
    }
}
