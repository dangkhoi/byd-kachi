package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlDef
import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ V1 · BỘ PHÂN TÍCH Ý ĐỊNH — TẤT ĐỊNH, THUẦN KOTLIN ════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R1–R3. `:core`, cấm `android.*` ⇒ 100% kiểm off-car.
 *
 * ## Hình dạng: `động từ × KIỂU ĐỐI TƯỢNG`
 * Lấy thẳng từ [ĐO] RE Kiki §7(c): bộ động từ của người Việt khi nói với xe rất **nhỏ và đều**, nhưng *"Mở"* thì
 * **quá tải nặng** (mở cửa · mở kính · mở app · mở nhạc · mở Cài đặt). Nên động từ **không** quyết định một mình:
 * nó chỉ chọn *loại việc*, còn việc cụ thể do **kiểu của đối tượng** quyết ([VoiceTermKind]).
 *
 * ## Ba luật giải nhập nhằng, không luật nào là `if (id == "…")`
 *  1. **Dãy từ dài nhất thắng** — giải ba cặp nhãn lồng nhau của L-RE2 (xem KDoc [VoiceGrammar]).
 *  2. **Loại động từ chọn ứng viên** — cùng một cụm *"Kính trước-trái"* trỏ tới CẢ datum (xem % mở) lẫn nút
 *     (đóng/mở); *"xem"* lấy datum, *"mở"* lấy nút. 18 nhãn trùng của `CapabilityCatalog.collidingLabels()` đều
 *     giải bằng đúng luật này, không cần bảng ngoại lệ nào.
 *  3. **Từ vựng MỞ thì nói thẳng là mở** — tên bài hát / điểm đến không nằm trong tập đóng; trả
 *     [VoiceUnknownReason.OPEN_VOCAB] hoặc [VoiceIntent.Nav]/[VoiceIntent.Media] kèm nguyên văn, **không** đoán.
 */
object VoiceIntentParser {

    /** Liên từ nối hai lệnh trong một câu ([ĐO] RE Kiki §7c #42: *"… và …"* là tính năng hạng nhất). */
    private val CONNECTORS = setOf("va", "roi", "and", "then")

    /**
     * Phân tích một câu, trả về **danh sách** ý định theo đúng thứ tự nói (R3).
     *
     * ## Luật tách câu ghép — và vì sao nó không tách bừa
     * Tách ở *"và"/"rồi"* rồi phân tích từng vế; **chỉ chấp nhận** nếu **mọi** vế đều hiểu được. Ngược lại thì
     * phân tích lại NGUYÊN câu như một vế.
     *
     * [ĐO] mẫu câu Kiki #10 là *"Mở bài Cỏ dại và hoa dành dành"* — chữ *"và"* nằm **trong tên bài hát**. Tách vô
     * điều kiện sẽ cắt đôi tên bài và gửi đi một nửa. Luật "mọi vế phải hiểu được" tự xử lý ca đó (*"hoa dành
     * dành"* không có động từ ⇒ quay về nguyên câu) mà không phải biết bài hát nào tên có chữ "và".
     */
    fun parse(text: String, profiles: List<String> = emptyList(), apps: List<String> = emptyList()): List<VoiceIntent> {
        val terms = VoiceGrammar.terms(profiles, apps)
        val all = VoiceLexicon.tokenize(text)
        if (all.isEmpty()) return listOf(VoiceIntent.Unknown(VoiceUnknownReason.EMPTY, text))

        val parts = splitOnConnectors(all)
        if (parts.size > 1) {
            val each = parts.map { parseTokens(it, terms, text) }
            if (each.none { it is VoiceIntent.Unknown }) return each
        }
        return listOf(parseTokens(all, terms, text))
    }

    /** Phân tích MỘT vế (không tách tiếp) — cửa dùng cho test và cho chỗ đã tự tách. */
    fun parseOne(text: String, profiles: List<String> = emptyList(), apps: List<String> = emptyList()): VoiceIntent =
        parseTokens(VoiceLexicon.tokenize(text), VoiceGrammar.terms(profiles, apps), text)

    private fun splitOnConnectors(t: List<Token>): List<List<Token>> {
        val out = ArrayList<List<Token>>()
        var start = 0
        t.forEachIndexed { i, tok ->
            if (tok.norm in CONNECTORS) {
                if (i > start) out.add(t.subList(start, i))
                start = i + 1
            }
        }
        if (start < t.size) out.add(t.subList(start, t.size))
        return out
    }

