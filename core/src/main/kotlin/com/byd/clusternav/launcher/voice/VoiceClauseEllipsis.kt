package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ 2.93 · CÂU GHÉP RỤNG ĐỘNG TỪ Ở VẾ SAU — *"tắt điều hòa và đèn đọc"* = tắt cả hai ═════════════════════════════════
 *
 * Spec `docs/specs/kachi-293-voice.html` §4.12 · §10. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Bệnh — [ĐO off-car 07/10, bộ phân tích thật]
 * Mỗi vế của câu ghép được hiểu RIÊNG; vế chỉ là một cái TÊN thì nhận động từ ngầm "mở/bật" của nhánh tên-việc — bất kể vế trước
 * nói gì: *"tắt điều hòa và đèn đọc"* ⇒ tắt điều hoà + **BẬT** đèn đọc; *"đóng kính lái và cửa sổ trời"* ⇒ đóng kính + **MỞ** nóc.
 * Người Việt lược động từ ở vế sau như mọi ngôn ngữ (*"tắt A và B"*), nên vế sau phải mượn động từ của vế trước.
 *
 * ## Luật — vế mà CẢ vế là tên một nút ([nameOnly]), không động từ thật của riêng nó, mượn cụm động từ của vế MANG động từ gần
 * nhất phía trước; mỗi họ nút một cổng nhận:
 *  • **bộ phận chuyển động** ([VoiceBareCover.moves]) — vế ấy hôm nay là `Unknown(NO_VERB)` (cổng [VoiceBareCover]); mượn mọi động
 *    từ HÀNH ĐỘNG ([VoiceVerbSpelling.actionSpan], gồm *"hạ"*), nhận khi câu mượn ra một lệnh ghi lên bộ phận chuyển động
 *    ([VoiceBareCover.writesCover]); không mượn được ⇒ [dropBare];
 *  • **nút khác** (bật/tắt điện–khí, chọn mức, bấm) — vế ấy hôm nay ra `Control(nút, …)` bằng động từ ngầm; chỉ mượn họ động từ
 *    [TOGGLE_VERBS] (quyết định điều phối 07/10), nhận khi câu mượn ra `Control` của CHÍNH nút ấy (động từ hợp với kiểu nút). Vế
 *    trước là nhạc · đọc · app · tăng/giảm, hay câu mượn không hợp ⇒ giữ NGUYÊN hành vi hôm nay (bật ngầm) — KHÔNG hỏi lại.
 * Vế có động từ thật của riêng nó thì cập nhật mạch: động từ hành động mở đầu LỆNH CUỐI mà bộ phân tích hiểu được trong vế
 * ([lastCommandVerb] — một vế có thể mang nhiều lệnh không liên từ) ⇒ thành động từ để mượn; vế mở bằng động từ khác loại
 * (*"phát"*, *"xem"*, *"dẫn đường"*) ⇒ cắt mạch. Chữ *"tất"* mang dấu không phải động từ ([VoiceVerbSpelling]) ⇒ không cắt mạch.
 */
internal object VoiceClauseEllipsis {

    /** Họ động từ mà vế tên nút KHÔNG chuyển động được mượn — bật/tắt/mở/đóng (điều phối 07/10). */
    private val TOGGLE_VERBS = setOf(VoiceVerb.ON, VoiceVerb.OFF, VoiceVerb.OPEN, VoiceVerb.CLOSE)

    /** Ý định của từng vế [parts] theo [seg], đã cho vế chỉ-là-tên mượn động từ — xem KDoc lớp. */
    fun inherit(parts: List<List<Token>>, terms: List<VoiceTerm>, seg: (List<Token>) -> VoiceIntent): List<VoiceIntent> {
        var verb: List<Token>? = null
        return parts.map { p ->
            val t = VoiceLexicon.dropLeadingFillers(p)
            val got = seg(p)
            val out = verb?.let { v -> borrow(t, v, got, terms, seg) } ?: got
            val last = lastCommandVerb(t, got, seg)
            when {
                last != null -> verb = last                                                  // động từ của LỆNH CUỐI ⇒ mạch mới
                VoiceVerbSpelling.isVerb(t, VoiceVerbSpelling.verbWords(t)) -> verb = null   // vế mở bằng động từ khác loại ⇒ cắt
            }
            out
        }
    }

