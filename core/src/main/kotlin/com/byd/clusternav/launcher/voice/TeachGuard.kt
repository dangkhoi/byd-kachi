package com.byd.clusternav.launcher.voice

/**
 * Mọi thứ cổng an toàn cần biết về máy + hồ sơ lúc dạy. Chỗ gọi (`:app`) dựng từ ĐÚNG các nguồn của phiên nghe (bảng
 * gọi app [VoiceAppIndex.keys], tên hồ sơ, nhãn sổ địa chỉ, tên đã dạy của hồ sơ đang dùng, câu gọi Kachi đang đặt).
 */
data class TeachContext(
    val keys: Map<String, String>,
    val taught: List<TaughtName>,
    val profiles: List<String> = emptyList(),
    val places: List<String> = emptyList(),
    val wakePhrases: List<String> = emptyList(),
) {
    val aliases: List<VoiceAppAlias> by lazy { VoiceAppIndex.aliasesOf(keys, taught) }

    fun parse(text: String, extra: List<VoiceAppAlias> = emptyList()): List<VoiceIntent> =
        VoiceIntentParser.parse(text, profiles, keys.keys.toList(), places, aliases + extra)

    /** Gói mà một ý định mở app trỏ tới (nhãn → gói, hoặc mã đích → gói đang cài), `null` nếu không phải mở app. */
    fun pkgOf(i: VoiceIntent): String? = (i as? VoiceIntent.OpenApp)?.let { o ->
        keys[o.appName] ?: VoiceAppTargets.byKey(o.appKey)?.packageIn(keys.values.toSet())
    }
}

/**
 * ═══ 2.91 VOICE-APP-NAMES · C5 — CỔNG AN TOÀN cho một tên sắp dạy ═══════════════════════════════════════════════
 *
 * Spec §4.7 (bảng luật) · R7. Một tên đã dạy là một từ mới trong TỪ VỰNG CHUNG ⇒ nó có thể cướp câu lệnh xe. Bốn tầng
 * rẻ → đắt, mọi tầng đọc bảng CÓ SẴN (không bảng thứ hai):
 *  A · từ vựng cấm (so chuỗi) — [VoiceGrammar.terms] tĩnh · [VoiceGrammar.VERBS] · [VoiceIntentParser.CONNECTORS] ·
 *      [VoiceEndWords] · [VoiceLexicon] (`CONFIRM_YES/NO` · `FILLERS` · `BY_APP_MARKERS` · `SLOT_WORDS` · `NUMBER_WORDS`) ·
 *      tên hồ sơ · nhãn sổ địa chỉ · câu gọi Kachi · nhãn/tên đã dạy/cách nói bảng đích của app KHÁC;
 *  B · phân tích thử *"mở &lt;tên&gt;"* + *"&lt;tên&gt;"* trần với từ vựng HIỆN TẠI;
 *  C · hotword thử ([SherpaTaughtHotwords.killedBy]);
 *  D · gần lệnh (phép chữa chính tả của B) / gần tên app khác ([VoiceNameFuzzy]) / một từ.
 * Tầng E (máy dò hồi quy trên mọi câu mẫu) đắt hơn ⇒ hàm riêng [regression], chạy lúc LƯU trên luồng nền.
 *
 * **Không tự mở app bằng một phép khớp yếu**: cổng này không thêm phép so nào lỏng hơn [VoiceNameFuzzy] (R7).
 */
object TeachGuard {

    enum class Level { NEW, ALREADY, WARN, BLOCK }

    /** Mã lý do (ASCII, ổn định — UI dịch theo mã, cầu kiểm thử in nguyên). [detail] là chữ máy, không phải câu cho người. */
    enum class Code(val level: Level) {
        SHAPE(Level.BLOCK), COMMAND(Level.BLOCK), COMMAND_PREFIX(Level.BLOCK), RESERVED_WORD(Level.BLOCK),
        SLOT(Level.BLOCK), PROFILE(Level.BLOCK), PLACE(Level.BLOCK), WAKE(Level.BLOCK), OTHER_APP(Level.BLOCK),
        IS_COMMAND(Level.BLOCK), REGRESSION(Level.BLOCK), UNREACHABLE(Level.BLOCK), SHORT_ONE_TAKE(Level.BLOCK),
        ALREADY_KNOWN(Level.ALREADY), DUPLICATE(Level.ALREADY),
        STEALS_FUZZY(Level.WARN), MISMATCH_WORD(Level.WARN), NO_BIAS(Level.WARN), NEAR_COMMAND(Level.WARN),
        NEAR_OTHER_APP(Level.WARN), ONE_WORD(Level.WARN),
    }

