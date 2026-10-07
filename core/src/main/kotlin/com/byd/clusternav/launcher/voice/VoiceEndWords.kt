package com.byd.clusternav.launcher.voice

/**
 * owner 2026-09-24 — câu KẾT THÚC phiên voice ("bye / tạm biệt / xong rồi / thôi / cảm ơn"): trong lượt nghe NỐI
 * (hội thoại), nói mấy câu này ⇒ đóng voice ngay, thay vì để tấm chữ "đứng hoài không biết làm sao".
 *
 * 2.96 R12 (owner 07/10: *"bye bye Kachi khi xử lý xong việc, đang không nghe được rõ… bai bai, gút bai, tạm biệt,
 * cảm ơn, thoát đi…"*) — mở rộng bảng + cho phép nối nhiều cụm (*"xong rồi, cảm ơn nhé"*) + gọi tên Kachi
 * (*"tạm biệt Kachi"*), và đưa các cụm ≥ 2 từ vào tệp hotword ([HOTWORDS]) để mô hình NGHE ra chúng.
 *
 * ## Chỉ khớp CẢ CÂU (đã bỏ dấu, đã cắt lịch sự) — KHÔNG khớp một-từ-giữa-câu
 * "thôi lấy gió ngoài" (lệnh recirc) có "thôi" ở đầu; "cảm ơn nhé rồi mở nhạc" có "cảm ơn" — nếu bắt theo TỪ thì
 * cắt oan lệnh thật. Vì vậy: câu kết thúc = sau khi bỏ từ đệm ([FILLER]) và tên gọi Kachi ([ADDRESS]), phần còn lại
 * phải **chia trọn** thành một hoặc nhiều cụm kết thúc. Câu còn từ khác (lấy/gió/mở/nhạc) ⇒ KHÔNG.
 *
 * ## Cố ý KHÔNG có (va chạm lệnh thật)
 *  • *"đóng"* / *"đóng đi"* / *"tắt"* / *"tắt đi"* trơn — thiếu đối tượng thì đó là lệnh dở, đường hỏi lại
 *    (`VoiceClarify`: *"Đóng gì?"*) mới đúng; bắt chúng làm câu kết thúc là nuốt mất lượt hỏi ấy. (Bảng cũ có
 *    `tat di` nhưng chưa từng khớp: `stripCourtesy` cắt `đi` trước ⇒ còn `tat`. 2.96 bỏ dòng chết đó.)
 *  • *"dừng"* trơn = TẠM DỪNG NHẠC (`VoiceGrammar.VERBS`) — chỉ *"dừng lại"* là kết thúc (giữ từ 09-24).
 *  • *"chào"* trơn — người ta cũng nói *"chào Kachi"* để BẮT ĐẦU.
 *  • *"đúng rồi"* là ĐỒNG Ý ([VoiceLexicon.CONFIRM_YES]); vì *rồi* là từ đệm nên `dung` một mình KHÔNG được vào bảng.
 *
 * Thuần `:core` — test off-car (`VoiceEndWordsTest`).
 */
object VoiceEndWords {

    /**
     * Cụm kết thúc **có dấu** — nguồn duy nhất: [PHRASES] (so khớp, bỏ dấu tại chỗ) và [HOTWORDS] (bias, giữ dấu) đều
     * sinh từ đây. Viết TAY có dấu (luật memory *"hotword phải có dấu"*: bảng không dấu không bao giờ bias; không khôi
     * phục dấu bằng máy). Cách đọc tiếng Anh viết theo âm Việt mà mô hình zipformer-vi thật sự in ra (*"bai bai"*,
     * *"gút bai"*), kèm dạng Latin cho chữ gõ tay.
     */
    val SPOKEN: List<String> = listOf(
        // tạm biệt
        "tạm biệt", "bai", "bye", "bai bai", "bye bye", "bái bai", "bay bay", "gút bai", "gút bái", "gút bay",
        "good bye", "goodbye", "hẹn gặp lại",
        // xong / đủ
        "xong", "xong rồi", "xong việc", "xong hết", "là xong", "đủ rồi", "hết rồi", "kết thúc",
        // thôi / không cần
        "thôi", "thôi được rồi", "thôi không cần", "không cần", "không cần nữa", "không cần đâu",
        // cảm ơn
        "cảm ơn", "cám ơn", "cảm ơn nhiều", "cám ơn nhiều", "thank you", "thanks", "thanh kiu",
        // thoát / dừng
        "thoát", "thoát ra", "đóng lại", "dừng lại",
    )

