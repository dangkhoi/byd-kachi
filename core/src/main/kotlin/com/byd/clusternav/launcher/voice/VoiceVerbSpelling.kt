package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ 2.93 — ĐỘNG TỪ ĐẦU VẾ ĐỌC THEO DẤU: chữ CÓ dấu chỉ là động từ khi đúng một cách viết của động từ ═══════════════════
 *
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car. Cùng luật [VoiceHomograph] của liên từ, áp cho [VoiceGrammar.VERBS] (khai không dấu):
 * bỏ dấu thì *"tất"* (tất cả) = `tat` = *"tắt"* ⇒ *"tất cả kính"* từng được đọc như một câu CÓ động từ tắt. Token **mang dấu**
 * chỉ là động từ khi chữ thô (chữ thường, NFC) đúng một cách viết có dấu của [SherpaSpokenWords.VERBS]; token **không dấu** (gõ,
 * nhật ký cũ) ⇒ không có dữ liệu để phân biệt ⇒ coi là động từ (hành vi cũ). Cụm của [VoiceGrammar.VERBS] chưa khai cách viết có
 * dấu nào (*"dẫn tới"* · *"đưa tôi đến"* · *"coi thử"* — bài canh của [SherpaSpokenWords] chỉ ép chiều *"mọi cách viết là một cụm
 * đã khai"*, không ép chiều ngược) cũng là *"không dữ liệu"* ⇒ hành vi cũ (senior review wave 2, [P3]).
 *
 * Từ mở đầu lệnh không thuộc [VoiceGrammar.VERBS] (*"hạ"* · *"kéo"* · *"nâng"* · *"lấy"*) đi CÙNG luật qua
 * [VoiceGrammar.ACTION_HEAD_WORDS] (senior review wave 2, [P1]): bỏ dấu thì *"hả"* = *"hạ"*, *"nắng"* = *"nâng"*.
 *
 * Ba chỗ hỏi CÙNG phép này: cổng tên bộ phận trần + ghép câu trả lời ([VoiceBareCover]) · mạch mượn động từ của câu ghép
 * ([VoiceClauseEllipsis]) · động từ mang sang lượt trả lời của câu hỏi lại ([VoiceClarify] `leadVerb`).
 */
internal object VoiceVerbSpelling {

    /** Khoá bỏ dấu của một cụm động từ (cùng phép tách của bộ phân tích) → các cách viết có dấu đã khai cho nó. */
    private val SPELLINGS: Map<String, Set<String>> = SherpaSpokenWords.VERBS.values.flatten()
        .groupBy({ v -> VoiceLexicon.tokenize(v).joinToString(" ") { it.norm } }, { VoiceHomograph.spelling(it) })
        .mapValues { (_, forms) -> forms.toSet() }

    /** Số từ của cụm [VoiceGrammar.VERBS] khớp ở đầu [t] (dài trước), `0` = không có — CHƯA xét dấu. */
    fun verbWords(t: List<Token>): Int =
        VoiceGrammar.VERBS.firstOrNull { VoiceLexicon.phraseAt(t, 0, it.first) }?.first?.size ?: 0

    /**
     * [n] từ đầu của [t] — một cụm [VoiceGrammar.VERBS] ([verbWords]) — có là một động từ THẬT không (xem KDoc lớp). `n ≤ 0` ⇒
     * `false`.
     */
    fun isVerb(t: List<Token>, n: Int): Boolean {
        if (n <= 0 || n > t.size) return false
        val head = t.subList(0, n)
        val norm = head.joinToString(" ") { it.norm }
        val spelled = head.joinToString(" ") { VoiceHomograph.spelling(it.raw) }
        val forms = SPELLINGS[norm] ?: return true   // cụm chưa khai cách viết có dấu ⇒ không dữ liệu ⇒ như cũ
        return spelled == norm || spelled in forms
    }

    /**
     * Số từ của cụm động từ HÀNH ĐỘNG thật ở đầu [t] (`0` = không có): [VoiceGrammar.actionVerbAt] (bật/tắt/mở/đóng/tăng/giảm/đặt
     * + từ mở đầu lệnh hướng kính *"hạ"* · *"kéo"* · *"nâng"* · *"lấy"*), dài theo cụm [VoiceGrammar.VERBS] khớp được, qua phép dấu
     * [isVerb] — hay [VoiceGrammar.ACTION_HEAD_WORDS] cho từ mở đầu không thuộc bảng động từ.
     *
     * ⚠ Senior review wave 2 [P1] — [ĐO off-car 07/10] bản trước trả `1` cho MỌI token bỏ dấu ra `ha`/`nang`… mà không xét dấu: câu
     * hỏi *"Mở hay đóng Kính lái?"* + trả lời *"hả"* (hả?) ⇒ [VoiceBareCover.verbFirst] ghép *"hả kính lái"* ⇒ `Control(win_lf, 1)`;
     * *"… 4 kính?"* + *"hả"* ⇒ hạ cả bốn kính; *"… Cốp sau?"* + *"hả"* ⇒ đóng cốp.
     */
    fun actionSpan(t: List<Token>): Int {
        if (!VoiceGrammar.actionVerbAt(t, 0)) return 0
        val n = verbWords(t)
        if (n == 0) return if (VoiceGrammar.ACTION_HEAD_WORDS.matches(t[0])) 1 else 0
        return if (isVerb(t, n)) n else 0
    }
}
