package com.byd.clusternav.launcher.voice

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/**
 * ═══ "Hey Kachi" — adapter KEYWORD-SPOTTER (sherpa-onnx `KeywordSpotter`) ═════════════════════════════════════
 *
 * Bọc engine KWS streaming của sherpa (đã [ĐO] có trong AAR v1.13.8 — cùng lib với ASR, không thêm dependency).
 * Chỉ trả `true` khi khung vừa khớp **câu gọi**; bộ nghe [VoiceWakeListener] gọi rất thường xuyên nên adapter giữ
 * đúng MỘT stream + tự `reset` sau mỗi lần khớp.
 *
 * ## Degrade-safe (owner chốt: chưa có model tiếng Việt; model KWS owner đăng OTA sau)
 * [build] trả **null** khi thiếu tệp model ⇒ bộ nghe chạy **chế độ chỉ-RMS** (đo được baseline CPU trên xe) mà
 * KHÔNG bắt được câu gọi. Không crash, không spam. Khi owner đăng model KWS lên repo OTA + tải về, adapter dựng
 * thật và bắt câu gọi.
 *
 * ## ⚠ Còn nợ DỮ LIỆU (không phải code): keywords-file phải là câu gọi ĐÃ TOKENIZE theo `tokens.txt` của model
 * (BPE). Việc tokenize cần bảng token của chính model ⇒ chốt **offline khi model được chọn** (`sherpa-onnx-cli
 * text2token`), rồi ghim chuỗi token vào [VoiceWakePhrase]/keywords-file. Tới lúc đó KWS không khớp gì (degrade).
 */
class VoiceWakeKws private constructor(private val spotter: KeywordSpotter, keywords: String?) : WakeEngine {

    private val stream: OnlineStream = spotter.createStream(keywords ?: "")
    private val lock = Any()

    /**
     * Đã nhả tài nguyên native chưa.
     *
     * `runCatching` **không** cứu được một lượt dùng-sau-khi-nhả ở tầng native: đó là SIGSEGV, giết cả tiến
     * trình. Cờ này là chốt duy nhất đứng giữa hai việc ấy, và nó ở trong cùng [lock] với [feed]/[release] nên
     * không cần nghĩ về thứ tự nhìn thấy giữa luồng nghe và luồng gọi `stop()`.
     */
    private var released = false

    /** Nạp PCM float [-1,1]; `true` nếu VỪA khớp câu gọi (đã `reset` để bắt lượt kế). Gọi trên luồng nghe. */
    override fun feed(pcm: FloatArray, n: Int): Boolean = synchronized(lock) {
        if (released) return false
        runCatching {
            stream.acceptWaveform(if (n == pcm.size) pcm else pcm.copyOf(n), SAMPLE_RATE)
            while (spotter.isReady(stream)) spotter.decode(stream)
            if (spotter.getResult(stream).keyword.isNotEmpty()) { spotter.reset(stream); true } else false
        }.getOrDefault(false)
    }

    override fun release(): Unit = synchronized(lock) {
        if (released) return@synchronized
        released = true
        runCatching { stream.release() }
        runCatching { spotter.release() }
    }

    companion object {
        private const val TAG = "WakeKws"
        const val SAMPLE_RATE = 16_000

        /**
         * Dựng KWS nếu đủ tệp model ở [dir] (encoder/decoder/joiner/tokens); thiếu bất kỳ ⇒ **null** (degrade →
         * chỉ-RMS). [keywords] = câu gọi ĐÃ tokenize (hoặc `null` để dùng keywords-file trong [dir]). 1 luồng để
         * ăn CPU tối thiểu — bộ nghe nền không được ngốn lõi.
         */
        fun build(
            dir: File,
            encoder: String, decoder: String, joiner: String, tokens: String,
            keywordsFileName: String? = null, keywords: String? = null,
        ): VoiceWakeKws? {
            val need = listOf(encoder, decoder, joiner, tokens).map { File(dir, it) }
            if (need.any { !it.exists() }) { Log.i(TAG, "thiếu model KWS ở $dir — chạy chế độ chỉ-RMS"); return null }
            // ⚠ KHÔNG có câu gọi nào ⇒ **degrade luôn**, đừng dựng engine.
            // Một spotter không có từ khoá nào vẫn chạy suy diễn trên mọi khung có giọng và **không bao giờ** khớp
            // được gì: đó là đốt CPU nền với xác suất thành công 0 — đúng thứ owner lo, mà lại im lặng. Ca này có
            // thật: tệp model tải xong nhưng `keywords.txt` (câu gọi đã tokenize) còn là nợ DỮ LIỆU — xem KDoc lớp.
            val kwFile = keywordsFileName?.let { File(dir, it) }?.takeIf { it.exists() }
            if (kwFile == null && keywords.isNullOrBlank()) {
                Log.i(TAG, "có model KWS nhưng chưa có câu gọi (keywords-file/chuỗi) — chạy chế độ chỉ-RMS")
                return null
            }
            return runCatching {
                val cfg = KeywordSpotterConfig().apply {
                    featConfig = FeatureConfig().apply { sampleRate = SAMPLE_RATE; featureDim = 80 }
                    modelConfig = OnlineModelConfig().apply {
                        transducer = OnlineTransducerModelConfig().apply {
                            this.encoder = File(dir, encoder).absolutePath
                            this.decoder = File(dir, decoder).absolutePath
                            this.joiner = File(dir, joiner).absolutePath
                        }
                        this.tokens = File(dir, tokens).absolutePath
                        numThreads = 1
                    }
                    kwFile?.let { keywordsFile = it.absolutePath }
                }
                val spotter = KeywordSpotter(assetManager = null, config = cfg)
                // `createStream` chạy trong hàm dựng của [VoiceWakeKws]; nó ném được (câu gọi sai token) và khi ấy
                // `spotter` native sẽ **rò** nếu không ai nhả. Trên một đầu xe 56–94 MB trống thì đó là rò thật.
                runCatching { VoiceWakeKws(spotter, keywords) }
                    .getOrElse { runCatching { spotter.release() }; throw it }
            }.getOrElse { Log.e(TAG, "dựng KWS lỗi", it); null }
        }
    }
}
