package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/**
 * ═══ V1 pha NGHE · BỘ NHẬN DẠNG — VOSK, **TẠI MÁY**, RÀNG BẰNG NGỮ PHÁP ══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R10 · R11**. Thư viện: `com.alphacephei:vosk-android:0.3.47`
 * (bản ổn định mới nhất, kiểm 2026-09-14 — xem ghi chú version trong `app/build.gradle.kts`).
 *
 * ## Vì sao KHÔNG dùng `SpeechService`/`SpeechStreamService` của chính gói Vosk
 * Hai lớp đó tự dựng `AudioRecord` + luồng riêng bên trong thư viện. Nghĩa là đường ghi âm nằm **ngoài** tệp
 * `Voice*` của dự án ⇒ nằm ngoài tầm bài canh *"không tệp Voice\* nào mở socket"*, ngoài trần 8 giây, và ngoài
 * chốt tiêu điểm âm thanh. Trên một chiếc xe đang chạy, *"micro đang bật tới bao giờ"* phải là câu trả lời được
 * bằng cách đọc mã của dự án, không phải bằng cách đọc mã của thư viện. Nên Kachi giữ `AudioRecord` cho mình
 * ([VoiceSession]) và chỉ dùng đúng phần lõi: [Model] + [Recognizer].
 *
 * ## Ngữ pháp, không phải từ vựng mở
 * `Recognizer(model, 16000f, grammarJson)` ràng bộ giải mã vào **tập đóng** của Kachi (xem [VoicePhrases]).
 * Ba cái được, tất cả đều đo được:
 *  • **đúng hơn** — mô hình 32 MB giải mã tự do trên tiếng Việt có WER 15,7 % (README của mô hình); ràng vào
 *    ~2.000 mục thì không gian tìm kiếm nhỏ hơn hàng nghìn lần;
 *  • **nhanh hơn** — LM bigram dựng từ danh sách, không phải LM 19.529 từ;
 *  • **an toàn hơn** — thứ ngoài tập đóng rơi vào `[unk]` rồi thành *"không hiểu"*, thay vì bị **ép** thành một
 *    câu lệnh gần giống. Trên xe, "không hiểu" là câu trả lời đúng; "đoán bừa rồi mở khoá" thì không.
 *
 * ## Vòng đời: mô hình nạp MỘT lần cho cả tiến trình
 * [Model] mmap ~53 MB và mất vài giây để dựng đồ thị. Nạp lại ở mỗi lần bấm mic là mỗi lần bấm mic chờ vài
 * giây — tức tính năng coi như hỏng. [VoiceEngine] giữ nó; [Recognizer] thì dựng mới mỗi phiên vì ngữ pháp phụ
 * thuộc **danh sách động** (hồ sơ tài xế, app đã cài) và danh sách ấy đổi được giữa hai lần nói.
 */
