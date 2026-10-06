package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 VOICE-OPEN-TURN-DYNVOCAB — ghép vế sau quãng ngừng bằng từ vựng ĐỘNG của phiên ════════════════════════════
 *
 * Khoá khe OQ10 của spec `kachi-290-voice-app-names.html` (backlog `VOICE-OPEN-TURN-DYNVOCAB`): tới 2.92
 * [VoiceOpenTurn.refine] phân tích bằng từ vựng TĨNH ⇒ *"mở &lt;tên đã dạy / nhãn app máy&gt;"* ⟨ngừng⟩ *"vào ô số hai"*
 * không ghép được (cả hai vế `Unknown`) ⇒ app mở vào ô CŨ / toàn màn. Chuỗi *"nep leag"* = chuỗi THẬT mô hình in cho
 * *"netflix"* [SUY — log xe 27/09, như `TeachSampleTest`]; gói `com.example.*` là bộ thử.
 */
class VoiceOpenTurnDynVocabTest {

    private val flix = "com.example.flix"
    private val labels = listOf("Netflix" to flix, "VTV Go" to "com.example.vtv")
    private val taught = listOf(TaughtName(flix, TaughtSource.SPEECH, "nep leag", "Netflix"))
    private val keys = VoiceAppIndex.build(labels, labels.map { it.second }.toSet(), taught).keys
    private val dyn = VoiceDynVocab(
        profiles = listOf("Mặc định"),
        apps = keys.keys.toList(),
        aliases = VoiceAppIndex.aliasesOf(keys, taught),
    )

    @Test
    fun `ten da day + ve sau co o ghep duoc bang tu vung dong`() {
        assertEquals("mở nep leag vào ô số hai", VoiceOpenTurn.attach("mở nep leag", "vào ô số hai", dyn))
        assertEquals(VoiceIntent.OpenApp("Netflix", 2), dyn.parseOne("mở nep leag vào ô số hai"))
        assertTrue(VoiceOpenTurn.mayAttach("mở nep leag", dyn), "cổng rẻ phải dùng CÙNG từ vựng với refine")
    }

    @Test
    fun `nhan app chi co tren may + ve sau co o ghep duoc`() {
        assertEquals("mở vtv go vào ô số hai", VoiceOpenTurn.attach("mở vtv go", "vào ô số hai", dyn))
        assertEquals(VoiceIntent.OpenApp("VTV Go", 2), dyn.parseOne("mở vtv go vào ô số hai"))
    }

    /** Hành vi 2.92 giữ nguyên khi chỗ gọi không truyền từ vựng (mặc định [VoiceDynVocab.STATIC]) — đây là khe vừa vá. */
    @Test
    fun `tu vung tinh van khong ghep duoc - dung khe 2_92`() {
        assertNull(VoiceOpenTurn.attach("mở nep leag", "vào ô số hai"))
        assertFalse(VoiceOpenTurn.mayAttach("mở nep leag"))
        assertNull(VoiceOpenTurn.refine("mở nep leag", "vào ô số hai", VoiceDynVocab.STATIC))
    }

    /** Luật *"cùng ý định, đầy đủ hơn"* không đổi: vế sau là một LỆNH KHÁC ⇒ giữ vế trước, kể cả với từ vựng động. */
    @Test
    fun `ve sau la lenh khac van bi tu choi`() {
        assertNull(VoiceOpenTurn.attach("mở nep leag", "bật đèn đọc", dyn))
        assertNull(VoiceOpenTurn.attach("mở nep leag vào ô số một", "vào ô số hai", dyn), "đã có ô ⇒ không có gì để thêm")
    }

    /** Vế trước DỞ vẫn ghép như cũ (đường `join`, không hỏi từ vựng) — từ vựng động không đổi nhánh này. */
    @Test
    fun `ve truoc do van ghep nhu cu`() {
        assertEquals("mở vietmap vào ô số hai", VoiceOpenTurn.attach("mở vietmap vào ô", "số hai", dyn))
        assertEquals("mở vietmap vào ô số hai", VoiceOpenTurn.attach("mở vietmap vào ô", "số hai"))
    }
}
