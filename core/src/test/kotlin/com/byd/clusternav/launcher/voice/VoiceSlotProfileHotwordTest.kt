package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ TỆP HOTWORD phải ĐỠ ĐƯỢC hai chỗ mà buổi xe 26/09 đo thấy trống ════════════════════════════════════════
 *
 * Bằng chứng + chuỗi lý lẽ: KDoc [VoiceSlotPhrases] · [VoiceProfileNames] ·
 * `docs/diagnostics/offcar-2026-09-26/voice-tail-fuzzy-phonetic.md`.
 *
 * [ĐO 2026-09-26] tệp thật trước bản này (`core/build/hotwords/hotwords-phrases.txt`, 2 223 dòng) có **0 dòng**
 * chứa *"VÀO Ô"* và **0 dòng** chứa tên hồ sơ ⇒ hai vế hay mất nhất của câu không có đường cộng điểm nào.
 */
class VoiceSlotProfileHotwordTest {

    private fun lines(file: String) = file.trimEnd().split("\n").filter { it.isNotBlank() }

    @Test
    fun `menh de o co mat trong tep hotword`() {
        val hot = lines(SherpaBiasing.hotwordsFile())
        assertTrue("VÀO Ô SỐ HAI" in hot, "vế ô không được bias ⇒ đúng bệnh VOICE-SLOT-TAIL-CUT")
        assertTrue("VÀO Ô HAI" in hot)
        assertTrue("Ô SỐ MỘT" in hot)
        // Tới hết số ô mà bố cục có, không hơn.
        assertTrue("VÀO Ô SỐ BỐN" in hot)
        assertTrue(hot.none { it.contains("Ô SỐ BẢY") }, "không bố cục nào có ô 7 ⇒ không bias")
    }

    @Test
    fun `cum o van dung luat chung cua tep hotword`() {
        val hot = lines(SherpaBiasing.hotwordsFile())
        VoiceSlotPhrases.SPOKEN.forEach { p ->
            val norm = SherpaHotwords.normalize(p)
            assertEquals(p.uppercase(), norm, "cụm ô phải sống qua tầng chuẩn hoá nguyên vẹn: «$p»")
            assertTrue(SherpaHotwords.isPhrase(norm!!), "cụm ô phải ≥ 2 từ: «$p»")
            assertTrue(norm in hot, "cụm ô «$norm» rụng ở tầng lọc — xem dropPrefixes/dropAppNameLeading")
        }
    }

    @Test
    fun `cum o chi gom tu DA CO trong tu vung — khong len them lenh moi`() {
        // Cùng lối canh của [VoiceLayouts.WORDS]: dạng có dấu khai tay, nhưng bỏ dấu ra phải là từ ĐÃ KHAI.
        val allowed = VoiceLexicon.SLOT_WORDS + VoiceLexicon.NUMBER_WORDS
        VoiceSlotPhrases.SPOKEN.forEach { p ->
            VoiceLexicon.tokenize(p).map { it.norm }.forEach { w ->
                assertTrue(w in allowed, "từ «$w» trong cụm ô «$p» không có trong SLOT_WORDS/NUMBER_WORDS")
            }
            assertTrue(VoiceLexicon.tokenize(p).size >= 2, "cụm ô phải ≥ 2 từ: «$p»")
        }
    }

    @Test
    fun `ten ho so vao tep hotword, ke ca ten tieng Anh qua dang doc Viet`() {
        val hot = lines(SherpaBiasing.hotwordsFile(emptyList(), listOf("Mặc định", "Test")))
        assertTrue("HỒ SƠ MẶC ĐỊNH" in hot, "tên hồ sơ tiếng Việt phải được bias — [ĐO xe] nó rụng chữ đầu")
        assertTrue(hot.any { it.contains("HỒ SƠ MẶC ĐỊNH") && it.startsWith("CHUYỂN") })
        // *"Test"* không phát được bằng token VN ⇒ phải có dạng đọc Việt ([VoiceAppPhonetics]).
        assertTrue("HỒ SƠ TÉT" in hot, "tên hồ sơ tiếng Anh phải vào bằng dạng đọc tiếng Việt: $hot")
    }

    @Test
    fun `khong ho so thi tep khong doi mot dong`() {
        assertEquals(SherpaBiasing.hotwordsFile(), SherpaBiasing.hotwordsFile(emptyList(), emptyList()))
    }

    @Test
    fun `tran nut VAD len 1200 de nut chinh-tren-xe voi tới duoc vung can do`() {
        // [ĐO xe 2026-09-26] owner thử `prefs_set voice_vad_min_silence_ms 1100`; trần cũ 800 ⇒ bị từ chối.
        assertEquals(1200, VoiceVadTrim.MAX_MIN_SILENCE_MS)
        assertTrue(1100 in VoiceVadTrim.MIN_MIN_SILENCE_MS..VoiceVadTrim.MAX_MIN_SILENCE_MS)
        // Mặc định KHÔNG đổi: [ĐO] 13/13 bản thu câu-có-ô đều chốt sau điểm hết tiếng (xem KDoc MAX_MIN_SILENCE_MS),
        // và ≥ 800 sẽ phá bất biến "VAD chốt sớm hơn bộ RMS".
        assertEquals(600, VoiceVadTrim.MIN_SILENCE_MS)
        assertTrue(VoiceVadTrim.MIN_SILENCE_MS < VoiceEndpointer.HANGOVER_MS)
    }
}
