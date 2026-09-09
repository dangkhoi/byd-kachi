package com.byd.clusternav.launcher

/**
 * Chế độ giao diện sáng/tối. AUTO = theo giờ (tối 18h–6h). Pure JVM → test off-car.
 * Áp dụng bằng cách đổi bảng màu (KachiTheme) rồi recreate Activity.
 */
enum class ThemeMode {
    DAY, NIGHT, AUTO;

    /** Có nên dùng bảng màu tối cho [hour] (0..23) không. */
    fun isNight(hour: Int): Boolean = when (this) {
        NIGHT -> true
        DAY -> false
        AUTO -> hour < 6 || hour >= 18
    }

    fun next(): ThemeMode = values()[(ordinal + 1) % values().size]

    fun label(): String = when (this) { DAY -> "Sáng"; NIGHT -> "Tối"; AUTO -> "Tự động" }
}
