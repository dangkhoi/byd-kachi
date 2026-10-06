package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ V1.1 · MỆNH ĐỀ ĐUÔI + CÁCH GỌI APP — phần *"câu còn nói thêm gì ở cuối"* ════════════════════════════════
 *
 * Tách khỏi [VoiceIntentParser] vì trần 500 dòng (CLAUDE.md §4.1; bài canh
 * `VoiceCommandWiringContractTest.pha NGHE khong day tep nao qua tran 500 dong`) — và tách **theo vai**, không
 * phải cắt cho đủ số: bộ phân tích nói về *"câu này là việc gì"*, còn tệp này chỉ trả lời ba câu hỏi về phần
 * ĐUÔI của câu:
 *  • *"…vào ô số mấy"* ([slotAt]);
 *  • *"…bằng / trên app nào"* ([appAfterMarker] · [withTarget]);
 *  • *"cái tên đứng đây là app nào"* ([appInTail] cho nhãn đã cài · [appByTargetName] cho cách nói tiếng Việt).
 *
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car. Mọi hàm `internal`: chỉ [VoiceIntentParser] và bài kiểm gọi tới.
 */
internal object VoiceTailClause {

    /**
     * Số ô mà câu nêu ra trong phần đuôi [after] (*"vào ô số 2"* · *"ô thứ hai"* · *"in slot 2"*), hoặc `null`.
     *
     * Trả về **đúng con số người ta nói** (1-based, chưa kẹp) — xem KDoc [VoiceIntent.OpenApp.slot] về vì sao
     * không quy đổi và không kẹp ở tầng này. Chữ *"ô"* rụng ⇒ [bareSlot] (đường lùi, kẹp chặt hơn).
     */
    @Suppress("ReturnCount")
    internal fun slotAt(after: List<Token>): Int? {
        after.indices.forEach { i ->
            if (after[i].norm !in VoiceLexicon.SLOT_HEADS) return@forEach
            var j = i + 1
            while (j < after.size && after[j].norm in VoiceLexicon.SLOT_ORDINALS) j++
            val n = VoiceLexicon.readNumber(after, j) ?: return@forEach
            // *"ô tối đa"* không phải một số ô; sentinel MIN/MAX chỉ có nghĩa với nút có dải giá trị.
            if (n.value == VoiceLexicon.MAX || n.value == VoiceLexicon.MIN || n.value <= 0) return@forEach
            return n.value
        }
        return bareSlot(after)
    }

    /**
     * ═══ CHỮ *"Ô"* RỤNG — *"mở vietmap hai"* · *"mở vietmap số hai"* ⇒ ô 2 ═══════════════════════════════════
     *
     * ## Bệnh nó chữa — [ĐO xe 2026-09-27, log `KachiVoiceSession`]
     * | mô hình in ra | ý định TRƯỚC | ý định SAU |
     * |---|---|---|
     * | *"mở vietmap hai"* (lượt 10:36:52, cửa sổ chạm trần 8 s) | `OpenApp(VietMap)` — **mất ô**, app mở vào ô cũ | `OpenApp(VietMap → ô 2)` |
     *
     * Đây là biến thể **nặng nhất** của VOICE-SLOT-TAIL-CUT: §1.2 của
     * `docs/diagnostics/offcar-2026-09-26/voice-tail-fuzzy-phonetic.md` đã đo *"mở vietmap **một**"* — bộ giải mã
     * bỏ ba chữ giữa và để lại **đúng con số**. Tệp hotword (2.73) chữa phần lớn ca ấy, nhưng khi cửa sổ có 6 giây
     * im lặng dẫn đầu ([ĐO] `tieng_bat_dau=6012ms`) thì nó vẫn rơi lại. Con số còn sót là **thông tin thật người
     * lái đã nói**; bỏ nó đi là mở app vào ô sai một cách im lặng.
     *
     * ## Ba cổng — vì sao đường lùi này kẹp CHẶT hơn mệnh đề đầy đủ
     *  1. **Phải là phần đuôi RỖNG ngoài con số**: sau khi bỏ tiếng đệm, [after] chỉ còn `&lt;số&gt;` hoặc
     *     `&lt;số/thứ&gt; &lt;số&gt;`. *"mở youtube tập hai"* · *"mở nhạc hai giờ"* ⇒ không khớp. Một con số **lơ
     *     lửng giữa câu** không bao giờ là một số ô.
     *  2. **Kẹp vào 1..[VoiceSlotPhrases.MAX_SLOT]** — khác [slotAt] (cố ý **không** kẹp, xem KDoc
     *     [VoiceIntent.OpenApp.slot]). Lý do: ở đây con số là **chứng cứ yếu** (không có chữ *"ô"* đi kèm), nên nó
     *     chỉ được nhận khi nó trỏ tới một ô **có thật** ở một bố cục nào đó. *"mở youtube hai mươi"* ⇒ `null`.
     *  3. **Chỉ chạy sau khi [slotAt] đã trượt** ⇒ không đổi một câu nào có chữ *"ô"* (CLAUDE.md §6).
     *
     * ⚠ Chỗ gọi là **các đường app** ([appByTargetName] · [appInTail] · [VoiceLastResort] · hai nhánh `OpenApp` của
     * [VoiceIntentParser]): mệnh đề ô chỉ tồn tại cho [VoiceIntent.OpenApp], nên đường lùi này không tới được lệnh
     * xe / datum / nhạc / dẫn đường.
     */
    private fun bareSlot(after: List<Token>): Int? {
        val w = after.filter { it.norm !in VoiceLexicon.FILLERS }
        val at = when {
            w.size == 1 -> 0
            w.size == 2 && w[0].norm in VoiceLexicon.SLOT_ORDINALS -> 1
            else -> return null
        }
        val n = VoiceLexicon.readNumber(w, at) ?: return null
        // Con số phải là TOÀN BỘ phần còn lại (*"hai mươi"* đọc hết 2 token ⇒ vẫn là một số, nhưng > MAX_SLOT).
        if (at + n.consumed != w.size) return null
        return if (n.value in 1..VoiceSlotPhrases.MAX_SLOT) n.value else null
    }

