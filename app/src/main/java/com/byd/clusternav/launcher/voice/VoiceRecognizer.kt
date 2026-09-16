package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/**
 * ═══ V2 pha NGHE · BỘ NHẬN DẠNG — sherpa-onnx OfflineRecognizer, **TẠI MÁY**, GIẢI MÃ TỰ DO + BIASING ════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html`. Thay Vosk (`org.vosk`): Vosk ràng **ngữ pháp FST cứng** + mô hình
 * 32 MB ⇒ trên xe nói cả câu ra một từ. V2 dùng Zipformer-vi (transducer) giải mã **tự do**, rồi kéo về đúng tập
 * lệnh bằng **hotwords/contextual biasing** ([SherpaBiasing] + [SherpaHotwords]).
 *
 * ## Vì sao GIỮ nguyên `AudioRecord` của dự án ([VoiceCapture]), chỉ đổi lõi giải mã
 * Cùng lý do như bản Vosk: đường ghi âm phải nằm TRONG tệp `Voice*` để bài canh *"không tệp Voice\* nào ra
 * mạng"* + trần 8 giây + chốt tiêu điểm còn hiệu lực. sherpa cũng có lớp mic/VAD tự dựng `AudioRecord` — KHÔNG
 * dùng, vì lý do đó.
 *
 * ## OFFLINE, không streaming: [accept] GOM, [result] mới giải mã
 * `OfflineRecognizer` giải mã cả một khúc một lần (không có "chốt câu giữa dòng" như Vosk). Nên [accept] chỉ
 * **gom** PCM và luôn trả `false`; [VoiceCapture] chạy tới trần 8 s / người dùng thả tay rồi gọi [finalResult]
 * — chỗ giải mã thật sự xảy ra. [decodeAll] giải mã thẳng một khúc đã thu (lượt 2 tự do). Hợp đồng với
 * [VoiceCapture] không đổi một dòng. (Endpoint theo VAD của sherpa = hạng mục mở, xem spec §Open Questions.)
 *
 * ## Vòng đời: recognizer nạp MỘT lần cho cả tiến trình ([VoiceEngine])
 * Encoder ONNX (fp32) hàng trăm MB, dựng phiên mất vài giây ⇒ không nạp lại mỗi lần bấm mic. Biasing **động** đi
 * qua `createStream(hotwords)` per-phiên, KHÔNG phải dựng lại recognizer ([ĐO] off-car: per-stream hotwords ăn).
 */