    /**
     * Cụm động từ HÀNH ĐỘNG mở đầu LỆNH CUỐI của vế [t] (vế ấy tự hiểu ra [got]), hoặc `null`: quét các vị trí có động từ hành
     * động ([VoiceVerbSpelling.actionSpan]) từ cuối vế lên, nhận vị trí đầu tiên mà phần từ đó tới hết vế phân tích RA một ý định —
     * cùng cổng *"mọi vế phải hiểu được"* của câu MIX ([VoiceControlParse.multiVerbSplit]). Vị trí 0 dùng lại [got].
     *
     * ⚠ Senior review wave 2 [P1] — [ĐO off-car 07/10, bộ phân tích thật] bản trước lấy chữ hành động CUỐI BẤT KỲ, kể cả chữ NẰM
     * TRONG một tên / điểm đến: *"dẫn đường đến hạ long và kính lái"* ⇒ MỞ kính lái (*"hạ"* của Hạ Long) · *"dẫn đường đến hà nội
     * và cốp"* ⇒ đóng cốp · *"bat dieu hoa tu dong va den doc"* (gõ không dấu) ⇒ TẮT đèn đọc, *"… va kinh lai"* ⇒ ĐÓNG kính
     * (*"dong"* của *"tự động"*) · *"mở rèm che nắng và kính lái"* ⇒ bỏ vế kính (*"nắng"* = *"nâng"*). Một chữ chỉ là động từ
     * của mạch khi nó MỞ ĐẦU một lệnh mà bộ phân tích hiểu được.
     */
    private fun lastCommandVerb(t: List<Token>, got: VoiceIntent, seg: (List<Token>) -> VoiceIntent): List<Token>? {
        for (j in t.indices.reversed()) {
            val from = t.subList(j, t.size)
            val n = VoiceVerbSpelling.actionSpan(from)
            if (n == 0) continue
            if ((if (j == 0) got else seg(from)) !is VoiceIntent.Unknown) return from.subList(0, n)
        }
        return null
    }

    /** Ý định mượn động từ [v] cho vế [t] (hôm nay ra [got]), hoặc `null` = giữ [got]. */
    private fun borrow(
        t: List<Token>,
        v: List<Token>,
        got: VoiceIntent,
        terms: List<VoiceTerm>,
        seg: (List<Token>) -> VoiceIntent,
    ): VoiceIntent? {
        val head = nameOnly(t, terms) ?: return null
        if (VoiceBareCover.moves(head.id)) {
            return if (got is VoiceIntent.Unknown) seg(v + t).takeIf { VoiceBareCover.writesCover(it) } else null
        }
        val family = VoiceGrammar.VERBS.firstOrNull { VoiceLexicon.phraseAt(v, 0, it.first) }?.second
        if (family !in TOGGLE_VERBS || got !is VoiceIntent.Control || got.id != head.id) return null
        return (seg(v + t) as? VoiceIntent.Control)?.takeIf { it.id == head.id }
    }

    /**
     * Sau [inherit]: vế CHƯA hiểu **chỉ toàn** là tên bộ phận chuyển động trần (không mượn được động từ — *"phát nhạc và cốp"* ·
     * *"đèn đọc và cốp"*) mà còn ≥ 1 vế hiểu được ⇒ giữ các vế hiểu được + MỘT dòng *"đã bỏ qua «…»"* ([VoiceDroppedNote]). Không
     * thế thì luật câu ghép cũ phân tích lại NGUYÊN câu — và [ĐO 07/10] *"phát nhạc và cốp"* thành `Media(QUERY "và cốp")`.
     * `null` ⇒ không phải ca này ⇒ luật cũ.
     */
    fun dropBare(parts: List<List<Token>>, each: List<VoiceIntent>, terms: List<VoiceTerm>): List<VoiceIntent>? {
        val kept = each.filterNot { it is VoiceIntent.Unknown }
        if (kept.isEmpty()) return null
        val allBare = parts.indices.all { i ->
            each[i] !is VoiceIntent.Unknown || VoiceBareCover.bareHead(VoiceLexicon.dropLeadingFillers(parts[i]), terms) != null
        }
        return if (allBare) kept + VoiceDroppedNote.droppedNote(parts, each, kept.first()) else null
    }

    /**
     * CẢ [t] là TÊN một nút (cụm CONTROL khớp tại vị trí 0 theo đúng phép của bộ phân tích, phủ hết vế), KHÔNG kèm động từ thật
     * ⇒ cụm ấy, không thì `null`. Tên nút mở đầu bằng một động từ HÀNH ĐỘNG thật — *"đóng tất cả kính"* · *"lấy gió trong"* (nhãn
     * nút `recirc`: chính chữ *"lấy"* là việc) — đã tự mang động từ, không phải tên trần: [ĐO 07/10] coi nó là tên trần thì *"tắt sấy
     * kính và lấy gió trong"* mượn *"tắt"* ⇒ TẮT tuần hoàn, ngược điều người nói xin.
     */
    fun nameOnly(t: List<Token>, terms: List<VoiceTerm>): VoiceTerm? {
        if (t.isEmpty() || VoiceVerbSpelling.actionSpan(t) > 0) return null
        val verbWords = VoiceVerbSpelling.verbWords(t)
        val head = VoiceIntentParser.headMatch(t, terms, verbWords) ?: return null
        return head.takeIf {
            it.kind == VoiceTermKind.CONTROL && it.words.size == t.size && !VoiceVerbSpelling.isVerb(t, verbWords)
        }
    }
}