class VoiceRecognizer private constructor(
    private val recognizer: Recognizer,
    /** Ngữ pháp đang áp — phơi ra để màn Cài đặt/nhật ký nói được con số thật, không phải "đã sẵn sàng". */
    val grammar: VoicePhraseSet,
) : AutoCloseable {

    /**
     * Nạp một khối PCM 16-bit mono 16 kHz.
     *
     * @return `true` khi Vosk **chốt câu** (nó tự nhận ra người nói đã ngừng — bảng ngắt câu nằm trong
     *   `conf/model.conf` của mô hình: `rule2 0.5s · rule3 1.0s · rule4 2.0s`). Chỗ gọi lấy [result] rồi dừng.
     */
    fun accept(buffer: ShortArray, length: Int): Boolean =
        runCatching { recognizer.acceptWaveForm(buffer, length) }
            .onFailure { Log.w(TAG, "acceptWaveForm hỏng", it) }
            .getOrDefault(false)

    /** Chữ đang nghe dở (vẽ lên overlay để người nói thấy máy đang theo kịp). */
    fun partial(): String = text(runCatching { recognizer.partialResult }.getOrNull(), "partial")

    /** Chữ của câu vừa chốt. */
    fun result(): String = text(runCatching { recognizer.result }.getOrNull(), "text")

    /** Chữ còn lại khi phiên bị cắt ngang (hết 8 giây / người dùng thả tay). */
    fun finalResult(): String = text(runCatching { recognizer.finalResult }.getOrNull(), "text")

    /**
     * Giải mã **cả một khúc PCM đã thu sẵn** (không phải luồng micro) và trả chữ cuối cùng.
     *
     * Dùng cho lượt 2 của [VoiceOpenVocab]: cùng bộ nhận dạng, cùng nhịp 200 ms như micro đẩy, chỉ khác nguồn.
     * **CHẶN** ⇒ luồng nền.
     */
    fun decodeAll(pcm: ShortArray, length: Int): String {
        var at = 0
        val chunk = VoiceCapture.SAMPLE_RATE / 5
        while (at < length) {
            val n = minOf(chunk, length - at)
            val block = if (at == 0 && n == pcm.size) pcm else pcm.copyOfRange(at, at + n)
            if (accept(block, n)) return result()
            at += n
        }
        return finalResult()
    }

    override fun close() {
        runCatching { recognizer.close() }.onFailure { Log.w(TAG, "đóng recognizer hỏng", it) }
    }

    /**
     * Lấy đúng một trường chuỗi trong JSON của Vosk.
     *
     * Bọc `runCatching` vì đây là **chuỗi từ mã native**: một bản Vosk tương lai đổi khuôn JSON sẽ làm
     * `JSONObject` ném ngay giữa lúc người lái đang nói. Trả chuỗi rỗng ⇒ phiên nghe kết thúc bằng *"không
     * nghe rõ"*, thay vì bằng một hộp thoại sập ứng dụng launcher.
     */
    private fun text(json: String?, key: String): String =
        runCatching { JSONObject(json ?: return "").optString(key).trim() }
            .onFailure { Log.w(TAG, "JSON của Vosk không đọc được: $json", it) }
            .getOrDefault("")

    companion object {
        private const val TAG = "KachiVoiceRec"

        /** Tần số lấy mẫu — cùng số với [VoiceSession] và với mô hình (`conf/mfcc.conf`). */
        const val SAMPLE_RATE = 16_000f

        /**
         * Mở một phiên nhận dạng, hoặc `null` nếu mô hình chưa sẵn sàng / ngữ pháp rỗng.
         *
         * **CHẶN** (dựng đồ thị giải mã) ⇒ gọi trên luồng nền.
         *
         * ⚠ Ngữ pháp rỗng bị từ chối **cố ý**: `Recognizer` với danh sách rỗng quay về giải mã tự do 19.529 từ
         * — đúng thứ ta vừa bỏ công tránh, và nó hỏng **im lặng** (vẫn chạy, chỉ là nghe ra linh tinh).
         */
        fun open(
            ctx: Context,
            profiles: List<String>,
            apps: List<String>,
            installed: Set<String> = emptySet(),
        ): VoiceRecognizer? {
            val model = VoiceEngine.model(ctx) ?: return null
            val grammar = VoiceGrammar.phrases(VoiceModelStore.words(ctx), profiles, apps, installed)
            if (grammar.phrasesKept == 0) {
                Log.w(TAG, "ngữ pháp rỗng — từ chối mở phiên (${grammar.logLine()})")
                return null
            }
            Log.i(TAG, grammar.logLine())
            return runCatching { VoiceRecognizer(Recognizer(model, SAMPLE_RATE, grammar.json()), grammar) }
                .onFailure { Log.e(TAG, "không dựng được recognizer", it) }
                .getOrNull()
        }

        /**
         * ═══ V1.1 · Bộ nhận dạng **TỰ DO** (không ngữ pháp) — chỉ cho LƯỢT 2 ══════════════════════════════
         *
         * Spec **R16**. `Recognizer(model, 16000f)` giải mã trên cả từ điển 19.529 từ của mô hình.
         *
         * ## Đây KHÔNG phải nới lỏng cam kết *"ràng bằng ngữ pháp"* — nó vẫn nguyên vẹn
         * Lượt 1 (thứ quyết định **làm gì với xe**) vẫn ràng bằng tập đóng, và điều đó không đổi một chữ: một
         * câu lệnh không bao giờ được dựng từ bộ giải mã này. Cái nó đọc là **phần đuôi từ vựng mở** — tên bài
         * hát, điểm đến — thứ mà theo định nghĩa không nằm trong tập đóng nào, và thứ mà Kachi **không tự thi
         * hành**: nó chuyển nguyên văn cho app đích, sau một cổng CONFIRM bắt buộc ([VoiceRiskTable]).
         *
         * Ba chốt giữ cho nó không lan ra: gọi từ **đúng một** chỗ ([VoiceSession.freeTail]), chỉ chạy khi
         * [VoiceOpenVocab.triggerOf] khác `null`, và chỉ chạy trên một **mảng PCM đã đóng** (không micro).
         *
         * **CHẶN** (dựng đồ thị giải mã) ⇒ luồng nền.
         */
        fun openFree(ctx: Context): VoiceRecognizer? {
            val model = VoiceEngine.model(ctx) ?: return null
            return runCatching { VoiceRecognizer(Recognizer(model, SAMPLE_RATE), VoicePhraseSet(emptyList(), 0, emptyList(), emptyList())) }
                .onFailure { Log.e(TAG, "không dựng được recognizer tự do", it) }
                .getOrNull()
        }
    }
}

