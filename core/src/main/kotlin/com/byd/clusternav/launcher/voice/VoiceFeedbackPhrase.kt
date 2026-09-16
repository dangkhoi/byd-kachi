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

    /** Chữ cái đầu về thường — *"Đã "* + *"Bật đèn"* thành *"Đã bật đèn"*, không phải *"Đã Bật đèn"*. */
    internal fun decap(s: String): String =
        if (s.isEmpty()) s else s.substring(0, 1).lowercase() + s.substring(1)

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
            val joined = ok.joinToString(", ") { decap(body(it.first)) }
            val sentence = Strings.t("Đã ", "Done: ") + joined
            return if (words(sentence) > MAX_WORDS) tooMany(ok.size) else sentence
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
