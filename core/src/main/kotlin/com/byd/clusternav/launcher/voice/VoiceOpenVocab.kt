package com.byd.clusternav.launcher.voice

/**
 * ═══ V1.1 · NHẬN DẠNG **HAI LƯỢT** — ghép bản NGỮ PHÁP với bản TỰ DO ══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R16**. Thuần Kotlin (`:core`) ⇒ kiểm off-car bằng chuỗi thật.
 *
 * ## Bài toán mà một lượt không giải được
 * Ngữ pháp đóng là thứ làm pha NGHE dùng được (xem KDoc [VoiceRecognizer]): nó ép mọi tiếng động về ~2.000 mục
 * của Kachi, nên không nghe nhầm thành *"mở khoá cửa"*. Nhưng chính nó cũng làm **tên bài hát và điểm đến không
 * bao giờ nghe ra được** — chúng là từ vựng MỞ, không nằm trong tập đóng. Tới 1.49 câu *"phát bài Diễm Xưa"*
 * nghe ra đúng hai chữ *"phát bài"* rồi hết.
 *
 * ## Cách giải: **hai bộ giải mã, một khúc tiếng**
 * Lượt 1 chạy như cũ (ngữ pháp). Nếu bản ngữ pháp có dạng *"&lt;cụm kích hoạt&gt; &lt;còn gì đó&gt;"* — tức
 * người ta vừa nói một câu nhạc/dẫn đường và còn một cái tên ở đuôi — thì lượt 2 chạy bộ giải mã **tự do** trên
 * **đúng khúc PCM vừa thu** (đã giữ trong RAM, ≤ 8 s × 32 KB/s = 256 KB) để đọc phần đuôi ấy. Điều kiện chính
 * xác, và vì sao nó **không** phải *"có `[unk]`"* như bản đầu, ở KDoc [triggerOf] — đó là một phép đo bác bỏ
 * một suy luận.
 *
 * Ba lý do lượt 2 chạy **sau**, không chạy song song:
 *  1. **Rẻ hơn ở ca thường.** Phần lớn câu lệnh (*"bật đèn đọc"*) không có cụm kích hoạt nào ⇒ không tốn lượt
 *     nào. Nuôi song song hai bộ giải mã cho mọi câu là trả giá CPU/pin suốt chuyến để dùng ở một phần nhỏ câu.
 *  2. **Kiểm được bằng bài canh.** *"Chỉ chạy sau cụm kích hoạt"* là một câu đọc được từ mã, không phải một lời
 *     hứa — `VoiceCommandWiringContractTest` canh đúng điều kiện đó.
 *  3. **Không đụng vòng nghe.** Vòng micro giữ nguyên nhịp 200 ms và trần 8 giây; lượt 2 chạy trên một mảng đã
 *     đóng, không có micro nào mở thêm.
 *
 * ## Ranh giới — thứ này KHÔNG làm
 * Nó **không** biết bài hát nào có thật, không sửa chính tả, không đoán. Nó chỉ **cắt và ghép chuỗi**; phần đuôi
 * đi tiếp nguyên văn tới app đích, và vì nó kém chính xác hơn phần còn lại nên [VoiceRiskTable] bắt hỏi lại.
 */
object VoiceOpenVocab {

    /** Mục *"ngoài từ vựng"* mà bộ giải mã trả về — cùng chuỗi với [VoicePhrases.UNK]. */
    private const val UNK = VoicePhrases.UNK

    /** Dạng đã tách từ của [UNK] (`"[unk]"` → `"unk"` sau [VoiceLexicon.tokenize]). */
    private const val UNK_WORD = "unk"

    /**
     * Cụm **MỞ TỪ VỰNG**: bản ngữ pháp có một trong các cụm này và còn từ đứng sau ⇒ phần đuôi thật nằm ở bản
     * tự do.
     *
     * ⚠ Danh sách này CỐ Ý hẹp, và mỗi cụm đều **kết thúc bằng một từ chỉ loại** (*"bài"* · *"nhạc"* · *"tới"* ·
     * *"đến"*): chỉ những cụm ấy mới chắc chắn còn một cái tên đứng sau. Thả một động từ trần như *"mở"* vào đây
     * là bật lượt tự do cho **mọi** câu *"mở …"* của xe — tốn lượt giải mã, và tệ hơn, đem một chuỗi tự do kém
     * chính xác vào đúng những câu mà tập đóng đang bảo vệ.
     *
     * Viết **không dấu** như mọi bảng so khớp khác ([VoiceLexicon.deaccent]).
     */
    val TRIGGERS: List<List<String>> = listOf(
        listOf("dan", "duong", "toi"), listOf("dan", "duong", "den"),
        listOf("chi", "duong", "toi"), listOf("chi", "duong", "den"),
        listOf("tim", "duong", "den"), listOf("tim", "duong", "toi"),
        listOf("duong", "toi"), listOf("duong", "den"),
        listOf("di", "toi"), listOf("di", "den"),
        listOf("navigate", "to"), listOf("directions", "to"),
        listOf("phat", "bai"), listOf("phat", "nhac"), listOf("mo", "bai"), listOf("mo", "nhac"),
        listOf("tim", "bai"), listOf("nghe", "bai"), listOf("nghe", "nhac"), listOf("bat", "bai"),
        listOf("play"),
    ).sortedByDescending { it.size }