/**
 * Giữ [Model] cho cả tiến trình — xem KDoc *"Vòng đời"* ở [VoiceRecognizer].
 *
 * `@Volatile` + `synchronized`: hai lối vào có thể bấm gần nhau (pill mic trên thanh trên và ô *Nói với xe* trên
 * thanh nút), và nạp mô hình hai lần là mmap 53 MB hai lần.
 *
 * ## [SOÁT Pass 2] Quyết định: KHÔNG nhả mô hình khi máy thiếu bộ nhớ (`onTrimMemory`)
 * Cân nhắc rồi bỏ, có lý do đo được: 53 MB ấy là **mmap tệp**, không phải vùng nhớ ẩn danh — nhân đã có quyền
 * thu hồi từng trang khi máy chật mà không cần ai xin, nên "nhả" ở tầng Kotlin không trả lại nhiều như con số
 * gợi ý. Đổi lại, nhả rồi thì lần bấm mic kế tiếp phải dựng lại đồ thị giải mã ([ĐO] máy ảo 114–125 ms, trên đầu
 * xe chưa đo) đúng lúc người lái vừa bấm và đang chờ. Đường nhả vẫn tồn tại và vẫn có chỗ gọi — [release] được
 * gọi khi người dùng **gỡ** mô hình, ca duy nhất mà tệp dưới tay Vosk thật sự biến mất.
 */
object VoiceEngine {

    private const val TAG = "KachiVoiceEngine"

    @Volatile private var model: Model? = null

    /** Mô hình đã nạp, nạp nếu chưa. `null` = chưa cài / hỏng. **CHẶN** ⇒ luồng nền. */
    fun model(ctx: Context): Model? {
        model?.let { return it }
        return synchronized(this) {
            model ?: load(ctx.applicationContext)?.also { model = it }
        }
    }

    /**
     * Trả mô hình về hệ thống — gọi khi người dùng **gỡ** mô hình trong Cài đặt.
     *
     * Không có hàm này thì thư mục bị xoá nhưng mã native vẫn giữ các tệp đã mmap: người dùng bấm "Gỡ", thấy
     * "đã gỡ", mà bộ nhớ không trả lại và lần cài sau nạp nhầm mô hình cũ còn trong tay Vosk.
     */
    fun release() = synchronized(this) {
        model?.let { runCatching { it.close() }.onFailure { t -> Log.w(TAG, "đóng model hỏng", t) } }
        model = null
    }

    /** Mô hình đang nằm sẵn trong bộ nhớ chưa (để màn Cài đặt nói *"lần nói đầu sẽ hơi chậm"*). */
    fun loaded(): Boolean = model != null

    private fun load(ctx: Context): Model? {
        if (!VoiceModelStore.isReady(ctx)) return null
        // Vosk in rất nhiều dòng ở mức INFO cho mỗi lần nạp; trên xe nhật ký đó chỉ làm trôi mất dòng của mình.
        runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS) }
        val path = VoiceModelStore.dir(ctx).absolutePath
        val t0 = System.currentTimeMillis()
        return runCatching { Model(path) }
            .onSuccess { Log.i(TAG, "nạp mô hình $path trong ${System.currentTimeMillis() - t0} ms") }
            // `Model` ném `IOException`, nhưng lỗi nặng của Kaldi thoát ra dạng `Error`/`UnsatisfiedLinkError`
            // (thiếu ABI) ⇒ bắt `Throwable`: một launcher không được chết vì một tính năng phụ.
            .onFailure { Log.e(TAG, "không nạp được mô hình $path", it) }
            .getOrNull()
    }
}
