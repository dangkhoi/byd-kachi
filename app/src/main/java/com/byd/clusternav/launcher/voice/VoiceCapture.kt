package com.byd.clusternav.launcher.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.util.Log

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
 *  1. **`VOICE_RECOGNITION` trước, `MIC` sau.** Nguồn đầu bỏ qua xử lý làm đẹp giọng (AGC/khử ồn định hướng cho
 *     cuộc gọi) — thứ làm méo phổ mà một mô hình Kaldi 32 MB rất nhạy. Nhưng không phải ROM nào cũng khai nguồn
 *     đó; [ĐO] nó có thể dựng ra `AudioRecord` ở trạng thái `STATE_UNINITIALIZED` **mà không ném**. Nên phải
 *     kiểm trạng thái rồi mới lùi về `MIC`, không lùi theo linh cảm.
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
    @Suppress("ReturnCount", "LongParameterList")
    fun listen(
        rec: VoiceRecognizer,
        maxMs: Long,
        cancelled: () -> Boolean,
        keepPcm: Boolean = false,
        onPartial: (String) -> Unit,
    ): Heard {
        val kept = if (keepPcm) ShortArray(MAX_KEPT_SAMPLES) else EMPTY
        var keptN = 0
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
            tone(ToneGenerator.TONE_PROP_BEEP, TONE_START_MS)
            val buf = ShortArray(CHUNK_SAMPLES)
            val deadline = System.currentTimeMillis() + maxMs
            var lastPartial = ""
            while (!cancelled() && System.currentTimeMillis() < deadline) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) {
                    // `ERROR_INVALID_OPERATION`/`ERROR_DEAD_OBJECT`: micro bị một app khác giành mất giữa chừng.
                    // Trả phần đã nghe thay vì quay vòng bận — một vòng lặp nóng trên đầu xe là quạt kêu và pin tụt.
                    if (n < 0) { Log.w(TAG, "đọc micro trả $n — dừng phiên"); break }
                    continue
                }
                // Chép TRƯỚC khi giải mã: `accept` có thể chốt câu và thoát ngay ở dòng dưới.
                if (keepPcm && keptN < kept.size) {
                    val room = minOf(n, kept.size - keptN)
                    System.arraycopy(buf, 0, kept, keptN, room)
                    keptN += room
                }
                if (rec.accept(buf, n)) return Heard(rec.result(), kept, keptN)
                val p = rec.partial()
                if (p.isNotEmpty() && p != lastPartial) { lastPartial = p; onPartial(p) }
            }
            return Heard(rec.finalResult(), kept, keptN)
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
            tone(ToneGenerator.TONE_PROP_ACK, TONE_END_MS)
            abandonFocus(focus)
        }
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
        for (source in intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val r = runCatching {
                AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
            }.getOrNull() ?: continue
            if (r.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "micro mở bằng nguồn $source (đệm $size byte)")
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
