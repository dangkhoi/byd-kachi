package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.TelemetryRegistry
import com.byd.clusternav.launcher.voice.VoiceLexicon.Token
import com.byd.clusternav.launcher.voice.VoicePhoneticConfusions.Syl

/**
 * ═══ H7 · SO KHỚP **CHỊU LỖI CHÍNH TẢ** — chạy SAU khi đường chính xác đã trượt ══════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` §4.4. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Vị trí trong đường đi: CUỐI, và chỉ khi đã hỏng
 * [VoiceIntentParser] chạy y nguyên như trước — khớp chính xác, bí danh, cụm nghe nhầm có ngữ cảnh. Chỉ khi
 * kết quả là [VoiceIntent.Unknown] thì tầng này mới được hỏi. Hai hệ quả, và cả hai đều cố ý:
 *  • **không câu nào đang chạy tốt bị đụng tới** — 4 385 bài kiểm hiện có không đi qua một dòng nào ở đây;
 *  • giá phải trả (quét ~600 cụm × mọi đoạn con) chỉ xảy ra trên câu **đã hỏng**, tức lúc người lái đằng nào
 *    cũng phải chờ một câu trả lời.
 *
 * ## Ba luật giữ cho nó KHÔNG đoán bừa
 *  1. **Đoạn đã khớp chính xác là vùng cấm** ([coveredByExact]). [ĐO off-car] không có luật này thì
 *     *"tăng đèn đọc"* (cố ý để MISMATCH) bị "sửa" thành `Bật Đèn đọc` — máy làm một việc mà câu không bảo.
 *  2. **Độ dài phải bằng nhau** (sau khi bỏ tiếng đệm). [ĐO nguyên mẫu host] cho phép bớt một âm tiết thì
 *     *"mở cửa"* khớp *"mở khóa cửa"* (giá 0,27) ⇒ một câu xin **mở cửa** thành lệnh **mở khoá** cả xe. Lỗi
 *     mà 5 bản thu thật đo được là **âm tiết trượt nấc**, không phải âm tiết bị nuốt — chữa đúng bệnh đã đo.
 *  3. **Nhập nhằng thì KHÔNG chọn.** Cụm thắng phải hơn cụm nhì (khác chữ) ít nhất [MARGIN]; không thì trả
 *     `null` và câu giữ nguyên [VoiceIntent.Unknown] ⇒ [VoiceClarify] hỏi lại *"X hay Y?"*. Một câu hỏi thừa
 *     chỉ tốn 2 giây; một lệnh đoán sai trên xe đang chạy thì không lấy lại được.
 *
 * ## Vì sao KHÔNG đem tên app / tên hồ sơ vào đây
 * Tên app đã có đường riêng đo bằng âm Việt ([VoiceSynonyms.APP_TARGETS] · [VoiceAppPhonetics]), còn tên hồ sơ
 * là **chữ người dùng tự gõ** — nới lỏng phép so ở đó là mời mọi câu lạ đổi hồ sơ tài xế.
 */
internal object VoicePhoneticMatch {

    /** Giá trung bình **trên mỗi âm tiết** tối đa còn nhận. 0,35 ⇒ một cụm 2 âm tiết được lẫn đúng một âm. */
    const val ACCEPT: Double = 0.35

    /** Cụm thắng phải rẻ hơn cụm nhì (khác chữ) ngần này, không thì coi là **nhập nhằng** ⇒ hỏi lại. */
    const val MARGIN: Double = 0.10

    /** Cụm dài nhất trong từ vựng đáng đem ra so — dài hơn là một câu, không phải một cái tên. */
    private const val MAX_SPAN = 4

