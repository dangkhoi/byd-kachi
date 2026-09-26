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
            // D3 — nói lại cũng ra đúng câu ấy: tính năng đã bỏ / chưa có nút thì không có gì để hỏi.
            VoiceUnknownReason.FEATURE_GONE,
            -> return null
            else -> Unit
        }
        val raw = VoiceLexicon.tokenize(unknown.text)
        val tokens = raw.filterNot { it.norm in VoiceLexicon.FILLERS }
        if (tokens.isEmpty()) return null
        // ⚠ Hỏi [VoiceQuestion] trên bản CHƯA lọc tiếng đệm. Chữ `hay` có hai vai — tiếng đệm (*"hãy"*) và bộ
        // khung của câu hỏi lựa chọn (*"khóa hay mở"*) — nên lọc trước rồi mới hỏi là xoá đúng dấu hiệu cần đọc.
        // [ĐO lượt soát senior 2026-09-18] chính chỗ này từng làm [VoiceQuestion.looksAsked] **không bao giờ** thấy
        // dạng *"A hay B"*: `hay` nằm trong `FILLERS` nên nó đã bị cắt trước khi đo ⇒ *"kính hay cửa"* hỏi lại với
        // carry rỗng ⇒ trả lời *"cửa sổ trời"* ra một lệnh ghi.
        val asking = VoiceQuestion.isQuestion(raw)
        val asked = VoiceQuestion.looksAsked(raw)

        // VOICE-PROFILE-NAME-PHONETIC (2026-09-26) — câu nêu *"hồ sơ"* mà **không có cái tên**.
        //
        // [ĐO xe 2026-09-26] 8/8 lượt: owner nói *"chuyển sang hồ sơ Test"*, mô hình in ra *"chuyển sang hồ sơ"* —
        // rụng đúng cái tên. Trước bản này câu ấy ra `MISMATCH` ⇒ Kachi đọc *"việc đó không đi với thứ đó — thử nêu
        // mức, hoặc đổi động từ"*: một câu **không nói được phải làm gì tiếp**, cho một câu mà máy đã hiểu 90 %.
        // Nay hỏi thẳng tên hồ sơ, và [carryFor] không dùng ở đây: ngữ cảnh mang theo là **nguyên vế đã hiểu**
        // (*"chuyển sang hồ sơ"*) nên trả lời *"Mặc định"* ghép lại thành một câu phân tích được.
        //
        // Đứng TRƯỚC [ambiguity] vì cụm đánh dấu *"hồ sơ"* hẹp hơn hẳn phép dò họ nhãn: [ĐO] chữ *"số"* (nhãn datum
        // `gear`) khớp giữa chính câu này, nên để [ambiguity] chạy trước là hỏi lại về một cái nút số.
        VoiceProfileNames.missingName(tokens, terms)?.let { names ->
            return Ask(question(Strings.t("hồ sơ", "profile"), names), tokens.map { it.raw })
        }

        ambiguity(tokens, terms, asking, asked)?.let { return it }

        // *"&lt;động từ&gt; gì?"* chỉ đúng khi đầu câu THẬT LÀ một động từ. [ĐO xe 2026-09-18] *"hev đi được bao
        // nhiêu"* trước đây ra *"Hev gì?"* — máy lấy một danh từ làm động từ rồi hỏi một câu vô nghĩa; nay câu ấy
        // rơi về [vague] (*"Chưa rõ — nói lại giúp"*). Câu HỎI cũng không đi đường này: nó không có động từ nào để
        // mang theo. Lấy **cả cụm** động từ (*"kiểm tra"*, không phải *"kiểm"*) vì đó là chữ sẽ được ghép lại ở
        // lượt sau — cắt còn một từ là ghép ra một câu không phân tích được.
        val verb = if (asking) null else leadVerb(tokens)
        if (verb != null && unknown.reason == VoiceUnknownReason.NO_OBJECT) {
            return Ask(
                Strings.t("${capitalize(verb)} gì?", "${capitalize(verb)} what?"),
                listOf(verb),
            )
        }
        return Ask(vague(), carryFor(tokens, asking, asked))
    }

    /*
     * ⚠ [SOÁT senior 2026-09-18 · P0] Câu *"Chưa rõ — nói lại giúp"* ở trên **cũng** mang ngữ cảnh, không còn
     * `emptyList()` cứng. [ĐO off-car] *"cốp đã mở chưa"* là một câu HỎI đã nhận ra được (`isQuestion` = true) mà
     * họ *"Cốp …"* không đủ hai nhãn để hỏi lại ⇒ nó rơi xuống đúng dòng này ⇒ carry rỗng ⇒ trả lời *"cửa sổ
     * trời"* ra **`Control(sunroof,1)`**: đúng tai nạn của nhật ký, chỉ đi qua một câu hỏi lại khác.
     *
     * Đánh đổi đã cân: sau câu *"nói lại giúp"* người lái thường **hỏi lại**, không ra lệnh; và nếu họ ra lệnh
     * thật thì lượt ấy thành một câu ĐỌC (mất một lượt nói) — rẻ hơn hẳn một lệnh thân xe không ai xin.
     */

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
     * ═══ D1 · Từ nào trong câu là **đầu một HỌ** còn thiếu một chữ — và lượt trả lời đi đường nào ═════════════
     *
     * ## ⚠⚠ Bệnh NẶNG NHẤT của phiên log, và nó KHÔNG nằm ở lượt phân tích đầu
     * [ĐO xe 2026-09-18, `20260917-200206-017.json`] người lái hỏi *"tất cả cửa đang khóa hay đang mở"* và kết
     * quả ghi trong nhật ký là **`Control(sunroof=1)`** — *"✗ Bật Cửa sổ trời"*, tức một lệnh **mở cửa sổ trời**
     * trên xe đang chạy. Cùng tệp đó ghi `clarify: true` và `follow_up: true`, và đây là chỗ mấu chốt: lượt phân
     * tích ĐẦU không hề bắn lệnh nào — nó ra `NO_OBJECT`, rồi Kachi hỏi lại *"Cửa nào …?"*, người lái đọc một
     * cái tên, và **chính lượt trả lời ấy** mới thành lệnh ghi. Tức mọi cổng đặt ở lượt đầu (kể cả
     * [VoiceQuestion.isChoice]) đều **không** đóng được đường này: câu trả lời *"cửa sổ trời"* đứng một mình là
     * một câu ra lệnh hoàn toàn hợp lệ, và nó không còn mang dấu hiệu nào của câu hỏi ban đầu.
     *
     * ## Cách đóng: mang theo một động từ ĐỌC, không thêm một cổng thứ hai
     * Câu gốc là câu hỏi ⇒ [Ask.carry] = [READ_VERB]. [combine] dán nó vào trước câu trả lời (*"cửa sổ trời"* →
     * *"xem cửa sổ trời"*), nên lượt hai đi **đường ĐỌC** của [VoiceIntentParser]. Hệ quả kiểm được:
     *  • trả lời một thứ ĐỌC được (*"cửa trước trái"*) ⇒ `Read(door_lf)` — đúng câu hỏi;
     *  • trả lời một thứ chỉ có NÚT (*"cửa sổ trời"*) ⇒ `MISMATCH` = *"việc đó không đi với thứ đó"*, **không**
     *     phải một lệnh ghi;
     *  • người lái tự thêm động từ (*"mở cửa sổ trời"*) cũng vậy: `xem` đứng ở vị trí 0 giữ cả câu trong đường đọc.
     * Cơ chế này dùng lại đúng đường [carry]/[combine] đã có (`VoiceSessionTurns.kt:188`), nên nó không thêm một
     * mảnh trạng thái nào vào `:app` — nơi hai tệp phiên thoại đang ở 498/491 dòng.
     *
     * Và vì đã biết câu là câu hỏi, danh sách lựa chọn **ưu tiên thứ ĐỌC được** ([readsFirst]): hỏi *"cửa đang
     * khóa hay mở"* thì bốn datum cửa mới là câu trả lời, không phải nút cửa sổ trời/hạ kính.
     *
     * ## Chọn đầu họ theo **mức ủng hộ của cả câu**, không theo từ đứng trước
     * [ĐO xe 2026-09-18] ba câu hỏi về lốp đều hỏi lại sai chỗ, vì bản cũ lấy **từ khớp đầu tiên**:
     *  • *"chỉ số áp suất lốp"* → *"**Số** nào — Odo tổng hay Số VIN?"* (chữ *"số"* của cụm dẫn *"chỉ số"* thắng);
     *  • *"kiểm tra áp suất"* → *"Áp nào — **Áp cell cao, Áp cell thấp**, hay Áp lốp trước-trái?"* (hai lựa chọn
     *    đầu là điện áp cell pin, cho một câu hỏi về lốp).
     * Nay mỗi đầu họ được tính **mức ủng hộ** = số từ CÒN LẠI của câu xuất hiện trong cách nói của họ ấy
     * ([support]), và các thành viên cũng xếp theo mức ấy. *"suất"* + *"lốp"* nâng họ lốp lên trước họ cell;
     * *"số"* không được từ nào đỡ nên nó tự rơi xuống. Hoà thì **giữ thứ tự câu** (`sortedByDescending` ổn định)
     * ⇒ mọi câu hỏi lại đang đúng không đổi một chữ.
     */
    private fun ambiguity(
        tokens: List<VoiceLexicon.Token>,
        terms: List<VoiceTerm>,
        asking: Boolean,
        asked: Boolean,
    ): Ask? {
        // Chỉ đo trên từ mang NGHĨA VỀ XE: bộ khung câu hỏi bị trừ ra — xem KDoc [VoiceQuestion.FRAME_WORDS] về
        // ca *"kính lái đang mở bao nhiêu"* từng hỏi lại thành *"Đang nào — Tốc độ hay Đèn đọc?"*.
        val words = tokens.asSequence()
            .map { it.norm }
            .filterNot { it in VoiceQuestion.FRAME_WORDS }
            .toHashSet()
        val heads = tokens.indices.filter { i ->
            // ⚠ BỎ QUA ĐỘNG TỪ. [ĐO off-car] không có điều kiện này thì *"mở kính"* hỏi *"Mở nào — Mô-men mô-tơ
            // trước, Mở cửa cảnh báo trái, hay Mở cửa cảnh báo phải?"*: chữ `mo` là chữ mở đầu của hàng chục
            // nhãn, nên câu hỏi bám vào đúng cái từ mà máy ĐÃ hiểu. Thứ thiếu là đối tượng, không phải động từ.
            //
            // ⚠⚠ Và **KHÔNG** thêm điều kiện `matchAt(...).isEmpty()` ở đây, dù [namesAFamily] có nó. Hai hàm hỏi
            // hai câu khác nhau: bên kia hỏi *"câu này có nêu một họ CHƯA khớp ai không"* (để chặn tầng chữa chính
            // tả), còn ở đây thứ cần là *"hỏi lại cho ra một câu dùng được"* — mà một cụm đã khớp trọn vẫn có thể
            // là đầu một họ. [ĐO off-car 2026-09-18] thêm điều kiện đó làm *"kính lái đang mở bao nhiêu"* mất hẳn
            // ứng viên `kinh` (vì *"kính lái"* khớp trọn nút `window`) ⇒ câu hỏi lại rơi sang một họ khác.
            tokens[i].norm !in VERB_HEADS && familyIds(tokens[i].norm, terms).size >= 2
        }
        val carry = carryFor(tokens, asking, asked)
        // Đi theo mức ủng hộ giảm dần, nhưng vẫn **thử tiếp** nếu một đầu họ không đủ hai nhãn đọc được — giữ
        // đúng hành vi "quét tới khi tìm được" của bản cũ, chỉ đổi THỨ TỰ quét.
        heads.sortedByDescending { i -> support(familyIds(tokens[i].norm, terms), words - tokens[i].norm, terms) }
            .forEach { i ->
                val head = tokens[i]
                val others = words - head.norm
                val labels = readsFirst(familyIds(head.norm, terms), asking)
                    .sortedByDescending { id -> support(listOf(id), others, terms) }
                    .mapNotNull { labelOf(it) }
                    .distinct()
                    .take(MAX_CHOICES)
                if (labels.size >= 2) return Ask(question(head.raw, labels), carry)
            }
        return null
    }

    /**
     * Ngữ cảnh mang sang lượt trả lời của một câu hỏi lại kiểu *"&lt;họ&gt; nào — …?"*.
     *
     * Ba ca, theo thứ tự đó:
     *  1. **câu gốc là câu HỎI** ⇒ [READ_VERB]. Đây là cổng D1 (xem KDoc [ambiguity]) — kể cả khi câu hỏi có một
     *     động từ hành động lẫn trong đó, lượt trả lời vẫn phải đi đường ĐỌC;
     *  2. **câu gốc mở đầu bằng một động từ** ⇒ mang chính động từ ấy. Bản trước bỏ nó đi, nên *"kiểm tra áp
     *     suất"* → hỏi *"Áp nào …?"* → người lái đọc *"áp lốp trước trái"* → **`NO_VERB`**: máy hỏi một câu rồi
     *     không hiểu nổi câu trả lời của chính nó. Một tên datum đứng trần không phải một lệnh
     *     (`VoiceGrammar.readsTail` — cổng [SOÁT 1.69 · P1]), nên động từ phải đi cùng;
     *  3. **không động từ nào, mà câu CÓ dấu hiệu hỏi** ([asked] = [VoiceQuestion.looksAsked] đo trên bản CHƯA
     *     lọc tiếng đệm — xem [ask]) ⇒ [READ_VERB].
     *  4. còn lại ⇒ rỗng (vd cả câu chỉ có một từ *"lọc"*).
     *
     * ## ⚠⚠ [SOÁT senior 2026-09-18 · P0] Vì sao phải có ca (3)
     * `carry` **rỗng** là ô nhớ duy nhất mà tai nạn của nhật ký còn đi qua được: câu trả lời đứng một mình thì
     * *"cửa sổ trời"* → `Control(sunroof,1)`, *"mở cốp"* → `Control(trunk,1)`, *"rời xe"* → `Macro(mac_leave)` —
     * đều là lệnh ghi hoàn toàn hợp lệ. [ĐO off-car, lượt soát] *"cốp đã mở chưa"* / *"điều hòa đang bật không"* /
     * *"kính hạ hết chưa"* đều rơi vào đúng ô ấy vì [VoiceQuestion.isQuestion] (chặt, vì nó đổi cách phân tích cả
     * câu) chưa dám nhận chúng. Ở đây thì ngưỡng phải LỎNG: chọn sai về phía ĐỌC chỉ tốn một lượt nói lại, chọn
     * sai về phía HÀNH ĐỘNG là mở cửa sổ trời trên xe đang chạy.
     *
     * Ca (4) vẫn còn — và **cố ý còn**: một danh ngữ trần không có dấu hiệu hỏi nào (*"lọc"*) đúng là một câu ra
     * lệnh nói thiếu, và owner đã chốt ở R8 rằng hỏi lại xong thì **làm**.
     */
    private fun carryFor(
        tokens: List<VoiceLexicon.Token>,
        asking: Boolean,
        asked: Boolean,
    ): List<String> = when {
        asking -> listOf(READ_VERB)
        else -> leadVerb(tokens)?.let { listOf(it) } ?: if (asked) listOf(READ_VERB) else emptyList()
    }

    /** Cụm động từ ở **vị trí 0**, trả về NGUYÊN VĂN (*"kiểm tra"*), hoặc `null`. Dài trước ngắn, như mọi nơi. */
    private fun leadVerb(tokens: List<VoiceLexicon.Token>): String? {
        val hit = VoiceGrammar.VERBS.firstOrNull { VoiceLexicon.phraseAt(tokens, 0, it.first) } ?: return null
        return tokens.take(hit.first.size).joinToString(" ") { it.raw }
    }

    /**
     * Số từ của [others] xuất hiện trong cách nói của [ids] — lấy mã được ủng hộ NHIỀU NHẤT.
     *
     * Đọc thẳng [terms] (từ vựng đã sinh từ bộ đăng ký) nên thêm một cách nói ở [VoiceSynonyms] là phép đo này
     * tự chính xác hơn; không có bảng "từ liên quan" nào chép tay.
     */
    private fun support(ids: List<String>, others: Set<String>, terms: List<VoiceTerm>): Int {
        if (others.isEmpty() || ids.isEmpty()) return 0
        return ids.maxOf { id ->
            terms.asSequence()
                .filter { it.id == id }
                .flatMap { it.words.asSequence() }
                .toSet()
                .count { it in others }
        }
    }

    /**
     * Câu HỎI ⇒ chỉ đưa ra những mã **đọc được**; câu ra lệnh ⇒ để nguyên.
     *
     * ## ⚠ Bản trước có một đường LÙI, và nó nói ngược lại chính [ambiguity]
     * Tới 1.79 hàm này trả về **nguyên** danh sách khi họ ấy còn dưới 2 mã đọc được, với lý do *"một câu hỏi không
     * có gì để chọn còn tệ hơn một câu hỏi lệch loại"*. Nhưng [ambiguity] đã có sẵn đường xử lý ca ấy — nó **thử
     * tiếp đầu họ sau** khi một họ không đủ hai nhãn (xem ghi chú ở vòng `forEach` đó) — nên đường lùi này chỉ làm
     * đúng một việc: **chặn** vòng thử tiếp, bằng cách trả về đủ 2 nhãn của loại SAI.
     *
     * [ĐO off-car 2026-09-19, lượt D] *"tất cả cửa đang khóa hay đang mở"* hỏi lại thành
     * *"Khóa nào — Khóa / mở khóa hay Khóa trẻ em?"* — mời người lái đọc tên hai cái **nút** để trả lời một câu
     * hỏi về trạng thái, trong khi họ *"Cửa …"* ở ngay sau đó có đủ 4 datum cửa. (Lượt D chỉ **làm lộ** chỗ này:
     * nó đổi thứ tự ủng hộ giữa hai đầu họ `cua` / `khoa`, chứ không sinh ra nó.)
     *
     * Không có mã đọc được nào ⇒ trả rỗng ⇒ [ambiguity] bỏ qua họ đó và thử họ tiếp; không họ nào đủ thì câu hỏi
     * rơi về [vague] — vẫn mang [READ_VERB] nên cổng D1 còn nguyên.
     */
    private fun readsFirst(ids: List<String>, asking: Boolean): List<String> {
        if (!asking) return ids
        return ids.filter { TelemetryRegistry.byId(it) != null }
    }

    /**
     * Động từ ĐỌC mang theo cho lượt trả lời của một câu HỎI — xem KDoc [ambiguity].
     *
     * Phải là một cụm của [VoiceGrammar.VERBS] trỏ tới [VoiceVerb.READ]. `VoiceClarifyQuestionTest` ép bằng máy:
     * đổi thành một chữ không có trong bảng ấy là bịt cổng D1 **mà vẫn xanh**, nên phép canh không thể là mắt người.
     */
    const val READ_VERB = "xem"

    /** Chỉ hỏi về thứ **bấm/đọc được**; hồ sơ/app/nhạc/điểm đến có đường hỏi riêng hoặc không hỏi được. */
    private val ASKABLE = setOf(VoiceTermKind.CONTROL, VoiceTermKind.TELEMETRY)

    /**
     * Các mã mà [head] là **chữ mở đầu** của một cụm nhiều từ trỏ tới chúng — *"kính"* → 4 nút kính + 4 datum.
     *
     * Tách ra vì [namesAFamily] hỏi **cùng một câu hỏi** với [ambiguity]; hai bản sao của phép lọc này là hai bản
     * sẽ lệch (CLAUDE.md §4.1).
     *
     * ⚠ Đọc lựa chọn theo THỨ TỰ DANH MỤC, không theo thứ tự [terms]. [terms] xếp **cụm dài trước** (luật L-RE2
     * của [VoiceGrammar]) — một thứ tự đúng cho việc so khớp và vô nghĩa cho một câu hỏi. [ĐO off-car] *"lọc"*
     * trước đây hỏi *"Lọc nào — Lọc ngay hay Lọc bụi?"*: `pm25_clean_now` lên trước chỉ vì nó tình cờ có một cách
     * nói BA từ (*"lọc không khí ngay"*), không vì nó quan trọng hơn. Thứ tự danh mục là thứ tự các nút nằm trên
     * màn hình, tức thứ tự người lái đã quen ⇒ *"Lọc bụi hay Lọc ngay?"*, đúng câu owner nêu.
     */
    private fun familyIds(head: String, terms: List<VoiceTerm>): List<String> = terms
        .filter { it.words.size > 1 && it.words.first() == head && it.kind in ASKABLE }
        .map { it.id }
        .distinct()
        .sortedBy { rank(it) }

    /**
     * ═══ D1 · Câu này nêu một **HỌ** thứ mà chưa nói cái nào ══════════════════════════════════════════════
     *
     * *"áp suất lốp bên trái"* nêu họ **Áp …** (4 lốp × áp/nhiệt) mà không nói lốp nào; *"đèn khẩn cấp"* nêu họ
     * **Đèn …** mà Kachi không có cái đèn ấy. Trả `true` cho cả hai.
     *
     * ## Ai dùng, và để làm gì
     * [VoiceIntentParser] hỏi hàm này **trước** tầng chữa chính tả ([VoicePhoneticMatch]). Lý do là một lỗi ĐÃ ĐO
     * (`oncar-voice-cases-findings-2026-09-18.md` §D1): với câu *"áp suất lốp bên trái là bao nhiêu"*, tầng chữa
     * sửa *"bên"* → *"pin"* rồi trả **`Read(soc)`** — máy trả lời **phần trăm pin** cho một câu hỏi về lốp. Khi
     * câu đã nêu rõ một họ, thứ đúng đắn là **hỏi lại cái nào** ([ambiguity] có sẵn câu hỏi đó), không phải đoán
     * sang một họ khác.
     *
     * ## Cổng thứ hai là thứ giữ cho nó không siết quá tay
     * `matchAt` rỗng = **chưa có thành viên nào của họ khớp trọn**. Thiếu cổng này thì *"bằng ghế sưởi"* (một ca
     * chữa chính tả đang chạy đúng: `bằng` → `bật`) cũng bị coi là nhập nhằng, vì *"ghế"* là chữ mở đầu của cả
     * họ ghế — trong khi *"ghế sưởi"* đã khớp trọn một nhãn ngay tại đó, tức không còn gì để hỏi.
     */
    fun namesAFamily(tokens: List<VoiceLexicon.Token>, terms: List<VoiceTerm> = VoiceGrammar.terms()): Boolean =
        tokens.indices.any { i ->
            val tk = tokens[i]
            tk.norm !in VERB_HEADS &&
                VoiceGrammar.matchAt(tokens, i, terms).isEmpty() &&
                familyIds(tk.norm, terms).size >= 2
        }

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
