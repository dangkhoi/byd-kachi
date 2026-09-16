package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Strings

/**
 * ═══ V1 pha NÓI · MỘT CÂU ĐỌC ĐƯỢC, DỰNG TỪ CÁC DÒNG TRẢ LỜI ĐÃ CÓ ═══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R1**. Thuần Kotlin (`:core`) ⇒ kiểm off-car, không đụng máy đọc.
 *
 * ## Vì sao KHÔNG đọc thẳng chuỗi của [VoiceReply]
 * [VoiceReply] viết cho **mắt**: nó mở đầu bằng `✓`/`✗`, ngăn vế bằng `—`, bọc tên riêng trong `«»`, và viết
 * `Đặt Nhiệt độ = 24`. Đưa nguyên chuỗi ấy cho một máy đọc thì người lái nghe được *"dấu kiểm"*, *"gạch ngang"*,
 * *"bằng"* — hoặc tệ hơn, tuỳ máy đọc mà mấy ký tự đó **bị bỏ lặng**, và câu *"✗ Bật đèn"* nghe hệt câu
 * *"✓ Bật đèn"*. Một câu xác nhận mà không phân biệt nổi **làm được** với **không làm được** thì tệ hơn im lặng.
 *
 * ## Vì sao GỘP, không đọc từng dòng
 * Một câu ghép (*"đặt nhiệt độ 24 và gió mức 3"*) làm `VoiceDispatcher` gọi `say` **hai lần**
 * (`VoiceDispatcher.runFrom`). Đọc nối đuôi hai câu đầy đủ mất ~6 giây và câu sau đè lên câu trước (máy đọc nào
 * cũng `QUEUE_FLUSH` khi bị gọi lại) ⇒ người lái chỉ nghe được **nửa sau**. Gộp trước rồi đọc **một lần**.
 *
 * ## Vì sao có trần [MAX_WORDS]
 * Người đang lái không nghe hết một câu 30 từ; họ liếc màn hình. Quá trần thì nói **số việc** — ngắn, và vẫn đúng.
 * Chữ đầy đủ vẫn nằm trên tấm chữ (`VoiceOverlay`), tầng tiếng không phải chở hết mọi thứ tầng chữ chở.
 */
object VoiceFeedbackPhrase {

    /** Trần số từ cho một câu đọc. Quá trần ⇒ lùi về câu đếm việc ([tooMany]). */
    const val MAX_WORDS = 12

    /** Một dòng trả lời thuộc loại nào — quyết định cách ghép, xem [merge]. */
    internal enum class Kind { OK, FAIL, PLAIN, INTERIM }

    private const val OK_MARK = "✓"
    private const val FAIL_MARK = "✗"

    /**
     * Dòng **tạm** (*"đang tra điểm đến…"*) — không đọc.
     *
     * Nó sẽ bị chính câu kết quả thay thế sau vài giây; đọc nó nghĩa là người lái nghe hai câu cho một việc, và
     * câu đầu nói về một trạng thái đã hết hạn lúc nó phát xong.
     */
    private const val INTERIM_SUFFIX = "…"

    /**
     * Đọc được hay không, và thuộc loại nào.
     *
     * `PLAIN` là các câu không mang dấu: [VoiceReply.unknown], [VoiceReply.noReading]. Chúng đã là câu hoàn chỉnh.
     */
    internal fun kindOf(line: String): Kind {
        val s = line.trim()
        return when {
            s.isEmpty() -> Kind.INTERIM
            s.endsWith(INTERIM_SUFFIX) -> Kind.INTERIM
            s.startsWith(OK_MARK) -> Kind.OK
            s.startsWith(FAIL_MARK) -> Kind.FAIL
            else -> Kind.PLAIN
        }
    }

    /**
     * Dòng này là dòng **tạm** (*"đang tra điểm đến…"*) — tức việc thật **chưa xong**, câu trả lời còn về sau.
     *
     * V3 · R9 dùng nó làm cổng cho hội thoại: giữ micro mở sau một lệnh còn đang tra mạng nghĩa là micro đóng
     * trước khi người lái biết việc xong hay hỏng. Phơi ra thay vì để chỗ gọi tự so `"…"`: luật *"dấu ba chấm =
     * tạm"* đã khai một lần ở đây, và một bản sao ở `:app` sẽ lệch đúng vào lần ai đó đổi ký tự.
     */
    fun isInterim(line: String): Boolean = kindOf(line) == Kind.INTERIM

