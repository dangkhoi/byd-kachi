package com.byd.clusternav.launcher.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.ToneGenerator
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.voiceMicSource

/**
 * ═══ V1 pha NGHE · MICRO → PCM → BỘ NHẬN DẠNG ════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R11**. Đây là **nơi duy nhất** trong dự án mở micro.
 *
 * ## Mức bằng chứng cho việc "app thường mở được micro trên xe này" (CLAUDE.md §2)
 * **[ĐO], gián tiếp nhưng là số đo thật**: Kiki Car (`ai.zalo.kiki.car`) là **app thường** — không phải app hệ
 * thống, không ký khoá nhà sản xuất — và nó chạy wake word tại máy trên chính đầu xe của owner, tức nó giữ
 * micro mở liên tục qua `AudioSource.MIC` (`docs/diagnostics/kiki-car-RE-2026-09-14.md`). Owner 2026-09-14:
 * *"Kiki nó chạy được, Gemini chạy được trên xe thì app mình cũng chạy được, đâu cần chứng minh gì nữa"* ⇒ cổng
 * tầng 1 của CLAUDE.md §14 được owner **miễn cho riêng mục micro**, và lý do miễn được ghi lại ở đây chứ không
 * chỉ trong một câu chat.
 *
 * **[CHƯA BIẾT]** và cố ý không đụng tới ở pha này: phát tiếng nói ra (TTS tiếng Việt). Phản hồi là **âm báo +
 * chữ** ([tone] + tấm chữ ở [VoiceOverlay]).
 *
 * ## Ba quyết định về âm thanh, mỗi cái là một lựa chọn có thể sai theo hướng khác
 *  1. **`MIC` trước** (V3 · R1, đổi 2026-09-16 — trước đó là `VOICE_RECOGNITION` trước). Lập luận cũ *"nguồn 6
 *     bỏ qua AGC/khử ồn nên sạch hơn cho mô hình"* đúng về cơ chế nhưng **bị [ĐO xe] bác về quy kết**: trên ROM
 *     này nguồn 6 cho tiếng gần câm 3/4 lượt. Thứ tự + lý do đầy đủ ở [VoiceMicSource]; ROM vẫn có thể dựng ra
 *     `AudioRecord` `STATE_UNINITIALIZED` **mà không ném**, nên vẫn phải kiểm trạng thái rồi mới lùi nguồn sau.
 *  2. **KHÔNG tắt nhạc — chỉ xin `TRANSIENT_MAY_DUCK`.** Dừng hẳn nhạc cho một câu 3 giây là cắt ngang thứ
 *     người ta đang nghe rồi trả lại ở chỗ khác. Hạ tiếng thì đủ để micro nghe rõ mà bài hát không đứt.
 *  3. **Âm báo, không phải giọng nói.** Tiếng "bíp" đầu/cuối trả lời đúng câu hỏi duy nhất người lái có lúc ấy
 *     (*"nó bắt đầu/kết thúc nghe chưa"*) trong 80 ms, không cần nhìn màn hình, và **không cần TTS** — thứ còn
 *     [CHƯA BIẾT] trên xe này.
 */
internal class VoiceCapture(private val ctx: Context) {

