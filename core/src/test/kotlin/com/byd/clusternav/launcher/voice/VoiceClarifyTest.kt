package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V3 · R8 — HỎI LẠI CHO TỚI KHI HIỂU (owner **D2** 2026-09-16) ════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` R8. Ba thứ bài này khoá:
 *  1. **hỏi đúng cái thiếu** (không phải một câu *"không hiểu"* chung chung);
 *  2. **không hỏi** ở những ca hỏi cũng vô ích (từ vựng mở · vế bị bỏ · *"đóng app"*);
 *  3. **ghép** câu trả lời với ngữ cảnh mà không nhân đôi động từ — ca người dùng hợp tác nhất lại là ca dễ hỏng
 *     nhất (*"mở"* + *"mở kính lái"* → *"mở mở kính lái"* ⇒ lại NO_OBJECT).
 */
class VoiceClarifyTest {

    @AfterEach fun reset() { Strings.current = Lang.VI }

    private fun unknown(reason: VoiceUnknownReason, text: String) = VoiceIntent.Unknown(reason, text)

    @Test
    fun `thieu doi tuong thi hoi dung dong tu vua noi`() {
        val ask = VoiceClarify.ask(unknown(VoiceUnknownReason.NO_OBJECT, "bật"), round = 0)
        assertNotNull(ask)
        assertEquals("Bật gì?", ask!!.question)
        assertEquals(listOf("bật"), ask.carry, "phải nhớ động từ để ghép với câu trả lời")
    }

    @Test
    fun `mot tu tro toi NHIEU nut thi hoi CHON — danh sach sinh tu tu vung, khong chep tay`() {
        // *"kính"* là chữ mở đầu của nhiều cụm trỏ tới nhiều mã nút khác nhau ⇒ đúng ca *"thiếu một từ"*.
        val ask = VoiceClarify.ask(unknown(VoiceUnknownReason.NO_OBJECT, "mở kính"), round = 0)
        assertNotNull(ask)
        assertTrue(ask!!.question.startsWith("Kính nào —"), "câu hỏi thật: ${ask.question}")
        assertTrue(ask.question.endsWith("?"), ask.question)
        // Có liệt kê ít nhất hai lựa chọn có tên (nếu chỉ một thì không có gì để chọn).
        assertTrue(ask.question.count { it == ',' } + 1 >= 2 || ask.question.contains(" hay "), ask.question)
    }

    /**
     * ═══ H4 · *"lọc"* — câu owner nêu tên (2026-09-16, tester 1.66) ══════════════════════════════════════════
     *
     * *"nói 'lọc ngay' nó chả hiểu lọc cái gì, nó phải hỏi lại"*. Danh sách lựa chọn **sinh từ từ vựng**
     * (`pm25` = *"Lọc bụi"* · `pm25_clean_now` = *"Lọc ngay"*), không có một dòng chữ nào viết cứng — đúng luật
     * CLAUDE.md §7: thêm một nút bắt đầu bằng *"Lọc"* là câu hỏi tự dài ra.
     *
     * ⚠ Thứ tự đọc là thứ tự **DANH MỤC** (thứ tự nút trên màn), không phải thứ tự [VoiceGrammar.terms] (cụm dài
     * trước). Trước bản vá, `pm25_clean_now` lên trước chỉ vì nó tình cờ có một cách nói BA từ
     * (*"lọc không khí ngay"*) ⇒ câu hỏi đọc ngược: *"Lọc ngay hay Lọc bụi?"*.
     */
    @Test
    fun `loc mot minh thi hoi Loc bui hay Loc ngay`() {
        val parsed = VoiceIntentParser.parseOne("lọc")
        assertTrue(parsed is VoiceIntent.Unknown, "phải là Unknown để có cửa hỏi lại, ra: $parsed")
        val ask = VoiceClarify.ask(parsed as VoiceIntent.Unknown, round = 0)
        assertNotNull(ask)
        assertEquals("Lọc nào — Lọc bụi hay Lọc ngay?", ask!!.question)
        assertEquals(emptyList<String>(), ask.carry, "không mang theo gì: cả câu mới có một từ")
    }

    @Test
    fun `bon ly do CO Y khong hoi — hoi cung khong giup gi`() {
        listOf(
            VoiceUnknownReason.EMPTY,
            VoiceUnknownReason.OPEN_VOCAB,
            VoiceUnknownReason.APP_CLOSE,
            VoiceUnknownReason.DROPPED_CLAUSE,
        ).forEach { r ->
            assertNull(VoiceClarify.ask(unknown(r, "đóng youtube"), 0), "không được hỏi lại ở lý do $r")
        }
    }

    @Test
    fun `het tran hai luot thi thoi — vong hoi khong the chay mai`() {
        val u = unknown(VoiceUnknownReason.NO_OBJECT, "bật")
        assertNotNull(VoiceClarify.ask(u, 0))
        assertNotNull(VoiceClarify.ask(u, 1))
        assertNull(VoiceClarify.ask(u, VoiceClarify.MAX_ROUNDS), "lượt thứ ba phải là bỏ cuộc")
        assertTrue(VoiceClarify.giveUp().isNotBlank())
    }

    @Test
    fun `ghep cau tra loi voi ngu canh`() {
        assertEquals("bật đèn đọc", VoiceClarify.combine(listOf("bật"), "đèn đọc"))
        assertEquals("đèn đọc", VoiceClarify.combine(emptyList(), "đèn đọc"))
        assertEquals("bật", VoiceClarify.combine(listOf("bật"), "   "))
    }

    @Test
    fun `tra loi ca cau thi KHONG nhan doi dong tu`() {
        // Người dùng trả lời đầy đủ (*"mở kính lái"*) sau câu hỏi *"Mở gì?"*: ghép ngây thơ sẽ ra *"mở mở kính
        // lái"* ⇒ bộ phân tích đọc `mo` hai lần và rơi lại vào NO_OBJECT, tức lượt hỏi làm mọi thứ TỆ HƠN.
        assertEquals("mở kính lái", VoiceClarify.combine(listOf("mở"), "mở kính lái"))
        // Nhận ra kể cả khi dấu khác nhau (chuẩn hoá bỏ dấu, cùng luật với bộ phân tích).
        assertEquals("Mở Kính Lái", VoiceClarify.combine(listOf("mở"), "Mở Kính Lái"))
    }

    /**
     * [SOÁT 2026-09-16 · P3] Ngữ cảnh mang theo **nhiều từ** cũng phải nhận ra được.
     *
     * `combine` so vế mang theo với **từng token** của câu trả lời. Bản trước cắt hai vế bằng hai cách khác nhau
     * (`deaccent` giữ nguyên khoảng trắng bên trong vs `tokenize` một-từ-một-phần-tử) nên một phần tử nhiều từ
     * **không bao giờ khớp** ⇒ ghép lại lần nữa: *"bật đèn bật đèn đọc"*. Hôm nay `ask()` chỉ mang theo một từ
     * (`listOf(verb)`), tức bất biến ngầm — bài này biến nó thành thứ có thể đổi mà không hỏng.
     */
    @Test
    fun `ngu canh nhieu tu cung duoc nhan ra, khong bi ghep lai lan hai`() {
        assertEquals("bật đèn đọc", VoiceClarify.combine(listOf("bật đèn"), "bật đèn đọc"))
        assertEquals("bật đèn đọc", VoiceClarify.combine(listOf("bật", "đèn"), "bật đèn đọc"))
        // Khác ngữ cảnh thì vẫn ghép — đừng "sửa" bằng cách nuốt luôn vế đã có.
        assertEquals("bật đèn mở kính lái", VoiceClarify.combine(listOf("bật đèn"), "mở kính lái"))
        // Và vế một từ (đường đang chạy thật) không đổi hành vi một li.
        assertEquals("mở kính lái", VoiceClarify.combine(listOf("mở"), "mở kính lái"))
    }

    @Test
    fun `cau hoi dich duoc — khong co chuoi Viet nao ket cung`() {
        Strings.current = Lang.EN
        val ask = VoiceClarify.ask(unknown(VoiceUnknownReason.NO_OBJECT, "turn on"), 0)
        assertNotNull(ask)
        assertTrue(ask!!.question.endsWith("what?"), ask.question)
        assertTrue(VoiceClarify.vague().none { it in "àáảãạăâđêôơư" }, VoiceClarify.vague())
        assertTrue(VoiceClarify.giveUp().contains("try saying"), VoiceClarify.giveUp())
    }
}