    // ── Thân bộ phân tích ────────────────────────────────────────────────────────────────────────

    @Suppress("ReturnCount")
    private fun parseTokens(raw: List<Token>, terms: List<VoiceTerm>, original: String): VoiceIntent {
        val t = dropFillers(raw)
        if (t.isEmpty()) return VoiceIntent.Unknown(VoiceUnknownReason.EMPTY, original)

        // (a) Cụm hỏi (*"… bao nhiêu?"*) — người Việt hỏi xe bằng cụm hỏi, không bằng động từ đứng đầu.
        val ask = askAt(t)
        if (ask != null) {
            val (at, len) = ask
            val body = dropFillers(t.subList(0, at) + t.subList(at + len, t.size))
            return objectOnlyRead(body, terms, original)
        }

        // (b) Động từ đứng đầu, khớp cụm DÀI nhất.
        val verbHit = VoiceGrammar.VERBS.firstOrNull { VoiceLexicon.phraseAt(t, 0, it.first) }

        // (b') CẢ CÂU chính là TÊN của một việc ⇒ tên thắng động từ.
        //
        // [ĐO] hai họ tên thật trong bộ đăng ký bắt đầu bằng một động từ: gói lệnh *"Mở hết kính"* / *"Đóng hết
        // kính"* / *"Rời xe"* (`ActionMacros`) và nút *"Mở khoá cửa"* (`ControlRegistry.door`). Tách động từ ra
        // trước thì *"Mở hết kính"* rơi vào nút `windows_all` — nút **chưa kiểm trên xe**, trong khi gói lệnh cùng
        // tên gồm 4 nút **đã chạy thật** (xem KDoc `ActionMacros`); còn *"Mở khoá cửa"* rơi vào nút `lock` với
        // nghĩa ngược. Luật: cụm khớp tại vị trí 0 mà **dài hơn** cụm động từ thì nó là tên của việc.
        headMatch(t, terms, verbHit?.first?.size ?: 0)?.let { head ->
            val after = dropFillers(t.subList(head.words.size, t.size))
            return build(head, implicitVerb(head), aloud = false, after = after, terms = terms, original = original)
        }
        if (verbHit == null) return VoiceIntent.Unknown(VoiceUnknownReason.NO_VERB, original)
        val verb = verbHit.second
        val aloud = verbHit.first.any { it == "doc" || it == "read" || it == "nghe" }
        val rest = dropFillers(t.subList(verbHit.first.size, t.size))

        // (c) Điểm đến là từ vựng MỞ ⇒ KHÔNG đem so với từ vựng của xe. Một điểm đến bất kỳ có thể chứa đúng một
        //     cụm của xe (vd "trạm sạc") và khớp nó lên là biến câu dẫn đường thành lệnh sạc pin.
        if (verb == VoiceVerb.NAV) {
            return if (rest.isEmpty()) VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
            else VoiceIntent.Nav(rest.joinToString(" ") { it.raw })
        }

        // (d) Quét từ trái sang, lấy **cách hiểu ĐẦU TIÊN có nghĩa**.
        //
        // ⚠ Không được dừng ở cụm khớp đầu tiên rồi thôi: từ vựng của xe có những cụm MỘT TỪ rất ngắn đụng vào từ
        // thường ([ĐO] datum `gear` mang nhãn *"Số"* ⇒ *"chuyển sang hồ sơ Vợ"* khớp *"số"* ở giữa câu, rồi
        // "chuyển" + một datum = MISMATCH, và cái tên hồ sơ đứng ngay sau đó không bao giờ được xét tới).
        // Đi tiếp cho tới cách hiểu đầu tiên hợp với động từ thì ca đó tự giải, mà không phải liệt kê từ cấm.
        var firstMiss: VoiceIntent? = null
        rest.indices.forEach { i ->
            val cands = VoiceGrammar.matchAt(rest, i, terms)
            if (cands.isNotEmpty()) {
                val term = choose(cands, verb)
                val after = dropFillers(rest.subList(i + term.words.size, rest.size))
                val built = build(term, verb, aloud, after, terms, original)
                if (built !is VoiceIntent.Unknown) return built
                if (firstMiss == null) firstMiss = built
            }
        }
        return firstMiss ?: noObject(verb, rest, original)
    }

