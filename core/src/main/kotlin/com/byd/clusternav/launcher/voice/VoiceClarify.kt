package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.TelemetryRegistry

/**
 * ═══ V3 · HỎI LẠI CHO TỚI KHI HIỂU ═══════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` **R8**. Owner 2026-09-16 (**D2**): *"hỏi lại cho tới khi hiểu
 * rồi làm"*. Thuần Kotlin (`:core`) ⇒ kiểm off-car; `:app` chỉ lo việc mở micro lượt hai.
 *
 * ## Vì sao một câu hỏi NGẮN, không phải câu *"không hiểu"*
 * Tới 1.65 mọi câu không hiểu đều ra **một** dòng (`VoiceReply.unknown`) và phiên chết ở đó. Người lái phải bấm
 * lại, nói lại **cả câu** — trong khi thứ máy thiếu thường chỉ là **một từ**: nghe được *"mở"* mà không nghe được
 * đối tượng, hoặc nghe được *"kính"* mà không biết kính nào. Hỏi đúng cái thiếu thì câu trả lời dài một từ.
 *
 * ## Ba dạng câu hỏi, và vì sao KHÔNG có dạng thứ tư
 *  1. **Thiếu đối tượng** ([VoiceUnknownReason.NO_OBJECT]) ⇒ *"Bật gì?"* — lấy đúng động từ người ta vừa nói.
 *  2. **Đối tượng NHẬP NHẰNG** — một từ đầu (*"kính"*) là chữ mở đầu của **≥ 2** cụm trỏ tới **≥ 2** mã khác nhau
 *     ⇒ *"Kính nào — Kính trước-trái, Kính trước-phải, hay …?"*. Danh sách sinh từ chính từ vựng, **không** chép
 *     tay (CLAUDE.md §7): thêm một nút kính ở bộ đăng ký là câu hỏi tự dài ra.
 *  3. **Còn lại** ⇒ *"Chưa rõ — nói lại giúp"*. Một câu thật thà còn hơn một câu hỏi đoán mò.
 * Dạng thứ tư (*"ý anh là X phải không?"*) cố ý không có: đoán một mã rồi hỏi xác nhận là đưa cổng CONFIRM vào
 * đúng chỗ nó không thuộc về, và [ĐO xe] cổng ấy vốn đã là nơi người lái bỏ cuộc.
 *
 * ## Trần [MAX_ROUNDS] = 2
 * Hỏi mãi là một vòng lặp mở trong lúc người ta đang lái. Hết trần thì [giveUp] nói một câu có ích (*"thử nói
 * «bật đèn đọc»"*) rồi đóng — không im lặng, và không hỏi lần thứ ba.
 */
object VoiceClarify {

    /** Tối đa hai lượt hỏi cho một phiên — xem KDoc lớp. */
    const val MAX_ROUNDS = 2

    /**
     * Một câu hỏi ngắn + phần ngữ cảnh để **ghép** với câu trả lời.
     *
     * @property question chữ hiện trên tấm chữ (và đọc lên, nếu máy có giọng).
     * @property carry các từ ĐÃ hiểu của lượt trước, theo đúng thứ tự người ta nói. [combine] dán câu trả lời vào
     *   sau chúng. Rỗng ⇒ câu trả lời đứng một mình.
     */
    data class Ask(val question: String, val carry: List<String>)

    /**
     * Câu hỏi cho một ý định không hiểu, hoặc `null` khi **không nên hỏi**.
     *
     * `null` ở bốn ca, và cả bốn đều là *"hỏi cũng không giúp gì"*: câu rỗng ([VoiceUnknownReason.EMPTY] — không
     * có gì để bám), từ vựng mở ([VoiceUnknownReason.OPEN_VOCAB] — Kachi cố ý không làm offline), *"đóng app"*
     * ([VoiceUnknownReason.APP_CLOSE] — hiểu đúng nhưng chưa có cơ chế), và vế bị bỏ của một câu ghép
     * ([VoiceUnknownReason.DROPPED_CLAUSE] — đã có một ý định thật chạy rồi).
     *
     * @param round lượt hỏi thứ mấy (0 = chưa hỏi lần nào). ≥ [MAX_ROUNDS] ⇒ `null`.
     * @param terms từ vựng đang dùng (truyền vào để `:app` khỏi dựng lại, và để test bơm bảng giả).
     */
    fun ask(
        unknown: VoiceIntent.Unknown,
        round: Int,
        terms: List<VoiceTerm> = VoiceGrammar.terms(),
    ): Ask? {
        if (round >= MAX_ROUNDS) return null
        when (unknown.reason) {
            VoiceUnknownReason.EMPTY,
            VoiceUnknownReason.OPEN_VOCAB,
            VoiceUnknownReason.APP_CLOSE,
            VoiceUnknownReason.DROPPED_CLAUSE,
            -> return null
            else -> Unit
        }
        val tokens = VoiceLexicon.tokenize(unknown.text).filterNot { it.norm in VoiceLexicon.FILLERS }
        if (tokens.isEmpty()) return null

        ambiguity(tokens, terms)?.let { return it }

        if (unknown.reason == VoiceUnknownReason.NO_OBJECT) {
            val verb = tokens.first().raw
            return Ask(
                Strings.t("${capitalize(verb)} gì?", "${capitalize(verb)} what?"),
                listOf(verb),
            )
        }
        return Ask(vague(), emptyList())
    }

