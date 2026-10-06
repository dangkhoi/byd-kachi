package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 2.93 VOICE-COURTESY-LAI-HOMOGRAPH (spec `kachi-293-voice.html` §10) — [VoiceLexicon.stripCourtesy] không cắt chữ «lái» mang dấu
 * ở cuối câu như tiếng đệm «lại».
 *
 * [ĐO off-car 07/10] bỏ dấu thì «lái» = «lại» = `lai`: *"mở một nửa kính lái"* (5 từ) bị cắt thành *"mở một nửa kính"* — mất đúng
 * chữ chỉ kính nào. Luật [VoiceHomograph]: chữ MANG dấu chỉ là đệm khi đúng cách viết «lại»; chữ không dấu ⇒ như cũ (cắt).
 */
class VoiceCourtesyLaiTest {

    private fun strip(text: String): List<String> =
        VoiceLexicon.stripCourtesy(VoiceLexicon.tokenize(text)).map { it.norm }

    @Test
    fun `lai mang dau o cuoi cau duoc giu`() {
        assertEquals(listOf("mo", "mot", "nua", "kinh", "lai"), strip("mở một nửa kính lái"))
        assertEquals(listOf("mo", "mot", "nua", "kinh", "lai"), strip("MỞ MỘT NỬA KÍNH LÁI"), "chữ HOA mô hình in")
        assertEquals(listOf("dong", "cua", "kinh", "ben", "lai"), strip("đóng cửa kính bên lái"))
        assertEquals(listOf(VoiceIntent.Control("win_half_lf", 1)), VoiceIntentParser.parse("mở một nửa kính lái"))
    }

    @Test
    fun `dem lai van cat nhu cu`() {
        assertEquals(listOf("dong", "cua", "so", "troi"), strip("đóng cửa sổ trời lại"), "«lại» đúng cách viết ⇒ đệm")
        assertEquals(listOf("mo", "mot", "nua", "kinh"), strip("mo mot nua kinh lai"), "không dấu ⇒ không dữ liệu ⇒ như cũ")
        assertEquals(listOf(VoiceIntent.Control("sunroof", 0)), VoiceIntentParser.parse("đóng cửa sổ trời lại"))
    }

    @Test
    fun `mo lai va mo lai di khong doi`() {
        assertEquals(listOf("mo", "lai"), strip("mở lại"))
        assertEquals(listOf("mo", "lai"), strip("mở lại đi"))
        assertEquals(listOf("bat", "suoi", "ghe", "lai"), strip("bật sưởi ghế lái"), "luật «ghế lái» cũ vẫn giữ")
    }
}
