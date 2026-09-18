package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.voiceVadMinSilenceMs
import com.byd.clusternav.voiceVadMinSpeechMs
import com.byd.clusternav.voiceVadThreshold
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * ═══ NGẮT CÂU BẰNG **Silero VAD** — cái đồng hồ của một lượt nghe ════════════════════════════════════════════
 *
 * Bằng chứng: `docs/diagnostics/voice-stream-eval-2026-09-16.md` §5 · §6 · §8.
 * Số học cắt cửa sổ nằm ở `:core` ([VoiceVadTrim]); tệp này **chỉ** là lớp bọc quanh ONNX.
 *
 * ## Vì sao thay bộ RMS ([VoiceEndpointer]) làm đường CHÍNH
 * [ĐO xe 2026-09-16] bộ RMS trên xe thật **gần như không bao giờ nổ**: `chot=4200ms` ở 165/299 lượt, có lượt
 * 8 400 ms — tức mọi câu đều trả giá bằng trọn cái trần. [ĐO host §5] cùng corpus 1 899 câu, Silero với bộ tham
 * số đã chốt cho **p50 660 ms · p90 780 ms · 0/1 899 cắt giữa câu · 0/1 899 không nổ**. ⚠ Hai con số endpoint ấy
 * đo với `min_silence = 0,15 s`; từ [ĐO xe 2026-09-18] núm ấy là **0,60 s** ⇒ cộng ~450 ms (xem
 * [VoiceVadTrim.MIN_SILENCE_MS] — vẫn nhanh hơn bộ RMS một bậc).
 *
 * Và quan trọng hơn cả tốc độ — §6: đuôi im lặng **phá độ chính xác** (22/25 → 6/25 khi nối thêm 4 s im lặng vào
 * chính mô hình đang ship). Cắt cửa sổ ở điểm hết tiếng là phép sửa **độ chính xác**, không phải phép tối ưu độ
 * trễ. Lý do đầy đủ + bảng số ở KDoc [VoiceVadTrim].
 *
 * ## ⚠ KDoc cũ của [VoiceEndpointer] nói *"KHÔNG dùng VAD của sherpa"* — vì sao lập luận ấy đổ
 * Lập luận cũ: *"nó là một mô hình ONNX thứ hai phải tải + nạp + chạy trên cùng cái CPU đang chật"*. Hai vế của
 * nó đều sai với thực tế đã đo:
 *  • **"phải tải"** — không: tệp **0,64 MB** và nay đóng **thẳng trong APK** (xem [ASSET_NAME]), không có lượt
 *    tải nào. Đúng ca quan trọng nhất (xe không internet) thì nó vẫn có mặt.
 *  • **"trên cùng cái CPU đang chật"** — đúng, nhưng nó **đổi lại** 2–3 giây giải mã thừa **mỗi lượt**: cửa sổ
 *    nạp vào mô hình ngắn đi đúng phần đuôi không có tiếng. Tức nó là một phép **tiết kiệm** CPU ròng, không
 *    phải một khoản chi thêm.
 *
 * Vế còn đúng của KDoc ấy — *"câu hỏi cần trả lời chỉ là người ta còn đang nói không"* — vẫn nguyên, và [ĐO] cho
 * thấy mức năng lượng **không** trả lời được nó trên cabin thật. Bộ RMS ở lại làm **đường lùi** (xem KDoc mới ở
 * đó), không bị gỡ.
 *
 * ## Gói mô hình: ASSET trong APK, không qua [VoiceModelStore]
 *  • `app/src/main/assets/voice/silero_vad.onnx` — **643 854 byte**,
 *    sha256 `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`,
 *    nguồn `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx`, giấy phép MIT.
 *  • **Không có phép kiểm sha lúc chạy**, và đó là đúng: chữ ký APK đã bảo chứng cho asset. [VoiceModelStore] băm
 *    các gói của nó vì chúng **tải qua mạng**; thứ đóng trong APK thì không có cửa nào để bị thay. Ghi sha ở đây
 *    là để **truy vết** khi ai đó đổi tệp, không phải để kiểm lúc chạy.
 *  • APK to thêm 0,64 MB — **cố ý**. Một VAD chỉ hoạt động sau khi tải xong là một VAD vắng mặt đúng lúc cần nó
 *    (xe không internet — cùng lý do đã chốt đường side-load cho mô hình nghe).
 *
 * ⚠ Dựng ONNX **chặn** vài chục tới vài trăm ms ⇒ [open] gọi trên luồng nền (chỗ gọi: [VoiceCapture.listen],
 * vốn đã ở luồng nền).
 */
