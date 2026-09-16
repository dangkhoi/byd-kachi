package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log

/**
 * ═══ ĐIỂM NGẮT CÂU của MỘT lượt nghe — Silero VAD, lùi về RMS ════════════════════════════════════════════════
 *
 * Bằng chứng: `docs/diagnostics/voice-stream-eval-2026-09-16.md` §5 · §6 · §8.
 *
 * ## Vì sao có lớp này, khi đã có [VoiceVad] và [VoiceEndpointer]
 * Vì [VoiceCapture] không được biết **đang dùng đường nào**. Nếu vòng đọc micro tự rẽ `if (vad != null) … else …`
 * thì mỗi câu hỏi của nó — *"ngắt chưa"*, *"đã nghe thấy tiếng chưa"*, *"cắt tới mẫu nào"*, *"bíp được chưa"* —
 * đều nhân đôi, và đường **lùi** (RMS) là đường ít được chạy nhất nên cũng là đường rữa trước nhất. Một bề mặt
 * duy nhất thì đường lùi đi qua đúng những lời gọi mà đường chính đi qua.
 *
 * Nó cũng là chỗ đặt **một** câu trả lời cho *"hôm nay lượt này chạy bằng gì"* — dòng `duong=` trong nhật ký, thứ
 * duy nhất phân biệt được *"VAD chốt sớm"* với *"VAD không dựng được và bộ RMS lại điếc như cũ"* khi đọc log xe.
 *
 * ## Thứ tự ưu tiên, và vì sao RMS **ở lại** chứ không bị gỡ
 *  1. **Silero VAD** — đường CHÍNH. [ĐO host §5] p50 660 ms · 0/1 899 cắt giữa câu · 0/1 899 không nổ.
 *  2. **[VoiceEndpointer] (RMS)** — đường LÙI, chạy khi asset thiếu / ONNX từ chối / ROM lạ. Nó **kém hơn hẳn**
 *     ([ĐO xe] gần như không bao giờ nổ, `chot=4200ms` ở 165/299 lượt) nhưng nó **không cần một mô hình thứ hai**,
 *     nên nó là thứ còn sống khi mọi thứ khác hỏng. Đây là lý do nó ở lại: một đường phục hồi phải có ít phụ
 *     thuộc hơn đường nó phục hồi cho (CLAUDE.md §3).
 *  3. **Không có gì** — chỗ gọi truyền `rms = null` *và* VAD không dựng được ⇒ lượt nghe chạy tới trần cứng, y
 *     như trước khi có bất kỳ bộ ngắt câu nào.
 *
 * Trần cứng `VoiceSession.MAX_LISTEN_MS` **vẫn nguyên** ở mọi nhánh — nó là lưới an toàn, không phải đường chính
 * (cùng luật đã ghi ở KDoc [VoiceEndpointer]).
 */
