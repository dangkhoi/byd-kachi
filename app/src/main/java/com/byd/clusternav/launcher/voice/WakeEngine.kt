package com.byd.clusternav.launcher.voice

/**
 * Bộ máy nhận "Hey Kachi" — hai thi hành hoán đổi được: [VoiceWakeKws] (KWS streaming) và [VoiceWakeAsr]
 * (ASR cửa-sổ + fuzzy, engine no-train owner chốt 2026-09-22). [VoiceWakeListener] chọn theo pref, mặc định ASR.
 *
 * Hợp đồng: `feed` nhận PCM float [-1,1] trên luồng nghe, trả `true` khi VỪA khớp câu gọi (tự reset để bắt lượt
 * kế); `release` giải phóng tài nguyên native, idempotent.
 */
interface WakeEngine {
    fun feed(pcm: FloatArray, n: Int): Boolean
    fun release()
}
