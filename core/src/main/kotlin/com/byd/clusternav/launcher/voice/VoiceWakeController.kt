package com.byd.clusternav.launcher.voice

/**
 * ═══ BỘ NÃO của bộ nghe "Hey Kachi" — quyết định MỖI NHỊP, thuần (cấm `android.*`) ════════════════════════════
 *
 * Đây là phần "làm rất khéo" mà owner lo: nó gom [VoiceLoadGuard] + [VoiceWakeGate] + cooldown + cầu chì
 * false-accept thành **một máy quyết định kiểm được off-car**. Lớp Android ở `:app` chỉ là vòng lặp mỏng: đọc
 * micro → tính RMS + đọc `/proc/loadavg` → hỏi [onFrame]; nếu bảo `RUN_KWS` thì chạy KWS trên khung → báo kết
 * quả về [onKwsResult]. Mọi lý lẽ chống-hang-CPU nằm ở đây, nơi bơm được số giả để chứng minh.
 *
 * ## Bốn tầng cắt CPU (đắt dần)
 *  1. **Load-guard** — hệ nóng ⇒ `SUSPENDED`: nhả mic, thôi cả RMS lẫn KWS (lá chắn chính vì nghe nền).
 *  2. **Cooldown** — vừa nổ wake xong ⇒ `IDLE` một lúc (phiên lệnh đang chạy; tránh tự nghe lại chime/tiếng mình).
 *  3. **Cổng năng lượng** — im ⇒ `IDLE` (chỉ toán RMS); có giọng ⇒ mới xét tầng 4.
 *  4. **Trần thời lượng suy diễn** — tối đa N khung KWS trong một cửa sổ ⇒ tỉ lệ CPU của phần nơ-ron có **chặn
 *     trên chứng minh được** kể cả khi cabin ồn liên tục trên ngưỡng (nhạc). Xem [chargeKws].
 *
 * ## Cầu chì false-accept (OQ5, owner: tự tắt + báo)
 * Wake nổ quá [maxWakesPerWindow] lần trong [wakeWindowMs] ⇒ `FUSED`: coi như đang nghe nhầm loạn xạ, **tự
 * đình chỉ** (mọi `onFrame` sau trả `SUSPENDED`); `:app` tắt công tắc + báo user. Cùng triết lý cầu chì của
 * [VoiceSingleFlight] — lớp cuối không cần biết nguyên nhân.
 */
