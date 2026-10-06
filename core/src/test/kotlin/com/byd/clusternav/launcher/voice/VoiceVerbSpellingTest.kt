package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 — động từ đầu vế đọc theo dấu ([VoiceVerbSpelling]) và [P3] `VoiceClarify.leadVerb` dùng cùng phép.
 *
 * [ĐO off-car 07/10] *"tất cả kính lên"* (không hiểu ⇒ hỏi lại) mang chữ *"tất"* sang lượt sau như động từ *"tắt"*: trả lời
 * *"mở cốp"* ghép thành *"tất mở cốp"* = `Control(trunk, 0)` — đóng cốp khi người lái xin mở.
 */
class VoiceVerbSpellingTest {

    private fun tok(s: String) = VoiceLexicon.tokenize(s)

    @Test
    fun `chu mang dau chi la dong tu khi dung cach viet`() {
        assertTrue(VoiceVerbSpelling.isVerb(tok("tắt đèn"), 1))
        assertTrue(VoiceVerbSpelling.isVerb(tok("MỞ CỐP"), 1), "chữ HOA — so theo chữ thường")
        assertTrue(VoiceVerbSpelling.isVerb(tok("kiểm tra pin"), 2), "cụm động từ hai từ")
        assertFalse(VoiceVerbSpelling.isVerb(tok("tất cả kính"), 1), "«tất» ≠ «tắt»")
        assertTrue(VoiceVerbSpelling.isVerb(tok("tat ca kinh"), 1), "không dấu ⇒ không dữ liệu ⇒ như cũ")
        assertFalse(VoiceVerbSpelling.isVerb(tok("tắt"), 0))
    }

    @Test
    fun `dong tu hanh dong o dau ve`() {
        assertEquals(1, VoiceVerbSpelling.actionSpan(tok("hạ kính")), "từ mở đầu lệnh hướng kính")
        assertEquals(1, VoiceVerbSpelling.actionSpan(tok("tắt đèn đọc")))
        assertEquals(0, VoiceVerbSpelling.actionSpan(tok("tất cả kính")))
        assertEquals(0, VoiceVerbSpelling.actionSpan(tok("xem pin")), "động từ ĐỌC không phải hành động")
        assertEquals(0, VoiceVerbSpelling.actionSpan(tok("đèn đọc")), "«đọc» là động từ ĐỌC trong tên nút — không tính")
        assertEquals(0, VoiceVerbSpelling.actionSpan(tok("kính trước trái")), "«trước» trong tên nút — không tính")
    }

    /**
     * Senior review wave 2 [P1] — từ mở đầu lệnh KHÔNG thuộc bảng động từ (hạ · kéo · nâng · lấy) cũng qua luật dấu. [ĐO off-car
     * 07/10] trước bản vá: trả lời *"hả"* cho *"Mở hay đóng Kính lái?"* ⇒ *"hả kính lái"* = MỞ kính (`VoiceBareCoverTest` khoá
     * đường ấy); *"nắng"* của *"rèm che nắng"* thành động từ *"nâng"* của mạch câu ghép (`VoiceClauseEllipsisTest`).
     */
    @Test
    fun `tu mo dau lenh huong kinh cung doc theo dau`() {
        listOf("hạ kính lái", "HẠ KÍNH", "kéo kính lên", "nâng kính lên", "lấy gió trong").forEach { s ->
            assertEquals(1, VoiceVerbSpelling.actionSpan(tok(s)), "«$s» — viết đúng ⇒ động từ")
        }
        listOf("hả", "hà nội", "há", "nắng", "nàng", "kẹo", "lây").forEach { s ->
            assertEquals(0, VoiceVerbSpelling.actionSpan(tok(s)), "«$s» mang dấu khác ⇒ không phải động từ")
        }
        assertEquals(1, VoiceVerbSpelling.actionSpan(tok("ha kinh")), "không dấu ⇒ không dữ liệu ⇒ như cũ")
    }

    /** Senior review wave 2 [P3] — cụm của bảng động từ CHƯA khai cách viết có dấu ⇒ không dữ liệu ⇒ như cũ (là động từ). */
    @Test
    fun `cum dong tu chua khai cach viet co dau thi nhu cu`() {
        assertTrue(VoiceVerbSpelling.isVerb(tok("dẫn tới chợ Bến Thành"), 2))
        assertTrue(VoiceVerbSpelling.isVerb(tok("đưa tôi đến sân bay"), 3))
        assertTrue(VoiceVerbSpelling.isVerb(tok("coi thử pin"), 2))
        assertFalse(VoiceVerbSpelling.isVerb(tok("đừng mở"), 1), "«dừng» ĐÃ khai ⇒ «đừng» không phải động từ")
    }

    @Test
    fun `cau hoi lai khong mang tat sang luot sau`() {
        val u = VoiceIntent.Unknown(VoiceUnknownReason.NO_VERB, "tất cả kính lên")
        val ask = requireNotNull(VoiceClarify.ask(u, 0, lang = Lang.VI))
        assertTrue(ask.carry.none { VoiceLexicon.deaccent(it) == "tat" }, "carry = ${ask.carry}")
        assertEquals(listOf(VoiceIntent.Control("trunk", 1)), VoiceIntentParser.parse(VoiceClarify.combine(ask.carry, "mở cốp")))
        val noObject = VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, "tất cả kính lên")
        assertTrue(VoiceClarify.ask(noObject, 0, lang = Lang.VI)?.carry.orEmpty().isEmpty())
        // Không dấu ⇒ không dữ liệu ⇒ như cũ (mang theo); động từ thật vẫn mang theo như mọi bản trước.
        val typed = VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, "tat ca kinh len")
        assertEquals(listOf("tat"), VoiceClarify.ask(typed, 0, lang = Lang.VI)?.carry)
        assertEquals(listOf("mở"), VoiceClarify.ask(VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, "mở"), 0, lang = Lang.VI)?.carry)
    }
}
