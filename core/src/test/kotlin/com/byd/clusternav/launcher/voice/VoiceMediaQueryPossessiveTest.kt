package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * VOICE-MEDIA-QUERY-POSSESSIVE — khoá [ĐO xe 15/10 18:38]: *"mở nhạc của đen trên youtube"* ra `Media(QUERY "của đen")`
 * ⇒ YouTube tìm "của đen" thay vì ca sĩ "Đen". Red-proof: trả [VoiceTailClause.queryText] về `text(body)` ⇒ bài 1 đỏ.
 */
class VoiceMediaQueryPossessiveTest {

    private fun query(s: String): String? =
        (VoiceIntentParserHarness.one(s) as? VoiceIntent.Media)?.takeIf { it.op == VoiceMediaOp.QUERY }?.query

    @Test fun `cum chi tac gia dau cau bi bo - chu tim la ten nguoi hat`() {
        mapOf(
            "mở nhạc của đen trên youtube" to "đen",          // nguyên văn buổi xe 15/10
            "mở nhạc của Đen" to "Đen",
            "mở bài của Sơn Tùng" to "Sơn Tùng",
            "phát nhạc của ca sĩ Mỹ Tâm" to "Mỹ Tâm",
            "mở nhạc bởi Hà Anh Tuấn" to "Hà Anh Tuấn",
            "tìm nhạc của Đen Vâu" to "Đen Vâu",
            "mở nhạc Đen" to "Đen",                           // không có "của" ⇒ như cũ
        ).forEach { (s, want) -> assertEquals(want, query(s), "câu: \"$s\"") }
    }

    @Test fun `giua ten bai thi giu nguyen, khong rut thanh rong`() {
        assertEquals("Bông hoa của anh", query("mở bài Bông hoa của anh"), "\"của\" giữa tên bài là một phần tên")
        assertEquals("Nồng nàn Hà Nội", query("Mở bài Nồng nàn Hà Nội"), "ca cũ không đổi")
    }
}
