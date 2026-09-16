package com.byd.clusternav.launcher.voice

/**
 * ═══ V1 pha NÓI · MỘT CÁI CỬA DUY NHẤT RA TIẾNG ══════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R2**. Ba đường ra tiếng khác nhau hẳn về cơ chế
 * ([AndroidTtsSpeaker] gọi dịch vụ của hệ thống · [SherpaTtsSpeaker] tự suy diễn ONNX rồi tự đẩy `AudioTrack` ·
 * [SilentSpeaker] không làm gì), nhưng `VoiceSession` chỉ được biết **một** giao diện: chỗ gọi mà phải hỏi
 * *"đang dùng máy đọc nào"* là chỗ sẽ mọc ra nhánh `if` thứ hai rồi lệch khỏi nhánh thứ nhất.
 *
 * ## Vì sao [available] tách khỏi [speak], và vì sao [speak] vẫn trả `Boolean`
 * [available] trả lời câu hỏi *"có đáng bật công tắc Đọc phản hồi không"* — hỏi được **trước** khi có câu nào để
 * đọc. Nhưng nó **không** hứa lần đọc tới sẽ thành công: máy đọc của hệ thống có thể chết giữa chuyến (ROM xe
 * dọn tiến trình nền), gói giọng có thể bị xoá. Nên [speak] vẫn phải tự báo kết quả của chính lần gọi đó.
 *
 * ## Ràng buộc chung cho mọi bản cài đặt
 *  • **KHÔNG CHẶN** luồng gọi. `VoiceSession.execute` chạy trên luồng vẽ; một lượt tổng hợp giọng offline mất
 *    hàng trăm ms tới vài giây (spec §6 V-oncar) — chặn ở đó là đơ launcher trên xe đang chạy (CLAUDE.md mở bài).
 *  • **KHÔNG NÉM**. Một tính năng phụ không được phép giết launcher; mọi đường hỏng đều lùi về *"chỉ còn chữ"*.
 *  • [stop] phải **an toàn khi gọi nhiều lần** và khi chưa từng [speak].
 */
interface VoiceSpeaker {

    /** Máy đọc này thuộc loại nào — chỉ để **báo cáo** (nhật ký, cầu kiểm thử), KHÔNG để rẽ nhánh hành vi. */
    val kind: VoiceSpeakerKind

    /**
     * Sẵn sàng đọc tiếng Việt chưa.
     *
     * ⚠ Có thể trả `false` trong **vài trăm ms đầu** rồi thành `true`: máy đọc của hệ thống dựng bất đồng bộ
     * ([AndroidTtsSpeaker]). Đây là lý do nó là một **hàm**, không phải một `val` đọc một lần lúc dựng.
     */
    fun available(): Boolean

    /**
     * Đọc [text]. Trả về `true` nếu đã **nhận** câu (đã xếp hàng đọc), `false` nếu không đọc được.
     *
     * Câu đưa vào đây phải đã đi qua [VoiceFeedbackPhrase] — xem KDoc ở đó về vì sao không đọc thẳng chuỗi của
     * `VoiceReply`.
     */
    fun speak(text: String): Boolean

    /**
     * Như [speak], nhưng gọi [onDone] khi câu đã **đọc xong** (hoặc bị cắt / hỏng giữa chừng).
     *
     * ## Vì sao cần, và vì sao mặc định lại gọi NGAY
     * Spec `kachi-voice-feedback.html` **OQ4** (owner duyệt 2026-09-16): câu hỏi xác nhận phải được **đọc xong**
     * rồi mới mở micro. Không có mốc "xong" thì chỉ còn hai lựa chọn tồi: mở micro ngay (Kachi nghe chính mình
     * đọc *"Mở khoá cửa?"* và có thể tự trả lời) hoặc chờ một khoảng cố định (đoán — câu dài thì cụt, câu ngắn
     * thì người lái ngồi im chờ micro).
     *
     * Bản mặc định đọc rồi gọi [onDone] **ngay** vì đó là hành vi đúng cho một máy đọc **không phát ra tiếng
     * gì** ([SilentSpeaker], và mọi bản cài đặt tương lai chỉ ghi nhật ký): ở đó không có gì để chờ, và chờ là
     * treo cổng xác nhận. Bản có tiếng thật ([AndroidTtsSpeaker] · [SherpaTtsSpeaker]) override để gọi đúng lúc.
     *
     * ⚠ **Hợp đồng ba vế**, chỗ gọi dựa cả ba:
     *  1. [onDone] được gọi **đúng một lần** cho mỗi lời gọi — kể cả khi trả `false` (không đọc được thì *"đọc
     *     xong"* là ngay bây giờ). Một cổng an toàn chờ [onDone] mà không bao giờ nhận được là một cổng chết im,
     *     nên hợp đồng chọn *"luôn gọi"* thay vì *"gọi khi thành công"*: chỗ gọi chỉ có MỘT đường, không có
     *     nhánh nào để quên.
     *  2. nó có thể chạy trên **luồng bất kỳ** (luồng của engine đọc) ⇒ chỗ gọi tự đẩy về luồng nó cần;
     *  3. giá trị trả về vẫn nói *"câu có được nhận không"* (dùng để ghi nhật ký), **không** nói khi nào [onDone]
     *     chạy. Chỗ gọi vẫn phải có **hạn chờ riêng**: một engine chết giữa chừng có thể trả `true` rồi im mãi.
     */
    fun speak(text: String, onDone: () -> Unit): Boolean {
        val ok = speak(text)
        onDone()
        return ok
    }

    /** Ngắt câu đang đọc (người lái huỷ phiên, hoặc sắp mở micro). Gọi thừa ⇒ không làm gì. */
    fun stop()

    /** Nhả tài nguyên (dịch vụ hệ thống / phiên ONNX / `AudioTrack`). Sau lời gọi này [available] là `false`. */
    fun shutdown()
}

/**
 * Máy đọc **không đọc** — đường lùi khi máy không có giọng Việt nào.
 *
 * Nó tồn tại để `VoiceSession` không phải mang một `VoiceSpeaker?` nullable: mỗi dấu `?.` là một chỗ có thể quên,
 * và chỗ quên ở đây không kêu — nó chỉ **im lặng không đọc**, đúng thứ không ai phát hiện ra khi thử trong phòng.
 */
object SilentSpeaker : VoiceSpeaker {
    override val kind: VoiceSpeakerKind = VoiceSpeakerKind.NONE
    override fun available(): Boolean = false
    override fun speak(text: String): Boolean = false

    /** Không đọc gì ⇒ *"đọc xong"* là **ngay bây giờ** — đúng vế (1) của hợp đồng, xem KDoc [VoiceSpeaker.speak]. */
    override fun speak(text: String, onDone: () -> Unit): Boolean {
        onDone()
        return false
    }

    override fun stop() = Unit
    override fun shutdown() = Unit
}
