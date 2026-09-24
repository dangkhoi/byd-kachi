package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Chuẩn hoá số đọc↔chữ số (findings 2026-09-23): "công ty 1"↔"công ty một", "67"↔"sáu bảy". */
class VoiceNumberNormTest {

    private fun norm(s: String) = VoiceLexicon.tokenize(s).map { it.norm }

    @Test fun `chuoi don vi roi noi thanh chu so`() {
        assertEquals(listOf("cong", "ty", "67"), VoiceNumberNorm.wordsToDigits(norm("cong ty sau bay")))
        assertEquals(listOf("67"), VoiceNumberNorm.wordsToDigits(norm("sau bay")))
        assertEquals(listOf("1"), VoiceNumberNorm.wordsToDigits(norm("mot")))
    }

    @Test fun `so hoc van gop dung`() {
        assertEquals(listOf("24"), VoiceNumberNorm.wordsToDigits(norm("hai muoi tu")))
    }

    @Test fun `chu so giu nguyen`() {
        assertEquals(listOf("cong", "ty", "1"), VoiceNumberNorm.wordsToDigits(norm("cong ty 1")))
    }

    @Test fun `normalize address giu chu giu dau`() {
        // "sáu bảy" → "67", phần chữ giữ nguyên dấu.
        assertEquals("67 hoàng văn thái", VoiceNumberNorm.normalizeSpokenNumbers("sáu bảy hoàng văn thái"))
    }

    @Test fun `khop so voi nhan luu co chu so`() {
        // ASR "công ty một" khớp nhãn lưu "Công ty 1".
        val hit = VoicePlaces.match(norm("cong ty mot"), listOf("Công ty 1"))
        assertEquals("Công ty 1", hit)
    }

    @Test fun `khop nguoc nhan chu ASR so`() {
        // ASR ra chữ số "công ty 1" khớp nhãn "Công ty một".
        val hit = VoicePlaces.match(norm("cong ty 1"), listOf("Công ty một"))
        assertEquals("Công ty một", hit)
    }

    @Test fun `so hang tram — bay tram hai muoi thanh 720`() {
        assertEquals("720 hoàng văn thái", VoiceNumberNorm.normalizeSpokenNumbers("bảy trăm hai mươi hoàng văn thái"))
    }

    @Test fun `tram le — bay tram le nam thanh 705`() {
        assertEquals("705", VoiceNumberNorm.normalizeSpokenNumbers("bảy trăm lẻ năm"))
    }

    @Test fun `mot tram thanh 100`() {
        assertEquals("100 lý thường kiệt", VoiceNumberNorm.normalizeSpokenNumbers("một trăm lý thường kiệt"))
    }

    @Test fun `so nho van dung — sau bay van la 67, hai muoi tu van 24`() {
        assertEquals("67 hồ văn thái", VoiceNumberNorm.normalizeSpokenNumbers("sáu bảy hồ văn thái"))
        assertEquals("24", VoiceNumberNorm.normalizeSpokenNumbers("hai mươi tư"))
    }


    @Test fun `so nha kho — nghin, chu cai hau to, gach cheo`() {
        assertEquals("1898 lý thường kiệt", VoiceNumberNorm.normalizeSpokenNumbers("một nghìn tám trăm chín mươi tám lý thường kiệt"))
        assertEquals("134A điện biên phủ", VoiceNumberNorm.normalizeSpokenNumbers("một trăm ba mươi tư a điện biên phủ"))
        assertEquals("123/34/24 huỳnh tấn phát", VoiceNumberNorm.normalizeSpokenNumbers("một hai ba xẹt ba tư xẹt hai tư huỳnh tấn phát"))
    }


    @Test fun `doc tat chu so + xuyet`() {
        assertEquals("1329 lý thường kiệt", VoiceNumberNorm.normalizeSpokenNumbers("1 ngàn 3 trăm 2 mươi chín lý thường kiệt"))
        assertEquals("32/9 hoàng văn thái", VoiceNumberNorm.normalizeSpokenNumbers("ba hai xuyệt chín hoàng văn thái"))
    }

}