internal class VoiceTurnEndpoint private constructor(
    private val vad: VoiceVad?,
    private val rms: VoiceEndpointer?,
) : AutoCloseable {

    /**
     * Nhãn ASCII cho nhật ký (`vad` · `rms` · `tran`) — grep được trên máy khác ngôn ngữ.
     *
     * ⚠ [SOÁT 1.69 · P2] Cạnh nó từng có `val usingVad: Boolean` với KDoc nói *"vào nhật ký, và vào bài canh
     * dây nối"* — **cả hai vế đều sai**: `grep -rn "usingVad"` trên `app` · `core` · `car-integration` ·
     * `scripts` · `docs` trả về **đúng một dòng**, chính dòng khai nó. Nhật ký đi qua [summary] (dùng [route]),
     * còn ba bài canh đọc **văn bản nguồn** của tệp này chứ không gọi thành viên nào. Đó đúng hình dạng
     * `CastShell.evictVd` mà CLAUDE.md §8 lấy làm ví dụ, và tệ hơn một bậc vì chính KDoc **khai** một chỗ gọi
     * không tồn tại — tức nó nói dối đúng lượt grep mà §8 bảo phải chạy. Đã gỡ; ai cần một cờ thì `route == "vad"`.
     */
    val route: String get() = when {
        vad != null -> "vad"
        rms != null -> "rms"
        else -> "tran"
    }

    /**
     * Nhận một khối đã đọc. Trả `true` khi **phải dừng nghe ngay**.
     *
     * @param rmsChunk mức rms của khối, chỉ đường lùi dùng tới — tính sẵn ở [VoiceCapture] vì nó đã duyệt khối
     *   một lượt để đo đỉnh, và duyệt lần thứ hai ở đây là trả giá hai lần cho cùng một phép cộng.
     */
    fun accept(pcm: ShortArray, n: Int, rmsChunk: Int, chunkMs: Int): Boolean {
        vad?.let { return it.accept(pcm, n) }
        return rms?.accept(rmsChunk, chunkMs) == VoiceEndpointer.Phase.ENDED
    }

    /**
     * Cửa sổ đo nền của đường LÙI còn đang mở không ⇒ **chưa được bíp**.
     *
     * [P0-2] Tiếng bíp rơi vào 300 ms đầu làm nền đo được vọt lên (ĐO xe: nền 531/602/888, cá biệt 4317, trong
     * khi giọng thật chỉ rms 206–627) ⇒ ngưỡng nằm trên giọng và lượt nghe điếc. Đường VAD **không có** cửa sổ đo
     * nền nên nó trả `false` ngay — bíp được luôn, đúng như trước [P0-2]; Silero nhìn dạng sóng giọng người, một
     * tiếng bíp thuần không làm nó mở đoạn.
     */
    fun floorWindowOpen(): Boolean = vad == null && rms?.phase == VoiceEndpointer.Phase.FLOOR

    /**
     * Chốt nốt đoạn đang mở khi lượt nghe dừng vì **trần cứng** (người ta nói dài, hoặc bị huỷ).
     *
     * Bỏ bước này thì một câu chạm trần không có đoạn nào ⇒ [sawSpeech] trả `false` ⇒ bị bỏ giải mã như một lượt
     * im lặng. *"Nói dài"* và *"không ai nói"* là hai ca ngược nhau; chúng không được rơi vào cùng một nhánh.
     */
    fun flush() { vad?.flush() }

    /** Lượt này có tiếng nào không — cổng của [P0-1a] *"không có tiếng thì không giải mã"*. */
    fun sawSpeech(): Boolean = vad?.sawSpeech() ?: rms?.sawSpeech() ?: true

    /**
     * Số mẫu **đầu cửa sổ** được đưa vào bộ giải mã (chế độ `head`, xem [VoiceVadTrim.headTrimSamples]).
     *
     * ⚠ Đường LÙI trả **nguyên cửa sổ**, cố ý: bộ RMS cho biết *"lúc nào hết tiếng"* theo mức năng lượng, và [ĐO
     * xe] con số ấy sai hệ thống trên cabin thật (nền bị nhiễm ⇒ không bao giờ thấy tiếng). Cắt theo một con số
     * không tin được là **tự cắt mất câu nói**; thà nạp thừa như bản cũ. Tức đường lùi giữ đúng hành vi 1.68 —
     * nó chỉ lùi về chỗ cũ, không mang theo một phép cắt chưa được đo.
     */
    fun trimSamples(windowSamples: Int): Int = vad?.headTrimSamples(windowSamples) ?: windowSamples

    /** Ba con số của hợp đồng nhật ký: bắt đầu tiếng · hết tiếng · còn lại sau khi cắt. `-1` = chưa có. */
    fun speechStartMs(): Int = vad?.speechStartMs() ?: rms?.speechStartMs ?: -1
    fun speechEndMs(): Int = vad?.speechEndMs() ?: rms?.speechEndMs ?: -1

    fun summary(windowSamples: Int): String =
        "duong=$route " + (vad?.summary(windowSamples) ?: rms?.summary() ?: "khong co bo ngat cau")

    override fun close() { vad?.close() }

    companion object {
        private const val TAG = "KachiVoiceVad"

        /**
         * Dựng bộ ngắt câu cho một lượt. **CHẶN** (dựng ONNX) ⇒ gọi trên luồng nền.
         *
         * @param rmsFallback bộ RMS mà chỗ gọi đã dựng sẵn theo prefs; `null` = chỗ gọi cố ý **không** muốn ngắt
         *   câu nào (một ca thật: bài kiểm truyền `endpointer = null`).
         */
        fun open(ctx: Context, rmsFallback: VoiceEndpointer?): VoiceTurnEndpoint {
            // `rmsFallback == null` nghĩa là chỗ gọi tắt hẳn việc ngắt câu ⇒ KHÔNG dựng VAD sau lưng họ.
            if (rmsFallback == null) return VoiceTurnEndpoint(null, null)
            val vad = VoiceVad.open(ctx)
            if (vad == null) Log.w(TAG, "không có Silero VAD — lượt này dùng bộ ngắt câu RMS (đường lùi)")
            return VoiceTurnEndpoint(vad, rmsFallback)
        }
    }
}