    /** Micro đã được cấp quyền chưa. */
    fun hasPermission(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Chữ nghe được + **khúc PCM đã thu** của chính lượt ấy.
     *
     * ## Vì sao giữ lại tiếng, trong một dự án mà cả một bài canh sinh ra để tiếng KHÔNG rời khỏi xe
     * V1.1 cần một lượt giải mã **thứ hai** (tự do, không ngữ pháp) trên đúng khúc tiếng vừa nghe, để đọc ra tên
     * bài / điểm đến — xem KDoc [VoiceOpenVocab]. Khúc ấy:
     *  • sống **trong RAM của tiến trình**, không tệp, không mạng (bài canh R14 vẫn nguyên hiệu lực);
     *  • có **trần cứng** [MAX_KEPT_SAMPLES] = 9 giây ≈ 288 KB, dài hơn trần một phiên
     *    (`VoiceSession.MAX_LISTEN_MS` = 8 s) đúng một giây để không cắt cụt câu cuối;
     *  • chết cùng lượt nghe — không có trường nào của lớp này giữ nó lại.
     */
    data class Heard(val text: String, val pcm: ShortArray, val samples: Int) {
        // `data class` mang `ShortArray` ⇒ `equals`/`hashCode` sinh sẵn so theo THAM CHIẾU. Khai lại tường minh
        // để không ai vô tình dựa vào một phép so sai; lớp này không bao giờ cần so bằng.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /**
     * Nghe MỘT lượt: mở micro, đẩy từng khối vào [rec], dừng khi Vosk chốt câu / hết [maxMs] / [cancelled].
     *
     * **CHẶN** ⇒ luồng nền. Trả chữ nghe được (có thể rỗng) kèm khúc PCM — xem [Heard].
     *
     * @param onPartial chữ đang nghe dở — gọi **trên luồng nền**, chỗ gọi tự đẩy lên luồng vẽ.
     * @param cancelled hỏi mỗi vòng; `true` ⇒ dừng ngay và trả phần đã nghe.
     * @param keepPcm giữ lại khúc tiếng hay không. `false` cho lượt nghe câu *"đồng ý/huỷ"* — nó không bao giờ
     *   cần lượt giải mã thứ hai, nên giữ tiếng ở đó là giữ một thứ không ai dùng.
     */
    @Suppress("ReturnCount", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")
    fun listen(
        rec: VoiceRecognizer,
        maxMs: Long,
        cancelled: () -> Boolean,
        keepPcm: Boolean = false,
        endpointer: VoiceEndpointer? = VoiceEndpointer(),
        /**
         * V3 · R9 — có kêu tiếng bíp **đầu** lượt không.
         *
         * `false` cho lượt nối của hội thoại: tiếng bíp trả lời câu *"nó bắt đầu nghe chưa"* của một phiên do
         * người dùng vừa mở; trong một vòng hội thoại thì micro chỉ **chưa đóng**, và một tiếng bíp sau mỗi câu
         * trả lời là thứ làm người ta tắt tính năng. Tiếng bíp CUỐI thì vẫn còn (nó là mốc *"tôi thôi nghe"*).
         */
        beep: Boolean = true,
        onPartial: (String) -> Unit,
    ): Heard {
        val kept = if (keepPcm) ShortArray(MAX_KEPT_SAMPLES) else EMPTY
        var keptN = 0
        val tOpen = System.currentTimeMillis()
        val record = openRecord() ?: return Heard("", kept, keptN)
        val focus = requestFocus()
        try {
            runCatching { record.startRecording() }.onFailure {
                Log.w(TAG, "startRecording hỏng", it); return Heard("", kept, keptN)
            }
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w(TAG, "micro không vào được trạng thái ghi — ROM từ chối?")
                return Heard("", kept, keptN)
            }
            Log.i(VoiceEngine.TIMING_TAG, "mic mở sau ${System.currentTimeMillis() - tOpen} ms")
            if (beep) tone(ToneGenerator.TONE_PROP_BEEP, TONE_START_MS)
            val buf = ShortArray(CHUNK_SAMPLES)
            val tListen = System.currentTimeMillis()
            val deadline = tListen + maxMs
            var lastPartial = ""
            // [ĐO bug voice 2026-09-15] Mức tín hiệu micro — chốt "câm/không nghe được" trên xe TRONG MỘT lượt nói:
            // đỉnh gần 0 ⇒ mic không có tiếng (nguồn/ROM chặn); đỉnh kịch 32767 liên tục ⇒ méo/clip (nghi mic-array
            // 4 kênh ghép sai); đỉnh vừa mà sherpa ra rỗng ⇒ chất lượng/định dạng. Không lưu gì, chỉ hai số.
            var peak = 0
            var sumSq = 0.0
            var samples = 0L
            var ended = false
            while (!cancelled() && System.currentTimeMillis() < deadline) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) {
                    // `ERROR_INVALID_OPERATION`/`ERROR_DEAD_OBJECT`: micro bị một app khác giành mất giữa chừng.
                    // Trả phần đã nghe thay vì quay vòng bận — một vòng lặp nóng trên đầu xe là quạt kêu và pin tụt.
                    if (n < 0) { Log.w(TAG, "đọc micro trả $n — dừng phiên"); break }
                    continue
                }
                var chunkSq = 0.0
                for (i in 0 until n) {
                    val a = kotlin.math.abs(buf[i].toInt())
                    if (a > peak) peak = a
                    chunkSq += a.toDouble() * a
                }
                sumSq += chunkSq
                samples += n
                // Chép TRƯỚC khi giải mã: `accept` có thể chốt câu và thoát ngay ở dòng dưới.
                if (keepPcm && keptN < kept.size) {
                    val room = minOf(n, kept.size - keptN)
                    System.arraycopy(buf, 0, kept, keptN, room)
                    keptN += room
                }
                if (rec.accept(buf, n)) { logLevel(peak, sumSq, samples); return Heard(rec.result(), kept, keptN) }
                // ═══ V3 · R2 — NGẮT CÂU khi người ta ngừng nói (xem KDoc [VoiceEndpointer]) ═══════════
                // Đặt SAU `rec.accept` (khối đã vào bộ gom) và TRƯỚC `partial`: thoát ở đây thì khúc tiếng đã
                // đầy đủ, `finalResult()` dưới kia giải mã đúng thứ vừa nói, không thiếu khối cuối.
                if (endpointer != null) {
                    val rmsChunk = kotlin.math.sqrt(chunkSq / n).toInt()
                    if (endpointer.accept(rmsChunk, n * 1000 / SAMPLE_RATE) == VoiceEndpointer.Phase.ENDED) {
                        Log.i(VoiceEngine.TIMING_TAG, "ngắt câu: ${endpointer.summary()}")
                        ended = true
                        break
                    }
                }
                val p = rec.partial()
                if (p.isNotEmpty() && p != lastPartial) { lastPartial = p; onPartial(p) }
            }
            if (!ended && endpointer != null) Log.i(VoiceEngine.TIMING_TAG, "hết trần: ${endpointer.summary()}")
            Log.i(VoiceEngine.TIMING_TAG, "nghe ${System.currentTimeMillis() - tListen} ms")
            logLevel(peak, sumSq, samples)
            val tDecode = System.currentTimeMillis()
            val text = rec.finalResult()
            Log.i(VoiceEngine.TIMING_TAG, "giải mã ${System.currentTimeMillis() - tDecode} ms")
            return Heard(text, kept, keptN)
        } finally {
            closeRecord(record, focus)
        }
    }