    /**
     * Số lượt nói phải ra ĐÚNG cùng chuỗi chuẩn hoá thì một tên GIỌNG ngắn ([TeachSample.Sample.short], 3 chữ cái) mới
     * được lưu — quyết định điều phối 2.91 (spec §7 OQ4): một âm tiết 3 chữ cái nghe MỘT lần có thể là âm rời/tiếng
     * đệm; mô hình in lại đúng nó ở lượt khác mới là tên người dùng thật sự nói.
     */
    const val MIN_TAKES_SHORT = 2

    data class Reason(val code: Code, val detail: String = "")

    data class Verdict(val level: Level, val reasons: List<Reason>) {
        fun has(code: Code): Boolean = reasons.any { it.code == code }
    }

    /**
     * Phán quyết A–D cho tên [accented] của gói [pkg]. Tên gõ đi qua [TeachSample.shape] trước (cùng luật hình dạng).
     *
     * @param takes số lượt nói (trong lần dạy này) mà mô hình in ra CÙNG chuỗi chuẩn hoá — chỗ gọi đếm (hộp dạy gộp mẫu
     *   theo `norm`; cầu kiểm thử đếm các lượt `teach` của gói). Chỉ dùng cho tên GIỌNG ngắn: < [MIN_TAKES_SHORT] ⇒
     *   [Code.SHORT_ONE_TAKE] (CHẶN tới khi nói lại đúng chuỗi ấy). Tên gõ: sàn [TeachSample.MIN_LETTERS], không cần lượt.
     */
    fun check(ctx: TeachContext, pkg: String, accented: String, source: TaughtSource, takes: Int = 1): Verdict {
        val sample = TeachSample.shape(accented) as? TeachSample.Sample
            ?: return Verdict(Level.BLOCK, listOf(Reason(Code.SHAPE)))
        val out = ArrayList<Reason>()
        val words = sample.words
        if (ctx.taught.any { it.pkg == pkg && it.norm == sample.norm }) out += Reason(Code.DUPLICATE)
        reserved(words, sample.accented).let { out.addAll(it) }
        vocabulary(ctx, pkg, words).let { out.addAll(it) }
        // Cổng va chạm chạy ĐỦ ngay ở lượt đầu (kể cả tên ngắn mới nghe một lần) ⇒ người dùng thấy ngay mọi lý do chặn.
        if (out.none { it.code.level == Level.BLOCK }) probe(ctx, pkg, sample, source).let { out.addAll(it) }
        if (words.size == 1) out += Reason(Code.ONE_WORD)
        // Đã lưu / Kachi đã hiểu sẵn ⇒ không có gì để lưu, không đòi thêm lượt.
        if (source == TaughtSource.SPEECH && sample.short && takes < MIN_TAKES_SHORT && out.none { it.code.level == Level.ALREADY }) {
            out += Reason(Code.SHORT_ONE_TAKE, sample.letters.toString())
        }
        return Verdict(levelOf(out), out)
    }

    /**
     * CHẶN thắng mọi thứ; rồi ĐÃ HIỂU SẴN (đã gọi được / đã lưu) thắng CẢNH BÁO — [ĐO máy ảo 06/10] bản đầu lấy mức CAO
     * nhất theo thứ tự khai nên *"gmail"* (ĐÃ HIỂU SẴN + một từ) ra CẢNH BÁO và bị LƯU dù Kachi đã mở được Gmail.
     */
    private fun levelOf(reasons: List<Reason>): Level = when {
        reasons.any { it.code.level == Level.BLOCK } -> Level.BLOCK
        reasons.any { it.code.level == Level.ALREADY } -> Level.ALREADY
        reasons.any { it.code.level == Level.WARN } -> Level.WARN
        else -> Level.NEW
    }

    // ── A · từ vựng cấm ─────────────────────────────────────────────────────────────────────────────────────────

