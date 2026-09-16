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

    /**
     * V3 · R3 — tag DUY NHẤT cho mọi mốc giờ của đường giọng nói (`adb logcat -s KachiVoiceTiming`).
     *
     * Một tag riêng, không trộn vào [TAG]: [ĐO xe 2026-09-16] việc đo *"3,1 giây biến đi đâu"* phải lọc thủ công
     * bốn tag khác nhau trên một máy đang chạy launcher + nav + cast. Một tag thì một lệnh `logcat` là ra cả
     * chuỗi mốc, theo đúng thứ tự thật.
     */
    const val TIMING_TAG = "KachiVoiceTiming"

    /** Hoãn trước khi nạp sẵn — xem KDoc [preload], tính chất (2). */
    const val PRELOAD_DELAY_MS = 3_000L

    private const val MIN_THREADS = 2
    private const val MAX_THREADS = 4

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

    /**
     * ═══ V3 · R4 — NẠP SẴN mô hình, **trên luồng nền, ưu tiên thấp** ═══════════════════════════════════════
     *
     * Spec `docs/specs/kachi-voice-fast-natural.html` R4. [ĐO xe 2026-09-16] lượt 09:30: **15 giây** từ lúc bấm
     * phím tới lúc micro mở, và đó là *lần đầu sau khi mở app* — các lượt sau 0,2 s. Tức cái giá 15 s không
     * thuộc về việc nghe, nó thuộc về việc **nạp encoder ONNX**, và nó rơi đúng vào lần người ta dùng thử đầu
     * tiên (lần quyết định họ có dùng tiếp không).
     *
     * ## Ba tính chất, mỗi cái chữa một ca hỏng
     *  1. **Luồng nền, ưu tiên thấp** — nạp mô hình ăn CPU hàng giây; chạy nó ở ưu tiên thường trong lúc launcher
     *     đang dựng màn chính là đổi 15 s chờ mic lấy 15 s giật màn hình.
     *  2. **Hoãn [PRELOAD_DELAY_MS]** — để lượt dựng màn chính, đo ô và mở app trong ô xong đã. Nạp ngay trong
     *     `onCreate` là tranh CPU với đúng thứ người dùng đang nhìn.
     *  3. **Không ném, không chặn** — chưa tải mô hình / máy hết RAM ⇒ [recognizer] trả `null` và đây im lặng rút
     *     lui. Một tính năng phụ không được giết launcher (cùng luật `VoiceSession.runSession`).
     *
     * An toàn khi gọi nhiều lần: [recognizer] tự khoá `synchronized` và tự nhận ra mô hình đã nạp.
     */
    fun preload(ctx: Context, delayMs: Long = PRELOAD_DELAY_MS) {
        val app = ctx.applicationContext
        Thread({
            runCatching {
                if (delayMs > 0) Thread.sleep(delayMs)
                if (!VoiceModelStore.isReady(app)) {
                    Log.i(TAG, "nạp sẵn: chưa có mô hình trên đĩa — bỏ qua")
                    return@runCatching
                }
                // H6 (PERF 2026-09-16) — hỏi RAM CÒN LẠI trước khi nạp thêm vài trăm MB. Quyết định thuần nằm ở
                // [VoicePreloadPolicy]; ở đây chỉ đọc số của hệ thống và ghi lý do (không im lặng bỏ qua).
                val mem = android.app.ActivityManager.MemoryInfo().also { mi ->
                    (app.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
                        ?.getMemoryInfo(mi)
                }
                val bytes = runCatching { VoiceModelStore.selected(app).totalBytes }.getOrDefault(0L)
                if (!VoicePreloadPolicy.shouldPreload(mem.availMem, mem.lowMemory, bytes)) {
                    Log.i(TAG, "nạp sẵn: BỎ QUA — ${VoicePreloadPolicy.reason(mem.availMem, mem.lowMemory, bytes)}" +
                        "; lần bấm mic đầu sẽ nạp như cũ")
                    return@runCatching
                }
                val t0 = System.currentTimeMillis()
                val ok = recognizer(app) != null
                Log.i(TIMING_TAG, "nạp sẵn mô hình ${System.currentTimeMillis() - t0} ms (ok=$ok)")
            }.onFailure { Log.w(TAG, "nạp sẵn hỏng — lần bấm mic đầu sẽ nạp như cũ", it) }
        }, "KachiVoicePreload").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }.start()
    }

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
            // ═══ V3 · R5 — số LUỒNG giải mã theo máy, không phải hằng 2 ═══════════════════════════════
            // [ĐO xe 2026-09-16] Qualcomm TRINKET **8 lõi** 1,8 GHz, mà giải mã một câu 8 s mất **2,35 s** với
            // `numThreads = 2` viết cứng. Nửa số lõi là mức mà onnxruntime còn nở tuyến tính; kẹp trần 4 vì
            // đây là launcher — 8 luồng giải mã ăn hết CPU của chính màn hình người lái đang nhìn, và lõi
            // nhỏ (big.LITTLE) không cho thêm gì. Sàn 2 giữ nguyên hành vi cũ trên máy 2-4 lõi.
            numThreads = threadsForDecode()
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
            .onSuccess {
                Log.i(
                    TIMING_TAG,
                    "nạp sherpa ${model.id} trong ${System.currentTimeMillis() - t0} ms " +
                        "(biasing=$biasing · luồng=${mc.numThreads} · lõi=${Runtime.getRuntime().availableProcessors()})",
                )
            }
            .onFailure { Log.e(TAG, "không nạp được sherpa ${model.id}", it) }
            .getOrNull()
    }

    /** `availableProcessors / 2`, kẹp [2, 4] — xem chú thích tại chỗ dùng. Tách hàm để đọc được trong nhật ký. */
    private fun threadsForDecode(): Int =
        (Runtime.getRuntime().availableProcessors() / 2).coerceIn(MIN_THREADS, MAX_THREADS)

    /**
     * Chép bảng BPE piece+score (asset `voice/<id>.bpe_vocab.txt`) vào `filesDir` để dùng làm `bpeVocab`.
     *
     * ⚠ [ĐO] off-car 2026-09-14: sherpa **KHÔNG** nhận thẳng `bpe.model` (sentencepiece nhị phân) làm `bpeVocab`
     * ("Each line should contain two items") — phải là bảng **piece score** xuất bằng sentencepiece lúc build.
     * Bảng này đóng theo APK (nhỏ ~55 KB), không tải mạng. Không có asset ⇒ trả "" ⇒ chạy không biasing.
     */
    private fun copyBpeVocabAsset(ctx: Context, model: SherpaModelCatalog.SherpaModel): String {
        // ⚠ Tên asset lấy từ [SherpaModelCatalog.SherpaModel.bpeVocab], **không** từ `model.id`: hai mô hình cùng
        // một bản huấn luyện (fp32 / int8) dùng CHUNG một bảng BPE, và đóng hai bản 55 KB giống hệt vào APK là
        // dựng bản sao thứ hai của cùng một bảng. Trước 1.66 trường `bpeVocab` tồn tại mà không ai đọc.
        val assetName = "voice/${model.bpeVocab}"
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