    /** Câu chung khi không bám được vào đâu. */
    fun vague(): String = Strings.t("Chưa rõ — nói lại giúp", "Not sure — say that again")

    /**
     * Câu **bỏ cuộc lịch sự** sau [MAX_ROUNDS] lượt. Nêu một câu mẫu có thật thay vì *"không hiểu"* lần thứ ba —
     * [ĐO] mẫu UX Kiki §5: sau hai lượt vật lộn thì thứ giúp được là **một ví dụ**, không phải một lời xin lỗi.
     */
    fun giveUp(): String = Strings.t(
        "Vẫn chưa rõ — thử nói \"bật đèn đọc\"",
        "Still not sure — try saying \"turn on the reading light\"",
    )

    /**
     * Ghép câu trả lời của lượt hỏi với ngữ cảnh đã có: `["mở"] + "kính lái"` → `"mở kính lái"`.
     *
     * Trả nguyên [answer] khi nó **đã** chứa đủ ngữ cảnh (người ta trả lời cả câu: *"mở kính lái"*). Không kiểm
     * điều đó thì câu ghép thành *"mở mở kính lái"* — bộ phân tích đọc `mo` hai lần và rơi lại vào NO_OBJECT,
     * tức lượt hỏi làm mọi thứ tệ hơn đúng ở ca người dùng hợp tác nhất.
     *
     * ## [SOÁT 2026-09-16 · P3] Hai vế của phép so phải được **cắt từ cùng một cách**
     * Bản trước so `VoiceLexicon.tokenize(a).map { it.norm }` (một phần tử = **một từ**) với
     * `carry.map { deaccent(it) }` (một phần tử = **nguyên chuỗi**, còn nguyên khoảng trắng bên trong). Một phần
     * tử [carry] nhiều từ vì thế **không bao giờ khớp** ⇒ vế đã có bị ghép lại lần nữa (*"mở kính mở kính lái"*).
     * Hôm nay không ai thấy vì [carry] luôn là `listOf(verb)` một từ (:76-80) — nhưng đó là một **bất biến ngầm**
     * không được ghi ở đâu và không được test nào giữ: đúng thứ hỏng im lặng ở lần ai đó mang theo cả cụm
     * *"bật đèn"*. Nay cả hai vế đi qua **cùng** [VoiceLexicon.tokenize].
     */
    fun combine(carry: List<String>, answer: String): String {
        val a = answer.trim()
        if (a.isEmpty()) return carry.joinToString(" ")
        if (carry.isEmpty()) return a
        val answerNorms = VoiceLexicon.tokenize(a).map { it.norm }
        val carryNorms = carry.flatMap { c -> VoiceLexicon.tokenize(c).map { it.norm } }
        if (carryNorms.isNotEmpty() && answerNorms.take(carryNorms.size) == carryNorms) return a
        return (carry + a).joinToString(" ")
    }

    // ── nhập nhằng ───────────────────────────────────────────────────────────────────────────────

