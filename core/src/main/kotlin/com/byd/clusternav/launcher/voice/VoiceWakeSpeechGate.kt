package com.byd.clusternav.launcher.voice

/**
 * ═══ CỔNG TIẾNG NGƯỜI trước lượt giải mã ASR của "Hey Kachi" (VOICE-WAKE-CPU-BUDGET, 2026-10-15) ══════════════════
 *
 * Cổng RMS cũ (`VoiceWakeAsr.RMS_GATE` 0,010) chỉ hỏi *"cửa sổ có to không"* — nhạc trong cabin luôn to ⇒ [ĐO xe
 * 15/10] 97/102 lượt giải mã có chữ, cổng không cắt gì. Cổng này hỏi *"vừa rồi có **tiếng người** không"* bằng xác
 * suất của Silero VAD (asset 0,64 MB đã có trong APK, cùng tệp `VoiceVad` dùng) trên từng cửa sổ 512 mẫu.
 *
 * Luật giải mã: chỉ giải mã khi có ≥ 1 cửa sổ tiếng người trong [holdSamples] mẫu gần nhất (mặc định = HAI nhịp
 * giải mã 2 × 500 ms + 200 ms dư). Vì sao hai: lượt đầu SAU khi câu dứt chứa trọn câu nhưng kèm nhiều nhạc phía
 * trước; [ĐO máy ảo 15/10, harness wake + nhạc tổng hợp] với hold một nhịp (700 ms) mất 4/57 ca "ka chi"/"ca chi" mà
 * bản cũ bắt được đúng ở lượt THỨ HAI sau câu (cửa sổ lệch đi, ít nhạc đầu hơn); hold 1 200 ms trả lại các ca đó. Các
 * lượt sau nữa (chỉ còn im/nhạc không lời) bị bỏ — đó là phần CPU tiết kiệm được.
 *
 * ⚠ Đây là lớp **phụ**: nhạc CÓ LỜI vẫn lọt VAD. Chặn trên cứng là [VoiceWakeCpuBudget]; cổng này chỉ làm ngân sách
 * ít bị tiêu vào nhạc không lời / tiếng ồn lăn bánh.
 *
 * ## Hỏng thì MỞ (không điếc)
 * Không dựng được VAD hoặc VAD ném ⇒ chỗ gọi không đưa xác suất nào ⇒ [open] = `true` mãi: rơi về hành vi cũ (chỉ cổng
 * RMS), CPU vẫn bị [VoiceWakeCpuBudget] chặn. Thà tốn CPU có trần còn hơn điếc im lặng.
 *
 * VAD được nuôi MỌI khung engine nhận (cả khung chỉ-quan-sát khi ngân sách đang nợ) ⇒ trạng thái của cổng không bao
 * giờ phụ thuộc vào việc lượt giải mã có chạy hay không (CLAUDE.md §3: không gate đường phục hồi bằng dữ liệu chỉ
 * chính đường đó làm mới).
 *
 * Thuần: xác suất do chỗ gọi tính (`Vad.compute`), lớp này chỉ cắt khối + nhớ mốc.
 */
class VoiceWakeSpeechGate(
    private val threshold: Float = VoiceVadTrim.THRESHOLD,
    private val holdSamples: Int = DEFAULT_HOLD_SAMPLES,
    private val windowSamples: Int = VoiceVadTrim.WINDOW_SIZE,
) {
    private val carry = FloatArray(windowSamples)
    private var carried = 0
    private var sinceSpeech = NEVER
    private var everFed = false

    /** Số cửa sổ đã chấm / số cửa sổ chấm là tiếng người — cho nhật ký/lệnh đo. */
    var windows: Long = 0L
        private set
    var speechWindows: Long = 0L
        private set

    /**
     * Nạp [n] mẫu float; với mỗi cửa sổ đủ [windowSamples] mẫu gọi [prob] (trả xác suất tiếng người 0..1, hoặc `null`
     * nếu VAD hỏng — khi đó cổng chuyển hẳn sang MỞ). Phần lẻ được giữ lại cho lượt sau (khung micro 1 600 = 3×512 + 64).
     */
    fun accept(pcm: FloatArray, n: Int, prob: (FloatArray) -> Float?) {
        if (broken) return
        var i = 0
        val take = minOf(n, pcm.size)
        while (i < take) {
            val k = minOf(windowSamples - carried, take - i)
            System.arraycopy(pcm, i, carry, carried, k)
            carried += k; i += k
            if (carried == windowSamples) {
                carried = 0
                val p = prob(carry)
                if (p == null || !p.isFinite()) { broken = true; return }
                everFed = true
                windows++
                if (p >= threshold) { speechWindows++; sinceSpeech = 0 }
                else sinceSpeech = (sinceSpeech + windowSamples).coerceAtMost(NEVER)
            }
        }
    }

    /** VAD đã hỏng ⇒ cổng MỞ vĩnh viễn cho engine này (xem KDoc lớp). */
    var broken: Boolean = false
        private set

    /**
     * Có được giải mã không: VAD hỏng ⇒ có; chưa chấm cửa sổ nào ⇒ có (không đoán thay dữ liệu); còn lại ⇒ có tiếng
     * người trong [holdSamples] mẫu gần nhất.
     */
    fun open(): Boolean = broken || !everFed || sinceSpeech <= holdSamples

    fun reset() { carried = 0; sinceSpeech = NEVER; everFed = false }

    companion object {
        private const val NEVER = Int.MAX_VALUE / 2

        /** 2 × 500 ms (nhịp giải mã của `VoiceWakeAsr`) + 200 ms dư cho trễ của VAD — 16 kHz. Xem KDoc lớp. */
        const val DEFAULT_HOLD_SAMPLES = 16_000 * 1_200 / 1000
    }
}
