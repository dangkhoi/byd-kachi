package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.CtlSafetyPolicy
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ 2.93 VOICE-BARE-NOUN-IMPLICIT-VERB — tên BỘ PHẬN CHUYỂN ĐỘNG nói trần KHÔNG nhận động từ ngầm "mở" ═════════════
 *
 * Spec `docs/specs/kachi-293-voice.html` OQ5 (đề xuất) → §4.12 · §10. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Bệnh — [ĐO off-car 07/10, bộ phân tích thật]
 * Nhánh *"cả câu chính là TÊN của một việc"* của [VoiceIntentParser] gắn động từ ngầm [VoiceVerb.OPEN] cho mọi nút: *"cốp"* ⇒
 * `Control(trunk, 1)` = **mở cốp**, *"kính trước trái"* ⇒ hạ kính lái, *"cửa sổ trời"* ⇒ mở nóc. Động từ là từ dễ rụng nhất của
 * câu (nhạc, ồn đường, đầu câu bị cắt — cùng họ OQ5 camera), và với bộ phận chuyển động thì chiều đoán sai là chiều **mở**:
 * *"[đóng] tất cả kính"* rụng chữ *"đóng"* ⇒ *"tất cả kính"* ⇒ **hạ cả bốn kính** (*"tất"* bỏ dấu = *"tắt"*, cụm tên thắng động
 * từ rồi nhận động từ ngầm mở).
 *
 * ## Luật — dữ liệu sẵn có của TỪNG nút, không mã cứng nào (CLAUDE.md §7)
 * Nút "chuyển động" ([moves]) = kiểu [ControlKind.COVER] (mở/đóng kính·nóc·rèm·cốp theo KDoc `ControlRegistry`) **hoặc**
 * [CtlSafetyPolicy.MOVES_SLOWLY] (bộ phận có mô-tơ kéo — nguồn duy nhất, đã kê cả kính/nóc là nút TOGGLE từ khi owner bỏ nút COVER
 * 3 mức). [ĐO 07/10] hai nguồn cho đúng 14 mã — cốp · nóc · rèm · 4 kính riêng · nút gộp 4 kính · 5 nút nửa-kính · đóng-tất-cả;
 * COVER ⊆ MOVES_SLOWLY, và cả 14 ⊆ [CtlSafetyPolicy.CONFIRM_REQUIRED]. Nút bật/tắt điện–khí (đèn · điều hoà · sưởi ghế · khoá trẻ
 * em…) **giữ nguyên** động từ ngầm bật — quyết định coordinator 07/10 (thay đổi tối thiểu).
 *
 * "Không có động từ" đọc theo dấu ([VoiceVerbSpelling]): tên nút nuốt một động từ THẬT (*"đóng tất cả kính"* = tên nút
 * `windows_close_all`; *"mở nửa kính lái"*) giữ nguyên; *"tất cả kính"* (chữ *"tất"* mang dấu) là câu KHÔNG động từ.
 *
 * ## Ba chỗ dùng, cùng MỘT phép [bare]
 *  1. **cổng** ở nhánh tên-việc của bộ phân tích ⇒ `Unknown(NO_VERB)` (hỏi lại) thay vì động từ ngầm;
 *  2. **câu ghép** — vế chỉ là tên bộ phận mượn động từ của vế trước, không mượn được thì thành dòng *"đã bỏ qua"*:
 *     [VoiceClauseEllipsis] (cùng tệp ấy lo luôn nút bật/tắt — VOICE-CLAUSE-VERB-ELLIPSIS);
 *  3. **câu hỏi lại** ([ask] + [verbFirst]) — *"Mở hay đóng Kính lái?"*, trả lời một chữ *"mở"* là đủ: hỏi kiểu chung sẽ hỏi
 *     *"Kính nào — …?"* cho một câu đã nêu đúng kính, rồi chính câu trả lời lại rơi vào cổng (1) — vòng hỏi không bao giờ xong.
 */
internal object VoiceBareCover {

    /** Nút [id] mở/đóng một bộ phận CHUYỂN ĐỘNG (kính · nóc · rèm · cốp) — xem KDoc lớp. */
    fun moves(id: String): Boolean =
        ControlRegistry.byId(id)?.kind == ControlKind.COVER || CtlSafetyPolicy.movesSlowly(id)

    /**
     * Cụm đầu câu [head] là tên một bộ phận chuyển động nói **không kèm động từ thật** ⇒ không được nhận động từ ngầm.
     *
     * @param t token của vế (đã bỏ tiếng đệm), đúng thứ `headMatch` vừa khớp tại vị trí 0.
     * @param verbWords số từ của cụm động từ khớp tại vị trí 0 (0 = không có) — khi > 0 nó nằm TRONG [head] (cụm tên dài hơn).
     */
    fun bare(t: List<Token>, head: VoiceTerm, verbWords: Int): Boolean =
        head.kind == VoiceTermKind.CONTROL && moves(head.id) && !VoiceVerbSpelling.isVerb(t, verbWords)