    /**
     * **Trần TUYỆT ĐỐI**: một lần chữa không bao giờ được đắt bằng *"một từ sai hẳn"*
     * ([VoicePhoneticConfusions.SUB_OTHER]).
     *
     * ## Vì sao ngưỡng trung bình [ACCEPT] là chưa đủ
     * [ĐO quét corpus 2026-09-16] — 4 486 câu của `scripts/voice/data/variants.tsv`: chỉ có [ACCEPT] thì
     * *"đèn chạy ban ngày"* khớp nhãn *"Đèn ban ngày"* với giá 1,00/3 = 0,33 — tức **một âm tiết sai hẳn** vẫn
     * lọt chỉ vì cụm đủ dài. Ca đó tình cờ ra đúng nút, nhưng cơ chế thì sai: *"đèn trước trái"* cũng sẽ khớp
     * *"kính trước-trái"* y như thế. Thiếu một từ trong nhãn là lỗi **TỪ VỰNG** (chữa bằng [VoiceSynonyms]),
     * không phải lỗi **chính tả** — hai bệnh khác nhau, và tầng này chỉ nhận đúng bệnh của nó.
     *
     * ⇒ Mọi âm tiết lệch đều phải là một cặp **có tên trong bảng lẫn**; tổng lại vẫn phải rẻ hơn một từ sai.
     */
    private val TOTAL_CAP = VoicePhoneticConfusions.SUB_OTHER

    /** Chỉ **nút · thông tin · gói lệnh · hành động launcher**: xem KDoc lớp về app/hồ sơ. */
    private val FUZZY_KINDS = setOf(
        VoiceTermKind.CONTROL, VoiceTermKind.TELEMETRY, VoiceTermKind.MACRO, VoiceTermKind.LAUNCHER,
    )

    /**
     * Sửa chuỗi từ [t] thành chuỗi **đọc được**, hoặc `null` khi không sửa được / không dám sửa.
     *
     * Trả về **chuỗi từ**, không trả về ý định: mọi luật của [VoiceIntentParser] (dãy dài nhất thắng · động từ
     * chọn ứng viên · mệnh đề đuôi · câu ghép) phải được áp lại **nguyên vẹn** trên chuỗi đã sửa. Tầng này tự
     * dựng ý định là dựng bộ phân tích thứ hai — đúng bẫy mà `VoiceCommandWiringContractTest` canh.
     */
    internal fun repair(tokens: List<Token>, terms: List<VoiceTerm>): List<Token>? {
        val t = tokens.dropWhile { it.norm in VoiceLexicon.FILLERS }
        if (t.isEmpty()) return null
        val syls = t.map { VoicePhoneticConfusions.syl(it.raw) }
        val busy = coveredByExact(t, terms)

        // ⚠ ĐỐI TƯỢNG trước, ĐỘNG TỪ sau. Cụm đối tượng nằm ở giữa câu nên chỉ số của nó phải được dùng khi
        // chuỗi còn NGUYÊN; động từ luôn ở vị trí 0 nên nó không quan tâm phần sau đã đổi độ dài hay chưa.
        val obj = objectHit(syls, busy, terms)
        if (obj is Hit.Ambiguous) return null
        // ⚠ Hai lượt dò KHÔNG được giẫm lên nhau: cụm đối tượng có quyền bắt đầu ngay ở vị trí 0 (*"mật độ hai
        // mươi bốn độ"* — cả câu là tên một nút). Chặn trần độ dài của lượt động từ bằng chính vị trí ấy thì ca
        // đó tự loại mình, không cần một nhánh riêng.
        val verb = verbHit(syls, busy, minOf(2, (obj as? Hit.One)?.at ?: 2))
        if (verb is Hit.Ambiguous) return null
        if (obj !is Hit.One && verb !is Hit.One) return null

        var out = t
        if (obj is Hit.One) out = replace(out, obj.at, obj.len, obj.words)
        if (verb is Hit.One) out = replace(out, verb.at, verb.len, verb.words)
        return out
    }

