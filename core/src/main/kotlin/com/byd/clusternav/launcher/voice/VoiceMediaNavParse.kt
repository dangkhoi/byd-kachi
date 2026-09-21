package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * Ba **bộ dựng ý-định** cho nhạc và dẫn đường, tách khỏi [VoiceIntentParser] khi tệp đó chạm trần 500 dòng
 * (code-only) — CLAUDE.md §4.1. Tách theo VAI: đây là chỗ *"giao chữ/điểm-đến cho một app ngoài"*, không phải
 * chỗ quyết động-từ × đối-tượng (vẫn ở [VoiceIntentParser]).
 *
 * `dropFillers` uỷ quyền cho [VoiceLexicon.dropLeadingFillers] — luật nằm CẠNH [VoiceLexicon.FILLERS], nên tệp này
 * và [VoiceIntentParser] không giữ hai bản sao có thể lệch nhau, mà cũng không phụ thuộc vào nhau.
 */
internal object VoiceMediaNavParse {

    private val SEARCH_HEADS = listOf(listOf("tim", "kiem"), listOf("tim"))

    /** *"nhạc"/"bài"* + động từ: có đuôi ⇒ tên bài/thể loại (từ vựng mở), không đuôi ⇒ lệnh phát đơn thuần. */
    fun media(verb: VoiceVerb, after: List<Token>, original: String): VoiceIntent =
        VoiceTailClause.withTarget(after, VoiceAppKind.MUSIC) { body, app ->
            when (verb) {
                VoiceVerb.PAUSE, VoiceVerb.OFF, VoiceVerb.CLOSE -> VoiceIntent.Media(VoiceMediaOp.PAUSE)
                VoiceVerb.NEXT -> VoiceIntent.Media(VoiceMediaOp.NEXT)
                VoiceVerb.PREV -> VoiceIntent.Media(VoiceMediaOp.PREV)
                VoiceVerb.PLAY, VoiceVerb.OPEN, VoiceVerb.ON ->
                    if (body.isEmpty()) VoiceIntent.Media(VoiceMediaOp.PLAY, app = app)
                    else VoiceIntent.Media(VoiceMediaOp.QUERY, VoiceTailClause.text(body), app)
                else -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
            }
        }

    /** (b''') *"tìm [kiếm] &lt;từ-nhạc&gt; &lt;tên bài&gt;"* ⇒ Media QUERY, `null` nếu không (⇒ NO_VERB hỏi lại). Ba cổng:
     * *"tìm"* · TỪ-NHẠC ngay sau (*"tìm trạm xăng"* không có ⇒ `null`, không đoán) · còn tên bài. */
    fun mediaSearch(t: List<Token>): VoiceIntent? {
        val head = SEARCH_HEADS.firstOrNull { VoiceLexicon.phraseAt(t, 0, it) } ?: return null
        val rest = dropFillers(t.subList(head.size, t.size))
        val mw = VoiceSynonyms.MEDIA_WORDS.map { it.split(" ") }.sortedByDescending { it.size }
            .firstOrNull { VoiceLexicon.phraseAt(rest, 0, it) } ?: return null
        val after = dropFillers(rest.subList(mw.size, rest.size))
        if (after.isEmpty()) return null
        return media(VoiceVerb.PLAY, after, "")
    }

    /**
     * Dẫn đường: phần đuôi là ĐIỂM ĐẾN, trừ mệnh đề *"bằng &lt;app&gt;"* ở cuối nếu có.
     *
     * ## Nơi ĐÃ LƯU được xét TRƯỚC điểm đến mở, và chỉ ở đây
     * Spec `kachi-voice-addresses.html` R2. Đây là **vị trí duy nhất** trong cả bộ phân tích tra sổ địa chỉ —
     * xem KDoc [VoicePlaces] về vì sao nhãn người dùng không được vào từ vựng chung.
     *
     * Khớp một nơi ⇒ [VoiceIntent.NavigateSaved] **kể cả khi sổ trống** (cách nói dựng sẵn *"về nhà"* vẫn ra
     * nhãn chuẩn): bắn chữ *"nhà"* cho app bản đồ là dẫn người ta tới một quán tên *"Nhà"* — máy làm một việc
     * khác việc được bảo. Tầng thi hành tra sổ và nói thẳng nếu chưa lưu (R4).
     */
    fun nav(after: List<Token>, places: List<String>, original: String): VoiceIntent =
        VoiceTailClause.withTarget(after, VoiceAppKind.NAV) { body, app ->
            val saved = VoicePlaces.match(body.map { it.norm }, places)
            when {
                saved != null -> VoiceIntent.NavigateSaved(saved, app)
                body.isEmpty() -> VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
                else -> VoiceIntent.Nav(VoiceTailClause.text(body), app)
            }
        }

    private fun dropFillers(t: List<Token>): List<Token> = VoiceLexicon.dropLeadingFillers(t)
}
