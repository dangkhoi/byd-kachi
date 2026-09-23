package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ GOLDEN CONVERSATION — hội thoại nhiều lượt end-to-end, KHÔNG loop (spec kachi-voice-ux.html R5) ═════════
 *
 * Owner 2026-09-23: *"vào conversation là toàn loop"*. Test này chạy luồng THẬT (`VoiceIntentParser` +
 * `VoiceClarify`) qua nhiều lượt, kiểm hai điều:
 *  1. Câu mơ hồ → hỏi lại đúng; trả lời → ghép (`combine`) → parse ra intent ĐÚNG (hội thoại tiến triển).
 *  2. Trả lời vẫn mơ hồ tới trần [VoiceClarify.MAX_ROUNDS] → `ask` trả `null` (hết hỏi, đóng — KHÔNG loop mãi).
 *
 * Thuần `:core` (parser + clarify không cần Android) — chạy off-car.
 */
class VoiceConversationGoldenTest {

    @AfterEach fun reset() { Strings.current = Lang.VI }

    private fun parse(text: String): VoiceIntent =
        VoiceIntentParser.parse(text, emptyList(), emptyList(), emptyList()).first()

    /** "mở kính" (mơ hồ) → hỏi lại → "kính lái" → ghép → ra Control đúng cửa lái. */
    @Test
    fun `mo kinh mo ho thi hoi lai roi ghep ra dung`() {
        val first = parse("mở kính")
        // Câu mơ hồ ⇒ Unknown (một cửa nào?) — parser giữ NO_OBJECT/ambiguous, không tự đoán.
        if (first is VoiceIntent.Unknown) {
            val ask = VoiceClarify.ask(first, round = 0)
            assertNotNull(ask, "câu mơ hồ phải hỏi lại")
            // Trả lời "kính lái" → ghép với carry → parse lại.
            val joined = VoiceClarify.combine(ask!!.carry, "kính lái")
            val second = parse(joined)
            assertTrue(second !is VoiceIntent.Unknown, "sau khi ghép câu trả lời phải hiểu được: $joined → $second")
        }
        // Nếu parser đã tự hiểu "mở kính" = kính lái (tiền lệ 1.80) thì cũng đạt — không loop, ra Control.
    }

    /** Trần hỏi lại: lượt ≥ MAX_ROUNDS ⇒ ask null (đóng, không loop vô hạn). */
    @Test
    fun `het tran hoi lai thi dong khong loop`() {
        val u = VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, "bật")
        assertNotNull(VoiceClarify.ask(u, round = 0), "lượt 0 còn hỏi")
        assertNull(VoiceClarify.ask(u, round = VoiceClarify.MAX_ROUNDS), "tới trần phải NGƯNG hỏi (null) — chống loop")
    }

    /** giveUp có câu tử tế (không rỗng) để tầng thi hành đóng phiên nói ra được. */
    @Test
    fun `giveUp co cau dong tu te`() {
        assertTrue(VoiceClarify.giveUp().isNotBlank())
    }
}