    /**
     * Câu hỏi lại cho một câu mà **cả câu** là tên bộ phận chuyển động trần: *"Mở hay đóng &lt;nhãn&gt;?"*, mang theo đúng các từ
     * của cái tên để [verbFirst] dán câu trả lời *"mở"* hay *"đóng"* vào TRƯỚC chúng. `null` ⇒ không phải ca này (câu hỏi, có đuôi,
     * có động từ thật, không phải bộ phận chuyển động) ⇒ [VoiceClarify] hỏi theo đường chung như cũ.
     */
    fun ask(text: String, terms: List<VoiceTerm>, lang: Lang): VoiceClarify.Ask? {
        val raw = VoiceLexicon.tokenize(text)
        if (raw.isEmpty() || VoiceQuestion.isQuestion(raw) || VoiceQuestion.looksAsked(raw)) return null
        val t = VoiceLexicon.dropLeadingFillers(VoiceLexicon.stripCourtesy(raw).ifEmpty { raw })
        val head = bareHead(t, terms) ?: return null
        val label = ControlRegistry.byId(head.id)?.labelIn(lang) ?: return null
        return VoiceClarify.Ask(Strings.fIn(lang, "Mở hay đóng {0}?", "Open or close {0}?", label), t.map { it.raw })
    }

    /**
     * Ghép câu trả lời cho câu hỏi của [ask] ([carry] = đúng một tên bộ phận chuyển động trần; mọi carry khác ⇒ `null` ⇒
     * [VoiceClarify.combine] ghép như cũ):
     *  • trả lời = động từ hành động + chỉ [particle] (*"mở"* · *"đóng lại"* · *"mở một nửa"*) ⇒ `động từ + tên + phần còn lại`
     *    (*"mở kính lái một nửa"* — tên đứng NGAY sau động từ, để *"mở hết"* không thành *"mở hết kính"* = gói mở cả bốn kính);
     *  • còn lại ⇒ giữ NGUYÊN câu trả lời: một câu của riêng nó (*"bật điều hòa"* · *"mở youtube"* — dán tên vào là biến nó thành
     *    *"mở cốp …"*), lời thôi (*"thôi"* ⇒ kết thúc như mọi lượt), hay nhắc lại cái tên (⇒ bộ phân tích hỏi lại lần nữa).
     */
    fun verbFirst(carry: List<String>, answer: String): String? {
        val c = VoiceLexicon.tokenize(carry.joinToString(" "))
        if (bareHead(c, VoiceGrammar.terms()) == null) return null
        val a = VoiceLexicon.tokenize(answer)
        val n = VoiceVerbSpelling.actionSpan(a)
        if (n == 0 || !a.subList(n, a.size).all { particle(it) }) return answer.trim()
        return (a.subList(0, n) + c + a.subList(n, a.size)).joinToString(" ") { it.raw }
    }

    /**
     * Từ được phép đứng sau động từ trong câu trả lời *"mở/đóng"*: tiếng đệm ([VoiceLexicon.FILLERS]), con số
     * ([VoiceLexicon.NUMBER_WORDS] · chữ số · `%`), và [ANSWER_PARTICLES]. Không từ nào trong số đó gọi tên một bộ phận ⇒ không
     * thể lén mang một đối tượng thứ hai vào câu ghép.
     */
    private fun particle(t: Token): Boolean =
        t.norm in ANSWER_PARTICLES || t.norm in VoiceLexicon.FILLERS || t.norm in VoiceLexicon.NUMBER_WORDS ||
            t.norm.all { it.isDigit() || it == '%' }

    /**
     * Tiểu từ theo sau động từ khi trả lời *"Mở hay đóng …?"* (bỏ dấu): *"mở **ra**"* · *"đóng **lại**"* · *"mở **hết**"* ·
     * *"mở **luôn**"* · *"mở **đi**"* · *"đóng **nhé**"* · *"mở **một nửa**"* · *"mở năm mươi **phần trăm**"* · *"kéo **lên**"* ·
     * *"hạ **xuống**"* · *"mở **ngay**"* · *"đóng **giùm**"*.
     */
    private val ANSWER_PARTICLES: Set<String> = setOf(
        "ra", "lai", "het", "luon", "di", "nhe", "nha", "nua", "phan", "tram", "len", "xuong", "ngay", "voi", "gium", "dum",
    )

    /**
     * Câu mượn động từ ra một lệnh GHI lên bộ phận chuyển động: `Control` của một mã [moves], hoặc gói lệnh (*"mở hết kính"* =
     * `mac_win_open_all` — cụm *"hết kính"* nối với động từ thành tên gói). Mọi thứ khác (đọc, nhạc, hồ sơ…) ⇒ không nhận.
     */
    fun writesCover(r: VoiceIntent): Boolean =
        (r is VoiceIntent.Control && moves(r.id)) || r is VoiceIntent.Macro

    /** CẢ [t] là tên một bộ phận chuyển động nói trần ([VoiceClauseEllipsis.nameOnly] + [moves]) ⇒ cụm ấy, không thì `null`. */
    fun bareHead(t: List<Token>, terms: List<VoiceTerm>): VoiceTerm? =
        VoiceClauseEllipsis.nameOnly(t, terms)?.takeIf { moves(it.id) }
}