    /**
     * Ý định của [got], hoặc ý định đọc lại được sau khi chữa chính tả — dùng ở đúng ba chỗ trong
     * [VoiceIntentParser].
     *
     * Hai cổng: chỉ chạy khi [got] đã hỏng, và **chỉ nhận** khi lần đọc lại ra một ý định CÓ NGHĨA. Cổng thứ
     * hai mới là thứ giữ an toàn: [ĐO nguyên mẫu host] vài câu đời thường vẫn "sửa" được thành một cụm của xe
     * (*"Tin thời sự hôm nay"* → `pin`), nhưng chuỗi sửa xong vẫn không có động từ ⇒ vẫn `Unknown` ⇒ câu trả
     * lời cho người lái **không đổi một chữ**.
     */
    internal fun orRepair(
        got: VoiceIntent,
        tokens: List<Token>,
        terms: List<VoiceTerm>,
        reparse: (List<Token>) -> VoiceIntent,
    ): VoiceIntent {
        if (got !is VoiceIntent.Unknown) return got
        val fixed = repair(tokens, terms) ?: return got
        val again = reparse(fixed)
        return if (again is VoiceIntent.Unknown) got else again
    }

    /** Kết quả một lượt dò. [Ambiguous] cố ý **không** mang cụm nào: nó là lệnh *"đừng chọn"*. */
    internal sealed interface Hit {
        data class One(val at: Int, val len: Int, val words: List<String>, val cost: Double) : Hit
        data class Ambiguous(val at: Int, val len: Int) : Hit
        object None : Hit
    }

    /** Một ứng viên trong từ vựng: chữ đã bỏ dấu + âm tiết (kèm thanh nếu tra được nhãn có dấu). */
    private data class Cand(val words: List<String>, val syls: List<Syl>)

    /** Cụm thắng, chữ của nó, và giá của cụm nhì **khác chữ** (dùng cho [MARGIN]). */
    private data class Scored(val cost: Double, val words: List<String>, val runner: Double)

    // ── Dò ĐỘNG TỪ ───────────────────────────────────────────────────────────────────────────────

    /**
     * Động từ ở vị trí 0 nghe trượt (*"bằng ghế sưởi"* · *"dần nhạc"* · *"sẽ áp suất lốp…"*).
     *
     * Chỉ dò khi vị trí 0 **chưa** khớp chính xác: câu đã có động từ đúng thì thứ thiếu nằm ở chỗ khác.
     *
     * @param maxLen trần độ dài — chỗ gọi hạ nó xuống để không giẫm lên cụm đối tượng vừa dò được.
     */
    @Suppress("ReturnCount")
    private fun verbHit(syls: List<Syl>, busy: Set<Int>, maxLen: Int): Hit {
        if (syls.isEmpty() || 0 in busy) return Hit.None
        for (len in 1..maxLen) {
            if (len > syls.size) break
            if ((0 until len).any { it in busy || syls[it].base in VoiceLexicon.FILLERS }) break
            val r = bestOf(syls.subList(0, len), VERB_CANDS) ?: continue
            if (r.cost > ACCEPT) continue
            return if (r.runner - r.cost < MARGIN) Hit.Ambiguous(0, len) else Hit.One(0, len, r.words, r.cost)
        }
        return Hit.None
    }

    /** Bảng động từ ở dạng ứng viên. Thanh để `null`: [VoiceGrammar.VERBS] khai KHÔNG DẤU, và chép tay một bản
     * có dấu thứ hai ở đây đúng là thứ [VoiceSynonyms] sinh ra để chặn — thiếu thanh chỉ làm phép so **nới**
     * ra đúng bằng mức trước khi có bảng thanh, không làm nó sai. */
    private val VERB_CANDS: List<Cand> by lazy {
        VoiceGrammar.VERBS.map { it.first }.distinct().map { w -> Cand(w, w.map { Syl(it, null) }) }
    }

    // ── Dò ĐỐI TƯỢNG ─────────────────────────────────────────────────────────────────────────────