    /**
     * Cụm khớp tại vị trí 0 và **dài hơn** động từ đứng đó ([verbWords] = 0 khi không có động từ nào).
     *
     * Chỉ nhận MACRO/CONTROL/LAUNCHER: chúng là những thứ **được đặt tên như một việc**. Một datum hay một app
     * đứng trần thì không phải câu lệnh (*"pin"* một mình không nói lên là xem hay làm gì), nên chúng vẫn phải đi
     * qua đường động từ.
     */
    private fun headMatch(t: List<Token>, terms: List<VoiceTerm>, verbWords: Int): VoiceTerm? {
        val cands = VoiceGrammar.matchAt(t, 0, terms).filter {
            it.kind == VoiceTermKind.MACRO || it.kind == VoiceTermKind.CONTROL || it.kind == VoiceTermKind.LAUNCHER
        }
        if (cands.isEmpty()) return null
        val best = choose(cands, VoiceVerb.OPEN)
        return best.takeIf { it.words.size > verbWords }
    }

    /** Động từ ngầm cho một cụm tự đặt tên: gói lệnh thì "chạy", còn lại là "mở/bật". */
    private fun implicitVerb(term: VoiceTerm): VoiceVerb =
        if (term.kind == VoiceTermKind.MACRO) VoiceVerb.ON else VoiceVerb.OPEN

    /** Câu chỉ có đối tượng + đuôi hỏi ⇒ ĐỌC. Không khớp được datum nào thì nói rõ là thiếu đối tượng. */
    private fun objectOnlyRead(body: List<Token>, terms: List<VoiceTerm>, original: String): VoiceIntent {
        // Cùng luật "cách hiểu đầu tiên CÓ NGHĨA" như ở [parseTokens] — xem lý do tại đó.
        body.indices.forEach { i ->
            val cands = VoiceGrammar.matchAt(body, i, terms)
            val term = cands.takeIf { it.isNotEmpty() }?.let { choose(it, VoiceVerb.READ) }
            if (term != null && term.kind == VoiceTermKind.TELEMETRY) return VoiceIntent.Read(term.id)
        }
        return VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
    }

    /**
     * Chọn ứng viên: **dài nhất trước**, rồi mới tới loại động từ (luật 1 rồi luật 2).
     *
     * Thứ tự đó không hoán đổi được: ưu tiên loại trước sẽ cho phép một cụm NGẮN đúng loại thắng một cụm DÀI khác
     * loại, tức mở lại đúng cái cửa mà L-RE2 đã đóng (*"chế độ đèn pha"* → *"đèn pha"*).
     */
    private fun choose(cands: List<VoiceTerm>, verb: VoiceVerb): VoiceTerm {
        val longest = cands.maxOf { it.words.size }
        val group = cands.filter { it.words.size == longest }
        val wantRead = VoiceGrammar.isRead(verb)
        return group.firstOrNull { (it.kind == VoiceTermKind.TELEMETRY) == wantRead } ?: group.first()
    }

    /** Không khớp đối tượng nào ⇒ vẫn còn vài động từ tự đứng một mình được (nhạc), phần còn lại là từ vựng mở. */
    private fun noObject(verb: VoiceVerb, rest: List<Token>, original: String): VoiceIntent = when {
        verb == VoiceVerb.NEXT -> VoiceIntent.Media(VoiceMediaOp.NEXT)
        verb == VoiceVerb.PREV -> VoiceIntent.Media(VoiceMediaOp.PREV)
        verb == VoiceVerb.PLAY && rest.isEmpty() -> VoiceIntent.Media(VoiceMediaOp.PLAY)
        verb == VoiceVerb.PAUSE && rest.isEmpty() -> VoiceIntent.Media(VoiceMediaOp.PAUSE)
        // "phát <cái gì đó không biết>" — đúng hình dạng "mở nhạc", chỉ thiếu từ đánh dấu. Coi là từ vựng mở.
        verb == VoiceVerb.PLAY -> VoiceIntent.Media(VoiceMediaOp.QUERY, rest.joinToString(" ") { it.raw })
        rest.isEmpty() -> VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
        else -> VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
    }

    @Suppress("LongParameterList")
    private fun build(
        term: VoiceTerm,
        verb: VoiceVerb,
        aloud: Boolean,
        after: List<Token>,
        terms: List<VoiceTerm>,
        original: String,
    ): VoiceIntent =
        when (term.kind) {
            VoiceTermKind.TELEMETRY ->
                if (VoiceGrammar.isRead(verb)) VoiceIntent.Read(term.id, aloud)
                else VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)