    /**
     * Kết quả ghép.
     *
     * @property text câu cuối cùng đưa xuống [VoiceIntentParser] — đã bỏ mọi `[unk]`.
     * @property trigger cụm kích hoạt đã khớp, `null` = **không chạy lượt 2** (và [tail] rỗng).
     * @property tail phần đuôi lấy được từ bản tự do (nguyên văn, còn dấu).
     */
    data class Merged(val text: String, val trigger: List<String>?, val tail: String)

    /**
     * Bản ngữ pháp [grammarText] có cần một lượt TỰ DO không, và nếu có thì cụm kích hoạt là cụm nào.
     *
     * ## ⚠ [ĐO] 2026-09-14 trên máy ảo — điều kiện này đã phải SỬA sau phép đo đầu tiên
     * Bản đầu đòi bản ngữ pháp phải có `[unk]` (*"phần ngoài tập đóng rơi vào `[unk]`"*). Phép đo bác bỏ:
     * câu *"phát bài Diễm Xưa bằng YouTube Music"* qua WAV probe cho ra
     * **`"phát lài diếm sơ bằng mu yt"` — KHÔNG một `[unk]` nào**, dù cả phần đuôi đều ngoài tập đóng. Bộ giải
     * mã có ràng buộc **luôn tìm được một đường đi** qua ~2.000 mục và ép phần lạ thành từ gần giống nhất;
     * `[unk]` chỉ là một mục cạnh tranh, không phải một cái lưới.
     *
     * ⇒ Gate theo `[unk]` sẽ làm R16 **chết ngay từ đầu mà vẫn xanh mọi bài kiểm dựng tay** — đúng loại lỗi
     * CLAUDE.md §14 nói tới (tin vào cơ chế suy luận thay vì phép đo thật). Điều kiện đúng là điều kiện **về
     * hình dạng câu**, không về một mục ký hiệu:
     *  • có một cụm trong [TRIGGERS], **và**
     *  • còn **ít nhất một từ** đứng sau nó (kể cả `[unk]`).
     *
     * Bảo đảm về giá vẫn nguyên: câu lệnh xe (*"bật đèn đọc"*, *"đặt nhiệt độ 22"*) không chứa cụm kích hoạt
     * nào ⇒ không tốn lượt giải mã nào. *"Phát nhạc"* trần cũng không (không có gì đứng sau).
     */
    fun triggerOf(grammarText: String): List<String>? = position(grammarText)?.first

    /**
     * Cụm kích hoạt + vị trí của nó — lấy lần xuất hiện **cuối cùng** còn có từ đứng sau.
     *
     * Lấy lần cuối vì bản ngữ pháp hay chèn lặp ở đầu; và điều kiện *"còn từ đứng sau"* chính là điều kiện
     * *"có một cái đuôi để đọc"*.
     */
    private fun position(grammarText: String): Pair<List<String>, Int>? {
        val t = words(grammarText)
        var best: Pair<List<String>, Int>? = null
        var i = 0
        while (i < t.size) {
            // Khớp được thì **nhảy qua cả cụm**, không dịch một từ: *"dẫn đường tới"* chứa *"đường tới"*, và
            // tiếp tục ở `i+1` sẽ khớp cụm ngắn hơn rồi ghi đè cụm dài — tức cắt đuôi sai một từ (luật *dãy dài
            // nhất thắng* của cả dự án bị phá ngay tại đây).
            val hit = TRIGGERS.firstOrNull { VoiceLexicon.phraseAt(t, i, it) }
            if (hit == null) { i++; continue }
            if (i + hit.size < t.size) best = hit to i
            i += hit.size
        }
        return best
    }

