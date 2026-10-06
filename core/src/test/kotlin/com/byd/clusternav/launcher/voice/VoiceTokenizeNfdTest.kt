package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.Normalizer

/**
 * ═══ 2.93 wave 2A · VOICE-TOKENIZE-NFD — chuỗi dạng tổ hợp (NFD) tách từ Y HỆT dạng dựng sẵn (NFC) ═══════════════════════
 *
 * Khoá phát hiện [ĐO test 06/10, spec `kachi-293-voice.html` §9]: dấu kết hợp là `\p{M}`, không thuộc lớp ký tự của từ ⇒ bộ tách
 * coi dấu là chỗ ngắt ⇒ *"phát"* NFD ra `pha` + `t`. Bộ nghe trả NFC [SUY], nhưng chữ gõ / dán / tệp nhập không bảo đảm. Nay
 * [VoiceLexicon.tokenize] chuẩn hoá NFC trước khi tách — mọi tầng trên (bộ phân tích, cổng dạy tên, liên từ đồng hình) nhận
 * cùng một dãy từ cho hai dạng mã của cùng một chữ.
 */
class VoiceTokenizeNfdTest {

    private fun nfd(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)

    @Test
    fun `NFD tach tu y het NFC, ban goc tra ve dang NFC`() {
        val text = "Phát bài Nồng nàn Hà Nội rồi bật đèn đọc"
        assertNotEquals(text, nfd(text), "bài phải thật sự đưa vào chuỗi tổ hợp")
        assertEquals(VoiceLexicon.tokenize(text), VoiceLexicon.tokenize(nfd(text)))
        assertEquals(listOf("phat", "bai"), VoiceLexicon.tokenize(nfd("phát bài")).map { it.norm }, "không còn pha + t")
    }

    @Test
    fun `bo phan tich va tu noi dong hinh cho cung ket qua voi hai dang ma`() {
        listOf(
            "bật đèn đọc",
            "phát bài mưa rơi bằng spotify",          // VOICE-ROI-CONNECTOR: «rơi» mang dấu không phải liên từ
            "Đóng hết kính rồi bật đèn đọc",          // «rồi» viết đúng ⇒ hai lệnh
            "đặt nhiệt độ hai mươi tư",
        ).forEach { s ->
            assertEquals(VoiceIntentParser.parse(s), VoiceIntentParser.parse(nfd(s)), "«$s»")
        }
        assertTrue(VoiceIntentParser.parse(nfd("Đóng hết kính rồi bật đèn đọc")).size == 2, "liên từ NFD vẫn tách câu")
    }
}
