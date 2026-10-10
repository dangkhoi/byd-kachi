package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.99 R9 `VOICE-COMPOUND-FALLBACK-WRONG-VERB` — câu ghép có vế DỞ không được làm hỏng vế sau ══════════════════════════
 *
 * Spec `docs/specs/kachi-299-plan.html` §R9. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Bệnh — [ĐO máy ảo T2 w33 + off-car parse, buổi xe 10/10]
 * *"đóng kính lái xong bật đèn đọc"* nghe ra *"đóng lái xong bật đèn đọc"* (nhãn hotword ngắn làm rụng *"kính"* —
 * backlog `VOICE-HOTWORD-SHORT-LABEL-DELETION`). Vế đầu *"đóng lái"* thiếu đối tượng ⇒ `Unknown`; câu ghép khi đó rơi về
 * phân tích NGUYÊN câu ([VoiceIntentParser.parse] nhánh `whole`), và động từ vế đầu *"đóng"* (CLOSE) dán lên đối tượng của
 * vế sau *"đèn đọc"* ⇒ **`Control(readl, 0)`** = TẮT đèn đọc, trong khi người ta nói **BẬT** — rồi còn bỏ vế đầu đi.
 *
 * Sửa: vế ĐÃ hiểu (*"bật đèn đọc"* ⇒ `Control(readl, 1)`) là lệnh thật của riêng nó ⇒ giữ nguyên + MỘT dòng *"đã bỏ qua"*
 * cho vế dở; chỉ đọc lại cả câu khi nó ÔM TRỌN vế kia (tên bài có *"và"/"xong"*). Kèm: *"đóng lái"* một mình KHÔNG còn là
 * câu KẾT THÚC phiên (đồng hình với *"đóng lại"* sau khi bỏ dấu) ⇒ bộ phân tích hỏi lại *"Đóng gì?"* ([VoiceClarify]).
 *
 * Gỡ bản vá ⇒ các bài dưới đây đỏ.
 */
class VoiceCompoundFallbackVerb299Test {

    private fun p(s: String) = VoiceIntentParser.parse(s)
    private fun c(id: String, v: Int) = VoiceIntent.Control(id, v)
    private fun dropped(s: String) = VoiceIntent.Unknown(VoiceUnknownReason.DROPPED_CLAUSE, s)

    // ── Lỗi chính: vế sau GIỮ động từ của riêng nó, vế đầu dở không được dán động từ sang ─────────────────────────────

    @Test
    fun `ve dau do khong ap dong tu len ve sau`() {
        // Gốc lỗi: "đóng" (vế dở "đóng lái") KHÔNG được biến "bật đèn đọc" thành TẮT đèn đọc.
        val got = p("đóng lái xong bật đèn đọc")
        assertEquals(c("readl", 1), got.first(), "vế sau phải giữ BẬT (1), không bị dán động từ ĐÓNG: $got")
        assertTrue(got.any { it == dropped("đóng lái") }, "vế dở «đóng lái» phải được nói là đã bỏ qua: $got")
        assertEquals(listOf(c("readl", 1), dropped("đóng lái")), got)
        // Mô hình in HOA có dấu — cùng kết quả.
        assertEquals(listOf(c("readl", 1), dropped("đóng lái")), p("ĐÓNG LÁI XONG BẬT ĐÈN ĐỌC".lowercase()))
    }

    /** Vế đầu dở với liên từ "rồi"/"và" cũng vậy (cùng nhánh `parts.size > 1`). */
    @Test
    fun `ve dau do voi lien tu roi va`() {
        assertEquals(listOf(c("readl", 1), dropped("đóng lái")), p("đóng lái rồi bật đèn đọc"))
        assertEquals(listOf(c("readl", 1), dropped("đóng lái")), p("đóng lái và bật đèn đọc"))
    }

    // ── GUARD: câu đầy đủ (có "kính") vẫn phải đúng như 2.98 ──────────────────────────────────────────────────────────

