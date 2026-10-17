package com.byd.clusternav.launcher.voice

/**
 * ═══ WAKE-WEAK-UNIT (3.1.14) — "Hey Kachi" lùi khi MÁY bận, nạp mô hình khi máy RẢNH (thuần, cấm `android.*`) ═══════════
 *
 * [ĐO log Seal 17/10, `docs/diagnostics/perf-sl6-3113-2026-10-17.md` §3b] sau mỗi lần nổ máy (BYD giết Kachi lúc tắt máy):
 *  • nạp mô hình ở `:wake` mất ~45 s, `:wake` ăn 96–154 % CPU suốt lượt nạp, đúng lúc launcher, cụm và app dẫn đường cùng khởi động;
 *  • sau đó ~12 phút `:wake` nằm ở trần ngân sách 25 %.
 * [ĐO mã] [VoiceLoadGuard] chỉ thấy CPU của CHÍNH `:wake`, vì `/proc/loadavg` bị SELinux chặn. Tức là không lớp nào biết
 * máy đang bận vì việc của người khác.
 *
 * ## Tín hiệu tải hệ thống: độ trễ hàng đợi chạy của chính luồng nghe
 * `/proc/self/task/<tid>/schedstat` (tệp của chính tiến trình, không qua `/proc/loadavg`/`/proc/stat`) cho tổng ns luồng đã
 * CHỜ CPU khi đã sẵn sàng chạy, và số lượt được lên CPU. Mốc ngay trước một giấc ngủ hẹn giờ ngắn, hiệu ngay sau ⇒ **ms chờ của
 * đúng lượt thức ấy**. Luồng nghe chạy ở nice 19, nên con số này gần 0 khi còn lõi rảnh và vọt lên khi mọi lõi đều bận. Đó đúng là
 * câu hỏi cần trả lời: chạy thêm việc của mình lúc này có giành CPU của ai không. Giấc ngủ đo không chồng lên lượt suy diễn nào
 * (giải mã chạy đồng bộ trên chính luồng nghe), nên số không bị luồng ORT của chính mình làm bẩn.
 *
 * ## Ba quyết định, một lớp mỗi quyết định
 *  • [VoiceWakeBackoff]: bận/rảnh có trễ hai chiều. Bận NHANH (2 mẫu), rảnh CHẬM (5 mẫu liên tiếp).
 *  • [VoiceWakeDuty]: trần CPU dài hạn theo cấu hình máy ĐO được (lõi, RAM, giá một lượt giải mã). Không theo tên đời xe.
 *  • [VoiceLoadDefer]: lượt nạp nền chỉ bắt đầu khi máy đã rảnh liên tục [VoiceLoadDefer.QUIET_MS], hoặc khi chờ đã quá
 *    [VoiceLoadDefer.MAX_WAIT_MS] (máy bận mãi thì vẫn nạp, ở ưu tiên thấp nhất; Hey Kachi không được điếc vĩnh viễn).
 *
 * ## Vì sao bận chỉ HẠ ngân sách chứ không tắt hẳn
 * [ĐO xe 2026-09-19, F5] một lá chắn siết quá đã treo bộ nghe vĩnh viễn ở tải xe thật ⇒ 0/20 câu gọi. Nên khi bận: trần dài hạn
 * xuống [VoiceWakeDuty.BUSY_DUTY] và xô bị kẹp ở [VoiceWakeDuty.BUSY_BURST_MS] (khoảng một lượt giải mã), không về 0. Câu gọi lúc
 * máy bận vẫn được giải mã một lượt, chỉ không được chạy liền một chuỗi 4 s CPU.
 */
