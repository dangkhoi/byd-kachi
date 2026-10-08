package com.byd.clusternav.launcher.voice

import java.text.Normalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.98 R1 + R2 — khoá hai lỗi bộ phân tích có từ trước 2.93 (spec `kachi-298-plan.html` §3) ══════════════════════════
 *
 * R1 `VOICE-HA-HOMOGRAPH`: luật *"hạ kính"* (= MỞ kính) và *"hạ cốp"* (= ĐÓNG cốp) so trên chữ BỎ DẤU ⇒ câu nói thẳng *"hả kính
 * lái"* (hỏi lại) từng MỞ kính lái, *"hả cốp"* từng ĐÓNG cốp. Nay chữ MANG dấu chỉ là lệnh khi viết đúng *"hạ"* (luật
 * [VoiceHomograph] qua [VoiceGrammar.ACTION_HEAD_WORDS]); chữ không dấu giữ hành vi cũ. [ĐO 09-16] mô hình đang ship in
 * `HẠ KÍNH TRƯỚC TRÁI` (thanh nặng) cho câu nói *"hạ kiếng trước trái"* — `voice-ft-2026-09-16.md` + `voice-rec-2026-09-16/`.
 *
 * R2 `VOICE-XONG-CONNECTOR`: *"đóng kính lái xong đèn đọc"* từng ra MỘT lệnh, vế *"đèn đọc"* mất im lặng — *"xong"* không phải
 * liên từ của [VoiceIntentParser.parse] (chỉ là từ nối đuôi của phép tách MIX, mà vế sau không có động từ nên phép ấy không chạy).
 *
 * Gỡ bản vá ⇒ các bài `ha …` / `xong …` dưới đây đỏ.
 */
class VoiceHaXong298Test {

    private fun p(s: String) = VoiceIntentParser.parse(s)

    /** Không ý định nào là lệnh ĐIỀU KHIỂN (ghi lên xe) — câu hỏi lại không được chạm phần cứng. */
    private fun noWrite(s: String) {
        val got = p(s)
        assertFalse(got.any { it is VoiceIntent.Control || it is VoiceIntent.Macro }, "«$s» KHÔNG được ra lệnh ghi: $got")
    }