    /**
     * Ghép bản ngữ pháp với bản tự do.
     *
     * @param grammarText chữ của lượt 1 (có thể chứa `[unk]`).
     * @param freeText chữ của lượt 2 — chuỗi rỗng khi lượt 2 không chạy / không nghe ra gì.
     */
    fun merge(grammarText: String, freeText: String): Merged {
        val clean = stripUnk(grammarText)
        val (trigger, at) = position(grammarText) ?: return Merged(clean, null, "")
        if (freeText.isBlank()) return Merged(clean, null, "")

        val t = words(grammarText)
        val head = t.subList(0, at + trigger.size).filterNot { it.norm == UNK_WORD }.map { it.raw }
        // Phần ngữ pháp còn lại SAU cụm kích hoạt, nếu nó là một mệnh đề *"bằng <app>"* đã khớp một đích: nó vẫn
        // thuộc tập đóng nên chính xác hơn hẳn bản tự do ⇒ giữ nguyên và đặt lại ở cuối (xem [spliceSuffix]).
        val suffix = appSuffix(t.subList(at + trigger.size, t.size).filterNot { it.norm == UNK_WORD })
        val tail = tailAfter(freeText, trigger)
        if (tail.isEmpty()) return Merged(clean, null, "")

        val body = (head + spliceSuffix(tail, suffix)).joinToString(" ")
        return Merged(body, trigger, tail.joinToString(" ") { it.raw })
    }

    /**
     * Mệnh đề *"bằng &lt;app&gt;"* ở cuối [rest], hoặc rỗng.
     *
     * Chỉ nhận khi **khớp một đích đã biết** ([VoiceAppTargets.bySpoken]). Không khớp thì phần ấy cũng chỉ là
     * chữ bộ giải mã đoán ra (*"bằng mu yt"* — [ĐO] máy ảo), giữ lại chỉ làm bẩn tên bài / điểm đến.
     */
    private fun appSuffix(rest: List<VoiceLexicon.Token>): List<VoiceLexicon.Token> {
        val at = rest.indexOfLast { it.norm in VoiceLexicon.BY_APP_MARKERS }
        if (at < 0 || at + 1 >= rest.size) return emptyList()
        val spoken = rest.subList(at + 1, rest.size).map { it.norm }
        return if (VoiceAppTargets.bySpoken(spoken) == null) emptyList() else rest.subList(at, rest.size)
    }

    // ── phần cắt ghép ────────────────────────────────────────────────────────────────────────────

    /**
     * Các từ của [freeText] đứng **sau** lần xuất hiện CUỐI CÙNG của [trigger].
     *
     * Lấy lần cuối chứ không lần đầu: bản tự do hay chèn thêm tiếng đệm ở đầu, và một câu như *"đi tới … đi tới
     * chợ Bến Thành"* (người nói nhắc lại) thì cái tên nằm sau lần nhắc sau. Không tìm thấy cụm ⇒ rỗng, và chỗ
     * gọi lùi về bản ngữ pháp — **không** đoán bừa một điểm cắt.
     */
    private fun tailAfter(freeText: String, trigger: List<String>): List<VoiceLexicon.Token> {
        val free = words(freeText)
        var at = -1
        for (i in free.indices) if (VoiceLexicon.phraseAt(free, i, trigger)) at = i
        if (at < 0) return emptyList()
        return free.subList(at + trigger.size, free.size)
    }

    /**
     * Đặt lại [suffix] (phần đã nghe bằng NGỮ PHÁP, vd *"bằng google map"*) vào cuối [tail].
     *
     * Cắt [tail] tại lần xuất hiện cuối của **từ đầu tiên** của suffix (thường là cụm đánh dấu *"bằng"*), rồi nối
     * suffix vào. Không tìm thấy ⇒ nối thẳng vào cuối. Nhờ vậy phần chọn app luôn lấy chữ của tập đóng — bản tự
     * do hay đọc *"google maps"* thành một chuỗi không khớp đích nào.
     */
    private fun spliceSuffix(
        tail: List<VoiceLexicon.Token>,
        suffix: List<VoiceLexicon.Token>,
    ): List<String> {
        val raw = tail.map { it.raw }
        if (suffix.isEmpty()) return raw
        val cut = tail.indexOfLast { it.norm == suffix[0].norm }
        val kept = if (cut >= 0) raw.subList(0, cut) else raw
        return kept + suffix.map { it.raw }
    }

    /** Bỏ mọi `[unk]` khỏi một chuỗi và nén khoảng trắng thừa. */
    fun stripUnk(text: String): String = text.replace(UNK, " ").trim().replace(Regex("\\s+"), " ")

    private fun words(text: String): List<VoiceLexicon.Token> = VoiceLexicon.tokenize(text)
}