class VoiceWakeBackoff(
    private val busyAboveMs: Double = BUSY_ABOVE_MS,
    private val calmBelowMs: Double = CALM_BELOW_MS,
    private val busyReads: Int = BUSY_READS,
    private val calmReads: Int = CALM_READS,
) {
    init {
        require(calmBelowMs < busyAboveMs) { "calmBelowMs phải < busyAboveMs (khe trễ)" }
        require(busyReads >= 1 && calmReads >= 1)
    }

    var busy = false
        private set
    private var hot = 0
    private var cool = 0

    /** Số lần chuyển rảnh → bận từ lúc dựng (dòng nhật ký `ngan_sach`). */
    var busySpells = 0
        private set

    /** Mẫu gần nhất (ms chờ mỗi lượt thức), `null` = chưa có mẫu sạch nào. Chỉ để ghi nhật ký. */
    var lastDelayMs: Double? = null
        private set

    /**
     * Một mẫu độ trễ. `null` = khoảng vừa rồi không có số sạch (có lượt suy diễn, hoặc không đo được) ⇒ GIỮ trạng thái:
     * không có dữ liệu thì không được tự kết luận "đã rảnh".
     */
    fun sample(delayMs: Double?): Boolean {
        if (delayMs == null || !delayMs.isFinite() || delayMs < 0.0) return busy
        lastDelayMs = delayMs
        if (busy) {
            if (delayMs < calmBelowMs) { if (++cool >= calmReads) { busy = false; cool = 0 } } else cool = 0
        } else {
            if (delayMs > busyAboveMs) { if (++hot >= busyReads) { busy = true; hot = 0; busySpells++ } } else hot = 0
        }
        return busy
    }

    fun reset() { busy = false; hot = 0; cool = 0; lastDelayMs = null }

    companion object {
        /**
         * Ngưỡng ms chờ CPU của một lượt thức do HẸN GIỜ trên luồng nice 19. [ĐO máy ảo `clusternav10` API 29, 2 lõi, 17/10, doc §3b;
         * tiến trình nice 19 ngủ 100 ms × 20 lượt không fork, TB mỗi lượt, 5 lần mỗi mức]: rảnh 0,02–0,78 ms · 1 vòng bận (còn 1 lõi
         * rảnh) 0,03–0,34 ms · 2 vòng (đủ 2/2 lõi) 2,7–3,7 ms · 4 vòng 3,8–6,3 ms. Bận = 2 mẫu liên tiếp > 1,5 ms; rảnh = 5 mẫu liên
         * tiếp < 0,5 ms. [CHƯA BIẾT] số của xe (8 lõi big.LITTLE); hai hằng này là toàn bộ chỗ phải đổi.
         */
        const val BUSY_ABOVE_MS = 1.5
        const val CALM_BELOW_MS = 0.5
        const val BUSY_READS = 2
        const val CALM_READS = 5
    }
}

/**
 * Hiệu hai mốc `schedstat` (`<ns chạy> <ns chờ> <số lượt lên CPU>`) → ms chờ mỗi lượt. Thuần; `:app` bơm số đọc từ tệp.
 * Lần đầu chỉ ghi mốc ⇒ `null`. Bộ đếm lùi (luồng khác, tệp lạ) hoặc không có lượt nào ⇒ ghi mốc mới, trả `null`.
 */
class VoiceSchedDelay {
    private var lastWaitNs = -1L
    private var lastSlices = -1L

    /** @param dirty khoảng vừa rồi có lượt suy diễn của chính mình ⇒ ghi mốc mới, không trả số (xem KDoc tệp). */
    fun sample(waitNs: Long, slices: Long, dirty: Boolean = false): Double? {
        val w0 = lastWaitNs; val s0 = lastSlices
        lastWaitNs = waitNs; lastSlices = slices
        if (w0 < 0 || dirty) return null
        val dw = waitNs - w0; val ds = slices - s0
        if (dw < 0 || ds <= 0) return null
        return dw / 1e6 / ds
    }

    companion object {
        /** `"<run_ns> <wait_ns> <slices>"` → (wait_ns, slices); `null` khi không đúng dạng. */
        fun parse(text: String): Pair<Long, Long>? {
            val p = text.trim().split(' ', '\t').filter { it.isNotEmpty() }
            if (p.size < 3) return null
            val w = p[1].toLongOrNull() ?: return null
            val s = p[2].toLongOrNull() ?: return null
            return if (w >= 0 && s >= 0) w to s else null
        }
    }
}

/**
 * Đường lùi khi `schedstat` không đọc được (kernel không bật `CONFIG_SCHED_INFO` [CHƯA BIẾT] trên xe): ms NGỦ QUÁ của một
 * `sleep` có hạn, trừ đi mức ngủ quá nhỏ nhất đã thấy. Phần trừ là độ trễ hẹn giờ (timer slack) của luồng ưu tiên thấp
 * ([ĐO AOSP r47 `sched_policy.cpp:137`] SP_BACKGROUND kèm "TimerSlackHigh"): cố định, không phải tải. Thuần.
 */
class VoiceOversleep {
    private var floorMs = Double.MAX_VALUE

    fun sample(requestedMs: Long, actualMs: Long): Double? {
        val over = (actualMs - requestedMs).toDouble()
        if (requestedMs <= 0 || over < 0) return null
        if (over < floorMs) floorMs = over
        return over - floorMs
    }
}