    /**
     * Cắt mệnh đề *"bằng &lt;app&gt;"* ở **cuối** [after], rồi dựng ý định bằng [make] với phần còn lại.
     *
     * ## Ba ràng buộc, mỗi cái chặn một cách hiểu sai
     *  1. **Chỉ nhận ở CUỐI câu.** *"dẫn đường tới cầu Bằng Lăng"* có chữ *"bằng"* nằm giữa tên cầu; đòi mệnh đề
     *     phải chạm cuối câu thì ca đó tự giải mà không cần biết cây cầu nào tên có chữ *"bằng"*.
     *  2. **Chỉ nhận khi ngay sau là một app ĐÃ BIẾT.** *"…bằng xe máy"* không khớp đích nào ⇒ để nguyên trong
     *     điểm đến. Đoán bừa ở đây là gửi một điểm đến thiếu chữ.
     *  3. **Lấy trọn phần đuôi**, không cắt ngắn: *"…bằng youtube music"* phải khớp `ytmusic`, chứ không phải
     *     khớp `youtube` rồi bỏ lại chữ *"music"* trong tên bài. Vì mệnh đề đã buộc chạm cuối câu (1), phần đuôi
     *     chỉ có **đúng một** độ dài ⇒ không cần quét dài-xuống-ngắn, chỉ cần chặn trần [VoiceAppTargets.LONGEST_SPOKEN]
     *     để không đem cả một tên bài mười chữ đi tra bảng.
     */
    internal fun withTarget(
        after: List<Token>,
        kind: VoiceAppKind,
        terms: List<VoiceTerm> = emptyList(),
        make: (List<Token>, String?) -> VoiceIntent,
    ): VoiceIntent {
        val hit = appAfterMarker(after, kind, terms)
        val body = if (hit == null) after else after.subList(0, hit.second)
        return make(body, hit?.first)
    }

    /**
     * Phần thân trả lại **nguyên văn** (chữ hoa + dấu đúng như người ta nói/gõ).
     *
     * ⚠ [make] của [withTarget] nhận **token**, không nhận chuỗi đã ghép: sổ địa chỉ phải khớp theo dạng đã bỏ
     * dấu (`Token.norm`) còn điểm đến mở phải đi tiếp nguyên văn (`Token.raw`) — ghép chuỗi sớm là vứt mất một
     * trong hai (xem KDoc [VoiceLexicon.Token]).
     */
    internal fun text(body: List<Token>): String = body.joinToString(" ") { it.raw }