    /**
     * Có từ nào trong câu là **chữ mở đầu** của nhiều cụm trỏ tới nhiều mã khác nhau không.
     *
     * Chỉ xét cụm **dài hơn một từ**: cụm một từ mà trùng thì nó đã là một mã hoàn chỉnh, và bộ phân tích đã chọn
     * xong — không còn gì để hỏi. Ngược lại *"kính"* là chữ mở đầu của *"kính trước-trái"*, *"kính trước-phải"*…
     * nên nó đúng là chỗ thiếu một từ.
     */
    private fun ambiguity(tokens: List<VoiceLexicon.Token>, terms: List<VoiceTerm>): Ask? {
        for (tk in tokens) {
            // ⚠ BỎ QUA ĐỘNG TỪ. [ĐO off-car] không có dòng này thì *"mở kính"* hỏi *"Mở nào — Mô-men mô-tơ
            // trước, Mở cửa cảnh báo trái, hay Mở cửa cảnh báo phải?"*: chữ `mo` là chữ mở đầu của hàng chục
            // nhãn, nên câu hỏi bám vào đúng cái từ mà máy ĐÃ hiểu. Thứ thiếu là đối tượng, không phải động từ.
            if (tk.norm in VERB_HEADS) continue
            val ids = terms
                .filter { it.words.size > 1 && it.words.first() == tk.norm && it.kind in ASKABLE }
                .map { it.id }
                .distinct()
                // ⚠ Đọc lựa chọn theo THỨ TỰ DANH MỤC, không theo thứ tự [terms].
                //
                // [terms] xếp **cụm dài trước** (luật L-RE2 của [VoiceGrammar]) — một thứ tự đúng cho việc so
                // khớp và vô nghĩa cho một câu hỏi. [ĐO off-car] *"lọc"* trước đây hỏi *"Lọc nào — Lọc ngay hay
                // Lọc bụi?"*: `pm25_clean_now` lên trước chỉ vì nó tình cờ có một cách nói BA từ
                // (*"lọc không khí ngay"*), không vì nó quan trọng hơn. Thứ tự danh mục là thứ tự các nút nằm
                // trên màn hình, tức thứ tự người lái đã quen ⇒ *"Lọc bụi hay Lọc ngay?"*, đúng câu owner nêu.
                .sortedBy { rank(it) }
            if (ids.size < 2) continue
            val labels = ids.mapNotNull { labelOf(it) }.distinct().take(MAX_CHOICES)
            if (labels.size < 2) continue
            return Ask(question(tk.raw, labels), listOf())
        }
        return null
    }

    /** Chỉ hỏi về thứ **bấm/đọc được**; hồ sơ/app/nhạc/điểm đến có đường hỏi riêng hoặc không hỏi được. */
    private val ASKABLE = setOf(VoiceTermKind.CONTROL, VoiceTermKind.TELEMETRY)

    /**
     * Từ mở đầu của MỌI động từ — sinh từ [VoiceGrammar.VERBS], không chép tay.
     *
     * Chép tay một danh sách động từ thứ hai ở đây là đúng bẫy mà [VoiceSynonyms] sinh ra để chặn: thêm một động
     * từ ở bảng kia mà quên ở đây thì câu hỏi lại bám vào chính động từ ấy, và cái sai đó **im lặng**.
     */
    private val VERB_HEADS: Set<String> by lazy { VoiceGrammar.VERBS.map { it.first.first() }.toSet() }

    /** Nhiều hơn ba lựa chọn thì câu hỏi dài hơn câu lệnh — người lái không nghe hết. */
    private const val MAX_CHOICES = 3

    private fun labelOf(id: String): String? =
        ControlRegistry.byId(id)?.displayLabel ?: TelemetryRegistry.byId(id)?.displayLabel

    /**
     * Vị trí của một mã trong danh mục — nút trước, datum sau; mã lạ xuống cuối.
     *
     * Sinh **một lần** từ chính hai bộ đăng ký (không chép tay một thứ tự thứ hai): thêm/đổi chỗ một dòng
     * registry là câu hỏi tự đọc theo thứ tự mới.
     */
    private fun rank(id: String): Int = RANK[id] ?: Int.MAX_VALUE

    private val RANK: Map<String, Int> by lazy {
        val out = HashMap<String, Int>(ControlRegistry.ALL.size + TelemetryRegistry.ALL.size)
        ControlRegistry.ALL.forEachIndexed { i, c -> out[c.id] = i }
        TelemetryRegistry.ALL.forEachIndexed { i, t -> out.putIfAbsent(t.id, ControlRegistry.ALL.size + i) }
        out
    }

    private fun question(head: String, labels: List<String>): String {
        val list = when (labels.size) {
            2 -> Strings.t("${labels[0]} hay ${labels[1]}", "${labels[0]} or ${labels[1]}")
            else -> Strings.t(
                labels.dropLast(1).joinToString(", ") + ", hay " + labels.last(),
                labels.dropLast(1).joinToString(", ") + ", or " + labels.last(),
            )
        }
        return Strings.t("${capitalize(head)} nào — $list?", "Which ${head.lowercase()} — $list?")
    }

    private fun capitalize(s: String): String =
        if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)
}