class VoiceRecognizer private constructor(
    private val recognizer: OfflineRecognizer,
    /** Hotwords cho phiên này (HOA có dấu, một dòng một cụm). Rỗng ⇒ không biasing (lượt tự do / thiếu bpe vocab). */
    private val hotwords: String,
) : AutoCloseable {

    // Gom PCM giữa các [accept]; giải mã một lần ở [result]/[finalResult]. Trần ~10 s để chặn rò bộ nhớ.
    private val buffer = ShortArray(MAX_SAMPLES)
    private var filled = 0

    /**
     * Gom một khối PCM 16-bit mono 16 kHz. **Luôn** trả `false`: mô hình offline không chốt câu giữa dòng —
     * [VoiceCapture] dừng theo trần thời gian / người dùng thả tay rồi lấy [finalResult].
     */
    fun accept(buffer: ShortArray, length: Int): Boolean {
        if (filled >= this.buffer.size) return false
        val room = minOf(length, this.buffer.size - filled)
        System.arraycopy(buffer, 0, this.buffer, filled, room)
        filled += room
        return false
    }

    /** Không có "chữ đang nghe dở" ở mô hình offline — overlay chỉ hiện trạng thái đang nghe. */
    fun partial(): String = ""

    /** Chữ của khúc đã gom. */
    fun result(): String = decode(buffer, filled)

    /** Giống [result] — mô hình offline chỉ có một kết quả cuối. */
    fun finalResult(): String = decode(buffer, filled)

    /**
     * Giải mã **cả một khúc PCM đã thu sẵn** và trả chữ (thường hoá). Dùng cho lượt 2 của [VoiceOpenVocab].
     * **CHẶN** ⇒ luồng nền.
     */
    fun decodeAll(pcm: ShortArray, length: Int): String = decode(pcm, length)

    /**
     * Một lượt giải mã: PCM16 → float [-1,1) → stream (+hotwords nếu có) → text.
     *
     * Mô hình VN xuất **CHỮ HOA CÓ DẤU**; [VoiceIntentParser] làm việc trên chữ **thường đã bỏ dấu** — nên hạ
     * chữ ở đây, giữ đúng hợp đồng chuỗi mà tầng chữ (Vosk trước đây) vẫn nhận.
     */
    private fun decode(pcm: ShortArray, length: Int): String {
        if (length <= 0) return ""
        val n = minOf(length, pcm.size)
        val samples = FloatArray(n) { pcm[it] / 32768f }
        return runCatching {
            // [ĐO cần trên xe] spec `kachi-voice-hotword-phrases` R-nf1/OQ2(b): dựng đồ thị hotword (1902 cụm) mỗi
            // phiên — host 6,6 ms, ngân sách xe ≤ 150 ms. Mốc giờ này là số duy nhất để chốt, đọc qua logcat tag này.
            val t0 = System.currentTimeMillis()
            val stream = if (hotwords.isNotEmpty()) recognizer.createStream(hotwords) else recognizer.createStream()
            if (hotwords.isNotEmpty()) Log.i(TAG, "createStream(hotwords) ${System.currentTimeMillis() - t0} ms")
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE_INT)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim().lowercase()
                    // [ĐO bug voice 2026-09-15] Chữ THÔ sherpa trả về (kể cả rỗng) — chốt "engine ra gì" trên xe:
                    // rỗng khi mức micro có tiếng ⇒ lỗi âm thanh/định dạng, không phải NLU; có chữ nhưng sai ⇒ NLU/ngữ pháp.
                    .also { Log.i(TAG, "sherpa ra: \"$it\" ($n mẫu)") }
            } finally {
                runCatching { stream.release() }
            }
        }.onFailure { Log.w(TAG, "giải mã hỏng", it) }.getOrDefault("")
    }

    /** Đóng phiên — chỉ quên khúc PCM; KHÔNG đóng recognizer chung (nó sống cả tiến trình, [VoiceEngine] giữ). */
    override fun close() { filled = 0 }

    companion object {
        private const val TAG = "KachiVoiceRec"

        /** Tần số lấy mẫu — cùng số với [VoiceCapture.SAMPLE_RATE] và mô hình (fbank 16 kHz). */
        const val SAMPLE_RATE = 16_000f
        private const val SAMPLE_RATE_INT = 16_000

        /** Trần khúc gom: 10 s @16 kHz. Dài hơn trần một phiên (8 s) để không cắt cụt câu cuối. */
        private const val MAX_SAMPLES = SAMPLE_RATE_INT * 10

        /**
         * Mở phiên nhận dạng RÀNG lệnh, hoặc `null` nếu mô hình chưa sẵn sàng. **CHẶN** ⇒ luồng nền.
         *
         * Biasing lấy từ **tập CỤM LỆNH tĩnh** ([SherpaBiasing], spec `kachi-voice-hotword-phrases.html`: cụm
         * ≥ 2 từ sinh từ 4 bộ đăng ký, không dòng một từ) — tên hồ sơ/app KHÔNG bias (mô hình VN không phát ra
         * token tiếng Anh; [VoiceIntentParser] khớp nhãn app lo). Chỉ bias khi engine có bpe vocab.
         */
        @Suppress("UNUSED_PARAMETER")
        fun open(
            ctx: Context,
            profiles: List<String>,
            apps: List<String>,
            installed: Set<String> = emptySet(),
            /**
             * Nhãn **sổ địa chỉ** của hồ sơ đang dùng (spec `kachi-voice-addresses.html` R6) — KHÁC tên hồ sơ/app
             * ở trên: chúng là tiếng Việt đời thường (*"Nhà"*, *"Công ty"*) nên biasing kéo về được. Xem KDoc
             * [SherpaBiasing.hotwordsFile].
             */
            places: List<String> = emptyList(),
        ): VoiceRecognizer? {
            val rec = VoiceEngine.recognizer(ctx) ?: return null
            val hot = if (VoiceEngine.biasingReady()) SherpaBiasing.hotwordsFile(places) else ""
            return VoiceRecognizer(rec, hot)
        }

        /**
         * Bộ nhận dạng **TỰ DO** (không biasing) — cho LƯỢT 2 ([VoiceOpenVocab]): đọc tên bài/điểm đến, thứ
         * không nằm trong tập đóng. Cùng recognizer chung, chỉ khác: không truyền hotwords.
         */
        fun openFree(ctx: Context): VoiceRecognizer? {
            val rec = VoiceEngine.recognizer(ctx) ?: return null
            return VoiceRecognizer(rec, "")
        }
    }
}

/**
 * Giữ [OfflineRecognizer] cho cả tiến trình — encoder ONNX nặng, dựng vài giây, không nạp lại mỗi phiên.
 *
 * `@Volatile` + `synchronized`: hai lối vào mic có thể bấm gần nhau. Recognizer dựng cho **một** model id; đổi
 * lựa chọn A/B ([VoiceModelStore.select]) ⇒ [release] rồi lần sau dựng lại bản mới.
 */