    /**
     * Mã đích + vị trí bắt đầu của mệnh đề *"bằng &lt;app&gt;"*, hoặc `null` khi câu không nêu app.
     *
     * Xem KDoc [withTarget] về ba ràng buộc, và KDoc [VoiceSynonyms.APP_TARGETS] về vì sao tên app đích **không**
     * nằm trong từ vựng chung.
     */
    @Suppress("ReturnCount")
    internal fun appAfterMarker(after: List<Token>, kind: VoiceAppKind, terms: List<VoiceTerm> = emptyList()): Pair<String, Int>? {
        after.indices.forEach { i ->
            if (after[i].norm !in VoiceLexicon.BY_APP_MARKERS) return@forEach
            // (1) mệnh đề phải chạm CUỐI câu ⇒ phần đuôi chỉ có đúng một độ dài; (3) trần LONGEST_SPOKEN.
            val len = after.size - i - 1
            if (len !in 1..maxOf(VoiceAppTargets.LONGEST_SPOKEN, TeachSample.MAX_WORDS)) return@forEach
            val words = (i + 1 until after.size).map { after[it].norm }
            // 2.91 VOICE-APP-NAMES (spec §4.6) — TÊN ĐÃ DẠY của một app thuộc bảng đích, đúng loại, khớp NGUYÊN dãy:
            // xét TRƯỚC mọi cách nói của bảng (chữ mô hình in cho giọng người này thắng bảng đoán). Tên đã dạy của
            // app KHÔNG thuộc bảng đích không có `target` ⇒ không cắt (Kachi không giao chuỗi cho app ấy được).
            terms.firstOrNull { t ->
                t.target != null && t.words == words && VoiceAppTargets.byKey(t.target)?.kind == kind
            }?.target?.let { return it to i }
            if (len > VoiceAppTargets.LONGEST_SPOKEN) return@forEach
            // Ba tầng, nới dần, và **thứ tự là hợp đồng**: khớp CHÍNH XÁC trước; rồi cụm rụng âm cuối
            // (*"vietma"* ⇒ VietMap); rồi cụm bị ASR bóp méo có NEO TIỀN TỐ (*"vietna"* ⇒ VietMap, [ĐO xe
            // 2026-09-20 §5]). Cả hai đường nới chỉ có ở đây, sau cụm đánh dấu — xem KDoc [VoiceAppTargets.bySpokenFuzzy].
            val target = VoiceAppTargets.bySpoken(words, kind)
                ?: VoiceAppTargets.bySpokenLoose(words, kind)
                ?: VoiceAppTargets.bySpokenFuzzy(words, kind)
                ?: return@forEach
            return target.key to i
        }
        return null
    }

    /**
     * Câu gọi app bằng **cách nói tiếng Việt** (*"mở bản đồ"* · *"mở viet map"*) thay vì bằng nhãn hệ thống.
     *
     * ## Vì sao cần, và vì sao nó đứng CUỐI
     * [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L6 (t45): *"mở bản đồ"* → `Unknown` vì nhãn app
     * do `PackageManager` cấp, và trên máy đó nó là *"Maps"*. Nhãn thật phải **thắng** (đó là chữ người dùng nhìn
     * thấy, và họ đổi được ngôn ngữ máy), nên nhánh này chỉ chạy khi **không cách hiểu nào có nghĩa** — kể cả một
     * cách hiểu lệch (`firstMiss`) cũng được thử lại ở đây trước, vì [ĐO] *"mở bản đồ vào ô số 2"* khớp nhầm nhãn
     * datum *"Số"* ở giữa câu và dừng lại ở MISMATCH.
     *
     * Chỉ khớp ở **đầu** phần đuôi, quét từ cụm DÀI xuống ngắn (cùng luật *"dãy dài nhất thắng"*); phần còn lại
     * vẫn đi qua [slotAt] nên *"mở bản đồ vào ô số 2"* không mất mệnh đề ô.
     */
    internal fun spokenApp(verb: VoiceVerb, rest: List<Token>, original: String): VoiceIntent? {
        val open = appByTargetName(rest) ?: return null
        return when {
            closesApp(verb) -> VoiceIntent.Unknown(VoiceUnknownReason.APP_CLOSE, original)
            VoiceGrammar.isAction(verb) -> open
            else -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
        }
    }

    /**
     * Động từ *"đóng / tắt / dừng"* — với một APP thì đó là việc Kachi **chưa làm được**
     * (xem [VoiceUnknownReason.APP_CLOSE]).
     */
    internal fun closesApp(verb: VoiceVerb): Boolean =
        verb == VoiceVerb.CLOSE || verb == VoiceVerb.OFF || verb == VoiceVerb.PAUSE

