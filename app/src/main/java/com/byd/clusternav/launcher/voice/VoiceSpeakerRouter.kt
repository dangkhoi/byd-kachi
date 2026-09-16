package com.byd.clusternav.launcher.voice

import android.content.Context
import android.util.Log

/**
 * ═══ V1 pha NÓI · MỘT MÁY ĐỌC ĐỐI VỚI CHỖ GỌI, BA ĐƯỜNG BÊN TRONG ════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R2**. Nó cầm cả [AndroidTtsSpeaker] lẫn [SherpaTtsSpeaker] và
 * hỏi [VoiceSpeakerSelector] **tại mỗi câu** xem đường nào đang dùng được.
 *
 * ## Vì sao chọn LẠI mỗi câu, không chọn một lần lúc dựng
 * Hai phép đo ở đây đều **đổi giữa chuyến**, và cả hai đều đổi theo hướng *"lúc dựng chưa có, lát nữa mới có"*:
 *  • [AndroidTtsSpeaker] dựng **bất đồng bộ** — hỏi lúc launcher vừa khởi động thì gần như chắc chắn chưa xong
 *    ([VoiceSpeaker.available] còn `false`), mà phiên nói đầu tiên có thể xảy ra vài giây sau đó;
 *  • gói giọng offline có thể vừa được lắp xong ở màn Cài đặt, hoặc vừa bị *Xoá dữ liệu* cuốn đi.
 * Chốt một lần lúc dựng nghĩa là một launcher khởi động xong trước máy đọc sẽ **im lặng tới hết chuyến**, và
 * không có gì báo. Phép chọn ở đây rẻ (một `Int` + ba `File.exists`), không đáng đánh đổi lấy sự im lặng ấy.
 *
 * ## Nó KHÔNG giữ trạng thái "đang nói"
 * [stop] gọi thẳng cả hai đường. Hỏi *"đường nào đang nói"* rồi chỉ dừng đúng đường đó là dựng một cờ RAM để
 * quyết định — CLAUDE.md §5 cấm đúng việc ấy, và ở đây cái giá của cờ sai là micro mở trong lúc loa còn đang nói.
 */
class VoiceSpeakerRouter(
    ctx: Context,
    /** Pha 2 — công tắc *"ưu tiên giọng offline"* trong Cài đặt. Chưa có ⇒ luôn `false`, xem KDoc probe. */
    private val preferOffline: () -> Boolean = { false },
) : VoiceSpeaker {

    private val android = AndroidTtsSpeaker(ctx)
    private val sherpa = SherpaTtsSpeaker(ctx)

    /** Đường **đang** được chọn — chỉ để báo cáo (nhật ký / cầu kiểm thử). */
    override val kind: VoiceSpeakerKind get() = VoiceSpeakerSelector.choose(probe())

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
        val target = active()
        if (target === SilentSpeaker) {
            // Không có đường nào đọc được ⇒ *"đọc xong"* là ngay bây giờ (vế (1) của hợp đồng).
            onDone?.let { runCatching { it() } }
            return false
        }
        // Đường kia có thể còn đang đọc câu trước (người lái nói hai câu sát nhau trong lúc gói offline vừa lắp
        // xong ⇒ đổi đường giữa hai câu). Dừng cả hai rồi mới nói là một lệnh rẻ, và nó chặn ca hai giọng chồng.
        stop()
        val ok = if (onDone == null) target.speak(text) else target.speak(text, onDone)
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
