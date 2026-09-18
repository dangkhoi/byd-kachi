package com.byd.clusternav.launcher.voice

/**
 * ═══ CÂU GỌI "Hey Kachi" — danh sách PRESET (thuần, cấm `android.*`) ══════════════════════════════════════════
 *
 * ## Owner chốt 2026-09-18: KHÔNG cần câu gọi tiếng Việt tùy ý
 * ⇒ chỉ vài **preset đã kiểm**. Điều này quan trọng vì model KWS của sherpa-onnx **không có tiếng Việt** (chỉ
 * zh/en/zh-en); preset chọn cụm **âm Anh/latinh** mà model en/zh-en bắt được. User chọn một preset ở Cài đặt,
 * KHÔNG gõ tự do (đỡ phải làm phương án chạy ASR mỗi onset — nặng CPU, đúng thứ owner lo).
 *
 * [spoken] = dạng chữ để dựng keywords-file của KWS ở `:app` (tstack token theo `tokens.txt` của model — việc
 * tokenize cần bảng token của model nên nằm ở `:app`, không ở đây). [display] = chữ hiện cho user.
 *
 * ⚠ Chất lượng bắt từng preset [CHƯA BIẾT] cho tới khi đo trên xe (OQ1). Danh sách này là **ứng viên**; preset
 * nào đo thấy bắt tệ/nhiều false-accept sẽ bị loại khi có số thật.
 */
object VoiceWakePhrase {

    data class Preset(val id: String, val display: String, val spoken: String)

    /** Ứng viên preset — cụm 2 âm tiết trở lên, tránh cụm quá ngắn/quá thường (dễ false-accept). */
    val PRESETS: List<Preset> = listOf(
        Preset("hey_kachi", "Hey Kachi", "hey kachi"),
        Preset("ok_kachi", "OK Kachi", "ok kachi"),
        Preset("hi_kachi", "Hi Kachi", "hi kachi"),
    )

    val DEFAULT: Preset = PRESETS.first()

    fun byId(id: String?): Preset = PRESETS.firstOrNull { it.id == id } ?: DEFAULT

    /**
     * Kiểm một câu gọi tùy chọn (dự phòng nếu SAU này owner mở nhập tay): 2..4 từ, mỗi từ ≥ 2 ký tự, chỉ
     * chữ/số/space. Cụm một từ hoặc quá ngắn bị loại vì false-accept cao (cùng lẽ vì sao preset ≥ 2 âm tiết).
     */
    fun isValidCustom(phrase: String): Boolean {
        val words = phrase.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size !in 2..4) return false
        return words.all { it.length >= 2 && it.all { c -> c.isLetterOrDigit() } }
    }
}