    /**
     * ═══ V3 · R3 — ĐÓNG micro, và **đo từng bước** ════════════════════════════════════════════════════════
     *
     * ## Vì sao hàm này tồn tại riêng
     * [ĐO xe 2026-09-16] có một **lỗ 3,1 giây** lặp lại ở MỌI lượt, nằm đúng giữa mốc *"sherpa ra: …"* và mốc
     * *"lượt 1 (ngữ pháp) nghe được"* (4 lần đo: 28.065→31.163 · 43.853→46.951 · 18.511→21.626 · 53.268→56.378).
     * [SUY] đọc mã: khoảng đó **chỉ có** `finally` của [listen] — `stop` · `release` · tiếng bíp cuối ·
     * `abandonAudioFocus`. Bốn việc, và trước bản này không có cách nào biết cái nào.
     *
     * ## Hai việc bản này làm, và cái thứ hai KHÔNG phải một phỏng đoán
     *  1. **Đo từng bước** (`KachiVoiceTiming`) ⇒ lượt xe sau đọc một dòng là biết thủ phạm.
     *  2. **Đẩy tiếng bíp cuối + nhả tiêu điểm sang luồng nền.** Đây không phải đoán mò mà là một quan sát đúng
     *     về **phân công**: cả hai việc ấy không ai chờ kết quả — tiếng bíp là phản hồi cho tai, nhả tiêu điểm là
     *     phép lịch sự với app nhạc. Giữ chúng trên đường về của câu trả lời là bắt người lái chờ hai việc không
     *     liên quan tới câu họ vừa nói. `stop`/`release` thì **ở lại** đúng chỗ: chúng phải xong trước khi phiên
     *     sau mở `AudioRecord` thứ hai (hazard đã ghi ở KDoc `VoiceSession.cancel`).
     */
    private fun closeRecord(record: AudioRecord, focus: AudioFocusRequest?) {
        val t0 = System.currentTimeMillis()
        runCatching { record.stop() }
        val tStop = System.currentTimeMillis()
        runCatching { record.release() }
        val tRelease = System.currentTimeMillis()
        Log.i(
            VoiceEngine.TIMING_TAG,
            "đóng mic: stop ${tStop - t0} ms · release ${tRelease - tStop} ms (bíp + nhả tiêu điểm chạy nền)",
        )
        Thread({
            val t1 = System.currentTimeMillis()
            tone(ToneGenerator.TONE_PROP_ACK, TONE_END_MS)
            val t2 = System.currentTimeMillis()
            abandonFocus(focus)
            Log.i(
                VoiceEngine.TIMING_TAG,
                "nền: bíp ${t2 - t1} ms · nhả tiêu điểm ${System.currentTimeMillis() - t2} ms",
            )
        }, "KachiMicTail").apply { isDaemon = true }.start()
    }

    /**
     * Ghi mức tín hiệu của một lượt nghe (bug voice 2026-09-15) — đỉnh biên độ + RMS, cả hai theo thang 0..32767.
     * Một dòng, đọc được ngay trong logcat trên xe để phân biệt câm / clip / chất-lượng mà không cần lưu tệp.
     */
    private fun logLevel(peak: Int, sumSq: Double, samples: Long) {
        val rms = if (samples > 0) kotlin.math.sqrt(sumSq / samples).toInt() else 0
        Log.i(TAG, "mức micro: đỉnh $peak/32767 · rms $rms · $samples mẫu (${samples / 16}ms)")
    }