internal class VoiceVad private constructor(
    private val vad: Vad,
    /** Ba tham số đang áp — in ra nhật ký để một lượt đo đọc được *"đặt bao nhiêu"* mà không phải hỏi prefs. */
    val threshold: Float,
    val minSpeechMs: Int,
    val minSilenceMs: Int,
    /** B1.1: `true` = instance dùng CHUNG holder (close ⇒ trả pool); `false` = dựng riêng (close ⇒ release ONNX). */
    private val shared: Boolean = false,
) : AutoCloseable {

    /** Các đoạn tiếng đã chốt trong lượt này, theo thứ tự VAD trả ra. */
    private val segments = ArrayList<VoiceVadTrim.Segment>(4)

    /** Tổng số mẫu đã đẩy vào — để đổi mốc mẫu ↔ mili-giây trong nhật ký. */
    var fedSamples: Int = 0
        private set

    /**
     * Đẩy một khối PCM16 vào VAD. Trả `true` khi **vừa có ít nhất một đoạn được chốt** ⇒ đó là điểm ngắt câu.
     *
     * Chuyển PCM16 → float [-1,1) đúng cùng phép chia `32768f` mà [VoiceRecognizer.decode] dùng: hai đường nghe
     * cùng một khúc tiếng thì phải nghe **cùng một biên độ**, nếu không ngưỡng 0.5 của Silero nói về một tín hiệu
     * khác với thứ mô hình nhận.
     */
    fun accept(pcm: ShortArray, n: Int): Boolean {
        val f = FloatArray(n) { pcm[it] / 32768f }
        fedSamples += n
        runCatching { vad.acceptWaveform(f) }
            .onFailure { Log.w(TAG, "VAD acceptWaveform hỏng — lượt này chạy như không có VAD", it); return false }
        var closed = false
        // `empty()`/`front()`/`pop()` là một HÀNG ĐỢI: một khối 200 ms có thể chốt nhiều hơn một đoạn. Rút hết
        // mỗi lần, đừng chỉ lấy `front` — xem KDoc [VoiceVadTrim.headTrimSamples] ca nhiều đoạn.
        while (!runCatching { vad.empty() }.getOrDefault(true)) {
            val seg = runCatching { vad.front() }.getOrNull() ?: break
            segments += VoiceVadTrim.Segment(seg.start, seg.samples.size)
            runCatching { vad.pop() }
            closed = true
        }
        return closed
    }

    /**
     * Chốt nốt đoạn đang mở (người nói tới sát trần cứng) rồi rút hàng đợi.
     *
     * Không có bước này thì một câu chạm trần 8,4 s **không có đoạn nào** ⇒ [sawSpeech] trả `false` ⇒ lượt ấy bị
     * bỏ giải mã như một lượt im lặng. Đó là ca *"người ta nói dài"*, không phải ca *"không ai nói"* — hai thứ
     * ngược nhau mà lại rơi vào cùng một nhánh nếu quên `flush`.
     */
    fun flush() {
        runCatching { vad.flush() }.onFailure { Log.w(TAG, "VAD flush hỏng", it); return }
        while (!runCatching { vad.empty() }.getOrDefault(true)) {
            val seg = runCatching { vad.front() }.getOrNull() ?: break
            segments += VoiceVadTrim.Segment(seg.start, seg.samples.size)
            runCatching { vad.pop() }
        }
    }

    /** Lượt này đã chốt được đoạn tiếng nào chưa — thay cho `VoiceEndpointer.sawSpeech()` ở đường VAD. */
    fun sawSpeech(): Boolean = segments.isNotEmpty()

    /** Số mẫu đưa vào bộ giải mã theo chế độ `head` — xem [VoiceVadTrim.headTrimSamples]. */
    fun headTrimSamples(windowSamples: Int): Int =
        VoiceVadTrim.headTrimSamples(segments, windowSamples, VoiceVadTrim.msToSamples(VoiceVadTrim.MARGIN_MS, RATE))

    /** Mốc bắt đầu / kết thúc tiếng (ms) — hai trong ba con số mà `KachiVoiceTiming` phải in. */
    fun speechStartMs(): Int =
        segments.minOfOrNull { VoiceVadTrim.samplesToMs(it.startSample, RATE) } ?: -1

    fun speechEndMs(): Int =
        segments.maxOfOrNull { VoiceVadTrim.samplesToMs(it.endSample, RATE) } ?: -1

    /** Một dòng nhật ký cho `KachiVoiceTiming` — mốc giờ của chính lượt này, không phải một lời kể. */
    fun summary(windowSamples: Int): String {
        val trim = headTrimSamples(windowSamples)
        return "vad doan=${segments.size} tieng_bat_dau=${speechStartMs()}ms tieng_dut=${speechEndMs()}ms " +
            "cua_so=${VoiceVadTrim.samplesToMs(windowSamples, RATE)}ms cat_con=${VoiceVadTrim.samplesToMs(trim, RATE)}ms " +
            "(nguong=$threshold · toi_thieu_tieng=${minSpeechMs}ms · nguong_im=${minSilenceMs}ms)"
    }

    override fun close() {
        // B1.1: instance dùng CHUNG chỉ trả holder về pool (giữ ONNX sống cho lượt sau); instance dựng RIÊNG thì
        // release ONNX như cũ. Trong cả hai ca, `segments` là của instance này nên không cần dọn — instance rời đi.
        if (shared) releaseShared()
        else runCatching { vad.release() }.onFailure { Log.w(TAG, "đóng VAD hỏng", it) }
    }

    companion object {
        private const val TAG = "KachiVoiceVad"

        /** 16 kHz — cùng số với [VoiceCapture.SAMPLE_RATE] và với mô hình nghe. */
        private const val RATE = 16_000

        /**
         * Đường dẫn asset của mô hình VAD — **tương đối trong `assets/`**, đúng dạng `Vad(ctx.assets, …)` nhận.
         *
         * Cùng thư mục và cùng lối với `voice/zipformer-vi-2025-04-20.bpe_vocab.txt`: tệp nhỏ thì đi theo APK,
         * tệp hàng trăm MB thì đi qua [VoiceModelStore]. Xem KDoc lớp về sha256 + nguồn + giấy phép.
         */
        const val ASSET_NAME = "voice/silero_vad.onnx"

        // ═══ B1.1 (1.70) — HÂM SẴN + DÙNG LẠI một Vad cho cả tiến trình ══════════════════════════════════════
        // [ĐO xe 2026-09-17] "bấm → mic mở" mất 1,5 s ở lượt đầu; một phần là dựng ONNX của Silero **mỗi lượt**
        // (`Vad(assets, config)` nạp lại đồ thị 0,64 MB). Silero có `reset()`/`clear()` (javap AAR v1.13.8) ⇒ giữ
        // MỘT instance sống, mỗi lượt chỉ reset — bỏ hẳn phần nạp ONNX khỏi đường "nói → nghe liền".
        //
        // Vì sao khoá theo BỘ THAM SỐ: ba núm (threshold/minSpeech/minSilence) chỉnh được trên xe giữa hai lượt
        // nói (`prefs_set`). Tham số nằm trong `VadModelConfig` lúc dựng, `reset()` KHÔNG đổi chúng ⇒ nếu người
        // đo vừa đổi núm thì phải dựng lại. Cùng pattern `VoiceEngine.recognizer(builtFor)`.
        //
        // Thread: một lượt nghe một lúc (VoiceSingleFlight), nhưng `VoiceWavProbe` (host) + preload có thể chạm
        // song song ⇒ `synchronized(lock)`. Instance đang cho mượn thì lượt kia dựng RIÊNG (không tranh reset).
        private val lock = Any()
        @Volatile private var holder: Vad? = null
        private var holderKey: String = ""
        @Volatile private var inUse = false

        private fun keyOf(t: Float, sp: Int, si: Int) = "$t/$sp/$si"

        /**
         * Hâm sẵn Vad trên luồng nền — gọi từ [com.byd.clusternav.KachiApplication] cùng [VoiceEngine.preload].
         * Không ném, không chặn caller. Đọc prefs mặc định (người chưa chỉnh núm) để dựng đúng bộ hay dùng nhất.
         */
        fun preload(ctx: Context) {
            val app = ctx.applicationContext
            Thread({
                runCatching {
                    val t = runCatching { Prefs.voiceVadThreshold(app) }.getOrDefault(VoiceVadTrim.THRESHOLD)
                    val sp = runCatching { Prefs.voiceVadMinSpeechMs(app) }.getOrDefault(VoiceVadTrim.MIN_SPEECH_MS)
                    val si = runCatching { Prefs.voiceVadMinSilenceMs(app) }.getOrDefault(VoiceVadTrim.MIN_SILENCE_MS)
                    synchronized(lock) {
                        if (holder == null) holder = build(app, t, sp, si)?.also { holderKey = keyOf(t, sp, si) }
                    }
                }.onFailure { Log.w(TAG, "hâm sẵn VAD hỏng — lần nghe đầu sẽ dựng như cũ", it) }
            }, "KachiVadPreload").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
        }

        private fun build(app: Context, t: Float, sp: Int, si: Int): Vad? {
            val config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = ASSET_NAME,
                    threshold = t,
                    minSilenceDuration = VoiceVadTrim.msToSeconds(si),
                    minSpeechDuration = VoiceVadTrim.msToSeconds(sp),
                    windowSize = VoiceVadTrim.WINDOW_SIZE,
                ),
                sampleRate = RATE, numThreads = 1, provider = "cpu", debug = false,
            )
            return runCatching { Vad(app.assets, config) }
                .onFailure { Log.w(TAG, "không dựng được Silero VAD", it) }.getOrNull()
        }

        /**
         * Dựng VAD cho MỘT lượt nghe, hoặc `null` khi không dựng được (asset thiếu, ONNX từ chối, ROM lạ).
         *
         * B1.1: ưu tiên **dùng lại** holder đã hâm (chỉ `reset()`, ~0 ms) khi bộ tham số khớp và không có lượt
         * khác đang mượn; nếu tham số đổi / holder chưa có / đang bận thì dựng riêng như cũ. Lượt dùng lại holder
         * KHÔNG `close()` nó (trả về pool ở [releaseShared]); lượt dựng riêng thì `close()` bình thường.
         *
         * `null` **không phải lỗi phải báo cho người lái**: chỗ gọi lùi về [VoiceEndpointer] (bộ RMS) và lượt
         * nghe vẫn chạy — kém hơn, nhưng chạy (cùng luật `VoiceSession.runSession`).
         */
        fun open(ctx: Context): VoiceVad? {
            val app = ctx.applicationContext
            val t = runCatching { Prefs.voiceVadThreshold(app) }.getOrDefault(VoiceVadTrim.THRESHOLD)
            val sp = runCatching { Prefs.voiceVadMinSpeechMs(app) }.getOrDefault(VoiceVadTrim.MIN_SPEECH_MS)
            val si = runCatching { Prefs.voiceVadMinSilenceMs(app) }.getOrDefault(VoiceVadTrim.MIN_SILENCE_MS)
            val key = keyOf(t, sp, si)
            synchronized(lock) {
                val h = holder
                if (h != null && holderKey == key && !inUse) {
                    inUse = true
                    runCatching { h.reset() }.onFailure {
                        // reset hỏng ⇒ vứt holder, để lượt này dựng riêng — không kẹt `inUse`.
                        runCatching { h.release() }; holder = null; inUse = false
                    }
                    if (holder != null) return VoiceVad(h, t, sp, si, shared = true)
                }
                // Tham số đổi ⇒ thay holder mới (đóng cái cũ nếu không ai mượn).
                if (h != null && holderKey != key && !inUse) {
                    runCatching { h.release() }; holder = build(app, t, sp, si)?.also { holderKey = key }
                    holder?.let { inUse = true; return VoiceVad(it, t, sp, si, shared = true) }
                }
            }
            // Holder chưa có / đang bận / dựng lại hỏng ⇒ dựng RIÊNG cho lượt này (close bình thường).
            val own = build(app, t, sp, si)
            if (own == null) Log.w(TAG, "không dựng được Silero VAD — lùi về bộ ngắt câu RMS")
            return own?.let { VoiceVad(it, t, sp, si, shared = false) }
        }

        /** Trả holder về pool (chỉ gọi cho instance `shared`). */
        private fun releaseShared() = synchronized(lock) { inUse = false }
    }
}
