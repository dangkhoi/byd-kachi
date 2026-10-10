package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.99 R3–R6 · BÀI CANH DỰNG TỪ **LOG XE THẬT 10/10** (Seal fw 2606, Kachi 2.98 OTA) ═══════════════════════════════
 *
 * Nguồn: logcat `KachiVoiceSession` / `KachiVoiceRec` / `KachiCtl` buổi xe 10/10 18:27–18:30 (chép nguyên chuỗi mô hình in
 * ra vào từng ca) — phân tích ở `docs/diagnostics/voice-299-r3-r7-2026-10-10.md`, spec `docs/specs/kachi-299-plan.html`.
 * Mỗi ca nói rõ nó KHOÁ cái gì (CLAUDE.md §10).
 */
class VoiceCarLog1010Test {

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

    private val apps = listOf("YouTube", "VietMap", "Spotify")

    private fun parse(s: String) = VoiceIntentParser.parse(s, apps = apps)

    private fun ask(s: String): VoiceClarify.Ask? =
        (parse(s).singleOrNull() as? VoiceIntent.Unknown)?.let { VoiceClarify.ask(it, 0, VoiceGrammar.terms(apps = apps), Lang.VI) }

    // ══ R3 · VOICE-SLOT-NUMBER-CLARIFY ═══════════════════════════════════════════════════════════════════════════

    /**
     * [ĐO xe 18:30:19] owner nói *"mở YouTube vào ô số 2"* ⇒ sherpa `"mở diếp vào ô số hai"` ⇒ `không hiểu: MISMATCH` ⇒ hỏi lại
     * *"Số nào — Odo tổng hay Số VIN?"*. Khoá: chữ *"số"* của mệnh đề ô KHÔNG được thành datum, câu hỏi phải là *"Mở app nào?"*
     * và mệnh đề ô đi theo sang lượt trả lời (trả lời *"YouTube"* ⇒ YouTube vào ô 2).
     */
    @Test fun `R3 · mo dIEP vao o so hai hoi MO APP NAO, giu o cho luot tra loi`() {
        val got = parse("mở diếp vào ô số hai").single()
        assertEquals(VoiceUnknownReason.NO_OBJECT, (got as? VoiceIntent.Unknown)?.reason, "ra: $got")
        val a = ask("mở diếp vào ô số hai")!!
        assertEquals("Mở app nào?", a.question)
        assertEquals(listOf("mở"), a.carry)
        assertEquals(listOf("vào", "ô", "số", "hai"), a.tail)
        val joined = VoiceClarify.combine(a.carry, "YouTube", a.tail)
        assertEquals("mở YouTube vào ô số hai", joined)
        assertEquals(listOf(VoiceIntent.OpenApp("YouTube", 2)), parse(joined))
        // Trả lời kèm ô MỚI ⇒ ô mới thắng, không dán ô cũ.
        assertEquals("mở YouTube vào ô ba", VoiceClarify.combine(a.carry, "YouTube vào ô ba", a.tail))
    }

    /** Họ câu ô có tên app lạ (gồm ca 27/09 *"mở nep leag vào ô số một"* — `kachi-290-voice-app-names.html` §4.10): KHÔNG BAO GIỜ hỏi Odo/VIN. */
    @Test fun `R3 · menh de o KHONG BAO GIO keo cau hoi Odo VIN`() {
        listOf("mở diếp vào ô số hai", "mở nep leag vào ô số một", "đưa diếp vào ô số hai", "mở vào ô số hai", "mở diếp vào ô thứ ba")
            .forEach { s ->
                val q = ask(s)?.question.orEmpty()
                assertFalse(q.startsWith("Số nào"), "«$s» hỏi sai: $q")
                assertTrue(q.endsWith("app nào?"), "«$s» phải hỏi app, ra: $q")
            }
    }

    /** Đối chứng: phép dò họ *"Số …"* vẫn sống khi KHÔNG có mệnh đề ô, và câu đọc / đặt mức có chữ *"ở"* không đổi. */
    @Test fun `R3 · doi chung — ho So van hoi, cau doc va dat muc khong doi`() {
        assertEquals("Số nào — Odo tổng hay Số VIN?", ask("số km")?.question)
        assertEquals(listOf(VoiceIntent.Read("vin")), parse("xem số vin"))
        assertEquals(listOf(VoiceIntent.Control("temp", 22)), parse("đặt nhiệt độ ở hai mươi hai độ"))
        assertEquals(listOf(VoiceIntent.OpenApp("YouTube", 2)), parse("mở YouTube vào ô số hai"))
        // Động từ ĐÓNG + ô không thành câu hỏi app (đóng app có đường riêng — APP_CLOSE).
        assertEquals("Đóng gì?", ask("đóng diếp vào ô số hai")?.question)
    }

