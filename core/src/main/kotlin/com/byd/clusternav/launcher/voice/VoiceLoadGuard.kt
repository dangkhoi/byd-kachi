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
 *
 * ## HAI nguồn tải, cùng một guard (2026-09-25 · wake)
 * [ĐO máy ảo 2.65] `avc: denied { read } name="loadavg"` **1 dòng/giây**: SELinux `untrusted_app` API 29 chặn
 * `/proc/loadavg` ⇒ `:app` bơm `0.0` mãi ⇒ guard **mù** (không bao giờ dừng) + 1 ngoại lệ/giây. [SUY] xe cùng
 * API/chính sách ⇒ cùng kết quả. Nguồn thứ hai không cần `/proc`: **CPU của chính tiến trình** — số lõi mà
 * `:wake` đang dùng, tính từ `Process.getElapsedCpuTime()` ([ĐO source AOSP r47 `android_util_Process.cpp:1075`]
 * = `clock_gettime(CLOCK_PROCESS_CPUTIME_ID)`, syscall, không qua tệp). Đó không phải "hệ nóng" mà là "**mình**
 * đang đổ bao nhiêu dầu" — chính con số của sự cố [ĐO xe 2026-09-23] `:wake ~130 % CPU` — nên là lá chắn đúng cho
 * mục tiêu của lớp này (không góp phần treo). Thang khác nhau ⇒ ngưỡng khác nhau: [forSelfCpu] đặt theo **phần
 * máy** (`nproc`), còn [VoiceSelfCpuMeter] đổi (ms CPU, ms tường) → "số lõi" để [allow] dùng như `load1`.
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

        /**
         * Ngưỡng cho nguồn **tự-CPU**, tính theo PHẦN MÁY: dừng khi tiến trình ăn > [SELF_SUSPEND_FRACTION] × nproc
         * lõi trong một nhịp; chạy lại khi < [SELF_RESUME_FRACTION] × nproc liên tiếp [DEFAULT_RESUME_STABLE] nhịp.
         *
         * Vì sao nới (½ máy) chứ không siết: [ĐO xe 2026-09-19, F5] guard siết đã treo bộ nghe **vĩnh viễn** ở tải
         * xe thật ⇒ 0/20 âm tính giả. Một bộ nghe nền chiếm nửa máy là bệnh rõ ràng (sự cố 130 % ≈ 1,3 lõi là
         * bệnh nhẹ hơn, đã chữa bằng RMS-gate); còn một lượt giải mã hợp lệ (≤ 4 luồng, ≤ 40 % thời gian theo tầng
         * 4 của controller) thì không được làm bộ nghe điếc đúng lúc người lái đang gọi. [CHƯA BIẾT] số tối ưu —
         * chốt cùng OQ3; hai hằng này là toàn bộ chỗ phải đổi.
         */
        const val SELF_SUSPEND_FRACTION = 0.5
        const val SELF_RESUME_FRACTION = 0.25

        /** Guard cho nguồn tự-CPU (đơn vị "lõi tiến trình đang dùng", xem [VoiceSelfCpuMeter]). `nproc` ≥ 1. */
        fun forSelfCpu(nproc: Int, resumeStableReads: Int = DEFAULT_RESUME_STABLE): VoiceLoadGuard {
            val n = nproc.coerceAtLeast(1).toDouble()
            return VoiceLoadGuard(
                suspendAbove = SELF_SUSPEND_FRACTION * n,
                resumeBelow = SELF_RESUME_FRACTION * n,
                resumeStableReads = resumeStableReads,
            )
        }

        /** `load1` từ nội dung `/proc/loadavg` ("0.35 0.40 0.38 1/512 1234"); `null` nếu không đọc ra số. */
        fun parseLoadavg(text: String): Double? =
            text.trim().substringBefore(' ').toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }
    }
}

/**
 * Đổi (ms CPU của tiến trình, ms đồng hồ tường) → **số lõi tiến trình đang dùng** giữa hai lần gọi — thang mà
 * [VoiceLoadGuard.forSelfCpu] đặt ngưỡng. Thuần; `:app` bơm `Process.getElapsedCpuTime()` + `elapsedRealtime()`.
 *
 * Lần gọi đầu chỉ ghi mốc (chưa có khoảng) ⇒ `0.0`. Khoảng tường ≤ 0 hoặc CPU lùi (đồng hồ bị đặt lại) ⇒ ghi
 * mốc mới, trả **giá trị trước** — không bao giờ trả số âm hay NaN cho guard.
 */
class VoiceSelfCpuMeter {
    private var lastCpuMs = -1L
    private var lastWallMs = 0L
    private var last = 0.0

    fun sample(cpuMs: Long, nowMs: Long): Double {
        val prevCpu = lastCpuMs
        val prevWall = lastWallMs
        lastCpuMs = cpuMs; lastWallMs = nowMs
        if (prevCpu < 0) return 0.0
        val dWall = nowMs - prevWall
        val dCpu = cpuMs - prevCpu
        if (dWall <= 0 || dCpu < 0) return last
        last = dCpu.toDouble() / dWall.toDouble()
        return last
    }
}
