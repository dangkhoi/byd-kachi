package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.TelemetryRegistry
import com.byd.clusternav.launcher.TyreIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.88 · soát regress-3 — 13 mã trạng thái THÔ của lốp KHÔNG vào bộ từ vựng giọng nói nào ═══════════════════
 *
 * Khoá hai hậu quả đã soát ra (KDoc [VoiceTelemetry]): (1) ngữ pháp ASR nở thêm 13 cụm + 37 mục mà `voice-e2e.sh` chưa
 * đo; (2) câu hỏi *"trạng thái áp lốp trước trái"* trả *"chưa đọc được"* giả vì R7 cố ý không đọc mã TPMS của bánh
 * mà cụm nói trắng. Câu hỏi về áp suất lốp (`tyre_p_*`) phải vẫn nói được như trước 2.88.
 */
class VoiceTelemetryTest {

    private val raw = TyreIds.RAW_STATES.toSet()

    @Test
    fun `danh sach noi duoc = bo dang ky tru dung 13 ma lop tho`() {
        assertEquals(raw, VoiceTelemetry.NOT_SPOKEN)
        assertEquals(13, raw.size)
        assertEquals(TelemetryRegistry.ALL.map { it.id } - raw, VoiceTelemetry.SPOKEN.map { it.id }, "giữ nguyên thứ tự")
    }

    @Test
    fun `ngu phap, danh muc cau, hotword deu khong co ma lop tho`() {
        val grammarIds = VoiceGrammar.terms().filter { it.kind == VoiceTermKind.TELEMETRY }.map { it.id }.toSet()
        assertEquals(emptySet<String>(), grammarIds intersect raw, "ngữ pháp")
        assertTrue(TyreIds.PRESSURE.all { it in grammarIds }, "áp suất lốp vẫn nói được")

        val catalogIds = VoiceCommandCatalog.groups().flatMap { g -> g.examples.mapNotNull { VoiceCommandCatalog.keyOf(it.intent) } }
        assertEquals(emptySet<String>(), catalogIds.toSet() intersect raw, "danh mục câu")

        // Cụm đặc trưng của nhãn 13 mã thô — không cụm hotword/ngữ pháp Sherpa nào được mang chúng.
        val marks = listOf("trạng thái áp lốp", "rò khí lốp", "màu cảnh báo lốp", "giám sát lốp")
        val hot = SherpaPhraseHotwords.phrases().map { it.lowercase() }
        marks.forEach { m -> assertTrue(hot.none { it.contains(m) }, "hotword còn «$m»") }
        val sherpa = VoicePhrases.build(vocabulary = setOf("x")).let { it.entries + it.phrasesDropped + it.wordsUnknown }
            .map { it.lowercase() }
        marks.forEach { m -> assertTrue(sherpa.none { it.contains(m) }, "ngữ pháp Sherpa còn «$m»") }
    }

    @Test
    fun `cau hoi trang thai ap lop khong ra ma tho, cau hoi ap suat van ra tyre_p`() {
        val got = VoiceIntentParser.parseOne("trạng thái áp lốp trước trái")
        assertFalse((got as? VoiceIntent.Read)?.datumId in raw, "không được trả lời bằng mã thô: $got")
        val p = VoiceIntentParser.parseOne("xem áp lốp trước trái")
        assertEquals("tyre_p_fl", (p as? VoiceIntent.Read)?.datumId, "áp suất vẫn hỏi được: $p")
    }
}
