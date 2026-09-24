package com.byd.clusternav.launcher.voice

/**
 * owner 2026-09-24 — câu KẾT THÚC phiên voice ("bye / tạm biệt / xong rồi / thôi / cảm ơn"): trong lượt nghe NỐI
 * (hội thoại), nói mấy câu này ⇒ đóng voice ngay, thay vì để tấm chữ "đứng hoài không biết làm sao".
 *
 * ## Chỉ khớp CẢ CÂU (đã bỏ dấu, đã cắt lịch sự) — KHÔNG khớp một-từ-giữa-câu
 * "thôi lấy gió ngoài" (lệnh recirc) có "thôi" ở đầu; "cảm ơn nhé rồi mở nhạc" có "cảm ơn" — nếu bắt theo TỪ thì
 * cắt oan lệnh thật. Vì vậy: câu kết thúc = **toàn bộ câu** (sau khi cắt lịch sự) NẰM TRONG tập cụm kết thúc, hoặc
 * là một cụm kết thúc + vài từ đệm vô nghĩa (ơi/à/nhé/rồi/ok/thôi). Câu còn từ khác (lấy/gió/mở/nhạc) ⇒ KHÔNG.
 *
 * Thuần `:core` — test off-car.
 */
object VoiceEndWords {

    /** Cụm kết thúc (đã bỏ dấu). "xong roi"/"cam on"/"tam biet"… */
    private val PHRASES: Set<List<String>> = setOf(
        listOf("bye"), listOf("bai"), listOf("tam", "biet"),
        listOf("xong"), listOf("xong", "roi"), listOf("xong", "viec"),
        listOf("thoi"), listOf("du", "roi"), listOf("dong", "lai"), listOf("tat", "di"),
        listOf("cam", "on"), listOf("cam", "on", "nhe"), listOf("khong", "can", "nua"),
        listOf("thoat"), listOf("dung", "lai"),
    )

    /** Từ đệm vô nghĩa được phép đi kèm câu kết thúc mà vẫn tính là kết thúc. */
    private val FILLER = setOf("oi", "o", "a", "nhe", "roi", "ok", "okay", "u", "um", "vay", "the")

    /** Câu [text] (thô) có phải câu KẾT THÚC phiên không. */
    fun isEnd(text: String): Boolean {
        val words = VoiceLexicon.stripCourtesy(VoiceLexicon.tokenize(text)).map { it.norm }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        // Câu khớp trọn một cụm kết thúc.
        if (words in PHRASES) return true
        // Cụm kết thúc + chỉ toàn từ đệm quanh nó (vd "bye nhé", "ừ xong rồi ok").
        val core = words.filterNot { it in FILLER }
        if (core.isEmpty()) return false
        return core in PHRASES
    }
}
