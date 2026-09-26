package com.byd.clusternav.launcher.voice

/**
 * Quyết định THUẦN cho BG-20 — `:wake` có nên đứng xuống sau một phiên nghe headless không (xem
 * `VoiceWakeService.standDownTask`). Tách khỏi service để test off-device với pha giả; về `:core` 2026-09-26 (CLOSE-3)
 * vì `VoiceWakeService.kt` chạm trần 500 dòng và object này thuần (Q1: file thuần không ở `:app`).
 */
object VoiceWakeStandDown {
    enum class Decision { KEEP, WAIT, STAND_DOWN }

    /** Nhịp hỏi lại pha của phiên. Rẻ (một `AtomicReference.get`), không cần nhanh: người lái không thấy gì. */
    const val POLL_MS = 2_000L

    /**
     * Trần chờ một phiên về IDLE. Một phiên bình thường: nghe ≤ 8 s + hỏi-lại/hội thoại (≤ 5 lượt) + đọc + nán 2,5 s
     * — dưới 2 phút. Quá 3 phút là phiên kẹt (lỗi), đứng xuống có log thay vì giữ FGS + 74 MB mãi.
     */
    const val MAX_WAIT_MS = 3 * 60_000L

    fun decide(wakeEnabled: Boolean, sessionPhase: VoiceTurnPhase, waitedMs: Long, maxWaitMs: Long = MAX_WAIT_MS): Decision = when {
        wakeEnabled -> Decision.KEEP                       // vòng đời thường sở hữu service (bộ nghe câu gọi cần recognizer)
        sessionPhase == VoiceTurnPhase.IDLE -> Decision.STAND_DOWN
        waitedMs >= maxWaitMs -> Decision.STAND_DOWN       // kẹt — đứng xuống có log
        else -> Decision.WAIT
    }
}
