package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings

/**
 * ═══ THÂN CÂU *"KHÔNG HIỂU"* + *"KHÔNG NGHE RÕ"* — phần của [VoiceReply.unknown], tách khỏi `VoiceReply.kt` ═══════
 *
 * Cùng lý do tách với [VoiceReplyPreview] (trần 500 dòng của `Voice*.kt`, spec `kachi-i18n-zh-th-ms.html` T2). Vai:
 * *"nói rõ máy kẹt ở đâu"*. `internal`, [lang] không mặc định — chỉ [VoiceReply] gọi, với ngôn ngữ của GIỌNG NÓI.
 */
internal object VoiceReplyUnknown {

    /**
     * Ví dụ động từ của câu NO_VERB — **luôn tiếng Việt**, ở mọi ngôn ngữ (spec R5/OQ3): bộ nhận dạng chỉ có một gói
     * tiếng Việt, nên dạy người dùng nói *"turn on…"* là dạy một câu máy không bao giờ nghe ra. Là ĐỐI SỐ `{0}` của mẫu
     * chứ không nằm trong mẫu: bản dịch zh/th/ms không được mang dấu tiếng Việt (`I18nCoverageTest`), mà ví dụ thì
     * phải giữ nguyên văn để parser hiểu. Bản VI ghép ra y từng byte như câu cũ.
     */
    private const val VERB_EXAMPLES = "\"bật…\", \"mở…\", \"xem…\""

    /**
     * D3 — câu gọi ĐÚNG TÊN tính năng đã bỏ/chưa làm, hoặc `null` khi [u] không phải ca ấy (hoặc không tra ra tên).
     *
     * Đứng riêng vì câu này **thay cả** câu không-hiểu (không kèm đuôi câu gốc) — xem [VoiceReply.unknown].
     */
    fun gone(u: VoiceIntent.Unknown, lang: Lang): String? =
        if (u.reason != VoiceUnknownReason.FEATURE_GONE) null
        else VoiceFeatureGone.match(VoiceLexicon.tokenize(u.text))?.let { VoiceFeatureGone.reply(it, lang) }

    /** Vế đầu của câu không hiểu — nói rõ **không hiểu ở đâu**. */
    fun head(u: VoiceIntent.Unknown, lang: Lang): String = when (u.reason) {
        VoiceUnknownReason.EMPTY -> Strings.t("Chưa có câu lệnh nào", "No command yet", lang)
        // ⚠ spec `kachi-i18n-zh-th-ms.html` R5: bản EN cũ dạy *"turn on…/open…/show…"* — câu tiếng Anh mà ASR tiếng
        // Việt không nghe ra. Nay mọi tiếng đều chỉ ví dụ tiếng Việt ([VERB_EXAMPLES]); bản VI không đổi một byte.
        VoiceUnknownReason.NO_VERB -> Strings.fIn(
            lang,
            "Chưa rõ cần làm gì — thử {0}",
            "No action word — commands are in Vietnamese, e.g. {0}",
            VERB_EXAMPLES,
        )
        VoiceUnknownReason.NO_OBJECT -> Strings.t(
            "Không tìm thấy thứ đó trong xe hay trong ứng dụng",
            "No such thing on this car or in the app",
            lang,
        )
        VoiceUnknownReason.MISMATCH -> Strings.t(
            "Việc đó không đi với thứ đó — thử nêu mức, hoặc đổi động từ",
            "That action does not fit that thing — give a level, or use another verb",
            lang,
        )
        VoiceUnknownReason.OPEN_VOCAB -> Strings.t(
            "Phần này Kachi không tự làm offline (tên bài hát / điểm đến)",
            "Kachi does not do this offline (song names / destinations)",
            lang,
        )
        // Nói thẳng **chưa làm được** + đường làm được ngay. Trước 1.64 câu này lại MỞ app (xem
        // [VoiceUnknownReason.APP_CLOSE]) — làm đúng việc ngược lại còn tệ hơn nói là chưa làm được.
        VoiceUnknownReason.APP_CLOSE -> Strings.t(
            "Chưa đóng được app bằng giọng — bấm phím Home, hoặc mở app khác đè lên",
            "Closing an app by voice is not supported yet — press Home, or open another app over it",
            lang,
        )
        VoiceUnknownReason.DROPPED_CLAUSE -> Strings.t(
            "Đã bỏ qua vế không hiểu",
            "Skipped a clause I did not understand",
            lang,
        )
        // D3 — không tra ra tên (đường [gone] trả `null`) ⇒ câu chung, KHÔNG phải *"không tìm thấy thứ đó trong xe"*
        // (một câu sai sự thật: thứ đó có trên xe, chỉ là Kachi không làm).
        VoiceUnknownReason.FEATURE_GONE -> Strings.t("Tính năng này Kachi không làm", "Kachi does not do this", lang)
    }

    /**
     * Câu NÓI khi lượt nghe chính không ra chữ nào (spec R6 · inventory §H). Tấm chữ vẫn hiện `R.string` của màn
     * (`kachi_voice_nothing_heard`, theo tiếng giao diện); phần ĐỌC ra phải theo tiếng của GIỌNG NÓI — giao diện
     * zh/th/ms mà đọc chuỗi tài nguyên thì giọng Piper tiếng Việt phải đọc chữ Hán/Thái. Hai chữ y hệt hai bản VI/EN
     * của khoá tài nguyên ấy.
     */
    fun nothingHeard(lang: Lang): String = Strings.t("Không nghe rõ", "Didn't catch that", lang)
}