    // ── R1 ──────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `ha hoi lai khong mo kinh khong dong cop`() {
        listOf("hả kính lái", "hả cốp", "hả cốp sau", "hả kính trước trái", "hả kính lái xuống").forEach(::noWrite)
        // Mô hình in HOA có dấu (tầng nghe hạ chữ thường) — cùng luật.
        listOf("HẢ KÍNH LÁI", "HẢ CỐP").forEach { noWrite(it.lowercase()) }
        // Các thanh khác của cùng âm tiết `ha` cũng không phải "hạ".
        listOf("hà kính lái", "há kính lái", "hã kính lái", "hà cốp", "há cốp").forEach(::noWrite)
    }

    @Test
    fun `ha that van la lenh nhu cu`() {
        assertEquals(listOf(VoiceIntent.Control("win_lf", 1)), p("hạ kính lái"))
        assertEquals(listOf(VoiceIntent.Control("win_lf", 1)), p("HẠ KÍNH TRƯỚC TRÁI".lowercase()))   // chuỗi mô hình in [ĐO 09-16]
        assertEquals(listOf(VoiceIntent.Control("win_lf", 1)), p("hạ kính lái xuống"))
        assertEquals(listOf(VoiceIntent.Control("trunk", 0)), p("hạ cốp"))
        assertEquals(listOf(VoiceIntent.Control("trunk", 0)), p("hạ cái cốp sau"))
        // NFD (dấu tổ hợp) — cùng chữ.
        assertEquals(listOf(VoiceIntent.Control("win_lf", 1)), p(Normalizer.normalize("hạ kính lái", Normalizer.Form.NFD)))
    }

    /** Gõ KHÔNG dấu (bàn phím xe, kịch bản test, nhật ký cũ): không dữ liệu để tách đồng hình ⇒ hành vi cũ từng byte. */
    @Test
    fun `ha khong dau giu hanh vi cu`() {
        assertEquals(listOf(VoiceIntent.Control("win_lf", 1)), p("ha kinh lai"))
        assertEquals(listOf(VoiceIntent.Control("trunk", 0)), p("ha cop"))
    }

    @Test
    fun `ha that trong cau ghep van tach`() {
        assertEquals(listOf(VoiceIntent.Control("win_lf", 1), VoiceIntent.Control("readl", 1)), p("hạ kính lái rồi bật đèn đọc"))
    }

    // ── R2 ──────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `xong noi hai lenh - ve sau muon dong tu`() {
        assertEquals(listOf(VoiceIntent.Control("win_lf", 0), VoiceIntent.Control("readl", 0)), p("đóng kính lái xong đèn đọc"))
        assertEquals(listOf(VoiceIntent.Control("win_lf", 0), VoiceIntent.Control("readl", 0)), p("ĐÓNG KÍNH LÁI XONG ĐÈN ĐỌC".lowercase()))
    }

    @Test
    fun `xong noi hai lenh day du`() {
        assertEquals(listOf(VoiceIntent.Control("win_lf", 0), VoiceIntent.Control("readl", 1)), p("đóng kính lái xong bật đèn đọc"))
        assertEquals(listOf(VoiceIntent.Control("readl", 1), VoiceIntent.Control("trunk", 1)), p("bật đèn đọc xong mở cốp"))
    }

    /** Vế của câu ghép vẫn là câu MIX không liên từ ⇒ tách tiếp (khoá phép tách tiếp ở [VoiceIntentParser.parse]). */
    @Test
    fun `xong trong cau mix nhieu lenh`() {
        assertEquals(
            listOf(VoiceIntent.Macro("mac_win_close_all"), VoiceIntent.Control("sunroof", 1), VoiceIntent.Control("trunk", 1)),
            p("đóng hết kính mở cửa sổ trời xong mở cốp"),
        )
        assertEquals(
            listOf(VoiceIntent.Control("win_lf", 0), VoiceIntent.Control("readl", 0), VoiceIntent.Control("ac_auto", 1)),
            p("đóng kính lái xong đèn đọc và bật điều hòa"),
        )
    }

    /** "xong" trong TÊN bài (từ vựng mở) không bị cắt thành lệnh — cùng luật "mọi vế phải hiểu được" của "và"/"rồi". */
    @Test
    fun `xong trong ten bai khong tach`() {
        val got = p("phát bài chưa xong")
        assertEquals(1, got.size, "$got")
        val m = got.single() as VoiceIntent.Media
        assertTrue(VoiceLexicon.deaccent(m.query).contains("xong"), "$got")
    }

    /** Vế sau không hiểu được ⇒ KHÔNG im lặng: phải có dòng *"đã bỏ qua"* (DROPPED_CLAUSE). */
    @Test
    fun `xong ve sau vo nghia thi bao da bo qua`() {
        val got = p("đóng kính lái xong con mèo nhà bên")
        assertEquals(VoiceIntent.Control("win_lf", 0), got.first(), "$got")
        assertTrue(got.any { it is VoiceIntent.Unknown && it.reason == VoiceUnknownReason.DROPPED_CLAUSE }, "phải báo đã bỏ qua: $got")
    }

    /** Câu kết thúc phiên (2.96) giữ nguyên — [VoiceEndWords] nhận trước phép tách. */
    @Test
    fun `xong roi van la ket thuc phien`() {
        listOf("xong", "xong rồi", "xong việc", "là xong", "xong rồi cảm ơn nhé").forEach {
            assertEquals(listOf(VoiceIntent.EndSession), p(it), "«$it»")
        }
    }

    /**
     * "xong" đứng CUỐI câu không sinh vế rỗng — câu đi như một vế. (Đứng ĐẦU câu — *"xong bật đèn đọc"* — vẫn NO_VERB như mọi
     * liên từ đầu câu (*"rồi bật đèn đọc"*): hành vi có sẵn, ngoài phạm vi R2.)
     */
    @Test
    fun `xong cuoi cau khong lam hong lenh`() {
        assertEquals(listOf(VoiceIntent.Control("readl", 1)), p("bật đèn đọc xong"))
    }
}
