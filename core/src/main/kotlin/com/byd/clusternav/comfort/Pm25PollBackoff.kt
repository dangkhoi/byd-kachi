package com.byd.clusternav.comfort

/**
 * Nhịp poll PM2.5 THUẦN theo số lần đọc INVALID liên tiếp (F7 · B1 `kachi-closeout-hardening` R3).
 *
 * [ĐO máy ảo 2.65] `Pm25Filter poll: mức=0 (—)` mỗi 45 s dù không có HAL — một lượt reflection + binder cho một
 * datum không tồn tại, mãi mãi. Cùng triết lý [com.byd.clusternav.launcher.HalAbsentCache]: **giãn, không khoá
 * vĩnh viễn** (CLAUDE.md §3 — không gate đường phục hồi bằng dữ liệu chỉ chính nó mới làm mới được): đọc được một
 * giá trị ⇒ về [baseMs] ngay; bật lại công tắc ⇒ vòng mới ⇒ đếm lại từ 0.
 *
 * Bảng (base 45 s, ngưỡng 3, trần 10 phút): invalid 0..2 → 45 s · 3 → 90 s · 4 → 180 s · 5 → 360 s · ≥6 → 600 s.
 */
object Pm25PollBackoff {
    /** Số INVALID liên tiếp trước khi bắt đầu giãn (một lượt HAL chớp không đủ để kết luận). */
    const val INVALID_BEFORE_BACKOFF = 3
    /** Trần 10 phút: off-car / trim không có cảm biến rơi về 6 lượt/giờ thay vì 80 lượt/giờ. */
    const val MAX_INTERVAL_MS = 10 * 60_000L

    fun nextIntervalMs(
        consecutiveInvalid: Int,
        baseMs: Long,
        threshold: Int = INVALID_BEFORE_BACKOFF,
        maxMs: Long = MAX_INTERVAL_MS,
    ): Long {
        if (consecutiveInvalid < threshold) return baseMs
        val doublings = (consecutiveInvalid - threshold + 1).coerceAtMost(30)
        return (baseMs shl doublings).coerceAtMost(maxMs)
    }
}
