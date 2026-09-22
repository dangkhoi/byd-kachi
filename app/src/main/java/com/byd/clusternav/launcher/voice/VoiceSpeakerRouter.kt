package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log

/**
 * ═══ CHỌN ĐƯỜNG RA TIẾNG — mỗi câu đo lại, không rẽ nhánh theo cờ RAM ════════════════════════════════════════
 *
 * Spec `kachi-voice-feedback.html` R2 (Piper/Android).
 *
 * **Máy đọc nào**: máy đọc hệ thống (`vi-VN` thật) hay gói Piper offline — do [VoiceSpeakerSelector] đo lại mỗi
 * câu. (Trục "giọng bé" clip đã GỠ ở C5 · owner 2026-09-22 — chỉ còn Piper/Android.)
 */
class VoiceSpeakerRouter(
    ctx: Context,
    /** Pha 2 — công tắc *"ưu tiên giọng offline"* trong Cài đặt. Chưa có ⇒ luôn `false`, xem KDoc probe. */
    private val preferOffline: () -> Boolean = { false },
) : VoiceSpeaker {

    private val android = AndroidTtsSpeaker(ctx)

    /**
     * Đường Piper — **qua ranh giới tiến trình** từ #0 (2026-09-18).
     *
     * [RemotePiperSpeaker] chuyển câu sang [PiperTtsService] (`android:process=":tts"`); engine ONNX không còn
     * sống trong tiến trình launcher. Lý do là một tombstone, không phải một sở thích kiến trúc: `OfflineTts.generate`
     * SIGSEGV trên xe làm **chết cả launcher** ⇒ mất binding phím (`docs/diagnostics/oncar-piper-crash-binding-2026-09-18.md`).
     *
     * Với lớp này thì **không có gì đổi**: vẫn là một [VoiceSpeaker] báo `kind = SHERPA_OFFLINE`, vẫn `available()`
     * bằng cách kiểm gói trên đĩa, vẫn gọi `onDone` đúng một lần. Đừng dựng lại [SherpaTtsSpeaker] ở đây —
     * `VoiceTtsIsolationContractTest` sẽ đỏ, và đúng như thế: đó là kéo con trỏ native về lại tiến trình launcher.
     */
    private val sherpa = RemotePiperSpeaker(ctx)

    /** Đường **đang** được chọn — chỉ để báo cáo (nhật ký / cầu kiểm thử). */
    override val kind: VoiceSpeakerKind
        get() = VoiceSpeakerSelector.choose(probe())

    /** Ảnh chụp phép đo, dùng cho cả [VoiceSpeakerSelector.choose] lẫn cầu kiểm thử (`TestBridgeState`). */
    fun probe(): VoiceSpeakerSelector.VoiceSpeakerProbe {
        val p = VoiceSpeakerSelector.VoiceSpeakerProbe(
            androidLangStatus = android.languageStatus(),
            sherpaVoiceReady = sherpa.available(),
            preferOffline = runCatching { preferOffline() }.getOrDefault(false),
        )
        last = Snapshot(
            kind = VoiceSpeakerSelector.choose(p),
            androidLangStatus = p.androidLangStatus,
            androidUsable = VoiceSpeakerSelector.androidUsable(p),
            sherpaVoiceReady = p.sherpaVoiceReady,
            engine = androidEngineName().orEmpty(),
        )
        return p
    }

    /** Tên engine đọc của hệ thống (hoặc `null`) — báo cáo, KHÔNG dùng để rẽ nhánh (CLAUDE.md §7). */
    fun androidEngineName(): String? = android.engineName()

    private fun active(): VoiceSpeaker = when (VoiceSpeakerSelector.choose(probe())) {
        VoiceSpeakerKind.ANDROID_TTS -> android
        VoiceSpeakerKind.SHERPA_OFFLINE -> sherpa
        VoiceSpeakerKind.NONE -> SilentSpeaker
    }

    override fun available(): Boolean = active() !== SilentSpeaker

    override fun speak(text: String): Boolean = route(text, null)

    /**
     * OQ4 — chuyển tiếp cả mốc *"đọc xong"*.
     *
     * ⚠ Không tự dựng một hạn chờ ở đây: lớp này **không biết** câu dài bao nhiêu và chỗ gọi nào đang chờ. Hạn
     * chờ là việc của cổng an toàn (`VoiceSession.ASK_ALOUD_CAP_MS`), đúng chỗ biết hậu quả của việc chờ quá lâu.
     */
    override fun speak(text: String, onDone: () -> Unit): Boolean = route(text, onDone)

    private fun route(text: String, onDone: (() -> Unit)?): Boolean {
        // Đường kia có thể còn đang đọc câu trước ⇒ dừng cả hai rồi mới nói (rẻ, và chặn ca hai giọng chồng).
        stop()
        val target = active()
        if (target === SilentSpeaker) {
            // Không có đường nào đọc được ⇒ *"đọc xong"* là ngay bây giờ (vế (1) của hợp đồng).
            onDone?.let { runCatching { it() } }
            return false
        }
        // §9 — CỬA DUY NHẤT đổi chữ Latin sang âm Việt cho đường Piper/Android (xem KDoc lớp về vì sao clip đứng trước).
        val said = TtsPronunciation.normalise(text)
        val ok = if (onDone == null) target.speak(said) else target.speak(said, onDone)
        if (!ok) Log.i(TAG, "đường ${target.kind} không đọc được câu — chỉ còn chữ trên tấm chữ")
        return ok
    }

    override fun stop() {
        android.stop()
        sherpa.stop()
    }

    override fun shutdown() {
        android.shutdown()
        sherpa.shutdown()
    }

    companion object {
        private const val TAG = "KachiVoiceSpeak"

        /**
         * Ảnh chụp phép đo GẦN NHẤT — **chỉ để báo cáo**, không ai được rẽ nhánh theo nó.
         *
         * ## Vì sao một biến toàn cục lại được phép ở đây (CLAUDE.md §5)
         * §5 cấm *"quyết định bằng cờ RAM"*, và câu tiếp theo của chính nó là *"cờ chỉ để hiển thị"*. Đây đúng vế
         * thứ hai: mọi **quyết định** vẫn chạy qua [probe] đo lại tại chỗ; biến này chỉ để cầu kiểm thử
         * (`TestBridgeState`) trả lời được câu *"máy này đọc bằng đường nào"* mà **không** phải dựng thêm một
         * `TextToSpeech` thứ hai — dựng nó là bất đồng bộ (vài trăm ms) trong một lệnh đọc phải trả lời ngay,
         * nên lượt đo sẽ luôn báo "chưa sẵn sàng" dù máy đọc thật đang chạy tốt.
         *
         * `null` = **chưa từng có phiên nói nào** kể từ lần khởi động này ⇒ chưa đo, không phải "không có giọng".
         */
        @Volatile
        private var last: Snapshot? = null

        /** Xem KDoc [last]. `null` ⇒ chưa đo. */
        fun lastSnapshot(): Snapshot? = last

        /**
         * @param androidLangStatus số thô của `isLanguageAvailable`; `null` = chưa dựng xong dịch vụ đọc.
         * @param androidUsable đã qua ngưỡng [VoiceSpeakerSelector.LANG_AVAILABLE] chưa.
         */
        data class Snapshot(
            val kind: VoiceSpeakerKind,
            val androidLangStatus: Int?,
            val androidUsable: Boolean,
            val sherpaVoiceReady: Boolean,
            val engine: String,
        )
    }
}
