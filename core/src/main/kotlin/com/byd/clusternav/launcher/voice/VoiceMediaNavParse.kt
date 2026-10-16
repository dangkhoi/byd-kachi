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
    fun media(verb: VoiceVerb, after: List<Token>, original: String, terms: List<VoiceTerm> = emptyList()): VoiceIntent =
        VoiceTailClause.withTarget(after, VoiceAppKind.MUSIC, terms) { body, app ->
            when (verb) {
                VoiceVerb.PAUSE, VoiceVerb.OFF, VoiceVerb.CLOSE -> VoiceIntent.Media(VoiceMediaOp.PAUSE)
                VoiceVerb.NEXT -> VoiceIntent.Media(VoiceMediaOp.NEXT)
                VoiceVerb.PREV -> VoiceIntent.Media(VoiceMediaOp.PREV)
                VoiceVerb.PLAY, VoiceVerb.OPEN, VoiceVerb.ON ->
                    if (body.isEmpty()) VoiceIntent.Media(VoiceMediaOp.PLAY, app = app)
                    else VoiceIntent.Media(VoiceMediaOp.QUERY, VoiceTailClause.queryText(body), app)
                else -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
            }
        }

    /** (b''') *"tìm [kiếm] &lt;từ-nhạc&gt; &lt;tên bài&gt;"* ⇒ Media QUERY, `null` nếu không (⇒ NO_VERB hỏi lại). Ba cổng:
     * *"tìm"* · TỪ-NHẠC ngay sau (*"tìm trạm xăng"* không có ⇒ `null`, không đoán) · còn tên bài. */
    fun mediaSearch(t: List<Token>, terms: List<VoiceTerm> = emptyList()): VoiceIntent? {
        val head = SEARCH_HEADS.firstOrNull { VoiceLexicon.phraseAt(t, 0, it) } ?: return null
        val after = afterMediaWord(dropFillers(t.subList(head.size, t.size))) ?: return null
        if (after.isEmpty()) return null
        return media(VoiceVerb.PLAY, after, "", terms)
    }

    /**
     * ═══ VOICE-MEDIA-IMPLIED-VERB — *"&lt;từ-nhạc&gt; &lt;tên&gt; trên &lt;app nhạc&gt;"* KHÔNG động từ ⇒ phát (đo xe 16/10) ═══════════════
     *
     * [ĐO xe 16/10] câu dạng *"nhạc &lt;tên&gt; trên youtube"* (rụng/không nói *"mở"*) ⇒ `NO_VERB` ⇒ hỏi lại lệch sang đèn. Cùng
     * lẽ [VoiceSlotNoVerb]: khi câu có CẢ một TỪ-NHẠC ở đầu ([VoiceSynonyms.MEDIA_WORDS]) VÀ một mệnh đề *"trên/bằng &lt;app&gt;"*
     * khớp một app NHẠC đã biết ([VoiceTailClause.appAfterMarker] — đúng phép khớp của câu nói đủ), thì việc duy nhất hai mảnh ấy
     * cùng mang nghĩa là PHÁT ⇒ động từ suy ra được, không đoán. Đi CÙNG đường [media] với động từ [VoiceVerb.PLAY] (một đường).
     *
     * Hai cổng: thiếu mệnh đề app (*"nhạc to quá"* · *"nhạc &lt;tên&gt;"*) ⇒ `null` (vẫn `NO_VERB`, hỏi lại); còn tên trống
     * (*"nhạc trên youtube"*) ⇒ PLAY app ấy — đúng như *"mở nhạc trên youtube"*. Chi phí: một phép so tiền tố + một lượt
     * [VoiceTailClause.appAfterMarker] chỉ trên câu đã rơi tới nhánh không-động-từ (không phải đường nóng).
     */
    fun impliedPlay(t: List<Token>, terms: List<VoiceTerm> = emptyList()): VoiceIntent? {
        val after = afterMediaWord(t) ?: return null
        VoiceTailClause.appAfterMarker(after, VoiceAppKind.MUSIC, terms) ?: return null
        return media(VoiceVerb.PLAY, after, "", terms)
    }

    /** Phần sau TỪ-NHẠC dài nhất ở vị trí 0 của [t] (đã bỏ tiếng đệm đầu), `null` khi [t] không mở đầu bằng từ-nhạc. */
    private fun afterMediaWord(t: List<Token>): List<Token>? {
        val mw = MEDIA_HEADS.firstOrNull { VoiceLexicon.phraseAt(t, 0, it) } ?: return null
        return dropFillers(t.subList(mw.size, t.size))
    }

    private val MEDIA_HEADS: List<List<String>> = VoiceSynonyms.MEDIA_WORDS.map { it.split(" ") }.sortedByDescending { it.size }

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
    fun nav(after: List<Token>, places: List<String>, original: String, terms: List<VoiceTerm> = emptyList()): VoiceIntent =
        VoiceTailClause.withTarget(after, VoiceAppKind.NAV, terms) { body, app ->
            val saved = VoicePlaces.match(body.map { it.norm }, places)
            when {
                saved != null -> VoiceIntent.NavigateSaved(saved, app)
                body.isEmpty() -> VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
                else -> VoiceIntent.Nav(VoiceTailClause.text(body), app)
            }
        }

    private fun dropFillers(t: List<Token>): List<Token> = VoiceLexicon.dropLeadingFillers(t)
}
