package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * Dời NGUYÊN VĂN (pure move, 2.91 VOICE-APP-NAMES C4) khỏi [VoiceIntentParser] — tệp ấy 499/500 dòng và cần chỗ cho
 * tham số `aliases`. Không đổi một dòng logic; chỗ gọi duy nhất vẫn là `VoiceIntentParser.parse`.
 */
internal object VoiceDroppedNote {

    /**
     * Dòng *"đã bỏ qua «…»"* cho vế bị nuốt khi cả câu được hiểu theo cách khác — hoặc rỗng khi không có gì bị bỏ.
     *
     * ## Vì sao im lặng ở đây là ca tệ nhất ([ĐO] `emulator-voice-e2e-2026-09-15.md` §3 L5)
     * *"mở cửa và đèn đọc"* → **một** ý định `Bật Đèn đọc`. Luật *"cách hiểu đầu tiên có nghĩa"* tìm thấy
     * *"đèn đọc"* ở giữa câu và trả đúng một ý định — đúng theo mã, nhưng người lái vừa nói hai việc và chỉ một
     * việc chạy, **không câu nào nói ra**. Họ sẽ tưởng cửa đã mở.
     *
     * ## Vì sao KHÔNG cảnh báo cho *"Mở bài Cỏ dại và hoa dành dành"*
     * Ở câu đó vế thứ hai **nằm trong tên bài hát** — nó không bị bỏ, nó được dùng. Phép phân biệt không cần biết
     * bài hát nào tên có chữ *"và"*: chỉ cần hỏi *"chuỗi của vế ấy có nằm trong phần **từ vựng mở** mà cả câu đã
     * nhận không"* ([openPayload]). Có ⇒ im lặng; không ⇒ nói ra.
     *
     * Cả câu cũng không hiểu được (`whole` là [VoiceIntent.Unknown]) thì không thêm gì: lúc đó đã có sẵn một câu
     * báo, thêm dòng thứ hai chỉ là nói hai lần về cùng một việc.
     */
    fun droppedNote(
        parts: List<List<Token>>,
        each: List<VoiceIntent>,
        whole: VoiceIntent,
    ): List<VoiceIntent> {
        if (whole is VoiceIntent.Unknown) return emptyList()
        val used = VoiceLexicon.deaccent(openPayload(whole))
        val dropped = parts.indices
            .filter { each[it] is VoiceIntent.Unknown }
            .map { VoiceTailClause.text(parts[it]) }
            .filterNot { used.isNotEmpty() && used.contains(VoiceLexicon.deaccent(it)) }
        if (dropped.isEmpty()) return emptyList()
        return listOf(VoiceIntent.Unknown(VoiceUnknownReason.DROPPED_CLAUSE, dropped.joinToString(" / ")))
    }

    /** Phần **từ vựng mở** mà một ý định mang theo (tên bài / điểm đến / tên app), rỗng nếu không có. */
    private fun openPayload(i: VoiceIntent): String = when (i) {
        is VoiceIntent.Nav -> i.query
        is VoiceIntent.Media -> i.query
        is VoiceIntent.NavigateSaved -> i.placeName
        is VoiceIntent.OpenApp -> i.appName
        else -> ""
    }
}