/** Trần CPU dài hạn của bộ nghe theo cấu hình máy đo được, và giới hạn lúc máy bận. */
object VoiceWakeDuty {
    /**
     * **HẰNG SỐ DUY NHẤT cho máy yếu**: phần một lõi mà suy diễn wake được dùng dài hạn khi máy yếu (thay [VoiceWakeCpuBudget.DUTY] 0,25).
     * Máy yếu = ≤ [LOW_END_MAX_CORES] lõi, hoặc RAM ≤ [LOW_END_MAX_RAM_MB], hoặc `isLowRamDevice`, hoặc giá một lượt giải mã đo được
     * > [HEAVY_DECODE_MS]. Xô (4 000 ms CPU) KHÔNG đổi ⇒ câu gọi sau một quãng yên vẫn được chạy liền như cũ; chỉ nguồn ồn kéo dài
     * (nhạc có lời) bị giãn: ~1 lượt / 13,5 s thay vì / 5,4 s ở giá 1 350 ms [SUY từ chặn trên của [VoiceWakeCpuBudget]].
     */
    const val LOW_END_DUTY = 0.10
    const val LOW_END_MAX_CORES = 4
    const val LOW_END_MAX_RAM_MB = 3_072L

    /** [ĐO xe 17/10] giá một lượt trước 3.1.12 2,2–4,4 s; [SUY] sau `force_spinning_stop` 0,8–1,6 s. Trên 2 s ⇒ máy này giải mã nặng. */
    const val HEAVY_DECODE_MS = 2_000L

    /** Máy bận ([VoiceWakeBackoff.busy]): trần dài hạn 5 % một lõi, xô kẹp ở khoảng một lượt giải mã của xe (1 350 ms [ĐO 15/10]). */
    const val BUSY_DUTY = 0.05
    const val BUSY_BURST_MS = 1_500.0

    /** Lý do chọn trần, để ghi nhật ký (`null` = máy thường). */
    fun lowEndReason(cores: Int, ramMb: Long, lowRam: Boolean, meanDecodeMs: Long?): String? = when {
        cores in 1..LOW_END_MAX_CORES -> "cores=$cores"
        lowRam -> "lowRamDevice"
        ramMb in 1..LOW_END_MAX_RAM_MB -> "ramMB=$ramMb"
        meanDecodeMs != null && meanDecodeMs > HEAVY_DECODE_MS -> "decodeMs=$meanDecodeMs"
        else -> null
    }

    /** Trần dài hạn cho máy này. */
    fun forDevice(cores: Int, ramMb: Long, lowRam: Boolean, meanDecodeMs: Long? = null): Double =
        if (lowEndReason(cores, ramMb, lowRam, meanDecodeMs) != null) LOW_END_DUTY else VoiceWakeCpuBudget.DUTY

    /** (trần dài hạn, trần xô) cho ngân sách theo trạng thái bận/rảnh. */
    fun limits(deviceDuty: Double, busy: Boolean, fullBurstMs: Double): Pair<Double, Double> =
        if (busy) minOf(deviceDuty, BUSY_DUTY) to minOf(fullBurstMs, BUSY_BURST_MS) else deviceDuty to fullBurstMs
}

/**
 * Cổng cho lượt nạp mô hình NỀN (nạp sẵn, lượt nạp đầu của bộ nghe). Lượt nạp do người dùng bấm thì không đi qua đây.
 * Mỗi nhịp, chỗ gọi đưa trạng thái bận/rảnh ([VoiceWakeBackoff.busy], `null` = chưa có số). Trả [Go] khi đã rảnh liên tục
 * [quietMs], hoặc đã chờ quá [maxWaitMs] kể từ nhịp đầu.
 */
class VoiceLoadDefer(private val quietMs: Long = QUIET_MS, private val maxWaitMs: Long = MAX_WAIT_MS) {
    /** [READY] = chỗ gọi thấy mô hình ĐÃ có (người bấm mic nạp trước, lượt chờ khác nạp xong) ⇒ thôi chờ, không còn gì để nạp. */
    enum class Go { WAIT, CALM, TIMEOUT, READY }

    private var startedAt = UNSET
    private var calmSince = UNSET

    fun step(nowMs: Long, busy: Boolean?): Go {
        if (startedAt == UNSET) startedAt = nowMs
        if (busy == false) { if (calmSince == UNSET) calmSince = nowMs } else calmSince = UNSET
        return when {
            calmSince != UNSET && nowMs - calmSince >= quietMs -> Go.CALM
            nowMs - startedAt >= maxWaitMs -> Go.TIMEOUT
            else -> Go.WAIT
        }
    }

    /** ms đã chờ từ nhịp đầu (nhật ký). */
    fun waitedMs(nowMs: Long): Long = if (startedAt == UNSET) 0L else nowMs - startedAt

    companion object {
        private const val UNSET = Long.MIN_VALUE

        /** Rảnh liên tục bao lâu thì được nạp. Lượt khởi động launcher + mở app trong ô trên xe xong trong ~10 s [ĐO startup-timeline 07/10]. */
        const val QUIET_MS = 10_000L

        /** Trần chờ: quá ngần này thì nạp dù máy còn bận (ở ưu tiên thấp nhất). */
        const val MAX_WAIT_MS = 120_000L

        /** Nhịp hỏi tín hiệu trong lúc chờ. */
        const val STEP_MS = 500L
    }
}
