package com.byd.clusternav.launcher.voice

/**
 * ═══ CỔNG NĂNG LƯỢNG — gác cửa cho bộ nghe wake (thuần, cấm `android.*`) ══════════════════════════════════════
 *
 * ## Vai trò trong chống-hang-CPU (owner: "không thì listen forever, làm hang CPU")
 * Bộ nghe wake mở micro **liên tục**, nhưng chạy KWS (mạng nơ-ron) trên **mọi** khung âm thanh thì tốn CPU
 * suốt ngày — kể cả cabin im/ồn đường. Cổng này là **tầng rẻ** đứng trước: chỉ khi năng lượng khung vượt nền
 * (tức **có ai đang nói**) mới cho chạy KWS. 99% thời gian im/ồn nền = chỉ một phép so RMS, **0 suy diễn nơ-ron**.
 *
 * Cùng họ với [VoiceEndpointer] (ngắt câu bằng năng lượng) — nhưng nhiệm vụ khác: đây chỉ trả lời *"khung này
 * có đáng chạy KWS không"*. Ngưỡng `max(multiplier × nền, absoluteFloor)` mượn đúng công thức đã [ĐO] hiệu quả
 * ở endpointer (sàn tuyệt đối chống ca micro gần câm mà nền tính ra quá thấp).
 *
 * Nền cập nhật **CHẬM và chỉ khi KHÔNG phải giọng** — để một câu nói dài không tự kéo nền lên rồi tự bịt mình.
 * Thuần ⇒ test bằng CHUỖI rms giả.
 */
class VoiceWakeGate(
    val multiplier: Double = DEFAULT_MULTIPLIER,
    val absoluteFloor: Double = DEFAULT_ABS_FLOOR,
    val floorAlpha: Double = DEFAULT_FLOOR_ALPHA,
) {
    private var floor = absoluteFloor

    /** `true` = khung có năng lượng giọng ⇒ ĐÁNG chạy KWS. `false` = im ⇒ bỏ qua (tiết kiệm CPU). */
    fun voiced(rms: Double): Boolean {
        val threshold = maxOf(multiplier * floor, absoluteFloor)
        val v = rms > threshold
        if (!v) floor += floorAlpha * (rms - floor) // chỉ học nền lúc im — không để giọng nhiễm nền
        return v
    }

    fun noiseFloor(): Double = floor

    fun reset() { floor = absoluteFloor }

    companion object {
        const val DEFAULT_MULTIPLIER = 2.0
        const val DEFAULT_ABS_FLOOR = 120.0   // khớp sàn của VoiceEndpointer ([ĐO] rms giọng thật 206–627)
        const val DEFAULT_FLOOR_ALPHA = 0.05  // học nền chậm
    }
}
