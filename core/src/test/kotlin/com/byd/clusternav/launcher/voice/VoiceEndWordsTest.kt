package com.byd.clusternav.launcher.voice
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
class VoiceEndWordsTest {
    @Test fun `cau ket thuc = true`() {
        listOf("bye", "tạm biệt", "xong rồi", "xong", "thôi", "cảm ơn", "cảm ơn nhé", "ừ xong rồi ok", "đủ rồi", "thoát")
            .forEach { assertTrue(VoiceEndWords.isEnd(it), "‘$it’ phải là câu kết thúc") }
    }
    @Test fun `lenh that KHONG bi nham la ket thuc`() {
        listOf("thôi lấy gió ngoài", "mở nhạc", "dẫn tới chợ bến thành", "bật đèn đọc", "cảm ơn rồi mở youtube")
            .forEach { assertFalse(VoiceEndWords.isEnd(it), "‘$it’ KHÔNG được là câu kết thúc") }
    }
}
