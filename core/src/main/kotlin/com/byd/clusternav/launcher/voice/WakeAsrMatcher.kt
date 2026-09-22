package com.byd.clusternav.launcher.voice

/**
 * ═══ "Hey Kachi" KHÔNG-TRAIN — khớp từ đánh thức trong TEXT do chính model NGHE trả về ══════════════════════════
 *
 * Owner 2026-09-22: *"listen nền đã tốt, chỉ nghe đúng «Kachi» là vật lộn — tìm cách đơn giản, KHÔNG train,
 * KHÔNG thu mẫu"*. Hướng chọn (spec `kachi-wake-no-train.html` §3): thay tầng KWS gigaspeech tiếng Anh (vật lộn
 * với từ lạ "Kachi") bằng cách chạy CHÍNH model NGHE tiếng Việt (đã ship, đã hiểu "kachi") trên cửa sổ ngắn rồi
 * **khớp mờ** ở đây.
 *
 * Thuần (`:core`, cấm `android.*`) ⇒ test off-car bằng chuỗi giả. Không mạng, không model, không train.
 *
 * ## Vì sao khớp MỜ, không khớp đúng chuỗi
 * ASR tiếng Việt phát ra "kachi" nhưng cũng "ca chi" · "ka chi" · "ga chi" · "cà chi" (thanh/phụ âm trượt — cùng
 * họ [VoicePhoneticConfusions]). Khớp đúng một chuỗi thì rớt hầu hết. Nên: bỏ dấu + gom mọi biến thể 2 âm tiết
 * mở đầu bằng `ca/ka/ga/co/cu/ga` và kết bằng `chi/che/chy`. Người Việt hay thêm "ơi"/"ok" ⇒ nhận cả cụm đó.
 *
 * ## Chống nổ nhầm
 * Chỉ nhận khi cụm khớp là MỘT-HAI TỪ đứng (không lẫn giữa câu dài) — cabin ồn cho ra chuỗi rác dài, còn "kachi"
 * là câu NGẮN. [maxWords] chặn: text > 4 từ ⇒ không phải câu gọi (là người đang nói chuyện khác).
 */
object WakeAsrMatcher {

    /** Âm tiết đầu chấp nhận cho "ka" (đã bỏ dấu) — ka/ca/ga/co/cu/kha/gha… (thanh + phụ âm đầu trượt). */
    private val HEAD = setOf("ka", "ca", "ga", "co", "cu", "kha", "gha", "cha", "kar", "car")

    /** Âm tiết sau cho "chi" (đã bỏ dấu). */
    private val TAIL = setOf("chi", "che", "chy", "chri", "ti", "tri")

    /** Cụm mở rộng người hay nói: "ơi"/"ok"/"hey"/"hi" bao quanh — không bắt buộc. */
    private val AROUND = setOf("oi", "o", "ok", "okay", "hey", "hay", "hi", "he", "a")

    /** Text ASR (một cửa sổ) có phải câu gọi "Kachi" không. */
    fun isWake(text: String): Boolean {
        val words = VoiceLexicon.tokenize(text).map { it.norm }.filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > MAX_WORDS) return false
        // (a) "kachi" liền một từ.
        if (words.any { oneWordKachi(it) }) return true
        // (b) "ka chi" hai từ liền nhau (bỏ các từ bao quanh ơi/ok/hey trước).
        val core = words.filterNot { it in AROUND }
        for (i in 0 until core.size - 1) {
            if (core[i] in HEAD && core[i + 1] in TAIL) return true
        }
        return false
    }

    /** Một từ đã dính "kachi"/"kachy"/"gachi"… (ASR gộp hai âm thành một). */
    private fun oneWordKachi(w: String): Boolean =
        w.length in 4..6 && HEAD.any { w.startsWith(it.take(2)) } && (w.endsWith("chi") || w.endsWith("chy") || w.endsWith("ti"))

    /** Câu gọi tối đa 4 từ ("ok kachi ơi" = 3) — dài hơn là người đang nói chuyện khác, không phải gọi. */
    const val MAX_WORDS = 4
}
