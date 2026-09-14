package com.byd.clusternav.launcher.voice

/**
 * ═══ V2 pha NGHE · HOTWORDS (contextual biasing) — KÉO GIẢI MÃ TỰ DO VỀ TẬP LỆNH ĐÓNG ═══════════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html` §Design. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Vì sao biasing thay cho "ngữ pháp đóng" của Vosk
 * Gốc bệnh Vosk trên xe là **ngữ pháp FST cứng** + mô hình 32 MB: nói cả câu ra một từ (README model WER 15,7%).
 * sherpa-onnx giải mã **tự do** (nghe được câu bất kỳ) rồi dùng **hotwords** kéo nhẹ về đúng nhãn control khi
 * người lái nói gần đúng — [ĐO] off-car: score 3.0 sửa `pin`/`tắt`/`âm lượng` mà KHÔNG chèn nhầm lệnh vào câu
 * thường ("hôm nay trời đẹp quá" giữ nguyên). Xem `docs/diagnostics/vn-stt-sherpa-emulator-eval-2026-09-14.md`.
 *
 * ## Vì sao HOA + có dấu
 * Mô hình VN của sherpa xuất **CHỮ HOA CÓ DẤU** (tokens.txt: `▁ĐÈN`, `▁PIN`…). Hotwords phải cùng bảng chữ thì
 * bộ mã hoá BPE (`modelingUnit=bpe` + `bpeVocab`) mới khớp được — [ĐO] hotword thường/không dấu bị native bỏ
 * ("Failed to encode some hotwords"). Nên hàm này **viết hoa** và giữ nguyên dấu.
 *
 * ## Vì sao lọc, không đổ nguyên danh sách
 *  • Bỏ token đơn ký tự / rỗng: một hotword một chữ cái kéo lệch mọi câu.
 *  • Bỏ chuỗi có chữ số / ký tự Latin lạ (tên app tiếng Anh "youtube"): mô hình VN **không phát ra** được token
 *    đó nên biasing vô nghĩa, còn làm rối — tên app do [VoiceIntentParser] khớp nhãn lo, không phải hotword.
 *  • Khử trùng, giữ thứ tự xuất hiện (ổn định cho test + nhật ký).
 */
object SherpaHotwords {

    /**
     * Sinh nội dung tệp hotwords từ danh sách cụm control **có dấu** (ví dụ các cụm trong [VoicePhraseSet]).
     *
     * @param phrases cụm đã có dấu, bất kỳ hoa/thường; mỗi cụm một dòng ở kết quả.
     * @return nội dung tệp (mỗi hotword một dòng, HOA có dấu, đã lọc + khử trùng); rỗng nếu không cụm nào hợp lệ.
     */
    fun fileContent(phrases: Iterable<String>): String {
        val seen = LinkedHashSet<String>()
        for (raw in phrases) {
            val hw = normalize(raw) ?: continue
            seen.add(hw)
        }
        return if (seen.isEmpty()) "" else seen.joinToString("\n") + "\n"
    }

    /**
     * Chuẩn hoá một cụm thành hotword, hoặc `null` nếu không dùng được.
     *
     * Giữ chữ cái (kể cả có dấu tiếng Việt) và khoảng trắng; gộp khoảng trắng; viết HOA; từ chối nếu sau khi lọc
     * còn rỗng, chỉ một ký tự, hay chứa ký tự không phải chữ Việt/khoảng trắng (chữ số, dấu câu, Latin thuần lạ).
     */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // Từ chối nếu có chữ số hoặc ký tự điều khiển — tên app/số slot không thuộc biasing.
        if (trimmed.any { it.isDigit() }) return null
        val cleaned = buildString {
            var lastSpace = false
            for (c in trimmed) {
                when {
                    c.isLetter() -> { append(c); lastSpace = false }
                    c.isWhitespace() -> { if (!lastSpace && isNotEmpty()) append(' '); lastSpace = true }
                    else -> return null // dấu câu / ký tự lạ ⇒ bỏ cả cụm (an toàn hơn là cắt xén)
                }
            }
        }.trim()
        if (cleaned.length < MIN_LEN) return null
        // Một token đơn (không khoảng trắng) mà quá ngắn cũng bỏ.
        if (!cleaned.contains(' ') && cleaned.length < MIN_SINGLE_TOKEN_LEN) return null
        return cleaned.uppercase()
    }

    private const val MIN_LEN = 2
    private const val MIN_SINGLE_TOKEN_LEN = 2
}
