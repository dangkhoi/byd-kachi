package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log

/**
 * ═══ "HEY KACHI" — ENGINE KHÔNG-TRAIN: ASR CỬA-SỔ + FUZZY "kachi" ═══════════════════════════════════════════
 *
 * Owner 2026-09-22: KWS gigaspeech (tiếng Anh) nghe "Kachi" kém. Cách NO-TRAIN đơn giản: dùng CHÍNH mô hình ASR
 * tiếng Việt đang có (`zipformer-vi-int8`) — nó ĐÃ nghe được "kachi" (nằm trong `VoiceLexicon.FILLERS`) — chạy
 * trên **cửa sổ cuộn ngắn** rồi khớp mờ bằng [WakeAsrMatcher] (`:core`, đã test). Không thu mẫu, không train.
 *
 * Drop-in cho [VoiceWakeKws]: cùng `feed(pcm, n): Boolean` + `release()`, nên [VoiceWakeListener] hoán engine mà
 * KHÔNG đổi vòng nghe. Khác biệt cơ chế:
 *  • KWS: streaming, chốt tức thì mỗi frame.
 *  • ASR: offline — phải GOM một cửa sổ (~[WINDOW_MS]) rồi giải mã MỘT lần mỗi [DECODE_EVERY_MS] (không giải mã
 *    mỗi frame: mô hình 74MB, giải mã liên tục sẽ bão hoà CPU — đây chính là biến số cần đo trên xe).
 *
 * ⚠ **CHỖ CHỈ XE TRẢ LỜI** (runbook): (1) CPU khi giải mã ~2 lần/giây trong cabin; (2) tỉ lệ nghe đúng "kachi"
 *   vs KWS; (3) false-accept trong lúc nói chuyện. Off-car chỉ đo được: dựng được engine, feed không crash,
 *   [WakeAsrMatcher] nổ đúng biến thể (đã có test).
 *
 * ⚠ Cửa sổ nhận PCM **float [-1,1]** (giống [VoiceWakeKws.feed]); ASR muốn `ShortArray` 16-bit ⇒ đổi tại chỗ.
 */
class VoiceWakeAsr private constructor(private val rec: VoiceRecognizer) : WakeEngine {

    private val lock = Any()
    private var released = false
    // Cửa sổ cuộn: giữ WINDOW_SAMPLES mẫu gần nhất (ring đơn giản bằng dịch trái khi đầy).
    private val window = ShortArray(WINDOW_SAMPLES)
    private var filled = 0
    private var sinceDecode = 0

    /** Nạp PCM float; `true` nếu cửa sổ vừa khớp "kachi". Gọi trên luồng nghe. */
    override fun feed(pcm: FloatArray, n: Int): Boolean = synchronized(lock) {
        if (released) return false
        appendFloat(pcm, n)
        sinceDecode += n
        if (sinceDecode < DECODE_EVERY_SAMPLES || filled < MIN_SAMPLES) return false
        sinceDecode = 0
        // #1 RMS-GATE (owner 2026-09-23, gốc :wake ~130% CPU): bản cũ giải mã mô hình 74MB MỖI 500ms VÔ ĐIỀU
        // KIỆN — kể cả cabin IM (log "sherpa ra: ''"). Đó là nguồn CPU nền cao. Chỉ giải mã khi cửa sổ có tiếng đủ
        // to. Im lặng → bỏ decode (giữ cửa sổ để khi có tiếng vẫn đủ ngữ cảnh). Ngưỡng RMS_GATE đo trên short PCM.
        if (windowRms() < RMS_GATE) return false
        val text = runCatching { rec.decodeAll(window, filled) }.getOrDefault("")
        if (text.isBlank()) return false
        val hit = WakeAsrMatcher.isWake(text)
        if (hit) { Log.i(TAG, "WAKE khớp: \"$text\""); filled = 0 }  // reset cửa sổ để không nổ lại cùng câu
        hit
    }

    /** RMS (0..1 chuẩn hoá) của cửa sổ hiện có — dùng để cổng decode (bỏ giải mã khi im lặng). */
    private fun windowRms(): Double {
        if (filled <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until filled) { val s = window[i].toDouble() / 32768.0; sum += s * s }
        return Math.sqrt(sum / filled)
    }

    override fun release(): Unit = synchronized(lock) { released = true }

    /** Nối n mẫu float (đổi sang short), dịch trái nếu tràn cửa sổ. */
    private fun appendFloat(pcm: FloatArray, n: Int) {
        val take = minOf(n, pcm.size)
        if (filled + take > window.size) {
            val drop = filled + take - window.size
            System.arraycopy(window, drop, window, 0, filled - drop)
            filled -= drop
        }
        for (i in 0 until take) {
            window[filled + i] = (pcm[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        filled += take
    }

    companion object {
        const val TAG = "KachiWakeAsr"
        const val SAMPLE_RATE = 16_000
        const val WINDOW_MS = 1_600
        const val DECODE_EVERY_MS = 500         // 2026-09-24: 1000→500 cắt nửa độ trễ "lâu mới lên" (owner). RMS-gate (windowRms<RMS_GATE) đã bỏ decode khi IM ⇒ CPU idle vẫn thấp, chỉ decode khi CÓ tiếng (đúng lúc cần bắt wake).
        const val MIN_MS = 500
        // #1 RMS-GATE: cửa sổ RMS < ngưỡng ⇒ coi là im lặng ⇒ KHÔNG giải mã (bỏ decode 74MB vô ích khi im).
        // 0.010 ≈ nền cabin im; tiếng nói thật RMS cao hơn nhiều. Nới lỏng để không bỏ sót "Hey Kachi" nói nhỏ.
        const val RMS_GATE = 0.010
        const val WINDOW_SAMPLES = SAMPLE_RATE * WINDOW_MS / 1000
        const val DECODE_EVERY_SAMPLES = SAMPLE_RATE * DECODE_EVERY_MS / 1000
        const val MIN_SAMPLES = SAMPLE_RATE * MIN_MS / 1000

        /** null nếu mô hình ASR chưa sẵn (off-car / chưa tải) ⇒ [VoiceWakeListener] degrade như khi kws null. */
        fun create(ctx: Context): VoiceWakeAsr? =
            VoiceRecognizer.openFree(ctx.applicationContext)?.let { VoiceWakeAsr(it) }
    }
}