    /** Từ đệm vô nghĩa được phép đi kèm câu kết thúc mà vẫn tính là kết thúc. (Khai TRƯỚC [PHRASES]: thứ tự khởi tạo.) */
    private val FILLER = setOf(
        "oi", "o", "a", "nhe", "nha", "roi", "ok", "okay", "oke", "u", "um", "vay", "the", "da", "nhieu", "di", "nao",
    )

    /** Tên gọi Kachi (và cách mô hình nghe ra nó) + đại từ gọi — đứng đâu trong câu kết thúc cũng được bỏ qua. */
    private val ADDRESS: List<List<String>> = listOf(
        listOf("ka", "chi"), listOf("ca", "chi"), listOf("cat", "chi"), listOf("kha", "chi"),
        listOf("kachi"), listOf("ban"), listOf("em"),
    ).sortedByDescending { it.size }

    /** Cụm kết thúc đã bỏ dấu + bỏ từ đệm (cùng phép chuẩn hoá với câu vào — xem [core]). */
    private val PHRASES: Set<List<String>> = SPOKEN.map { core(norm(it)) }.filter { it.isNotEmpty() }.toSet()

    /** Cụm không mang dấu nhưng là âm tiết tiếng Việt thật (*"bai bai"*) — vẫn đáng bias. */
    private val LATIN_VI_SOUNDING: Set<String> = setOf("bai bai", "bay bay")

    /**
     * Dòng HOTWORD — cụm **≥ 2 từ** của [SPOKEN] (dòng một từ bị `SherpaHotwords.phraseFile` loại vì nó làm hỏng
     * cụm khác — KDoc `SherpaPhraseHotwords`), trừ dạng Latin tiếng Anh (mô hình tiếng Việt không phát ra được —
     * cùng luật `NO_VI_FORM`). Viết hoa ở tầng chuẩn hoá (`SherpaHotwords.normalize`).
     *
     * ⚠ Bỏ cụm NỐI DÀI một cụm khác của chính bảng này (*"cảm ơn nhiều"* ⊃ *"cảm ơn"*): tầng lọc
     * (`SherpaHotwords.dropPrefixes`) giữ dòng DÀI và xoá dòng NGẮN — tức nếu để cả hai thì chính *"CẢM ƠN"*, câu hay
     * nói nhất, mất bias. Dạng dài vẫn được HIỂU ([isEnd]: *"nhiều"* là từ đệm), chỉ không được cộng điểm âm.
     */
    val HOTWORDS: List<String> = SPOKEN
        .filter { ' ' in it && (it.any { c -> c.code > 0x7F } || it in LATIN_VI_SOUNDING) }
        .let { lines ->
            lines.filterNot { l -> lines.any { o -> o != l && l.startsWith("$o ") } }
        }

    /** Câu [text] (thô) có phải câu KẾT THÚC phiên không. */
    fun isEnd(text: String): Boolean {
        val words = VoiceLexicon.stripCourtesy(VoiceLexicon.tokenize(text)).map { it.norm }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val c = core(words)
        if (c.isEmpty()) return false
        return segments(c)
    }

    private fun norm(s: String): List<String> = VoiceLexicon.tokenize(s).map { it.norm }

    /** Bỏ tên gọi ([ADDRESS]) rồi bỏ từ đệm ([FILLER]). */
    private fun core(words: List<String>): List<String> {
        val out = ArrayList<String>(words.size)
        var i = 0
        while (i < words.size) {
            val a = ADDRESS.firstOrNull { p -> i + p.size <= words.size && words.subList(i, i + p.size) == p }
            if (a != null) { i += a.size; continue }
            if (words[i] !in FILLER) out.add(words[i])
            i++
        }
        return out
    }

    /** [words] chia trọn được thành các cụm của [PHRASES] (*"xong cam on"* = `xong` + `cam on`)? */
    private fun segments(words: List<String>): Boolean {
        val ok = BooleanArray(words.size + 1).also { it[0] = true }
        for (end in 1..words.size) {
            ok[end] = PHRASES.any { p -> p.size <= end && ok[end - p.size] && words.subList(end - p.size, end) == p }
        }
        return ok[words.size]
    }
}
