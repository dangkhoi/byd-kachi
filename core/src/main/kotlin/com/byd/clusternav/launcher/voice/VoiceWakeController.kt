package com.byd.clusternav.launcher.voice

/**
 * ═══ BỘ NÃO của bộ nghe "Hey Kachi" — quyết định MỖI NHỊP, thuần (cấm `android.*`) ════════════════════════════
 *
 * Đây là phần "làm rất khéo" mà owner lo: nó gom [VoiceLoadGuard] + [VoiceWakeGate] + cooldown + cầu chì
 * false-accept thành **một máy quyết định kiểm được off-car**. Lớp Android ở `:app` chỉ là vòng lặp mỏng: đọc
 * micro → tính RMS + đọc `/proc/loadavg` → hỏi [onFrame]; nếu bảo `RUN_KWS` thì chạy KWS trên khung → báo kết
 * quả về [onKwsResult]. Mọi lý lẽ chống-hang-CPU nằm ở đây, nơi bơm được số giả để chứng minh.
 *
 * ## Ba tầng cắt CPU (đắt dần)
 *  1. **Load-guard** — hệ nóng ⇒ `SUSPENDED`: nhả mic, thôi cả RMS lẫn KWS (lá chắn chính vì nghe nền).
 *  2. **Cooldown** — vừa nổ wake xong ⇒ `IDLE` một lúc (phiên lệnh đang chạy; tránh tự nghe lại chime/tiếng mình).
 *  3. **Cổng năng lượng** — im ⇒ `IDLE` (chỉ toán RMS); có giọng ⇒ `RUN_KWS` (mới trả giá nơ-ron).
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
) {
    /** Việc cần làm với khung âm thanh hiện tại. */
    enum class Frame { SUSPENDED, IDLE, RUN_KWS }

    /** Kết quả sau khi KWS chấm một khung. */
    enum class Wake { NONE, FIRE, FUSED }

    private var cooldownUntil = 0L
    private val wakeTimes = ArrayDeque<Long>()
    private var fused = false

    /** Nhịp âm thanh: quyết định có chạy KWS không. `:app` gọi với RMS khung + load hiện tại. */
    fun onFrame(rms: Double, load1: Double, nowMs: Long): Frame {
        if (fused) return Frame.SUSPENDED
        if (!loadGuard.allow(load1)) return Frame.SUSPENDED      // tầng 1: hệ nóng
        if (nowMs < cooldownUntil) return Frame.IDLE             // tầng 2: vừa nổ wake
        return if (gate.voiced(rms)) Frame.RUN_KWS else Frame.IDLE // tầng 3: có giọng mới chạy KWS
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
        cooldownUntil = 0L; wakeTimes.clear(); fused = false
        gate.reset(); loadGuard.reset()
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 3_000L      // sau khi nổ wake, nghỉ nghe (phiên lệnh đang chạy)
        const val DEFAULT_MAX_WAKES = 6             // > số này trong cửa sổ ⇒ nghi nghe nhầm ⇒ tự tắt (OQ5)
        const val DEFAULT_WAKE_WINDOW_MS = 60_000L
    }
}
