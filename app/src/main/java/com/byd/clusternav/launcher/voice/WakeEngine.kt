package com.byd.clusternav.launcher.voice

/**
 * Bộ máy nhận "Hey Kachi" — hai thi hành hoán đổi được: [VoiceWakeKws] (KWS streaming) và [VoiceWakeAsr]
 * (ASR cửa-sổ + fuzzy, engine no-train owner chốt 2026-09-22). [VoiceWakeListener] chọn theo pref, mặc định ASR.
 *
 * Hợp đồng: `feed` nhận PCM float [-1,1] trên luồng nghe, trả `true` khi VỪA khớp câu gọi (tự reset để bắt lượt
 * kế); `release` giải phóng tài nguyên native, idempotent.
 *
 * `observe` (VOICE-WAKE-CPU-BUDGET): khung có tiếng nhưng ngân sách CPU đang nợ ([VoiceWakeController.Frame.OBSERVE])
 * ⇒ engine được GOM khung để cửa sổ của lượt suy diễn kế tiếp liền mạch, nhưng **không được suy diễn**. Mặc định
 * không làm gì (KWS dòng: bỏ khung là đúng đánh đổi đã ghi ở controller).
 */
interface WakeEngine {
    fun feed(pcm: FloatArray, n: Int): Boolean
    fun observe(pcm: FloatArray, n: Int) {}

    /**
     * VOICE-WAKE-TAIL-UNDECODED — sườn tiếng→im ([VoiceWakeController.Frame.FINISH]): suy diễn MỘT lần trên những gì
     * đã gom (cả đuôi câu vừa nhận qua [observe]). `true` nếu khớp câu gọi. Mặc định `false` (KWS dòng đã chấm từng khung).
     */
    fun finish(): Boolean = false
    fun release()
}
