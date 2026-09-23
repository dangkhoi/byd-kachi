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
}
