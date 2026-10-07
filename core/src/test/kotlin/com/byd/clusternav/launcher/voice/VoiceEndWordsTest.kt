package com.byd.clusternav.launcher.voice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
class VoiceEndWordsTest {
    @Test fun `cau ket thuc = true`() {
        listOf("bye", "tạm biệt", "xong rồi", "xong", "thôi", "cảm ơn", "cảm ơn nhé", "ừ xong rồi ok", "đủ rồi", "thoát")
            .forEach { assertTrue(VoiceEndWords.isEnd(it), "‘$it’ phải là câu kết thúc") }
    }

    /** 2.96 R12 — đúng danh sách owner 07/10 (*"bai bai, gút bai, tạm biệt, cảm ơn, thoát đi…"*) + biến thể hay gặp. */
    @Test fun `2_96 R12 - cau ket thuc owner liet ke va bien the`() {
        listOf(
            "bai bai", "bye bye", "bái bai", "gút bai", "gút bái", "good bye", "goodbye", "tạm biệt nhé", "tạm biệt Kachi",
            "bye Kachi", "bai bai ca chi", "hẹn gặp lại", "cảm ơn Kachi", "cám ơn", "cảm ơn nhiều", "cảm ơn bạn nhé",
            "cảm ơn nha", "thank you", "thanks", "thoát đi", "thoát ra", "thôi được rồi", "thôi nhé", "vậy thôi", "thế thôi",
            "ok xong", "xong việc rồi", "xong hết rồi", "vậy là xong", "hết rồi", "kết thúc", "không cần nữa", "không cần đâu",
            "thôi không cần", "dạ cảm ơn", "đóng lại", "dừng lại",
            // nhiều cụm nối nhau
            "xong rồi cảm ơn nhé", "ok cảm ơn tạm biệt", "thôi được rồi cảm ơn Kachi", "cảm ơn, bai bai",
        ).forEach { assertTrue(VoiceEndWords.isEnd(it), "‘$it’ phải là câu kết thúc") }
    }

    @Test fun `lenh that KHONG bi nham la ket thuc`() {
        listOf("thôi lấy gió ngoài", "mở nhạc", "dẫn tới chợ bến thành", "bật đèn đọc", "cảm ơn rồi mở youtube")
            .forEach { assertFalse(VoiceEndWords.isEnd(it), "‘$it’ KHÔNG được là câu kết thúc") }
    }

    /** 2.96 R12 — các câu VA CHẠM đã cân nhắc (KDoc [VoiceEndWords] mục *"Cố ý KHÔNG có"*). */
    @Test fun `2_96 R12 - va cham lenh that van KHONG la ket thuc`() {
        listOf(
            "tắt đi", "đóng đi", "đóng", "tắt", "dừng", "dừng nhạc", "chào Kachi", "chào", "đúng rồi", "đồng ý", "không",
            "bài tiếp theo", "tạm biệt rồi mở nhạc", "đóng kính lái", "tắt sưởi ghế phụ", "thoát youtube", "xong rồi bật điều hoà",
            "Kachi", "ơi", "cảm ơn bật đèn đọc",
        ).forEach { assertFalse(VoiceEndWords.isEnd(it), "‘$it’ KHÔNG được là câu kết thúc") }
    }

    /** Bộ phân tích trả đúng MỘT ý định kết thúc cho câu kết thúc. */
    @Test fun `parser tra EndSession`() {
        listOf("gút bai", "cảm ơn Kachi", "thoát đi", "xong rồi cảm ơn nhé").forEach {
            assertEquals(listOf(VoiceIntent.EndSession), VoiceIntentParser.parse(it), it)
        }
    }

    /**
     * Dòng HOTWORD: có dấu (hoặc âm tiết Việt không dấu đã khai), ≥ 2 từ, chính nó là câu kết thúc, và SỐNG SÓT qua
     * tầng lọc của tệp thật (`SherpaHotwords.phraseFile` — luật một-từ / tiền tố / tên-app-đứng-đầu). Luật memory: bảng
     * không dấu không bao giờ bias.
     */
    @Test fun `hotword ket thuc co dau, vao tep that, va tu no la cau ket thuc`() {
        val hw = VoiceEndWords.HOTWORDS
        assertTrue(hw.size >= 10, "quá ít dòng: $hw")
        listOf("tạm biệt", "cảm ơn", "gút bai", "bai bai", "xong rồi", "thôi được rồi", "kết thúc").forEach {
            assertTrue(it in hw, "thiếu hotword ‘$it’")
        }
        hw.forEach { l ->
            assertTrue(' ' in l, "‘$l’ một từ ⇒ sẽ bị lọc")
            assertTrue(VoiceEndWords.isEnd(l), "hotword ‘$l’ phải tự là câu kết thúc")
        }
        assertFalse("good bye" in hw || "thank you" in hw, "dạng Latin tiếng Anh không đi vào hotword")
        assertFalse("cảm ơn nhiều" in hw, "dạng nối dài một dòng khác sẽ xoá mất dòng ngắn ở tầng lọc tiền tố")
        val file = SherpaHotwords.phraseFile(SherpaPhraseHotwords.phrases(), SherpaPhraseHotwords.appNames()).lines().toSet()
        // MỌI dòng phải sống sót qua tầng lọc của tệp thật (tiền tố / tên-app-đứng-đầu) — dòng bị xoá im lặng là dòng chết.
        hw.map { SherpaHotwords.normalize(it)!! }.forEach { up ->
            assertTrue(up in file, "‘$up’ không còn trong tệp hotword thật")
        }
        assertEquals(23, hw.size, "đổi bảng thì đổi số này + bảng ở docs/diagnostics/voice-phrasing-296.md: $hw")
    }
}
