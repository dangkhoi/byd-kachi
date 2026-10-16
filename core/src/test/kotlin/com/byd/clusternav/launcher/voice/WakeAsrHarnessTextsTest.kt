package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [WakeAsrMatcher] trên CHỮ THẬT của harness wake 16/10 — đánh đổi nổ-đúng / nổ-nhầm khoá bằng máy ═══════════════════════════
 *
 * Nguồn: `scripts/voice/wake-harness` (máy ảo API 29, bản 3.1.9 vehicleTest, giọng TTS tổng hợp, cabin yên) — mọi chữ ASR của
 * từng cửa sổ giải mã, mỗi tệp một dòng (`voice/wake-harness-texts-2026-10-16.tsv`). Một tệp "nổ" khi CÓ một cửa sổ khớp — đúng
 * điều kiện `VoiceWakeAsr.decodeWindow`. Bộ âm gồm 5 câu mới của giọng dẫn đường *"… có camera …"* / *"có chị …"* (xe 16/10).
 *
 * Ngưỡng: câu gọi KHÔNG được tụt dưới số 3.1.9 đo cùng bộ (49/57 = 86,0 %; sàn task 61 %); câu thường 0 nổ. Bản 3.1.9 nổ nhầm 6/60
 * (cả 6 thuộc 5 câu mới). Red-proof: trả "co" vào `HEAD` ⇒ âm đỏ.
 */
class WakeAsrHarnessTextsTest {

    private data class Row(val name: String, val firedOld: Boolean, val texts: List<String>)

    private val rows: List<Row> by lazy {
        val raw = javaClass.getResourceAsStream("/voice/wake-harness-texts-2026-10-16.tsv")!!.bufferedReader().readText()
        raw.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { l ->
            val c = l.split("\t")
            Row(c[0], c[1] == "1", c.getOrElse(2) { "" }.split("|").filter { it.isNotBlank() })
        }
    }

    private fun fires(r: Row) = r.texts.any { WakeAsrMatcher.isWake(it) }

    @Test fun `cau goi khong tut, cau thuong 0 no nham`() {
        val pos = rows.filter { "_pos_" in it.name }
        val neg = rows.filter { "_neg_" in it.name }
        assertEquals(57, pos.size); assertEquals(60, neg.size)
        val hit = pos.count(::fires)
        val oldHit = pos.count { it.firedOld }
        assertTrue(hit >= oldHit, "câu gọi nổ $hit/${pos.size} < 3.1.9 $oldHit/${pos.size}")
        val bad = neg.filter(::fires).map { it.name to it.texts.filter(WakeAsrMatcher::isWake) }
        assertEquals(emptyList<Pair<String, List<String>>>(), bad, "câu thường nổ nhầm (3.1.9: ${neg.count { it.firedOld }}/60)")
    }
}