    private fun reserved(words: List<String>, accented: String): List<Reason> {
        val out = ArrayList<Reason>()
        val first = words.first()
        if (VoiceGrammar.VERBS.any { (w, _) -> words.size >= w.size && words.take(w.size) == w }) {
            out += Reason(Code.RESERVED_WORD, "verb")
        }
        if (first in VoiceIntentParser.CONNECTORS || first in VoiceLexicon.FILLERS || first in VoiceLexicon.BY_APP_MARKERS) {
            out += Reason(Code.RESERVED_WORD, first)
        }
        if (VoiceLexicon.CONFIRM_YES.any { it == words } || VoiceLexicon.CONFIRM_NO.any { it == words } || VoiceEndWords.isEnd(accented)) {
            out += Reason(Code.RESERVED_WORD, "answer")
        }
        if (first in VoiceLexicon.NUMBER_WORDS || first.all { it.isDigit() }) out += Reason(Code.SLOT, "number")
        val toks = VoiceLexicon.tokenize(accented)
        toks.indices.forEach { i ->
            if (toks[i].norm in VoiceLexicon.SLOT_WORDS && VoiceTailClause.slotAt(toks.subList(i, toks.size)) != null) {
                out += Reason(Code.SLOT, "slot")
            }
        }
        return out.distinct()
    }

    private fun vocabulary(ctx: TeachContext, pkg: String, words: List<String>): List<Reason> {
        val out = ArrayList<Reason>()
        val norm = words.joinToString(" ")
        // Cụm của 4 bộ đăng ký + từ khoá nhạc/dẫn đường (tập TĨNH): trùng nguyên văn ⇒ cướp lệnh; là tiền tố theo từ ⇒
        // cướp câu lệnh dở (*"đèn"* ⊂ *"đèn đọc"*).
        // MỘT lý do cho mỗi loại (mã lệnh đầu tiên) — [ĐO máy ảo 06/10] "nhiệt" là tiền tố của hàng chục cụm, hộp dạy từng in
        // cùng một câu lý do hàng chục lần.
        VoiceGrammar.terms().firstOrNull { it.words == words }?.let { out += Reason(Code.COMMAND, it.id) }
        VoiceGrammar.terms().firstOrNull { it.words.size > words.size && it.words.take(words.size) == words }
            ?.let { out += Reason(Code.COMMAND_PREFIX, it.id) }
        if (ctx.profiles.any { VoiceAppIndex.normOf(it) == norm }) out += Reason(Code.PROFILE)
        if (ctx.places.any { VoiceAppIndex.normOf(it) == norm } || VoicePlaces.match(words, ctx.places) != null) {
            out += Reason(Code.PLACE)
        }
        ctx.wakePhrases.map { VoiceAppIndex.normOf(it) }.filter { it.isNotBlank() }.forEach { w ->
            if (norm == w || " $norm ".contains(" $w ")) out += Reason(Code.WAKE)
        }
        // Bộ nghe câu gọi đang chạy là [WakeAsrMatcher] (ASR, không phải chuỗi cố định) ⇒ hỏi thẳng nó: tên nào nó coi là
        // "Kachi" thì nói tên đó sẽ GỌI Kachi thay vì mở app.
        if (WakeAsrMatcher.isWake(words.joinToString(" "))) out += Reason(Code.WAKE)
        // App KHÁC: nhãn thật / dạng suy (khoá bảng) · tên đã dạy · cách nói bảng đích.
        ctx.keys.forEach { (k, p) -> if (p != pkg && VoiceAppIndex.normOf(k) == norm) out += Reason(Code.OTHER_APP, k) }
        ctx.taught.forEach { t -> if (t.pkg != pkg && t.norm == norm) out += Reason(Code.OTHER_APP, t.label.ifBlank { t.pkg }) }
        VoiceAppTargets.ALL.forEach { target ->
            if (pkg !in target.packages && target.spoken.any { VoiceAppIndex.normOf(it) == norm }) {
                out += Reason(Code.OTHER_APP, target.label)
            }
        }
        return out.distinct()
    }

    // ── B/C/D · phân tích thử + hotword + gần ───────────────────────────────────────────────────────────────────