    /** Đoạn con RẺ NHẤT của câu khớp một cụm trong từ vựng, hoặc [Hit.Ambiguous] khi có hai cụm ngang nhau. */
    private fun objectHit(syls: List<Syl>, busy: Set<Int>, terms: List<VoiceTerm>): Hit {
        val cands = candidates(terms)
        var best: Scored? = null
        var bestCost = Double.MAX_VALUE
        var at = -1
        var len = 0
        for (i in syls.indices) {
            for (n in 1..MAX_SPAN) {
                if (i + n > syls.size) break
                if ((i + n - 1) in busy) break
                val r = bestOf(syls.subList(i, i + n), cands) ?: continue
                if (r.cost > ACCEPT || r.cost >= bestCost) continue
                best = r
                bestCost = r.cost
                at = i
                len = n
            }
        }
        val b = best ?: return Hit.None
        return if (b.runner - b.cost < MARGIN) Hit.Ambiguous(at, len) else Hit.One(at, len, b.words, b.cost)
    }

    /**
     * Cụm rẻ nhất trong [cands] cho đoạn [span], kèm giá của cụm nhì **khác chữ**.
     *
     * ## Vì sao cụm nhì tính theo CHỮ, không theo mã
     * [ĐO] 18 nhãn trùng giữa mục ĐỌC và HÀNH ĐỘNG (`CapabilityCatalog.collidingLabels`): *"Cốp sau"* vừa là
     * nút vừa là datum. Tính nhập nhằng theo **mã** thì mọi cặp ấy thành "nhập nhằng" và câu nào cũng bị hỏi
     * lại — trong khi đó là việc mà `VoiceIntentParser.choose` đã giải xong từ V1 (loại động từ chọn ứng viên).
     * Cùng CHỮ thì không có gì để hỏi.
     */
    private fun bestOf(span: List<Syl>, cands: List<Cand>): Scored? {
        val core = span.filterNot { it.base in VoiceLexicon.FILLERS }
        if (core.isEmpty()) return null
        val scored = cands.mapNotNull { c ->
            when {
                c.syls.size != core.size -> null
                // Cụm MỘT âm tiết: chỉ cặp đã ĐO mới được vào — xem KDoc [VoicePhoneticConfusions.nearMiss].
                core.size == 1 && !VoicePhoneticConfusions.nearMiss(core[0], c.syls[0]) -> null
                else -> distance(span, c.syls).let { d ->
                    if (d >= TOTAL_CAP) null else (d / maxOf(span.size, c.syls.size)) to c.words
                }
            }
        }.sortedBy { it.first }
        val top = scored.firstOrNull() ?: return null
        val runner = scored.firstOrNull { it.second != top.second }?.first ?: Double.MAX_VALUE
        return Scored(top.first, top.second, runner)
    }

    // ── Khoảng cách sửa chữa trên ÂM TIẾT ────────────────────────────────────────────────────────

    /**
     * Khoảng cách sửa chữa (Levenshtein có trọng số) giữa hai dãy **âm tiết** — không phải giữa hai dãy ký tự.
     *
     * Đơn vị là âm tiết vì tiếng Việt là ngôn ngữ đơn lập: *"cốp"* so với *"cấp"* lệch một ký tự nhưng là
     * **một** lỗi, còn *"pin"* so với *"phim"* lệch hai ký tự và cũng là **một** lỗi. Đếm theo ký tự thì hai ca
     * ấy được giá khác nhau mà chẳng vì lý do gì có thật.
     */
    private fun distance(a: List<Syl>, b: List<Syl>): Double {
        var prev = DoubleArray(b.size + 1)
        for (j in 1..b.size) prev[j] = prev[j - 1] + gap(b[j - 1])
        for (i in 1..a.size) {
            val cur = DoubleArray(b.size + 1)
            cur[0] = prev[0] + gap(a[i - 1])
            for (j in 1..b.size) {
                cur[j] = minOf(
                    prev[j - 1] + VoicePhoneticConfusions.cost(a[i - 1], b[j - 1]),
                    prev[j] + gap(a[i - 1]),
                    cur[j - 1] + gap(b[j - 1]),
                )
            }
            prev = cur
        }
        return prev[b.size]
    }

