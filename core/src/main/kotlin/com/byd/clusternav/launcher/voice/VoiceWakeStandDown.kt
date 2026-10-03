package com.byd.clusternav.launcher.voice

/**
 * Quyết định THUẦN cho BG-20 — `:wake` có nên đứng xuống sau một phiên nghe headless không (xem
 * `VoiceWakeService.standDownTask`). Tách khỏi service để test off-device với pha giả; về `:core` 2026-09-26 (CLOSE-3)
 * vì `VoiceWakeService.kt` chạm trần 500 dòng và object này thuần (Q1: file thuần không ở `:app`).
 *
 * ## FIX286 · VK1 + VK3 (2.86) — hai đầu vào mới
 *  • [VoiceWakeMode] thay cờ `wakeEnabled`: HOLD (phím vô-lăng gán Kachi nghe, wake TẮT) giữ mô hình như WAKE ⇒ KEEP.
 *  • `loading` — mô hình **đang được dựng** (đọc từ chính khoá dựng, `ModelHolder.loading()`, không cờ ghi tay). [ĐO mã
 *    02/10] trước 2.86 nhánh đứng xuống gọi `VoiceEngine.release()` trên LUỒNG CHÍNH, chờ đúng khoá mà luồng nạp đang
 *    giữ (9–34 s): người lái huỷ khi còn "Getting ready…" rồi bấm lại ⇒ `onStartCommand` xếp hàng sau luồng chính bị
 *    chặn, không tấm chữ nào hiện, rồi mô hình vừa nạp xong bị nhả và nạp lại từ đầu. Đang nạp ⇒ WAIT (không bao giờ
 *    chờ khoá trên luồng chính); nạp xong lượt hỏi sau mới đứng xuống.
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

    /**
     * @param loading mô hình đang dựng dở. Đứng TRƯỚC trần chờ có chủ ý: đứng xuống lúc đang nạp không nhả được gì
     *   (khoá dựng đang bị giữ) mà chỉ bỏ service lại — luồng nạp xong sẽ để 74 MB trong một tiến trình không chủ.
     *   Lượt nạp luôn kết thúc (xong hoặc hỏng), nên WAIT ở đây không phải chờ vô hạn.
     */
    fun decide(
        mode: VoiceWakeMode,
        sessionPhase: VoiceTurnPhase,
        waitedMs: Long,
        loading: Boolean,
        maxWaitMs: Long = MAX_WAIT_MS,
    ): Decision = when {
        mode.modelInWake -> Decision.KEEP                  // WAKE/HOLD sở hữu service + mô hình (bộ nghe câu gọi / phím)
        loading -> Decision.WAIT                           // VK3: không đứng xuống giữa lượt nạp — xem KDoc
        sessionPhase == VoiceTurnPhase.IDLE -> Decision.STAND_DOWN
        waitedMs >= maxWaitMs -> Decision.STAND_DOWN       // kẹt — đứng xuống có log
        else -> Decision.WAIT
    }
}
