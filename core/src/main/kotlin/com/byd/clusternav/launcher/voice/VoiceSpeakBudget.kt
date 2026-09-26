package com.byd.clusternav.launcher.voice

/**
 * ═══ LƯỚI AN TOÀN CHO MỘT LƯỢT ĐỌC — tấm chữ phải sống lâu hơn câu đang đọc ═══════════════════════════════════
 *
 * Bằng chứng: `docs/diagnostics/oncar-voice-cases-findings-2026-09-18.md` **§B**.
 *
 * ## Vấn đề: một hằng số 10 giây mô tả một việc dài theo ĐỘ DÀI CÂU
 * Đường trả lời của `VoiceSession.execute` có hai mốc đóng tấm chữ, và chúng nói về hai thứ khác nhau:
 *  • **mốc thật** — `onReplyDone` (chính `onDone` của máy đọc): đọc xong ⇒ nán `LINGER_MS` rồi đóng. Đây là mốc
 *    đúng, và nó đã có từ 1.70.
 *  • **lưới an toàn** — hẹn đóng đặt NGAY lúc `execute`, phòng ca `onDone` **không bao giờ về** (engine chết,
 *    tiến trình đọc bị hệ thống thu). Tới 1.78 nó là hằng **10 s cứng**.
 *
 * [ĐO xe 2026-09-18] 8 lõi bão hoà (load 14 — GMaps-in-slot 61% + BYD cdr 39% + surfaceflinger 34%), Piper
 * (`SherpaTtsSpeaker`, ONNX một luồng) **tổng hợp trước khi phát tiếng đầu tiên**, nên một câu trả lời dài
 * (vd *"✓ Mở Tất cả kính — chưa kiểm trên xe"* + hai vế khác) vượt 10 s ⇒ lưới an toàn đóng tấm chữ **giữa lúc
 * đang đọc**, đúng thứ owner báo: *"feedback chưa hết câu đã mất overlay, tiếng đứng nửa chừng"*. Off-car CPU
 * rảnh nên câu nào cũng xong dưới 10 s ⇒ không lộ.
 *
 * ## Vì sao ƯỚC LƯỢNG hào phóng, không ước lượng chính xác
 * Hai đầu của phép chọn **không đối xứng** (cùng lập luận đã ghi ở `VoiceSession.ASK_ALOUD_CAP_MS`):
 *  • **hụt** ⇒ cắt tấm chữ giữa câu ở MỌI câu dài — đúng lỗi đang sửa, và nó xảy ra thường xuyên.
 *  • **thừa** ⇒ tấm chữ nán thêm vài giây **chỉ trong ca engine chết** (hiếm), và nó vẫn tắt được bằng một cú
 *    chạm ra ngoài tấm chữ (Back thôi là đường thoát từ 2.73 — `VoiceOverlay` không lấy tiêu điểm). Trong ca
 *    THƯỜNG, `onReplyDone` rút hẹn đóng về `LINGER_MS` ngay khi đọc xong ⇒
 *    con số ở đây không làm người lái phải chờ một giây nào.
 *
 * ⇒ [MS_PER_CHAR] = 200 ms/ký tự ≈ **3× thời lượng đọc thật** ([ĐO host] Piper 11 từ ≈ 2,11 s ⇒ ~65 ms/ký tự),
 * tức chừa đúng phần biên cho một CPU đang bão hoà. Cố ý KHÔNG có trần trên theo độ dài: câu càng dài thì càng
 * cần lưới dài, và thứ giới hạn nó không phải một hằng số mà là chính `onReplyDone`.
 *
 * ## Vì sao ở `:core`
 * Ba dòng số học, không chạm Android — nên nó kiểm được off-car bằng vài chuỗi giả (cùng lẽ [VoiceVadTrim]).
 * Tầng Android chỉ còn việc truyền **sàn** của nó vào (`VoiceSession.SPEAK_SAFETY_MS`).
 */
object VoiceSpeakBudget {

    /** Mỗi ký tự của câu trả lời cộng ngần này vào lưới an toàn — xem KDoc lớp về vì sao hào phóng. */
    const val MS_PER_CHAR = 200L

    /**
     * Cộng thêm ngần này bất kể độ dài: lượt **nạp mô hình + tổng hợp** của Piper xảy ra TRƯỚC tiếng đầu tiên và
     * nó không tỉ lệ với số ký tự (nó tỉ lệ với việc CPU có rảnh hay không).
     */
    const val HEAD_ROOM_MS = 5_000L

    /**
     * **SÀN** của lưới — câu một chữ vẫn được ngần này. 15 s (1.78 là 10 s cứng cho MỌI câu).
     *
     * Khai ở `:core` chứ không ở `VoiceSession` để có **đúng một con số**: `VoiceSession.SPEAK_SAFETY_MS` chỉ là
     * tên gọi của hằng này ở tầng Android (cùng luật *"mặc định phải LÀ hằng của `:core`, không phải số chép
     * tay"* mà `VoiceVadWiringContractTest` đang canh cho ba núm VAD).
     */
    const val FLOOR_MS = 15_000L

    /**
     * Lưới an toàn cho lượt đọc [lines] (một batch được `VoiceFeedbackPhrase.merge` gộp thành MỘT câu).
     *
     * @param floorMs sàn — không bao giờ trả nhỏ hơn con số này, kể cả câu một chữ.
     *
     * Đếm ký tự trên các dòng **trước khi gộp**, không trên câu đã gộp: phép gộp có thể cắt bớt theo trần của nó,
     * và một lưới an toàn tính trên chuỗi NGẮN hơn chuỗi thật là một lưới hụt. Thừa ở đây không tốn gì (KDoc lớp).
     */
    fun estimateMs(lines: List<String>, floorMs: Long = FLOOR_MS): Long =
        maxOf(floorMs, lines.sumOf { it.length.toLong() } * MS_PER_CHAR + HEAD_ROOM_MS)
}