    private fun gap(s: Syl): Double =
        if (s.base in VoiceLexicon.FILLERS) VoicePhoneticConfusions.GAP_FILLER else VoicePhoneticConfusions.GAP

    // ── Từ vựng ứng viên ─────────────────────────────────────────────────────────────────────────

    private fun candidates(terms: List<VoiceTerm>): List<Cand> =
        terms.filter { it.kind in FUZZY_KINDS }.map { Cand(it.words, sylsOf(it.words)) }

    /** Chữ đã bỏ dấu → âm tiết; thanh lấy từ [ACCENTED] khi cụm đúng là một **nhãn** của bộ đăng ký. */
    private fun sylsOf(words: List<String>): List<Syl> {
        val tones = ACCENTED[words.joinToString(" ")]
        return words.mapIndexed { i, w -> Syl(w, tones?.getOrNull(i)) }
    }

    /**
     * Nhãn CÓ DẤU của bốn bộ đăng ký, tra theo dạng đã bỏ dấu ⇒ *"nhiet do"* biết mình là *"Nhiệt độ"*.
     *
     * Đây là cách duy nhất lấy được thanh điệu mà **không** chép tay một bảng thứ hai: [VoiceTerm.words] đã bỏ
     * dấu từ lâu và đổi hình dạng của nó là đụng vào `distinct()` của [VoiceGrammar.terms]. Cụm nào không phải
     * nhãn (bí danh của [VoiceSynonyms], nhãn tiếng Anh) thì không có thanh ⇒ `null` ⇒ không phạt.
     */
    private val ACCENTED: Map<String, List<VoicePhoneticConfusions.Tone>> by lazy {
        val out = HashMap<String, List<VoicePhoneticConfusions.Tone>>()
        fun put(label: String?) {
            val toks = VoiceLexicon.tokenize(label ?: return)
            if (toks.isEmpty()) return
            out.putIfAbsent(toks.joinToString(" ") { it.norm }, toks.map { VoicePhoneticConfusions.toneOf(it.raw) })
        }
        ControlRegistry.ALL.forEach { put(it.label); put(it.short) }
        TelemetryRegistry.ALL.forEach { put(it.label); put(it.short) }
        ActionMacros.ALL.forEach { put(it.label) }
        LauncherActions.ALL.forEach { put(it.label) }
        out
    }

    // ── Vùng cấm + thay chuỗi ────────────────────────────────────────────────────────────────────

    /**
     * Chỉ số từ đã được một cụm **khớp chính xác** phủ (từ vựng ở bất kỳ đâu, hoặc động từ ở đầu câu).
     *
     * Đây là luật số 1 của lớp: thứ máy đã nghe ĐÚNG thì không được "sửa".
     */
    private fun coveredByExact(t: List<Token>, terms: List<VoiceTerm>): Set<Int> {
        val out = HashSet<Int>()
        t.indices.forEach { i ->
            VoiceGrammar.matchAt(t, i, terms).forEach { term ->
                (i until i + term.words.size).forEach { out.add(it) }
            }
        }
        VoiceGrammar.VERBS.firstOrNull { VoiceLexicon.phraseAt(t, 0, it.first) }
            ?.let { hit -> hit.first.indices.forEach { out.add(it) } }
        return out
    }

    /**
     * Thay đoạn `[at, at+len)` bằng [words].
     *
     * Từ mới mang **chữ đã bỏ dấu** ở cả [Token.raw] lẫn [Token.norm]: chuỗi này chỉ đi tiếp vào phép so khớp,
     * còn phần **từ vựng mở** (tên bài / điểm đến) thì không bao giờ nằm trong một đoạn đã sửa — [FUZZY_KINDS]
     * cố ý không có `MEDIA`/`NAV`/`APP`.
     */
    private fun replace(t: List<Token>, at: Int, len: Int, words: List<String>): List<Token> =
        t.subList(0, at) + words.map { Token(it, it) } + t.subList(at + len, t.size)
}
