package com.byd.clusternav.launcher.automation

/**
 * ═══ KACHI-301 R12 — PHÉP TÍNH của bộ chọn giờ trong hộp *Lịch dẫn đường*, thuần (`:core`) ═════════════════════
 *
 * Bộ chọn giờ (`TimeWheel`, `:app`) là hai cột ▲▼ — giờ và phút — nằm TRONG hộp (không hộp con: hộp chồng hộp làm
 * mất tiêu điểm trên xe, [ĐO] 2026-09-12). Mọi phép đổi giá trị nằm ở đây để kiểm off-car:
 *  - mỗi cột **quay vòng độc lập** (23 ▲ ⇒ 00; phút 55 ▲ ⇒ 00, giờ KHÔNG nhảy theo — đúng cảm giác cột cuộn);
 *  - phút đi theo bước [MINUTE_STEP]; giá trị lẻ (07:32 từ luật cũ / giọng nói) **giữ nguyên** tới khi chạm,
 *    chạm ▲ ⇒ lên mốc bước kế (07:35), ▼ ⇒ xuống mốc bước dưới (07:30) — không nhảy hai bước;
 *  - mốc nhanh [PRESETS] (06:00 · 07:00 · 08:00 · 17:00 · 18:00).
 *
 * Trần giờ suy từ [ScheduledNavRules.MAX_MIN] (một nguồn) — không viết dải giờ/phút tay.
 */
object TimeWheelMath {

    /** Bước của cột phút (R12: "phút bước 5"). */
    const val MINUTE_STEP = 5

    private const val MIN_PER_HOUR = 60

    /** Số giờ trong ngày, suy từ [ScheduledNavRules.MAX_MIN] (23:59 ⇒ 24). */
    private val HOURS = (ScheduledNavRules.MAX_MIN + 1) / MIN_PER_HOUR

    /** Mốc giờ nhanh (phút-trong-ngày), theo thứ tự hiện. */
    val PRESETS: List<Int> = listOf(6, 7, 8, 17, 18).map { it * MIN_PER_HOUR }

    fun hourOf(minOfDay: Int): Int = clamp(minOfDay) / MIN_PER_HOUR
    fun minuteOf(minOfDay: Int): Int = clamp(minOfDay) % MIN_PER_HOUR

    /** Kẹp vào `0..MAX_MIN` — giá trị rác (−1 từ luật hỏng) không làm cột hiện "-1". */
    fun clamp(minOfDay: Int): Int = minOfDay.coerceIn(0, ScheduledNavRules.MAX_MIN)

    /** Cột GIỜ một nấc theo [dir] (> 0 lên, < 0 xuống), quay vòng; phút giữ nguyên. */
    fun stepHour(minOfDay: Int, dir: Int): Int {
        val h = Math.floorMod(hourOf(minOfDay) + Integer.signum(dir), HOURS)
        return h * MIN_PER_HOUR + minuteOf(minOfDay)
    }

    /** Cột PHÚT một nấc [MINUTE_STEP] theo [dir], bắt về lưới bước, quay vòng TRONG giờ; giờ giữ nguyên. */
    fun stepMinute(minOfDay: Int, dir: Int): Int {
        val m = minuteOf(minOfDay)
        val next = when {
            dir > 0 -> (m / MINUTE_STEP + 1) * MINUTE_STEP
            dir < 0 -> if (m % MINUTE_STEP != 0) m / MINUTE_STEP * MINUTE_STEP else m - MINUTE_STEP
            else -> m
        }
        return hourOf(minOfDay) * MIN_PER_HOUR + Math.floorMod(next, MIN_PER_HOUR)
    }
}

/**
 * Chip thứ nhanh của hộp lịch: *Ngày thường · Cuối tuần · Mỗi ngày*. Chip sáng khi tập thứ **đúng bằng** tập của nó
 * (không phải "chứa") — T2–T7 thì không chip nào sáng, đúng sự thật.
 */
enum class DayPreset(val days: Set<Int>) {
    WEEKDAYS(ScheduledNavRules.WEEKDAYS),
    WEEKEND(setOf(ScheduledNavRules.SAT, ScheduledNavRules.SUN)),
    EVERYDAY(ScheduledNavRules.ALL_DAYS),
    ;

    companion object {
        fun matching(days: Set<Int>): DayPreset? = entries.firstOrNull { it.days == days }
    }
}
