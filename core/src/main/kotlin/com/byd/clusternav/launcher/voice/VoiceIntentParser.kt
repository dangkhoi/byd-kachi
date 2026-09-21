package com.byd.clusternav.launcher.voice

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

    /**
     * Liên từ nối hai lệnh trong một câu ([ĐO] RE Kiki §7c #42: *"… và …"* là tính năng hạng nhất).
     *
     * `internal` từ pha NGHE: [VoicePhrases] phải khai bốn từ này với bộ nhận dạng, nếu không thì câu ghép
     * **nghe** được từng vế mà mất đúng cái từ nối chúng. Đọc lại ở đây thay vì chép sang đó — chép là để lệch.
     */
    internal val CONNECTORS = setOf("va", "roi", "and", "then")

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
    fun parse(
        text: String,
        profiles: List<String> = emptyList(),
        apps: List<String> = emptyList(),
        /**
         * Nhãn trong **sổ địa chỉ của hồ sơ đang dùng** (`SavedPlaces`) — danh sách ĐỘNG, cùng họ [profiles]/[apps].
         *
         * ⚠ Chúng KHÔNG đi vào từ vựng chung: chỉ được tra ở vị trí điểm đến — xem KDoc [VoicePlaces].
         */
        places: List<String> = emptyList(),
    ): List<VoiceIntent> {
        val all = VoiceLexicon.tokenize(text)
        if (all.isEmpty()) return listOf(VoiceIntent.Unknown(VoiceUnknownReason.EMPTY, text))
        // H4 — cụm NGHE NHẦM chỉ bật khi CẢ CÂU có từ ngữ cảnh, nên phải tính trên `all`, không trên từng vế.
        val terms = VoiceGrammar.plusMisheard(VoiceGrammar.terms(profiles, apps), all)

        val parts = splitOnConnectors(all)
        if (parts.size > 1) {
            val each = parts.map { parseTokens(it, terms, places, text) }
            if (each.none { it is VoiceIntent.Unknown }) return each
            val whole = fuzzy(parseTokens(all, terms, places, text), all, terms, places, text)
            return listOf(whole) + droppedNote(parts, each, whole)
        }
        return listOf(fuzzy(parseTokens(all, terms, places, text), all, terms, places, text))
    }

    /**
     * H7 — câu không hiểu được thì thử **chữa lỗi chính tả** rồi đọc lại đúng một lần ([VoicePhoneticMatch]).
     *
     * Đứng sau tất cả: mọi luật V1 chạy trước, không đổi một dòng. Chỉ nhận khi lần đọc lại ra một ý định CÓ
     * NGHĨA — không thì giữ nguyên câu báo cũ, kể cả **lý do** không hiểu (bài kiểm đang khoá lý do).
     *
     * ## D1 (log xe 2026-09-18) — BA cổng thêm vào, mỗi cổng chặn một lỗi ĐÃ ĐO
     * Tầng chữa chính tả là chỗ ba lỗi nặng nhất của phiên log sinh ra: nó "sửa" một âm tiết thành một cụm của
     * xe, và lần đọc lại thì có đủ động từ + đối tượng nên ra một ý định **rất tự tin mà sai**.
     *  1. **Tính năng đã bỏ / không có nút** ⇒ trả lời đúng tên, không đem đi chữa. *"bật đèn khẩn cấp"* từng ra
     *     `Control(trunk, 1)` = **mở cốp** (*"cấp"* → *"cốp"*, cặp lẫn owner đã nêu).
     *  2. **Câu nêu một HỌ thứ mà chưa nói cái nào** ⇒ hỏi lại, không đoán sang họ khác. *"áp suất lốp bên trái
     *     là bao nhiêu"* từng ra `Read(soc)` (*"bên"* → *"pin"*) — trả lời phần trăm pin cho một câu hỏi về lốp.
     *  3. **Câu HỎI thì không bao giờ được thành lệnh GHI.** *"tất cả cửa đang khóa hay đang mở"* từng ra
     *     `Control(sunroof, 1)` = **mở cửa sổ trời**. Cổng này đứng SAU phép chữa (chứ không chặn trước) để câu
     *     hỏi nghe trượt vẫn chữa được thành một câu ĐỌC — chỉ chặn đúng đường ra lệnh ghi.
     *
     * ⚠ Cổng (2) và (3) **chỉ áp cho câu HỎI**, không áp cho câu ra lệnh. [ĐO off-car] siết sang cả câu lệnh thì
     * hai phép chữa đang chạy đúng chết theo: *"mở góc sau"* (họ *"Góc …"*) và *"mật độ hai mươi hai độ"* (họ
     * *"Độ …"*) — ở đó chính phép chữa mới là thứ **giải** được cái họ ấy, nên chặn nó là chặn ngược. Câu lệnh
     * nói về một tính năng không có thì đã có cổng (1) lo.
     */
    private fun fuzzy(got: VoiceIntent, t: List<Token>, terms: List<VoiceTerm>, p: List<String>, s: String):
        VoiceIntent {
        if (got !is VoiceIntent.Unknown) return got
        if (got.reason == VoiceUnknownReason.FEATURE_GONE) return got
        if (VoiceFeatureGone.match(t) != null) return VoiceIntent.Unknown(VoiceUnknownReason.FEATURE_GONE, s)
        val question = VoiceQuestion.isQuestion(t)
        if (question && VoiceClarify.namesAFamily(t, terms)) return got
        val out = VoicePhoneticMatch.orRepair(got, t, terms) { parseTokens(it, terms, p, s) }
        return if (question && VoiceQuestion.writesToCar(out)) got else out
    }

    /**
     * Dòng *"đã bỏ qua «…»"* cho vế bị nuốt khi cả câu được hiểu theo cách khác — hoặc rỗng khi không có gì bị bỏ.
     *
     * ## Vì sao im lặng ở đây là ca tệ nhất ([ĐO] `emulator-voice-e2e-2026-09-15.md` §3 L5)
     * *"mở cửa và đèn đọc"* → **một** ý định `Bật Đèn đọc`. Luật *"cách hiểu đầu tiên có nghĩa"* tìm thấy
     * *"đèn đọc"* ở giữa câu và trả đúng một ý định — đúng theo mã, nhưng người lái vừa nói hai việc và chỉ một
     * việc chạy, **không câu nào nói ra**. Họ sẽ tưởng cửa đã mở.
     *
     * ## Vì sao KHÔNG cảnh báo cho *"Mở bài Cỏ dại và hoa dành dành"*
     * Ở câu đó vế thứ hai **nằm trong tên bài hát** — nó không bị bỏ, nó được dùng. Phép phân biệt không cần biết
     * bài hát nào tên có chữ *"và"*: chỉ cần hỏi *"chuỗi của vế ấy có nằm trong phần **từ vựng mở** mà cả câu đã
     * nhận không"* ([openPayload]). Có ⇒ im lặng; không ⇒ nói ra.
     *
     * Cả câu cũng không hiểu được (`whole` là [VoiceIntent.Unknown]) thì không thêm gì: lúc đó đã có sẵn một câu
     * báo, thêm dòng thứ hai chỉ là nói hai lần về cùng một việc.
     */
    private fun droppedNote(
        parts: List<List<Token>>,
        each: List<VoiceIntent>,
        whole: VoiceIntent,
    ): List<VoiceIntent> {
        if (whole is VoiceIntent.Unknown) return emptyList()
        val used = VoiceLexicon.deaccent(openPayload(whole))
        val dropped = parts.indices
            .filter { each[it] is VoiceIntent.Unknown }
            .map { VoiceTailClause.text(parts[it]) }
            .filterNot { used.isNotEmpty() && used.contains(VoiceLexicon.deaccent(it)) }
        if (dropped.isEmpty()) return emptyList()
        return listOf(VoiceIntent.Unknown(VoiceUnknownReason.DROPPED_CLAUSE, dropped.joinToString(" / ")))
    }

    /** Phần **từ vựng mở** mà một ý định mang theo (tên bài / điểm đến / tên app), rỗng nếu không có. */
    private fun openPayload(i: VoiceIntent): String = when (i) {
        is VoiceIntent.Nav -> i.query
        is VoiceIntent.Media -> i.query
        is VoiceIntent.NavigateSaved -> i.placeName
        is VoiceIntent.OpenApp -> i.appName
        else -> ""
    }

    /** Phân tích MỘT vế (không tách tiếp) — cửa dùng cho test và cho chỗ đã tự tách. */
    fun parseOne(
        text: String,
        profiles: List<String> = emptyList(),
        apps: List<String> = emptyList(),
        places: List<String> = emptyList(),
    ): VoiceIntent {
        val t = VoiceLexicon.tokenize(text)
        val terms = VoiceGrammar.plusMisheard(VoiceGrammar.terms(profiles, apps), t)
        return fuzzy(parseTokens(t, terms, places, text), t, terms, places, text)
    }

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
    private fun parseTokens(
        raw: List<Token>,
        terms: List<VoiceTerm>,
        places: List<String>,
        original: String,
    ): VoiceIntent {
        val t = dropFillers(raw)
        if (t.isEmpty()) return VoiceIntent.Unknown(VoiceUnknownReason.EMPTY, original)
        // WP8 · [VoiceFeatureGone.HARD_BLOCK] — từ chặn cứng, xét TRƯỚC mọi phép khớp (lý do ở KDoc bên đó).
        if (VoiceFeatureGone.blocked(t)) return VoiceIntent.Unknown(VoiceUnknownReason.FEATURE_GONE, original)
        // «mở … một nửa / 50%» ⇒ cờ NỬA cho kính (COVER). Dò cả câu vì «một nửa» đứng TRƯỚC object («một nửa kính»).
        val half = VoiceControlParse.mentionsHalf(t)

        // (a) Cụm hỏi (*"… bao nhiêu?"*) — người Việt hỏi xe bằng cụm hỏi, không bằng động từ đứng đầu.
        val ask = VoiceQuestion.askAt(t)
        if (ask != null) {
            val (at, len) = ask
            val body = dropFillers(t.subList(0, at) + t.subList(at + len, t.size))
            return objectOnlyRead(body, terms, original)
        }
        // (a″) D1 — **câu hỏi LỰA CHỌN** (*"… đang khóa hay đang mở"*, *"… hay không"*) ⇒ ép ĐỌC.
        //
        // [ĐO xe 2026-09-18] *"tất cả cửa đang khóa hay đang mở"* ra **`Control(sunroof, 1)`** = mở cửa sổ trời.
        // Câu ấy không có cụm hỏi nào cắt được, nên nhánh (a) không thấy nó; mà bỏ dấu thì *"tất"* = *"tắt"* ⇒
        // nó có đủ hình dạng một câu ra lệnh. Nhận dạng theo HÌNH DẠNG câu hỏi rồi đi đường ĐỌC là chỗ chữa
        // duy nhất không phải liệt kê từng câu — xem ba cổng ở [VoiceQuestion.isChoice].
        if (VoiceQuestion.isChoice(t)) return objectOnlyRead(VoiceQuestion.strip(t), terms, original)
        // (a‴) D2 — câu hỏi mức mà chữ hỏi rụng còn MỘT tiếng ở cuối (*"ghế mát mức mấy"* → ASR *"ghế mất mấy"*): chỉ
        //       đổi được thành lệnh ĐỌC, và chỉ khi thân ra datum thật. Ba cổng ở [VoiceQuestion.bareAskBody].
        VoiceQuestion.bareAskBody(t)?.let { body ->
            val read = objectOnlyRead(body, terms, original)
            if (read is VoiceIntent.Read) return read
        }
        // (a') *"chỉ số X"* = *"cho biết giá trị của X"* — là câu ĐỌC kể cả khi KHÔNG có cụm hỏi (*"chỉ số bụi mịn
        //      hiện nay"*). [objectOnlyRead] tự bỏ cụm dẫn để *"số"* không nuốt thành datum `gear`.
        if (VoiceQuestion.readsLead(t)) return objectOnlyRead(t, terms, original)

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
            // ⚠ [SOÁT 1.69 · P1] Không động từ + cụm không đọc đuôi ⇒ KHÔNG phải lệnh (*"cốp xe bẩn quá"* từng ra **mở cốp**) — KDoc [VoiceGrammar.readsTail].
            if (verbHit == null && after.isNotEmpty() && !VoiceGrammar.readsTail(head)) return@let
            return build(head, implicitVerb(head), aloud = false, after, terms, places, original, half)
        }
        // (b½) L7 — *"bố cục 2 cột"* / *"đổi sang bố cục 4 ô"* / *"về bố cục hai hàng"*.
        //
        // Đứng SAU [headMatch] (nhãn registry vẫn thắng) và TRƯỚC [savedPlace]: hai chữ *"bố cục"* là một cụm
        // đánh dấu rất hẹp, còn *"về/đi/đến"* của sổ địa chỉ thì rộng — cái hẹp phải xét trước, nếu không một
        // mục sổ tên *"bố cục"* sẽ nuốt mất cả họ câu này. [VoiceLayouts.match] tự trả `null` cho mọi câu không
        // mang cụm đánh dấu, nên nó không đụng tới một câu nào đang chạy.
        VoiceLayouts.match(t)?.let { return VoiceIntent.Layout(it) }
        // (b'') *"về nhà"* · *"đi làm"* · *"đến công ty"* — động từ CÓ ĐIỀU KIỆN của sổ địa chỉ.
        //
        // Đứng SAU [headMatch] có chủ ý: bỏ dấu thì *"đến"* = *"đèn"*, nên câu *"đèn đọc"* phải được nhãn nút
        // (dài hơn) giành lấy trước. Và chỉ nhận khi **cả phần đuôi** là một nơi có thật — xem KDoc
        // [VoicePlaces.PLACE_VERBS] về vì sao ba từ này không được vào bảng động từ chung.
        savedPlace(t, places)?.let { return it }
        // (b''') [ĐO log 1.79] "tìm + TỪ-NHẠC" → tra nhạc; "tìm <phi-nhạc>" giữ NO_VERB (hỏi lại). Đứng sau
        // headMatch nên "tìm đường đến X" (động từ NAV) đã giải trước — xem [mediaSearch].
        if (verbHit == null) {
            VoiceMediaNavParse.mediaSearch(t)?.let { return it }
            // "hạ [cái] cốp [sau]" → ĐÓNG cốp — scoped: "hạ" mơ hồ theo vật (hạ kính=MỞ) nên KHÔNG vào bảng verb chung.
            if (VoiceLexicon.phraseAt(t, 0, listOf("ha")) && t.any { it.norm == "cop" }) return VoiceIntent.Control("trunk", 0)
            return VoiceIntent.Unknown(VoiceUnknownReason.NO_VERB, original)
        }
        val verb = verbHit.second
        val aloud = verbHit.first.any { it == "doc" || it == "read" || it == "nghe" }
        val rest = dropFillers(t.subList(verbHit.first.size, t.size))

        // (c) Điểm đến là từ vựng MỞ ⇒ KHÔNG đem so với từ vựng của xe. Một điểm đến bất kỳ có thể chứa đúng một
        //     cụm của xe (vd "trạm sạc") và khớp nó lên là biến câu dẫn đường thành lệnh sạc pin.
        if (verb == VoiceVerb.NAV) return VoiceMediaNavParse.nav(rest, places, original)

        // (d) Quét từ trái sang, lấy **cách hiểu ĐẦU TIÊN có nghĩa**.
        //
        // ⚠ Không được dừng ở cụm khớp đầu tiên rồi thôi: từ vựng của xe có những cụm MỘT TỪ rất ngắn đụng vào từ
        // thường ([ĐO] datum `gear` mang nhãn *"Số"* ⇒ *"chuyển sang hồ sơ Vợ"* khớp *"số"* ở giữa câu, rồi
        // "chuyển" + một datum = MISMATCH, và cái tên hồ sơ đứng ngay sau đó không bao giờ được xét tới).
        // Đi tiếp cho tới cách hiểu đầu tiên hợp với động từ thì ca đó tự giải, mà không phải liệt kê từ cấm.
        // (c') H3 — cách gọi app ≥ 2 từ đứng NGAY SAU động từ thắng một nhãn NGẮN ở giữa câu. Chữa hai ca MỞ
        //      NHẦM APP đo được trên máy ảo; toàn bộ lý do + ba cổng ở KDoc [VoiceTailClause.appAtHead].
        VoiceTailClause.appAtHead(rest, terms, verb)?.let { return it }
        var firstMiss: VoiceIntent? = null
        rest.indices.forEach { i ->
            val cands = VoiceGrammar.matchAt(rest, i, terms)
            if (cands.isNotEmpty()) {
                val term = choose(cands, verb)
                // (d') H3 — **luật dãy dài nhất thắng áp cho cả TÊN APP**, không chỉ cho từ vựng chung.
                //
                // [ĐO xe 2026-09-16, tester 1.66]: *"Mở Google được mà Google Map chưa hiểu"*. Cơ chế: nhãn app
                // đã cài *"Google"* là một cụm MỘT từ trong từ vựng, nên nó khớp tại vị trí 0 và trả ngay một ý
                // định CÓ NGHĨA (`OpenApp("Google")`) — vòng quét dừng luôn, và cách nói HAI từ *"google map"*
                // của [VoiceSynonyms.APP_TARGETS] không bao giờ được hỏi tới. Tức luật số 1 của [VoiceGrammar]
                // (dài trước ngắn) đang bị hụt đúng ở ranh giới giữa hai bảng.
                //
                // Chỉ nhận khi cách nói **dài hơn hẳn** cụm vừa khớp ⇒ nhãn thật vẫn thắng khi hoà (*"mở
                // google"* vẫn mở app Google), đúng cam kết ở KDoc [VoiceTailClause.spokenApp]. Và chỉ cho
                // động từ MỞ/BẬT: *"đóng google map"* phải đi tiếp để ra [VoiceUnknownReason.APP_CLOSE], còn
                // *"xem …"* thì đã có nhánh riêng — bẻ chúng về đây là đổi hành vi ngoài phạm vi phép đo.
                if (VoiceGrammar.isAction(verb) && !VoiceTailClause.closesApp(verb)) {
                    VoiceTailClause.appByTargetName(rest, i, term.words.size)?.let { return it }
                }
                val after = dropFillers(rest.subList(i + term.words.size, rest.size))
                val built = build(term, verb, aloud, after, terms, places, original, half)
                if (built !is VoiceIntent.Unknown) return built
                if (firstMiss == null) firstMiss = built
            }
        }
        // (e) Chưa có cách hiểu nào CÓ NGHĨA ⇒ thử *cách gọi app bằng tiếng Việt* (*"mở bản đồ"*). Đứng sau (d)
        //     nên nhãn app thật và mọi nhãn registry vẫn thắng — xem KDoc [spokenApp].
        return VoiceTailClause.spokenApp(verb, rest, original) ?: firstMiss ?: noObject(verb, rest, original)
    }

    /**
     * *"&lt;động từ nơi chốn&gt; &lt;nơi&gt;"* ⇒ [VoiceIntent.NavigateSaved], hoặc `null` khi câu **không** phải thế.
     *
     * Ba điều kiện, và điều kiện thứ ba là thứ làm cả cơ chế này an toàn:
     *  1. động từ khớp tại **vị trí 0** (dài trước ngắn — *"đi đến"* thắng *"đi"*);
     *  2. mệnh đề *"bằng &lt;app&gt;"* ở cuối được cắt ra trước, đúng như mọi câu dẫn đường khác;
     *  3. **toàn bộ** phần còn lại phải khớp một nơi ([VoicePlaces.match]) — không khớp thì trả `null` và câu đi
     *     tiếp y như chưa có gì xảy ra. Nhờ (3), *"về bố cục 2 cột"* vẫn là [VoiceUnknownReason.NO_VERB] như bộ
     *     test đang đòi, mà không cần một danh sách từ cấm nào.
     */
    private fun savedPlace(t: List<Token>, places: List<String>): VoiceIntent? {
        val verb = VoicePlaces.PLACE_VERB_WORDS.firstOrNull { VoiceLexicon.phraseAt(t, 0, it) } ?: return null
        val after = dropFillers(t.subList(verb.size, t.size))
        if (after.isEmpty()) return null
        val hit = VoiceTailClause.appAfterMarker(after, VoiceAppKind.NAV)
        val body = if (hit == null) after else after.subList(0, hit.second)
        val label = VoicePlaces.match(body.map { it.norm }, places) ?: return null
        return VoiceIntent.NavigateSaved(label, hit?.first)
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
    private fun objectOnlyRead(body0: List<Token>, terms: List<VoiceTerm>, original: String): VoiceIntent {
        // D3 (log xe 2026-09-18) — câu HỎI về một tính năng đã bỏ thì trả lời đúng tên nó, TRƯỚC khi tra datum.
        // *"xe đang sạc pin hay không"* trước đây ra `Read(soc)`: máy đọc phần trăm pin cho một câu hỏi về SẠC —
        // đúng datum gần nhất, sai câu hỏi. Đây là chỗ an toàn để hỏi bảng: một câu HỎI không bao giờ là một
        // điểm đến ([VoiceIntent.Nav] không đi qua đây), nên cụm *"sạc pin"* không thể cướp *"trạm sạc pin"*.
        VoiceFeatureGone.match(body0)?.let { return VoiceIntent.Unknown(VoiceUnknownReason.FEATURE_GONE, original) }
        // [ĐO xe 2026-09-17 · log] *"chỉ số bụi mịn là bao nhiêu"* ra `Read(gear)` vì *"số"* (nhãn datum `gear`)
        // khớp Ở TRƯỚC *"bụi mịn"*. *"chỉ số X"* = *"giá trị của X"* ⇒ bỏ cụm dẫn để *"số"* thôi nuốt câu.
        val body = stripReadLead(body0)
        // ⚠ Chọn datum DÀI NHẤT trong cả câu, KHÔNG lấy vị-trí-khớp-đầu-tiên. [ĐO] cùng câu trên: *"bụi mịn"* (2
        // từ, `pm25`) phải thắng *"số"* (1 từ, `gear`) dù đứng sau. Đây là luật "dãy dài nhất thắng" áp cho READ —
        // trước đây chỉ áp trong vòng quét hành động, còn câu hỏi thì dừng ở khớp đầu tiên (gốc bug).
        var best: VoiceTerm? = null
        body.indices.forEach { i ->
            val term = VoiceGrammar.matchAt(body, i, terms)
                .filter { it.kind == VoiceTermKind.TELEMETRY }
                .maxByOrNull { it.words.size }
            if (term != null && (best == null || term.words.size > best!!.words.size)) best = term
        }
        return best?.let { VoiceIntent.Read(it.id) } ?: VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
    }

    /** Bỏ cụm dẫn *"chỉ số"* đầu câu hỏi (*"chỉ số bụi mịn"*) — nó là "giá trị của", không phải datum `gear`. */
    private fun stripReadLead(body: List<Token>): List<Token> {
        VoiceQuestion.READ_LEADS.forEach { lead ->
            if (VoiceLexicon.phraseAt(body, 0, lead)) return dropFillers(body.subList(lead.size, body.size))
        }
        return body
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
        verb == VoiceVerb.PLAY -> VoiceTailClause.withTarget(rest, VoiceAppKind.MUSIC) { body, app ->
            if (body.isEmpty()) VoiceIntent.Media(VoiceMediaOp.PLAY, app = app)
            else VoiceIntent.Media(VoiceMediaOp.QUERY, VoiceTailClause.text(body), app)
        }
        else -> VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
    }

    @Suppress("LongParameterList")
    private fun build(
        term: VoiceTerm,
        verb: VoiceVerb,
        aloud: Boolean,
        after: List<Token>,
        terms: List<VoiceTerm>,
        places: List<String>,
        original: String,
        half: Boolean = false,
    ): VoiceIntent =
        when (term.kind) {
            VoiceTermKind.TELEMETRY ->
                if (VoiceGrammar.isRead(verb)) VoiceIntent.Read(term.id, aloud)
                else VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)

            VoiceTermKind.CONTROL ->
                if (VoiceGrammar.isAction(verb)) VoiceControlParse.control(term.id, verb, after, original, half)
                else VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)

            VoiceTermKind.MACRO ->
                if (VoiceGrammar.isAction(verb)) VoiceIntent.Macro(term.id)
                else VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)

            // *"Mở ứng dụng VTV Go"*: cụm "Ứng dụng" khớp hành động launcher (mở NGĂN KÉO), nhưng còn một cái
            // tên đứng sau — và cái tên đó mới là thứ người ta muốn. Đuôi khớp một app đã cài ⇒ mở thẳng app đó.
            VoiceTermKind.LAUNCHER -> when {
                !VoiceGrammar.isAction(verb) -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
                else -> VoiceTailClause.appInTail(after, terms)?.let { (app, tail) ->
                    if (VoiceTailClause.closesApp(verb)) VoiceIntent.Unknown(VoiceUnknownReason.APP_CLOSE, original)
                    else VoiceIntent.OpenApp(app, VoiceTailClause.slotAt(tail))
                } ?: VoiceIntent.Launcher(term.id)
            }

            VoiceTermKind.PROFILE -> VoiceIntent.Profile(term.id)

            // V1.1 — *"mở YouTube **vào ô số 2**"*: cái đuôi sau tên app quyết định app đi vào ô nào. Không có
            // đuôi ⇒ `null` ⇒ y như trước (mở toàn màn).
            //
            // ⚠ Nhánh này CỐ Ý xét động từ (mọi nhánh khác đều xét): tới 1.63 nó dựng `OpenApp` cho **mọi** động
            // từ, nên [ĐO] `đóng YouTube` / `tắt YouTube` lại **MỞ** YouTube — xem [VoiceUnknownReason.APP_CLOSE].
            VoiceTermKind.APP ->
                if (VoiceTailClause.closesApp(verb)) VoiceIntent.Unknown(VoiceUnknownReason.APP_CLOSE, original)
                else VoiceIntent.OpenApp(term.id, VoiceTailClause.slotAt(after))

            VoiceTermKind.NAV -> VoiceMediaNavParse.nav(after, places, original)

            VoiceTermKind.MEDIA -> VoiceMediaNavParse.media(verb, after, original)
        }

    // ── Nhạc / dẫn đường ─────────────────────────────────────────────────────────────────────────
    // Ba bộ dựng `media`/`mediaSearch`/`nav` tách sang [VoiceMediaNavParse] (trần 500 dòng — CLAUDE.md §4.1).

    // ── Câu hỏi ĐỌC ──────────────────────────────────────────────────────────────────────────────
    // Cụm dẫn *"chỉ số X"* nay khai ở [VoiceQuestion.READ_LEADS] — [VoiceClarify] cần cùng bảng ấy (xem KDoc ở đó).

    // ── Tiện ích ─────────────────────────────────────────────────────────────────────────────────

    private fun dropFillers(t: List<Token>): List<Token> = VoiceLexicon.dropLeadingFillers(t)
}