    /**
     * Bỏ dấu + đổi các ký hiệu **của mắt** thành thứ đọc lên nghe được.
     *
     * Bốn phép thay, mỗi phép chữa một thứ đã thấy trong chuỗi thật của [VoiceReply]:
     *  • `✓`/`✗` đầu câu — xem KDoc lớp; ai gọi sẽ tự gắn *"Đã"* / *"Chưa"*;
     *  • `«…»` (tên bài / tên nơi) — máy đọc phát âm ngoặc nhọn, hoặc nuốt luôn cả tên;
     *  • ` = ` (bậc của `ControlKind.STEP`) — *"Nhiệt độ bằng 24"* không phải tiếng Việt nói;
     *  • ` — ` (đuôi lý do) — gạch ngang dài thành một quãng lặng vô nghĩa; dấu phẩy mới là chỗ ngắt hơi.
     */
    internal fun body(line: String): String =
        line.trim()
            .removePrefix(OK_MARK)
            .removePrefix(FAIL_MARK)
            .replace("«", "")
            .replace("»", "")
            .replace(" = ", " ")
            .replace(" — ", ", ")
            .replace('\n', ' ')
            .trim()
            .trim(',')
            .trim()

    /**
     * Chữ cái đầu về thường — *"Đã "* + *"Bật đèn"* thành *"Đã bật đèn"*, không phải *"Đã Bật đèn"*.
     *
     * ## ⚠ [SOÁT chuỗi-lời-đáp 2026-09-17] CHỮ VIẾT TẮT được MIỄN — và đó là một lỗi có thật, không phải phòng xa
     * Nhãn nút `powertrain_mode` là **"EV / HEV"**. Hạ chữ đầu cho ra `"eV / HEV"`, mà
     * [TtsPronunciation.spellOut] chỉ nhận chuỗi **toàn chữ HOA** ⇒ `"eV"` trượt khỏi đường đánh vần và rơi về
     * cách đọc thô của Piper ⇒ người lái nghe *"Chưa eV / hát e vê…"* (một nửa câu đánh vần đúng, một nửa không).
     * Hai tầng ở hai module khác nhau nên không bên nào một mình thấy được lỗi; bài canh
     * `decap roi spellOut van danh van duoc chu viet tat` khoá đúng **cặp** đó.
     *
     * Luật: chỉ hạ chữ khi **từ đầu tiên không phải chữ viết tắt** (≥ 2 ký tự và mọi chữ cái đều HOA).
     */
    internal fun decap(s: String): String = when {
        s.isEmpty() -> s
        isAcronym(s.takeWhile { !it.isWhitespace() }) -> s
        else -> s.substring(0, 1).lowercase() + s.substring(1)
    }

    /** Từ này là chữ viết tắt? (≥ 2 ký tự, có chữ cái, và mọi chữ cái đều HOA — `"EV"` · `"PM2.5"` · `"SOH)"`.) */
    private fun isAcronym(w: String): Boolean =
        w.length >= 2 && w.any { it.isLetter() } && w.all { !it.isLetter() || it.isUpperCase() }

    /**
     * Thân dòng đã **tự mang** lời dẫn *"Đã"* rồi? (vd [VoiceReply.doneActual] = *"Đã gửi Nhiệt độ 24 — xe báo 23"*.)
     *
     * ## [SOÁT chuỗi-lời-đáp 2026-09-17] — lỗi *"Đã đã gửi…"*
     * [merge] ghép *"Đã "* vào trước mọi dòng OK. Dòng của `doneActual` đã bắt đầu bằng *"Đã gửi"* ⇒ câu đọc ra
     * **"Đã đã gửi Nhiệt độ 24, xe báo 23"**. Kiểm bằng **nội dung** chứ không bằng một danh sách hàm được miễn:
     * bất kỳ dòng nào mở đầu bằng chính lời dẫn ấy cũng không được nhận thêm một lời dẫn thứ hai.
     */
    private fun hasDoneLead(body: String): Boolean =
        body.startsWith(Strings.t("Đã ", "Done: "), ignoreCase = true)

    private fun words(s: String): Int = s.split(' ', '\n', '\t').count { it.isNotBlank() }

    /** Câu thay thế khi vượt [MAX_WORDS] — nói **số việc**, không nói sai. */
    private fun tooMany(n: Int): String = Strings.t("Đã xong $n việc", "$n things done")

