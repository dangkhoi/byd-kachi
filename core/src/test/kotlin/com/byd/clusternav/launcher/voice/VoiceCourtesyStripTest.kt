package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ═══ REGRESSION P0 (2026-09-24) — stripCourtesy KHÔNG được nuốt "nhà"/"đi" ═════════════════════════════════
 *
 * [ĐO 2026-09-24, senior review] Bản 2.23 (LIVE) mất điểm đến của MỌI câu "… về nhà": `TAIL_COURTESY` có "nha"
 * (ý «nhé») nhưng bỏ dấu thì "nhà" (nhãn HOME) cũng = "nha" ⇒ vòng cắt-đuôi nuốt mất. Test cũ dùng `parseOne`
 * (KHÔNG gọi stripCourtesy) nên mù. Test này gọi THẲNG stripCourtesy — đường mà `VoiceIntentParser.parse` đi.
 *
 * Guard: không cắt "nha"/"di" ở đuôi khi ngay TRƯỚC là từ CHUYỂN ĐỘNG (về/đến/tới/ra/vào/lên/xuống/đi).
 */
class VoiceCourtesyStripTest {

    private fun strip(text: String): List<String> =
        VoiceLexicon.stripCourtesy(VoiceLexicon.tokenize(text)).map { it.norm }.filter { it.isNotEmpty() }

    @Test
    fun `khong nuot 'nha' sau tu chuyen dong`() {
        // "về nhà" · "đến nhà" · "đi về nhà" phải GIỮ "nha" (nơi đến), không bị coi là đệm «nhé».
        assertEquals(listOf("ve", "nha"), strip("về nhà"), "về nhà")
        assertEquals(listOf("den", "nha"), strip("đến nhà"), "đến nhà")
        assertEquals(listOf("di", "ve", "nha"), strip("đi về nhà"), "đi về nhà")
        assertEquals(listOf("ve", "nha"), strip("về nhà nhé"), "về nhà nhé (chỉ cắt 'nhé' cuối)")
    }

    @Test
    fun `van cat 'nhe' 'di' dem that`() {
        // "nhé"/"đi" là đệm THẬT khi KHÔNG đứng sau từ chuyển động.
        assertEquals(listOf("bat", "den", "doc"), strip("bật đèn đọc nhé"), "bật đèn đọc nhé")
        assertEquals(listOf("bat", "dieu", "hoa"), strip("bật điều hòa đi"), "bật điều hòa đi")
        // courtesy chained đầu câu vẫn cắt hết.
        assertEquals(listOf("mo", "kinh"), strip("làm ơn cho tôi mở kính"), "làm ơn cho tôi mở kính")
    }
}