    /**
     * ═══ H3 · CÁCH GỌI APP **≥ 2 TỪ** ngay sau động từ THẮNG một nhãn NGẮN nằm giữa câu ══════════════════════
     *
     * ## Bệnh nó chữa — [ĐO máy ảo 2026-09-16], hai ca **MỞ NHẦM APP** (tệ hơn hẳn *"không hiểu"*)
     *  • *"mở việt máp"* ⇒ mở **Google Maps**. [VoiceAppIndex.build] (trước 2.91: `withPhonetics`) sinh dạng đọc
     *    cho MỌI nhãn app đã cài, nên nhãn *"Maps"* đẻ ra cụm MỘT từ `máp`, và cụm ấy đi vào **từ vựng chung**. Vòng quét (d) của
     *    [VoiceIntentParser] đi từ trái sang: vị trí 0 (`việt`) không khớp gì, vị trí 1 khớp `máp` ⇒ trả ngay
     *    một ý định *có nghĩa* rồi dừng. Cách gọi HAI từ *"việt máp"* của [VoiceSynonyms.APP_TARGETS] không bao
     *    giờ được hỏi tới.
     *  • *"mở bản đồ google"* ⇒ mở **Voice Search** (`googlequicksearchbox`), cùng cơ chế.
     *
     * Luật (d') mà H3 thêm **không** đỡ được hai ca này: nó chỉ so tại **cùng một vị trí**, còn ở đây cụm đáng
     * thắng bắt đầu **sớm hơn** cụm bị khớp nhầm. Mà *"dãy dài nhất thắng"* là luật số 1 của [VoiceGrammar], nên
     * nó phải được hỏi **trước khi** vòng quét kịp nhận một cụm ngắn ở giữa câu.
     *
     * ## Ba cổng, không cổng nào là trang trí
     *  1. **Chỉ ở vị trí 0** (ngay sau động từ). Giữa câu đã có (d') và (e) lo; quét mù mọi vị trí ở đây là mở
     *     lại đúng cánh cửa mà luật *"cách hiểu đầu tiên CÓ NGHĨA"* đóng lại.
     *  2. **Phải dài hơn cụm từ-vựng khớp tại vị trí 0, và tối thiểu 2 từ** (`maxOf(atZero, 1)`). Nhờ sàn 2 từ,
     *     một cách gọi MỘT từ không bao giờ cướp được nhãn app thật — *"mở google"* vẫn mở app *Google*, đúng
     *     cam kết ở KDoc [spokenApp] (*"nhãn thật phải thắng"*).
     *  3. **Chỉ động từ hành động, và không phải động từ đóng.** *"đóng google map"* vẫn phải ra
     *     [VoiceUnknownReason.APP_CLOSE]; *"xem …"* đã có nhánh riêng.
     */
    internal fun appAtHead(rest: List<Token>, terms: List<VoiceTerm>, verb: VoiceVerb): VoiceIntent.OpenApp? {
        if (!VoiceGrammar.isAction(verb) || closesApp(verb)) return null
        val atZero = VoiceGrammar.matchAt(rest, 0, terms).maxOfOrNull { it.words.size } ?: 0
        return appByTargetName(rest, 0, maxOf(atZero, 1))
    }

    /**
     * Cụm bắt đầu tại [at] của [rest] khớp một **cách nói** trong bảng đích ⇒ ý định mở app đó.
     *
     * @param longerThan chỉ nhận cách nói dài **hơn** ngần này từ. Mặc định 0 = nhận mọi độ dài (đường (e) của
     *   [VoiceIntentParser], nơi không còn ứng viên nào khác). Đường (d) truyền vào độ dài của cụm từ-vựng vừa
     *   khớp, để luật **dãy dài nhất thắng** áp cho cả tên app — xem KDoc ở chỗ gọi.
     */
    internal fun appByTargetName(rest: List<Token>, at: Int = 0, longerThan: Int = 0): VoiceIntent.OpenApp? {
        if (at >= rest.size) return null
        val tail = rest.subList(at, rest.size)
        val max = minOf(tail.size, VoiceAppTargets.LONGEST_SPOKEN)
        for (len in max downTo longerThan + 1) {
            val words = tail.take(len).map { it.norm }
            val target = VoiceAppTargets.bySpoken(words) ?: continue
            return VoiceIntent.OpenApp(target.label, slotAt(tail.subList(len, tail.size)), appKey = target.key)
        }
        return null
    }

    /**
     * Nhãn app đã cài trong phần đuôi + **phần còn lại sau nó**, hoặc `null`. Chỉ dùng cho ca
     * [VoiceTermKind.LAUNCHER] ở trên.
     *
     * Trả luôn phần đuôi vì *"mở ứng dụng YouTube **vào ô số 2**"* cũng phải nhận ra ô — cùng câu, cùng ý, chỉ
     * khác ở chỗ người ta nói thêm hai chữ *"ứng dụng"*. Trả mỗi cái tên thì nhánh này lặng lẽ mất mệnh đề ô.
     */
    internal fun appInTail(after: List<Token>, terms: List<VoiceTerm>): Pair<String, List<Token>>? = after.indices
        .firstNotNullOfOrNull { i ->
            VoiceGrammar.matchAt(after, i, terms).firstOrNull { it.kind == VoiceTermKind.APP }
                ?.let { it.id to after.subList(i + it.words.size, after.size) }
        }
}