    @Suppress("CyclomaticComplexMethod")
    private fun probe(ctx: TeachContext, pkg: String, s: TeachSample.Sample, source: TaughtSource): List<Reason> {
        val out = ArrayList<Reason>()
        val asOpen = ctx.parse("mở " + s.accented)
        val bare = ctx.parse(s.accented)
        val one = asOpen.singleOrNull()
        when {
            one is VoiceIntent.OpenApp && ctx.pkgOf(one) == pkg -> out += Reason(Code.ALREADY_KNOWN, one.appName)
            one is VoiceIntent.OpenApp -> out += Reason(Code.STEALS_FUZZY, one.appName)
            one is VoiceIntent.Unknown && one.reason == VoiceUnknownReason.MISMATCH -> out += Reason(Code.MISMATCH_WORD)
            one is VoiceIntent.Unknown || one is VoiceIntent.Nav -> Unit
            else -> out += Reason(Code.IS_COMMAND, describe(asOpen))
        }
        val bareMeaning = bare.firstOrNull { it !is VoiceIntent.Unknown && it !is VoiceIntent.Nav }
        if (bareMeaning != null && bareMeaning !is VoiceIntent.OpenApp) {
            // Lệnh khớp NGUYÊN (không qua chữa chính tả) ⇒ CHẶN; chỉ khớp sau khi chữa (*"xem phim"* ~ *"xem pin"*) ⇒ CẢNH BÁO.
            val repaired = VoicePhoneticMatch.repair(VoiceLexicon.tokenize(s.accented), VoiceGrammar.terms())
            out += Reason(if (repaired == null) Code.IS_COMMAND else Code.NEAR_COMMAND, describe(bare))
        }
        if (source == TaughtSource.SPEECH) {
            val base = SherpaBiasing.hotwordsFile(ctx.places, ctx.profiles)
            val kept = base.split('\n').filterTo(HashSet()) { it.isNotBlank() }
            val killed = SherpaTaughtHotwords.killedBy(s.accented, kept)
            if (killed.isNotEmpty()) out += Reason(Code.NO_BIAS, killed.joinToString(" · "))
        }
        val joined = s.words.joinToString("")
        val near = ctx.keys.filter { (k, p) -> p != pkg && VoiceNameFuzzy.nearly(VoiceAppIndex.normOf(k).replace(" ", ""), joined) }
        near.keys.firstOrNull()?.let { out += Reason(Code.NEAR_OTHER_APP, it) }
        ctx.taught.firstOrNull { it.pkg != pkg && VoiceNameFuzzy.nearly(it.words.joinToString(""), joined) }
            ?.let { out += Reason(Code.NEAR_OTHER_APP, it.label.ifBlank { it.pkg }) }
        return out
    }

    private fun describe(i: List<VoiceIntent>): String = i.joinToString(",") { it::class.simpleName.orEmpty() }

    // ── E · máy dò hồi quy (lúc LƯU) ─────────────────────────────────────────────────────────────────────────────

    /** Kết quả [regression]: câu mẫu đổi ý định khi có tên mới, và câu gọi của chính tên mới có tới đúng gói không. */
    data class Regression(val changed: List<String>, val reachable: Boolean, val checked: Int) {
        val ok: Boolean get() = changed.isEmpty() && reachable
    }

    /**
     * Phân tích LẠI mọi câu mẫu của [VoiceCommandCatalog] (cùng ba nguồn động với parser: hồ sơ · app · sổ địa chỉ)
     * có/không tên mới; một câu đổi ý định ⇒ CHẶN, nêu đúng câu bị cướp. Thêm: *"mở &lt;tên mới&gt;"* phải ra mở ĐÚNG
     * gói (tên bị che ⇒ `reachable = false`). Chạy trên luồng nền (vài trăm câu × 2 lượt phân tích).
     */
    fun regression(ctx: TeachContext, candidate: TaughtName): Regression {
        val extra = VoiceAppIndex.aliasesOf(ctx.keys, ctx.taught + candidate).filter { it.pkg == candidate.pkg && it.accented == candidate.accented }
        val phrases = VoiceCommandCatalog.groups(ctx.profiles, ctx.keys.keys.toList(), ctx.places, aliases = ctx.aliases)
            .flatMap { g -> g.examples.map { it.phrase } }
            .distinct()
        val changed = phrases.filter { p -> ctx.parse(p) != ctx.parse(p, extra) }
        val opened = ctx.parse("mở " + candidate.accented, extra).singleOrNull()
        val reachable = extra.isNotEmpty() && opened != null && ctx.pkgOf(opened) == candidate.pkg
        return Regression(changed, reachable, phrases.size)
    }
}
