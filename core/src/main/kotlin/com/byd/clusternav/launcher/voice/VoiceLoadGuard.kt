package com.byd.clusternav.launcher.voice

/**
 * ═══ LÁ CHẮN CPU CHÍNH của "Hey Kachi" — dừng nghe khi hệ NÓNG, chạy lại khi NGUỘI (thuần, cấm `android.*`) ═══
 *
 * ## Vì sao lớp này là trọng tâm an toàn (owner 2026-09-18)
 * Owner chốt: wake-word **nghe cả khi launcher không hiện** (nền, qua foreground-service). Bỏ cổng foreground
 * nghĩa là mất lá chắn CPU tự nhiên "chỉ nghe khi đang mở launcher" ⇒ **lớp này gánh vai chính**: bộ nghe nền
 * hỏi nó mỗi nhịp, [allow]=false thì **nhả mic + thôi inference** ngay.
 *
 * [ĐO xe 18-09] `load average 13.48 / 14.52 / 16.36` = CPU bão hoà toàn hệ, bind trợ năng kẹt vì AMS đói CPU.
 * Nếu bộ nghe wake cứ chạy khi hệ đã 14 thì nó **đổ thêm dầu vào lửa** — đúng thứ owner lo. Guard này bảo đảm
 * wake **tự lùi** khi hệ nóng, và chỉ trở lại khi đã nguội **đủ lâu** (chống rung bật/tắt liên tục).
 *
 * ## Trễ (hysteresis) hai chiều
 *  • **Dừng NHANH**: `load1 > suspendAbove` một nhịp là dừng ngay (thà tắt nhầm còn hơn góp phần treo).
 *  • **Chạy lại CHẬM**: phải `load1 < resumeBelow` **liên tiếp [resumeStableReads] nhịp** mới chạy lại — một
 *    nhịp nguội đơn lẻ không đủ (load dao động mạnh); tránh vòng dừng→chạy→dừng đốt CPU đúng lúc không nên.
 *
 * ## Ngưỡng = [CHƯA BIẾT], ĐO trên xe (OQ3, owner chốt "chuẩn bị để lên xe đo")
 * Load phụ thuộc **số lõi** và tải nền của ROM này (idle emulator 0.35, xe lúc lỗi 14). Mặc định dưới đây là
 * **thận trọng** (thà lùi sớm); `:app` bơm `load1` đọc từ `/proc/loadavg` và có thể chia số lõi nếu cần
 * chuẩn hoá. Núm ẩn chỉnh ngưỡng sẽ thêm ở T5. Thuần ⇒ test bằng CHUỖI load giả (không cần thiết bị).
 */
class VoiceLoadGuard(
    val suspendAbove: Double = DEFAULT_SUSPEND_ABOVE,
    val resumeBelow: Double = DEFAULT_RESUME_BELOW,
    val resumeStableReads: Int = DEFAULT_RESUME_STABLE,
) {
    init {
        require(resumeBelow < suspendAbove) { "resumeBelow ($resumeBelow) phải < suspendAbove ($suspendAbove) để có khe trễ" }
        require(resumeStableReads >= 1) { "resumeStableReads phải ≥ 1" }
    }

    private var suspended = false
    private var goodStreak = 0

    /** Có được phép nghe ở nhịp này không. Gọi mỗi lần đọc load. `false` ⇒ bộ nghe phải nhả mic + thôi KWS. */
    fun allow(load1: Double): Boolean {
        if (suspended) {
            if (load1 < resumeBelow) {
                if (++goodStreak >= resumeStableReads) { suspended = false; goodStreak = 0 }
            } else {
                goodStreak = 0 // một nhịp nóng xoá chuỗi nguội — phải nguội LIÊN TIẾP mới trở lại
            }
        } else if (load1 > suspendAbove) {
            suspended = true; goodStreak = 0
        }
        return !suspended
    }

    fun isSuspended(): Boolean = suspended

    /** Về trạng thái đầu (chạy) — chỉ cho bài kiểm / khi bật lại công tắc. */
    fun reset() { suspended = false; goodStreak = 0 }

    companion object {
        /** [CHƯA BIẾT] — đo trên xe (OQ3). Mặc định thận trọng cho head unit ít lõi (lỗi xe từng ở load 14). */
        const val DEFAULT_SUSPEND_ABOVE = 12.0
        const val DEFAULT_RESUME_BELOW = 9.0
        const val DEFAULT_RESUME_STABLE = 3
    }
}
