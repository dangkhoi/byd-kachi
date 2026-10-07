package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.CtlSafetyPolicy
import com.byd.clusternav.launcher.Lang

/**
 * ═══ 2.96 R12 · CÂU **ĐÃ LÀM** — thuận miệng, đúng mức bằng chứng ═══════════════════════════════════════════════
 *
 * Owner 07/10: *"các message khi xử lý nó chưa tự nhiên, chẳng hạn như 'Tắt kính lái' → Đóng kính lại, 'Sưởi ghế phụ:
 * Tắt' → đã tắt sưởi ghế phụ"*. Tới 2.95 [VoiceReply.done] = `"✓ " + preview` — tức câu **mệnh lệnh** (*"✓ Tắt Kính
 * lái"*) hiện trên tấm chữ, và tầng đọc ([VoiceFeedbackPhrase.merge]) ghép *"Đã "* vào trước một cách mù: *"Đã sưởi ghế
 * phụ: Tắt"* · *"Đã bố cục 2 cột"* · *"Đã camera sau"*. Ở đây dựng sẵn câu QUÁ KHỨ trọn vẹn (*"Đã tắt sưởi ghế phụ"*),
 * và `merge` nhận nguyên văn câu đã mang lời dẫn.
 *
 * ## Ba mức, theo đúng thứ code BIẾT (không hứa hơn)
 *  • [confirmed] = lượt đọc lại xe KHỚP ⇒ *"Đã …"*.
 *  • chưa xác nhận + bộ phận chạy bằng mô-tơ ([CtlSafetyPolicy.MOVES_SLOWLY]: kính · cốp · cửa sổ trời · rèm) ⇒
 *    *"Đang đóng kính lái"* — xe đã nhận lệnh, bộ phận đang chạy; nói *"Đã đóng"* là hứa một thứ chưa xảy ra.
 *  • chưa xác nhận, bộ phận đổi mức tức thì ⇒ *"Đã …"* như 2.95 (đuôi *"chưa kiểm trên xe"* vẫn do
 *    [VoiceReply.done] gắn theo mức bằng chứng).
 *
 * Chỉ tiếng VIỆT đổi dạng (giọng nói chỉ có VI/EN — `voiceLangOf`); tiếng khác giữ câu xem-trước, lời dẫn *"Done: "* do
 * `merge` gắn như cũ. `internal`, [lang] không mặc định — cùng lẽ [VoiceReplyPreview].
 */
internal object VoiceReplyDone {

    private const val DONE = "Đã "
    private const val DOING = "Đang "

    /** Thân câu (không dấu ✓) cho việc [i] vừa làm. */
    fun body(i: VoiceIntent, lang: Lang, confirmed: Boolean): String {
        val preview = VoiceReply.preview(i, lang)
        if (lang != Lang.VI) return preview
        return when (i) {
            is VoiceIntent.Control ->
                if (!confirmed && i.relative == 0 && CtlSafetyPolicy.movesSlowly(i.id)) DOING + VoiceFeedbackPhrase.decap(preview)
                else past(preview, lang)
            // *"Mở hết kính"* đã là một câu lệnh ⇒ *"Đã mở hết kính"*, không phải *"Đã chạy gói Mở hết kính"*.
            is VoiceIntent.Macro -> ActionMacros.byId(i.id)?.labelIn(lang)?.takeIf { verbLed(it) }
                ?.let { past(it, lang) } ?: past(preview, lang)
            // Tên app là chữ người dùng nhìn thấy — giữ nguyên hoa/thường; bỏ chữ thừa *"ứng dụng"*.
            is VoiceIntent.OpenApp -> DONE + "mở " + i.appName + VoiceReplyPreview.inSlot(i.slot, lang)
            is VoiceIntent.Nav, is VoiceIntent.NavigateSaved -> DONE + "bắt đầu " + VoiceFeedbackPhrase.decap(preview)
            is VoiceIntent.Layout -> DONE + "chuyển sang " + VoiceFeedbackPhrase.decap(preview)
            else -> past(preview, lang)
        }
    }

    /** *"Mở camera sau"* → *"Đã mở camera sau"* (VI); tiếng khác giữ nguyên. */
    fun past(command: String, lang: Lang): String =
        if (lang == Lang.VI) DONE + VoiceFeedbackPhrase.decap(command) else command

    /**
     * Nhãn đã mở đầu bằng một ĐỘNG TỪ của bộ phân tích (*"Đóng tất cả kính"* · *"Mở hết kính"*)? — hỏi thẳng
     * [VoiceGrammar.VERBS] (bỏ dấu), không giữ một danh sách động từ thứ hai.
     */
    fun verbLed(label: String): Boolean {
        val words = VoiceLexicon.tokenize(label).map { it.norm }
        if (words.isEmpty()) return false
        return VoiceGrammar.VERBS.any { (v, _) -> v.size <= words.size && words.subList(0, v.size) == v }
    }
}