    @Test
    fun `cau day du van dung nhu truoc`() {
        assertEquals(listOf(c("win_lf", 0), c("readl", 1)), p("đóng kính lái xong bật đèn đọc"))
        assertEquals(listOf(c("win_lf", 0), c("readl", 0)), p("đóng kính lái xong đèn đọc"))
        assertEquals(listOf(c("readl", 1), c("trunk", 1)), p("bật đèn đọc xong mở cốp"))
    }

    // ── "đóng lái" một mình: hỏi lại "Đóng gì?", KHÔNG kết thúc phiên ─────────────────────────────────────────────────

    @Test
    fun `dong lai mot minh khong ket thuc phien`() {
        assertFalse(VoiceEndWords.isEnd("đóng lái"), "«đóng lái» (thanh sắc) KHÔNG phải câu kết thúc")
        assertFalse(VoiceEndWords.isEnd("ĐÓNG LÁI".lowercase()), "mô hình in HOA có dấu — cùng luật")
        val got = p("đóng lái")
        val u = got.single() as VoiceIntent.Unknown
        assertEquals(VoiceUnknownReason.NO_OBJECT, u.reason, "phải là THIẾU ĐỐI TƯỢNG để hỏi lại: $got")
        val ask = VoiceClarify.ask(u, 0)
        assertEquals("Đóng gì?", ask?.question, "phải hỏi «Đóng gì?»: $ask")
        assertEquals(listOf("đóng"), ask?.carry)
    }

    /** "đóng lại" (thanh nặng) VẪN là câu kết thúc phiên — không được vạ lây. */
    @Test
    fun `dong lai thanh nang van ket thuc`() {
        assertTrue(VoiceEndWords.isEnd("đóng lại"), "«đóng lại» (thanh nặng) vẫn là câu kết thúc")
        assertEquals(listOf(VoiceIntent.EndSession), p("đóng lại"))
        // Gõ KHÔNG dấu: không dữ liệu để tách đồng hình ⇒ hành vi cũ (là câu kết thúc).
        assertTrue(VoiceEndWords.isEnd("dong lai"), "không dấu giữ hành vi cũ")
    }

    // ── Quét: MỌI "<verb1> <vế dở> xong <verb2> <đối tượng>" giữ đúng vế sau ──────────────────────────────────────────

    /**
     * Sinh từ bộ đăng ký (không câu mẫu): với mọi nút TOGGLE, câu *"đóng lái xong &lt;bật|tắt&gt; &lt;nhãn&gt;"* phải
     * ra đúng `Control(nút, 1|0)` (vế sau giữ động từ của RIÊNG nó) + dòng bỏ qua «đóng lái» — không bao giờ bị dán ĐÓNG.
     */
    @Test
    fun `quet - ve do khong lam hong moi nut toggle`() {
        val tog = ControlRegistry.ALL.filter { it.kind == ControlKind.TOGGLE }
        var n = 0
        val miss = ArrayList<String>()
        for (b in tog) {
            val lb = b.label.lowercase()
            // Chỉ xét nút mà "bật/tắt <nhãn>" tự nó ra đúng Control (vài nhãn trùng đầu họ khác — bỏ qua, không thuộc bài này).
            if (p("bật $lb") != listOf(c(b.id, 1)) || p("tắt $lb") != listOf(c(b.id, 0))) continue
            n++
            val on = listOf(c(b.id, 1), dropped("đóng lái"))
            if (p("đóng lái xong bật $lb") != on) miss += "đóng lái xong bật $lb"
            val off = listOf(c(b.id, 0), dropped("đóng lái"))
            if (p("đóng lái xong tắt $lb") != off) miss += "đóng lái xong tắt $lb"
        }
        assertEquals(emptyList<String>(), miss)
        assertFalse(n < 10, "bài không được tự rỗng: n=$n")
    }
}
