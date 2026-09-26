package com.byd.clusternav.launcher.voice

/**
 * ═══ CHUẨN HOÁ SỐ ĐỌC ↔ CHỮ SỐ — cho khớp sổ địa chỉ + địa chỉ tự do (findings 2026-09-23) ══════════════════
 *
 * On-car 2026-09-23: "dẫn đến **công ty 1**" (ô lưu "Công ty 1") → GMaps thay vì sổ; "**<số nhà>** <tên đường>" →
 * ASR "sáu bảy hồ văn thái", gửi nguyên chữ "sáu bảy" ra GMaps. Gốc: khớp sổ so token CHÍNH XÁC ("1"≠"một"),
 * địa chỉ tự do không gộp "sáu bảy"→"67".
 *
 *  • [wordsToDigits] — trên token ĐÃ BỎ DẤU: cụm số học ("hai mươi tư"→"24") qua [VoiceLexicon.readNumber];
 *    chuỗi đơn vị đọc RỜI ("sau bay"→"67") nối chữ số. Dùng khớp sổ (chuẩn cả nhãn lẫn câu ⇒ "công ty 1"=="công ty một").
 *  • [normalizeSpokenNumbers] — thay các CỤM TỪ-SỐ trong text bằng chữ số, GIỮ NGUYÊN từ không-số (kể cả dấu),
 *    cho địa chỉ tự do trước khi gửi bản đồ ⇒ "sáu bảy hồ văn thái" → "67 hồ văn thái".
 *
 * Thuần `:core` — test off-car.
 */
object VoiceNumberNorm {

    private val ONES = mapOf(
        "khong" to 0, "mot" to 1, "hai" to 2, "ba" to 3, "bon" to 4,
        "nam" to 5, "sau" to 6, "bay" to 7, "tam" to 8, "chin" to 9,
    )
    /** Từ báo hiệu SỐ HỌC — có mặt (sau một đơn vị) thì dùng readNumber thay vì nối chữ số rời. */
    private val ARITH = setOf("muoi", "tram", "nghin", "ngan", "trieu", "ham", "linh", "le", "tu", "lam")

    /** Đổi từ-số trong [words] (đã bỏ dấu) thành chữ số; từ khác giữ nguyên. */
    fun wordsToDigits(words: List<String>): List<String> {
        val toks = words.map { VoiceLexicon.Token(it, it) }
        val out = ArrayList<String>()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            if ((w in ONES || w.toIntOrNull() in 0..9) && words.getOrNull(i + 1) in ARITH) {   // số học (kể cả "1 ngàn")
                val num = VoiceLexicon.readNumber(toks, i)
                if (num != null && num.consumed > 0) { out.add(num.value.toString()); i += num.consumed; continue }
            }
            if (w in ONES) {                                       // chuỗi đơn vị rời: sau bay → 67
                val sb = StringBuilder()
                while (i < words.size && words[i] in ONES && words.getOrNull(i + 1) !in ARITH) {
                    sb.append(ONES[words[i]]); i++
                }
                if (sb.isNotEmpty()) { out.add(sb.toString()); continue }
            }
            out.add(w); i++
        }
        return out
    }

    /** Thay cụm từ-số bằng chữ số trong [text], giữ nguyên từ không-số (dấu nguyên vẹn) cho địa chỉ. */
    fun normalizeSpokenNumbers(text: String): String {
        val raw = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (raw.isEmpty()) return text
        val norm = raw.map { VoiceLexicon.tokenize(it).firstOrNull()?.norm ?: it.lowercase() }
        val out = ArrayList<String>()
        var i = 0
        while (i < raw.size) {
            val n = norm[i]
            if ((n in ONES || n.toIntOrNull() in 0..9) && norm.getOrNull(i + 1) in ARITH) {     // số học (kể cả đọc tắt chữ số "1 ngàn")
                val num = VoiceLexicon.readNumber(norm.map { VoiceLexicon.Token(it, it) }, i)
                if (num != null && num.consumed > 0) { out.add(num.value.toString()); i += num.consumed; continue }
            }
            if (n in ONES) {                                       // đơn vị rời
                val sb = StringBuilder()
                while (i < raw.size && norm[i] in ONES && norm.getOrNull(i + 1) !in ARITH) { sb.append(ONES[norm[i]]); i++ }
                if (sb.isNotEmpty()) { out.add(sb.toString()); continue }
            }
            out.add(raw[i]); i++                                    // giữ TỪ GỐC (còn dấu)
        }
        return joinHouseNumber(out)
    }

    /** Ký tự đọc là dấu "/" trong SỐ NHÀ Việt: "xẹt"/"sẹt"/"trên" (123 xẹt 34 → 123/34). */
    private val SLASH_WORDS = setOf("xet", "xuyet", "set", "sep", "suyet", "suet", "tren")

    /**
     * Ghép các mảnh SỐ NHÀ sau khi đã đổi từ-số → chữ số:
     *  • `<số> xẹt/trên <số>` → `<số>/<số>` (123/34/24) — dán liền không khoảng trắng.
     *  • `<số> <chữ-cái-đơn>` → `<số><CHỮ HOA>` (134 a → 134A) — hậu tố nhà đất.
     * Chỉ tác động khi vế trước là CHỮ SỐ thuần; từ thường không bị đụng.
     */
    private fun joinHouseNumber(tokens: List<String>): String {
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val cur = tokens[i]
            if (cur.all { it.isDigit() } && cur.isNotEmpty()) {
                val sb = StringBuilder(cur)
                // nối chuỗi "xẹt <số>" và "<chữ-cái-đơn>" ngay sau số nhà.
                while (i + 1 < tokens.size) {
                    val nxt = tokens[i + 1]
                    val nxtNorm = VoiceLexicon.tokenize(nxt).firstOrNull()?.norm ?: nxt.lowercase()
                    val after = tokens.getOrNull(i + 2)
                    if (nxtNorm in SLASH_WORDS && after != null && after.all { it.isDigit() } && after.isNotEmpty()) {
                        sb.append('/').append(after); i += 2                 // "xẹt 34" → "/34"
                    } else if (nxt.length == 1 && nxt[0].isLetter()) {
                        sb.append(nxt.uppercase()); i += 1                   // "a" → "A" (134A)
                    } else break
                }
                out.add(sb.toString()); i++
            } else { out.add(cur); i++ }
        }
        return out.joinToString(" ")
    }
}
