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
    private val HEAD = setOf("ka", "ca", "ga", "co", "cu", "kha", "gha", "cha", "kar", "car", "kach", "cach", "kacha", "cac")

    /** Âm tiết sau cho "chi" (đã bỏ dấu). "chi"/"che"/"chì"→chi · "chị"→chi (các chị). */
    private val TAIL = setOf("chi", "che", "chy", "chri", "ti", "tri", "chie")

    /** Từ đệm/bao quanh người hay nói HOẶC ASR chèn giữa: "ơi"/"ok"/"hey"/"hay"/"hai"… — bỏ trước khi soi cặp. */
    private val FILLER = setOf("oi", "o", "ok", "okay", "hey", "hay", "hai", "hi", "he", "a", "e", "va", "cai")

    /**
     * Text ASR (một cửa sổ) có phải câu gọi "Kachi" không — chỉnh theo GOLDEN on-car 2026-09-23
     * (`scripts/voice/data/wake-golden-oncar-2026-09-23.txt`): model ra "kach"/"kacha"/"cá chì"/"các chị" +
     * chèn "hay/hai" giữa. Cân bằng: bằng chứng MẠNH ("kach" 1 từ · "các chị") cho cụm dài hơn; head+tail cho
     * phép ≤1 từ đệm giữa; câu THƯỜNG trong golden (giờ giấc, "mở nhạc"…) KHÔNG khớp.
     */
    fun isWake(text: String): Boolean {
        val words = VoiceLexicon.tokenize(text).map { it.norm }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        // (a) MỘT từ đã dính "kach"/"kacha"/"kachi"… trong cụm KHÔNG quá dài. Golden: câu gọi ra tối đa ~6 từ
        // (chèn "hay/hai"); câu THƯỜNG chứa "kachi" giữa (vd "con kachi màu đỏ hôm qua" 8 từ) thì KHÔNG nổ.
        if (words.size <= WAKE_MAX_WORDS && words.any { oneWordKachi(it) }) return true
        // (b) "các chị"/"cac chi" hai từ liền = "Kachi ơi" (golden, kể cả lặp "các chị ơi các chị ơi"). Cho cụm
        // dài NẾU nó CHỦ YẾU là âm-wake (cac/chi/đệm) — chặn câu thường "các chị em ơi lại đây" (có em/lai/day).
        var cacChi = false
        for (i in 0 until words.size - 1) if (words[i] == "cac" && words[i + 1].startsWith("chi")) cacChi = true
        if (cacChi) {
            val wakeish = words.count { it == "cac" || it.startsWith("chi") || it in FILLER }
            if (words.size <= WAKE_MAX_WORDS || wakeish * 10 >= words.size * 7) return true
        }
        // Bỏ từ đệm ("hay/hai/ơi/ok"…) rồi soi phần LÕI. Model chèn đệm nhiều (golden "hay kach hay kach") ⇒
        // đo độ dài trên LÕI, không trên chuỗi thô — nhưng lõi vẫn phải NGẮN (câu dài = đang nói chuyện khác).
        val core = words.filterNot { it in FILLER }
        if (core.isEmpty() || core.size > MAX_WORDS) return false
        // (c) head + tail liền nhau trong lõi ("ka chi").
        for (i in 0 until core.size - 1) {
            if (core[i] in HEAD && core[i + 1] in TAIL) return true
        }
        // (d) có CẢ một head và một tail trong lõi ngắn (không cần liền) — "cá chì cá", "ka che ca".
        return core.any { it in HEAD } && core.any { it in TAIL }
    }

    /** Một từ đã dính "kachi"/"kach"/"kacha"/"cach"/"gachi"… (ASR gộp). Bắt đầu ka/ca/ga/kha + chứa "ch". */
    private fun oneWordKachi(w: String): Boolean =
        w.length in 4..6 &&
            (w.startsWith("ka") || w.startsWith("ca") || w.startsWith("ga") || w.startsWith("kh")) &&
            (w.contains("ch") || w.endsWith("ti"))

    /** Câu gọi tối đa 4 từ ("ok kachi ơi" = 3) — dài hơn là người đang nói chuyện khác, không phải gọi. */
    const val MAX_WORDS = 4
    /** Cụm câu GỌI (kể cả chèn "hay/hai") tối đa ~6 từ; dài hơn = câu thường chứa "kachi" giữa ⇒ không nổ. */
    const val WAKE_MAX_WORDS = 6
}
