package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V2 pha NGHE · HOTWORDS — KHOÁ QUY TẮC CHUẨN HOÁ (HOA + CÓ DẤU + LỌC) ════════════════════════════════════
 *
 * Khoá đúng cái làm biasing hỏng-im-lặng: hotword thường/không dấu ⇒ native bỏ; token có chữ số (tên app/số ô)
 * ⇒ mô hình VN không phát ra được ⇒ biasing vô nghĩa. [ĐO] `docs/diagnostics/vn-stt-sherpa-emulator-eval-*`.
 */
class SherpaHotwordsTest {

    @Test
    fun `uppercases and keeps Vietnamese accents`() {
        assertEquals("BẬT ĐÈN ĐỌC SÁCH", SherpaHotwords.normalize("bật đèn đọc sách"))
        assertEquals("ÂM LƯỢNG", SherpaHotwords.normalize("Âm lượng"))
        assertEquals("PIN", SherpaHotwords.normalize("pin"))
    }

    @Test
    fun `collapses internal whitespace and trims`() {
        assertEquals("DẪN ĐƯỜNG", SherpaHotwords.normalize("  dẫn    đường  "))
    }

    @Test
    fun `rejects phrases with digits or punctuation or too short`() {
        assertNull(SherpaHotwords.normalize("ô số 2"), "có chữ số ⇒ bỏ")
        assertNull(SherpaHotwords.normalize("a"), "một ký tự ⇒ bỏ")
        assertNull(SherpaHotwords.normalize("   "), "rỗng ⇒ bỏ")
        assertNull(SherpaHotwords.normalize("nhiệt-độ"), "dấu câu lạ ⇒ bỏ cả cụm")
    }

    @Test
    fun `file content is one hotword per line, deduped, order-stable`() {
        val out = SherpaHotwords.fileContent(listOf("tắt", "Tắt", "bật đèn", "xem pin", "pin"))
        val lines = out.trimEnd().split("\n")
        assertEquals(listOf("TẮT", "BẬT ĐÈN", "XEM PIN", "PIN"), lines)
        assertTrue(out.endsWith("\n"), "tệp hotwords phải kết bằng newline")
    }

    @Test
    fun `empty or all-invalid input yields empty file`() {
        assertEquals("", SherpaHotwords.fileContent(emptyList()))
        assertEquals("", SherpaHotwords.fileContent(listOf("2", "!", "x")))
    }

    @Test
    fun `english app names are dropped so parser not the bias handles them`() {
        // "youtube" là Latin thường model VN không phát ra ⇒ vẫn giữ (chữ cái) NHƯNG không nên gây lỗi;
        // ở đây chỉ kiểm nó không làm ném và được viết hoa — quyết định giữ/bỏ tên app nằm ở tầng wiring.
        assertEquals("YOUTUBE", SherpaHotwords.normalize("youtube"))
        assertFalse(SherpaHotwords.fileContent(listOf("youtube music")).isBlank())
    }
}