class VoiceWakeController(
    private val gate: VoiceWakeGate = VoiceWakeGate(),
    private val loadGuard: VoiceLoadGuard = VoiceLoadGuard(),
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val maxWakesPerWindow: Int = DEFAULT_MAX_WAKES,
    private val wakeWindowMs: Long = DEFAULT_WAKE_WINDOW_MS,
    private val maxKwsFramesPerWindow: Int = DEFAULT_MAX_KWS_FRAMES,
    private val kwsWindowMs: Long = DEFAULT_KWS_WINDOW_MS,
) {
    /** Việc cần làm với khung âm thanh hiện tại. */
    enum class Frame { SUSPENDED, IDLE, RUN_KWS }

    /** Kết quả sau khi KWS chấm một khung. */
    enum class Wake { NONE, FIRE, FUSED }

    private var cooldownUntil = 0L
    private val wakeTimes = ArrayDeque<Long>()

    /** Mốc các khung ĐÃ chạy KWS trong cửa sổ [kwsWindowMs] — xem [chargeKws]. */
    private val kwsFrames = ArrayDeque<Long>()
    private var fused = false

    /** Nhịp âm thanh: quyết định có chạy KWS không. `:app` gọi với RMS khung + load hiện tại. */
    fun onFrame(rms: Double, load1: Double, nowMs: Long): Frame {
        if (fused) return Frame.SUSPENDED
        if (!loadGuard.allow(load1)) return Frame.SUSPENDED      // tầng 1: hệ nóng
        if (nowMs < cooldownUntil) return Frame.IDLE             // tầng 2: vừa nổ wake
        if (!gate.voiced(rms)) return Frame.IDLE                 // tầng 3: im ⇒ chỉ toán RMS
        return if (chargeKws(nowMs)) Frame.RUN_KWS else Frame.IDLE // tầng 4: trần thời lượng suy diễn
    }

    /**
     * ═══ TẦNG 4 — TRẦN CỨNG cho THỜI LƯỢNG suy diễn (thêm ở lượt soát 2026-09-18) ══════════════════════════
     *
     * ## Lỗ mà ba tầng trên KHÔNG bịt
     * Tầng 3 ([VoiceWakeGate]) chỉ hỏi *"khung này to hơn nền không"*, và nền của nó **cố ý chỉ học lúc im**
     * (nếu học cả lúc có tiếng thì một câu dài tự kéo nền lên rồi tự bịt mình — bài canh `giong lien tuc khong
     * tu keo nen len bit minh` khoá đúng tính chất ấy). Hệ quả [SUY, đọc mã]: một nguồn ồn **liên tục nằm trên
     * ngưỡng** — nhạc trong cabin, đúng một trong ba ca mà spec §6 đòi đo — làm `voiced()` trả `true` ở **mọi**
     * khung, và tầng 3 khi ấy không cắt gì cả: KWS chạy 100% thời gian. Đó chính là *"listen forever, hang CPU"*.
     *
     * Tầng này bịt lỗ ấy mà **không** đụng vào tính chất của tầng 3: nó không hỏi âm thanh, chỉ đếm — **tối đa
     * [maxKwsFramesPerWindow] khung được chạy KWS trong [kwsWindowMs]**. Vì thế tỉ lệ CPU của suy diễn có **chặn
     * trên chứng minh được** (mặc định 40 khung / 10 s ≈ **≤ 40 %** một khung-đọc, với khung 100 ms của `:app`),
     * bất kể phổ âm của cabin ra sao — một khẳng định kiểm được off-car, không phải một lời hứa chờ đo.
     *
     * ⚠ Đánh đổi đã biết: KWS là bộ giải mã **dòng**, nên bỏ khung làm đứt mạch và giảm khả năng bắt câu gọi
     * *trong lúc đang bị ồn liên tục* — đúng lúc mà nó vốn đã kém tin. Chọn CPU trước, đúng thứ tự ưu tiên owner
     * đặt ra. Một câu gọi hợp lệ dài ~1 s = ~10 khung, còn xa trần.
     */
    private fun chargeKws(nowMs: Long): Boolean {
        while (kwsFrames.isNotEmpty() && nowMs - kwsFrames.first() >= kwsWindowMs) kwsFrames.removeFirst()
        if (kwsFrames.size >= maxKwsFramesPerWindow) return false
        kwsFrames.addLast(nowMs)
        return true
    }

    /** KWS báo có/không khớp câu gọi trên khung vừa RUN_KWS. Trả việc cần làm ở cấp phiên. */
    fun onKwsResult(matched: Boolean, nowMs: Long): Wake {
        if (fused || !matched) return Wake.NONE
        cooldownUntil = nowMs + cooldownMs
        wakeTimes.addLast(nowMs)
        while (wakeTimes.isNotEmpty() && nowMs - wakeTimes.first() >= wakeWindowMs) wakeTimes.removeFirst()
        if (wakeTimes.size > maxWakesPerWindow) { fused = true; return Wake.FUSED }
        return Wake.FIRE
    }

    fun isFused(): Boolean = fused

    /** Bật lại từ đầu (khi user bật công tắc lại sau khi cầu chì đã tắt). */
    fun reset() {
        cooldownUntil = 0L; wakeTimes.clear(); kwsFrames.clear(); fused = false
        gate.reset(); loadGuard.reset()
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 3_000L      // sau khi nổ wake, nghỉ nghe (phiên lệnh đang chạy)
        const val DEFAULT_MAX_WAKES = 6             // > số này trong cửa sổ ⇒ nghi nghe nhầm ⇒ tự tắt (OQ5)
        const val DEFAULT_WAKE_WINDOW_MS = 60_000L

        /**
         * Trần thời lượng suy diễn (xem [chargeKws]). Đơn vị là **khung đọc của `:app`** (100 ms) — 40 khung /
         * 10 s ≈ ≤ 40 % thời gian. [CHƯA BIẾT] con số tối ưu: chốt cùng lượt đo on-car của OQ3 (cùng chỗ với
         * ngưỡng load-guard). Hạ xuống = an toàn hơn/bắt kém hơn, không có đường nào làm nó vô hiệu.
         */
        const val DEFAULT_MAX_KWS_FRAMES = 40
        const val DEFAULT_KWS_WINDOW_MS = 10_000L
    }
}