    /**
     * Dựng [AudioRecord]: thử `VOICE_RECOGNITION` rồi mới `MIC` — xem KDoc lớp, quyết định (1).
     *
     * Bộ đệm lấy **gấp đôi** mức tối thiểu của ROM: mức tối thiểu là ngưỡng *"không tràn nếu đọc đúng nhịp"*,
     * mà luồng nghe của ta còn phải chạy giải mã Kaldi giữa hai lượt đọc. Thiếu chỗ đệm ⇒ mất mẫu ⇒ mất chữ,
     * và mất kiểu đó không có lỗi nào báo.
     */
    private fun openRecord(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val size = if (min > 0) min * 2 else CHUNK_SAMPLES * 2 * 8
        // V3 · R1 — thứ tự lấy từ `:core` ([VoiceMicSource]) + lựa chọn của người dùng. Xem KDoc ở đó để biết
        // vì sao MIC đứng trước ([ĐO xe 2026-09-16]) và vì sao ép một nguồn vẫn còn đường lùi.
        val pref = runCatching { Prefs.voiceMicSource(ctx) }.getOrDefault(VoiceMicSource.PREF_AUTO)
        for (source in VoiceMicSource.order(pref)) {
            val r = runCatching {
                AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
            }.getOrNull() ?: continue
            if (r.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "micro mở bằng nguồn ${VoiceMicSource.sourceName(source)} (đệm $size byte · pref=$pref)")
                return r
            }
            Log.w(TAG, "nguồn $source dựng ra AudioRecord chưa khởi tạo — thử nguồn sau")
            runCatching { r.release() }
        }
        Log.w(TAG, "không mở được micro bằng nguồn nào")
        return null
    }

    // ── tiêu điểm âm thanh ───────────────────────────────────────────────────────────────────────

    private fun audio(): AudioManager? = ctx.getSystemService(AudioManager::class.java)

    private fun requestFocus(): AudioFocusRequest? {
        val am = audio() ?: return null
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            // Không nghe đổi tiêu điểm: phiên dài tối đa 8 s và tự kết thúc. Đăng ký một listener chỉ để bỏ qua
            // mọi sự kiện của nó là thêm một đường sống lâu hơn phiên — thứ §5 CLAUDE.md dặn phải tránh.
            .setWillPauseWhenDucked(false)
            .build()
        return if (runCatching { am.requestAudioFocus(req) }.getOrNull() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) req
        else null
    }

    private fun abandonFocus(req: AudioFocusRequest?) {
        val am = audio() ?: return
        req?.let { runCatching { am.abandonAudioFocusRequest(it) } }
    }

    /**
     * Một tiếng báo ngắn.
     *
     * Dựng rồi **giải phóng ngay** mỗi lần: giữ một [ToneGenerator] sống suốt đời tiến trình là giữ một đường
     * vào `AudioTrack` mở vĩnh viễn cho hai tiếng bíp mỗi vài phút. `runCatching` vì vài ROM xe từ chối hẳn
     * `STREAM_NOTIFICATION` — im lặng một tiếng bíp không được phép làm hỏng phiên nghe.
     */
    private fun tone(type: Int, ms: Int) {
        runCatching {
            val g = ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
            g.startTone(type, ms)
            // Giải phóng sau khi tiếng đã phát xong; huỷ sớm là cắt cụt tiếng bíp.
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ runCatching { g.release() } }, (ms + TONE_RELEASE_PAD_MS).toLong())
        }.onFailure { Log.w(TAG, "không phát được âm báo", it) }
    }

    companion object {
        private const val TAG = "KachiVoiceMic"

        /** 16 kHz — cùng số với mô hình (`conf/mfcc.conf`) và với [VoiceRecognizer.SAMPLE_RATE]. */
        const val SAMPLE_RATE = 16_000

        /** 200 ms mỗi khối: đủ lớn để không gọi `read` liên tục, đủ nhỏ để chữ partial hiện gần như tức thì. */
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 5

        /**
         * Trần khúc tiếng giữ lại cho lượt giải mã thứ hai — **9 giây** (≈ 288 KB PCM16 @16 kHz).
         *
         * Dài hơn trần một phiên (`VoiceSession.MAX_LISTEN_MS` = 8 s) đúng một giây: trần phiên đếm theo đồng hồ
         * treo tường còn mảng này đếm theo mẫu, và hai thứ đó không bao giờ khớp tuyệt đối.
         */
        const val MAX_KEPT_SAMPLES = SAMPLE_RATE * 9

        /** Không cấp phát gì khi chỗ gọi không cần tiếng (lượt nghe *"đồng ý/huỷ"*). */
        private val EMPTY = ShortArray(0)

        private const val TONE_VOLUME = 70
        private const val TONE_START_MS = 90
        private const val TONE_END_MS = 60
        private const val TONE_RELEASE_PAD_MS = 250
    }
}
