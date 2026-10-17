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
 * ## Cổng tiếng người + ngân sách (VOICE-WAKE-CPU-BUDGET, 2026-10-15)
 * [ĐO xe 15/10 tối] nhạc phát ⇒ cổng RMS luôn mở ⇒ giải mã mỗi 500 ms ⇒ `:wake` 105 % TB / 263 % đỉnh. Nay hai lớp:
 *  • [VoiceWakeSpeechGate] (Silero VAD riêng, [VoiceWakeVad]) — chỉ giải mã khi có tiếng NGƯỜI trong ~1,2 s gần nhất;
 *    VAD được nuôi ở MỌI khung engine nhận (cả [observe]), không phụ thuộc lượt giải mã có chạy không.
 *  • Chặn trên cứng ở [VoiceWakeController] (ngân sách ms CPU đo được): hết ngân sách ⇒ listener gọi [observe] thay
 *    [feed] ⇒ cửa sổ vẫn được gom liền mạch, lượt giải mã kế tiếp chạy ngay khi có lại ngân sách.
 *
 * ⚠ Cửa sổ nhận PCM **float [-1,1]** (giống [VoiceWakeKws.feed]); ASR muốn `ShortArray` 16-bit ⇒ đổi tại chỗ.
 */
class VoiceWakeAsr private constructor(
    private val rec: VoiceRecognizer,
    private val vad: VoiceWakeVad?,
) : WakeEngine {

    private val lock = Any()
    private var released = false
    // Cửa sổ cuộn: giữ WINDOW_SAMPLES mẫu gần nhất (ring đơn giản bằng dịch trái khi đầy).
    private val window = ShortArray(WINDOW_SAMPLES)
    private var filled = 0
    private var sinceDecode = 0
    private val speech = VoiceWakeSpeechGate()

    /** Số lượt giải mã đã chạy / số lượt bỏ vì không có tiếng người — cho nhật ký định kỳ + lệnh đo `kws`. */
    @Volatile var decodes: Long = 0L
        private set
    @Volatile var skippedNoSpeech: Long = 0L
        private set
    @Volatile var finishes: Long = 0L
        private set
    /**
     * WAKE-INTERMITTENT-1017 — lượt bị CỔNG RMS bỏ trong lúc cổng tiếng người đang MỞ (VAD vừa nghe tiếng người): tức tiếng
     * người NHỎ hơn [RMS_GATE]. Trước bản này lượt ấy không để lại dấu gì ⇒ "gọi mà không lên" không truy được trên xe.
     */
    @Volatile var skippedQuiet: Long = 0L
        private set

    /** Nạp PCM float; `true` nếu cửa sổ vừa khớp "kachi". Gọi trên luồng nghe. */
    override fun feed(pcm: FloatArray, n: Int): Boolean = synchronized(lock) {
        if (released) return false
        take(pcm, n)
        if (sinceDecode < DECODE_EVERY_SAMPLES || filled < MIN_SAMPLES) return false
        decodeWindow()
    }

    /**
     * Lượt giải mã CUỐI của một đoạn tiếng (VOICE-WAKE-TAIL-UNDECODED) — chỉ khi có ≥ [FINISH_MIN_MS] tiếng mới từ lượt
     * trước (vừa giải mã xong thì cửa sổ gần như y hệt, giải mã lại là CPU vô ích). Cùng các cổng như [feed].
     */
    override fun finish(): Boolean = synchronized(lock) {
        if (released || filled < MIN_SAMPLES || sinceDecode < FINISH_MIN_SAMPLES) return false
        finishes++
        decodeWindow()
    }

    /** Một lượt giải mã cửa sổ (đã giữ [lock]): cổng RMS → cổng tiếng người → ASR → khớp mờ. */
    private fun decodeWindow(): Boolean {
        sinceDecode = 0
        // #1 RMS-GATE (owner 2026-09-23, gốc :wake ~130% CPU): bản cũ giải mã mô hình 74MB MỖI 500ms VÔ ĐIỀU
        // KIỆN — kể cả cabin IM (log "sherpa ra: ''"). Đó là nguồn CPU nền cao. Chỉ giải mã khi cửa sổ có tiếng đủ
        // to. Im lặng → bỏ decode (giữ cửa sổ để khi có tiếng vẫn đủ ngữ cảnh). Ngưỡng RMS_GATE đo trên short PCM.
        if (windowRms() < RMS_GATE) { if (vad != null && speech.open()) skippedQuiet++; return false }
        if (!speech.open()) { skippedNoSpeech++; return false }   // có tiếng to nhưng không phải tiếng người (nhạc không lời)
        decodes++
        val text = runCatching { rec.decodeAll(window, filled) }.getOrDefault("")
        if (text.isBlank()) return false
        val hit = WakeAsrMatcher.isWake(text)
        if (hit) { Log.i(TAG, "WAKE khớp: \"$text\""); filled = 0 }  // reset cửa sổ để không nổ lại cùng câu
        return hit
    }

    /** RMS (0..1 chuẩn hoá) của cửa sổ hiện có — dùng để cổng decode (bỏ giải mã khi im lặng). */
    private fun windowRms(): Double {
        if (filled <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until filled) { val s = window[i].toDouble() / 32768.0; sum += s * s }
        return Math.sqrt(sum / filled)
    }

    /** Ngân sách đang nợ: gom khung (cửa sổ + VAD) nhưng KHÔNG giải mã — xem [WakeEngine.observe]. */
    override fun observe(pcm: FloatArray, n: Int): Unit = synchronized(lock) { if (!released) take(pcm, n) }

    private fun take(pcm: FloatArray, n: Int) {
        appendFloat(pcm, n)
        sinceDecode += n
        val v = vad ?: return
        speech.accept(pcm, n) { v.prob(it) }
    }

    override fun release(): Unit = synchronized(lock) {
        if (released) return
        released = true
        vad?.release()
    }

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
        const val FINISH_MIN_MS = 200
        const val FINISH_MIN_SAMPLES = SAMPLE_RATE * FINISH_MIN_MS / 1000
        // #1 RMS-GATE: cửa sổ RMS < ngưỡng ⇒ coi là im lặng ⇒ KHÔNG giải mã (bỏ decode 74MB vô ích khi im).
        // WAKE-INTERMITTENT-1017: 0,010 → 0,005. [ĐO xe 17/10] micro phiên lệnh rms 423–956/32767 (0,013–0,029) trên cả khúc
        // 4,8–7,2 s ⇒ cửa sổ 1,6 s của một câu gọi nói nhẹ nằm ngay quanh 0,010. [ĐO host `wake-harness/gate-sweep.py`, Silero
        // asset thật, 60 câu gọi TTS] giọng RMS 0,010 ⇒ chỉ 25/60 cửa sổ tới ASR; 0,007 ⇒ 0/60 — và bị bỏ IM LẶNG (không bộ đếm
        // nào). Ở 0,005: 60/60 cả hai mức; 0,0037 (sàn cổng khung) ra đúng bằng 0,005 nên giữ 0,005 (lề trên sàn). Từ 3.1.9 cổng
        // tiếng người ([VoiceWakeSpeechGate]) mới là chỗ chặn chính; cổng này chỉ còn bỏ cửa sổ gần-im. Giá: 45 s nhạc có lời
        // nhỏ (0,008) 0 → 2 lượt; câu thường nói nhỏ 1,51 → 2,51 lượt/tệp (= mức câu thường nói to 2,63) — vẫn qua ngân sách.
        const val RMS_GATE = 0.005
        const val WINDOW_SAMPLES = SAMPLE_RATE * WINDOW_MS / 1000
        const val DECODE_EVERY_SAMPLES = SAMPLE_RATE * DECODE_EVERY_MS / 1000
        const val MIN_SAMPLES = SAMPLE_RATE * MIN_MS / 1000

        /** null nếu mô hình ASR chưa sẵn (off-car / chưa tải) ⇒ [VoiceWakeListener] degrade như khi kws null. */
        /**
         * @param speechGate `false` CHỈ cho lệnh đo `kws name=asr-base` (so trước/sau trên cùng bộ WAV): không dựng VAD
         *   ⇒ đúng hành vi cổng-RMS-thuần trước VOICE-WAKE-CPU-BUDGET. Đường thật luôn dùng mặc định.
         */
        fun create(ctx: Context, speechGate: Boolean = true): VoiceWakeAsr? {
            val rec = VoiceRecognizer.openFree(ctx.applicationContext) ?: return null
            return VoiceWakeAsr(rec, if (speechGate) VoiceWakeVad.open(ctx) else null)
        }
    }
}