    // ══ R4 · VOICE-COP-GOP ═══════════════════════════════════════════════════════════════════════════════════════

    /**
     * [ĐO xe] *"đóng cốp"* nghe ra *"đóng góp"* ở BA buổi: 10/10 18:27:23 · 18:27:29 · 18:27:34 (*"đóng góp sau"*) — owner bỏ
     * cuộc, đóng bằng phím (`KachiCtl … trunk voiceCtlBackDoor args=[3]` 18:27:44); 09-20 10:45 ×4; 09-17 16:18 *"đóng góp xe"*.
     * Khoá: ba chuỗi THẬT ấy ra `Control(trunk, 0)`.
     */
    @Test fun `R4 · ba chuoi xe dong gop ra DONG COP`() {
        listOf("đóng góp", "đóng góp sau", "đóng góp xe").forEach { s ->
            assertEquals(listOf(VoiceIntent.Control("trunk", 0)), parse(s), "«$s»")
        }
    }

    /** Âm tính: *"đóng góp"* là từ thường — cụm không chạm cuối câu, không có động từ trước, hay câu đã hiểu khác ⇒ KHÔNG chạm cốp. */
    @Test fun `R4 · dong gop la tu thuong — khong cham cuoi cau thi KHONG thanh cop`() {
        listOf("đóng góp ý kiến", "đóng góp vào quỹ", "đóng góp đâu", "góp", "góp sau", "có đóng góp đâu").forEach { s ->
            parse(s).forEach { assertFalse(it is VoiceIntent.Control && it.id == "trunk", "«$s» ra lệnh cốp: $it") }
        }
        assertEquals(listOf(VoiceIntent.Media(VoiceMediaOp.PREV)), parse("trước khi đóng góp"), "log 09-20 10:45:00 — không đổi")
    }

    // ══ R6 · VOICE-DROPPED-MIDDLE — chuỗi THẬT, khoá hành vi tầng chữ ═════════════════════════════════════════════════

    /**
     * [ĐO xe 18:29:35] *"sưởi ghế phụ bật gió tự động"* ⇒ sherpa `"sưởi ghế phụ động"` (42 752 mẫu, cửa sổ liền) ⇒ chỉ
     * `seath_r=1`. Ba chữ rụng ở bộ GIẢI MÃ, không ở tầng chữ: tầng chữ không có cách nào dựng lại *"bật gió tự"* — khoá đúng
     * điều đó để không ai "chữa" bằng cách đoán (mở gió tự động khi không nghe thấy). Hai câu xe đã chạy đúng cùng buổi khoá cạnh.
     */
    @Test fun `R6 · chuoi rung giua giu dung phan nghe duoc, hai cau dung cua buoi khong doi`() {
        assertEquals(listOf(VoiceIntent.Control("seath_r", 1)), parse("sưởi ghế phụ động"))
        assertEquals(
            listOf(VoiceIntent.Control("seath_r", 0), VoiceIntent.Control("ac_auto", 0)),
            parse("tắt sưởi ghế phụ tắt gió tự động"),
        )
        assertEquals(
            listOf(VoiceIntent.Control("win_lf", 0), VoiceIntent.Control("readl", 1)),
            parse("đóng kính lái xong bật đèn độc"),
        )
    }

    // ══ R5 · VOICE-YOUTUBE-ASR — tầng NGHE ══════════════════════════════════════════════════════════════════════════

    /**
     * [ĐO host TTS] mô hình in **YOUTUBE** (không in *"DU TÚP"*); tệp ship thiếu dòng cho chữ ấy ⇒ *"mở … vào ô số hai"* bị kéo
     * sang *"MỞ DI"* / *"MỞ ZUBE"* (cùng dạng xe *"mở diếp"*). Khoá: ba dòng động-từ + YOUTUBE có mặt và sống qua luật tiền tố;
     * KHÔNG có dòng *"… YOUTUBE MUSIC"* (đo được: nó làm MỞ YOUTUBE thành tiền tố và cắt *"vào ô"*).
     */
    @Test fun `R5 · tep hotword co MO DUA BAT YOUTUBE, khong co YOUTUBE MUSIC`() {
        val lines = SherpaBiasing.hotwordsFile().trimEnd().split("\n").toSet()
        listOf("MỞ YOUTUBE", "ĐƯA YOUTUBE", "BẬT YOUTUBE").forEach { assertTrue(it in lines, "thiếu dòng $it") }
        assertTrue(lines.none { it.contains("YOUTUBE MUSIC") }, "dòng YOUTUBE MUSIC cắt mệnh đề ô — cấm")
        assertFalse("YOUTUBE" in lines, "từ rời cấm (luật cụm)")
    }
}
