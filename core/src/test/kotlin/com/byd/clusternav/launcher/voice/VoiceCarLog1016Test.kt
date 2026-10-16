package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BÀI CANH DỰNG TỪ **HÌNH DẠNG** CÂU XE 16/10 (3.1.9) — `docs/diagnostics/voice-oncar-2026-10-16.md` ════════════════════════
 *
 * [ĐO xe 16/10] câu dạng *"nhạc &lt;tên&gt; trên youtube"* (không động từ) ⇒ `không hiểu: NO_VERB` ⇒ hỏi lại *"&lt;Tên&gt; nào — Đèn đọc,
 * Đèn pha, hay Đèn ban ngày?"*: một chữ của TÊN BÀI (khác thanh với *"đèn"*) bị gập vào họ nút đèn. Tên bài ở đây là TỔNG HỢP (chỉ
 * giữ hình dạng câu). Mỗi ca nói nó khoá gì (CLAUDE.md §10).
 */
class VoiceCarLog1016Test {

    @AfterEach fun resetLang() { Strings.current = Lang.VI }

    private val apps = listOf("YouTube", "Spotify", "VietMap")

    private fun parse(s: String) = VoiceIntentParser.parse(s, apps = apps)

    private fun ask(s: String): VoiceClarify.Ask? =
        (parse(s).singleOrNull() as? VoiceIntent.Unknown)?.let { VoiceClarify.ask(it, 0, VoiceGrammar.terms(apps = apps), Lang.VI) }

    // ══ (a) VOICE-MEDIA-IMPLIED-VERB — từ-nhạc + mệnh đề app nhạc ⇒ PHÁT (động từ ngầm) ══════════════════════════════════════

    /** Red-proof: gỡ dòng `impliedPlay` ở [VoiceIntentParser] ⇒ cả bảng ra `Unknown(NO_VERB)`. */
    @Test fun `tu-nhac + tren app nhac khong dong tu thi phat, ten bai nguyen van`() {
        mapOf(
            "nhạc Áo Đen trên youtube" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Áo Đen", VoiceAppTargets.YOUTUBE),
            "bài hát Gió Chiều Xa trên spotify" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Gió Chiều Xa", VoiceAppTargets.SPOTIFY),
            "nhạc của Mây Lam bằng youtube" to VoiceIntent.Media(VoiceMediaOp.QUERY, "Mây Lam", VoiceAppTargets.YOUTUBE),
            "nhạc trên youtube" to VoiceIntent.Media(VoiceMediaOp.PLAY, app = VoiceAppTargets.YOUTUBE),
        ).forEach { (s, want) -> assertEquals(listOf(want), parse(s), "câu: \"$s\"") }
    }

    /** Cùng câu có động từ ⇒ CÙNG ý định (một đường, không phải đường thứ hai). */
    @Test fun `co dong tu hay khong cung ra mot y dinh`() {
        assertEquals(parse("mở nhạc Áo Đen trên youtube"), parse("nhạc Áo Đen trên youtube"))
        assertEquals(parse("phát bài hát Gió Chiều Xa trên spotify"), parse("bài hát Gió Chiều Xa trên spotify"))
    }

    /** Cổng: thiếu mệnh đề app NHẠC ⇒ vẫn NO_VERB (không đoán phát cho *"nhạc to quá"*); app dẫn đường không phải app nhạc. */
    @Test fun `thieu menh de app nhac thi giu NO_VERB`() {
        listOf("nhạc to quá", "nhạc Áo Đen", "nhạc Áo Đen trên vietmap").forEach { s ->
            assertEquals(VoiceUnknownReason.NO_VERB, (parse(s).single() as? VoiceIntent.Unknown)?.reason, "câu: \"$s\" ra ${parse(s)}")
        }
    }

    // ══ (b) VOICE-CLARIFY-TONE-FOLD + VOICE-CLARIFY-TITLE-FOLD — hỏi lại không viết lại chữ của tên bài ════════════════════════

    /** Red-proof: bỏ [VoiceClarify] `openSpan` VÀ `familyOf` ⇒ hỏi lại *"Đen nào — Đèn …"* như xe 16/10. */
    @Test fun `ten bai sau tu-nhac khong bi hoi lai thanh nut den`() {
        listOf("nhạc Áo Đen", "nhạc ao den", "bài Đèn Khuya").forEach { s ->
            val a = ask(s)
            assertNotNull(a, "câu: \"$s\" phải được hỏi lại")
            assertFalse(a!!.question.contains("Đèn đọc"), "câu: \"$s\" ⇒ hỏi \"${a.question}\": chữ tên bài bị gập vào họ đèn")
        }
    }

    /** Luật thanh: *"đen"* (mang dấu, khác thanh) không phải đầu họ *"đèn"*; *"đèn"* và *"den"* (không dấu) vẫn hỏi như cũ. */
    @Test fun `chu mang dau khac thanh khong gap vao ho nut, khong dau giu hanh vi cu`() {
        val black = ask("đen")!!
        assertFalse(black.question.contains("Đèn"), "\"đen\" ⇒ \"${black.question}\"")
        val lamp = ask("đèn")!!
        assertTrue(lamp.question.startsWith("Đèn nào"), "\"đèn\" vẫn hỏi họ đèn: \"${lamp.question}\"")
        val bare = ask("den")!!
        assertTrue(bare.question.contains("Đèn"), "không dấu ⇒ không dữ liệu để phân biệt ⇒ như cũ: \"${bare.question}\"")
    }
}