object VoiceEngine {

    private const val TAG = "KachiVoiceEngine"

    @Volatile private var recognizer: OfflineRecognizer? = null
    @Volatile private var builtFor: String? = null
    @Volatile private var biasing = false

    /** Recognizer cho model đang chọn, nạp nếu chưa / dựng lại nếu đổi model. `null` = chưa cài / hỏng. */
    fun recognizer(ctx: Context): OfflineRecognizer? {
        val model = VoiceModelStore.selected(ctx)
        recognizer?.let { if (builtFor == model.id) return it }
        return synchronized(this) {
            recognizer?.let { if (builtFor == model.id) return it else release() }
            build(ctx.applicationContext, model)?.also { recognizer = it; builtFor = model.id }
        }
    }

    /** Engine hiện tại có bật được biasing không (đã nạp bpe vocab). Đọc sau [recognizer]. */
    fun biasingReady(): Boolean = biasing

    /** Trả recognizer về hệ thống — gọi khi người dùng **gỡ** / **đổi** mô hình. */
    fun release() = synchronized(this) {
        recognizer?.let { runCatching { it.release() }.onFailure { t -> Log.w(TAG, "đóng recognizer hỏng", t) } }
        recognizer = null; builtFor = null; biasing = false
    }

    /** Mô hình đang nằm sẵn trong bộ nhớ chưa (để Cài đặt nói *"lần nói đầu sẽ hơi chậm"*). */
    fun loaded(): Boolean = recognizer != null

    private fun build(ctx: Context, model: SherpaModelCatalog.SherpaModel): OfflineRecognizer? {
        if (!VoiceModelStore.isReady(ctx)) return null
        val dir = VoiceModelStore.dir(ctx)
        val t0 = System.currentTimeMillis()
        val bpeVocabPath = copyBpeVocabAsset(ctx, model)
        biasing = bpeVocabPath.isNotEmpty()

        val transducer = OfflineTransducerModelConfig().apply {
            encoder = File(dir, model.encoder).absolutePath
            decoder = File(dir, model.decoder).absolutePath
            joiner = File(dir, model.joiner).absolutePath
        }
        val mc = OfflineModelConfig().apply {
            this.transducer = transducer
            tokens = File(dir, model.tokens).absolutePath
            numThreads = 2
            debug = false
            provider = "cpu"
            if (biasing) { modelingUnit = SherpaModelCatalog.MODELING_UNIT; bpeVocab = bpeVocabPath }
        }
        val config = OfflineRecognizerConfig().apply {
            featConfig = FeatureConfig().apply {
                sampleRate = 16_000   // fbank 16 kHz, 80 chiều — cùng số với mô hình VN
                featureDim = 80
            }
            modelConfig = mc
            decodingMethod = model.decodingMethod
            hotwordsScore = SherpaModelCatalog.HOTWORDS_SCORE
            maxActivePaths = 4
        }
        return runCatching { OfflineRecognizer(assetManager = null, config = config) }
            .onSuccess { Log.i(TAG, "nạp sherpa ${model.id} trong ${System.currentTimeMillis() - t0} ms (biasing=$biasing)") }
            .onFailure { Log.e(TAG, "không nạp được sherpa ${model.id}", it) }
            .getOrNull()
    }

    /**
     * Chép bảng BPE piece+score (asset `voice/<id>.bpe_vocab.txt`) vào `filesDir` để dùng làm `bpeVocab`.
     *
     * ⚠ [ĐO] off-car 2026-09-14: sherpa **KHÔNG** nhận thẳng `bpe.model` (sentencepiece nhị phân) làm `bpeVocab`
     * ("Each line should contain two items") — phải là bảng **piece score** xuất bằng sentencepiece lúc build.
     * Bảng này đóng theo APK (nhỏ ~55 KB), không tải mạng. Không có asset ⇒ trả "" ⇒ chạy không biasing.
     */
    private fun copyBpeVocabAsset(ctx: Context, model: SherpaModelCatalog.SherpaModel): String {
        val assetName = "voice/${model.id}.bpe_vocab.txt"
        val dest = File(VoiceModelStore.dir(ctx), "bpe_vocab.txt")
        return runCatching {
            if (!dest.isFile || dest.length() == 0L) {
                dest.parentFile?.mkdirs()
                ctx.assets.open(assetName).use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
            dest.absolutePath
        }.onFailure { Log.i(TAG, "không có bpe vocab cho ${model.id} — chạy không biasing") }.getOrDefault("")
    }
}