    /**
     * Gộp các dòng `say` của **một lượt** thành một câu để đọc; `null` ⇒ không có gì đáng đọc.
     *
     * ## Luật ghép — vì sao KHÔNG bao giờ gộp thành công với thất bại
     * Gộp *"✓ đặt nhiệt độ 24"* với *"✗ mở cốp"* dưới một chữ *"Đã"* là **nói dối** đúng ở chỗ nguy hiểm nhất:
     * người lái nghe *"đã"* rồi thôi không nhìn màn nữa. Nên khi có bất kỳ vế nào hỏng, câu đọc **mở đầu bằng vế
     * hỏng** (*"Chưa mở được cốp"*) và chỉ nói thêm số việc đã xong ở đuôi. Thứ tự ấy có chủ ý: tai người giữ lại
     * mấy từ đầu, và mấy từ đầu phải là thứ cần nhìn lại.
     *
     * @param lines các dòng theo đúng thứ tự `VoiceDispatcher` phát ra.
     */
    fun merge(lines: List<String>): String? {
        val kept = lines.map { it to kindOf(it) }.filter { it.second != Kind.INTERIM }
        if (kept.isEmpty()) return null

        val ok = kept.filter { it.second == Kind.OK }
        val bad = kept.filter { it.second != Kind.OK }

        if (bad.isEmpty()) {
            val lead = Strings.t("Đã ", "Done: ")
            val single = ok.singleOrNull()?.let { body(it.first) }
            // Dòng đã tự mang lời dẫn ⇒ giữ NGUYÊN VĂN (kể cả chữ hoa đầu câu): `decap` ở đây sẽ cho ra
            // *"đã gửi…"* — một câu mở đầu bằng chữ thường.
            val sentence =
                if (single != null && hasDoneLead(single)) single
                // ⚠ [SOÁT 1.69 · P2] Nhánh NHIỀU dòng cũng phải kiểm, không chỉ nhánh một dòng. KDoc
                // [hasDoneLead] hứa *"bất kỳ dòng nào mở đầu bằng chính lời dẫn ấy"*, nhưng tới lượt soát này
                // phép kiểm chỉ chạy trên `ok.singleOrNull()` ⇒ hai vế mà một vế là [VoiceReply.doneActual]
                // vẫn đọc ra **"Đã đã gửi Nhiệt độ 24, …"**. Hôm nay ca ấy khó tới (dòng đọc-lại về SAU
                // `flushed` nên đi đường một-dòng) — tức đây là một bẫy **chờ sẵn**, không phải một lỗi đang
                // kêu, và đó chính là loại mà lượt soát phải bắt. Gỡ lời dẫn rồi mới ghép: một lời dẫn cho cả
                // câu, đúng như nhánh một-dòng.
                else lead + ok.joinToString(", ") { p ->
                    val b = body(p.first)
                    decap(if (hasDoneLead(b)) b.substring(lead.length) else b)
                }
            // ⚠ [SOÁT chuỗi-lời-đáp 2026-09-17] MỘT dòng thì KHÔNG bao giờ lùi về câu đếm việc.
            // `tooMany` ("Đã xong 1 việc") sinh ra cho ca **nhiều việc** — ở đó nó nói ngắn mà vẫn đủ. Với MỘT
            // dòng nó nói **ít hơn hẳn** dòng gốc: [VoiceReply.doneActual] tồn tại để đọc ra HAI con số (đã gửi
            // 24 · xe báo 23), và "Đã xong 1 việc" nuốt đúng hai con số ấy. Cắt theo từ như nhánh HỎNG bên dưới:
            // mất phần đuôi còn hơn mất phần đầu.
            if (words(sentence) <= MAX_WORDS) return sentence
            return if (ok.size == 1) clampWords(sentence, MAX_WORDS) else tooMany(ok.size)
        }

        val head = bad.first()
        val headText = when (head.second) {
            Kind.FAIL -> Strings.t("Chưa ", "Could not ") + decap(body(head.first))
            else -> body(head.first)
        }
        val tail = if (ok.isEmpty()) "" else Strings.t(", ${ok.size} việc khác đã xong", ", ${ok.size} other(s) done")
        val sentence = headText + tail
        // Vế hỏng dài quá trần vẫn PHẢI đọc (nó là thứ người lái cần biết) — cắt ở ranh giới từ, không cắt giữa từ.
        return if (words(sentence) > MAX_WORDS) clampWords(sentence, MAX_WORDS) else sentence
    }

    /** Cắt còn [n] từ, thêm dấu ba chấm **ký tự** (máy đọc bỏ qua) để câu không cụt giữa chừng trên màn log. */
    internal fun clampWords(s: String, n: Int): String {
        val parts = s.split(' ').filter { it.isNotBlank() }
        if (parts.size <= n) return s
        return parts.take(n).joinToString(" ").trimEnd(',') + INTERIM_SUFFIX
    }
}