            VoiceTermKind.CONTROL ->
                if (VoiceGrammar.isAction(verb)) control(term.id, verb, after, original)
                else VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)

            VoiceTermKind.MACRO ->
                if (VoiceGrammar.isAction(verb)) VoiceIntent.Macro(term.id)
                else VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)

            // *"Mở ứng dụng VTV Go"*: cụm "Ứng dụng" khớp hành động launcher (mở NGĂN KÉO), nhưng còn một cái
            // tên đứng sau — và cái tên đó mới là thứ người ta muốn. Đuôi khớp một app đã cài ⇒ mở thẳng app đó.
            VoiceTermKind.LAUNCHER -> when {
                !VoiceGrammar.isAction(verb) -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
                else -> appInTail(after, terms)?.let { VoiceIntent.OpenApp(it) } ?: VoiceIntent.Launcher(term.id)
            }

            VoiceTermKind.PROFILE -> VoiceIntent.Profile(term.id)

            VoiceTermKind.APP -> VoiceIntent.OpenApp(term.id)

            VoiceTermKind.NAV ->
                if (after.isEmpty()) VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
                else VoiceIntent.Nav(after.joinToString(" ") { it.raw })

            VoiceTermKind.MEDIA -> media(verb, after, original)
        }

    /** *"nhạc"/"bài"* + động từ: có đuôi ⇒ tên bài/thể loại (từ vựng mở), không đuôi ⇒ lệnh phát đơn thuần. */
    private fun media(verb: VoiceVerb, after: List<Token>, original: String): VoiceIntent {
        val q = after.joinToString(" ") { it.raw }
        return when (verb) {
            VoiceVerb.PAUSE, VoiceVerb.OFF, VoiceVerb.CLOSE -> VoiceIntent.Media(VoiceMediaOp.PAUSE)
            VoiceVerb.NEXT -> VoiceIntent.Media(VoiceMediaOp.NEXT)
            VoiceVerb.PREV -> VoiceIntent.Media(VoiceMediaOp.PREV)
            VoiceVerb.PLAY, VoiceVerb.OPEN, VoiceVerb.ON ->
                if (q.isEmpty()) VoiceIntent.Media(VoiceMediaOp.PLAY) else VoiceIntent.Media(VoiceMediaOp.QUERY, q)
            else -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
        }
    }

    // ── Nút: tính giá trị theo ControlKind ───────────────────────────────────────────────────────

    @Suppress("ReturnCount")
    private fun control(id: String, verb: VoiceVerb, after: List<Token>, original: String): VoiceIntent {
        val def = ControlRegistry.byId(id) ?: return VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
        val num = firstNumber(after)
        return when (def.kind) {
            ControlKind.TOGGLE, ControlKind.COVER -> when (verb) {
                VoiceVerb.ON, VoiceVerb.OPEN -> VoiceIntent.Control(id, 1)
                // "dừng chiếu cụm" = tắt nút `cast`. Không có nhánh này thì đúng câu người ta hay nói nhất cho
                // việc **dừng** một thứ đang chạy lại rơi vào MISMATCH.
                VoiceVerb.OFF, VoiceVerb.CLOSE, VoiceVerb.PAUSE -> VoiceIntent.Control(id, 0)
                VoiceVerb.SET -> num?.let { VoiceIntent.Control(id, if (it > 0) 1 else 0) }
                    ?: VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
                else -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
            }
            // Nút BẤM-một-phát: không có mặt "tắt" nào để nói dối, nên mọi động từ hành động đều là "bấm".
            ControlKind.BUTTON -> VoiceIntent.Control(id, null)
            ControlKind.SELECT -> selectIndex(def, after)?.let { VoiceIntent.Control(id, it) }
                ?: VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
            ControlKind.STEP -> step(def, verb, num, original)
        }
    }

    /**
     * Bước nhảy (nhiệt độ / gió / âm lượng / độ sáng).
     *
     * *"tăng/giảm"* ⇒ **tương đối** (xem KDoc [VoiceIntent.Control.relative] về việc vì sao `:core` không được tự
     * quy nó về tuyệt đối). Nêu số ⇒ tuyệt đối, đã kẹp trong `min..max` bằng chính [ControlDef.clamp] mà thanh nút
     * đang dùng — hai bề mặt không được có hai luật kẹp.
     */
    private fun step(def: ControlDef, verb: VoiceVerb, num: Int?, original: String): VoiceIntent = when (verb) {
        // *"Tăng âm lượng tối đa"* ([ĐO] mẫu câu Kiki #14) là lệnh TUYỆT ĐỐI đội lốt lệnh tương đối: "tối đa" đã
        // nói ra đích rồi. Bỏ qua vế đó thì câu phổ biến nhất của người dùng chỉ nhích lên một nấc.
        VoiceVerb.UP -> if (num == VoiceLexicon.MAX) VoiceIntent.Control(def.id, def.max)
        else VoiceIntent.Control(def.id, null, relative = num?.takeIf { plainStep(it) } ?: 1)
        VoiceVerb.DOWN -> if (num == VoiceLexicon.MIN) VoiceIntent.Control(def.id, def.min)
        else VoiceIntent.Control(def.id, null, relative = -(num?.takeIf { plainStep(it) } ?: 1))
        VoiceVerb.SET, VoiceVerb.ON, VoiceVerb.OPEN -> when (num) {
            null -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
            VoiceLexicon.MAX -> VoiceIntent.Control(def.id, def.max)
            VoiceLexicon.MIN -> VoiceIntent.Control(def.id, def.min)
            else -> VoiceIntent.Control(def.id, def.clamp(num))
        }
        VoiceVerb.OFF, VoiceVerb.CLOSE -> VoiceIntent.Control(def.id, def.min)
        else -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
    }

    /** Số đi kèm "tăng/giảm" phải là số bước thật, không phải sentinel *"hết cỡ"*. */
    private fun plainStep(n: Int): Boolean = n != VoiceLexicon.MAX && n != VoiceLexicon.MIN

    /** Khớp một nhãn lựa chọn của nút SELECT (VI hoặc EN), hoặc số thứ tự nói thẳng. */
    private fun selectIndex(def: ControlDef, after: List<Token>): Int? {
        val lists = listOf(def.args, def.argsEn).filter { it.isNotEmpty() }
        lists.forEach { args ->
            args.forEachIndexed { idx, label ->
                val words = VoiceLexicon.tokenize(label).map { it.norm }
                if (words.isNotEmpty() && after.indices.any { VoiceLexicon.phraseAt(after, it, words) }) return idx
            }
        }
        val n = firstNumber(after) ?: return null
        return n.takeIf { it in def.args.indices }
    }

    /** Nhãn app đã cài xuất hiện trong phần đuôi, hoặc `null`. Chỉ dùng cho ca [VoiceTermKind.LAUNCHER] ở trên. */
    private fun appInTail(after: List<Token>, terms: List<VoiceTerm>): String? = after.indices
        .firstNotNullOfOrNull { i ->
            VoiceGrammar.matchAt(after, i, terms).firstOrNull { it.kind == VoiceTermKind.APP }?.id
        }

    private fun firstNumber(after: List<Token>): Int? {
        after.indices.forEach { i -> VoiceLexicon.readNumber(after, i)?.let { return it.value } }
        return null
    }

    // ── Tiện ích ─────────────────────────────────────────────────────────────────────────────────

    private fun dropFillers(t: List<Token>): List<Token> {
        var i = 0
        while (i < t.size && t[i].norm in VoiceLexicon.FILLERS) i++
        return if (i == 0) t else t.subList(i, t.size)
    }

    /**
     * Vị trí + độ dài của cụm hỏi (*"bao nhiêu"*, *"thế nào"*…) ở BẤT KỲ đâu trong câu, hoặc `null`.
     *
     * ## [SOÁT P2] Vì sao không còn bắt buộc nằm ở CUỐI
     * Bản đầu chỉ nhận đuôi câu nên *"pin còn bao nhiêu"* hiểu được mà *"còn bao nhiêu pin"* thì NO_VERB — cùng
     * một câu hỏi, đảo trật tự là câm. Tiếng Việt đặt cụm hỏi ở giữa cũng tự nhiên như ở cuối (*"bao nhiêu phần
     * trăm pin"*). Cắt cụm hỏi ra rồi đọc phần còn lại xử lý được cả ba trật tự bằng một luật.
     *
     * Không sợ nuốt nhầm câu HÀNH ĐỘNG: năm cụm trong [VoiceLexicon.READ_TAILS] đều là cụm **hỏi** thuần
     * (*"bao nhiêu"*, *"thế nào"*, *"ra sao"*, *"how much/many"*), không cụm nào xuất hiện trong một câu ra lệnh.
     */
    private fun askAt(t: List<Token>): Pair<Int, Int>? =
        VoiceLexicon.READ_TAILS.firstNotNullOfOrNull { words ->
            t.indices.firstOrNull { VoiceLexicon.phraseAt(t, it, words) }?.let { it to words.size }
        }
}
